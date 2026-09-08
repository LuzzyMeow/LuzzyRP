package com.luzzymeow.luzzyrp.assistant.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.luzzymeow.luzzyrp.assistant.ui.component.PageHeader
import com.luzzymeow.luzzyrp.assistant.ui.terminal.TerminalLine
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyShapes
import com.luzzymeow.luzzyrp.assistant.ui.theme.LuzzyTheme

/**
 * 终端页（方向 A 抽屉二级，PLAN §10.2）。
 *
 * 等宽输出 + 单行输入；顶部显示模式（宿主 / 沙盒）与退出码；危险命令在输入侧即被拦截。
 */
@Composable
fun TerminalScreen(
    lines: List<TerminalLine>,
    running: Boolean,
    modeLabel: String,
    banner: String,
    lastExitCode: Int?,
    onRun: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LuzzyTheme.colors
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    Column(modifier = modifier.fillMaxSize().background(colors.canvas)) {
        PageHeader(
            title = "终端",
            subtitle = modeLabel + (lastExitCode?.let { " · 上次退出码 $it" } ?: ""),
            onBack = onBack,
            action = {
                Text(
                    text = "清屏",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.muted,
                    modifier = Modifier.clickable(onClick = onClear).padding(8.dp),
                )
            },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(LuzzyShapes.card)
                .background(colors.surfaceSoft)
                .border(1.dp, colors.hairline, LuzzyShapes.card)
                .padding(12.dp),
        ) {
            if (lines.isEmpty()) {
                Text(
                    text = banner,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = colors.mutedSoft,
                )
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(lines) { line ->
                    when (line) {
                        is TerminalLine.Command -> Text(
                            text = "$ " + line.text,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = colors.accentDeep,
                        )

                        is TerminalLine.Output -> Text(
                            text = line.text,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = if (line.isError) colors.error else colors.body,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.size(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(LuzzyShapes.inputIsland)
                .background(colors.surfaceCard)
                .border(1.dp, colors.hairline, LuzzyShapes.inputIsland)
                .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("$", style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = colors.accentGraphic)
            Box(modifier = Modifier.weight(1f).height(44.dp), contentAlignment = Alignment.CenterStart) {
                if (input.isEmpty()) {
                    Text(
                        text = if (running) "执行中…" else "输入命令（如 ls -la）",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = colors.mutedSoft,
                    )
                }
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    enabled = !running,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = colors.body,
                        fontFamily = FontFamily.Monospace,
                        fontSize = MaterialTheme.typography.labelMedium.fontSize,
                    ),
                    cursorBrush = SolidColor(colors.accentButton),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (running) colors.hairlineStrong else colors.accentButton)
                    .clickable(enabled = !running) {
                        onRun(input)
                        input = ""
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("↵", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
        }
    }
}
