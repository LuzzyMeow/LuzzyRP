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
import com.luzzymeow.luzzyrp.data.store.LuzzyStore
import com.luzzymeow.luzzyrp.testing.Await
import com.luzzymeow.luzzyrp.testing.Capture
import com.luzzymeow.luzzyrp.testing.TestStoreFixture
import com.luzzymeow.luzzyrp.ui.pages.usage.UsagePage
import com.luzzymeow.luzzyrp.ui.theme.LuzzyTheme
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **用量页的视觉留证**（有数据的形态：多供应商 / 多模型 / 分类混合）。
 *
 * 种出来的数据刻意有**形状**：三条系列量级不同、跨若干小时、含一条 summary 类型（记忆系统档）
 * 与一条没有 provider 的记录（未归属档）——空图或单点图看不出分桶、配色、图例对不对。
 */
@RunWith(AndroidJUnit4::class)
class UsageVisualCaptureTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var fixture: TestStoreFixture
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val stamp = Capture.newStamp()

    @Before
    fun setUp() {
        fixture = TestStoreFixture.create(context, "usagecapture")
        runBlocking { seed() }
    }

    @After
    fun tearDown() {
        fixture.close()
    }

    private suspend fun seed() {
        val now = System.currentTimeMillis()
        val hour = 60L * 60 * 1000
        val rows = listOf(
            // deepseek · chat：贯穿最近 12 小时，量最大
            Triple("deepseek", "deepseek-chat", listOf(1200 to 0L, 3400 to 2L, 900 to 5L, 2600 to 9L, 1500 to 12L)),
            // 智谱 · glm-4：中等，只在后半段
            Triple("zhipu", "glm-4-air", listOf(700 to 1L, 1800 to 4L, 400 to 11L)),
            // openai · gpt-4o-mini：小而稳
            Triple("openai", "gpt-4o-mini", listOf(300 to 3L, 260 to 8L)),
        )
        val store = fixture.store
        rows.forEach { (provider, model, points) ->
            points.forEach { (tokens, hoursAgo) ->
                val record = json.parseToJsonElement(
                    """{"type":"chat","model":"$model","provider":"$provider","protocol":"openai",
                       "inputTokens":${tokens / 2},"outputTokens":${tokens / 2},"totalTokens":$tokens,
                       "cacheReadTokens":${tokens / 4},"durationMs":1500,"finishReason":"stop","reported":true,
                       "timestamp":${now - hoursAgo * hour}}""",
                )
                store.replaceRecords(LuzzyStore.RECORD_USAGE, "", store.records(LuzzyStore.RECORD_USAGE) + record)
            }
        }
        // 记忆系统档（summary）与未归属档（provider 为空）：用来验分类筛选与「未归属」选项
        listOf(
            """{"type":"summary","model":"deepseek-chat","provider":"deepseek","protocol":"openai",
               "inputTokens":900,"outputTokens":120,"totalTokens":1020,"cacheReadTokens":0,"durationMs":2200,
               "finishReason":"stop","reported":true,"timestamp":${now - 6 * hour}}""",
            """{"type":"chat","model":"local-model","provider":"","protocol":"openai",
               "inputTokens":420,"outputTokens":80,"totalTokens":500,"cacheReadTokens":0,"durationMs":800,
               "finishReason":"stop","reported":true,"timestamp":${now - 7 * hour}}""",
        ).forEach { raw ->
            val record = json.parseToJsonElement(raw)
            store.replaceRecords(LuzzyStore.RECORD_USAGE, "", store.records(LuzzyStore.RECORD_USAGE) + record)
        }
    }

    @Test
    fun 有数据时的用量页逐张留证() {
        compose.setContent {
            LuzzyTheme(darkTheme = false) {
                UsagePage(onOpenDrawer = {}, pageData = PageDataSource(fixture.store))
            }
        }
        Await.text(compose, "总用量", 8_000, substring = true)
        Await.text(compose, "24 格", 8_000, substring = true)
        Capture.shot(context, compose, "70-usage-light-top", stamp)

        // 趋势图卡（含供应商 / 模型芯片条 + 折线 + 图例）
        compose.onNodeWithTag("usage_list").performScrollToNode(hasText("用量趋势", substring = true))
        compose.waitForIdle()
        Capture.shot(context, compose, "71-usage-light-chart", stamp)

        // 周粒度：桶少、点稀，能看清「每格一天」的标签
        compose.onAllNodes(hasText("周", substring = false)).onFirst().performClick()
        compose.waitForIdle()
        Await.text(compose, "7 格", 8_000, substring = true)
        Capture.shot(context, compose, "72-usage-light-week", stamp)

        // 按模型汇总 + 前缀缓存段
        compose.onNodeWithTag("usage_list").performScrollToNode(hasText("按模型汇总", substring = true))
        compose.waitForIdle()
        Capture.shot(context, compose, "73-usage-light-models", stamp)
    }

    /** **暗色留证**：折线是分类数据色，暗色下必须仍然可辨（背景变了，线不能糊成一团）。 */
    @Test
    fun 暗色下的用量页留证() {
        compose.setContent {
            LuzzyTheme(darkTheme = true) {
                UsagePage(onOpenDrawer = {}, pageData = PageDataSource(fixture.store))
            }
        }
        Await.text(compose, "24 格", 8_000, substring = true)
        compose.onNodeWithTag("usage_list").performScrollToNode(hasText("用量趋势", substring = true))
        compose.waitForIdle()
        Capture.shot(context, compose, "78-usage-dark-chart", stamp)
    }
}
