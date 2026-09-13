package com.luzzymeow.luzzyrp.ui.pages.world

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luzzymeow.luzzyrp.data.chat.ChatSessionRepository
import com.luzzymeow.luzzyrp.data.store.DatabaseProvider
import com.luzzymeow.luzzyrp.data.store.LuzzyStore
import com.luzzymeow.luzzyrp.data.world.EntryRef
import com.luzzymeow.luzzyrp.data.world.WorldBook
import com.luzzymeow.luzzyrp.data.world.WorldBookRepository
import com.luzzymeow.luzzyrp.data.world.WorldEntry
import com.luzzymeow.luzzyrp.data.world.WorldInfoSettings
import com.luzzymeow.luzzyrp.data.world.WorldRow
import com.luzzymeow.luzzyrp.data.world.WorldScope
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.pages.common.EntryBadge
import com.luzzymeow.luzzyrp.ui.pages.common.EntryCard
import com.luzzymeow.luzzyrp.ui.pages.common.EntryMenuAction
import com.luzzymeow.luzzyrp.ui.pages.common.EmptyState
import com.luzzymeow.luzzyrp.ui.pages.common.PageHeader
import com.luzzymeow.luzzyrp.ui.pages.common.SectionTitle
import com.luzzymeow.luzzyrp.ui.pages.common.SettingCard
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 世界书页（W2，**真数据**）。替换 P1 的静态假列表。
 *
 * ## 分组怎么来的
 *
 * 界面上两组（全局 / 绑定当前角色）**不等于**存储的两个位置——分组按**有效归属**算
 * （`scope=global` 或名字属于系统名的条目按全局展示，见 `WorldBookOps.effectiveScope`）。
 * 动作寻址用 [WorldRow.ref]（**物理**位置），所以「显示在全局组、其实躺在角色卡里」的条目
 * 一保存就会按选定归属落回正确的桶。
 *
 * ## 本版边界（界面上如实写着）
 *
 * 编辑/启停/排序/删除都**立刻落盘**；但**检索注入是 P5**——“世界书在此管理，尚未影响回复”。
 * 不写这一行，用户会以为改了就生效（`docs/PLAN-v3.0-presets-worldbook.md` §1）。
 */
