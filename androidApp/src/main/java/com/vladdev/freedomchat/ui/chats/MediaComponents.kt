package com.vladdev.freedomchat.ui.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vladdev.freedomchat.R
import com.vladdev.shared.chats.dto.DecryptedMessage
import com.vladdev.shared.chats.dto.MediaDto
import com.vladdev.shared.chats.dto.MediaState
import com.vladdev.shared.crypto.MediaKeyCache
import java.io.File

@Composable
fun MediaContent(
    messages: List<DecryptedMessage>,
    currentMessage: DecryptedMessage,
    mediaGroup: List<DecryptedMessage>?,   // НОВОЕ: null = одиночное
    onMediaClick: (DecryptedMessage, Int) -> Unit,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val media = currentMessage.media ?: return

    if (mediaGroup != null && mediaGroup.size > 1) {
        MediaCollageGrid(
            mediaMessages = mediaGroup,
            onMediaClick  = { msg -> onMediaClick(msg, 0) },
            modifier      = modifier
        )
    } else {
        SingleMediaPreview(
            message      = currentMessage,
            media        = media,
            onMediaClick = { onMediaClick(currentMessage, 0) },
            modifier     = modifier
        )
    }
}

@Composable
fun SingleMediaPreview(
    message: DecryptedMessage,
    media: MediaDto,
    onMediaClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onMediaClick() }
    ) {
        when (media.type.uppercase()) {
            "PHOTO" -> PhotoPreview(message = message, media = media)
            "VIDEO" -> VideoPreview(message = message, media = media)
            else    -> UnknownMediaPlaceholder(media = media)
        }
    }
}

@Composable
private fun PhotoPreview(
    message: DecryptedMessage,
    media: MediaDto
) {
    val localPath = message.mediaLocalPath

    // Рассчитываем aspect ratio из метаданных или дефолтный 4:3
    val aspectRatio = if (media.width != null && media.height != null && media.height!! > 0)
        media.width!!.toFloat() / media.height!!.toFloat()
    else 4f / 3f

    val imageModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(aspectRatio.coerceIn(0.5f, 2f))

    when {
        localPath != null -> {
            // Уже расшифровано — показываем файл
            AsyncImage(
                model              = File(localPath),
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = imageModifier
            )
        }
        message.mediaState == MediaState.DOWNLOADING ||
                message.mediaState == MediaState.DECRYPTING  -> {
            // Загружается
            Box(
                modifier         = imageModifier.background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color    = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        else -> {
            // Не загружено — показываем плейсхолдер с иконкой
            Box(
                modifier         = imageModifier.background(
                    MaterialTheme.colorScheme.surfaceVariant
                ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter           = painterResource(R.drawable.ic_photo),
                        contentDescription = null,
                        modifier          = Modifier.size(40.dp),
                        tint              = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text  = "Нажмите для загрузки",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoPreview(
    message: DecryptedMessage,
    media: MediaDto
) {
    val aspectRatio = if (media.width != null && media.height != null && media.height!! > 0)
        media.width!!.toFloat() / media.height!!.toFloat()
    else 16f / 9f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio.coerceIn(0.5f, 2f))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (message.mediaLocalPath != null) {
            // Превью первого кадра через coil
            AsyncImage(
                model              = File(message.mediaLocalPath!!),
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                painter           = painterResource(R.drawable.ic_video),
                contentDescription = null,
                tint              = Color.White.copy(alpha = 0.6f),
                modifier          = Modifier.size(48.dp)
            )
        }

        // Кнопка Play поверх
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter           = painterResource(R.drawable.ic_play),
                contentDescription = "Воспроизвести",
                tint              = Color.White,
                modifier          = Modifier.size(24.dp)
            )
        }

        // Длительность
        media.durationSec?.let { dur ->
            Text(
                text     = "%d:%02d".format(dur / 60, dur % 60),
                style    = MaterialTheme.typography.labelSmall,
                color    = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        // Индикатор загрузки
        if (message.mediaState == MediaState.DOWNLOADING) {
            CircularProgressIndicator(
                color    = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun UnknownMediaPlaceholder(media: MediaDto) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text  = mediaTypeLabel(media.type),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// MediaCollageGrid.kt

@Composable
fun MediaCollageGrid(
    mediaMessages: List<DecryptedMessage>,
    onMediaClick: (DecryptedMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    val count = mediaMessages.size

    when (count) {
        1 -> SingleMediaPreview(
            message      = mediaMessages[0],
            media        = mediaMessages[0].media!!,
            onMediaClick = { onMediaClick(mediaMessages[0]) },
            modifier     = modifier
        )

        2 -> Row(
            modifier            = modifier.fillMaxWidth().aspectRatio(2f),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            mediaMessages.forEach { msg ->
                SingleMediaPreview(
                    message      = msg,
                    media        = msg.media!!,
                    onMediaClick = { onMediaClick(msg) },
                    modifier     = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }

        3 -> Row(
            modifier              = modifier.fillMaxWidth().aspectRatio(1.5f),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            SingleMediaPreview(
                message      = mediaMessages[0],
                media        = mediaMessages[0].media!!,
                onMediaClick = { onMediaClick(mediaMessages[0]) },
                modifier     = Modifier.weight(1f).fillMaxHeight()
            )
            Column(
                modifier            = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                mediaMessages.drop(1).forEach { msg ->
                    SingleMediaPreview(
                        message      = msg,
                        media        = msg.media!!,
                        onMediaClick = { onMediaClick(msg) },
                        modifier     = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            }
        }

        else -> {
            // 4+ — сетка 2 колонки, последняя ячейка показывает "+N"
            val visible = mediaMessages.take(4)
            val overflow = count - 4

            Column(
                modifier            = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                visible.chunked(2).forEachIndexed { rowIndex, rowItems ->
                    Row(
                        modifier              = Modifier.fillMaxWidth().height(140.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        rowItems.forEachIndexed { colIndex, msg ->
                            val isLast = rowIndex == 1 && colIndex == 1 && overflow > 0
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                SingleMediaPreview(
                                    message      = msg,
                                    media        = msg.media!!,
                                    onMediaClick = { onMediaClick(msg) },
                                    modifier     = Modifier.fillMaxSize()
                                )
                                // Оверлей "+N" на последней ячейке
                                if (isLast) {
                                    Box(
                                        modifier         = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.55f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text  = "+$overflow",
                                            style = MaterialTheme.typography.titleLarge,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                        // Если нечётный ряд — заполняем пустой блок
                        if (rowItems.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}