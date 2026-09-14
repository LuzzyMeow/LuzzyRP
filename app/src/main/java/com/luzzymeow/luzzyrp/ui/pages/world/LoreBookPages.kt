package com.luzzymeow.luzzyrp.ui.pages.world

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luzzymeow.luzzyrp.data.store.DatabaseProvider
import com.luzzymeow.luzzyrp.data.store.LuzzyStore
import com.luzzymeow.luzzyrp.data.world.DepthRole
import com.luzzymeow.luzzyrp.data.world.LoreBook
import com.luzzymeow.luzzyrp.data.world.LoreBookRepository
import com.luzzymeow.luzzyrp.data.world.SecondaryLogic
import com.luzzymeow.luzzyrp.data.world.WorldEntry
import com.luzzymeow.luzzyrp.data.world.WorldPosition
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.pages.common.BadgeChip
import com.luzzymeow.luzzyrp.ui.pages.common.EditorHeader
import com.luzzymeow.luzzyrp.ui.pages.common.EmptyState
import com.luzzymeow.luzzyrp.ui.pages.common.FieldLabel
import com.luzzymeow.luzzyrp.ui.pages.common.LongTextEditorDialog
import com.luzzymeow.luzzyrp.ui.pages.common.LuzzySwitch
import com.luzzymeow.luzzyrp.ui.pages.common.PageHeader
import com.luzzymeow.luzzyrp.ui.pages.common.Placeholder
import com.luzzymeow.luzzyrp.ui.pages.common.PrimaryButton
import com.luzzymeow.luzzyrp.ui.pages.common.SectionTitle
import com.luzzymeow.luzzyrp.ui.pages.common.SettingCard
import com.luzzymeow.luzzyrp.ui.pages.common.ToggleRow
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 世界书（多书，v3.1）三页：**列表 → 编辑世界书 → 编辑条目**。
 *
 * 照用户提供的参考图（`app.bitbear.t` 的 SillyTavern 谱系界面）实现信息架构，
 * 视觉全部复用既有 `PageKit` / `EditorKit` 与织机 Loom token（**零新色相**）。
 *
 * 三页用**页内状态机**切换（不新增导航路由）：入口仍是侧栏「世界书」与聊天页世界书面板的
 * 「管理」——那两处的路由不变，落地即新界面。
 *
 * 参考图里有、我们**没有**的能力（如实登记，见 `docs/PLAN-worldbook-v3.1.md` §0）：
 * outlet、递归激活、包含组、token 预算、附加匹配源、触发类型；AN 两档（本应用提示词无 AN 模块）。
 */
private sealed interface LoreScreen {
    data object List : LoreScreen
    data class Edit(val bookId: String) : LoreScreen
    data class Entry(val bookId: String, val slot: Int?) : LoreScreen
}

@Composable
fun LoreBookPage(
    onOpenDrawer: () -> Unit,
    repository: LoreBookRepository? = null,
) {
    val context = LocalContext.current
    val repo = remember(repository) {
        repository ?: LoreBookRepository(LuzzyStore(DatabaseProvider.luzzy(context.applicationContext)))
    }
    var screen by remember { mutableStateOf<LoreScreen>(LoreScreen.List) }
    var reload by remember { mutableStateOf(0) }

    when (val current = screen) {
        is LoreScreen.List -> LoreBookList(
            onOpenDrawer = onOpenDrawer,
            repo = repo,
            reloadKey = reload,
            onOpenBook = { screen = LoreScreen.Edit(it) },
            onChanged = { reload++ },
        )
        is LoreScreen.Edit -> LoreBookEdit(
            repo = repo,
            bookId = current.bookId,
            reloadKey = reload,
            onBack = { screen = LoreScreen.List; reload++ },
            onEditEntry = { screen = LoreScreen.Entry(current.bookId, it) },
            onChanged = { reload++ },
        )
        is LoreScreen.Entry -> WorldEntryEdit(
            repo = repo,
            bookId = current.bookId,
            slot = current.slot,
            onBack = { screen = LoreScreen.Edit(current.bookId); reload++ },
        )
    }
}

