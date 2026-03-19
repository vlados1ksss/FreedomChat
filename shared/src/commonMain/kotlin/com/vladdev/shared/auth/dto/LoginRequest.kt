package com.vladdev.shared.auth.dto

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

@InternalSerializationApi @Serializable
data class LoginRequest(
    val username: String,
    val password: String,
    val publicKey: String,
    val verifyKey: String,
    val deviceInfo: String? = null
)
