package com.vladdev.freedomchat.ui.chats

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.vladdev.freedomchat.MediaPendingItem
import com.vladdev.freedomchat.PendingMediaType
import com.vladdev.freedomchat.R
import com.vladdev.freedomchat.ui.theme.FreedomChatTheme
import com.vladdev.shared.chats.ChatRepository
import com.vladdev.shared.chats.dto.ChatDto
import com.vladdev.shared.chats.dto.DecryptedMessage
import com.vladdev.shared.chats.dto.MediaDto
import com.vladdev.shared.crypto.E2eeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.InternalSerializationApi
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
data class MessageGroup(
    val leadMessage: DecryptedMessage,     // первое в группе (с подписью)
    val mediaItems: List<DecryptedMessage>  // все медиа группы включая lead
)
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
    onOpenInterlocutorProfile: (userId: String, name: String, nick: String, status: String) -> Unit = { _, _, _, _ -> },
) {
    val context = LocalContext.current
    val currentUserName = remember(currentUserId, availableChats) {
        availableChats
            .flatMap { it.participants }
            .firstOrNull { it.userId == currentUserId }
            ?.name ?: ""
    }

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
            currentUserName  = currentUserName
        )
    )
    val revealedSpoilers = remember { mutableStateMapOf<String, Set<Int>>() }
    val userNames = remember(currentUserId, interlocutorUserId, currentUserName) {
        buildMap {
            currentUserId?.let { put(it, currentUserName) }
            put(interlocutorUserId, interlocutorUsername)
        }
    }

    val currentUserNick = remember(currentUserId, availableChats) {
        availableChats
            .flatMap { it.participants }
            .firstOrNull { it.userId == currentUserId }
            ?.username ?: ""
    }

    val listState = rememberLazyListState()
    val messages by viewModel.messages.collectAsState()
    val scope = rememberCoroutineScope()
    var showNewMessagePopup by remember { mutableStateOf(false) }
    val isAtBottom by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    var pinnedDisplayIndex by remember { mutableIntStateOf(0) }
    val selectedMessages = viewModel.selectedMessages
    val isMultiSelectMode = viewModel.isMultiSelectMode
    val clipboardManager = LocalClipboardManager.current
    var hasTextSelection by remember { mutableStateOf(false) }
    var showFormatMenu by remember { mutableStateOf(false) }
    var applyFormat by remember { mutableStateOf<(String, String) -> Unit>({ _, _ -> }) }
    val mediaViewerMessage by remember { derivedStateOf { viewModel.mediaViewerMessage } }
    val groupedMessages = remember(messages) { groupMessages(messages) }
    val uploadProgress by viewModel.uploadProgress.collectAsState()
    suspend fun scrollToMessage(messageId: String) {
        val index = groupedMessages.indexOfFirst { item ->
            when (item) {
                is GroupedItem.Single -> item.message.id == messageId
                is GroupedItem.Group  -> item.messages.any { it.id == messageId }
            }
        }
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
    LaunchedEffect(hasTextSelection) {
        if (!hasTextSelection) showFormatMenu = false
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
                    },
                    hasTextSelection = hasTextSelection,
                    showFormatMenu   = showFormatMenu,
                    onFormatClick    = { showFormatMenu = !showFormatMenu },
                    onFormatDismiss  = { showFormatMenu = false },
                    onApplyFormat    = { prefix, suffix -> applyFormat(prefix, suffix) }
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
                        ReplyEditBar(
                            label    = authorName,
                            preview  = reply.text ?: "",
                            media    = reply.media,      // передаём — ReplyEditBar сам построит "📷 Фото: подпись"
                            mediaLocalPath = reply.mediaLocalPath,
                            onCancel = { viewModel.cancelReply() }
                        )
                    }
                    viewModel.editingMessage?.let { editing ->
                        ReplyEditBar(
                            label    = "Редактирование",
                            preview  = editing.text ?: "",
                            media    = editing.media,    // аналогично для режима редактирования
                            mediaLocalPath = editing.mediaLocalPath,
                            onCancel = { viewModel.cancelEdit() }
                        )
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
                            value           = viewModel.newMessage,
                            onValueChange   = viewModel::onMessageChange,
                            onSend          = { viewModel.sendMessage() },
                            onSelectionChange = { hasTextSelection = it },
                            onFormatApplier   = { fn -> applyFormat = fn },
                            pendingMedia    = viewModel.pendingMedia,          // НОВОЕ
                            onMediaPicked   = viewModel::onMediaPicked,        // НОВОЕ
                            onMediaRemove   = viewModel::removePendingMedia,   // НОВОЕ
                            onMediaError    = { viewModel.clearMediaError() },
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
                        items = groupedMessages,
                        key   = { _, item -> item.id }
                    ) { index, groupedItem ->

                        // showTail — смотрим на предыдущий элемент (в reverseLayout = следующий визуально)
                        val previousItem = groupedMessages.getOrNull(index - 1)
                        val nextItem     = groupedMessages.getOrNull(index + 1)
                        val showTail     = previousItem?.senderId != groupedItem.senderId

                        // Достаём DecryptedMessage для общей логики
                        val message = when (groupedItem) {
                            is GroupedItem.Single -> groupedItem.message
                            is GroupedItem.Group  -> groupedItem.lead
                        }
                        val mediaGroup = when (groupedItem) {
                            is GroupedItem.Single -> null
                            is GroupedItem.Group  -> groupedItem.messages
                        }

                        MessageItem(
                            message              = message,
                            currentUserId        = currentUserId,
                            messages             = messages,
                            showTail             = showTail,
                            isSelected           = when (groupedItem) {
                                is GroupedItem.Single -> selectedMessages.contains(message.id)
                                is GroupedItem.Group  -> groupedItem.messages.any { selectedMessages.contains(it.id) }
                            },
                            isMultiSelectMode    = isMultiSelectMode,
                            onDelete             = { id, forAll -> viewModel.deleteMessage(id, forAll) },
                            onReply              = { viewModel.startReply(it) },
                            onEdit               = { viewModel.startEdit(it) },
                            onSelect             = { msg ->
                                when (groupedItem) {
                                    is GroupedItem.Single -> viewModel.toggleMessageSelection(msg.id)
                                    // При выборе группы — выбираем все её сообщения
                                    is GroupedItem.Group  -> groupedItem.messages.forEach {
                                        if (!selectedMessages.contains(it.id))
                                            viewModel.toggleMessageSelection(it.id)
                                    }
                                }
                            },
                            onEnterMultiSelect   = { viewModel.enterMultiSelect(it.id) },
                            onScrollToMessage    = { id -> scope.launch { scrollToMessage(id) } },
                            interlocutorUsername = interlocutorUsername,
                            interlocutorUserId   = interlocutorUserId,
                            onForward            = { msg ->
                                when (groupedItem) {
                                    is GroupedItem.Single -> viewModel.startForward(listOf(msg))
                                    is GroupedItem.Group  -> viewModel.startForward(groupedItem.messages)
                                }
                            },
                            onPin                = { viewModel.pinMessage(it) },
                            onForwardedAuthorClick = { userId, name ->
                                if (userId != null) {
                                    val participant = availableChats
                                        .flatMap { it.participants }
                                        .firstOrNull { it.userId == userId }
                                    if (participant != null) {
                                        onOpenInterlocutorProfile(userId, name, participant.username, participant.status)
                                    } else {
                                        viewModel.resolveUserAndOpen(userId, name) { uName, uNick, uStatus ->
                                            onOpenInterlocutorProfile(userId, uName, uNick, uStatus)
                                        }
                                    }
                                }
                            },
                            userNames            = userNames,
                            onReact              = { msg, emoji -> viewModel.toggleReaction(msg, emoji) },
                            onCopy               = { msg ->
                                msg.text?.let { clipboardManager.setText(AnnotatedString(it)) }
                            },
                            onUsernameClick      = { username ->
                                val participant = availableChats
                                    .flatMap { it.participants }
                                    .firstOrNull { it.username == username }
                                if (participant != null) {
                                    onOpenInterlocutorProfile(
                                        participant.userId,
                                        participant.name,
                                        participant.username,
                                        participant.status
                                    )
                                } else {
                                    viewModel.resolveUserByUsernameAndOpen(username) { userId, name, nick, status ->
                                        onOpenInterlocutorProfile(userId, name, nick, status)
                                    }
                                }
                            },
                            currentUserNick      = currentUserNick,
                            revealedSpoilersForMessage = revealedSpoilers[message.id] ?: emptySet(),
                            onRevealSpoiler      = { idx ->
                                val cur = revealedSpoilers[message.id] ?: emptySet()
                                revealedSpoilers[message.id] = cur + idx
                            },
                            onMediaClick         = { msg -> viewModel.openMediaViewer(msg) },
                            mediaGroup           = mediaGroup,
                            uploadProgressMap = uploadProgress,
                            onCancelUpload    = { tempId -> viewModel.cancelUpload(tempId) },
                        )

                        // Разделитель по дате — используем createdAt группы
                        val nextCreatedAt = nextItem?.createdAt
                        val showSeparator = nextCreatedAt == null || run {
                            val msgDay  = Calendar.getInstance().apply { timeInMillis = groupedItem.createdAt }
                            val nextDay = Calendar.getInstance().apply { timeInMillis = nextCreatedAt }
                            msgDay.get(Calendar.YEAR)        != nextDay.get(Calendar.YEAR) ||
                                    msgDay.get(Calendar.DAY_OF_YEAR) != nextDay.get(Calendar.DAY_OF_YEAR)
                        }
                        if (showSeparator) {
                            DateSeparator(label = formatDateSeparator(groupedItem.createdAt))
                        }
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
                viewModel.mediaError?.let { err ->
                    LaunchedEffect(err) {
                        delay(3000)
                        viewModel.clearMediaError()
                    }
                    ErrorBanner(
                        message  = err,
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 80.dp)
                    )
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
        if (mediaViewerMessage != null) {
            MediaViewerScreen(
                initialMessage = mediaViewerMessage!!,
                allMessages    = messages,              // весь список
                onDismiss      = viewModel::closeMediaViewer
            )
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
    onBack: () -> Unit,
    hasTextSelection: Boolean = false,
    showFormatMenu: Boolean = false,
    onFormatClick: () -> Unit = {},
    onFormatDismiss: () -> Unit = {},
    onApplyFormat: (String, String) -> Unit = { _, _ -> }
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
                // Анимированная замена иконки
                AnimatedContent(
                    targetState  = hasTextSelection,
                    transitionSpec = {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                    },
                    label = "topbar_action_icon"
                ) { isSelecting ->
                    if (isSelecting) {
                        IconButton(onClick = onFormatClick) {
                            Icon(
                                painter            = painterResource(R.drawable.ic_format),
                                contentDescription = "Форматирование",
                                tint               = if (showFormatMenu)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        IconButton(onClick = onMuteToggle) {
                            Icon(
                                painter            = painterResource(
                                    if (isMuted) R.drawable.notifications_off
                                    else R.drawable.notifications_on
                                ),
                                contentDescription = null
                            )
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )
        AnimatedVisibility(
            visible = showFormatMenu && hasTextSelection,
            enter   = fadeIn(tween(150)) + expandVertically(
                animationSpec = tween(180),
                expandFrom    = Alignment.Top
            ),
            exit    = fadeOut(tween(100)) + shrinkVertically(
                animationSpec = tween(150),
                shrinkTowards = Alignment.Top
            )
        ) {
            // Перехватываем клик снаружи строки кнопок
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        indication        = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onFormatDismiss() }
            ) {
                Surface(
                    shape          = RoundedCornerShape(16.dp),
                    color          = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier       = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 12.dp, bottom = 8.dp)
                ) {
                    Row(
                        modifier              = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        FormatChip("B", FontWeight.Bold) {
                            onApplyFormat("**", "**"); onFormatDismiss()
                        }
                        FormatChip("I", fontStyle = FontStyle.Italic) {
                            onApplyFormat("_", "_"); onFormatDismiss()
                        }
                        FormatChip("S", textDecoration = TextDecoration.LineThrough) {
                            onApplyFormat("~~", "~~"); onFormatDismiss()
                        }
                        FormatChip("||") {
                            onApplyFormat("||", "||"); onFormatDismiss()
                        }
                    }
                }
            }
        }
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
    val label = if (pinnedMessages.size == 1)
        "Закреплённое сообщение"
    else
        "Закреплённые сообщения (${pinnedMessages.size})"

    // Текст превью с учётом медиа
    val previewText = when {
        displayed.media != null && !displayed.text.isNullOrBlank() ->
            "${mediaTypeLabel(displayed.media!!.type)}: ${displayed.text}"
        displayed.media != null ->
            mediaTypeLabel(displayed.media!!.type)
        else ->
            displayed.text ?: ""
    }

    Surface(
        shape           = RoundedCornerShape(16.dp),
        color           = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 2.dp,
        modifier        = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable { onScrollTo(displayed.id) }
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp).height(36.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(10.dp))

            // Миниатюра закреплённого медиа
            if (displayed.media != null && displayed.mediaLocalPath != null) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                ) {
                    if (displayed.media!!.type.uppercase() == "VIDEO") {
                        var thumb by remember(displayed.mediaLocalPath) {
                            mutableStateOf<Bitmap?>(null)
                        }
                        LaunchedEffect(displayed.mediaLocalPath) {
                            thumb = withContext(Dispatchers.IO) {
                                extractVideoFrame(displayed.mediaLocalPath!!)
                            }
                        }
                        if (thumb != null) {
                            Image(
                                bitmap           = thumb!!.asImageBitmap(),
                                contentDescription = null,
                                contentScale     = ContentScale.Crop,
                                modifier         = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_video),
                                    contentDescription = null,
                                    tint     = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    } else {
                        AsyncImage(
                            model              = File(displayed.mediaLocalPath!!),
                            contentDescription = null,
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = label,
                    style      = MaterialTheme.typography.labelSmall,
                    color      = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text     = previewText,
                    style    = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick  = { onUnpin(displayed.id) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    painterResource(R.drawable.ic_unpin),
                    contentDescription = null,
                    modifier           = Modifier.size(16.dp),
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun ChatInputField(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onSelectionChange: (Boolean) -> Unit,
    onFormatApplier: ((String, String) -> Unit) -> Unit,
    pendingMedia: List<MediaPendingItem>,
    onMediaPicked: (List<Uri>) -> Unit,
    onMediaRemove: (String) -> Unit,
    onMediaError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tfValue by remember { mutableStateOf(TextFieldValue(value)) }

    LaunchedEffect(value) {
        if (value != tfValue.text)
            tfValue = TextFieldValue(value, selection = TextRange(value.length))
    }
    LaunchedEffect(Unit) {
        onFormatApplier { prefix, suffix ->
            tfValue = wrapSelection(tfValue, prefix, suffix)
            onValueChange(tfValue.text)
        }
    }

    // Лаунчер медиапикера — множественный выбор
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        if (uris.isNotEmpty()) onMediaPicked(uris)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Очередь вложений — показываем только если есть
        if (pendingMedia.isNotEmpty()) {
            PendingMediaRow(
                items    = pendingMedia,
                onRemove = onMediaRemove
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Кнопка вложений
            IconButton(
                onClick = {
                    mediaPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_attachments),
                    contentDescription = "Прикрепить",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.width(4.dp))

            OutlinedTextField(
                value         = tfValue,
                onValueChange = { new ->
                    tfValue = new
                    onValueChange(new.text)
                    onSelectionChange(!new.selection.collapsed)
                },
                modifier    = Modifier.weight(1f).animateContentSize(),
                shape       = RoundedCornerShape(24.dp),
                placeholder = { Text("Сообщение...") },
                colors      = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor   = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedBorderColor      = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor    = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                singleLine = false,
                maxLines   = 5
            )

            Spacer(Modifier.width(8.dp))

            val hasPending = pendingMedia.any { !it.isOverLimit }
            val canSend    = value.isNotBlank() || hasPending

            FilledIconButton(
                onClick  = { if (canSend) onSend() },
                enabled  = canSend,
                modifier = Modifier.size(48.dp),
                colors   = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor   = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(painterResource(R.drawable.send), contentDescription = "Отправить")
            }
        }
    }
}

// ── Очередь вложений над полем ввода ──────────────────────────────────────────

@Composable
private fun PendingMediaRow(
    items: List<MediaPendingItem>,
    onRemove: (String) -> Unit
) {
    LazyRow(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.localId }) { item ->
            PendingMediaThumb(item = item, onRemove = { onRemove(item.localId) })
        }
    }
}

@Composable
private fun PendingMediaThumb(
    item: MediaPendingItem,
    onRemove: () -> Unit
) {
    val borderColor = if (item.isOverLimit)
        MaterialTheme.colorScheme.error
    else
        Color.Transparent

    Box(modifier = Modifier.size(80.dp)) {
        // Превью
        if (item.thumbBitmap != null) {
            Image(
                bitmap = item.thumbBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            )
        } else {
            // Fallback-иконка для видео без превью
            Box(
                modifier         = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_video),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Иконка видео поверх превью
        if (item.type == PendingMediaType.VIDEO) {
            Box(
                modifier         = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                val durationText = item.durationMs?.let { ms ->
                    val s = ms / 1000
                    "%d:%02d".format(s / 60, s % 60)
                } ?: ""
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter           = painterResource(R.drawable.ic_video),
                        contentDescription = null,
                        tint              = Color.White,
                        modifier          = Modifier.size(12.dp)
                    )
                    if (durationText.isNotEmpty()) {
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text  = durationText,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Ошибка превышения размера
        if (item.isOverLimit) {
            Box(
                modifier         = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text  = if (item.type == PendingMediaType.VIDEO) ">150MB" else ">5MB",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        // Крестик удаления
        Box(
            modifier         = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 4.dp, y = (-4).dp)
                .size(20.dp)
                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                .clickable { onRemove() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter           = painterResource(R.drawable.close),
                contentDescription = "Убрать",
                tint              = Color.White,
                modifier          = Modifier.size(12.dp)
            )
        }
    }
}

// Оборачивает выделенный текст маркерами и возвращает новый TextFieldValue
private fun wrapSelection(tfv: TextFieldValue, prefix: String, suffix: String): TextFieldValue {
    val start = tfv.selection.min
    val end   = tfv.selection.max
    val text  = tfv.text
    val inner = text.substring(start, end)

    // Если уже обёрнут — снимаем маркеры (toggle)
    val before = text.substring((start - prefix.length).coerceAtLeast(0), start)
    val after  = text.substring(end, (end + suffix.length).coerceAtMost(text.length))
    return if (before == prefix && after == suffix) {
        val newText = text.substring(0, start - prefix.length) + inner +
                text.substring(end + suffix.length)
        TextFieldValue(
            text      = newText,
            selection = TextRange(start - prefix.length, end - prefix.length)
        )
    } else {
        val newText = text.substring(0, start) + prefix + inner + suffix + text.substring(end)
        TextFieldValue(
            text      = newText,
            selection = TextRange(start + prefix.length, end + prefix.length)
        )
    }
}

@Composable
private fun FormatChip(
    label: String,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    textDecoration: TextDecoration? = null,
    onClick: () -> Unit
) {
    Surface(
        shape  = RoundedCornerShape(10.dp),
        color  = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier         = Modifier
                .size(width = 40.dp, height = 36.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text  = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight     = fontWeight ?: FontWeight.Normal,
                    fontStyle      = fontStyle ?: FontStyle.Normal,
                    textDecoration = textDecoration
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
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
fun ReplyEditBar(
    label: String,
    preview: String,
    media: MediaDto? = null,
    mediaLocalPath: String? = null,   // ДОБАВИТЬ параметр
    onCancel: () -> Unit
) {
    val previewText = when {
        media != null && preview.isNotBlank() -> "${mediaTypeLabel(media.type)}: $preview"
        media != null                         -> mediaTypeLabel(media.type)
        else                                  -> preview
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Row(
            modifier          = Modifier
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

            // Превью медиа — используем localPath вместо thumbUrl
            if (media != null && mediaLocalPath != null) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                ) {
                    if (media.type.uppercase() == "VIDEO") {
                        var thumb by remember(mediaLocalPath) { mutableStateOf<Bitmap?>(null) }
                        LaunchedEffect(mediaLocalPath) {
                            thumb = withContext(Dispatchers.IO) {
                                extractVideoFrame(mediaLocalPath)
                            }
                        }
                        if (thumb != null) {
                            Image(
                                bitmap           = thumb!!.asImageBitmap(),
                                contentDescription = null,
                                contentScale     = ContentScale.Crop,
                                modifier         = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_video),
                                    null,
                                    tint     = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else {
                        AsyncImage(
                            model              = File(mediaLocalPath),
                            contentDescription = null,
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text     = previewText,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
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

fun mediaTypeLabel(type: String): String = when (type.uppercase()) {
    "PHOTO"      -> "\uD83D\uDCF7 Фото"
    "VIDEO"      -> "\uD83C\uDFA5 Видео"
    "VIDEO_NOTE" -> "\uD83D\uDCF9 Видеокружок"
    "VOICE"      -> "\uD83C\uDFA4 Голосовое"
    else         -> "Медиа"
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
sealed class GroupedItem {
    data class Single(val message: DecryptedMessage) : GroupedItem()
    data class Group(
        val messages: List<DecryptedMessage>,  // уже в правильном порядке: старые → новые
        val lead: DecryptedMessage
    ) : GroupedItem()

    // Универсальный доступ к полям для логики showTail / separators
    val senderId: String get() = when (this) {
        is Single -> message.senderId
        is Group  -> lead.senderId
    }
    val createdAt: Long get() = when (this) {
        is Single -> message.createdAt
        is Group  -> lead.createdAt
    }
    val id: String get() = when (this) {
        is Single -> message.id
        is Group  -> "group_${lead.id}"
    }
}

fun groupMessages(messages: List<DecryptedMessage>): List<GroupedItem> {
    val result = mutableListOf<GroupedItem>()
    var i = 0

    while (i < messages.size) {
        val msg = messages[i]

        if (msg.media != null && !msg.deletedForAll) {
            val group = mutableListOf(msg)
            var j = i + 1

            while (j < messages.size) {
                val next = messages[j]
                if (next.senderId == msg.senderId &&
                    next.media != null &&
                    !next.deletedForAll &&
                    kotlin.math.abs(next.createdAt - msg.createdAt) < 60_000L
                ) {
                    group.add(next)
                    j++
                } else break
            }

            if (group.size > 1) {
                // reverseLayout=true: messages[0] — самое новое, поэтому
                // переворачиваем чтобы визуально левый/верхний = старший по времени = №1
                val sorted = group.sortedBy { it.createdAt }
                val lead   = sorted.firstOrNull { it.text != null } ?: sorted.last()
                result.add(GroupedItem.Group(messages = sorted, lead = lead))
                i = j
            } else {
                result.add(GroupedItem.Single(msg))
                i++
            }
        } else {
            result.add(GroupedItem.Single(msg))
            i++
        }
    }

    return result
}