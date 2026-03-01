package com.vladdev.shared.crypto.dto

import kotlinx.serialization.Serializable

@Serializable
data class EncryptedPayload(
    val ciphertext: String,     // hex
    val nonce: String,          // hex, 24 bytes
    val messageIndex: Int,      // для ratchet step
    val senderPublicKey: String // X25519 hex — чтобы получатель мог сделать DH
)