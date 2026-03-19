package com.vladdev.freedomchat.ui.chats

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vladdev.freedomchat.R
import com.vladdev.freedomchat.ui.theme.FreedomChatTheme
import com.vladdev.shared.chats.ChatRepository
import com.vladdev.shared.chats.dto.ChatDto
import com.vladdev.shared.chats.dto.DecryptedMessage
import com.vladdev.shared.crypto.E2eeManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, InternalSerializationApi::class)
@Composable
fun ChatScreen(
    chatId: String,
    repository: ChatRepository,
    e2ee: E2eeManager,
    interlocutorUsername: String,
    interlocutorNick: String,
    interlocutorUserId: String,
    currentUserId: String?,
    interlocutorStatus: String = "standard",
    onBack: () -> Unit,
    availableChats: List<ChatDto> = emptyList(),
    onOpenInterlocutorProfile: (userId: String, name: String, nick: String, status: String) -> Unit = { _, _, _, _ -> }
) {
    val context = LocalContext.current
    val viewModel: ChatViewModel = viewModel(
        key     = "chat_$chatId",
        factory = ChatViewModelFactory(
            application      = context.applicationContext as Application,
            repository       = repository,
            e2ee             = e2ee,
            chatId           = chatId,
            currentUserId    = currentUserId,
            theirUserId      = interlocutorUserId,
            interlocutorName = interlocutorUsername,
            currentUserName  = availableChats
                .flatMap { it.participants }
                .firstOrNull { it.userId == currentUserId }
                ?.name ?: ""
        )
    )

    val listState = rememberLazyListState()
    val messages by viewModel.messages.collectAsState()
    val scope = rememberCoroutineScope()
    var showNewMessagePopup by remember { mutableStateOf(false) }
    val isAtBottom by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    var pinnedDisplayIndex by remember { mutableIntStateOf(0) }
    val selectedMessages = viewModel.selectedMessages
    val isMultiSelectMode = viewModel.isMultiSelectMode

    suspend fun scrollToMessage(messageId: String) {
        val index = messages.indexOfFirst { it.id == messageId }
        if (index != -1) listState.animateScrollToItem(index)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.silentRefresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(messages.size) {
        if (messages.isEmpty()) return@LaunchedEffect
        val lastMessage = messages.first()
        if (viewModel.isScrollToBottomPending) {
            listState.animateScrollToItem(0)
            viewModel.onScrolledToBottom()
            showNewMessagePopup = false
        } else if (!isAtBottom) {
            if (lastMessage.senderId != currentUserId) showNewMessagePopup = true
        }
    }
    LaunchedEffect(messages.size) {
        listState.animateScrollToItem(0)
    }
    LaunchedEffect(isAtBottom) { if (isAtBottom) showNewMessagePopup = false }

    LaunchedEffect(listState.firstVisibleItemIndex) {
        val visibleId = messages.getOrNull(listState.firstVisibleItemIndex)?.id
        if (visibleId != null) {
            val closestPinnedIndex = viewModel.pinnedMessages.indexOfFirst { pinned ->
                val pinnedMsgIndex = messages.indexOfFirst { it.id == pinned.id }
                pinnedMsgIndex >= listState.firstVisibleItemIndex
            }
            if (closestPinnedIndex != -1) pinnedDisplayIndex = closestPinnedIndex
        }
    }

    FreedomChatTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                ChatTopBarRounded(
                    name                 = interlocutorUsername,
                    status               = interlocutorStatus,
                    presence             = viewModel.interlocutorPresence,
                    isInterlocutorTyping = viewModel.isInterlocutorTyping,
                    isMuted              = viewModel.isMuted,
                    onMuteToggle         = viewModel::toggleMute,
                    onBack               = onBack,
                    onProfileClick       = {
                        onOpenInterlocutorProfile(interlocutorUserId, interlocutorUsername, interlocutorNick, interlocutorStatus)
                    }
                )
            },
            bottomBar = {
                // imePadding применяется здесь, чтобы поднять только панель ввода
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    viewModel.replyTo?.let { reply ->
                        val authorName = if (reply.senderId == currentUserId) "Вы" else interlocutorUsername
                        ReplyEditBar(label = authorName, preview = reply.text ?: "", onCancel = { viewModel.cancelReply() })
                    }
                    viewModel.editingMessage?.let { editing ->
                        ReplyEditBar(label = "Редактирование", preview = editing.text ?: "", onCancel = { viewModel.cancelEdit() })
                    }

                    if (isMultiSelectMode) {
                        MultiSelectBar(
                            count = selectedMessages.size,
                            onDeleteForMe = { viewModel.deleteSelectedMessages(false) },
                            onDeleteForAll = { viewModel.deleteSelectedMessages(true) },
                            onForward = { viewModel.startForward(messages.filter { it.id in selectedMessages }) },
                            onCancel = { viewModel.exitMultiSelect() }
                        )
                    } else {
                        ChatInputField(
                            value = viewModel.newMessage,
                            onValueChange = viewModel::onMessageChange,
                            onSend = { if (viewModel.newMessage.isNotBlank()) viewModel.sendMessage() }
                        )
                    }
                }
            }
        ) { padding ->
            // padding от Scaffold теперь содержит высоту TopBar и высоту BottomBar (включая клавиатуру)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding(),
                    state = listState,
                    reverseLayout = true,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    itemsIndexed(
                        items = messages,
                        key = { _, it -> it.id }
                    ) { index, message ->
                        val previousMessage = messages.getOrNull(index - 1)
                        val nextMessage = messages.getOrNull(index + 1)
                        val showTail = previousMessage?.senderId != message.senderId

                        MessageItem(
                            message = message,
                            currentUserId = currentUserId,
                            messages = messages,
                            showTail = showTail,
                            isSelected = selectedMessages.contains(message.id),
                            isMultiSelectMode = isMultiSelectMode,
                            onDelete = { id, forAll -> viewModel.deleteMessage(id, forAll) },
                            onReply = { viewModel.startReply(it) },
                            onEdit = { viewModel.startEdit(it) },
                            onSelect = { viewModel.toggleMessageSelection(it.id) },
                            onEnterMultiSelect = { viewModel.enterMultiSelect(it.id) },
                            onScrollToMessage = { id -> scope.launch { scrollToMessage(id) } },
                            interlocutorUsername = interlocutorUsername,
                            interlocutorUserId   = interlocutorUserId,
                            onForward = { viewModel.startForward(listOf(it)) },
                            onPin = { viewModel.pinMessage(it) },
                            onForwardedAuthorClick = { userId, name ->
                                if (userId != null) {
                                    val participant = availableChats.flatMap { it.participants }.firstOrNull { it.userId == userId }
                                    onOpenInterlocutorProfile(userId, name, participant?.username ?: "", participant?.status ?: "standard")
                                }
                            }
                        )

                        val showSeparator = nextMessage == null || run {
                            val msgDay = Calendar.getInstance().apply { timeInMillis = message.createdAt }
                            val nextDay = Calendar.getInstance().apply { timeInMillis = nextMessage.createdAt }
                            msgDay.get(Calendar.YEAR) != nextDay.get(Calendar.YEAR) || msgDay.get(Calendar.DAY_OF_YEAR) != nextDay.get(Calendar.DAY_OF_YEAR)
                        }
                        if (showSeparator) DateSeparator(label = formatDateSeparator(message.createdAt))
                    }
                }

                // Пин-бар зафиксирован вверху контентной области (под TopBar)
                PinnedMessagesBar(
                    pinnedMessages = viewModel.pinnedMessages,
                    currentDisplayIndex = pinnedDisplayIndex,
                    onUnpin = { viewModel.unpinMessage(it) },
                    onScrollTo = { id ->
                        val index = messages.indexOfFirst { it.id == id }
                        if (index != -1) scope.launch { listState.animateScrollToItem(index) }
                    },
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                viewModel.error?.let {
                    ErrorBanner(message = it, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).align(Alignment.TopCenter))
                }

                AnimatedVisibility(
                    visible = showNewMessagePopup,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
                ) {
                    OutlinedButton(
                        onClick = { scope.launch { listState.animateScrollToItem(0); showNewMessagePopup = false } },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Icon(painterResource(R.drawable.arrow_down), contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Новые сообщения")
                    }
                }
            }
        }

        if (viewModel.showForwardDialog) {
            ForwardChatPickerDialog(
                chats = availableChats,
                currentUserId = currentUserId,
                onSelect = { tId, thId -> viewModel.forwardTo(targetChatId = tId, theirUserId = thId) },
                onDismiss = { viewModel.dismissForward() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBarRounded(
    name: String,
    presence: String,
    isInterlocutorTyping: Boolean,
    isMuted: Boolean,
    onProfileClick: () -> Unit,
    onMuteToggle: () -> Unit,
    status: String = "standard",
    onBack: () -> Unit
) {
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            currentTime = System.currentTimeMillis()
        }
    }
    val (dotColor, statusText) = when {
        isInterlocutorTyping -> MaterialTheme.colorScheme.tertiary to "Печатает..."
        presence == "online" -> MaterialTheme.colorScheme.tertiary to "В сети"
        presence == "recently" -> MaterialTheme.colorScheme.secondary to "Был(-а) недавно"
        presence.startsWith("at:") -> {
            val ts = presence.removePrefix("at:").toLongOrNull()
            MaterialTheme.colorScheme.secondary to (if (ts != null) formatLastSeen(ts, currentTime) else "Был(-а) давно")
        }
        else -> MaterialTheme.colorScheme.secondary to "Не в сети"
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 4.dp,
        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
    ) {
        TopAppBar(
            title = {
                Row(modifier = Modifier.clickable { onProfileClick() }, verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(name = name, size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            StatusIcon(status = status, size = 16.dp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(dotColor))
                            Spacer(Modifier.width(4.dp))
                            when (statusText) {
                                "Печатает" -> {
                                    AnimatedTypingText(
                                        text = "Печатает",
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                else -> {
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(painterResource(R.drawable.arrow_back), contentDescription = "Назад", tint = MaterialTheme.colorScheme.onSurface) }
            },
            actions = {
                IconButton(onClick = onMuteToggle) {
                    Icon(painter = painterResource(if (isMuted) R.drawable.notifications_off else R.drawable.notifications_on), contentDescription = null)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )
    }
}

@Composable
fun PinnedMessagesBar(
    modifier: Modifier = Modifier,
    pinnedMessages: List<DecryptedMessage>,
    currentDisplayIndex: Int,
    onUnpin: (String) -> Unit,
    onScrollTo: (String) -> Unit
) {
    if (pinnedMessages.isEmpty()) return
    val displayed = pinnedMessages.getOrNull(currentDisplayIndex) ?: pinnedMessages.first()
    val label = if (pinnedMessages.size == 1) "Закреплённое сообщение" else "Закреплённые сообщения (${pinnedMessages.size})"

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable { onScrollTo(displayed.id) }
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(3.dp).height(36.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Text(text = displayed.text ?: "", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { onUnpin(displayed.id) }, modifier = Modifier.size(32.dp)) {
                Icon(painterResource(R.drawable.ic_unpin), contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatInputField(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically // Исправлено: строго по центру
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).animateContentSize(),
            shape = RoundedCornerShape(24.dp),
            placeholder = { Text("Сообщение...") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            ),
            singleLine = false,
            maxLines = 5
        )
        Spacer(Modifier.width(8.dp))
        FilledIconButton(
            onClick = onSend,
            enabled = value.isNotBlank(),
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
        ) {
            Icon(painterResource(R.drawable.send), contentDescription = "Отправить")
        }
    }
}

@Composable
fun DateSeparator(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
            )
        }
    }
}

fun formatDateSeparator(timestamp: Long): String {
    val messageDate = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

    fun Calendar.isSameDay(other: Calendar) =
        get(Calendar.YEAR) == other.get(Calendar.YEAR) &&
                get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)

    return when {
        messageDate.isSameDay(today) -> "Сегодня"
        messageDate.isSameDay(yesterday) -> "Вчера"
        else -> {
            val day = messageDate.get(Calendar.DAY_OF_MONTH)
            val month = when (messageDate.get(Calendar.MONTH)) {
                Calendar.JANUARY  -> "января"
                Calendar.FEBRUARY -> "февраля"
                Calendar.MARCH    -> "марта"
                Calendar.APRIL    -> "апреля"
                Calendar.MAY      -> "мая"
                Calendar.JUNE     -> "июня"
                Calendar.JULY     -> "июля"
                Calendar.AUGUST   -> "августа"
                Calendar.SEPTEMBER -> "сентября"
                Calendar.OCTOBER  -> "октября"
                Calendar.NOVEMBER -> "ноября"
                Calendar.DECEMBER -> "декабря"
                else -> ""
            }
            // Если год не текущий — добавляем год
            val year = messageDate.get(Calendar.YEAR)
            if (year != today.get(Calendar.YEAR)) "$day $month $year г." else "$day $month"
        }
    }
}


private fun formatLastSeen(ts: Long, now: Long): String {
    val diffMin   = (now - ts) / 60_000
    val diffHours = diffMin / 60
    val diffDays  = diffHours / 24

    return when {
        diffMin < 1    -> "Был(-а) только что"
        diffMin < 60   -> "Был(-а) $diffMin мин. назад"
        diffHours < 24 -> {
            val time = Instant.ofEpochMilli(ts)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm"))
            "Был(-а) сегодня в $time"
        }
        diffDays == 1L -> {
            val time = Instant.ofEpochMilli(ts)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm"))
            "Был(-а) вчера в $time"
        }
        else -> {
            val date = Instant.ofEpochMilli(ts)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("d MMM в HH:mm", Locale("ru")))
            "Был(-а) $date"
        }
    }
}

@Composable
fun ReplyEditBar(label: String, preview: String, onCancel: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp).height(36.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    preview,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onCancel) {
                Icon(painterResource(R.drawable.close), contentDescription = null)
            }
        }
    }
}

@Composable
fun MultiSelectBar(
    count: Int,
    onDeleteForMe: () -> Unit,
    onDeleteForAll: () -> Unit,
    onForward: () -> Unit,
    onCancel: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить сообщения") },
            text = { Text("Удалить $count сообщений?") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDeleteForAll(/* forAll = true */) }) {
                    Text("У всех")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; onDeleteForMe(/* forAll = false */) }) {
                    Text("У себя")
                }
            }
        )
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 4.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(painterResource(R.drawable.close), contentDescription = null)
            }
            Text(
                "$count выбрано",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium
            )
            IconButton(onClick = { onForward() }) {
                Icon(painterResource(R.drawable.forward), contentDescription = "Переслать")
            }
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(painterResource(R.drawable.delete), contentDescription = "Удалить")
            }
        }
    }
}

