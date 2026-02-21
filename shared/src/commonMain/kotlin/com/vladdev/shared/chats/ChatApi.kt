package com.vladdev.shared.chats

import com.vladdev.shared.chats.dto.ChatDto
import com.vladdev.shared.chats.dto.ChatIdResponse
import com.vladdev.shared.chats.dto.ChatRequestDto
import com.vladdev.shared.chats.dto.MessageDto
import com.vladdev.shared.chats.dto.RequestIdResponse
import com.vladdev.shared.chats.dto.SendMessageRequest
import com.vladdev.shared.chats.dto.SendMessageResponse
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
import kotlinx.serialization.encodeToString

class ChatApi(private val client: HttpClient,private val tokenStorage: TokenStorage) {

    private val baseUrl = "http://192.168.31.191:8080"
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
        onMessage: (MessageDto) -> Unit
    ) {
        println("Opening WS session...")
        if (wsConnections.containsKey(chatId)) return

        val token = tokenStorage.getAccessToken()
            ?: throw IllegalStateException("No access token")

        val session = client.webSocketSession {
            println("WS URL = $baseUrl/ws/chats/$chatId")
            url {
                takeFrom(baseUrl)
                encodedPath = "/ws/chats/$chatId"

                parameters.append("token", token)
            }

        }
        println("WS session created: ${session.isActive}")

        wsConnections[chatId] = session
        println("Connecting WS with token=${token.take(10)}...")


        scope.launch {
            println("WS listening started")
            try {
                for (frame in session.incoming) {
                    println("Frame received")
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        println("RAW MESSAGE: $text")
                        val message = kotlinx.serialization.json.Json.decodeFromString<MessageDto>(text)
                        onMessage(message)
                    }
                }
            } catch (e: Exception) {
                println("WS closed for chat $chatId: ${e.message}")
            } finally {
                wsConnections.remove(chatId)
                session.cancel()
            }
        }
    }

    suspend fun sendMessageWS(chatId: String, encryptedContent: String) {
        val session = wsConnections[chatId]
            ?: throw IllegalStateException("WebSocket for chat $chatId not open")
        val messageRequest = SendMessageRequest(encryptedContent)
        val jsonText = kotlinx.serialization.json.Json.encodeToString(messageRequest)
        println("Sending WS message: $jsonText")
        session.send(Frame.Text(jsonText))
        println("WS message sent")
    }

    suspend fun closeChatWebSocket(chatId: String) {
        wsConnections.remove(chatId)?.close()
    }
}

