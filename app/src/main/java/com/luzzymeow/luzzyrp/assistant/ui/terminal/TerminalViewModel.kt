package com.luzzymeow.luzzyrp.assistant.ui.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luzzymeow.luzzyrp.assistant.domain.tool.HardlineGuard
import com.luzzymeow.luzzyrp.assistant.runtime.AssistantRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 终端页状态（PLAN §10.2/§10.3）。
 *
 * 当前实现 **宿主（host）模式**：`/system/bin/sh -c`，App 权限，工作目录 = 该助手工作区
 * `files/`。沙盒（proot 真 Linux）待 rootfs 就绪后接入（P3 后续）。
 *
 * **安全**：命令先过 [HardlineGuard]（无条件拦截），再由 `GlobalShellRunner` 二次拦截；
 * 输出上限 200KB，溢出落工作区并给出文件名。
 */
class TerminalViewModel(
    private val runtime: AssistantRuntime,
    private val assistantId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(TerminalState())
    val state: StateFlow<TerminalState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val sandboxStatus = runtime.prootRuntime.status()
            _state.value = _state.value.copy(
                sandboxStatus = sandboxStatus,
                banner = "LuzzyRP 终端 · 工作目录 = 工作区 files/ · 危险命令将被无条件拦截",
            )
        }
    }

    /** 切换模式（`host` / `sandbox`）。沙盒首次切换会释放 rootfs（带进度）。 */
    fun setMode(mode: String) {
        if (_state.value.running || _state.value.installing) return
        _state.value = _state.value.copy(
            mode = mode,
            modeLabel = if (mode == "sandbox") "沙盒（proot 真 Linux）" else "宿主（App 权限）",
        )
        if (mode == "sandbox") ensureSandbox()
    }

    /** 安装 / 校验沙盒（幂等）。 */
    fun ensureSandbox() {
        if (_state.value.installing) return
        viewModelScope.launch {
            _state.value = _state.value.copy(installing = true, installProgress = "检查沙盒资产…")
            val error = runtime.prootRuntime.ensureInstalled { progress ->
                _state.value = _state.value.copy(installProgress = progress)
            }
            _state.value = _state.value.copy(
                installing = false,
                installProgress = null,
                sandboxStatus = runtime.prootRuntime.status(),
                lines = _state.value.lines + if (error == null) {
                    listOf(TerminalLine.Output("沙盒已就绪（Alpine 3.20.3 / proot 5.1.107）。可执行 apk add python3。", isError = false))
                } else {
                    listOf(TerminalLine.Output("沙盒安装失败：$error", isError = true))
                },
            )
        }
    }

    fun run(command: String) {
        val text = command.trim()
        if (text.isEmpty() || _state.value.running) return
        val blocked = HardlineGuard.reasonOf(text)
        if (blocked != null) {
            _state.value = _state.value.copy(
                lines = _state.value.lines + TerminalLine.Command(text) +
                    TerminalLine.Output("已拦截：$blocked", isError = true),
            )
            return
        }
        _state.value = _state.value.copy(
            lines = _state.value.lines + TerminalLine.Command(text),
            running = true,
        )
        viewModelScope.launch {
            val runner = if (_state.value.mode == "sandbox") {
                runtime.sandboxShellFor(assistantId)
            } else {
                runtime.shellRunnerFor(assistantId)
            }
            val result = runCatching { runner.run(text, DEFAULT_TIMEOUT_MS) }
            _state.value = result.fold(
                onSuccess = { r ->
                    val suffix = r.truncatedToPath?.let { "\n（输出过长，已落盘：$it）" } ?: ""
                    _state.value.copy(
                        lines = _state.value.lines +
                            TerminalLine.Output(r.output.ifBlank { "（无输出）" } + suffix, isError = r.exitCode != 0),
                        running = false,
                        lastExitCode = r.exitCode,
                    )
                },
                onFailure = { error ->
                    _state.value.copy(
                        lines = _state.value.lines + TerminalLine.Output("执行失败：${error.message}", isError = true),
                        running = false,
                    )
                },
            )
        }
    }

    fun clear() {
        _state.value = _state.value.copy(lines = emptyList())
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 120_000L
    }
}

data class TerminalState(
    val lines: List<TerminalLine> = emptyList(),
    val running: Boolean = false,
    /** `host` | `sandbox` */
    val mode: String = "host",
    val modeLabel: String = "宿主（App 权限）",
    val banner: String = "",
    val lastExitCode: Int? = null,
    val installing: Boolean = false,
    val installProgress: String? = null,
    val sandboxStatus: String = "未安装",
)

sealed interface TerminalLine {
    data class Command(val text: String) : TerminalLine
    data class Output(val text: String, val isError: Boolean) : TerminalLine
}
