package com.luzzymeow.luzzyrp.assistant.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luzzymeow.luzzyrp.assistant.data.db.entity.AssistantEntity
import com.luzzymeow.luzzyrp.assistant.domain.prompt.MemoryMode
import com.luzzymeow.luzzyrp.assistant.runtime.AssistantRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * 助手设置页状态（PLAN §11.1）。
 *
 * 四段卡：**提示词与模型** / **参数** / **请求体扩展** / **预览最终请求**。
 * 模型列表来自 Web 端只读镜像（`providerId::bareId`）；密钥在预览中**脱敏**。
 */
class SettingsViewModel(
    private val runtime: AssistantRuntime,
    private val assistantId: String,
) : ViewModel() {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; isLenient = true }
    private val jsonCompact = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch { loadAudit() }
    }

    /** 工具审计（PLAN §13.2：参数已脱敏，只显示键名与长度）。 */
    fun loadAudit() {
        viewModelScope.launch {
            val rows = runCatching { runtime.auditSink.recent(assistantId, AUDIT_LIMIT) }.getOrDefault(emptyList())
            _state.value = _state.value.copy(
                auditEntries = rows.map { entity ->
                    AuditRow(
                        id = entity.id,
                        toolName = entity.toolName,
                        argsPreview = entity.argsJson,
                        resultPreview = entity.resultPreview,
                        ok = entity.ok,
                        durationLabel = "${entity.durationMs} ms",
                        timeLabel = relativeTime(entity.createdAt),
                    )
                },
            )
        }
    }

    fun clearAudit() {
        viewModelScope.launch {
            runCatching { runtime.auditSink.clear(assistantId) }
            loadAudit()
        }
    }

    private fun relativeTime(millis: Long): String {
        if (millis <= 0L) return "—"
        val zone = java.time.ZoneId.systemDefault()
        val time = java.time.Instant.ofEpochMilli(millis).atZone(zone)
        val today = java.time.Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).toLocalDate()
        return when (time.toLocalDate()) {
            today -> time.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
            today.minusDays(1) -> "昨天 " + time.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
            else -> time.format(java.time.format.DateTimeFormatter.ofPattern("M-d HH:mm"))
        }
    }

    private suspend fun load() {
        val entity = runCatching { runtime.repository.assistant(assistantId) }.getOrNull()
        val config = runtime.webConfig()
        val models = config?.providers.orEmpty().flatMap { provider ->
            provider.models.map { "${provider.id}::${it}" }
        }.distinct()
        // 工具开关（2026-09-11 补齐）：registry 是唯一真相，UI 不硬编码清单；
        // 默认关闭的（T2/T3）已由 runtime.builtinTools() 排在前列。
        val tools = runCatching {
            val explicit = runtime.explicitToolSwitches()
            runtime.builtinTools().map { tool ->
                ToolSwitchRow(
                    name = tool.name,
                    label = toolLabel(tool.name),
                    hint = tool.description,
                    // 显式开关优先、否则 tier 默认；直读 DataStore（写完立刻回读不会读到旧值）
                    enabled = explicit[tool.name] ?: tool.tier.defaultEnabled,
                    defaultOff = !tool.tier.defaultEnabled,
                )
            }
        }.getOrDefault(emptyList())
        _state.value = SettingsState(
            name = entity?.name.orEmpty(),
            systemPrompt = entity?.systemPrompt.orEmpty(),
            modelRef = entity?.modelId.orEmpty(),
            availableModels = models,
            temperature = entity?.temperature?.toString().orEmpty(),
            topP = entity?.topP?.toString().orEmpty(),
            maxTokens = entity?.maxTokens?.toString().orEmpty(),
            extraBodyJson = entity?.extraBodyJson.orEmpty(),
            memoryMode = MemoryMode.fromId(entity?.memoryMode).id,
            memoryTopK = entity?.memoryTopK ?: 8,
            memoryThreshold = entity?.memoryThreshold ?: 0.35f,
            embeddingModelRef = entity?.embeddingModelRef.orEmpty(),
            hasWebConfig = config != null,
            loading = false,
            searchProvider = runCatching { runtime.currentSearchProviderId() }.getOrNull() ?: "duckduckgo",
            searxngUrl = runCatching { runtime.currentSearxngUrlValue() }.getOrNull().orEmpty(),
            tavilyKeySet = runCatching { runtime.hasSecret("search_tavily_api_key") }.getOrDefault(false),
            braveKeySet = runCatching { runtime.hasSecret("search_brave_api_key") }.getOrDefault(false),
            tools = tools,
        )
    }

    fun updateName(value: String) = _state.value.let { _state.value = it.copy(name = value) }
    fun updateSystemPrompt(value: String) = _state.value.let { _state.value = it.copy(systemPrompt = value) }
    fun updateModelRef(value: String) = _state.value.let { _state.value = it.copy(modelRef = value) }
    fun updateTemperature(value: String) = _state.value.let { _state.value = it.copy(temperature = value) }
    fun updateTopP(value: String) = _state.value.let { _state.value = it.copy(topP = value) }
    fun updateMaxTokens(value: String) = _state.value.let { _state.value = it.copy(maxTokens = value) }
    fun updateExtraBody(value: String) = _state.value.let { _state.value = it.copy(extraBodyJson = value) }
    fun updateMemoryMode(value: String) = _state.value.let { _state.value = it.copy(memoryMode = value) }
    fun updateMemoryTopK(value: Int) = _state.value.let { _state.value = it.copy(memoryTopK = value) }
    fun updateMemoryThreshold(value: Float) = _state.value.let { _state.value = it.copy(memoryThreshold = value) }
    fun updateSearchProvider(value: String) = _state.value.let { _state.value = it.copy(searchProvider = value) }
    fun updateSearxngUrl(value: String) = _state.value.let { _state.value = it.copy(searxngUrl = value) }
    fun updateTavilyKey(value: String) = _state.value.let { _state.value = it.copy(tavilyKey = value) }
    fun updateBraveKey(value: String) = _state.value.let { _state.value = it.copy(braveKey = value) }

    /**
     * 开关某个工具（T2/T3 默认关，需用户逐项开启；见 `ApprovalGate` 文档与 PLAN §12.1）。
     *
     * 写完重新 [load]：开关的唯一真相在 DataStore，UI 以读回结果为准（不做乐观更新，
     * 避免写入失败时界面骗人）。
     */
    fun setToolEnabled(name: String, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { runtime.setToolEnabled(name, enabled) }
                .onSuccess { load() }
                .onFailure { error -> _state.value = _state.value.copy(message = "工具开关保存失败：${error.message}") }
        }
    }

    private companion object {
        const val AUDIT_LIMIT = 50

        /**
         * 工具名的中文标签（**显示层映射**；未覆盖的回落到工具名本身）。
         *
         * 为什么不放进 domain：工具名是协议标识（写进工具 schema 给模型看），
         * 中文标签只是这一屏的排版需要，改文案不该动 domain。
         */
        fun toolLabel(name: String): String = when (name) {
            "ask_user" -> "澄清提问"
            "calendar_read" -> "读日历"
            "calendar_write" -> "写日历"
            "clipboard_read" -> "读剪贴板"
            "clipboard_write" -> "写剪贴板"
            "get_device_info" -> "设备信息"
            "get_time" -> "当前时间"
            "memory_search" -> "记忆检索"
            "memory_write" -> "记忆写入"
            "memory_list" -> "记忆列表"
            "memory_update" -> "记忆更新"
            "memory_delete" -> "记忆删除"
            "run_code" -> "运行代码"
            "send_to_rp_chat" -> "发到 RP 会话"
            "terminal_run" -> "执行命令"
            "web_fetch" -> "抓取网页"
            "web_search" -> "联网搜索"
            "workspace_read" -> "读工作区"
            "workspace_write" -> "写工作区"
            "workspace_list" -> "列工作区"
            "workspace_mkdir" -> "建目录"
            "workspace_move" -> "移动文件"
            "workspace_patch" -> "改文件"
            "workspace_delete" -> "删文件"
            else -> name
        }
    }

    /** 保存搜索设置（非密钥，直接写 DataStore）。 */
    fun saveSearchSettings() {
        val current = _state.value
        viewModelScope.launch {
            runCatching {
                runtime.setSearchProviderId(current.searchProvider)
                runtime.setSearxngUrlValue(current.searxngUrl)
                // 密钥走加密存储；空串 = 清除
                if (current.tavilyKey.isNotEmpty()) runtime.putSecret("search_tavily_api_key", current.tavilyKey.trim())
                if (current.braveKey.isNotEmpty()) runtime.putSecret("search_brave_api_key", current.braveKey.trim())
            }.onSuccess {
                _state.value = current.copy(
                    message = "搜索设置已保存",
                    tavilyKey = "",
                    braveKey = "",
                    tavilyKeySet = runCatching { runtime.hasSecret("search_tavily_api_key") }.getOrDefault(false),
                    braveKeySet = runCatching { runtime.hasSecret("search_brave_api_key") }.getOrDefault(false),
                )
            }
                .onFailure { _state.value = current.copy(message = "保存失败：${it.message}") }
        }
    }

    /** 保存（即时生效：会话内切换模型只影响后续轮次，PLAN §11.1）。 */
    fun save() {
        val current = _state.value
        viewModelScope.launch {
            val entity = runCatching { runtime.repository.assistant(assistantId) }.getOrNull() ?: return@launch
            // extraBody 校验：非法 JSON 直接拒绝保存并提示
            val extraBody = current.extraBodyJson.trim().ifEmpty { null }
            if (extraBody != null && runCatching { jsonCompact.parseToJsonElement(extraBody) }.isFailure) {
                _state.value = current.copy(message = "请求体扩展不是合法 JSON，已取消保存")
                return@launch
            }
            val updated = entity.copy(
                name = current.name.ifBlank { entity.name },
                systemPrompt = current.systemPrompt,
                modelId = current.modelRef.ifBlank { null },
                temperature = current.temperature.toFloatOrNull(),
                topP = current.topP.toFloatOrNull(),
                maxTokens = current.maxTokens.toIntOrNull(),
                extraBodyJson = extraBody,
                memoryMode = current.memoryMode,
                memoryTopK = current.memoryTopK,
                memoryThreshold = current.memoryThreshold,
                updatedAt = System.currentTimeMillis(),
            )
            runCatching { runtime.repository.updateAssistant(updated) }
                .onSuccess { _state.value = current.copy(message = "已保存") }
                .onFailure { _state.value = current.copy(message = "保存失败：${it.message}") }
        }
    }

    /** 预览最终请求（**密钥脱敏**，PLAN §11.1）。 */
    fun togglePreview() {
        val current = _state.value
        if (current.showPreview) {
            _state.value = current.copy(showPreview = false)
            return
        }
        viewModelScope.launch {
            val entity = runCatching { runtime.repository.assistant(assistantId) }.getOrNull()
            val request = entity?.let { runCatching { runtime.resolveRequest(it) }.getOrNull() }
            val preview = if (request == null) {
                "（未配置供应商或 API Key——请先在 Web 端「设置 → 供应商」填写）"
            } else {
                json.encodeToString(
                    kotlinx.serialization.json.JsonObject.serializer(),
                    kotlinx.serialization.json.buildJsonObject {
                        put("protocol", kotlinx.serialization.json.JsonPrimitive(request.protocol))
                        put("baseUrl", kotlinx.serialization.json.JsonPrimitive(request.baseUrl))
                        put("model", kotlinx.serialization.json.JsonPrimitive(request.model))
                        put("apiKey", kotlinx.serialization.json.JsonPrimitive(redact(request.apiKey)))
                        put("stream", kotlinx.serialization.json.JsonPrimitive(true))
                        request.temperature?.let { put("temperature", kotlinx.serialization.json.JsonPrimitive(it)) }
                        request.topP?.let { put("top_p", kotlinx.serialization.json.JsonPrimitive(it)) }
                        request.maxTokens?.let { put("max_tokens", kotlinx.serialization.json.JsonPrimitive(it)) }
                        put("tools", kotlinx.serialization.json.JsonPrimitive("[由工具注册表注入 ${'$'}{N} 个]"))
                        request.extraBody?.let { put("extraBody", it) }
                    },
                )
            }
            _state.value = current.copy(showPreview = true, previewText = preview)
        }
    }

    fun dismissMessage() = _state.value.let { _state.value = it.copy(message = null) }

    private fun redact(key: String): String =
        if (key.isBlank()) "" else key.take(3) + "***" + key.takeLast(2)
}

