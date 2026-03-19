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
import com.vladdev.shared.chats.IncomingChatDeleted
import com.vladdev.shared.chats.IncomingDelete
import com.vladdev.shared.chats.IncomingEdit
import com.vladdev.shared.chats.IncomingHistoryCleared
import com.vladdev.shared.chats.IncomingMessage
import com.vladdev.shared.chats.IncomingPin
import com.vladdev.shared.chats.IncomingStatus
import com.vladdev.shared.chats.IncomingTyping
import com.vladdev.shared.chats.dto.DecryptedMessage
import com.vladdev.shared.chats.dto.MessageStatus
import com.vladdev.shared.chats.dto.MessageStatusDto
import com.vladdev.shared.crypto.E2eeManager
import com.vladdev.shared.user.dto.PresenceResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    private val theirUserId: String,
    private val interlocutorName: String,
    private val currentUserName: String
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

    val pinnedMessages = mutableStateListOf<DecryptedMessage>()
    var showForwardDialog by mutableStateOf(false)
        private set
    var messagesToForward by mutableStateOf<List<DecryptedMessage>>(emptyList())
        private set
    var interlocutorPresence by mutableStateOf<String>("")
        private set
    var isInterlocutorTyping by mutableStateOf(false)
        private set
    sealed class ChatUiEvent {
        object ChatDeleted : ChatUiEvent()
        object HistoryCleared : ChatUiEvent()
    }

    private val _uiEvents = MutableSharedFlow<ChatUiEvent>(extraBufferCapacity = 1)
    val uiEvents: SharedFlow<ChatUiEvent> = _uiEvents.asSharedFlow()
    private var typingClearJob: Job? = null
    private var presenceJob: Job? = null
    private var typingDebounceJob: Job? = null
    init {
        app.activeChatId = chatId
        loadHistory()
        loadMuteStatus()
        connectWebSocket()
        loadPinnedMessages()
        startPresencePolling()
    }

    fun onMessageChange(text: String) {
        newMessage = text
        typingDebounceJob?.cancel()
        if (text.isNotBlank()) {
            viewModelScope.launch { repository.sendTyping(chatId, true) }
            typingDebounceJob = viewModelScope.launch {
                delay(3_000)
                repository.sendTyping(chatId, false)
            }
        } else {
            viewModelScope.launch { repository.sendTyping(chatId, false) }
        }
    }
    fun silentRefresh() {
        viewModelScope.launch {
            try {
                val history = repository.getMessages(chatId, currentUserId ?: "")
                val sorted = history.sortedByDescending { it.createdAt }
                // Обновляем только если данные изменились — не сбрасываем scroll
                if (sorted.map { it.id } != _messages.value.map { it.id }) {
                    _messages.value = sorted
                }
            } catch (e: Exception) {
                // тихо игнорируем
            }
        }
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

                            if (msg.senderId == currentUserId) {
                                _messages.update { old ->
                                    val existing = old.firstOrNull {
                                        it.senderId == currentUserId &&
                                                it.id.startsWith("optimistic_") &&
                                                it.text == (e2ee.getOutgoingPlaintext(chatId, msg.id) ?: "") // Или сравнение с расшифрованным текстом
                                    }
                                    if (existing != null) {
                                        old.map {
                                            if (it.id == existing.id) it.copy(
                                                id                = msg.id,
                                                statuses          = msg.statuses,
                                                // Берём из сервера только если не null, иначе оставляем из optimistic
                                                forwardedFromId = msg.forwardedFromId?.takeIf { it.isNotBlank() } ?: it.forwardedFromId,
                                                forwardedFromName = msg.forwardedFromName?.takeIf { it.isNotBlank() } ?: it.forwardedFromName
                                            )
                                            else it
                                        }
                                    } else if (old.none { it.id == msg.id }) {
                                        val plaintext = e2ee.getOutgoingPlaintext(chatId, msg.id)
                                        if (plaintext != null) {
                                            val decrypted = DecryptedMessage(
                                                id                = msg.id,
                                                chatId            = msg.chatId,
                                                senderId          = msg.senderId,
                                                text              = plaintext,
                                                createdAt         = msg.createdAt,
                                                deletedForAll     = false,
                                                statuses          = msg.statuses,
                                                forwardedFromId   = msg.forwardedFromId,
                                                forwardedFromName = msg.forwardedFromName
                                            )
                                            listOf(decrypted) + old
                                        } else old
                                    } else old
                                }
                                return@collect
                            }

                            // Чужое сообщение — без изменений
                            val plaintext = when {
                                msg.deletedForAll -> null
                                else -> e2ee.decryptMessage(chatId, msg.encryptedContent)
                            }

                            val decrypted = DecryptedMessage(
                                id                = msg.id,
                                chatId            = msg.chatId,
                                senderId          = msg.senderId,
                                text              = plaintext,
                                createdAt         = msg.createdAt,
                                deletedForAll     = msg.deletedForAll,
                                statuses          = msg.statuses,
                                forwardedFromId   = msg.forwardedFromId,
                                forwardedFromName = msg.forwardedFromName
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

                            currentUserId?.let { repository.sendRead(chatId, msg.id, it) }
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
                            println("IncomingEdit received: messageId=${event.messageId}, editedAt=${event.editedAt}")
                            viewModelScope.launch {
                                val plaintext = if (event.senderId == currentUserId) {
                                    e2ee.getOutgoingPlaintext(chatId, event.messageId)
                                } else {
                                    e2ee.decryptMessage(chatId, event.encryptedContent)
                                        .also { if (it == null) println("IncomingEdit: decrypt failed for ${event.messageId}") }
                                }

                                _messages.update { old ->
                                    old.map { msg ->
                                        if (msg.id != event.messageId) msg
                                        else msg.copy(
                                            text     = plaintext ?: msg.text,
                                            editedAt = event.editedAt ?: System.currentTimeMillis()
                                        )
                                    }
                                }
                            }
                        }

                        is IncomingPin -> {
                            if (event.unpin) {
                                pinnedMessages.removeIf { it.id == event.messageId }
                                _messages.update { old ->
                                    old.map { if (it.id != event.messageId) it else it.copy(pinnedAt = null) }
                                }
                            } else {
                                val msg = _messages.value.firstOrNull { it.id == event.messageId }
                                if (msg != null && pinnedMessages.none { it.id == event.messageId }) {
                                    pinnedMessages.add(0, msg.copy(pinnedAt = System.currentTimeMillis()))
                                }
                                _messages.update { old ->
                                    old.map { if (it.id != event.messageId) it else it.copy(pinnedAt = System.currentTimeMillis()) }
                                }
                            }
                        }
                        is IncomingTyping -> {
                            if (event.userId == theirUserId) {
                                isInterlocutorTyping = event.isTyping
                                // Автосброс через 5 сек если не пришёл isTyping=false
                                typingClearJob?.cancel()
                                if (event.isTyping) {
                                    typingClearJob = viewModelScope.launch {
                                        delay(5_000)
                                        isInterlocutorTyping = false
                                    }
                                }
                            }
                        }
                        is IncomingChatDeleted -> {
                            if (event.chatId == chatId) {
                                _uiEvents.tryEmit(ChatUiEvent.ChatDeleted)
                            }
                        }

                        is IncomingHistoryCleared -> {
                            if (event.chatId == chatId) {
                                _messages.value = emptyList()
                                _uiEvents.tryEmit(ChatUiEvent.HistoryCleared)
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

    private fun loadPinnedMessages() {
        viewModelScope.launch {
            try {
                val pinned = repository.getPinnedMessages(chatId, currentUserId ?: "")
                pinnedMessages.clear()
                pinnedMessages.addAll(pinned.sortedByDescending { it.pinnedAt })
            } catch (e: Exception) {
                Log.e("ChatVM", "Failed to load pinned", e)
            }
        }
    }

    fun pinMessage(message: DecryptedMessage) {
        viewModelScope.launch {
            try {
                repository.pinMessage(chatId, message.id, unpin = false)
                // Оптимистично добавляем
                if (pinnedMessages.none { it.id == message.id }) {
                    pinnedMessages.add(0, message.copy(pinnedAt = System.currentTimeMillis()))
                }
            } catch (e: Exception) {
                error = "Не удалось закрепить сообщение"
            }
        }
    }

    fun unpinMessage(messageId: String) {
        viewModelScope.launch {
            try {
                repository.pinMessage(chatId, messageId, unpin = true)
                pinnedMessages.removeIf { it.id == messageId }
            } catch (e: Exception) {
                error = "Не удалось открепить сообщение"
            }
        }
    }

    fun startForward(messages: List<DecryptedMessage>) {
        messagesToForward = messages.sortedBy { it.createdAt }
        showForwardDialog = true
    }

    fun dismissForward() {
        showForwardDialog = false
        messagesToForward = emptyList()
    }

    // ChatViewModel — убираем senderName из forwardTo, берём из самого сообщения
    fun forwardTo(targetChatId: String, theirUserId: String) {
        val msgs = messagesToForward
        dismissForward()
        exitMultiSelect()

        // оптимистичные сообщения
        if (targetChatId == chatId) {
            val optimisticList = msgs.map { original ->
                DecryptedMessage(
                    id                = "optimistic_fwd_${UUID.randomUUID()}",
                    chatId            = targetChatId,
                    senderId          = currentUserId ?: "",
                    text              = original.text,
                    createdAt         = System.currentTimeMillis(),
                    deletedForAll     = false,
                    statuses          = emptyList(),
                    forwardedFromId   = original.senderId,
                    forwardedFromName = resolveAuthorName(original)
                )
            }
            _messages.update { listOf(*optimisticList.toTypedArray()) + it }
        }

        viewModelScope.launch {
            try {
                repository.forwardMessages(
                    targetChatId = targetChatId,
                    messages     = msgs,
                    theirUserId  = theirUserId,
                    nameResolver = { msg -> resolveAuthorName(msg) }  // ← передаём резолвер
                )
            } catch (e: Exception) {
                if (targetChatId == chatId) {
                    _messages.update { old ->
                        old.filterNot { it.id.startsWith("optimistic_fwd_") }
                    }
                }
                error = "Не удалось переслать сообщения"
            }
        }
    }

    // Определяем имя автора по senderId сообщения
    private fun resolveAuthorName(message: DecryptedMessage): String {
        return when (message.senderId) {
            currentUserId -> currentUserName  // имя текущего пользователя
            theirUserId   -> interlocutorName     // имя собеседника
            else          -> message.forwardedFromName ?: "Неизвестно"
        }
    }


    override fun onCleared() {
        app.activeChatId = null
        wsJob?.cancel()

        viewModelScope.launch(Dispatchers.IO) {
            repository.detachFromChat(chatId)  // ← было closeChat, теперь detachFromChat
        }
        presenceJob?.cancel()
        typingClearJob?.cancel()
        typingDebounceJob?.cancel()
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


    private fun startPresencePolling() {
        presenceJob = viewModelScope.launch {
            while (true) {
                loadPresence()
                delay(15_000) // опрос каждые 15 сек
            }
        }
    }

    private suspend fun loadPresence() {
        repository.getPresence(theirUserId)
            .onSuccess { presence ->
                interlocutorPresence = formatPresence(presence)
            }
    }

    private fun formatPresence(presence: PresenceResponse): String {
        return when {
            presence.isOnline       -> "online"
            presence.lastSeenAt == null -> "recently"
            else                    -> "at:${presence.lastSeenAt}"
        }
    }
}
