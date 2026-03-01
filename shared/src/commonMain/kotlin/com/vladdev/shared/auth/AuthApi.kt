package com.vladdev.shared.auth

import com.vladdev.shared.auth.dto.AuthRequest
import com.vladdev.shared.auth.dto.RegisterRequest
import com.vladdev.shared.auth.dto.AuthResponse
import com.vladdev.shared.auth.dto.HasDeviceResponse
import com.vladdev.shared.auth.dto.LoginRequest
import com.vladdev.shared.auth.dto.PublicKeyResponse
import com.vladdev.shared.auth.dto.RefreshRequest
import com.vladdev.shared.auth.dto.TransferChallengeResponse
import com.vladdev.shared.auth.dto.TransferRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.InternalSerializationApi

// AuthApi.kt

@OptIn(InternalSerializationApi::class)
class AuthApi(private val client: HttpClient) {
    private val baseUrl = "http://192.168.31.191:8080"

    suspend fun register(
        username: String, name: String, email: String,
        password: String, publicKey: String, verifyKey: String, deviceInfo: String?
    ): AuthResponse =
        client.post("$baseUrl/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(AuthRequest(username, name, email, password, publicKey, verifyKey, deviceInfo))
        }.body()

    suspend fun login(
        username: String, password: String,
        publicKey: String, verifyKey: String, deviceInfo: String?
    ): AuthResponse =
        client.post("$baseUrl/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username, password, publicKey, verifyKey, deviceInfo))
        }.body()

    suspend fun refresh(refreshToken: String): AuthResponse =
        client.post("$baseUrl/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(refreshToken))
        }.body()

    suspend fun transfer(req: TransferRequest): AuthResponse =
        client.post("$baseUrl/auth/transfer") {
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()

    suspend fun hasExistingDevice(username: String): HasDeviceResponse =
        client.get("$baseUrl/auth/has-device/$username").body()

    suspend fun getPublicKey(userId: String): PublicKeyResponse =
        client.get("$baseUrl/keys/$userId").body()
}