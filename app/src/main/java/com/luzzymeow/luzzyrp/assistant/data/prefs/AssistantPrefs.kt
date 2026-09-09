package com.luzzymeow.luzzyrp.assistant.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 进程内单例 DataStore（文件名 assistant_prefs，PLAN §4.3）。 */
private val Context.assistantPrefsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = AssistantPrefs.FILE_NAME)

private val PREF_ACTIVE_ASSISTANT_ID = stringPreferencesKey(AssistantPrefs.KEY_ACTIVE_ASSISTANT_ID)
private val PREF_THEME_MODE = stringPreferencesKey(AssistantPrefs.KEY_THEME_MODE)
private val PREF_APPROVAL_POLICY = stringSetPreferencesKey(AssistantPrefs.KEY_APPROVAL_POLICY)
private val PREF_SANDBOX_ROOTFS_VERSION = stringPreferencesKey(AssistantPrefs.KEY_SANDBOX_ROOTFS_VERSION)
private val PREF_TERMINAL_FONT_SIZE_SP = floatPreferencesKey(AssistantPrefs.KEY_TERMINAL_FONT_SIZE_SP)
private val PREF_TERMINAL_SCROLLBACK_LINES = intPreferencesKey(AssistantPrefs.KEY_TERMINAL_SCROLLBACK_LINES)
private val PREF_TERMINAL_CURSOR_STYLE = stringPreferencesKey(AssistantPrefs.KEY_TERMINAL_CURSOR_STYLE)
private val PREF_MCP_TIMEOUT_MS = longPreferencesKey(AssistantPrefs.KEY_MCP_TIMEOUT_MS)
private val PREF_MCP_MAX_RETRIES = intPreferencesKey(AssistantPrefs.KEY_MCP_MAX_RETRIES)
private val PREF_SEARCH_PROVIDER = stringPreferencesKey(AssistantPrefs.KEY_SEARCH_PROVIDER)
private val PREF_SEARXNG_URL = stringPreferencesKey(AssistantPrefs.KEY_SEARXNG_URL)

/**
 * 助手偏好（Preferences DataStore，PLAN §4.3）。
 *
 * 与 Web 端 RP-Hub 的 settings（localStorage）**完全解耦**：这里只放原生助手自己的偏好。
 *
 * 键与类型严格对齐 §4.3 表：
 * active_assistant_id / theme_mode / tool_global_switch_&lt;name&gt; / approval_policy /
 * sandbox_rootfs_version / terminal_* / mcp_timeout_ms / mcp_max_retries。
 *
 * **密钥红线（PLAN §13.2）**：本类不存任何 API Key / MCP 密钥 / token。
 * 密钥走独立加密区，见同包 SecretStore 接口占位。
 *
 * 读用 Flow、写用 suspend（§4.3 要求）；所有键都有默认值，首启无需预写入。
 */
class AssistantPrefs internal constructor(private val store: DataStore<Preferences>) {

    // ---------- active_assistant_id ----------

    /** 当前助手 id；null = 尚未选择（UI 应回落到第一个助手或引导新建）。 */
    val activeAssistantId: Flow<String?> = store.data.map { prefs -> prefs[PREF_ACTIVE_ASSISTANT_ID] }

    suspend fun setActiveAssistantId(assistantId: String?) {
        store.edit { prefs ->
            if (assistantId == null) prefs.remove(PREF_ACTIVE_ASSISTANT_ID)
            else prefs[PREF_ACTIVE_ASSISTANT_ID] = assistantId
        }
    }

    // ---------- theme_mode（只读镜像，写入由 Web 端负责） ----------

    /** 跟随 Web 端的主题模式（system | light | dark）。写入仅限桥接层，UI 不得直接改。 */
    val themeMode: Flow<String> = store.data.map { prefs -> prefs[PREF_THEME_MODE] ?: DEFAULT_THEME_MODE }

    /** 仅桥接层（Web 端 settings 变更回写）调用。 */
    suspend fun setThemeMode(mode: String) {
        require(mode in THEME_MODES) { "非法主题模式: " + mode }
        store.edit { prefs -> prefs[PREF_THEME_MODE] = mode }
    }

