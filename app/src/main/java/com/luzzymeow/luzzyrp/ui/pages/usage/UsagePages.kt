package com.luzzymeow.luzzyrp.ui.pages.usage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luzzymeow.luzzyrp.chat.CacheObserver
import com.luzzymeow.luzzyrp.chat.PageDataSource
import com.luzzymeow.luzzyrp.chat.UsageAggregate
import com.luzzymeow.luzzyrp.chat.UsageChart
import com.luzzymeow.luzzyrp.chat.UsageFormat
import com.luzzymeow.luzzyrp.data.store.DatabaseProvider
import com.luzzymeow.luzzyrp.data.store.LuzzyStore
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.pages.common.BadgeChip
import com.luzzymeow.luzzyrp.ui.pages.common.EmptyState
import com.luzzymeow.luzzyrp.ui.pages.common.PageScaffold
import com.luzzymeow.luzzyrp.ui.pages.common.SectionTitle
import com.luzzymeow.luzzyrp.ui.pages.common.SegmentChips
import com.luzzymeow.luzzyrp.ui.pages.common.SettingCard
import com.luzzymeow.luzzyrp.ui.pages.common.StatMini
import com.luzzymeow.luzzyrp.ui.pages.common.ThinDivider
import com.luzzymeow.luzzyrp.ui.theme.Loom
import com.luzzymeow.luzzyrp.ui.theme.LoomEasing
import com.luzzymeow.luzzyrp.ui.theme.LoomMotion
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts
import kotlinx.coroutines.launch

/**
 * 用量统计页（v3.2 重建）。
 *
 * ## 为什么重建
 *
 * 这一页此前有三处「看着像功能、其实没接线」的东西，而且**都不报错**：
 * 1. 顶部四个类型筛选是**画出来的方块**——高亮永远在第一项，点了没有任何反应；
 * 2. 折线只有一条按天的线，没有粒度、没有供应商、没有模型维度，也没有坐标轴；
 * 3. 页头那个红色垃圾桶是装饰图标（`HeaderAction` 不带点击），清空不可达。
 *
 * 旧版（WebView）此处是有真实现的（patch 025/037：三粒度 + 供应商筛选 + 模型多选 + 网格坐标），
 * 本次照它翻译：**数据层的算术进 [UsageChart]（纯函数、可单测），页面只做筛选状态的宿主。**
 *
 * ## 三段数据来源不同，不要混为一谈
 *
 * | 段 | 来源 | 生命周期 |
 * |---|---|---|
 * | 类型统计 / 趋势图 / 按模型汇总 | 库里 `token_usage_history` | 跨进程、跨角色（历史累计） |
 * | 请求日志 + 公共前缀 | `CacheObserver` | **本次运行**（进程内观测） |
 *
 * ## 一处**有意偏离**旧版（见 [UsageChart.usageOf]）
 *
 * 用量口径取「输入 + 输出」，不含缓存读（旧版含，会在开缓存后凭空涨一截）。
 * 好处是**图表各系列之和 == 总用量卡的大字**，页面不会自相矛盾。
 *
 * @param pageData 测试接缝（同 `MemoryPage`）。
 */
