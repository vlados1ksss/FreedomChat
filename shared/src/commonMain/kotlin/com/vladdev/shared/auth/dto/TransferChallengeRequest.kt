package com.vladdev.shared.auth.dto

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

@InternalSerializationApi @Serializable
data class TransferRequest(
    val userId: String,
    val challenge: String,
    val signature: String,
    val newPublicKey: String,
    val newVerifyKey: String,
    val deviceInfo: String? = null
)