package com.luzzymeow.luzzyrp.ui.pages.characters

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luzzymeow.luzzyrp.chat.CharacterCards
import com.luzzymeow.luzzyrp.chat.PageDataSource
import com.luzzymeow.luzzyrp.data.store.DatabaseProvider
import com.luzzymeow.luzzyrp.data.store.LuzzyStore
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.pages.AvatarLoader
import com.luzzymeow.luzzyrp.ui.pages.common.LoomBadge
import com.luzzymeow.luzzyrp.ui.pages.common.LoomEmpty
import com.luzzymeow.luzzyrp.ui.pages.common.LoomIconButton
import com.luzzymeow.luzzyrp.ui.pages.common.LoomScaffold
import com.luzzymeow.luzzyrp.ui.pages.common.LoomSkeletonRow
import com.luzzymeow.luzzyrp.ui.pages.common.Placeholder
import com.luzzymeow.luzzyrp.ui.theme.Loom
import com.luzzymeow.luzzyrp.ui.theme.LoomShape
import com.luzzymeow.luzzyrp.ui.theme.LuzzySemantic
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts
import kotlinx.coroutines.launch

/**
 * 角色卡页（v3.2 重建；v3.1 Loom v4 重皮）。
 *
 * ## Loom 化（逻辑与 testTag 全部保留）
 * 织纹画布 + 大标题头；检索框 → 圆角字段（focus 珊瑚描边）；布局切换 → 40dp tonal 钮；
 * 卡片 = 2:3 封面（真头像 / monogram 降级）+ 自下而上黑渐变 + 白字标题（两行防截断）+
 * 徽标（当前使用 / 世界书 / 正则）+ 右上动作列；批量模式 = 变暗 + 中央勾选圆；
 * 空态 / 检索未命中 / 加载骨架全部 Loom 形态。
 *
 * ## 两处**有意偏离**旧版（保留，都写进注释）
 * 1. **不做「角色卡工坊 / 编辑器」以外的假按钮**（点了没反应的按钮正是要修的病）；
 * 2. **不做叠卡翻牌动画**——过渡编排要跟聊天页的转场一起设计。
 *
 * @param pageData 测试接缝（同 `MemoryPage`）。
 * @param onImportCharacter 宿主提供：拉起 SAF 选择角色卡 JSON（复用设置页那条导入通道）。
 * @param onExportCharacter 宿主提供：把某张卡导出到 SAF（带 uuid）。
 * @param onOpenChat 切到对话页（点卡片 = 用这张卡开聊）。
 */
