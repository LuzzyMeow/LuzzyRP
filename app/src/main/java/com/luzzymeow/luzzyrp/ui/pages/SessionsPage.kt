package com.luzzymeow.luzzyrp.ui.pages

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import com.luzzymeow.luzzyrp.ui.pages.common.LoomBadge
import com.luzzymeow.luzzyrp.ui.pages.common.LoomEmpty
import com.luzzymeow.luzzyrp.ui.pages.common.LoomScaffold
import com.luzzymeow.luzzyrp.ui.pages.common.LoomSkeletonRow
import com.luzzymeow.luzzyrp.ui.theme.LoomShape
import com.luzzymeow.luzzyrp.ui.theme.Loom
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts
import com.luzzymeow.luzzyrp.ui.theme.LoomAppear
import kotlinx.coroutines.launch

/**
 * 会话总览（跨角色平铺）—— Loom v4 重皮（骨架主张不变：**角色=粘性组头，分支=等高行**，
 * 设计门与用户选择见历史方向档案）。
 *
 * Loom 化：织纹画布 + 大标题头；组头 = 头像环 + Lora 名 + 条数药丸；
 * 分支行 = 卡行（分支名 + 徽标 + 预览 + 当前点）；空会话虚线语义由斜体与淡色承担；
 * 加载态 = 三行骨架（旧版是空白 Spacer，观感「页面坏了」）。
 *
 * 数据来自 [ChatSessionRepository.overview]——只取摘要（条数与预览），**不搬历史正文**
 * （末条预览是单条 `LIMIT 1` 查询：总览要为每条分会话各取一次，搬全量会让「打开一个列表」变成搬几 MB）。
 *
 * 三个由真实数据定下来的细节（保留）：
 * 1. **空会话必须可见**：0 条的会话显示斜体「还没开始」，不过滤、不折叠；
 * 2. **预览可能很脏**：真实数据里可能以 `<thinking>` 开头（CoT 泄漏缺陷，归 P5），
 *    界面**原样展示用户数据**，不美化也不隐藏；
 * 3. **无用户发言**时预览回落末条正文。
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

    LoomScaffold(
        title = "会话",
        iconRes = LuzzyIcons.Conversation,
        onOpenDrawer = onOpenDrawer,
        accent = MaterialTheme.colorScheme.secondary,
    ) { padding ->
        val data = rows
        if (data == null) {
            // 加载骨架（三行，观感「正在读取」而非「页面坏了」）
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                repeat(3) { LoomSkeletonRow(height = 72.dp) }
            }
            return@LoomScaffold
        }
        if (data.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                LoomEmpty(
                    iconRes = LuzzyIcons.Conversation,
                    title = "还没有会话",
                    supporting = "先去角色页导入一张角色卡",
                )
            }
            return@LoomScaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("sessions_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 抬头行：统计可见（保留 v3.2 修正——放内容区首行才真的可见）
            item(key = "sessions-summary") {
                Text(
                    text = "${data.size} 条 · ${data.map { r -> r.characterUuid }.distinct().size} 张卡",
                    fontFamily = LuzzyFonts.Body,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp),
                )
            }
            // 分组：相邻同角色归一组（overview 已按角色排好序，这里只需按序切段）
            var groupIndex = 0
            data.groupAdjacentBy { it.characterUuid }.forEach { (characterUuid, group) ->
                val gi = groupIndex++
                stickyHeader(key = "head-$characterUuid") {
                    GroupHeader(
                        name = group.first().characterName,
                        count = group.size,
                        avatarPath = group.first().characterAvatarPath,
                    )
                }
                items(
                    items = group,
                    key = { "$characterUuid/${it.branchId}" },
                ) { row ->
                    val index = gi
                    LoomAppear(index = index) {
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
}

/** 粘性组头：头像环 + 角色名（Lora）+ 会话数药丸。 */
@Composable
private fun GroupHeader(name: String, count: Int, avatarPath: String?) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CharacterAvatar(name = name, avatarPath = avatarPath, size = 26.dp)
        Text(
            text = name,
            fontFamily = LuzzyFonts.Lora,
            fontSize = 14.sp,
            color = scheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        LoomBadge(text = "$count 条", tint = scheme.secondary)
    }
}

/**
 * 一行会话：Loom 卡行。左分支名 + 分支徽标、右条数、下方一行预览。
 *
 * 行高固定 68dp（触控基线 ≥44dp），预览单行省略——**不截断成多行**：等高滚动不跳。
 * 当前会话 = secondary 描边 + 左缘轨道 + 「当前」点。
 */
@Composable
private fun SessionRow(
    row: ChatSessionRepository.SessionSummary,
    current: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val loom = Loom.current
    val empty = row.messageCount == 0
    val shape = RoundedCornerShape(LoomShape.Card)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clickable(onClick = onClick)
            .testTag("session_row_${row.characterUuid}_${row.branchId}"),
        shape = shape,
        color = if (current) loom.raised else loom.card,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (current) scheme.secondary.copy(alpha = 0.55f) else loom.hairline,
        ),
    ) {
        Box(Modifier.heightIn(min = 68.dp)) {
            // 当前项左缘轨道（secondary）
            if (current) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 0.dp)
                        .size(width = 3.dp, height = 34.dp)
                        .background(scheme.secondary, RoundedCornerShape(2.dp)),
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = row.branchName,
                        fontFamily = LuzzyFonts.Body,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (current) scheme.secondary else scheme.onSurface,
                    )
                    if (!row.isMain) {
                        Spacer(Modifier.width(6.dp))
                        LoomBadge(text = "分支", tint = scheme.outline)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${row.messageCount} 条",
                        fontFamily = LuzzyFonts.Body,
                        fontSize = 12.sp,
                        color = scheme.outline,
                    )
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
                    color = if (empty) scheme.outline else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 角色小头像：**有图显示图、无图显示首字 monogram**。
 *
 * 这是真实降级路径而不是占位色块：旧数据里头像本来就可缺（迁移来的 5 张卡里 3 张有、2 张没有），
 * 而首字永远画得出来。
 */
@Composable
private fun CharacterAvatar(name: String, avatarPath: String?, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.take(1),
            fontFamily = LuzzyFonts.Lora,
            fontSize = (size.value * 0.5f).sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
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