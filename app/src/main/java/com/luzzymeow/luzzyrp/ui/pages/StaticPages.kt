package com.luzzymeow.luzzyrp.ui.pages

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luzzymeow.luzzyrp.BuildConfig
import com.luzzymeow.luzzyrp.R
import com.luzzymeow.luzzyrp.chat.CacheObserver
import com.luzzymeow.luzzyrp.chat.PageDataSource
import com.luzzymeow.luzzyrp.chat.UsageAggregate
import com.luzzymeow.luzzyrp.chat.UsageFormat
import com.luzzymeow.luzzyrp.data.legacy.MigrationReport
import com.luzzymeow.luzzyrp.data.settings.SettingsBootstrap
import com.luzzymeow.luzzyrp.data.store.DatabaseProvider
import com.luzzymeow.luzzyrp.data.store.LuzzyStore
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.pages.common.BadgeChip
import com.luzzymeow.luzzyrp.ui.pages.common.LuzzySwitch
import com.luzzymeow.luzzyrp.ui.pages.common.PageHeader
import com.luzzymeow.luzzyrp.ui.pages.common.SectionTitle
import com.luzzymeow.luzzyrp.ui.pages.common.SettingCard
import com.luzzymeow.luzzyrp.ui.pages.common.SettingRow
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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

/**
 * 角色卡管理页（批 C C1/C2：真列表 + 真头像）。
 *
 * [pageData] 是**测试接缝**（与 `SessionsPage`/`ChatPage` 同一约定）：不注入时自建指向
 * 设备真库的实例——不注入的话仪器化测试读到的就是「这台机器恰好装了什么」，
 * 那是隐藏耦合，本机绿换机红。
 */
@Composable
fun CharactersPage(onOpenDrawer: () -> Unit, pageData: PageDataSource? = null) {
    val context = LocalContext.current
    val source = remember(pageData) {
        pageData ?: PageDataSource(LuzzyStore(DatabaseProvider.luzzy(context.applicationContext)))
    }
    // null = 还没读完（首帧不显示「0 张卡」——那会让人以为数据丢了）
    var rows by remember { mutableStateOf<List<PageDataSource.CharacterRow>?>(null) }
    LaunchedEffect(source) {
        val active = source.activeCharacter()
        rows = source.characters(active)
    }

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
                    text = rows?.let { "${it.size} 张角色卡" } ?: "正在读取…",
                    fontSize = 12.sp,
                    fontFamily = LuzzyFonts.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                )
            }
            when {
                rows == null -> Unit // 首帧：只有上面那行「正在读取…」
                rows!!.isEmpty() -> item {
                    SettingCard {
                        Text(
                            text = "库里还没有角色卡。导入角色卡后会在这里列出，并带上真实头像。",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            fontFamily = LuzzyFonts.Body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }

                else -> items(rows!!, key = { it.uuid }) { row ->
                    CharacterCard(
                        name = row.name,
                        desc = if (row.isActive) "当前角色" else "未启用",
                        inUse = row.isActive,
                        avatarPath = row.avatarPath,
                    )
                }
            }
        }
    }
}

/**
 * 角色卡行（批 C C2）：**有真头像就显示真头像**，否则回落既有占位立绘。
 *
 * 为什么保留占位立绘而不是画一块纯色：它在暗色主题下提供了「卡面」这一层的视觉结构，
 * 而真头像（旧数据里多是 `data:` 内联图）通常是小方图，直接铺满会糊。
 * 于是这里把真头像裁圆放在左下角作为**身份标识**，占位立绘继续承担卡面。
 */
