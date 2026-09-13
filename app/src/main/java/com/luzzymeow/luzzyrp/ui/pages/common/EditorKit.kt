package com.luzzymeow.luzzyrp.ui.pages.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts

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

/** 编辑器页头：关闭 + 标题 + 右侧动作槽。 */
@Composable
fun EditorHeader(
    title: String,
    onClose: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
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
 * 分段选择（世界书的「范围」、预设的「注入角色」共用）。
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
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            !enabled -> MaterialTheme.colorScheme.surfaceContainer
                            active -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                    )
                    .then(if (enabled) Modifier.clickable { onSelect(index) } else Modifier)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontFamily = LuzzyFonts.Body,
                    color = when {
                        !enabled -> MaterialTheme.colorScheme.outline
                        active -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/** 主按钮（保存 / 完成）。 */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontFamily = LuzzyFonts.Body,
            fontWeight = FontWeight.Medium,
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
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .imePadding(),
        ) {
            EditorHeader(
                title = title,
                onClose = onCancel,
                trailing = {
                    TextButton(onClick = { onDone(text) }) {
                        Text("完成", fontFamily = LuzzyFonts.Body, fontWeight = FontWeight.Medium)
                    }
                },
            )
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
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}
