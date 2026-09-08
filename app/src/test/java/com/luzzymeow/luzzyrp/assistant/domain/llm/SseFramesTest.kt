package com.luzzymeow.luzzyrp.assistant.domain.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SseFrames] 单测：SSE 分帧的协议细节。
 *
 * 为什么用纯函数测试代替 MockWebServer：本机 Gradle 缓存有 `mockwebserver` 制品，
 * 但 `app/build.gradle.kts` 未声明该测试依赖，任务约束禁止改构建脚本——因此
 * 「SSE 分帧」抽成纯函数单测，「HTTP/超时/取消」改用 JDK `ServerSocket` 起真 socket
 * 走 OkHttp（见 `OpenAiTransportTest`），无需任何新依赖。
 */
class SseFramesTest {

    @Test
    fun `单帧解析出 data 载荷`() {
        assertEquals(listOf("""{"a":1}"""), SseFrames.parse("data: {\"a\":1}\n\n"))
    }

    @Test
    fun `一个 chunk 含多帧按序返回`() {
        val chunk = "data: 一\n\ndata: 二\n\ndata: 三\n\n"
        assertEquals(listOf("一", "二", "三"), SseFrames.parse(chunk))
    }

    @Test
    fun `未以空行收尾的半帧不产出`() {
        assertEquals(emptyList<String>(), SseFrames.parse("data: {\"partial\":true}\n"))
        assertEquals(listOf("一"), SseFrames.parse("data: 一\n\ndata: 半"))
    }

    @Test
    fun `CRLF 与裸 CR 分隔符都识别`() {
        assertEquals(listOf("a", "b"), SseFrames.parse("data: a\r\n\r\ndata: b\r\r"))
    }

    @Test
    fun `注释与 event 字段被忽略`() {
        val chunk = ": keep-alive\nevent: message\nid: 42\ndata: payload\n\n"
        assertEquals(listOf("payload"), SseFrames.parse(chunk))
    }

    @Test
    fun `同帧多行 data 以换行连接`() {
        assertEquals(listOf("第一行\n第二行"), SseFrames.parse("data: 第一行\ndata: 第二行\n\n"))
    }

    @Test
    fun `DONE 哨兵原样返回`() {
        assertEquals(listOf(SseFrames.DONE), SseFrames.parse("data: [DONE]\n\n"))
    }

    @Test
    fun `data 冒号后无空格也解析`() {
        assertEquals(listOf("x"), SseFrames.parse("data:x\n\n"))
    }

    @Test
    fun `增量 Parser 跨 chunk 拼装半帧`() {
        val parser = SseFrames.Parser()
        assertTrue(parser.accept("data: {\"cho").isEmpty())
        assertTrue(parser.accept("ices\":[]}").isEmpty())
        assertEquals(listOf("""{"choices":[]}"""), parser.accept("\n\n"))
        assertTrue(parser.pending().isEmpty())
    }

    @Test
    fun `增量 Parser 逐行喂入等价于整块解析`() {
        val parser = SseFrames.Parser()
        val text = "data: a\n\ndata: b\n\ndata: [DONE]\n\n"
        val payloads = text.map { ch -> parser.accept(ch.toString()) }.flatten()
        assertEquals(listOf("a", "b", SseFrames.DONE), payloads)
    }

    @Test
    fun `finish 冲刷未收尾的残余帧`() {
        val parser = SseFrames.Parser()
        assertTrue(parser.accept("data: tail").isEmpty())
        assertEquals(listOf("tail"), parser.finish())
        assertTrue(parser.finish().isEmpty())
    }

    @Test
    fun `空 chunk 与空文本安全`() {
        assertEquals(emptyList<String>(), SseFrames.parse(""))
        assertEquals(emptyList<String>(), SseFrames.parse("\n\n"))
        assertTrue(SseFrames.Parser().accept("").isEmpty())
    }

    @Test
    fun `多字节字符不被切分影响`() {
        val parser = SseFrames.Parser()
        val text = "data: 中文内容\n\n"
        val half = text.length / 2
        val first = parser.accept(text.substring(0, half))
        val second = parser.accept(text.substring(half))
        assertEquals(listOf("中文内容"), first + second)
    }
}
