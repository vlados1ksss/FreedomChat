package com.vladdev.shared.chats

import com.vladdev.shared.auth.dto.PublicKeyResponse
import com.vladdev.shared.chats.dto.ChatDto
import com.vladdev.shared.chats.dto.ChatIdResponse
import com.vladdev.shared.chats.dto.ChatRequestDto
import com.vladdev.shared.chats.dto.ForwardedMessagePayload
import com.vladdev.shared.chats.dto.MediaUploadResponse
import com.vladdev.shared.chats.dto.MessageDto
import com.vladdev.shared.chats.dto.MessageStatus
import com.vladdev.shared.chats.dto.RequestIdResponse
import com.vladdev.shared.chats.dto.SearchUserResponse
import com.vladdev.shared.chats.dto.WsDeleteEvent
import com.vladdev.shared.chats.dto.WsEditEvent
import com.vladdev.shared.chats.dto.WsChatDeletedEvent
import com.vladdev.shared.chats.dto.WsHistoryClearedEvent
import com.vladdev.shared.chats.dto.WsMessageEvent
import com.vladdev.shared.chats.dto.WsPinEvent
import com.vladdev.shared.chats.dto.WsReactionEvent
import com.vladdev.shared.chats.dto.WsStatusEvent
import com.vladdev.shared.chats.dto.WsTypingEvent
import com.vladdev.shared.storage.TokenStorage
import com.vladdev.shared.user.dto.PresenceResponse
import com.vladdev.shared.user.dto.UserProfileResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.cancellation.CancellationException

@OptIn(InternalSerializationApi::class)
class ChatApi(private val client: HttpClient, private val tokenStorage: TokenStorage) {

    val AppJson = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    private val baseUrl = "http://176.124.199.31/api/"
    private val baseMediaUrl = "http://176.124.199.31/api/media"

