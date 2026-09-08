package com.luzzymeow.luzzyrp.assistant.domain.tool.builtin

import com.luzzymeow.luzzyrp.assistant.domain.tool.Schema
import com.luzzymeow.luzzyrp.assistant.domain.tool.Tool
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolContext
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolResult
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolTier
import com.luzzymeow.luzzyrp.assistant.domain.tool.WorkspaceEntry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 工作区工具（PLAN §10.1）：`workspace_list` / `read` / `write` / `patch` / `delete` / `move` / `mkdir`。
 *
 * 路径安全由 [com.luzzymeow.luzzyrp.assistant.domain.tool.WorkspaceAccess] 实现负责
 * （越界抛异常 → 本层统一转 `ToolResult.Error` 回灌模型，不中断整轮）。
 */
private fun argsError(missing: String) = ToolResult.Error("缺少 $missing 参数")

/** 列目录。 */
class WorkspaceListTool : Tool {
    override val name = "workspace_list"
    override val description = "列出工作区某个目录下的文件与子目录（相对路径，默认根目录）。"
    override val tier = ToolTier.T1_WRITE_APP
    override val parameters = Schema.objectSchema(
        properties = mapOf("path" to Schema.string("相对目录路径，默认空串（工作区根）")),
    )

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult = runCatching {
        val path = args.str("path") ?: ""
        val entries = ctx.workspace.list(path)
        if (entries.isEmpty()) return@runCatching ToolResult.Ok("（目录为空）")
        ToolResult.Ok(entries.joinToString("\n") { formatEntry(it) })
    }.getOrElse { ToolResult.Error(it.message ?: "列目录失败") }
}

private fun formatEntry(entry: WorkspaceEntry): String =
    if (entry.isDirectory) "[目录] ${entry.relativePath}/"
    else "[文件] ${entry.relativePath} (${entry.sizeBytes} B)"

/** 读文件。 */
class WorkspaceReadTool : Tool {
    override val name = "workspace_read"
    override val description = "读取工作区内的文本文件（UTF-8）。超过单文件上限时拒绝。"
    override val tier = ToolTier.T1_WRITE_APP
    override val parameters = Schema.objectSchema(
        properties = mapOf("path" to Schema.string("相对文件路径")),
        required = listOf("path"),
    )

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult = runCatching {
        val path = args.str("path") ?: return@runCatching argsError("path")
        val bytes = ctx.workspace.read(path)
        val text = String(bytes, Charsets.UTF_8)
        // 简易二进制判定：出现 NUL 视为二进制，只回长度
        if (text.indexOf('\u0000') >= 0) {
            ToolResult.Ok("（二进制文件，${bytes.size} 字节，未展开）")
        } else {
            ToolResult.Ok(text)
        }
    }.getOrElse { ToolResult.Error(it.message ?: "读取失败") }
}

/** 写文件（覆盖）。 */
class WorkspaceWriteTool : Tool {
    override val name = "workspace_write"
    override val description = "把文本写入工作区文件（UTF-8，覆盖同名文件）。父目录不存在会自动创建。"
    override val tier = ToolTier.T1_WRITE_APP
    override val parameters = Schema.objectSchema(
        properties = mapOf(
            "path" to Schema.string("相对文件路径"),
            "content" to Schema.string("文件内容（UTF-8 文本）"),
        ),
        required = listOf("path", "content"),
    )

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult = runCatching {
        val path = args.str("path") ?: return@runCatching argsError("path")
        val content = args.str("content") ?: return@runCatching argsError("content")
        ctx.workspace.write(path, content.toByteArray(Charsets.UTF_8))
        ToolResult.Ok("已写入 $path（${content.toByteArray(Charsets.UTF_8).size} 字节）")
    }.getOrElse { ToolResult.Error(it.message ?: "写入失败") }
}

/** 就地替换（find → replace，必须唯一命中，避免误改）。 */
class WorkspacePatchTool : Tool {
    override val name = "workspace_patch"
    override val description =
        "在工作区文件内做精确替换：find 必须唯一命中，否则拒绝（防止误改）。适合小范围编辑。"
    override val tier = ToolTier.T1_WRITE_APP
    override val parameters = Schema.objectSchema(
        properties = mapOf(
            "path" to Schema.string("相对文件路径"),
            "find" to Schema.string("要被替换的原文（需唯一）"),
            "replace" to Schema.string("替换后的文本"),
        ),
        required = listOf("path", "find", "replace"),
    )

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult = runCatching {
        val path = args.str("path") ?: return@runCatching argsError("path")
        val find = args.str("find") ?: return@runCatching argsError("find")
        val replace = args.str("replace") ?: return@runCatching argsError("replace")
        val text = String(ctx.workspace.read(path), Charsets.UTF_8)
        val count = text.windowed(find.length, 1).count { it == find }
        when {
            count == 0 -> ToolResult.Error("find 未命中，文件未修改")
            count > 1 -> ToolResult.Error("find 命中 $count 处（需唯一），文件未修改")
            else -> {
                ctx.workspace.write(path, text.replace(find, replace).toByteArray(Charsets.UTF_8))
                ToolResult.Ok("已替换 1 处：$path")
            }
        }
    }.getOrElse { ToolResult.Error(it.message ?: "替换失败") }
}

/** 删除文件。 */
class WorkspaceDeleteTool : Tool {
    override val name = "workspace_delete"
    override val description = "删除工作区内的文件。"
    override val tier = ToolTier.T1_WRITE_APP
    override val parameters = Schema.objectSchema(
        properties = mapOf("path" to Schema.string("相对文件路径")),
        required = listOf("path"),
    )

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult = runCatching {
        val path = args.str("path") ?: return@runCatching argsError("path")
        ctx.workspace.delete(path)
        ToolResult.Ok("已删除 $path")
    }.getOrElse { ToolResult.Error(it.message ?: "删除失败") }
}

/** 移动/重命名。 */
class WorkspaceMoveTool : Tool {
    override val name = "workspace_move"
    override val description = "移动或重命名工作区内的文件/目录。"
    override val tier = ToolTier.T1_WRITE_APP
    override val parameters = Schema.objectSchema(
        properties = mapOf(
            "from" to Schema.string("源相对路径"),
            "to" to Schema.string("目标相对路径"),
        ),
        required = listOf("from", "to"),
    )

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult = runCatching {
        val from = args.str("from") ?: return@runCatching argsError("from")
        val to = args.str("to") ?: return@runCatching argsError("to")
        ctx.workspace.move(from, to)
        ToolResult.Ok("已移动 $from → $to")
    }.getOrElse { ToolResult.Error(it.message ?: "移动失败") }
}

/** 建目录。 */
class WorkspaceMkdirTool : Tool {
    override val name = "workspace_mkdir"
    override val description = "在工作区内创建目录（含父目录）。"
    override val tier = ToolTier.T1_WRITE_APP
    override val parameters = Schema.objectSchema(
        properties = mapOf("path" to Schema.string("相对目录路径")),
        required = listOf("path"),
    )

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult = runCatching {
        val path = args.str("path") ?: return@runCatching argsError("path")
        ctx.workspace.mkdir(path)
        ToolResult.Ok("已创建目录 $path")
    }.getOrElse { ToolResult.Error(it.message ?: "创建目录失败") }
}

/** 参数取字符串（内部小工具）。 */
internal fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotEmpty() }

/** 参数取布尔。 */
internal fun JsonObject.bool(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.booleanOrNull
