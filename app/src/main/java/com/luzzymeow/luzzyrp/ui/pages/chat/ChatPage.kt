package com.luzzymeow.luzzyrp.ui.pages.chat

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luzzymeow.luzzyrp.R
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 聊天页 · 沉浸形态（DESIGN-compose §12）：全屏角色背景 + 顶栏黑渐隐 + 雾纸玻璃气泡 +
 * 假流式 + 思考卡节点 + 功能 icon 输入岛。角色 = Vanio（示例卡，P1 演示资产）。
 */

/** Vanio 的假 RP 对话（贴合角色提示词：恶魔角尾/橙眼/蓝发/红帽斗篷/教堂+苹果；P2 换真实数据）。 */
private fun vanioScript(): List<FakeMessage> = listOf(
    FakeMessage.User("Vanio？听说你在教堂后面藏了什么……"),
    FakeMessage.Ai(
        paragraphs = listOf(
            FakeParagraph.Narration("少年被吓得差点把苹果抛出去。他僵着脖子回头，帽檐下的橘色眼睛瞪得溜圆，红斗篷下的蝙蝠翅膀不安分地扑棱了两下。"),
            FakeParagraph.Speech("嘘——！小声点！要是被嬷嬷听见，我攒了一个秋天的宝贝就全完啦。"),
            FakeParagraph.Action("左右看了看，把那颗红得发亮的苹果塞进兜里，冲你勾了勾手指"),
            FakeParagraph.Narration("他蹲在彩绘窗洒下的光斑里，靴尖沾着落叶。看那副神秘兮兮的样子，好像石头缝里真的藏着什么了不得的东西。"),
        ),
    ),
    FakeMessage.User("行行行，我不喊。所以……到底是什么？"),
    FakeMessage.Ai(
        branch = "‹ 2/3 ›",
        paragraphs = listOf(
            FakeParagraph.Speech("嘿嘿，想知道？"),
            FakeParagraph.Action("凑近你的耳边，用气声说道"),
            FakeParagraph.Narration("「是长在钟楼顶上的、一整树的红苹果。全城只有我知道那棵树在哪——因为呀，」他晃了晃帽子上小小的角，得意地眯起眼，「恶魔的果子，只有恶魔找得到。」"),
        ),
    ),
)

/** 思考步骤假数据。 */
private val thinkSteps = listOf(
    ThinkStep("解析用户意图：追问隐藏物——延续此前「藏苹果」伏笔"),
    ThinkStep("检索角色设定：Vanio 恶魔少年，怕嬷嬷发现，得意于秘密；决定先受惊再炫耀"),
    ThinkStep("组织回复节奏：受惊 → 压低声音 → 邀请 → 揭晓「钟楼红苹果树」，保留悬念钩子"),
)

/** 假流式产出的 AI 回复。 */
private val streamingReply = listOf(
    FakeParagraph.Speech("你就承认了吧，你根本不知道那棵树。"),
    FakeParagraph.Narration("Vanio 把脸鼓成包子，草莓红的发梢气得一颤一颤。他跳起来要去捂你的嘴，尾巴却先一步出卖了他——尾巴尖正指向钟楼的方向。"),
    FakeParagraph.Speech("……哎呀！！"),
)

/** 流式状态机（P1 假流式；P2 换 v2.0 Kotlin 传输事件）。 */
private enum class GenState { Idle, Thinking, Streaming, Done }

/** 假流式：按进度把段落切片渲染。 */
private fun renderStreaming(paragraphs: List<FakeParagraph>, progress: Int): List<FakeParagraph> {
    val out = mutableListOf<FakeParagraph>()
    var remain = progress
    for (p in paragraphs) {
        if (remain <= 0) break
        when (p) {
            is FakeParagraph.Narration -> {
                out += FakeParagraph.Narration(p.text.take(remain)); remain -= p.text.length
            }
            is FakeParagraph.Action -> {
                out += FakeParagraph.Action(p.text.take(remain)); remain -= p.text.length
            }
            is FakeParagraph.Speech -> {
                out += FakeParagraph.Speech(p.text.take(remain)); remain -= p.text.length
            }
        }
    }
    return out
}

private fun FakeParagraph.textLength(): Int = when (this) {
    is FakeParagraph.Narration -> text.length
    is FakeParagraph.Action -> text.length
    is FakeParagraph.Speech -> text.length
}

