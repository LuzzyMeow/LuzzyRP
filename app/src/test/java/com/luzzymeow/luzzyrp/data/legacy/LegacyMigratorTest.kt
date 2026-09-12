package com.luzzymeow.luzzyrp.data.legacy

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 旧数据迁移器单测（`docs/DESIGN-migration.md` §5 的 12 条坑 + §1 的验收判据）。
 *
 * 分两层，**都不是摆设**：
 * 1. **真实夹具**（`app/src/test/resources/legacy/webview-db-fixture.json`，由前端自身函数写出来的
 *    真数据）—— 断言计数（G1）与幂等（G2）；
 * 2. **构造用例** —— 覆盖真数据里恰好没有的边界（畸形记录、越界下标、孤儿作用域…）。
 *
 * 夹具是真实输入而不是桩，所以这一层不需要模拟器就能守住迁移语义。
 */
class LegacyMigratorTest {

    // ---------------------------------------------------------------- 夹具层

    private val fixture: LegacyDb by lazy {
        val text = checkNotNull(javaClass.classLoader?.getResourceAsStream(FIXTURE_PATH)) {
            "夹具缺失：src/test/resources/$FIXTURE_PATH"
        }.bufferedReader().use { it.readText() }
        LegacyDb.parse(text)
    }

    private val migrated: MigratedData by lazy { LegacyMigrator.migrate(fixture) }

    @Test
    fun `夹具可解析且两库键数符合来源`() {
        assertEquals(29, fixture.main.size)
        assertEquals(3, fixture.legacy.size)
        assertEquals(1, fixture.mainVersion)
        assertEquals(1, fixture.legacyVersion)
    }

    @Test
    fun `G1 计数与源数据一致`() {
        val counts = migrated.counts()
        // 3 张真实角色卡 + 主库里 legacy 前缀的 1 张 + 只在旧库的 1 张 ——
        // 后两者证明「按身份合并」生效：整键优先会各自丢掉一张
        assertEquals(5, counts["characters"])
        // Vanio: main + 1 分支；谢昭: main；夏梧 / 11111111 / 66666666 无 branches 键 → 各合成 main
        assertEquals(6, counts["branches"])
        // Vanio 主线 / Vanio 分支 / 谢昭 / 旧档角色(11111111) / 仅旧库角色(66666666)
        assertEquals(5, counts["conversations"])
        assertEquals(5 + 5 + 1 + 3 + 2, counts["messages"])
        assertEquals(4, counts["vectorMemories"])
        assertEquals(4, counts["classicMemories"])
        assertEquals(4, counts["worldEntries"])
        assertEquals(2, counts["regexes"])
        assertEquals(19, counts["presets"])
        assertEquals(9, counts["usage"])
        assertEquals(2, counts["profiles"])
    }

    @Test
    fun `G1 真实夹具没有需要跳过的坏记录`() {
        assertEquals(
            "真实数据不应产生跳过项，出现了说明迁移器把正常形态误判成非法：${migrated.skipped}",
            emptyList<SkippedRecord>(),
            migrated.skipped,
        )
    }

    @Test
    fun `G2 幂等：同一夹具跑两次逐字段相等`() {
        val again = LegacyMigrator.migrate(fixture)
        // MigratedData 是数据类，逐字段比较；ExtractedAsset 的 equals 按字节内容比
        assertEquals(migrated, again)
    }

    @Test
    fun `坑1 作用域拼接：主线是裸 uuid，分支带 __branch__ 分隔符`() {
        val vanio = "e4c2c68b-2d1d-4b5d-8efd-7ada0b43f555"
        val branch = "a4e75ca8-07d2-440b-b55a-84e71eb2b1fe"
        val scopes = migrated.conversations.map { it.scope }.toSet()
        assertTrue("主线作用域必须是裸 uuid", ScopeId(vanio) in scopes)
        assertTrue(
            "分支作用域必须是 <uuid>__branch__<bid>",
            ScopeId(vanio, branch) in scopes,
        )
        assertEquals(vanio, ScopeId(vanio).suffix())
        assertEquals("$vanio${LegacyKeys.BRANCH_SEPARATOR}$branch", ScopeId(vanio, branch).suffix())
    }

