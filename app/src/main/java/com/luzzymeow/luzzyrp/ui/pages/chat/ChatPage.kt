package com.luzzymeow.luzzyrp.ui.pages.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.luzzymeow.luzzyrp.data.chat.ChatSessionRepository
import com.luzzymeow.luzzyrp.data.store.DatabaseProvider
import com.luzzymeow.luzzyrp.data.store.LuzzyStore
import com.luzzymeow.luzzyrp.chat.llm.LlmMessage
import com.luzzymeow.luzzyrp.chat.llm.LlmRole
import com.luzzymeow.luzzyrp.ui.DevHooks
import com.luzzymeow.luzzyrp.ui.icons.LuzzyIcons
import com.luzzymeow.luzzyrp.ui.theme.LuzzyFonts
import com.luzzymeow.luzzyrp.ui.theme.Motion
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow

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

/** 待确认的删除（T6）：写清会删几条、不可恢复，而不是点了就删。 */
private data class PendingDelete(
    val branchId: String,
    val index: Int,
    val andAfter: Boolean,
    val count: Int,
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
    /**
     * 引擎工厂（默认真实传输）。**测试接缝**：仪器化 UI 测试注入假传输，
     * 就能确定性地驱动「流式上屏 / 工具节点 / 失败态」，不必依赖网络与真实供应商。
     */
    engineFactory: () -> ChatEngine = { ChatEngine() },
    /**
     * 会话仓库（默认真实 Room 存储）。**测试接缝**：仪器化测试注入一个指向临时库的仓库，
     * 就能确定性地验证「启动即读 / 改动落盘 / 重启仍在」，不必依赖设备上的真实数据。
     */
    sessionRepository: ChatSessionRepository? = null,
) {
    val hazeState = remember { HazeState() }
    // 稳定测试选择器（ui-ux-pro-max 的 Compose 栈规约要求 testTag 而非依赖文案）
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // 「算不算贴底」的容差：8dp（与 rikkahub 同量级）——避免像素级抖动导致跟随忽开忽关
    val bottomSlackPx = with(LocalDensity.current) { 8.dp.roundToPx() }

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

    val engine = remember { engineFactory() }
    var live by remember { mutableStateOf<LiveTurn?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    // ── 剧情分支（上游语义：会话 = 角色 × 分支）──
    //
    // [P4-B-3.4] 会话数据现在来自**真实存储**：
    //   启动 → 载入当前角色的分支与消息；改动 → 按行落盘（追加/改写/删除各只碰自己那一行）。
    // 存储为空时（首次安装、尚未迁移）回落到内置演示角色，此时 **characterUuid 为 null =
    // 不落盘**：无宿主角色的消息没有地方可存，与其写半套数据不如明说「这是演示」。
    val repository = remember(sessionRepository) {
        sessionRepository ?: ChatSessionRepository(
            LuzzyStore(DatabaseProvider.luzzy(context.applicationContext)),
        )
    }
    var characterUuid by remember { mutableStateOf<String?>(null) }
    var tree by remember { mutableStateOf(BranchTree.single()) }
    val branchMessages = remember { mutableStateMapOf<String, List<ChatMessage>>() }
    var showBranches by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (branchMessages.isEmpty()) {
            val session = repository.load()
            if (session != null) {
                characterUuid = session.character.uuid
                tree = BranchTree(branches = session.branches, activeId = session.activeBranchId)
                branchMessages.clear()
                session.messagesByBranch.forEach { (branchId, messages) ->
                    branchMessages[branchId] = messages
                }
            } else {
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
    }

    /**
     * 落盘助手：只在**有宿主角色**时写（演示态不落盘）。
     *
     * 每个写操作都是独立协程：界面不等 IO（消息立刻上屏），落盘失败只影响持久化，
     * 不回滚已经呈现的内容 —— 这正是「先让人看到，再保证存住」的顺序。
     */
    fun persist(block: suspend (ChatSessionRepository, String) -> Unit) {
        val uuid = characterUuid ?: return
        scope.launch {
            runCatching { block(repository, uuid) }
                // 界面不等 IO，但**错误不能吞**：吞掉的话「没存住」会表现为「重启后少一条」，
                // 那时再查就晚了。日志是这条路径唯一的现场。
                .onFailure { android.util.Log.w("LuzzyChat", "会话落盘失败", it) }
        }
    }
    val activeBranchId = tree.activeId
    val activeMessages = branchMessages[activeBranchId].orEmpty()
    val branchStats = tree.branches.associate { branch ->
        branch.id to BranchStat.of(branchMessages[branch.id].orEmpty().map { it.text() })
    }

    fun appendTo(branchId: String, message: ChatMessage, reasoning: String? = null) {
        branchMessages[branchId] = branchMessages[branchId].orEmpty() + message
        persist { repo, uuid -> repo.append(uuid, branchId, message, reasoning) }
    }

    fun appendMessage(message: ChatMessage) = appendTo(activeBranchId, message)

    fun replaceMessage(branchId: String, index: Int, message: ChatMessage) {
        val list = branchMessages[branchId].orEmpty().toMutableList()
        if (index !in list.indices) return
        list[index] = message
        branchMessages[branchId] = list
        persist { repo, uuid -> repo.updateContent(uuid, branchId, index, message.text()) }
    }

    fun editMessage(branchId: String, index: Int, transform: (ChatMessage) -> ChatMessage) {
        val list = branchMessages[branchId].orEmpty().toMutableList()
        if (index !in list.indices) return
        list[index] = transform(list[index])
        branchMessages[branchId] = list
        persist { repo, uuid -> repo.updateContent(uuid, branchId, index, list[index].text()) }
    }

    fun removeMessage(branchId: String, index: Int, andAfter: Boolean) {
        val list = branchMessages[branchId].orEmpty()
        if (index !in list.indices) return
        branchMessages[branchId] = if (andAfter) list.take(index) else list.filterIndexed { i, _ -> i != index }
        persist { repo, uuid -> repo.delete(uuid, branchId, index, andAfter) }
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

    /**
     * 是否贴着底部（滚动跟随的判据）。
     *
     * 与 rikkahub `ChatList.kt:236-243` 同语义：**末项可见且其底边到达视口底**才算在底部。
     * 比 `!canScrollForward` 精确——后者在「内容刚好占满、无余量可滚」时也算底部，
     * 而用户其实停在中间（例如气泡很高时）。
     *
     * 列表已被 Scaffold 的 `innerPadding` 内缩，故无需再扣输入岛高度。
     */
    fun isAtBottom(): Boolean {
        val info = listState.layoutInfo
        if (info.totalItemsCount == 0) return true
        val lastVisible = info.visibleItemsInfo.lastOrNull() ?: return true
        if (lastVisible.index < info.totalItemsCount - 1) return false
        return lastVisible.offset + lastVisible.size <= info.viewportEndOffset + bottomSlackPx
    }

    // 「用户上滑离开了底部」期间的到达内容 → 回底按钮上点一个小圆点（对齐 rikkahub 的新内容提示语义）
    var unseenWhileAway by remember { mutableStateOf(false) }

    /**
     * 悬浮错误卡（**不进消息列表**）：生成失败不再作为一条「消息」插入对话——
     * 那会污染上下文回填、被算进分支楼数，也把真实失败伪装成了发言。
     */
    val chatErrors = remember { mutableStateListOf<ChatError>() }

    // 破坏性操作先确认（T6）：删除按条数说明后果；编辑用户消息后询问是否按新内容重跑
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }
    var pendingRerunIndex by remember { mutableStateOf<Int?>(null) }

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
                    // 「是否贴底」要在内容变化**之前**判定：变化之后末项会变高、判据立刻变 false，
                    // 那时再判会把「本来贴着底」误判成「用户上滑了」而停止跟随。
                    // 另加 `!isScrollInProgress`：用户正在拖动/惯性滑动时不抢滚动（rikkahub 同条件）。
                    val follow = isAtBottom() && !listState.isScrollInProgress
                    traceStreamEvent(event)
                    turn.apply(event)
                    if (follow) pinToBottom() else unseenWhileAway = true
                }
            } catch (_: CancellationException) {
                // 用户点「停止」：保留已真实到达的正文与节点（不丢弃）
            }
            onFinish(turn)
            // 截断可见化（T5）：结束原因若为输出上限，用一句可操作的话告诉用户——
            // 此前 finishReason 只是被存下来、应用内完全看不见，「回复为什么断了」无法定性。
            if (com.luzzymeow.luzzyrp.chat.UsageFormat.isTruncated(turn.finishReason)) {
                scope.launch {
                    snackbarHostState.showSnackbar("回复因达到输出上限被截断；可在供应商配置里提高最大输出")
                }
            }
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
                        results = listOf(
                            AiResult(turn.body, turn.nodes, turn.finishReason, turn.usage, turn.elapsedMs),
                        ),
                    ),
                )
            }
            if (error != null) chatErrors.add(ChatError(System.currentTimeMillis(), error))
        }
    }

    /**
     * 在 [userIndex] 这条用户消息之后**追加**一条新回复（编辑用户消息后重跑走这里）。
     *
     * 与 [regenerate] 的区别：那里是「同一位置换个候选」，这里是「这条之后重新说一遍」——
     * 调用前应先截断其后楼层（UI 的确认框已写明）。
     */
    fun regenerateFrom(userIndex: Int) {
        if (live != null) return
        if (!config.configured) {
            showConfig = true
            return
        }
        val prefix = activeMessages.take(userIndex + 1)
        val userText = prefix.lastOrNull() as? ChatMessage.User ?: return
        val history = prefix.dropLast(1).mapNotNull { m ->
            when (m) {
                is ChatMessage.User -> LlmMessage(role = LlmRole.USER, content = m.text)
                is ChatMessage.Ai -> LlmMessage(role = LlmRole.ASSISTANT, content = m.raw)
                else -> null
            }
        }
        val turnBranchId = activeBranchId
        runTurn(history = history, userText = userText.text, regeneratingIndex = null) { turn ->
            val error = turn.error
            if (turn.body.isNotBlank() || turn.nodes.isNotEmpty()) {
                appendTo(
                    turnBranchId,
                    ChatMessage.Ai(
                        results = listOf(
                            AiResult(turn.body, turn.nodes, turn.finishReason, turn.usage, turn.elapsedMs),
                        ),
                    ),
                )
            }
            if (error != null) chatErrors.add(ChatError(System.currentTimeMillis(), error))
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
            }
        }
        val turnBranchId = activeBranchId
        runTurn(history = history, userText = userText, regeneratingIndex = messageIndex) { turn ->
            val error = turn.error
            if (turn.body.isNotBlank() || turn.nodes.isNotEmpty()) {
                editMessage(turnBranchId, messageIndex) { current ->
                    if (current is ChatMessage.Ai) {
                        current.withResult(
                            AiResult(turn.body, turn.nodes, turn.finishReason, turn.usage, turn.elapsedMs),
                        )
                    } else {
                        current
                    }
                }
                scope.launch { snackbarHostState.showSnackbar("已生成第 ${target.resultCount + 1} 个结果") }
            }
            if (error != null) chatErrors.add(ChatError(System.currentTimeMillis(), error))
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
                            IconButton(
                                onClick = { showBranches = true },
                                modifier = Modifier.testTag("chat_branches"),
                            ) {
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
                // 内容盒必须自己吃掉 innerPadding：否则它的 BottomEnd 落在**屏幕**右下角，
                // 「回到底部」按钮会被 bottomBar 的输入岛盖住（实测：按钮存在但看不见）。
                Box(Modifier.fillMaxSize().padding(innerPadding)) {
                LazyColumn(
                    state = listState,
                    // 稳定选择器：岛上还有一条横向滚动的功能行，用 hasScrollAction() 定位列表会匹配到多个
                    modifier = Modifier.fillMaxSize().testTag("chat_list"),
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
                                MessageNerdLine(
                                    usage = m.current.usage,
                                    elapsedMs = m.current.elapsedMs,
                                    finishReason = m.finishReason,
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
                                    onDelete = {
                                        pendingDelete = PendingDelete(activeBranchId, i, false, count = 1)
                                    },
                                    onDeleteAfter = {
                                        pendingDelete = PendingDelete(
                                            activeBranchId, i, true,
                                            count = (activeMessages.size - i).coerceAtLeast(1),
                                        )
                                    },
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
                                    onDelete = {
                                        pendingDelete = PendingDelete(activeBranchId, i, false, count = 1)
                                    },
                                    onDeleteAfter = {
                                        pendingDelete = PendingDelete(
                                            activeBranchId, i, true,
                                            count = (activeMessages.size - i).coerceAtLeast(1),
                                        )
                                    },
                                )
                            }

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

                    // ── 底部覆盖层：错误卡栈 + 回到底部（同列排布，天然不重叠）──
                    val atBottom by remember { derivedStateOf { isAtBottom() } }
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                    ChatErrorCards(
                        errors = chatErrors,
                        onDismiss = { id -> chatErrors.removeAll { it.id == id } },
                        onDismissAll = { chatErrors.clear() },
                        onCopy = { copyMessage(it) },
                    )
                    // 回到底部（脱离底部时出现；rikkahub 的 MessageJumper 取其中最必要的一钮）
                    // 动效遵 DESIGN 纪律：进入 200ms / 退出 140ms，禁 scale(0)（起点 0.9）
                    AnimatedVisibility(
                        visible = !atBottom,
                        enter = fadeIn(tween(Motion.EnterMs)) +
                            scaleIn(initialScale = 0.9f, animationSpec = tween(Motion.EnterMs)),
                        exit = fadeOut(tween(Motion.ExitMs)) +
                            scaleOut(targetScale = 0.9f, animationSpec = tween(Motion.ExitMs)),
                        modifier = Modifier.padding(end = 6.dp),
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .testTag("chat_back_to_bottom")
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    CircleShape,
                                )
                                .clickable {
                                    unseenWhileAway = false
                                    scope.launch { pinToBottom() }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(LuzzyIcons.ChevronDown),
                                contentDescription = if (unseenWhileAway) "回到底部（有新内容）" else "回到底部",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                            // 离开底部期间有新内容 → 右上角一个主色小圆点
                            if (unseenWhileAway) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 6.dp, end = 6.dp)
                                        .size(7.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                                )
                            }
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
                    // 改用户消息通常就是要「按新内容重说一遍」→ 先问，再决定是否截断其后并重跑
                    pendingRerunIndex = target.index
                }
            },
        )
    }

    pendingDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除消息", fontFamily = LuzzyFonts.Body, fontSize = 17.sp) },
            text = {
                Text(
                    text = if (pending.count == 1) {
                        "将删除 1 条消息，不可恢复。"
                    } else {
                        "将删除 ${pending.count} 条消息（这条及其之后的全部楼层），不可恢复。"
                    },
                    fontFamily = LuzzyFonts.Body,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    removeMessage(pending.branchId, pending.index, pending.andAfter)
                    pendingDelete = null
                }) {
                    Text("删除", fontFamily = LuzzyFonts.Body, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消", fontFamily = LuzzyFonts.Body) }
            },
        )
    }

    pendingRerunIndex?.let { index ->
        AlertDialog(
            onDismissRequest = { pendingRerunIndex = null },
            title = { Text("按新内容重新生成？", fontFamily = LuzzyFonts.Body, fontSize = 17.sp) },
            text = {
                Text(
                    text = "你的消息已修改。若现在重新生成，这条消息之后的楼层会被删除。",
                    fontFamily = LuzzyFonts.Body,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRerunIndex = null
                    removeMessage(activeBranchId, index + 1, andAfter = true)
                    regenerateFrom(index)
                }) { Text("重新生成", fontFamily = LuzzyFonts.Body) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRerunIndex = null }) { Text("只改内容", fontFamily = LuzzyFonts.Body) }
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
                persist { repo, uuid -> repo.rememberActiveBranch(uuid, it) }
                // 进入即收起（上游 StoryBranchModal「进入」同语义：选完就看内容，不再挡着）
                showBranches = false
            },
            onRename = { id, name ->
                tree = tree.rename(id, name)
                persist { repo, uuid -> repo.renameBranch(uuid, id, name) }
            },
            onDelete = { id ->
                tree = tree.delete(id)
                // 上游语义：删分支即删该分支的会话数据
                branchMessages.remove(id)
                persist { repo, uuid -> repo.deleteBranch(uuid, id) }
            },
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
        is ChatEngine.Event.Usage ->
            "usage in=${event.info.input} out=${event.info.output} cached=${event.info.cached}"

        is ChatEngine.Event.Finished -> "finished ${event.finishReason}"
        is ChatEngine.Event.Failed -> "failed ${event.message}"
    }
    android.util.Log.d(StreamTraceTag, line)
}
