package com.vladdev.shared.chats.dto

import kotlinx.serialization.InternalSerializationApi

// models/DecryptedMessage.kt

data class DecryptedMessage @OptIn(InternalSerializationApi::class) constructor(
    val id: String,
    val chatId: String,
    val senderId: String,
    val text: String?,
    val createdAt: Long,
    val deletedForAll: Boolean = false,
    val statuses: List<MessageStatusDto> = emptyList()
) {
    val isDeleted get() = deletedForAll
    val displayText get() = when {
        isDeleted -> null
        text == null -> "Ошибка расшифровки"
        else -> text
    }
}