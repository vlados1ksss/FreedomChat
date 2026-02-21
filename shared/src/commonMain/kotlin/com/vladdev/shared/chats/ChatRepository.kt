package com.vladdev.shared.chats

import com.vladdev.shared.chats.dto.ChatDto
import com.vladdev.shared.chats.dto.ChatRequestDto
import com.vladdev.shared.chats.dto.MessageDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class ChatRepository(
    private val api: ChatApi
) {
    private val chatFlows = mutableMapOf<String, MutableSharedFlow<MessageDto>>()
    suspend fun loadChats(): Result<List<ChatDto>> =
        runCatching { api.getChats() }

    suspend fun loadRequests(): Result<List<ChatRequestDto>> =
        runCatching { api.getIncomingRequests() }

    suspend fun sendRequest(username: String): Result<String> =
        runCatching { api.sendRequest(username).requestId }

    suspend fun accept(requestId: String): Result<String> =
        runCatching { api.accept(requestId).chatId }

    suspend fun reject(requestId: String): Result<Unit> =
        runCatching { api.reject(requestId) }

    fun messagesFlow(chatId: String): Flow<MessageDto> {
        return chatFlows.getOrPut(chatId) { MutableSharedFlow(replay = 50) }
    }


    suspend fun openChat(chatId: String, scope: CoroutineScope) {
        println("openChat $chatId")

        api.openChatWebSocket(chatId, scope) { message ->
            println("emit message ${message.id}")
            chatFlows.getOrPut(chatId) {
                MutableSharedFlow(extraBufferCapacity = 50)
            }.tryEmit(message)
        }
    }


    suspend fun sendMessage(chatId: String, encryptedContent: String) {
        println("sendMessage -> API")
        api.sendMessageWS(chatId, encryptedContent)
    }

    suspend fun closeChat(chatId: String) {
        api.closeChatWebSocket(chatId)
        chatFlows.remove(chatId)
    }
}
