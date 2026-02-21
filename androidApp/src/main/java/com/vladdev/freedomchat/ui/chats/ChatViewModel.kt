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
import com.vladdev.shared.chats.dto.MessageDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(
    private val repository: ChatRepository,
    private val chatId: String,
    private val currentUserId: String?
) : ViewModel() {

    private val _messages = mutableStateListOf<MessageDto>()
    val messages: List<MessageDto> = _messages

    var newMessage by mutableStateOf("")
        private set

    var error by mutableStateOf<String?>(null)
        private set

    private var wsJob: Job? = null

    var isConnected by mutableStateOf(false)
        private set


    init {
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
            Log.d(MainApplication.LogTags.CHAT_VM, "Sending message: $messageToSend")

            try {
                repository.sendMessage(chatId, messageToSend)
                Log.d(MainApplication.LogTags.CHAT_VM, "Message sent to repository")
            } catch (e: Exception) {
                Log.e(MainApplication.LogTags.CHAT_VM, "SEND ERROR", e)
                error = "Не удалось отправить сообщение"
            }
        }
    }

    private fun connectWebSocket() {
        wsJob?.cancel()


        wsJob = viewModelScope.launch {
            Log.d(MainApplication.LogTags.CHAT_VM, "Connecting WS chat=$chatId")
            try {
                repository.openChat(chatId, viewModelScope)
                Log.d(MainApplication.LogTags.CHAT_VM, "WS openChat success")
                isConnected = true

                repository.messagesFlow(chatId)
                    .collect { message ->
                        Log.d(
                            MainApplication.LogTags.CHAT_VM,
                            "Message received id=${message.id}"
                        )
                        if (_messages.none { it.id == message.id }) {
                            _messages.add(message)
                            _messages.sortBy { it.createdAt }
                        }
                    }

            } catch (e: Exception) {
                isConnected = false
                Log.e(MainApplication.LogTags.CHAT_VM, "WS ERROR", e)
                error = "Ошибка подключения к чату"
            }
        }
    }


//    fun deleteMessage(messageId: String) {
//        viewModelScope.launch {
//            repository.deleteMessage(chatId, messageId).onSuccess {
//                _messages.removeAll { it.id == messageId }
//            }.onFailure {
//                error = "Не удалось удалить сообщение"
//            }
//        }
//    }


    override fun onCleared() {
        wsJob?.cancel()

        CoroutineScope(Dispatchers.IO).launch {
            repository.closeChat(chatId)
        }

        super.onCleared()
    }

}
