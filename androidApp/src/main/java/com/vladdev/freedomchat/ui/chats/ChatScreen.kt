package com.vladdev.freedomchat.ui.chats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vladdev.freedomchat.R
import com.vladdev.freedomchat.ui.theme.FreedomChatTheme
import com.vladdev.shared.chats.ChatRepository
import com.vladdev.shared.crypto.E2eeManager
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, InternalSerializationApi::class)
@Composable
fun ChatScreen(
    chatId: String,
    repository: ChatRepository,
    e2ee: E2eeManager,
    interlocutorUsername: String,
    interlocutorUserId: String,          // <-- новый параметр
    currentUserId: String?,
    interlocutorStatus: String = "standard",
    onBack: () -> Unit
) {
    val viewModel: ChatViewModel = viewModel(
        key = "chat_$chatId",
        factory = ChatViewModelFactory(
            repository    = repository,
            e2ee          = e2ee,
            chatId        = chatId,
            currentUserId = currentUserId,
            theirUserId   = interlocutorUserId
        )
    )

    val listState = rememberLazyListState()
    val messages by viewModel.messages.collectAsState()
    val scope = rememberCoroutineScope()
    var showNewMessagePopup by remember { mutableStateOf(false) }
    val isAtBottom by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0
        }
    }
    val messagesCount = messages.size

    LaunchedEffect(messages.size) {
        if (messages.isEmpty()) return@LaunchedEffect

        val lastMessage = messages.first()

        if (viewModel.isScrollToBottomPending) {
            listState.animateScrollToItem(0)
            viewModel.onScrolledToBottom()
            showNewMessagePopup = false
        } else if (!isAtBottom) {
            if (lastMessage.senderId != currentUserId) {
                showNewMessagePopup = true
            }
        }
    }
    LaunchedEffect(isAtBottom) {
        if (isAtBottom) showNewMessagePopup = false
    }
    FreedomChatTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                ChatTopBarRounded(
                    name = interlocutorUsername,
                    status = interlocutorStatus,
                    isConnected = viewModel.isConnected,
                    onBack = onBack
                )
            },
            modifier = Modifier.imePadding()
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 72.dp),
                    state = listState,
                    reverseLayout = true,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    itemsIndexed(
                        items = messages,
                        key = { _, it -> it.id }
                    ) { index, message ->

                        val previousMessage = messages.getOrNull(index - 1)
                        val nextMessage = messages.getOrNull(index + 1) // более старое сообщение

                        val isOwn = message.senderId == currentUserId
                        val previousIsSameSender = previousMessage?.senderId == message.senderId
                        val showTail = !previousIsSameSender

                        MessageItem(
                            message = message,
                            currentUserId = currentUserId,
                            showTail = showTail,
                            onDelete = { id, forAll -> viewModel.deleteMessage(id, forAll) }
                        )

                        // Показываем разделитель ПОСЛЕ рендера сообщения (визуально — над ним,
                        // т.к. reverseLayout = true) если день изменился или это последнее сообщение
                        val showSeparator = when {
                            nextMessage == null -> true // самое старое сообщение — всегда показываем дату
                            else -> {
                                val msgDay = Calendar.getInstance().apply { timeInMillis = message.createdAt }
                                val nextDay = Calendar.getInstance().apply { timeInMillis = nextMessage.createdAt }
                                msgDay.get(Calendar.YEAR) != nextDay.get(Calendar.YEAR) ||
                                        msgDay.get(Calendar.DAY_OF_YEAR) != nextDay.get(Calendar.DAY_OF_YEAR)
                            }
                        }

                        if (showSeparator) {
                            DateSeparator(label = formatDateSeparator(message.createdAt))
                        }
                    }
                }

                viewModel.error?.let {
                    ErrorBanner(
                        message = it,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                AnimatedVisibility(
                    visible = showNewMessagePopup,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 80.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                listState.animateScrollToItem(0)
                                showNewMessagePopup = false
                            }
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Icon(painterResource(R.drawable.ic_right), contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Новые сообщения")
                    }
                }

                ChatInputField(
                    value = viewModel.newMessage,
                    onValueChange = viewModel::onMessageChange,
                    onSend = {
                        if (viewModel.newMessage.isNotBlank()) {
                            viewModel.sendMessage()
                            scope.launch {
                                listState.animateScrollToItem(0)
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        LaunchedEffect(messagesCount) {
            if (viewModel.isScrollToBottomPending || isAtBottom) {
                listState.animateScrollToItem(0)
                if (viewModel.isScrollToBottomPending) {
                    viewModel.onScrolledToBottom()
                }
            }
        }

        LaunchedEffect(viewModel.lastSentMessageId) {
            viewModel.lastSentMessageId?.let {
                listState.animateScrollToItem(0)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBarRounded(
    name: String,
    isConnected: Boolean,
    status: String = "standard",
    onBack: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 4.dp,
        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(name = name, size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            StatusIcon(status = status, size = 16.dp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isConnected) MaterialTheme.colorScheme.tertiary
                                        else MaterialTheme.colorScheme.secondary
                                    )
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (isConnected) "В сети" else "Подключение...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        painterResource(R.drawable.arrow_back),
                        contentDescription = "Назад",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            actions = {
                IconButton(onClick = {}) {
                    Icon(
                        painterResource(R.drawable.more_vert),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            ),
            scrollBehavior = null
        )
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
    val isSendEnabled = value.isNotBlank()

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        cursorColor = MaterialTheme.colorScheme.primary,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedPlaceholderColor = MaterialTheme.colorScheme.outline,
        unfocusedPlaceholderColor = MaterialTheme.colorScheme.outline
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ){
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .animateContentSize(),
            shape = RoundedCornerShape(24.dp),
            placeholder = { Text("Сообщение...") },
            colors = textFieldColors,
            singleLine = false,
            maxLines = 5
        )

        Spacer(Modifier.width(8.dp))

        FilledIconButton(
            onClick = onSend,
            enabled = isSendEnabled,
            modifier = Modifier
                .padding(bottom = 4.dp)
                .size(48.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                painterResource(R.drawable.send),
                contentDescription = "Отправить"
            )
        }
    }
    Spacer(Modifier.height(8.dp))
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