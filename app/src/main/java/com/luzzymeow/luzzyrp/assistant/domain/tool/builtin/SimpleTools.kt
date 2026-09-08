package com.luzzymeow.luzzyrp.assistant.domain.tool.builtin

import com.luzzymeow.luzzyrp.assistant.domain.tool.AskUserOption
import com.luzzymeow.luzzyrp.assistant.domain.tool.AskUserPrompt
import com.luzzymeow.luzzyrp.assistant.domain.tool.ClipboardPort
import com.luzzymeow.luzzyrp.assistant.domain.tool.ClockProvider
import com.luzzymeow.luzzyrp.assistant.domain.tool.DeviceInfoProvider
import com.luzzymeow.luzzyrp.assistant.domain.tool.Schema
import com.luzzymeow.luzzyrp.assistant.domain.tool.Tool
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolContext
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolResult
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolTier
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * 基础内置工具（PLAN §12.2）：`ask_user` / `get_time` / `get_device_info` / `clipboard_*`。
 *
 * 全部纯 Kotlin——Android 能力经 [Ports] 注入，便于单测与后续沙盒化。
 */

/** 澄清提问：暂停循环，等用户选择（PLAN §12.2 `ask_user` ★）。 */
class AskUserTool : Tool {
    override val name = "ask_user"
    override val description =
        "向用户提一个澄清问题并等待回答。当需求含糊、缺少关键参数或需要在多个方案间取舍时使用。" +
            "可给出候选选项，用户也可自由输入。"
    override val tier = ToolTier.T1_WRITE_APP
    override val parameters = Schema.objectSchema(
        properties = mapOf(
            "question" to Schema.string("要问用户的问题，一句话说清"),
            "options" to Schema.array(
                Schema.objectSchema(
                    mapOf(
                        "label" to Schema.string("选项标题"),
                        "description" to Schema.string("选项说明（可选）"),
                    ),
                    required = listOf("label"),
                ),
                "候选选项；留空则只让用户自由输入",
            ),
            "allow_multiple" to Schema.boolean("是否允许多选，默认 false"),
        ),
        required = listOf("question"),
    )

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val question = args["question"]?.jsonPrimitive?.contentOrNullSafe()
        if (question.isNullOrBlank()) return ToolResult.Error("缺少 question 参数")
        val options = args["options"]?.jsonArray.orEmpty().mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val label = obj["label"]?.jsonPrimitive?.contentOrNullSafe() ?: return@mapNotNull null
            AskUserOption(label, obj["description"]?.jsonPrimitive?.contentOrNullSafe())
        }
        val allowMultiple = args["allow_multiple"]?.jsonPrimitive?.booleanOrNull ?: false
        return ToolResult.NeedUserInput(AskUserPrompt(question, options, allowMultiple))
    }
}

/** 系统时间（PLAN §12.2 `get_time` ★）。 */
class GetTimeTool(private val clock: ClockProvider) : Tool {
    override val name = "get_time"
    override val description = "获取当前日期时间、星期与时区。用户提到「今天/现在/本周」或需要计算时间时先调用。"
    override val tier = ToolTier.T0_READ
    override val parameters = Schema.objectSchema(
        properties = mapOf(
            "timezone" to Schema.string("IANA 时区，如 Asia/Shanghai；缺省用设备时区"),
            "format" to Schema.string("输出格式：full（默认）| date | time | epoch"),
        ),
    )

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val zone = args["timezone"]?.jsonPrimitive?.contentOrNullSafe()
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: runCatching { ZoneId.of(clock.zoneId()) }.getOrDefault(ZoneId.systemDefault())
        val format = args["format"]?.jsonPrimitive?.contentOrNullSafe() ?: "full"
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(clock.nowMillis()), zone)
        val text = when (format) {
            "date" -> now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            "time" -> now.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            "epoch" -> clock.nowMillis().toString()
            else -> now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss EEEE", Locale.CHINA)) +
                " · " + zone.id
        }
        return ToolResult.Ok(text)
    }
}

/** 设备信息（PLAN §12.2 `get_device_info`）。 */
class GetDeviceInfoTool(private val provider: DeviceInfoProvider) : Tool {
    override val name = "get_device_info"
    override val description = "获取设备机型、系统版本与可用存储空间。"
    override val tier = ToolTier.T0_READ
    override val parameters = Schema.empty()

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val free = provider.freeStorageBytes()
        val freeText = if (free >= 0) "${free / 1024 / 1024} MB" else "未知"
        return ToolResult.Ok("${provider.summary()}\n可用存储：$freeText")
    }
}

/** 读剪贴板（PLAN §12.2 `clipboard_read`）。 */
class ClipboardReadTool(private val clipboard: ClipboardPort) : Tool {
    override val name = "clipboard_read"
    override val description = "读取系统剪贴板文本。"
    override val tier = ToolTier.T1_WRITE_APP
    override val parameters = Schema.empty()

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val text = clipboard.read()
        return if (text.isNullOrEmpty()) ToolResult.Ok("（剪贴板为空）") else ToolResult.Ok(text)
    }
}

/** 写剪贴板（PLAN §12.2 `clipboard_write`）。 */
class ClipboardWriteTool(private val clipboard: ClipboardPort) : Tool {
    override val name = "clipboard_write"
    override val description = "把文本写入系统剪贴板。"
    override val tier = ToolTier.T1_WRITE_APP
    override val parameters = Schema.objectSchema(
        properties = mapOf("text" to Schema.string("要写入的文本")),
        required = listOf("text"),
    )

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val text = args["text"]?.jsonPrimitive?.contentOrNullSafe()
            ?: return ToolResult.Error("缺少 text 参数")
        return if (clipboard.write(text)) ToolResult.Ok("已写入剪贴板（${text.length} 字符）")
        else ToolResult.Error("写入剪贴板失败")
    }
}

/** JsonPrimitive 取字符串的安全包装（非字符串类型返回其字面量）。 */
internal fun JsonPrimitive.contentOrNullSafe(): String? =
    when (this) {
        is kotlinx.serialization.json.JsonNull -> null
        else -> content
    }
