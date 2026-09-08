package com.luzzymeow.luzzyrp.assistant.data.prefs

/**
 * 密钥存储接口占位（PLAN §13.2：API Key / MCP 密钥走 DataStore 加密区 + 内存短生命周期）。
 *
 * **本层只留接口，不落盘任何密钥**：
 * - Preferences DataStore（assistant_prefs）与 Room（assistant.db）中禁止出现密钥字段；
 * - 真实实现（Android Keystore 派生密钥 + 加密文件 / EncryptedSharedPreferences）属 runtime 层，
 *   由后续 Agent 在 assistant/runtime/ 下实现；
 * - 日志过滤器白名单同样属 runtime 层职责，本接口不提供任何打印。
 */
interface SecretStore {

    /** 写入 / 覆盖一条密钥（值不落日志）。 */
    suspend fun put(key: String, value: String)

    /** 读取密钥；不存在返回 null。 */
    suspend fun get(key: String): String?

    suspend fun remove(key: String)

    suspend fun clear()
}

/**
 * 未接入实现时的占位：任何读写都抛异常，避免上层误以为「密钥已加密存储」而静默丢数据。
 */
object UnavailableSecretStore : SecretStore {

    private const val MESSAGE: String = "SecretStore 尚未接入（密钥存储属 runtime 层，见 PLAN §13.2）"

    override suspend fun put(key: String, value: String): Unit = throw UnsupportedOperationException(MESSAGE)

    override suspend fun get(key: String): String? = throw UnsupportedOperationException(MESSAGE)

    override suspend fun remove(key: String): Unit = throw UnsupportedOperationException(MESSAGE)

    override suspend fun clear(): Unit = throw UnsupportedOperationException(MESSAGE)
}