    @Test
    fun `坑4 消息没有时间戳，顺序只能靠数组下标`() {
        val vanioMain = migrated.conversationOf("e4c2c68b-2d1d-4b5d-8efd-7ada0b43f555")
        assertEquals(5, vanioMain.messages.size)
        assertEquals(listOf(0, 1, 2, 3, 4), vanioMain.messages.map { it.sortIndex })
        assertEquals("assistant", vanioMain.messages[0].role)
        assertEquals("user", vanioMain.messages[1].role)
        assertTrue("正文非空", vanioMain.messages[0].content.isNotEmpty())
    }

    @Test
    fun `坑5 瞬态字段被丢弃，内容字段全部保留`() {
        val messages = migrated.conversationOf("e4c2c68b-2d1d-4b5d-8efd-7ada0b43f555").messages
        // 第 3 条是 deepseek-chat 那一轮：有 reasoning 字段但内容为空（真实数据就是这样）
        val plain = messages[2]
        assertEquals("assistant", plain.role)
        assertEquals("", plain.reasoning)
        // 第 5 条是 deepseek-reasoner 那一轮：思考内容 2347 字符，必须完整留下
        val reasoned = messages[4]
        assertEquals("assistant", reasoned.role)
        assertNotNull(reasoned.reasoning)
        assertTrue("思考内容要留下", reasoned.reasoning!!.length > 500)

        // 真实数据里这几项都在，且都必须被剔掉
        assertTrue(plain.droppedTransient.containsAll(listOf("shouldAnimate", "isCotOpen", "isReasoningOpen")))
        for (message in messages) {
            for (field in LegacyModelDefaults.TRANSIENT_MESSAGE_FIELDS) {
                assertFalse("临时态 $field 不应进入 rest", message.rest.containsKey(field))
            }
            assertFalse(message.rest.containsKey("content"))
            assertFalse(message.rest.containsKey("role"))
            assertFalse(message.rest.containsKey("reasoning"))
        }
        assertTrue("isSelf 这类语义字段要保留", plain.rest.containsKey("isSelf"))
    }

    @Test
    fun `坑6 大头像转文件、小头像留内联`() {
        val vanio = migrated.characters.first { it.name == "Vanio" }
        assertNotNull("7871 字符的头像应被抽成文件", vanio.avatarPath)
        assertEquals("assets/avatars/${vanio.uuid}.jpg", vanio.avatarPath)
        // 原字段已被换成路径（新读取方按「非 data: 即路径」解释）
        assertEquals(vanio.avatarPath, vanio.fields.string("avatar"))
        val asset = migrated.assets.single { it.path == vanio.avatarPath }
        assertEquals("image/jpeg", asset.mimeType)
        assertTrue("解出来应是真 JPEG（含 SOI 标记）", asset.bytes.size > 1000)
        assertEquals(0xFF.toByte(), asset.bytes[0])
        assertEquals(0xD8.toByte(), asset.bytes[1])

        // 另外两张卡的头像只有 182 字符（defaultAvatar），低于阈值 → 保持内联，不为几 KB 建文件
        val small = migrated.characters.first { it.name == "谢昭" }
        assertNull(small.avatarPath)
        assertTrue(small.fields.string("avatar")!!.startsWith("data:"))
    }

    @Test
    fun `坑7 向量记忆的 embedding 四字段原样搬运（绝不重算）`() {
        val scope = ScopeId("e4c2c68b-2d1d-4b5d-8efd-7ada0b43f555")
        val vector = migrated.vectorMemories.getValue(scope)
        assertEquals(2, vector.size)
        val raw = rawEntry("rp_hub_memories_${scope.suffix()}").let { Json.parseToJsonElement(it.toString()) }
        // 逐字段与源一致（不是「看起来像」）
        assertEquals(raw, kotlinx.serialization.json.JsonArray(vector))
        val first = vector.first() as JsonObject
        assertEquals("int8:maxabs:v1", first.string("embeddingEncoding"))
        assertEquals(3072, first.intOf("embeddingDims"))
        assertEquals(4096, first.string("embeddingQ")!!.length)
        assertTrue(first.containsKey("embeddingScale"))
    }

