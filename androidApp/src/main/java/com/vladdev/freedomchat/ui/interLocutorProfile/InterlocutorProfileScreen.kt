package com.vladdev.freedomchat.ui.interLocutorProfile


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.vladdev.freedomchat.R
import com.vladdev.freedomchat.ui.chats.UserAvatar
import com.vladdev.freedomchat.ui.profile.ProfileDivider
import com.vladdev.freedomchat.ui.theme.FreedomChatTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.graphics.Color


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterlocutorProfileScreen(
    viewModel: InterlocutorProfileViewModel,
    onBack: () -> Unit,
    onOpenChat: (chatId: String, theirUserId: String, name: String, status: String) -> Unit,
    onHistoryCleared: () -> Unit,   // ← новый
    onChatDeleted: () -> Unit
) {
    val presence = viewModel.presence
    val presenceText = remember(presence) {
        when {
            presence == null         -> ""
            presence.isOnline        -> "В сети"
            presence.lastSeenAt == null -> "Был(-а) недавно"
            else                     -> formatLastSeen(presence.lastSeenAt!!)
        }
    }

    val presenceColor = when {
        presence?.isOnline == true -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    FreedomChatTheme {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            viewModel.theirName,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.arrow_back), "Назад")
                        }
                    }
                )
            }
        ) { padding ->

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                // Аватар + статус
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box {
                            UserAvatar(name = viewModel.theirName, size = 88.dp)
                            if (viewModel.theirStatus != "standard") {
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(2.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = when (viewModel.theirStatus) {
                                        "admin"    -> MaterialTheme.colorScheme.error
                                        "verified" -> MaterialTheme.colorScheme.primary
                                        "service"  -> MaterialTheme.colorScheme.tertiary
                                        else       -> Color.Transparent
                                    }
                                ) {
                                    Text(
                                        text = when (viewModel.theirStatus) {
                                            "admin"    -> "admin"
                                            "verified" -> "✓"
                                            "service"  -> "bot"
                                            else       -> ""
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Presence
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(presenceColor)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = presenceText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Карточка с инфо
                ProfileCard {
                    ProfileRow(
                        icon  = painterResource(R.drawable.ic_profile),
                        label = "Имя",
                        value = viewModel.theirName
                    )
                    ProfileDivider()
                    ProfileRow(
                        icon  = painterResource(R.drawable.ic_username),
                        label = "Имя пользователя",
                        value = "@${viewModel.theirUsername}"
                    )
                }

                // Кнопка "Открыть чат"
                Button(
                    onClick = {
                        viewModel.openOrCreateChat { chatId ->
                            onOpenChat(
                                chatId,
                                viewModel.theirUserId,
                                viewModel.theirName,
                                viewModel.theirStatus
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(painterResource(R.drawable.ic_chat), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Открыть чат")
                }

                // Управление чатом — только если чат существует
                if (viewModel.resolvedChatId != null) {
                    ProfileCard {
                        // Уведомления
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleMute() }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painterResource(
                                        if (viewModel.isMuted) R.drawable.notifications_off
                                        else R.drawable.notifications_on
                                    ),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = if (viewModel.isMuted) "Уведомления отключены"
                                    else "Уведомления включены",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Switch(
                                checked = !viewModel.isMuted,
                                onCheckedChange = { viewModel.toggleMute() }
                            )
                        }

                        ProfileDivider()

                        // Очистить историю
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showClearDialog = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painterResource(R.drawable.clear),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Очистить историю",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        ProfileDivider()

                        // Удалить чат
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDeleteDialog = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painterResource(R.drawable.delete_forever),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Удалить чат",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // Вкладки медиа/голосовые/файлы
                val tabs = listOf("Медиа", "Голосовые", "Файлы")
                ScrollableTabRow(
                    selectedTabIndex = viewModel.selectedTab,
                    edgePadding = 0.dp
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = viewModel.selectedTab == index,
                            onClick  = { viewModel.selectTab(index) },
                            text     = { Text(title) }
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Здесь пока пусто",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }

    // Диалог удаления чата
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title   = { Text("Удалить чат?") },
            text    = { Text("Переписка будет удалена для обоих участников.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteChat {
                            showDeleteDialog = false
                            onChatDeleted()
                        }
                    }
                ) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Отмена") }
            }
        )
    }

    // Диалог очистки истории
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title   = { Text("Очистить историю?") },
            text    = { Text("Все сообщения в чате будут удалены.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory {
                            showClearDialog = false
                            onHistoryCleared()
                        }
                    }
                ) { Text("Очистить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun ProfileCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            content()
        }
    }
}

@Composable
private fun ProfileRow(
    icon: Painter,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
    }
}

private fun formatLastSeen(ts: Long): String {
    val now = System.currentTimeMillis()
    val diffMin = (now - ts) / 60_000
    val diffHours = diffMin / 60
    val diffDays = diffHours / 24

    return when {
        diffMin < 1   -> "Был(-а) только что"
        diffMin < 60  -> "Был(-а) $diffMin мин. назад"
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