package com.luzzymeow.luzzyrp.data.store

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luzzymeow.luzzyrp.data.legacy.LegacyDb
import com.luzzymeow.luzzyrp.data.legacy.LegacyMigrator
import com.luzzymeow.luzzyrp.data.legacy.MigratedData
import com.luzzymeow.luzzyrp.data.legacy.ScopeId
import com.luzzymeow.luzzyrp.testing.SampleLegacyExport
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 数据层仪器化测试（**只跑模拟器**）：证明 Room 在这套工具链下**运行时**可用，
 * 而不只是「能生成代码」。
 *
 * 为什么这一条必须真跑：存储选型 spike 的原判据只是「15 分钟内能构建过」，
 * 而构建通过 ≠ 运行时能开库（Room 还需要 SQLite 驱动、且真实设备上会撞到
 * 主线程限制、文件权限、schema 校验）。构建通过就宣布「Room 可用」是不负责任的。
 *
 * 覆盖三件事：
 * 1. **迁移 → 存储** 的端到端落地（走真实迁移器，不是手搓实体）；
 * 2. **幂等**：同一份数据导入两次，行数不翻倍；
 * 3. **持久化**：关库再开，数据仍在（这是「杀进程重启数据还在」的最小证据）。
 */
@RunWith(AndroidJUnit4::class)
class LuzzyStoreTest {