    @Test
    fun `坑8 memory_settings 的内嵌作用域键被看见并被规范化`() {
        val settings = migrated.memorySettings as JsonObject
        val emptyTurns = settings["emptyTurns"] as JsonObject
        // 源里有两条，规范化后仍是两条（形态本就一致；这一步的验收点是「没有静默漏掉」）
        assertEquals(2, emptyTurns.size)
        assertTrue(
            emptyTurns.containsKey("e4c2c68b-2d1d-4b5d-8efd-7ada0b43f555:vector"),
        )
        assertTrue(
            emptyTurns.containsKey("e4c2c68b-2d1d-4b5d-8efd-7ada0b43f555__branch__a4e75ca8-07d2-440b-b55a-84e71eb2b1fe:vector"),
        )
    }

    @Test
    fun `坑9 last_active_char 是下标，要换算成 uuid`() {
        // 源里是 1 → 第二张角色卡（谢昭）
        assertEquals("cf39170a-97df-4f70-b2a3-2b2a3c13603c", migrated.activeCharacterUuid)
        assertEquals("谢昭", migrated.characters[1].name)
    }

    @Test
    fun `坑10 旧库独有的角色没被丢掉`() {
        val legacyOnly = migrated.characters.firstOrNull { it.uuid == LEGACY_ONLY_UUID }
        assertNotNull("只写在旧库的角色必须被合并进来（整键优先会丢掉它）", legacyOnly)
        assertEquals("仅旧库角色", legacyOnly!!.name)
        assertTrue(
            "它的会话也要跟过来",
            migrated.conversations.any { it.scope == ScopeId(LEGACY_ONLY_UUID) },
        )
        // 主库里 legacy 前缀那张卡同样要进来（它只存在于 silly_tavern_characters）
        assertNotNull(
            "主库 legacy 前缀里的角色也要合并进来",
            migrated.characters.firstOrNull { it.uuid == "11111111-2222-3333-4444-555555555555" },
        )
        assertTrue(
            "它的会话也要跟过来",
            migrated.conversations.any { it.scope == ScopeId("11111111-2222-3333-4444-555555555555") },
        )
    }

    @Test
    fun `坑3 同键同库并存时新前缀胜出`() {
        // rp_hub_settings（新）与 silly_tavern_settings（旧）同在 RPHubDB：必须取新的。
        // 断言不写死具体模型名（夹具会随生成脚本演进），改为与源里的新键逐字比对。
        val expected = (fixture.main.getValue("rp_hub_settings") as JsonObject).string("model")
        val settings = migrated.settings as JsonObject
        assertEquals(expected, settings.string("model"))
        assertFalse("旧 settings 的 model 不应覆盖新值", settings.string("model") == "legacy-model")
    }

    @Test
    fun `坑2 旧格式 chat_数字下标 能回落到新格式缺失的会话`() {
        val db = LegacyDb(
            mainVersion = 1,
            main = mapOf(
                "rp_hub_characters" to buildJsonArray {
                    add(buildJsonObject { put("uuid", JsonPrimitive("u-1")); put("name", JsonPrimitive("旧用户角色")) })
                },
                // 旧版本用过 `chat_<角色下标>`（新前缀、旧编号）
                "rp_hub_chat_0" to buildJsonArray {
                    add(buildJsonObject { put("role", JsonPrimitive("assistant")); put("content", JsonPrimitive("旧编号会话")) })
                },
            ),
            legacyVersion = null,
            legacy = emptyMap(),
        )
        val result = LegacyMigrator.migrate(db)
        assertEquals(1, result.conversations.size)
        assertEquals("rp_hub_chat_0", result.conversations.single().sourceKey)
        assertTrue(result.notes.any { it.contains("回落到旧格式 chat_0") })
    }

    @Test
    fun `坑2 数字索引键不会被两个角色重复认领`() {
        val db = LegacyDb(
            mainVersion = 1,
            main = mapOf(
                "rp_hub_characters" to buildJsonArray {
                    add(buildJsonObject { put("uuid", JsonPrimitive("u-0")); put("name", JsonPrimitive("甲")) })
                    add(buildJsonObject { put("uuid", JsonPrimitive("u-1")); put("name", JsonPrimitive("乙")) })
                },
                // 只有一条 chat_1：乙（下标 1）能认领，甲不能也去抢
                "rp_hub_chat_1" to buildJsonArray {
                    add(buildJsonObject { put("role", JsonPrimitive("assistant")); put("content", JsonPrimitive("给乙的")) })
                },
            ),
            legacyVersion = null,
            legacy = emptyMap(),
        )
        val result = LegacyMigrator.migrate(db)
        assertEquals(1, result.conversations.size)
        assertEquals("u-1", result.conversations.single().scope.characterUuid)
    }

