package com.vladdev.shared.auth.dto

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

@InternalSerializationApi @Serializable
data class RefreshRequest(
    val refreshToken: String
)