// ───────────────────────────────── 列表页

@Composable
private fun LoreBookList(
    onOpenDrawer: () -> Unit,
    repo: LoreBookRepository,
    reloadKey: Int,
    onOpenBook: (String) -> Unit,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var books by remember { mutableStateOf<List<LoreBook>?>(null) }
    var enabledIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<LoreBook?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reloadKey) {
        books = withContext(Dispatchers.IO) { repo.all() }
        enabledIds = withContext(Dispatchers.IO) { repo.enabledGlobally() }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("无法打开读取流")
            }.getOrElse {
                notice = "导入失败：${it.message}"
                return@launch
            }
            val id = withContext(Dispatchers.IO) {
                runCatching { repo.importJson(text, fallbackName = "导入的世界书") }.getOrNull()
            }
            if (id == null) {
                notice = "导入失败：文件里没有可识别的世界书条目（支持 CCv3 / SillyTavern 格式）"
            } else {
                notice = "导入完成"
                books = withContext(Dispatchers.IO) { repo.all() }
                onChanged()
            }
        }
    }
    var pendingExport by remember { mutableStateOf<LoreBook?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val pending = pendingExport
        if (pending == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = withContext(Dispatchers.IO) { repo.exportJson(pending.id) }
            if (json == null) {
                notice = "导出失败：书不存在"
                return@launch
            }
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                    ?: error("无法打开写入流")
            }.onSuccess { notice = "已导出 ${pending.name}.json" }
                .onFailure { notice = "导出失败：${it.message}" }
        }
    }

    val visible = books?.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { PageHeader("世界书", LuzzyIcons.BookOpen, onOpenDrawer) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Placeholder("搜索世界书") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag("lore_search"),
            )
            when {
                books == null -> Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("读取中…", fontFamily = LuzzyFonts.Body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                books!!.isEmpty() -> LazyColumn(
                    // weight(1f) 而不是 fillMaxSize()：后者会吃掉全部空间，把底部「新建」按钮
                    // 挤出屏幕（本页第一版真机截图抓到的缺陷）。
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    item {
                        EmptyState(
                            iconRes = LuzzyIcons.BookOpen,
                            title = "还没有世界书",
                            supporting = "点下方「新建世界书」从头创建，或导入 SillyTavern / CCv3 格式的书。",
                        )
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(visible.orEmpty(), key = { it.id }) { book ->
                        LoreBookRow(
                            book = book,
                            enabled = book.id in enabledIds,
                            onOpen = { onOpenBook(book.id) },
                            onToggle = { next ->
                                scope.launch {
                                    withContext(Dispatchers.IO) { repo.setEnabled(book.id, next) }
                                    enabledIds = withContext(Dispatchers.IO) { repo.enabledGlobally() }
                                    onChanged()
                                }
                            },
                            onRename = { renaming = book },
                            onExport = {
                                pendingExport = book
                                exportLauncher.launch("${book.name}.json")
                            },
                            onDelete = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { repo.delete(book.id) }
                                    books = withContext(Dispatchers.IO) { repo.all() }
                                    enabledIds = withContext(Dispatchers.IO) { repo.enabledGlobally() }
                                    notice = "已删除《${book.name}》"
                                    onChanged()
                                }
                            },
                        )
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                PrimaryButton("＋ 新建世界书", onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("创建世界书", fontFamily = LuzzyFonts.Body, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OptionRow(
                        title = "新建世界书",
                        supporting = "从头开始，创建一个新世界书",
                        onClick = {
                            showCreate = false
                            scope.launch {
                                val id = withContext(Dispatchers.IO) { repo.create("未命名世界书") }
                                books = withContext(Dispatchers.IO) { repo.all() }
                                onChanged()
                                onOpenBook(id)
                            }
                        },
                    )
                    OptionRow(
                        title = "导入世界书",
                        supporting = "支持 CCv3 Spec 与 SillyTavern 格式",
                        onClick = {
                            showCreate = false
                            importLauncher.launch(arrayOf("application/json"))
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showCreate = false }) { Text("取消", fontFamily = LuzzyFonts.Body) }
            },
        )
    }

    renaming?.let { book ->
        TextInputDialog(
            title = "重命名世界书",
            initial = book.name,
            hint = "名字（必填）",
            onDismiss = { renaming = null },
            onConfirm = { name ->
                renaming = null
                if (name.isBlank()) return@TextInputDialog
                scope.launch {
                    withContext(Dispatchers.IO) { repo.rename(book.id, name) }
                    books = withContext(Dispatchers.IO) { repo.all() }
                    onChanged()
                }
            },
        )
    }

    notice?.let { text ->
        AlertDialog(
            onDismissRequest = { notice = null },
            title = { Text("世界书", fontFamily = LuzzyFonts.Body, fontWeight = FontWeight.SemiBold) },
            text = { Text(text, fontFamily = LuzzyFonts.Body, fontSize = 14.sp) },
            confirmButton = { TextButton(onClick = { notice = null }) { Text("好", fontFamily = LuzzyFonts.Body) } },
        )
    }
}

@Composable
private fun LoreBookRow(
    book: LoreBook,
    enabled: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    SettingCard {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter = painterResource(LuzzyIcons.BookOpen),
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(22.dp).clickable(onClick = onOpen),
            )
            Column(
                Modifier.weight(1f).clickable(onClick = onOpen).padding(vertical = 4.dp),
            ) {
                Text(book.name, fontFamily = LuzzyFonts.Body, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    "${book.entryCount} 条 · ${book.enabledCount} 启用" + if (enabled) " · 全局生效" else " · 未启用",
                    fontFamily = LuzzyFonts.Body,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LuzzySwitch(checked = enabled, onCheckedChange = onToggle, label = "启用 ${book.name}")
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        painter = painterResource(LuzzyIcons.DotsHorizontal),
                        contentDescription = "${book.name} 的更多操作",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("重命名", fontFamily = LuzzyFonts.Body) },
                        onClick = { menuOpen = false; onRename() },
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("导出", fontFamily = LuzzyFonts.Body) },
                        onClick = { menuOpen = false; onExport() },
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = {
                            Text("删除", fontFamily = LuzzyFonts.Body, color = MaterialTheme.colorScheme.error)
                        },
                        onClick = { menuOpen = false; confirmDelete = true },
                    )
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除世界书", fontFamily = LuzzyFonts.Body, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "将删除《${book.name}》及其 ${book.entryCount} 条条目，并从角色绑定中移除。此操作不可撤销。",
                    fontFamily = LuzzyFonts.Body,
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("删除", fontFamily = LuzzyFonts.Body, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消", fontFamily = LuzzyFonts.Body) }
            },
        )
    }
}

