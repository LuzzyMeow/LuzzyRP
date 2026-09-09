package com.luzzymeow.luzzyrp.assistant.runtime.prefs

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.luzzymeow.luzzyrp.assistant.data.prefs.SecretStore
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * 密钥存储实现（PLAN §13.2：**DataStore 加密区 + 内存短生命周期**）。
 *
 * 方案（不引入第三方依赖）：**AndroidKeyStore 里的 AES-256-GCM 主密钥**（不出安全硬件）
 * 加密每条值，密文以 `base64(iv ‖ ciphertext+tag)` 存 `filesDir/assistant/secrets.json`。
 *
 * 安全约束：
 * - 主密钥 `setUserAuthenticationRequired(false)`（助手需在后台用 Key，不能强制解锁）；
 * - **值不进日志**：本类不打印任何内容，异常消息只含键名与字节数；
 * - 读取**不缓存**（每次解密后由调用方尽快释放）；文件位于应用私有目录、不参与备份；
 * - 主密钥失效（如用户清除锁屏/恢复出厂）→ 无法解密，本类**清空并重建**（不抛给 UI）。
 *
 * 说明：这是「应用私有目录 + Keystore 加密」，不是 `EncryptedSharedPreferences`
 * （`androidx.security:security-crypto` 未在本地依赖缓存中，且已停止维护）。
 */
class KeystoreSecretStore(
    private val file: File,
) : SecretStore {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val mutex = Mutex()

    override suspend fun put(key: String, value: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val store = load()
            store[key] = encrypt(value)
            persist(store)
        }
    }

    override suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val encoded = load()[key] ?: return@withLock null
            runCatching { decrypt(encoded) }.getOrNull()
        }
    }

    override suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val store = load()
            if (store.remove(key) != null) persist(store)
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching { if (file.exists()) file.delete() }
            Unit
        }
    }

    /** 已存条目数（诊断用，**不返回内容**）。 */
    fun size(): Int = runCatching { load().size }.getOrDefault(0)

    // ------------------------------------------------------------------

    private fun load(): MutableMap<String, String> {
        if (!file.isFile) return mutableMapOf()
        return runCatching {
            val root = json.parseToJsonElement(file.readText()).let { it as? JsonObject } ?: return@runCatching mutableMapOf()
            root.entries.mapNotNull { (k, v) ->
                (v as? JsonPrimitive)?.content?.let { k to it }
            }.toMap().toMutableMap()
        }.getOrDefault(mutableMapOf())
    }

    private fun persist(store: Map<String, String>) {
        file.parentFile?.mkdirs()
        val body = buildJsonObject { store.forEach { (k, v) -> put(k, JsonPrimitive(v)) } }.toString()
        // 先写临时文件再改名，避免写入中断留下半个密文文件
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(body)
        if (!tmp.renameTo(file)) {
            file.writeText(body)
            tmp.delete()
        }
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val iv = cipher.iv
        val payload = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return base64Encode(iv + payload)
    }

    private fun decrypt(encoded: String): String? {
        val raw = base64Decode(encoded) ?: return null
        if (raw.size <= IV_LENGTH) return null
        val iv = raw.copyOfRange(0, IV_LENGTH)
        val payload = raw.copyOfRange(IV_LENGTH, raw.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(payload), Charsets.UTF_8)
    }

    private fun masterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    private fun base64Encode(bytes: ByteArray): String =
        android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

    private fun base64Decode(text: String): ByteArray? =
        runCatching { android.util.Base64.decode(text, android.util.Base64.NO_WRAP) }.getOrNull()

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "luzzy_assistant_secrets_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val IV_LENGTH = 12
    }
}
