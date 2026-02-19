package com.vladdev.freedomchat.ui.chats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vladdev.shared.chats.ChatRepository
import com.vladdev.shared.chats.dto.ChatDto
import com.vladdev.shared.chats.dto.ChatRequestDto
import kotlinx.coroutines.launch
import kotlin.collections.emptyList

class ChatsViewModel(
    private val repository: ChatRepository
) : ViewModel() {

    var chats by mutableStateOf<List<ChatDto>>(emptyList())
        private set

    var incomingRequests by mutableStateOf<List<ChatRequestDto>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            isLoading = true

            repository.loadChats()
                .onSuccess { chats = it }
                .onFailure { error = it.message }

            repository.loadRequests()
                .onSuccess { incomingRequests = it }
                .onFailure { error = it.message }

            isLoading = false
        }
    }

    fun sendRequest(username: String) {
        viewModelScope.launch {
            repository.sendRequest(username)
                .onFailure { error = it.message }
        }
    }

    fun acceptRequest(requestId: String) {
        viewModelScope.launch {
            repository.accept(requestId)
                .onSuccess {
                    refresh()
                }
                .onFailure { error = it.message }
        }
    }

    fun rejectRequest(requestId: String) {
        viewModelScope.launch {
            repository.reject(requestId)
                .onSuccess { refresh() }
                .onFailure { error = it.message }
        }
    }
}

