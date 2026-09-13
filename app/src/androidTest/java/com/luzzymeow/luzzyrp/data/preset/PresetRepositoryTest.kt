package com.luzzymeow.luzzyrp.data.preset

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luzzymeow.luzzyrp.testing.TestStoreFixture
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 预设仓库的仪器化测试（W1，**只跑模拟器**）。
 *
 * 预设只有一组、顺序即注入顺序，所以这里要证的是：
 * 每次操作都是**整数组落盘**且在重启后仍然成立（不是内存里动了、库里没动）。
 */
@RunWith(AndroidJUnit4::class)
class PresetRepositoryTest {

    private lateinit var fixture: TestStoreFixture
    private lateinit var repository: PresetRepository

    @Before
    fun setUp() {
        fixture = TestStoreFixture.create(
            InstrumentationRegistry.getInstrumentation().targetContext,
            "preset",
        )
        runBlocking {
            fixture.seedFromSample()
            repository = PresetRepository(fixture.store)
        }
    }

    @After
    fun tearDown() = fixture.close()

    @Test
    fun readsSeededPreset() = runBlocking {
        val rows = repository.all()
        assertEquals(1, rows.size)
        assertEquals("一条预设", rows[0].entry.name)
        assertEquals(PresetRole.System, rows[0].entry.role)
        assertEquals("内容", rows[0].entry.content)
        assertTrue(rows[0].entry.enabled)
    }

    @Test
    fun appendsNewPresetToEnd() = runBlocking {
        repository.upsert(ref = null, entry = PresetEntry(name = "新预设", role = PresetRole.User, content = "正文"))
        val rows = repository.all()
        assertEquals(2, rows.size)
        assertEquals("一条预设", rows[0].entry.name)
        assertEquals("新预设", rows[1].entry.name)
        assertEquals(PresetRole.User, rows[1].entry.role)
    }

    @Test
    fun editsInPlaceKeepingNeighbours() = runBlocking {
        repository.upsert(ref = null, entry = PresetEntry(name = "第二条"))
        val first = repository.all().first()
        repository.upsert(
            ref = first.index,
            entry = first.entry.copy(name = "一条预设（改）", role = PresetRole.Assistant, content = "改了正文"),
        )
        val rows = repository.all()
        assertEquals("一条预设（改）", rows[0].entry.name)
        assertEquals(PresetRole.Assistant, rows[0].entry.role)
        assertEquals("改了正文", rows[0].entry.content)
        assertEquals("邻居没被动", "第二条", rows[1].entry.name)
    }

    @Test
    fun togglesEnabledAndFiltersEnabledList() = runBlocking {
        val index = repository.all().single().index
        repository.setEnabled(index, false)
        assertFalse(repository.all().single().entry.enabled)
        assertTrue("enabled() 只列开启的", repository.enabled().isEmpty())

        repository.setEnabled(index, true)
        assertEquals(1, repository.enabled().size)
    }

    @Test
    fun reordersAndDeletes() = runBlocking {
        repository.upsert(ref = null, entry = PresetEntry(name = "甲"))
        repository.upsert(ref = null, entry = PresetEntry(name = "乙"))
        assertEquals(listOf("一条预设", "甲", "乙"), repository.all().map { it.entry.name })

        repository.move(repository.all().last().index, -1)
        assertEquals(listOf("一条预设", "乙", "甲"), repository.all().map { it.entry.name })

        repository.remove(repository.all().first().index)
        assertEquals(listOf("乙", "甲"), repository.all().map { it.entry.name })
    }

    @Test
    fun survivesReopen() = runBlocking {
        repository.upsert(ref = null, entry = PresetEntry(name = "重启也要在", role = PresetRole.User))
        repository.remove(0)

        fixture = fixture.reopen()
        val repository = PresetRepository(fixture.store)
        assertEquals(listOf("重启也要在"), repository.all().map { it.entry.name })
        assertEquals(PresetRole.User, repository.all().single().entry.role)
    }
}
