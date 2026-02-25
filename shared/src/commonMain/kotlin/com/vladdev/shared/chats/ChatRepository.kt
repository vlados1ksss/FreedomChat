package com.vladdev.shared.chats

import com.vladdev.shared.chats.dto.ChatDto
import com.vladdev.shared.chats.dto.ChatRequestDto
import com.vladdev.shared.chats.dto.MessageDto
import com.vladdev.shared.chats.dto.MessageStatus
import com.vladdev.shared.chats.dto.SearchUserResponse
import com.vladdev.shared.chats.dto.WsDeleteEvent
import com.vladdev.shared.chats.dto.WsMessageEvent
import com.vladdev.shared.chats.dto.WsStatusEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.InternalSerializationApi

@OptIn(InternalSerializationApi::class)
class ChatRepository(
    private val api: ChatApi
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



    suspend fun sendMessage(chatId: String, encryptedContent: String) {
        println("sendMessage -> API")
        api.sendMessageWS(chatId, encryptedContent)
    }
    suspend fun getMessages(chatId: String): List<MessageDto> =
        api.getMessages(chatId)
    suspend fun closeChat(chatId: String) {
        api.closeChatWebSocket(chatId)
        chatFlows.remove(chatId)
    }
}
