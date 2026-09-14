package com.luzzymeow.luzzyrp.ui.pages

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luzzymeow.luzzyrp.chat.PageDataSource
import com.luzzymeow.luzzyrp.data.legacy.MigrationReport
import com.luzzymeow.luzzyrp.data.store.LuzzyStore
import com.luzzymeow.luzzyrp.testing.Await
import com.luzzymeow.luzzyrp.testing.TestStoreFixture
import com.luzzymeow.luzzyrp.ui.theme.LuzzyTheme
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * D3 迁移报告的**全链**仪器化判据：种 kv（`MigrationWriter.markMigrated` 同形状）→
 * `PageDataSource.migrationReport()` 取数 → 设置页点行 → Dialog 里真的出现那些数字。
 * 「本机没有迁移记录」的显式呈现也钉住（不许渲染成空表）。
 */
@RunWith(AndroidJUnit4::class)
class MigrationReportUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var fixture: TestStoreFixture

    @Before
    fun setUp() {
        fixture = TestStoreFixture.create(context, "migreport")
    }

    @After
    fun tearDown() {
        fixture.close()
    }

    private fun seedCounts() = runBlocking {
        fixture.store.putJson(
            LuzzyStore.KEY_LEGACY_MIGRATION_COUNTS,
            JsonObject(
                mapOf(
                    "characters" to JsonPrimitive(2),
                    "branches" to JsonPrimitive(3),
                    "messages" to JsonPrimitive(6),
                    "vectorMemories" to JsonPrimitive(4),
                    "classicMemories" to JsonPrimitive(4),
                    "worldEntries" to JsonPrimitive(2),
                    "legacyWorldEntriesDropped" to JsonPrimitive(2),
                    "regexes" to JsonPrimitive(1),
                    "presets" to JsonPrimitive(18),
                    "usage" to JsonPrimitive(9),
                    "profiles" to JsonPrimitive(1),
                    "assets" to JsonPrimitive(7),
                ),
            ),
        )
        fixture.store.putString(LuzzyStore.KEY_LEGACY_MIGRATED_AT, "1726000000000")
    }

    private fun setContent() {
        compose.setContent {
            LuzzyTheme(darkTheme = false) {
                SettingsPage(
                    onOpenDrawer = {},
                    migrationReportProvider = {
                        PageDataSource(fixture.store).migrationReport()
                    },
                )
            }
        }
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
    }

    private fun openReport() {
        // 报告行在第三张卡（数据 · 导入与导出）里，小屏上可能滚出视口——先滚到它
        compose.onNodeWithTag("settings_list")
            .performScrollToNode(hasText("迁移报告"))
        compose.onAllNodes(hasText("迁移报告")).onFirst().performClick()
    }

    @Test
    fun dialogShowsCountsSeededFromRealStore() {
        seedCounts()
        setContent()
        openReport()
        // 行标签 + 数值 + 尾注都出现（数值是种进库里的真实值，不是常量）
        Await.text(compose, "预设")
        Await.text(compose, "18")
        Await.text(compose, "附件资产")
        // 尾注整行是「迁移于 <时间> · 迁移对旧数据只读」——含时间变体，按 Await 的放宽条款用子串
        Await.text(compose, "迁移对旧数据只读", substring = true)
    }

    @Test
    fun dialogShowsNotMigratedWhenNoRecord() {
        setContent()
        openReport()
        // 精确匹配全文（substring 匹配会让判据放过「部分渲染」）
        Await.text(compose, "本机没有迁移记录（新装或尚未从旧版升级）。")
    }
}
