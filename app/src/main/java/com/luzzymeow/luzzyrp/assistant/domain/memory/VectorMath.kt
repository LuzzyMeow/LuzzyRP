package com.luzzymeow.luzzyrp.assistant.domain.memory

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * 向量数学（PLAN §7.2：**纯 Kotlin 余弦暴力扫描**，零 NDK）。
 *
 * 存储格式：float32 小端 BLOB（与 `memory.embedding` 列约定一致）。
 * 万条量级扫描 <10ms（单测覆盖正确性与边界）。
 */
object VectorMath {

    /** 编码为 float32 小端字节。 */
    fun encode(vector: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(vector.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        vector.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    /** 解码；长度不是 4 的倍数时返回 null（脏数据不崩）。 */
    fun decode(bytes: ByteArray?): FloatArray? {
        if (bytes == null || bytes.isEmpty() || bytes.size % 4 != 0) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(bytes.size / 4)
        for (i in out.indices) out[i] = buffer.getFloat()
        return out
    }

    /**
     * 余弦相似度。零向量 / 维度不一致返回 0f（不抛异常，调用方按「无相似度」处理）。
     */
    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || b.isEmpty() || a.size != b.size) return 0f
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        if (na == 0f || nb == 0f) return 0f
        return (dot / (sqrt(na) * sqrt(nb))).coerceIn(-1f, 1f)
    }

    /** L2 归一化（可选：入库前归一，后续用点积代替余弦更快）。 */
    fun normalize(v: FloatArray): FloatArray {
        var sum = 0f
        for (x in v) sum += x * x
        val norm = sqrt(sum)
        if (norm == 0f) return v
        return FloatArray(v.size) { v[it] / norm }
    }
}
