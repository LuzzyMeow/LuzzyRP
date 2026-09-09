package com.luzzymeow.luzzyrp.assistant.runtime

import android.content.Context
import com.luzzymeow.luzzyrp.assistant.data.db.AssistantDatabase
import com.luzzymeow.luzzyrp.assistant.data.db.entity.SkillBindingEntity
import com.luzzymeow.luzzyrp.assistant.data.db.entity.SkillEntity
import com.luzzymeow.luzzyrp.assistant.domain.prompt.SkillDocument
import com.luzzymeow.luzzyrp.assistant.domain.prompt.SkillScope
import com.luzzymeow.luzzyrp.assistant.domain.skill.SkillLoader
import com.luzzymeow.luzzyrp.assistant.domain.skill.SkillParseException
import com.luzzymeow.luzzyrp.assistant.domain.tool.SsrfGuard
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID

/**
 * 技能仓库（PLAN §8）。
 *
 * 职责：
 * - 内置技能从 `assets/assistant/skills/` 下的 `.md` 首次导入（幂等，按名去重）；
 * - 文件导入（SAF 选 `.md`）→ 解析校验 → 落库；
 * - 解析「全局启用 + 助手绑定启用」的技能，转 [SkillDocument] 供 ContextBuilder 注入；
 * - 同名冲突：**用户导入 > 内置**（PLAN §8.2）。
 */
class SkillRepository(
    private val context: Context,
    private val database: AssistantDatabase,
) {

    private val skillDao = database.skillDao()
    private val bindingDao = database.skillBindingDao()
    private val now: () -> Long = System::currentTimeMillis

    /** 首次启动导入内置技能（幂等：同名内置已存在则跳过）。 */
    suspend fun importBuiltinsIfNeeded(): Int {
        val existingNames = runCatching { skillDao.getAll().map { it.name } }.getOrDefault(emptyList())
        val assets = runCatching { context.assets.list(BUILTIN_DIR).orEmpty().toList() }.getOrDefault(emptyList())
        var imported = 0
        assets.filter { it.endsWith(".md") }.forEach { fileName ->
            val markdown = runCatching { context.assets.open("$BUILTIN_DIR/$fileName").bufferedReader().use { it.readText() } }
                .getOrNull() ?: return@forEach
            val parsed = runCatching { SkillLoader.parse(markdown, SkillLoader.nameFromFileName(fileName)) }
                .getOrNull() ?: return@forEach
            if (parsed.name in existingNames) return@forEach
            val ts = now()
            skillDao.upsert(
                SkillEntity(
                    id = UUID.randomUUID().toString(),
                    name = parsed.name,
                    description = parsed.description,
                    body = parsed.body,
                    source = SkillEntity.SOURCE_BUILTIN,
                    enabledGlobal = false,
                    createdAt = ts,
                    updatedAt = ts,
                )
            )
            imported++
        }
        return imported
    }

    suspend fun all(): List<SkillEntity> = skillDao.getAll()

    suspend fun get(id: String): SkillEntity? = skillDao.getById(id)

    /** 从 Markdown 文本导入（SAF 或粘贴）。解析失败抛 [com.luzzymeow.luzzyrp.assistant.domain.skill.SkillParseException]。 */
    suspend fun importMarkdown(markdown: String, fallbackName: String?, source: String = SkillEntity.SOURCE_FILE): SkillEntity {
        val parsed = SkillLoader.parse(markdown, fallbackName)
        val ts = now()
        val existing = skillDao.getByName(parsed.name)
        val entity = SkillEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = parsed.name,
            description = parsed.description,
            body = parsed.body,
            source = source,
            // 同名覆盖时保留原有开关（用户导入覆盖内置，但不静默改变启用状态）
            enabledGlobal = existing?.enabledGlobal ?: false,
            createdAt = existing?.createdAt ?: ts,
            updatedAt = ts,
        )
        skillDao.upsert(entity)
        return entity
    }

    /**
     * 从 URL 导入技能（PLAN §8.2 `source=url`）。
     *
     * **安全**：协议白名单 + [SsrfGuard] DNS 层拒私网/回环；正文上限 256KB；
     * 解析失败抛 [com.luzzymeow.luzzyrp.assistant.domain.skill.SkillParseException]（不静默吞）。
     */
    suspend fun importFromUrl(url: String): SkillEntity = withContext(Dispatchers.IO) {
        if (!SsrfGuard.schemeAllowed(url)) throw SkillParseException("仅支持 http/https 链接")
        SsrfGuard.reasonOf(url)?.let { throw SkillParseException(it) }
        val text = try {
            client.newCall(
                Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build()
            ).execute().use { response ->
                if (!response.isSuccessful) throw SkillParseException("HTTP ${response.code}")
                response.body?.string().orEmpty().take(MAX_URL_BYTES)
            }
        } catch (e: SkillParseException) {
            throw e
        } catch (e: IOException) {
            throw SkillParseException("下载失败：${e.message ?: e.javaClass.simpleName}")
        }
        val fileName = url.substringBefore('?').substringAfterLast('/').ifBlank { "skill.md" }
        importMarkdown(text, SkillLoader.nameFromFileName(fileName), SkillEntity.SOURCE_URL)
    }

    suspend fun setEnabledGlobal(id: String, enabled: Boolean) = skillDao.setEnabledGlobal(id, enabled, now())

    suspend fun delete(id: String) {
        bindingDao.deleteBySkill(id)
        skillDao.deleteById(id)
    }

    suspend fun bindingsFor(assistantId: String): List<SkillBindingEntity> = bindingDao.getByAssistant(assistantId)

    suspend fun setBinding(skillId: String, assistantId: String, enabled: Boolean) =
        bindingDao.upsert(SkillBindingEntity(skillId, assistantId, enabled))

    /**
     * 装配注入文档：全局启用（按更新时间升序）→ 助手绑定启用，组内保持顺序；同名去重。
     */
    suspend fun documentsFor(assistantId: String): List<SkillDocument> {
        val global = runCatching { skillDao.getGloballyEnabled() }.getOrDefault(emptyList())
        val bound = runCatching { bindingDao.getEnabledForAssistant(assistantId) }.getOrDefault(emptyList())
        val seen = mutableSetOf<String>()
        val out = mutableListOf<SkillDocument>()
        global.forEach { entity ->
            if (seen.add(entity.name)) out += entity.toDocument(SkillScope.GLOBAL)
        }
        bound.forEach { entity ->
            if (seen.add(entity.name)) out += entity.toDocument(SkillScope.ASSISTANT)
        }
        return out
    }

    private fun SkillEntity.toDocument(scope: SkillScope) = SkillDocument(
        name = name,
        body = body,
        description = description.ifBlank { null },
        scope = scope,
    )

    companion object {
        const val BUILTIN_DIR: String = "assistant/skills"
        private const val MAX_URL_BYTES: Int = 256 * 1024
        private const val USER_AGENT: String = "LuzzyRP/1.5.0 (+assistant skill import)"
        private val client: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
}
