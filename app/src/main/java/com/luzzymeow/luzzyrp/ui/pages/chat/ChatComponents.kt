package com.luzzymeow.luzzyrp.ui.pages.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.markdown.MarkdownText
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts
import com.luzzymeow.luzzyrp.ui.theme.LuzzyThemeColors
import com.luzzymeow.luzzyrp.ui.theme.Motion
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * 聊天页组件 · 沉浸形态（DESIGN-compose §12/§14）。
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
    val dark = LuzzyThemeColors.isDark
    return if (user) {
        if (dark) Color(0xFF3A2E26) else Color(0xFFF1E3D9)
    } else {
        if (dark) Color(0xFF2B2824) else Color(0xFFF5F0E8)
    }
}

/** 一条候选结果 = 一次**真实生成**的产出（多结果切换器的单位）。 */
data class AiResult(
    val raw: String,
    val thinkNodes: List<ThinkNode> = emptyList(),
    val finishReason: String? = null,
)

/** 消息数据（P2：真实发送与真实流式产出；`demoScript()` 为演示角色的历史数据）。 */
sealed class ChatMessage {
    abstract val name: String

    data class Ai(
        override val name: String = "Vanio",
        /**
         * 候选结果列表。首次生成为 1 条；「重新生成」**真实再跑一次请求**并追加，
         * 切换器（`‹ n/m ›`）随之出现——不是把同一段文本复制成多份。
         */
        val results: List<AiResult>,
        /** 当前展示的候选下标。 */
        val index: Int = 0,
    ) : ChatMessage() {
        val current: AiResult
            get() = results.getOrElse(index.coerceIn(0, (results.size - 1).coerceAtLeast(0))) {
                AiResult(raw = "")
            }

        val raw: String get() = current.raw
        val thinkNodes: List<ThinkNode> get() = current.thinkNodes
        val finishReason: String? get() = current.finishReason
        val resultCount: Int get() = results.size

        /** 追加候选并切到新结果（「重新生成」用）。 */
        fun withResult(result: AiResult): Ai = copy(results = results + result, index = results.size)

        /** 切换候选（越界即忽略）。 */
        fun selectResult(target: Int): Ai =
            if (target in results.indices) copy(index = target) else this

        /** 就地改写当前候选文本（「编辑」用；思考节点属该次生成的历史，保留）。 */
        fun editCurrent(text: String): Ai {
            if (results.isEmpty()) return copy(results = listOf(AiResult(text)), index = 0)
            val updated = results.toMutableList()
            updated[index.coerceIn(0, updated.lastIndex)] = current.copy(raw = text)
            return copy(results = updated)
        }
    }

    data class User(val text: String) : ChatMessage() {
        override val name: String = "你"

        /** 就地改写内容（「编辑」用）。 */
        fun edited(text: String): User = copy(text = text)
    }

    /** 真实失败（网络/协议/未配置）——如实展示，不伪装成模型输出。 */
    data class Error(val text: String) : ChatMessage() {
        override val name: String = "错误"
    }
}

/** 消息纯文本（分支统计/检索用；与渲染同源，不再二次拼接）。 */
fun ChatMessage.text(): String = when (this) {
    is ChatMessage.User -> text
    is ChatMessage.Ai -> raw
    is ChatMessage.Error -> text
}

/** AI 消息段落已由 Markdown 渲染器接管（[com.luzzymeow.luzzyrp.ui.markdown.MarkdownText]）。 */

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

/** 名牌行：Lora 衬线名牌（分支切换器只在气泡下方，避免同一信息显示两遍）。 */
@Composable
fun NameBadgeRow(name: String, modifier: Modifier = Modifier) {
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
        Spacer(Modifier.weight(1f))
    }
}

/** 玻璃气泡容器（雾纸配方）：blur + tint 单点常量。 */
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
                style = HazeStyle(
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    tints = listOf(HazeTint(tint.copy(alpha = LuzzyGlass.TintAlpha))),
                    blurRadius = LuzzyGlass.BlurDp.dp,
                ),
            )
            .border(borderWidth.dp, borderColor, shape),
        content = content,
    )
}

