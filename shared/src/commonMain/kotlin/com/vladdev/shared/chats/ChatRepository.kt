package com.vladdev.shared.chats

import com.vladdev.shared.chats.dto.ChatDto
import com.vladdev.shared.chats.dto.ChatRequestDto
import com.vladdev.shared.chats.dto.DecryptedMessage
import com.vladdev.shared.chats.dto.MessageDto
import com.vladdev.shared.chats.dto.SearchUserResponse
import com.vladdev.shared.crypto.Base64Helper
import com.vladdev.shared.crypto.CryptoManager
import com.vladdev.shared.crypto.E2eeManager
import com.vladdev.shared.crypto.dto.EncryptedPayload
import com.vladdev.shared.storage.IdentityKeyStorage
import com.vladdev.shared.storage.UserIdStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json

@OptIn(InternalSerializationApi::class)
class ChatRepository(
    private val api: ChatApi,
    private val identityStorage: IdentityKeyStorage,
    private val crypto: CryptoManager,
    private val e2ee: E2eeManager,
    private val userIdStorage: UserIdStorage
) {
    private val chatFlows = mutableMapOf<String, MutableSharedFlow<WsIncomingEvent>>()

    private fun getOrCreateFlow(chatId: String): MutableSharedFlow<WsIncomingEvent> =
        chatFlows.getOrPut(chatId) { MutableSharedFlow(replay = 50) }

    fun eventsFlow(chatId: String): Flow<WsIncomingEvent> = getOrCreateFlow(chatId)

    suspend fun openChat(chatId: String, scope: CoroutineScope) {
        api.openChatWebSocket(chatId, scope) { event ->
            getOrCreateFlow(chatId).tryEmit(event)
        }
    }
    suspend fun sendMessage(chatId: String, plaintext: String, theirUserId: String) {
        // Получаем pubkey собеседника с сервера
        val theirPublicKey = api.getPublicKey(theirUserId).publicKey

        // Шифруем
        val encryptedContent = e2ee.encryptMessage(
            chatId         = chatId,
            plaintext      = plaintext,
            theirPublicKeyHex = theirPublicKey
        )

        api.sendMessageWS(chatId, encryptedContent)
    }

    suspend fun getMessages(chatId: String, myUserId: String): List<DecryptedMessage> {
        val raw = api.getMessages(chatId)
        return raw.mapNotNull { msg ->
            when {
                msg.deletedForAll -> null

                msg.encryptedContent.isBlank() -> null

                msg.senderId == myUserId -> {
                    val plaintext = runCatching {
                        val payloadBytes = Base64Helper.decode(msg.encryptedContent)
                        val payload = Json.decodeFromString<EncryptedPayload>(
                            payloadBytes.decodeToString()
                        )
                        e2ee.getOutgoingPlaintext(chatId, payload.messageIndex)
                    }.getOrNull()

                    if (plaintext == null) {
                        null
                    } else {
                        DecryptedMessage(
                            id        = msg.id,
                            chatId    = msg.chatId,
                            senderId  = msg.senderId,
                            text      = plaintext,
                            createdAt = msg.createdAt,
                            statuses  = msg.statuses
                        )
                    }
                }

                else -> {
                    val plaintext = e2ee.decryptMessage(chatId, msg.encryptedContent)
                    if (plaintext == null) {
                        null
                    } else {
                        DecryptedMessage(
                            id        = msg.id,
                            chatId    = msg.chatId,
                            senderId  = msg.senderId,
                            text      = plaintext,
                            createdAt = msg.createdAt,
                            statuses  = msg.statuses
                        )
                    }
                }
            }
        }
    }

    suspend fun sendRead(chatId: String, messageId: String, userId: String) {
        api.sendReadWS(chatId, messageId, userId)
    }

    suspend fun deleteMessage(chatId: String, messageId: String, forAll: Boolean) {
        api.deleteMessageWS(chatId, messageId, forAll)
    }

    suspend fun loadChats(): Result<List<ChatDto>> = runCatching {
        val raw = api.getChats()
        val myUserId = userIdStorage.getUID()

        raw.map { chat ->
            val decryptedLast = chat.lastMessage?.let { msg ->
                decryptLastMessage(msg, chat.chatId, myUserId)
            }
            chat.copy(lastMessage = decryptedLast)
        }
    }

    suspend fun searchUser(username: String): Result<SearchUserResponse> =
        runCatching { api.searchUser(username) }

    suspend fun createDirectChat(userId: String): Result<String> =
        runCatching { api.createDirectChat(userId).chatId }

    suspend fun loadRequests(): Result<List<ChatRequestDto>> =
        runCatching { api.getIncomingRequests() }

    suspend fun sendRequest(username: String): Result<String> =
        runCatching { api.sendRequest(username).requestId }

    suspend fun accept(requestId: String): Result<String> =
        runCatching { api.accept(requestId).chatId }

    suspend fun reject(requestId: String): Result<Unit> =
        runCatching { api.reject(requestId) }

    suspend fun getChatMuted(chatId: String): Boolean =
        runCatching { api.getChatMuted(chatId) }.getOrDefault(false)

    suspend fun setChatMuted(chatId: String, muted: Boolean) =
        api.setChatMuted(chatId, muted)

    private suspend fun decryptLastMessage(
        msg: MessageDto,
        chatId: String,
        myUserId: String?
    ): MessageDto {
        if (msg.deletedForAll || msg.encryptedContent.isBlank()) return msg

        val plaintext: String? = runCatching {
            if (msg.senderId == myUserId) {
                // Своё сообщение — берём из локального хранилища
                val payloadBytes = Base64Helper.decode(msg.encryptedContent)
                val payload = Json.decodeFromString<EncryptedPayload>(payloadBytes.decodeToString())
                e2ee.getOutgoingPlaintext(chatId, payload.messageIndex)
            } else {
                // Чужое — расшифровываем
                e2ee.decryptMessage(chatId, msg.encryptedContent)
            }
        }.getOrNull()

        // Возвращаем MessageDto с текстом в encryptedContent заменённым plaintext-ом
        // Используем отдельное поле — добавляем plaintext как расширение
        return msg.copy(plaintextPreview = plaintext)
        // encryptedContent теперь содержит plaintext для lastMessage превью
        // (либо добавьте поле plaintext: String? = null в MessageDto)
    }

    // ChatRepository.kt
    suspend fun decryptPreview(msg: MessageDto, chatId: String, myUserId: String): String? {
        if (msg.deletedForAll || msg.encryptedContent.isBlank()) return null
        return runCatching {
            if (msg.senderId == myUserId) {
                // Исходящее — берём из локального хранилища
                val payloadBytes = Base64Helper.decode(msg.encryptedContent)
                val payload = Json.decodeFromString<EncryptedPayload>(payloadBytes.decodeToString())
                e2ee.getOutgoingPlaintext(chatId, payload.messageIndex)
            } else {
                e2ee.decryptMessage(chatId, msg.encryptedContent)
            }
        }.getOrNull()
    }

    suspend fun markChatAsRead(chatId: String): Result<Unit> =
        runCatching { api.markChatAsRead(chatId) }

    suspend fun openChatBackground(chatId: String, scope: CoroutineScope) {
        api.openChatWebSocket(chatId, scope) { event ->
            getOrCreateFlow(chatId).tryEmit(event)
        }
    }

    suspend fun detachFromChat(chatId: String) {
        // Ничего не делаем с WS и flow — они живут в ChatsViewModel
        // Просто сигнализируем что активный чат покинут
    }

    // Вызывается из ChatsViewModel при уничтожении экрана списка
    suspend fun closeChatBackground(chatId: String) {
        api.closeChatWebSocket(chatId)
        chatFlows.remove(chatId)
    }

    // Оставляем для обратной совместимости но убираем удаление flow
    suspend fun closeChat(chatId: String) {
        // WS закроется сам когда ChatsViewModel уничтожится
        // Здесь только чистим если ChatsViewModel не активен
        if (!hasBackgroundSubscriber(chatId)) {
            api.closeChatWebSocket(chatId)
            chatFlows.remove(chatId)
        }
    }

    private val backgroundSubscribers = mutableSetOf<String>()

    fun registerBackgroundSubscriber(chatId: String) {
        backgroundSubscribers.add(chatId)
    }

    fun unregisterBackgroundSubscriber(chatId: String) {
        backgroundSubscribers.remove(chatId)
    }

    fun hasBackgroundSubscriber(chatId: String): Boolean =
        backgroundSubscribers.contains(chatId)
}
