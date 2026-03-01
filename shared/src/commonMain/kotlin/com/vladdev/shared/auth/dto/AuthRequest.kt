package com.vladdev.shared.auth.dto

import kotlinx.serialization.Serializable

@Serializable
data class AuthRequest(
    val username: String,
    val name: String,
    val email: String,
    val password: String,
    val publicKey: String,
    val verifyKey: String,
    val deviceInfo: String? = null
)