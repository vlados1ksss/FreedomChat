package com.vladdev.shared.crypto

// crypto/Base64Helper.kt
expect object Base64Helper {
    fun encode(bytes: ByteArray): String
    fun decode(str: String): ByteArray
}