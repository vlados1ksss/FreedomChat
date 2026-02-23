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
    val statuses: List<MessageStatusDto> = emptyList()
)

@InternalSerializationApi @Serializable
data class WsMessageEvent(val type: String = "message", val message: MessageDto)

@InternalSerializationApi @Serializable
data class WsStatusEvent(val type: String = "status", val messageId: String, val userId: String, val status: MessageStatus)

@InternalSerializationApi @Serializable
data class WsDeleteEvent(val type: String = "delete", val messageId: String, val deleteForAll: Boolean)

@InternalSerializationApi @Serializable
data class SendMessageRequest(
    val encryptedContent: String
)

@InternalSerializationApi @Serializable
data class SendMessageResponse(
    val messageId: String
)
