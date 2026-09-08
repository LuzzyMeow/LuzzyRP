package com.luzzymeow.luzzyrp.assistant.domain.tool

/**
 * HARDLINE 底线拦截（PLAN §13.1 第 3 层，硬性要求 11）。
 *
 * **无条件拦截**——不受审批策略影响：用户点了「本会话始终允许」也不能放行。
 * 命中即拒绝并把原因回灌模型（让模型知道这条路走不通，而不是静默失败）。
 *
 * 设计原则：
 * - **保守优先**：宁可误拦（用户可改用其他命令），不可漏放；
 * - **归一化后匹配**：去多余空白、折叠大小写、识别 `;` / `&&` / `|` 串联后的每一段；
 * - 纯 Kotlin、无 Android 依赖，可单测。
 *
 * 覆盖（PLAN §13.1）：`rm -rf /`、`dd if=/dev/…`、`mkfs`、`reboot`、`pm uninstall`、
 * `su`、`curl|sh`、覆盖系统目录、修改其他应用数据。
 */
object HardlineGuard {

    /** 拦截原因（null = 放行）。 */
    fun reasonOf(command: String): String? {
        val raw = command.trim()
        if (raw.isEmpty()) return null
        val normalized = raw.replace(Regex("\\s+"), " ").lowercase()

        // 1) 递归强删根 / 系统目录 / 通配
        if (Regex("\\brm\\s+(-[a-z]*[rf][a-z]*\\s+)+(/|/\\*|/system|/data|/sdcard|/storage|~|\\*)").containsMatchIn(normalized)) {
            return "禁止递归强制删除根/系统目录（rm -rf /）"
        }
        // 2) 裸写块设备
        if (Regex("\\bdd\\s+[^|;&]*\\bof\\s*=\\s*/dev/").containsMatchIn(normalized)) {
            return "禁止向块设备裸写（dd of=/dev/*）"
        }
        // 3) 格式化 / 分区
        if (Regex("\\bmkfs(\\.\\w+)?\\b").containsMatchIn(normalized) ||
            Regex("\\bmkswap\\b").containsMatchIn(normalized) ||
            Regex("\\bfdisk\\b|\\bparted\\b").containsMatchIn(normalized)
        ) {
            return "禁止格式化/分区操作（mkfs/fdisk）"
        }
        // 4) 重启 / 关机 / 恢复模式
        if (Regex("\\b(reboot|shutdown|poweroff|halt)\\b").containsMatchIn(normalized) ||
            Regex("\\bam\\s+reboot").containsMatchIn(normalized)
        ) {
            return "禁止重启/关机指令"
        }
        // 5) 卸载 / 停用应用（含自身）
        if (Regex("\\bpm\\s+(uninstall|disable|clear|force-stop)").containsMatchIn(normalized)) {
            return "禁止通过 pm 卸载/停用/清空应用数据"
        }
        // 6) 提权
        if (Regex("(^|[;&|(\\s])su(\\s|$|[;&|)])").containsMatchIn(normalized)) {
            return "禁止提权（su）"
        }
        // 7) 管道执行远程脚本
        if (Regex("(curl|wget)[^|;&]*(\\||\\|\\|)\\s*(sudo\\s+)?(sh|bash|zsh|python3?|node)\\b").containsMatchIn(normalized) ||
            Regex("(curl|wget)[^|;&]*-o\\s*-\\s*\\|").containsMatchIn(normalized)
        ) {
            return "禁止管道执行远程脚本（curl|sh）"
        }
        // 8) 覆盖系统目录 / 其他应用数据
        if (Regex(">\\s*/(system|system_ext|vendor|product|data/data|data/system)(/|\\s|$)").containsMatchIn(normalized)) {
            return "禁止写入系统目录"
        }
        if (Regex("\\bchmod\\s+(-[a-z]+\\s+)*(777|666)\\s+/(system|data/data|data/system)").containsMatchIn(normalized)) {
            return "禁止放开系统目录权限"
        }
        // 9) 其他高危：抹除设备 / 篡改 hosts / 关闭安全机制
        if (Regex("\\bfastboot\\b|\\bflash\\b|\\bsetprop\\b.*\\b(ro\\.secure|ro\\.debuggable)").containsMatchIn(normalized)) {
            return "禁止刷机/篡改系统属性"
        }
        return null
    }

    /** 便捷判定。 */
    fun isBlocked(command: String): Boolean = reasonOf(command) != null
}