@Composable
fun CharactersPage(
    onOpenDrawer: () -> Unit,
    pageData: PageDataSource? = null,
    onImportCharacter: () -> Unit = {},
    onExportCharacter: (String) -> Unit = {},
    onOpenChat: () -> Unit = {},
) {
    val context = LocalContext.current
    val source = remember(pageData) {
        pageData ?: PageDataSource(LuzzyStore(DatabaseProvider.luzzy(context.applicationContext)))
    }
    val scope = rememberCoroutineScope()

    // null = 还没读完（首帧不显示「0 张卡」——那会让人以为数据丢了）
    var rows by remember { mutableStateOf<List<CharacterCards.Row>?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var deck by remember { mutableStateOf(false) }
    var batchMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var deleting by remember { mutableStateOf<CharacterCards.Row?>(null) }
    var editing by remember { mutableStateOf<EditorRequest?>(null) }
    var confirmBatch by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(source, refresh) { rows = source.characterCards() }

    val all = rows
    val shown = remember(all, query) { all?.filter { it.matches(query) } ?: emptyList() }

    LoomScaffold(
        title = "角色",
        iconRes = LuzzyIcons.Assistants,
        onOpenDrawer = onOpenDrawer,
        accent = MaterialTheme.colorScheme.secondary,
        headerActions = {
            if (batchMode) {
                LoomIconButton(
                    iconRes = LuzzyIcons.Close,
                    contentDescription = "取消选择",
                    onClick = { batchMode = false; selected = emptySet() },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LoomIconButton(
                    iconRes = LuzzyIcons.Trash,
                    contentDescription = "确认删除选中的 ${selected.size} 张",
                    onClick = { if (selected.isNotEmpty()) confirmBatch = true },
                    tint = if (selected.isEmpty()) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.error,
                )
            } else {
                LoomIconButton(
                    iconRes = LuzzyIcons.Download,
                    contentDescription = "导入角色卡",
                    onClick = onImportCharacter,
                    tint = MaterialTheme.colorScheme.secondary,
                )
                LoomIconButton(
                    iconRes = LuzzyIcons.Plus,
                    contentDescription = "新建角色",
                    onClick = {
                        // 「新建角色」= 建一张空白卡并直接进编辑器（旧版同流程：新建后就在编辑器里）。
                        // 取数同样在主树里做完再开窗。
                        scope.launch {
                            val uuid = source.createCharacter()
                            if (uuid == null) {
                                message = "新建失败：写库出错"
                            } else {
                                refresh++
                                editing = EditorRequest(
                                    uuid = uuid,
                                    draft = source.characterDraft(uuid),
                                    avatarPath = source.characterAvatarPath(uuid),
                                )
                            }
                        }
                    },
                    tint = MaterialTheme.colorScheme.secondary,
                )
                LoomIconButton(
                    iconRes = LuzzyIcons.Trash,
                    contentDescription = "批量删除",
                    onClick = { batchMode = true; selected = emptySet() },
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 检索 + 布局切换
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Placeholder("检索角色卡名称或描述…") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(54.dp).testTag("character_search"),
                )
                LoomIconButton(
                    iconRes = if (deck) LuzzyIcons.Grid else LuzzyIcons.Cards,
                    contentDescription = if (deck) "切换为网格布局" else "切换为叠卡布局",
                    onClick = { deck = !deck },
                )
            }
            Text(
                text = when {
                    all == null -> "正在读取…"
                    query.isNotBlank() -> "命中 ${shown.size} / ${all.size} 张角色卡"
                    else -> "${all.size} 张角色卡" + if (batchMode) " · 已选 ${selected.size}" else ""
                },
                fontSize = 12.sp,
                fontFamily = LuzzyFonts.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, bottom = 6.dp),
            )

            when {
                all == null -> Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(3) { LoomSkeletonRow(height = 150.dp) }
                }
                all.isEmpty() -> LoomEmpty(
                    iconRes = LuzzyIcons.Assistants,
                    title = "库里还没有角色卡",
                    supporting = "点右上角「＋」导入角色卡（PNG / JSON）；导入后这里会列出，并带上真实头像。",
                )
                shown.isEmpty() -> LoomEmpty(
                    iconRes = LuzzyIcons.Search,
                    title = "未找到匹配的角色卡",
                    supporting = "尝试更换检索关键词（检索范围：名称与描述）。",
                )
                else -> LazyVerticalGrid(
                    columns = if (deck) GridCells.Fixed(1) else GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().testTag("character_grid"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(shown, key = { it.uuid }) { card ->
                        CharacterCard(
                            card = card,
                            batchMode = batchMode,
                            selected = card.uuid in selected,
                            onOpen = {
                                // 点卡 = 切成当前角色并进对话（旧版 `select` 的语义）
                                scope.launch {
                                    source.setActiveCharacter(card.uuid)
                                    onOpenChat()
                                }
                            },
                            onToggleSelect = {
                                selected = if (card.uuid in selected) selected - card.uuid else selected + card.uuid
                            },
                            onFavorite = {
                                scope.launch {
                                    val ok = source.setCharacterFavorite(card.uuid, !card.favorite)
                                    message = when {
                                        !ok -> "收藏失败：这张卡已经不在了"
                                        card.favorite -> "已取消收藏「${card.name}」"
                                        else -> "已收藏「${card.name}」"
                                    }
                                    refresh++
                                }
                            },
                            onExport = { onExportCharacter(card.uuid) },
                            onDelete = { deleting = card },
                            onEdit = {
                                scope.launch {
                                    // **取数在主树里做完再开窗**：草稿与头像一次读齐，
                                    // 对话框拿到的是已经就绪的值（细节见 CharacterEditorDialog 的注释）
                                    editing = EditorRequest(
                                        uuid = card.uuid,
                                        draft = source.characterDraft(card.uuid),
                                        avatarPath = source.characterAvatarPath(card.uuid),
                                    )
                                }
                            },
                        )
                    }
                }
            }
            message?.let { text ->
                Text(
                    text = text,
                    fontSize = 12.sp,
                    fontFamily = LuzzyFonts.Body,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp),
                )
            }
        }
    }

    editing?.let { request ->
        CharacterEditorDialog(
            uuid = request.uuid,
            source = source,
            initialDraft = request.draft,
            initialAvatarPath = request.avatarPath,
            onClose = { editing = null },
            onSaved = { name ->
                editing = null
                message = "已保存「$name」"
                refresh++
            },
        )
    }

    deleting?.let { card ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleting = null },
            shape = RoundedCornerShape(LoomShape.Card),
            containerColor = Loom.current.raised,
            title = { Text("删除「${card.name}」？", fontFamily = LuzzyFonts.Body, color = MaterialTheme.colorScheme.error) },
            text = {
                Text(
                    text = "将删除这张角色卡，以及它的**全部会话与记忆**（主线与所有分支），" +
                        "并同步删除 ${card.worldInfoCount} 条角色世界书与 ${card.regexCount} 条正则。\n\n" +
                        "此操作不可恢复。",
                    fontFamily = LuzzyFonts.Body,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    deleting = null
                    scope.launch {
                        val ok = source.deleteCharacter(card.uuid)
                        message = if (ok) "已删除「${card.name}」及其会话数据" else "删除失败"
                        refresh++
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { deleting = null }) { Text("取消") }
            },
        )
    }

    if (confirmBatch) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmBatch = false },
            shape = RoundedCornerShape(LoomShape.Card),
            containerColor = Loom.current.raised,
            title = { Text("删除选中的 ${selected.size} 张角色卡？", fontFamily = LuzzyFonts.Body, color = MaterialTheme.colorScheme.error) },
            text = {
                Text(
                    text = "每张卡的**全部会话与记忆**（主线与所有分支）会一并删除。\n\n此操作不可恢复。",
                    fontFamily = LuzzyFonts.Body,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmBatch = false
                    val targets = selected.toList()
                    scope.launch {
                        var removed = 0
                        targets.forEach { if (source.deleteCharacter(it)) removed++ }
                        batchMode = false
                        selected = emptySet()
                        message = if (removed > 0) "已删除 $removed 张角色卡" else "删除失败"
                        refresh++
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmBatch = false }) { Text("取消") }
            },
        )
    }
}