    // ---------- tool_global_switch_<name> ----------

    /** 单个内置工具的全局开关（键 tool_global_switch_&lt;name&gt;）。 */
    fun toolGlobalSwitch(toolName: String, defaultEnabled: Boolean = defaultToolSwitch(toolName)): Flow<Boolean> =
        store.data.map { prefs -> prefs[toolSwitchKey(toolName)] ?: defaultEnabled }

    suspend fun setToolGlobalSwitch(toolName: String, enabled: Boolean) {
        require(toolName.isNotBlank()) { "工具名不能为空" }
        store.edit { prefs -> prefs[toolSwitchKey(toolName)] = enabled }
    }

    /** 已显式设置过的工具开关（未出现的键用 defaultToolSwitch 求默认值）。 */
    fun observeExplicitToolSwitches(): Flow<Map<String, Boolean>> = store.data.map { prefs ->
        prefs.asMap().entries
            .filter { entry -> entry.key.name.startsWith(TOOL_SWITCH_KEY_PREFIX) }
            .associate { entry ->
                entry.key.name.removePrefix(TOOL_SWITCH_KEY_PREFIX) to (entry.value as? Boolean ?: false)
            }
    }

    // ---------- approval_policy ----------

    /**
     * 审批策略：值为「白名单自动批准的工具名集合」（PLAN §4.3 逐调用审批 / 白名单自动批准）。
     * 空集合 = 每个写类调用都弹审批（默认，最安全）；非空 = 集合内工具免审批。
     */
    val approvalPolicy: Flow<Set<String>> = store.data.map { prefs -> prefs[PREF_APPROVAL_POLICY] ?: emptySet() }

    suspend fun setApprovalPolicy(autoApprovedTools: Set<String>) {
        store.edit { prefs -> prefs[PREF_APPROVAL_POLICY] = autoApprovedTools }
    }

    suspend fun setToolAutoApproved(toolName: String, autoApproved: Boolean) {
        require(toolName.isNotBlank()) { "工具名不能为空" }
        store.edit { prefs ->
            val current = prefs[PREF_APPROVAL_POLICY] ?: emptySet()
            prefs[PREF_APPROVAL_POLICY] = if (autoApproved) current + toolName else current - toolName
        }
    }

    fun isToolAutoApproved(toolName: String): Flow<Boolean> =
        approvalPolicy.map { policy -> toolName in policy }

    // ---------- sandbox_rootfs_version ----------

    /** rootfs 版本号；空串 = 尚未安装（PLAN §10.2 首启解压 / 升级重装）。 */
    val sandboxRootfsVersion: Flow<String> =
        store.data.map { prefs -> prefs[PREF_SANDBOX_ROOTFS_VERSION] ?: DEFAULT_SANDBOX_ROOTFS_VERSION }

    suspend fun setSandboxRootfsVersion(version: String) {
        store.edit { prefs -> prefs[PREF_SANDBOX_ROOTFS_VERSION] = version }
    }

    // ---------- terminal_* ----------

    val terminalFontSizeSp: Flow<Float> =
        store.data.map { prefs -> prefs[PREF_TERMINAL_FONT_SIZE_SP] ?: DEFAULT_TERMINAL_FONT_SIZE_SP }

    suspend fun setTerminalFontSizeSp(sizeSp: Float) {
        require(sizeSp in MIN_TERMINAL_FONT_SIZE_SP..MAX_TERMINAL_FONT_SIZE_SP) { "终端字号超出范围: " + sizeSp }
        store.edit { prefs -> prefs[PREF_TERMINAL_FONT_SIZE_SP] = sizeSp }
    }

    val terminalScrollbackLines: Flow<Int> =
        store.data.map { prefs -> prefs[PREF_TERMINAL_SCROLLBACK_LINES] ?: DEFAULT_TERMINAL_SCROLLBACK_LINES }

    suspend fun setTerminalScrollbackLines(lines: Int) {
        require(lines in MIN_TERMINAL_SCROLLBACK_LINES..MAX_TERMINAL_SCROLLBACK_LINES) { "回滚行数超出范围: " + lines }
        store.edit { prefs -> prefs[PREF_TERMINAL_SCROLLBACK_LINES] = lines }
    }

