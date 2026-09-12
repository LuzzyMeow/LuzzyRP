package com.luzzymeow.luzzyrp.data.legacy

import java.io.File
import java.security.MessageDigest

/**
 * 迁移收件箱：把迁移 WebView 分块回传的导出物拼回一个文件。
 *
 * **为什么必须分块**：导出物里可能有大头像与 4096 字符的向量记忆，一个真实用户的全量导出
 * 轻松到几 MB。JS → 原生一次性传这么大的字符串会撞 Binder 事务上限（1 MB）——
 * 表现为**静默截断或直接失败**，而且是数据量越大越出问题，测试小样本时看不出来。
 * 所以协议定为「开始 → 若干块 → 结束」，每块由调用方控制在安全尺寸内。
 *
 * 设计约束：
 * - **顺序强校验**：块序号必须连续递增，缺块即失败（宁可报错，也不要把半份数据拼成一份"能解析"的 JSON）；
 * - **落盘而不是攒内存**：拼的过程直接写文件，避免几十 MB 常驻；
 * - **只增不改**：只写 `incoming/` 下的文件，不碰旧库（G5）。
 *
 * 目录布局（`filesDir/migration/`）：
 * ```
 * incoming/legacy-export.json   拼接结果（迁移器的输入）
 * incoming/manifest.json        块数 / 字节数 / 完成标记（中断续跑判据）
 * ```
 */
class MigrationInbox(private val root: File) {

    private val incomingDir: File get() = File(root, DIR_INCOMING).apply { mkdirs() }
    private val exportFile: File get() = File(incomingDir, FILE_EXPORT)
    private val manifestFile: File get() = File(incomingDir, FILE_MANIFEST)

    private var session: Session? = null
    private var lastResult: Result? = null

    /** 期待的下一块序号；**严格连续**才接受（缺块 = 拼出来的 JSON 不完整，宁可失败）。 */
    private var expectedSeq = 0
    private var charCount = 0L

    /** 上一次 [finish] 的结果（供原生侧读取报告）。 */
    val last: Result? get() = lastResult

    data class Session(val id: String, val startedAt: Long)

    data class Result(
        val file: File,
        val chunkCount: Int,
        val charCount: Long,
        val sha256: String,
        val summary: Summary?,
    )

    /** 页面侧 `migrateDone` 带来的自述信息（键数、库版本等），仅用于报告与交叉校验。 */
    data class Summary(val mainKeys: Int, val legacyKeys: Int, val mainVersion: Int?, val legacyVersion: Int?)

    /**
     * 开始一次导出：清掉上次残留。
     *
     * 返回会话 id；页面把它原样带回 [appendChunk]（防止上一次中断的块混进这一次）。
     */
    fun start(sessionId: String): String {
        incomingDir.mkdirs()
        exportFile.delete()
        manifestFile.delete()
        expectedSeq = 0
        charCount = 0L
        session = Session(sessionId, System.currentTimeMillis())
        return sessionId
    }

    /**
     * 追加一块。返回 false 表示这块被拒绝（顺序错乱 / 没有会话 / 序号重复），
     * 页面据此走 `migrateError` 而不是继续。
     */
    fun appendChunk(seq: Int, payload: String): Boolean {
        if (session == null) return false
        if (seq != expectedSeq) return false
        return try {
            exportFile.appendText(payload, Charsets.UTF_8)
            expectedSeq += 1
            charCount += payload.length
            true
        } catch (e: Exception) {
            false
        }
    }

    /** 收尾：校验并给出结果。块序号不连续 / 结果为空即返回 null（调用方按「导出失败」处理）。 */
    fun finish(summary: Summary?): Result? {
        val current = session ?: return null
        val file = exportFile
        if (!file.isFile || file.length() == 0L) return null
        if (charCount <= 0L) return null
        val result = Result(
            file = file,
            chunkCount = expectedSeq,
            charCount = charCount,
            sha256 = sha256Of(file),
            summary = summary,
        )
        val elapsedMs = System.currentTimeMillis() - current.startedAt
        manifestFile.writeText(
            buildString {
                appendLine("{")
                appendLine("  \"chunks\": ${result.chunkCount},")
                appendLine("  \"chars\": ${result.charCount},")
                appendLine("  \"sha256\": \"${result.sha256}\",")
                appendLine("  \"mainKeys\": ${summary?.mainKeys ?: -1},")
                appendLine("  \"legacyKeys\": ${summary?.legacyKeys ?: -1},")
                appendLine("  \"elapsedMs\": $elapsedMs,")
                appendLine("  \"completed\": true")
                appendLine("}")
            },
            Charsets.UTF_8,
        )
        lastResult = result
        session = null
        return result
    }

    /** 失败/放弃：清掉半成品，避免下次误当完整数据读取。 */
    fun abandon() {
        exportFile.delete()
        manifestFile.delete()
        session = null
        expectedSeq = 0
        charCount = 0L
    }

    /** 读回导出物（迁移器的输入）。 */
    fun readExport(): String? = exportFile.takeIf { it.isFile }?.readText(Charsets.UTF_8)

    companion object {
        const val DIR_INCOMING = "incoming"
        const val FILE_EXPORT = "legacy-export.json"
        const val FILE_MANIFEST = "manifest.json"

        /**
         * 单块字符上限。Binder 事务上限是 1 MB **字节**；块内容是 JSON（大量 ASCII + 少量中文），
         * 中文按 UTF-8 占到 3 字节/字符，故 180_000 字符最坏约 540 KB，留足余量。
         */
        const val CHUNK_CHARS = 180_000

        fun sha256Of(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
