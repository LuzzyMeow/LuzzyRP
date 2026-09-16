package com.luzzymeow.luzzyrp.ui.pages.preset

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luzzymeow.luzzyrp.data.preset.PresetEntry
import com.luzzymeow.luzzyrp.data.preset.PresetRepository
import com.luzzymeow.luzzyrp.data.preset.PresetRole
import com.luzzymeow.luzzyrp.data.preset.PresetRow
import com.luzzymeow.luzzyrp.data.store.DatabaseProvider
import com.luzzymeow.luzzyrp.data.store.LuzzyStore
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.pages.common.LoomBadge
import com.luzzymeow.luzzyrp.ui.pages.common.LoomCard
import com.luzzymeow.luzzyrp.ui.pages.common.LoomConfirmDialog
import com.luzzymeow.luzzyrp.ui.pages.common.LoomEmpty
import com.luzzymeow.luzzyrp.ui.pages.common.LoomIconButton
import com.luzzymeow.luzzyrp.ui.pages.common.LoomMenuAction
import com.luzzymeow.luzzyrp.ui.pages.common.LoomOverflowMenu
import com.luzzymeow.luzzyrp.ui.pages.common.LoomScaffold
import com.luzzymeow.luzzyrp.ui.pages.common.LoomSectionLabel
import com.luzzymeow.luzzyrp.ui.pages.common.LoomSwitch
import com.luzzymeow.luzzyrp.ui.pages.common.LoomTier
import com.luzzymeow.luzzyrp.ui.pages.common.EditorHeader
import com.luzzymeow.luzzyrp.ui.pages.common.FieldLabel
import com.luzzymeow.luzzyrp.ui.pages.common.Placeholder
import com.luzzymeow.luzzyrp.ui.pages.common.PrimaryButton
import com.luzzymeow.luzzyrp.ui.pages.common.LongTextEditorDialog
import com.luzzymeow.luzzyrp.ui.pages.common.SegmentChips
import com.luzzymeow.luzzyrp.ui.theme.LoomAppear
import com.luzzymeow.luzzyrp.ui.theme.LoomShape
import com.luzzymeow.luzzyrp.ui.theme.Loom
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts
import kotlinx.coroutines.launch

/**
 * 预设页（W4，**真数据**）。替换 P1 的静态假列表。
 *
 * ## 这一页要讲清的语义（列表上直接写着）
 *
 * **顺序就是注入顺序**（上游 `app.js:4534-4628`：system 类按数组序拼进 system 提示词，
 * User/AI 类作为**独立消息**紧随首条 system 之后）——所以「上移/下移」是真语义，不是排版洁癖。
 *
 * ## 边界
 *
 * 与「生效」的关系：本版只管**管理**（增删改启停排序，改动立即落盘）；把这些条目真正拼进请求
 * 是 P5 的对账项。界面上如实写着，不让用户以为改了就生效。
 *
 * ## Loom v4 重皮（2026-09，迁移模式照 SessionsPage）
 *
 * 骨架 = [LoomScaffold]（accent=tertiary）；行 = [LoomCard] 卡行（标题+徽标+正文摘要）+
 * [LoomSwitch] + [LoomOverflowMenu]；空态 = [LoomEmpty]；删除确认 = [LoomConfirmDialog]；
 * 列表项入场 = [LoomAppear]。**行为与测试接缝原样保留**：testTag（`presets_list` /
 * `preset_row_${index}` / 编辑器三枚 / 删除确认两枚）、读屏锚（「启用 X」「X 的更多操作」
 * 「新建预设」「关闭」）、文案、回调与 repository 通道一行未改。
 */
