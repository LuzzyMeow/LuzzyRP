package com.luzzymeow.luzzyrp.ui.pages.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.pages.chat.AvatarCircle
import com.luzzymeow.luzzyrp.ui.pages.chat.CircleIconButton
import com.luzzymeow.luzzyrp.ui.pages.chat.InputIsland
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts
import kotlinx.coroutines.launch

/** P1 假数据（灯塔守夜人场景，与方向板同源）。 */
private val fakeConversation: List<FakeMessage> = listOf(
    FakeMessage.Thinking,
    FakeMessage.Ai(
        branch = "‹ 2/3 ›",
        paragraphs = listOf(
            FakeParagraph.Narration("潮水退到第三级石阶时，她把灯芯捻亮了一格。塔内的钟摆声停了，雾从窗缝里渗进来，带着盐与旧木头的气味。"),
            FakeParagraph.Action("指尖还残留着灯油的温度，她望向雾里的航道，很久没有说话"),
            FakeParagraph.Speech("你迟到了整整一班潮讯。"),
            FakeParagraph.Narration("她的声音很轻，像怕惊动了塔里的钟。灯焰在她瞳孔里晃出两簇很小的火。"),
        ),
    ),
    FakeMessage.User("船在雾里丢了方向……你点的灯，我在岸上看见了。"),
    FakeMessage.Ai(
        paragraphs = listOf(
            FakeParagraph.Narration("她笑了一下，把灯抬高。光晕在雾里慢慢晕开，像一枚温柔的印章，盖在整片灰白的海面上。"),
            FakeParagraph.Action("转身从架子上取下另一盏备用灯，掂了掂"),
            FakeParagraph.Speech("那这盏，就借给你带到下一座塔去。"),
            FakeParagraph.Narration("雾更深了，但航道上的浮标一颗接一颗亮了起来——从灯塔脚下，一直排到看不见的远方。"),
        ),
    ),
    FakeMessage.Ai(
        paragraphs = listOf(
            FakeParagraph.Narration("「夜航的人不怕黑，」她把新灯递过来时说，「怕的是没有人等他进港。」"),
            FakeParagraph.Action("重新坐回灯下，翻开那本被海风卷了角的航志"),
            FakeParagraph.Narration("纸页沙沙作响，仿佛有另一双手，也在很久以前的某个雾夜里，写过同样的一行字。"),
        ),
    ),
)

/** P1 聊天页：顶栏 + 假数据消息流 + 输入岛 + 抽屉（Modal，宽屏 Permanent 待 P5 自适应）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPage(
    darkMode: Boolean,
    onToggleDarkMode: () -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                Text(
                    text = "LuzzyRP",
                    fontFamily = LuzzyFonts.Lora,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(20.dp),
                )
                val drawerIcons = listOf(
                    LuzzyIcons.Conversation to "对话",
                    LuzzyIcons.Assistants to "角色",
                    LuzzyIcons.BookOpen to "世界书",
                    LuzzyIcons.Sliders to "预设",
                    LuzzyIcons.Memory to "记忆",
                    LuzzyIcons.ChartBar to "用量",
                    LuzzyIcons.Settings to "设置",
                )
                drawerIcons.forEachIndexed { i, (icon, item) ->
                    ListItem(
                        leadingContent = {
                            Icon(
                                painter = painterResource(icon),
                                contentDescription = null,
                                tint = if (i == 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        headlineContent = {
                            Text(
                                text = item,
                                fontFamily = LuzzyFonts.Body,
                                fontSize = 14.sp,
                                color = if (i == 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (i == 0) FontWeight.Medium else FontWeight.Normal,
                            )
                        },
                    )
                }
            }
        },
    ) {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            AvatarCircle(initial = "L")
                            Column {
                                Text(
                                    text = "Luna",
                                    fontFamily = LuzzyFonts.Lora,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "灯塔守夜人 · 在线",
                                    fontFamily = LuzzyFonts.Body,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                painterResource(LuzzyIcons.Menu),
                                contentDescription = "打开菜单",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        // 方向板 A 组件语言：圆形描边动作钮（模型 ⌄ / 更多 ⋯）；月亮为 P1 主题验证入口
                        CircleIconButton(
                            iconRes = LuzzyIcons.ChevronDown,
                            contentDescription = "模型（P1 占位）",
                            onClick = {},
                        )
                        Spacer(Modifier.width(6.dp))
                        CircleIconButton(
                            iconRes = LuzzyIcons.DotsHorizontal,
                            contentDescription = "更多（P1 占位）",
                            onClick = {},
                        )
                        Spacer(Modifier.width(6.dp))
                        CircleIconButton(
                            iconRes = if (darkMode) LuzzyIcons.Sun else LuzzyIcons.Moon,
                            contentDescription = if (darkMode) "切换亮色" else "切换暗色",
                            onClick = onToggleDarkMode,
                        )
                        Spacer(Modifier.width(10.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
            },
            bottomBar = {
                InputIsland(
                    onToggleTheme = onToggleDarkMode,
                )
            },
        ) { innerPadding: PaddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp),
                reverseLayout = false,
            ) {
                items(fakeConversation.size) { i ->
                    Box(Modifier.fillMaxWidth()) {
                        when (val m = fakeConversation[i]) {
                            is FakeMessage.Thinking ->
                                ThinkingCardCollapsed("思考 · 已折叠 · 1.2s")
                            is FakeMessage.Ai ->
                                AiMessage(m)
                            is FakeMessage.User ->
                                UserBubble(m.text)
                        }
                    }
                }
            }
        }
    }
}
