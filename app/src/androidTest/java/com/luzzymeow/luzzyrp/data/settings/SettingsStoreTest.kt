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

    @Test
    fun marksImportedEvenWhenNothingToMove() = runBlocking {
        // kv 里没有 settings（空库）
        val summary = SettingsBootstrap.importOnce(context, fixture.store)

        assertNull("没有可搬的东西就不该报摘要", summary)
        assertTrue("但标记要置位：否则每次启动都白跑", SettingsStore(context).legacyImported)
        assertEquals(null, SettingsStore(context).load().themeMode)
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
