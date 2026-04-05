package com.vladdev.shared.chats

fun extensionFromMime(mime: String): String = when (mime) {
    "image/jpeg"       -> "jpg"
    "image/png"        -> "png"
    "image/webp"       -> "webp"
    "video/mp4"        -> "mp4"
    "video/quicktime"  -> "mov"
    "audio/ogg",
    "audio/opus"       -> "ogg"
    "audio/mpeg"       -> "mp3"
    else               -> "bin"
}

fun mediaNotificationPreview(
    encryptedContent: String,
    plaintext: String?,        // расшифрованная подпись (может быть null или zero-width space)
    mediaTypeHint: String?     // "PHOTO"|"VIDEO"|"VOICE" из FCM data, если сервер шлёт
): String {
    // Подпись без zero-width space
    val caption = plaintext?.takeIf { it.isNotBlank() && it != "\u200B" }

    return when (mediaTypeHint?.uppercase()) {
        "PHOTO"      -> if (caption != null) "📷 Фото: $caption" else "📷 Фото"
        "VIDEO"      -> if (caption != null) "🎥 Видео: $caption" else "🎥 Видео"
        "VIDEO_NOTE" -> "📹 Кружок"
        "VOICE"      -> "🎤 Голосовое сообщение"
        else         -> plaintext ?: "Новое сообщение"
    }
}