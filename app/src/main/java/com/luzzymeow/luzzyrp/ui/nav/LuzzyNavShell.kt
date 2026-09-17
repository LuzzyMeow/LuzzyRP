package com.luzzymeow.luzzyrp.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luzzymeow.luzzyrp.BuildConfig
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.theme.LoomMotion
import com.luzzymeow.luzzyrp.ui.theme.LoomShape
import com.luzzymeow.luzzyrp.ui.theme.LoomEasing
import com.luzzymeow.luzzyrp.ui.theme.Loom
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts
import kotlinx.coroutines.launch

/**
 * v3.1（Loom v4）路由。页面集合不变（P6 定稿）。
 */
enum class LuzzyRoute(val title: String, val icon: Int, val inDrawer: Boolean = true) {
    Chat("对话", LuzzyIcons.Conversation),

    /**
     * 会话总览（跨角色平铺）。
     *
     * **不进抽屉**（用户 2026-09-13 拍板）：入口放在聊天页顶栏——所以 [inDrawer] = false，
     * 抽屉不显示它。加这个开关而不是把会话并进 Chat，是因为它确实是一个独立页面。
     */
    Sessions("会话", LuzzyIcons.Conversation, inDrawer = false),
    Characters("角色", LuzzyIcons.Assistants),
    WorldInfo("世界书", LuzzyIcons.BookOpen),
    Presets("预设", LuzzyIcons.Sliders),
    Memory("记忆", LuzzyIcons.Memory),
    Usage("用量", LuzzyIcons.ChartBar),
    Settings("设置", LuzzyIcons.Settings),
    About("关于", LuzzyIcons.Info),
}

/**
 * 抽屉收起动画时长 = **单一真源** [LoomMotion.PageMs]（v3.2 收敛：三常量曾在 NavShell
 * 重复定义 420，违背单一真源；语义值不变——2026-09-12 帧采样实测 419/405/409ms 取上界）。
 */
const val DrawerCloseMs = LoomMotion.PageMs

/**
 * 内容转场时长 = **实测抽屉收起时长**（侧边菜单完全收入抽屉时页面转场刚好完成）。
 */
const val ContentTxMs = DrawerCloseMs

/** 旧页淡出时长 = 等长交叉（与新页同段完成，不留残影）。 */
const val OldFadeMs = DrawerCloseMs

/**
 * 转场曲线：M3 标准对称缓动 `FastOutSlowIn`。
 *
 * **为何不用强 ease-out（2026-09-12 三修）**：`cubic-bezier(0.23,1,0.32,1)` 在时长前 1/4
 * 内完成约 75~80% 变化——400ms 动画实际感知只有 ~100ms。对称曲线铺满全程；
 * 抽屉与内容共用同一曲线与同一时长 → 严格同帧起跑、同时完成。
 * （元素级动效仍走 [LoomEasing.Enter] 的 ease-out——见 LoomMotion。）
 */
val TxEasing = LoomEasing.Page

