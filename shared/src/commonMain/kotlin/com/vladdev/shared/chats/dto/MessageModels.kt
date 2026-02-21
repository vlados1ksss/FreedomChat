package com.vladdev.shared.chats.dto
import kotlinx.serialization.Serializable

@Serializable
data class MessageDto(
    val id: String,
    val chatId: String,
    val senderId: String?,
    val encryptedContent: String,
    val createdAt: Long
)

@Serializable
data class SendMessageRequest(
    val encryptedContent: String
)

@Serializable
data class SendMessageResponse(
    val messageId: String
)
