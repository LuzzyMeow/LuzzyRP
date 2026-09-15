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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.luzzymeow.luzzyrp.data.store.DatabaseProvider
import com.luzzymeow.luzzyrp.data.store.LuzzyStore
import com.luzzymeow.luzzyrp.data.world.BindableCharacter
import com.luzzymeow.luzzyrp.data.world.DepthRole
import com.luzzymeow.luzzyrp.data.world.LoreBook
import com.luzzymeow.luzzyrp.data.world.LoreBookRepository
import com.luzzymeow.luzzyrp.data.world.WorldInfoSettings
import com.luzzymeow.luzzyrp.data.world.SecondaryLogic
import com.luzzymeow.luzzyrp.data.world.WorldEntry
import com.luzzymeow.luzzyrp.data.world.WorldPosition
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.pages.common.BadgeChip
import com.luzzymeow.luzzyrp.ui.pages.common.EditorHeader
import com.luzzymeow.luzzyrp.ui.pages.common.EmptyState
import com.luzzymeow.luzzyrp.ui.pages.common.EntryBadge
import com.luzzymeow.luzzyrp.ui.pages.common.EntryCard
import com.luzzymeow.luzzyrp.ui.pages.common.EntryMenuAction
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
import kotlin.math.roundToInt
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
    /**
     * 全局扫描设置（扫描深度 / 最大扫描深度）。
     *
     * **v3.2 修的一处断链**：这两个滑杆原先只挂在 `WorldInfoPage` 上，而路由走的是本页
     * （v3.1 多书架构）——也就是**功能写好了但界面上完全不可达**（改不了扫描深度）。
     * 迁到这里之后，「世界书」入口里的第一个卡片就是它。
     */
    var settings by remember { mutableStateOf(WorldInfoSettings()) }
    var query by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<LoreBook?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    /**
     * 角色绑定面板：要绑的**书** + 当前已绑的角色 + 可绑的角色清单。
     *
     * **v3.2 修的一处断链**：`setBoundTo` / `boundTo` 原先**没有任何界面调用方**——
     * 而聊天页的世界书面板早就会显示「+ 某角色 绑定」，条目编辑器也提供了 `canBindCharacter`
     * 的入口条件。也就是说「绑定」这件事在数据层与展示层都齐了，**只差一个能按下按钮的地方**
     * （用户无法把一本书指定给某个角色，绑定能力成了死代码）。
     *
     * 落点选在**书列表**而不是条目编辑器：绑定的粒度是**书**（ST 的 character lore
     * 就是「角色 → 一组书」），放在书这一层才对得上语义。
     */
    var binding by remember { mutableStateOf<LoreBook?>(null) }
    var bindable by remember { mutableStateOf<List<BindableCharacter>>(emptyList()) }
    var boundUuids by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(reloadKey) {
        books = withContext(Dispatchers.IO) { repo.all() }
        enabledIds = withContext(Dispatchers.IO) { repo.enabledGlobally() }
        settings = withContext(Dispatchers.IO) { repo.settings() }
    }

    // 打开绑定面板时读一次「可绑角色 + 已绑角色」；绑定动作后由 reloadKey 触发重读。
    LaunchedEffect(binding?.id, reloadKey) {
        val target = binding ?: return@LaunchedEffect
        bindable = withContext(Dispatchers.IO) { repo.bindableCharacters() }
        boundUuids = withContext(Dispatchers.IO) { repo.boundCharacters(target.id) }.toSet()
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
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        // 空书时也要能改扫描深度（这条曾经完全不可达，见 settings 的说明）
                        WorldSettingsCard(
                            settings = settings,
                            onCommit = { next ->
                                settings = next
                                scope.launch { withContext(Dispatchers.IO) { repo.saveSettings(next) } }
                            },
                        )
                    }
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
                    item {
                        WorldSettingsCard(
                            settings = settings,
                            onCommit = { next ->
                                settings = next
                                scope.launch { withContext(Dispatchers.IO) { repo.saveSettings(next) } }
                            },
                        )
                    }
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
                            onBind = { binding = book },
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

    binding?.let { book ->
        BindCharactersDialog(
            book = book,
            characters = bindable,
            bound = boundUuids,
            onToggle = { uuid, next ->
                scope.launch {
                    withContext(Dispatchers.IO) { repo.setBoundTo(uuid, book.id, next) }
                    // 回读真实绑定集再上屏：界面说的必须就是库里存的
                    // （乐观更新在这里会掩盖「没写进去」，而那种状态用户无从察觉）。
                    boundUuids = withContext(Dispatchers.IO) { repo.boundCharacters(book.id) }.toSet()
                    onChanged()
                }
            },
            onDismiss = { binding = null },
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

/**
 * 「绑定角色」弹层：把《书》指定给若干个角色（ST 的 character lore）。
 *
 * 语义：**绑定是叠加的**——全局启用的书对所有角色生效，绑定则是「额外只对这个角色生效」。
 * 所以文案里写清「不影响全局启用」，避免用户以为绑定会挤掉全局。
 *
 * 切换即落库（不等「保存」）：这里每行是一个独立的二元关系，没有「草稿」的概念；
 * 攒一批再保存只会制造「看着改了其实没存」的窗口。动作失败会回读真实值覆盖界面
 * （不做乐观显示——界面说绑上了而库里没有，是最坏的一种状态）。
 */
@Composable
private fun BindCharactersDialog(
    book: LoreBook,
    characters: List<BindableCharacter>,
    bound: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("绑定角色", fontFamily = LuzzyFonts.Body, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    // 文案里的排版记号要按**目标渲染器**写：Compose `Text` 不解析 Markdown，
                    // 写 `**叠加**` 会让用户看见光秃秃的星号（DESIGN-compose §23.4 登记过这个坑）。
                    // 这里改用中文引号承载强调。
                    text = "把《${book.name}》绑定给角色后，这本书的条目只在这些角色的会话里参与注入。" +
                        "与「全局启用」是叠加关系，不会取消全局生效。",
                    fontFamily = LuzzyFonts.Body,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (characters.isEmpty()) {
                    Text(
                        text = "还没有角色卡，无法绑定。先去角色卡页新建或导入一张。",
                        fontFamily = LuzzyFonts.Body,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.outline,
                    )
                } else {
                    Column(
                        Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        characters.forEach { character ->
                            ToggleRow(
                                title = character.name,
                                hint = if (character.uuid in bound) "已绑定" else "未绑定",
                                checked = character.uuid in bound,
                                onChange = { next -> onToggle(character.uuid, next) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成", fontFamily = LuzzyFonts.Body) }
        },
    )
}

@Composable
private fun LoreBookRow(
    book: LoreBook,
    enabled: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRename: () -> Unit,
    onBind: () -> Unit,
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
                        text = { Text("绑定角色", fontFamily = LuzzyFonts.Body) },
                        onClick = { menuOpen = false; onBind() },
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
            /**
             * 存储下标 → 可见序下标（排序按钮的可用性判定用；见 EntryRow 的说明）。
             */
            val visibleIndices = visible.withIndex().associate { (position, item) -> item.index to position }
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
                    /**
                     * **为什么这里的 key 是下标**（`compose-expert/references/lists-scrolling.md`
                     * 的原则是「永远不要用下标作 key」——这里是有依据的例外）：
                     *
                     * key 要求**稳定且唯一**。世界书条目**没有 id**（ST 的条目也没有），
                     * 而 `duplicateEntry` 产出的是**逐字节相同的副本**——于是：
                     * - 按内容（comment/content）作 key → 复制出来的两条**互相撞 key**，
                     *   直接抛 `IllegalArgumentException: Key was already used`；
                     * - 按 `order` 作 key → 默认全 0，同样全撞。
                     *
                     * 下标是唯一「稳定且唯一」的候选：它是条目的**身份**（`TimedEffects.keyOf` 与
                     * 仓库的全部写方法都按它寻址），上/下移时整列重排本来就是预期行为
                     * （条目没有可保留的独立 UI 状态——`EntryRow` 里只有一个菜单开合，
                     * 移动后跟着新位置走反而更合理）。
                     *
                     * 换言之：**该原则针对的是「可变列表 + 每项有独立状态/id」，
                     * 而这里两者都不成立**。若将来条目拿到 id，应立即改用它。
                     */
                    items(visible, key = { it.index }) { indexed ->
                        val positionInVisible = visibleIndices[indexed.index] ?: -1
                        EntryRow(
                            entry = indexed.value,
                            canMoveUp = positionInVisible > 0,
                            canMoveDown = positionInVisible in 0 until visible.size - 1,
                            onEdit = { onEditEntry(indexed.index) },
                            onDuplicate = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { repo.duplicateEntry(bookId, indexed.index) }
                                    book = withContext(Dispatchers.IO) { repo.byId(bookId) }
                                    onChanged()
                                }
                            },
                            onMoveUp = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { repo.moveEntry(bookId, indexed.index, -1) }
                                    book = withContext(Dispatchers.IO) { repo.byId(bookId) }
                                    onChanged()
                                }
                            },
                            onMoveDown = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { repo.moveEntry(bookId, indexed.index, +1) }
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

/**
 * 条目行 —— **收编共用件** [EntryCard]（DESIGN-compose §23.2 早已写定的落点）。
 *
 * ## 为什么从「自造的三图标行」改回 EntryCard（v3.2 收口）
 *
 * 这一行原先是自己拼的：徽标 + 两个文本 + **编辑/复制/删除三个并排 IconButton** + 开关。
 * 同一份 `EntryCard`（内含「⋯」菜单）在预设页用得好好的，于是两页同语义的行长成了两种形态——
 * 更关键的是**排序无处可放**：`LoreBookRepository.moveEntry` 写好了、**界面上接不到**
 * （用户改不了本书的条目次序）。
 *
 * 预设页同位置有「上移/下移」（`PresetsPage.kt` 的 `EntryMenuAction("上移", …)`），
 * 世界书这边缺的正是这个入口。改回共用件一步解决两件事：形态与预设页归一，
 * 且菜单天然容得下「复制」这个本页独有的动作（预设页没有）。
 *
 * ## 上移/下移到底改的是几件事（如实写清，别让按钮承诺它做不到的事）
 *
 * 它交换的是**书内的存储位次**（`slot`），由此影响三处：
 * 1. **条目列表的展示次序**——用户直接看得见的那一件事；
 * 2. **注入次序**：`groupByPosition` 用 `sortedBy { it.order }`，而 `order` 默认 0 且本页
 *    不暴露编辑 → 同 position 的条目**全部同序**，于是稳定排序让**位次成为实际次序**；
 * 3. **定时效果的身份**：`TimedEffects.keyOf(bookId, slot)` 按位次认条目，
 *    所以调序会让粘性/冷却状态跟着**位次**走（存储注释里已登记这个取舍）。
 *
 * **不承诺**的是：`system` 位置那几档（SystemTop / GlobalNote / Before / AfterChar /
 * Example）在渲染时按 `comment` 字典序重排（`PromptSections.worldSection`，为前缀缓存稳定），
 * 所以那几档的最终字节**与位次无关**——这一点写在按钮的语义里容易被误解，故在此注明。
 *
 * 排序**不做拖拽**（菜单项天然可达；pro-rules 要求「拖拽必须有非拖拽替代」），
 * 与既有约定一致。越界项显式 `enabled = false`，不做「点了没反应」。
 */
@Composable
private fun EntryRow(
    entry: WorldEntry,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    EntryCard(
        title = entry.displayName,
        checked = entry.enabled,
        onCheckedChange = onToggle,
        badges = listOf(
            EntryBadge(
                text = if (entry.constant) "常驻" else "关键词",
                tint = if (entry.constant) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.primary,
            ),
        ),
        supporting = entry.triggerSummary,
        onOpen = onEdit,
        menu = listOf(
            EntryMenuAction("编辑", onEdit),
            EntryMenuAction("复制", onDuplicate),
            EntryMenuAction("上移", onMoveUp, enabled = canMoveUp),
            EntryMenuAction("下移", onMoveDown, enabled = canMoveDown),
            EntryMenuAction("删除", { confirmDelete = true }, destructive = true),
        ),
    )

    /**
     * **删除必须过确认框**（v3.2 补的一处破坏性操作缺口）。
     *
     * 此前条目行的垃圾桶是**一点即删**——不可逆、且没有撤销；而同一页的书级删除、
     * 以及旧版（`WorldInfoPage`）的条目删除都有确认框。这不是风格差异，是操作安全性差异：
     * 世界书条目往往是手打的长文本，误触一次就没了。
     * 收编进菜单后这条纪律照旧：菜单点「删除」只开确认框，确认才真删。
     */
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除「${entry.displayName}」？", fontFamily = LuzzyFonts.Body) },
            text = {
                Text(
                    text = "这条条目会从本书里移除（${entry.triggerSummary}）。\n\n此操作不可恢复。",
                    fontFamily = LuzzyFonts.Body,
                    fontSize = 13.sp,
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
                                hint = "只匹配完整单词；中文建议保持关闭（中文不分词，开了会匹配不到）",
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

// ───────────────────────────────── 全局扫描设置（v3.2 从 WorldInfoPage 迁入）

/**
 * 世界书激活设置（扫描深度 / 最大扫描深度）。
 *
 * 两个值经 `LoreBookRepository.settings()` 读写，并由 `WorldBookActivator` 在组装请求时真的使用
 * ——也就是说这里的滑杆**不是装饰**：扫描深度决定关键词只看最近多少条消息。
 *
 * 迁入理由见 [LoreBookList] 里 `settings` 的说明：原先只挂在已不在路由里的旧页上。
 */
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
                testTag = "world_scan_depth",
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
                testTag = "world_max_depth",
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
    testTag: String,
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
            modifier = Modifier.fillMaxWidth().testTag(testTag),
        )
    }
}
