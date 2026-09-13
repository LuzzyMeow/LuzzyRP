package com.luzzymeow.luzzyrp.ui.pages.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.luzzymeow.luzzyrp.data.chat.ChatSessionRepository
import com.luzzymeow.luzzyrp.data.preset.PresetRepository
import com.luzzymeow.luzzyrp.data.preset.PresetRole
import com.luzzymeow.luzzyrp.data.preset.PresetRow
import com.luzzymeow.luzzyrp.data.store.DatabaseProvider
import com.luzzymeow.luzzyrp.data.store.LuzzyStore
import com.luzzymeow.luzzyrp.data.world.WorldBook
import com.luzzymeow.luzzyrp.data.world.WorldBookRepository
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.luzzymeow.luzzyrp.chat.ModelCatalog
import com.luzzymeow.luzzyrp.chat.TransportConfig
import com.luzzymeow.luzzyrp.chat.WorldBookTool
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.pages.common.BadgeChip
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts

/**
 * 输入岛功能行弹出的三个**真面板**（2026-09-12 用户要求把图标做成实质功能）。
 *
 * 共同纪律：面板里展示的每一条都来自真实来源（网络响应 / 真实配置 / 真实角色数据），
 * 不做占位清单；失败如实展示错误，不伪装成空列表。
 */

