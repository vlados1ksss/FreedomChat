package com.vladdev.freedomchat

import android.app.Application
import com.vladdev.shared.auth.AuthApi
import com.vladdev.shared.auth.AuthRepository
import com.vladdev.shared.chats.ChatApi
import com.vladdev.shared.chats.ChatRepository
import com.vladdev.shared.network.HttpClientFactory
import com.vladdev.shared.storage.AndroidTokenStorage
import com.vladdev.shared.storage.AndroidUserIdStorage
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.WebSocketDeflateExtension.Companion.install

class MainApplication : Application() {

    lateinit var httpClient: HttpClient
    lateinit var authRepository: AuthRepository

    override fun onCreate() {
        super.onCreate()

        val tokenStorage = AndroidTokenStorage(this)
        val userIdStorage = AndroidUserIdStorage(this)

        // 1️⃣ Отдельный клиент без interceptor для AuthApi
        val baseClient = HttpClient {
            install(ContentNegotiation) { json() }
        }

        val authApi = AuthApi(baseClient)

        authRepository = AuthRepository(
            api = authApi,
            storage = tokenStorage,
            uIdStorage = userIdStorage
        )

        // 2️⃣ Основной клиент с авто-refresh
        httpClient = HttpClientFactory.create(
            storage = tokenStorage,
            authRepository = authRepository
        )
    }
}
