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
    val isUnavailable: Boolean = false,
    val statuses: List<MessageStatusDto> = emptyList(),
    val editedAt: Long? = null,
    val replyToId: String? = null,
    val replyToSenderId: String? = null
) {
    val isDeleted get() = deletedForAll
    val displayText get() = when {
        isDeleted -> null
        isUnavailable  -> null
        text == null -> null
        else -> text
    }
}