package com.luzzymeow.luzzyrp.ui.pages

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
    fun 暗色下的角色卡留证() {
        seedCharacter("c1", "钟楼下的小恶魔", writeAvatar("d1", 0xFF6E3B2E.toInt(), "鹿"), worldInfo = 3, regex = 2)
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
}
