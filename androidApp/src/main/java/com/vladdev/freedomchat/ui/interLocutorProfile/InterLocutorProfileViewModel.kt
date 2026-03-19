package com.vladdev.freedomchat.ui.interLocutorProfile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vladdev.shared.chats.ChatRepository
import com.vladdev.shared.user.dto.PresenceResponse
import kotlinx.coroutines.launch

class InterlocutorProfileViewModel(
    private val chatRepository: ChatRepository,
    val theirUserId: String,
    val theirName: String,
    val theirUsername: String,
    val theirStatus: String,
    val existingChatId: String?,
) : ViewModel() {

    var presence by mutableStateOf<PresenceResponse?>(null)
        private set
    var isMuted by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var chatDeleted by mutableStateOf(false)
        private set
    var historyCleared by mutableStateOf(false)
        private set
    var resolvedChatId by mutableStateOf(existingChatId)
        private set

    // Для вкладок
    var selectedTab by mutableStateOf(0)
        private set

    init {
        loadPresence()
        existingChatId?.let { loadMuteStatus(it) }
    }

    private fun loadPresence() {
        viewModelScope.launch {
            chatRepository.getPresence(theirUserId)
                .onSuccess { presence = it }
        }
    }

    private fun loadMuteStatus(chatId: String) {
        viewModelScope.launch {
            isMuted = chatRepository.getChatMuted(chatId)
        }
    }

    fun selectTab(index: Int) { selectedTab = index }

    fun toggleMute() {
        val chatId = resolvedChatId ?: return
        viewModelScope.launch {
            val newMuted = !isMuted
            chatRepository.setChatMuted(chatId, newMuted)
            isMuted = newMuted
        }
    }

    fun openOrCreateChat(onReady: (chatId: String) -> Unit) {
        viewModelScope.launch {
            val existing = resolvedChatId
            if (existing != null) {
                onReady(existing)
                return@launch
            }
            isLoading = true
            chatRepository.createDirectChat(theirUserId)
                .onSuccess { chatId ->
                    resolvedChatId = chatId
                    onReady(chatId)
                }
                .onFailure { error = it.message }
            isLoading = false
        }
    }

    fun clearHistory(onCleared: () -> Unit) {
        val chatId = resolvedChatId ?: return
        viewModelScope.launch {
            isLoading = true
            chatRepository.clearHistory(chatId)
                .onSuccess { onCleared() }
                .onFailure { error = it.message }
            isLoading = false
        }
    }

    fun deleteChat(onDeleted: () -> Unit) {
        val chatId = resolvedChatId ?: return
        viewModelScope.launch {
            isLoading = true
            chatRepository.deleteChat(chatId)
                .onSuccess { onDeleted() }
                .onFailure { error = it.message }
            isLoading = false
        }
    }
}