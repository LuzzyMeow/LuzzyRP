package com.luzzymeow.luzzyrp.ui.pages.chat

import com.luzzymeow.luzzyrp.chat.llm.LlmMessage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * **一条图片附件**（C4）。
 *
 * ## `location` 的两种形态（与迁移产物同构，这是刻意的）
 *
 * | 形态 | 含义 | 来源 |
 * |---|---|---|
 * | `assets/attachments/….jpg` | **相对 `filesDir` 的文件路径**（与迁移写附件的约定逐字一致，见 `MigrationWriter.writeAssets` 的 `File(filesDir, path)`） | 新选的图（C4 落盘）与迁移时**抽取过大图**的旧数据 |
 * | `data:image/…;base64,…` | **内联 data URL** | 旧数据里小于抽取阈值的内联图（迁移原样保留） |
 *
 * 为什么不拆成两个字段：旧数据的 `imageAttachments` 里两种形态**混在同一个键**里
 * （`LegacyMigrator.extractAttachment` 只把超阈值的换成路径，小的原样留着），
 * 读的时候必须都认；拆字段反而要为「哪个是哪种」发明第二个判据。
 *
 * ## 请求侧
 *
 * 三家协议的图片都以 **OpenAI 形态**进请求（`{"type":"image_url",…}`）：
 * OpenAI 原样直通，Anthropic / Gemini 的 wire 已有翻译分支（含单测）。
 * 路径形态在**发请求前**才解析成 data URL（见 [resolveImageParts]）——
 * payload 里存路径不存 base64（几百 KB 的图不该躺进 SQLite 的 payload 列）。
 */
data class ChatAttachment(
    /** `data:` URL 或相对 `filesDir` 的文件路径（见类注释）。 */
    val location: String,
    /** `image/jpeg` 等；未知时为空串（旧数据可能没写）。 */
    val mime: String = "",
    /** 原文件名（展示用；旧数据可能没有）。 */
    val name: String? = null,
) {
    /** 是否需要发请求前解析（路径形态）；内联 data URL 可直接用。 */
    val isInline: Boolean get() = location.startsWith("data:")
}

/** payload 的 `imageAttachments` 数组 → 附件列表（宽松解析：坏数据一律丢弃该条，绝不抛）。 */
fun attachmentsOfJson(element: JsonElement?): List<ChatAttachment> {
    val array = element as? JsonArray ?: return emptyList()
    return array.mapNotNull { item ->
        val obj = item as? JsonObject ?: return@mapNotNull null
        val location = (obj["dataUrl"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        ChatAttachment(
            location = location,
            mime = (obj["mime"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            name = (obj["name"] as? JsonPrimitive)?.contentOrNull,
        )
    }
}

/** 附件列表 → payload 的 `imageAttachments` 数组（键名与旧结构逐字一致，extra 字段不再保留）。 */
fun attachmentsToJson(attachments: List<ChatAttachment>): JsonArray = buildJsonArray {
    attachments.forEach { attachment ->
        add(
            buildJsonObject {
                put("dataUrl", JsonPrimitive(attachment.location))
                put("mime", JsonPrimitive(attachment.mime))
                attachment.name?.let { put("name", JsonPrimitive(it)) }
            },
        )
    }
}

/**
 * 一条 user 消息的**请求 content**：正文在前、图片在后（OpenAI parts 形态）。
 *
 * 纯文本时返回 null（调用方走 `content` 字符串的旧路径，**请求字节与引入附件之前逐字节一致**
 * ——这对前缀缓存是硬要求：没配过图的历史消息不该因为这段代码多出一个字节）。
 */
fun userContentParts(text: String, attachments: List<ChatAttachment>): JsonArray? {
    if (attachments.isEmpty()) return null
    return buildJsonArray {
        add(
            buildJsonObject {
                put("type", JsonPrimitive("text"))
                put("text", JsonPrimitive(text))
            },
        )
        attachments.forEach { attachment ->
            add(
                buildJsonObject {
                    put("type", JsonPrimitive("image_url"))
                    put(
                        "image_url",
                        buildJsonObject { put("url", JsonPrimitive(attachment.location)) },
                    )
                },
            )
        }
    }
}

/**
 * 把 messages 里**路径形态**的图片解析成 data URL（发请求前的最后一步，IO 在调用方的调度器上）。
 *
 * - `data:` URL 原样保留；
 * - [read] 收到 payload 里的原始 `location`，返回**可直发的 data URL**
 *   （`AttachmentStore.readDataUrl` 负责：路径 → 读文件 → base64）；
 * - **读不到就抛**：把一张模型看过的图静默丢掉等于改写历史——模型上一轮看得见、这一轮看不见，
 *   那是「静默漂移」。如实报错让用户知道这轮发不出去，好过让模型凭空「失忆」。
 *
 * 只改**带待解析图片**的消息：纯文本消息逐字节不变（前缀缓存不受影响）。
 */
suspend fun resolveImageParts(
    messages: List<LlmMessage>,
    read: suspend (String) -> String,
): List<LlmMessage> = messages.map { message ->
    val array = message.rawContent as? JsonArray ?: return@map message
    var changed = false
    val resolved = JsonArray(array.map { element ->
        val obj = element as? JsonObject ?: return@map element
        val imageUrl = obj["image_url"] as? JsonObject ?: return@map element
        val url = (imageUrl["url"] as? JsonPrimitive)?.contentOrNull ?: return@map element
        if (url.startsWith("data:")) {
            element
        } else {
            changed = true
            JsonObject(obj.toMutableMap().apply {
                this["image_url"] = buildJsonObject { put("url", JsonPrimitive(read(url))) }
            })
        }
    })
    if (changed) message.copy(rawContent = resolved) else message
}
