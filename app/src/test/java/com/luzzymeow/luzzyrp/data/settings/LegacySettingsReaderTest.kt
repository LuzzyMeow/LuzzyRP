package com.luzzymeow.luzzyrp.data.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 旧设置读数器单测（P4-B-3.3）。
 *
 * **第一段直接用真夹具的 `rp_hub_settings`**：那 44 个字段是旧版前端自己写出来的，
 * 手写样本必然漏字段（比如供应商 URL 散在 `apiProviderOverrides` / `apiProviders` / `apiUrl`
 * 三处各一份，只有真数据看得出优先级该怎么排）。
 */
class LegacySettingsReaderTest {

    private val fixtureSettings: JsonObject by lazy {
        val text = checkNotNull(javaClass.classLoader?.getResourceAsStream(FIXTURE_PATH)) {
            "夹具缺失：src/test/resources/$FIXTURE_PATH"
        }.bufferedReader().use { it.readText() }
        val all = Json.parseToJsonElement(text) as JsonObject
        val entries = ((all["databases"] as JsonObject)["RPHubDB"] as JsonObject)["entries"] as JsonObject
        entries["rp_hub_settings"] as JsonObject
    }

    @Test
    fun `真夹具的旧设置能读出主题与供应商`() {
        val legacy = LegacySettingsReader.read(fixtureSettings)

        assertEquals("旧版 themeMode=dark 应映射为 Dark", ThemeMode.Dark, legacy.themeMode)
        assertEquals("模型引用要去掉 providerId 前缀", "deepseek-chat", legacy.providerModel)
        assertEquals("https://api.deepseek.com/v1", legacy.providerBaseUrl)
        assertTrue("密钥应取到（夹具里是脱敏占位符）", legacy.providerApiKey!!.isNotBlank())
        assertEquals(15, legacy.fontSize)
        assertTrue("配置齐全", legacy.hasProvider)
        assertTrue("不该有告警：${legacy.notes}", legacy.notes.isEmpty())
    }

    @Test
    fun `供应商 URL 优先级：override 胜过缓存字段`() {
        val blob = buildJsonObject {
            put("apiProviderId", "p1")
            put("apiUrl", "https://cached.example/v1")           // 旧版缓存
            put("apiKey", "sk-legacy")
            put("model", "p1::m1")
            put(
                "apiProviderOverrides",
                buildJsonObject { put("p1", buildJsonObject { put("apiUrl", "https://override.example/v1") }) },
            )
        }
        val legacy = LegacySettingsReader.read(blob)
        assertEquals("override 是用户最后编辑的结果，必须优先", "https://override.example/v1", legacy.providerBaseUrl)
    }

    @Test
    fun `供应商 URL 回落到用户自定义列表`() {
        val blob = buildJsonObject {
            put("apiProviderId", "p2")
            put("apiUrl", "https://cached.example/v1")
            put("model", "p2::m2")
            put(
                "apiProviders",
                kotlinx.serialization.json.buildJsonArray {
                    add(buildJsonObject { put("id", "p2"); put("apiUrl", "https://userlist.example/v1") })
                },
            )
        }
        val legacy = LegacySettingsReader.read(blob)
        assertEquals("https://userlist.example/v1", legacy.providerBaseUrl)
    }

    @Test
    fun `缓存字段只在它就是当前供应商时才可信`() {
        // 旧版的 `apiUrl` 缓存只对应 `apiProviderId` 那一商（这里是 p9）。
        // 而模型引用指向 p3 —— 若把缓存的 URL 拿去配 p3，就是把 A 商的地址配到 B 商头上。
        val blob = buildJsonObject {
            put("apiProviderId", "p9")
            put("apiUrl", "https://cached-for-p9.example/v1")
            put("model", "p3::m3")
            put(
                "apiProviderKeys",
                buildJsonObject { put("p3", "sk-p3") },
            )
        }
        val legacy = LegacySettingsReader.read(blob)
        assertNull("模型指向 p3 时不能用 p9 的缓存地址", legacy.providerBaseUrl)
        assertTrue(legacy.notes.any { it.contains("找不到供应商 p3 的 API 地址") })
        // 密钥同理：分商槽位按 p3 取（槽位本身就是按商的，不受缓存影响）
        assertEquals("sk-p3", legacy.providerApiKey)
    }

    @Test
    fun `密钥优先取分商槽位，缺失才回落旧单值`() {
        val withSlots = buildJsonObject {
            put("apiProviderId", "p1")
            put("apiKey", "sk-single")
            put("model", "p1::m1")
            put(
                "apiProviderKeys",
                buildJsonObject { put("p1", "sk-per-provider"); put("custom", ""); put("custom2", "") },
            )
        }
        assertEquals("sk-per-provider", LegacySettingsReader.read(withSlots).providerApiKey)

        val withoutSlots = buildJsonObject {
            put("apiProviderId", "p1")
            put("apiKey", "sk-single")
            put("model", "p1::m1")
        }
        assertEquals("sk-single", LegacySettingsReader.read(withoutSlots).providerApiKey)
    }

    @Test
    fun `模型引用可能只写裸 id`() {
        val blob = buildJsonObject {
            put("apiProviderId", "p1")
            put("apiUrl", "https://x.example/v1")
            put("apiKey", "k")
            put("model", "gpt-x")
        }
        val legacy = LegacySettingsReader.read(blob)
        assertEquals("gpt-x", legacy.providerModel)
        assertEquals("https://x.example/v1", legacy.providerBaseUrl)
    }

    @Test
    fun `没有旧设置时不崩、不猜`() {
        assertNull(LegacySettingsReader.read(null).themeMode)
        assertNull(LegacySettingsReader.read(null).providerBaseUrl)
        assertTrue(!LegacySettingsReader.read(null).hasProvider)

        // 空对象与全空的字段组合同样安全
        val empty = LegacySettingsReader.read(buildJsonObject { })
        assertNull(empty.themeMode)
        assertNull(empty.providerModel)
        assertTrue(empty.notes.isNotEmpty())
    }

    @Test
    fun `无法识别的主题值按跟随系统处理并留痕`() {
        val blob = buildJsonObject { put("themeMode", "classic-dark") }
        val legacy = LegacySettingsReader.read(blob)
        assertNull(legacy.themeMode)
        assertTrue(legacy.notes.any { it.contains("无法识别") })
    }

    private companion object {
        const val FIXTURE_PATH = "legacy/webview-db-fixture.json"
    }
}
