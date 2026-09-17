package com.luzzymeow.luzzyrp.ui.pages.characters

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luzzymeow.luzzyrp.chat.CharacterCards
import com.luzzymeow.luzzyrp.chat.PageDataSource
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.pages.AvatarLoader
import com.luzzymeow.luzzyrp.ui.pages.common.EditorHeader
import com.luzzymeow.luzzyrp.ui.pages.chat.ChatPalette
import com.luzzymeow.luzzyrp.ui.pages.common.FieldLabel
import com.luzzymeow.luzzyrp.ui.pages.common.Placeholder
import com.luzzymeow.luzzyrp.ui.pages.common.PrimaryButton
import com.luzzymeow.luzzyrp.ui.pages.common.SegmentChips
import com.luzzymeow.luzzyrp.ui.pages.common.LocalLoomBottomInset
import com.luzzymeow.luzzyrp.ui.pages.common.LocalLoomTopInset
import com.luzzymeow.luzzyrp.ui.pages.common.loomCanvas
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts
import kotlinx.coroutines.launch

/**
 * **角色卡编辑器**（v3.2 补；旧版 `CharacterEditorModal` 的等价物）。
 *
 * ## 为什么补它
 *
 * 重建角色卡页时**有意没有放编辑按钮**——放一个点了没反应的按钮正是那一轮在修的病
 * （见 `DESIGN-compose §34.4`）。这一轮把编辑器做出来，按钮才跟着回来。
 *
 * ## 四段与旧版逐字段对应
 *
 * | tab | 字段 | 键名 | 进不进提示词 |
 * |---|---|---|---|
 * | 基础 | 头像 + 角色名称 | `avatar` / `name` | 名字进（角色块的标题行） |
 * | 描述 | 简短描述 | `description` | 进（角色块） |
 * | 人设 | 具体人设 | `personality` | 进（角色块） |
 * | 开场白 | 开场白 | `first_mes` | 进（新会话的首条 AI 消息） |
 *
 * 也就是说**编辑器里改的是真的会被模型读到的东西**，不是只改显示名。
 *
 * ## 保存纪律
 *
 * 草稿只在这四个键上生效（[CharacterCards.withDraft]），payload 其余部分**逐字保留**；
 * 名字会**同时写列与 payload**（列给列表页读，payload 给组装读，只写一处会两边不一致）。
 * 名称是唯一必填项：空白时「保存」禁用，而不是存下一张看不到名字的卡。
 */
