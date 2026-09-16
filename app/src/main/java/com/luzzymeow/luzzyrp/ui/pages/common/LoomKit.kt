package com.luzzymeow.luzzyrp.ui.pages.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.rememberReduceMotion
import com.luzzymeow.luzzyrp.ui.scaledDuration
import com.luzzymeow.luzzyrp.ui.theme.LoomMotion
import com.luzzymeow.luzzyrp.ui.theme.LoomShape
import com.luzzymeow.luzzyrp.ui.theme.LoomEasing
import com.luzzymeow.luzzyrp.ui.theme.Loom
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts
import com.luzzymeow.luzzyrp.ui.theme.WeaveSpec
import com.luzzymeow.luzzyrp.ui.theme.loomSpring
import com.luzzymeow.luzzyrp.ui.theme.loomCanvasBrush
import com.luzzymeow.luzzyrp.ui.theme.loomPress
import kotlinx.coroutines.delay

/**
 * LoomKit · Loom v4 组件库（设计真源 `docs/DESIGN-ui-v4.md`）。
 *
 * 与旧 `PageKit`/`BandCard` 的关系：本文件是唯一组件源；旧文件里的
 * SettingCard / EntryCard / BandCard / LuzzySwitch / EmptyState 等由
 * 本组件库接管，旧文件在本轮改造后删除（不做两处定义）。
 *
 * 全部色彩从 M3 role 取；触控目标 ≥48dp；文本层级由 [LoomType] 统一。
 */

// ───────────────────────── 骨架 ─────────────────────────

/**
 * **Loom 画布**：accent wash 渐变 + 织纹点阵（12dp 间距，`drawWithCache` 外的轻量实现）。
 *
 * 页面级统一用它——顶页（[LoomScaffold]）、二级页（`EditorHeader` 系）、全屏编辑器。
 * 少了它页面就退化成一块纯色板，与一级页的织机语言断裂（模拟器实测：世界书二级页
 * 曾是一块白板）。
 */
@Composable
fun Modifier.loomCanvas(accent: Color): Modifier {
    val brush = loomCanvasBrush(accent)
    val weave = Loom.current.weave
    return this.drawBehind {
        drawRect(brush)
        val pitch = WeaveSpec.Pitch.toPx()
        val radius = WeaveSpec.Dot.toPx()
        if (radius > 0f && pitch > 0f) {
            var y = 0f
            var row = 0
            while (y < size.height) {
                var x = if (row % 2 == 0) 0f else pitch / 2f
                while (x < size.width) {
                    drawCircle(weave, radius = radius, center = Offset(x, y))
                    x += pitch
                }
                y += pitch
                row++
            }
        }
    }
}

/**
 * **宿主下发的系统栏内边距**（Dp；缺省 0 = 测试环境/无系统栏）。
 *
 * ## 为什么不让页面自己读 `WindowInsets.systemBars`
 *
 * 页面内直接 `windowInsetsPadding(WindowInsets.systemBars…)` 会让**每个页面**订阅窗口
 * insets；在仪器化环境里这会与 BottomSheet 的独立窗口叠加，把「面板取数的 LaunchedEffect」
 * 卷进额外重组（实测：ChatUiTest 的两条面板用例稳定红在「读取中…」）。
 * 真实宿主（`ComposeActivity`）只读一次、算成 Dp 下发，页面只做 padding——
 * 订阅集中在宿主，页面保持纯函数式，测试里自然为 0（那本来就没有系统栏）。
 *
 * 宿主侧见 `ComposeActivity`（`WindowInsets.systemBars` → 这两个 local）。
 */
val LocalLoomTopInset = androidx.compose.runtime.compositionLocalOf { 0.dp }
val LocalLoomBottomInset = androidx.compose.runtime.compositionLocalOf { 0.dp }

/**
 * Loom 页面骨架：织纹画布 + 可折叠大标题头。
 *
 * - 画布：`loomCanvasBrush(accent)` + 织纹点阵（WeaveSpec）。
 * - 头：40dp 方圆汉堡 + 大标题（Lora 24sp）→ 滚动后缩为 16sp（由
 *   [titleExpanded] 切换，调用方根据滚动状态传入）。
 * - 头底一条珊瑚 hairline（随滚动淡入）。
 * - [snackbarHost]：透传给底层 `androidx.compose.material3.Scaffold` 的反馈槽
 *   （页面失败提示等；缺省空实现，已迁移页面零感知）。
 */
