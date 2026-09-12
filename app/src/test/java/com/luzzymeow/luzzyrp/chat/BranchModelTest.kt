package com.luzzymeow.luzzyrp.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 剧情分支模型（上游语义：会话 = 角色 × 分支）的确定性单测。 */
class BranchModelTest {

    private fun treeWithChild(): BranchTree =
        BranchTree.single()
            .addChild(parentId = ChatBranch.MainId, id = "b1", name = "钟楼探险", forkFloor = 3, createdAt = 100L)
            .addChild(parentId = "b1", id = "b2", name = "阁楼", forkFloor = 1, createdAt = 200L)

    @Test
    fun `默认只有主线且主线为当前`() {
        val t = BranchTree.single()
        assertEquals(1, t.branches.size)
        assertTrue(t.active.isMain)
        assertEquals(ChatBranch.MainId, t.activeId)
    }

    @Test
    fun `新建子分支记录父与分叉楼层，排序为主线优先再按创建时间`() {
        val t = treeWithChild()
        assertEquals(listOf("main", "b1", "b2"), t.sorted().map { it.id })
        val b1 = t.branches.single { it.id == "b1" }
        assertEquals(ChatBranch.MainId, b1.parentId)
        assertEquals(3, b1.forkFloor)
        assertFalse(b1.isMain)
    }

    @Test
    fun `重复 id 不覆盖`() {
        val t = treeWithChild().addChild(ChatBranch.MainId, "b1", "另一个", 1, 300L)
        assertEquals("钟楼探险", t.branches.single { it.id == "b1" }.name)
    }

    @Test
    fun `血统深度表达层级`() {
        val t = treeWithChild()
        assertEquals(0, t.depthOf(ChatBranch.MainId))
        assertEquals(1, t.depthOf("b1"))
        assertEquals(2, t.depthOf("b2"))
        assertEquals(listOf("b1"), t.childrenOf(ChatBranch.MainId).map { it.id })
    }

    @Test
    fun `改名截断到 30 字且空白名不生效`() {
        val long = "字".repeat(40)
        val renamed = treeWithChild().rename("b1", long)
        assertEquals(30, renamed.branches.single { it.id == "b1" }.name.length)
        val blank = renamed.rename("b1", "   ")
        assertEquals(30, blank.branches.single { it.id == "b1" }.name.length)
    }

    @Test
    fun `主线不可改名`() {
        val t = treeWithChild().rename(ChatBranch.MainId, "改主线")
        assertEquals("主线", t.branches.single { it.id == ChatBranch.MainId }.name)
    }

    @Test
    fun `删除分支连同后代，主线删不掉`() {
        val afterDeleteChild = treeWithChild().delete("b1")
        assertEquals(listOf("main"), afterDeleteChild.branches.map { it.id })

        val keepMain = treeWithChild().delete(ChatBranch.MainId)
        assertEquals(3, keepMain.branches.size)
    }

    @Test
    fun `删除当前分支时回退主线`() {
        val t = treeWithChild().switchTo("b2").delete("b1")
        assertEquals(ChatBranch.MainId, t.activeId)
        assertEquals(1, t.branches.size)
    }

    @Test
    fun `切换只对存在的分支生效`() {
        val t = treeWithChild().switchTo("b2")
        assertEquals("b2", t.activeId)
        val unchanged = t.switchTo("不存在")
        assertEquals("b2", unchanged.activeId)
    }

    @Test
    fun `统计从真实文本算，字数格式化按上游口径`() {
        val stat = BranchStat.of(listOf("你好呀", "他蹲在光斑里。"))
        assertEquals(2, stat.floorCount)
        assertEquals(10, stat.wordCount) // 3 + 7
        assertEquals("10 字", stat.wordCountLabel)
        assertEquals("2 楼", stat.floorLabel)

        assertEquals("1.2 万字", BranchStat(1, 12_345).wordCountLabel)
        assertEquals("0 字", BranchStat.Empty.wordCountLabel)
    }

    @Test
    fun `缺主线时构造直接失败（数据不变式）`() {
        val failed = runCatching {
            BranchTree(branches = listOf(ChatBranch.main().copy(id = "x", isMain = false)))
        }.isFailure
        assertTrue("不含主线的分支集合必须被拒绝", failed)
    }
}
