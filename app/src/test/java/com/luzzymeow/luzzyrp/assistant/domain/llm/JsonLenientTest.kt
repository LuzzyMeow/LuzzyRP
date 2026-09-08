package com.luzzymeow.luzzyrp.assistant.domain.llm

import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [JsonLenient] 单测：模型产出 JSON 的容错路径（围栏 / 前后噪声 / 尾随逗号 / 空串）。 */
class JsonLenientTest {

    @Test
    fun `去除 markdown 代码围栏`() {
        val raw = "```json\n{\"a\":1}\n```"
        assertEquals(1, JsonLenient.parseObjectOrEmpty(raw)["a"]?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun `容忍 JSON 前后的解释文字`() {
        val raw = "好的，我调用工具：{\"query\":\"天气\"} 以上。"
        assertEquals("天气", JsonLenient.parseObjectOrEmpty(raw)["query"]?.jsonPrimitive?.content)
    }

    @Test
    fun `容忍尾随逗号`() {
        val raw = "{\"a\":1,}"
        assertEquals(1, JsonLenient.parseObjectOrEmpty(raw)["a"]?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun `空串返回空对象`() {
        assertTrue(JsonLenient.parseObjectOrEmpty("").isEmpty())
        assertTrue(JsonLenient.parseObjectOrEmpty("   ").isEmpty())
    }

    @Test
    fun `完全非 JSON 返回空对象不抛异常`() {
        assertTrue(JsonLenient.parseObjectOrEmpty("抱歉，我无法完成").isEmpty())
    }

    @Test
    fun `嵌套对象与数组保真`() {
        val raw = "{\"options\":[{\"label\":\"A\"},{\"label\":\"B\"}],\"n\":2}"
        val obj = JsonLenient.parseObjectOrEmpty(raw)
        assertEquals(2, obj["n"]?.jsonPrimitive?.content?.toInt())
        assertEquals(2, (obj["options"] as kotlinx.serialization.json.JsonArray).size)
    }
}
