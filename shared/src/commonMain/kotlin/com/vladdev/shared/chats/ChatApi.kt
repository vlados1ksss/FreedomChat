package com.vladdev.shared.chats

import com.vladdev.shared.chats.dto.ChatDto
import com.vladdev.shared.chats.dto.ChatIdResponse
import com.vladdev.shared.chats.dto.ChatRequestDto
import com.vladdev.shared.chats.dto.RequestIdResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess

//class ChatApi(
//    private val client: HttpClient
//) {
//
//
//
//    suspend fun sendChatRequest(username: String) {
//
//        client.post("chats/request/$username")
//    }
//
//    suspend fun acceptRequest(requestId: String): ChatIdResponse {
//
//        return client.post("$baseUrl/chats/accept/$requestId")
//            .body()
//    }
//
//    suspend fun rejectRequest(requestId: String) {
//
//        client.post("$baseUrl/chats/reject/$requestId")
//    }
//}
class ChatApi(private val client: HttpClient) {
    private val baseUrl = "http://192.168.31.191:8080"

    suspend inline fun <reified T> HttpResponse.safeBody(): T {
        if (!status.isSuccess()) {
            throw Exception("HTTP ${status.value}")
        }
        return body()
    }

    suspend fun sendRequest(username: String): RequestIdResponse {
        return client.post("$baseUrl/chats/request/$username").body()
    }

    suspend fun getIncomingRequests(): List<ChatRequestDto> {
        val response = client.get("$baseUrl/chats/requests")
        return response.safeBody()
    }

    suspend fun getChats(): List<ChatDto> {
        val response = client.get("$baseUrl/chats")
        return response.safeBody()
    }

    suspend fun accept(requestId: String): ChatIdResponse {
        return client.post("$baseUrl/chats/accept/$requestId").body()
    }

    suspend fun reject(requestId: String) {
        client.post("$baseUrl/chats/reject/$requestId")
    }
}
