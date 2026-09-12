package com.luzzymeow.luzzyrp.ui.pages

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luzzymeow.luzzyrp.BuildConfig
import com.luzzymeow.luzzyrp.R
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.pages.common.BadgeChip
import com.luzzymeow.luzzyrp.ui.pages.common.LuzzySwitch
import com.luzzymeow.luzzyrp.ui.pages.common.PageHeader
import com.luzzymeow.luzzyrp.ui.pages.common.SectionTitle
import com.luzzymeow.luzzyrp.ui.pages.common.SettingCard
import com.luzzymeow.luzzyrp.ui.pages.common.SettingRow
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts

/**
 * P1 静态稿 ×7（DESIGN-compose §13.1；上游各页 IA 翻译，假数据）。
 * 统一骨架：Scaffold(PageHeader) + LazyColumn 卡片流；底色 surface 实底。
 */

@Composable
private fun PageScaffold(
    title: String,
    iconRes: Int,
    onOpenDrawer: () -> Unit,
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { PageHeader(title, iconRes, onOpenDrawer, actions) },
    ) { padding -> content(padding) }
}

/** 行卡通用：把手 + 名 + 徽标 + toggle + 编辑/删除（世界书/预设共用语义）。 */
@Composable
private fun EntryRow(
    title: String,
    badgeText: String,
    badgeTint: Color,
    checked: Boolean,
    supporting: String? = null,
) {
    SettingCard(Modifier.padding(bottom = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(LuzzyIcons.Menu),
                contentDescription = "拖拽排序",
                tint = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(16.dp),
            )
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontFamily = LuzzyFonts.Body,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    BadgeChip(badgeText, badgeTint)
                }
                if (supporting != null) {
                    Text(
                        text = supporting,
                        fontSize = 11.5.sp,
                        fontFamily = LuzzyFonts.Body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LuzzySwitch(checked)
            Icon(
                painter = painterResource(LuzzyIcons.Settings),
                contentDescription = "编辑",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp),
            )
            Icon(
                painter = painterResource(LuzzyIcons.Trash),
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

/** 页头右侧动作钮（图标占位）。 */
@Composable
private fun HeaderAction(iconRes: Int, desc: String, tint: Color = MaterialTheme.colorScheme.primary) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = desc,
        tint = tint,
        modifier = Modifier.size(20.dp).padding(1.dp),
    )
}

// ───────────────────────── 角色卡页 ─────────────────────────

@Composable
fun CharactersPage(onOpenDrawer: () -> Unit) {
    PageScaffold("角色卡管理", LuzzyIcons.Assistants, onOpenDrawer, actions = {
        HeaderAction(LuzzyIcons.Search, "检索")
        HeaderAction(LuzzyIcons.Plus, "添加角色卡")
        Spacer(Modifier.width(8.dp))
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = "2 张角色卡 · 网格视图",
                    fontSize = 12.sp,
                    fontFamily = LuzzyFonts.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                )
            }
            item { CharacterCard("Vanio", "教堂后的小恶魔 · 偷苹果惯犯", inUse = true) }
            item { CharacterCard("Luna", "灯塔守夜人 · 雾季值班", inUse = false) }
            item {
                Text(
                    text = "「前往角色卡工坊导入更多角色」",
                    fontSize = 12.sp,
                    fontFamily = LuzzyFonts.Body,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun CharacterCard(name: String, desc: String, inUse: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        Image(
            painter = painterResource(R.drawable.vanio_card),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xFF141413).copy(alpha = 0.72f)),
                    ),
                ),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = name,
                    fontFamily = LuzzyFonts.Lora,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                if (inUse) BadgeChip("使用中", Color(0xFF5DB872))
            }
            Text(
                text = desc,
                fontSize = 11.5.sp,
                fontFamily = LuzzyFonts.Body,
                color = Color.White.copy(alpha = 0.8f),
            )
            Text(
                text = "世界书 1 · 正则 2",
                fontSize = 11.sp,
                fontFamily = LuzzyFonts.Body,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Row(
            Modifier.align(Alignment.TopEnd).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter = painterResource(LuzzyIcons.Download),
                contentDescription = "导出",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(17.dp),
            )
            Icon(
                painter = painterResource(LuzzyIcons.Trash),
                contentDescription = "删除",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

// ───────────────────────── 世界书页 ─────────────────────────

@Composable
fun WorldInfoPage(onOpenDrawer: () -> Unit) {
    PageScaffold("世界书", LuzzyIcons.BookOpen, onOpenDrawer, actions = {
        HeaderAction(LuzzyIcons.Download, "导出")
        HeaderAction(LuzzyIcons.Plus, "新建条目")
        Spacer(Modifier.width(8.dp))
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
        ) {
            item {
                SettingCard(Modifier.padding(bottom = 12.dp)) {
                    SettingRow("全局激活", "扫描深度 / 最大扫描深度", trailing = { LuzzySwitch(true) })
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        SliderStat("扫描深度", "4")
                        SliderStat("最大扫描深度", "12")
                    }
                }
            }
            item { SectionTitle("条目 · 3") }
            item {
                EntryRow("钟楼红苹果树", "全局", Color(0xFF8B5CF6), true, "关键词：苹果 / 钟楼 / 恶魔果子")
            }
            item {
                EntryRow("嬷嬷的巡视路线", "绑定", Color(0xFFD4A017), true, "关键词：嬷嬷 / 巡视 / 被发现")
            }
            item {
                EntryRow("雾季航行规则", "全局", Color(0xFF8B5CF6), false, "关键词：雾 / 灯塔 / 航道")
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SliderStat(label: String, value: String) {
    Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontFamily = LuzzyFonts.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                fontSize = 12.sp,
                fontFamily = LuzzyFonts.Body,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.4f)
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
            )
        }
    }
}

// ───────────────────────── 预设页 ─────────────────────────

@Composable
fun PresetsPage(onOpenDrawer: () -> Unit) {
    PageScaffold("预设管理", LuzzyIcons.Sliders, onOpenDrawer, actions = {
        HeaderAction(LuzzyIcons.Download, "导出")
        HeaderAction(LuzzyIcons.Plus, "新建预设")
        Spacer(Modifier.width(8.dp))
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
        ) {
            item { SectionTitle("提示词预设 · 3") }
            item {
                EntryRow("破限预设 · v4", "system", Color(0xFF8B5CF6), true, "越狱主预设 · 注入 system")
            }
            item {
                EntryRow("COT 思维链", "system", Color(0xFF8B5CF6), false, "思维链引导 · 与抗截断互斥")
            }
            item {
                EntryRow("写作风格 · 轻小说", "user", Color(0xFFD4A017), true, "文风注入 · 注入 user")
            }
        }
    }
}

// ───────────────────────── 记忆页 ─────────────────────────

@Composable
fun MemoryPage(onOpenDrawer: () -> Unit) {
    PageScaffold("记忆系统", LuzzyIcons.Memory, onOpenDrawer, actions = {
        HeaderAction(LuzzyIcons.Trash, "清空当前模式记忆", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
        Spacer(Modifier.width(8.dp))
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SettingCard {
                    SettingRow("记忆引擎", "已开启 · 向量模式", trailing = { LuzzySwitch(true) })
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = "向量模式",
                                fontSize = 12.sp,
                                fontFamily = LuzzyFonts.Body,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = "总结模式",
                                fontSize = 12.sp,
                                fontFamily = LuzzyFonts.Body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                SettingCard {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        StatMini("总分片", "24")
                        StatMini("覆盖轮数", "18")
                        StatMini("召回阈值", "0.45")
                    }
                }
            }
            item { SectionTitle("检索结果 · 2") }
            item {
                SettingCard(Modifier.padding(bottom = 8.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            BadgeChip("第 17 轮", MaterialTheme.colorScheme.primary)
                            BadgeChip("相关度 91%", MaterialTheme.colorScheme.tertiary)
                        }
                        Text(
                            text = "Vanio 把苹果藏进兜里，冲你勾了勾手指；钟楼顶上的红苹果树是只有恶魔找得到的秘密。",
                            fontSize = 12.5.sp, lineHeight = 19.sp,
                            fontFamily = LuzzyFonts.Body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                SettingCard {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            BadgeChip("第 12 轮", MaterialTheme.colorScheme.primary)
                            BadgeChip("相关度 87%", MaterialTheme.colorScheme.tertiary)
                        }
                        Text(
                            text = "灯塔守夜人 Luna 把备用灯借给雾中迷航的你；航道浮标从灯塔脚下一直排到远方。",
                            fontSize = 12.5.sp, lineHeight = 19.sp,
                            fontFamily = LuzzyFonts.Body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.StatMini(label: String, value: String) {
    Column {
        Text(
            text = value,
            fontSize = 20.sp,
            fontFamily = LuzzyFonts.Lora,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontFamily = LuzzyFonts.Body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ───────────────────────── 用量页 ─────────────────────────

@Composable
fun UsagePage(onOpenDrawer: () -> Unit) {
    PageScaffold("用量统计", LuzzyIcons.ChartBar, onOpenDrawer, actions = {
        HeaderAction(LuzzyIcons.Trash, "清空记录", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
        Spacer(Modifier.width(8.dp))
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("全部", "主对话", "记忆系统", "变量分析").forEachIndexed { i, s ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (i == 0) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainer,
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = s,
                                fontSize = 12.sp,
                                fontFamily = LuzzyFonts.Body,
                                color = if (i == 0) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                SettingCard {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = "总用量",
                            fontSize = 12.sp,
                            fontFamily = LuzzyFonts.Body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "1,284,506 tokens",
                            fontSize = 24.sp,
                            fontFamily = LuzzyFonts.Lora,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        // 静态趋势占位折线（P1 示意；P5 接真数据画 Canvas 折线）
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .height(96.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        ) {
                            Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                repeat(3) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                    )
                                }
                            }
                        }
                        Text(
                            text = "粒度 日 / 周 / 月 · 供应商筛选（P1 静态占位）",
                            fontSize = 11.sp,
                            fontFamily = LuzzyFonts.Body,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            item { SectionTitle("请求日志 · 最近 2 条") }
            item { UsageLogRow("[STA1N] DeepSeek-V4-Pro", "主对话", "6.2s · 42 tok/s") }
            item { UsageLogRow("[STA1N] DeepSeek-V4-Flash", "记忆系统", "1.1s · 88 tok/s") }
        }
    }
}

@Composable
private fun UsageLogRow(model: String, type: String, meta: String) {
    SettingCard(Modifier.padding(bottom = 8.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = model,
                    fontSize = 13.sp,
                    fontFamily = LuzzyFonts.Body,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                BadgeChip(type, MaterialTheme.colorScheme.primary)
            }
            Text(
                text = meta,
                fontSize = 11.5.sp,
                fontFamily = LuzzyFonts.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf("输入 12,004", "输出 1,842", "¥ 0.031").forEach {
                    Text(
                        text = it,
                        fontSize = 11.sp,
                        fontFamily = LuzzyFonts.Body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ───────────────────────── 设置页 ─────────────────────────

@Composable
fun SettingsPage(onOpenDrawer: () -> Unit) {
    PageScaffold("设置", LuzzyIcons.Settings, onOpenDrawer) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SettingCard {
                    Column {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(Brush.horizontalGradient(listOf(Color(0xFF9A5638), Color(0xFF723520)))),
                        )
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "用户设置",
                                fontSize = 15.sp,
                                fontFamily = LuzzyFonts.Body,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            SettingRow("角色名", null, trailing = {})
                            SettingRow("叙事视角", "第二人称", trailing = {})
                            SettingRow("偏好设定", "已填写 120 字", trailing = {})
                        }
                    }
                }
            }
            item {
                SettingCard {
                    Column {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(Brush.horizontalGradient(listOf(Color(0xFF3E6B6E), Color(0xFF2F5D50)))),
                        )
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "API 连接",
                                fontSize = 15.sp,
                                fontFamily = LuzzyFonts.Body,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            SettingRow("API 提供商", "[STA1N] DeepSeek · 已连接", trailing = {})
                            SettingRow("聊天模型", "已配置 3 / 3 个槽位", leadingIconRes = LuzzyIcons.Search)
                            SettingRow("识图模型", "未配置", leadingIconRes = LuzzyIcons.Search)
                            SettingRow("刷新可用模型", null, leadingIconRes = LuzzyIcons.Refresh)
                        }
                    }
                }
            }
            item {
                SettingCard {
                    Column {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(Brush.horizontalGradient(listOf(Color(0xFF54426B), Color(0xFF3D3352)))),
                        )
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "高级设置",
                                fontSize = 15.sp,
                                fontFamily = LuzzyFonts.Body,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            SettingRow("使用封面背景", null, trailing = { LuzzySwitch(true) })
                            SettingRow("沉浸模式", null, trailing = { LuzzySwitch(true) })
                            SettingRow("显示最新用量", null, trailing = { LuzzySwitch(false) })
                            SettingRow("文风过滤", null, trailing = { LuzzySwitch(false) })
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────── 关于页 ─────────────────────────

@Composable
fun AboutPage(onOpenDrawer: () -> Unit) {
    PageScaffold("关于", LuzzyIcons.Info, onOpenDrawer) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.luzzy_logo),
                        contentDescription = "LuzzyRP logo",
                        modifier = Modifier.size(84.dp).clip(CircleShape),
                    )
                    Text(
                        text = "LuzzyRP",
                        fontFamily = LuzzyFonts.Lora,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "每次对话，都像一本有你的小说。",
                        fontSize = 12.sp,
                        fontFamily = LuzzyFonts.Body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                SettingCard {
                    SettingRow("版本", "v2.0.0（P1 静态稿 · versionCode 见构建）", leadingIconRes = LuzzyIcons.Info)
                    SettingRow("上游基线", "RP-Hub 1.9.3（同步已退役）", leadingIconRes = LuzzyIcons.ExternalLink)
                    SettingRow(
                        "许可",
                        "自有代码 AGPL-3.0 · 上游资产 CC BY-NC 4.0 · 仅侧载分发",
                        leadingIconRes = LuzzyIcons.Info,
                    )
                }
            }
            item {
                SettingCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "v3.0.0 — 全面转 Jetpack Compose（开发中）",
                            fontSize = 14.sp,
                            fontFamily = LuzzyFonts.Body,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        changelogBullets.forEach {
                            Text(
                                text = "·  $it",
                                fontSize = 12.5.sp,
                                lineHeight = 19.sp,
                                fontFamily = LuzzyFonts.Body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "完整更新日志见仓库 CHANGELOG.md（P5 接入真实数据源 ext/luzzy-changelog.js）",
                            fontSize = 11.sp,
                            fontFamily = LuzzyFonts.Body,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
            item {
                Text(
                    text = "基于 RP-Hub by STA1N156 · AGPL-3.0（自有）+ CC BY-NC 4.0（上游资产）",
                    fontSize = 11.sp,
                    fontFamily = LuzzyFonts.Body,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}

private val changelogBullets = listOf(
    "P1 空壳可跑：Compose 座 + HCT 主题 + 字体 + 沉浸聊天页",
    "雾纸玻璃气泡 + 假流式 + 思考卡节点（复刻原项目聊天页）",
    "全页面静态稿 + 页面切换转场 + 关于页（本版新增）",
    "上游同步退役：基线定格 1.9.3，自有代码转 AGPL-3.0",
)