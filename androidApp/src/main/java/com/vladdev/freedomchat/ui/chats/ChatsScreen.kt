@file:OptIn(InternalSerializationApi::class)

package com.vladdev.freedomchat.ui.chats

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
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
import com.vladdev.freedomchat.R
import com.vladdev.freedomchat.ui.theme.FreedomChatTheme
import com.vladdev.shared.chats.ChatRepository
import com.vladdev.shared.chats.dto.ChatRequestDto
import com.vladdev.shared.storage.AndroidUserIdStorage
import kotlinx.serialization.InternalSerializationApi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    repository: ChatRepository,
    onOpenChat: (chatId: String, name: String) -> Unit,
    currentUserId: String?,
    onOpenProfile: () -> Unit
) {

//    val context = LocalContext.current
//    val sharedPrefs = context.getSharedPreferences("auth", android.content.Context.MODE_PRIVATE)
//    val currentUserId = sharedPrefs.getString("userId", null)

    val viewModel = remember { ChatsViewModel(repository) }
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

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                    ) {
                        FilledIconButton(
                            onClick = onOpenProfile,
                            modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp)
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

                if (viewModel.chats.isEmpty()) {
                    item { EmptyChatsPlaceholder() }
                } else {
                    items(viewModel.chats, key = { it.chatId }) { chat ->
                        val other = chat.participants.firstOrNull { it.userId != currentUserId }
                        ChatListItem(
                            // Показываем name вместо username
                            name = other?.name ?: other?.username ?: "Чат",
                            onClick = {
                                onOpenChat(chat.chatId, other?.name ?: other?.username ?: "Чат")
                            }
                        )
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
                onOpenChat = { chatId, name ->
                    showSearch = false
                    viewModel.clearSearch()
                    onOpenChat(chatId, name)
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

@Composable
private fun ChatListItem(name: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interactionSource, indication = ripple(), onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(name = name, size = 52.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Нажмите, чтобы открыть чат",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            painter = painterResource(id = R.drawable.ic_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp)
        )
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 86.dp, end = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
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