    private val wsConnections = mutableMapOf<String, DefaultClientWebSocketSession>()
    private val wsReconnectJobs = mutableMapOf<String, Job>()
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
    suspend fun getUserById(userId: String): UserProfileResponse =
        client.get("$baseUrl/profile/$userId/public").safeBody()

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
                takeFrom("ws://176.124.199.31")
                encodedPath = "/api/ws/chats/$chatId"
                parameters.append("token", token)
            }
        }
        connectWithRetry(chatId, scope, onEvent)
        wsConnections[chatId] = session

        scope.launch {
            try {
                for (frame in session.incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        println("WS FRAME received for $chatId: $text")
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
                                IncomingEdit(e.messageId, e.senderId, e.encryptedContent, e.editedAt)
                            }
                            "typing" -> {
                                println("Typing WS event received: $text")
                                val e = Json.decodeFromString<WsTypingEvent>(text)
                                IncomingTyping(e.userId, e.isTyping)
                            }
                            "chat_deleted"     -> {
                                val e = Json.decodeFromString<WsChatDeletedEvent>(text)
                                IncomingChatDeleted(e.chatId)
                            }
                            "history_cleared"  -> {
                                val e = Json.decodeFromString<WsHistoryClearedEvent>(text)
                                IncomingHistoryCleared(e.chatId)
                            }
                            "reaction" -> {
                                val e = Json.decodeFromString<WsReactionEvent>(text)
                                IncomingReaction(
                                    messageId    = e.messageId,
                                    emoji        = e.emoji,
                                    userId       = e.userId,
                                    remove       = e.remove,
                                    createdAt    = e.createdAt,
                                    replacedEmoji = e.replacedEmoji
                                )
                            }
                            else -> continue
                        }
                        onEvent(event)
                    }
                }
            } catch (e: Exception) {
                println("WS frame loop DIED for $chatId: ${e.message}")
                wsConnections.remove(chatId)
            }
        }
    }
    private fun connectWithRetry(
        chatId: String,
        scope: CoroutineScope,
        onEvent: (WsIncomingEvent) -> Unit
    ) {
        wsReconnectJobs[chatId]?.cancel()
        wsReconnectJobs[chatId] = scope.launch {
            var attempt = 0
            while (isActive) {
                try {
                    println("WS connecting for $chatId, attempt=$attempt")
                    val token = tokenStorage.getAccessToken()
                        ?: throw IllegalStateException("No access token")

                    val session = client.webSocketSession {
                        url {
                            takeFrom("ws://176.124.199.31")
                            encodedPath = "/api/ws/chats/$chatId"
                            parameters.append("token", token)
                        }
                    }

                    wsConnections[chatId] = session
                    attempt = 0  // сброс счётчика при успехе
                    println("WS connected for $chatId")

                    // Читаем фреймы
                    try {
                        for (frame in session.incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                parseAndEmit(chatId, text, onEvent)
                            }
                        }
                    } catch (e: Exception) {
                        println("WS read error for $chatId: ${e.message}")
                    } finally {
                        wsConnections.remove(chatId)
                        println("WS disconnected for $chatId")
                    }

                } catch (e: CancellationException) {
                    throw e  // не перехватываем отмену корутины
                } catch (e: Exception) {
                    wsConnections.remove(chatId)
                    println("WS connection failed for $chatId: ${e.message}")
                }

                if (!isActive) break

                // Экспоненциальная задержка: 1, 2, 4, 8, 16 сек максимум
                val delayMs = minOf(1000L * (1 shl attempt), 16_000L)
                attempt = minOf(attempt + 1, 4)
                println("WS reconnecting for $chatId in ${delayMs}ms")
                delay(delayMs)
            }
        }
    }

    // Парсинг вынести в отдельный метод:
    private fun parseAndEmit(
        chatId: String,
        text: String,
        onEvent: (WsIncomingEvent) -> Unit
    ) {
        try {
            val json = Json { ignoreUnknownKeys = true }
            val type = json.parseToJsonElement(text)
                .jsonObject["type"]?.jsonPrimitive?.content

            val event: WsIncomingEvent = when (type) {
                "message" -> {
                    val e = json.decodeFromString<WsMessageEvent>(text)
                    // resolvePendingMessage нужен для ЛЮБОГО сообщения — текст или медиа
                    if (e.message.id.isNotEmpty()) {
                        resolvePendingMessage(chatId, e.message.id)
                    }
                    IncomingMessage(e.message)
                }
                "status"  -> {
                    val e = json.decodeFromString<WsStatusEvent>(text)
                    IncomingStatus(e.messageId, e.userId, e.status)
                }
                "delete"  -> {
                    val e = json.decodeFromString<WsDeleteEvent>(text)
                    IncomingDelete(e.messageId, e.deleteForAll)
                }
                "edit"    -> {
                    val e = json.decodeFromString<WsEditEvent>(text)
                    IncomingEdit(e.messageId, e.senderId, e.encryptedContent, e.editedAt)
                }
                "pin"     -> {
                    val e = json.decodeFromString<WsPinEvent>(text)
                    IncomingPin(e.messageId, e.unpin)
                }
                "typing"  -> {
                    val e = json.decodeFromString<WsTypingEvent>(text)
                    IncomingTyping(e.userId, e.isTyping)
                }
                "chat_deleted" -> {
                    val e = json.decodeFromString<WsChatDeletedEvent>(text)
                    IncomingChatDeleted(e.chatId)
                }
                "history_cleared" -> {
                    val e = json.decodeFromString<WsHistoryClearedEvent>(text)
                    IncomingHistoryCleared(e.chatId)
                }
                "reaction" -> {
                    val e = Json.decodeFromString<WsReactionEvent>(text)
                    IncomingReaction(e.messageId, e.emoji, e.userId, e.remove, e.createdAt, e.replacedEmoji)
                }
                else -> return
            }
            onEvent(event)
        } catch (e: Exception) {
            println("WS parse error for $chatId: ${e.message}")
        }
    }

    suspend fun sendMessageWS(
        chatId: String,
        encryptedContent: String,
        replyToId: String? = null,
        replyToPreview: String? = null
    ): String {
        val session = wsConnections[chatId]
        if (session == null || !session.isActive) {
            throw IllegalStateException("WS not connected for $chatId")
        }

        println("WS send attempt for $chatId, session isActive=${session.isActive}")
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
        return withTimeout(15000) { pendingId.await() }
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
        wsReconnectJobs.remove(chatId)?.cancel()
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

    suspend fun sendForward(
        targetChatId: String,
        payloads: List<ForwardedMessagePayload>
    ): List<String> {
        return client.post("$baseUrl/chats/$targetChatId/forward") {
            contentType(ContentType.Application.Json)
            setBody(payloads)
        }.body<List<String>>()
    }

    suspend fun sendPinWS(chatId: String, messageId: String, unpin: Boolean) {
        val session = wsConnections[chatId] ?: throw IllegalStateException("WS not open")
        session.send(Frame.Text(AppJson.encodeToString(
            WsPinEvent(
                messageId = messageId,
                unpin = unpin
            )
        )))
    }

    suspend fun getPinnedMessages(chatId: String): List<MessageDto> =
        client.get("$baseUrl/chats/$chatId/pinned").safeBody()

    suspend fun sendTypingWS(chatId: String, isTyping: Boolean) {
        val session = wsConnections[chatId] ?: return
        session.send(Frame.Text(AppJson.encodeToString(
            WsTypingEvent(isTyping = isTyping)
        )))
    }
    suspend fun getPresence(userId: String): PresenceResponse =
        client.get("$baseUrl/profile/$userId/presence").safeBody()

    suspend fun deleteChat(chatId: String) {
        client.delete("$baseUrl/chats/$chatId")
    }

    suspend fun clearHistory(chatId: String) {
        client.delete("$baseUrl/chats/$chatId/history")
    }

    suspend fun sendReactionWS(
        chatId: String,
        messageId: String,
        emoji: String,
        userId: String,
        remove: Boolean
    ) {
        val session = wsConnections[chatId] ?: return
        session.send(Frame.Text(AppJson.encodeToString(
            WsReactionEvent(
                messageId = messageId,
                emoji     = emoji,
                userId    = userId,
                remove    = remove
            )
        )))
    }

    suspend fun uploadMedia(
        chatId: String?,
        type: String,                     // "PHOTO"|"VIDEO"|"VOICE"|"VIDEO_NOTE"|"AVATAR"
        mimeType: String,
        originalFileName: String,
        encryptedFile: ByteArray,
        encryptedThumb: ByteArray?,
        metadataJson: String?
    ): MediaUploadResponse {
        return client.submitFormWithBinaryData(
            url = "$baseMediaUrl/upload",
            formData = formData {
                append("type",     type)
                append("mimeType", mimeType)
                chatId?.let { append("chatId", it) }
                metadataJson?.let { append("metadata", it) }

                append(
                    "file",
                    encryptedFile,
                    Headers.build {
                        append(
                            HttpHeaders.ContentDisposition,
                            "form-data; name=\"file\"; filename=\"$originalFileName\"")
                        append(HttpHeaders.ContentType, "application/octet-stream")
                    }
                )

                encryptedThumb?.let { thumb ->
                    append(
                        "thumb",
                        thumb,
                        Headers.build {
                            append(HttpHeaders.ContentDisposition,
                                "form-data; name=\"thumb\"; filename=\"thumb_$originalFileName\"")
                            append(HttpHeaders.ContentType, "application/octet-stream")
                        }
                    )
                }
            }
        ).safeBody()
    }
    suspend fun downloadMedia(mediaId: String, chatId: String?): ByteArray {
        val url = buildString {
            append("$baseMediaUrl/$mediaId")
            if (chatId != null) append("?chatId=$chatId")
        }
        return client.get(url).body()
    }

    /** Скачать зашифрованное превью. */
    suspend fun downloadThumb(mediaId: String, chatId: String?): ByteArray {
        val url = buildString {
            append("$baseMediaUrl/$mediaId/thumb")
            if (chatId != null) append("?chatId=$chatId")
        }
        return client.get(url).body()
    }

    /** Отправить WS-событие с mediaId. */
    suspend fun sendMessageWithMediaWS(
        chatId: String,
        encryptedContent: String,         // зашифрованная подпись (может быть "")
        mediaId: String,
        replyToId: String? = null
    ): String {
        val session = wsConnections[chatId]
        if (session == null || !session.isActive) {
            throw IllegalStateException("WS not connected for $chatId")
        }

        val pendingId = CompletableDeferred<String>()
        pendingMessages[chatId] = pendingId

        val event = WsMessageEvent(
            message = MessageDto(
                id               = "",
                chatId           = chatId,
                senderId         = "",
                encryptedContent = encryptedContent,
                createdAt        = 0
            ),
            replyToId = replyToId,
            mediaId   = mediaId             // отдельное поле в WsMessageEvent
        )
        session.send(Frame.Text(AppJson.encodeToString(event)))
        return withTimeout(15000) { pendingId.await() }
    }
}