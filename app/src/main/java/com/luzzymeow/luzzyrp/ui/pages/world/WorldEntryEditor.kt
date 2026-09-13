package com.luzzymeow.luzzyrp.ui.pages.world

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luzzymeow.luzzyrp.data.world.EntryRef
import com.luzzymeow.luzzyrp.data.world.WorldEntry
import com.luzzymeow.luzzyrp.data.world.WorldPosition
import com.luzzymeow.luzzyrp.data.world.WorldScope
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.pages.common.EditorHeader
import com.luzzymeow.luzzyrp.ui.pages.common.FieldLabel
import com.luzzymeow.luzzyrp.ui.pages.common.LongTextEditorDialog
import com.luzzymeow.luzzyrp.ui.pages.common.Placeholder
import com.luzzymeow.luzzyrp.ui.pages.common.PrimaryButton
import com.luzzymeow.luzzyrp.ui.pages.common.SegmentChips
import com.luzzymeow.luzzyrp.ui.pages.common.SettingCard
import com.luzzymeow.luzzyrp.ui.pages.common.ToggleRow
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts
import kotlin.math.roundToInt

/** 打开编辑器要带的东西：`ref == null` 表示新建。 */
data class WorldEditorRequest(val ref: EntryRef?, val initial: WorldEntry)

