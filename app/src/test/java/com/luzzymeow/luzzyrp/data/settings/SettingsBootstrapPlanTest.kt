package com.luzzymeow.luzzyrp.data.settings

import com.luzzymeow.luzzyrp.chat.TransportConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 旧设置搬运的**决定**（纯函数，可 JVM 单测）。
 *
 * ## 为什么要有这个文件：真机实测抓到的一个真缺陷
 *
 * 2026-09-13 真机（小米 25098PN5AC）首次跑 v3.0：`ComposeActivity` 先 `ensureMigrated()`、
 * 再 `SettingsBootstrap.importOnce()`。但**首次打开界面时迁移还没跑完**（旧数据 28.6 MB、
 * 160 块，导出要二十来秒），于是 `kv["settings"]` 还是空的 → 旧设置"没有可搬的东西"，
 * 而当时的实现**无条件把 `legacy_imported` 标记种下** →
 * 第二次启动迁移成功了，`importOnce` 却因为标记已种而**直接返回** →
 * 用户的供应商配置（Base URL / API Key / 模型）**永久搬不过来**，界面一直显示「未配置」。
 *
 * 两条修正，各有一条用例钉住：
 * 1. **读不到旧设置就不种标记**（读不到 ≠ 没有旧设置，可能只是迁移还没成功）；
 * 2. **快路径不能只看标记**：标记已种但「还有空缺可补」时必须继续尝试（真机自愈路径）。
 */
class SettingsBootstrapPlanTest {

    private val legacyProvider = LegacySettings(
        themeMode = ThemeMode.Dark,
        providerBaseUrl = "https://api.deepseek.com/v1",
        providerApiKey = "sk-legacy",
        providerModel = "deepseek-chat",
    )

    private val configured = TransportConfig(
        baseUrl = "https://mine.example/v1",
        apiKey = "sk-mine",
        model = "my-model",
    )

    private fun plan(
        blobPresent: Boolean = true,
        legacy: LegacySettings = legacyProvider,
        existing: TransportConfig = TransportConfig(),
        theme: ThemeMode? = null,
        alreadyImported: Boolean = false,
    ) = SettingsBootstrap.plan(
        legacyBlobPresent = blobPresent,
        legacy = legacy,
        existingTransport = existing,
        currentTheme = theme,
        alreadyImported = alreadyImported,
    )

    // ---------------------------------------------------------------- 缺陷 1：标记不能乱种

    @Test
    fun `读不到旧设置时不种标记——迁移晚到否则会被永久错过`() {
        val result = plan(blobPresent = false, legacy = LegacySettings())
        assertNull("没有旧设置可搬", result.transport)
        assertNull(result.themeMode)
        assertFalse("★ 读不到 ≠ 没有：这时种标记会让迁移成功后的旧设置永远搬不过来", result.latch)
    }

    @Test
    fun `读到旧设置才种标记`() {
        assertTrue(plan().latch)
    }

    @Test
    fun `标记已种也保持种着（不必回退一个已经成立的结论）`() {
        assertTrue(plan(blobPresent = false, alreadyImported = true).latch)
    }

    // ---------------------------------------------------------------- 缺陷 2：快路径要留空缺

    @Test
    fun `标记已种但供应商仍是空的 → 必须继续补（真机自愈路径）`() {
        val result = plan(alreadyImported = true, existing = TransportConfig())
        assertTrue("真机上就是这个状态：标记被误种，配置没搬过来", SettingsBootstrap.hasGap(existing = TransportConfig(), currentTheme = null))
        assertEquals("https://api.deepseek.com/v1", result.transport?.baseUrl)
        assertEquals("sk-legacy", result.transport?.apiKey)
        assertEquals("deepseek-chat", result.transport?.model)
    }

    @Test
    fun `没有空缺时不必再读旧设置（快路径的判据）`() {
        assertTrue(SettingsBootstrap.hasGap(existing = TransportConfig(), currentTheme = null))
        assertTrue(SettingsBootstrap.hasGap(existing = configured, currentTheme = null))
        assertTrue(SettingsBootstrap.hasGap(existing = TransportConfig(), currentTheme = ThemeMode.Dark))
        assertFalse(
            "供应商已配 + 主题已设 = 没有空缺，才允许跳过",
            SettingsBootstrap.hasGap(existing = configured, currentTheme = ThemeMode.Dark),
        )
    }

    // ---------------------------------------------------------------- 只补空缺，绝不覆盖

    @Test
    fun `新版已配置供应商时绝不覆盖（用户后来改的优先）`() {
        val result = plan(existing = configured)
        assertNull(result.transport)
        assertTrue("真的读到过旧设置 → 标记照常种", result.latch)
    }

    @Test
    fun `主题只在新版没设过时才用旧值`() {
        assertEquals(ThemeMode.Dark, plan(theme = null).themeMode)
        assertNull("用户已在新版里选过 → 不许打回旧值", plan(theme = ThemeMode.Light).themeMode)
    }

    @Test
    fun `灌供应商时保留新版自己的工具开关`() {
        val result = plan(existing = TransportConfig(toolsEnabled = false))
        assertFalse("工具开关是新版的选择，不该被旧设置顶掉", result.transport!!.toolsEnabled)
    }

    // ---------------------------------------------------------------- 报告文案

    @Test
    fun `applied 说明含搬了什么（报告与日志用）`() {
        val applied = plan().applied
        assertTrue("要能看出搬了供应商：$applied", applied.any { it.contains("供应商配置") })
        assertTrue("要能看出搬了主题：$applied", applied.any { it.contains("主题") })
        assertTrue("没有可搬的东西时不该编造说明", plan(blobPresent = false, legacy = LegacySettings()).applied.isEmpty())
    }
}