    private lateinit var db: LuzzyDatabase
    private lateinit var store: LuzzyStore
    private lateinit var writer: MigrationWriter
    private lateinit var dbFile: File
    private lateinit var filesDir: File

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        filesDir = File(context.cacheDir, "store-test-files").apply { mkdirs() }
        dbFile = File(context.cacheDir, "store-test.db")
        dbFile.delete()
        openDatabase()
    }

    @After
    fun tearDown() {
        db.close()
        dbFile.delete()
        filesDir.deleteRecursively()
    }

    private fun openDatabase() {
        db = Room.databaseBuilder(context, LuzzyDatabase::class.java, dbFile.absolutePath)
            .allowMainThreadQueries()
            .build()
        store = LuzzyStore(db)
        writer = MigrationWriter(store, filesDir)
    }

    /** 关库再开：验证数据真的落到了文件，而不只是活在内存里。 */
    private fun reopenDatabase() {
        db.close()
        openDatabase()
    }

    // ---------------------------------------------------------------- 用例

    @Test
    fun migrationImportLandsInRoomWithSameCounts() = runBlocking {
        val data = LegacyMigrator.migrate(LegacyDb.parse(SampleLegacyExport.JSON))

        val outcome = writer.import(data)

        assertEquals(data.characters.size, outcome.characters)
        assertEquals(data.conversations.sumOf { it.messages.size }, outcome.messages)
        assertEquals(store.characterCount(), data.characters.size)
        assertEquals(2, store.messages(ScopeId("char-1")).size)
        assertEquals(2, store.messages(ScopeId("char-1", "b1")).size)
        // 记忆：向量 1 + 经典 1
        assertEquals(1, store.memories(ScopeId("char-1"), LuzzyStore.MEMORY_VECTOR).size)
        assertEquals(1, store.memories(ScopeId("char-1"), LuzzyStore.MEMORY_CLASSIC).size)
        // 向量四字段在 payload 里逐字保留
        val vector = store.memories(ScopeId("char-1"), LuzzyStore.MEMORY_VECTOR).single().toString()
        assertTrue(vector.contains("\"embeddingEncoding\":\"int8:maxabs:v1\""))
        assertTrue(vector.contains("\"embeddingDims\":8"))
    }

    @Test
    fun importingTwiceDoesNotDuplicate() = runBlocking {
        val data = LegacyMigrator.migrate(LegacyDb.parse(SampleLegacyExport.JSON))

        val first = writer.import(data)
        val second = writer.import(data)

        assertEquals(first, second)
        assertEquals(store.characterCount(), data.characters.size)
        assertEquals(2, store.messages(ScopeId("char-1")).size)
        assertEquals(2, store.branches("char-1").size)
    }

    @Test
    fun dataSurvivesDatabaseReopen() = runBlocking {
        val data = LegacyMigrator.migrate(LegacyDb.parse(SampleLegacyExport.JSON))
        writer.import(data)
        store.putString(LuzzyStore.KEY_ACTIVE_CHARACTER, "char-1")

        reopenDatabase()

        assertEquals(2, store.characterCount())
        assertEquals(2, store.messages(ScopeId("char-1")).size)
        assertEquals("char-1", store.string(LuzzyStore.KEY_ACTIVE_CHARACTER))
        assertEquals("b1", store.activeBranchId("char-1"))
    }

    @Test
    fun migrationMarkSurvivesReimport() = runBlocking {
        val data = LegacyMigrator.migrate(LegacyDb.parse(SampleLegacyExport.JSON))
        val outcome = writer.import(data)
        writer.markMigrated(outcome, migratedAt = 1_700_000_000_000L)
        assertTrue(writer.alreadyMigrated())

        writer.import(data) // 再导一次

        assertTrue("标记不能被内容清表带走", writer.alreadyMigrated())
        assertEquals("1700000000000", store.string(LuzzyStore.KEY_LEGACY_MIGRATED_AT))
    }

    @Test
    fun appendMessageTouchesOnlyItsOwnRow() = runBlocking {
        val data = LegacyMigrator.migrate(LegacyDb.parse(SampleLegacyExport.JSON))
        writer.import(data)
        val scope = ScopeId("char-1")
        val before = store.messages(scope).size

        store.appendMessage(
            MessageEntity(
                scopeId = scope.suffix(),
                sortIndex = before,
                id = "new-1",
                role = "user",
                name = "我",
                content = "刚发的一句",
                reasoning = null,
                payload = "{}",
            ),
        )

        val after = store.messages(scope)
        assertEquals(before + 1, after.size)
        assertEquals("刚发的一句", after.last().content)
        // 原有条目的内容一字未动
        assertEquals("首句", after.first().content)
    }

    @Test
    fun deletingTailMessages() = runBlocking {
        val data = LegacyMigrator.migrate(LegacyDb.parse(SampleLegacyExport.JSON))
        writer.import(data)
        val scope = ScopeId("char-1")

        store.deleteMessagesFrom(scope, fromIndex = 1)

        assertEquals(1, store.messages(scope).size)
        assertEquals(0, store.messages(scope).single().sortIndex)
    }

    @Test
    fun extractedAssetsWrittenToDiskOnce() = runBlocking {
        // 样例头像只有几十字符，低于默认抽取阈值（4096）→ 用低阈值把这条机制单独逼出来
        val data = LegacyMigrator.migrate(
            LegacyDb.parse(SampleLegacyExport.JSON),
            LegacyMigrator.Options(inlineBase64Threshold = 16),
        )
        assertTrue("低阈值下应当有被抽出来的头像", data.assets.isNotEmpty())
        val asset = data.assets.first()

        writer.import(data)
        val file = File(filesDir, asset.path)
        assertTrue("附件应落盘：${asset.path}", file.isFile)
        assertEquals(asset.byteCount, file.length().toInt())

        val stamp = file.lastModified()
        writer.import(data)
        assertEquals("内容一致时不应重写文件", stamp, file.lastModified())
        assertTrue(store.attachments().any { it.path == asset.path })
    }

    @Test
    fun listsAllConversationScopes() = runBlocking {
        val data = LegacyMigrator.migrate(LegacyDb.parse(SampleLegacyExport.JSON))
        writer.import(data)

        val scopes = store.conversationScopes()

        assertTrue(ScopeId("char-1") in scopes)
        assertTrue(ScopeId("char-1", "b1") in scopes)
        assertEquals(2, scopes.size)
    }

    /**
     * 跨角色平铺总览的数据层（P4-C）：只取摘要，不搬历史正文。
     *
     * 断言口径全部落在**真实性**上：条数来自消息表、预览来自末条、空分支也要出现
     * （用户需要看到「这个分支还是空的」，而不是让它凭空消失）。
     */
    @Test
    fun sessionOverviewListsEveryCharacterAndBranch() = runBlocking {
        val data = LegacyMigrator.migrate(LegacyDb.parse(SampleLegacyExport.JSON))
        writer.import(data)
        val repository = com.luzzymeow.luzzyrp.data.chat.ChatSessionRepository(store)

        val overview = repository.overview()

        val char1 = overview.filter { it.characterUuid == "char-1" }
        assertEquals("char-1 应有 main + b1 两条会话", 2, char1.size)
        assertEquals("主线必须排在前面", com.luzzymeow.luzzyrp.chat.ChatBranch.MainId, char1.first().branchId)
        assertEquals(2, char1.first().messageCount)
        assertEquals("第二句", char1.first().previewText)
        val branch = char1.first { it.branchId == "b1" }
        assertEquals(2, branch.messageCount)
        assertEquals("分支里的问句", branch.previewText)
        assertEquals("分支名要带上", "分支甲", branch.branchName)

        // 第二张角色卡没有分支键 → 也要出现在总览里（合成主线），而不是消失
        assertTrue(overview.any { it.characterUuid == "char-2" && it.isMain })
        assertEquals("角色名带上，供列表显示", "样例角色", char1.first().characterName)
    }

    /**
     * 预览取「最后一条**用户**发言」（用户 2026-09-13 拍板），
     * 且**无用户发言时回落末条正文**——否则那一行会空着，与已批准的版式不符。
     *
     * 这里用合成的三行验证回落：只开场白（无用户发言）/ 有用户发言但后面又跟了助手回复。
     */
    @Test
    fun sessionOverviewPreviewPrefersLastUserMessageAndFallsBack() = runBlocking {
        store.putString(LuzzyStore.KEY_ACTIVE_CHARACTER, "c1")
        store.upsertCharacter(
            com.luzzymeow.luzzyrp.data.store.CharacterEntity(
                uuid = "c1", name = "角色甲", avatarPath = null, createdAt = 1L, payload = "{}",
            ),
        )
        val scope = ScopeId("c1")
        store.replaceMessages(
            scope,
            listOf(
                com.luzzymeow.luzzyrp.data.store.MessageEntity(scope.suffix(), 0, "m0", "assistant", "甲", "开场白", null, "{}"),
                com.luzzymeow.luzzyrp.data.store.MessageEntity(scope.suffix(), 1, "m1", "user", "我", "我说过的第一句", null, "{}"),
                com.luzzymeow.luzzyrp.data.store.MessageEntity(scope.suffix(), 2, "m2", "assistant", "甲", "模型回了一大段（末条正文）", null, "{}"),
            ),
        )
        val repository = com.luzzymeow.luzzyrp.data.chat.ChatSessionRepository(store)
        assertEquals("必须取用户那一句，而不是末条助手正文", "我说过的第一句", repository.overview().single().previewText)

        // 只有开场白（用户一句话都没说）→ 回落末条正文，别让那一行空着
        store.replaceMessages(
            scope,
            listOf(
                com.luzzymeow.luzzyrp.data.store.MessageEntity(scope.suffix(), 0, "m0", "assistant", "甲", "开场白", null, "{}"),
            ),
        )
        assertEquals("开场白", repository.overview().single().previewText)
    }

    /**
     * **内联思维链必须变成思考节点、且不再出现在正文里**（本轮修的真实缺陷）。
     *
     * 上游把 CoT 存在正文里（`<thinking>…</thinking>`，靠 `parseCot()` 渲染期剥离）。
     * 我们当初只认独立的 `reasoning` 字段，于是迁移进来的消息出现两个症状：
     * 思维链被当正文渲染 + 思考节点是空的。这条断言把两者一起钉住。
     */
    @Test
    fun inlineCotBecomesThinkNodeAndLeavesTheBody() = runBlocking {
        val scope = ScopeId("c1")
        val withCot = "<thinking>\n[情景意图分析] 先想清楚再答。\n</thinking>\n\n正文从这里开始。"
        store.upsertCharacter(
            com.luzzymeow.luzzyrp.data.store.CharacterEntity("c1", "角色甲", null, 1L, "{}"),
        )
        store.replaceMessages(
            scope,
            listOf(
                com.luzzymeow.luzzyrp.data.store.MessageEntity(scope.suffix(), 0, "m0", "assistant", "甲", withCot, null, "{}"),
            ),
        )
        val repository = com.luzzymeow.luzzyrp.data.chat.ChatSessionRepository(store)
        val message = repository.loadBranch("c1", "main").single() as com.luzzymeow.luzzyrp.ui.pages.chat.ChatMessage.Ai

        val node = message.thinkNodes.filterIsInstance<com.luzzymeow.luzzyrp.ui.pages.chat.ThinkNode.Brainstorm>().singleOrNull()
        assertTrue("内联 CoT 应还原成思考节点", node != null)
        assertTrue("节点里应是思维链原文", node!!.text.contains("[情景意图分析]"))
        assertEquals("展示正文不该带 thinking 标记", "正文从这里开始。", message.body)
        assertTrue("原文（落盘/重生成用）保持不变", message.raw.contains("<thinking>"))
        // 总览预览同样要剥掉，否则会显示 <thinking>…
        assertEquals("正文从这里开始。", repository.overview().single().previewText)
    }

    @Test
    fun emptyDatabaseIsReadable() = runBlocking {
        assertEquals(0, store.characterCount())
        assertTrue(store.characters().isEmpty())
        assertTrue(store.messages(ScopeId("nobody")).isEmpty())
        assertNull(store.activeBranchId("nobody"))
        assertNull(store.string(LuzzyStore.KEY_LEGACY_MIGRATED))
        assertNotNull(store.keys())
    }

}
