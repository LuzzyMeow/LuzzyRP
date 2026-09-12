package com.luzzymeow.luzzyrp.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts
import com.luzzymeow.luzzyrp.ui.theme.Motion
import kotlinx.coroutines.launch

/**
 * v3.0 路由（P1 静态稿；P5 引入详情栈时再议 Navigation3）。
 * 页面为平铺切换，转场 = DESIGN-compose §13.2（fadeIn 200ms + scaleIn 0.96 /
 * fadeOut 140ms，交叉淡化语义）。
 */
enum class LuzzyRoute(val title: String, val icon: Int) {
    Chat("对话", LuzzyIcons.Conversation),
    Characters("角色", LuzzyIcons.Assistants),
    WorldInfo("世界书", LuzzyIcons.BookOpen),
    Presets("预设", LuzzyIcons.Sliders),
    Memory("记忆", LuzzyIcons.Memory),
    Usage("用量", LuzzyIcons.ChartBar),
    Settings("设置", LuzzyIcons.Settings),
    About("关于", LuzzyIcons.Info),
}

/**
 * v3.0 应用壳：抽屉（提升到壳层，全页共用）+ AnimatedContent 页面转场。
 *
 * 转场（DESIGN-compose §13.2）：进入 fadeIn(200ms)+scaleIn(0.96)，退出 fadeOut(140ms)——
 * 交叉淡化语义（现行 DESIGN.md「页面交接」条款移植）；抽屉点菜单 = setRoute + 关抽屉同拍
 * （rikkahub「直接 navigate」实践合并）。
 */
@Composable
fun LuzzyNavShell(
    route: LuzzyRoute,
    onNavigate: (LuzzyRoute) -> Unit,
    darkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    content: @Composable (onOpenDrawer: () -> Unit) -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                Text(
                    text = "LuzzyRP",
                    fontFamily = LuzzyFonts.Lora,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(20.dp),
                )
                LuzzyRoute.entries.forEach { r ->
                    val selected = r == route
                    ListItem(
                        leadingContent = {
                            Icon(
                                painter = painterResource(r.icon),
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        headlineContent = {
                            Text(
                                text = r.title,
                                fontFamily = LuzzyFonts.Body,
                                fontSize = 14.sp,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                            )
                        },
                        modifier = Modifier.clickable {
                            onNavigate(r)
                            scope.launch { drawerState.close() }
                        },
                    )
                }
            }
        },
    ) {
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                (fadeIn(tween(Motion.EnterMs, easing = Motion.Easing)) +
                    scaleIn(initialScale = 0.96f, animationSpec = tween(Motion.EnterMs, easing = Motion.Easing)))
                    .togetherWith(fadeOut(tween(Motion.ExitMs, easing = Motion.Easing)))
            },
            label = "pageTransition",
        ) { current ->
            Box(Modifier.fillMaxWidth()) {
                content { scope.launch { drawerState.open() } }
            }
        }
    }
}