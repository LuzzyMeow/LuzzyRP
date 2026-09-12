package com.luzzymeow.luzzyrp.ui.pages.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luzzymeow.luzzyrp.BuildConfig
import com.luzzymeow.luzzyrp.R
import com.luzzymeow.luzzyrp.chat.BranchStat
import com.luzzymeow.luzzyrp.chat.BranchTree
import com.luzzymeow.luzzyrp.chat.ChatBranch
import com.luzzymeow.luzzyrp.chat.ChatEngine
import com.luzzymeow.luzzyrp.chat.TransportConfig
import com.luzzymeow.luzzyrp.chat.TransportStore
import com.luzzymeow.luzzyrp.chat.VanioCard
import com.luzzymeow.luzzyrp.chat.llm.LlmMessage
import com.luzzymeow.luzzyrp.chat.llm.LlmRole
import com.luzzymeow.luzzyrp.ui.DevHooks
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 演示角色的历史（真实数据源：会话上下文与记忆检索都读它）。
 *
 * 只含真实存在的过往轮次**文本**——不含任何思考节点：那些轮次没有真实产生过节点数据，
 * 编造节点等于造假。节点只由真实事件（检索/工具/推理增量）产生。
 */
private fun demoHistory(): List<ChatMessage> = listOf(
    ChatMessage.User("Vanio？听说你在教堂后面藏了什么……"),
    ChatMessage.Ai(
        raw = "少年被吓得差点把苹果抛出去。他僵着脖子回头，帽檐下的橘色眼睛瞪得溜圆，红斗篷下的翅膀不安分地扑棱了两下。\n" +
            "「嘘——！小声点！要是被嬷嬷听见，我攒了一个秋天的宝贝就全完啦。」\n" +
            "*左右看了看，把那颗红得发亮的苹果塞进兜里，冲你勾了勾手指*",
    ),
    ChatMessage.User("行行行，我不喊。所以……到底是什么？"),
    ChatMessage.Ai(
        raw = "「嘿嘿，想知道？」\n" +
            "*凑近你的耳边，用气声说道*\n" +
            "「是长在钟楼顶上的、一整树的红苹果。全城只有我知道那棵树在哪——因为呀，」他晃了晃帽子上小小的角，得意地眯起眼，「恶魔的果子，只有恶魔找得到。」",
    ),
)

