package com.vladdev.shared.auth.dto

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

@InternalSerializationApi @Serializable
data class AuthRequest(
    val username: String,
    val password: String
)
