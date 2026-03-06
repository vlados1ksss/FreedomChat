package com.vladdev.freedomchat.ui.chats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vladdev.shared.chats.ChatRepository
import com.vladdev.shared.chats.dto.ChatDto
import com.vladdev.shared.chats.dto.ChatRequestDto
import com.vladdev.shared.chats.dto.SearchUserResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi
import kotlin.collections.emptyList
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vladdev.shared.chats.IncomingDelete
import com.vladdev.shared.chats.IncomingMessage
import com.vladdev.shared.chats.IncomingStatus
import com.vladdev.shared.chats.WsIncomingEvent
import com.vladdev.shared.chats.dto.MessageStatus
import com.vladdev.shared.chats.dto.MessageStatusDto

@OptIn(InternalSerializationApi::class)
class ChatsViewModel(private val repository: ChatRepository, private val dataStore: DataStore<Preferences>, private val currentUserId: String?    ) : ViewModel() {

    var chats by mutableStateOf<List<ChatDto>>(emptyList())
        private set
    var incomingRequests by mutableStateOf<List<ChatRequestDto>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    // Поиск
    var searchQuery by mutableStateOf("")
        private set
    var searchResult by mutableStateOf<SearchUserResponse?>(null)
        private set
    var isSearching by mutableStateOf(false)
        private set

    // Контекстное меню
    var expandedMenuChatId by mutableStateOf<String?>(null)
        private set

    // Закреплённые чаты (локально)
    var pinnedChatIds by mutableStateOf<Set<String>>(emptySet())
        private set

    // Muted чаты (chatId -> muted)
    var mutedChatIds by mutableStateOf<Set<String>>(emptySet())
        private set

    var activeChatId by mutableStateOf<String?>(null)

    private var searchJob: Job? = null

    private val chatSubscriptions = mutableMapOf<String, Job>()


    companion object {
        private val PINNED_CHATS_KEY = stringPreferencesKey("pinned_chats")
    }

    init {
        viewModelScope.launch {
            loadPinnedChats()
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            isLoading = true

            repository.loadChats()
                .onSuccess { loaded ->
                    chats = loaded
                    // Подписываемся на новые чаты после загрузки
                    subscribeToNewChats(loaded)
                }
                .onFailure { error = it.message }

            repository.loadRequests()
                .onSuccess { incomingRequests = it }
                .onFailure { error = it.message }

            // Загружаем mute статусы
            val muted = mutableSetOf<String>()
            chats.forEach { chat ->
                if (repository.getChatMuted(chat.chatId)) muted.add(chat.chatId)
            }
            mutedChatIds = muted

            isLoading = false
        }
    }

    private fun subscribeToNewChats(loadedChats: List<ChatDto>) {
        loadedChats.forEach { chat ->
            if (chatSubscriptions.containsKey(chat.chatId)) return@forEach

            repository.registerBackgroundSubscriber(chat.chatId)

            val job = viewModelScope.launch {
                try {
                    repository.openChatBackground(chat.chatId, viewModelScope)
                } catch (e: Exception) {
                    println("Background WS error for ${chat.chatId}: ${e.message}")
                }
                repository.eventsFlow(chat.chatId).collect { event ->
                    handleChatEvent(chat.chatId, event)
                }
            }
            chatSubscriptions[chat.chatId] = job
        }
    }


    private fun handleChatEvent(chatId: String, event: WsIncomingEvent) {
        when (event) {
            // Внутри handleChatEvent -> IncomingMessage
            is IncomingMessage -> {
                val msg = event.message
                val isOwn = msg.senderId == currentUserId

                viewModelScope.launch {
                    val plaintext = repository.decryptPreview(msg, chatId, currentUserId ?: "")
                    chats = chats.map { chat ->
                        if (chat.chatId != chatId) return@map chat

                        // ПРОВЕРКА: Если это сообщение уже было последним, не инкрементируем счетчик
                        val isNewMessage = chat.lastMessage?.id != msg.id
                        val shouldIncrement = !isOwn && activeChatId != chatId && isNewMessage

                        chat.copy(
                            lastMessage = msg.copy(plaintextPreview = plaintext),
                            unreadCount = if (shouldIncrement) chat.unreadCount + 1 else chat.unreadCount
                        )
                    }
                }
            }

            is IncomingStatus -> {
                chats = chats.map { chat ->
                    if (chat.chatId != chatId) return@map chat

                    // Обновляем статусы у lastMessage
                    val updatedLast = chat.lastMessage?.let { last ->
                        val newStatuses = last.statuses
                            .filter { it.userId != event.userId } +
                                MessageStatusDto(event.messageId, event.userId, event.status)
                        last.copy(statuses = newStatuses)
                    }

                    // Если READ пришёл от текущего пользователя — обнуляем счётчик
                    val newUnread = if (
                        event.status == MessageStatus.READ &&
                        event.userId == currentUserId
                    ) 0 else chat.unreadCount

                    chat.copy(lastMessage = updatedLast, unreadCount = newUnread)
                }
            }

            is IncomingDelete -> {
                chats = chats.map { chat ->
                    if (chat.chatId != chatId) return@map chat
                    if (chat.lastMessage?.id == event.messageId) {
                        chat.copy(lastMessage = null)
                    } else chat
                }
            }
        }
    }


