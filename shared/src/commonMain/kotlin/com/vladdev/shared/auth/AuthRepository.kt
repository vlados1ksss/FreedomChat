package com.vladdev.shared.auth

import com.vladdev.shared.auth.dto.AuthResponse
import com.vladdev.shared.auth.dto.RefreshResult
import com.vladdev.shared.network.HttpClientFactory
import com.vladdev.shared.storage.TokenStorage
import com.vladdev.shared.storage.UserIdStorage
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.InternalSerializationApi
import kotlin.io.encoding.Base64

class AuthRepository(
    private val api: AuthApi,
    private val storage: TokenStorage,
    private val uIdStorage: UserIdStorage
) {
    var onSessionCreated: (() -> Unit)? = null
    var onSessionDestroyed: (() -> Unit)? = null

    private val _sessionExpiredFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpiredFlow: SharedFlow<Unit> = _sessionExpiredFlow


    suspend fun logout() {
        storage.clear()
        uIdStorage.clear()
        onSessionDestroyed?.invoke()
    }

    private val refreshMutex = Mutex()

    @OptIn(InternalSerializationApi::class)
    suspend fun register(username: String, name: String, email: String, password: String): Result<Unit> =
        runCatching {
            val response = api.register(username, name, email, password)
            saveSession(response)
            onSessionCreated?.invoke()
        }

    @OptIn(InternalSerializationApi::class)
    suspend fun login(username: String, password: String): Result<Unit> =
        runCatching {
            val response = api.login(username, password)
            saveSession(response)
            onSessionCreated?.invoke()
        }

    @OptIn(InternalSerializationApi::class)
    suspend fun refreshTokens(): Boolean = refreshMutex.withLock {
        val refreshToken = storage.getRefreshToken() ?: return false

        return try {
            val response = api.refresh(refreshToken)
            saveSession(response)
            true
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.Unauthorized ||
                e.response.status == HttpStatusCode.Forbidden) {
                storage.clear()
                uIdStorage.clear()
                onSessionDestroyed?.invoke()
                _sessionExpiredFlow.tryEmit(Unit) // уведомляем UI
            }
            false
        } catch (e: Exception) {
            println("Refresh failed (network?): ${e.message}")
            false
        }
    }

    @OptIn(InternalSerializationApi::class)
    suspend fun refreshTokensResult(): RefreshResult = refreshMutex.withLock {
        val refreshToken = storage.getRefreshToken() ?: return RefreshResult.Unauthorized

        return try {
            val response = api.refresh(refreshToken)
            saveSession(response)
            RefreshResult.Success
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.Unauthorized ||
                e.response.status == HttpStatusCode.Forbidden) {
                storage.clear()
                uIdStorage.clear()
                onSessionDestroyed?.invoke()
                _sessionExpiredFlow.tryEmit(Unit)
                RefreshResult.Unauthorized
            } else {
                RefreshResult.NetworkError
            }
        } catch (e: Exception) {
            // IOException, ConnectException, таймаут — сервер недоступен
            println("Refresh network error: ${e.message}")
            RefreshResult.NetworkError
        }
    }

    suspend fun isLoggedIn(): Boolean = storage.getRefreshToken() != null

    @OptIn(InternalSerializationApi::class)
    private suspend fun saveSession(response: AuthResponse) {
        storage.saveAccessToken(response.accessToken)
        storage.saveRefreshToken(response.refreshToken)
        uIdStorage.saveUID(response.userId)
    }
}

