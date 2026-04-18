package com.vladdev.shared.crypto.dto

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

@InternalSerializationApi @Serializable
data class EncryptedMessage(
    val ciphertext: String,   // hex
    val nonce: String         // hex, 24 bytes for XChaCha20
)