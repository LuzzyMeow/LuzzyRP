package com.luzzymeow.luzzyrp.assistant.ui.component.ledger

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.annotation.DrawableRes
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyColors
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyMotion
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyTheme

/**
 * 「卷宗」组件库（DESIGN.md §管理页组件规范，v1.5.0）。
 *
 * 每个组件 = **上游结构（Tailwind 类逐项复制）+ Luzzy token**；规格见 [Ledger] 与 DESIGN.md，
 * **不得临场改动**。六个管理页一律使用本库组件，禁止各页自行拼样式。
 */

private val CardShape @Composable get() = RoundedCornerShape(Ledger.RadiusLg)
private val InnerShape @Composable get() = RoundedCornerShape(Ledger.RadiusMd)
private val SmallShape @Composable get() = RoundedCornerShape(Ledger.RadiusSm)

/** `active:scale-95`——按下 0.95 缩放（150ms）。 */
@Composable
private fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = tween(150),
        label = "ledger-press",
    )
    return this.scale(scale)
}

/** `shadow-sm`：0 1dp 2dp rgba(0,0,0,.05)。 */
private fun Modifier.ledgerShadow(shape: androidx.compose.ui.graphics.Shape) =
    shadow(
        elevation = 2.dp,
        shape = shape,
        ambientColor = Color.Black.copy(alpha = 0.05f),
        spotColor = Color.Black.copy(alpha = 0.05f),
    )

// ---------------------------------------------------------------------------
// 1. 页面头（.settings-page-header）
// ---------------------------------------------------------------------------

/**
 * 页面头：`flex items-center mb-4` + 前置 24dp 图标（`text-primary-600`）+ `text-xl font-bold` 标题
 * + 右侧动作区。可选返回/菜单按钮（`.mobile-menu-button` `w-6 h-6 mr-3`）。
 */
@Composable
fun LedgerPageHeader(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    val colors = LuzzyTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Ledger.PageHeaderHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            val interaction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .size(Ledger.IconButtonSize)
                    .pressScale(interaction)
                    .clip(SmallShape)
                    .clickable(interactionSource = interaction, indication = null, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(LedgerIcons.ChevronLeft),
                    contentDescription = "返回",
                    tint = colors.muted,
                    modifier = Modifier.size(Ledger.IconSize),
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = colors.accentButton,
            modifier = Modifier.size(Ledger.IconSize),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = LedgerType.pageTitle,
            color = colors.body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { actions() }
    }
}

// ---------------------------------------------------------------------------
// 2. 分组标题（.settings-section-heading）
// ---------------------------------------------------------------------------

@Composable
fun LedgerSectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = LedgerType.sectionHeading,
        color = LuzzyTheme.colors.hairlineStrong,
        modifier = modifier.padding(bottom = Ledger.PageHeaderGap),
    )
}

// ---------------------------------------------------------------------------
// 3. 卡片（bg-white rounded-2xl border border-gray-200 shadow-sm）
// ---------------------------------------------------------------------------