@OptIn(InternalSerializationApi::class)
@Composable
fun ForwardChatPickerDialog(
    chats: List<ChatDto>,
    currentUserId: String?,
    onSelect: (chatId: String, theirUserId: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedChatId by remember { mutableStateOf<String?>(null) }

    // Сортируем от новых к старым
    val sortedChats = remember(chats) {
        chats.sortedByDescending { it.lastMessage?.createdAt ?: 0L }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
        ) {
            Column {
                // Заголовок
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Переслать в...",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(painterResource(R.drawable.close), contentDescription = null)
                    }
                }

                HorizontalDivider()

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(sortedChats, key = { it.chatId }) { chat ->
                        val other = chat.participants.firstOrNull { it.userId != currentUserId }
                        val chatName = other?.name ?: other?.username ?: "Чат"
                        val theirUserId = other?.userId ?: ""
                        val isSelected = selectedChatId == chat.chatId

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedChatId = if (isSelected) null else chat.chatId
                                }
                                .background(
                                    if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    else
                                        Color.Transparent
                                )
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val size = 42.dp
                            Box(
                                modifier = Modifier
                                    .size(size)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.primaryContainer
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        painterResource(R.drawable.check),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    Text(
                                        text = chatName.take(2).uppercase(),
                                        style =  MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontSize = (size.value * 0.33f).sp
                                        ),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = chatName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                                chat.lastMessage?.plaintextPreview?.let { preview ->
                                    Text(
                                        text = preview,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Кнопка подтверждения
                AnimatedVisibility(
                    visible = selectedChatId != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    val selectedChat = sortedChats.firstOrNull { it.chatId == selectedChatId }
                    val other = selectedChat?.participants?.firstOrNull { it.userId != currentUserId }
                    val theirUserId = other?.userId ?: ""

                    Button(
                        onClick = {
                            selectedChatId?.let { onSelect(it, theirUserId) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            painterResource(R.drawable.forward),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Переслать")
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedTypingText(
    text: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing_anim")

    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 300f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1200,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset_anim"
    )

    val baseColor = MaterialTheme.colorScheme.tertiary

    val brush = Brush.linearGradient(
        colors = listOf(
            baseColor.copy(alpha = 0.4f),
            baseColor,
            baseColor.copy(alpha = 0.4f)
        ),
        start = Offset(offset, 0f),
        end = Offset(offset + 200f, 0f)
    )

    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall.copy(
            brush = brush
        )
    )
}