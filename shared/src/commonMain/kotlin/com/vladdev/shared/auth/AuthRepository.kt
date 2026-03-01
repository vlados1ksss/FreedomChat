package com.vladdev.shared.auth

import com.vladdev.shared.auth.dto.AuthResponse
import com.vladdev.shared.auth.dto.RefreshResult
import com.vladdev.shared.auth.dto.TransferChallengeResponse
import com.vladdev.shared.auth.dto.TransferRequest
import com.vladdev.shared.crypto.CryptoManager
import com.vladdev.shared.crypto.E2eeManager
import com.vladdev.shared.network.HttpClientFactory
import com.vladdev.shared.storage.IdentityKeyStorage
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

// AuthRepository.kt
sealed class LoginResult {
    object SameDevice : LoginResult()
    object NewDevice : LoginResult()
}
@OptIn(InternalSerializationApi::class)
class AuthRepository(
    private val api: AuthApi,
    private val storage: TokenStorage,
    private val uIdStorage: UserIdStorage,
    private val identityStorage: IdentityKeyStorage,
    private val crypto: CryptoManager
) {
    var onSessionDestroyed: (() -> Unit)? = null

    private val _sessionExpiredFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpiredFlow: SharedFlow<Unit> = _sessionExpiredFlow

    private val refreshMutex = Mutex()
    var onSessionCreated: ((isNewDevice: Boolean) -> Unit)? = null
    suspend fun register(
        username: String,
        name: String,
        email: String,
        password: String,
        deviceInfo: String? = null
    ): Result<Unit> = runCatching {
        val (pubKey, privKey)   = crypto.generateIdentityKeyPair()
        val (verifyKey, signKey) = crypto.generateSigningKeyPair()

        val response = api.register(
            username  = username, name = name, email = email, password = password,
            publicKey = pubKey,
            verifyKey = verifyKey,   // ← отправляем verify key на сервер
            deviceInfo = deviceInfo
        )
        identityStorage.saveIdentityKeyPair(pubKey, privKey)
        identityStorage.saveSigningKeyPair(verifyKey, signKey)
        saveSession(response)
        onSessionCreated?.invoke(true)

    }

    // AuthRepository.kt

    suspend fun login(
        username: String,
        password: String,
        deviceInfo: String? = null
    ): Result<LoginResult> = runCatching {
        val existingPubKey  = identityStorage.getPublicKey()
        val existingVerifyKey = identityStorage.getVerifyKey()

        val (pubKey, privKey) = if (existingPubKey != null)
            existingPubKey to identityStorage.getPrivateKey()!!
        else crypto.generateIdentityKeyPair()

        val (verifyKey, signKey) = if (existingVerifyKey != null)
            existingVerifyKey to identityStorage.getSignKey()!!
        else crypto.generateSigningKeyPair()

        val response = api.login(
            username  = username, password = password,
            publicKey = pubKey,
            verifyKey = verifyKey,   // ← отправляем verify key на сервер
            deviceInfo = deviceInfo
        )

        if (existingPubKey == null) {
            identityStorage.saveIdentityKeyPair(pubKey, privKey)
            identityStorage.saveSigningKeyPair(verifyKey, signKey)
        }

        saveSession(response)
        val isNewDevice = existingPubKey == null
        onSessionCreated?.invoke(isNewDevice)
        if (isNewDevice) LoginResult.NewDevice else LoginResult.SameDevice
    }

    suspend fun completeTransfer(
        userId: String,
        challenge: String,
        oldSignKeyHex: String,
        deviceInfo: String? = null
    ): Result<Unit> = runCatching {
        println("completeTransfer: userId=$userId challenge=${challenge.take(8)}")
        println("completeTransfer: signing challenge...")

        val (newPubKey, newPrivKey)    = crypto.generateIdentityKeyPair()
        val (newVerifyKey, newSignKey) = crypto.generateSigningKeyPair()

        val signature = crypto.sign(challenge.hexToByteArray(), oldSignKeyHex)
        println("completeTransfer: signature=${signature.take(8)}, calling api.transfer...")

        val response = api.transfer(
            TransferRequest(
                userId       = userId,
                challenge    = challenge,
                signature    = signature,
                newPublicKey = newPubKey,
                newVerifyKey = newVerifyKey,
                deviceInfo   = deviceInfo
            )
        )

        println("completeTransfer: got response, saving session...")

        identityStorage.saveIdentityKeyPair(newPubKey, newPrivKey)
        identityStorage.saveSigningKeyPair(newVerifyKey, newSignKey)
        saveSession(response)
        onSessionCreated?.invoke(true)
    }

    suspend fun logout() {
        storage.clear()
        uIdStorage.clear()
        // identity keys НЕ чистим — они привязаны к аккаунту, не к сессии
        onSessionDestroyed?.invoke()
    }

    suspend fun deleteAccount() {
        storage.clear()
        uIdStorage.clear()
        identityStorage.clear()   // при удалении аккаунта — чистим ключи
        onSessionDestroyed?.invoke()
    }

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
                _sessionExpiredFlow.tryEmit(Unit)
            }
            false
        } catch (e: Exception) { false }
    }

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
            } else RefreshResult.NetworkError
        } catch (e: Exception) { RefreshResult.NetworkError }
    }

    suspend fun isLoggedIn() = storage.getRefreshToken() != null

    private suspend fun saveSession(response: AuthResponse) {
        storage.saveAccessToken(response.accessToken)
        storage.saveRefreshToken(response.refreshToken)
        uIdStorage.saveUID(response.userId)
    }

    private fun String.hexToByteArray(): ByteArray {
        check(length % 2 == 0)
        return ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
}
