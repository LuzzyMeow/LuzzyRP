package com.luzzymeow.luzzyrp.assistant.domain.tool.builtin

import com.luzzymeow.luzzyrp.assistant.domain.tool.CodeRunner
import com.luzzymeow.luzzyrp.assistant.domain.tool.HardlineGuard
import com.luzzymeow.luzzyrp.assistant.domain.tool.Schema
import com.luzzymeow.luzzyrp.assistant.domain.tool.ShellRunner
import com.luzzymeow.luzzyrp.assistant.domain.tool.Tool
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolContext
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolResult
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolTier
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 执行类工具：`terminal_run`（PLAN §12.2）与 `run_code`（用户追加的「程序编译/执行」）。
 *
 * **安全（硬性要求 11，双层）**：
 * 1. 本层先过 [HardlineGuard]——危险命令**无条件**拦截（不受审批放行影响）；
 * 2. [ShellRunner]/[CodeRunner] 实现方**必须再跑一遍**（防绕过），并把输出截断到 200KB。
 *
 * 沙盒/宿主差异由实现吸收（PLAN §10.2）：沙盒 = proot 真 Linux（可 apk/pip/node）；
 * 宿主 = `/system/bin/sh`（App 权限，不能提权）。
 */
class TerminalRunTool(
    private val runner: ShellRunner,
    private val defaultTimeoutMs: Long = DEFAULT_TIMEOUT_MS,
) : Tool {

    override val name = "terminal_run"
    override val description =
        "执行一条 shell 命令并返回输出（一次性、非交互）。沙盒模式为真 Linux，可 apk/pip/node；" +
            "宿主模式仅 App 权限。危险命令会被无条件拒绝。"
    override val tier = ToolTier.T1_WRITE_APP
    override val parameters = Schema.objectSchema(
        properties = mapOf(
            "command" to Schema.string("要执行的命令"),
            "mode" to Schema.string("执行模式", enumValues = listOf("sandbox", "host")),
            "timeout_ms" to Schema.integer("超时毫秒，默认 $DEFAULT_TIMEOUT_MS"),
        ),
        required = listOf("command"),
    )

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val command = args.str("command") ?: return ToolResult.Error("缺少 command 参数")
        HardlineGuard.reasonOf(command)?.let { reason ->
            ctx.log("terminal_run blocked by HARDLINE: ${reason}")
            return ToolResult.Error("已拦截：$reason")
        }
        val mode = args.str("mode")
        if (mode != null && mode != runner.mode) {
            return ToolResult.Error("当前终端模式为 ${runner.mode}，不支持切换到 $mode（PLAN §10.2）")
        }
        val timeout = (args["timeout_ms"]?.jsonPrimitive?.intOrNull ?: defaultTimeoutMs.toInt())
            .coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS).toLong()
        val result = runner.run(command, timeout)
        return formatResult(result, command)
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 120_000L
        const val MIN_TIMEOUT_MS = 1_000
        const val MAX_TIMEOUT_MS = 600_000
    }
}

/** `run_code`：把代码写进沙盒临时文件再交给解释器（沙盒缺失时明确报「需沙盒」）。 */
class RunCodeTool(
    private val runner: CodeRunner,
    private val defaultTimeoutMs: Long = 60_000L,
) : Tool {

    override val name = "run_code"
    override val description =
        "执行一段代码（javascript / python）。需要沙盒环境（proot 真 Linux，含 node/python3）；" +
            "沙盒未就绪时会明确报错。"
    override val tier = ToolTier.T1_WRITE_APP
    override val parameters = Schema.objectSchema(
        properties = mapOf(
            "language" to Schema.string("语言", enumValues = listOf("javascript", "python")),
            "code" to Schema.string("代码正文"),
            "timeout_ms" to Schema.integer("超时毫秒，默认 60000"),
        ),
        required = listOf("language", "code"),
    )

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val language = args.str("language") ?: return ToolResult.Error("缺少 language 参数")
        val code = args.str("code") ?: return ToolResult.Error("缺少 code 参数")
        if (language != "javascript" && language != "python") {
            return ToolResult.Error("不支持的语言：$language（仅 javascript / python）")
        }
        // 代码本身可能包含危险 shell 片段（如 os.system("rm -rf /")），先做一次底线扫描
        HardlineGuard.reasonOf(code)?.let { reason ->
            ctx.log("run_code blocked by HARDLINE: ${reason}")
            return ToolResult.Error("已拦截：$reason")
        }
        val timeout = (args["timeout_ms"]?.jsonPrimitive?.intOrNull ?: defaultTimeoutMs.toInt())
            .coerceIn(1_000, 600_000).toLong()
        return formatResult(runner.run(language, code, timeout), "run_code($language)")
    }
}

private fun formatResult(result: com.luzzymeow.luzzyrp.assistant.domain.tool.ShellResult, label: String): ToolResult {
    val body = result.output.ifBlank { "（无输出）" }
    val suffix = result.truncatedToPath?.let { "\n\n（输出过长，已落盘：$it）" } ?: ""
    return if (result.exitCode == 0) {
        ToolResult.Ok(body + suffix)
    } else {
        ToolResult.Error("$label 退出码 ${result.exitCode}\n$body$suffix")
    }
}
