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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
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
        results = listOf(
            AiResult(
                raw = "少年被吓得差点把苹果抛出去。他僵着脖子回头，帽檐下的橘色眼睛瞪得溜圆，红斗篷下的翅膀不安分地扑棱了两下。\n" +
                    "「嘘——！小声点！要是被嬷嬷听见，我攒了一个秋天的宝贝就全完啦。」\n" +
                    "*左右看了看，把那颗红得发亮的苹果塞进兜里，冲你勾了勾手指*",
            ),
        ),
    ),
    ChatMessage.User("行行行，我不喊。所以……到底是什么？"),
    ChatMessage.Ai(
        results = listOf(
            AiResult(
                raw = "「嘿嘿，想知道？」\n" +
                    "*凑近你的耳边，用气声说道*\n" +
                    "「是长在钟楼顶上的、一整树的红苹果。全城只有我知道那棵树在哪——因为呀，」他晃了晃帽子上小小的角，得意地眯起眼，「恶魔的果子，只有恶魔找得到。」",
            ),
        ),
    ),
)

/** 编辑目标（操作行「编辑」开弹窗时携带）。 */
private data class EditingTarget(
    val branchId: String,
    val index: Int,
    val isAi: Boolean,
    val initial: String,
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

    // 功能面板（真实现）：模型切换 / 工具开关 / 世界书只读
    var showModels by remember { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(false) }
    var showWorldBook by remember { mutableStateOf(false) }

    // 操作行接线的宿主状态
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    var editing by remember { mutableStateOf<EditingTarget?>(null) }
    var regeneratingIndexState by remember { mutableStateOf<Int?>(null) }

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

    fun replaceMessage(branchId: String, index: Int, message: ChatMessage) {
        val list = branchMessages[branchId].orEmpty().toMutableList()
        if (index !in list.indices) return
        list[index] = message
        branchMessages[branchId] = list
    }

    fun editMessage(branchId: String, index: Int, transform: (ChatMessage) -> ChatMessage) {
        val list = branchMessages[branchId].orEmpty().toMutableList()
        if (index !in list.indices) return
        list[index] = transform(list[index])
        branchMessages[branchId] = list
    }

    fun removeMessage(branchId: String, index: Int, andAfter: Boolean) {
        val list = branchMessages[branchId].orEmpty()
        if (index !in list.indices) return
        branchMessages[branchId] = if (andAfter) list.take(index) else list.filterIndexed { i, _ -> i != index }
    }

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

    /**
     * 跑一轮真实生成（发送与重新生成共用）。
     *
     * [onFinish] 拿到收尾后的 [LiveTurn] 自行决定落库方式：新消息追加、或作为候选并入既有消息。
     * [regeneratingIndex] 非空时，live 面板**就地**渲染在那条消息的位置（而不是列表末尾），
     * 让「重新生成」看起来是在原处重写，而不是凭空冒出新气泡。
     */
    fun runTurn(
        history: List<LlmMessage>,
        userText: String,
        regeneratingIndex: Int?,
        onFinish: (LiveTurn) -> Unit,
    ) {
        val turn = LiveTurn()
        live = turn
        regeneratingIndexState = regeneratingIndex
        job = scope.launch {
            pinToBottom()
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
            onFinish(turn)
            if (live === turn) {
                live = null
                regeneratingIndexState = null
            }
            job = null
            pinToBottom()
        }
    }

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

        runTurn(history = history, userText = userText, regeneratingIndex = null) { turn ->
            val error = turn.error
            if (turn.body.isNotBlank() || turn.nodes.isNotEmpty()) {
                appendTo(
                    turnBranchId,
                    ChatMessage.Ai(
                        results = listOf(AiResult(turn.body, turn.nodes, turn.finishReason)),
                    ),
                )
            }
            if (error != null) appendTo(turnBranchId, ChatMessage.Error(error))
        }
    }

    /** 重新生成：[messageIndex] 处必须是 AI 消息——**真实再跑一次请求**并把结果作为新候选追加。 */
    fun regenerate(messageIndex: Int) {
        if (live != null) return
        if (!config.configured) {
            showConfig = true
            return
        }
        val target = activeMessages.getOrNull(messageIndex) as? ChatMessage.Ai ?: return
        val prefix = activeMessages.take(messageIndex)
        val userText = prefix.filterIsInstance<ChatMessage.User>().lastOrNull()?.text
        if (userText.isNullOrBlank()) {
            scope.launch { snackbarHostState.showSnackbar("这条之前没有用户消息，无法重新生成") }
            return
        }
        val history = prefix.mapNotNull { m ->
            when (m) {
                is ChatMessage.User -> LlmMessage(role = LlmRole.USER, content = m.text)
                is ChatMessage.Ai -> LlmMessage(role = LlmRole.ASSISTANT, content = m.raw)
                is ChatMessage.Error -> null
            }
        }
        val turnBranchId = activeBranchId
        runTurn(history = history, userText = userText, regeneratingIndex = messageIndex) { turn ->
            val error = turn.error
            if (turn.body.isNotBlank() || turn.nodes.isNotEmpty()) {
                editMessage(turnBranchId, messageIndex) { current ->
                    if (current is ChatMessage.Ai) {
                        current.withResult(AiResult(turn.body, turn.nodes, turn.finishReason))
                    } else {
                        current
                    }
                }
                scope.launch { snackbarHostState.showSnackbar("已生成第 ${target.resultCount + 1} 个结果") }
            }
            if (error != null) appendTo(turnBranchId, ChatMessage.Error(error))
        }
    }

    fun copyMessage(text: String) {
        clipboard.setText(AnnotatedString(text))
        scope.launch { snackbarHostState.showSnackbar("已复制到剪贴板") }
    }

    /** 依赖后续期的入口：给如实说明，而不是静默无反应。 */
    fun pendingFeatureHint(feature: String, reason: String) {
        scope.launch { snackbarHostState.showSnackbar("$feature：需要$reason，届时开放") }
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
                // 操作反馈（复制/已生成第 N 个结果/未开放功能提示）——pro-rules：每个可点元素都要有反馈
                snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        toolsEnabled = config.toolsEnabled,
                        onSendOrStop = {
                            if (live != null) {
                                job?.cancel()
                                live = null
                                regeneratingIndexState = null
                                job = null
                            } else {
                                send()
                            }
                        },
                        // 模型：已配置时开「真实模型列表」面板，未配置时去填配置
                        onModelChipClick = { if (config.configured) showModels = true else showConfig = true },
                        onWorldBook = { showWorldBook = true },
                        onTools = { showTools = true },
                        // 依赖后续期的入口**保留**，点击给出如实说明（不装死、也不删组件）
                        onAttach = { pendingFeatureHint("附件", "P5 的图片管线（选图 + 图片消息）") },
                        onPresets = { pendingFeatureHint("预设", "P4 的数据层（预设是用户数据）") },
                        onWorkspace = { pendingFeatureHint("工作区", "P5 的工作区特性") },
                    )
                },
            ) { innerPadding ->
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 稳定 key：分支 id + 下标。切换分支时整列 key 变化 → 强制重建条目，
                    // 避免上一条分支的条目状态（如代码块展开态）泄漏到新分支（pro-rules 亦要求列表带 key）
                    items(activeMessages.size, key = { i -> "$activeBranchId#$i" }) { i ->
                        // 重新生成时就地渲染 live 面板：看起来是「原处重写」，而非凭空冒出新气泡
                        val inPlaceLive = live?.takeIf { regeneratingIndexState == i }
                        if (inPlaceLive != null) {
                            Column(Modifier.fillMaxWidth()) {
                                AiMessagePanel(
                                    name = VanioCard.Name,
                                    raw = inPlaceLive.body,
                                    nodes = inPlaceLive.nodes,
                                    isLive = inPlaceLive.generating,
                                    activeNode = inPlaceLive.activeNode,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            return@items
                        }
                        when (val m = activeMessages[i]) {
                            is ChatMessage.Ai -> Column(Modifier.fillMaxWidth()) {
                                AiMessagePanel(
                                    name = m.name,
                                    raw = m.raw,
                                    nodes = m.thinkNodes,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                MessageActionRow(
                                    branchIndex = m.index,
                                    branchCount = m.resultCount,
                                    onBranchChange = { target ->
                                        editMessage(activeBranchId, i) { cur ->
                                            if (cur is ChatMessage.Ai) cur.selectResult(target) else cur
                                        }
                                    },
                                    onCopy = { copyMessage(m.raw) },
                                    onRegenerate = { regenerate(i) },
                                    onEdit = {
                                        editing = EditingTarget(activeBranchId, i, isAi = true, initial = m.raw)
                                    },
                                    onDelete = { removeMessage(activeBranchId, i, andAfter = false) },
                                    onDeleteAfter = { removeMessage(activeBranchId, i, andAfter = true) },
                                )
                            }

                            is ChatMessage.User -> Column(Modifier.fillMaxWidth()) {
                                UserBubble(m.text)
                                MessageActionRow(
                                    alignEnd = true,
                                    onCopy = { copyMessage(m.text) },
                                    // 用户消息没有「重新生成」（重生成是模型输出的动作）
                                    onRegenerate = null,
                                    onEdit = {
                                        editing = EditingTarget(activeBranchId, i, isAi = false, initial = m.text)
                                    },
                                    onDelete = { removeMessage(activeBranchId, i, andAfter = false) },
                                    onDeleteAfter = { removeMessage(activeBranchId, i, andAfter = true) },
                                )
                            }

                            is ChatMessage.Error -> ErrorPanel(m.text)
                        }
                    }
                    // 新消息的 live 面板（重新生成时已被就地渲染，避免出现两个 live）
                    live?.takeIf { regeneratingIndexState == null }?.let { turn ->
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
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { target ->
        EditMessageDialog(
            initial = target.initial,
            title = if (target.isAi) "编辑这条回复" else "编辑你的消息",
            onDismiss = { editing = null },
            onSave = { text ->
                editMessage(target.branchId, target.index) { cur ->
                    when {
                        target.isAi && cur is ChatMessage.Ai -> cur.editCurrent(text)
                        !target.isAi && cur is ChatMessage.User -> cur.edited(text)
                        else -> cur
                    }
                }
                editing = null
                if (!target.isAi) {
                    scope.launch { snackbarHostState.showSnackbar("已修改；可对该回复点「重新生成」按新内容重跑") }
                }
            },
        )
    }

    if (showModels) {
        ModelPickerSheet(
            config = config,
            onSelect = { id ->
                config = config.copy(model = id)
                store.save(config)
                showModels = false
                scope.launch { snackbarHostState.showSnackbar("已切换到 $id（下一条请求即生效）") }
            },
            onDismiss = { showModels = false },
        )
    }

    if (showTools) {
        ToolsSheet(
            config = config,
            onToggle = { enabled ->
                config = config.copy(toolsEnabled = enabled)
                store.save(config)
            },
            onDismiss = { showTools = false },
        )
    }

    if (showWorldBook) {
        WorldBookSheet(onDismiss = { showWorldBook = false })
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