@Composable
fun LedgerCard(
    modifier: Modifier = Modifier,
    padded: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = LuzzyTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .ledgerShadow(CardShape)
            .clip(CardShape)
            .background(colors.card)
            .border(1.dp, colors.hairline, CardShape)
            .then(if (padded) Modifier.padding(Ledger.CardPadding) else Modifier),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

// ---------------------------------------------------------------------------
// 4. 折叠卡片（.settings-collapse + .settings-collapse-trigger）
// ---------------------------------------------------------------------------

@Composable
fun LedgerCollapseCard(
    @DrawableRes icon: Int,
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    statusText: String? = null,
    content: @Composable () -> Unit,
) {
    val colors = LuzzyTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .ledgerShadow(CardShape)
            .clip(CardShape)
            .background(colors.card)
            .border(1.dp, colors.hairline, CardShape)
            .padding(4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Ledger.CollapseRowHeight)
                .pressScale(interaction)
                .clip(InnerShape)
                .background(if (expanded) colors.accentSoft else Color.Transparent)
                .clickable(interactionSource = interaction, indication = null, onClick = onToggle)
                .padding(horizontal = Ledger.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(SmallShape)
                    .background(if (expanded) colors.accentSoft else colors.surfaceSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = if (expanded) colors.accentButton else colors.muted,
                    modifier = Modifier.size(Ledger.IconSizeSm),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = title,
                style = LedgerType.cardTitle,
                color = if (expanded) colors.accentDeep else colors.bodyStrongMid,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (statusText != null) {
                Text(text = statusText, style = LedgerType.caption, color = colors.accentButton)
                Spacer(Modifier.width(12.dp))
            }
            Icon(
                painter = painterResource(LedgerIcons.ChevronDown),
                contentDescription = if (expanded) "收起" else "展开",
                tint = colors.muted,
                modifier = Modifier
                    .size(Ledger.IconSizeMd)
                    .graphicsLayer { rotationZ = if (expanded) 180f else 0f },
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(
                    Ledger.CollapseExpandMs,
                    easing = LuzzyMotion.EaseOut,
                ),
            ),
            exit = shrinkVertically(
                animationSpec = tween(
                    Ledger.CollapseCollapseMs,
                    easing = LuzzyMotion.EaseOut,
                ),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Ledger.CardPadding, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { content() }
        }
    }
}

// ---------------------------------------------------------------------------
// 5. 开关（.settings-toggle 44×24）
// ---------------------------------------------------------------------------

@Composable
fun LedgerToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LuzzyTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val offset by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(200),
        label = "ledger-toggle",
    )
    Box(
        modifier = modifier
            .size(width = Ledger.ToggleWidth, height = Ledger.ToggleHeight)
            .clip(CircleShape)
            .background(
                when {
                    !enabled -> colors.hairline
                    checked -> colors.accentButton
                    else -> colors.surfaceCard
                }
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
            ) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(Ledger.ToggleInset)
                .size(Ledger.ToggleThumb)
                .graphicsLayer {
                    translationX = offset * (Ledger.ToggleWidth - Ledger.ToggleThumb - Ledger.ToggleInset * 2).toPx()
                }
                .clip(CircleShape)
                .background(if (checked) Color.White else colors.card)
                .border(1.dp, if (checked) Color.White else colors.hairlineStrong, CircleShape),
        )
    }
}

/** 开关行（`flex items-center justify-between`：标签 + 开关）。 */
@Composable
fun LedgerToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    enabled: Boolean = true,
) {
    val colors = LuzzyTheme.colors
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = Ledger.ListRowMinHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = LedgerType.label, color = colors.body)
            if (hint != null) {
                Text(text = hint, style = LedgerType.caption, color = colors.mutedSoft)
            }
        }
        LedgerToggle(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

// ---------------------------------------------------------------------------
// 6. 按钮（px-3 py-1.5 rounded-lg border shadow-sm active:scale-95）
// ---------------------------------------------------------------------------

enum class LedgerButtonTone { Secondary, Primary, Danger }

@Composable
fun LedgerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    tone: LedgerButtonTone = LedgerButtonTone.Secondary,
    enabled: Boolean = true,
) {
    val colors = LuzzyTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val background = when (tone) {
        LedgerButtonTone.Primary -> colors.accentButton
        LedgerButtonTone.Danger -> colors.card
        LedgerButtonTone.Secondary -> colors.card
    }
    val contentColor = when (tone) {
        LedgerButtonTone.Primary -> Color.White
        LedgerButtonTone.Danger -> colors.error
        LedgerButtonTone.Secondary -> colors.accentDeep
    }
    val borderColor = when (tone) {
        LedgerButtonTone.Primary -> Color.Transparent
        LedgerButtonTone.Danger -> colors.hairline
        LedgerButtonTone.Secondary -> colors.accentSoft
    }
    Row(
        modifier = modifier
            .height(Ledger.ButtonHeight)
            .pressScale(interaction)
            .clip(SmallShape)
            .background(if (enabled) background else colors.surfaceSoft)
            .border(1.dp, borderColor, SmallShape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = Ledger.ButtonPaddingH),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = if (enabled) contentColor else colors.mutedSoft,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = text,
            style = LedgerType.button,
            color = if (enabled) contentColor else colors.mutedSoft,
        )
    }
}

