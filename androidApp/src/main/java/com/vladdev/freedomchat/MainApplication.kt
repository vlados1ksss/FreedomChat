package com.vladdev.freedomchat

import android.app.Application
import com.vladdev.freedomchat.ui.chats.ChatScreen
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
    lateinit var chatRepository: ChatRepository

    override fun onCreate() {
        super.onCreate()

        val tokenStorage = AndroidTokenStorage(this)
        val userIdStorage = AndroidUserIdStorage(this)
//baseClient без аутентификации
        val baseClient = HttpClient {
            install(ContentNegotiation) { json() }
        }

        val authApi = AuthApi(baseClient)

        authRepository = AuthRepository(
            api = authApi,
            storage = tokenStorage,
            uIdStorage = userIdStorage
        )
//httpClient с аутентификацией, требуется для всех запросов с authenticate
        httpClient = HttpClientFactory.create(
            storage = tokenStorage,
            authRepository = authRepository
        )

        val chatApi = ChatApi(
            httpClient,
            tokenStorage
        )


        chatRepository = ChatRepository(
            api = chatApi
        )
    }
    object LogTags {
        const val CHAT_VM = "CHAT_VM"
        const val CHAT_REPO = "CHAT_REPO"
        const val CHAT_API = "CHAT_API"
        const val CHAT_WS = "CHAT_WS"
    }

}