/** 模型面板：**真实** `GET {base}/models`，选中即写回配置并影响后续请求。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    config: TransportConfig,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(config.baseUrl, config.apiKey) {
        loading = true
        error = null
        ModelCatalog.fetch(config).fold(
            onSuccess = { models = it },
            onFailure = { error = it.message ?: "拉取失败" },
        )
        loading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SheetHeader(icon = LuzzyIcons.Conversation, title = "切换模型")
            Text(
                text = ModelCatalog.modelsEndpoint(config).ifEmpty { "（未配置 Base URL）" },
                fontSize = 11.sp,
                fontFamily = LuzzyFonts.Mono,
                color = MaterialTheme.colorScheme.outline,
            )
            Text(
                text = "当前：${config.model.ifBlank { "未设置" }}",
                fontSize = 12.sp,
                fontFamily = LuzzyFonts.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when {
                loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = "正在向供应商拉取模型列表…",
                        fontSize = 12.5.sp,
                        fontFamily = LuzzyFonts.Body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                error != null -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "拉取失败：$error",
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp,
                        fontFamily = LuzzyFonts.Body,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = "可在输入岛右侧的「未配置供应商 / 模型」处改成手动填写模型名。",
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                        fontFamily = LuzzyFonts.Body,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }

                models.isEmpty() -> Text(
                    text = "供应商返回了空列表——请确认该端点是否支持 /models。",
                    fontSize = 12.5.sp,
                    fontFamily = LuzzyFonts.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> Column(
                    Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    models.forEach { id ->
                        val active = id == config.model
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f),
                                )
                                .border(
                                    1.dp,
                                    if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable { onSelect(id) }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = id,
                                fontSize = 13.sp,
                                fontFamily = LuzzyFonts.Mono,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            if (active) BadgeChip("当前", MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

/** 工具面板：开关**真实影响请求体**（关闭后不再发送 tools）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsSheet(
    config: TransportConfig,
    onToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SheetHeader(icon = LuzzyIcons.Mcp, title = "工具")
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(12.dp),
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = WorldBookTool.Name,
                        fontSize = 13.sp,
                        fontFamily = LuzzyFonts.Mono,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "世界书检索：回复涉及设定细节时由模型自主调用，应用侧真实执行",
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                        fontFamily = LuzzyFonts.Body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = config.toolsEnabled, onCheckedChange = onToggle)
            }
            Text(
                text = if (config.toolsEnabled) {
                    "已开启：请求体会带上 tools，模型可以请求工具，思考时间线会出现「调用工具」节点。"
                } else {
                    "已关闭：请求体不再发送 tools，模型无从请求工具，时间线不会出现工具节点。"
                },
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                fontFamily = LuzzyFonts.Body,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/**
 * 世界书面板（**真数据**，只读）：列出当前会参与注入的条目——全局 + 当前角色绑定。
 *
 * 为什么只读：编辑在「世界书」页（全屏、有字段说明与二级正文编辑器），
 * 在聊天里的半屏面板上改这些字段会挤成一团。这里给一个「管理」直达入口。
 *
 * 面板上如实写着本版边界：**检索注入尚未接入**（P5），现在改条目还不会影响回复。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldBookSheet(
    onDismiss: () -> Unit,
    onManage: () -> Unit,
    repository: WorldBookRepository? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val repo = remember(repository) {
        repository ?: run {
            val store = LuzzyStore(DatabaseProvider.luzzy(context.applicationContext))
            WorldBookRepository(store, ChatSessionRepository(store))
        }
    }
    var book by remember { mutableStateOf<WorldBook?>(null) }
    LaunchedEffect(repo) { book = repo.load() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SheetHeader(
                icon = LuzzyIcons.BookOpen,
                title = "世界书",
                trailing = book?.let { "${it.rows.size} 条" },
            )
            Text(
                text = "来源：本机数据（全局" +
                    (book?.characterName?.let { " + $it 绑定" } ?: "") +
                    "）。本版面板只读与跳转管理；**检索注入在 P5 接入**——现在改条目还不会影响回复。",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontFamily = LuzzyFonts.Body,
                color = MaterialTheme.colorScheme.outline,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetAction("管理", onManage)
                SheetAction("关闭", onDismiss, subtle = true)
            }
            val current = book
            when {
                current == null -> Text(
                    text = "读取中…",
                    fontSize = 12.5.sp,
                    fontFamily = LuzzyFonts.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                current.isEmpty -> Text(
                    text = "还没有世界书条目。到「世界书」页新建——全局条目对所有角色生效，绑定条目只对当前角色生效。",
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                    fontFamily = LuzzyFonts.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> Column(
                    Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    current.rows.forEach { row ->
                        SheetEntryCard(
                            title = row.entry.displayName,
                            badges = buildList {
                                add(row.group.label to MaterialTheme.colorScheme.primary)
                                if (!row.entry.enabled) add("已停用" to MaterialTheme.colorScheme.outline)
                                if (row.entry.constant) add("常驻" to MaterialTheme.colorScheme.tertiary)
                                if (row.entry.useProbability && row.entry.probability < 100) {
                                    add("概率 ${row.entry.probability}%" to MaterialTheme.colorScheme.outline)
                                }
                            },
                            supporting = row.entry.triggerSummary,
                            body = row.entry.content,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 预设面板（**真数据**，只读）：只列**启用中**的条目——因为只有它们参与组装
 * （上游 `app.js:4534-4536` 把 `enabled=false` 与空正文直接丢弃）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetsSheet(
    onDismiss: () -> Unit,
    onManage: () -> Unit,
    repository: PresetRepository? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val repo = remember(repository) {
        repository ?: PresetRepository(LuzzyStore(DatabaseProvider.luzzy(context.applicationContext)))
    }
    var rows by remember { mutableStateOf<List<PresetRow>?>(null) }
    LaunchedEffect(repo) { rows = repo.enabled() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SheetHeader(
                icon = LuzzyIcons.Sliders,
                title = "预设",
                trailing = rows?.let { "启用中 ${it.size} 条" },
            )
            Text(
                text = "顺序即注入顺序：系统类按列表序拼进 system，User / AI 类作为独立消息插入。" +
                    "本版面板只读与跳转管理；**拼进请求在 P5 接入**——现在改这些还不会影响回复。",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontFamily = LuzzyFonts.Body,
                color = MaterialTheme.colorScheme.outline,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetAction("管理", onManage)
                SheetAction("关闭", onDismiss, subtle = true)
            }
            val current = rows
            when {
                current == null -> Text(
                    text = "读取中…",
                    fontSize = 12.5.sp,
                    fontFamily = LuzzyFonts.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                current.isEmpty() -> Text(
                    text = "没有启用中的预设。到「预设」页新建或打开某一条。",
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                    fontFamily = LuzzyFonts.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> Column(
                    Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    current.forEach { row ->
                        SheetEntryCard(
                            title = row.entry.displayName,
                            badges = listOf(
                                row.entry.role.label to when (row.entry.role) {
                                    PresetRole.System -> MaterialTheme.colorScheme.primary
                                    PresetRole.User -> MaterialTheme.colorScheme.secondary
                                    PresetRole.Assistant -> MaterialTheme.colorScheme.tertiary
                                },
                            ),
                            supporting = row.entry.summary,
                            body = null,
                        )
                    }
                }
            }
        }
    }
}

/** 面板里的一张条目卡（只读展示：名称 + 徽标 + 触发说明 + 正文摘录）。 */
@Composable
private fun SheetEntryCard(
    title: String,
    badges: List<Pair<String, androidx.compose.ui.graphics.Color>>,
    supporting: String,
    body: String?,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                fontSize = 13.5.sp,
                fontFamily = LuzzyFonts.Body,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            badges.forEach { (text, tint) -> BadgeChip(text, tint) }
        }
        Text(
            text = supporting,
            fontSize = 12.sp,
            fontFamily = LuzzyFonts.Body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!body.isNullOrBlank()) {
            Text(
                text = body.lineSequence().filter { it.isNotBlank() }.take(6).joinToString("\n"),
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontFamily = LuzzyFonts.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 面板上的小动作按钮（管理 / 关闭）。 */
@Composable
private fun SheetAction(text: String, onClick: () -> Unit, subtle: Boolean = false) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (subtle) MaterialTheme.colorScheme.surfaceContainerHigh
                else MaterialTheme.colorScheme.primaryContainer,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontFamily = LuzzyFonts.Body,
            fontWeight = FontWeight.Medium,
            color = if (subtle) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun SheetHeader(icon: Int, title: String, trailing: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = title,
            fontFamily = LuzzyFonts.Lora,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        trailing?.let {
            Text(
                text = it,
                fontSize = 12.sp,
                fontFamily = LuzzyFonts.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