    val terminalCursorStyle: Flow<String> =
        store.data.map { prefs -> prefs[PREF_TERMINAL_CURSOR_STYLE] ?: DEFAULT_TERMINAL_CURSOR_STYLE }

    suspend fun setTerminalCursorStyle(style: String) {
        require(style in TERMINAL_CURSOR_STYLES) { "非法光标样式: " + style }
        store.edit { prefs -> prefs[PREF_TERMINAL_CURSOR_STYLE] = style }
    }

    // ---------- mcp_* ----------

    val mcpTimeoutMs: Flow<Long> = store.data.map { prefs -> prefs[PREF_MCP_TIMEOUT_MS] ?: DEFAULT_MCP_TIMEOUT_MS }

    suspend fun setMcpTimeoutMs(timeoutMs: Long) {
        require(timeoutMs in MIN_MCP_TIMEOUT_MS..MAX_MCP_TIMEOUT_MS) { "MCP 超时超出范围: " + timeoutMs }
        store.edit { prefs -> prefs[PREF_MCP_TIMEOUT_MS] = timeoutMs }
    }

    val mcpMaxRetries: Flow<Int> = store.data.map { prefs -> prefs[PREF_MCP_MAX_RETRIES] ?: DEFAULT_MCP_MAX_RETRIES }

    /** 搜索提供方 id（`duckduckgo` 默认 / `searxng`）。 */
    val searchProvider: Flow<String> =
        store.data.map { prefs -> prefs[PREF_SEARCH_PROVIDER] ?: DEFAULT_SEARCH_PROVIDER }

    suspend fun setSearchProvider(providerId: String) {
        store.edit { prefs -> prefs[PREF_SEARCH_PROVIDER] = providerId }
    }

    /** SearXNG 实例地址（非密钥，明文存储）。 */
    val searxngUrl: Flow<String> = store.data.map { prefs -> prefs[PREF_SEARXNG_URL].orEmpty() }

    suspend fun setSearxngUrl(url: String) {
        store.edit { prefs -> prefs[PREF_SEARXNG_URL] = url.trim() }
    }

    suspend fun setMcpMaxRetries(retries: Int) {
        require(retries in 0..MAX_MCP_MAX_RETRIES) { "MCP 重试次数超出范围: " + retries }
        store.edit { prefs -> prefs[PREF_MCP_MAX_RETRIES] = retries }
    }

    // ---------- 汇总快照（设置页一次性读取） ----------

    fun snapshot(): Flow<AssistantPrefsSnapshot> = store.data.map { prefs ->
        AssistantPrefsSnapshot(
            activeAssistantId = prefs[PREF_ACTIVE_ASSISTANT_ID],
            themeMode = prefs[PREF_THEME_MODE] ?: DEFAULT_THEME_MODE,
            approvalPolicy = prefs[PREF_APPROVAL_POLICY] ?: emptySet(),
            sandboxRootfsVersion = prefs[PREF_SANDBOX_ROOTFS_VERSION] ?: DEFAULT_SANDBOX_ROOTFS_VERSION,
            terminalFontSizeSp = prefs[PREF_TERMINAL_FONT_SIZE_SP] ?: DEFAULT_TERMINAL_FONT_SIZE_SP,
            terminalScrollbackLines = prefs[PREF_TERMINAL_SCROLLBACK_LINES] ?: DEFAULT_TERMINAL_SCROLLBACK_LINES,
            terminalCursorStyle = prefs[PREF_TERMINAL_CURSOR_STYLE] ?: DEFAULT_TERMINAL_CURSOR_STYLE,
            mcpTimeoutMs = prefs[PREF_MCP_TIMEOUT_MS] ?: DEFAULT_MCP_TIMEOUT_MS,
            mcpMaxRetries = prefs[PREF_MCP_MAX_RETRIES] ?: DEFAULT_MCP_MAX_RETRIES,
        )
    }

    private fun toolSwitchKey(toolName: String) = booleanPreferencesKey(TOOL_SWITCH_KEY_PREFIX + toolName)