// ───────────────────────────────── 编辑世界书

@Composable
private fun LoreBookEdit(
    repo: LoreBookRepository,
    bookId: String,
    reloadKey: Int,
    onBack: () -> Unit,
    onEditEntry: (Int?) -> Unit,
    onChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var book by remember { mutableStateOf<LoreBook?>(null) }
    var name by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(bookId, reloadKey) {
        val loaded = withContext(Dispatchers.IO) { repo.byId(bookId) }
        book = loaded
        if (loaded != null && name.isBlank()) name = loaded.name
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            EditorHeader(
                title = "编辑世界书",
                onClose = onBack,
                trailing = {
                    TextButton(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { repo.rename(bookId, name.ifBlank { LoreBook.DEFAULT_NAME }) }
                            onChanged()
                            onBack()
                        }
                    }) { Text("保存", fontFamily = LuzzyFonts.Body, fontWeight = FontWeight.Medium) }
                },
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { FieldLabel("名字", "必填") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag("lore_book_name"),
            )
            val entries = book?.entries.orEmpty()
            val visible = entries.withIndex().filter { (_, e) ->
                query.isBlank() || e.comment.contains(query, ignoreCase = true) || e.keys.any { it.contains(query, ignoreCase = true) }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle("条目", Modifier.weight(1f))
                TextButton(onClick = { onEditEntry(null) }) { Text("添加", fontFamily = LuzzyFonts.Body) }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Placeholder("搜索条目") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            )
            if (entries.isEmpty()) {
                EmptyState(
                    iconRes = LuzzyIcons.BookOpen,
                    title = "这本书还没有条目",
                    supporting = "点上方「添加」新建第一条；条目在激活后按注入位置进入提示词。",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visible, key = { it.index }) { indexed ->
                        EntryRow(
                            entry = indexed.value,
                            onEdit = { onEditEntry(indexed.index) },
                            onDuplicate = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { repo.duplicateEntry(bookId, indexed.index) }
                                    book = withContext(Dispatchers.IO) { repo.byId(bookId) }
                                    onChanged()
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { repo.removeEntry(bookId, indexed.index) }
                                    book = withContext(Dispatchers.IO) { repo.byId(bookId) }
                                    onChanged()
                                }
                            },
                            onToggle = { next ->
                                scope.launch {
                                    withContext(Dispatchers.IO) { repo.setEntryEnabled(bookId, indexed.index, next) }
                                    book = withContext(Dispatchers.IO) { repo.byId(bookId) }
                                    onChanged()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: WorldEntry,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    SettingCard {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            BadgeChip(
                text = if (entry.constant) "常驻" else "关键词",
                tint = if (entry.constant) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f).clickable(onClick = onEdit).padding(horizontal = 6.dp, vertical = 4.dp)) {
                Text(entry.displayName, fontFamily = LuzzyFonts.Body, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    entry.triggerSummary,
                    fontFamily = LuzzyFonts.Body,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(painterResource(LuzzyIcons.Edit), "编辑", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDuplicate) {
                Icon(painterResource(LuzzyIcons.Copy), "复制", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(painterResource(LuzzyIcons.Trash), "删除", tint = MaterialTheme.colorScheme.error)
            }
            LuzzySwitch(checked = entry.enabled, onCheckedChange = onToggle, label = "启用 ${entry.displayName}")
        }
    }
}

// ───────────────────────────────── 编辑条目

@Composable
private fun WorldEntryEdit(
    repo: LoreBookRepository,
    bookId: String,
    slot: Int?,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var draft by remember(bookId, slot) { mutableStateOf<WorldEntry?>(null) }
    var pendingPicker by remember { mutableStateOf<PickerKind?>(null) }
    var editingContent by remember { mutableStateOf(false) }

    LaunchedEffect(bookId, slot) {
        draft = withContext(Dispatchers.IO) {
            slot?.let { repo.byId(bookId)?.entries?.getOrNull(it) } ?: WorldEntry()
        }
    }

    val entry = draft ?: return
    val update: (WorldEntry) -> Unit = { draft = it }

    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            EditorHeader(
                title = "编辑条目",
                onClose = onBack,
                trailing = {
                    TextButton(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { repo.upsertEntry(bookId, slot, draft ?: return@withContext) }
                            onBack()
                        }
                    }) { Text("保存", fontFamily = LuzzyFonts.Body, fontWeight = FontWeight.Medium) }
                },
            )
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = entry.comment,
                        onValueChange = { update(entry.copy(comment = it)) },
                        label = { FieldLabel("名字", "必填") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("entry_name"),
                    )
                }
                item {
                    SettingCard {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            FieldLabel("内容")
                            Text(
                                text = entry.content.ifBlank { "（点这里写内容）" },
                                fontFamily = LuzzyFonts.Body,
                                fontSize = 13.sp,
                                color = if (entry.content.isBlank()) MaterialTheme.colorScheme.outline
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth().clickable { editingContent = true }.padding(vertical = 6.dp),
                            )
                            Text(
                                "${entry.content.length} 字",
                                fontFamily = LuzzyFonts.Body,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
                item {
                    SettingCard {
                        Column(Modifier.padding(horizontal = 4.dp)) {
                            PickerRow("激活策略", if (entry.constant) "常驻" else "关键词") { pendingPicker = PickerKind.Strategy }
                            PickerRow("激活概率", "${entry.probability}%") { pendingPicker = PickerKind.Probability }
                            PickerRow("注入位置", positionLabel(entry)) { pendingPicker = PickerKind.Position }
                        }
                    }
                }
                item {
                    SectionTitle("关键词匹配")
                    SettingCard {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = WorldEntry.keysToText(entry.keys),
                                onValueChange = { update(entry.copy(keys = WorldEntry.keysFromText(it))) },
                                label = { FieldLabel("关键词") },
                                placeholder = { Placeholder("输入激活关键词，以逗号分割或正则匹配") },
                                modifier = Modifier.fillMaxWidth().testTag("entry_keys"),
                            )
                            OutlinedTextField(
                                value = WorldEntry.keysToText(entry.secondaryKeys),
                                onValueChange = { update(entry.copy(secondaryKeys = WorldEntry.keysFromText(it))) },
                                label = { FieldLabel("次级关键词") },
                                placeholder = { Placeholder("留空 = 不启用次级逻辑") },
                                modifier = Modifier.fillMaxWidth().testTag("entry_secondary"),
                            )
                            PickerRow("次级关键词匹配", if (entry.secondaryKeys.isEmpty()) "NONE" else entry.secondaryLogic.label) {
                                pendingPicker = PickerKind.SecondaryLogic
                            }
                            PickerRow("扫描深度", entry.scanDepth?.toString() ?: "跟随全局") {
                                pendingPicker = PickerKind.ScanDepth
                            }
                            ToggleRow(
                                title = "区分大小写",
                                hint = "开启后关键词按原样匹配（正则键也不再忽略大小写）",
                                checked = entry.caseSensitive,
                                onChange = { update(entry.copy(caseSensitive = it)) },
                            )
                            ToggleRow(
                                title = "全词匹配",
                                hint = "只匹配完整单词；**中文建议保持关闭**（中文不分词，开了会匹配不到）",
                                checked = entry.matchWholeWords,
                                onChange = { update(entry.copy(matchWholeWords = it)) },
                            )
                            ToggleRow(
                                title = "正则匹配",
                                hint = "关键词按 /pattern/flags 解析（上游同名能力）",
                                checked = entry.useRegex,
                                onChange = { update(entry.copy(useRegex = it)) },
                            )
                        }
                    }
                }
                item {
                    SectionTitle("延时作用")
                    SettingCard {
                        Column(Modifier.padding(horizontal = 4.dp)) {
                            PickerRow("粘性", if (entry.sticky > 0) "${entry.sticky} 条消息" else "无") {
                                pendingPicker = PickerKind.Sticky
                            }
                            PickerRow("冷却", if (entry.cooldown > 0) "${entry.cooldown} 条消息" else "无") {
                                pendingPicker = PickerKind.Cooldown
                            }
                            PickerRow("延迟", if (entry.delay > 0) "${entry.delay} 条消息" else "无") {
                                pendingPicker = PickerKind.Delay
                            }
                        }
                    }
                }
                item {
                    PrimaryButton("保存", onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { repo.upsertEntry(bookId, slot, entry) }
                            onBack()
                        }
                    }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    if (editingContent) {
        LongTextEditorDialog(
            title = "内容",
            initial = entry.content,
            placeholder = "条目内容：激活后原样进入提示词",
            footerHint = "激活后原样进入提示词",
            onCancel = { editingContent = false },
            onDone = { editingContent = false; update(entry.copy(content = it)) },
        )
    }

    pendingPicker?.let { kind ->
        PickerDialog(
            kind = kind,
            entry = entry,
            onDismiss = { pendingPicker = null },
            onPick = { updated ->
                update(updated)
                pendingPicker = null
            },
        )
    }
}

private enum class PickerKind { Strategy, Probability, Position, SecondaryLogic, ScanDepth, Sticky, Cooldown, Delay }

@Composable
private fun PickerDialog(
    kind: PickerKind,
    entry: WorldEntry,
    onDismiss: () -> Unit,
    onPick: (WorldEntry) -> Unit,
) {
    when (kind) {
        PickerKind.Probability -> NumberDialog(
            title = "激活概率",
            initial = entry.probability,
            range = 0..100,
            hint = "激活概率 0-100：条目被判定激活后再掷一次骰子，未过则不注入（每次生成每条只掷一次）",
            onDismiss = onDismiss,
            onConfirm = { onPick(entry.copy(probability = it)) },
        )
        PickerKind.ScanDepth -> NumberDialog(
            title = "扫描深度",
            initial = entry.scanDepth ?: 0,
            range = 0..100,
            hint = "只看最近 N 条消息里的关键词；0 = 跟随全局设置（默认 2）",
            onDismiss = onDismiss,
            onConfirm = { onPick(entry.copy(scanDepth = it.takeIf { value -> value > 0 })) },
        )
        PickerKind.Sticky -> NumberDialog(
            title = "粘性",
            initial = entry.sticky,
            range = 0..100,
            hint = "粘性条目被激活后，将保持激活状态 N 条消息（期间忽略概率）；0 或留空为关闭",
            onDismiss = onDismiss,
            onConfirm = { onPick(entry.copy(sticky = it)) },
        )
        PickerKind.Cooldown -> NumberDialog(
            title = "冷却",
            initial = entry.cooldown,
            range = 0..100,
            hint = "冷却条目被激活后，N 条消息内无法再次激活；0 或留空为关闭（粘性结束即进入冷却）",
            onDismiss = onDismiss,
            onConfirm = { onPick(entry.copy(cooldown = it)) },
        )
        PickerKind.Delay -> NumberDialog(
            title = "延迟",
            initial = entry.delay,
            range = 0..100,
            hint = "本聊天至少 N 条消息后才允许被激活；0 或留空为关闭",
            onDismiss = onDismiss,
            onConfirm = { onPick(entry.copy(delay = it)) },
        )
        PickerKind.Strategy -> OptionDialog(
            title = "激活策略",
            entry = entry,
            options = listOf(
                PickOption("常驻", "直接激活，但是否激活仍受概率、粘性等影响") { it.copy(constant = true) },
                PickOption("关键词", "只有当对话中匹配到关键词时，才会激活此条目") { it.copy(constant = false) },
            ),
            onDismiss = onDismiss,
            onPick = onPick,
        )
        PickerKind.SecondaryLogic -> OptionDialog(
            title = "次级关键词匹配",
            entry = entry,
            options = listOf(
                PickOption("NONE", "不使用次级关键词（只看主关键词）") { it.copy(secondaryKeys = emptyList()) },
                PickOption(SecondaryLogic.AndAny.label, "主键命中，且任一次级键命中") { it.copy(secondaryLogic = SecondaryLogic.AndAny) },
                PickOption(SecondaryLogic.AndAll.label, "主键命中，且全部次级键命中") { it.copy(secondaryLogic = SecondaryLogic.AndAll) },
                PickOption(SecondaryLogic.NotAny.label, "主键命中，且次级键一个都不命中") { it.copy(secondaryLogic = SecondaryLogic.NotAny) },
                PickOption(SecondaryLogic.NotAll.label, "主键命中，且次级键不是全部命中") { it.copy(secondaryLogic = SecondaryLogic.NotAll) },
            ),
            onDismiss = onDismiss,
            onPick = onPick,
        )
        PickerKind.Position -> OptionDialog(
            title = "注入位置",
            entry = entry,
            options = listOf(
                PickOption("↑ 角色描述之前", "注入到角色定义之前（影响较温和，适合大多数设定）") { it.copy(position = WorldPosition.BeforeChar) },
                PickOption("↓ 角色描述之后", "注入到角色定义之后（权重更高）") { it.copy(position = WorldPosition.AfterChar) },
                PickOption("↑ 对话示例之前", "注入到对话示例之前，用于引导对话风格") { it.copy(position = WorldPosition.ExampleTop) },
                PickOption("↓ 对话示例之后", "注入到对话示例之后，用于引导对话风格") { it.copy(position = WorldPosition.ExampleBottom) },
                PickOption("系统提示词开头", "注入到 system 最前（稳定块，适合全局规则）") { it.copy(position = WorldPosition.SystemTop) },
                PickOption("全局注释", "作为 system 里的全局注释注入") { it.copy(position = WorldPosition.GlobalNote) },
                PickOption("@Depth 系统", "按指定深度注入，标注为 system 角色（尾部快照，不破坏前缀缓存）") {
                    it.copy(position = WorldPosition.AtDepth, depthRole = DepthRole.System)
                },
                PickOption("@Depth 用户", "按指定深度注入，标注为 user 角色") {
                    it.copy(position = WorldPosition.AtDepth, depthRole = DepthRole.User)
                },
                PickOption("@Depth 助手", "按指定深度注入，标注为 assistant 角色") {
                    it.copy(position = WorldPosition.AtDepth, depthRole = DepthRole.Assistant)
                },
                PickOption("用户消息上方", "随用户轮次注入（尾部快照）") { it.copy(position = WorldPosition.UserTop) },
                PickOption("AI 消息上方", "随 AI 轮次注入（尾部快照）") { it.copy(position = WorldPosition.AssistantTop) },
            ),
            onDismiss = onDismiss,
            onPick = onPick,
        )
    }
}

// ───────────────────────────────── 通用小件

@Composable
private fun PickerRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontFamily = LuzzyFonts.Body, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(
            value,
            fontFamily = LuzzyFonts.Body,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            painter = painterResource(LuzzyIcons.ChevronRight),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun OptionRow(title: String, supporting: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
    ) {
        Text(title, fontFamily = LuzzyFonts.Body, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(supporting, fontFamily = LuzzyFonts.Body, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 二级选择页的一个选项：名字 + 说明 + **作用于当前草稿**的变换。 */
private data class PickOption(
    val title: String,
    val supporting: String,
    val apply: (WorldEntry) -> WorldEntry,
)

/**
 * 选项弹层（照参考图的二级选择页：标题 + 每项「名字 + 说明」）。
 *
 * 关键：变换作用在**用户当前草稿** [entry] 上（不是新条目）——否则选一次位置就把
 * 已填的名字/内容全丢了（本文件第一版正是这个错，被自己的实现评审抓出来）。
 */
@Composable
private fun OptionDialog(
    title: String,
    entry: WorldEntry,
    options: List<PickOption>,
    onDismiss: () -> Unit,
    onPick: (WorldEntry) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontFamily = LuzzyFonts.Body, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { option ->
                    OptionRow(
                        title = option.title,
                        supporting = option.supporting,
                        onClick = { onPick(option.apply(entry)) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消", fontFamily = LuzzyFonts.Body) } },
    )
}

/** 数值输入弹层（照参考图：标题 + 输入框 + 说明文案）。 */
@Composable
private fun NumberDialog(
    title: String,
    initial: Int,
    range: IntRange,
    hint: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(initial.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontFamily = LuzzyFonts.Body, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { ch -> ch.isDigit() }.take(3) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Slider(
                    value = (text.toIntOrNull() ?: initial).coerceIn(range.first, range.last).toFloat(),
                    onValueChange = { text = it.toInt().toString() },
                    valueRange = range.first.toFloat()..range.last.toFloat(),
                )
                Text(hint, fontFamily = LuzzyFonts.Body, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm((text.toIntOrNull() ?: initial).coerceIn(range.first, range.last)) }) {
                Text("确定", fontFamily = LuzzyFonts.Body)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", fontFamily = LuzzyFonts.Body) } },
    )
}

/** 文本输入弹层（重命名世界书用）。 */
@Composable
private fun TextInputDialog(
    title: String,
    initial: String,
    hint: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontFamily = LuzzyFonts.Body, fontWeight = FontWeight.SemiBold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { FieldLabel(hint) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("确定", fontFamily = LuzzyFonts.Body) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", fontFamily = LuzzyFonts.Body) } },
    )
}

/** 注入位置的可读标签（`@Depth` 带角色）。 */
private fun positionLabel(entry: WorldEntry): String = when (entry.position) {
    WorldPosition.AtDepth -> "@Depth · ${entry.depthRole.label}"
    WorldPosition.UserTop -> "用户消息上方"
    WorldPosition.AssistantTop -> "AI 消息上方"
    else -> entry.position.label
}