/**
 * v3.1 应用壳：抽屉 + AnimatedContent 页面转场。
 *
 * Loom v4 抽屉：品牌头（logo 环 + Lora 字标 + 版本）→ 分组条目（44dp 高、
 * 选中态 = coral 药丸 + 指示器 spring 位移）→ 底部版本行。
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
    // C7：减弱动效时抽屉与跨页交叉淡化都瞬时完成。
    val reduceMotion = com.luzzymeow.luzzyrp.ui.rememberReduceMotion()
    val drawerCloseMs = com.luzzymeow.luzzyrp.ui.scaledDuration(DrawerCloseMs, reduceMotion)
    val contentTxMs = com.luzzymeow.luzzyrp.ui.scaledDuration(ContentTxMs, reduceMotion)
    val oldFadeMs = com.luzzymeow.luzzyrp.ui.scaledDuration(OldFadeMs, reduceMotion)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            LoomDrawerSheet(
                route = route,
                onNavigate = onNavigate,
                onCloseDrawer = {
                    scope.launch {
                        drawerState.animateTo(
                            androidx.compose.material3.DrawerValue.Closed,
                            androidx.compose.animation.core.tween(drawerCloseMs, easing = TxEasing),
                        )
                    }
                },
                darkMode = darkMode,
                onToggleDarkMode = onToggleDarkMode,
            )
        },
    ) {
        androidx.compose.animation.AnimatedContent(
            targetState = route,
            transitionSpec = {
                (androidx.compose.animation.fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(contentTxMs, easing = TxEasing),
                    initialAlpha = 0.30f,
                )).togetherWith(
                    androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(oldFadeMs, easing = TxEasing)),
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

/** Loom 抽屉内容（品牌头 + 分组条目 + 主题切换 + 版本）。 */
@Composable
private fun LoomDrawerSheet(
    route: LuzzyRoute,
    onNavigate: (LuzzyRoute) -> Unit,
    onCloseDrawer: () -> Unit,
    darkMode: Boolean,
    onToggleDarkMode: () -> Unit,
) {
    val loom = Loom.current
    val scheme = MaterialTheme.colorScheme
    ModalDrawerSheet(drawerContainerColor = loom.canvas) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 16.dp)) {
            // 品牌头：logo 圆环 + 名称 + 版本
            Row(
                Modifier.padding(start = 8.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(scheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(scheme.primary.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "L",
                            fontFamily = LuzzyFonts.Lora,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onPrimary,
                        )
                    }
                }
                Column {
                    Text(
                        text = "LuzzyRP",
                        fontFamily = LuzzyFonts.Lora,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                    )
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        fontFamily = LuzzyFonts.Body,
                        fontSize = 10.5.sp,
                        color = scheme.outline,
                    )
                }
            }

            // 分组条目（G-1 借鉴上游 1.9.5 导航面板的「分组小标题」信息架构：
            // 8 项平铺在功能增多后可读性会降；分组语义 = 对话主场 / 创作物料 / 数据与设置。
            // 布局**不**学上游的两列网格——桌面宽屏产物，移动单列拇指可达性更好）。
            LoomDrawerGroup(
                "对话",
                listOf(LuzzyRoute.Chat),
                route = route,
                onNavigate = onNavigate,
                onCloseDrawer = onCloseDrawer,
            )
            LoomDrawerGroup(
                "创作",
                listOf(LuzzyRoute.Characters, LuzzyRoute.WorldInfo, LuzzyRoute.Presets, LuzzyRoute.Memory),
                route = route,
                onNavigate = onNavigate,
                onCloseDrawer = onCloseDrawer,
            )
            LoomDrawerGroup(
                "数据",
                listOf(LuzzyRoute.Usage, LuzzyRoute.Settings, LuzzyRoute.About),
                route = route,
                onNavigate = onNavigate,
                onCloseDrawer = onCloseDrawer,
            )

            Spacer(Modifier.weight(1f))

            // 主题切换（保留原 toggleDark 语义）
            Surface(
                shape = RoundedCornerShape(LoomShape.Control),
                color = loom.card,
                border = androidx.compose.foundation.BorderStroke(1.dp, loom.hairline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier
                        .clickable(onClick = onToggleDarkMode)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        painter = painterResource(if (darkMode) LuzzyIcons.Sun else LuzzyIcons.Moon),
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = if (darkMode) "切到亮色" else "切到暗色",
                        fontFamily = LuzzyFonts.Body,
                        fontSize = 13.5.sp,
                        color = scheme.onSurface,
                    )
                }
            }
            Text(
                text = "每次对话，都像一本有你的小说。",
                fontFamily = LuzzyFonts.Body,
                fontSize = 10.5.sp,
                color = scheme.outline,
                modifier = Modifier.padding(start = 10.dp, top = 12.dp),
            )
        }
    }
}

/** 分组条目：组标签（LoomSectionLabel 同语言的小字距排版）+ 该组成员逐行渲染。 */
@Composable
private fun LoomDrawerGroup(
    label: String,
    routes: List<LuzzyRoute>,
    route: LuzzyRoute,
    onNavigate: (LuzzyRoute) -> Unit,
    onCloseDrawer: () -> Unit,
) {
    if (routes.none { it.inDrawer }) return
    Text(
        text = label,
        fontFamily = LuzzyFonts.Body,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.5.sp,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp),
    )
    routes.filter { it.inDrawer }.forEach { r ->
        val selected = r == route
        LoomDrawerItem(
            route = r,
            selected = selected,
            onClick = {
                onNavigate(r)
                onCloseDrawer()
            },
        )
    }
}

/** 抽屉条目：44dp 高、圆角行；选中 = coral 药丸底 + onPrimary 文字（spring 变色）。 */
@Composable
private fun LoomDrawerItem(route: LuzzyRoute, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val loom = Loom.current
    val bg by animateColorAsState(
        targetValue = if (selected) scheme.primary else Color.Transparent,
        animationSpec = com.luzzymeow.luzzyrp.ui.theme.loomSpring(),
        label = "drawer-item-bg",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
        animationSpec = com.luzzymeow.luzzyrp.ui.theme.loomSpring(),
        label = "drawer-item-fg",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(route.icon),
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = route.title,
            fontFamily = LuzzyFonts.Body,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = fg,
        )
        if (selected) {
            Spacer(Modifier.weight(1f))
            // 织机针脚指示点
            Box(Modifier.size(6.dp).background(scheme.onPrimary.copy(alpha = 0.9f), CircleShape))
        }
    }
}