@Composable
fun WorldInfoPage(
    onOpenDrawer: () -> Unit,
    /**
     * 仓库注入点。**测试接缝**（与 [com.luzzymeow.luzzyrp.ui.pages.SessionsPage] 同一约定）：
     * 不注入的话仪器化测试会读设备上真实的 `luzzy.db`，页面初始状态就取决于这台机器恰好有什么数据。
     */
    repository: WorldBookRepository? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember(repository) {
        repository ?: run {
            val store = LuzzyStore(DatabaseProvider.luzzy(context.applicationContext))
            WorldBookRepository(store, ChatSessionRepository(store))
        }
    }

    var book by remember { mutableStateOf<WorldBook?>(null) }
    var settings by remember { mutableStateOf(WorldInfoSettings()) }
    var editing by remember { mutableStateOf<WorldEditorRequest?>(null) }
    var pendingDelete by remember { mutableStateOf<WorldRow?>(null) }
    val snackbar = remember { SnackbarHostState() }

    suspend fun reload() {
        book = repo.load()
        settings = repo.settings()
    }

    /** 统一的「做完就重读 + 失败如实报」入口：任何一步失败都不许静默。 */
    fun act(block: suspend () -> Unit) {
        scope.launch {
            val failure = runCatching { block() }.exceptionOrNull()
            runCatching { reload() }
            if (failure != null) {
                snackbar.showSnackbar(failure.message ?: "操作失败，数据未改动")
            }
        }
    }

    LaunchedEffect(repo) { reload() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            PageHeader("世界书", LuzzyIcons.BookOpen, onOpenDrawer, actions = {
                IconButton(
                    onClick = {
                        editing = WorldEditorRequest(
                            ref = null,
                            initial = WorldEntry(scope = WorldScope.Global),
                        )
                    },
                ) {
                    Icon(
                        painter = painterResource(LuzzyIcons.Plus),
                        contentDescription = "新建条目",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val current = book
        if (current == null) {
            // 首帧：DB 读是毫秒级，这里不放 spinner（放了反而闪一下）
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("worldinfo_list"),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                WorldSettingsCard(
                    settings = settings,
                    onCommit = { next ->
                        settings = next
                        act { repo.saveSettings(next) }
                    },
                )
            }
            item {
                Text(
                    text = "本版：在此管理条目（改动立即保存）；「检索注入」在 P5 接入——" +
                        "现在改这些还不会影响回复。",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontFamily = LuzzyFonts.Body,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }
            if (current.isEmpty) {
                item {
                    EmptyState(
                        iconRes = LuzzyIcons.BookOpen,
                        title = "还没有世界书条目",
                        supporting = "点右上角「＋」新建；全局条目对所有角色生效",
                    )
                }
            }

            if (current.globalRows.isNotEmpty()) {
                item { SectionTitle("全局条目 · ${current.globalRows.size}") }
                items(current.globalRows, key = { it.ref.key() }) { row ->
                    WorldEntryRow(
                        row = row,
                        onOpen = { editing = WorldEditorRequest(row.ref, row.entry) },
                        onToggle = { enabled -> act { repo.setEnabled(row.ref, enabled) } },
                        onMoveUp = { act { repo.move(row.ref, -1) } },
                        onMoveDown = { act { repo.move(row.ref, +1) } },
                        onDelete = { pendingDelete = row },
                    )
                }
            }

            if (current.hasCharacter) {
                item { SectionTitle("${current.characterName.orEmpty()} 绑定 · ${current.characterRows.size}") }
                if (current.characterRows.isEmpty()) {
                    item {
                        Text(
                            text = "这个角色还没有专属条目（新建时把「范围」选成「绑定当前角色」）",
                            fontSize = 12.sp,
                            fontFamily = LuzzyFonts.Body,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                        )
                    }
                }
                items(current.characterRows, key = { it.ref.key() }) { row ->
                    WorldEntryRow(
                        row = row,
                        onOpen = { editing = WorldEditorRequest(row.ref, row.entry) },
                        onToggle = { enabled -> act { repo.setEnabled(row.ref, enabled) } },
                        onMoveUp = { act { repo.move(row.ref, -1) } },
                        onMoveDown = { act { repo.move(row.ref, +1) } },
                        onDelete = { pendingDelete = row },
                    )
                }
            } else {
                item {
                    Text(
                        text = "尚未选择角色：角色绑定条目在这里看不到",
                        fontSize = 12.sp,
                        fontFamily = LuzzyFonts.Body,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                }
            }
        }
    }

    val request = editing
    if (request != null) {
        WorldEntryEditorSheet(
            request = request,
            canBindCharacter = book?.hasCharacter == true,
            onDismiss = { editing = null },
            onSave = { ref, entry ->
                editing = null
                act { repo.upsert(ref, entry) }
            },
        )
    }

    pendingDelete?.let { row ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这条世界书条目？") },
            text = { Text("「${row.entry.displayName}」将被删除，无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        act { repo.remove(row.ref) }
                    },
                    modifier = Modifier.testTag("world_delete_confirm"),
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }, modifier = Modifier.testTag("world_delete_cancel")) {
                    Text("取消")
                }
            },
        )
    }
}

/** Lazy 列表的稳定 key（归属 + 物理下标就是身份）。 */
private fun EntryRef.key(): String = "${scope.id}-$slot"

@Composable
private fun WorldEntryRow(
    row: WorldRow,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    val entry = row.entry
    val badges = buildList {
        add(
            EntryBadge(
                text = row.group.label,
                tint = if (row.group == WorldScope.Global) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.secondary,
            ),
        )
        if (entry.constant) add(EntryBadge("常驻", MaterialTheme.colorScheme.tertiary))
        if (entry.useProbability && entry.probability < 100) {
            add(EntryBadge("概率 ${entry.probability}%", MaterialTheme.colorScheme.outline))
        }
    }
    EntryCard(
        title = entry.displayName,
        checked = entry.enabled,
        onCheckedChange = onToggle,
        badges = badges,
        supporting = if (entry.neverTriggers) "${entry.triggerSummary} · 这条不会触发" else entry.triggerSummary,
        onOpen = onOpen,
        // 行 tag 用**物理位置**（动作寻址就是它），测试据此定位与断言
        modifier = Modifier.testTag("world_row_${row.ref.scope.id}_${row.ref.slot}"),
        menu = listOf(
            EntryMenuAction("编辑", onOpen),
            EntryMenuAction("上移", onMoveUp),
            EntryMenuAction("下移", onMoveDown),
            EntryMenuAction("删除", onDelete, destructive = true),
        ),
    )
}

/** 全局设置卡：两个滑杆**真写库**（拖动过程只改本地值，松手才落盘）。 */
@Composable
private fun WorldSettingsCard(settings: WorldInfoSettings, onCommit: (WorldInfoSettings) -> Unit) {
    var scan by remember(settings.scanDepth) { mutableFloatStateOf(settings.scanDepth.toFloat()) }
    var max by remember(settings.maxDepth) { mutableFloatStateOf(settings.maxDepth.toFloat()) }

    SettingCard(Modifier.padding(bottom = 6.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            SettingSlider(
                label = "扫描深度",
                hint = "关键词匹配只看最近 N 条消息",
                value = scan,
                valueRange = 0f..WorldInfoSettings.MAX_SCAN_DEPTH.toFloat(),
                steps = WorldInfoSettings.MAX_SCAN_DEPTH - 1,
                valueText = scan.roundToInt().toString(),
                onChange = { scan = it },
                onFinished = { onCommit(settings.copy(scanDepth = scan.roundToInt())) },
            )
            SettingSlider(
                label = "最大扫描深度",
                hint = "上限（条目自带的扫描深度会被它夹住）",
                value = max,
                valueRange = 0f..WorldInfoSettings.MAX_MAX_DEPTH.toFloat(),
                steps = WorldInfoSettings.MAX_MAX_DEPTH - 1,
                valueText = if (max.roundToInt() == 0) "不限制" else max.roundToInt().toString(),
                onChange = { max = it },
                onFinished = { onCommit(settings.copy(maxDepth = max.roundToInt())) },
            )
        }
    }
}

@Composable
private fun SettingSlider(
    label: String,
    hint: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueText: String,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
) {
    Column(Modifier.padding(bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontFamily = LuzzyFonts.Body,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                fontSize = 14.sp,
                fontFamily = LuzzyFonts.Body,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = hint,
            fontSize = 12.sp,
            fontFamily = LuzzyFonts.Body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = valueRange,
            steps = steps.coerceAtLeast(0),
            onValueChangeFinished = onFinished,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