/** P2 聊天页 · 沉浸形态 + **真实流式**（DESIGN-compose §12/§14/§15）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPage(
    darkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    onOpenDrawer: () -> Unit,
) {
    val hazeState = remember { HazeState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val store = remember { TransportStore(context) }
    var config by remember { mutableStateOf(store.load()) }
    var showConfig by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }

    val engine = remember { ChatEngine() }
    var live by remember { mutableStateOf<LiveTurn?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    // ── 剧情分支（上游语义：会话 = 角色 × 分支；P4 换真实存储） ──
    // 每条分支持有自己的消息列表；在某一分支发送只进该分支。演示数据 = 主线（种子历史）
    // + 一条**真实从主线分叉**的子分支（消息是主线前两楼的真实副本，不含编造内容）。
    var tree by remember { mutableStateOf(BranchTree.single()) }
    val branchMessages = remember { mutableStateMapOf<String, List<ChatMessage>>() }
    var showBranches by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (branchMessages.isEmpty()) {
            val main = demoHistory()
            branchMessages[ChatBranch.MainId] = main
            tree = tree.addChild(
                parentId = ChatBranch.MainId,
                id = "branch-1",
                name = "教堂后墙",
                forkFloor = 2,
                createdAt = 1L,
            )
            branchMessages["branch-1"] = main.take(2)
        }
    }
    val activeBranchId = tree.activeId
    val activeMessages = branchMessages[activeBranchId].orEmpty()
    val branchStats = tree.branches.associate { branch ->
        branch.id to BranchStat.of(branchMessages[branch.id].orEmpty().map { it.text() })
    }

    fun appendTo(branchId: String, message: ChatMessage) {
        branchMessages[branchId] = branchMessages[branchId].orEmpty() + message
    }

    fun appendMessage(message: ChatMessage) = appendTo(activeBranchId, message)

    /**
     * 贴底：把末项底部对齐视口底部。
     *
     * 传 `scrollOffset = 末项高度` 是常用配方——末项比视口高时（流式气泡长起来之后）
     * 也会停在**最新内容**那一端，而不是停在气泡顶部看旧文字。
     */
    suspend fun pinToBottom() {
        val total = listState.layoutInfo.totalItemsCount
        if (total == 0) return
        val lastIndex = total - 1
        val lastSize = listState.layoutInfo.visibleItemsInfo
            .lastOrNull { it.index == lastIndex }?.size ?: 0
        listState.scrollToItem(lastIndex, lastSize)
    }

    // 开页即贴底（聊天页默认停在最新一轮）
    LaunchedEffect(Unit) { pinToBottom() }

    // 切换分支后列表内容整体更换：回到最新一轮
    LaunchedEffect(activeBranchId) { pinToBottom() }

    fun send() {
        val userText = input.trim()
        if (userText.isEmpty() || live != null) return
        if (!config.configured) {
            showConfig = true
            return
        }
        input = ""
        val history = activeMessages.mapNotNull { m ->
            when (m) {
                is ChatMessage.User -> LlmMessage(role = LlmRole.USER, content = m.text)
                is ChatMessage.Ai -> LlmMessage(role = LlmRole.ASSISTANT, content = m.raw)
                is ChatMessage.Error -> null
            }
        }
        // 记住本轮所属分支：生成期间用户切到别的分支时，结果仍落在**发起的那条分支**上
        val turnBranchId = activeBranchId
        appendTo(turnBranchId, ChatMessage.User(userText))

        val turn = LiveTurn()
        live = turn
        job = scope.launch {
            pinToBottom()   // 发送后立刻让用户看见自己的消息与 live 气泡
            try {
                engine.run(config = config, history = history, userText = userText).collect { event ->
                    // 「是否贴底」要在内容变化**之前**判定：变化之后 canScrollForward 会变 true，
                    // 那时再判会把「本来贴着底」误判成「用户上滑了」而停止跟随。
                    val follow = !listState.canScrollForward
                    traceStreamEvent(event)
                    turn.apply(event)
                    if (follow) pinToBottom()
                }
            } catch (_: CancellationException) {
                // 用户点「停止」：保留已真实到达的正文与节点（不丢弃）
            }
            val error = turn.error
            if (turn.body.isNotBlank() || turn.nodes.isNotEmpty()) {
                appendTo(
                    turnBranchId,
                    ChatMessage.Ai(
                        raw = turn.body,
                        thinkNodes = turn.nodes,
                        finishReason = turn.finishReason,
                    ),
                )
            }
            if (error != null) appendTo(turnBranchId, ChatMessage.Error(error))
            if (live === turn) live = null
            job = null
            pinToBottom()
        }
    }

    // 开发注入挂点（release 下无注册者、恒为 null）：见 DevHooks 说明
    DisposableEffect(Unit) {
        DevHooks.inputInjector = { input = it }
        DevHooks.sendTrigger = { send() }
        onDispose {
            DevHooks.inputInjector = null
            DevHooks.sendTrigger = null
        }
    }

    CompositionLocalProvider(LocalChatHazeState provides hazeState) {
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
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
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
                                        text = VanioCard.Name,
                                        fontFamily = LuzzyFonts.Lora,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                    )
                                    Text(
                                        text = "${VanioCard.Subtitle} · 在线",
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
                            // 剧情分支入口（与上游 openStoryBranchModal 同一位置：聊天页顶栏）
                            IconButton(onClick = { showBranches = true }) {
                                Icon(
                                    painter = painterResource(LuzzyIcons.Branch),
                                    contentDescription = "剧情分支",
                                    tint = Color.White.copy(alpha = 0.92f),
                                )
                            }
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
                },
                bottomBar = {
                    InputIsland(
                        text = input,
                        onTextChange = { input = it },
                        isGenerating = live != null,
                        modelLabel = config.model.ifBlank { "未配置" },
                        configured = config.configured,
                        onSendOrStop = {
                            if (live != null) {
                                job?.cancel()
                                live = null
                                job = null
                            } else {
                                send()
                            }
                        },
                        onModelChipClick = { showConfig = true },
                    )
                },
            ) { innerPadding ->
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(activeMessages.size) { i ->
                        when (val m = activeMessages[i]) {
                            is ChatMessage.Ai -> Column(Modifier.fillMaxWidth()) {
                                AiMessagePanel(
                                    name = m.name,
                                    raw = m.raw,
                                    nodes = m.thinkNodes,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                MessageActionRow(
                                    branchIndex = m.branchIndex,
                                    branchCount = m.branchCount,
                                )
                            }

                            is ChatMessage.User -> Column(Modifier.fillMaxWidth()) {
                                UserBubble(m.text)
                                MessageActionRow(alignEnd = true)
                            }

                            is ChatMessage.Error -> ErrorPanel(m.text)
                        }
                    }
                    // 生成中：思考节点与正文都在**同一个气泡内**实时生长（§14.3①）
                    live?.let { turn ->
                        item {
                            Column(Modifier.fillMaxWidth()) {
                                AiMessagePanel(
                                    name = VanioCard.Name,
                                    raw = turn.body,
                                    nodes = turn.nodes,
                                    isLive = turn.generating,
                                    activeNode = turn.activeNode,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                if (!turn.generating) MessageActionRow()
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBranches) {
        BranchListSheet(
            tree = tree,
            stats = branchStats,
            onSwitch = {
                tree = tree.switchTo(it)
                // 进入即收起（上游 StoryBranchModal「进入」同语义：选完就看内容，不再挡着）
                showBranches = false
            },
            onRename = { id, name -> tree = tree.rename(id, name) },
            onDelete = { tree = tree.delete(it) },
            onDismiss = { showBranches = false },
        )
    }

    if (showConfig) {
        TransportConfigDialog(
            initial = config,
            onDismiss = { showConfig = false },
            onSave = {
                config = it
                store.save(it)
                showConfig = false
            },
        )
    }
}

/** 真实错误的如实展示（不伪装成模型输出）。 */
@Composable
private fun ErrorPanel(text: String) {    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.error.copy(alpha = 0.45f),
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            fontSize = 12.5.sp,
            lineHeight = 19.sp,
            fontFamily = LuzzyFonts.Body,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

/**
 * 供应商配置（P2：设备本地保存；P4 迁 DataStore + 多供应商管理）。
 *
 * 密钥只写本地 SharedPreferences，界面以密文输入 + 打码回显；**不入库、不进构建产物**。
 */
@Composable
private fun TransportConfigDialog(
    initial: TransportConfig,
    onDismiss: () -> Unit,
    onSave: (TransportConfig) -> Unit,
) {
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var model by remember { mutableStateOf(initial.model) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "供应商配置",
                fontFamily = LuzzyFonts.Lora,
                fontSize = 18.sp,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "OpenAI 兼容协议。配置仅保存在本机（不入库、不进安装包）。",
                    fontSize = 11.5.sp,
                    fontFamily = LuzzyFonts.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL", fontFamily = LuzzyFonts.Body) },
                    placeholder = { Text("https://api.deepseek.com", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key", fontFamily = LuzzyFonts.Body) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("模型", fontFamily = LuzzyFonts.Body) },
                    placeholder = { Text("deepseek-flash", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (initial.apiKey.isNotBlank()) {
                    Text(
                        text = "当前密钥：${initial.maskedKey()}",
                        fontSize = 11.sp,
                        fontFamily = LuzzyFonts.Body,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    TransportConfig(
                        baseUrl = baseUrl.trim(),
                        apiKey = apiKey.trim(),
                        model = model.trim(),
                    ),
                )
            }) {
                Text("保存", fontFamily = LuzzyFonts.Body)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", fontFamily = LuzzyFonts.Body)
            }
        },
    )
}

/**
 * 流式轨迹（**仅 debug 构建**）：每个真实增量打一行 logcat，用于验证
 * 「1 个 SSE 增量 = 1 次状态更新」而无需在引擎里引入 Android 依赖。
 *
 * 取证据：`adb logcat -s LuzzyStream` 后统计行数与相邻时间戳即可还原真实帧到达节奏。
 */
private const val StreamTraceTag = "LuzzyStream"

private fun traceStreamEvent(event: ChatEngine.Event) {
    if (!BuildConfig.DEBUG) return
    val line = when (event) {
        is ChatEngine.Event.Recall -> "recall hits=${event.hits.size} range=${event.range}"
        is ChatEngine.Event.ToolCallStarted -> "tool_start ${event.name}"
        is ChatEngine.Event.ToolCallArgs -> "tool_args +${event.chunk.length}"
        is ChatEngine.Event.ToolCallFinished -> "tool_result ${event.name} ${event.result.length}B"
        is ChatEngine.Event.Reasoning -> "reasoning +${event.chunk.length}"
        is ChatEngine.Event.Content -> "content +${event.chunk.length}"
        is ChatEngine.Event.Finished -> "finished ${event.finishReason}"
        is ChatEngine.Event.Failed -> "failed ${event.message}"
    }
    android.util.Log.d(StreamTraceTag, line)
}
