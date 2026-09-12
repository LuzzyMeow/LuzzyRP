package com.luzzymeow.luzzyrp.ui.pages.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts

/**
 * 聊天页组件（P1 假数据版；规格 = DESIGN-compose §4 组件表）。
 *
 * - 用户气泡：primaryContainer 底 + 16dp 圆角 + 最大宽 320dp + 右对齐；
 * - AI 消息：**无气泡默认**（Markdown 裸排在 surface 上，rikkahub 语义）；
 * - 名牌「Luna」用 Lora 衬线（品牌不变量）+ 分支 chip `‹ 2/3 ›`；
 * - 思考卡：折叠行（dot + 摘要 + chevron）；
 * - 输入岛：largeIncreased(28dp) 圆角 + outlineVariant 边 + 44dp 圆形发送键
 *   （haze 玻璃为 P2+，P1 实底回退——DESIGN-compose §4「性能逃生门」语义）。
 */

/** 消息数据（P1 假数据；P2 换真实 UIMessage 模型）。 */
sealed class FakeMessage {
    abstract val name: String
    abstract val branch: String?

    data class Ai(
        override val name: String = "Luna",
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

/** 角色名牌行：Lora 衬线名牌（左）+ 分支 chip（右）。 */
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
        if (branch != null) {
            BranchChip(branch)
        }
        Spacer(Modifier.weight(1f))
    }
}

/** 分支指示 chip：`‹ 2/3 ›`（outlineVariant 边，胶囊）。 */
@Composable
fun BranchChip(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceContainer,
                RoundedCornerShape(50),
            )
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

/** 思考卡折叠行（ChainOfThought 折叠态；live 态 P2 接流式时再做）。 */
@Composable
fun ThinkingCardCollapsed(summary: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
            Text(
                text = summary,
                fontSize = 11.5.sp,
                fontFamily = LuzzyFonts.Body,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = "展开思考",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** 用户气泡：primaryContainer + 16dp 圆角 + 320dp 上限 + 右对齐。 */
@Composable
fun UserBubble(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 0.dp,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(
                text = text,
                fontSize = 13.5.sp,
                lineHeight = 22.sp,
                fontFamily = LuzzyFonts.Body,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            )
        }
    }
}

/** AI 消息（无气泡默认）：名牌行 + 段落流（叙述/动作斜体/对白）。 */
@Composable
fun AiMessage(
    message: FakeMessage.Ai,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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

/** 输入岛：附件钮 + 占位文本 + 38dp 圆形发送键（largeIncreased 圆角，实底）。 */
@Composable
fun InputIsland(
    modifier: Modifier = Modifier,
    onToggleTheme: () -> Unit = {},
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding() // edge-to-edge 下避让系统导航栏
            .padding(horizontal = 10.dp, vertical = 10.dp),
        shape = RoundedCornerShape(28.dp), // M3 largeIncreased（material3 内部属性，同值落位）
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onToggleTheme, modifier = Modifier.size(30.dp)) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "附件（P1 占位）",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = "写点什么……",
                fontSize = 13.sp,
                fontFamily = LuzzyFonts.Body,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送（P1 占位）",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** 全屏高度占位（滚动验证用间隔）。 */
@Composable
fun SectionGap() = Spacer(Modifier.height(4.dp))