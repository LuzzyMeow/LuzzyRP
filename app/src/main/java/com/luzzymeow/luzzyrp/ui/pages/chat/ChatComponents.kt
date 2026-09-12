package com.luzzymeow.luzzyrp.ui.pages.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts
import dev.chrisbanes.haze.hazeEffect

/**
 * 聊天页组件 · 沉浸形态（DESIGN-compose §12；用户 2026-09-12 拍板复刻原项目聊天页）。
 *
 * 雾纸玻璃配方（单点调参，承袭现行 DESIGN.md「单点变量」纪律）：
 * blur 18dp + tint alpha 0.78——上游 0.74+blur18 在深色立绘上正文 ≥7:1 的实证配方。
 */

/** 雾纸玻璃单点调参（DESIGN-compose §12.2）。 */
object LuzzyGlass {
    const val BlurDp = 18
    const val TintAlpha = 0.78f
    const val UserTintAlpha = 0.80f
}

/** 玻璃 tint 取色（亮/暗两套；调用方在 composable 上下文取）。 */
@Composable
fun glassTint(user: Boolean): Color {
    val dark = com.luzzymeow.luzzyrp.ui.theme.LuzzyThemeColors.isDark
    return if (user) {
        if (dark) Color(0xFF3A2E26) else Color(0xFFF1E3D9)
    } else {
        if (dark) Color(0xFF2B2824) else Color(0xFFF5F0E8)
    }
}

/** 消息数据（P1 假数据 + 假流式；P2 换真实 UIMessage 模型）。 */
sealed class FakeMessage {
    abstract val name: String
    abstract val branch: String?

    data class Ai(
        override val name: String = "Vanio",
        override val branch: String? = null,
        val paragraphs: List<FakeParagraph>,
    ) : FakeMessage()

    data class User(val text: String, override val branch: String? = null) : FakeMessage() {
        override val name: String = "你"
    }

    data object Thinking : FakeMessage() {
        override val name: String get() = ""
        override val branch: String? get() = null
    }
}

/** AI 消息段落（叙述 / 动作斜体 / 对白）。 */
sealed class FakeParagraph {
    data class Narration(val text: String) : FakeParagraph()
    data class Action(val text: String) : FakeParagraph()
    data class Speech(val text: String) : FakeParagraph()
}

/** 分支指示 chip：`‹ 2/3 ›`。 */
@Composable
fun BranchChip(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f), RoundedCornerShape(50))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontFamily = LuzzyFonts.Body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 名牌行：Lora 衬线名牌 + 分支 chip。 */
@Composable
fun NameBadgeRow(name: String, branch: String?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = name,
            fontFamily = LuzzyFonts.Lora,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        if (branch != null) BranchChip(branch)
        Spacer(Modifier.weight(1f))
    }
}

/**
 * 玻璃气泡容器（雾纸配方）：blur + tint 单点常量。
 * `hazeEffect` 挂在容器 Surface 上；内容正常排版。
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    tint: Color = glassTint(user = false),
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    borderWidth: Int = 1,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val hazeState = LocalChatHazeState.current
    Box(
        modifier = modifier
            .clip(shape)
            .hazeEffect(
                state = hazeState,
                style = dev.chrisbanes.haze.HazeStyle(
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    tints = listOf(dev.chrisbanes.haze.HazeTint(tint.copy(alpha = LuzzyGlass.TintAlpha))),
                    blurRadius = LuzzyGlass.BlurDp.dp,
                ),
            )
            .border(borderWidth.dp, borderColor, shape),
        content = content,
    )}

/**
 * 用户气泡：玻璃 tint 用用户气泡底语义（`#F1E3D9` 系），16dp 圆角，320dp 上限，右对齐。
 */
@Composable
fun UserBubble(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        GlassPanel(
            shape = RoundedCornerShape(16.dp),
            tint = glassTint(user = true),
            borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
            modifier = Modifier.widthIn(max = 320.dp).animateContentSize(
                animationSpec = tween(com.luzzymeow.luzzyrp.ui.theme.Motion.EnterMs),
            ),
        ) {
            Text(
                text = text,
                fontSize = 13.5.sp,
                lineHeight = 22.sp,
                fontFamily = LuzzyFonts.Body,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            )
        }
    }
}

