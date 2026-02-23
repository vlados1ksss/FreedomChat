package com.vladdev.shared.auth

import com.vladdev.shared.auth.dto.AuthRequest
import com.vladdev.shared.auth.dto.AuthResponse
import com.vladdev.shared.auth.dto.RefreshRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.InternalSerializationApi

@OptIn(InternalSerializationApi::class)
class AuthApi(
    private val client: HttpClient
) {

    private val baseUrl = "http://192.168.31.191:8080"
//private val baseUrl = "https://f41585cb8b9516.lhr.life"

    suspend fun register(username: String, password: String): AuthResponse =
        client.post("$baseUrl/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(AuthRequest(username, password))
        }.body()

    suspend fun login(username: String, password: String): AuthResponse =
        client.post("$baseUrl/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(AuthRequest(username, password))
        }.body()

    suspend fun refresh(refreshToken: String): AuthResponse =
        client.post("$baseUrl/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(refreshToken))
        }.body()
}

