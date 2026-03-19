@file:OptIn(InternalSerializationApi::class)

package com.vladdev.freedomchat.ui.chats

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.vladdev.freedomchat.R
import com.vladdev.freedomchat.chatsDataStore
import com.vladdev.freedomchat.ui.theme.FreedomChatTheme
import com.vladdev.shared.chats.ChatRepository
import com.vladdev.shared.chats.dto.ChatRequestDto
import com.vladdev.shared.chats.dto.MessageDto
import com.vladdev.shared.chats.dto.MessageStatus
import com.vladdev.shared.storage.AndroidUserIdStorage
import kotlinx.serialization.InternalSerializationApi
import java.util.prefs.Preferences


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun ChatsScreen(
    viewModel: ChatsViewModel,
    currentUserId: String?,
    onOpenChat: (chatId: String, theirUserId: String, name: String, username: String, status: String) -> Unit,
    onOpenProfile: () -> Unit
) {
    val context = LocalContext.current
// В ChatsScreen добавь:
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.silentRefresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Вызывается когда composable покидает композицию (уход на другой экран)
            // НЕ вызывается при возврате
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(Unit) {
        var wasResumed = false
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (wasResumed) {
                // Это уже второй и более RESUME — значит вернулись из чата
                viewModel.activeChatId?.let { viewModel.onChatClosed(it) }
            }
            wasResumed = true
        }
    }
    var showSearch by remember { mutableStateOf(false) }
    FreedomChatTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { showSearch = true },
                    icon = { Icon(painterResource(R.drawable.new_chat), null) },
                    text = { Text("Новый чат") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.navigationBarsPadding()
                )
            }
        ) { padding ->

            val sortedChats = remember(viewModel.chats, viewModel.pinnedChatIds) {
                viewModel.sortedChats()
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                    ) {
                        FilledIconButton(
                            onClick = onOpenProfile,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 16.dp)
                        ) {
                            Icon(painterResource(R.drawable.ic_profile), "Профиль")
                        }
                        Text(
                            text = "FreedomChat",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                if (viewModel.incomingRequests.isNotEmpty()) {
                    item {
                        SectionLabel(
                            text = "Запросы в чат",
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)
                        )
                    }
                    items(viewModel.incomingRequests, key = { it.id }) { request ->
                        RequestCard(
                            request = request,
                            onAccept = { viewModel.acceptRequest(request.id) },
                            onReject = { viewModel.rejectRequest(request.id) }
                        )
                    }
                }

                item {
                    SectionLabel(
                        text = "Чаты",
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)
                    )
                }
                if (viewModel.isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            ContainedLoadingIndicator(
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                } else if (sortedChats.isEmpty()) {
                    item { EmptyChatsPlaceholder() }
                } else {
                    items(sortedChats, key = { it.chatId }) { chat ->
                        val other = chat.participants.firstOrNull { it.userId != currentUserId }
                        val theirParticipant = chat.participants.first { it.userId != currentUserId }
                        val isMenuExpanded = viewModel.expandedMenuChatId == chat.chatId
                        val isPinned = chat.chatId in viewModel.pinnedChatIds
                        val isMuted = chat.chatId in viewModel.mutedChatIds

                        Column {
                            ChatListItem(
                                name = other?.name ?: other?.username ?: "Чат",
                                status = other?.status ?: "standard",
                                lastMessage = chat.lastMessage,
                                unreadCount = chat.unreadCount,
                                currentUserId = currentUserId,
                                isPinned = isPinned,
                                isMuted = isMuted,
                                isMenuExpanded = isMenuExpanded,
                                onClick = {
                                    viewModel.onChatOpened(chat.chatId)  // ← добавляем здесь
                                    onOpenChat(
                                        chat.chatId,
                                        theirParticipant.userId,
                                        theirParticipant.name,
                                        theirParticipant.username,
                                        theirParticipant.status
                                    )
                                },
                                onLongClick = { viewModel.showContextMenu(chat.chatId) }
                            )

                            // Встроенное контекстное меню
                            AnimatedVisibility(
                                visible = isMenuExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                ChatContextMenu(
                                    isPinned = isPinned,
                                    isMuted = isMuted,
                                    hasUnread = chat.unreadCount > 0,
                                    onPin = { viewModel.togglePin(chat.chatId) },
                                    onMarkRead = { viewModel.markAsRead(chat.chatId) },
                                    onToggleMute = { viewModel.toggleMute(chat.chatId) },
                                    onDismiss = { viewModel.hideContextMenu() }
                                )
                            }
                        }
                    }
                }

                viewModel.error?.let {
                    item { ErrorBanner(message = it, modifier = Modifier.padding(16.dp)) }
                }
            }
        }

        if (showSearch) {
            SearchUserDialog(
                viewModel = viewModel,
                onDismiss = {
                    showSearch = false
                    viewModel.clearSearch()
                },
                onOpenChat = { chatId, theirUserId, name, username, status ->
                    showSearch = false
                    viewModel.clearSearch()
                    onOpenChat(chatId, theirUserId, name, username, status)
                }
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.primary
        ),
        modifier = modifier
    )
}