/** AI 消息（玻璃卡）：名牌行 + 段落流（叙述/动作斜体/对白）。 */
@Composable
fun AiMessage(
    message: FakeMessage.Ai,
    modifier: Modifier = Modifier,
) {
    GlassPanel(
        modifier = modifier
            .widthIn(max = 320.dp)
            .animateContentSize(
                animationSpec = tween(com.luzzymeow.luzzyrp.ui.theme.Motion.EnterMs),
            ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            NameBadgeRow(message.name, message.branch)
            message.paragraphs.forEach { p ->
                when (p) {
                    is FakeParagraph.Narration -> Text(
                        text = p.text,
                        fontSize = 13.5.sp, lineHeight = 23.sp,
                        fontFamily = LuzzyFonts.Body,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    is FakeParagraph.Action -> Text(
                        text = "*${p.text}*",
                        fontSize = 13.5.sp, lineHeight = 23.sp,
                        fontFamily = LuzzyFonts.Body,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    is FakeParagraph.Speech -> Text(
                        text = "「${p.text}」",
                        fontSize = 13.5.sp, lineHeight = 23.sp,
                        fontFamily = LuzzyFonts.Body,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/** 思考步骤（时间线节点）。 */
data class ThinkStep(val text: String)

/**
 * 思考卡（上游 native-thinking-card 同构）：
 * 折叠行（dot + 摘要 + chevron，可点展开）+ 展开时间线；整卡玻璃；live 态 coral 描边。
 */
@Composable
fun ThinkingCard(
    steps: List<ThinkStep>,
    isLive: Boolean,
    elapsedLabel: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(com.luzzymeow.luzzyrp.ui.theme.Motion.EnterMs),
        label = "cotChevron",
    )
    GlassPanel(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        borderColor = if (isLive) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        borderWidth = if (isLive) 2 else 1,
    ) {
        Column(Modifier.animateContentSize(animationSpec = tween(com.luzzymeow.luzzyrp.ui.theme.Motion.EnterMs))) {
            Row(
                modifier = Modifier
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 9.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .background(
                            if (isLive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            CircleShape,
                        ),
                )
                Text(
                    text = if (isLive) "思考中…" else "思考 · $elapsedLabel",
                    fontSize = 11.5.sp,
                    fontFamily = LuzzyFonts.Body,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                androidx.compose.material3.Icon(
                    painter = painterResource(LuzzyIcons.ChevronDown),
                    contentDescription = if (expanded) "折叠思考" else "展开思考",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(14.dp).rotate(chevronRotation),
                )
            }
            if (expanded) {
                Column(
                    Modifier.padding(start = 15.dp, end = 12.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    steps.forEachIndexed { i, step ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                Modifier
                                    .padding(top = 5.dp)
                                    .size(5.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = if (isLive && i == steps.lastIndex) 1f else 0.55f),
                                        CircleShape,
                                    ),
                            )
                            Text(
                                text = step.text,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                fontFamily = LuzzyFonts.Body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 玻璃卡的 haze 状态由聊天页顶层提供。 */
val LocalChatHazeState = androidx.compose.runtime.staticCompositionLocalOf<dev.chrisbanes.haze.HazeState> {
    error("LocalChatHazeState not provided")
}

/**
 * 输入岛 v2（DESIGN-compose §12.4）：功能 icon 行（上游复刻）+ 输入行 + 发送/停止。
 * 玻璃近实底（tint surfaceContainerHigh@.95 + 无 blur——键盘邻接面，不入玻璃族）。
 */
@Composable
fun InputIsland(
    isGenerating: Boolean,
    onSendOrStop: () -> Unit,
    onModelChipClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            // ── 功能行 ──
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(
                    LuzzyIcons.Plus to "附件",
                    LuzzyIcons.Sliders to "预设",
                    LuzzyIcons.BookOpen to "世界书",
                    LuzzyIcons.Mcp to "工具",
                    LuzzyIcons.Workspace to "工作区",
                ).forEach { (res, desc) ->
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable {},
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(res),
                            contentDescription = desc,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                // 模型 chip
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable(onClick = onModelChipClick)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "DeepSeek-V4",
                        fontSize = 12.sp,
                        fontFamily = LuzzyFonts.Body,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            // ── 输入行 ──
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = "",
                    onValueChange = {},
                    enabled = !isGenerating,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 14.sp,
                        fontFamily = LuzzyFonts.Body,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                    decorationBox = { inner ->
                        Box {
                            Text(
                                text = "写点什么……",
                                fontSize = 14.sp,
                                fontFamily = LuzzyFonts.Body,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            inner()
                        }
                    },
                )
                // 发送 / 停止
                Box(
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(38.dp)
                        .background(
                            if (isGenerating) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                            CircleShape,
                        )
                        .clickable(onClick = onSendOrStop),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isGenerating) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .background(MaterialTheme.colorScheme.onError, RoundedCornerShape(2.dp)),
                        )
                    } else {
                        Icon(
                            painter = painterResource(LuzzyIcons.Send),
                            contentDescription = "发送",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 流式打字点（三个点呼吸；live 态气泡尾部）。 */
@Composable
fun TypingDots(modifier: Modifier = Modifier) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "typing")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(700),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "typingAlpha",
    )
    Row(modifier = modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) {
            Box(
                Modifier
                    .size(5.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha), CircleShape),
            )
        }
    }
}
