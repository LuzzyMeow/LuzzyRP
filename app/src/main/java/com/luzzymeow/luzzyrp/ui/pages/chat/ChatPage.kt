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
import androidx.compose.foundation.layout.imePadding
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
import android.util.Log
import com.luzzymeow.luzzyrp.chat.PromptAssembler
import com.luzzymeow.luzzyrp.chat.PromptInputSource
import com.luzzymeow.luzzyrp.chat.WorldBookTool
import com.luzzymeow.luzzyrp.data.legacy.ScopeId
import com.luzzymeow.luzzyrp.chat.TransportConfig
import com.luzzymeow.luzzyrp.chat.TransportStore
import com.luzzymeow.luzzyrp.chat.VanioCard
import com.luzzymeow.luzzyrp.data.chat.ChatSessionRepository
import com.luzzymeow.luzzyrp.data.preset.PresetRepository
import com.luzzymeow.luzzyrp.data.world.WorldBookRepository
import com.luzzymeow.luzzyrp.data.store.DatabaseProvider
import com.luzzymeow.luzzyrp.data.legacy.MigrationCoordinator
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow

/**
 * 缓存观测的 logcat tag（真机验收的探针通道）。
 *
 * 真机装的是 **release 包**且未 root → `run-as` 不可用、数据库读不到，
 * 所以「每轮请求的公共前缀占比 / 命中率 / 落盘顺序」只可能从日志里看见。
 * 用法：`adb -s <真机> logcat -s LuzzyCache`。
 */
private const val TAG_CACHE = "LuzzyCache"

/** 落盘条目的**抗混淆**标签（`::class.simpleName` 在 release 包会被 R8 改名，日志就不可读了）。 */
private fun appendLabel(message: ChatMessage): String = when (message) {
    is ChatMessage.Snapshot -> "Snapshot"
    is ChatMessage.User -> "User"
    is ChatMessage.Ai -> "Ai"
}

/**
 * 演示角色的历史（真实数据源：会话上下文与记忆检索都读它）。
 *
 * 只含真实存在的过往轮次**文本**——不含任何思考节点：那些轮次没有真实产生过节点数据，
 * 编造节点等于造假。节点只由真实事件（检索/工具/推理增量）产生。
 */
