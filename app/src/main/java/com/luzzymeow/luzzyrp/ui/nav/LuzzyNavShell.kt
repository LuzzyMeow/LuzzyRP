package com.luzzymeow.luzzyrp.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
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

/** 抽屉收起时长（对齐 M3 ModalNavigationDrawer 默认关闭动画 ≈250ms）。 */
const val DrawerCloseMs = 250

/** 内容页面转场总时长（两段式：抽屉期主体 + 收完后可感知落定段）。 */
const val ContentTxMs = 250

/** 旧页淡出时长（随抽屉收起期内完成，避免残影）。 */
const val OldFadeMs = 250

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
                // DESIGN-compose §13.2（二次修订：250ms 主体被抽屉遮挡 ≈ 硬切，用户实测反馈）。
                // 内容转场 450ms 两段式：0-250ms 抽屉收起期完成主体（alpha 0.35→0.8+上移大半），
                // 250-450ms 为抽屉收完后可感知的落定段（渐显至 1 + 上移到位）；
                // 旧页 fadeOut 200ms 随抽屉期完成。不透明度 ease-out 全程，禁 scale(0)。
                (fadeIn(
                    animationSpec = tween(ContentTxMs, easing = Motion.Easing),
                    initialAlpha = 0.35f,
                )).togetherWith(
                    fadeOut(animationSpec = tween(OldFadeMs, easing = Motion.Easing)),
                )
            },
            label = "pageTransition",
        ) { current ->
            Box(Modifier.fillMaxWidth()) {
                content { scope.launch { drawerState.open() } }
            }
        }
    }
}