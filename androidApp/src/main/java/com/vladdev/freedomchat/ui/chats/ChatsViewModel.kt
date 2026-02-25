package com.vladdev.freedomchat.ui.chats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vladdev.shared.chats.ChatRepository
import com.vladdev.shared.chats.dto.ChatDto
import com.vladdev.shared.chats.dto.ChatRequestDto
import com.vladdev.shared.chats.dto.SearchUserResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi
import kotlin.collections.emptyList

@OptIn(InternalSerializationApi::class)
class ChatsViewModel(private val repository: ChatRepository) : ViewModel() {

    var chats by mutableStateOf<List<ChatDto>>(emptyList())
        private set
    var incomingRequests by mutableStateOf<List<ChatRequestDto>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    // Поиск
    var searchQuery by mutableStateOf("")
        private set
    var searchResult by mutableStateOf<SearchUserResponse?>(null)
        private set
    var isSearching by mutableStateOf(false)
        private set

    private var searchJob: Job? = null

    init { refresh() }

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

    fun onSearchQueryChange(query: String) {
        searchQuery = query
        searchJob?.cancel()
        if (query.isBlank()) {
            searchResult = null
            return
        }
        searchJob = viewModelScope.launch {
            delay(400) // debounce
            isSearching = true
            repository.searchUser(query.trimStart('@'))
                .onSuccess { searchResult = it }
                .onFailure { searchResult = null }
            isSearching = false
        }
    }

    fun clearSearch() {
        searchQuery = ""
        searchResult = null
        searchJob?.cancel()
    }

    // Создаём чат и возвращаем chatId через колбэк
    fun openOrCreateChat(
        userId: String,
        existingChatId: String?,
        onReady: (chatId: String, name: String) -> Unit
    ) {
        viewModelScope.launch {
            if (existingChatId != null) {
                val name = searchResult?.user?.name ?: searchResult?.user?.username ?: ""
                onReady(existingChatId, name)
            } else {
                repository.createDirectChat(userId)
                    .onSuccess { chatId ->
                        refresh()
                        val name = searchResult?.user?.name ?: searchResult?.user?.username ?: ""
                        onReady(chatId, name)
                    }
                    .onFailure { error = it.message }
            }
        }
    }

    fun acceptRequest(requestId: String) {
        viewModelScope.launch {
            repository.accept(requestId)
                .onSuccess { refresh() }
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

