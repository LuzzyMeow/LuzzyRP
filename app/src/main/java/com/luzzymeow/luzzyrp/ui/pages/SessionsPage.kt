package com.luzzymeow.luzzyrp.ui.pages

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luzzymeow.luzzyrp.chat.ChatBranch
import com.luzzymeow.luzzyrp.data.chat.ChatSessionRepository
import com.luzzymeow.luzzyrp.data.store.DatabaseProvider
import com.luzzymeow.luzzyrp.data.store.LuzzyStore
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.pages.common.EmptyState
import com.luzzymeow.luzzyrp.ui.pages.common.PageHeader
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts
import kotlinx.coroutines.launch

/**
 * 会话总览（跨角色平铺）—— **方向 B · 分组行**
 * （设计门与用户选择见 `docs/design/boards-v5/direction-approved-v5.md`）。
 *
 * 骨架主张（与另两个方向的结构性差异在 `direction-summary.md`）：**角色=粘性组头，分支=等高行**
 * （左分支名 + 右条数 + 下方一行预览）。信息效率优先，一屏扫完所有会话。
 *
 * 数据来自 [ChatSessionRepository.overview]——只取摘要（条数与预览），**不搬历史正文**
 * （末条预览是单条 `LIMIT 1` 查询：总览要为每条分会话各取一次，搬全量会让「打开一个列表」变成搬几 MB）。
 *
 * 三个由真实数据定下来的细节（都在 `SPEC.md` 里记着原因）：
 * 1. **空会话必须可见**：0 条的会话显示斜体「还没开始」，不过滤、不折叠——
 *    用户会用它想起「我本来想开这条线」；
 * 2. **预览可能很脏**：真实数据里有一条末条正文以 `<thinking>` 开头（CoT 泄漏缺陷，归 P5），
 *    界面**原样展示用户数据**，不美化也不隐藏；
 * 3. **无用户发言**时预览回落末条正文（见 [ChatSessionRepository.SessionSummary.previewText]）。
 */
@Composable
fun SessionsPage(
    onOpenDrawer: () -> Unit,
    /** 选中某条会话后的去向（切到该角色该分支）。 */
    onOpenSession: (characterUuid: String, branchId: String) -> Unit,
    /**
     * 仓库注入点。**测试接缝**（与 ChatPage 同一约定）：不注入的话仪器化测试会去读设备上真实的
     * `luzzy.db`——「页面初始状态取决于这台机器恰好有什么数据」是隐藏耦合，本机绿换机红。
     */
    sessionRepository: ChatSessionRepository? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(sessionRepository) {
        sessionRepository ?: ChatSessionRepository(LuzzyStore(DatabaseProvider.luzzy(context.applicationContext)))
    }
    var rows by remember { mutableStateOf<List<ChatSessionRepository.SessionSummary>?>(null) }
    var activeCharacter by remember { mutableStateOf<String?>(null) }
    var activeBranch by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        rows = repository.overview()
        activeCharacter = repository.currentCharacterUuid()
        activeBranch = activeCharacter?.let { repository.currentBranchId(it) }
    }

    PageScaffold(
        title = "会话",
        iconRes = LuzzyIcons.Conversation,
        onOpenDrawer = onOpenDrawer,
        trailing = {
            rows?.let {
                Text(
                    text = "${it.size} 条 · ${it.map { r -> r.characterUuid }.distinct().size} 张卡",
                    fontFamily = LuzzyFonts.Body,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        },
    ) { padding ->
        val data = rows
        if (data == null) {
            Spacer(Modifier.fillMaxSize().padding(padding))
            return@PageScaffold
        }
        if (data.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    iconRes = LuzzyIcons.Conversation,
                    title = "还没有会话",
                    supporting = "先去角色页导入一张角色卡",
                )
            }
            return@PageScaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("sessions_list"),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            // 分组：相邻同角色归一组（overview 已按角色排好序，这里只需按序切段）
            data.groupAdjacentBy { it.characterUuid }.forEach { (characterUuid, group) ->
                stickyHeader(key = "head-$characterUuid") {
                    GroupHeader(
                        name = group.first().characterName,
                        count = group.size,
                        // 角色卡有头像文件就显示图（真实数据里可能没有——那时用首字 monogram）
                        avatarPath = group.first().characterAvatarPath,
                    )
                }
                items(
                    items = group,
                    key = { "$characterUuid/${it.branchId}" },
                ) { row ->
                    SessionRow(
                        row = row,
                        current = row.characterUuid == activeCharacter && row.branchId == activeBranch,
                        onClick = {
                            scope.launch {
                                repository.rememberActiveCharacter(row.characterUuid)
                                repository.rememberActiveBranch(row.characterUuid, row.branchId)
                                onOpenSession(row.characterUuid, row.branchId)
                            }
                        },
                    )
                }
            }
        }
    }
}

