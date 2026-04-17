package com.vladdev.freedomchat.ui.chats

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.util.Size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vladdev.freedomchat.MAX_PHOTOS
import com.vladdev.freedomchat.MAX_VIDEOS
import com.vladdev.freedomchat.MainApplication
import com.vladdev.freedomchat.MediaPendingItem
import com.vladdev.freedomchat.PHOTO_MAX_BYTES
import com.vladdev.freedomchat.PendingMediaType
import com.vladdev.freedomchat.VIDEO_MAX_BYTES
import com.vladdev.shared.chats.ChatRepository
import com.vladdev.shared.chats.IncomingChatDeleted
import com.vladdev.shared.chats.IncomingDelete
import com.vladdev.shared.chats.IncomingEdit
import com.vladdev.shared.chats.IncomingHistoryCleared
import com.vladdev.shared.chats.IncomingMessage
import com.vladdev.shared.chats.IncomingPin
import com.vladdev.shared.chats.IncomingReaction
import com.vladdev.shared.chats.IncomingStatus
import com.vladdev.shared.chats.IncomingTyping
import com.vladdev.shared.chats.dto.DecryptedMessage
import com.vladdev.shared.chats.dto.MediaDto
import com.vladdev.shared.chats.dto.MediaState
import com.vladdev.shared.chats.dto.MessageStatus
import com.vladdev.shared.chats.dto.MessageStatusDto
import com.vladdev.shared.chats.dto.ReactionDto
import com.vladdev.shared.chats.extensionFromMime
import com.vladdev.shared.crypto.E2eeManager
import com.vladdev.shared.crypto.PlatformFile
import com.vladdev.shared.user.dto.PresenceResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.InternalSerializationApi
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

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
    private var _pendingViewerMessageId: String? = null
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
    private val _revealedSpoilers = mutableStateMapOf<String, Set<Int>>()
    val revealedSpoilers: Map<String, Set<Int>> = _revealedSpoilers

    fun revealSpoiler(messageId: String, spoilerIndex: Int) {
        val current = _revealedSpoilers[messageId] ?: emptySet()
        _revealedSpoilers[messageId] = current + spoilerIndex
    }

    // ── Состояние очереди вложений ─────────────────────────────────────────────

    var pendingMedia = mutableStateListOf<MediaPendingItem>()
        private set

    var mediaError by mutableStateOf<String?>(null)
        private set
    // Прогресс загрузки по tempId
    private val _uploadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val uploadProgress: StateFlow<Map<String, Int>> = _uploadProgress.asStateFlow()

    // Джобы отмены по tempId
    private val uploadJobs = mutableMapOf<String, Job>()
    // Для просмотра медиа
    var mediaViewerMessage by mutableStateOf<DecryptedMessage?>(null)
        private set

    fun openMediaViewer(message: DecryptedMessage) {
        if (message.mediaLocalPath != null) {
            mediaViewerMessage = message
        } else {
            // Помечаем что после загрузки нужно открыть viewer
            _pendingViewerMessageId = message.id
            downloadMedia(message)
        }
    }


    fun closeMediaViewer() { mediaViewerMessage = null }

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
        val hasPendingMedia = pendingMedia.any { !it.isOverLimit }

        // Выходим только если нет ни текста ни медиа
        if (newMessage.isBlank() && !hasPendingMedia) return

        // Медиа отправляем первыми
        if (hasPendingMedia) {
            val mediaToSend = pendingMedia.filter { !it.isOverLimit }.toList()
            pendingMedia.clear()
            sendMediaMessages(mediaToSend, captionText = newMessage.trim())
            newMessage = ""
            replyTo = null
            return
        }

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
                val sorted  = history.sortedByDescending { it.createdAt }
                _messages.value = sorted
                markAllIncomingAsRead(sorted)
                autoDownloadMedia(sorted)
            } catch (e: Exception) {
                Log.e("ChatVM", "HISTORY ERROR", e)
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
                                // Наше эхо — ищем оптимистичный плейсхолдер
                                _messages.update { old ->
                                    val optimistic = old.firstOrNull { it.id.startsWith("optimistic_") }

                                    if (optimistic != null) {
                                        // Заменяем плейсхолдер реальным сообщением
                                        old.map { m ->
                                            if (m.id != optimistic.id) return@map m

                                            // Для медиасообщений берём media из эхо-события
                                            val mediaFromEcho = msg.media

                                            m.copy(
                                                id                = msg.id,
                                                statuses          = msg.statuses,
                                                media             = mediaFromEcho ?: m.media,
                                                // Сохраняем mediaState и mediaLocalPath из оптимистичного
                                                // (они уже установлены правильно)
                                                forwardedFromId   = msg.forwardedFromId
                                                    ?.takeIf { it.isNotBlank() } ?: m.forwardedFromId,
                                                forwardedFromName = msg.forwardedFromName
                                                    ?.takeIf { it.isNotBlank() } ?: m.forwardedFromName
                                            )
                                        }
                                    } else if (old.none { it.id == msg.id }) {
                                        // Эхо без оптимистичного плейсхолдера — строим из processIncomingMessage
                                        viewModelScope.launch {
                                            val decrypted = repository.processIncomingMessage(
                                                msg      = msg,
                                                chatId   = chatId,
                                                myUserId = currentUserId ?: ""
                                            )
                                            if (decrypted != null) {
                                                _messages.update { current ->
                                                    if (current.any { it.id == decrypted.id }) current
                                                    else listOf(decrypted) + current
                                                }
                                            }
                                        }
                                        old
                                    } else old
                                }
                                return@collect
                            }

                            // Входящее от собеседника
                            viewModelScope.launch {
                                val decrypted = repository.processIncomingMessage(
                                    msg      = msg,
                                    chatId   = chatId,
                                    myUserId = currentUserId ?: ""
                                ) ?: return@launch

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
                                if (decrypted.media != null && decrypted.mediaLocalPath == null) {
                                    downloadMedia(decrypted)
                                }
                                currentUserId?.let { repository.sendRead(chatId, msg.id, it) }
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
                            println("IncomingEdit received: messageId=${event.messageId}, editedAt=${event.editedAt}")
                            viewModelScope.launch {
                                val plaintext = if (event.senderId == currentUserId) {
                                    e2ee.getOutgoingPlaintext(chatId, event.messageId)
                                } else {
                                    e2ee.decryptMessage(chatId, event.encryptedContent)?.also { pt ->
                                        e2ee.saveIncomingPlaintext(chatId, event.messageId, pt)  // ← обновляем кэш
                                    }
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

                        is IncomingReaction -> {
                            _messages.update { old ->
                                old.map { msg ->
                                    if (msg.id != event.messageId) return@map msg

                                    var reactions = msg.reactions

                                    // Если сервер сообщил о вытесненной реакции — убираем её
                                    event.replacedEmoji?.let { replaced ->
                                        reactions = reactions.filterNot {
                                            it.userId == event.userId && it.emoji == replaced
                                        }
                                    }

                                    reactions = if (event.remove) {
                                        // Убираем реакцию
                                        reactions.filterNot { it.userId == event.userId && it.emoji == event.emoji }
                                    } else {
                                        // Добавляем, избегая дублей
                                        if (reactions.none { it.userId == event.userId && it.emoji == event.emoji }) {
                                            reactions + ReactionDto(
                                                emoji     = event.emoji,
                                                userId    = event.userId,
                                                createdAt = event.createdAt
                                            )
                                        } else reactions
                                    }

                                    msg.copy(reactions = reactions)
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
                // Ищем mediaId в текущих сообщениях
                val mediaId = _messages.value
                    .firstOrNull { it.id == messageId }
                    ?.media?.id
                    ?.takeIf { !it.startsWith("optimistic_") }  // не удаляем temp-id

                repository.deleteMessage(
                    chatId    = chatId,
                    messageId = messageId,
                    forAll    = forAll,
                    mediaId   = if (forAll) mediaId else null
                )
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

    fun toggleReaction(message: DecryptedMessage, emoji: String) {
        val myId = currentUserId ?: return
        val isRemoving = message.reactions.any { it.userId == myId && it.emoji == emoji }

        // Оптимистичное обновление
        _messages.update { old ->
            old.map { msg ->
                if (msg.id != message.id) return@map msg

                val reactions = if (isRemoving) {
                    msg.reactions.filterNot { it.userId == myId && it.emoji == emoji }
                } else {
                    var updated = msg.reactions
                    // Локально вытесняем самую раннюю если уже 3
                    val myReactions = updated
                        .filter { it.userId == myId }
                        .sortedBy { it.createdAt }
                    if (myReactions.size >= 3) {
                        val oldest = myReactions.first()
                        updated = updated.filterNot { it.userId == myId && it.emoji == oldest.emoji }
                    }
                    updated + ReactionDto(
                        emoji     = emoji,
                        userId    = myId,
                        createdAt = System.currentTimeMillis()
                    )
                }

                msg.copy(reactions = reactions)
            }
        }

        viewModelScope.launch {
            try {
                repository.sendReaction(chatId, message.id, emoji, myId, isRemoving)
            } catch (e: Exception) {
                // Откат — восстанавливаем исходные реакции
                _messages.update { old ->
                    old.map { msg ->
                        if (msg.id == message.id) msg.copy(reactions = message.reactions)
                        else msg
                    }
                }
            }
        }
    }

    fun resolveUserAndOpen(
        userId: String,
        fallbackName: String,
        onReady: (name: String, username: String, status: String) -> Unit
    ) {
        viewModelScope.launch {
            repository.searchUserById(userId)
                .onSuccess { onReady(it.name, it.username, it.status) }
                .onFailure  { onReady(fallbackName, fallbackName, "standard") }
        }
    }

    fun resolveUserByUsernameAndOpen(
        username: String,
        onReady: (userId: String, name: String, nick: String, status: String) -> Unit
    ) {
        viewModelScope.launch {
            repository.searchUser(username)
                .onSuccess { response ->
                    val user = response.user
                    if (user != null) {
                        onReady(user.userId, user.name, user.username, user.status)
                    } else {
                        error = "Пользователь @$username не найден"
                    }
                }
                .onFailure {
                    error = "Пользователь @$username не найден"
                }
        }
    }

    fun onMediaPicked(uris: List<Uri>) {
        val context = getApplication<Application>()

        viewModelScope.launch(Dispatchers.IO) {
            val newItems = uris.mapNotNull { uri ->
                runCatching { buildPendingItem(context, uri) }.getOrNull()
            }

            // Проверяем лимиты
            val currentPhotos = pendingMedia.count { it.type == PendingMediaType.PHOTO }
            val currentVideos = pendingMedia.count { it.type == PendingMediaType.VIDEO }

            var addedPhotos = 0; var addedVideos = 0
            val errors = mutableListOf<String>()

            for (item in newItems) {
                when {
                    item.type == PendingMediaType.PHOTO && currentPhotos + addedPhotos >= MAX_PHOTOS -> {
                        errors += "Максимум $MAX_PHOTOS фото за раз"
                        break
                    }
                    item.type == PendingMediaType.VIDEO && currentVideos + addedVideos >= MAX_VIDEOS -> {
                        errors += "Максимум $MAX_VIDEOS видео за раз"
                        break
                    }
                    else -> {
                        pendingMedia.add(item)
                        if (item.type == PendingMediaType.PHOTO) addedPhotos++
                        else addedVideos++
                    }
                }
            }

            if (errors.isNotEmpty()) {
                withContext(Dispatchers.Main) { mediaError = errors.first() }
            }
        }
    }

    fun removePendingMedia(localId: String) {
        pendingMedia.removeIf { it.localId == localId }
    }

    fun clearMediaError() { mediaError = null }

    private suspend fun buildPendingItem(context: Context, uri: Uri): MediaPendingItem {
        val cr       = context.contentResolver
        val mimeType = cr.getType(uri) ?: "application/octet-stream"
        val isVideo  = mimeType.startsWith("video")
        val type     = if (isVideo) PendingMediaType.VIDEO else PendingMediaType.PHOTO

        // Размер файла
        val sizeBytes = cr.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))
            else 0L
        } ?: 0L

        val maxBytes = if (isVideo) VIDEO_MAX_BYTES else PHOTO_MAX_BYTES
        val isOver   = sizeBytes > maxBytes

        // Превью
        val thumb: Bitmap? = if (isVideo) {
            val retriever = MediaMetadataRetriever()
            runCatching {
                retriever.setDataSource(context, uri)
                // Берём кадр на 1й секунде (или на 0 если видео короче)
                retriever.getFrameAtTime(
                    1_000_000L, // 1 секунда в микросекундах
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )?.let { frame ->
                    // Масштабируем до 200x200 сохраняя пропорции
                    val scale = 200f / maxOf(frame.width, frame.height)
                    Bitmap.createScaledBitmap(
                        frame,
                        (frame.width * scale).toInt(),
                        (frame.height * scale).toInt(),
                        true
                    )
                }
            }.also {
                retriever.release()
            }.getOrNull()
        } else {
            // Фото — без изменений
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(uri, Size(200, 200), null)
                } else {
                    BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri))
                        ?.let { bmp ->
                            val scale = 200f / maxOf(bmp.width, bmp.height)
                            Bitmap.createScaledBitmap(
                                bmp,
                                (bmp.width * scale).toInt(),
                                (bmp.height * scale).toInt(),
                                true
                            )
                        }
                }
            }.getOrNull()
        }

        // Длительность видео
        val durationMs: Long? = if (isVideo) {
            val retriever = MediaMetadataRetriever()
            runCatching {
                retriever.setDataSource(context, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()
            }.also { retriever.release() }.getOrNull()
        } else null

        return MediaPendingItem(
            uri       = uri,
            type      = type,
            mimeType  = mimeType,
            sizeBytes = sizeBytes,
            thumbBitmap = thumb,
            durationMs  = durationMs,
            isOverLimit = isOver
        )
    }

    fun cancelUpload(tempId: String) {
        uploadJobs[tempId]?.cancel()
        uploadJobs.remove(tempId)
        _messages.update { it.filterNot { m -> m.id == tempId } }
        _uploadProgress.update { it - tempId }
    }

    private fun sendMediaMessages(items: List<MediaPendingItem>, captionText: String) {
        val context      = getApplication<Application>()
        val mediaGroupId = UUID.randomUUID().toString()

        items.forEachIndexed { index, item ->
            val caption = if (index == 0) captionText else ""
            val tempId  = "optimistic_${UUID.randomUUID()}"

            val job = viewModelScope.launch(Dispatchers.IO) {

                // Сохраняем превью на диск сразу — чтобы показать в UI до завершения загрузки
                val thumbPath: String? = item.thumbBitmap?.let { bmp ->
                    runCatching {
                        val thumbFile = File(context.cacheDir, "thumb_preview_${tempId}.jpg")
                        thumbFile.outputStream().use { out ->
                            // Сжимаем фото если превышает лимит
                            val compressed = if (item.type == PendingMediaType.PHOTO) {
                                compressThumb(bmp)
                            } else bmp
                            compressed.compress(Bitmap.CompressFormat.JPEG, 80, out)
                        }
                        thumbFile.absolutePath
                    }.getOrNull()
                }

                val optimistic = DecryptedMessage(
                    id           = tempId,
                    chatId       = chatId,
                    senderId     = currentUserId ?: "",
                    text         = caption.ifBlank { null },
                    createdAt    = System.currentTimeMillis() + index,
                    media        = MediaDto(
                        id          = tempId,
                        type        = item.type.name,
                        mimeType    = item.mimeType,
                        sizeBytes   = item.sizeBytes,
                        durationSec = item.durationMs?.let { (it / 1000).toInt() },
                        createdAt   = System.currentTimeMillis()
                    ),
                    // Для фото — thumbPath как основной путь для отображения до загрузки
                    // Для видео — тоже thumbPath (кадр превью)
                    mediaLocalPath = if (item.type == PendingMediaType.PHOTO) null else thumbPath,
                    mediaThumbPath = thumbPath,
                    mediaState     = MediaState.DOWNLOADING,
                    mediaGroupId   = mediaGroupId
                )

                withContext(Dispatchers.Main) {
                    _messages.update { listOf(optimistic) + it }
                    _uploadProgress.update { it + (tempId to 0) }
                }

                try {
                    // Сжимаем фото перед отправкой если нужно (Uri → сжатый файл)
                    val sourceUri = if (item.type == PendingMediaType.PHOTO &&
                        item.sizeBytes > PHOTO_MAX_BYTES) {
                        compressPhotoUri(context, item)   // ← возвращает Uri сжатого файла
                    } else {
                        item.uri
                    }

                    repository.sendMediaMessageFromUri(
                        chatId            = chatId,
                        theirUserId       = theirUserId,
                        mediaType         = item.type.name,
                        mimeType          = if (item.type == PendingMediaType.PHOTO &&
                            item.sizeBytes > PHOTO_MAX_BYTES) "image/jpeg" else item.mimeType,
                        fileName          = "media_${System.currentTimeMillis()}.${extensionFromMime(item.mimeType)}",
                        uri               = sourceUri,
                        thumbBitmap       = item.thumbBitmap,
                        metadataJson      = buildMetadataJson(item),
                        caption           = caption,
                        replyToId         = if (index == 0) replyTo?.id else null,
                        context           = context,
                        onEncryptProgress = { pct ->
                            viewModelScope.launch(Dispatchers.Main) {
                                _uploadProgress.update { map -> map + (tempId to pct) }
                            }
                        },
                        onUploadProgress  = { pct ->
                            viewModelScope.launch(Dispatchers.Main) {
                                _uploadProgress.update { map -> map + (tempId to pct) }
                            }
                        },
                        onUploaded = { mediaId, localPath ->
                            viewModelScope.launch(Dispatchers.Main) {
                                _uploadProgress.update { map -> map - tempId }
                                _messages.update { old ->
                                    old.map { m ->
                                        if (m.id == tempId) m.copy(
                                            mediaState     = MediaState.READY,
                                            mediaLocalPath = localPath,
                                            media          = m.media?.copy(id = mediaId)
                                        ) else m
                                    }
                                }
                            }
                        }
                    )

                    withContext(Dispatchers.Main) {
                        isScrollToBottomPending = true
                        if (index == 0) replyTo = null
                    }

                } catch (e: CancellationException) {
                    withContext(Dispatchers.Main) {
                        _messages.update { list -> list.filterNot { m -> m.id == tempId } }
                        _uploadProgress.update { map -> map - tempId }
                    }
                    // Удаляем временный превью файл
                    thumbPath?.let { File(it).delete() }

                } catch (e: Exception) {
                    Log.e("ChatVM", "Media send error", e)
                    withContext(Dispatchers.Main) {
                        _messages.update { list -> list.filterNot { m -> m.id == tempId } }
                        _uploadProgress.update { map -> map - tempId }
                        error = "Не удалось отправить медиа: ${e.message}"
                    }
                    thumbPath?.let { File(it).delete() }

                } finally {
                    uploadJobs.remove(tempId)
                }
            }
            uploadJobs[tempId] = job
        }
    }

    // Сжатие превью до разумного размера для отображения
    private fun compressThumb(bmp: Bitmap): Bitmap {
        val maxSide = 400
        if (bmp.width <= maxSide && bmp.height <= maxSide) return bmp
        val scale = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
        return Bitmap.createScaledBitmap(
            bmp,
            (bmp.width * scale).toInt(),
            (bmp.height * scale).toInt(),
            true
        )
    }

    // Сжатие фото перед шифрованием — заменяет старый compressMedia для ByteArray
