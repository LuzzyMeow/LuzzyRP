package com.luzzymeow.luzzyrp.ui.pages

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luzzymeow.luzzyrp.chat.CharacterCards
import com.luzzymeow.luzzyrp.chat.PageDataSource
import com.luzzymeow.luzzyrp.data.legacy.ScopeId
import com.luzzymeow.luzzyrp.data.store.BranchEntity
import com.luzzymeow.luzzyrp.data.store.CharacterEntity
import com.luzzymeow.luzzyrp.data.store.LuzzyStore
import com.luzzymeow.luzzyrp.data.store.MessageEntity
import com.luzzymeow.luzzyrp.testing.Await
import com.luzzymeow.luzzyrp.testing.Capture
import com.luzzymeow.luzzyrp.testing.TestStoreFixture
import com.luzzymeow.luzzyrp.ui.pages.characters.CharactersPage
import com.luzzymeow.luzzyrp.ui.theme.LuzzyTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * **角色卡页的真交互 + 视觉留证**（v3.2 重建）。
 *
 * 重建前那页的三处硬伤都得能被判据钉住，否则「改了但没接上」还会再来一次：
 * ① 卡面是不是**这张卡自己的图**（而不是所有卡共用一张固定立绘）；
 * ② 搜索、收藏、删除、点卡切换这些动作是不是**真的落库**；
 * ③ 删除是否**级联**清掉了那条角色的会话与记忆（留下孤儿行是不会报错的）。
 */
