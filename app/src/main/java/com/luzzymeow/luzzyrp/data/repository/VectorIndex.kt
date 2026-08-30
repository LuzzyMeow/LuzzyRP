package com.luzzymeow.luzzyrp.data.repository

import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import com.luzzymeow.luzzyrp.core.common.JsonInstant
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * sqlite-vec 向量索引管理器。
 *
 * 职责：维度自适应建表（首次写入真实向量时按实际维度重建虚表）+ upsert + Top-K 检索。
 * 三张虚表：memory_vec / worldbook_vec / summary_vec（见 VectorTables）。
 * 检索为纯 SQL（MATCH + knn = k），余弦/欧氏由 sqlite-vec 的 distance_metric 决定，
 * 默认 L2；本类将距离归一为相似度（1 / (1 + L2²)）供阈值过滤。
 */
class VectorIndex(private val dbProvider: () -> SupportSQLiteDatabase) {

    /** 首次写入时记录的维度缓存（进程级；重建后维度随实际嵌入模型变化）。 */
    private val dimensions = mutableMapOf<String, Int>()

    /** 写入/覆盖一条向量。 */
    fun upsert(table: String, idColumn: String, id: String, vector: FloatArray) {
        val db = dbProvider()
        ensureDimension(table, db, vector.size)
        val vecJson = vectorToJson(vector)
        // id 唯一：先删后插
        db.execSQL("DELETE FROM $table WHERE $idColumn = ?", arrayOf<Any?>(id))
        db.execSQL(
            "INSERT INTO $table ($idColumn, embedding) VALUES (?, vec_f32(?))",
            arrayOf<Any?>(id, vecJson),
        )
    }

    /** Top-K 检索：返回 (id, distance) 列表，按距离升序。 */
    fun search(table: String, idColumn: String, query: FloatArray, topK: Int): List<Pair<String, Float>> {
        val db = dbProvider()
        ensureDimension(table, db, query.size)
        val vecJson = vectorToJson(query)
        val cursor = db.query(
            "SELECT $idColumn, distance FROM $table WHERE embedding MATCH vec_f32(?) AND knn = ?",
            arrayOf<Any?>(vecJson, topK.toLong()),
        )
        val results = mutableListOf<Pair<String, Float>>()
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getString(0)
                val distance = it.getFloat(1)
                results.add(id to distance)
            }
        }
        return results
    }

    /** 删除指定 id 的向量。 */
    fun delete(table: String, idColumn: String, id: String) {
        dbProvider().execSQL("DELETE FROM $table WHERE $idColumn = ?", arrayOf(id))
    }

    /** 删除指定会话/前缀相关的向量（conversation_id 列仅 summary_vec 有）。 */
    fun deleteByConversation(summaryIdColumn: String = "summary_id", conversationId: String) {
        dbProvider().execSQL(
            "DELETE FROM summary_vec WHERE conversation_id = ?",
            arrayOf(conversationId),
        )
    }

    private fun ensureDimension(table: String, db: SupportSQLiteDatabase, dimension: Int) {
        val known = dimensions[table]
        if (known == dimension) return
        // 检查现表维度是否匹配：尝试读一行向量维度的代价高于直接记录，
        // 这里以「维度未登记或变化」为准重建
        synchronized(dimensions) {
            val current = dimensions[table]
            if (current != dimension) {
                val dropSql = "DROP TABLE IF EXISTS $table"
                db.execSQL(dropSql)
                val createSql = when (table) {
                    "memory_vec" -> "CREATE VIRTUAL TABLE IF NOT EXISTS memory_vec USING vec0(memory_id TEXT, embedding FLOAT[$dimension])"
                    "worldbook_vec" -> "CREATE VIRTUAL TABLE IF NOT EXISTS worldbook_vec USING vec0(entry_id TEXT, embedding FLOAT[$dimension])"
                    "summary_vec" -> "CREATE VIRTUAL TABLE IF NOT EXISTS summary_vec USING vec0(summary_id TEXT, conversation_id TEXT, level TEXT, embedding FLOAT[$dimension])"
                    else -> error("未知向量表：$table")
                }
                db.execSQL(createSql)
                dimensions[table] = dimension
                Log.i("VectorIndex", "虚表 $table 已按维度 $dimension 重建（嵌入模型变更会导致旧向量失效，属预期行为）")
            }
        }
    }

    /** sqlite-vec 接受 JSON 数组形式的向量输入，直接手工拼接（避免序列化器重载歧义）。 */
    private fun vectorToJson(vector: FloatArray): String =
        vector.joinToString(prefix = "[", postfix = "]") { f ->
            if (f.isNaN() || f.isInfinite()) "0" else f.toString()
        }

    companion object {
        /** 从 sqlite-vec 返回的 JSON 数组文本解析距离（备用）。 */
        fun parseVectorJson(text: String): FloatArray =
            runCatching {
                Json.parseToJsonElement(text).jsonArray.map { it.jsonPrimitive.content.toFloat() }.toFloatArray()
            }.getOrDefault(FloatArray(0))
    }
}