@Composable
fun LoomScaffold(
    title: String,
    iconRes: Int,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
    /** 页面 accent（每页一色：primary/secondary/tertiary 之一）。 */
    accent: Color = MaterialTheme.colorScheme.primary,
    /** 头部右侧动作槽（40dp tonal 圆钮）。 */
    headerActions: @Composable RowScope.() -> Unit = {},
    /** 滚动聚合度 0..1（0=顶，1=收起；调用方经 LazyListState 派生）。 */
    collapsed: Boolean = false,
    /** 宿主反馈槽（snackbar 等，缺省不挂——大多数页面没有全局提示条）。 */
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val loom = Loom.current
    val scheme = MaterialTheme.colorScheme
    val reduce = rememberReduceMotion()
    val headerEnterMs = scaledDuration(LoomMotion.StandardMs, reduce)
    val headerExitMs = scaledDuration(LoomMotion.QuickMs, reduce)
    /**
     * 头部 insets：`enableEdgeToEdge()` 下窗口延伸到系统栏之后，**topBar 必须自己避让**
     * （Scaffold 只在没有 topBar 时把 insets.top 给内容；有 topBar 时 innerPadding.top
     * 就是 topBar 高度，不含状态栏）。
     *
     * 值由**宿主**读一次下发（[LocalLoomTopInset]）——页面不直接订阅窗口 insets，
     * 理由见该 local 的 KDoc。
     */
    val topInset = LocalLoomTopInset.current
    androidx.compose.material3.Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        snackbarHost = { snackbarHost() },
        topBar = {
            Surface(color = Color.Transparent) {
                Column(
                    Modifier.padding(top = topInset),
                ) {
                    // 大标题区（accent wash 已在画布，头部透明）
                    androidx.compose.animation.AnimatedContent(
                        targetState = collapsed,
                        transitionSpec = {
                            (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(headerEnterMs, easing = LoomEasing.Enter)))
                                .togetherWith(androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(headerExitMs, easing = LoomEasing.Exit)))
                        },
                        label = "loom-header",
                    ) { isCollapsed ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Transparent)
                                .padding(horizontal = 8.dp, vertical = if (isCollapsed) 8.dp else 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            LoomIconButton(iconRes = LuzzyIcons.Menu, contentDescription = "打开菜单", onClick = onOpenDrawer)
                            Icon(
                                painter = painterResource(iconRes),
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(if (isCollapsed) 20.dp else 24.dp),
                            )
                            Text(
                                text = title,
                                fontFamily = if (isCollapsed) LuzzyFonts.Body else LuzzyFonts.Lora,
                                fontSize = if (isCollapsed) 17.sp else 24.sp,
                                fontWeight = if (isCollapsed) FontWeight.SemiBold else FontWeight.Bold,
                                color = scheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.weight(1f))
                            headerActions()
                        }
                    }
                    // 珊瑚 hairline：滚动后浮现（thread 线）
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .alpha(if (collapsed) 1f else 0f)
                            .background(accent.copy(alpha = 0.35f)),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().loomCanvas(accent)) {
            content(padding)
        }
    }
}

// ───────────────────────── 卡与行 ─────────────────────────

/**
 * Loom 卡：织层阶梯 + 顶缘高光 + hairline 边。
 * [tier] 决定底色阶（card/raised/overlay）；[rail] 给左缘 3dp 珊瑚轨（可空）。
 */
