package com.vladdev.shared.chats

import com.vladdev.shared.chats.dto.MessageDto
import com.vladdev.shared.chats.dto.MessageStatus
import kotlinx.serialization.InternalSerializationApi

// com/vladdev/shared/chats/WsEvents.kt

sealed class WsIncomingEvent
data class IncomingMessage @OptIn(InternalSerializationApi::class) constructor(val message: MessageDto) : WsIncomingEvent()
data class IncomingStatus(val messageId: String, val userId: String, val status: MessageStatus) : WsIncomingEvent()
data class IncomingDelete(val messageId: String, val deleteForAll: Boolean) : WsIncomingEvent()
data class IncomingEdit(
    val messageId: String,
    val senderId: String,
    val encryptedContent: String,
    val editedAt: Long?
) : WsIncomingEvent()

data class IncomingPin(
    val messageId: String,
    val unpin: Boolean
) : WsIncomingEvent()
data class IncomingTyping(
    val userId: String,
    val isTyping: Boolean
) : WsIncomingEvent()
// WsIncomingEvent:
data class IncomingChatDeleted(val chatId: String) : WsIncomingEvent()
data class IncomingHistoryCleared(val chatId: String) : WsIncomingEvent()