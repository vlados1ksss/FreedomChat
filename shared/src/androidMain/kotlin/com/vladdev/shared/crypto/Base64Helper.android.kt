package com.vladdev.shared.crypto

// crypto/Base64Helper.android.kt
import android.util.Base64

actual object Base64Helper {
    actual fun encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    actual fun decode(str: String): ByteArray =
        Base64.decode(str, Base64.NO_WRAP)
}