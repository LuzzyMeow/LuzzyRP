package com.luzzymeow.luzzyrp.ui.pages

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.luzzymeow.luzzyrp.chat.PromptAssembler
import com.luzzymeow.luzzyrp.chat.UsageAggregate
import com.luzzymeow.luzzyrp.chat.UsageFormat
import com.luzzymeow.luzzyrp.data.legacy.MigrationReport
import com.luzzymeow.luzzyrp.data.settings.SettingsBootstrap
import com.luzzymeow.luzzyrp.data.store.DatabaseProvider
import com.luzzymeow.luzzyrp.data.store.LuzzyStore
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.markdown.MarkdownText
import com.luzzymeow.luzzyrp.ui.pages.common.BadgeChip
import com.luzzymeow.luzzyrp.ui.pages.common.BandCard
import com.luzzymeow.luzzyrp.ui.pages.common.EmptyState
import com.luzzymeow.luzzyrp.ui.pages.common.LuzzySwitch
import com.luzzymeow.luzzyrp.ui.pages.common.PageHeader
import com.luzzymeow.luzzyrp.ui.pages.common.PageScaffold
import com.luzzymeow.luzzyrp.ui.pages.common.SectionTitle
import com.luzzymeow.luzzyrp.ui.pages.common.SettingCard
import com.luzzymeow.luzzyrp.ui.pages.common.SettingRow
import com.luzzymeow.luzzyrp.ui.pages.common.StatMini
import com.luzzymeow.luzzyrp.ui.pages.common.ThinDivider
import com.luzzymeow.luzzyrp.ui.pages.common.bandTone
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * P1 静态稿 ×7（DESIGN-compose §13.1；上游各页 IA 翻译，假数据）。
 * 统一骨架：Scaffold(PageHeader) + LazyColumn 卡片流；底色 surface 实底。
 *
 * 骨架本体已提到 `ui.pages.common.PageScaffold`（2026-09-15）：记忆页重建需要同一副骨架，
 * 每个页面各写一份必然出现「改一处忘一处」。本文件的私有副本已删除。
 */

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

    // 页头不放装饰性图标：此前的「检索 / +」是无点击的占位（假按钮，P6 验收撤下）——
    // 导入走「设置 → 数据 · 导入与导出」，入口在空态里写明。
    PageScaffold("角色卡管理", LuzzyIcons.Assistants, onOpenDrawer) { padding ->
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
                    EmptyState(
                        iconRes = LuzzyIcons.Assistants,
                        title = "库里还没有角色卡",
                        supporting = "从「设置 → 数据 · 导入与导出」导入（PNG / JSON）；" +
                            "导入后这里会列出，并带上真实头像。",
                    )
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

/** API 连接卡的一行真实状态（由 [SettingsData] 注入取数；显示语义在设置页内归一）。 */
data class ApiStatusRow(
    val configured: Boolean,
    val model: String,
    val endpoint: String,
)

/**
 * 设置页的数据接线（宿主注入；测试注入 fake）。
 * 取数是 suspend lambda：设置页进入时各取一次。不注入（null）时对应行显示占位——
 * **绝不显示编造的数据**（P1 静态稿的假行已在本轮全部撤掉）。
 */
class SettingsData(
    val apiStatus: suspend () -> ApiStatusRow?,
    val userProfile: suspend () -> PromptAssembler.UserView?,
    val styleFilterEnabled: () -> Boolean,
    val onStyleFilterChange: (Boolean) -> Unit,
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
    /** 真实数据接线（用户设置/API 连接/文风过滤开关）；null = 显示占位。 */
    data: SettingsData? = null,
) {
    var userView by remember { mutableStateOf<PromptAssembler.UserView?>(null) }
    var apiStatus by remember { mutableStateOf<ApiStatusRow?>(null) }
    LaunchedEffect(data) {
        data ?: return@LaunchedEffect
        runCatching { data.userProfile() }.onSuccess { userView = it }
        runCatching { data.apiStatus() }.onSuccess { apiStatus = it }
    }
    var styleFilter by remember(data) { mutableStateOf(data?.styleFilterEnabled?.invoke() ?: false) }
    val reportScope = rememberCoroutineScope()
    var showReport by remember { mutableStateOf(false) }
    var reportState by remember { mutableStateOf<Result<MigrationReport?>?>(null) }
    PageScaffold("设置", LuzzyIcons.Settings, onOpenDrawer) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("settings_list"),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                BandCard(
                    title = "用户设置",
                    iconRes = LuzzyIcons.Assistants,
                    bandFirst = MaterialTheme.colorScheme.primary,
                    bandSecond = bandTone(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.surface),
                    bandContent = MaterialTheme.colorScheme.onPrimary,
                    avatar = {
                        // 头像叠压头带下沿（方向 A 的品牌签名位）；没名字时用「鹿」
                        Box(
                            Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .border(3.dp, MaterialTheme.colorScheme.surfaceContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = userView?.name?.takeIf { it.isNotBlank() }?.take(1) ?: "鹿",
                                fontFamily = LuzzyFonts.Lora,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                ) {
                    SettingRow(
                        "角色名",
                        userView?.name?.takeIf { it.isNotBlank() } ?: "未设置",
                    )
                    ThinDivider()
                    SettingRow(
                        "偏好设定",
                        userView
                            ?.takeIf { it.description.isNotBlank() || it.preferences.isNotBlank() }
                            ?.let { "描述 ${it.description.length} 字 · 偏好 ${it.preferences.length} 字" }
                            ?: "未填写",
                    )
                }
            }
            item {
                BandCard(
                    title = "API 连接",
                    iconRes = LuzzyIcons.Chip,
                    bandFirst = MaterialTheme.colorScheme.secondary,
                    bandSecond = bandTone(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.surface),
                    bandContent = MaterialTheme.colorScheme.onSecondary,
                ) {
                    val status = apiStatus
                    SettingRow(
                        "API 提供商",
                        when {
                            status == null -> "—"
                            status.configured -> "已配置"
                            else -> "未配置"
                        },
                    )
                    ThinDivider()
                    SettingRow(
                        "聊天模型",
                        apiStatus?.model?.takeIf { it.isNotBlank() } ?: "未配置",
                    )
                    ThinDivider()
                    SettingRow(
                        "端点",
                        apiStatus?.endpoint?.takeIf { it.isNotBlank() } ?: "—",
                    )
                    Text(
                        text = "模型与供应商在聊天页输入岛的「模型」面板配置",
                        fontSize = 11.sp,
                        fontFamily = LuzzyFonts.Body,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            item {
                BandCard(
                    title = "数据 · 导入与导出",
                    iconRes = LuzzyIcons.Download,
                    bandFirst = MaterialTheme.colorScheme.tertiary,
                    bandSecond = bandTone(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.surface),
                    bandContent = MaterialTheme.colorScheme.onTertiary,
                ) {
                    SettingRow("导出预设（presets.json）", onClick = transfer.exportPresets)
                    ThinDivider()
                    SettingRow(
                        "导出世界书（world_info.json）",
                        "仅全局书；角色绑定的条目随角色卡导出",
                        onClick = transfer.exportWorldInfo,
                    )
                    ThinDivider()
                    SettingRow("导出角色卡（characters.json）", "正文与头像引用原样携带", onClick = transfer.exportCharacters)
                    ThinDivider()
                    SettingRow("导入预设", "整组覆盖现有预设", onClick = transfer.importPresets)
                    ThinDivider()
                    SettingRow("导入世界书", "整组覆盖全局书", onClick = transfer.importWorldInfo)
                    ThinDivider()
                    SettingRow("导入角色卡", "同卡覆盖；外来卡新建", onClick = transfer.importCharacters)
                    if (migrationReportProvider != null) {
                        ThinDivider()
                        SettingRow("迁移报告", "从旧版搬来了什么", onClick = {
                            showReport = true
                            reportState = null
                            val provider = migrationReportProvider ?: return@SettingRow
                            reportScope.launch { reportState = runCatching { provider() } }
                        })
                    }
                }
            }
            item {
                BandCard(
                    title = "高级设置",
                    iconRes = LuzzyIcons.Sliders,
                    // 高级卡用 primaryContainer（与用户卡同族但更沉一档）——色相仍只来自 M3 role
                    bandFirst = MaterialTheme.colorScheme.primaryContainer,
                    bandSecond = bandTone(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface),
                    bandContent = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    SectionTitle("显示", Modifier.padding(start = 16.dp, top = 6.dp))
                    // 参数块（方向 A 的承载件：底色块 + 内嵌控件）
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        FontSizeSliderRow(
                            fontScale = fontScale,
                            onChange = onFontScaleChange,
                            onFinished = onFontScaleFinished,
                        )
                    }
                    SettingRow(
                        "文风过滤",
                        "删改 AI 措辞（提示词侧、显示、落库三处同源；照上游默认开）",
                        trailing = {
                            LuzzySwitch(
                                checked = styleFilter,
                                onCheckedChange = { next ->
                                    styleFilter = next
                                    data?.onStyleFilterChange?.invoke(next)
                                },
                                label = "文风过滤",
                            )
                        },
                    )
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
                    SettingRow("版本", "${BuildConfig.VERSION_NAME}（versionCode ${BuildConfig.VERSION_CODE}）", leadingIconRes = LuzzyIcons.Info)
                    SettingRow("上游基线", "RP-Hub 1.9.3（同步已退役）", leadingIconRes = LuzzyIcons.ExternalLink)
                    SettingRow(
                        "许可",
                        "自有代码 AGPL-3.0 · 上游资产 CC BY-NC 4.0 · 仅侧载分发",
                        leadingIconRes = LuzzyIcons.Info,
                    )
                }
            }
            item {
                AboutChangelogCard()
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

/**
 * 关于页「更新日志」卡（P6 验收修复）：真实数据源 = 构建期生成的
 * `assets/ext/luzzy-changelog.js`（硬性规定 5 的自动同步机制，构建前由
 * gen-changelog 从仓库根 CHANGELOG.md 重生成）——此处不再维护静态副本。
 *
 * 形态：版本下拉（默认最新）+ 该版本的 Markdown 正文（复用 [MarkdownText]，
 * 与聊天正文同一套渲染与记忆化）。
 */
private data class ChangelogSection(val version: String, val title: String, val body: String)

private const val CHANGELOG_ASSET = "ext/luzzy-changelog.js"
private const val CHANGELOG_MARKER = "window.LuzzyChangelog = { md: `"
private const val CHANGELOG_FOOTER = "` };"

private fun parseChangelogSections(context: Context): List<ChangelogSection> {
    val raw = context.assets.open(CHANGELOG_ASSET).bufferedReader().use { it.readText() }
    val start = raw.indexOf(CHANGELOG_MARKER)
    val end = raw.lastIndexOf(CHANGELOG_FOOTER)
    if (start < 0 || end <= start) return emptyList()
    // 生成侧的转义顺序是 \ → \\ 、` → \` 、${ → \${，逆向按反序还原
    val md = raw.substring(start + CHANGELOG_MARKER.length, end)
        .replace("\\`", "`")
        .replace("\\$", "$")
        .replace("\\\\", "\\")
    val header = Regex("(?m)^### (v\\d+\\.\\d+\\.\\d+) — (.*)$")
    val hits = header.findAll(md).toList()
    if (hits.isEmpty()) return emptyList()
    return hits.mapIndexed { i, m ->
        val bodyStart = m.range.last + 1
        val bodyEnd = hits.getOrNull(i + 1)?.range?.first ?: md.length
        ChangelogSection(
            version = m.groupValues[1],
            title = m.groupValues[2].trim(),
            body = md.substring(bodyStart, bodyEnd).trim(),
        )
    }
}

@Composable
private fun AboutChangelogCard() {
    val context = LocalContext.current
    var sections by remember { mutableStateOf<List<ChangelogSection>?>(null) }
    var selected by remember { mutableStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        sections = withContext(Dispatchers.IO) {
            runCatching { parseChangelogSections(context) }.getOrDefault(emptyList())
        }
    }
    val list = sections
    SettingCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "更新日志",
                    fontSize = 14.sp,
                    fontFamily = LuzzyFonts.Body,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (!list.isNullOrEmpty()) {
                    Box {
                        TextButton(onClick = { menuOpen = true }) {
                            Text(
                                text = list[selected].version,
                                fontSize = 13.sp,
                                fontFamily = LuzzyFonts.Body,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            list.forEachIndexed { i, section ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "${section.version} — ${section.title}",
                                            fontSize = 13.sp,
                                            fontFamily = LuzzyFonts.Body,
                                        )
                                    },
                                    onClick = {
                                        selected = i
                                        menuOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
            when {
                list == null -> Text(
                    text = "读取中…",
                    fontSize = 13.sp,
                    fontFamily = LuzzyFonts.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                list.isEmpty() -> Text(
                    text = "更新日志数据缺失（构建期生成失败；不影响使用）。",
                    fontSize = 13.sp,
                    fontFamily = LuzzyFonts.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> MarkdownText(content = list[selected].body)
            }
        }
    }
}
