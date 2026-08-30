package com.luzzymeow.luzzyrp.ui.pages.character

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.luzzymeow.luzzyrp.ui.components.AuroraSurface
import com.luzzymeow.luzzyrp.ui.components.LuzzyTextField
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.theme.LuzzySpacing
import java.io.File

/**
 * 角色卡详情/编辑（5.x 重构）。
 *
 * 结构（DESIGN.md 图层层次）：hero 头像区 → 形象（提示词/性格/场景）→ 开场白（<CUT> 多泡）
 * → 聊天背景（不设默认用头像 + 透明度）→ 世界书（二级页入口）→ 导出。
 * 内置卡只读；手动新建经「new」卡 id 进入本页。
 */
@Composable
fun CharacterDetailPage(cardId: String, onBack: () -> Unit, onOpenWorldbook: (String) -> Unit = {}) {
    val viewModel: CharacterDetailViewModel =
        org.koin.androidx.compose.koinViewModel { org.koin.core.parameter.parametersOf(cardId) }
    val card by viewModel.card.collectAsState()
    val message by viewModel.message.collectAsState()

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var personality by remember { mutableStateOf("") }
    var scenario by remember { mutableStateOf("") }
    var firstMes by remember { mutableStateOf("") }
    var opacity by remember { mutableIntStateOf(45) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(card) {
        card?.let { c ->
            if (!initialized) {
                initialized = true
                name = c.name
                description = c.description
                personality = c.personality
                scenario = c.scenario
                firstMes = c.firstMes
                opacity = c.chatBackground.opacity
            }
        }
    }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::setAvatar)
    }
    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::setBackground)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // 顶栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = LuzzySpacing.XS, vertical = LuzzySpacing.SM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(LuzzyIcons.Back.res), contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                if (cardId == "new") "新建角色卡" else "角色卡",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (card?.readonly != true) {
                IconButton(onClick = viewModel::exportJson) {
                    Icon(painterResource(LuzzyIcons.Code.res), contentDescription = "导出 JSON",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = viewModel::exportPng) {
                    Icon(painterResource(LuzzyIcons.Share.res), contentDescription = "导出 PNG（SillyTavern 兼容）",
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        message?.let {
            Text(it, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = LuzzySpacing.LG))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LuzzySpacing.LG),
        ) {
            // —— Hero 头像区 ——
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = LuzzySpacing.LG),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.BottomEnd) {
                    if (card?.avatarPath != null && File(card!!.avatarPath!!).exists()) {
                        AsyncImage(
                            model = File(card!!.avatarPath!!),
                            contentDescription = "角色头像",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(120.dp).clip(CircleShape),
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(120.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                name.take(1).ifBlank { "角" },
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    if (card?.readonly != true) {
                        Surface(
                            onClick = { avatarPicker.launch("image/*") },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(painterResource(LuzzyIcons.Edit.res), contentDescription = "更换头像（自动 1:1 裁剪）",
                                    tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.size(LuzzySpacing.SM))
                if (card?.readonly == true) {
                    Text("内置角色 · 只读", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // —— 形象 ——
            SectionTitle("角色设定（提示词）")
            LuzzyTextField(name, { name = it }, placeholder = "名字 *",
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.size(LuzzySpacing.MD))
            LuzzyTextField(description, { description = it }, placeholder = "角色设定（世界观/外貌/经历…）",
                minLines = 4, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.size(LuzzySpacing.MD))
            LuzzyTextField(personality, { personality = it }, placeholder = "性格",
                minLines = 2, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.size(LuzzySpacing.MD))
            LuzzyTextField(scenario, { scenario = it }, placeholder = "场景",
                minLines = 2, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.size(LuzzySpacing.LG))
            SectionTitle("开场白")
            LuzzyTextField(
                firstMes, { firstMes = it },
                placeholder = "角色发送的第一条消息。\n使用 <CUT> 分割多条开场白（输出为多个气泡）。",
                minLines = 4, modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.size(LuzzySpacing.LG))
            SectionTitle("聊天背景")
            AuroraSurface(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (card?.chatBackground?.path != null) "已设置背景图" else "未设置（默认使用头像）",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (card?.readonly != true) {
                            androidx.compose.material3.TextButton(onClick = { bgPicker.launch("image/*") }) {
                                Text("选择图片")
                            }
                        }
                    }
                    Text("透明度：$opacity%", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = opacity.toFloat(),
                        onValueChange = { opacity = it.toInt() },
                        valueRange = 0f..100f,
                    )
                }
            }

            Spacer(Modifier.size(LuzzySpacing.LG))
            SectionTitle("世界书")
            AuroraSurface(
                onClick = {
                    // 4.6：世界书入口并入角色卡（二级页）
                    card?.let { c -> onOpenWorldbook(c.id) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(LuzzyIcons.Book.res), contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(LuzzySpacing.MD))
                    Column(Modifier.weight(1f)) {
                        Text("世界书管理", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "条目的名称/内容/激活策略/概率/注入位置",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(painterResource(LuzzyIcons.Expand.res), contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.size(LuzzySpacing.LG))
            if (card?.readonly != true) {
                androidx.compose.material3.Button(
                    onClick = { viewModel.save(name, description, personality, scenario, firstMes, opacity) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("保存") }
            }
            Spacer(Modifier.size(LuzzySpacing.XXXL))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = LuzzySpacing.SM),
    )
}
