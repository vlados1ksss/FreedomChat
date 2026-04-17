package com.vladdev.shared.chats

import okhttp3.RequestBody
import okio.BufferedSink

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


class ProgressRequestBody(
    private val bytes: ByteArray,
    private val mediaType: okhttp3.MediaType?,
    private val onProgress: (Int) -> Unit   // 0..100
) : RequestBody() {

    override fun contentType() = mediaType
    override fun contentLength() = bytes.size.toLong()

    override fun writeTo(sink: BufferedSink) {
        val total     = bytes.size.toLong()
        var uploaded  = 0L
        val chunkSize = 8192

        var offset = 0
        while (offset < bytes.size) {
            val end  = minOf(offset + chunkSize, bytes.size)
            sink.write(bytes, offset, end - offset)
            uploaded += (end - offset)
            offset    = end
            val pct = ((uploaded * 100) / total).toInt()
            onProgress(pct)
        }
        sink.flush()
    }
}