@Composable
fun PresetsPage(
    onOpenDrawer: () -> Unit,
    /** 测试接缝（与 [com.luzzymeow.luzzyrp.ui.pages.world.WorldInfoPage] 同一约定）。 */
    repository: PresetRepository? = null,
    /**
     * 导入/导出回调（宿主走 SAF，与设置页的数据卡同一通道；v3.2 补）。
     *
     * 缺省空实现 = 不显示这两个入口（本页的仪器化测试与静态预览因此不受影响）——
     * **宁可不显示，也不放点了没反应的按钮**（本轮修的正是那类东西）。
     */
    onImport: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember(repository) {
        repository ?: PresetRepository(LuzzyStore(DatabaseProvider.luzzy(context.applicationContext)))
    }

    var rows by remember { mutableStateOf<List<PresetRow>?>(null) }
    var editing by remember { mutableStateOf<PresetEditorRequest?>(null) }
    var pendingDelete by remember { mutableStateOf<PresetRow?>(null) }
    val snackbar = remember { SnackbarHostState() }

    suspend fun reload() {
        rows = repo.all()
    }

    fun act(block: suspend () -> Unit) {
        scope.launch {
            val failure = runCatching { block() }.exceptionOrNull()
            runCatching { reload() }
            if (failure != null) snackbar.showSnackbar(failure.message ?: "操作失败，数据未改动")
        }
    }

    LaunchedEffect(repo) { reload() }

    LoomScaffold(
        title = "预设",
        iconRes = LuzzyIcons.Sliders,
        onOpenDrawer = onOpenDrawer,
        accent = MaterialTheme.colorScheme.tertiary,
        snackbarHost = { SnackbarHost(snackbar) },
        headerActions = {
            // 导入 / 导出（v3.2 补）：旧版预设页页头就有这两个入口，而数据层
            // （`TransferStore.exportPresets` / `importPresets`）也早已就绪——
            // 缺的只是「页面上有没有人把它接出来」。回调缺省时不渲染（不放死按钮）。
            onImport?.let { run ->
                LoomIconButton(
                    iconRes = LuzzyIcons.Download,
                    contentDescription = "导入预设",
                    onClick = run,
                )
            }
            onExport?.let { run ->
                LoomIconButton(
                    iconRes = LuzzyIcons.ExternalLink,
                    contentDescription = "导出预设",
                    onClick = run,
                )
            }
            LoomIconButton(
                iconRes = LuzzyIcons.Plus,
                contentDescription = "新建预设",
                tint = MaterialTheme.colorScheme.tertiary,
                onClick = { editing = PresetEditorRequest(ref = null, initial = PresetEntry()) },
            )
        },
    ) { padding ->
        val current = rows
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@LoomScaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("presets_list"),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = "顺序即注入顺序：系统类按此序拼进 system，User / AI 类作为独立消息插入。\n" +
                        "改动立即保存；启用的条目已按注入位置拼进请求。",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontFamily = LuzzyFonts.Body,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }
            if (current.isEmpty()) {
                item {
                    LoomEmpty(
                        iconRes = LuzzyIcons.Sliders,
                        title = "还没有预设条目",
                        supporting = "点右上角「＋」新建；启用中的条目才参与组装",
                    )
                }
            } else {
                item { LoomSectionLabel("提示词预设 · ${current.size}") }
                items(current, key = { it.index }) { row ->
                    LoomAppear(index = row.index) {
                        PresetCard(
                            row = row,
                            onOpen = { editing = PresetEditorRequest(row.index, row.entry) },
                            onToggle = { enabled -> act { repo.setEnabled(row.index, enabled) } },
                            onMoveUp = { act { repo.move(row.index, -1) } },
                            onMoveDown = { act { repo.move(row.index, +1) } },
                            onDelete = { pendingDelete = row },
                        )
                    }
                }
            }
        }
    }

    val request = editing
    if (request != null) {
        PresetEditorSheet(
            request = request,
            onDismiss = { editing = null },
            onSave = { ref, entry ->
                editing = null
                act { repo.upsert(ref, entry) }
            },
        )
    }

    pendingDelete?.let { row ->
        LoomConfirmDialog(
            title = "删除这条预设？",
            text = "「${row.entry.displayName}」将被删除，无法撤销。",
            confirmLabel = "删除",
            onConfirm = {
                pendingDelete = null
                act { repo.remove(row.index) }
            },
            onDismiss = { pendingDelete = null },
            danger = true,
            testTagConfirm = "preset_delete_confirm",
            testTagCancel = "preset_delete_cancel",
        )
    }
}

