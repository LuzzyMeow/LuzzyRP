package com.luzzymeow.luzzyrp.data.world

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luzzymeow.luzzyrp.testing.TestStoreFixture
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 世界书仓库的仪器化测试（W1，**只跑模拟器**）。
 *
 * 纯函数层已由 `WorldEntryTest`（JVM）覆盖；**这一层要证的是「落到哪张表」**：
 * 全局条目在 `records(global_worldinfo)`、角色绑定条目在**角色卡 payload 的 `worldInfo` 里**
 * 且不伤其它键；以及「杀进程重启后还在」。
 *
 * 样例数据正好是「有旧键、没有全局键」→ 迁移后应被**采纳为全局**（W0 的坑 13）。
 */
@RunWith(AndroidJUnit4::class)
class WorldBookRepositoryTest {

    private lateinit var fixture: TestStoreFixture
    private lateinit var repository: WorldBookRepository

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Before
    fun setUp() {
        fixture = TestStoreFixture.create(
            InstrumentationRegistry.getInstrumentation().targetContext,
            "worldbook",
        )
        runBlocking {
            fixture.seedFromSample()
            repository = WorldBookRepository(fixture.store, fixture.repository)
        }
    }

    @After
    fun tearDown() = fixture.close()

    @Test
    fun adoptsLegacyEntryAsGlobal() = runBlocking {
        val book = repository.load()
        assertEquals("样例里只有旧键 → 采纳为全局", 1, book.globalRows.size)
        assertTrue("没有角色绑定条目", book.characterRows.isEmpty())
        assertEquals("一条世界书", book.globalRows[0].entry.comment)
        assertEquals(listOf("钥匙"), book.globalRows[0].entry.keys)
        assertEquals("采纳时 scope 归一到 global", WorldScope.Global, book.globalRows[0].entry.scope)
        assertEquals("样例角色", book.characterName)
    }

    @Test
    fun createsCharacterBoundEntryInsideCharacterPayload() = runBlocking {
        repository.upsert(
            ref = null,
            entry = WorldEntry(comment = "新绑定条目", keys = listOf("雾"), scope = WorldScope.Character),
        )

        val book = repository.load()
        assertEquals(1, book.globalRows.size)
        assertEquals(1, book.characterRows.size)
        assertEquals("新绑定条目", book.characterRows[0].entry.comment)

        // 关键：条目进了**角色卡 payload**，且角色卡的其它键一字未动
        val payload = json.parseToJsonElement(fixture.store.character("char-1")!!.payload).jsonObject
        assertEquals(1, payload["worldInfo"]!!.jsonArray.size)
        assertEquals("首句", payload["first_mes"]!!.jsonPrimitive.content)
        assertEquals("样例角色", payload["name"]!!.jsonPrimitive.content)
        assertEquals("char-1", payload["uuid"]!!.jsonPrimitive.content)
    }

    @Test
    fun movesEntryAcrossBucketsOnScopeChange() = runBlocking {
        repository.upsert(
            ref = null,
            entry = WorldEntry(comment = "会搬家的条目", scope = WorldScope.Character),
        )
        val characterRef = repository.load().characterRows.single().ref

        repository.upsert(
            ref = characterRef,
            entry = WorldEntry(comment = "会搬家的条目", scope = WorldScope.Global),
        )

        val book = repository.load()
        assertEquals("全局多一条", 2, book.globalRows.size)
        assertTrue("角色桶清空", book.characterRows.isEmpty())
        assertEquals("追加到全局末尾", "会搬家的条目", book.globalRows.last().entry.comment)
        assertTrue(
            "角色卡里的 worldInfo 也应被清空",
            json.parseToJsonElement(fixture.store.character("char-1")!!.payload)
                .jsonObject["worldInfo"]!!.jsonArray.isEmpty(),
        )
    }

    @Test
    fun toggleWritesOnlyEnabledKey() = runBlocking {
        val ref = repository.load().globalRows.single().ref
        repository.setEnabled(ref, false)
        assertFalse(repository.load().globalRows.single().entry.enabled)

        // 其余字段不许被顺手重排
        val entry = repository.load().globalRows.single().entry
        assertEquals("一条世界书", entry.comment)
        assertEquals("内容", entry.content)
        assertEquals(listOf("钥匙"), entry.keys)

        repository.setEnabled(ref, true)
        assertTrue(repository.load().globalRows.single().entry.enabled)
    }

