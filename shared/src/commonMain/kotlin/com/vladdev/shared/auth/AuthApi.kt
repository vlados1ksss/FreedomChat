package com.vladdev.shared.auth

import com.vladdev.shared.auth.dto.RegisterRequest
import com.vladdev.shared.auth.dto.AuthResponse
import com.vladdev.shared.auth.dto.LoginRequest
import com.vladdev.shared.auth.dto.RefreshRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.InternalSerializationApi

class AuthApi(
    private val client: HttpClient
) {
    private val baseUrl = "http://192.168.31.191:8080"

    @OptIn(InternalSerializationApi::class)
    suspend fun register(
        username: String,
        name: String,
        email: String,
        password: String
    ): AuthResponse =
        client.post("$baseUrl/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username, name, email, password))
        }.body()

    @OptIn(InternalSerializationApi::class)
    suspend fun login(username: String, password: String): AuthResponse =
        client.post("$baseUrl/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username, password))
        }.body()

    @OptIn(InternalSerializationApi::class)
    suspend fun refresh(refreshToken: String): AuthResponse =
        client.post("$baseUrl/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(refreshToken))
        }.body()
}