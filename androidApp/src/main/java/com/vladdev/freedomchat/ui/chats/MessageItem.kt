package com.vladdev.freedomchat.ui.chats

import android.graphics.drawable.shapes.Shape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vladdev.freedomchat.R
import com.vladdev.shared.chats.dto.DecryptedMessage
import com.vladdev.shared.chats.dto.MessageDto
import com.vladdev.shared.chats.dto.MessageStatus
import kotlinx.serialization.InternalSerializationApi
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
@OptIn(ExperimentalFoundationApi::class, InternalSerializationApi::class)
@Composable
fun MessageItem(
    message: DecryptedMessage,
    currentUserId: String?,
    showTail: Boolean,
    onDelete: (messageId: String, forAll: Boolean) -> Unit
) {

    val displayText = message.displayText
    val isOwn = message.senderId == currentUserId
    var showMenu by remember { mutableStateOf(false) }

    // statuses / icon logic — оставляем как было
    val statusIconRes = remember(message, currentUserId) {
        if (isOwn) {
            val statuses = message.statuses.filter { it.userId != currentUserId }
            when {
                message.deletedForAll -> null
                statuses.any { it.status == MessageStatus.READ } -> R.drawable.ic_read
                statuses.any { it.status == MessageStatus.DELIVERED } -> R.drawable.ic_read
                else -> R.drawable.ic_sent
            }
        } else null
    }

    val bubbleColor = if (isOwn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val textColor = if (isOwn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer

    val alignment = if (isOwn) Alignment.CenterEnd else Alignment.CenterStart
    val maxWidthFraction = 0.7f

    // shape: remember with simple params (not calling composable inside)
    val bubbleShape = remember(isOwn, showTail) {
        messageBubbleShape(isOwn, showTail)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = alignment
    ) {
        Surface(
            shape = bubbleShape,
            color = bubbleColor,
            shadowElevation = 1.dp,
            modifier = Modifier
                .widthIn(max = LocalConfiguration.current.screenWidthDp.dp * maxWidthFraction)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            if (!message.deletedForAll) showMenu = true
                        }
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    // немного больше bottom-padding если есть хвост, чтобы не обрезался
                    .padding(bottom = if (showTail) 8.dp else 0.dp)
            ) {
                Text(
                    text = displayText ?: "Сообщение удалено",
                    color = if (displayText == null) textColor.copy(alpha = 0.5f) else textColor,
                    fontStyle = if (message.deletedForAll || displayText == null)
                        FontStyle.Italic else FontStyle.Normal,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val timeText = remember(message.createdAt) {
                        Instant.ofEpochMilli(message.createdAt)
                            .atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("HH:mm"))
                    }
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = textColor.copy(alpha = 0.6f)
                    )

                    statusIconRes?.let {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            painter = painterResource(id = it),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = textColor.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Удалить у себя") },
                onClick = {
                    showMenu = false
                    onDelete(message.id, false)
                }
            )
            if (isOwn) {
                DropdownMenuItem(
                    text = { Text("Удалить у всех") },
                    onClick = {
                        showMenu = false
                        onDelete(message.id, true)
                    }
                )
            }
        }
    }
}
private fun messageBubbleShape(isOwn: Boolean, showTail: Boolean): RoundedCornerShape {
    val big = 18.dp
    val small = 8.dp

    return if (isOwn) {
        // исходящие — хвостик справа -> уменьшаем нижний правый угол
        RoundedCornerShape(
            topStart = big,
            topEnd = big,
            bottomStart = big,
            bottomEnd = if (showTail) small else big
        )
    } else {
        // входящие — хвостик слева -> уменьшаем нижний левый угол
        RoundedCornerShape(
            topStart = big,
            topEnd = big,
            bottomStart = if (showTail) small else big,
            bottomEnd = big
        )
    }
}

