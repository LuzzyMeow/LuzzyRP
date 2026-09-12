package com.luzzymeow.luzzyrp.ui.pages.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts
import com.luzzymeow.luzzyrp.ui.theme.Motion

/**
 * 悬浮错误卡（DESIGN-compose §19；形态照 rikkahub `ui/components/ui/ErrorCard.kt`，
 * **采用形态、未逐行复制代码**，登记见 `docs/LICENSING.md` §5）。
 *
 * **为什么错误不进消息流**（2026-09-12 对照后改）：
 * ① 错误不是「一轮对话」，插进消息列表会污染上下文回填与阅读顺序；
 * ② 更实在的缺陷——它会被 `BranchStat.of` 当作一条消息，**把「楼数/字数」算多**；
 * ③ 用户要的是「知道失败了 + 能重试」，而不是在对话里留一条假发言。
 *
 * 现在是**悬浮卡栈**：可复制错误、可单条关闭、多条时一键「全部清除」；
 * 生成失败时已真实到达的正文仍然保留为消息（不丢内容）。
 */
data class ChatError(val id: Long, val text: String)

@Composable
fun ChatErrorCards(
    errors: List<ChatError>,
    onDismiss: (Long) -> Unit,
    onDismissAll: () -> Unit,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = errors.isNotEmpty(),
        enter = fadeIn(tween(Motion.EnterMs)) + scaleIn(initialScale = 0.92f, animationSpec = tween(Motion.EnterMs)),
        exit = fadeOut(tween(Motion.ExitMs)) + scaleOut(targetScale = 0.92f, animationSpec = tween(Motion.ExitMs)),
        modifier = modifier,
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // 多条时给一行汇总（rikkahub 的「全部清除」同语义）
            if (errors.size > 1) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${errors.size} 条错误",
                        fontSize = 11.5.sp,
                        fontFamily = LuzzyFonts.Body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismissAll) {
                        Text("全部清除", fontSize = 12.sp, fontFamily = LuzzyFonts.Body)
                    }
                }
            }
            errors.forEach { error ->
                ErrorCard(
                    error = error,
                    onCopy = { onCopy(error.text) },
                    onDismiss = { onDismiss(error.id) },
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(error: ChatError, onCopy: () -> Unit, onDismiss: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("chat_error_card")
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.error.copy(alpha = 0.45f),
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = error.text,
            fontSize = 12.5.sp,
            lineHeight = 19.sp,
            fontFamily = LuzzyFonts.Body,
            color = MaterialTheme.colorScheme.onErrorContainer,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionChip(icon = LuzzyIcons.Copy, description = "复制错误信息", onClick = onCopy)
            ActionChip(icon = LuzzyIcons.Close, description = "关闭这条错误", onClick = onDismiss)
        }
    }
}

/** 48dp 热区的圆形小动作钮（pro-rules：Android 触控下限）。 */
@Composable
private fun ActionChip(icon: Int, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(16.dp),
        )
    }
}
