package com.vladdev.shared.chats

import com.vladdev.shared.chats.dto.ChatDto
import com.vladdev.shared.chats.dto.ChatRequestDto
import com.vladdev.shared.chats.dto.DecryptedMessage
import com.vladdev.shared.chats.dto.SearchUserResponse
import com.vladdev.shared.crypto.Base64Helper
import com.vladdev.shared.crypto.CryptoManager
import com.vladdev.shared.crypto.E2eeManager
import com.vladdev.shared.crypto.dto.EncryptedPayload
import com.vladdev.shared.storage.IdentityKeyStorage
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
    private val e2ee: E2eeManager
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

    /**
     * Отправить сообщение с E2EE шифрованием.
     * @param theirUserId — ID собеседника (для получения его pubkey)
     */
    suspend fun sendMessage(chatId: String, plaintext: String, theirUserId: String) {
        // Получаем pubkey собеседника с сервера
        val theirPublicKey = api.getPublicKey(theirUserId).publicKey

        // Шифруем
        val encryptedContent = e2ee.encryptMessage(
            chatId         = chatId,
            plaintext      = plaintext,
            theirPublicKeyHex = theirPublicKey
        )

        println("sendMessage -> API (encrypted)")
        api.sendMessageWS(chatId, encryptedContent)
    }

    /**
     * Загрузить и расшифровать историю сообщений.
     * @param myUserId — свой userId чтобы не расшифровывать свои (или расшифровывать иначе)
     */
    suspend fun getMessages(chatId: String, myUserId: String): List<DecryptedMessage> {
        val raw = api.getMessages(chatId)
        return raw.map { msg ->
            val plaintext = when {
                msg.deletedForAll -> null
                msg.encryptedContent.isBlank() -> null   // пустое — старое сообщение
                msg.senderId == myUserId -> {
                    // Пробуем достать из персистентного кэша по индексу
                    // Индекс неизвестен из MessageDto — нужно декодировать payload
                    runCatching {
                        val payloadBytes = Base64Helper.decode(msg.encryptedContent)
                        val payload = Json.decodeFromString<EncryptedPayload>(payloadBytes.decodeToString())
                        e2ee.getOutgoingPlaintext(chatId, payload.messageIndex)
                    }.getOrNull() ?: "🔒 Отправлено в другой сессии"
                }
                else -> e2ee.decryptMessage(chatId, msg.encryptedContent)
            }
            DecryptedMessage(
                id            = msg.id,
                chatId        = msg.chatId,
                senderId      = msg.senderId,
                text          = plaintext,
                createdAt     = msg.createdAt,
                deletedForAll = msg.deletedForAll,
                statuses      = msg.statuses
            )
        }
    }

    suspend fun sendRead(chatId: String, messageId: String, userId: String) {
        api.sendReadWS(chatId, messageId, userId)
    }

    suspend fun deleteMessage(chatId: String, messageId: String, forAll: Boolean) {
        api.deleteMessageWS(chatId, messageId, forAll)
    }

    suspend fun loadChats(): Result<List<ChatDto>> =
        runCatching { api.getChats() }

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

    suspend fun closeChat(chatId: String) {
        api.closeChatWebSocket(chatId)
        chatFlows.remove(chatId)
    }
}