/** 顶栏黑玻璃圆钮（上游移动端顶栏按钮语言：black 28% 圆底 + 白 icon）。 */
@Composable
private fun ImmersiveIconButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.IconButton(onClick = onClick, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(Color.Black.copy(alpha = 0.28f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 沉浸顶栏（M3 TopAppBar 透明底 + 白字 + 黑玻璃动作钮；黑渐隐由背景层 scrim 提供）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImmersiveTopBar(
    darkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    onOpenDrawer: () -> Unit,
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(36.dp).clip(CircleShape)) {
                    Image(
                        painter = painterResource(R.drawable.vanio_card),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column {
                    Text(
                        text = "Vanio",
                        fontFamily = LuzzyFonts.Lora,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                    Text(
                        text = "教堂后的小恶魔 · 在线",
                        fontFamily = LuzzyFonts.Body,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(
                    painter = painterResource(LuzzyIcons.Menu),
                    contentDescription = "打开菜单",
                    tint = Color.White.copy(alpha = 0.92f),
                )
            }
        },
        actions = {
            IconButton(onClick = onToggleDarkMode) {
                Icon(
                    painter = painterResource(if (darkMode) LuzzyIcons.Sun else LuzzyIcons.Moon),
                    contentDescription = "切换主题",
                    tint = Color.White.copy(alpha = 0.92f),
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
    )
}

/** P1 聊天页 · 沉浸形态。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPage(
    darkMode: Boolean,
    onToggleDarkMode: () -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val hazeState = remember { HazeState() }
    val listState = rememberLazyListState()

    // ── 假流式状态机（P1 demo；P2 接 Kotlin 传输事件） ──
    val messages = remember { mutableStateListOf<FakeMessage>().apply { addAll(vanioScript()) } }
    var genState by remember { mutableStateOf(GenState.Idle) }
    var thinkProgress by remember { mutableStateOf(0) }
    var streamProgress by remember { mutableStateOf(0) }
    var pendingReply by remember {
        mutableStateOf(FakeMessage.Ai(paragraphs = listOf(FakeParagraph.Narration(""))))
    }

    LaunchedEffect(genState) {
        when (genState) {
            GenState.Thinking -> {
                for (i in thinkSteps.indices) {
                    thinkProgress = i + 1
                    delay(700)
                }
                genState = GenState.Streaming
            }
            GenState.Streaming -> {
                val total = streamingReply.fold(0) { acc, p -> acc + p.textLength() } + streamingReply.size * 2
                streamProgress = 0
                // 起始帧完整性：流式玻璃容器在循环前已渲染（空内容容器）
                while (streamProgress < total) {
                    streamProgress += 2
                    pendingReply = FakeMessage.Ai(paragraphs = renderStreaming(streamingReply, streamProgress))
                    delay(22)
                    listState.requestScrollToItem(messages.size + 1)
                }
                pendingReply = FakeMessage.Ai(paragraphs = streamingReply)
                messages.add(pendingReply)
                genState = GenState.Done
            }
            else -> Unit
        }
    }

    CompositionLocalProvider(LocalChatHazeState provides hazeState) {
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
            Box(Modifier.fillMaxSize()) {
                // ── 背景层：角色卡图 + hazeSource + scrim ──
                Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
                    Image(
                        painter = painterResource(R.drawable.vanio_card),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (darkMode) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)))
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF141413).copy(alpha = 0.55f), Color.Transparent),
                                ),
                            ),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color(0xFF141413).copy(alpha = 0.30f)),
                                ),
                            ),
                    )
                }

                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        ImmersiveTopBar(
                            darkMode = darkMode,
                            onToggleDarkMode = onToggleDarkMode,
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                        )
                    },
                    bottomBar = {
                        InputIsland(
                            isGenerating = genState == GenState.Thinking || genState == GenState.Streaming,
                            onSendOrStop = {
                                when (genState) {
                                    GenState.Idle, GenState.Done -> {
                                        messages.add(FakeMessage.User("……那你倒是说说看？"))
                                        thinkProgress = 0
                                        streamProgress = 0
                                        genState = GenState.Thinking
                                    }
                                    else -> genState = GenState.Idle
                                }
                            },
                            onModelChipClick = {},
                        )
                    },
                ) { innerPadding ->
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(messages.size) { i ->
                            when (val m = messages[i]) {
                                is FakeMessage.Ai -> AiMessage(m, modifier = Modifier.fillMaxWidth())
                                is FakeMessage.User -> UserBubble(m.text)
                                FakeMessage.Thinking -> Unit
                            }
                        }
                        if (genState == GenState.Thinking || genState == GenState.Streaming) {
                            item {
                                ThinkingCard(
                                    steps = thinkSteps.take(
                                        if (genState == GenState.Thinking) thinkProgress else thinkSteps.size,
                                    ).ifEmpty { listOf(ThinkStep("…")) },
                                    isLive = genState == GenState.Thinking,
                                    elapsedLabel = "2.1s",
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        if (genState == GenState.Streaming) {
                            item {
                                val shown = pendingReply.paragraphs
                                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        Modifier.padding(12.dp).fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        NameBadgeRow("Vanio", null)
                                        shown.forEach { p ->
                                            when (p) {
                                                is FakeParagraph.Narration -> Text(
                                                    text = p.text,
                                                    fontSize = 13.5.sp, lineHeight = 23.sp,
                                                    fontFamily = LuzzyFonts.Body,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                )
                                                is FakeParagraph.Action -> Text(
                                                    text = "*${p.text}*",
                                                    fontSize = 13.5.sp, lineHeight = 23.sp,
                                                    fontFamily = LuzzyFonts.Body,
                                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                is FakeParagraph.Speech -> Text(
                                                    text = "「${p.text}」",
                                                    fontSize = 13.5.sp, lineHeight = 23.sp,
                                                    fontFamily = LuzzyFonts.Body,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                )
                                            }
                                        }
                                        if (genState == GenState.Streaming && streamProgress > 0) {
                                            TypingDots()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}