    @Test
    fun roundTripsAllEditableFields() = runBlocking {
        val ref = repository.load().globalRows.single().ref
        repository.upsert(
            ref = ref,
            entry = WorldEntry(
                comment = "全字段",
                content = "正文",
                keys = listOf("甲", "乙"),
                enabled = true,
                scope = WorldScope.Global,
                position = WorldPosition.UserTop,
                order = 7,
                depth = 9,
                scanDepth = 3,
                probability = 60,
                useProbability = true,
                useRegex = true,
                constant = false,
            ),
        )
        val back = repository.load().globalRows.single().entry
        assertEquals("全字段", back.comment)
        assertEquals(listOf("甲", "乙"), back.keys)
        assertEquals(WorldPosition.UserTop, back.position)
        assertEquals(7, back.order)
        assertEquals(9, back.depth)
        assertEquals(3, back.scanDepth)
        assertEquals(60, back.probability)
        assertTrue(back.useRegex)
        assertFalse(back.constant)
    }

    @Test
    fun reordersWithinBucket() = runBlocking {
        repository.upsert(ref = null, entry = WorldEntry(comment = "甲", scope = WorldScope.Global))
        repository.upsert(ref = null, entry = WorldEntry(comment = "乙", scope = WorldScope.Global))
        assertEquals(listOf("一条世界书", "甲", "乙"), repository.load().globalRows.map { it.entry.comment })

        val last = repository.load().globalRows.last().ref
        repository.move(last, -1)
        assertEquals(listOf("一条世界书", "乙", "甲"), repository.load().globalRows.map { it.entry.comment })

        val first = repository.load().globalRows.first().ref
        repository.move(first, -1)
        assertEquals("上越界不动", listOf("一条世界书", "乙", "甲"), repository.load().globalRows.map { it.entry.comment })
    }

    @Test
    fun deletesEntry() = runBlocking {
        repository.remove(repository.load().globalRows.single().ref)
        assertTrue(repository.load().isEmpty)
    }

    @Test
    fun settingsRoundTrip() = runBlocking {
        assertEquals(WorldInfoSettings(2, 0), repository.settings())
        repository.saveSettings(WorldInfoSettings(scanDepth = 5, maxDepth = 12))
        assertEquals(WorldInfoSettings(5, 12), repository.settings())
        assertFalse(repository.settings().unlimitedDepth)

        repository.saveSettings(WorldInfoSettings(scanDepth = 99, maxDepth = 99))
        assertEquals("越界值按滑杆量程夹取", WorldInfoSettings(20, 50), repository.settings())
    }

    @Test
    fun survivesReopen() = runBlocking {
        repository.upsert(
            ref = null,
            entry = WorldEntry(comment = "重启也要在", keys = listOf("存"), scope = WorldScope.Character),
        )
        repository.saveSettings(WorldInfoSettings(3, 8))

        fixture = fixture.reopen()
        val repository = WorldBookRepository(fixture.store, fixture.repository)
        val book = repository.load()
        assertEquals(1, book.characterRows.size)
        assertEquals("重启也要在", book.characterRows.single().entry.comment)
        assertEquals(WorldInfoSettings(3, 8), repository.settings())
    }

    @Test
    fun requiresCharacterForCharacterScopedWrites() = runBlocking {
        // 空库（无角色）时写「绑定角色」条目必须显式失败，而不是静默丢掉用户的编辑
        val empty = TestStoreFixture.create(
            InstrumentationRegistry.getInstrumentation().targetContext,
            "worldbook-empty",
        )
        try {
            val repository = WorldBookRepository(empty.store, empty.repository)
            assertNull(repository.load().characterUuid)
            val failure = runCatching {
                repository.upsert(ref = null, entry = WorldEntry(comment = "无处安放", scope = WorldScope.Character))
            }.exceptionOrNull()
            assertNotNull("应当报错而不是默默不写", failure)
            assertTrue(failure!!.message!!.contains("没有当前角色"))
        } finally {
            empty.close()
        }
    }
}
