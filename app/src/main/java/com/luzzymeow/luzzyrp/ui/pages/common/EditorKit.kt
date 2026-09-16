package com.luzzymeow.luzzyrp.ui.pages.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.theme.Loom
import com.luzzymeow.luzzyrp.ui.theme.LoomShape
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts
import com.luzzymeow.luzzyrp.ui.theme.loomSpring

/**
 * 编辑器通用件（W4 抽出）：世界书与预设两个编辑器共用同一套壳，
 * 避免「同一种控件在两页长得不一样」这种最常见的漂移。
 *
 * 分成两块：
 * - 表单件：`FieldLabel` / `Placeholder` / `ToggleRow` / `SegmentChips` / `PrimaryButton`
 * - 正文编辑器 [LongTextEditorDialog]：**只有一个滚动容器**（文本域本身）。
 *   为什么必须独立一屏：真实正文可达数 KB，塞进表单会变成「可滚动文本域套在可滚动列表里」
 *   的嵌套滚动反模式（jetpack-compose 栈规约 #29），在手机上表现为「拖不动 / 拖错层」。
 */

/** 编辑器页头：关闭 + 标题 + 右侧动作槽（v3.1 Loom 化：44dp 钮 + 发丝线下界）。 */
@Composable
fun EditorHeader(
    title: String,
    onClose: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(LuzzyIcons.Close),
                    contentDescription = "关闭",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = title,
                fontFamily = LuzzyFonts.Lora,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .height(1.dp)
                .background(Loom.current.hairline),
        )
    }
}

@Composable
fun FieldLabel(text: String, hint: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontFamily = LuzzyFonts.Body,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (hint != null) {
            Text(
                text = hint,
                fontSize = 12.sp,
                fontFamily = LuzzyFonts.Body,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
fun Placeholder(text: String) {
    Text(text = text, fontSize = 14.sp, fontFamily = LuzzyFonts.Body, color = MaterialTheme.colorScheme.outline)
}

/** 一行开关（标题 + 说明 + 右侧 [LuzzySwitch]）。 */
@Composable
fun ToggleRow(
    title: String,
    hint: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontFamily = LuzzyFonts.Body,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
            )
            Text(
                text = hint,
                fontSize = 12.sp,
                fontFamily = LuzzyFonts.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LuzzySwitch(
            checked = checked,
            onCheckedChange = if (enabled) onChange else null,
            label = title,
        )
    }
}

/**
 * 分段选择（世界书的「范围」、预设的「注入角色」共用；v3.1 = Loom 药丸 + spring 变色）。
 *
 * [selectable] 用来表达「这一项现在不能选」（例：没有角色时不能建绑定条目）——
 * 此时**置灰并保持可读**，而不是隐藏：用户需要知道这个选项存在、为什么点不了。
 */
@Composable
fun SegmentChips(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    selectable: List<Boolean> = labels.map { true },
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            val enabled = selectable.getOrElse(index) { true }
            val active = index == selectedIndex
            val bg by androidx.compose.animation.animateColorAsState(
                targetValue = when {
                    !enabled -> Loom.current.card
                    active -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                },
                animationSpec = loomSpring(),
                label = "segment-bg",
            )
            val fg by androidx.compose.animation.animateColorAsState(
                targetValue = when {
                    !enabled -> MaterialTheme.colorScheme.outline
                    active -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = loomSpring(),
                label = "segment-fg",
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .then(if (enabled) Modifier.clickable { onSelect(index) } else Modifier)
                    .defaultMinSize(minHeight = 36.dp)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontFamily = LuzzyFonts.Body,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    color = fg,
                )
            }
        }
    }
}

/** 主按钮（保存 / 完成；v3.1 = 48dp 高珊瑚药丸 + 按压缩放）。 */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduce = com.luzzymeow.luzzyrp.ui.rememberReduceMotion()
    Box(
        modifier = modifier
            .graphicsLayer { if (pressed && !reduce) { scaleX = 0.985f; scaleY = 0.985f } }
            .clip(RoundedCornerShape(LoomShape.Control))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick, interactionSource = interaction, indication = null)
            .defaultMinSize(minHeight = 48.dp)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontFamily = LuzzyFonts.Body,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/**
 * 正文全屏编辑器：**只有一个滚动容器**（文本域本身），底部实时字数。
 *
 * 取消 = 回到打开时的内容；完成 = 把文本交回调用方（**仍不算保存**，外层「保存」才是落盘点）。
 */
@Composable
fun LongTextEditorDialog(
    title: String,
    initial: String,
    placeholder: String,
    footerHint: String,
    onCancel: () -> Unit,
    onDone: (String) -> Unit,
    testTag: String = "long_text_editor",
) {
    var text by rememberSaveable(initial) { mutableStateOf(initial) }
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            // 全屏编辑器要**连状态栏一起铺满**：默认 `decorFitsSystemWindows = true` 时
            // 窗口不覆盖状态栏区域，那里会透出被 dim 的下层 → 顶部一条深灰带，
            // 与主页面的织纹画布断裂（模拟器实测）。关掉 fit 后自己用 insets 避让。
            decorFitsSystemWindows = false,
        ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .loomCanvas(MaterialTheme.colorScheme.primary)
                .imePadding(),
        ) {
            // 头部避让状态栏——用宿主下发的 Dp（见 LocalLoomTopInset 的 KDoc：
            // 页面/弹窗自己订阅窗口 insets 会把仪器化测试的面板取数卷进额外重组）
            Column(Modifier.padding(top = LocalLoomTopInset.current)) {
                EditorHeader(
                    title = title,
                    onClose = onCancel,
                    trailing = {
                        TextButton(onClick = { onDone(text) }) {
                            Text("完成", fontFamily = LuzzyFonts.Body, fontWeight = FontWeight.Medium)
                        }
                    },
                )
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Placeholder(placeholder) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .testTag(testTag),
            )
            Text(
                text = "${text.length} 字 · $footerHint",
                fontSize = 12.sp,
                fontFamily = LuzzyFonts.Body,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .padding(bottom = LocalLoomBottomInset.current)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}
