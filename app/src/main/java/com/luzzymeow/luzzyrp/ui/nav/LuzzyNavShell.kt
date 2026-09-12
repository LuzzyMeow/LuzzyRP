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

/**
 * 抽屉收起时长（覆盖 M3 默认 ≈250ms；放慢到 400ms——用户反馈「250ms 像硬切」：
 * 抽屉宽约占屏 80%，淡化主体被移动中的抽屉遮挡，故抽屉与内容同时放慢）。
 */
const val DrawerCloseMs = 400

/** 内容转场时长 = 抽屉收起时长（抽屉完全收入时转场恰好完成；等长交叉）。 */
const val ContentTxMs = DrawerCloseMs

/** 旧页淡出时长 = 等长交叉（与新页同段完成，不留残影）。 */
const val OldFadeMs = DrawerCloseMs

/** 转场曲线（ease-out：快起慢收，尾部减速让落定段可感知）。 */
val TxEasing = Motion.Easing

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
    content: @Composable (route: LuzzyRoute, onOpenDrawer: () -> Unit) -> Unit,
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
                            // 用显式 400ms spec 覆盖 M3 默认，与内容转场等长（同帧起跑、同时结束）
                            scope.launch {
                                drawerState.animateTo(
                                    DrawerValue.Closed,
                                    tween(DrawerCloseMs, easing = TxEasing),
                                )
                            }
                        },
                    )
                }
            }
        },
    ) {
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                // DESIGN-compose §13.2（三次修订）：纯交叉淡化（无位移）。
                // 实测 250ms 交叉期仅 ~1 录屏帧（≈130ms 可辨窗口）→ 用户观感「像硬切」；
                // 现取 400ms：交叉段延伸到抽屉收完之后，两页交叠可见时间 ≈ 400ms，
                // 新页 alpha 0.35→1（抬高起点防灰陷），旧页 1→0；ease-out；禁 scale(0)。
                (fadeIn(
                    animationSpec = tween(ContentTxMs, easing = TxEasing),
                    initialAlpha = 0.30f,   // 关键帧起点：抬高防灰陷，同时让淡化幅度更大更可见
                )).togetherWith(
                    fadeOut(animationSpec = tween(OldFadeMs, easing = TxEasing)),
                )
            },
            label = "pageTransition",
        ) { current ->
            Box(Modifier.fillMaxWidth()) {
                // 关键：必须把 AnimatedContent 的 current 传下去。若 content 内部读外层 route，
                // 两个动画槽位会渲染同一个新页面（旧页瞬间消失）→ 视觉上退化成硬切。
                content(current) { scope.launch { drawerState.open() } }
            }
        }
    }
}