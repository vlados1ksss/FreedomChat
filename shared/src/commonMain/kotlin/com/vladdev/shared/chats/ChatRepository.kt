package com.vladdev.shared.chats

import com.vladdev.shared.chats.dto.ChatDto
import com.vladdev.shared.chats.dto.ChatRequestDto
import com.vladdev.shared.chats.dto.DecryptedMessage
import com.vladdev.shared.chats.dto.ForwardedMessagePayload
import com.vladdev.shared.chats.dto.MessageDto
import com.vladdev.shared.chats.dto.SearchUserResponse
import com.vladdev.shared.crypto.Base64Helper
import com.vladdev.shared.crypto.CryptoManager
import com.vladdev.shared.crypto.E2eeManager
import com.vladdev.shared.crypto.dto.EncryptedPayload
import com.vladdev.shared.storage.IdentityKeyStorage
import com.vladdev.shared.storage.UserIdStorage
import com.vladdev.shared.user.dto.PresenceResponse
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
    suspend fun sendMessage(
        chatId: String,
        plaintext: String,
        theirUserId: String,
        replyToId: String? = null, // уже расшифрованный текст оригинала — передаём как есть
    ) {
        val theirPublicKey = api.getPublicKey(theirUserId).publicKey
        val encryptedContent = e2ee.encryptMessage(chatId, plaintext, theirPublicKey)
        val messageId = api.sendMessageWS(
            chatId = chatId,
            encryptedContent = encryptedContent,
            replyToId = replyToId
        )
        e2ee.saveOutgoingPlaintext(chatId, messageId, plaintext)
    }

    suspend fun getMessages(chatId: String, myUserId: String): List<DecryptedMessage> {
        val raw = api.getMessages(chatId)
        return raw.mapNotNull { msg ->
            when {
                msg.deletedForAll -> null

                msg.encryptedContent.isBlank() -> null

                msg.senderId == myUserId -> {
                    val plaintext = e2ee.getOutgoingPlaintext(chatId, msg.id)
                    if (plaintext == null) null
                    else DecryptedMessage(
                        id               = msg.id,
                        chatId           = msg.chatId,
                        senderId         = msg.senderId,
                        text             = plaintext,
                        createdAt        = msg.createdAt,
                        statuses         = msg.statuses,
                        editedAt         = msg.editedAt,
                        replyToId        = msg.replyToId,
                        replyToSenderId  = null,
                        forwardedFromId   = msg.forwardedFromId,
                        forwardedFromName = msg.forwardedFromName
                    )
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
                            statuses  = msg.statuses,
                            replyToId       = msg.replyToId,
                            replyToSenderId = null,
                            forwardedFromId   = msg.forwardedFromId,
                            forwardedFromName = msg.forwardedFromName
                        )
                    }
                }
            }
        }
    }

    suspend fun sendRead(chatId: String, messageId: String, userId: String) {
        api.sendReadWS(chatId, messageId, userId)
    }

    // ChatRepository
    suspend fun editMessage(chatId: String, messageId: String, newPlaintext: String, theirUserId: String) {
        val theirPublicKey = api.getPublicKey(theirUserId).publicKey
        val encrypted = e2ee.encryptMessage(chatId, newPlaintext, theirPublicKey)

        // Сначала сохраняем локально — до отправки по WS
        e2ee.saveOutgoingPlaintext(chatId, messageId, newPlaintext)

        api.sendEditWS(
            chatId           = chatId,
            messageId        = messageId,
            senderId         = userIdStorage.getUID() ?: "",
            encryptedContent = encrypted
        )
    }

    suspend fun deleteMessages(chatId: String, messageIds: List<String>, forAll: Boolean) {
        messageIds.forEach { id ->
            api.deleteMessageWS(chatId, id, forAll)
        }
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
                // Сначала пробуем из хранилища
                e2ee.getOutgoingPlaintext(chatId, msg.id)
                // Fallback: расшифровываем как входящее (для пересланных когда plaintext не сохранён)
                    ?: e2ee.decryptMessage(chatId, msg.encryptedContent)
            } else {
                e2ee.decryptMessage(chatId, msg.encryptedContent)
            }
        }.getOrNull()

        return msg.copy(plaintextPreview = plaintext)
    }

    suspend fun decryptPreview(msg: MessageDto, chatId: String, myUserId: String): String? {
        if (msg.deletedForAll || msg.encryptedContent.isBlank()) return null
        return runCatching {
            if (msg.senderId == myUserId) {
                e2ee.getOutgoingPlaintext(chatId, msg.id)
                    ?: e2ee.decryptMessage(chatId, msg.encryptedContent)
            } else {
                e2ee.decryptMessage(chatId, msg.encryptedContent)
            }
        }.getOrNull()
    }
    suspend fun decryptEditPreview(
        chatId: String,
        messageId: String,
        encryptedContent: String,
        myUserId: String,
        senderId: String
    ): String? = runCatching {
        if (senderId == myUserId) {
            e2ee.getOutgoingPlaintext(chatId, messageId)
        } else {
            e2ee.decryptMessage(chatId, encryptedContent)
        }
    }.getOrNull()


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


    // ChatRepository
    suspend fun forwardMessages(
        targetChatId: String,
        messages: List<DecryptedMessage>,
        theirUserId: String,
        nameResolver: (DecryptedMessage) -> String
    ) {
        val theirPublicKey = api.getPublicKey(theirUserId).publicKey
        e2ee.ensureSessionInitialized(targetChatId, theirPublicKey)

        val payloads = messages.sortedBy { it.createdAt }.map { msg ->
            val text = msg.text ?: ""
            val encrypted = e2ee.encryptMessage(targetChatId, text, theirPublicKey)
            Triple(text, encrypted, msg)
        }

        val sentIds = api.sendForward(
            targetChatId,
            payloads.map { (_, encrypted, msg) ->
                ForwardedMessagePayload(
                    encryptedContent    = encrypted,
                    originalSenderId    = msg.senderId,
                    originalSenderName  = nameResolver(msg),
                    originalCreatedAt   = msg.createdAt
                )
            }
        )

        sentIds.zip(payloads).forEach { (msgId, triple) ->
            val (text, _, _) = triple
            e2ee.saveOutgoingPlaintext(targetChatId, msgId, text)
        }
    }

    suspend fun pinMessage(chatId: String, messageId: String, unpin: Boolean) {
        api.sendPinWS(chatId, messageId, unpin)
    }

    suspend fun getPinnedMessages(chatId: String, myUserId: String): List<DecryptedMessage> {
        val raw = api.getPinnedMessages(chatId)
        return raw.mapNotNull { msg ->
            val text = if (msg.senderId == myUserId)
                e2ee.getOutgoingPlaintext(chatId, msg.id)
            else
                e2ee.decryptMessage(chatId, msg.encryptedContent)
            if (text == null) null
            else DecryptedMessage(
                id               = msg.id,
                chatId           = msg.chatId,
                senderId         = msg.senderId,
                text             = text,
                createdAt        = msg.createdAt,
                deletedForAll    = false,
                statuses         = emptyList(),
                pinnedAt         = msg.pinnedAt
            )
        }
    }

    private val backgroundSubscribers = mutableSetOf<String>()

    fun registerBackgroundSubscriber(chatId: String) {
        backgroundSubscribers.add(chatId)
    }

    fun unregisterBackgroundSubscriber(chatId: String) {
        backgroundSubscribers.remove(chatId)
    }
    suspend fun sendTyping(chatId: String, isTyping: Boolean) {
        runCatching { api.sendTypingWS(chatId, isTyping) }
    }
    suspend fun getPresence(userId: String): Result<PresenceResponse> =
        runCatching { api.getPresence(userId) }

    suspend fun deleteChat(chatId: String): Result<Unit> =
        runCatching { api.deleteChat(chatId) }

    suspend fun clearHistory(chatId: String): Result<Unit> =
        runCatching { api.clearHistory(chatId) }
}