@Composable
private fun CharacterCard(
    name: String,
    desc: String,
    inUse: Boolean,
    avatarPath: String? = null,
) {
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
                // 真头像：解码失败或没有时这个组件自己回落成首字 monogram（见 AvatarImage）
                AvatarImage(name = name, avatarPath = avatarPath, size = 26.dp)
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
//
// 2026-09-13（W2）**已迁出**：真页面在 `ui/pages/world/WorldInfoPage.kt`（真数据 + 编辑器）。
// 这里的静态稿删除，不做「两处定义」的过渡态——旧稿留着就会有人改错文件。


// ───────────────────────── 预设页 ─────────────────────────
//
// 2026-09-13（W4）**已迁出**：真页面在 `ui/pages/preset/PresetsPage.kt`（真数据 + 编辑器）。
// 静态稿删除，不留两处定义。


// ───────────────────────── 记忆页 ─────────────────────────

/**
 * 记忆系统页（批 C C1：真统计）。
 *
 * 页面上原来那三个数字（总分片 24 / 覆盖轮数 18 / 召回阈值 0.45）是**假数据**；
 * 现在前两个来自真库（[PageDataSource.memory]），第三个是**设置**不是库数据。
 *
 * **本轮不做**（如实登记）：清空按钮的真执行（那是写操作，需要真机验证；
 * 无真机时「点了会怎样」无法判定）；召回阈值的设置绑定（属设置页的活）。
 */
@Composable
fun MemoryPage(onOpenDrawer: () -> Unit, pageData: PageDataSource? = null) {
    val context = LocalContext.current
    val source = remember(pageData) {
        pageData ?: PageDataSource(LuzzyStore(DatabaseProvider.luzzy(context.applicationContext)))
    }
    var stats by remember { mutableStateOf<PageDataSource.MemoryPair?>(null) }
    LaunchedEffect(source) {
        // ⚠️ 作用域必须是「角色 × **当前分支**」：用户的活跃会话常常不在主线上，
        //    只按角色取会读到主线的记忆 → 数字静默不对（见 PageDataSource.memory 的说明）
        val uuid = source.activeCharacter()
        stats = source.memory(uuid, uuid?.let { source.activeBranch(it) })
    }
    val vector = stats?.vector
    val classic = stats?.classic

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
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "向量分片",
                            fontSize = 13.sp,
                            fontFamily = LuzzyFonts.Body,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            // 未读完时显示「—」而不是 0：0 会被读成「一条记忆都没有」
                            StatMini("总分片", vector?.shards?.toString() ?: "—")
                            StatMini("覆盖轮数", vector?.coveredTurns?.toString() ?: "—")
                            StatMini("已嵌入", vector?.embeddedShards?.toString() ?: "—")
                        }
                        Text(
                            text = vector?.let {
                                "合计 ${UsageFormat.grouped(it.totalChars)} 字 · 平均 ${it.averageChars} 字/片" +
                                    if (it.embeddingDims > 0) " · 维度 ${it.embeddingDims}" else ""
                            } ?: "正在读取…",
                            fontSize = 11.sp,
                            fontFamily = LuzzyFonts.Body,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
            item {
                SettingCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "总结记忆",
                            fontSize = 13.sp,
                            fontFamily = LuzzyFonts.Body,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            StatMini("总条数", classic?.shards?.toString() ?: "—")
                            StatMini("覆盖轮数", classic?.coveredTurns?.toString() ?: "—")
                            StatMini("最长到第", classic?.maxTurn?.toString() ?: "—")
                        }
                        Text(
                            text = classic?.let {
                                "合计 ${UsageFormat.grouped(it.totalChars)} 字 · 平均 ${it.averageChars} 字/条"
                            } ?: "正在读取…",
                            fontSize = 11.sp,
                            fontFamily = LuzzyFonts.Body,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
            if (stats != null && vector?.isEmpty == true && classic?.isEmpty == true) {
                item {
                    SettingCard {
                        Text(
                            text = "当前角色还没有记忆。对话推进到一定轮数后，记忆会在这里按分片统计出来。",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            fontFamily = LuzzyFonts.Body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
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

/**
 * 用量统计页（批 C C1：总用量与趋势接真库 + A8 的前缀缓存段）。
 *
 * 两段数据来源不同、**不要混为一谈**（页面标题已分别标注）：
 * - 「总用量 / 按天趋势」= 库里 `token_usage_history` 的**历史累计**（跨进程、跨角色）；
 * - 「前缀缓存 · 本次运行」= `CacheObserver` 的**进程内**观测（批 A 的验收指标）。
 *
 * [pageData] 是测试接缝（同 `CharactersPage`）。
 */
@Composable
fun UsagePage(onOpenDrawer: () -> Unit, pageData: PageDataSource? = null) {
    // ★ A8：本页的「前缀缓存」段是**真数据**——直接订阅观测层（A7）。
    //   它只统计不干预，所以这一页读它就等于读真实发生过的请求，不是又一处占位。
    val cache by CacheObserver.summary.collectAsState()
    val context = LocalContext.current
    val source = remember(pageData) {
        pageData ?: PageDataSource(LuzzyStore(DatabaseProvider.luzzy(context.applicationContext)))
    }
    var usage by remember { mutableStateOf<UsageAggregate.Summary?>(null) }
    LaunchedEffect(source) { usage = source.usage() }

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
                            // 真数据：迁移进来的 `token_usage_history` 聚合（批 C C1）
                            text = usage?.let { "${UsageFormat.grouped(it.totalTokens)} tokens" } ?: "—",
                            fontSize = 24.sp,
                            fontFamily = LuzzyFonts.Lora,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = usage?.let {
                                "输入 ${UsageFormat.grouped(it.inputTokens)} · 输出 ${UsageFormat.grouped(it.outputTokens)}" +
                                    " · ${it.measuredRequests} 次计入统计 / 共 ${it.requests} 次请求"
                            } ?: "正在读取…",
                            fontSize = 11.sp,
                            fontFamily = LuzzyFonts.Body,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        // 按天趋势（真数据）。空数据时给一行说明，不画空坐标系——
                        // 空轴看起来像「有数据但都是 0」，那是另一种误导。
                        val snapshot = usage
                        if (snapshot != null && snapshot.byDay.isNotEmpty()) {
                            DayTrend(snapshot.byDay)
                        } else if (snapshot != null) {
                            Text(
                                text = "还没有可统计的用量记录。发几条消息后这里会按天累计。",
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                fontFamily = LuzzyFonts.Body,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                        if (snapshot != null && snapshot.byProvider.isNotEmpty()) {
                            Text(
                                text = "供应商：" + snapshot.byProvider.joinToString(" · ") {
                                    "${it.key} ${UsageFormat.grouped(it.totalTokens)}"
                                },
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                fontFamily = LuzzyFonts.Body,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }

            // ── 前缀缓存（A7 观测层 → A8 展示）：本页唯一的**真实数据**段 ──
            item { SectionTitle("前缀缓存 · 本次运行") }
            item { CacheSummaryCard(cache) }
            if (cache.turns.isEmpty()) {
                item {
                    SettingCard {
                        Text(
                            text = "还没有请求记录。发一条消息后这里会显示：每轮与上一轮的公共前缀占比、供应商报告的缓存命中率。",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            fontFamily = LuzzyFonts.Body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            } else {
                item { SectionTitle("请求日志 · 最近 ${minOf(cache.turns.size, CacheLogRows)} 条") }
                items(cache.turns.reversed().take(CacheLogRows), key = { it.index }) { turn ->
                    CacheTurnRow(turn)
                }
            }
        }
    }
}

/** 请求日志展示的条数上限（观测层本身最多留 [CacheObserver.MAX_TURNS] 条）。 */
private const val CacheLogRows = 10

/**
 * 按天用量趋势（批 C C1 的真折线）。
 *
 * 画法刻意最简：一条折线 + 基线，无坐标轴、无网格、无图例——页面上已经有「总计」与
 * 「日期范围」两行文字承载读数，图形只负责**形状**（趋势是涨还是平）。
 * 单点数据画成一个小圆点而不是一条退化的线。
 */
@Composable
private fun DayTrend(days: List<UsageAggregate.DayBucket>) {
    val lineColor = MaterialTheme.colorScheme.primary
    val baseline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    Text(
        text = "${days.first().day} → ${days.last().day} · ${days.size} 天",
        fontSize = 11.sp,
        fontFamily = LuzzyFonts.Body,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(10.dp),
    ) {
        val max = days.maxOf { it.totalTokens }.coerceAtLeast(1)
        val stepX = if (days.size > 1) size.width / (days.size - 1) else 0f
        val yOf = { value: Int -> size.height - (value.toFloat() / max) * size.height }

        drawLine(
            color = baseline,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 1f,
        )

        if (days.size == 1) {
            drawCircle(color = lineColor, radius = 4f, center = Offset(size.width / 2f, yOf(days[0].totalTokens)))
        } else {
            for (i in 0 until days.size - 1) {
                drawLine(
                    color = lineColor,
                    start = Offset(stepX * i, yOf(days[i].totalTokens)),
                    end = Offset(stepX * (i + 1), yOf(days[i + 1].totalTokens)),
                    strokeWidth = 2.5f,
                )
            }
        }
    }
    Text(
        text = "峰值 " + UsageFormat.grouped(days.maxOf { it.totalTokens }) + " tokens/天",
        fontSize = 11.sp,
        fontFamily = LuzzyFonts.Body,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/**
 * 前缀缓存汇总卡（A8）。
 *
 * 三个数字直接对应 PLAN §7.1 的验收判据：公共前缀占比（≥0.95 达标）、
 * 命中率（cached/prompt）、缓存纪元变化次数（改设置/换模型就该 +1）。
 */
@Composable
private fun CacheSummaryCard(summary: CacheObserver.Summary) {
    SettingCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "公共前缀占比（相邻两轮）",
                    fontSize = 12.sp,
                    fontFamily = LuzzyFonts.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (summary.rounds > 1) {
                    BadgeChip(
                        text = if (summary.meetsTarget) "达标 ≥0.95" else "未达标",
                        tint = if (summary.meetsTarget) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                    )
                }
            }
            Text(
                text = cachePercent(summary.avgCommonRatio),
                fontSize = 24.sp,
                fontFamily = LuzzyFonts.Lora,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                StatMini("缓存命中率", cachePercent(summary.hitRate))
                StatMini("请求轮数", "${summary.rounds}")
                StatMini("缓存纪元变化", "${summary.headerChanges}")
            }
            Text(
                text = "命中 " + summary.cachedTokens.toString() + " / " + summary.promptTokens.toString() +
                    " tokens · 末轮公共前缀 " + cachePercent(summary.lastCommonRatio),
                fontSize = 11.sp,
                fontFamily = LuzzyFonts.Body,
                color = MaterialTheme.colorScheme.outline,
            )
            summary.lastHeaderLabel?.let { label ->
                Text(
                    text = "请求头：$label",
                    fontSize = 11.sp,
                    fontFamily = LuzzyFonts.Body,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

/** 一行请求日志：模型 + 公共前缀占比 + 命中率（全部来自真实观测）。 */
@Composable
private fun CacheTurnRow(turn: CacheObserver.Turn) {
    SettingCard(Modifier.padding(bottom = 8.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "#${turn.index} ${turn.header.model}",
                    fontSize = 13.sp,
                    fontFamily = LuzzyFonts.Body,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (turn.headerChanged) {
                    BadgeChip("缓存纪元变化", MaterialTheme.colorScheme.error)
                }
            }
            Text(
                text = "公共前缀 " + cachePercent(turn.commonRatio) +
                    " · 命中 " + cachePercent(turn.hitRate) +
                    " · ${turn.messageCount} 条消息 / ${turn.chars} 字符",
                fontSize = 11.5.sp,
                fontFamily = LuzzyFonts.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 占比文案：没有对比对象/未上报时如实写「无数据」，不编一个 0%。 */
private fun cachePercent(value: Double?): String =
    if (value == null) "无数据" else "%.4f".format(value)

// ───────────────────────── 设置页 ─────────────────────────

/**
 * 设置页「数据 · 导入与导出」的六个动作回调（D2）。
 * SAF launcher 在宿主（ComposeActivity）；这里只发意图，结果用 Toast 反馈。
 */
data class TransferActions(
    val exportPresets: () -> Unit = {},
    val exportWorldInfo: () -> Unit = {},
    val exportCharacters: () -> Unit = {},
    val importPresets: () -> Unit = {},
    val importWorldInfo: () -> Unit = {},
    val importCharacters: () -> Unit = {},
)

@Composable
fun SettingsPage(
    onOpenDrawer: () -> Unit,
    /** 用户字号缩放（D1；宿主持有，改动立即生效于两条字号 token 体系）。 */
    fontScale: Float = 1f,
    /** 拖动中回调（实时预览，不落盘）。 */
    onFontScaleChange: (Float) -> Unit = {},
    /** 松手回调（此时落盘）。 */
    onFontScaleFinished: () -> Unit = {},
    /** 导入导出动作（D2；默认空实现让既有调用/测试不破）。 */
    transfer: TransferActions = TransferActions(),
    /** 迁移报告取数（D3）；null = 不显示入口（旧调用/测试兼容）。 */
    migrationReportProvider: (suspend () -> MigrationReport?)? = null,
) {
    var showReport by remember { mutableStateOf(false) }
    var reportState by remember { mutableStateOf<Result<MigrationReport?>?>(null) }
    val reportScope = rememberCoroutineScope()
    PageScaffold("设置", LuzzyIcons.Settings, onOpenDrawer) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("settings_list"),
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
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "数据 · 导入与导出",
                            fontSize = 15.sp,
                            fontFamily = LuzzyFonts.Body,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        SettingRow("导出预设（presets.json）", onClick = transfer.exportPresets)
                        SettingRow(
                            "导出世界书（world_info.json）",
                            "仅全局书；角色绑定的条目随角色卡导出",
                            onClick = transfer.exportWorldInfo,
                        )
                        SettingRow("导出角色卡（characters.json）", "正文与头像引用原样携带", onClick = transfer.exportCharacters)
                        SettingRow("导入预设", "整组覆盖现有预设", onClick = transfer.importPresets)
                        SettingRow("导入世界书", "整组覆盖全局书", onClick = transfer.importWorldInfo)
                        SettingRow("导入角色卡", "同卡覆盖；外来卡新建", onClick = transfer.importCharacters)
                        if (migrationReportProvider != null) {
                            SettingRow("迁移报告", "从旧版搬来了什么", onClick = {
                                showReport = true
                                reportState = null
                                val provider = migrationReportProvider ?: return@SettingRow
                                reportScope.launch { reportState = runCatching { provider() } }
                            })
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
                            FontSizeSliderRow(
                                fontScale = fontScale,
                                onChange = onFontScaleChange,
                                onFinished = onFontScaleFinished,
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
        if (showReport) {
            AlertDialog(
                onDismissRequest = { showReport = false },
                title = {
                    Text(
                        "迁移报告",
                        fontFamily = LuzzyFonts.Body,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                text = {
                    when (val result = reportState) {
                        null -> Text("读取中…", fontSize = 13.sp, fontFamily = LuzzyFonts.Body)
                        else -> result.fold(
                            onSuccess = { report ->
                                if (report == null) {
                                    Text(
                                        "本机没有迁移记录（新装或尚未从旧版升级）。",
                                        fontSize = 13.sp,
                                        fontFamily = LuzzyFonts.Body,
                                    )
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        report.rows().forEach { (label, value) ->
                                            Row(Modifier.fillMaxWidth()) {
                                                Text(
                                                    label,
                                                    Modifier.weight(1f),
                                                    fontSize = 13.sp,
                                                    fontFamily = LuzzyFonts.Body,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                Text(
                                                    value,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = LuzzyFonts.Body,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                )
                                            }
                                        }
                                        Text(
                                            "迁移于 ${report.timeText} · 迁移对旧数据只读",
                                            fontSize = 11.sp,
                                            fontFamily = LuzzyFonts.Body,
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    }
                                }
                            },
                            onFailure = {
                                Text(
                                    "读取失败：${it.message}",
                                    fontSize = 13.sp,
                                    fontFamily = LuzzyFonts.Body,
                                )
                            },
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showReport = false }) {
                        Text("关闭", fontFamily = LuzzyFonts.Body)
                    }
                },
            )
        }
    }
}

/**
 * 字号滑杆（D1；照 `WorldInfoPage.SettingSlider` 范式）。
 *
 * 值域照上游 `fontSizes`（12–20px 整数，7 个中间步进）；显示用 px 语义（用户在旧版熟悉的东西），
 * 存储用相对缩放（`px / 16`）。拖动实时预览、松手才落盘——与 SettingSlider 的 onChange/onFinished 分工一致。
 */
@Composable
private fun FontSizeSliderRow(
    fontScale: Float,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
) {
    val px = (fontScale * SettingsBootstrap.LEGACY_PX_BASE).coerceIn(12f, 20f)
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "字号",
                fontSize = 14.sp,
                fontFamily = LuzzyFonts.Body,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${px.roundToInt()}px",
                fontSize = 14.sp,
                fontFamily = LuzzyFonts.Body,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = "正文与 Markdown 的字号（12–20，与旧版一致）；松手后保存",
            fontSize = 12.sp,
            fontFamily = LuzzyFonts.Body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = px,
            onValueChange = { onChange(it / SettingsBootstrap.LEGACY_PX_BASE) },
            valueRange = 12f..20f,
            steps = 7,
            onValueChangeFinished = onFinished,
            modifier = Modifier.fillMaxWidth(),
        )
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