/**
 * 世界书条目编辑器（W3）。全屏 sheet，字段分组；**正文走二级全屏**（[LongTextEditorDialog]）。
 *
 * 纪律：
 * - 草稿用 `rememberSaveable`（旋转/进程重建不丢，栈规约 #7）；
 * - 数字字段保持**文本**形态，用户没输完不会被强制成 0；
 * - 未保存就退出要确认（不许静默丢编辑）；
 * - 保存是唯一的写盘点（`onSave`），取消不动数据。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldEntryEditorSheet(
    request: WorldEditorRequest,
    canBindCharacter: Boolean,
    onDismiss: () -> Unit,
    onSave: (EntryRef?, WorldEntry) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val initial = request.initial
    var draft by rememberSaveable(stateSaver = WorldEntryDraft.Saver) {
        mutableStateOf(WorldEntryDraft.from(initial))
    }
    var positionMenuOpen by remember { mutableStateOf(false) }
    var contentEditing by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val dirty = draft.toEntry() != initial

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
                .fillMaxHeight(0.96f)
                .imePadding(),
        ) {
            EditorHeader(
                title = if (request.ref == null) "新建条目" else "编辑条目",
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
                                value = draft.comment,
                                onValueChange = { draft = draft.copy(comment = it) },
                                placeholder = { Placeholder("例如：钟楼红苹果树") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("world_editor_name"),
                            )
                        }
                    }
                }

                item {
                    SettingCard {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            FieldLabel("触发", hint = "命中关键词时才注入；常驻条目不需要关键词")
                            OutlinedTextField(
                                value = draft.keysText,
                                onValueChange = { draft = draft.copy(keysText = it) },
                                placeholder = { Placeholder("多个关键词用逗号分隔") },
                                enabled = !draft.constant,
                                singleLine = true,
                                supportingText = {
                                    Text(
                                        text = if (draft.constant) "常驻条目：不按关键词匹配"
                                        else "逗号分隔；用正则时可以写 /pattern/i",
                                        fontSize = 12.sp,
                                        fontFamily = LuzzyFonts.Body,
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().testTag("world_editor_keys"),
                            )
                            ToggleRow(
                                title = "常驻",
                                hint = "每次请求都注入，不看关键词",
                                checked = draft.constant,
                                onChange = { draft = draft.copy(constant = it) },
                            )
                            ToggleRow(
                                title = "正则匹配",
                                hint = "关键词按正则解释（大小写不敏感）",
                                checked = draft.useRegex,
                                onChange = { draft = draft.copy(useRegex = it) },
                                enabled = !draft.constant,
                            )
                        }
                    }
                }

                item {
                    SettingCard {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            FieldLabel("注入", hint = "这条内容插到哪里、按什么顺序")
                            FieldLabel("范围")
                            SegmentChips(
                                labels = WorldScope.entries.map { it.label },
                                selectedIndex = WorldScope.entries.indexOf(draft.scope),
                                onSelect = { draft = draft.copy(scope = WorldScope.entries[it]) },
                                selectable = WorldScope.entries.map { it == WorldScope.Global || canBindCharacter },
                            )
                            if (!canBindCharacter) {
                                Text(
                                    text = "还没有角色，只能建全局条目",
                                    fontSize = 12.sp,
                                    fontFamily = LuzzyFonts.Body,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                            PositionPicker(
                                selected = draft.position,
                                expanded = positionMenuOpen,
                                onExpandChange = { positionMenuOpen = it },
                                onSelect = { draft = draft.copy(position = it) },
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                NumberField(
                                    label = "顺序",
                                    value = draft.orderText,
                                    hint = "同组内升序",
                                    modifier = Modifier.weight(1f),
                                    onChange = { draft = draft.copy(orderText = it) },
                                )
                                if (draft.position == WorldPosition.AtDepth) {
                                    NumberField(
                                        label = "深度",
                                        value = draft.depthText,
                                        hint = "倒数第 N 条",
                                        testTag = "world_editor_depth",
                                        modifier = Modifier.weight(1f),
                                        onChange = { draft = draft.copy(depthText = it) },
                                    )
                                }
                            }
                            NumberField(
                                label = "扫描深度（留空 = 跟随全局）",
                                value = draft.scanDepthText,
                                hint = "只影响关键词匹配的扫描范围",
                                testTag = "world_editor_scan_depth",
                                onChange = { draft = draft.copy(scanDepthText = it) },
                            )
                        }
                    }
                }

                item {
                    SettingCard {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            FieldLabel("概率", hint = "每轮只掷一次骰子，没过就不注入")
                            ToggleRow(
                                title = "按概率触发",
                                hint = "关闭时等于 100%（必定注入）",
                                checked = draft.useProbability,
                                onChange = { draft = draft.copy(useProbability = it) },
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Slider(
                                    value = draft.probability.toFloat(),
                                    onValueChange = { draft = draft.copy(probability = it.roundToInt()) },
                                    valueRange = 0f..100f,
                                    enabled = draft.useProbability,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "${draft.probability}%",
                                    fontSize = 14.sp,
                                    fontFamily = LuzzyFonts.Body,
                                    fontWeight = FontWeight.Bold,
                                    color = if (draft.useProbability) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(start = 10.dp),
                                )
                            }
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
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { contentEditing = true }
                                    .padding(4.dp),
                            )
                        }
                    }
                }

                item { Spacer(Modifier.size(8.dp)) }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(onClick = { requestDismiss() }, modifier = Modifier.weight(1f)) {
                    Text("取消", fontFamily = LuzzyFonts.Body)
                }
                PrimaryButton(
                    text = "保存",
                    modifier = Modifier.weight(1.4f).testTag("world_editor_save"),
                    onClick = { onSave(request.ref, draft.toEntry()) },
                )
            }
        }
    }

    if (contentEditing) {
        LongTextEditorDialog(
            title = draft.comment.ifBlank { "正文" },
            initial = draft.content,
            placeholder = "这段内容会在命中关键词时注入给模型",
            footerHint = "命中关键词时整段注入",
            onCancel = { contentEditing = false },
            onDone = {
                draft = draft.copy(content = it)
                contentEditing = false
            },
            testTag = "world_editor_content",
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("放弃未保存的修改？") },
            text = { Text("这条条目的改动还没保存。") },
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

/** 注入位置（7 项；别名与数字映射由 `WorldPosition.fromRaw` 在读取时处理）。 */
@Composable
private fun PositionPicker(
    selected: WorldPosition,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onSelect: (WorldPosition) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "位置",
            fontSize = 13.sp,
            fontFamily = LuzzyFonts.Body,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Box {
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onExpandChange(true) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = selected.label,
                    fontSize = 13.5.sp,
                    fontFamily = LuzzyFonts.Body,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    painter = painterResource(LuzzyIcons.ChevronDown),
                    contentDescription = "选择注入位置",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { onExpandChange(false) }) {
                WorldPosition.entries.forEach { position ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = position.label,
                                fontFamily = LuzzyFonts.Body,
                                fontSize = 14.sp,
                                color = if (position == selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        onClick = {
                            onExpandChange(false)
                            onSelect(position)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    hint: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 12.sp, fontFamily = LuzzyFonts.Body) },
        supportingText = { Text(hint, fontSize = 11.5.sp, fontFamily = LuzzyFonts.Body) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = (if (testTag != null) modifier.testTag(testTag) else modifier).fillMaxWidth(),
    )
}

/**
 * 编辑草稿。数字字段一律**文本**形态（用户没输完时别把它变成 0），
 * 落库前才在 [toEntry] 里解析并回落默认值。
 */
internal data class WorldEntryDraft(
    val comment: String,
    val keysText: String,
    val content: String,
    val enabled: Boolean,
    val scope: WorldScope,
    val position: WorldPosition,
    val orderText: String,
    val depthText: String,
    val scanDepthText: String,
    val probability: Int,
    val useProbability: Boolean,
    val useRegex: Boolean,
    val constant: Boolean,
) {

    fun toEntry(): WorldEntry = WorldEntry(
        comment = comment.trim(),
        content = content,
        keys = WorldEntry.keysFromText(keysText),
        enabled = enabled,
        scope = scope,
        position = position,
        order = orderText.trim().toIntOrNull() ?: 0,
        depth = depthText.trim().toIntOrNull() ?: 4,
        scanDepth = scanDepthText.trim().takeIf { it.isNotEmpty() }?.toIntOrNull(),
        probability = probability.coerceIn(0, 100),
        useProbability = useProbability,
        useRegex = useRegex,
        constant = constant,
    )

    companion object {
        fun from(entry: WorldEntry): WorldEntryDraft = WorldEntryDraft(
            comment = entry.comment,
            keysText = WorldEntry.keysToText(entry.keys),
            content = entry.content,
            enabled = entry.enabled,
            scope = entry.scope,
            position = entry.position,
            orderText = entry.order.toString(),
            depthText = entry.depth.toString(),
            scanDepthText = entry.scanDepth?.toString().orEmpty(),
            probability = entry.probability,
            useProbability = entry.useProbability,
            useRegex = entry.useRegex,
            constant = entry.constant,
        )

        /** 旋转/进程重建不丢草稿（Compose 栈规约 #7）。 */
        val Saver = listSaver<WorldEntryDraft, Any>(
            save = {
                listOf(
                    it.comment, it.keysText, it.content, it.enabled, it.scope.id, it.position.id,
                    it.orderText, it.depthText, it.scanDepthText, it.probability, it.useProbability,
                    it.useRegex, it.constant,
                )
            },
            restore = { values ->
                WorldEntryDraft(
                    comment = values[0] as String,
                    keysText = values[1] as String,
                    content = values[2] as String,
                    enabled = values[3] as Boolean,
                    scope = WorldScope.fromId(values[4] as String),
                    position = WorldPosition.fromId(values[5] as String) ?: WorldPosition.AtDepth,
                    orderText = values[6] as String,
                    depthText = values[7] as String,
                    scanDepthText = values[8] as String,
                    probability = values[9] as Int,
                    useProbability = values[10] as Boolean,
                    useRegex = values[11] as Boolean,
                    constant = values[12] as Boolean,
                )
            },
        )
    }
}