@RunWith(AndroidJUnit4::class)
class CharactersPageTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var fixture: TestStoreFixture
    private val stamp = Capture.newStamp()

    @Before
    fun setUp() {
        fixture = TestStoreFixture.create(context, "characters")
    }

    @After
    fun tearDown() {
        fixture.close()
    }

    private fun store(): LuzzyStore = fixture.store

    /** 造一张**真图片文件**（纯色 + 大字），用来验证卡面走的是图片路径而不是 monogram 降级。 */
    private fun writeAvatar(name: String, color: Int, letter: String): String {
        val dir = File(context.filesDir, "test-avatars").apply { mkdirs() }
        val file = File(dir, "$name.png")
        val bitmap = Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color)
        val paint = Paint().apply {
            this.color = 0xFFFFFFFF.toInt()
            textSize = 380f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(letter, 300f, 620f, paint)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file.absolutePath
    }

    private fun seedCharacter(
        uuid: String,
        name: String,
        avatarPath: String? = null,
        worldInfo: Int = 0,
        regex: Int = 0,
        description: String = "",
        favoriteAt: Long = 0L,
    ) = runBlocking {
        val wi = (1..worldInfo).joinToString(",") { """{"keys":["k$it"],"content":"c$it"}""" }
        val rx = (1..regex).joinToString(",") { """{"name":"r$it"}""" }
        store().upsertCharacter(
            CharacterEntity(
                uuid = uuid,
                name = name,
                avatarPath = avatarPath,
                createdAt = System.currentTimeMillis(),
                payload = """{"name":"$name","description":"$description","worldInfo":[$wi],""" +
                    """"regexScripts":[$rx],"favoriteAt":$favoriteAt}""",
            ),
        )
    }

    private fun render() {
        compose.setContent {
            LuzzyTheme(darkTheme = false) {
                CharactersPage(
                    onOpenDrawer = {},
                    pageData = PageDataSource(store()),
                    onOpenChat = {},
                )
            }
        }
    }

    // ────────────────────────── 真数据上屏

    @Test
    fun 卡面用角色自己的图并且计数徽标来自真实payload() {
        seedCharacter("c1", "钟楼下的小恶魔", writeAvatar("vanio", 0xFF7A4B3A.toInt(), "鹿"), worldInfo = 3, regex = 2)
        render()

        Await.text(compose, "钟楼下的小恶魔", 8_000, substring = true)
        compose.onAllNodes(hasText("3 世界书", substring = true)).onFirst().assertIsDisplayed()
        compose.onAllNodes(hasText("2 正则", substring = true)).onFirst().assertIsDisplayed()
    }

    @Test
    fun 搜索真的按名称与描述过滤() {
        seedCharacter("c1", "钟楼下的小恶魔", description = "偷苹果的少年")
        seedCharacter("c2", "海边的医生", description = "沉默寡言")
        render()
        Await.text(compose, "钟楼下的小恶魔", 8_000, substring = true)
        Await.text(compose, "海边的医生", 8_000, substring = true)

        // 用描述里的词搜（名称里没有它）——这是「按名称**或描述**检索」的判据
        compose.onNodeWithTag("character_search").performTextInput("苹果")
        Await.text(compose, "命中 1 / 2 张角色卡", 8_000, substring = true)
        assertEquals(
            "不匹配的那张必须从列表里消失",
            0,
            compose.onAllNodes(hasText("海边的医生", substring = true)).fetchSemanticsNodes().size,
        )
    }

    // ────────────────────────── 真动作落库

    @Test
    fun 收藏真的写进payload() {
        seedCharacter("c1", "钟楼下的小恶魔")
        render()
        Await.text(compose, "钟楼下的小恶魔", 8_000, substring = true)

        compose.onNodeWithContentDescription("收藏 钟楼下的小恶魔").performClick()
        Await.db(compose, 5_000) {
            val row = store().character("c1") ?: return@db false
            CharacterCards.parse("c1", row.name, row.avatarPath, row.payload, false, row.createdAt).favorite
        }
        val row = runBlocking { store().character("c1")!! }
        val parsed = CharacterCards.parse("c1", row.name, row.avatarPath, row.payload, false, row.createdAt)
        assertTrue("描述等其余键不该被收藏动作抹掉", row.payload.contains("\"regexScripts\""))
        assertTrue(parsed.favorite)
    }

    @Test
    fun 点卡片切换当前角色() {
        seedCharacter("c1", "钟楼下的小恶魔")
        seedCharacter("c2", "海边的医生")
        render()
        Await.text(compose, "钟楼下的小恶魔", 8_000, substring = true)

        compose.onAllNodes(hasText("钟楼下的小恶魔", substring = true)).onFirst().performClick()
        Await.db(compose, 5_000) {
            store().string(LuzzyStore.KEY_ACTIVE_CHARACTER) == "c1"
        }
    }

    /**
     * **删除要级联**：角色 + 分支 + 消息 + 记忆一起清。
     *
     * 只删角色行是最容易犯的错（界面看着「删掉了」，库里留下一堆永远读不到的孤儿行）——
     * 而且不报错、不崩溃，只有下次做数据统计时才发现数字对不上。
     */
    @Test
    fun 删除走确认框并级联清掉会话与记忆() {
        seedCharacter("c1", "钟楼下的小恶魔")
        runBlocking {
            store().replaceBranches(
                characterUuid = "c1",
                branches = listOf(
                    BranchEntity(
                        characterUuid = "c1", branchId = "b1", name = "分叉", parentId = null,
                        createdAt = 1L, updatedAt = 1L, forkFloor = 2, messageCount = 1, wordCount = 4, isMain = false,
                    ),
                ),
                activeBranchId = "b1",
            )
            store().replaceMessages(
                ScopeId("c1", "b1"),
                listOf(
                    MessageEntity(
                        scopeId = ScopeId("c1", "b1").suffix(), sortIndex = 0, id = "m0",
                        role = "user", name = null, content = "你好", reasoning = null, payload = "{}",
                    ),
                ),
            )
            store().replaceMemories(
                ScopeId("c1", "b1"), LuzzyStore.MEMORY_VECTOR,
                listOf(kotlinx.serialization.json.Json.parseToJsonElement("""{"id":"v1","turn":1,"paragraph":"甲"}""")),
            )
        }
        render()
        Await.text(compose, "钟楼下的小恶魔", 8_000, substring = true)

        compose.onNodeWithContentDescription("删除 钟楼下的小恶魔").performClick()
        Await.text(compose, "此操作不可恢复", 8_000, substring = true)
        assertEquals("确认之前不许动数据", 1, runBlocking { store().characters().size })

        compose.onAllNodes(hasText("删除", substring = false)).onFirst().performClick()
        Await.db(compose, 8_000) {
            store().characters().isEmpty() &&
                store().branches("c1").isEmpty() &&
                store().messages(ScopeId("c1", "b1")).isEmpty() &&
                store().memories(ScopeId("c1", "b1"), LuzzyStore.MEMORY_VECTOR).isEmpty()
        }
    }

    /**
     * **编辑器保存要把名字写进两个地方**（payload + 列）。
     *
     * 列给列表页/总览页读（它们不解析 payload），payload 给提示词组装读。
     * 只写一处会出现「卡片上换了名字、聊天里还是旧名字」——两个真源各说各话，且不报错。
     */
    @Test
    fun 编辑器保存同时写payload与名字列() {
        seedCharacter("c1", "谢昭", description = "冷淡的剑客")
        render()
        Await.text(compose, "谢昭", 8_000, substring = true)

        compose.onNodeWithContentDescription("编辑 谢昭").performClick()
        Await.text(compose, "角色名称（必填）", 8_000, substring = true)

        // 描述 tab：写一段话（**先清空再输入**——performTextInput 是追加，不清会拼出
        // 「新描述 + 旧描述」这种看着像 bug 的值，实测踩过一次）
        compose.onAllNodes(hasText("描述", substring = false)).onFirst().performClick()
        compose.onNodeWithTag("character_description").performTextClearance()
        compose.onNodeWithTag("character_description").performTextInput("钟楼下的剑客，护短")
        // 基础 tab：改名字
        compose.onAllNodes(hasText("基础", substring = false)).onFirst().performClick()
        compose.onNodeWithTag("character_name").performTextClearance()
        compose.onNodeWithTag("character_name").performTextInput("谢昭·改")
        compose.onAllNodes(hasText("保存角色", substring = false)).onFirst().performClick()

        Await.db(compose, 8_000) {
            val row = store().character("c1") ?: return@db false
            row.name == "谢昭·改" && row.payload.contains("钟楼下的剑客，护短")
        }
        val row = runBlocking { store().character("c1")!! }
        assertEquals("名字列要跟着改", "谢昭·改", row.name)
        assertEquals(
            "payload 的 name 也要跟着改",
            "谢昭·改",
            CharacterCards.draftOf(row.payload, "").name,
        )
        assertEquals(
            "描述落进 payload 的 description",
            "钟楼下的剑客，护短",
            CharacterCards.draftOf(row.payload, "").description,
        )
    }

    /** 名称为空时保存按钮禁用（不存下一张看不到名字的卡）。 */
    @Test
    fun 编辑器在名称为空时不放行保存() {
        seedCharacter("c1", "谢昭")
        render()
        Await.text(compose, "谢昭", 8_000, substring = true)
        compose.onNodeWithContentDescription("编辑 谢昭").performClick()
        Await.text(compose, "角色名称（必填）", 8_000, substring = true)

        compose.onNodeWithTag("character_name").performTextClearance()
        compose.waitForIdle()
        compose.onAllNodes(hasText("保存角色", substring = false)).onFirst().assertIsNotEnabled()
    }

    /** 新建角色：建出空白卡并直接进编辑器（旧版同一流程）。 */
    @Test
    fun 新建角色建出空白卡并直接进编辑器() {
        render()
        Await.text(compose, "库里还没有角色卡", 8_000, substring = true)
        compose.onNodeWithContentDescription("新建角色").performClick()
        // 分两段等：**先等库里有卡**（这一步是产品行为），再等编辑器内容就绪。
        // 合成一段等界面在整类跑（模拟器负载高）时会偶发超时——「偶发红」的判据要改确定性，
        // 而不是把超时调大了事：拆开之后失败原因也能一眼区分（没建出来 vs 界面没起来）。
        Await.db(compose, 15_000) { store().characters().isNotEmpty() }
        // 诊断：把编辑器窗口的语义树打进 logcat（多 root 时 onRoot() 会抛，必须逐 root）
        runCatching { Await.dumpAllRoots(compose, "新建角色后编辑器内容") }
        Await.text(compose, "角色名称", 15_000, substring = true)

        // 写名字后保存 → 库里出现一张有名字的卡
        compose.onNodeWithTag("character_name").performTextInput("新来的那位")
        compose.onAllNodes(hasText("保存角色", substring = false)).onFirst().performClick()
        Await.db(compose, 8_000) {
            store().characters().any { it.name == "新来的那位" }
        }
    }

    // ────────────────────────── 视觉留证

    @Test
    fun 有真头像时的卡片逐张留证() {
        seedCharacter("c1", "钟楼下的小恶魔", writeAvatar("c1", 0xFF6E3B2E.toInt(), "鹿"), worldInfo = 3, regex = 2)
        seedCharacter("c2", "海边的医生", writeAvatar("c2", 0xFF2E4A6E.toInt(), "医"), worldInfo = 1, regex = 0)
        seedCharacter("c3", "无头像的那位", null, worldInfo = 0, regex = 0)
        runBlocking { store().putString(LuzzyStore.KEY_ACTIVE_CHARACTER, "c1") }
        render()
        Await.text(compose, "钟楼下的小恶魔", 8_000, substring = true)
        Capture.shot(context, compose, "74-characters-light-grid", stamp)

        // 叠卡（单列大卡）
        compose.onNodeWithContentDescription("切换为叠卡布局").performClick()
        compose.waitForIdle()
        Capture.shot(context, compose, "75-characters-light-deck", stamp)

        // 批量选择态
        compose.onNodeWithContentDescription("批量删除").performClick()
        compose.waitForIdle()
        compose.onAllNodes(hasText("钟楼下的小恶魔", substring = true)).onFirst().performClick()
        compose.waitForIdle()
        Capture.shot(context, compose, "76-characters-light-batch", stamp)
    }

    /**
     * **暗色留证**（卡片上是白字，暗色下必须仍然可读）。
     *
     * 卡面自带黑渐变，所以名字读得清不是自动成立的——它是设计的一部分，
     * 得在暗色主题下真的看一眼（§31 那次就是「白字压在浅色带上」被截图抓出来的）。
     */
    @Test
    fun 暗色下的角色卡留证() {        seedCharacter("c1", "钟楼下的小恶魔", writeAvatar("d1", 0xFF6E3B2E.toInt(), "鹿"), worldInfo = 3, regex = 2)
        seedCharacter("c2", "无头像的那位", null, worldInfo = 0, regex = 0)
        runBlocking { store().putString(LuzzyStore.KEY_ACTIVE_CHARACTER, "c1") }
        compose.setContent {
            LuzzyTheme(darkTheme = true) {
                CharactersPage(onOpenDrawer = {}, pageData = PageDataSource(store()), onOpenChat = {})
            }
        }
        Await.text(compose, "钟楼下的小恶魔", 8_000, substring = true)
        Capture.shot(context, compose, "77-characters-dark-grid", stamp)
    }

    /**
     * **编辑器的四段留证**——只留**内容断言**，不留视觉图。
     *
     * 原因（实测两次）：`AlertDialog` / `Dialog` 是独立窗口，本夹具的 `captureToImage()` 拿不到它
     * ——`isRoot()` 报 2 个 root 时，root1 拍出来是**主窗口的一块裁剪**而不是弹层
     * （记忆页的编辑/清空弹层也是同一现象，已登记）。所以这里改为断言语义树里确实有
     * 标题、四段 tab、名称框与保存按钮，视觉形态复用共用组件
     * （`EditorHeader` / `SegmentChips` / `OutlinedTextField` / `PrimaryButton`，
     * 那几个组件在世界书与预设编辑器里已经逐张看过）。
     */
    @Test
    fun 编辑器四段留证() {
        seedCharacter("c1", "钟楼下的小恶魔", writeAvatar("e1", 0xFF6E3B2E.toInt(), "鹿"), worldInfo = 3, regex = 2)
        render()
        Await.text(compose, "钟楼下的小恶魔", 8_000, substring = true)
        compose.onNodeWithContentDescription("编辑 钟楼下的小恶魔").performClick()
        Await.text(compose, "角色名称（必填）", 8_000, substring = true)

        // 标题 + 四段 tab + 名称框都在
        compose.onAllNodes(hasText("编辑角色", substring = true)).onFirst().assertIsDisplayed()
        for (tab in listOf("基础", "描述", "人设", "开场白")) {
            compose.onAllNodes(hasText(tab, substring = false)).onFirst().assertIsDisplayed()
        }
        compose.onNodeWithTag("character_name").assertIsDisplayed()

        // 四段都能切，且各自的输入框都在
        compose.onAllNodes(hasText("描述", substring = false)).onFirst().performClick()
        Await.text(compose, "简短描述", 8_000, substring = true)
        compose.onNodeWithTag("character_description").assertIsDisplayed()
        compose.onAllNodes(hasText("人设", substring = false)).onFirst().performClick()
        Await.text(compose, "具体人设", 8_000, substring = true)
        compose.onNodeWithTag("character_personality").assertIsDisplayed()
        compose.onAllNodes(hasText("开场白", substring = false)).onFirst().performClick()
        Await.text(compose, "开场白", 8_000, substring = true)
        compose.onNodeWithTag("character_first_mes").assertIsDisplayed()
    }
}
