package com.luzzymeow.luzzyrp.assistant.runtime.toolimpl

import com.luzzymeow.luzzyrp.assistant.data.prefs.SecretStore
import com.luzzymeow.luzzyrp.assistant.domain.tool.builtin.SearchException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 需 API Key 的搜索提供方单测（PLAN §12.2）。
 *
 * 网络路径由真机/联网验证；此处锁定「未配置 Key 时给出可操作提示」与「Key 只从加密存储取」。
 */
class KeyedSearchProvidersTest {

    private class FakeSecrets(private val values: Map<String, String> = emptyMap()) : SecretStore {
        val reads = mutableListOf<String>()
        override suspend fun put(key: String, value: String) {}
        override suspend fun get(key: String): String? {
            reads += key
            return values[key]
        }
        override suspend fun remove(key: String) {}
        override suspend fun clear() {}
    }

    @Test
    fun `Tavily 未配置 Key 时给出可操作提示`() = runBlocking {
        val provider = TavilyProvider(FakeSecrets())
        val error = runCatching { provider.search("q", 3) }.exceptionOrNull()
        assertTrue(error is SearchException)
        assertTrue(error!!.message!!.contains("未配置 Tavily API Key"))
    }

    @Test
    fun `Brave 未配置 Key 时给出可操作提示`() = runBlocking {
        val provider = BraveProvider(FakeSecrets())
        val error = runCatching { provider.search("q", 3) }.exceptionOrNull()
        assertTrue(error is SearchException)
        assertTrue(error!!.message!!.contains("未配置 Brave API Key"))
    }

    @Test
    fun `Key 从加密存储按固定键名读取`() = runBlocking {
        val secrets = FakeSecrets()
        runCatching { TavilyProvider(secrets).search("q", 3) }
        runCatching { BraveProvider(secrets).search("q", 3) }
        assertTrue(secrets.reads.contains(TavilyProvider.KEY_SECRET))
        assertTrue(secrets.reads.contains(BraveProvider.KEY_SECRET))
        assertTrue(TavilyProvider.KEY_SECRET.startsWith("search_"))
    }

    @Test
    fun `空白 Key 视为未配置`() = runBlocking {
        val provider = TavilyProvider(FakeSecrets(mapOf(TavilyProvider.KEY_SECRET to "   ")))
        val error = runCatching { provider.search("q", 3) }.exceptionOrNull()
        assertTrue(error is SearchException)
    }

    @Test
    fun `提供方 id 与展示名稳定`() {
        val tavily = TavilyProvider(FakeSecrets())
        val brave = BraveProvider(FakeSecrets())
        assertTrue(tavily.id == "tavily" && tavily.displayName == "Tavily")
        assertTrue(brave.id == "brave" && brave.displayName == "Brave Search")
    }
}
