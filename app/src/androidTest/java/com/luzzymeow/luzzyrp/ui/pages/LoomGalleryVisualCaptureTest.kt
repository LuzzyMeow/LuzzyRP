package com.luzzymeow.luzzyrp.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luzzymeow.luzzyrp.testing.Capture
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.pages.common.BandCard
import com.luzzymeow.luzzyrp.ui.pages.common.LoomBadge
import com.luzzymeow.luzzyrp.ui.pages.common.LoomCard
import com.luzzymeow.luzzyrp.ui.pages.common.LoomChip
import com.luzzymeow.luzzyrp.ui.pages.common.LoomConfirmDialog
import com.luzzymeow.luzzyrp.ui.pages.common.LoomEmpty
import com.luzzymeow.luzzyrp.ui.pages.common.LoomField
import com.luzzymeow.luzzyrp.ui.pages.common.LoomHero
import com.luzzymeow.luzzyrp.ui.pages.common.LoomRow
import com.luzzymeow.luzzyrp.ui.pages.common.LoomSkeletonRow
import com.luzzymeow.luzzyrp.ui.pages.common.LoomSliderRow
import com.luzzymeow.luzzyrp.ui.pages.common.LoomSwitch
import com.luzzymeow.luzzyrp.ui.pages.common.LoomTier
import com.luzzymeow.luzzyrp.ui.pages.common.StatMini
import com.luzzymeow.luzzyrp.ui.pages.common.bandTone
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts
import com.luzzymeow.luzzyrp.ui.theme.LuzzyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **Loom v4 组件画廊留证**（「每页每组件截图」验收的「组件」半边）。
 *
 * 一屏装下全部 Loom 组件（BandCard 织带 / Hero / 卡片三层 / 行 / 徽标 / chip /
 * 开关 / 滑杆 / 字段 / 统计 / 空态 / 骨架），亮暗各一张 + 危险对话框一张——
 * 组件级验收在两套 ColorScheme 下一次看完。
 */
@RunWith(AndroidJUnit4::class)
class LoomGalleryVisualCaptureTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val stamp = Capture.newStamp()

    @Test
    fun 组件画廊亮色() = gallery(dark = false, name = "loom-gallery-light")

    @Test
    fun 组件画廊暗色() = gallery(dark = true, name = "loom-gallery-dark")

    private fun gallery(dark: Boolean, name: String) {
        compose.setContent {
            LuzzyTheme(darkTheme = dark) {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GalleryHeader("Loom v4 组件画廊（${if (dark) "暗色" else "亮色"}）")

                    GalleryLabel("织带卡（BandCard）")
                    BandCard(
                        title = "用户设置",
                        iconRes = LuzzyIcons.Assistants,
                        bandFirst = MaterialTheme.colorScheme.primary,
                        bandSecond = bandTone(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.surface),
                        bandContent = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        LoomRow("角色名", supporting = "Vanio")
                        LoomRow("偏好设定", supporting = "描述 12 字 · 偏好 30 字")
                    }

                    GalleryLabel("Hero（独立形态）")
                    LoomHero(
                        title = "世界书 · 钟楼设定集",
                        iconRes = LuzzyIcons.BookOpen,
                        first = MaterialTheme.colorScheme.tertiary,
                        second = bandTone(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.surface),
                        onColor = MaterialTheme.colorScheme.onTertiary,
                        summary = "12 条 · 3 张卡绑定",
                    )

                    GalleryLabel("卡片三层（Card / Raised / Overlay）")
                    LoomCard(tier = LoomTier.Card) { LoomRow("LoomTier.Card", supporting = "surfaceContainerLow") }
                    LoomCard(tier = LoomTier.Raised) { LoomRow("LoomTier.Raised", supporting = "surfaceContainerHigh") }
                    LoomCard(tier = LoomTier.Overlay) { LoomRow("LoomTier.Overlay", supporting = "surfaceContainerHighest") }

                    GalleryLabel("带 rail 的行")
                    LoomCard(tier = LoomTier.Card, rail = MaterialTheme.colorScheme.primary) {
                        LoomRow("启用中的条目", supporting = "左缘珊瑚轨 = 启用语义")
                    }

                    GalleryLabel("徽标 / 药丸 / Chip")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LoomBadge("常驻", MaterialTheme.colorScheme.tertiary)
                        LoomBadge("关键词", MaterialTheme.colorScheme.primary)
                        LoomBadge("12 字", MaterialTheme.colorScheme.secondary)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LoomChip("全部", selected = true)
                        LoomChip("本月", selected = false)
                        LoomChip("累计", selected = false)
                    }

                    GalleryLabel("开关（on / off）")
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LoomSwitch(checked = true, onCheckedChange = {}, label = "开")
                        LoomSwitch(checked = false, onCheckedChange = {}, label = "关")
                    }

                    GalleryLabel("滑杆")
                    LoomSliderRow(
                        label = "字号",
                        supporting = "12–20，松手保存",
                        valueText = "16px",
                        value = 16f,
                        onValueChange = {},
                        valueRange = 12f..20f,
                        steps = 7,
                    )

                    GalleryLabel("输入字段")
                    LoomField(value = "", onValueChange = {}, placeholder = "检索角色卡名称或描述…")

                    GalleryLabel("统计（Lora 数字）")
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        StatMini("今日 tokens", "12.4k")
                        StatMini("缓存命中", "86%")
                        StatMini("调用次数", "23")
                    }

                    GalleryLabel("空态")
                    LoomEmpty(
                        iconRes = LuzzyIcons.Conversation,
                        title = "还没有会话",
                        supporting = "先去角色页导入一张角色卡",
                    )

                    GalleryLabel("骨架行（shimmer）")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LoomSkeletonRow(height = 48.dp)
                        LoomSkeletonRow(height = 48.dp)
                    }
                }
            }
        }
        compose.waitForIdle()
        Thread.sleep(600) // 入场 / shimmer 需要真实时间过几帧
        Capture.shot(context, compose, name, stamp)
    }

    @Test
    fun 危险确认对话框亮色() {
        compose.setContent {
            LuzzyTheme(darkTheme = false) {
                LoomConfirmDialog(
                    title = "删除「钟楼下的小恶魔」？",
                    text = "将删除这张角色卡，以及它的全部会话与记忆。此操作不可恢复。",
                    confirmLabel = "删除",
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
        compose.waitForIdle()
        Thread.sleep(300)
        Capture.shot(context, compose, "loom-dialog-light", stamp, allRoots = true)
    }
}

@androidx.compose.runtime.Composable
private fun GalleryHeader(text: String) {
    Text(
        text = text,
        fontSize = 18.sp,
        fontFamily = LuzzyFonts.Lora,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    )
}

@androidx.compose.runtime.Composable
private fun GalleryLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontFamily = LuzzyFonts.Body,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
    )
}