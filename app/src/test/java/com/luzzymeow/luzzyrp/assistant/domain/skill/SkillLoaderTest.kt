package com.luzzymeow.luzzyrp.assistant.domain.skill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** [SkillLoader] 单测（PLAN §8.1/§8.2：front-matter 解析 + 校验失败拒绝导入）。 */
class SkillLoaderTest {

    @Test
    fun `标准 front-matter 解析`() {
        val md = """
            ---
            name: 周报生成
            description: 把本周会话整理成周报
            tools: [memory_search, workspace_write]
            ---
            # 流程
            1. 收集
        """.trimIndent()
        val parsed = SkillLoader.parse(md)
        assertEquals("周报生成", parsed.name)
        assertEquals("把本周会话整理成周报", parsed.description)
        assertEquals(listOf("memory_search", "workspace_write"), parsed.tools)
        assertTrue(parsed.body.startsWith("# 流程"))
    }

    @Test
    fun `tools 支持逗号分隔与去引号`() {
        val parsed = SkillLoader.parse(
            "---\nname: x\ntools: \"a, b\", c\n---\nbody"
        )
        // 非方括号形式：按逗号切分，逐项去引号与空白
        assertEquals(listOf("a", "b", "c"), parsed.tools)
    }

    @Test
    fun `无 front-matter 用首个标题兜底`() {
        val parsed = SkillLoader.parse("# 我的技能\n正文")
        assertEquals("我的技能", parsed.name)
        assertEquals("", parsed.description)
    }

    @Test
    fun `无 front-matter 且无标题时用文件名兜底`() {
        val parsed = SkillLoader.parse("正文没有标题", "my_skill")
        assertEquals("my_skill", parsed.name)
    }

    @Test
    fun `front-matter 未闭合抛异常`() {
        val e = assertThrows(SkillParseException::class.java) {
            SkillLoader.parse("---\nname: x\n正文")
        }
        assertTrue(e.message!!.contains("未闭合"))
    }

    @Test
    fun `front-matter 行缺冒号抛异常`() {
        assertThrows(SkillParseException::class.java) {
            SkillLoader.parse("---\nname x\n---\nbody")
        }
    }

    @Test
    fun `缺 name 且无标题抛异常`() {
        assertThrows(SkillParseException::class.java) {
            SkillLoader.parse("---\ndescription: 只有描述\n---\nbody")
        }
    }

    @Test
    fun `未知键忽略向前兼容`() {
        val parsed = SkillLoader.parse("---\nname: x\nfuture_key: y\n---\nb")
        assertEquals("x", parsed.name)
    }

    @Test
    fun `CRLF 文本可解析`() {
        val parsed = SkillLoader.parse("---\r\nname: 中文技能\r\n---\r\n正文")
        assertEquals("中文技能", parsed.name)
        assertEquals("正文", parsed.body)
    }

    @Test
    fun `文件名推断技能名`() {
        assertEquals("weekly report", SkillLoader.nameFromFileName("weekly-report.md"))
        assertEquals("我的技能", SkillLoader.nameFromFileName("我的技能.md"))
    }

    @Test
    fun `转领域文档带作用域`() {
        val parsed = SkillLoader.parse("---\nname: n\n---\nb")
        val doc = SkillLoader.toDocument(parsed, com.luzzymeow.luzzyrp.assistant.domain.prompt.SkillScope.GLOBAL)
        assertEquals("n", doc.name)
        assertEquals(com.luzzymeow.luzzyrp.assistant.domain.prompt.SkillScope.GLOBAL, doc.scope)
    }
}
