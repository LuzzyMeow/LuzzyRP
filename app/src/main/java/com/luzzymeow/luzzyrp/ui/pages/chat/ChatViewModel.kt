package com.luzzymeow.luzzyrp.ui.pages.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luzzymeow.luzzyrp.core.model.Conversation
import com.luzzymeow.luzzyrp.core.model.Role
import com.luzzymeow.luzzyrp.core.model.UIMessage
import com.luzzymeow.luzzyrp.core.model.UIMessagePart
import com.luzzymeow.luzzyrp.data.datastore.Settings
import com.luzzymeow.luzzyrp.data.datastore.SettingsStore
import com.luzzymeow.luzzyrp.data.repository.ConversationRepository
import com.luzzymeow.luzzyrp.service.ChatService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 聊天页 ViewModel。
 *
 * [INVARIANT-STREAMING] conversation 直接来自 ChatService 的单一真源
 * StateFlow —— UI 用 collectAsStateWithLifecycle 渲染，1 字 = 1 次更新（S5）。
 * VM 不缓冲、不合并、不改写任何模型增量。
 */
class ChatViewModel(
    private val conversationId: String,
    private val chatService: ChatService,
    private val conversationRepository: ConversationRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    val conversation: StateFlow<Conversation?> =
        chatService.observeConversation(conversationId)

    val settings: StateFlow<Settings?> =
        settingsStore.settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** 正在生成（输入栏切换为 Stop）。 */
    val generating: StateFlow<Boolean> = chatService.generatingIds
        .map { it.contains(conversationId) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 生成错误（错误卡片展示）。 */
    val errors: StateFlow<List<ChatService.ChatError>> = chatService.errors

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        chatService.sendMessage(conversationId, text.trim())
    }

    fun stop() = chatService.stopGenerating(conversationId)

    fun regenerate() = chatService.regenerateAtLast(conversationId)

    fun approveTool(toolCallId: String) =
        chatService.handleToolApproval(conversationId, toolCallId, approved = true)

    fun denyTool(toolCallId: String, reason: String = "用户拒绝") =
        chatService.handleToolApproval(conversationId, toolCallId, approved = false, reason = reason)

    fun dismissError(error: ChatService.ChatError) = chatService.dismissError(error)

    fun bindCard(cardId: String?) {
        viewModelScope.launch {
            val conversation = conversation.value ?: return@launch
            conversationRepository.saveConversation(conversation.copy(cardId = cardId))
        }
    }
}

/** 消息渲染条目：把一条消息拆为「思考区（Reasoning）/工具区（Tool）/正文区（Text）」。 */
data class MessageVisual(
    val message: UIMessage,
    val reasoningParts: List<UIMessagePart.Reasoning>,
    val toolParts: List<UIMessagePart.Tool>,
    val textContent: String,
    val isUser: Boolean,
) {
    companion object {
        fun from(message: UIMessage): MessageVisual = MessageVisual(
            message = message,
            reasoningParts = message.parts.filterIsInstance<UIMessagePart.Reasoning>(),
            toolParts = message.parts.filterIsInstance<UIMessagePart.Tool>(),
            textContent = message.textContent(),
            isUser = message.role == Role.USER,
        )
    }
}
