package com.vladdev.shared.user

import com.vladdev.shared.auth.dto.TransferChallengeResponse
import com.vladdev.shared.user.dto.ChangePasswordRequest
import com.vladdev.shared.user.dto.DeleteAccountRequest
import com.vladdev.shared.user.dto.UpdateEmailRequest
import com.vladdev.shared.user.dto.UpdateNameRequest
import com.vladdev.shared.user.dto.UserProfileResponse
import com.vladdev.shared.user.dto.VerifyPasswordRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json

class ProfileApi(private val client: HttpClient) {
    private val base = "http://192.168.31.191:8080"

    @OptIn(InternalSerializationApi::class)
    suspend fun getProfile(): UserProfileResponse =
        client.get("$base/profile/me").body()

    @OptIn(InternalSerializationApi::class)
    suspend fun updateName(name: String) =
        client.patch("$base/profile/name") {
            contentType(ContentType.Application.Json)
            setBody(UpdateNameRequest(name))
        }

    @OptIn(InternalSerializationApi::class)
    suspend fun updateEmail(email: String) =
        client.patch("$base/profile/email") {
            contentType(ContentType.Application.Json)
            setBody(UpdateEmailRequest(email))
        }

    @OptIn(InternalSerializationApi::class)
    suspend fun verifyPassword(password: String): Boolean =
        client.post("$base/profile/verify-password") {
            contentType(ContentType.Application.Json)
            setBody(VerifyPasswordRequest(password))
        }.status == HttpStatusCode.OK

    @OptIn(InternalSerializationApi::class)
    suspend fun changePassword(current: String, new: String) {
        val response = client.patch("$base/profile/password") {
            contentType(ContentType.Application.Json)
            setBody(ChangePasswordRequest(current, new))
        }
        if (response.status != HttpStatusCode.OK) {
            throw Exception(response.bodyAsText())
        }
    }

    @OptIn(InternalSerializationApi::class)
    suspend fun deleteAccount(password: String) {
        val response = client.delete("$base/profile") {
            contentType(ContentType.Application.Json)
            setBody(DeleteAccountRequest(password))
        }
        // Явно бросаем исключение если не OK — иначе runCatching считает это успехом
        if (response.status != HttpStatusCode.OK) {
            val message = runCatching { response.bodyAsText() }.getOrDefault("Unknown error")
            throw Exception(message)
        }
    }
    @OptIn(InternalSerializationApi::class)
    suspend fun getTransferChallenge(): TransferChallengeResponse =
        client.get("$base/auth/transfer/challenge").body()
}