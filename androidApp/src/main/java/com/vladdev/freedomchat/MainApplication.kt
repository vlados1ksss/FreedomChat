package com.vladdev.freedomchat

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.messaging.FirebaseMessaging
import com.vladdev.freedomchat.notifications.NotificationHelper
import com.vladdev.shared.auth.AuthApi
import com.vladdev.shared.auth.AuthRepository
import com.vladdev.shared.chats.ChatApi
import com.vladdev.shared.chats.ChatRepository
import com.vladdev.shared.crypto.CryptoManager
import com.vladdev.shared.crypto.E2eeManager
import com.vladdev.shared.network.HttpClientFactory
import com.vladdev.shared.storage.AndroidIdentityKeyStorage
import com.vladdev.shared.storage.AndroidRatchetStorage
import com.vladdev.shared.storage.AndroidTokenStorage
import com.vladdev.shared.storage.AndroidUserIdStorage
import com.vladdev.shared.user.ProfileApi
import com.vladdev.shared.user.ProfileRepository
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.prefs.Preferences

class MainApplication : Application() {

    lateinit var tokenStorage: AndroidTokenStorage          private set
    lateinit var userIdStorage: AndroidUserIdStorage        private set
    lateinit var identityStorage: AndroidIdentityKeyStorage private set
    lateinit var ratchetStorage: AndroidRatchetStorage      private set
    lateinit var crypto: CryptoManager                      private set
    lateinit var e2ee: E2eeManager                          private set
    lateinit var authRepository: AuthRepository             private set
    lateinit var notificationHelper: NotificationHelper
        private set
    // В Application или как extension

    var httpClient: HttpClient? = null
    var chatRepository: ChatRepository? = null
    var profileRepository: ProfileRepository? = null
    var activeChatId: String? = null

    override fun onCreate() {
        super.onCreate()

        tokenStorage    = AndroidTokenStorage(this)
        userIdStorage   = AndroidUserIdStorage(this)
        identityStorage = AndroidIdentityKeyStorage(this)
        ratchetStorage  = AndroidRatchetStorage(this)
        crypto          = CryptoManager()
        e2ee            = E2eeManager(crypto, identityStorage, ratchetStorage)

        val authClient = HttpClient {
            install(ContentNegotiation) { json() }
            install(HttpTimeout) {
                connectTimeoutMillis = 5000
                requestTimeoutMillis = 10000
            }
            expectSuccess = true
        }

        authRepository = AuthRepository(
            api             = AuthApi(authClient),
            storage         = tokenStorage,
            uIdStorage      = userIdStorage,
            identityStorage = identityStorage,
            crypto          = crypto
        )
        notificationHelper = NotificationHelper(this)

        // Получаем FCM токен при старте
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                println("FCM token: ${token.take(10)}")
                // Сохраняем токен когда сессия будет создана
                pendingFcmToken = token
            }
        }
    }
    private var pendingFcmToken: String? = null

    fun createSession(isNewDevice: Boolean = false) {
        destroySession()

        if (isNewDevice) {
            // Сбрасываем ratchet только если сменилось устройство / новый ключ
            kotlinx.coroutines.runBlocking { e2ee.resetAllSessions() }
        }

        val client = HttpClientFactory.create(tokenStorage, authRepository)
        httpClient = client
        chatRepository = ChatRepository(
            api             = ChatApi(client, tokenStorage),
            identityStorage = identityStorage,
            crypto          = crypto,
            e2ee            = e2ee,
            userIdStorage = userIdStorage
        )
        profileRepository = ProfileRepository(ProfileApi(client), authRepository)

        pendingFcmToken?.let { token ->
            kotlinx.coroutines.GlobalScope.launch {
                profileRepository?.saveFcmToken(token)
            }
            pendingFcmToken = null
        }
        refreshFcmToken()
    }


    fun destroySession() {
        httpClient?.let {
            kotlinx.coroutines.GlobalScope.launch {
                profileRepository?.deleteFcmToken()
            }
        }
        httpClient?.close()
        httpClient = null; chatRepository = null; profileRepository = null; activeChatId = null
    }


    private fun refreshFcmToken() {
        // Удаляем старый токен — FCM выдаст новый через onNewToken
        FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener { deleteTask ->
            if (deleteTask.isSuccessful) {
                println("FCM: old token deleted, requesting new...")
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val token = task.result
                        println("FCM: new token=${token.take(10)}")
                        kotlinx.coroutines.GlobalScope.launch {
                            profileRepository?.saveFcmToken(token)
                                ?.onSuccess { println("FCM: token saved to server") }
                                ?.onFailure { println("FCM: token save failed: ${it.message}") }
                        }
                    } else {
                        println("FCM: token request failed: ${task.exception?.message}")
                    }
                }
            } else {
                // Если удаление не удалось — просто берём текущий
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val token = task.result
                        kotlinx.coroutines.GlobalScope.launch {
                            profileRepository?.saveFcmToken(token)
                        }
                    }
                }
            }
        }
    }


    object LogTags {
        const val CHAT_VM = "CHAT_VM"
        const val CHAT_REPO = "CHAT_REPO"
        const val CHAT_API = "CHAT_API"
        const val CHAT_WS = "CHAT_WS"
    }

}


