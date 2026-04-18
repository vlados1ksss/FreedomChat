package com.vladdev.shared.network

import com.vladdev.shared.auth.AuthRepository
import com.vladdev.shared.storage.TokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.encodedPath
import io.ktor.websocket.*
import kotlin.time.Duration.Companion.seconds



object HttpClientFactory {

    fun create(
        storage: TokenStorage,
        authRepository: AuthRepository
    ): HttpClient {

        return HttpClient {

            install(ContentNegotiation) {
                json()
            }
            install(WebSockets) {
                pingInterval = 20.seconds.inWholeMilliseconds
                maxFrameSize = Long.MAX_VALUE
            }


            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.ALL
            }

            install(HttpTimeout) {
                // Для обычных запросов
                requestTimeoutMillis = 30_000L

                // Для загрузки больших файлов — отдельно через конфиг запроса
                connectTimeoutMillis = 15_000L
                socketTimeoutMillis  = 120_000L  // 2 минуты на чтение/запись
            }

            install(Auth) {
                bearer {
                    loadTokens {
                        val access = storage.getAccessToken()
                        val refresh = storage.getRefreshToken()
                        if (access != null && refresh != null)
                            BearerTokens(access, refresh)
                        else null
                    }

                    sendWithoutRequest { request ->
                        request.url.encodedPath.startsWith("/auth") ||
                                request.url.encodedPath == "/refresh"
                    }

                    refreshTokens {
                        val success = authRepository.refreshTokens()
                        if (!success) {
                            // Сигналим NavGraph что нужно идти на auth
                            null
                        } else {
                            BearerTokens(
                                storage.getAccessToken()!!,
                                storage.getRefreshToken()!!
                            )
                        }
                    }
                }
            }

        }
    }
}