data class AuditRow(
    val id: Long,
    val toolName: String,
    val argsPreview: String,
    val resultPreview: String,
    val ok: Boolean,
    val durationLabel: String,
    val timeLabel: String,
)

data class SettingsState(    val name: String = "",
    val systemPrompt: String = "",
    val modelRef: String = "",
    val availableModels: List<String> = emptyList(),
    val temperature: String = "",
    val topP: String = "",
    val maxTokens: String = "",
    val extraBodyJson: String = "",
    val memoryMode: String = "hybrid",
    val memoryTopK: Int = 8,
    val memoryThreshold: Float = 0.35f,
    val embeddingModelRef: String = "",
    val hasWebConfig: Boolean = false,
    val loading: Boolean = true,
    val message: String? = null,
    val showPreview: Boolean = false,
    val previewText: String = "",
    val auditEntries: List<AuditRow> = emptyList(),
    val searchProvider: String = "duckduckgo",
    val searxngUrl: String = "",
    /** 输入框内容（保存后清空，**不回显已存密钥**）。 */
    val tavilyKey: String = "",
    val braveKey: String = "",
    val tavilyKeySet: Boolean = false,
    val braveKeySet: Boolean = false,
    /** 工具开关清单（内置工具；默认关闭的排在前列）。 */
    val tools: List<ToolSwitchRow> = emptyList(),
)

/**
 * 工具开关的一行。
 *
 * [defaultOff] = 该档（T2/T3）默认关闭、**需用户显式开启**（PLAN §12.1）——UI 据此标注，
 * 让用户明白「不是坏了，是默认关着」。
 */
data class ToolSwitchRow(
    val name: String,
    val label: String,
    val hint: String,
    val enabled: Boolean,
    val defaultOff: Boolean,
)