/** 图标按钮（`p-2.5 bg-white rounded-xl border shadow-sm`）。 */
@Composable
fun LedgerIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    val colors = LuzzyTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(Ledger.IconButtonSize)
            .pressScale(interaction)
            .clip(InnerShape)
            .background(colors.card)
            .border(1.dp, colors.hairline, InnerShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = tint ?: colors.muted,
            modifier = Modifier.size(Ledger.IconSizeMd),
        )
    }
}

// ---------------------------------------------------------------------------
// 7. 输入框 / 搜索框
// ---------------------------------------------------------------------------

@Composable
fun LedgerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    minHeight: androidx.compose.ui.unit.Dp = Ledger.InputMinHeight,
    singleLine: Boolean = false,
) {
    val colors = LuzzyTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(InnerShape)
            .background(colors.surfaceSoft)
            .border(2.dp, colors.hairline, InnerShape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty() && placeholder.isNotEmpty()) {
            Text(text = placeholder, style = LedgerType.body, color = colors.mutedSoft)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = LedgerType.body.copy(color = colors.body),
            cursorBrush = SolidColor(colors.accentGraphic),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun LedgerSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索…",
) {
    val colors = LuzzyTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Ledger.SearchHeight)
            .clip(RoundedCornerShape(Ledger.RadiusLg))
            .background(colors.card)
            .border(1.dp, colors.hairline, RoundedCornerShape(Ledger.RadiusLg))
            .padding(horizontal = Ledger.SearchIconInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(LedgerIcons.Search),
            contentDescription = null,
            tint = colors.mutedSoft,
            modifier = Modifier.size(Ledger.IconSizeSm),
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(text = placeholder, style = LedgerType.body, color = colors.mutedSoft)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LedgerType.body.copy(color = colors.body),
                cursorBrush = SolidColor(colors.accentGraphic),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 8. 列表行 / 空态 / 徽标 / 分段
// ---------------------------------------------------------------------------

@Composable
fun LedgerListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = LuzzyTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Ledger.ListRowMinHeight)
            .clip(InnerShape)
            .then(
                if (onClick != null) {
                    Modifier
                        .pressScale(interaction)
                        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                } else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = LedgerType.label, color = colors.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = LedgerType.caption,
                    color = colors.mutedSoft,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun LedgerEmptyState(
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = LuzzyTheme.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = colors.hairlineStrong,
                modifier = Modifier.size(32.dp),
            )
        }
        Text(text = title, style = LedgerType.body, color = colors.muted)
        Text(text = hint, style = LedgerType.caption, color = colors.mutedSoft)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(4.dp))
            LedgerButton(text = actionLabel, onClick = onAction, icon = LedgerIcons.Plus)
        }
    }
}

@Composable
fun LedgerStatusPill(
    text: String,
    modifier: Modifier = Modifier,
    tone: LedgerButtonTone = LedgerButtonTone.Secondary,
) {
    val colors = LuzzyTheme.colors
    val background: Color
    val foreground: Color
    when (tone) {
        LedgerButtonTone.Primary -> {
            background = colors.accentSoft
            foreground = colors.accentDeep
        }
        LedgerButtonTone.Danger -> {
            background = colors.error.copy(alpha = 0.12f)
            foreground = colors.error
        }
        LedgerButtonTone.Secondary -> {
            background = colors.surfaceSoft
            foreground = colors.muted
        }
    }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text = text, style = LedgerType.caption, color = foreground)
    }
}

/** 分段选择器（`.segmented-switch`：外框 `rounded-xl bg-gray-100 p-1`，滑块 `bg-white shadow-sm`）。 */
@Composable
fun <T> LedgerSegmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LuzzyTheme.colors
    Row(
        modifier = modifier
            .clip(InnerShape)
            .background(colors.surfaceSoft)
            .padding(Ledger.SegmentPadding),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            val interaction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .height(Ledger.ButtonHeight)
                    .pressScale(interaction)
                    .clip(SmallShape)
                    .then(if (active) Modifier.ledgerShadow(SmallShape) else Modifier)
                    .background(if (active) colors.card else Color.Transparent)
                    .clickable(interactionSource = interaction, indication = null) { onSelect(value) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = LedgerType.button,
                    color = if (active) colors.accentDeep else colors.muted,
                )
            }
        }
    }
}

/** 供页面读取色板（避免各页直接 import 主题）。 */
@Composable
fun ledgerColors(): LuzzyColors = LuzzyTheme.colors
