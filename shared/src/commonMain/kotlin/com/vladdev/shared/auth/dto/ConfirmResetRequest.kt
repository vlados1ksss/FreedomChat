package com.vladdev.shared.auth.dto

import kotlinx.serialization.Serializable

@Serializable
data class ConfirmResetRequest(
    val username: String,
    val email: String,
    val code: String,
    val newPassword: String
)