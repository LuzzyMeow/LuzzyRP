package com.luzzymeow.luzzyrp.data.store

import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luzzymeow.luzzyrp.data.chat.ChatSessionRepository
import com.luzzymeow.luzzyrp.data.legacy.ScopeId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

/**
 * **性能基线**（只跑模拟器；不是正确性测试，是「肉眼看数字」的剖面）。
 *
 * ## 为什么要它
 *
 * 这一层有两个刻意的取舍，都在**每次读**上付代价，而小样本上完全看不出来：
 * 1. `ChatSessionRepository.load()` 会装载该角色**所有分支的全部消息**；
 * 2. `overview()` 对**每条会话各查 3 次**（条数 / 最后一条用户发言 / 最后一条正文）。
 * 用户只有几段会话时这两条都是 0 成本；攒了几年之后就是「打开就卡一下」。
 * 没有数字就没有优化依据，于是先造一份**接近重度用户**的数据集，把基线钉下来。
 *
 * ## 数据集规模（有意偏大但不过分）
 *
 * - **30 张角色卡**，每张 payload 约 2KB（内嵌 20 条世界书条目 —— 真卡就是这样）；
 * - 每张卡 3 条分支（主线 + 2 分支）→ **90 条会话**；
 * - **重卡**：主线 5000 条 + 两条分支各 500 条 = 6000 条消息；
 * - 其余 29 张：主线各 200 条 → 5800 条；
 * - 合计 **约 1.18 万条消息**。
 *
 * ## 纪律
 *
 * - 每项**预热 1 次 + 测 3 次取中位数**（真机/模拟器上的单次读数不可信，见 AGENTS §7）；
 * - 断言只设**宽松上限**（灾难性退化要红），数字打印出来供人比对，不做脆弱的精确断言。
 */
@RunWith(AndroidJUnit4::class)
class PerfProfileTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: LuzzyDatabase
    private lateinit var store: LuzzyStore
    private lateinit var repository: ChatSessionRepository

    private val heavyUuid = "char-0"

    @Before
    fun setUp() {
        db = DatabaseProvider.forTest(context, "perf-profile")
        store = LuzzyStore(db)
        repository = ChatSessionRepository(store)
        runBlocking { seed() }
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** 造数据：走 Dao 批量插入 + 单事务，避免种子本身拖垮测试。 */
    private suspend fun seed() {
        val payload = buildCharacterPayload()
        val characters = (0 until CharacterCount).map { i ->
            CharacterEntity(
                uuid = "char-$i",
                name = "角色$i",
                avatarPath = null,
                createdAt = i.toLong(),
                payload = payload,
            )
        }
        val branches = characters.flatMap { c ->
            listOf(
                BranchEntity(c.uuid, "main", "主线", null, 0L, 0L, 0, 0, 0, true),
                BranchEntity(c.uuid, "b1", "分支甲", "main", 1L, 1L, 0, 0, 0, false),
                BranchEntity(c.uuid, "b2", "分支乙", "main", 2L, 2L, 0, 0, 0, false),
            )
        }
        db.withTransaction {
            db.characters().upsert(com.luzzymeow.luzzyrp.data.store.CharacterEntity("perf-probe", "探针", null, 0L, "{}"))
            db.characters().delete("perf-probe")
            characters.forEach { db.characters().upsert(it) }
            db.branches().upsertAll(branches)
        }
        for (c in characters) {
            val isHeavy = c.uuid == heavyUuid
            insertMessages(c.uuid, "main", if (isHeavy) 5000 else 200)
            insertMessages(c.uuid, "b1", if (isHeavy) 500 else 0)
            insertMessages(c.uuid, "b2", if (isHeavy) 500 else 0)
        }
    }

    /** 真卡形状：内含 worldInfo / regexScripts / uiTemplates 三个数组（约 2KB JSON）。 */
    private fun buildCharacterPayload(): String {
        val entries = (0 until 20).joinToString(",") {
            """{"comment":"条目$it","keys":["钥匙$it","key$it"],"content":"这是第 $it 条世界书内容，长度接近真实条目，用于让 payload 达到真实量级。","enabled":true,"scope":"character"}"""
        }
        return """{"uuid":"x","name":"角色","description":"一段不长不短的介绍文字，用于占位。","worldInfo":[$entries],"regexScripts":[],"uiTemplates":[],"creator":"fixture","character_version":"1.0","tags":["日常","书店"],"createdAt":1}"""
    }

    private suspend fun insertMessages(characterUuid: String, branchId: String, count: Int) {
        if (count == 0) return
        val scopeId = ScopeId(characterUuid, branchId).suffix()
        val rows = (0 until count).map { i ->
            MessageEntity(
                scopeId = scopeId,
                sortIndex = i,
                id = "m$i",
                role = if (i % 2 == 0) "assistant" else "user",
                name = if (i % 2 == 0) "角色" else "我",
                content = "第 $i 条消息的正文字符串，长度大约一段话，模拟真实对话量级。",
                reasoning = if (i % 10 == 0) "一段思考内容" else null,
                payload = """{"isSelf":true,"avatar":"","imageAttachments":[]}""",
            )
        }
        db.withTransaction { rows.chunked(1000).forEach { db.messages().upsertAll(it) } }
    }

    /** 预热 1 次 + 测 3 次取中位数（真机单次读数不可信）。 */
    private fun median3(label: String, block: () -> Unit): Long {
        block()
        val samples = (1..3).map { measureTimeMillis { block() } }.sorted()
        val median = samples[1]
        println("PERF $label median=${median}ms samples=$samples")
        return median
    }

    @Test
    fun profile() = runBlocking {
        println("PERF dataset characters=$CharacterCount branches=${CharacterCount * 3} messages=${CharacterCount * 200 + 6000 - 600 + 1000}")

        val heavyLoad = median3("load(重卡 6000 条/3 分支)") { runBlocking { repository.load() } }
        val lightLoad = median3("load(轻卡 200 条)") {
            runBlocking {
                store.putString(LuzzyStore.KEY_ACTIVE_CHARACTER, "char-7")
                repository.load()
                store.putString(LuzzyStore.KEY_ACTIVE_CHARACTER, heavyUuid)
            }
        }
        val overview = median3("overview(90 条会话)") { runBlocking { repository.overview() } }
        val characters = median3("characters(30 张全量 payload)") { runBlocking { store.characters() } }
        val append = median3("append(1 条)") {
            runBlocking {
                repository.append(heavyUuid, "main", com.luzzymeow.luzzyrp.ui.pages.chat.ChatMessage.User("性能测试追加"))
            }
        }
        val switchBranch = median3("切换分支(重卡 b1 500 条)") {
            runBlocking { store.messages(ScopeId(heavyUuid, "b1")) }
        }
        println("PERF summary heavyLoad=${heavyLoad}ms lightLoad=${lightLoad}ms overview=${overview}ms characters=${characters}ms append=${append}ms branchLoad=${switchBranch}ms")

        // 宽松上限：只拦「灾难性退化」，不锁具体数值（机器不同、模拟器抖动大）
        assertTrue("load(重卡) 超过 8s，已属灾难性退化", heavyLoad < 8_000)
        assertTrue("overview 超过 5s，已属灾难性退化", overview < 5_000)
    }

    private companion object {
        const val CharacterCount = 30
    }
}