    @Test
    fun `坑10 孤立作用域不静默丢：补占位角色接住`() {
        val db = LegacyDb(
            mainVersion = 1,
            main = mapOf(
                "rp_hub_memories_ghost-uuid" to buildJsonArray {
                    add(buildJsonObject { put("id", JsonPrimitive("m1")); put("summary", JsonPrimitive("幽灵记忆")) })
                },
            ),
            legacyVersion = null,
            legacy = emptyMap(),
        )
        val result = LegacyMigrator.migrate(db)
        assertEquals(1, result.vectorMemoryCount)
        val ghost = result.characters.single()
        assertEquals("ghost-uuid", ghost.uuid)
        assertTrue(ghost.name.startsWith("（孤立数据）"))
        assertTrue(result.notes.any { it.contains("孤立作用域") })
    }

    @Test
    fun `坏记录只跳过不中断，且原因可见`() {
        val db = LegacyDb(
            mainVersion = 1,
            main = mapOf(
                "rp_hub_characters" to buildJsonArray {
                    add(buildJsonObject { put("uuid", JsonPrimitive("u-ok")); put("name", JsonPrimitive("正常")) })
                    add(JsonPrimitive("这不是对象"))
                },
                "rp_hub_chat_u-ok" to buildJsonArray {
                    add(buildJsonObject { put("role", JsonPrimitive("assistant")); put("content", JsonPrimitive("ok")) })
                    add(buildJsonObject { put("name", JsonPrimitive("缺 role")) })            // 缺 role
                    add(buildJsonObject { put("role", JsonPrimitive("user")) })               // 缺 content
                    add(buildJsonObject { put("role", JsonPrimitive("user")); put("content", JsonPrimitive("好")) })
                },
                "rp_hub_presets" to JsonPrimitive("本该是数组"),
                "rp_hub_active_profile_id" to JsonPrimitive("指向不存在的人设"),
            ),
            legacyVersion = 1,
            legacy = mapOf("silly_tavern_memories_bad__branch__" to buildJsonArray { }),
        )
        val result = LegacyMigrator.migrate(db)

        // 好数据照常迁完
        assertEquals(1, result.characters.size)
        assertEquals(2, result.conversations.single().messages.size)
        // 坏的每一条都有原因
        assertTrue(result.skipped.any { it.reason.contains("不是对象") })
        assertTrue(result.skipped.any { it.reason.contains("缺 role") })
        assertTrue(result.skipped.any { it.reason.contains("缺 content") })
        assertTrue(result.skipped.any { it.key == "rp_hub_presets" && it.reason.contains("期望数组") })
        assertTrue(result.skipped.any { it.reason.contains("作用域后缀非法") })
        assertTrue(result.skipped.any { it.key == "rp_hub_active_profile_id" })
        assertNull("指向不存在人设时置空而不是留悬空 id", result.activeProfileId)
    }

    @Test
    fun `坑9 下标越界时跳过且不误指`() {
        val db = LegacyDb(
            mainVersion = 1,
            main = mapOf(
                "rp_hub_characters" to buildJsonArray {
                    add(buildJsonObject { put("uuid", JsonPrimitive("only-one")); put("name", JsonPrimitive("独苗")) })
                },
                "rp_hub_last_active_char" to JsonPrimitive(7),
            ),
            legacyVersion = null,
            legacy = emptyMap(),
        )
        val result = LegacyMigrator.migrate(db)
        assertNull(result.activeCharacterUuid)
        assertTrue(result.skipped.any { it.reason.contains("越界") })
    }

