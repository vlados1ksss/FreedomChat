package com.vladdev.freedomchat

import android.graphics.Bitmap
import android.net.Uri
import java.util.UUID


data class MediaPendingItem(
    val localId: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val type: PendingMediaType,
    val mimeType: String,
    val sizeBytes: Long,
    val thumbBitmap: Bitmap? = null,   // превью для UI
    val durationMs: Long? = null,      // для видео
    val isOverLimit: Boolean = false
)

enum class PendingMediaType { PHOTO, VIDEO }

// Лимиты (должны совпадать с сервером)
const val PHOTO_MAX_BYTES = 5L * 1024 * 1024    // 5 MB
const val VIDEO_MAX_BYTES = 150L * 1024 * 1024  // 150 MB
const val MAX_PHOTOS      = 10
const val MAX_VIDEOS      = 2