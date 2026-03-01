package com.vladdev.freedomchat.ui.chats

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vladdev.freedomchat.MainApplication
import com.vladdev.shared.chats.ChatRepository
import com.vladdev.shared.chats.IncomingDelete
import com.vladdev.shared.chats.IncomingMessage
import com.vladdev.shared.chats.IncomingStatus
import com.vladdev.shared.chats.dto.DecryptedMessage
import com.vladdev.shared.chats.dto.MessageStatusDto
import com.vladdev.shared.crypto.E2eeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi

@OptIn(InternalSerializationApi::class)
class ChatViewModel(
    private val repository: ChatRepository,
    private val chatId: String,
    private val currentUserId: String?,
    private val e2ee: E2eeManager,
    private val theirUserId: String,
) : ViewModel() {

    private val _messages = MutableStateFlow<List<DecryptedMessage>>(emptyList())
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
            // Optimistic insert — показываем сразу с известным текстом
            val optimistic = DecryptedMessage(
                id            = "",   // id придёт с сервера через эхо
                chatId        = chatId,
                senderId      = currentUserId ?: "",
                text          = messageToSend,
                createdAt     = System.currentTimeMillis(),
                deletedForAll = false,
                statuses      = emptyList()
            )
            _messages.update { listOf(optimistic) + it }

            try {
                repository.sendMessage(chatId, messageToSend, theirUserId)
                isScrollToBottomPending = true
            } catch (e: Exception) {
                // Откатываем optimistic insert при ошибке
                _messages.update { it.filterNot { m -> m === optimistic } }
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
                val history = repository.getMessages(chatId, currentUserId ?: "")
                _messages.value = history.sortedByDescending { it.createdAt }
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
                            val msg = event.message

                            val plaintext = when {
                                msg.deletedForAll -> null
                                msg.senderId == currentUserId -> null  // своё сообщение — текст уже есть в UI
                                else -> e2ee.decryptMessage(chatId, msg.encryptedContent)
                            }

                            // Если это своё сообщение пришедшее эхом — обновляем только статусы,
                            // текст берём из уже добавленного optimistic сообщения
                            if (msg.senderId == currentUserId) {
                                _messages.update { old ->
                                    // Если сообщение уже есть (optimistic insert) — обновляем id и статусы
                                    val existing = old.firstOrNull {
                                        it.senderId == currentUserId && it.id.isEmpty()
                                    }
                                    if (existing != null) {
                                        old.map {
                                            if (it === existing) it.copy(id = msg.id, statuses = msg.statuses)
                                            else it
                                        }
                                    } else {
                                        // Эхо без optimistic — добавляем с известным текстом из sendMessage
                                        old  // просто игнорируем эхо, текст уже показан
                                    }
                                }
                                return@collect
                            }

                            // Чужое сообщение — расшифровываем
                            val decrypted = DecryptedMessage(
                                id            = msg.id,
                                chatId        = msg.chatId,
                                senderId      = msg.senderId,
                                text          = plaintext,
                                createdAt     = msg.createdAt,
                                deletedForAll = msg.deletedForAll,
                                statuses      = msg.statuses
                            )

                            _messages.update { old ->
                                if (old.any { it.id == decrypted.id }) return@update old
                                val insertIndex = old.indexOfFirst {
                                    it.createdAt < decrypted.createdAt
                                }.let { if (it == -1) old.size else it }
                                buildList {
                                    addAll(old)
                                    add(insertIndex, decrypted)
                                }
                            }

                            currentUserId?.let {
                                repository.sendRead(chatId, msg.id, it)
                            }
                        }

                        is IncomingStatus -> {
                            _messages.update { old ->
                                old.map { msg ->
                                    if (msg.id != event.messageId) return@map msg
                                    val newStatuses = msg.statuses
                                        .filter { it.userId != event.userId } +
                                            MessageStatusDto(event.messageId, event.userId, event.status)
                                    msg.copy(statuses = newStatuses)
                                }
                            }
                        }

                        is IncomingDelete -> {
                            _messages.update { old ->
                                if (event.deleteForAll) {
                                    old.map {
                                        if (it.id == event.messageId)
                                            it.copy(deletedForAll = true, text = null)
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