    @Test
    fun `坑11 未序列化的畸形值不崩、不猜：分支时间回落且不抛`() {
        val db = LegacyDb(
            mainVersion = 1,
            main = mapOf(
                "rp_hub_characters" to buildJsonArray {
                    add(buildJsonObject { put("uuid", JsonPrimitive("u-1")); put("name", JsonPrimitive("A")) })
                },
                // branches 里 createdAt 是个对象（真机上不该出现：上游 cloneForStorage 会转 ISO/毫秒数）
                "rp_hub_branches_u-1" to buildJsonObject {
                    put("version", JsonPrimitive(1))
                    put("activeBranchId", JsonPrimitive("b1"))
                    put(
                        "branches",
                        buildJsonArray {
                            add(buildJsonObject { put("id", JsonPrimitive("main")); put("name", JsonPrimitive("主线")) })
                            add(
                                buildJsonObject {
                                    put("id", JsonPrimitive("b1"))
                                    put("name", JsonPrimitive("分支甲"))
                                    put("parentId", JsonPrimitive("不存在的主线"))
                                    put("createdAt", buildJsonObject { put("bad", JsonPrimitive(true)) })
                                },
                            )
                        },
                    )
                },
            ),
            legacyVersion = null,
            legacy = emptyMap(),
        )
        val result = LegacyMigrator.migrate(db)
        val branches = result.branchesByCharacter.getValue("u-1")
        assertEquals(2, branches.size)
        assertEquals(0L, branches.first { it.id == "b1" }.createdAt)
        assertEquals(
            "父分支不存在要回落到主线并留下说明",
            LegacyKeys.MAIN_BRANCH_ID,
            branches.first { it.id == "b1" }.parentId,
        )
        assertTrue(result.notes.any { it.contains("parentId") })
        assertEquals("activeBranchId 指向存在的分支则保留", "b1", result.activeBranchByCharacter.getValue("u-1"))
    }

    @Test
    fun `没有 uuid 的角色按内容哈希补，且两次结果一致`() {
        val main = mapOf<String, kotlinx.serialization.json.JsonElement>(
            "rp_hub_characters" to buildJsonArray {
                add(
                    buildJsonObject {
                        put("name", JsonPrimitive("无名"))
                        put("createdAt", JsonPrimitive(1700000000000L))
                    },
                )
            },
        )
        val db = LegacyDb(1, main, null, emptyMap())
        val first = LegacyMigrator.migrate(db)
        val second = LegacyMigrator.migrate(db)
        val uuid = first.characters.single().uuid
        assertTrue(first.characters.single().synthesizedUuid)
        // 确定性：同一输入两次推导出同一个 uuid，否则第二次迁移会变成「多出一个角色」
        assertEquals(uuid, second.characters.single().uuid)
        assertEquals(uuid, LegacyMigrator.deterministicUuid("无名", "1700000000000", "0"))
    }

    @Test
    fun `identityMergedArray 同 uuid 取新库版本`() {
        val db = LegacyDb(
            mainVersion = 1,
            main = mapOf(
                "rp_hub_characters" to buildJsonArray {
                    add(buildJsonObject { put("uuid", JsonPrimitive("same")); put("name", JsonPrimitive("新版本")) })
                },
            ),
            legacyVersion = 1,
            legacy = mapOf(
                "silly_tavern_characters" to buildJsonArray {
                    add(buildJsonObject { put("uuid", JsonPrimitive("same")); put("name", JsonPrimitive("旧版本")) })
                    add(buildJsonObject { put("uuid", JsonPrimitive("extra")); put("name", JsonPrimitive("旧库独有")) })
                },
            ),
        )
        val result = LegacyMigrator.migrate(db)
        assertEquals(2, result.characters.size)
        assertEquals("新版本", result.characters.first { it.uuid == "same" }.name)
        assertEquals("旧库独有", result.characters.first { it.uuid == "extra" }.name)
    }

    @Test
    fun `parseDataUrl 只认内联 base64`() {
        assertNotNull(LegacyMigrator.parseDataUrl("data:image/png;base64,AAAA"))
        assertNull(LegacyMigrator.parseDataUrl("assets/avatars/x.png"))
        assertNull(LegacyMigrator.parseDataUrl("data:image/png,notbase64"))
        assertNull(LegacyMigrator.parseDataUrl(""))
    }

    // ---------------------------------------------------------------- 辅助

    private fun MigratedData.conversationOf(characterUuid: String): MigratedConversation =
        conversations.single { it.scope == ScopeId(characterUuid) }

    /** 取源载荷（用于「逐字段一致」断言，而不是只比数量）。 */
    private fun rawEntry(logicalKey: String) = when {
        fixture.main.containsKey(LegacyKeys.STORAGE_PREFIX + logicalKey) ->
            fixture.main.getValue(LegacyKeys.STORAGE_PREFIX + logicalKey)
        else -> fixture.main.getValue(logicalKey)
    }

    private companion object {
        const val FIXTURE_PATH = "legacy/webview-db-fixture.json"
        const val LEGACY_ONLY_UUID = "66666666-7777-8888-9999-aaaaaaaaaaaa"
    }
}
