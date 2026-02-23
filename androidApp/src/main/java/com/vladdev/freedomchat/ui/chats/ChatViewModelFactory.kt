package com.vladdev.freedomchat.ui.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vladdev.shared.chats.ChatRepository

class ChatViewModelFactory(
    private val repository: ChatRepository,
    private val chatId: String,
    private val currentUserId: String?
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            return ChatViewModel(
                repository,
                chatId,
                currentUserId
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}