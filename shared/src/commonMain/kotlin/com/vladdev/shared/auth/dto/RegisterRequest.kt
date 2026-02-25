package com.vladdev.shared.auth.dto

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

@InternalSerializationApi @Serializable
data class RegisterRequest(
    val username: String,
    val name: String,
    val email: String,
    val password: String
)