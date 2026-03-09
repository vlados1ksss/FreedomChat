package com.vladdev.freedomchat.ui.chats

import android.graphics.drawable.shapes.Shape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vladdev.freedomchat.R
import com.vladdev.shared.chats.dto.DecryptedMessage
import com.vladdev.shared.chats.dto.MessageDto
import com.vladdev.shared.chats.dto.MessageStatus
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class, InternalSerializationApi::class)
@Composable
fun MessageItem(
    message: DecryptedMessage,
    currentUserId: String?,
    messages: List<DecryptedMessage>,
    showTail: Boolean,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    interlocutorUsername: String,
    interlocutorUserId: String,
    onDelete: (messageId: String, forAll: Boolean) -> Unit,
    onReply: (DecryptedMessage) -> Unit,
    onEdit: (DecryptedMessage) -> Unit,
    onSelect: (DecryptedMessage) -> Unit,
    onEnterMultiSelect: (DecryptedMessage) -> Unit,
    onScrollToMessage: (String) -> Unit
) {
    val displayText = message.displayText ?: return
    val isOwn = message.senderId == currentUserId
    var showMenu by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val swipeOffset = remember { Animatable(0f) }
    var swipeTriggered by remember { mutableStateOf(false) }

    val replyToMessage = remember(message.replyToId, messages) {
        message.replyToId?.let { id -> messages.firstOrNull { it.id == id } }
    }
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

    // Цвет рамки при выборе
    val borderColor = if (isOwn)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.primary

    val bubbleColor = if (isOwn)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.secondaryContainer

    val textColor = if (isOwn)
        MaterialTheme.colorScheme.onPrimary
    else
        MaterialTheme.colorScheme.onSecondaryContainer

    val bubbleShape = remember(isOwn, showTail) { messageBubbleShape(isOwn, showTail) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        // Иконка ответа при свайпе
        val replyIconAlpha = (swipeOffset.value / 80f).coerceIn(0f, 1f)
        if (swipeOffset.value > 8f) {
            Icon(
                painter = painterResource(R.drawable.reply),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = replyIconAlpha),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
                    .size(24.dp)
                    .graphicsLayer {
                        // Иконка появляется с небольшим scale-эффектом
                        scaleX = 0.6f + 0.4f * replyIconAlpha
                        scaleY = 0.6f + 0.4f * replyIconAlpha
                    }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(swipeOffset.value.roundToInt(), 0) }
                .pointerInput(isMultiSelectMode) {
                    if (isMultiSelectMode) {
                        detectTapGestures(onTap = { onSelect(message) })
                    } else {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (swipeOffset.value > 100f && !swipeTriggered) {
                                    swipeTriggered = true
                                    onReply(message)
                                }
                                scope.launch {
                                    swipeOffset.animateTo(
                                        0f,
                                        spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessLow
                                        )
                                    )
                                    swipeTriggered = false
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    swipeOffset.animateTo(0f,
                                        spring(stiffness = Spring.StiffnessLow))
                                    swipeTriggered = false
                                }
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                if (dragAmount > 0) {
                                    scope.launch {
                                        // Нелинейное сопротивление — чем дальше, тем туже
                                        val current = swipeOffset.value
                                        val resistance = 1f - (current / 280f).coerceIn(0f, 0.75f)
                                        val target = (current + dragAmount * resistance)
                                            .coerceIn(0f, 130f)
                                        swipeOffset.snapTo(target)
                                    }
                                }
                            }
                        )
                    }
                },
            horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = bubbleShape,
                color = bubbleColor,
                shadowElevation = 1.dp,
                border = if (isSelected)
                    BorderStroke(2.dp, borderColor)
                else null,
                modifier = Modifier
                    .widthIn(max = LocalConfiguration.current.screenWidthDp.dp * 0.7f)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                if (isMultiSelectMode) onSelect(message)
                                else showMenu = true   // только меню, без выбора
                            },
                            onTap = {
                                if (isMultiSelectMode) onSelect(message)
                            }
                        )
                    }
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .padding(bottom = if (showTail) 8.dp else 0.dp)
                ) {
                    // Reply preview
                    replyToMessage?.let { original ->
                        val replyAuthorName = when (original.senderId) {
                            currentUserId    -> "Вы"
                            interlocutorUserId -> interlocutorUsername
                            else             -> "Неизвестно"
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = textColor.copy(alpha = 0.15f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onScrollToMessage(original.id) }
                                .padding(bottom = 6.dp)
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(40.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(2.dp))
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = replyAuthorName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = original.text ?: "Сообщение удалено",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = textColor.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    } ?: message.replyToId?.let {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = textColor.copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                        ) {
                            Text(
                                text = "Сообщение недоступно",
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor.copy(alpha = 0.4f),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Основной текст — НЕ курсив
                    Text(
                        text = displayText,
                        color = textColor,
                        fontStyle = FontStyle.Normal,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Spacer(Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // "изм." курсивом
                        if (message.editedAt != null) {
                            Text(
                                text = "изм. ",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontStyle = FontStyle.Italic
                                ),
                                color = textColor.copy(alpha = 0.5f)
                            )
                        }
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
        }

        // Контекстное меню с анимацией
        AnimatedVisibility(
            visible = showMenu,
            enter = fadeIn(tween(150)) + scaleIn(
                tween(150),
                transformOrigin = if (isOwn)
                    TransformOrigin(1f, 1f)
                else
                    TransformOrigin(0f, 1f)
            ),
            exit = fadeOut(tween(100)) + scaleOut(tween(100))
        ) {
            MessageContextMenu(
                isOwn = isOwn,
                onReply = { showMenu = false; onReply(message) },
                onEdit = { showMenu = false; onEdit(message) },
                onSelect = {
                    showMenu = false
                    onEnterMultiSelect(message)   // выбираем сообщение после закрытия меню
                },
                onDeleteForMe = { showMenu = false; onDelete(message.id, false) },
                onDeleteForAll = { showMenu = false; onDelete(message.id, true) },
                onDismiss = { showMenu = false }
            )
        }
    }
}

@Composable
fun MessageContextMenu(
    isOwn: Boolean,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onSelect: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForAll: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onDismiss() }
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(if (isOwn) Alignment.TopEnd else Alignment.TopStart)
                .padding(horizontal = 16.dp)
                .width(220.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                MenuRow(icon = R.drawable.reply,      label = "Ответить",       onClick = onReply)
                if (isOwn) {
                    MenuRow(icon = R.drawable.edit,   label = "Изменить",       onClick = onEdit)
                }
                MenuRow(icon = R.drawable.select,     label = "Выбрать",        onClick = onSelect)
                MenuRow(icon = R.drawable.delete,     label = "Удалить у себя", onClick = onDeleteForMe)
                MenuRow(icon = R.drawable.delete_forever, label = "Удалить у всех", onClick = onDeleteForAll)
                MenuRow(
                    icon = R.drawable.close,
                    label = "Скрыть",
                    onClick = onDismiss,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun MenuRow(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = tint)
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

