package com.vladdev.freedomchat

import android.app.Application
import com.vladdev.shared.auth.AuthApi
import com.vladdev.shared.auth.AuthRepository
import com.vladdev.shared.chats.ChatApi
import com.vladdev.shared.chats.ChatRepository
import com.vladdev.shared.network.HttpClientFactory
import com.vladdev.shared.storage.AndroidTokenStorage
import com.vladdev.shared.storage.AndroidUserIdStorage
import com.vladdev.shared.user.ProfileApi
import com.vladdev.shared.user.ProfileRepository
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

class MainApplication : Application() {

    // Постоянные — живут всё время
    lateinit var tokenStorage: AndroidTokenStorage
        private set
    lateinit var userIdStorage: AndroidUserIdStorage
        private set
    lateinit var authRepository: AuthRepository
        private set

    // Сессионные — пересоздаются при каждом входе
    var httpClient: HttpClient? = null
        private set
    var chatRepository: ChatRepository? = null
        private set
    var profileRepository: ProfileRepository? = null
        private set

    override fun onCreate() {
        super.onCreate()

        tokenStorage = AndroidTokenStorage(this)
        userIdStorage = AndroidUserIdStorage(this)

        val authClient = HttpClient {
            install(ContentNegotiation) { json() }
            expectSuccess = true
        }

        authRepository = AuthRepository(
            api = AuthApi(authClient),
            storage = tokenStorage,
            uIdStorage = userIdStorage
        )
    }

    // Вызывается после успешного login/register
    fun createSession() {
        // Закрываем старую сессию если есть
        destroySession()

        val client = HttpClientFactory.create(
            storage = tokenStorage,
            authRepository = authRepository
        )

        httpClient = client
        chatRepository = ChatRepository(api = ChatApi(client, tokenStorage))
        profileRepository = ProfileRepository(
            api = ProfileApi(client),
            authRepository = authRepository
        )

        println("Session created for user: ${tokenStorage}")
    }

    // Вызывается при logout/delete
    fun destroySession() {
        httpClient?.close()
        httpClient = null
        chatRepository = null
        profileRepository = null
        println("Session destroyed")
    }

    object LogTags {
        const val CHAT_VM = "CHAT_VM"
        const val CHAT_REPO = "CHAT_REPO"
        const val CHAT_API = "CHAT_API"
        const val CHAT_WS = "CHAT_WS"
    }
}
