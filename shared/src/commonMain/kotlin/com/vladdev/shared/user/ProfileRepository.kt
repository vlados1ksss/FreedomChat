package com.vladdev.shared.user

import com.vladdev.shared.auth.AuthRepository
import com.vladdev.shared.user.dto.UserProfileResponse
import kotlinx.serialization.InternalSerializationApi

class ProfileRepository(
    private val api: ProfileApi,
    private val authRepository: AuthRepository
) {
    @OptIn(InternalSerializationApi::class)
    suspend fun getProfile(): Result<UserProfileResponse> = runCatching { api.getProfile() }

    suspend fun updateName(name: String): Result<Unit> = runCatching { api.updateName(name) }

    suspend fun updateEmail(email: String): Result<Unit> = runCatching { api.updateEmail(email) }

    suspend fun verifyPassword(password: String): Boolean = runCatching {
        api.verifyPassword(password)
    }.getOrDefault(false)

    suspend fun changePassword(current: String, new: String): Result<Unit> = runCatching {
        api.changePassword(current, new)
    }

    suspend fun deleteAccount(password: String): Result<Unit> = runCatching {
        api.deleteAccount(password)
    }

    suspend fun logout() = authRepository.logout()
}