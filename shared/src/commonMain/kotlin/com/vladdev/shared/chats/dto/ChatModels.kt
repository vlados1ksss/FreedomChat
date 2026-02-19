package com.vladdev.shared.chats.dto
import kotlinx.serialization.Serializable

@Serializable
data class ChatRequestDto(
    val id: String,
    val fromUserId: String,
    val fromUsername: String,
    val createdAt: Long
)

@Serializable
data class ParticipantDto(
    val userId: String,
    val username: String
)

@Serializable
data class ChatDto(
    val chatId: String,
    val participants: List<ParticipantDto>,
    val createdAt: Long
)

@Serializable
data class RequestIdResponse(
    val requestId: String
)

@Serializable
data class ChatIdResponse(
    val chatId: String
)
