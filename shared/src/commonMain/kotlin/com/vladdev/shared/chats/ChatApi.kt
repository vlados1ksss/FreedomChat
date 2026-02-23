package com.vladdev.shared.chats

import com.vladdev.shared.chats.dto.ChatDto
import com.vladdev.shared.chats.dto.ChatIdResponse
import com.vladdev.shared.chats.dto.ChatRequestDto
import com.vladdev.shared.chats.dto.MessageDto
import com.vladdev.shared.chats.dto.MessageStatus
import com.vladdev.shared.chats.dto.RequestIdResponse
import com.vladdev.shared.chats.dto.SendMessageRequest
import com.vladdev.shared.chats.dto.SendMessageResponse
import com.vladdev.shared.chats.dto.WsDeleteEvent
import com.vladdev.shared.chats.dto.WsMessageEvent
import com.vladdev.shared.chats.dto.WsStatusEvent
import com.vladdev.shared.storage.TokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.encodedPath
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
//    private val baseUrl = "https://f41585cb8b9516.lhr.life"
//    private val baseWsUrl = "wss://f41585cb8b9516.lhr.life"
    private val baseUrl = "http://192.168.31.191:8080"
    private val baseWsUrl = "ws://192.168.31.191:8080"
    private val wsConnections = mutableMapOf<String, DefaultClientWebSocketSession>()

    suspend inline fun <reified T> HttpResponse.safeBody(): T {
        if (!status.isSuccess()) throw Exception("HTTP ${status.value}")
        return body()
    }

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

    suspend fun openChatWebSocket(
        chatId: String,
        scope: CoroutineScope,
        onEvent: (WsIncomingEvent) -> Unit  // теперь тип известен
    ) {
        println("Opening WS session...")
        if (wsConnections.containsKey(chatId)) return

        val token = tokenStorage.getAccessToken()
            ?: throw IllegalStateException("No access token")

        val session = client.webSocketSession {
            println("WS URL = $baseUrl/ws/chats/$chatId")
            url {
                takeFrom(baseWsUrl)  // ws:// вместо http://
                encodedPath = "/ws/chats/$chatId"
                parameters.append("token", token)
            }

        }
        println("WS session created: ${session.isActive}")

        wsConnections[chatId] = session
        println("Connecting WS with token=${token.take(10)}...")


        // ChatApi.kt — в цикле обработки фреймов
        scope.launch {
            println("WS frame listener started")
            try {
                for (frame in session.incoming) {
                    println("WS frame received: $frame")
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        println("WS raw text: $text")

                        val type = Json.parseToJsonElement(text)
                            .jsonObject["type"]?.jsonPrimitive?.content
                        println("WS event type: $type")

                        val event: WsIncomingEvent = when (type) {
                            "message" -> IncomingMessage(
                                Json.decodeFromString<WsMessageEvent>(text).message
                            )
                            "status" -> {
                                val e = Json.decodeFromString<WsStatusEvent>(text)
                                IncomingStatus(e.messageId, e.userId, e.status)
                            }
                            "delete" -> {
                                val e = Json.decodeFromString<WsDeleteEvent>(text)
                                IncomingDelete(e.messageId, e.deleteForAll)
                            }
                            else -> {
                                println("WS unknown event type: $type, skipping")
                                continue  // <- ВАЖНО: continue вместо return@launch
                            }
                        }
                        println("WS event parsed: $event")
                        onEvent(event)
                    }
                }
                println("WS frame loop ended normally")
            } catch (e: Exception) {
                println("WS frame loop error: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    suspend fun sendMessageWS(chatId: String, encryptedContent: String) {
        val session = wsConnections[chatId] ?: throw IllegalStateException("WS not open")
        val event = WsMessageEvent(message = MessageDto(
            id = "", chatId = chatId, senderId = "",
            encryptedContent = encryptedContent, createdAt = 0
        ))
        val json = AppJson.encodeToString(event)
        println("Sending WS frame: $json")
        session.send(Frame.Text(json))
    }

    suspend fun sendReadWS(chatId: String, messageId: String, userId: String) {
        val session = wsConnections[chatId] ?: return
        val event = WsStatusEvent(type = "status", messageId = messageId, userId = userId, status = MessageStatus.READ)
        session.send(Frame.Text(AppJson.encodeToString(event)))
    }

    suspend fun deleteMessageWS(chatId: String, messageId: String, forAll: Boolean) {
        val session = wsConnections[chatId] ?: return
        val event = WsDeleteEvent(messageId = messageId, deleteForAll = forAll)
        session.send(Frame.Text(AppJson.encodeToString(event)))
    }
    suspend fun getMessages(chatId: String): List<MessageDto> =
        client.get("$baseUrl/chats/$chatId/messages").safeBody()
    suspend fun closeChatWebSocket(chatId: String) {
        wsConnections.remove(chatId)?.close()
    }
}