    companion object {
        const val FILE_NAME: String = "assistant_prefs"

        const val KEY_ACTIVE_ASSISTANT_ID: String = "active_assistant_id"
        const val KEY_THEME_MODE: String = "theme_mode"
        const val KEY_APPROVAL_POLICY: String = "approval_policy"
        const val KEY_SANDBOX_ROOTFS_VERSION: String = "sandbox_rootfs_version"
        const val KEY_TERMINAL_FONT_SIZE_SP: String = "terminal_font_size_sp"
        const val KEY_TERMINAL_SCROLLBACK_LINES: String = "terminal_scrollback_lines"
        const val KEY_TERMINAL_CURSOR_STYLE: String = "terminal_cursor_style"
        const val KEY_MCP_TIMEOUT_MS: String = "mcp_timeout_ms"
        const val KEY_MCP_MAX_RETRIES: String = "mcp_max_retries"
        const val KEY_SEARCH_PROVIDER: String = "search_provider"
        const val KEY_SEARXNG_URL: String = "searxng_url"

        /** 默认搜索提供方（无 Key 引擎）。 */
        const val DEFAULT_SEARCH_PROVIDER: String = "duckduckgo"
        const val TOOL_SWITCH_KEY_PREFIX: String = "tool_global_switch_"

        const val DEFAULT_THEME_MODE: String = "system"
        const val DEFAULT_SANDBOX_ROOTFS_VERSION: String = ""
        const val DEFAULT_TERMINAL_FONT_SIZE_SP: Float = 13f
        const val DEFAULT_TERMINAL_SCROLLBACK_LINES: Int = 2000
        const val DEFAULT_TERMINAL_CURSOR_STYLE: String = "block"
        const val DEFAULT_MCP_TIMEOUT_MS: Long = 30_000L
        const val DEFAULT_MCP_MAX_RETRIES: Int = 2

        val THEME_MODES: Set<String> = setOf("system", "light", "dark")
        val TERMINAL_CURSOR_STYLES: Set<String> = setOf("block", "bar", "underline")

        const val MIN_TERMINAL_FONT_SIZE_SP: Float = 8f
        const val MAX_TERMINAL_FONT_SIZE_SP: Float = 32f
        const val MIN_TERMINAL_SCROLLBACK_LINES: Int = 100
        const val MAX_TERMINAL_SCROLLBACK_LINES: Int = 50_000
        const val MIN_MCP_TIMEOUT_MS: Long = 1_000L
        const val MAX_MCP_TIMEOUT_MS: Long = 600_000L
        const val MAX_MCP_MAX_RETRIES: Int = 5

        /** MCP 工具名前缀（PLAN §12.2：mcp__<server>__<tool>）。 */
        const val MCP_TOOL_NAME_PREFIX: String = "mcp__"

        /**
         * 默认关闭的工具（PLAN §12.1：T2/T3 默认关，需用户显式开启）。
         * T0/T1 默认开。terminal_run 属 T1/T2（沙盒内 T1、全局 T2），按安全侧默认关。
         */
        val DEFAULT_OFF_TOOLS: Set<String> = setOf(
            "calendar_read",
            "calendar_write",
            "send_to_rp_chat",
            "terminal_run",
            "screen_capture",
            "screen_tap",
            "sms_send",
            "contacts_read",
        )

        fun defaultToolSwitch(toolName: String): Boolean = when {
            toolName.startsWith(MCP_TOOL_NAME_PREFIX) -> false
            toolName in DEFAULT_OFF_TOOLS -> false
            else -> true
        }

        fun create(context: Context): AssistantPrefs =
            AssistantPrefs(context.applicationContext.assistantPrefsDataStore)
    }
}

/**
 * 助手偏好快照（设置页一次性渲染用；键语义同 [AssistantPrefs]）。
 */
data class AssistantPrefsSnapshot(
    val activeAssistantId: String?,
    val themeMode: String,
    val approvalPolicy: Set<String>,
    val sandboxRootfsVersion: String,
    val terminalFontSizeSp: Float,
    val terminalScrollbackLines: Int,
    val terminalCursorStyle: String,
    val mcpTimeoutMs: Long,
    val mcpMaxRetries: Int,
)
