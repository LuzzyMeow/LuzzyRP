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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luzzymeow.luzzyrp.data.preset.PresetEntry
import com.luzzymeow.luzzyrp.data.preset.PresetRepository
import com.luzzymeow.luzzyrp.data.preset.PresetRole
import com.luzzymeow.luzzyrp.data.preset.PresetRow
import com.luzzymeow.luzzyrp.data.store.DatabaseProvider
import com.luzzymeow.luzzyrp.data.store.LuzzyStore
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.pages.common.EditorHeader
import com.luzzymeow.luzzyrp.ui.pages.common.EmptyState
import com.luzzymeow.luzzyrp.ui.pages.common.EntryBadge
import com.luzzymeow.luzzyrp.ui.pages.common.EntryCard
import com.luzzymeow.luzzyrp.ui.pages.common.EntryMenuAction
import com.luzzymeow.luzzyrp.ui.pages.common.FieldLabel
import com.luzzymeow.luzzyrp.ui.pages.common.LongTextEditorDialog
import com.luzzymeow.luzzyrp.ui.pages.common.PageHeader
import com.luzzymeow.luzzyrp.ui.pages.common.Placeholder
import com.luzzymeow.luzzyrp.ui.pages.common.PrimaryButton
import com.luzzymeow.luzzyrp.ui.pages.common.SectionTitle
import com.luzzymeow.luzzyrp.ui.pages.common.SegmentChips
import com.luzzymeow.luzzyrp.ui.pages.common.SettingCard
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
 */
@Composable
fun PresetsPage(
    onOpenDrawer: () -> Unit,
    /** 测试接缝（与 [com.luzzymeow.luzzyrp.ui.pages.world.WorldInfoPage] 同一约定）。 */
    repository: PresetRepository? = null,
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            PageHeader("预设", LuzzyIcons.Sliders, onOpenDrawer, actions = {
                IconButton(onClick = { editing = PresetEditorRequest(ref = null, initial = PresetEntry()) }) {
                    Icon(
                        painter = painterResource(LuzzyIcons.Plus),
                        contentDescription = "新建预设",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val current = rows
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("presets_list"),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                Text(
                    text = "顺序即注入顺序：系统类按此序拼进 system，User / AI 类作为独立消息插入。\n" +
                        "本版：在此管理（改动立即保存）；「拼进请求」在 P5 接入——现在改这些还不会影响回复。",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontFamily = LuzzyFonts.Body,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }
            if (current.isEmpty()) {
                item {
                    EmptyState(
                        iconRes = LuzzyIcons.Sliders,
                        title = "还没有预设条目",
                        supporting = "点右上角「＋」新建；启用中的条目才参与组装",
                    )
                }
            } else {
                item { SectionTitle("提示词预设 · ${current.size}") }
                items(current, key = { it.index }) { row ->
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
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这条预设？") },
            text = { Text("「${row.entry.displayName}」将被删除，无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        act { repo.remove(row.index) }
                    },
                    modifier = Modifier.testTag("preset_delete_confirm"),
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDelete = null },
                    modifier = Modifier.testTag("preset_delete_cancel"),
                ) {
                    Text("取消")
                }
            },
        )
    }
}

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
    val badges = buildList {
        add(
            EntryBadge(
                text = entry.role.label,
                tint = when (entry.role) {
                    PresetRole.System -> MaterialTheme.colorScheme.primary
                    PresetRole.User -> MaterialTheme.colorScheme.secondary
                    PresetRole.Assistant -> MaterialTheme.colorScheme.tertiary
                },
            ),
        )
        if (entry.ignoredByAssembly) add(EntryBadge("正文为空", MaterialTheme.colorScheme.outline))
    }
    EntryCard(
        title = entry.displayName,
        checked = entry.enabled,
        onCheckedChange = onToggle,
        badges = badges,
        supporting = entry.summary,
        onOpen = onOpen,
        modifier = Modifier.testTag("preset_row_${row.index}"),
        menu = listOf(
            EntryMenuAction("编辑", onOpen),
            EntryMenuAction("上移", onMoveUp),
            EntryMenuAction("下移", onMoveDown),
            EntryMenuAction("删除", onDelete, destructive = true),
        ),
    )
}

/** 打开预设编辑器要带的东西：`ref == null` 表示新建。 */
data class PresetEditorRequest(val ref: Int?, val initial: PresetEntry)

/**
 * 预设编辑器（W4）。字段只有三个（名称 / 注入角色 / 正文），所以比世界书那个简单得多；
 * 正文同样走二级全屏（真实「破限」正文 1063 字，塞在表单里不好改）。
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
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
                    SettingCard {
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
                    SettingCard {
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
                    SettingCard {
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
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
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
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("放弃未保存的修改？") },
            text = { Text("这条预设的改动还没保存。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onDismiss()
                }) {
                    Text("放弃", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("继续编辑") }
            },
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