@Composable
fun UsagePage(onOpenDrawer: () -> Unit, pageData: PageDataSource? = null) {
    val cache by CacheObserver.summary.collectAsState()
    val context = LocalContext.current
    val source = remember(pageData) {
        pageData ?: PageDataSource(LuzzyStore(DatabaseProvider.luzzy(context.applicationContext)))
    }
    val scope = rememberCoroutineScope()

    var records by remember { mutableStateOf<List<UsageAggregate.Record>?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var category by remember { mutableStateOf(UsageChart.Category.ALL) }
    var granularity by remember { mutableStateOf(UsageChart.Granularity.DAY) }
    var provider by remember { mutableStateOf<String?>(null) }
    var selectedModels by remember { mutableStateOf(emptySet<String>()) }
    var confirmClear by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(source, refresh) {
        records = source.usageRecords()
        // 换数据后旧的模型勾选可能已经不在了：清空选择 = 回到「全选」，
        // 否则会停在「勾了几个不存在的系列」上，图表空着而图例还在
        selectedModels = emptySet()
    }

    val all = records ?: emptyList()
    // 时间窗的锚点：只在数据变化时取一次（每帧取 now 会让图表窗口逐帧漂移）
    val now = remember(records, refresh) { System.currentTimeMillis() }
    val filtered = remember(all, category, provider) {
        all.filter { UsageChart.matches(it, category, provider) }
    }
    val summary = remember(filtered) { UsageAggregate.summarize(filtered) }
    val providerOptions = remember(all) { UsageChart.providerOptions(all) }
    val modelOptions = remember(all, category, provider) { UsageChart.modelOptions(all, category, provider) }
    val chart = remember(filtered, granularity, category, provider, selectedModels, now) {
        UsageChart.build(
            records = filtered,
            granularity = granularity,
            category = category,
            provider = provider,
            selectedModels = selectedModels,
            now = now,
            // 过滤已在外面做过（matches），这里再传一遍是给纯函数自己兜底：
            // 少传一个筛选条件就会静默画出「没筛过」的图，而两边都过一遍的成本只是几次比较
        )
    }

    PageScaffold(
        title = "用量统计",
        iconRes = LuzzyIcons.ChartBar,
        onOpenDrawer = onOpenDrawer,
        accent = MaterialTheme.colorScheme.primary,
        actions = {
            IconButton(
                onClick = { confirmClear = true },
                enabled = all.isNotEmpty(),
            ) {
                Icon(
                    painter = painterResource(LuzzyIcons.Trash),
                    contentDescription = "清空用量记录",
                    tint = if (all.isEmpty()) {
                        MaterialTheme.colorScheme.outlineVariant
                    } else {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("usage_list"),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ① 类型筛选（真过滤：改这一项，下面的统计、图表、模型汇总一起变）
            item {
                SegmentChips(
                    labels = UsageChart.Category.entries.map { it.label },
                    selectedIndex = UsageChart.Category.entries.indexOf(category),
                    onSelect = { category = UsageChart.Category.entries[it] },
                )
            }

            // ② 总用量
            item {
                SettingCard {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = if (category == UsageChart.Category.ALL) "总用量" else "总用量 · ${category.label}",
                            fontSize = 12.sp,
                            fontFamily = LuzzyFonts.Body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${UsageFormat.grouped(summary.totalTokens)} tokens",
                            fontSize = 26.sp,
                            fontFamily = LuzzyFonts.Lora,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            StatMini("输入", UsageFormat.grouped(summary.inputTokens))
                            StatMini("输出", UsageFormat.grouped(summary.outputTokens))
                            StatMini("缓存读", UsageFormat.compact(summary.cacheReadTokens))
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            StatMini("请求", "${summary.requests}")
                            StatMini("计入统计", "${summary.measuredRequests}")
                            StatMini("缓存命中率", summary.cacheHitRate?.let { percent(it) } ?: "无数据")
                        }
                        Text(
                            text = summary.avgTokensPerRequest?.let {
                                "平均每轮 ${UsageFormat.grouped(it.toInt())} tokens · 命中缓存 ${UsageFormat.grouped(summary.cacheReadTokens)}"
                            } ?: "还没有可统计的用量记录。发几条消息后这里会按天累计。",
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            fontFamily = LuzzyFonts.Body,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            // ③ 趋势图（真三粒度 + 供应商 + 模型多选）
            item { SectionTitle("用量趋势") }
            item {
                SettingCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "窗口",
                                fontSize = 12.sp,
                                fontFamily = LuzzyFonts.Body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(48.dp),
                            )
                            SegmentChips(
                                labels = UsageChart.Granularity.entries.map { it.label },
                                selectedIndex = UsageChart.Granularity.entries.indexOf(granularity),
                                onSelect = { granularity = UsageChart.Granularity.entries[it] },
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "${chart.buckets.size} 格 · 峰值 ${UsageFormat.compact(chart.peak)}",
                                fontSize = 11.sp,
                                fontFamily = LuzzyFonts.Body,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        if (providerOptions.size > 2) {
                            ChipStrip(
                                title = "供应商",
                                chips = providerOptions.map { (value, label) ->
                                    ChipOption(label, value == provider) { provider = value }
                                },
                            )
                        }
                        if (modelOptions.size > 1) {
                            ChipStrip(
                                title = "模型",
                                chips = modelOptions.mapIndexed { index, (key, label) ->
                                    ChipOption(
                                        label = label,
                                        selected = selectedModels.isEmpty() || key in selectedModels,
                                        color = seriesColor(index),
                                    ) {
                                        selectedModels = if (key in selectedModels) {
                                            selectedModels - key
                                        } else {
                                            selectedModels + key
                                        }
                                    }
                                },
                                // 「全选 / 清空」是给可达性用的非拖拽替代：模型多起来以后
                                // 逐个点太慢，而且用户需要一眼看出「现在到底勾了几个」
                                trailing = if (selectedModels.isEmpty()) {
                                    "全选（默认）"
                                } else {
                                    "已选 ${selectedModels.size}/${modelOptions.size} · 点此全选"
                                },
                                onTrailing = { selectedModels = emptySet() },
                            )
                        }
                        if (chart.isEmpty) {
                            Text(
                                text = "这个筛选下没有可画的记录。换个分类或粒度看看；" +
                                    "也可能是这段窗口内确实没发过请求。",
                                fontSize = 11.5.sp,
                                lineHeight = 17.sp,
                                fontFamily = LuzzyFonts.Body,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        } else {
                            UsageLineChart(chart)
                            SeriesLegend(chart)
                        }
                    }
                }
            }

            // ④ 按模型汇总（真数据：summary.byModel）
            if (summary.byModel.isNotEmpty()) {
                item { SectionTitle("按模型汇总") }
                items(summary.byModel, key = { "model-${it.key}" }) { bucket ->
                    SettingCard {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = bucket.key,
                                fontSize = 13.sp,
                                fontFamily = LuzzyFonts.Body,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${bucket.requests} 次",
                                fontSize = 11.5.sp,
                                fontFamily = LuzzyFonts.Body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = UsageFormat.grouped(bucket.totalTokens),
                                fontSize = 13.sp,
                                fontFamily = LuzzyFonts.Lora,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        ThinDivider()
                        Text(
                            text = "缓存命中率 " + (bucket.cacheHitRate?.let { percent(it) } ?: "未上报"),
                            fontSize = 11.sp,
                            fontFamily = LuzzyFonts.Body,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            // ⑤ 前缀缓存（A7 观测层 → A8 展示）：进程内观测，与历史累计不是一回事
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

            if (all.isEmpty()) {
                item {
                    EmptyState(
                        iconRes = LuzzyIcons.ChartBar,
                        title = "还没有用量记录",
                        supporting = "记录在每次请求结束后由应用写入（跨角色累计）；" +
                            "旧版数据经迁移导入后也会出现在这里。",
                    )
                }
            }
            message?.let { text ->
                item {
                    Text(
                        text = text,
                        fontSize = 12.sp,
                        fontFamily = LuzzyFonts.Body,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }

    if (confirmClear) {
        com.luzzymeow.luzzyrp.ui.pages.common.LoomConfirmDialog(
            title = "清空用量记录？",
            text = "将删除全部 ${all.size} 条用量记录（跨角色累计，含迁移进来的旧记录）。\n" +
                "会话、记忆、世界书不受影响。\n\n此操作不可恢复。",
            confirmLabel = "清空",
            onConfirm = {
                confirmClear = false
                scope.launch {
                    val removed = source.clearUsage()
                    message = if (removed > 0) "已清空 $removed 条用量记录" else "没有可清空的记录"
                    refresh++
                }
            },
            onDismiss = { confirmClear = false },
        )
    }
}

/** 请求日志展示的条数上限（观测层本身最多留 `CacheObserver.MAX_TURNS` 条）。 */
private const val CacheLogRows = 10

/** 百分比文案（0~1 → `87.3%`）。 */
private fun percent(value: Double): String = "%.1f%%".format(value * 100)

/**
 * 图表分类色板（**数据色**，DESIGN-compose §33）。
 *
 * 前三色取主题 role，其余是固定点缀色（金 / 绿 / 红）——与旧版折线图的调色板同源。
 * 曲折线必须彼此可辨，全用同一色相的系统色是做不到的；所以这里**明确登记**一组数据色，
 * 而不是临场在每个页面里各挑一个。
 */
@Composable
private fun seriesColor(rank: Int): Color = when (rank) {
    0 -> MaterialTheme.colorScheme.primary
    1 -> Color(0xFFD4A017)
    2 -> MaterialTheme.colorScheme.tertiary
    3 -> Color(0xFF5DB872)
    4 -> MaterialTheme.colorScheme.secondary
    5 -> Color(0xFFC64545)
    6 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    else -> MaterialTheme.colorScheme.outline
}

/** 一条可勾选的筛选芯片。 */
private data class ChipOption(
    val label: String,
    val selected: Boolean,
    val color: Color? = null,
    val onClick: () -> Unit,
)

/**
 * 横向可滚动的芯片条（供应商 / 模型）。
 *
 * 用横向滚动而不是自动换行：芯片数量随用户配置增长，换行会让卡片高度不可预测（滚动位置跳动），
 * 而横向滚动的高度是恒定的一行。
 */
@Composable
private fun ChipStrip(
    title: String,
    chips: List<ChipOption>,
    trailing: String? = null,
    onTrailing: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontFamily = LuzzyFonts.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (trailing != null && onTrailing != null) {
                Text(
                    text = trailing,
                    fontSize = 11.sp,
                    fontFamily = LuzzyFonts.Body,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onTrailing)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            chips.forEach { chip ->
                val bg by androidx.compose.animation.animateColorAsState(
                    targetValue = if (chip.selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    animationSpec = com.luzzymeow.luzzyrp.ui.theme.loomSpring(),
                    label = "usage-chip-bg",
                )
                val fg by androidx.compose.animation.animateColorAsState(
                    targetValue = if (chip.selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = com.luzzymeow.luzzyrp.ui.theme.loomSpring(),
                    label = "usage-chip-fg",
                )
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(bg)
                        .clickable(onClick = chip.onClick)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    chip.color?.let { color ->
                        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                    }
                    Text(
                        text = chip.label,
                        fontSize = 11.5.sp,
                        fontFamily = LuzzyFonts.Body,
                        color = fg,
                    )
                }
            }
        }
    }
}

/**
 * 用量折线图（每格一条线 + 网格 + 纵轴刻度；v3.1 Loom 化：渐变面积填充 + 绘制入场）。
 *
 * 纵轴刻度与横轴标签用**布局**而不是 Canvas 里写字：Compose 的 Canvas 文本要
 * `TextMeasurer`，而刻度文本的排版（对齐、字号）用现成的 `Text` 更省事也更一致。
 * 坐标轴留白固定（左侧 44dp 给刻度），与旧版 SVG 的 `left: 44` 是同一个数。
 *
 * **绘制入场**：expressive 320ms 按 x 轴裁剪推进（0→1）；减弱动效时恒为终态。
 * 面积填充只在**首条系列**下画一层 primary 渐变（22%→透明），多系列时叠加会脏，
 * 故只给 rank 0 的系列画。
 */
@Composable
private fun UsageLineChart(chart: UsageChart.Data) {
    val gridColor = Loom.current.hairline
    val axisColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val axisLabelColor = MaterialTheme.colorScheme.outline
    val colors = chart.series.map { seriesColor(it.rank) }
    // 三条刻度：0 / 中 / 峰（够定位形状，又不至于把图糊满）
    val ticks = listOf(chart.peak, chart.peak / 2, 0)
    // 绘制进度（1 = 完整）；减弱动效时直接终态
    val reduce = com.luzzymeow.luzzyrp.ui.rememberReduceMotion()
    var played by remember { mutableStateOf(reduce) }
    LaunchedEffect(chart) { played = true }
    val progress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (played) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(
            com.luzzymeow.luzzyrp.ui.scaledDuration(com.luzzymeow.luzzyrp.ui.theme.LoomMotion.ExpressiveMs, reduce),
            easing = com.luzzymeow.luzzyrp.ui.theme.LoomEasing.Page,
        ),
        label = "usage-draw",
    )
    val areaTop = MaterialTheme.colorScheme.primary

    Row(Modifier.fillMaxWidth().height(150.dp)) {
        Column(
            Modifier.width(44.dp).fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End,
        ) {
            ticks.forEach { value ->
                Text(
                    text = UsageFormat.compact(value),
                    fontSize = 9.sp,
                    fontFamily = LuzzyFonts.Body,
                    color = axisLabelColor,
                )
            }
        }
        Canvas(
            Modifier
                .weight(1f)
                .fillMaxSize()
                .padding(top = 2.dp, bottom = 2.dp),
        ) {
            // **按格定位**：一格一个点，点的横坐标是格心（`(index + 0.5) * cellW`），
            // 竖网格线画在格与格的边界上。
            //
            // 第一版是按「点均分整宽」（`index / (n-1) * width`）画的，配上下面的标签行就错位了：
            // 标签用 Box 对齐铺开，中间那几个全都落在同一个中心点上叠成一团
            // （截图里 `9/1▮` 就是这个重叠）。格心制让**点、网格线、标签**三者用同一套坐标，
            // 且标签行可以用等宽格自然铺开，不需要猜位置。
            val cellW = if (chart.buckets.isEmpty()) size.width else size.width / chart.buckets.size
            val xOf = { index: Int -> (index + 0.5f) * cellW }
            val yOf = { value: Int -> size.height - (value.toFloat() / chart.peak) * size.height }

            // 网格（含 0 基线）
            ticks.forEach { tick ->
                val y = yOf(tick)
                drawLine(
                    color = if (tick == 0) axisColor else gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
            }
            // 每格一条竖线：把「这一格是几点」的读数落到具体位置上，
            // 否则横轴标签和图上的点对不上（尤其在 24 格时）
            for (index in 0..chart.buckets.size) {
                val x = index * cellW
                drawLine(
                    color = gridColor.copy(alpha = 0.25f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f,
                )
            }
            // 绘制进度裁剪（从左往右推进）
            clipRect(right = size.width * progress) {
                // 首系列的渐变面积（primary 22% → 透明）
                chart.series.firstOrNull()?.let { first ->
                    if (first.totals.isNotEmpty()) {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(0f, size.height)
                            val points = first.totals.mapIndexed { index, value ->
                                Offset(xOf(index), yOf(value))
                            }
                            points.forEach { lineTo(it.x, it.y) }
                            lineTo(points.lastOrNull()?.x ?: 0f, size.height)
                            close()
                        }
                        drawPath(
                            path = path,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    areaTop.copy(alpha = 0.22f),
                                    Color.Transparent,
                                ),
                            ),
                        )
                    }
                }
                // 曲线与数据点
                chart.series.forEachIndexed { seriesIndex, series ->
                    val color = colors[seriesIndex]
                    for (index in 0 until series.totals.size - 1) {
                        drawLine(
                            color = color,
                            start = Offset(xOf(index), yOf(series.totals[index])),
                            end = Offset(xOf(index + 1), yOf(series.totals[index + 1])),
                            strokeWidth = 2.2f,
                        )
                    }
                    series.totals.forEachIndexed { index, value ->
                        if (value > 0) {
                            drawCircle(color = color, radius = 2.6f, center = Offset(xOf(index), yOf(value)))
                        }
                    }
                }
            }
        }
    }
    // 横轴标签：**每格一个等宽格**，标签落在格心；格数多时只标首、四分位、尾
    // （24 格全标会糊成一团，而且格子只有 ~14dp 宽，字根本放不下）
    val labels = chart.buckets.map { it.label }
    val shown = when {
        labels.size <= 8 -> labels.indices.toSet()
        else -> setOf(0, labels.size / 4, labels.size / 2, labels.size * 3 / 4, labels.size - 1)
    }
    Row(Modifier.fillMaxWidth().padding(start = 44.dp, top = 4.dp)) {
        chart.buckets.forEachIndexed { index, bucket ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (index in shown) {
                    Text(
                        text = bucket.label,
                        fontSize = 9.sp,
                        fontFamily = LuzzyFonts.Body,
                        color = axisLabelColor,
                    )
                }
            }
        }
    }
}

/** 图例（颜色 + 名称 + 该系列总量）。 */
@Composable
private fun SeriesLegend(chart: UsageChart.Data) {
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        chart.series.forEach { series ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(seriesColor(series.rank)))
                Text(
                    text = series.label,
                    fontSize = 11.5.sp,
                    fontFamily = LuzzyFonts.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = UsageFormat.grouped(series.totals.sum()),
                    fontSize = 11.5.sp,
                    fontFamily = LuzzyFonts.Lora,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
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