/**
 * 一张角色卡（Loom 形态）。
 *
 * 卡面 = **角色自己的头像**（解码失败或无头像 → 首字 monogram 大字，见 [AvatarLoader] 的降级纪律）。
 * 黑渐变保证白字标题在任意画面上可读；选中/当前态用 tonal 层表达。
 */
@Composable
private fun CharacterCard(
    card: CharacterCards.Row,
    batchMode: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
    onEdit: () -> Unit,
    onFavorite: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val scale by animateFloatAsState(
        targetValue = if (batchMode && selected) 0.97f else 1f,
        animationSpec = com.luzzymeow.luzzyrp.ui.theme.loomSpring(),
        label = "char-card-scale",
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(LoomShape.Card))
            .let {
                if (selected) it.border2(scheme.secondary) else it
            },
        shape = RoundedCornerShape(LoomShape.Card),
        color = Loom.current.card,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) scheme.secondary else Loom.current.hairline,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable { if (batchMode) onToggleSelect() else onOpen() },
        ) {
            CharacterCover(card)
            // 底部渐变：白字标题压在任意画面上都要读得清
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xFF141413).copy(alpha = 0.78f)),
                            startY = 0f,
                        ),
                    ),
            )

            if (card.isActive && !batchMode) {
                Box(Modifier.align(Alignment.TopStart).padding(10.dp)) {
                    LoomBadge("当前使用", Loom.current.success)
                }
            }

            if (batchMode) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) scheme.secondary else Color.White.copy(alpha = 0.28f),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(
                                painter = painterResource(LuzzyIcons.Check),
                                contentDescription = "已选中",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            } else {
                Column(
                    Modifier.align(Alignment.TopEnd).padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    CardAction(iconRes = LuzzyIcons.Edit, desc = "编辑 ${card.name}", tint = Color.White, onClick = onEdit)
                    CardAction(
                        iconRes = LuzzyIcons.Star,
                        desc = if (card.favorite) "取消收藏 ${card.name}" else "收藏 ${card.name}",
                        tint = if (card.favorite) Color(0xFFE9B949) else Color.White,
                        onClick = onFavorite,
                    )
                    CardAction(iconRes = LuzzyIcons.Download, desc = "导出 ${card.name}", tint = Color.White, onClick = onExport)
                    CardAction(iconRes = LuzzyIcons.Trash, desc = "删除 ${card.name}", tint = Color.White, onClick = onDelete)
                }
            }

            Column(
                Modifier.align(Alignment.BottomStart).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = card.name,
                    fontFamily = LuzzyFonts.Lora,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    // **两行**而不是一行：150% 系统字号下中文名一行放不下，
                    // `maxLines = 1` 会把名字**静默截断**（截图实测：「钟楼下的小恶魔」→「钟楼下的小恶」）。
                    // 卡片高度由 2:3 比例固定，名字多占一行只会往上挤一点，不会把卡撑破。
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp,
                )
                if (!batchMode) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        LoomBadge("${card.worldInfoCount} 世界书", Color.White)
                        LoomBadge("${card.regexCount} 正则", Color.White)
                    }
                }
            }
        }
    }
}

