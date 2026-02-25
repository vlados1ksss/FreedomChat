package com.vladdev.shared.user.dto
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable


@InternalSerializationApi @Serializable
data class UserProfileResponse(
    val userId: String,
    val username: String,
    val name: String,
    val email: String? = null,
    val status: String,
    val createdAt: Long
)

@InternalSerializationApi @Serializable
data class UpdateNameRequest(val name: String)

@InternalSerializationApi @Serializable
data class UpdateEmailRequest(val email: String)

@InternalSerializationApi @Serializable
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

@InternalSerializationApi @Serializable
data class VerifyPasswordRequest(val password: String)

@InternalSerializationApi @Serializable
data class DeleteAccountRequest(val password: String)