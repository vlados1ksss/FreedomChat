package com.vladdev.shared.auth.dto

import kotlinx.serialization.Serializable

@Serializable
data class PublicKeyResponse(
    val userId: String,
    val publicKey: String
)