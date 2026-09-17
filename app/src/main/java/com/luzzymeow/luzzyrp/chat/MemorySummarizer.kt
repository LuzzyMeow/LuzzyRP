package com.luzzymeow.luzzyrp.chat

import com.luzzymeow.luzzyrp.data.legacy.ScopeId
import com.luzzymeow.luzzyrp.data.store.LuzzyStore
import com.luzzymeow.luzzyrp.chat.llm.LlmMessage
import com.luzzymeow.luzzyrp.chat.llm.LlmRole
import com.luzzymeow.luzzyrp.chat.llm.LlmRequest
import com.luzzymeow.luzzyrp.chat.llm.LlmTransport
import com.luzzymeow.luzzyrp.chat.llm.OpenAiTransport
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject

/**
 * 记忆**自动总结**（v3.2，补上「产出半边」）。
 *
 * ## 机制（对齐旧版口径，如实取舍）
 *
 * 每完成 [THRESHOLD] 个**新轮次**，取最近 [WINDOW] 条可见消息让总结模型生成一段
 * 第三人称高密度记忆，写入 `classic` 组（`MemoryBrowser.CLASSIC`，正文键 `summary`）。
 * 与上游「增强模式」的差异（如实登记）：上游还把用户原输入与总结**生成向量**做混合召回；
 * 我们召回是词面口径（[RecallEngine]，如实登记过），所以总结写库后由既有召回直接受益，
 * 不引入嵌入。
 *
 * ## 失败 = 静默降级
 *
 * 总结是**后台优化**：任何失败（未配置/网络错/空响应）只记日志、绝不打断对话，
 * 也不弹任何界面提示——用户不该为一条后台记忆买单次打扰。
 *
 * ## 复用纪律（ponytail 阶梯：已在本代码库的不重写）
 *
 * 传输复用 [OpenAiTransport]（与 [AgentLoop] 同一条请求管线）；配置复用对话的
 * [TransportConfig]（总结与对话同供应商同模型——用户已配好的那条）；存储复用
 * [LuzzyStore.replaceMemories]。新增的只有：触发计数、提示词、classic 条目落库。
 */
class MemorySummarizer(
    /** 存储门面（只有 [summarizeAndStore] 碰它；纯函数调用方可用 null 构造，JVM 单测场景）。 */
    private val store: LuzzyStore?,
    private val transport: LlmTransport = OpenAiTransport(),
) {

    /** 设置（kv 持久化，记忆页引擎卡开关）。 */
    data class Settings(
        val enabled: Boolean = true,
        /** 每多少个新轮次总结一次。 */
        val everyTurns: Int = 5,
    ) {
        fun toJson(): JsonObject = buildJsonObject {
            put("enabled", JsonPrimitive(enabled))
            put("everyTurns", JsonPrimitive(everyTurns))
        }

        companion object {
            fun from(element: kotlinx.serialization.json.JsonElement?): Settings {
                val obj = element as? JsonObject ?: return Settings()
                val enabled = (obj["enabled"] as? JsonPrimitive)?.booleanOrNull ?: true
                val every = (obj["everyTurns"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 5
                return Settings(enabled = enabled, everyTurns = every.coerceIn(2, 20))
            }
        }
    }

    /**
     * 一轮完成后的**触发检查**（纯函数，可 JVM 单测）。
     *
     * @param completedTurns 本分支累计完成的用户轮数（本次含内）
     * @return 应总结时返回「本轮要覆盖到的轮次号」，否则 null
     */
    fun shouldSummarize(settings: Settings, completedTurns: Int, lastCoveredTurn: Int): Int? {
        if (!settings.enabled) return null
        if (completedTurns < settings.everyTurns) return null
        val due = completedTurns / settings.everyTurns * settings.everyTurns
        return if (due > lastCoveredTurn) due else null
    }

    /**
     * 生成总结并写入 classic（**失败静默**：返回 null 并记日志，不抛）。
     *
     * @param turns 最近可见消息（`发言者 to 正文`），调用方保证非空
     * @param upToTurn 本条记忆覆盖到第几轮（落 `turn` 字段，与旧数据同口径）
     */
    suspend fun summarizeAndStore(
        scope: ScopeId,
        turns: List<Pair<String, String>>,
        upToTurn: Int,
        config: TransportConfig,
        characterName: String,
    ): String? {
        if (!config.configured || turns.isEmpty()) return null
        val transcript = turns.joinToString("\n") { (speaker, text) ->
            "$speaker: ${text.take(MAX_LINE_CHARS)}"
        }.take(MAX_TRANSCRIPT_CHARS)
        val request = LlmRequest(
            messages = listOf(
                LlmMessage(
                    role = LlmRole.SYSTEM,
                    content = "你是记忆归档员。把以下对话浓缩成第三人称高密度记忆要点（$characterName 与用户的" +
                        "关系变化、承诺、地点、物品、未决之事），只输出要点本身，不超过 200 字，" +
                        "不写任何前言或客套。",
                ),
                LlmMessage(role = LlmRole.USER, content = transcript),
            ),
            protocol = AgentLoop.Protocol,
            baseUrl = config.chatEndpoint(),
            apiKey = config.apiKey,
            model = config.model,
            temperature = config.temperature,
            maxTokens = config.maxTokens,
            stream = true,
            // 总结请求**不发工具**：模型不需要查世界书，tools 差异也不影响任何已存前缀
            // （本请求的 messages 与对话前缀完全不同，本来就不会命中对话的缓存纪元）
            tools = emptyList(),
        )
        val summary = StringBuilder()
        var error: String? = null
        try {
            transport.stream(request).collect { delta ->
                delta.content?.takeIf { it.isNotEmpty() }?.let { summary.append(it) }
                delta.error?.let { error = it.message }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            android.util.Log.w(TAG, "记忆总结请求失败（静默降级）", e)
            return null
        }
        val text = summary.toString().trim()
        if (error != null || text.isEmpty()) {
            android.util.Log.w(TAG, "记忆总结未产出（${error ?: "空响应"}），静默跳过")
            return null
        }
        val s = store ?: run { android.util.Log.w(TAG, "store 缺失，总结丢弃"); return null }
        s.replaceMemories(scope, MemoryBrowser.CLASSIC, s.memories(scope, MemoryBrowser.CLASSIC) + payloadOf(text, upToTurn, config.model))
        android.util.Log.i(TAG, "记忆总结落库 turn≤$upToTurn ${text.length} 字")
        return text
    }

    /** classic 条目 payload（字段口径对齐旧数据：`summary`/`turn`/`id`/来源模型）。 */
    private fun payloadOf(text: String, turn: Int, model: String) = buildJsonObject {
        put("id", JsonPrimitive("classic-${System.currentTimeMillis()}"))
        put("summary", JsonPrimitive(text))
        put("turn", JsonPrimitive(turn))
        put("enabled", JsonPrimitive(true))
        put("sourceRole", JsonPrimitive("assistant"))
        put("sourceName", JsonPrimitive(model))
        put("chunkMode", JsonPrimitive("auto"))
    }

    companion object {
        private const val TAG = "LuzzyMemory"

        /** 触发阈值：每 N 轮总结一次（默认，UI 可调 2-20）。 */
        const val THRESHOLD = 5

        /** 每条输入行截断（防单条超长消息把提示词撑爆）。 */
        const val MAX_LINE_CHARS = 400

        /** 对话稿总长上限。 */
        const val MAX_TRANSCRIPT_CHARS = 6000

        /** 设置的 kv 键（全局一份；阈值不按角色区分——记忆页是全局引擎卡）。 */
        const val SETTINGS_KEY = "memory.autoSummary"
    }
}