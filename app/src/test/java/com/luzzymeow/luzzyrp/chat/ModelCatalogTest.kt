package com.luzzymeow.luzzyrp.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 供应商模型目录：解析宽容性 + 端点拼接（纯函数，不触网）。 */
class ModelCatalogTest {

    @Test
    fun `OpenAI 标准形态解析 id 并保序去重`() {
        val body = """
            {"object":"list","data":[
              {"id":"deepseek-chat","object":"model"},
              {"id":"deepseek-reasoner","object":"model"},
              {"id":"deepseek-chat","object":"model"}
            ]}
        """.trimIndent()
        assertEquals(listOf("deepseek-chat", "deepseek-reasoner"), ModelCatalog.parseModelIds(body))
    }

    @Test
    fun `兼容 models 键与裸数组两种真实形态`() {
        assertEquals(
            listOf("glm-4-flash"),
            ModelCatalog.parseModelIds("""{"models":[{"id":"glm-4-flash"}]}"""),
        )
        assertEquals(
            listOf("m1"),
            ModelCatalog.parseModelIds("""[{"id":"m1"}]"""),
        )
        // 少数中转用 name 而非 id
        assertEquals(
            listOf("some-model"),
            ModelCatalog.parseModelIds("""{"models":[{"name":"some-model"}]}"""),
        )
    }

    @Test
    fun `脏输入一律空列表而不抛异常`() {
        assertTrue(ModelCatalog.parseModelIds("").isEmpty())
        assertTrue(ModelCatalog.parseModelIds("   ").isEmpty())
        assertTrue(ModelCatalog.parseModelIds("<html>502</html>").isEmpty())
        assertTrue(ModelCatalog.parseModelIds("""{"error":{"message":"unauthorized"}}""").isEmpty())
        assertTrue(ModelCatalog.parseModelIds("""{"data":"not-an-array"}""").isEmpty())
        assertTrue(ModelCatalog.parseModelIds("""{"data":[{"noid":1},{"id":"  "}]}""").isEmpty())
    }

    @Test
    fun `models 端点从聊天端点回退`() {
        assertEquals(
            "https://api.example/v1/models",
            ModelCatalog.modelsEndpoint(TransportConfig(baseUrl = "https://api.example/v1")),
        )
        assertEquals(
            "https://api.example/v1/models",
            ModelCatalog.modelsEndpoint(TransportConfig(baseUrl = "https://api.example/v1/chat/completions")),
        )
        assertEquals("", ModelCatalog.modelsEndpoint(TransportConfig()))
    }
}