@Composable
fun CharacterEditorDialog(
    uuid: String,
    source: PageDataSource,
    /** 打开前由调用方读好的草稿（null = 还没读到，界面显示一行提示）。 */
    initialDraft: CharacterCards.Draft?,
    initialAvatarPath: String?,
    onClose: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    /**
     * 草稿由**调用方在开窗前读好**，本对话框不做取数。
     *
     * 第一版是在这里的 `LaunchedEffect` 里读的：日志证明值**读回来了**
     * （`草稿载入 uuid=… → ok`），但界面始终停在「正在读取角色卡…」——那次赋值没能让
     * Dialog 子树重组（`Dialog` 的内容跑在独立窗口的 composition 里，时序与主树不同）。
     * 与其在独立窗口里跟时序纠缠，不如把取数留在主树、开窗只做展示与编辑：
     * 这样对话框是纯的、可预测的，也更好测。
     */
    var draft by remember(uuid) { mutableStateOf(initialDraft) }
    var avatarPath by remember(uuid) { mutableStateOf(initialAvatarPath) }
    var tab by remember { mutableStateOf(0) }
    var saving by remember { mutableStateOf(false) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            if (source.setCharacterAvatar(context, uuid, uri)) {
                // 换了图立刻刷新预览：文件名固定是 <uuid>.png，**必须清缓存**否则显示旧图
                AvatarLoader.clearCache()
                avatarPath = source.characterAvatarPath(uuid)
            }
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            // 与 LongTextEditorDialog 同口径：全屏编辑器连状态栏一起铺满，
            // 否则顶部会透出被 dim 的下层（一条深灰带），画布断裂。
            decorFitsSystemWindows = false,
        ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .loomCanvas(MaterialTheme.colorScheme.secondary)
                .imePadding(),
        ) {
            Column(
                // 宿主下发的状态栏高度（见 LocalLoomTopInset 的 KDoc）
                Modifier.padding(top = LocalLoomTopInset.current),
            ) {
                EditorHeader(
                    title = "编辑角色",
                    onClose = onClose,
                    trailing = {
                        TextButton(
                            onClick = {
                                val current = draft ?: return@TextButton
                                saving = true
                                scope.launch {
                                    val ok = source.saveCharacterDraft(uuid, current)
                                    saving = false
                                    if (ok) onSaved(current.name) else onClose()
                                }
                            },
                            enabled = draft?.isValid == true && !saving,
                        ) {
                            Text(
                                text = if (saving) "保存中…" else "保存角色",
                                fontFamily = LuzzyFonts.Body,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    },
                )
            }

            SegmentChips(
                labels = listOf("基础", "描述", "人设", "开场白"),
                selectedIndex = tab,
                onSelect = { tab = it },
                // 草稿还没读回来时不可切（否则会先在空草稿上编辑，回读后又被覆盖）
            )

            val current = draft
            if (current == null) {
                Text(
                    text = "正在读取角色卡…",
                    fontFamily = LuzzyFonts.Body,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(16.dp),
                )
                return@Column
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (tab) {
                    0 -> {
                        // 头像预览（2:3）+ 换图（旧版是「悬停出现遮罩 + 更换图片」；触屏没有悬停，
                        // 所以这里明放一个按钮——触屏上的悬停交互等于没有入口）
                        Box(
                            Modifier
                                .align(Alignment.CenterHorizontally)
                                .fillMaxWidth(0.55f)
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(com.luzzymeow.luzzyrp.ui.theme.LoomShape.Hero)),
                        ) {
                            EditorCover(name = current.name, avatarPath = avatarPath)
                        }
                        TextButton(
                            onClick = { pickImage.launch("image/*") },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Icon(
                                painter = painterResource(LuzzyIcons.Download),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "  更换头像",
                                fontFamily = LuzzyFonts.Body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedTextField(
                            value = current.name,
                            onValueChange = { draft = current.copy(name = it) },
                            label = { Text("角色名称（必填）", fontFamily = LuzzyFonts.Body) },
                            singleLine = true,
                            isError = current.name.isBlank(),
                            supportingText = if (current.name.isBlank()) {
                                { Text("名字不能为空：列表与角色块都要用它", fontFamily = LuzzyFonts.Body) }
                            } else {
                                null
                            },
                            modifier = Modifier.fillMaxWidth().testTag("character_name"),
                        )
                    }

                    1 -> FieldEditor(
                        label = "简短描述",
                        hint = "对角色的简短介绍（一句话也够）",
                        value = current.description,
                        onChange = { draft = current.copy(description = it) },
                        testTag = "character_description",
                    )

                    2 -> FieldEditor(
                        label = "具体人设",
                        hint = "性格、外貌、喜好、说话方式……这一段是模型理解「他是谁」的主要来源",
                        value = current.personality,
                        onChange = { draft = current.copy(personality = it) },
                        testTag = "character_personality",
                    )

                    else -> FieldEditor(
                        label = "开场白",
                        hint = "新会话的第一条消息（first_mes）；留空则新会话从你的第一句话开始",
                        value = current.firstMes,
                        onChange = { draft = current.copy(firstMes = it) },
                        testTag = "character_first_mes",
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = LocalLoomBottomInset.current)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("取消") }
                PrimaryButton(
                    text = if (saving) "保存中…" else "保存角色",
                    onClick = {
                        val toSave = draft ?: return@PrimaryButton
                        saving = true
                        scope.launch {
                            val ok = source.saveCharacterDraft(uuid, toSave)
                            saving = false
                            if (ok) onSaved(toSave.name) else onClose()
                        }
                    },
                    modifier = Modifier.weight(2f),
                )
            }
        }
    }
}

/** 一个长文本字段（标题 + 字数 + 多行输入）。 */
@Composable
private fun FieldEditor(
    label: String,
    hint: String,
    value: String,
    onChange: (String) -> Unit,
    testTag: String,
) {
    FieldLabel(label, hint)
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Placeholder(hint) },
        minLines = 8,
        modifier = Modifier.fillMaxWidth().testTag(testTag),
    )
    Text(
        text = "${value.length} 字",
        fontSize = 12.sp,
        fontFamily = LuzzyFonts.Body,
        color = MaterialTheme.colorScheme.outline,
    )
}

/** 编辑器里的头像预览（与卡面同一套解码与降级，尺寸取卡面档）。 */
@Composable
private fun EditorCover(name: String, avatarPath: String?) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, avatarPath) {
        value = AvatarLoader.load(avatarPath, AvatarLoader.CoverPixels)
    }
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = "角色头像",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Box(
            Modifier.fillMaxSize().background(ChatPalette.MonogramBase),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.trim().take(1).ifBlank { "角" },
                fontFamily = LuzzyFonts.Lora,
                fontSize = 44.sp,
                color = Color.White.copy(alpha = 0.72f),
            )
        }
    }
}
