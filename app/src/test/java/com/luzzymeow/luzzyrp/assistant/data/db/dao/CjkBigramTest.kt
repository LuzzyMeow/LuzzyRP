package com.luzzymeow.luzzyrp.assistant.data.db.dao

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** CjkBigram 单测：中文 bigram、中英混排、标点、单字、emoji / 代理对。 */
class CjkBigramTest {

    @Test
    fun pureChineseBecomesBigrams() {
        assertEquals("年度 度报 报告", CjkBigram.tokenize("年度报告"))
        assertEquals("请帮 帮我 我整 整理", CjkBigram.tokenize("请帮我整理"))
    }

    @Test
    fun singleChineseCharIsKeptAsIs() {
        assertEquals("中", CjkBigram.tokenize("中"))
        assertEquals("中 中国", CjkBigram.tokenize("中 中国"))
    }

    @Test
    fun mixedChineseLatinDigit() {
        assertEquals(
            "请帮 帮我 我整 整理 2024 年度 度报 报告 report",
            CjkBigram.tokenize("请帮我整理 2024 年度报告 Report"),
        )
    }

    @Test
    fun punctuationAndSymbolsSeparateRuns() {
        assertEquals("你好 世界 hello world", CjkBigram.tokenize("你好，世界！hello-world"))
        assertEquals("abc 报告", CjkBigram.tokenize("abc_报告"))
    }

    @Test
    fun latinIsLowercased() {
        assertEquals("report 2024 v2", CjkBigram.tokenize("Report 2024 V2"))
    }

    @Test
    fun emojiAndSurrogatePairsDoNotCrash() {
        val tokens = CjkBigram.tokenize("😀 报告 🎉 emoji 𠀀𠀁")
        assertTrue(tokens.contains("报告"))
        assertTrue(tokens.contains("emoji"))
        assertTrue(tokens.contains("𠀀𠀁"))
    }

    @Test
    fun blankAndPunctuationOnlyProduceNoTokens() {
        assertEquals("", CjkBigram.tokenize(""))
        assertEquals("", CjkBigram.tokenize("   "))
        assertEquals("", CjkBigram.tokenize("!!! ，。、--"))
    }

    @Test
    fun searchTextKeepsOriginalAndAppendsTokens() {
        val text = "整理年度报告"
        val searchText = CjkBigram.searchText(text)
        assertTrue(searchText.startsWith(text))
        assertTrue(searchText.contains("年度"))
        assertTrue(searchText.contains("度报"))
        assertTrue(searchText.contains("报告"))
        // 无 token 时只保留原文
        assertEquals("!!! ，。、", CjkBigram.searchText("!!! ，。、"))
    }

    @Test
    fun searchTextIsDeterministicSoRebuildIsIdempotent() {
        val content = "请帮我整理 2024 年度报告 Report"
        val first = CjkBigram.searchText(content)
        assertEquals(first, CjkBigram.searchText(content))
        // 模拟索引重建：同一正文反复索引得到完全相同的值
        val index = HashMap<String, String>()
        index["m1"] = CjkBigram.searchText(content)
        index["m1"] = CjkBigram.searchText(content)
        assertEquals(first, index["m1"])
    }

    @Test
    fun tokenCountIsCapped() {
        val long = "字".repeat(CjkBigram.MAX_TOKENS + 500)
        assertEquals(CjkBigram.MAX_TOKENS, CjkBigram.tokenize(long).split(' ').size)
    }

    @Test
    fun containsCjkDetectsChineseOnly() {
        assertTrue(CjkBigram.containsCjk("a报b"))
        assertTrue(CjkBigram.containsCjk("报告"))
        assertFalse(CjkBigram.containsCjk("abc123"))
        assertFalse(CjkBigram.containsCjk(""))
    }
}