@Composable
private fun RequestCard(
    request: ChatRequestDto,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(name = request.fromUsername, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = request.fromUsername,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Хочет начать диалог",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }
            IconButton(
                onClick = onAccept,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(painterResource(R.drawable.ic_sent), contentDescription = "Принять", modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onReject,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(painterResource(R.drawable.close), contentDescription = "Отклонить", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatListItem(
    name: String,
    status: String = "standard",
    lastMessage: MessageDto?,
    unreadCount: Int,
    currentUserId: String?,
    isPinned: Boolean,
    isMuted: Boolean,
    isMenuExpanded: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hasUnread = unreadCount > 0

    // Цвет фона — подсвечиваем если меню открыто
    val bgColor = if (isMenuExpanded)
        MaterialTheme.colorScheme.surfaceContainerLow
    else
        Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(name = name, size = 52.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f, fill = false)
                )
                StatusIcon(status = status, size = 14.dp)
                if (isPinned) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pin), // нужна иконка
                        contentDescription = "Закреплён",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                if (isMuted) {
                    Icon(
                        painter = painterResource(R.drawable.notifications_off), // нужна иконка
                        contentDescription = "Без звука",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Последнее сообщение
            if (lastMessage != null && !lastMessage.deletedForAll) {
                val isOwn = lastMessage.senderId == currentUserId
                Text(
                    text = lastMessage.plaintextPreview ?: "Сообщение недоступно",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (hasUnread && !isOwn) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (hasUnread && !isOwn)
                        MaterialTheme.colorScheme.onBackground
                    else
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = "Нажмите, чтобы открыть чат",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // Правая часть: время + счётчик
        // Правая часть: время + статус/счётчик
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (lastMessage != null) {
                Text(
                    text = formatChatTime(lastMessage.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasUnread)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.outline
                )
            }

            val isOwnLast = lastMessage?.senderId == currentUserId

            when {
                // Непрочитанные входящие — счётчик
                hasUnread && !isOwnLast -> {
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            )
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                // Исходящее последнее — статус вместо стрелки
                isOwnLast && lastMessage != null -> {
                    val statuses = lastMessage.statuses.filter { it.userId != currentUserId }
                    val isRead = statuses.any { it.status == MessageStatus.READ }
                    val isDelivered = statuses.any { it.status == MessageStatus.DELIVERED }
                    val iconRes = when {
                        isRead      -> R.drawable.ic_read
                        isDelivered -> R.drawable.ic_read
                        else        -> R.drawable.ic_sent
                    }
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = if (isRead)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Входящее последнее, нет непрочитанных — ничего не показываем
                else -> {
                    Spacer(Modifier.size(16.dp))
                }
            }
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(start = 86.dp, end = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun ChatContextMenu(
    isPinned: Boolean,
    isMuted: Boolean,
    hasUnread: Boolean,
    onPin: () -> Unit,
    onMarkRead: () -> Unit,
    onToggleMute: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 0.5.dp
            )

            ContextMenuItem(
                icon = if (isPinned) R.drawable.ic_unpin else R.drawable.ic_pin,
                label = if (isPinned) "Открепить" else "Закрепить",
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onPin
            )

            if (hasUnread) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 0.5.dp
                )
                ContextMenuItem(
                    icon = R.drawable.mark_read,
                    label = "Пометить прочитанным",
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = onMarkRead
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 0.5.dp
            )

            ContextMenuItem(
                icon = if (isMuted) R.drawable.notifications_on else R.drawable.notifications_off,
                label = if (isMuted) "Включить уведомления" else "Отключить уведомления",
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onToggleMute
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 0.5.dp
            )

            // Кнопка отмены — акцентный цвет, чуть больше отступ снизу для закруглений
            ContextMenuItem(
                icon = R.drawable.close,
                label = "Свернуть",
                tint = MaterialTheme.colorScheme.error,
                labelColor = MaterialTheme.colorScheme.error,
                onClick = onDismiss,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun ContextMenuItem(
    @DrawableRes icon: Int,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = labelColor
        )
    }
}

// Вспомогательная функция форматирования времени
private fun formatChatTime(timestamp: Long): String {
    val msgDate = java.util.Date(timestamp)
    val now = java.util.Date()
    val msgCal = java.util.Calendar.getInstance().apply { time = msgDate }
    val nowCal = java.util.Calendar.getInstance()

    return when {
        msgCal.get(java.util.Calendar.YEAR) != nowCal.get(java.util.Calendar.YEAR) -> {
            java.text.SimpleDateFormat("dd.MM.yy", java.util.Locale.getDefault()).format(msgDate)
        }
        msgCal.get(java.util.Calendar.DAY_OF_YEAR) == nowCal.get(java.util.Calendar.DAY_OF_YEAR) -> {
            java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(msgDate)
        }
        nowCal.get(java.util.Calendar.DAY_OF_YEAR) - msgCal.get(java.util.Calendar.DAY_OF_YEAR) == 1 -> "вчера"
        else -> {
            java.text.SimpleDateFormat("dd.MM", java.util.Locale.getDefault()).format(msgDate)
        }
    }
}

@Composable
private fun EmptyChatsPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(id = R.drawable.ic_chat),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Нет активных чатов",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Text(
                text = "Нажмите + чтобы начать разговор",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
