package com.vladdev.freedomchat.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.vladdev.freedomchat.MainApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// notifications/FcmMessagingService.kt
class FcmMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        println("FCM new token: ${token.take(10)}")
        // Отправляем на сервер если пользователь авторизован
        val app = application as MainApplication
        CoroutineScope(Dispatchers.IO).launch {
            app.profileRepository?.saveFcmToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        val type = data["type"] ?: return

        if (type != "new_message") return

        val chatId          = data["chatId"]          ?: return
        val senderId        = data["senderId"]         ?: return
        val senderUsername  = data["senderUsername"]   ?: return
        val encryptedContent = data["encryptedContent"] ?: return

        val app = application as MainApplication

        // Проверяем — если чат сейчас открыт, не показываем уведомление
        if (app.activeChatId == chatId) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Расшифровываем локально
                val plaintext = app.e2ee.decryptMessage(chatId, encryptedContent)
                    ?: return@launch  // не удалось расшифровать — не показываем

                val notificationId = chatId.hashCode()
                val helper = NotificationHelper(applicationContext)
                helper.showMessageNotification(
                    chatId         = chatId,
                    senderUsername = senderUsername,
                    text           = plaintext,
                    notificationId = notificationId
                )
            } catch (e: Exception) {
                println("FCM decrypt error: ${e.message}")
            }
        }
    }
}