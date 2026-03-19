package com.vladdev.shared.chats.dto
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
enum class MessageStatus { SENT, DELIVERED, READ }

@InternalSerializationApi @Serializable
data class MessageStatusDto(
    val messageId: String,
    val userId: String,
    val status: MessageStatus
)

@InternalSerializationApi @Serializable
data class MessageDto(
    val id: String,
    val chatId: String,
    val senderId: String,
    val encryptedContent: String,
    val createdAt: Long,
    val deletedForAll: Boolean = false,
    val statuses: List<MessageStatusDto> = emptyList(),
    val plaintextPreview: String? = null,
    val editedAt: Long? = null,
    val replyToId: String? = null,
    val replyToPreview: String? = null,
    val forwardedFromId: String? = null,
    val forwardedFromName: String? = null,
    val pinnedAt: Long? = null
)

@InternalSerializationApi @Serializable
data class WsMessageEvent(val type: String = "message", val message: MessageDto, val replyToId: String? = null, val replyToPreview: String? = null)

@InternalSerializationApi @Serializable
data class WsStatusEvent(val type: String = "status", val messageId: String, val userId: String, val status: MessageStatus)

@InternalSerializationApi @Serializable
data class WsDeleteEvent(val type: String = "delete", val messageId: String, val deleteForAll: Boolean)

@InternalSerializationApi @Serializable
data class WsEditEvent(val type: String = "edit", val messageId: String, val senderId: String, val encryptedContent: String, val editedAt: Long?)

@Serializable
data class WsForwardEvent(
    val type: String = "forward",
    val targetChatId: String,
    val messages: List<ForwardedMessagePayload>
)

@Serializable
data class ForwardedMessagePayload(
    val encryptedContent: String,
    val originalSenderId: String,
    val originalSenderName: String,
    val originalCreatedAt: Long
)

@Serializable
data class WsPinEvent(
    val type: String = "pin",
    val messageId: String,
    val unpin: Boolean = false
)

@Serializable
data class WsTypingEvent(
    val type: String = "typing",
    val chatId: String = "",
    val userId: String = "",
    val isTyping: Boolean
)

@Serializable
data class WsChatDeletedEvent(
    val type: String = "chat_deleted",
    val chatId: String
)

@Serializable
data class WsHistoryClearedEvent(
    val type: String = "history_cleared",
    val chatId: String
)

@InternalSerializationApi @Serializable
data class SendMessageRequest(
    val encryptedContent: String
)

@InternalSerializationApi @Serializable
data class SendMessageResponse(
    val messageId: String
)
