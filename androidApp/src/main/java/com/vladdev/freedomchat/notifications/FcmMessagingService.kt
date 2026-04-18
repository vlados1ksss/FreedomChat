package com.vladdev.freedomchat.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.vladdev.freedomchat.MainApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.vladdev.shared.chats.mediaNotificationPreview

class FcmMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val app = application as MainApplication
        CoroutineScope(Dispatchers.IO).launch {
            app.profileRepository?.saveFcmToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data            = message.data
        val type            = data["type"] ?: return
        if (type != "new_message") return

        val chatId           = data["chatId"]           ?: return
        val senderUsername   = data["senderUsername"]   ?: return
        val encryptedContent = data["encryptedContent"] ?: return
        val mediaType        = data["mediaType"]?.takeIf { it.isNotBlank() }

        val app = application as MainApplication
        if (app.activeChatId == chatId) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val isMediaMessage = mediaType != null

                val plaintext: String? = if (encryptedContent.isNotBlank()) {
                    app.e2ee.decryptMessage(chatId, encryptedContent)
                        ?.takeIf { it != "\u200B" }
                } else null

                // Для медиасообщений показываем даже если подпись null
                if (!isMediaMessage && plaintext == null) return@launch

                val notificationText = mediaNotificationPreview(
                    encryptedContent = encryptedContent,
                    plaintext        = plaintext,
                    mediaTypeHint    = mediaType
                )

                val notificationId = chatId.hashCode()
                NotificationHelper(applicationContext).showMessageNotification(
                    chatId         = chatId,
                    senderUsername = senderUsername,
                    text           = notificationText,
                    notificationId = notificationId
                )
            } catch (e: Exception) {
                println("FCM error: ${e.message}")
            }
        }
    }
}
