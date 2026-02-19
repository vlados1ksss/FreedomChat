package com.vladdev.shared.network

import com.vladdev.shared.auth.AuthRepository
import com.vladdev.shared.storage.TokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.request.HttpRequestBuilder
import jdk.javadoc.internal.tool.Main.execute

object HttpClientFactory {

    fun create(
        storage: TokenStorage,
        authRepository: AuthRepository
    ): HttpClient {

        return HttpClient {

            install(ContentNegotiation) {
                json()
            }

            expectSuccess = false

            install(HttpSend) {

                install(HttpSend) {
                    intercept { request ->

                        val accessToken = storage.getAccessToken()
                        if (accessToken != null) {
                            request.headers[HttpHeaders.Authorization] = "Bearer $accessToken"
                        }

                        val originalCall = execute(request)

                        if (originalCall.response.status == HttpStatusCode.Unauthorized) {
                            val refreshed = authRepository.refreshTokens()

                            if (refreshed) {
                                val newAccess = storage.getAccessToken()
                                request.headers[HttpHeaders.Authorization] = "Bearer $newAccess"
                                return@intercept execute(request)
                            }
                        }

                        originalCall
                    }
                }
            }
        }
    }
}