/** 粘性组头：monogram（或真实头像）+ 角色名 + 会话数。 */
@Composable
private fun GroupHeader(name: String, count: Int, avatarPath: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CharacterAvatar(name = name, avatarPath = avatarPath, size = 22.dp)
        Text(
            text = name,
            fontFamily = LuzzyFonts.Lora,
            fontSize = 13.5.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "$count 条",
            fontFamily = LuzzyFonts.Body,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/**
 * 一行会话：左分支名、右条数、下方一行预览。
 *
 * 行高固定 68dp（触控基线 ≥44dp，ui-ux-pro-max 优先级 2），预览单行省略——
 * **不截断成多行**：等高是这一方向的骨架主张，滚动时才不会跳。
 */
@Composable
private fun SessionRow(
    row: ChatSessionRepository.SessionSummary,
    current: Boolean,
    onClick: () -> Unit,
) {
    val empty = row.messageCount == 0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clickable(onClick = onClick)
            .testTag("session_row_${row.characterUuid}_${row.branchId}")
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.branchName,
                fontFamily = LuzzyFonts.Body,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (current) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
            if (!row.isMain) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "分支",
                    fontFamily = LuzzyFonts.Body,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "${row.messageCount} 条",
                fontFamily = LuzzyFonts.Body,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline,
            )
            if (current) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(6.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            // 预览文字**原样**来自用户数据（可能带 <thinking> 之类的前缀，不美化）
            text = when {
                empty -> "还没开始"
                row.previewText.isNullOrBlank() -> "只有开场白"
                else -> row.previewText
            },
            fontFamily = LuzzyFonts.Body,
            fontSize = 12.5.sp,
            lineHeight = 17.sp,
            fontStyle = if (empty) FontStyle.Italic else FontStyle.Normal,
            color = if (empty) MaterialTheme.colorScheme.outline
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
    )
}

/**
 * 角色小头像：**有图显示图、无图显示首字 monogram**。
 *
 * 这是真实降级路径而不是占位色块：旧数据里头像本来就可缺（迁移来的 5 张卡里 3 张有、2 张没有），
 * 而首字永远画得出来。位图解码在 P5 接入（`DESIGN-compose` §20.3 第 7 项已登记）。
 */
@Composable
private fun CharacterAvatar(name: String, avatarPath: String?, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.take(1),
            fontFamily = LuzzyFonts.Lora,
            fontSize = (size.value * 0.5f).sp,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 与既有七页同构的页面骨架（透明 TopAppBar + LazyColumn）。 */
@Composable
private fun PageScaffold(
    title: String,
    iconRes: Int,
    onOpenDrawer: () -> Unit,
    trailing: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            PageHeader(
                title = title,
                iconRes = iconRes,
                onOpenDrawer = onOpenDrawer,
                actions = { trailing() },
            )
        },
    ) { padding -> content(padding) }
}

/** `groupAdjacentBy`：按 key 把**相邻**同组元素切段（保持原顺序，不重排）。 */
private fun <T, K> List<T>.groupAdjacentBy(key: (T) -> K): List<Pair<K, List<T>>> {
    if (isEmpty()) return emptyList()
    val out = mutableListOf<Pair<K, MutableList<T>>>()
    for (item in this) {
        val k = key(item)
        val last = out.lastOrNull()
        if (last != null && last.first == k) last.second.add(item) else out.add(k to mutableListOf(item))
    }
    return out.map { it.first to it.second.toList() }
}

/** 分支主线的判定（呈现层用；与存储层的 `isMain` 语义一致）。 */
private val ChatSessionRepository.SessionSummary.isMain: Boolean
    get() = branchId == ChatBranch.MainId
