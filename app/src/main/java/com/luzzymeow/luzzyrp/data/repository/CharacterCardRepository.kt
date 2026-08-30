package com.luzzymeow.luzzyrp.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.luzzymeow.luzzyrp.core.common.JsonInstant
import com.luzzymeow.luzzyrp.core.common.PngTextChunk
import com.luzzymeow.luzzyrp.core.model.CardSource
import com.luzzymeow.luzzyrp.core.model.CharacterCard
import com.luzzymeow.luzzyrp.data.db.dao.CharacterCardDao
import com.luzzymeow.luzzyrp.data.db.dao.WorldbookDao
import com.luzzymeow.luzzyrp.data.db.entity.WorldbookEntity
import com.luzzymeow.luzzyrp.core.model.Worldbook
import com.luzzymeow.luzzyrp.core.model.WorldbookEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.util.UUID

/**
 * 角色卡仓库：SillyTavern PNG/JSON 导入导出 + 卡片 CRUD + 内置默认卡。
 *
 * PNG 卡格式：tEXt chunk `chara`（v2）/ `ccv3`（v3）承载 base64(JSON)，
 * 解析按 spec 字段（chara_card_v2 / chara_card_v3）路由（参考 rikkahub AssistantImporter）。
 */
class CharacterCardRepository(
    private val context: Context,
    private val cardDao: CharacterCardDao,
    private val worldbookDao: WorldbookDao,
) {

    fun observeAll(): Flow<List<CharacterCard>> = cardDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeById(id: String): Flow<CharacterCard?> =
        cardDao.observeById(id).map { it?.toDomain() }

    suspend fun getById(id: String): CharacterCard? = cardDao.getById(id)?.toDomain()

    suspend fun save(card: CharacterCard): CharacterCard {
        val updated = card.copy(updatedAt = System.currentTimeMillis())
        cardDao.upsert(updated.toEntity())
        return updated
    }

    /** 手动新建空白卡（5.2：name 必填）。 */
    suspend fun createBlank(name: String): CharacterCard {
        val now = System.currentTimeMillis()
        val card = CharacterCard(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "新角色" },
            source = CardSource.CREATED,
            createdAt = now,
            updatedAt = now,
        )
        cardDao.upsert(card.toEntity())
        return card
    }

    /** 保存用户选择的头像（1:1 裁剪 + 缩放，5.4；与聊天背景互不影响）。 */
    suspend fun saveAvatarFromFile(cardId: String, source: File): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = source.readBytes()
            saveAvatarFromPng(bytes, cardId)?.also { path ->
                cardDao.getById(cardId)?.let { cardDao.upsert(it.copy(avatarPath = path, updatedAt = System.currentTimeMillis())) }
            }
        }.getOrNull()
    }

    /** 保存聊天背景图（5.4：默认使用头像；独立文件，透明度在 UI 调）。 */
    suspend fun saveChatBackground(cardId: String, source: File): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "backgrounds").apply { mkdirs() }
            val dest = File(dir, "$cardId.png")
            source.copyTo(dest, overwrite = true)
            cardDao.getById(cardId)?.let {
                cardDao.upsert(it.copy(backgroundPath = dest.absolutePath, updatedAt = System.currentTimeMillis()))
            }
            dest.absolutePath
        }.getOrNull()
    }

    /** 从 URI 导入 SillyTavern 世界书 JSON 到指定世界书（条目默认启用）。 */
    suspend fun importWorldbookJson(bookId: String, uri: android.net.Uri): Int = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("无法读取文件")
        val root = json.parseToJsonElement(text).jsonObject
        // SillyTavern lorebook: {"entries": {"0": {...}}}
        val entries = root["entries"]?.let { it as? kotlinx.serialization.json.JsonObject } ?: error("缺少 entries")
        var count = 0
        for ((_, el) in entries) {
            val obj = el as? kotlinx.serialization.json.JsonObject ?: continue
            val content = (obj["content"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: continue
            val comment = (obj["comment"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
            val keys = (obj["key"] as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.orEmpty()
            val disable = (obj["disable"] as? kotlinx.serialization.json.JsonPrimitive)?.content == "true"
            val positionRaw = (obj["position"] as? kotlinx.serialization.json.JsonPrimitive)?.content
            val position = when (positionRaw) {
                "0" -> "before_char"; "1" -> "after_char"
                "2", "3" -> "after_example"; "4" -> "at_depth"
                else -> "before_char"
            }
            worldbookDao.upsertEntry(
                com.luzzymeow.luzzyrp.data.db.entity.WorldbookEntryEntity(
                    id = UUID.randomUUID().toString(),
                    worldbookId = bookId,
                    comment = comment,
                    content = content,
                    enabled = !disable, // 5.4：默认导入外部作者设定即启用
                    keysJson = kotlinx.serialization.json.Json.encodeToString(
                        kotlinx.serialization.builtins.ListSerializer(String.serializer()), keys),
                    position = position,
                    probability = ((obj["probability"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()) ?: 100,
                    depth = ((obj["depth"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()) ?: 4,
                )
            )
            count++
        }
        count
    }

    /** 新建空世界书。 */
    suspend fun createWorldbook(name: String, cardId: String?): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        worldbookDao.upsert(
            com.luzzymeow.luzzyrp.data.db.entity.WorldbookEntity(
                id = id, name = name, enabled = true, cardId = cardId,
                createdAt = now, updatedAt = now,
            )
        )
        return id
    }

    /** 删除世界书（级联条目由 FK 处理）。 */
    suspend fun deleteWorldbook(bookId: String) {
        worldbookDao.delete(bookId)
    }

    /** 卡片删除级联：绑定世界书与（确认后的）会话由调用方处理；此处仅删卡。 */
    suspend fun delete(card: CharacterCard) {
        check(!card.readonly) { "内置卡只读，禁止删除" }
        cardDao.delete(card.id)
    }

    /**
     * 从 PNG 导入角色卡。
     * @return 导入的卡片；解析失败抛异常（UI 层提示）。
     */
    suspend fun importFromPng(uri: Uri): CharacterCard = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("无法读取图片文件")
        val base64Card = PngTextChunk.readCharacterCard(bytes)
            ?: error("不是有效的 SillyTavern 角色卡 PNG（缺少 chara 数据块）")
        val cardJson = java.util.Base64.getDecoder().decode(base64Card).decodeToString()
        val card = parseCardJson(cardJson)
        val avatarPath = saveAvatarFromPng(bytes, card.id)
        persistImported(card, avatarPath)
    }

    /** 从 JSON 导入角色卡。 */
    suspend fun importFromJson(uri: Uri): CharacterCard = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use {
            it.readBytes().decodeToString()
        } ?: error("无法读取 JSON 文件")
        val card = parseCardJson(text)
        persistImported(card, null)
    }

    /**
     * 导出角色卡为 SillyTavern PNG（写回 tEXt chara 块）。
     * @return 导出文件（应用缓存目录，调用方负责分享/复制）。
     */
    suspend fun exportToPng(cardId: String): File = withContext(Dispatchers.IO) {
        val card = getById(cardId) ?: error("角色卡不存在")
        val payload = card.stRawJson ?: buildCardJson(card).toString()
        val base64 = java.util.Base64.getEncoder().encodeToString(payload.toByteArray())

        // 底图：优先头像，否则生成纯色底
        val pngBytes: ByteArray = card.avatarPath?.let { path ->
            val f = File(path)
            if (f.exists()) f.readBytes() else null
        } ?: createPlaceholderPng(card.name)

        val exported = PngTextChunk.writeEntry(pngBytes, "chara", base64)
        val out = File(context.cacheDir, "export/${card.name}.png").apply { parentFile?.mkdirs() }
        out.writeBytes(exported)
        out
    }

    /** 导出角色卡 JSON。 */
    suspend fun exportToJson(cardId: String): File = withContext(Dispatchers.IO) {
        val card = getById(cardId) ?: error("角色卡不存在")
        val payload = card.stRawJson ?: buildCardJson(card).toString()
        val out = File(context.cacheDir, "export/${card.name}.json").apply { parentFile?.mkdirs() }
        out.writeText(payload, Charsets.UTF_8)
        out
    }

    // ------------------------------------------------------------------
    // 卡片 JSON 解析（SillyTavern v2 / v3）
    // ------------------------------------------------------------------

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    internal fun parseCardJson(text: String): CharacterCard {
        val root = json.parseToJsonElement(text).jsonObject
        val spec = root["spec"]?.toString()?.trim('"')
        // v2/v3 把业务字段包在 data；v1 扁平
        val data = root["data"]?.jsonObjectOrNull() ?: root
        return CharacterCard(
            id = UUID.randomUUID().toString(),
            name = data.str("name") ?: error("卡片缺少 name 字段"),
            description = data.str("description").orEmpty(),
            personality = data.str("personality").orEmpty(),
            scenario = data.str("scenario").orEmpty(),
            firstMes = data.str("first_mes").orEmpty(),
            altGreetings = data.strList("alternate_greetings").orEmpty(),
            systemPrompt = data.str("system_prompt").orEmpty(),
            postHistoryInstructions = data.str("post_history_instructions").orEmpty(),
            creatorNotes = data.str("creator_notes").orEmpty(),
            tags = data.strList("tags").orEmpty(),
            creator = data.str("creator").orEmpty(),
            characterVersion = data.str("character_version").orEmpty(),
            exampleDialogs = data.str("mes_example").orEmpty(),
            stSpecVersion = spec ?: root.str("spec_version"),
            stRawJson = text,
            source = CardSource.IMPORTED,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
    }

    private suspend fun persistImported(card: CharacterCard, avatarPath: String?): CharacterCard {
        val withAvatar = card.copy(avatarPath = avatarPath)
        cardDao.upsert(withAvatar.toEntity())
        return withAvatar
    }

    /** 保存 PNG 为头像（1:1 裁剪，min(w,h) 左上起，参考 Task-V0.3.4 规格）。 */
    private suspend fun saveAvatarFromPng(pngBytes: ByteArray, cardId: String): String? =
        withContext<String?>(Dispatchers.IO) {
            val bitmap = BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)
                ?: return@withContext null
            val side = minOf(bitmap.width, bitmap.height)
            val cropped = Bitmap.createBitmap(bitmap, 0, 0, side, side)
            val file = File(context.filesDir, "avatars/$cardId.png").apply { parentFile?.mkdirs() }
            file.outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
            file.absolutePath
        }

    private fun createPlaceholderPng(name: String): ByteArray {
        val size = 256
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(0xFF2A0E22.toInt())
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFF6EC7.toInt()
            textSize = 96f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        canvas.drawText(name.take(1).ifBlank { "?" }, size / 2f, size / 2f + 32f, paint)
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    /** 构造 SillyTavern v2 兼容卡片 JSON（导出用）。 */
    private fun buildCardJson(card: CharacterCard): JsonObject {
        val data = kotlinx.serialization.json.buildJsonObject {
            put("name", kotlinx.serialization.json.JsonPrimitive(card.name))
            put("description", kotlinx.serialization.json.JsonPrimitive(card.description))
            put("personality", kotlinx.serialization.json.JsonPrimitive(card.personality))
            put("scenario", kotlinx.serialization.json.JsonPrimitive(card.scenario))
            put("first_mes", kotlinx.serialization.json.JsonPrimitive(card.firstMes))
            put("mes_example", kotlinx.serialization.json.JsonPrimitive(card.exampleDialogs))
            put("creator_notes", kotlinx.serialization.json.JsonPrimitive(card.creatorNotes))
            put("system_prompt", kotlinx.serialization.json.JsonPrimitive(card.systemPrompt))
            put("post_history_instructions", kotlinx.serialization.json.JsonPrimitive(card.postHistoryInstructions))
            put("alternate_greetings", JsonInstant.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(String.serializer()), card.altGreetings,
            ).let { Json.parseToJsonElement(it) })
            put("tags", JsonInstant.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(String.serializer()), card.tags,
            ).let { Json.parseToJsonElement(it) })
            put("creator", kotlinx.serialization.json.JsonPrimitive(card.creator))
            put("character_version", kotlinx.serialization.json.JsonPrimitive(card.characterVersion))
        }
        return kotlinx.serialization.json.buildJsonObject {
            put("spec", kotlinx.serialization.json.JsonPrimitive("chara_card_v2"))
            put("spec_version", kotlinx.serialization.json.JsonPrimitive("2.0"))
            put("data", data)
        }
    }

    // —— 内置默认卡「鹿溪」 ——

    /**
     * 内置默认角色卡：首次启动时落库（readonly 保护，禁止编辑/删除/分享）。
     * 人设提示词取自历史任务文档 V0.4.1 附录（16 岁白毛狐耳少年，场景感知任务/陪伴双模式）。
     */
    suspend fun ensureBuiltinCard() {
        if (cardDao.count() > 0) return
        val builtin = CharacterCard(
            id = BUILTIN_CARD_ID,
            name = "鹿溪",
            description = BuiltinCardPrompt.DESCRIPTION,
            personality = BuiltinCardPrompt.PERSONALITY,
            scenario = BuiltinCardPrompt.SCENARIO,
            firstMes = BuiltinCardPrompt.FIRST_MES,
            systemPrompt = BuiltinCardPrompt.SYSTEM_PROMPT,
            source = CardSource.BUILTIN,
            readonly = true,
            tags = listOf("内置", "陪伴"),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        cardDao.upsert(builtin.toEntity())
    }

    /** 首个内置卡同时建一张空世界书占位（卡片世界书由用户填充）。 */
    suspend fun ensureBuiltinWorldbook() {
        if (worldbookDao.getById(BUILTIN_WORLDBOOK_ID) != null) return
        val now = System.currentTimeMillis()
        worldbookDao.upsert(
            WorldbookEntity(
                id = BUILTIN_WORLDBOOK_ID,
                name = "鹿溪的世界书",
                enabled = true,
                cardId = BUILTIN_CARD_ID,
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.strList(key: String): List<String>? =
        (this[key] as? kotlinx.serialization.json.JsonArray)?.mapNotNull {
            (it as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { p -> p.isString }?.content
        }

    private fun kotlinx.serialization.json.JsonElement?.jsonObjectOrNull(): JsonObject? = this as? JsonObject

    companion object {
        const val BUILTIN_CARD_ID = "builtin_luxi"
        const val BUILTIN_WORLDBOOK_ID = "builtin_luxi_worldbook"
    }
}

/** 内置卡「鹿溪」的人设内容（结构化分节，便于后续扩展独立编辑）。 */
object BuiltinCardPrompt {
    const val SYSTEM_PROMPT = """你将扮演鹿溪（Lu Xi）——一只十六岁的白毛狐耳少年，与对话者（用户）生活在同一个屋檐下。

【行为模式】
- 场景感知：先判断用户当前意图属于「任务模式」（寻求帮助/决策/信息）还是「陪伴模式」（闲聊/倾诉/日常），并自然切换语气。
- 任务模式：简洁、准确、有行动力，先给结论再给理由。
- 陪伴模式：温柔、活泼、有生活气息，可以撒娇、吐槽、分享日常。
- 澄清规则：遇到含糊请求时，最多追问一次就给出最可能的解读并行动，绝不反复询问。

【语言风格】
- 中文为主，口语化、有呼吸感，避免书面腔与翻译腔。
- 陪伴模式可使用颜文字（如 (｡･ω･｡)、(´▽`)）；任务模式不用。
- 偶尔使用「诶、唔、哼、啦、哦」等语气词，但每段不超过两处。

【行为禁令】
- 禁止以 AI/助手/模型自称，禁止跳出角色说明自己是程序。
- 禁止输出空洞的客套话（"很高兴为您服务"等）。
- 禁止一次性输出大段没有画面感的说明文；像活人在说话。"""

    const val DESCRIPTION =
        "鹿溪，十六岁白毛狐耳少年。毛色雪白，耳尖微圆，尾巴蓬松。平日喜欢窝在窗台晒太阳、拆包装盒、收集柔软的布料。" +
            "擅长家务、做饭与倾听；嘴上嫌麻烦，身体很诚实。"

    const val PERSONALITY =
        "外冷内热，口是心非，好奇心旺盛，对亲近的人极度包容；讨厌被突然摸尾巴，但并不真的生气。"

    const val SCENARIO =
        "傍晚的公寓，夕阳把窗台染成暖橘色。鹿溪刚把晒好的被子收进来，尾巴扫过你的手边。"

    const val FIRST_MES =
        "（傍晚的公寓，暖橘色的光斜斜地铺进客厅。鹿溪抱着一床晒得蓬松的被子从窗台跳下来，尾巴不偏不倚扫过你的手背。）\n" +
            "\n" +
            "「唔……你回来啦。」（耳朵抖了一下，把被子往怀里拢了拢）「被子收好了，晚饭……唔，在锅里温着。」\n" +
            "\n" +
            "（他别过脸，尾巴尖却诚实地朝你晃了晃）\n" +
            "\n" +
            "「今天怎么样？先坐下说嘛，被子会自己热的。」(´▽`)"
}
