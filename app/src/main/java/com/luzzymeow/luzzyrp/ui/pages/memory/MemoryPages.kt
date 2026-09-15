package com.luzzymeow.luzzyrp.ui.pages.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luzzymeow.luzzyrp.chat.BranchOption
import com.luzzymeow.luzzyrp.chat.MemoryBrowser
import com.luzzymeow.luzzyrp.chat.MemoryScopeOption
import com.luzzymeow.luzzyrp.chat.PageDataSource
import com.luzzymeow.luzzyrp.chat.RecallEngine
import com.luzzymeow.luzzyrp.chat.RecallOptions
import com.luzzymeow.luzzyrp.data.store.DatabaseProvider
import com.luzzymeow.luzzyrp.data.store.LuzzyStore
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.pages.common.BadgeChip
import com.luzzymeow.luzzyrp.ui.pages.common.EmptyState
import com.luzzymeow.luzzyrp.ui.pages.common.LongTextEditorDialog
import com.luzzymeow.luzzyrp.ui.pages.common.LuzzySwitch
import com.luzzymeow.luzzyrp.ui.pages.common.PageScaffold
import com.luzzymeow.luzzyrp.ui.pages.common.Placeholder
import com.luzzymeow.luzzyrp.ui.pages.common.SectionTitle
import com.luzzymeow.luzzyrp.ui.pages.common.SegmentChips
import com.luzzymeow.luzzyrp.ui.pages.common.SettingCard
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts
import kotlinx.coroutines.launch

/**
 * 记忆系统页（v3.2 重建）。
 *
 * ## 为什么重建（而不是「再修一处」）
 *
 * P5 批 C 只把**三个数字**接上了真库，页面结构本身还是静态稿：一屏两个统计卡 + 一句空态。
 * 于是它同时缺两样东西，缺法还不一样：
 * - **看得见的功能**：旧版（WebView）这页有「记忆内容管理」——跨角色/分支浏览每一条分片与总结，
 *   逐条启停、编辑、删除、清空；原生版一条都没有，用户能做的只有「看数字」。
 * - **改不动的参数**：召回（注入哪些历史、注入几条、阈值多少）原先硬编码在 `RequestBuilder`
 *   的默认参数里，界面没有任何入口——页面上既不能调、也看不出它存在。
 *
 * 本次把两者一起补齐，并**刻意不引入新概念**：区块顺序与操作语义照旧版 IA 翻译
 * （作用域 → 引擎 → 检索 → 内容管理），只在原生侧确有真实现的地方才画控件。
 *
 * ## 四条纪律（这页最容易出的四类假东西）
 *
 * 1. **没有假按钮**：页头不放装饰性图标；清空只有内容区那一个入口，带条数与二次确认。
 * 2. **没有假开关**：引擎卡的三个参数真的写进 `kv[memorySettings].recall`，
 *    `RequestBuilder.plan` 也真的读它（见 [RecallOptions]）。
 * 3. **没有假数据**：检索结果由 [RecallEngine] 在**本作用域的真实历史**上算出，
 *    与发送路径同一套算法、同一份设置。
 * 4. **没有假成功**：写入失败（条目已被别处删掉等）如实说「保存失败」，不谎报已保存。
 *
 * ## 与向量记忆的关系（如实记录，不当成已完成）
 *
 * 原生引擎是**词面重叠**打分（不需要嵌入模型）；库里带 `embeddingQ` 的向量分片是旧版迁移
 * 过来的数据。因此编辑一条分片**不重算向量**——旧版保存时会调嵌入模型重新嵌入，原生侧还没有
 * embeddings 客户端。这一点写在编辑弹层的页脚里，而不是让按钮假装在算。
 *
 * @param pageData 测试接缝（同 `CharactersPage`）：不注入时自建指向设备真库的实例。
 */
