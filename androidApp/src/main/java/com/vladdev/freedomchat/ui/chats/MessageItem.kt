package com.vladdev.freedomchat.ui.chats

import android.graphics.Bitmap
import android.graphics.drawable.shapes.Shape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vladdev.freedomchat.R
import com.vladdev.shared.chats.dto.DecryptedMessage
import com.vladdev.shared.chats.dto.MessageDto
import com.vladdev.shared.chats.dto.MessageStatus
import com.vladdev.shared.crypto.MediaKeyCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.InternalSerializationApi
import java.io.File
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
    onScrollToMessage: (String) -> Unit,
    onForward: (DecryptedMessage) -> Unit,
    onPin: (DecryptedMessage) -> Unit,
    onForwardedAuthorClick: (userId: String?, name: String) -> Unit,
    onReact: (DecryptedMessage, String) -> Unit,
    userNames: Map<String, String>,
    onCopy: (DecryptedMessage) -> Unit,
    onUsernameClick: (username: String) -> Unit,
    currentUserNick: String,
    revealedSpoilersForMessage: Set<Int>,
    onRevealSpoiler: (Int) -> Unit,
    onMediaClick: (DecryptedMessage) -> Unit,
    mediaGroup: List<DecryptedMessage>? = null,
    uploadProgressMap: Map<String, Int> = emptyMap(),
    onCancelUpload: ((tempId: String) -> Unit)? = null,
) {
    val displayText = message.displayText
    val hasMedia = message.media != null || mediaGroup != null
    // Если ни текста ни медиа — скрываем
    if (displayText == null && !hasMedia) return
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
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showReactionPicker by remember { mutableStateOf(false) }
    var showAllEmojis by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val spoilerState = remember(message.id, revealedSpoilersForMessage) {
        mutableStateOf(revealedSpoilersForMessage)
    }
    // Цвет рамки при выборе
    val borderColor = if (isOwn)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.primary

    val replyColor = if (isOwn)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.secondary

    val bubbleColor = if (isOwn)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.secondaryContainer

    val textColor = if (isOwn)
        MaterialTheme.colorScheme.onPrimary
    else
        MaterialTheme.colorScheme.onSecondaryContainer

    val bubbleShape = remember(isOwn, showTail) { messageBubbleShape(isOwn, showTail) }
    LaunchedEffect(revealedSpoilersForMessage) {
        spoilerState.value = revealedSpoilersForMessage
    }
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
                    .pointerInput(isMultiSelectMode) {
                        detectTapGestures(
                            onLongPress = {
                                if (!isMultiSelectMode) showMenu = true
                            },
                            onTap = {
                                when {
                                    isMultiSelectMode      -> onSelect(message)
                                    showReactionPicker     -> {
                                        showReactionPicker = false
                                        showAllEmojis = false
                                    }
                                    else                   -> {
                                        showReactionPicker = true
                                        showAllEmojis = false
                                    }
                                }
                            }
                        )
                    }
            ) {
                Column(
                    modifier = Modifier
                        .padding(
                            horizontal = if (hasMedia && displayText == null) 4.dp else 12.dp,
                            vertical   = if (hasMedia && displayText == null) 4.dp else 8.dp
                        )
                        .padding(bottom = if (showTail) 4.dp else 0.dp)
                ) {
                    // Reply preview
                    replyToMessage?.let { original ->
                        val replyAuthorName = when (original.senderId) {
                            currentUserId      -> "Вы"
                            interlocutorUserId -> interlocutorUsername
                            else               -> "Неизвестно"
                        }

                        // Текст превью с учётом медиа
                        val replyPreviewText = when {
                            original.media != null && !original.text.isNullOrBlank() ->
                                "${mediaTypeLabel(original.media!!.type)}: ${original.text}"
                            original.media != null ->
                                mediaTypeLabel(original.media!!.type)
                            original.deletedForAll ->
                                "Сообщение удалено"
                            else ->
                                original.text ?: "Сообщение удалено"
                        }

                        Surface(
                            shape    = RoundedCornerShape(8.dp),
                            color    = textColor.copy(alpha = 0.15f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onScrollToMessage(original.id) }
                                .padding(bottom = 6.dp)
                        ) {
                            Row(
                                modifier          = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(40.dp)
                                        .background(replyColor, RoundedCornerShape(2.dp))
                                )
                                Spacer(Modifier.width(8.dp))

                                // Миниатюра медиа слева — показываем если есть localPath
                                if (original.media != null && original.mediaLocalPath != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                    ) {
                                        if (original.media!!.type.uppercase() == "VIDEO") {
                                            // Стоп-кадр для видео
                                            var thumb by remember(original.mediaLocalPath) {
                                                mutableStateOf<Bitmap?>(null)
                                            }
                                            LaunchedEffect(original.mediaLocalPath) {
                                                thumb = withContext(Dispatchers.IO) {
                                                    extractVideoFrame(original.mediaLocalPath!!)
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
                                                        .background(Color.Black.copy(alpha = 0.3f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        painter           = painterResource(R.drawable.ic_video),
                                                        contentDescription = null,
                                                        tint              = Color.White,
                                                        modifier          = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        } else {
                                            AsyncImage(
                                                model              = File(original.mediaLocalPath!!),
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
                                        text       = replyAuthorName,
                                        style      = MaterialTheme.typography.labelSmall,
                                        color      = replyColor,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text     = replyPreviewText,
                                        style    = MaterialTheme.typography.bodySmall,
                                        color    = textColor.copy(alpha = 0.7f),
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

                    message.forwardedFromName?.let { name ->
                        val isForwardedFromMe = message.forwardedFromId == currentUserId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp)
                                .then(
                                    if (!isForwardedFromMe)
                                        Modifier.clickable { onForwardedAuthorClick(message.forwardedFromId, name) }
                                    else
                                        Modifier
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painterResource(R.drawable.forward),
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = textColor.copy(alpha = 0.6f)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text      = "Переслано от $name",
                                style     = MaterialTheme.typography.labelSmall,
                                color     = textColor.copy(alpha = 0.6f),
                                fontStyle = FontStyle.Italic
                            )
                        }
                    }
                    // MessageItem.kt — блок одиночного медиа

                    if (hasMedia) {
                        val groupToShow = mediaGroup ?: message.media?.let { listOf(message) }
                        if (groupToShow != null) {
                            val mediaModifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = if (displayText != null) 6.dp else 0.dp)

                            if (groupToShow.size > 1) {
                                MediaCollageGrid(
                                    mediaMessages     = groupToShow,
                                    onMediaClick      = { msg -> onMediaClick(msg) },
                                    uploadProgressMap = uploadProgressMap,
                                    onCancelUpload    = onCancelUpload,
                                    modifier          = mediaModifier
                                )
                            } else {
                                val singleMsg = groupToShow.first()
                                val singleMedia = singleMsg.media
                                val ratio       = smartAspectRatio(singleMedia)
                                Box(modifier = mediaModifier.aspectRatio(ratio).clip(RoundedCornerShape(8.dp))
                                    .clickable { onMediaClick(singleMsg) }
                                ) {
                                    MediaThumbContent(
                                        message        = singleMsg,
                                        uploadProgress = uploadProgressMap[singleMsg.id],
                                        onCancelUpload = onCancelUpload?.let { { it(singleMsg.id) } }
                                    )
                                }
                            }
                        }
                    }

                    if (displayText != null) {
                        MessageText(
                            text             = displayText,
                            textColor        = textColor,
                            style            = MaterialTheme.typography.bodyLarge,
                            onUsernameClick  = { username ->
                                if (username != currentUserNick) onUsernameClick(username)
                            },
                            revealedSpoilers = spoilerState,
                            onUrlClick       = { url ->
                                try {
                                    uriHandler.openUri(if (url.startsWith("http")) url else "https://$url")
                                } catch (_: Exception) { }
                            }
                        )
                    }

                    LaunchedEffect(spoilerState.value) {
                        spoilerState.value.forEach { onRevealSpoiler(it) }
                    }

                    Spacer(Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (message.pinnedAt != null) {
                            Icon(
                                painter           = painterResource(R.drawable.ic_pin),
                                contentDescription = null,
                                modifier          = Modifier.size(14.dp),
                                tint              = textColor.copy(alpha = 0.7f)
                            )
                        }
                        if (message.editedAt != null) {
                            Text(
                                text  = "изм. ",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize  = 10.sp,
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
                            text  = timeText,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = textColor.copy(alpha = 0.6f)
                        )
                        statusIconRes?.let {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                painter           = painterResource(id = it),
                                contentDescription = null,
                                modifier          = Modifier.size(14.dp),
                                tint              = textColor.copy(alpha = 0.7f)
                            )
                        }
                    }

                    if (message.reactions.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier        = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            ReactionPills(
                                reactions       = message.reactions,
                                currentUserId   = currentUserId,
                                userNames       = userNames,
                                onReactionClick = { emoji -> onReact(message, emoji) },
                                textColor       = textColor,
                                bubbleColor     = bubbleColor
                            )
                        }
                    }
                }
            }
        }
        // ── Пикер реакций (одиночный тап) ─────────────────────────
        AnimatedVisibility(
            visible     = showReactionPicker && !showMenu,
            enter       = fadeIn(tween(150)) + scaleIn(
                animationSpec   = tween(150),
                transformOrigin = if (isOwn) TransformOrigin(1f, 1f) else TransformOrigin(0f, 1f)
            ),
            exit        = fadeOut(tween(100)) + scaleOut(
                animationSpec   = tween(100),
                transformOrigin = if (isOwn) TransformOrigin(1f, 1f) else TransformOrigin(0f, 1f)
            )
        ) {
            ReactionPickerPopup(
                message       = message,
                currentUserId = currentUserId,
                isOwn         = isOwn,
                onReact       = { emoji ->
                    showReactionPicker = false
                    showAllEmojis = false
                    onReact(message, emoji)
                },
                onDismiss     = {
                    showReactionPicker = false
                    showAllEmojis = false
                }
            )
        }

// ── Контекстное меню (долгий тап) ─────────────────────────
        AnimatedVisibility(
            visible     = showMenu,
            enter       = fadeIn(tween(150)) + scaleIn(
                animationSpec   = tween(150),
                transformOrigin = if (isOwn) TransformOrigin(1f, 1f) else TransformOrigin(0f, 1f)
            ),
            exit        = fadeOut(tween(100)) + scaleOut(tween(100))
        ) {
            MessageContextMenu(
                isOwn          = isOwn,
                hasMedia = hasMedia,
                onReply        = { showMenu = false; onReply(message) },
                onEdit         = { showMenu = false; onEdit(message) },
                onSelect       = { showMenu = false; onEnterMultiSelect(message) },
                onDeleteForMe  = { showMenu = false; onDelete(message.id, false) },
                onDeleteForAll = { showMenu = false; onDelete(message.id, true) },
                onDismiss      = { showMenu = false },
                onForward      = { showMenu = false; onForward(message) },
                onPin          = { showMenu = false; onPin(message) },
                onCopy         = { showMenu = false; onCopy(message) },
            )
        }
    }
}

@Composable
fun MessageContextMenu(
    isOwn: Boolean,
    hasMedia: Boolean,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onSelect: () -> Unit,
    onForward: () -> Unit,
    onPin: () -> Unit,
    onCopy: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication        = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onDismiss() }
    ) {
        Surface(
            shape           = RoundedCornerShape(16.dp),
            color           = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 8.dp,
            modifier        = Modifier
                .align(if (isOwn) Alignment.TopEnd else Alignment.TopStart)
                .padding(horizontal = 16.dp)
                .width(220.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                MenuRow(R.drawable.reply,   "Ответить",       onReply)
                if (!hasMedia) {
                    MenuRow(R.drawable.forward, "Переслать", onForward)
                }
                if (!hasMedia) {
                    MenuRow(R.drawable.content_copy, "Копировать", onCopy)
                }
                if (isOwn && !hasMedia) {
                    MenuRow(R.drawable.edit, "Изменить", onEdit)
                }
                MenuRow(R.drawable.ic_pin,        "Закрепить",      onPin)
                MenuRow(R.drawable.select,        "Выбрать",        onSelect)
                MenuRow(R.drawable.delete,        "Удалить у себя", onDeleteForMe)
                MenuRow(R.drawable.delete_forever,"Удалить у всех", onDeleteForAll)
                MenuRow(
                    icon    = R.drawable.close,
                    label   = "Скрыть",
                    onClick = onDismiss,
                    tint    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
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

@Composable
fun MessageText(
    text: String,
    textColor: Color,
    style: TextStyle,
    onUsernameClick: (String) -> Unit,
    onUrlClick: (String) -> Unit,
    revealedSpoilers: MutableState<Set<Int>>  // пробрасываем состояние снаружи
) {
    val uriHandler = LocalUriHandler.current

    val usernameRegex = remember { Regex("""@([A-Za-z0-9_]{2,32})""") }
    val urlRegex      = remember { Regex("""https?://[^\s]+|www\.[^\s]+""") }

    val (mdAnnotated, _) = remember(text, revealedSpoilers.value, textColor) {
        parseMarkdown(text, textColor, revealedSpoilers.value)
    }

    // Поверх MD-аннотаций накладываем username/url аннотации
    val annotated = remember(mdAnnotated, text) {
        buildAnnotatedString {
            append(mdAnnotated)

            data class LinkMatch(val range: IntRange, val tag: String, val value: String)
            val links = (
                    usernameRegex.findAll(text).map {
                        LinkMatch(it.range, "USERNAME", it.groupValues[1])
                    } +
                            urlRegex.findAll(text).map {
                                LinkMatch(it.range, "URL", it.value)
                            }
                    ).sortedBy { it.range.first }

            // Нужно пересчитать позиции — MD-парсер мог съесть маркеры (**,~~,||)
            // Поэтому аннотируем по plain-тексту через отдельный проход
            links.forEach { link ->
                // Ищем позицию в уже построенной AnnotatedString
                val plainInAnnotated = mdAnnotated.text.indexOf(
                    text.substring(link.range).let {
                        if (link.tag == "USERNAME") it else it
                    }
                )
                if (plainInAnnotated >= 0) {
                    val end = plainInAnnotated + link.range.count()
                    addStringAnnotation(
                        tag   = link.tag,
                        annotation = link.value,
                        start = plainInAnnotated,
                        end   = end.coerceAtMost(mdAnnotated.text.length)
                    )
                    addStyle(
                        SpanStyle(
                            textDecoration = TextDecoration.Underline,
                            fontWeight     = FontWeight.Medium
                        ),
                        start = plainInAnnotated,
                        end   = end.coerceAtMost(mdAnnotated.text.length)
                    )
                }
            }
        }
    }

    ClickableText(
        text  = annotated,
        style = style.copy(color = textColor),
        onClick = { offset ->
            // Спойлер
            annotated.getStringAnnotations("SPOILER", offset, offset)
                .firstOrNull()?.let { ann ->
                    val idx = ann.item.toIntOrNull() ?: return@ClickableText
                    revealedSpoilers.value = revealedSpoilers.value + idx
                    return@ClickableText
                }
            // Username
            annotated.getStringAnnotations("USERNAME", offset, offset)
                .firstOrNull()?.let { onUsernameClick(it.item); return@ClickableText }
            // URL
            annotated.getStringAnnotations("URL", offset, offset)
                .firstOrNull()?.let {
                    try {
                        uriHandler.openUri(
                            if (it.item.startsWith("http")) it.item else "https://${it.item}"
                        )
                    } catch (_: Exception) {}
                }
        }
    )
}

// Markdown токены которые поддерживаем
private val MD_PATTERNS = listOf(
    "BOLD"      to Regex("""(?<!\*)\*\*(.+?)\*\*(?!\*)"""),
    "ITALIC"    to Regex("""(?<![_*])_(.+?)_(?![_*])"""),
    "STRIKE"    to Regex("""~~(.+?)~~"""),
    "SPOILER"   to Regex("""\|\|(.+?)\|\|""")
)

fun parseMarkdown(
    text: String,
    textColor: Color,
    revealedSpoilers: Set<Int>  // индексы раскрытых спойлеров
): Pair<AnnotatedString, List<IntRange>> {  // аннотированный текст + диапазоны спойлеров

    data class Token(val range: IntRange, val type: String, val inner: String)

    // Собираем все совпадения, сортируем по позиции
    val tokens = MD_PATTERNS
        .flatMap { (type, regex) ->
            regex.findAll(text).map { Token(it.range, type, it.groupValues[1]) }
        }
        .sortedBy { it.range.first }
        // Убираем пересекающиеся (берём первый)
        .fold(emptyList<Token>()) { acc, token ->
            if (acc.isNotEmpty() && token.range.first <= acc.last().range.last) acc
            else acc + token
        }

    val spoilerRanges = mutableListOf<IntRange>()
    var spoilerIndex  = 0

    val annotated = buildAnnotatedString {
        var cursor = 0
        tokens.forEach { token ->
            // Текст до токена
            if (token.range.first > cursor) {
                append(text.substring(cursor, token.range.first))
            }
            when (token.type) {
                "BOLD" -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(token.inner)
                }
                "ITALIC" -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(token.inner)
                }
                "STRIKE" -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    append(token.inner)
                }
                "SPOILER" -> {
                    val idx      = spoilerIndex++
                    val revealed = idx in revealedSpoilers
                    val start    = length
                    pushStringAnnotation("SPOILER", idx.toString())
                    withStyle(
                        SpanStyle(
                            color      = if (revealed) textColor
                            else Color.Transparent,
                            background = textColor.copy(alpha = if (revealed) 0.15f else 0.85f)
                        )
                    ) { append(token.inner) }
                    pop()
                    spoilerRanges.add(start until (start + token.inner.length))
                }
            }
            cursor = token.range.last + 1
        }
        if (cursor < text.length) append(text.substring(cursor))
    }

    return annotated to spoilerRanges
}