    fun onSearchQueryChange(query: String) {
        searchQuery = query
        searchJob?.cancel()
        if (query.isBlank()) {
            searchResult = null
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            isSearching = true
            repository.searchUser(query.trimStart('@'))
                .onSuccess { searchResult = it }
                .onFailure { searchResult = null }
            isSearching = false
        }
    }

    fun clearSearch() {
        searchQuery = ""
        searchResult = null
        searchJob?.cancel()
    }

    fun openOrCreateChat(
        userId: String,
        existingChatId: String?,
        onReady: (chatId: String, theirUserId: String, name: String, status: String) -> Unit
    ) {
        viewModelScope.launch {
            val name   = searchResult?.user?.name ?: searchResult?.user?.username ?: ""
            val status = searchResult?.user?.status ?: "standard"

            if (existingChatId != null) {
                onReady(existingChatId, userId, name, status)
            } else {
                repository.createDirectChat(userId)
                    .onSuccess { chatId ->
                        refresh()
                        onReady(chatId, userId, name, status)
                    }
                    .onFailure { error = it.message }
            }
        }
    }

    fun acceptRequest(requestId: String) {
        viewModelScope.launch {
            repository.accept(requestId)
                .onSuccess { refresh() }
                .onFailure { error = it.message }
        }
    }

    fun rejectRequest(requestId: String) {
        viewModelScope.launch {
            repository.reject(requestId)
                .onSuccess { refresh() }
                .onFailure { error = it.message }
        }
    }

    // --- Контекстное меню ---
    fun showContextMenu(chatId: String) { expandedMenuChatId = chatId }
    fun hideContextMenu() { expandedMenuChatId = null }

    private suspend fun loadPinnedChats() {
        val prefs = dataStore.data.first()
        val raw = prefs[PINNED_CHATS_KEY] ?: ""
        pinnedChatIds = if (raw.isBlank()) emptySet() else raw.split(",").toSet()
    }

    fun togglePin(chatId: String) {
        val newPinned = if (chatId in pinnedChatIds)
            pinnedChatIds - chatId
        else
            pinnedChatIds + chatId

        pinnedChatIds = newPinned

        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PINNED_CHATS_KEY] = newPinned.joinToString(",")
            }
        }
        hideContextMenu()
    }

    fun markAsRead(chatId: String) {
        viewModelScope.launch {
            repository.markChatAsRead(chatId)
            // Сразу патчим UI не дожидаясь refresh
            chats = chats.map { chat ->
                if (chat.chatId == chatId) chat.copy(unreadCount = 0)
                else chat
            }
            hideContextMenu()
        }
    }

    fun toggleMute(chatId: String) {
        viewModelScope.launch {
            val newMuted = chatId !in mutedChatIds
            repository.setChatMuted(chatId, newMuted)
            mutedChatIds = if (newMuted) mutedChatIds + chatId else mutedChatIds - chatId
            hideContextMenu()
        }
    }

    // Сортировка: сначала закреплённые, потом по времени последнего сообщения
    fun sortedChats(): List<ChatDto> {
        return chats.sortedWith(
            compareByDescending<ChatDto> { it.chatId in pinnedChatIds }
                .thenByDescending { it.lastMessage?.createdAt ?: 0L }
        )
    }

    override fun onCleared() {
        viewModelScope.launch {
            chatSubscriptions.keys.toList().forEach { chatId ->
                repository.unregisterBackgroundSubscriber(chatId)
                repository.closeChatBackground(chatId)
            }
        }
        chatSubscriptions.clear()
        super.onCleared()
    }

    fun onChatOpened(chatId: String) {
        activeChatId = chatId
        // Сразу обнуляем счётчик
        chats = chats.map { chat ->
            if (chat.chatId == chatId) chat.copy(unreadCount = 0)
            else chat
        }
    }

    fun onChatClosed(chatId: String) {
        activeChatId = null
        viewModelScope.launch {
            // 1. Сначала помечаем в репозитории (на всякий случай)
            repository.markChatAsRead(chatId)

            // 2. Обновляем этот конкретный чат в списке из локальной БД/сервера
            // или просто вызываем refresh()
            refresh()
        }
    }
}

