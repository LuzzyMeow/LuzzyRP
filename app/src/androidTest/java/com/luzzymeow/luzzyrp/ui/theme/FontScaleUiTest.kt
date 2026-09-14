package com.luzzymeow.luzzyrp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import com.luzzymeow.luzzyrp.testing.Await
import com.luzzymeow.luzzyrp.ui.markdown.LocalMarkdownTokens
import com.luzzymeow.luzzyrp.ui.pages.SettingsPage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * D1 字号缩放的仪器化判据：JVM 测试钉的是纯函数（`scaled()` / `luzzyTypography`），
 * 这里钉「**LuzzyTheme → LocalMarkdownTokens / MaterialTheme.typography**」这条 provide 链
 * 真的把缩放后的值放进了组合（provide 点是全仓新增的，最值得钉）。
 * 设置页滑杆只断言 px 换算显示（交互行为是平台 API，不测）。
 *
 * 方法名纪律：androidTest 一律 ASCII camelCase（D8 对含空格/中文的 SimpleName 直接 dex 失败，
 * AGENTS §7 坑表）。
 */
class FontScaleUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun captureTokens(fontScale: Float): Pair<Float, Float> {
        var tokensBody = 0f
        var typoBody = 0f
        compose.setContent {
            LuzzyTheme(darkTheme = false, fontScale = fontScale) {
                tokensBody = LocalMarkdownTokens.current.bodySize.value
                typoBody = MaterialTheme.typography.bodyMedium.fontSize.value
            }
        }
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        return tokensBody to typoBody
    }

    @Test
    fun defaultScaleIsOneToOne() {
        val (tokens, typo) = captureTokens(1f)
        assertEquals(13.5f, tokens, 1e-4f)
        assertEquals(14f, typo, 1e-4f)
    }

    @Test
    fun bothTokenSystemsFollowScale() {
        val (tokens, typo) = captureTokens(1.25f)
        assertEquals(13.5f * 1.25f, tokens, 1e-3f)
        assertEquals(14f * 1.25f, typo, 1e-3f)
    }

    /**
     * 滑杆行在「高级设置」卡里，LazyColumn **滚出视口的条目不在语义树**（CHAT-REGRESSION §4
     * 的原坑）——断言前必须先滚到它。
     */
    private fun scrollToSlider() {
        compose.onNodeWithTag("settings_list")
            .performScrollToNode(hasText("字号"))
    }

    @Test
    fun sliderShowsPxForDefaultScale() {
        compose.setContent {
            LuzzyTheme(darkTheme = false) { SettingsPage(onOpenDrawer = {}, fontScale = 1f) }
        }
        scrollToSlider()
        Await.text(compose, "16px")
    }

    @Test
    fun sliderShowsPxForScaledValue() {
        compose.setContent {
            LuzzyTheme(darkTheme = false) { SettingsPage(onOpenDrawer = {}, fontScale = 1.25f) }
        }
        scrollToSlider()
        Await.text(compose, "20px")
    }
}
