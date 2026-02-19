package com.vladdev.shared.auth

import com.vladdev.shared.network.HttpClientFactory
import com.vladdev.shared.storage.TokenStorage
import com.vladdev.shared.storage.UserIdStorage

class AuthRepository(
    private val api: AuthApi,
    private val storage: TokenStorage,
    private val uIdStorage: UserIdStorage
) {

    suspend fun register(username: String, password: String): Boolean {
        val response = api.register(username, password)

        storage.saveAccessToken(response.accessToken)
        storage.saveRefreshToken(response.refreshToken)
        uIdStorage.saveUID(response.userId)

        return true
    }

    suspend fun login(username: String, password: String): Boolean {
        val response = api.login(username, password)

        storage.saveAccessToken(response.accessToken)
        storage.saveRefreshToken(response.refreshToken)
        uIdStorage.saveUID(response.userId)

        return true
    }

    suspend fun refreshTokens(): Boolean {

        val refreshToken = storage.getRefreshToken() ?: return false

        return try {
            val response = api.refresh(refreshToken)

            storage.saveAccessToken(response.accessToken)
            storage.saveRefreshToken(response.refreshToken)

            true
        } catch (e: Exception) {
            storage.clear()
            false
        }
    }

    suspend fun isLoggedIn(): Boolean {
        return storage.getAccessToken() != null
    }
}

