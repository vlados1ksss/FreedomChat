package com.vladdev.shared.auth.dto

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

@InternalSerializationApi @Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val userId: String
)