/**
 * 一行预设（Loom 卡行）：标题 + 徽标 + 正文摘要 + 启停开关 + 「⋯」菜单。
 *
 * 迁移自旧 `EntryCard`（PageKit）；「⋯」菜单 = 编辑 / 上移 / 下移 / 删除
 * （**排序不做拖拽**：菜单项天然可达，pro-rules 要求「拖拽必须有非拖拽替代」）。
 * 读屏锚不变：开关 = 「启用 [displayName]」，菜单钮 = 「[displayName] 的更多操作」。
 */
@Composable
private fun PresetCard(
    row: PresetRow,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    val entry = row.entry
    val scheme = MaterialTheme.colorScheme
    LoomCard(
        tier = LoomTier.Card,
        rail = scheme.tertiary,
        onClick = onOpen,
        modifier = Modifier.testTag("preset_row_${row.index}"),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.displayName,
                        fontSize = 14.sp,
                        fontFamily = LuzzyFonts.Body,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    LoomBadge(
                        text = entry.role.label,
                        tint = when (entry.role) {
                            PresetRole.System -> scheme.primary
                            PresetRole.User -> scheme.secondary
                            PresetRole.Assistant -> scheme.tertiary
                        },
                    )
                    if (entry.ignoredByAssembly) {
                        LoomBadge(text = "正文为空", tint = scheme.outline)
                    }
                }
                Text(
                    text = entry.summary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontFamily = LuzzyFonts.Body,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            LoomSwitch(checked = entry.enabled, onCheckedChange = onToggle, label = "启用 ${entry.displayName}")
            LoomOverflowMenu(
                actions = listOf(
                    LoomMenuAction("编辑", onOpen),
                    LoomMenuAction("上移", onMoveUp),
                    LoomMenuAction("下移", onMoveDown),
                    LoomMenuAction("删除", onDelete, destructive = true),
                ),
                label = "${entry.displayName} 的更多操作",
            )
        }
    }
}

/** 打开预设编辑器要带的东西：`ref == null` 表示新建。 */
data class PresetEditorRequest(val ref: Int?, val initial: PresetEntry)

