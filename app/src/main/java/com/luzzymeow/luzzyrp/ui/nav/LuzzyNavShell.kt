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
 * 抽屉收起动画时长。
 *
 * **实测取值（2026-09-12，会话 64）**：App 内按帧采样 `drawerState.offset`，三次实测
 * 「开始移动 → 完全停止」= **419 / 405 / 409 ms**（frames=11~25, movedFrames≈全部,
 * endOffset=-945 完全收起）→ 取上界 **420ms** 作为抽屉动画时长。
 */
const val DrawerCloseMs = 420

/**
 * 内容转场时长 = **实测抽屉收起时长**（用户定稿语义：侧边菜单栏完全收入左侧抽屉时
 * 页面转场刚好完成）。等长交叉 → 两者同帧起跑、同时结束。
 */
const val ContentTxMs = DrawerCloseMs

/** 旧页淡出时长 = 等长交叉（与新页同段完成，不留残影）。 */
const val OldFadeMs = DrawerCloseMs

/**
 * 转场曲线：M3 标准对称缓动 `FastOutSlowIn`（cubic-bezier(0.4, 0, 0.2, 1)）。
 *
 * **为何不用强 ease-out（2026-09-12 三修，用户续报「还是快」的根因）**：
 * `cubic-bezier(0.23,1,0.32,1)` 会在时长前 1/4 内完成约 75~80% 的变化——400ms 的动画
 * 实际感知只有 ~100ms（新页 alpha 在 100ms 时已 0.83），因此视觉上仍是硬切。
 * 改为对称曲线后，不透明度变化均匀铺满全程，**感知时长 = 实际时长**。
 * 抽屉与内容**共用同一曲线与同一时长** → 严格同帧起跑、同时完成。
 */
val TxEasing = androidx.compose.animation.core.FastOutSlowInEasing

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
                            // 抽屉与内容转场等长同帧起跑（DESIGN-compose §13.2）
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