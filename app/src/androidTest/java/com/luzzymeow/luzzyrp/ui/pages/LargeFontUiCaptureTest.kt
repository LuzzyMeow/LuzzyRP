package com.luzzymeow.luzzyrp.ui.pages

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luzzymeow.luzzyrp.chat.PageDataSource
import com.luzzymeow.luzzyrp.data.legacy.ScopeId
import com.luzzymeow.luzzyrp.data.store.BranchEntity
import com.luzzymeow.luzzyrp.data.store.CharacterEntity
import com.luzzymeow.luzzyrp.data.store.LuzzyStore
import com.luzzymeow.luzzyrp.testing.Await
import com.luzzymeow.luzzyrp.testing.Capture
import com.luzzymeow.luzzyrp.testing.TestStoreFixture
import com.luzzymeow.luzzyrp.ui.pages.characters.CharactersPage
import com.luzzymeow.luzzyrp.ui.pages.memory.MemoryPage
import com.luzzymeow.luzzyrp.ui.pages.usage.UsagePage
import com.luzzymeow.luzzyrp.ui.theme.LuzzyTheme
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * **大字号（150%）留证**：重建后的三个页面在系统字号放大一档时会不会破版。
 *
 * ## 为什么必须单独验这一档
 *
 * 150% 是最容易破版的输入：中文标签变长、卡片头带高度固定、徽标行会换行。
 * 而「破版」的形态往往是**静默截断**（`maxLines = 1` 把关键词吃掉）或**溢出被裁**
 * ——两者在语义树里都「存在」，只有看图才发现。
 *
 * ## 怎么模拟
 *
 * 不改系统设置（那会影响同机其它用例），而是在渲染层覆写 `LocalDensity` 的 `fontScale`：
 * 这正是系统字号在 Compose 里的落地方式（`sp` 会乘它）。
 * 密度取 2.75（与模拟器 1080×2400 / 420dpi 一致），字号乘 1.5。
 */
@RunWith(AndroidJUnit4::class)
class LargeFontUiCaptureTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var fixture: TestStoreFixture
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val stamp = Capture.newStamp()

    @Before
    fun setUp() {
        fixture = TestStoreFixture.create(context, "largefont")
        runBlocking { seed() }
    }

    @After
    fun tearDown() {
        fixture.close()
    }

    private fun avatar(name: String, color: Int, letter: String): String {
        val dir = File(context.filesDir, "test-avatars").apply { mkdirs() }
        val file = File(dir, "$name.png")
        val bitmap = Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color)
        canvas.drawText(
            letter, 300f, 620f,
            Paint().apply {
                this.color = 0xFFFFFFFF.toInt()
                textSize = 380f
                isFakeBoldText = true
                textAlign = Paint.Align.CENTER
            },
        )
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file.absolutePath
    }

    private suspend fun seed() {
        val store = fixture.store
        store.upsertCharacter(
            CharacterEntity("k1", "钟楼下的小恶魔", avatar("lf1", 0xFF6E3B2E.toInt(), "鹿"), 1L,
                """{"name":"钟楼下的小恶魔","description":"偷苹果的少年","worldInfo":[{"k":1},{"k":2},{"k":3}],"regexScripts":[{"n":1},{"n":2}]}"""),
        )
        store.upsertCharacter(
            CharacterEntity("k2", "无头像的那位", null, 2L, """{"name":"无头像的那位"}"""),
        )
        store.replaceBranches(
            characterUuid = "k1",
            branches = listOf(
                BranchEntity("k1", "main", "主线", null, 1L, 1L, 0, 0, 0, true),
                BranchEntity("k1", "b1", "钟楼西侧走廊尽头", "main", 1L, 2L, 2, 0, 0, false),
            ),
            activeBranchId = "b1",
        )
        store.putString(LuzzyStore.KEY_ACTIVE_CHARACTER, "k1")
        store.replaceMemories(
            ScopeId("k1", "b1"), LuzzyStore.MEMORY_VECTOR,
            listOf(
                json.parseToJsonElement(
                    """{"id":"v1","turn":1,"paragraph":"钟楼顶上的红苹果树只有他找得到；树下埋着一枚生锈的钥匙。","enabled":true,"chunkMode":"paragraph","embeddingDims":1536,"embeddingModel":"text-embedding-3-small"}""",
                ),
            ),
        )
        store.replaceMemories(
            ScopeId("k1", "b1"), LuzzyStore.MEMORY_CLASSIC,
            listOf(
                json.parseToJsonElement(
                    """{"id":"c1","turn":2,"summary":"第 1-2 轮：你在钟楼下撞见偷苹果的小恶魔，他起初戒备。","enabled":true}""",
                ),
            ),
        )
        val now = System.currentTimeMillis()
        listOf(
            """{"type":"chat","model":"deepseek-chat","provider":"deepseek","protocol":"openai",
               "inputTokens":1200,"outputTokens":400,"totalTokens":1600,"cacheReadTokens":600,
               "durationMs":1500,"finishReason":"stop","reported":true,"timestamp":${now - 3600_000}}""",
            """{"type":"chat","model":"gpt-4o-mini","provider":"openai","protocol":"openai",
               "inputTokens":300,"outputTokens":120,"totalTokens":420,"cacheReadTokens":90,
               "durationMs":900,"finishReason":"stop","reported":true,"timestamp":${now - 7200_000}}""",
        ).forEach { raw ->
            val record = json.parseToJsonElement(raw)
            store.replaceRecords(LuzzyStore.RECORD_USAGE, "", store.records(LuzzyStore.RECORD_USAGE) + record)
        }
    }

    /** 150% 字号 + 亮色渲染（`fontScale` 是 `sp` 的乘数，正是系统字号设置的作用点）。 */
    private fun renderLarge(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 2.75f, fontScale = 1.5f),
            ) {
                LuzzyTheme(darkTheme = false) { content() }
            }
        }
    }

    @Test
    fun 记忆页在百分之一百五字号下不破版() {
        renderLarge { MemoryPage(onOpenDrawer = {}, pageData = PageDataSource(fixture.store)) }
        Await.text(compose, "记忆系统", 8_000, substring = true)
        Await.text(compose, "注入记忆召回", 8_000, substring = true)
        Capture.shot(context, compose, "84-memory-font150", stamp)
        compose.onNodeWithTag("memory_list").performScrollToNode(hasText("清空此作用域记忆", substring = true))
        compose.waitForIdle()
        Capture.shot(context, compose, "85-memory-font150-content", stamp)
    }

    @Test
    fun 用量页在百分之一百五字号下不破版() {
        renderLarge { UsagePage(onOpenDrawer = {}, pageData = PageDataSource(fixture.store)) }
        Await.text(compose, "总用量", 8_000, substring = true)
        compose.onNodeWithTag("usage_list").performScrollToNode(hasText("用量趋势", substring = true))
        compose.waitForIdle()
        Capture.shot(context, compose, "86-usage-font150", stamp)
    }

    @Test
    fun 角色卡页在百分之一百五字号下不破版() {
        renderLarge {
            CharactersPage(onOpenDrawer = {}, pageData = PageDataSource(fixture.store), onOpenChat = {})
        }
        Await.text(compose, "钟楼下的小恶魔", 8_000, substring = true)
        Capture.shot(context, compose, "87-characters-font150", stamp)
    }
}
