package com.vladdev.freedomchat.ui.chats

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vladdev.freedomchat.MainApplication
import com.vladdev.shared.chats.ChatRepository
import com.vladdev.shared.chats.IncomingDelete
import com.vladdev.shared.chats.IncomingEdit
import com.vladdev.shared.chats.IncomingMessage
import com.vladdev.shared.chats.IncomingStatus
import com.vladdev.shared.chats.dto.DecryptedMessage
import com.vladdev.shared.chats.dto.MessageStatus
import com.vladdev.shared.chats.dto.MessageStatusDto
import com.vladdev.shared.crypto.E2eeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi
import java.util.UUID

@OptIn(InternalSerializationApi::class)
class ChatViewModel(
    application: Application,
    private val repository: ChatRepository,
    private val chatId: String,
    private val currentUserId: String?,
    private val e2ee: E2eeManager,
    private val theirUserId: String
) : AndroidViewModel(application) {

    private val app get() = getApplication<MainApplication>()
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

    var isMuted by mutableStateOf(false)
        private set
    var replyTo by mutableStateOf<DecryptedMessage?>(null)
        private set
    var editingMessage by mutableStateOf<DecryptedMessage?>(null)
        private set
    val selectedMessages = mutableStateListOf<String>()  // ids
    var isMultiSelectMode by mutableStateOf(false)
        private set

    init {
        app.activeChatId = chatId
        loadHistory()
        loadMuteStatus()
        connectWebSocket()
    }

    fun onMessageChange(text: String) {
        newMessage = text
    }
    fun startReply(message: DecryptedMessage) { replyTo = message }
    fun cancelReply() { replyTo = null }

    fun startEdit(message: DecryptedMessage) {
        editingMessage = message
        newMessage = message.text ?: ""
    }
    fun cancelEdit() {
        editingMessage = null
        newMessage = ""
    }

    fun toggleMessageSelection(messageId: String) {
        if (selectedMessages.contains(messageId)) {
            selectedMessages.remove(messageId)
            if (selectedMessages.isEmpty()) isMultiSelectMode = false
        } else {
            selectedMessages.add(messageId)
        }
    }

    fun enterMultiSelect(messageId: String) {
        isMultiSelectMode = true
        selectedMessages.clear()
        selectedMessages.add(messageId)
    }

    fun exitMultiSelect() {
        isMultiSelectMode = false
        selectedMessages.clear()
    }

    fun deleteSelectedMessages(forAll: Boolean) {
        val ids = selectedMessages.toList()
        viewModelScope.launch {
            try {
                repository.deleteMessages(chatId, ids, forAll)
                exitMultiSelect()
            } catch (e: Exception) {
                error = "Не удалось удалить сообщения"
            }
        }
    }
    fun sendMessage() {
        if (newMessage.isBlank()) return

        // Если редактируем
        editingMessage?.let { editing ->
            val updatedText = newMessage
            newMessage = ""
            editingMessage = null

            // Оптимистично обновляем UI сразу
            _messages.update { old ->
                old.map { msg ->
                    if (msg.id != editing.id) msg
                    else msg.copy(
                        text     = updatedText,
                        editedAt = System.currentTimeMillis()
                    )
                }
            }

            viewModelScope.launch {
                try {
                    repository.editMessage(chatId, editing.id, updatedText, theirUserId)
                } catch (e: Exception) {
                    // Откатываем при ошибке
                    _messages.update { old ->
                        old.map { msg ->
                            if (msg.id != editing.id) msg
                            else msg.copy(text = editing.text, editedAt = editing.editedAt)
                        }
                    }
                    error = "Не удалось изменить сообщение"
                }
            }
            return
        }

        val messageToSend = newMessage
        val reply = replyTo
        newMessage = ""
        replyTo = null

        viewModelScope.launch {
            val tempId = "optimistic_${UUID.randomUUID()}"
            val optimistic = DecryptedMessage(
                id = tempId,
                chatId = chatId,
                senderId = currentUserId ?: "",
                text = messageToSend,
                createdAt = System.currentTimeMillis(),
                deletedForAll = false,
                statuses = emptyList(),
                replyToId = reply?.id,
                replyToSenderId = reply?.senderId
            )
            _messages.update { listOf(optimistic) + it }
            try {
                repository.sendMessage(chatId, messageToSend, theirUserId, reply?.id)
                isScrollToBottomPending = true
            } catch (e: Exception) {
                _messages.update { it.filterNot { m -> m.id == tempId } }
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

                // Помечаем все непрочитанные входящие как прочитанные
                markAllIncomingAsRead(history)
            } catch (e: Exception) {
                Log.e(MainApplication.LogTags.CHAT_VM, "HISTORY ERROR", e)
            }
        }
    }

    private fun markAllIncomingAsRead(messages: List<DecryptedMessage>) {
        val myId = currentUserId ?: return
        viewModelScope.launch {
            messages
                .filter { msg ->
                    msg.senderId != myId &&
                            !msg.deletedForAll &&
                            msg.statuses.none { it.userId == myId && it.status == MessageStatus.READ }
                }
                .forEach { msg ->
                    repository.sendRead(chatId, msg.id, myId)
                }
        }
    }

    private fun connectWebSocket() {
        wsJob?.cancel()
        wsJob = viewModelScope.launch {

            val collectJob = launch {
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
                                    // Ищем optimistic по префиксу вместо пустого id
                                    val existing = old.firstOrNull {
                                        it.senderId == currentUserId && it.id.startsWith("optimistic_")
                                    }
                                    if (existing != null) {
                                        old.map {
                                            if (it.id == existing.id) it.copy(id = msg.id, statuses = msg.statuses)
                                            else it
                                        }
                                    } else {
                                        old
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
                            if (msg.senderId != currentUserId) {
                                currentUserId?.let {
                                    repository.sendRead(chatId, msg.id, it)
                                }
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
                                    old.filterNot { it.id == event.messageId }
                                } else {
                                    old.filterNot { it.id == event.messageId }
                                }
                            }
                        }

                        is IncomingEdit -> {
                            viewModelScope.launch {
                                val plaintext = if (event.senderId == currentUserId) {
                                    e2ee.getOutgoingPlaintext(chatId, event.messageId)
                                } else {
                                    e2ee.decryptMessage(chatId, event.encryptedContent)
                                }

                                _messages.update { old ->
                                    old.map { msg ->
                                        if (msg.id != event.messageId) msg
                                        else msg.copy(
                                            text     = plaintext ?: msg.text,
                                            editedAt = event.editedAt
                                        )
                                    }
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
                collectJob.cancel()
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

    // ChatViewModel.kt

    override fun onCleared() {
        app.activeChatId = null
        wsJob?.cancel()

        viewModelScope.launch(Dispatchers.IO) {
            repository.detachFromChat(chatId)  // ← было closeChat, теперь detachFromChat
        }

        super.onCleared()
    }
    private fun loadMuteStatus() {
        viewModelScope.launch {
            isMuted = repository.getChatMuted(chatId)
        }
    }

    fun toggleMute() {
        viewModelScope.launch {
            val newMuted = !isMuted
            repository.setChatMuted(chatId, newMuted)
            isMuted = newMuted
        }
    }
}
