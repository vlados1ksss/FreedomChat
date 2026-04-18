package com.vladdev.shared.chats.dto

import kotlinx.serialization.InternalSerializationApi

// models/DecryptedMessage.kt

data class DecryptedMessage @OptIn(InternalSerializationApi::class) constructor(
    val id: String,
    val chatId: String,
    val senderId: String,
    val text: String?,
    val createdAt: Long,
    val deletedForAll: Boolean          = false,
    val isUnavailable: Boolean          = false,
    val statuses: List<MessageStatusDto> = emptyList(),
    val editedAt: Long?                 = null,
    val replyToId: String?              = null,
    val replyToSenderId: String?        = null,
    val forwardedFromId: String?        = null,
    val forwardedFromName: String?      = null,
    val pinnedAt: Long?                 = null,
    val reactions: List<ReactionDto>    = emptyList(),
    // НОВОЕ
    val media: MediaDto?                = null,
    val mediaGroupId: String? = null,
    // Локальное состояние загрузки/скачивания медиа (не сериализуется)
    val mediaLocalPath: String? = null,
    val mediaThumbPath: String? = null,   // ← локальный путь к превью
    val mediaState: MediaState = MediaState.IDLE,
) {
    val isDeleted     get() = deletedForAll
    val hasMedia      get() = media != null
    val isMediaOnly   get() = media != null && text.isNullOrBlank()
    val displayText   get() = when {
        isDeleted     -> null
        isUnavailable -> null
        else          -> text
    }
}

enum class MediaState {
    IDLE,         // не скачивалось
    DOWNLOADING,  // скачивается прямо сейчас
    DECRYPTING,   // дешифруется
    READY,        // файл на диске, готов к показу
    ERROR         // ошибка загрузки
}