/** 从角色卡 payload 里取「卡作者」（顶栏状态行的数据来源；缺省返回空串）。 */
private fun characterCreator(character: com.luzzymeow.luzzyrp.data.store.CharacterEntity): String =
    runCatching {
        val obj = kotlinx.serialization.json.Json.parseToJsonElement(character.payload)
        ((obj as? kotlinx.serialization.json.JsonObject)
            ?.get("creator") as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
    }.getOrDefault("")

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
private data class PendingDelete(    val branchId: String,
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

/**
 * 一次发送/重跑的**准备工作**（A6）：请求 + 本次激活的世界书条目。
 *
 * 两件事必须在**落盘之前**一起算出——否则「先落快照、再落用户消息」这条顺序就没法保证。
 */
private class PreparedTurn(
    val plan: RequestBuilder.Plan,
    /**
     * 本次**激活**的世界书条目（工具执行器与生成前扫描共用同一份）。
     * `null` = 本轮取数失败（引擎回落到默认执行器），与「取到了但一条都没激活」（空列表）不同。
     */
    val worldEntries: List<com.luzzymeow.luzzyrp.data.world.WorldEntry>?,
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
    /**
     * 打开「会话总览」。
     *
     * 入口在**聊天页顶栏**（用户 2026-09-13 拍板；侧栏不加项，故总览自身 `inDrawer = false`）。
     */
    onOpenSessions: () -> Unit = {},
    /** 世界书面板的「管理」去向（跳到世界书页）。 */
    onOpenWorldInfo: () -> Unit = {},
    /** 预设面板的「管理」去向（跳到预设页）。 */
    onOpenPresets: () -> Unit = {},
    /**
     * 组装取数层（默认真库）。**测试接缝**：注入指向临时库的实例，
     * 否则「本轮请求里有什么」会取决于设备上恰好有什么数据。
     */
    promptInputSource: PromptInputSource? = null,
    /**
     * 两个用户数据仓库（默认真实 Room 存储）。**测试接缝**：仪器化测试必须注入指向临时库的
     * 仓库，否则面板会去读设备上真实的 `luzzy.db` ——「界面取决于这台机器恰好有什么数据」
     * 是隐藏耦合（本用例第一版就因为读不到注入夹具的数据而超时）。
     */
    worldBookRepository: WorldBookRepository? = null,
    presetRepository: PresetRepository? = null,
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

    // 功能面板（真实现）：模型切换 / 工具开关 / 世界书与预设（真数据、只读 + 管理入口）
    var showModels by remember { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(false) }
    var showWorldBook by remember { mutableStateOf(false) }
    var showPresets by remember { mutableStateOf(false) }

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

    /**
     * 「生效」的取数层（A1b）：角色 / 用户 / 预设 / 世界书全部来自真库。
     *
     * 与 [repository] 同一套接缝纪律——仪器化测试注入指向临时库的实例，
     * 否则「本轮请求里有什么」会取决于这台机器恰好装了什么数据（隐藏耦合）。
     *
     * **注意**：这里**不能**自己 new `LuzzyStore` 再拼一个 source——那会绕过注入，
     * 让测试读到设备上的真库（本用例第一版正是这么红的：预设面板等不到注入夹具的条目）。
     * 所以整条链只在**没注入**时才自建，且 store 由注入的仓库反推。
     */
    val promptSource = remember(repository, worldBookRepository, presetRepository, promptInputSource) {
        promptInputSource ?: run {
            val store = LuzzyStore(DatabaseProvider.luzzy(context.applicationContext))
            PromptInputSource(
                store = store,
                sessions = repository,
                presets = presetRepository ?: PresetRepository(store),
                worldBook = worldBookRepository ?: WorldBookRepository(store, repository),
            )
        }
    }
    var characterUuid by remember { mutableStateOf<String?>(null) }

    /**
     * 顶栏与名牌上显示的角色身份。
     *
     * DESIGN-compose §12 的顶栏条款原文就是「角色名 Lora 17sp + 状态行 11sp + 角色图裁圆」——
     * 也就是说**设计早就要求显示当前角色**，此前一直填的是内置演示角色 Vanio，
     * 属于实现缺口（迁移进来的角色内容配着 Vanio 的名字，看着就是错的）。
     * 存储里有角色就用真实角色；空库演示态才回落 [VanioCard]。
     */
    /**
     * `{{user}}` 的替换值。
     *
     * 目前应用内还没有「用户名」这个概念（用户消息的名牌固定是「你」，见 [ChatMessage.User]），
     * 所以先用同一个值；等 P4-C 把用户资料接进来后只改这一处。
     */
    val currentUserName = "你"
    var characterName by remember { mutableStateOf(VanioCard.Name) }
    var characterStatus by remember { mutableStateOf("${VanioCard.Subtitle} · 在线") }
    var tree by remember { mutableStateOf(BranchTree.single()) }
    val branchMessages = remember { mutableStateMapOf<String, List<ChatMessage>>() }
    var showBranches by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (branchMessages.isEmpty()) {
            // 等迁移出结论再读库：否则会在迁移还没写完时按空库渲染成演示数据
            // （用户先看到演示角色、数据随后「悄悄出现」，或要重启才出现）。
            MigrationCoordinator.state.first { it !is MigrationCoordinator.State.Running }
            // ★ 供应商配置可能**刚刚**才被「旧设置搬运」写进 SharedPreferences，而本页的
            //   `remember { store.load() }` 发生在启动准备之前（首帧早于它）→ 这里补读一次。
            //   不补的话首次启动会一直显示「未配置」，要切页或重启才正常（真机实测）。
            config = store.load()
            val session = repository.load()
            if (session != null) {
                characterUuid = session.character.uuid
                characterName = session.character.name.ifBlank { VanioCard.Name }
                val creator = characterCreator(session.character)
                characterStatus = if (creator.isNotBlank()) "$creator · 在线" else "在线"
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
            // 迁移报告：一次性提示一行（用既有 Snackbar 组件，不引入新的视觉设计）。
            // 明细（跳过/失败条目）留在 kv 与日志里，等有了设计过的报告界面再展开。
            MigrationCoordinator.consumeReport()?.let { snackbarHostState.showSnackbar(it) }
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

    /**
     * 「可见项 → 存储下标」映射（A6）：尾部快照**不渲染**，但**下标必须仍是存储下标**——
     * 编辑 / 删除 / 重新生成 / 就地 live 面板全都按存储下标定位。
     *
     * 所以这里做的是**映射**而不是把快照从数据里摘掉：摘掉的话所有下标都会错位，
     * 表现是「点了第 3 条的删除，删掉的是第 4 条」这类静默错位。
     */
    val visibleMessages = remember(activeMessages) {
        activeMessages.withIndex().filterNot { it.value is ChatMessage.Snapshot }
    }

    /** 从存储下标 [from] 起**可见的**条数（删除确认文案用；不能把看不见的快照算进去）。 */
    fun visibleCountFrom(from: Int): Int = visibleMessages.count { it.index >= from }

    val branchStats = tree.branches.associate { branch ->
        branch.id to BranchStat.of(
            branchMessages[branch.id].orEmpty().visibleMessages().map { it.text() },
        )
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
     * 分支消息**按需装载**（P4-C 性能专项）。
     *
     * 启动时只装当前分支（见 `ChatSessionRepository.load`），所以切到别的分支时这里补一次读。
     * 已在内存里的分支不重复读——切回来是零成本。
     */
    LaunchedEffect(activeBranchId, characterUuid) {
        val uuid = characterUuid ?: return@LaunchedEffect
        if (branchMessages.containsKey(activeBranchId)) return@LaunchedEffect
        branchMessages[activeBranchId] = repository.loadBranch(uuid, activeBranchId)
    }

    /**
     * 取数（IO）→ 组装（纯函数）：**在落盘之前**把本轮请求完全确定下来（A6）。
     *
     * 线程纪律（两条都踩过，注释留在原地）：
     * ① **DB 读必须切 IO**：生产库没有 `allowMainThreadQueries`，在主线程读会抛
     *    `Cannot access database on the main thread`（测试库曾放宽该限制 → 「测试绿、
     *    真机崩」，那个放宽已于 P5-A 移除）。
     * ② **Compose 状态必须在主线程读**：`withContext` 会换线程，若在 IO 段里读
     *    `characterUuid` 这类快照状态，会触发 `Detected multithreaded access to SnapshotStateObserver`。
     *    故：先把要用的值**取进局部变量**，再交给 IO 段只做 DB 操作。
     * 失败一律**降级为空输入**（不注任何块），绝不让取数失败打断对话。
     */
    suspend fun prepareTurn(
        state: List<ChatMessage>,
        userText: String,
        branchId: String,
        freshTurn: Boolean,
    ): PreparedTurn {
        val uuidForTurn = characterUuid
        val history = RequestBuilder.historyOf(state)
        // 世界书扫描的「最近消息」**不含快照**：快照正文里有 `<memory_recall>` 这类结构化片段，
        // 拿它去匹配关键词会误触发条目（那是模型看的运行时事实，不是「最近说过的话」）。
        val recent = state.visibleMessages().mapNotNull { message ->
            when (message) {
                is ChatMessage.User -> message.text
                is ChatMessage.Ai -> message.raw
                is ChatMessage.Snapshot -> null
            }
        }
        val bundle = withContext(Dispatchers.IO) {
            runCatching {
                promptSource.bundle(
                    characterUuid = uuidForTurn,
                    branchId = branchId,
                    history = history,
                    userText = userText,
                    recentMessages = recent,
                )
            }.onFailure {
                Log.w("LuzzyPrompt", "组装取数失败，本轮按空输入组装（不注入角色/预设/世界书）", it)
            }.getOrNull()
        }
        return PreparedTurn(
            plan = RequestBuilder.plan(
                state = state,
                userText = userText,
                input = bundle?.input ?: PromptAssembler.Input(),
                freshTurn = freshTurn,
            ),
            // null = 取数失败（引擎回落到它的默认执行器）；非 null 时**即使为空**也用注入的
            // ——「本次没有条目激活」与「这次没取到数」是两件事，不能混。
            worldEntries = bundle?.activatedWorldEntries,
        )
    }

    /**
     * 跑一轮真实生成（发送与重新生成共用）。
     *
     * [prepared] 已经把请求与落盘顺序都算好了（见 [prepareTurn] / [RequestBuilder]），
     * 本函数只负责「发出去 → 收到事件 → 更新界面状态」。
     *
     * [onFinish] 拿到收尾后的 [LiveTurn] 自行决定落库方式：新消息追加、或作为候选并入既有消息。
     * [regeneratingIndex] 非空时，live 面板**就地**渲染在那条消息的位置（而不是列表末尾），
     * 让「重新生成」看起来是在原处重写，而不是凭空冒出新气泡。
     */
    fun runTurn(
        prepared: PreparedTurn,
        regeneratingIndex: Int?,
        onFinish: (LiveTurn) -> Unit,
    ) {
        val turn = LiveTurn()
        live = turn
        regeneratingIndexState = regeneratingIndex
        job = scope.launch {
            pinToBottom()
            val runner = prepared.worldEntries?.let { WorldBookTool.withEntries(it) }
            try {
                engine.run(config = config, request = prepared.plan.request, toolRunner = runner)
                    .collect { event ->
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

            // 缓存观测（A7）的每轮一行：真机验收就是读这一行（PLAN §7.1 的三项指标都在里面）。
            Log.i(TAG_CACHE, "本轮 " + com.luzzymeow.luzzyrp.chat.CacheObserver.current().report())

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

    /**
     * 要用的 Compose 状态**先在主线程取进局部变量**，再把它交给协程：
     * `activeMessages` 是快照状态，在 IO 段里读会撞 `SnapshotStateObserver`（见 [prepareTurn]）。
     */
    fun send() {
        val userText = input.trim()
        if (userText.isEmpty() || live != null) return
        if (!config.configured) {
            showConfig = true
            return
        }
        input = ""
        // 记住本轮所属分支：生成期间用户切到别的分支时，结果仍落在**发起的那条分支**上
        val turnBranchId = activeBranchId
        val state = activeMessages.toList()
        job = scope.launch {
            val prepared = prepareTurn(state, userText, turnBranchId, freshTurn = true)

            // ★★ 先落快照、再落用户消息 —— **存储顺序 = 请求顺序**（A6 的核心不变式）
            //
            // 反过来（快照追加到列表末尾）就是会话 73 回退的那个真缺陷：请求里快照在用户消息
            // **之前**，存储里却在**之后** → 下一轮它出现在错误的位置 → 前缀照样从那里断开，
            // 而且不报错。顺序只在这里决定一次，见 `RequestBuilder.Plan.appends`。
            // 落盘顺序的现场：真机 release 包不可 `run-as`（数据目录读不到），
            // logcat 是验收「存储顺序 = 请求顺序」的唯一外部探针通道。
            // 标签**必须是字面量**：`it::class.simpleName` 在 release 包里会被 R8 改名成
            // `bp`/`cp` 这种不可读的形式（实测），日志就失去了判据价值。
            Log.i(
                TAG_CACHE,
                "落盘 idx=${state.size} 顺序=" + prepared.plan.appends.joinToString("→") { appendLabel(it) },
            )
            prepared.plan.appends.forEach { appendTo(turnBranchId, it) }

            runTurn(prepared, regeneratingIndex = null) { turn ->
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
        val userText = (prefix.lastOrNull() as? ChatMessage.User)?.text ?: return
        // 历史 = 这条用户消息**之前**的全部（含它前面已落盘的快照）。
        // `dropLast(1)` 是必须的：这条消息由本轮输入带上，若历史里再收一份，请求里同一句话
        // 会出现两次（既挤占上下文、也让模型以为用户说了两遍）。
        val state = prefix.dropLast(1)
        val turnBranchId = activeBranchId
        job = scope.launch {
            val prepared = prepareTurn(state, userText, turnBranchId, freshTurn = false)
            runTurn(prepared, regeneratingIndex = null) { turn ->
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
        // 同上：这条用户消息由本轮输入带上，历史里不再收一份
        val state = prefix.dropLast(1)
        val turnBranchId = activeBranchId
        job = scope.launch {
            val prepared = prepareTurn(state, userText, turnBranchId, freshTurn = false)
            runTurn(prepared, regeneratingIndex = messageIndex) { turn ->
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
                // ★ 输入法抬起时输入岛必须跟着上移。
                //
                // `ComposeActivity` 开了 `enableEdgeToEdge()`（`setDecorFitsSystemWindows(false)`），
                // 从那一刻起 Manifest 的 `windowSoftInputMode="adjustResize"` 就**不再生效**：
                // 系统不会缩小窗口，而是把键盘高度作为 IME inset 交上来，**必须由应用自己消费**。
                // 不消费的后果：软键盘盖住输入岛，用户看不见自己在打什么（真机实测，用户报告）。
                //
                // 真机实测（小米 25098PN5AC / Android 16，键盘弹起时）：
                //   `imeBottom=1036`、`compose ime == viewIme == 1036`、`decorH == screenH`（窗口未被缩小）
                //   加上本行后输入岛 y 从 **2465 → 1481**，正好抬升 1036px；收起键盘回到 2465。
                //
                // 加在 Scaffold 上而不是输入岛内部：连同「回到底部」按钮、错误卡栈与列表的
                // contentPadding 一起被抬到键盘之上，不会出现「输入岛上去了、浮层还在键盘后面」。
                //
                // 范围：本行只覆盖聊天页底栏。其它页面的长文本编辑器走
                // `androidx.compose.ui.window.Dialog`（**独立窗口**，inset 路径不同），
                // 那条路径**尚未在真机验证**——需要时单独走查（见 WORKLOG 会话 74）。
                modifier = Modifier.fillMaxSize().imePadding(),
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
                                        text = characterName,
                                        fontFamily = LuzzyFonts.Lora,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                    )
                                    Text(
                                        text = characterStatus,
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
                            // 会话总览入口（跨角色平铺；P4-C，方向 B）
                            IconButton(
                                onClick = onOpenSessions,
                                modifier = Modifier.testTag("chat_sessions"),
                            ) {
                                Icon(
                                    painter = painterResource(LuzzyIcons.Conversation),
                                    contentDescription = "全部会话",
                                    tint = Color.White.copy(alpha = 0.92f),
                                )
                            }
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
                        onPresets = { showPresets = true },
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
                    // 稳定 key：分支 id + **存储下标**。切换分支时整列 key 变化 → 强制重建条目，
                    // 避免上一条分支的条目状态（如代码块展开态）泄漏到新分支（pro-rules 亦要求列表带 key）
                    items(visibleMessages.size, key = { position -> "$activeBranchId#${visibleMessages[position].index}" }) { position ->
                        // 下标一律取**存储下标**（快照行占着位置但不渲染，见 visibleMessages 的说明）
                        val i = visibleMessages[position].index
                        // 重新生成时就地渲染 live 面板：看起来是「原处重写」，而非凭空冒出新气泡
                        val inPlaceLive = live?.takeIf { regeneratingIndexState == i }
                        if (inPlaceLive != null) {
                            Column(Modifier.fillMaxWidth()) {
                                AiMessagePanel(
                                    name = characterName,
                                    raw = inPlaceLive.body,
                                    nodes = inPlaceLive.nodes,
                                    isLive = inPlaceLive.generating,
                                    activeNode = inPlaceLive.activeNode,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            return@items
                        }
                        when (val m = visibleMessages[position].value) {
                            is ChatMessage.Ai -> Column(Modifier.fillMaxWidth()) {
                                AiMessagePanel(
                                    name = m.name,
                                    // 渲染期替换 {{char}}/{{user}}（存储不动，见 Placeholders 的说明）
                                    raw = com.luzzymeow.luzzyrp.chat.Placeholders.render(
                                        m.body, characterName, currentUserName,
                                    ),
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
                                    onCopy = { copyMessage(m.body) },
                                    onRegenerate = { regenerate(i) },
                                    onEdit = {
                                        // 编辑初值给**正文**（思维链是那次生成的历史，不该在编辑框里让用户改）
                                        editing = EditingTarget(activeBranchId, i, isAi = true, initial = m.body)
                                    },
                                    onDelete = {
                                        pendingDelete = PendingDelete(activeBranchId, i, false, count = 1)
                                    },
                                    onDeleteAfter = {
                                        pendingDelete = PendingDelete(
                                            activeBranchId, i, true,
                                            count = visibleCountFrom(i),
                                        )
                                    },
                                )
                            }

                            is ChatMessage.User -> Column(Modifier.fillMaxWidth()) {
                                UserBubble(
                                    com.luzzymeow.luzzyrp.chat.Placeholders.render(
                                        m.text, characterName, currentUserName,
                                    ),
                                )
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
                                            count = visibleCountFrom(i),
                                        )
                                    },
                                )
                            }

                            // 结构上到不了这里（`visibleMessages` 已把快照滤掉）。
                            // 保留这个分支是为了让 sealed 的**穷尽性检查继续生效**：
                            // 将来给 ChatMessage 加第四个变体时，编译器仍会在这里拦下来。
                            is ChatMessage.Snapshot -> Unit
                        }
                    }
                    // 新消息的 live 面板（重新生成时已被就地渲染，避免出现两个 live）
                    live?.takeIf { regeneratingIndexState == null }?.let { turn ->
                        item {
                            Column(Modifier.fillMaxWidth()) {
                                AiMessagePanel(
                                    name = characterName,
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
                        // AI 消息：编辑的是**正文**，而原文可能内联着思维链（旧版存法）——
                        // 用 rewrap 把思维链按原样装回去，免得「改几个字」把那次生成的思考记录抹掉。
                        target.isAi && cur is ChatMessage.Ai ->
                            cur.editCurrent(com.luzzymeow.luzzyrp.chat.CotParser.rewrap(cur.raw, text))
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
        WorldBookSheet(
            onDismiss = { showWorldBook = false },
            onManage = {
                showWorldBook = false
                onOpenWorldInfo()
            },
            repository = worldBookRepository,
        )
    }

    if (showPresets) {
        PresetsSheet(
            onDismiss = { showPresets = false },
            onManage = {
                showPresets = false
                onOpenPresets()
            },
            repository = presetRepository,
        )
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
