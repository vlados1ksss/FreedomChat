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
    val deletedForAll: Boolean         = false,
    val statuses: List<MessageStatusDto> = emptyList(),
    val editedAt: Long?                = null,
    val replyToId: String?             = null,
    val replyToPreview: String?        = null,   // только локально, @Transient не нужен — сервер просто игнорирует
    val plaintextPreview: String?      = null,   // локальный кэш, сервер не шлёт
    val forwardedFromId: String?       = null,
    val forwardedFromName: String?     = null,
    val pinnedAt: Long?                = null,
    val reactions: List<ReactionDto>   = emptyList(),
    val media: MediaDto?               = null    // НОВОЕ: null = текстовое сообщение
)

@Serializable
data class ReactionDto(
    val emoji: String,
    val userId: String,
    val createdAt: Long
)

@Serializable
data class MediaDto(
    val id: String,
    val type: String,              // "PHOTO" | "VIDEO" | "VOICE" | "VIDEO_NOTE"
    val mimeType: String,
    val sizeBytes: Long,
    val durationSec: Int?  = null,
    val width: Int?        = null,
    val height: Int?       = null,
    val thumbUrl: String?  = null, // относительный путь, клиент сам строит полный URL
    val waveform: List<Float>? = null,
    val createdAt: Long
)

@Serializable
data class MediaUploadResponse(
    val mediaId: String,
    val status: String,
    val thumbUrl: String? = null
)

@InternalSerializationApi
@Serializable
data class WsMessageEvent(
    val type: String       = "message",
    val message: MessageDto,
    val replyToId: String? = null,
    val replyToPreview: String? = null,
    val mediaId: String?   = null
)

@InternalSerializationApi @Serializable
data class WsStatusEvent(val type: String = "status", val messageId: String, val userId: String, val status: MessageStatus)

@InternalSerializationApi @Serializable
data class WsDeleteEvent(val type: String = "delete", val messageId: String, val deleteForAll: Boolean)

@InternalSerializationApi @Serializable
data class WsEditEvent(val type: String = "edit", val messageId: String, val senderId: String, val encryptedContent: String, val editedAt: Long?)

@Serializable
data class WsReactionEvent(
    val type: String = "reaction",
    val messageId: String,
    val emoji: String,
    val userId: String,
    val remove: Boolean = false,
    val createdAt: Long = 0L,
    val replacedEmoji: String? = null
)

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