@Composable
fun LoomCard(
    modifier: Modifier = Modifier,
    tier: LoomTier = LoomTier.Card,
    rail: Color? = null,
    shape: RoundedCornerShape = RoundedCornerShape(LoomShape.Card),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val loom = Loom.current
    val scheme = MaterialTheme.colorScheme
    val reduce = rememberReduceMotion()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val base = when (tier) {
        LoomTier.Canvas -> loom.canvas
        LoomTier.Card -> loom.card
        LoomTier.Raised -> loom.raised
        LoomTier.Overlay -> loom.overlay
    }
    val bg by animateColorAsState(
        targetValue = if (pressed) lerp(base, scheme.onSurface, 0.05f) else base,
        animationSpec = loomSpring(),
        label = "loom-card-bg",
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .loomPress(pressed, reduce)
            .border(1.dp, loom.hairline, shape),
        shape = shape,
        color = bg,
        tonalElevation = 0.dp,
    ) {
        Column(
            Modifier
                .drawTopHighlight(shape, loom.topHighlight)
                .clickable(enabled = onClick != null, onClick = onClick ?: {}, interactionSource = interaction, indication = null)
                .let { if (rail != null) it.drawRail(rail) else it },
            content = content,
        )
    }
}

/** tier 语义。 */
enum class LoomTier { Canvas, Card, Raised, Overlay }

private fun Modifier.drawTopHighlight(shape: RoundedCornerShape, highlight: Color): Modifier = drawBehind {
    // 顶缘微光：上 22% 高度的白→透明渐变（亮 5% / 暗 3%，来自 loom.topHighlight）
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(highlight, Color.Transparent),
            startY = 0f,
            endY = size.height * 0.22f,
        ),
    )
}

private fun Modifier.drawRail(color: Color): Modifier = drawBehind {
    drawRoundRect(
        color = color,
        topLeft = Offset(0f, 8f),
        size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height - 16f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()),
    )
}

/**
 * Loom 行：leading 槽 + 标题/支撑 + trailing 槽；可选点击。
 * 48dp 最小高度（触控下限）。
 */
@Composable
fun LoomRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    testTag: String? = null,
) {
    val loom = Loom.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .let { if (testTag != null) it.testTag(testTag) else it }
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.5.sp,
                fontFamily = LuzzyFonts.Body,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontFamily = LuzzyFonts.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke(this)
    }
}

/** 分组微标题（uppercase + 字距 + 发丝线延展）。 */
@Composable
fun LoomSectionLabel(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = text.uppercase(),
            fontSize = 11.5.sp,
            fontFamily = LuzzyFonts.Body,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.primary,
        )
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(Loom.current.hairline),
        )
    }
}

/** 药丸徽标（tonal fill + 文字；颜色永远不是唯一指示）。 */
@Composable
fun LoomBadge(text: String, tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(tint.copy(alpha = 0.15f), RoundedCornerShape(50))
            .border(0.5.dp, tint.copy(alpha = 0.3f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontFamily = LuzzyFonts.Body,
            fontWeight = FontWeight.Medium,
            color = tint,
        )
    }
}

// ───────────────────────── Hero ─────────────────────────

/**
 * Loom Hero：132dp 渐变织带头带（取代 BandCard）。
 *
 * - 渐变 [first]→[second]（调用方传 M3 role 与 bandTone）；
 * - 白色两层织纹（波形）叠加；
 * - 图标进 40dp 玻璃圆；标题白字（传 on 色）；
 * - 底缘「针脚缝」：3 个 4dp 圆点 + 1.5dp 深色线（织机收尾语义）；
 * - [overlap] 叠压件（头像/大图标）压在带下沿。
 */
