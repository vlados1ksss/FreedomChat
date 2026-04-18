package com.vladdev.freedomchat.ui.chats

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.vladdev.shared.chats.dto.DecryptedMessage
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vladdev.freedomchat.R
import com.vladdev.shared.chats.dto.ReactionDto

// Константы
val AVAILABLE_EMOJIS = listOf(
    "😁","🤣","😍","🤩","🫣","🤫","🫥","😎","🥳",
    "🤮","🥵","😭","💩","🤡","💯","💦","👍","👎","🖕",
    "🫶","💓","💖","💔","❤️‍🩹","🐽","🥂","🏀","✨",
    "🔥","🌟","💎","🍑","🍆","✅","⛔","🇺🇸","🇷🇸",
    "❤️","❤️‍🔥","🥰","💋","💘"
)

@Composable
fun EmojiPickerRow(
    message: DecryptedMessage,
    currentUserId: String?,
    onReact: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val myEmojis = remember(message.reactions, currentUserId) {
        message.reactions.filter { it.userId == currentUserId }.map { it.emoji }.toSet()
    }
    val myCount = myEmojis.size

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 8.dp,
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AVAILABLE_EMOJIS.forEach { emoji ->
                val isSelected = emoji in myEmojis
                val isDisabled = !isSelected && myCount >= 3
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else
                                Color.Transparent
                        )
                        .clickable(enabled = !isDisabled) {
                            onReact(emoji)
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emoji,
                        fontSize = 20.sp,
                        modifier = Modifier.graphicsLayer {
                            alpha = if (isDisabled) 0.35f else 1f
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ReactionPills(
    reactions: List<ReactionDto>,
    currentUserId: String?,
    userNames: Map<String, String>,          // userId → displayName
    onReactionClick: (String) -> Unit,
    textColor: Color,
    bubbleColor: Color
) {
    if (reactions.isEmpty()) return

    val grouped = remember(reactions) {
        reactions
            .sortedBy { it.createdAt }
            .groupBy { it.emoji }
    }

    FlowRow(
        modifier             = Modifier
            .padding(top = 6.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement   = Arrangement.spacedBy(4.dp)
    ) {
        grouped.forEach { (emoji, reactors) ->
            val iMine = reactors.any { it.userId == currentUserId }

            Surface(
                shape  = RoundedCornerShape(12.dp),
                color  = textColor.copy(alpha = 0.10f),
                border = BorderStroke(
                    1.dp,
                    if (iMine) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    else        textColor.copy(alpha = 0.20f)
                ),
                modifier = Modifier.clickable { onReactionClick(emoji) }
            ) {
                Row(
                    modifier              = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(text = emoji, fontSize = 14.sp)

                    // Аватары с overlap
                    Box(
                        modifier = Modifier
                            .height(16.dp)
                            .width((reactors.take(3).size * 12 + 4).dp)
                    ) {
                        reactors.take(3).forEachIndexed { index, reactor ->
                            UserAvatarTiny(
                                name      = userNames[reactor.userId] ?: reactor.userId.take(2),
                                avatarUrl = null,
                                isSelf    = reactor.userId == currentUserId,
                                size      = 16.dp,
                                modifier  = Modifier.offset(x = (index * 10).dp)
                            )
                        }
                    }

                    if (reactors.size > 3) {
                        Text(
                            text  = "+${reactors.size - 3}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = textColor.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UserAvatarTiny(
    name: String,
    avatarUrl: String?,
    isSelf: Boolean,
    size: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    val initials  = remember(name) { name.take(2).uppercase() }
    val bgColor   = if (isSelf) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.primaryContainer
    val textColor = if (isSelf) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onPrimaryContainer

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model              = avatarUrl,
                contentDescription = null,
                modifier           = Modifier.fillMaxSize(),
                contentScale       = ContentScale.Crop
            )
        } else {
            Text(
                text  = initials,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color      = textColor,
                    fontSize   = (size.value * 0.38f).sp
                )
            )
        }
    }
}
@Composable
fun ReactionPickerPopup(
    message: DecryptedMessage,
    currentUserId: String?,
    isOwn: Boolean,
    onReact: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val quickEmojis = listOf(
        "❤️", "❤️‍🔥", "🥰", "💋", "💘"
    )
    val allEmojis = listOf(
        "😁","🤣","😍","🤩","🫣","🤫","🫥","😎","🥳",
        "🤮","🥵","😭","💩","🤡","💯","💦","👍","👎","🖕",
        "🫶","💓","💖","💔","❤️‍🩹","🐽","🥂","🏀","✨",
        "🔥","🌟","💎","🍑","🍆","✅","⛔","🇺🇸","🇷🇸",
        "❤️","❤️‍🔥","🥰","💋","💘"
    )

    var showAll by remember { mutableStateOf(false) }

    // Закрываем по тапу на внешний Box
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication        = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onDismiss() }
    ) {
        Surface(
            shape           = RoundedCornerShape(24.dp),
            color           = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 6.dp,
            modifier        = Modifier
                .align(if (isOwn) Alignment.TopEnd else Alignment.TopStart)
                .padding(horizontal = 16.dp)
                // Останавливаем всплытие тапа чтобы клик внутри не закрывал
                .clickable(
                    indication        = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { /* перехватываем */ }
        ) {
            AnimatedContent(
                targetState  = showAll,
                transitionSpec = {
                    (fadeIn(tween(180)) + expandHorizontally(tween(180))) togetherWith
                            (fadeOut(tween(120)) + shrinkHorizontally(tween(120)))
                },
                label = "reaction_picker_content"
            ) { expanded ->
                Row(
                    modifier              = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (!expanded) {
                        // Быстрые 5 эмодзи
                        quickEmojis.forEach { emoji ->
                            EmojiButton(
                                emoji    = emoji,
                                selected = message.reactions.any {
                                    it.emoji == emoji && it.userId == currentUserId
                                },
                                onClick  = { onReact(emoji); onDismiss() }
                            )
                        }
                        // Кнопка «ещё»
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)
                                )
                                .clickable { showAll = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter            = painterResource(R.drawable.arrow_down),
                                contentDescription = "Ещё",
                                modifier           = Modifier.size(16.dp),
                                tint               = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        // Полная сетка — оборачиваем в FlowRow фиксированной ширины
                        FlowRow(
                            modifier              = Modifier.width(240.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement   = Arrangement.spacedBy(2.dp),
                            maxItemsInEachRow     = 8
                        ) {
                            allEmojis.forEach { emoji ->
                                EmojiButton(
                                    emoji    = emoji,
                                    selected = message.reactions.any {
                                        it.emoji == emoji && it.userId == currentUserId
                                    },
                                    onClick  = { onReact(emoji); onDismiss() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmojiButton(
    emoji: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(
                if (selected)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                else
                    Color.Transparent
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text     = emoji,
            fontSize = 20.sp
        )
    }
}