// Записывает сжатый файл во временный и возвращает его Uri
    private suspend fun compressPhotoUri(context: Context, item: MediaPendingItem): Uri {
        return withContext(Dispatchers.IO) {
            val bmp = context.contentResolver.openInputStream(item.uri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return@withContext item.uri   // не удалось декодировать — шлём оригинал

            var quality = 85
            val out     = ByteArrayOutputStream()

            while (quality >= 40) {
                out.reset()
                bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
                if (out.size() <= PHOTO_MAX_BYTES) break
                quality -= 10
            }

            // Если всё равно больше — масштабируем
            if (out.size() > PHOTO_MAX_BYTES) {
                val scale = Math.sqrt(PHOTO_MAX_BYTES.toDouble() / out.size()).toFloat()
                val scaled = Bitmap.createScaledBitmap(
                    bmp,
                    (bmp.width * scale).toInt(),
                    (bmp.height * scale).toInt(),
                    true
                )
                out.reset()
                scaled.compress(Bitmap.CompressFormat.JPEG, 75, out)
            }

            // Сохраняем во временный файл и возвращаем его Uri
            val tempFile = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.jpg")
            tempFile.writeBytes(out.toByteArray())
            Uri.fromFile(tempFile)
        }
    }

    private fun compressMedia(
        rawBytes: ByteArray,
        item: MediaPendingItem
    ): Pair<ByteArray, String> {
        if (item.type == PendingMediaType.VIDEO) {
            // Видео не сжимаем на клиенте — сервер только проверяет размер
            // Транскодирование требует MediaCodec и выходит за рамки
            return rawBytes to item.mimeType
        }

        // Фото — сжимаем если > 5MB
        if (item.sizeBytes <= PHOTO_MAX_BYTES) return rawBytes to item.mimeType

        val bmp = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
            ?: return rawBytes to item.mimeType

        var quality = 85
        var result  = rawBytes
        val out     = ByteArrayOutputStream()

        // Ступенчатое снижение качества пока не вложимся в лимит
        while (quality >= 40) {
            out.reset()
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
            result = out.toByteArray()
            if (result.size <= PHOTO_MAX_BYTES) break
            quality -= 10
        }

        // Если всё равно больше — масштабируем
        if (result.size > PHOTO_MAX_BYTES) {
            val scale  = Math.sqrt(PHOTO_MAX_BYTES.toDouble() / result.size).toFloat()
            val scaled = Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * scale).toInt(),
                (bmp.height * scale).toInt(),
                true
            )
            out.reset()
            scaled.compress(Bitmap.CompressFormat.JPEG, 75, out)
            result = out.toByteArray()
        }

        return result to "image/jpeg"
    }

    fun downloadMedia(message: DecryptedMessage) {
        val media = message.media ?: return

        if (message.mediaState == MediaState.DOWNLOADING ||
            message.mediaState == MediaState.DECRYPTING  ||
            message.mediaState == MediaState.READY) return

        updateMediaState(message.id, MediaState.DOWNLOADING)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val path = repository.downloadAndDecryptMedia(
                    messageId = message.id,
                    chatId    = chatId,
                    media     = media,
                    senderId  = message.senderId,
                    context   = getApplication<Application>()
                )
                withContext(Dispatchers.Main) {
                    _messages.update { old ->
                        old.map { m ->
                            if (m.id == message.id)
                                m.copy(mediaLocalPath = path, mediaState = MediaState.READY)
                            else m
                        }
                    }
                    if (_pendingViewerMessageId == message.id) {
                        _pendingViewerMessageId = null
                        mediaViewerMessage = _messages.value.firstOrNull { it.id == message.id }
                    }
                    // Если пользователь уже тапнул и ждёт — открываем viewer
                    if (mediaViewerMessage?.id == message.id) {
                        mediaViewerMessage = _messages.value.firstOrNull { it.id == message.id }
                    }
                }
            } catch (e: Exception) {
                Log.e("MediaDecrypt", "downloadMedia FAILED for ${message.id}: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    updateMediaState(message.id, MediaState.ERROR)
                }
            }
        }
    }

    private fun updateMediaState(messageId: String, state: MediaState) {
        _messages.update { old ->
            old.map { m -> if (m.id == messageId) m.copy(mediaState = state) else m }
        }
    }

    private fun autoDownloadMedia(messages: List<DecryptedMessage>) {
        messages.forEach { msg ->
            if (msg.media != null &&
                msg.mediaLocalPath == null &&
                msg.mediaState == MediaState.IDLE
            ) {
                downloadMedia(msg)
            }
        }
    }

    private suspend fun buildMetadataJson(item: MediaPendingItem): String {
        val context = getApplication<Application>()
        val parts   = mutableListOf<String>()

        withContext(Dispatchers.IO) {
            if (item.type == PendingMediaType.VIDEO) {
                val retriever = MediaMetadataRetriever()
                runCatching {
                    retriever.setDataSource(context, item.uri)
                    val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                    val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                    val d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    w?.let { parts += "\"width\":$it" }
                    h?.let { parts += "\"height\":$it" }
                    d?.let { parts += "\"durationSec\":${it / 1000}" }
                }
                retriever.release()
            } else {
                // Фото — читаем через BitmapFactory.Options без декодирования пикселей
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(item.uri)?.use { inp ->
                    BitmapFactory.decodeStream(inp, null, opts)
                }
                opts.outWidth.takeIf  { it > 0 }?.let { parts += "\"width\":$it" }
                opts.outHeight.takeIf { it > 0 }?.let { parts += "\"height\":$it" }
            }
        }

        return "{${parts.joinToString(",")}}"
    }


}
