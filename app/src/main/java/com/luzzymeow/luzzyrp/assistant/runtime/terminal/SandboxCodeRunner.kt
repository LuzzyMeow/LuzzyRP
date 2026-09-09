package com.luzzymeow.luzzyrp.assistant.runtime.terminal

import com.luzzymeow.luzzyrp.assistant.domain.tool.CodeRunner
import com.luzzymeow.luzzyrp.assistant.domain.tool.ShellResult
import java.io.File

/**
 * 沙盒代码执行（`run_code` 工具，PLAN §12.2）。
 *
 * 把代码写入工作区 `scripts/` 下的临时文件，再在 proot 沙盒里用解释器执行：
 * - `python` → `python3 script.py`
 * - `javascript` → `node script.js`
 *
 * 解释器**未安装**时（Alpine 最小 rootfs 默认没有 node/python3）返回明确提示，
 * 引导用户先 `apk add python3`（PLAN §10.2 的体积决策：重包按需装）。
 */
class SandboxCodeRunner(
    private val proot: ProotRuntime,
    /** 该助手的工作区 `files/` 目录（bind 到容器内 `/workspace`）。 */
    private val workspace: File,
) : CodeRunner {

    override suspend fun run(language: String, code: String, timeoutMs: Long): ShellResult {
        val scriptsDir = File(workspace, "scripts").apply { mkdirs() }
        val (fileName, interpreter, probe) = when (language) {
            "python" -> Triple("script-${System.currentTimeMillis()}.py", "python3", "command -v python3")
            "javascript" -> Triple("script-${System.currentTimeMillis()}.js", "node", "command -v node")
            else -> return ShellResult(-1, "不支持的语言：$language（仅 python / javascript）")
        }
        val file = File(scriptsDir, fileName)
        file.writeText(code, Charsets.UTF_8)

        val check = proot.runInWorkspace(workspace, "$probe >/dev/null 2>&1 || echo __MISSING__", 30_000)
        if (check.output.contains("__MISSING__")) {
            val packageName = if (language == "python") "python3" else "nodejs"
            return ShellResult(
                -1,
                "沙盒内未安装 $interpreter。请先在「终端」页执行：apk add $packageName\n" +
                    "（Alpine 最小 rootfs 未预装解释器，见 PLAN §10.2 体积决策）",
            )
        }
        return proot.runInWorkspace(workspace, "$interpreter scripts/$fileName", timeoutMs)
    }
}