@Composable
fun LoomHero(
    title: String,
    iconRes: Int,
    first: Color,
    second: Color,
    onColor: Color,
    modifier: Modifier = Modifier,
    summary: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    overlap: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(LoomShape.Hero)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = Loom.current.card,
        border = androidx.compose.foundation.BorderStroke(1.dp, Loom.current.hairline),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(120.dp)) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    drawRect(Brush.horizontalGradient(listOf(first, second)))
                    // 织纹（两层错位波形，白 14%/7%）
                    val upper = Path().apply {
                        moveTo(0f, h * 0.72f)
                        cubicTo(w * 0.24f, h * 0.38f, w * 0.52f, h * 1.0f, w * 0.76f, h * 0.56f)
                        cubicTo(w * 0.87f, h * 0.36f, w, h * 0.52f, w, h * 0.52f)
                        lineTo(w, h); lineTo(0f, h); close()
                    }
                    drawPath(upper, Color.White.copy(alpha = 0.14f))
                    val lower = Path().apply {
                        moveTo(0f, h * 0.88f)
                        cubicTo(w * 0.2f, h * 0.64f, w * 0.46f, h * 1.06f, w * 0.7f, h * 0.78f)
                        cubicTo(w * 0.85f, h * 0.6f, w, h * 0.74f, w, h * 0.74f)
                        lineTo(w, h); lineTo(0f, h); close()
                    }
                    drawPath(lower, Color.White.copy(alpha = 0.07f))
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            tint = onColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = onColor,
                            fontFamily = LuzzyFonts.Body,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (summary != null) {
                            Text(
                                text = summary,
                                color = onColor.copy(alpha = 0.82f),
                                fontFamily = LuzzyFonts.Body,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    trailing?.invoke()
                }
                // 针脚缝（3 点）
                Row(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 14.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    repeat(3) {
                        Box(Modifier.size(3.dp).background(Color.White.copy(alpha = 0.5f), CircleShape))
                    }
                }
                overlap?.let {
                    Box(Modifier.align(Alignment.BottomStart).padding(start = 16.dp).offset(y = 24.dp)) { it() }
                }
            }
            Column(
                Modifier.padding(top = if (overlap != null) 30.dp else 6.dp, bottom = 8.dp),
                content = {},
            )
        }
    }
}

// ───────────────────────── 控件 ─────────────────────────
/** 开关（44×24 视觉 / 48dp 热区；thumb 位移 spring 动画；减弱动效直切）。 */
@Composable
fun LoomSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    label: String? = null,
) {
    val loom = Loom.current
    val scheme = MaterialTheme.colorScheme
    val reduce = rememberReduceMotion()
    val interaction = if (onCheckedChange != null) {
        Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
    } else Modifier
    val semantics = if (label != null) Modifier.semantics { contentDescription = label } else Modifier
    val track by animateColorAsState(
        targetValue = if (checked) scheme.primary else loom.overlay,
        animationSpec = loomSpring(),
        label = "loom-switch-track",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = if (reduce) androidx.compose.animation.core.snap() else loomSpring(),
        label = "loom-switch-thumb",
    )
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .then(semantics)
            .size(width = 44.dp, height = 24.dp)
            .then(interaction)
            .background(track, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = thumbOffset)
                .size(20.dp)
                .background(Color.White, CircleShape),
        )
    }
}

/** 药丸 chip（可选选中态；选中 spring 变色 + 勾）。 */
@Composable
fun LoomChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        targetValue = if (selected) scheme.primary else scheme.surfaceContainerHighest,
        animationSpec = loomSpring(),
        label = "loom-chip-bg",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
        animationSpec = loomSpring(),
        label = "loom-chip-fg",
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .defaultMinSize(minHeight = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = text,
            fontSize = 12.5.sp,
            fontFamily = LuzzyFonts.Body,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = fg,
        )
    }
}

/** 滑杆行：标题 + 值药丸 + Slider（valueRange/steps 由调用方给）。 */
@Composable
fun LoomSliderRow(
    label: String,
    supporting: String? = null,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontFamily = LuzzyFonts.Body,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            LoomBadge(text = valueText, tint = MaterialTheme.colorScheme.primary)
        }
        if (supporting != null) {
            Text(
                text = supporting,
                fontSize = 12.sp,
                fontFamily = LuzzyFonts.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier
                .fillMaxWidth()
                .let { if (testTag != null) it.testTag(testTag) else it },
        )
    }
}

/** 输入字段（搜索 / 文本）：14dp 圆角 + focus 珊瑚描边 + 前导图标槽。 */
@Composable
fun LoomField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    testTag: String? = null,
) {
    val loom = Loom.current
    val scheme = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(
        targetValue = if (focused) scheme.primary.copy(alpha = 0.6f) else loom.hairline,
        animationSpec = loomSpring(),
        label = "loom-field-border",
    )
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, fontSize = 13.sp, fontFamily = LuzzyFonts.Body, color = scheme.outline) },
        leadingIcon = leading,
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = border,
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = loom.card,
            unfocusedContainerColor = loom.card,
        ),
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 13.5.sp,
            fontFamily = LuzzyFonts.Body,
            color = scheme.onSurface,
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .let { if (testTag != null) it.testTag(testTag) else it },
    )
}

