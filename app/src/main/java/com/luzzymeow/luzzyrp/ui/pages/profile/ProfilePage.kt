package com.luzzymeow.luzzyrp.ui.pages.profile

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.luzzymeow.luzzyrp.core.model.UserProfile
import com.luzzymeow.luzzyrp.data.datastore.SettingsStore
import com.luzzymeow.luzzyrp.ui.components.AuroraSurface
import com.luzzymeow.luzzyrp.ui.components.LuzzyTextField
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.theme.LuzzySpacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** 用户档案 ViewModel（7.1：头像/名字/身份 → settings.userProfile，注入 system）。 */
class ProfileViewModel(
    private val context: android.content.Context,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    val settings: StateFlow<com.luzzymeow.luzzyrp.data.datastore.Settings?> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun save(name: String, identity: String) {
        viewModelScope.launch {
            settingsStore.update {
                it.copy(userProfile = it.userProfile.copy(name = name.trim(), identity = identity.trim()))
            }
        }
    }

    /** 头像：复制到应用私有目录后记录路径（居方 1:1 压缩）。 */
    fun setAvatar(uri: android.net.Uri) {
        viewModelScope.launch {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@runCatching
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: return@runCatching
                val side = minOf(bitmap.width, bitmap.height)
                val cropped = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, side, side)
                val scaled = if (side > 512) {
                    android.graphics.Bitmap.createScaledBitmap(cropped, 512, 512, true)
                } else cropped
                val file = File(context.filesDir, "avatars/user_profile.png").apply { parentFile?.mkdirs() }
                file.outputStream().use { scaled.compress(android.graphics.Bitmap.CompressFormat.PNG, 92, it) }
                settingsStore.update { it.copy(userProfile = it.userProfile.copy(avatarPath = file.absolutePath)) }
            }
        }
    }

}

@Composable
fun ProfilePage(onBack: () -> Unit) {
    val viewModel: ProfileViewModel = org.koin.androidx.compose.koinViewModel()
    val settings by viewModel.settings.collectAsState()
    val profile = settings?.userProfile ?: UserProfile()

    var name by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var identity by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    androidx.compose.runtime.LaunchedEffect(profile) {
        if (name.isBlank() && identity.isBlank()) {
            name = profile.name
            identity = profile.identity
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::setAvatar)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = LuzzySpacing.XS, vertical = LuzzySpacing.SM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(LuzzyIcons.Back.res), contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface)
            }
            Text("用户档案", style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        }

        Column(Modifier.padding(LuzzySpacing.LG), horizontalAlignment = Alignment.CenterHorizontally) {
            // 头像（点击更换）
            Box(
                modifier = Modifier.size(112.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                if (profile.avatarPath != null && File(profile.avatarPath).exists()) {
                    AsyncImage(
                        model = File(profile.avatarPath),
                        contentDescription = "用户头像",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(112.dp).clip(CircleShape),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            profile.name.take(1).ifBlank { "我" },
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
                Surface(
                    onClick = { picker.launch("image/*") },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(LuzzyIcons.Edit.res), contentDescription = "更换头像",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.size(LuzzySpacing.XL))

            AuroraSurface(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    "档案将注入对话（AI 会记住你是谁）；头像用于菜单与对话展示。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(LuzzySpacing.LG))

            LuzzyTextField(name, { name = it }, placeholder = "名字（如：阿澈）", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.size(LuzzySpacing.MD))
            LuzzyTextField(identity, { identity = it }, placeholder = "身份/自我设定（如：大学生，喜欢科幻与夜跑）",
                minLines = 3, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.size(LuzzySpacing.LG))

            androidx.compose.material3.Button(
                onClick = { viewModel.save(name, identity) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存") }
        }
    }
}
