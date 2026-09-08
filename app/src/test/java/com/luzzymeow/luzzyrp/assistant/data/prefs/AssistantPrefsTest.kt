package com.luzzymeow.luzzyrp.assistant.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * AssistantPrefs 单测（PLAN §4.3 键、类型与默认值）。
 *
 * 说明：写路径用内存 DataStore 驱动 AssistantPrefs 的全部键映射逻辑；
 * 真实 Preferences DataStore 只做「默认值 + 落盘键名」验证——Windows 上
 * DataStore 的 FileStorage 用 renameTo 落盘，目标文件已存在时第二次写会失败
 * （Android 上无此问题），故不做多次写盘的断言。
 */
class AssistantPrefsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var realPrefs: AssistantPrefs
    private lateinit var prefsFile: File

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        prefsFile = File(tempFolder.root, "assistant_prefs.preferences_pb")
        realPrefs = AssistantPrefs(PreferenceDataStoreFactory.create(scope = scope) { prefsFile })
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    // ---------------- 真实 DataStore ----------------

    @Test
    fun defaultsMatchPlan43() = runBlocking {
        assertNull(realPrefs.activeAssistantId.first())
        assertEquals("system", realPrefs.themeMode.first())
        assertEquals(emptySet<String>(), realPrefs.approvalPolicy.first())
        assertEquals("", realPrefs.sandboxRootfsVersion.first())
        assertEquals(13f, realPrefs.terminalFontSizeSp.first())
        assertEquals(2000, realPrefs.terminalScrollbackLines.first())
        assertEquals("block", realPrefs.terminalCursorStyle.first())
        assertEquals(30_000L, realPrefs.mcpTimeoutMs.first())
        assertEquals(2, realPrefs.mcpMaxRetries.first())
        // PLAN §12.1：T0/T1 默认开，T2/T3（含 MCP 工具）默认关
        assertTrue(realPrefs.toolGlobalSwitch("workspace_read").first())
        assertFalse(realPrefs.toolGlobalSwitch("calendar_write").first())
        assertFalse(realPrefs.toolGlobalSwitch("mcp__demo__search").first())
    }

    @Test
    fun realDataStoreFileCarriesPlanKeyNames() = runBlocking {
        realPrefs.setActiveAssistantId("asst-1")
        val raw = prefsFile.readBytes().toString(Charsets.ISO_8859_1)
        assertTrue(raw.contains(AssistantPrefs.KEY_ACTIVE_ASSISTANT_ID))
        assertFalse(raw.contains("apiKey"))
        assertFalse(raw.contains("api_key"))
        assertFalse(raw.contains("Authorization"))
    }

    @Test
    fun keyNamesAndDefaultsMatchPlan43() {
        val keys = listOf(
            AssistantPrefs.KEY_ACTIVE_ASSISTANT_ID,
            AssistantPrefs.KEY_THEME_MODE,
            AssistantPrefs.KEY_APPROVAL_POLICY,
            AssistantPrefs.KEY_SANDBOX_ROOTFS_VERSION,
            AssistantPrefs.KEY_TERMINAL_FONT_SIZE_SP,
            AssistantPrefs.KEY_TERMINAL_SCROLLBACK_LINES,
            AssistantPrefs.KEY_TERMINAL_CURSOR_STYLE,
            AssistantPrefs.KEY_MCP_TIMEOUT_MS,
            AssistantPrefs.KEY_MCP_MAX_RETRIES,
        )
        assertEquals(
            listOf(
                "active_assistant_id",
                "theme_mode",
                "approval_policy",
                "sandbox_rootfs_version",
                "terminal_font_size_sp",
                "terminal_scrollback_lines",
                "terminal_cursor_style",
                "mcp_timeout_ms",
                "mcp_max_retries",
            ),
            keys,
        )
        assertEquals("tool_global_switch_", AssistantPrefs.TOOL_SWITCH_KEY_PREFIX)
        assertEquals("assistant_prefs", AssistantPrefs.FILE_NAME)
        // 密钥红线：偏好键名不得涉及任何密钥语义
        val sensitive = listOf("key", "token", "secret", "password", "authorization", "credential")
        for (key in keys) {
            for (word in sensitive) {
                assertFalse("偏好键不得涉及密钥: " + key, key.contains(word, ignoreCase = true))
            }
        }
    }

    // ---------------- 内存 DataStore：写读回 ----------------

    @Test
    fun writesAreReadableBack() = runBlocking {
        val prefs = AssistantPrefs(InMemoryPreferencesDataStore())
        prefs.setActiveAssistantId("asst-1")
        prefs.setToolGlobalSwitch("calendar_write", true)
        prefs.setApprovalPolicy(setOf("workspace_write"))
        prefs.setSandboxRootfsVersion("1.0.3")
        prefs.setTerminalFontSizeSp(15f)
        prefs.setTerminalScrollbackLines(5000)
        prefs.setTerminalCursorStyle("bar")
        prefs.setMcpTimeoutMs(60_000L)
        prefs.setMcpMaxRetries(3)
        prefs.setThemeMode("dark")

        assertEquals("asst-1", prefs.activeAssistantId.first())
        assertTrue(prefs.toolGlobalSwitch("calendar_write").first())
        assertEquals(setOf("workspace_write"), prefs.approvalPolicy.first())
        assertTrue(prefs.isToolAutoApproved("workspace_write").first())
        assertEquals("1.0.3", prefs.sandboxRootfsVersion.first())
        assertEquals(15f, prefs.terminalFontSizeSp.first())
        assertEquals(5000, prefs.terminalScrollbackLines.first())
        assertEquals("bar", prefs.terminalCursorStyle.first())
        assertEquals(60_000L, prefs.mcpTimeoutMs.first())
        assertEquals(3, prefs.mcpMaxRetries.first())
        assertEquals("dark", prefs.themeMode.first())

        prefs.setToolAutoApproved("workspace_write", false)
        assertFalse(prefs.isToolAutoApproved("workspace_write").first())

        prefs.setActiveAssistantId(null)
        assertNull(prefs.activeAssistantId.first())
    }

    @Test
    fun snapshotAggregatesAllKeys() = runBlocking {
        val prefs = AssistantPrefs(InMemoryPreferencesDataStore())
        prefs.setActiveAssistantId("asst-2")
        prefs.setTerminalFontSizeSp(16f)
        val snapshot = prefs.snapshot().first()
        assertEquals("asst-2", snapshot.activeAssistantId)
        assertEquals("system", snapshot.themeMode)
        assertEquals(16f, snapshot.terminalFontSizeSp)
        assertEquals(30_000L, snapshot.mcpTimeoutMs)
        assertEquals(2, snapshot.mcpMaxRetries)
        assertEquals(emptySet<String>(), snapshot.approvalPolicy)
    }

    @Test
    fun explicitToolSwitchesAreExposedByPrefix() = runBlocking {
        val prefs = AssistantPrefs(InMemoryPreferencesDataStore())
        prefs.setToolGlobalSwitch("calendar_write", true)
        prefs.setToolGlobalSwitch("mcp__demo__search", true)
        val explicit = prefs.observeExplicitToolSwitches().first()
        assertEquals(setOf("calendar_write", "mcp__demo__search"), explicit.keys)
    }

    @Test
    fun invalidValuesAreRejected() = runBlocking {
        val prefs = AssistantPrefs(InMemoryPreferencesDataStore())
        assertThrows { prefs.setTerminalFontSizeSp(1f) }
        assertThrows { prefs.setTerminalCursorStyle("neon") }
        assertThrows { prefs.setMcpMaxRetries(99) }
        assertThrows { prefs.setThemeMode("solarized") }
    }

    private inline fun assertThrows(block: () -> Unit) {
        try {
            block()
            org.junit.Assert.fail("应抛 IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
        }
    }

    /** 内存 DataStore：绕过 Windows 上 DataStore FileStorage 的 renameTo 限制。 */
    private class InMemoryPreferencesDataStore : DataStore<Preferences> {

        private val state = MutableStateFlow<Preferences>(emptyPreferences())

        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
