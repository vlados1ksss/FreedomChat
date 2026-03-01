package com.vladdev.freedomchat.ui.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vladdev.shared.chats.ChatRepository
import com.vladdev.shared.crypto.E2eeManager

class ChatViewModelFactory(
    private val repository: ChatRepository,
    private val e2ee: E2eeManager,
    private val chatId: String,
    private val currentUserId: String?,
    private val theirUserId: String
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            return ChatViewModel(
                repository    = repository,
                e2ee          = e2ee,
                chatId        = chatId,
                currentUserId = currentUserId,
                theirUserId   = theirUserId
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}