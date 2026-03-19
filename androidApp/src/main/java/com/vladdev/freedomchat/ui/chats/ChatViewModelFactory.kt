package com.vladdev.freedomchat.ui.chats

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vladdev.shared.chats.ChatRepository
import com.vladdev.shared.crypto.E2eeManager

class ChatViewModelFactory(
    private val application: Application,
    private val repository: ChatRepository,
    private val e2ee: E2eeManager,
    private val chatId: String,
    private val currentUserId: String?,
    private val theirUserId: String,
    private val interlocutorName: String,
    private val currentUserName: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ChatViewModel(
            application   = application,
            repository    = repository,
            chatId        = chatId,
            currentUserId = currentUserId,
            e2ee          = e2ee,
            theirUserId   = theirUserId,
            interlocutorName  = interlocutorName,
            currentUserName = currentUserName
        ) as T
}