/**
 * 预设编辑器（W4）。字段只有三个（名称 / 注入角色 / 正文），所以比世界书那个简单得多；
 * 正文同样走二级全屏（真实「破限」正文 1063 字，塞在表单里不好改）。
 *
 * Loom v4：表底 = [Loom.current.raised]（织层第二阶，与列表卡的 Card 区分）；
 * 圆角走 [LoomShape.Card]；头部大标题（Lora）；表单卡 = [LoomCard]（Raised 层）。
 * 行为（草稿保存 / 弃改确认 / 二级正文编辑器）与 testTag 一字未改。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetEditorSheet(
    request: PresetEditorRequest,
    onDismiss: () -> Unit,
    onSave: (Int?, PresetEntry) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by rememberSaveable(stateSaver = PresetDraft.Saver) {
        mutableStateOf(PresetDraft.from(request.initial))
    }
    var contentEditing by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val dirty = draft.toEntry() != request.initial

    fun requestDismiss() {
        if (dirty) confirmDiscard = true else onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = { requestDismiss() },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = LoomShape.Card, topEnd = LoomShape.Card),
        containerColor = Loom.current.raised,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .imePadding(),
        ) {
            EditorHeader(
                title = if (request.ref == null) "新建预设" else "编辑预设",
                onClose = { requestDismiss() },
            )
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    LoomCard(tier = LoomTier.Raised, shape = RoundedCornerShape(LoomShape.Control)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FieldLabel("名称")
                            OutlinedTextField(
                                value = draft.name,
                                onValueChange = { draft = draft.copy(name = it) },
                                placeholder = { Placeholder("例如：防抢话") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("preset_editor_name"),
                            )
                        }
                    }
                }
                item {
                    LoomCard(tier = LoomTier.Raised, shape = RoundedCornerShape(LoomShape.Control)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            FieldLabel("注入角色", hint = "决定这条内容以什么身份进入请求")
                            SegmentChips(
                                labels = PresetRole.entries.map { it.label },
                                selectedIndex = PresetRole.entries.indexOf(draft.role),
                                onSelect = { draft = draft.copy(role = PresetRole.entries[it]) },
                            )
                            Text(
                                text = when (draft.role) {
                                    PresetRole.System -> "拼进 system 提示词（按本列表顺序）"
                                    PresetRole.User -> "作为一条独立的 user 消息插入"
                                    PresetRole.Assistant -> "作为一条独立的 assistant 消息插入"
                                },
                                fontSize = 12.sp,
                                fontFamily = LuzzyFonts.Body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    LoomCard(tier = LoomTier.Raised, shape = RoundedCornerShape(LoomShape.Control)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FieldLabel("正文", hint = "${draft.content.length} 字")
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { contentEditing = true }) {
                                    Text(
                                        text = if (draft.content.isBlank()) "写正文" else "编辑正文",
                                        fontFamily = LuzzyFonts.Body,
                                    )
                                }
                            }
                            Text(
                                text = draft.content.lineSequence()
                                    .filter { it.isNotBlank() }
                                    .take(6)
                                    .joinToString("\n")
                                    .ifBlank { "（正文为空）" },
                                fontSize = 12.5.sp,
                                lineHeight = 19.sp,
                                fontFamily = LuzzyFonts.Body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 150.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .padding(4.dp),
                            )
                        }
                    }
                }
                item { Spacer(Modifier.size(8.dp)) }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(onClick = { requestDismiss() }, modifier = Modifier.weight(1f)) {
                    Text("取消", fontFamily = LuzzyFonts.Body)
                }
                PrimaryButton(
                    text = "保存",
                    modifier = Modifier.weight(1.4f).testTag("preset_editor_save"),
                    onClick = { onSave(request.ref, draft.toEntry()) },
                )
            }
        }
    }

    if (contentEditing) {
        LongTextEditorDialog(
            title = draft.name.ifBlank { "正文" },
            initial = draft.content,
            placeholder = "这段内容会按上面选定的身份进入请求",
            footerHint = "启用后参与组装",
            onCancel = { contentEditing = false },
            onDone = {
                draft = draft.copy(content = it)
                contentEditing = false
            },
            testTag = "preset_editor_content",
        )
    }

    if (confirmDiscard) {
        LoomConfirmDialog(
            title = "放弃未保存的修改？",
            text = "这条预设的改动还没保存。",
            confirmLabel = "放弃",
            onConfirm = {
                confirmDiscard = false
                onDismiss()
            },
            onDismiss = { confirmDiscard = false },
            danger = true,
        )
    }
}

/** 预设草稿（三个字段，同样旋转不丢）。 */
internal data class PresetDraft(
    val name: String,
    val role: PresetRole,
    val content: String,
) {
    fun toEntry(): PresetEntry = PresetEntry(name = name.trim(), role = role, content = content)

    companion object {
        fun from(entry: PresetEntry) = PresetDraft(entry.name, entry.role, entry.content)

        val Saver = listSaver<PresetDraft, Any>(
            save = { listOf(it.name, it.role.id, it.content) },
            restore = { PresetDraft(it[0] as String, PresetRole.fromId(it[1] as String), it[2] as String) },
        )
    }
}