/** 用户气泡：玻璃 tint 用用户气泡底语义（`#F1E3D9` 系），16dp 圆角，320dp 上限，右对齐。 */
@Composable
fun UserBubble(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        GlassPanel(
            shape = RoundedCornerShape(16.dp),
            tint = glassTint(user = true),
            borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
            modifier = Modifier.widthIn(max = 320.dp).animateContentSize(
                animationSpec = tween(Motion.EnterMs),
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

/**
 * AI 消息面板（玻璃卡）：**思考节点卡在气泡内部**（§14.3①）+ 名牌 + Markdown 正文。
 *
 * 生成中（[isLive]）不挂 `animateContentSize`——逐字增长期间让内容自然撑开，
 * 避免每个增量都触发一次尺寸动画（那是掉帧来源）；节点的展开/收起各自带动画。
 */
@Composable
fun AiMessagePanel(
    name: String,
    raw: String,
    nodes: List<ThinkNode> = emptyList(),
    isLive: Boolean = false,
    activeNode: Int = -1,
    modifier: Modifier = Modifier,
) {
    GlassPanel(
        modifier = modifier
            .widthIn(max = 336.dp)
            .let {
                if (isLive) it
                else it.animateContentSize(animationSpec = tween(Motion.EnterMs))
            },
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (nodes.isNotEmpty()) {
                ThinkingCard(
                    nodes = nodes,
                    isLive = isLive,
                    activeIndex = activeNode,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            NameBadgeRow(name)
            // 正文交给 Markdown 渲染器：`*动作*` 是 emphasis 斜体、`「对白」`是普通文本
            // ——与上游 marked 渲染一致（此前的「对白/动作/叙述」三分类是自造语义）。
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                MarkdownText(content = raw, live = isLive, modifier = Modifier.fillMaxWidth())
            }
            if (isLive) TypingDots()
        }
    }
}

/** 玻璃卡的 haze 状态由聊天页顶层提供。 */
val LocalChatHazeState = androidx.compose.runtime.staticCompositionLocalOf<dev.chrisbanes.haze.HazeState> {
    error("LocalChatHazeState not provided")
}

/**
 * 输入岛（§12.4）：功能行 + 输入行 + 发送/停止。
 * 玻璃近实底（tint surfaceContainerHigh@.95 + 无 blur——键盘邻接面，不入玻璃族）。
 *
 * **排版依据（2026-09-12 用户报「太臃肿」后按上游实测重排）**：上游 RP-Hub 这一行是
 * 4 个 `w-8 h-8`（32dp）小圆钮 + 图标 `w-4 h-4`（16dp），且**模型选择器不在这一行**
 * （在快捷面板里）。本版据此：
 * ① 功能行只留**有真实去向**的入口（世界书 / 工具）——附件/预设/工作区依赖 P4/P5，
 *    在实现前**不放图标**（装饰性 icon 是 huashu 明令的 slop；点不动的图标更是欺骗）；
 * ② 模型 chip 退到行尾并**限宽 128dp + 单行省略**（此前不限宽导致换行压到图标行上）；
 * ③ 图标热区仍保持 48dp（Android 触控下限），但**取消图标间的人工间距**——视觉更紧、
 *    热区不重叠；行高与内边距一并收紧。
 */
@Composable
fun InputIsland(
    text: String,
    onTextChange: (String) -> Unit,
    isGenerating: Boolean,
    modelLabel: String,
    configured: Boolean,
    onSendOrStop: () -> Unit,
    onModelChipClick: () -> Unit,
    onWorldBook: () -> Unit,
    onTools: () -> Unit,
    toolsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val canSend = text.isNotBlank()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        tonalElevation = 0.dp,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
            // ── 功能行：左侧紧凑图标簇 + 行尾模型 chip ──
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(
                    Triple(LuzzyIcons.BookOpen, "世界书", onWorldBook),
                    Triple(LuzzyIcons.Mcp, "工具", onTools),
                ).forEach { (res, desc, action) ->
                    val active = res == LuzzyIcons.Mcp && toolsEnabled
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(onClick = action),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(res),
                            contentDescription = if (active) "$desc（已开启）" else desc,
                            tint = if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                // 模型 chip：**次级信息**，正常态安静（中性底/次级文字），只有「未配置」才用告警色
                // ——实心珊瑚胶囊会盖过输入框，最强对比没给最重要的内容（视觉层级反了）。
                // 限宽 + 单行省略：不限宽会换行并压住图标行（实测）。
                Box(
                    Modifier
                        .widthIn(max = 128.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (configured) MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.errorContainer,
                        )
                        .clickable(onClick = onModelChipClick)
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = if (configured) modelLabel else "未配置供应商",
                        fontSize = 11.5.sp,
                        fontFamily = LuzzyFonts.Body,
                        fontWeight = FontWeight.Normal,
                        color = if (configured) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onErrorContainer,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            // ── 输入行 ──
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    enabled = !isGenerating,
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        fontFamily = LuzzyFonts.Body,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
                    decorationBox = { inner ->
                        // 占位与真实输入必须同处一个容器：decorationBox 的测量只认一个子节点，
                        // 平铺两个兄弟会让命中区域与测量错乱（点不中输入框）。
                        Box {
                            if (text.isEmpty()) {
                                Text(
                                    text = "写点什么……",
                                    fontSize = 14.sp,
                                    fontFamily = LuzzyFonts.Body,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
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
                            when {
                                isGenerating -> MaterialTheme.colorScheme.error
                                canSend -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            CircleShape,
                        )
                        .clickable(enabled = isGenerating || canSend, onClick = onSendOrStop),
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
                            tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 流式打字点（三点呼吸；live 态气泡尾部）。 */
@Composable
fun TypingDots(modifier: Modifier = Modifier) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "typing")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            tween(700),
            RepeatMode.Reverse,
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
