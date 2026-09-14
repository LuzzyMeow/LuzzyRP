package com.luzzymeow.luzzyrp.data.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luzzymeow.luzzyrp.chat.TransportStore
import com.luzzymeow.luzzyrp.testing.TestStoreFixture
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 设置持久化 + 旧设置搬运的**仪器化**测试（P4-B-3.3）。
 *
 * 为什么必须真跑：设置走 SharedPreferences（真实文件），搬运要读迁移后的 kv，
 * 两者都涉及「进程内的真实存储状态」；纯 JVM 测不出「存了能不能读回来」。
 */
@RunWith(AndroidJUnit4::class)
class SettingsStoreTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var fixture: TestStoreFixture

    @Before
    fun setUp() {
        fixture = TestStoreFixture.create(context, "settings-test")
        // 清掉设备上可能残留的设置（用例之间必须互不影响）
        SettingsStore(context).save(AppSettings(themeMode = null))
        SettingsBootstrap.resetImportedFlag(context)
        TransportStore(context).save(com.luzzymeow.luzzyrp.chat.TransportConfig())
    }

    @After
    fun tearDown() {
        fixture.close()
        SettingsStore(context).save(AppSettings(themeMode = null))
        SettingsBootstrap.resetImportedFlag(context)
        TransportStore(context).save(com.luzzymeow.luzzyrp.chat.TransportConfig())
    }

    @Test
    fun themeModePersistsAcrossReads() {
        assertEquals("默认应为跟随系统", null, SettingsStore(context).load().themeMode)

        SettingsStore(context).save(AppSettings(themeMode = ThemeMode.Dark))
        assertEquals(ThemeMode.Dark, SettingsStore(context).load().themeMode)

        // 再开一个实例（等价于重新读一次 SharedPreferences）
        assertEquals(ThemeMode.Dark, SettingsStore(context).load().themeMode)

        SettingsStore(context).save(AppSettings(themeMode = null))
        assertEquals("显式清空应回到跟随系统", null, SettingsStore(context).load().themeMode)
    }

    @Test
    fun importsLegacyThemeAndProviderOnce() = runBlocking {
        // 让 kv 里有一份「迁移过来的旧设置」（走真实迁移器 + 真实样例）
        fixture.seedFromSample()

        val summary = SettingsBootstrap.importOnce(context, fixture.store)

        assertNotNull("应报告搬来了东西", summary)
        assertTrue("摘要应提到主题：$summary", summary!!.contains("主题"))
        assertEquals(ThemeMode.Dark, SettingsStore(context).load().themeMode)

        val transport = TransportStore(context).load()
        assertTrue("供应商应被灌入：$transport", transport.configured)
        assertEquals("https://example.invalid/v1", transport.baseUrl)
        assertEquals("test-model", transport.model)
        assertEquals("密钥应取分商槽位而不是旧单值", "sk-legacy-slot", transport.apiKey)
        assertTrue("标记应已置位", SettingsStore(context).legacyImported)
    }

    @Test
    fun doesNotOverwriteUserChoiceOnSecondRun() = runBlocking {
        fixture.seedFromSample()
        SettingsBootstrap.importOnce(context, fixture.store)

        // 用户在**新版里改过**：显式切回亮色 + 换成另一个供应商
        SettingsStore(context).save(AppSettings(themeMode = ThemeMode.Light))
        TransportStore(context).save(
            com.luzzymeow.luzzyrp.chat.TransportConfig(
                baseUrl = "https://mine.example/v1",
                apiKey = "sk-mine",
                model = "my-model",
            ),
        )

        SettingsBootstrap.resetImportedFlag(context) // 强制再跑一次搬运
        SettingsBootstrap.importOnce(context, fixture.store)

        assertEquals("旧值不能顶掉用户在新版里的选择", ThemeMode.Light, SettingsStore(context).load().themeMode)
        assertEquals("https://mine.example/v1", TransportStore(context).load().baseUrl)
        assertEquals("my-model", TransportStore(context).load().model)
    }

    /**
     * **「读不到旧设置」不等于「没有旧设置」——标记此时**不许**种下。**
     *
     * ## 这条断言在 2026-09-14（会话 77）被改正过，原因值得留档
     *
     * 它原本断言的是**相反**的行为（`assertTrue("但标记要置位…")`），那是本用例
     * 2026-09-13 写就时的实现。当天稍后 `bd858254` 修了一个**真机实测抓到的真缺陷**：
     * 首次打开界面时迁移还没跑完（旧数据 28.6 MB，导出二十来秒），`kv["settings"]` 还是空的
     * → 标记被误种 → 第二次启动迁移成功后 `importOnce` 直接返回 →
     * **用户的供应商配置（Base URL / API Key / 模型）永久搬不过来**，界面一直显示「未配置」。
     *
     * 修法就是 `latch = alreadyImported || legacyBlobPresent`（`SettingsBootstrap.kt:96`）：
     * **只有真的读到过旧设置才种标记**，靠 `hasGap` 走快路径自愈。
     * 于是本用例的旧断言与实现正好相反。
     *
     * 之所以拖到现在才暴露：**仪器化测试此前从未真正跑通过**（`checkChat` 一直没跑成，
     * 见 `docs/WORKLOG.md`）。这是「只断言编译通过、不断言能运行」的代价 ——
     * 会话 77 把仪器化跑起来后，它当场就被抓出来了。
     */
    @Test
    fun doesNotLatchWhenLegacySettingsUnreadable() = runBlocking {
        // kv 里没有 settings（空库）——等价于「迁移还没成功」
        val summary = SettingsBootstrap.importOnce(context, fixture.store)

        assertNull("没有可搬的东西就不该报摘要", summary)
        assertTrue(
            "读不到旧设置时**不许**种标记：种了会让后来的迁移成果永远搬不进来（bd858254 的真机缺陷）",
            !SettingsStore(context).legacyImported,
        )
        assertEquals(null, SettingsStore(context).load().themeMode)
    }

    /** 反面：**真的读到过**旧设置时才种标记（与上一条构成负控对）。 */
    @Test
    fun latchesAfterLegacyBlobIsActuallyRead() = runBlocking {
        fixture.seedFromSample() // 走真实迁移器，kv 里就有 settings 了
        SettingsBootstrap.importOnce(context, fixture.store)

        assertTrue(
            "读到过旧设置 → 标记要种（否则每次启动都白跑一遍搬运）",
            SettingsStore(context).legacyImported,
        )
    }

    @Test
    fun legacyReaderSeesRealFixtureBlobThroughKv() = runBlocking {
        // 端到端：夹具 → 迁移器 → kv → 读数器
        fixture.seedFromSample()
        val legacy = LegacySettingsReader.read(fixture.store.json(com.luzzymeow.luzzyrp.data.store.LuzzyStore.KEY_SETTINGS))
        // 样例夹具（安卓侧共享的那份）里 themeMode 未设 → null；但供应商三项必须齐全
        assertTrue("样例旧设置应能读出供应商：$legacy", legacy.hasProvider)
        assertEquals("https://example.invalid/v1", legacy.providerBaseUrl)
        assertEquals("test-model", legacy.providerModel)
    }
}