@Composable
fun MemoryPage(onOpenDrawer: () -> Unit, pageData: PageDataSource? = null) {
    val context = LocalContext.current
    val source = remember(pageData) {
        pageData ?: PageDataSource(LuzzyStore(DatabaseProvider.luzzy(context.applicationContext)))
    }
    val scope = rememberCoroutineScope()

    // ── 作用域（角色 × 分支）与内容 ──
    var scopes by remember { mutableStateOf<List<MemoryScopeOption>?>(null) }
    var scopeUuid by remember { mutableStateOf<String?>(null) }
    var scopeBranch by remember { mutableStateOf<String?>(null) }
    var vector by remember { mutableStateOf<List<MemoryBrowser.Item>?>(null) }
    var classic by remember { mutableStateOf<List<MemoryBrowser.Item>?>(null) }
    var refresh by remember { mutableStateOf(0) }

    // ── 引擎与检索 ──
    var recall by remember { mutableStateOf<RecallOptions?>(null) }
    var turns by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<RecallEngine.Hit>?>(null) }
    var searching by remember { mutableStateOf(false) }

    // ── 弹层与提示 ──
    var picker by remember { mutableStateOf<PickerKind?>(null) }
    var editing by remember { mutableStateOf<MemoryBrowser.Item?>(null) }
    var deleting by remember { mutableStateOf<MemoryBrowser.Item?>(null) }
    var clearing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var section by remember { mutableStateOf(0) }

    // 首帧：作用域候选 + 引擎设置（各读一次）
    LaunchedEffect(source) {
        val loaded = source.memoryScopes()
        scopes = loaded
        recall = source.recallOptions()
        val first = loaded.firstOrNull { it.isActive } ?: loaded.firstOrNull()
        if (first != null && scopeUuid == null) {
            scopeUuid = first.uuid
            scopeBranch = first.activeBranchId
        }
    }
    // 作用域变化 / 任何写入之后重新读内容：写入一律走这里刷新，**不手改本地列表**
    // （手改列表 = 第二份真源，失败时界面会显示一个库里并不存在的状态）
    LaunchedEffect(source, scopeUuid, scopeBranch, refresh) {
        val uuid = scopeUuid
        vector = null
        classic = null
        vector = source.memoryItems(uuid, scopeBranch, MemoryBrowser.VECTOR)
        classic = source.memoryItems(uuid, scopeBranch, MemoryBrowser.CLASSIC)
        turns = source.turnCount(uuid, scopeBranch)
        hits = null
    }

    val current = scopes?.firstOrNull { it.uuid == scopeUuid }
    val shown = if (section == 0) vector else classic
    val shownKind = if (section == 0) MemoryBrowser.VECTOR else MemoryBrowser.CLASSIC
    val options = recall ?: RecallOptions()

    PageScaffold("记忆系统", LuzzyIcons.Memory, onOpenDrawer) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("memory_list"),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ① 作用域
            item {
                SettingCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "作用域",
                            fontSize = 13.sp,
                            fontFamily = LuzzyFonts.Body,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                        ScopeRow(
                            label = "角色卡",
                            value = current?.name ?: "（还没有角色卡）",
                            enabled = (scopes?.size ?: 0) > 0,
                            onClick = { picker = PickerKind.Character },
                        )
                        // 分支下拉只在**真的多于一个**分支时出现（旧版同义）：只有一个时它是噪声
                        if ((current?.branches?.size ?: 0) > 1) {
                            ScopeRow(
                                label = "剧情分支",
                                value = current?.branches?.firstOrNull { it.id == scopeBranch }?.name ?: "主线",
                                enabled = true,
                                onClick = { picker = PickerKind.Branch },
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "分片 ${vector?.size ?: 0}",
                                fontSize = 12.sp,
                                fontFamily = LuzzyFonts.Body,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text("｜", fontSize = 12.sp, color = MaterialTheme.colorScheme.outlineVariant)
                            Text(
                                text = "总结 ${classic?.size ?: 0}",
                                fontSize = 12.sp,
                                fontFamily = LuzzyFonts.Body,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (current?.isActive == true) {
                                BadgeChip("当前角色", MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = if (options.enabled) "召回 ${options.topK} 条 · ≥${options.minScoreLabel}" else "召回已关",
                                fontSize = 11.sp,
                                fontFamily = LuzzyFonts.Body,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }

            // ② 记忆引擎（真设置：写 kv，发送路径真的读）
            item { SectionTitle("记忆引擎") }
            item {
                SettingCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "注入记忆召回",
                                    fontSize = 14.sp,
                                    fontFamily = LuzzyFonts.Body,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "命中时在 system 里注入 <memory_recall> 块；关掉则整段消失",
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp,
                                    fontFamily = LuzzyFonts.Body,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            LuzzySwitch(
                                checked = options.enabled,
                                label = "注入记忆召回",
                                onCheckedChange = { on ->
                                    val next = options.copy(enabled = on)
                                    recall = next
                                    scope.launch { source.saveRecallOptions(next) }
                                },
                            )
                        }
                        EngineChips(
                            title = "召回条数",
                            hint = "每轮最多注入几条命中",
                            labels = RecallOptions.TOP_K_STEPS.map { "$it 条" },
                            selected = RecallOptions.TOP_K_STEPS.indexOf(options.topK).coerceAtLeast(0),
                            enabled = options.enabled,
                        ) { index ->
                            val next = options.copy(topK = RecallOptions.TOP_K_STEPS[index])
                            recall = next
                            scope.launch { source.saveRecallOptions(next) }
                        }
                        EngineChips(
                            title = "相关度阈值",
                            hint = "低于此值的命中不注入；调高 = 更严格",
                            labels = RecallOptions.MIN_SCORE_STEPS.map { "${(it * 100).toInt()}%" },
                            selected = nearestScoreIndex(options.minScore),
                            enabled = options.enabled,
                        ) { index ->
                            val next = options.copy(minScore = RecallOptions.MIN_SCORE_STEPS[index])
                            recall = next
                            scope.launch { source.saveRecallOptions(next) }
                        }
                        Text(
                            text = "原生召回是词面重叠打分（中文二元组 + 英文整词），离线可跑、不依赖嵌入模型；" +
                                "库里带向量的分片是旧版迁移过来的数据。",
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            fontFamily = LuzzyFonts.Body,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }

            // ③ 检索测试（真跑：与发送路径同一算法、同一设置）
            item { SectionTitle("检索测试") }
            item {
                SettingCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = if (turns > 0) {
                                "在本作用域的 $turns 轮历史里检索"
                            } else {
                                "本作用域还没有可检索的历史（先聊几句）"
                            },
                            fontSize = 11.5.sp,
                            fontFamily = LuzzyFonts.Body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = { Placeholder("输入一句话看会召回哪些轮次") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("memory_query"),
                            )
                            TextButton(
                                enabled = !searching && query.isNotBlank() && turns > 0,
                                onClick = {
                                    searching = true
                                    scope.launch {
                                        hits = source.recallPreview(scopeUuid, scopeBranch, query, options)
                                        searching = false
                                    }
                                },
                            ) {
                                Text("检索", fontFamily = LuzzyFonts.Body, fontWeight = FontWeight.Medium)
                            }
                        }
                        val result = hits
                        when {
                            result == null -> Unit
                            result.isEmpty() -> Text(
                                text = "没有命中：这句话与本作用域历史任何一轮的词面重叠都没到阈值（${options.minScoreLabel}）。",
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                                fontFamily = LuzzyFonts.Body,
                                color = MaterialTheme.colorScheme.outline,
                            )

                            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                result.forEach { hit ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        BadgeChip("第 ${hit.turn} 轮", MaterialTheme.colorScheme.primary)
                                        BadgeChip(RecallEngine.percent(hit.score), MaterialTheme.colorScheme.tertiary)
                                        Text(
                                            text = hit.text,
                                            fontSize = 12.sp,
                                            lineHeight = 17.sp,
                                            fontFamily = LuzzyFonts.Body,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                                Text(
                                    text = "命中会以 <memory_recall> 注入 system（与发送时同一份渲染）",
                                    fontSize = 11.sp,
                                    fontFamily = LuzzyFonts.Body,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                }
            }

            // ④ 内容管理
            item { SectionTitle("记忆内容") }
            item {
                SegmentChips(
                    labels = listOf("向量分片 ${vector?.size ?: 0}", "总结记忆 ${classic?.size ?: 0}"),
                    selectedIndex = section,
                    onSelect = { section = it },
                )
            }
            when {
                shown == null -> item {
                    SettingCard {
                        Text(
                            text = "正在读取记忆…",
                            fontSize = 12.sp,
                            fontFamily = LuzzyFonts.Body,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }

                shown.isEmpty() -> item {
                    EmptyState(
                        iconRes = LuzzyIcons.Memory,
                        title = if (shownKind == MemoryBrowser.VECTOR) "这个作用域没有向量分片" else "这个作用域没有总结记忆",
                        supporting = if (shownKind == MemoryBrowser.VECTOR) {
                            "旧版会在对话推进到一定轮数后按段落切分并嵌入；原生侧尚未接入自动生成，" +
                                "新对话不会自动产出分片（迁移进来的旧数据照常显示在这里）。"
                        } else {
                            "总结记忆由旧版按轮次生成；原生侧尚未接入自动总结，历史数据可在这里查看与编辑。"
                        },
                    )
                }

                else -> items(shown, key = { "${it.kind}#${it.id}" }) { item ->
                    MemoryRow(
                        item = item,
                        onToggle = { enabled ->
                            scope.launch {
                                val ok = source.setMemoryEnabled(
                                    scopeUuid, scopeBranch, item.kind, item.id, enabled,
                                )
                                message = if (ok) {
                                    if (enabled) "已恢复参与召回（${item.turnLabel}）" else "已停用（${item.turnLabel}）"
                                } else {
                                    "操作失败：这条记忆已不在库里"
                                }
                                refresh++
                            }
                        },
                        onEdit = { editing = item },
                        onDelete = { deleting = item },
                    )
                }
            }
            if (shown?.isNotEmpty() == true) {
                item {
                    DangerRow(
                        text = "清空此作用域记忆（共 ${(vector?.size ?: 0) + (classic?.size ?: 0)} 条）",
                        onClick = { clearing = true },
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

    // ── 弹层 ──

    picker?.let { kind ->
        val items: List<PickOption> = when (kind) {
            PickerKind.Character -> (scopes ?: emptyList()).map { option ->
                PickOption("${option.name}${if (option.isActive) "（当前）" else ""}", option.uuid == scopeUuid) {
                    scopeUuid = option.uuid
                    // 切角色时**跟着切分支**：否则会停在上一角色的分支 id 上，读到的是一份
                    // 不存在的作用域（表现为「记忆凭空消失」，不报错）
                    scopeBranch = option.activeBranchId
                }
            }

            PickerKind.Branch -> (current?.branches ?: emptyList()).map { branch: BranchOption ->
                PickOption(if (branch.isMain) "${branch.name}（主线）" else branch.name, branch.id == scopeBranch) {
                    scopeBranch = branch.id
                }
            }
        }
        PickDialog(
            title = if (kind == PickerKind.Character) "选择角色卡" else "选择剧情分支",
            options = items,
            onDismiss = { picker = null },
        )
    }

    editing?.let { item ->
        LongTextEditorDialog(
            title = if (item.kind == MemoryBrowser.VECTOR) "编辑记忆分片" else "编辑总结记忆",
            initial = item.text,
            placeholder = "记忆正文",
            footerHint = if (item.kind == MemoryBrowser.VECTOR) {
                "${item.turnLabel} · ${item.metaLabel} · 本机暂不重算向量，保存只改文本"
            } else {
                "${item.turnLabel} · 旧版由模型生成的摘要，可直接改写"
            },
            onCancel = { editing = null },
            onDone = { text ->
                editing = null
                scope.launch {
                    val ok = source.setMemoryText(scopeUuid, scopeBranch, item.kind, item.id, text)
                    message = if (ok) "已保存（${item.turnLabel}）" else "保存失败：这条记忆已不在库里"
                    refresh++
                }
            },
        )
    }

    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除这条记忆？", fontFamily = LuzzyFonts.Body) },
            text = {
                Text(
                    text = "${item.turnLabel} · ${item.preview(60)}\n\n删除后不可恢复。",
                    fontFamily = LuzzyFonts.Body,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch {
                        val ok = source.deleteMemory(scopeUuid, scopeBranch, item.kind, item.id)
                        message = if (ok) "已删除（${item.turnLabel}）" else "删除失败：这条记忆已不在库里"
                        refresh++
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }

    if (clearing) {
        val total = (vector?.size ?: 0) + (classic?.size ?: 0)
        AlertDialog(
            onDismissRequest = { clearing = false },
            title = { Text("清空记忆？", fontFamily = LuzzyFonts.Body) },
            text = {
                Text(
                    text = "将删除「${current?.name ?: "当前角色"}」在${branchLabel(current, scopeBranch)}上的 " +
                        "$total 条记忆（向量分片与总结记忆一起）。\n\n此操作不可恢复。",
                    fontFamily = LuzzyFonts.Body,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    clearing = false
                    scope.launch {
                        val removed = source.clearMemories(scopeUuid, scopeBranch)
                        message = if (removed > 0) "已清空 $removed 条记忆" else "没有可清空的记忆"
                        refresh++
                    }
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { clearing = false }) { Text("取消") } },
        )
    }
}

private enum class PickerKind { Character, Branch }

/** 与档位最接近的下标（库里的值可能被手改成档位之间的数，不崩、不空选）。 */
private fun nearestScoreIndex(score: Double): Int {
    var best = 0
    var bestDelta = Double.MAX_VALUE
    RecallOptions.MIN_SCORE_STEPS.forEachIndexed { index, step ->
        val delta = kotlin.math.abs(step - score)
        if (delta < bestDelta) {
            bestDelta = delta
            best = index
        }
    }
    return best
}

private fun branchLabel(scope: MemoryScopeOption?, branchId: String?): String {
    val branch = scope?.branches?.firstOrNull { it.id == branchId } ?: return "主线"
    return if (branch.isMain) "「${branch.name}」主线" else "「${branch.name}」分支"
}

/** 一行「标签 + 值 + 切换」，点了开选择器。 */
@Composable
private fun ScopeRow(label: String, value: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontFamily = LuzzyFonts.Body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp),
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontFamily = LuzzyFonts.Body,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (enabled) "切换 ›" else "—",
            fontSize = 12.sp,
            fontFamily = LuzzyFonts.Body,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 引擎卡里的一组分段芯片（标题 + 选项 + 说明）。 */
@Composable
private fun EngineChips(
    title: String,
    hint: String,
    labels: List<String>,
    selected: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontFamily = LuzzyFonts.Body,
            fontWeight = FontWeight.Medium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
        )
        SegmentChips(
            labels = labels,
            selectedIndex = selected,
            onSelect = onSelect,
            selectable = labels.map { enabled },
        )
        Text(
            text = hint,
            fontSize = 11.sp,
            fontFamily = LuzzyFonts.Body,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/** 一条记忆（向量分片 / 总结记忆共用）。点正文 = 展开/收起（旧版同义）。 */
@Composable
private fun MemoryRow(
    item: MemoryBrowser.Item,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var expanded by remember(item.id) { mutableStateOf(false) }
    SettingCard {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BadgeChip(item.turnLabel, MaterialTheme.colorScheme.secondary)
                if (item.hasEmbedding) BadgeChip("已嵌入", MaterialTheme.colorScheme.tertiary)
                if (item.chunkMode.isNotBlank()) BadgeChip(item.chunkMode, MaterialTheme.colorScheme.outline)
                Spacer(Modifier.weight(1f))
                LuzzySwitch(checked = item.enabled, onCheckedChange = onToggle, label = "启用 ${item.turnLabel}")
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            painter = painterResource(LuzzyIcons.DotsHorizontal),
                            contentDescription = "${item.turnLabel} 的更多操作",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("编辑内容", fontFamily = LuzzyFonts.Body, fontSize = 14.sp) },
                            onClick = {
                                menuOpen = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "删除",
                                    fontFamily = LuzzyFonts.Body,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            }
            // 元信息**单独一行**（截图抓到过缺陷：把它塞在徽标与开关之间时，
            // 空间被两头挤掉 → 模型名在词中间折行成「text-embeddin / g-3-small · 1536 维」）。
            // 单行 + 省略号是这里的正确形态：它是**辅助信息**，截断了也读得懂。
            Text(
                text = item.metaLabel,
                fontSize = 11.sp,
                fontFamily = LuzzyFonts.Body,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = if (expanded) item.text.trim() else item.preview(),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontFamily = LuzzyFonts.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 2.dp),
            )
            if (item.text.length > 96) {
                Text(
                    text = if (expanded) "收起" else "展开全文",
                    fontSize = 11.sp,
                    fontFamily = LuzzyFonts.Body,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { expanded = !expanded }
                        .padding(vertical = 2.dp),
                )
            }
        }
    }
}

/** 危险操作行（清空）。 */
@Composable
private fun DangerRow(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontFamily = LuzzyFonts.Body,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

private data class PickOption(val label: String, val selected: Boolean, val onPick: () -> Unit)

@Composable
private fun PickDialog(title: String, options: List<PickOption>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontFamily = LuzzyFonts.Body) },
        text = {
            Column {
                if (options.isEmpty()) {
                    Text("没有可选项", fontFamily = LuzzyFonts.Body, fontSize = 13.sp)
                }
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                option.onPick()
                                onDismiss()
                            }
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = option.label,
                            fontSize = 14.sp,
                            fontFamily = LuzzyFonts.Body,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (option.selected) {
                            Text(
                                text = "当前",
                                fontSize = 11.sp,
                                fontFamily = LuzzyFonts.Body,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
