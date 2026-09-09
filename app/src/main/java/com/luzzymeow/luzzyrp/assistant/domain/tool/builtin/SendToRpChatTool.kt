package com.luzzymeow.luzzyrp.assistant.domain.tool.builtin

import com.luzzymeow.luzzyrp.assistant.domain.tool.Schema
import com.luzzymeow.luzzyrp.assistant.domain.tool.Tool
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolContext
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolResult
import com.luzzymeow.luzzyrp.assistant.domain.tool.ToolTier
import kotlinx.serialization.json.JsonObject

/**
 * 把内容回填到 RP-Hub 会话（PLAN §12.2 **预留**项，T2：默认关闭 + 逐调用审批）。
 *
 * 用户已拍板「助手与 RP 会话**不自动同步**」，因此本工具：
 * - 默认**关闭**（T2），需用户在设置里显式开启；
 * - 必须由模型显式调用（不会自动触发）；
 * - 通过 [RpChatPort] 端口落到 WebView 侧（`LuzzyBridge`），未接线时返回明确「未启用」。
 */
fun interface RpChatPort {
    /** 把文本写入指定 RP 会话；未接线实现返回 false。 */
    suspend fun send(conversationId: String, text: String): Boolean

    companion object {
        /** 未接线（默认）：任何调用都返回 false，工具层给出可操作提示。 */
        val UNAVAILABLE: RpChatPort = RpChatPort { _, _ -> false }
    }
}

class SendToRpChatTool(private val port: RpChatPort = RpChatPort.UNAVAILABLE) : Tool {

    override val name = "send_to_rp_chat"
    override val description =
        "把一段文本回填到 RP-Hub 的角色扮演会话。仅在用户明确要求「发到 RP 会话」时调用；" +
            "助手与 RP 会话默认不自动同步。"
    override val tier = ToolTier.T2_WRITE_DEVICE
    override val parameters = Schema.objectSchema(
        properties = mapOf(
            "conversationId" to Schema.string("目标 RP 会话 id（留空表示当前会话）"),
            "text" to Schema.string("要写入的文本"),
        ),
        required = listOf("text"),
    )

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val text = args.str("text") ?: return ToolResult.Error("缺少 text 参数")
        val conversationId = args.str("conversationId").orEmpty()
        val ok = runCatching { port.send(conversationId, text) }.getOrDefault(false)
        return if (ok) {
            ToolResult.Ok("已写入 RP 会话（${text.length} 字符）")
        } else {
            ToolResult.Error(
                "未启用：RP 会话回填需要显式接线（本版为预留功能，默认关闭；" +
                    "助手与 RP 会话不自动同步）。"
            )
        }
    }
}