// ───────────────────────── 反馈 ─────────────────────────

/**
 * 空态（Canvas 织纹意象 + 主副文）。
 *
 * [supporting] 常常是一整句引导文案（含全角括号与英文缩写）——**必须有水平内边距并居中**：
 * 少了它长文案会在窄屏两侧溢出被裁（模拟器实测：「点右上角「＋」导入角色卡…」左右都出屏）。
 */
@Composable
fun LoomEmpty(
    iconRes: Int,
    title: String,
    supporting: String,
    modifier: Modifier = Modifier,
) {
    val loom = Loom.current
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(loom.card)
                .border(1.dp, loom.hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = scheme.outlineVariant,
                modifier = Modifier.size(30.dp),
            )
        }
        Text(
            text = title,
            fontSize = 14.5.sp,
            fontFamily = LuzzyFonts.Body,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text(
            text = supporting,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontFamily = LuzzyFonts.Body,
            color = scheme.outline,
            fontStyle = FontStyle.Normal,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** 骨架行（shimmer 扫光；减弱动效停帧）。 */
@Composable
fun LoomSkeletonRow(modifier: Modifier = Modifier, height: Dp = 56.dp) {
    val loom = Loom.current
    val scheme = MaterialTheme.colorScheme
    val reduce = rememberReduceMotion()
    val shimmer: Float = if (reduce) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "loom-shimmer")
        val value by transition.animateFloat(
            initialValue = -0.4f,
            targetValue = 1.4f,
            animationSpec = infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.LinearEasing),
            ),
            label = "loom-shimmer-x",
        )
        value
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(LoomShape.Control))
            .background(loom.card)
            .drawBehind {
                if (!reduce) {
                    val x = shimmer * size.width
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, scheme.onSurface.copy(alpha = 0.05f), Color.Transparent),
                            startX = x - size.width * 0.3f,
                            endX = x + size.width * 0.3f,
                        ),
                    )
                }
            },
    )
}

/** 危险确认对话框（LoomDialog 形态：18dp 圆角 + error 容器色）。 */
@Composable
fun LoomConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = true,
    /** dismiss 钮文案（默认「取消」；「放弃改动 → 继续编辑」这类语义可改写）。 */
    cancelLabel: String = "取消",
    testTagConfirm: String? = null,
    testTagCancel: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(LoomShape.Card),
        containerColor = Loom.current.raised,
        title = {
            Text(
                title,
                fontFamily = LuzzyFonts.Body,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (danger) scheme.error else scheme.onSurface,
            )
        },
        text = { Text(text, fontSize = 13.sp, fontFamily = LuzzyFonts.Body, color = scheme.onSurfaceVariant) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = if (testTagConfirm != null) Modifier.testTag(testTagConfirm) else Modifier,
            ) {
                Text(confirmLabel, color = if (danger) scheme.error else scheme.primary, fontFamily = LuzzyFonts.Body, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = if (testTagCancel != null) Modifier.testTag(testTagCancel) else Modifier,
            ) { Text(cancelLabel, color = scheme.onSurfaceVariant, fontFamily = LuzzyFonts.Body) }
        },
    )
}

/** 「⋯」菜单数据（承旧语义）。 */
data class LoomMenuAction(
    val label: String,
    val onClick: () -> Unit,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
)

/** 「⋯」图标钮 + 菜单。 */
@Composable
fun LoomOverflowMenu(actions: List<LoomMenuAction>, label: String) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                painter = painterResource(LuzzyIcons.DotsHorizontal),
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.forEach { a ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = a.label,
                            fontSize = 14.sp,
                            fontFamily = LuzzyFonts.Body,
                            color = if (a.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    enabled = a.enabled,
                    onClick = {
                        open = false
                        a.onClick()
                    },
                )
            }
        }
    }
}

/** 40dp 方圆 tonal 图标钮（头部动作槽标准件）。 */
@Composable
fun LoomIconButton(
    iconRes: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(LoomShape.Control))
            .background(Loom.current.card)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}