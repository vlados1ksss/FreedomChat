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
    val username: String,
    val name: String,
    val status: String = "standard"
)

@Serializable
data class ChatDto @OptIn(InternalSerializationApi::class) constructor(
    val chatId: String,
    val participants: List<ParticipantDto>,
    val createdAt: Long,
    val lastMessage: MessageDto? = null,
    val unreadCount: Int = 0
)

@InternalSerializationApi @Serializable
data class RequestIdResponse(
    val requestId: String
)

@InternalSerializationApi @Serializable
data class ChatIdResponse(
    val chatId: String
)

@InternalSerializationApi @Serializable
data class UserSearchResult(
    val userId: String,
    val username: String,
    val name: String,
    val status: String
)

@InternalSerializationApi @Serializable
data class SearchUserResponse(
    val user: UserSearchResult? = null,
    val existingChatId: String? = null
)
