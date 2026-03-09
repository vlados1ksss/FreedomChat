package com.vladdev.shared.chats

import com.vladdev.shared.auth.dto.PublicKeyResponse
import com.vladdev.shared.chats.dto.ChatDto
import com.vladdev.shared.chats.dto.ChatIdResponse
import com.vladdev.shared.chats.dto.ChatRequestDto
import com.vladdev.shared.chats.dto.MessageDto
import com.vladdev.shared.chats.dto.MessageStatus
import com.vladdev.shared.chats.dto.RequestIdResponse
import com.vladdev.shared.chats.dto.SearchUserResponse
import com.vladdev.shared.chats.dto.WsDeleteEvent
import com.vladdev.shared.chats.dto.WsEditEvent
import com.vladdev.shared.chats.dto.WsMessageEvent
import com.vladdev.shared.chats.dto.WsStatusEvent
import com.vladdev.shared.storage.TokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(InternalSerializationApi::class)
class ChatApi(private val client: HttpClient, private val tokenStorage: TokenStorage) {

    val AppJson = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
//    private val baseUrl = "http://192.168.31.191:8080"
    private val baseWsUrl = "ws://176.124.199.31:8080"
    private val baseUrl = "http://176.124.199.31:8080"

    private val wsConnections = mutableMapOf<String, DefaultClientWebSocketSession>()

    suspend inline fun <reified T> HttpResponse.safeBody(): T {
        if (!status.isSuccess()) throw Exception("HTTP ${status.value}")
        return body()
    }
    suspend fun searchUser(username: String): SearchUserResponse =
        client.get("$baseUrl/chats/search") {
            parameter("username", username)
        }.safeBody()

    suspend fun createDirectChat(userId: String): ChatIdResponse =
        client.post("$baseUrl/chats/direct/$userId").safeBody()

    suspend fun sendRequest(username: String): RequestIdResponse =
        client.post("$baseUrl/chats/request/$username").safeBody()

    suspend fun getIncomingRequests(): List<ChatRequestDto> =
        client.get("$baseUrl/chats/requests").safeBody()

    suspend fun getChats(): List<ChatDto> =
        client.get("$baseUrl/chats").safeBody()

    suspend fun accept(requestId: String): ChatIdResponse =
        client.post("$baseUrl/chats/accept/$requestId").safeBody()

    suspend fun reject(requestId: String) =
        client.post("$baseUrl/chats/reject/$requestId")

    suspend fun getMessages(chatId: String): List<MessageDto> =
        client.get("$baseUrl/chats/$chatId/messages").safeBody()

    suspend fun getPublicKey(userId: String): PublicKeyResponse =
        client.get("$baseUrl/keys/$userId").safeBody()

    // ChatApi.kt

    suspend fun openChatWebSocket(
        chatId: String,
        scope: CoroutineScope,
        onEvent: (WsIncomingEvent) -> Unit
    ) {
        // Если соединение уже есть — просто добавляем ещё одного слушателя через flow
        // Само соединение одно, подписчиков через SharedFlow может быть много
        if (wsConnections.containsKey(chatId)) {
            println("WS already open for $chatId, reusing")
            return
        }

        val token = tokenStorage.getAccessToken()
            ?: throw IllegalStateException("No access token")

        val session = client.webSocketSession {
            url {
                takeFrom(baseWsUrl)
                encodedPath = "/ws/chats/$chatId"
                parameters.append("token", token)
            }
        }

        wsConnections[chatId] = session

        scope.launch {
            try {
                for (frame in session.incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        val type = Json.parseToJsonElement(text)
                            .jsonObject["type"]?.jsonPrimitive?.content

                        val event: WsIncomingEvent = when (type) {
                            "message" -> {
                                val wsEvent = Json.decodeFromString<WsMessageEvent>(text)

                                // Резолвим pending если это эхо нашего сообщения
                                if (wsEvent.message.id.isNotEmpty()) {
                                    resolvePendingMessage(chatId, wsEvent.message.id)
                                }

                                IncomingMessage(wsEvent.message)
                            }
                            "status" -> {
                                val e = Json.decodeFromString<WsStatusEvent>(text)
                                IncomingStatus(e.messageId, e.userId, e.status)
                            }
                            "delete" -> {
                                val e = Json.decodeFromString<WsDeleteEvent>(text)
                                IncomingDelete(e.messageId, e.deleteForAll)
                            }
                            "edit" -> {
                                val e = Json.decodeFromString<WsEditEvent>(text)
                                if (e.messageId.isNotEmpty()) resolvePendingMessage(chatId, e.messageId)
                                IncomingEdit(e.messageId, e.senderId, e.encryptedContent, e.editedAt)
                            }
                            else -> continue
                        }
                        onEvent(event)
                    }
                }
            } catch (e: Exception) {
                println("WS frame loop error for $chatId: ${e.message}")
                wsConnections.remove(chatId)
            }
        }
    }

    suspend fun sendMessageWS(
        chatId: String,
        encryptedContent: String,
        replyToId: String? = null,
        replyToPreview: String? = null
    ): String {
        val session = wsConnections[chatId] ?: throw IllegalStateException("WS not open")
        val pendingId = CompletableDeferred<String>()
        pendingMessages[chatId] = pendingId

        val event = WsMessageEvent(
            message = MessageDto(
                id = "", chatId = chatId, senderId = "",
                encryptedContent = encryptedContent, createdAt = 0
            ),
            replyToId = replyToId,
            replyToPreview = replyToPreview
        )
        session.send(Frame.Text(AppJson.encodeToString(event)))
        return withTimeout(5000) { pendingId.await() }
    }

    private val pendingMessages = mutableMapOf<String, CompletableDeferred<String>>()

    // Вызывать из frame-loop когда приходит эхо своего сообщения:
    fun resolvePendingMessage(chatId: String, messageId: String) {
        pendingMessages.remove(chatId)?.complete(messageId)
    }
    suspend fun sendReadWS(chatId: String, messageId: String, userId: String) {
        val session = wsConnections[chatId] ?: return
        val event = WsStatusEvent(
            type = "status", messageId = messageId,
            userId = userId, status = MessageStatus.READ
        )
        session.send(Frame.Text(AppJson.encodeToString(event)))
    }

    suspend fun deleteMessageWS(chatId: String, messageId: String, forAll: Boolean) {
        val session = wsConnections[chatId] ?: return
        val event = WsDeleteEvent(messageId = messageId, deleteForAll = forAll)
        session.send(Frame.Text(AppJson.encodeToString(event)))
    }

    suspend fun sendEditWS(chatId: String, senderId: String, messageId: String, encryptedContent: String) {
        val session = wsConnections[chatId] ?: throw IllegalStateException("WS not open")
        val event = WsEditEvent(messageId = messageId, senderId = senderId, encryptedContent = encryptedContent, editedAt = 0)
        session.send(Frame.Text(AppJson.encodeToString(event)))
    }

    suspend fun closeChatWebSocket(chatId: String) {
        wsConnections.remove(chatId)?.close()
    }

    suspend fun setChatMuted(chatId: String, muted: Boolean) {
        client.post("$baseUrl/chats/$chatId/mute") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("muted" to muted))
        }
    }
    suspend fun markChatAsRead(chatId: String) {
        client.post("$baseUrl/chats/$chatId/read")
    }
    suspend fun getChatMuted(chatId: String): Boolean =
        client.get("$baseUrl/chats/$chatId/mute")
            .body<Map<String, Boolean>>()["muted"] ?: false

    fun hasActiveConnection(chatId: String): Boolean = wsConnections.containsKey(chatId)
}