private fun Modifier.border2(color: androidx.compose.ui.graphics.Color): Modifier =
    this.border(2.dp, color, RoundedCornerShape(LoomShape.Card))

/** 卡面头像（真图 / 首字降级）。 */
@Composable
private fun CharacterCover(card: CharacterCards.Row) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, card.avatarPath) {
        // 卡面按**大尺寸**解码（列表小头像的 192px 铺满整张卡会糊）
        value = AvatarLoader.load(card.avatarPath, AvatarLoader.CoverPixels)
    }
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = "${card.name} 的头像",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        // 降级：首字 monogram。**这条路径必须真的走得到**——夹具里 2/3 的头像是 SVG
        // （BitmapFactory 解不了），所以它不是「理论上存在的兜底」。
        //
        // 底色用**固定的深暖中性色**而不是主题 role，理由有二：
        // 1. 卡面名字与动作图标都是**白字**，底色必须恒为深色——用 `primaryContainer` 时
        //    亮色主题是浅粉（白字看不见）、暗色主题是深棕（与真图卡糊成一片），两头都不对；
        // 2. 深色底 + 浅色首字在亮/暗两套主题下**表现一致**，不需要按主题再分支。
        Box(
            Modifier.fillMaxSize().background(Color(0xFF2E2724)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = card.monogram,
                fontFamily = LuzzyFonts.Lora,
                fontSize = 44.sp,
                color = Color.White.copy(alpha = 0.72f),
            )
        }
    }
}

/** 卡面右上角的圆形动作钮（白底半透明 + 白色图标，压在任意画面上都可辨）。 */
@Composable
private fun CardAction(iconRes: Int, desc: String, tint: Color, onClick: () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = desc,
            tint = tint,
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.28f))
                .padding(5.dp),
        )
    }
}

/**
 * 打开编辑器的请求：**uuid + 已读好的草稿与头像**。
 *
 * 为什么不是只传 uuid、让对话框自己读：那版实测「值读回来了但界面不刷新」
 * （日志打出 `草稿载入 … → ok`，界面却停在「正在读取角色卡…」——`Dialog` 的内容跑在
 * 独立窗口的 composition 里，时序与主树不同）。取数留在主树 → 开窗即渲染，行为可预测。
 */
private data class EditorRequest(
    val uuid: String,
    val draft: CharacterCards.Draft?,
    val avatarPath: String?,
)