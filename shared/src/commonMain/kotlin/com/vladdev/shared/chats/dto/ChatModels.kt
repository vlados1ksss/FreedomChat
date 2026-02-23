package com.vladdev.shared.chats.dto
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

@InternalSerializationApi @Serializable
data class ChatRequestDto(
    val id: String,
    val fromUserId: String,
    val fromUsername: String,
    val createdAt: Long
)

@InternalSerializationApi @Serializable
data class ParticipantDto(
    val userId: String,
    val username: String
)

@InternalSerializationApi @Serializable
data class ChatDto(
    val chatId: String,
    val participants: List<ParticipantDto>,
    val createdAt: Long
)

@InternalSerializationApi @Serializable
data class RequestIdResponse(
    val requestId: String
)

@InternalSerializationApi @Serializable
data class ChatIdResponse(
    val chatId: String
)
