package com.vladdev.freedomchat.ui.chats

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vladdev.freedomchat.MainApplication
import com.vladdev.shared.chats.ChatRepository
import com.vladdev.shared.chats.IncomingDelete
import com.vladdev.shared.chats.IncomingMessage
import com.vladdev.shared.chats.IncomingStatus
import com.vladdev.shared.chats.dto.MessageDto
import com.vladdev.shared.chats.dto.MessageStatusDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi
import java.util.UUID

@OptIn(InternalSerializationApi::class)
class ChatViewModel(
    private val repository: ChatRepository,
    private val chatId: String,
    private val currentUserId: String?
) : ViewModel() {

    private val _messages = MutableStateFlow<List<MessageDto>>(emptyList())
    val messages = _messages.asStateFlow()

    var newMessage by mutableStateOf("")
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var isConnected by mutableStateOf(false)
        private set

    var lastSentMessageId by mutableStateOf<String?>(null)
        private set

    private var wsJob: Job? = null
    var isScrollToBottomPending by mutableStateOf(false)
        private set

    init {
        loadHistory()
        connectWebSocket()
    }

    fun onMessageChange(text: String) {
        newMessage = text
    }

    fun sendMessage() {
        if (newMessage.isBlank()) return

        val messageToSend = newMessage
        newMessage = ""

        viewModelScope.launch {
            try {
                repository.sendMessage(chatId, messageToSend)
                isScrollToBottomPending = true
            } catch (e: Exception) {
                error = "Не удалось отправить сообщение"
            }
        }
    }

    fun onScrolledToBottom() {
        isScrollToBottomPending = false
    }

    private fun loadHistory() {
        viewModelScope.launch {
            try {
                val history = repository.getMessages(chatId)

                _messages.value =
                    history.sortedByDescending { it.createdAt }

            } catch (e: Exception) {
                Log.e(MainApplication.LogTags.CHAT_VM, "HISTORY ERROR", e)
            }
        }
    }

    private fun connectWebSocket() {
        wsJob?.cancel()

        wsJob = viewModelScope.launch {

            launch {
                repository.eventsFlow(chatId).collect { event ->

                    when (event) {

                        is IncomingMessage -> {

                            _messages.update { old ->

                                if (old.any { it.id == event.message.id })
                                    return@update old

                                val insertIndex = old.indexOfFirst {
                                    it.createdAt < event.message.createdAt
                                }.let { if (it == -1) old.size else it }

                                buildList {
                                    addAll(old)
                                    add(insertIndex, event.message)
                                }
                            }

                            if (event.message.senderId != currentUserId) {
                                currentUserId?.let {
                                    repository.sendRead(
                                        chatId,
                                        event.message.id,
                                        it
                                    )
                                }
                            }
                        }

                        is IncomingStatus -> {

                            _messages.update { old ->
                                old.map { msg ->
                                    if (msg.id != event.messageId) return@map msg

                                    val newStatuses =
                                        msg.statuses
                                            .filter { it.userId != event.userId } +
                                                MessageStatusDto(
                                                    event.messageId,
                                                    event.userId,
                                                    event.status
                                                )

                                    msg.copy(statuses = newStatuses)
                                }
                            }
                        }

                        is IncomingDelete -> {

                            _messages.update { old ->

                                if (event.deleteForAll) {
                                    old.map {
                                        if (it.id == event.messageId)
                                            it.copy(
                                                encryptedContent = "Сообщение удалено",
                                                deletedForAll = true
                                            )
                                        else it
                                    }
                                } else {
                                    old.filterNot { it.id == event.messageId }
                                }
                            }
                        }
                    }
                }
            }

            try {
                repository.openChat(chatId, viewModelScope)
                isConnected = true
            } catch (e: Exception) {
                isConnected = false
            }
        }
    }

    fun deleteMessage(messageId: String, forAll: Boolean) {
        viewModelScope.launch {
            try {
                repository.deleteMessage(chatId, messageId, forAll)
            } catch (e: Exception) {
                error = "Не удалось удалить сообщение"
            }
        }
    }

    override fun onCleared() {
        wsJob?.cancel()

        viewModelScope.launch(Dispatchers.IO) {
            repository.closeChat(chatId)
        }

        super.onCleared()
    }
}
