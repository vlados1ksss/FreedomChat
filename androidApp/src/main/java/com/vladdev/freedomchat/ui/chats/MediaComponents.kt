package com.vladdev.freedomchat.ui.chats

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vladdev.freedomchat.R
import com.vladdev.shared.chats.dto.DecryptedMessage
import com.vladdev.shared.chats.dto.MediaDto
import com.vladdev.shared.chats.dto.MediaState
import com.vladdev.shared.crypto.MediaKeyCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

//@Composable
//fun MediaContent(
//    messages: List<DecryptedMessage>,
//    currentMessage: DecryptedMessage,
//    mediaGroup: List<DecryptedMessage>?,   // НОВОЕ: null = одиночное
//    onMediaClick: (DecryptedMessage, Int) -> Unit,
//    textColor: Color,
//    modifier: Modifier = Modifier
//) {
//    val media = currentMessage.media ?: return
//
//    if (mediaGroup != null && mediaGroup.size > 1) {
//        MediaCollageGrid(
//            mediaMessages = mediaGroup,
//            onMediaClick  = { msg -> onMediaClick(msg, 0) },
//            modifier      = modifier
//        )
//    } else {
//        SingleMediaPreview(
//            message      = currentMessage,
//            media        = media,
//            onMediaClick = { onMediaClick(currentMessage, 0) },
//            modifier     = modifier
//        )
//    }
//}
//
//@Composable
//fun SingleMediaPreview(
//    message: DecryptedMessage,
//    media: MediaDto,
//    onMediaClick: () -> Unit,
//    modifier: Modifier = Modifier
//) {
//    val context = LocalContext.current
//
//    Box(
//        modifier = modifier
//            .fillMaxWidth()
//            .clip(RoundedCornerShape(8.dp))
//            .clickable { onMediaClick() }
//    ) {
//        when (media.type.uppercase()) {
//            "PHOTO" -> PhotoPreview(message = message, media = media)
//            "VIDEO" -> VideoPreview(message = message, media = media)
//            else    -> UnknownMediaPlaceholder(media = media)
//        }
//    }
//}
//
//@Composable
//private fun PhotoPreview(
//    message: DecryptedMessage,
//    media: MediaDto,
//    uploadProgress: Int? = null,      // null = не наш upload, Int = 0..100
//    onCancelUpload: (() -> Unit)? = null
//) {
//    val aspectRatio = if (media.width != null && media.height != null && media.height!! > 0)
//        media.width!!.toFloat() / media.height!!.toFloat()
//    else 4f / 3f
//
//    val imageModifier = Modifier
//        .fillMaxWidth()
//        .aspectRatio(aspectRatio.coerceIn(0.5f, 2f))
//
//    Box(modifier = imageModifier) {
//        when {
//            // Загружено полностью
//            message.mediaLocalPath != null -> {
//                AsyncImage(
//                    model              = File(message.mediaLocalPath!!),
//                    contentDescription = null,
//                    contentScale       = ContentScale.Crop,
//                    modifier           = Modifier.fillMaxSize()
//                )
//            }
//            // Есть превью (оптимистичное сообщение во время отправки)
//            message.mediaThumbPath != null -> {
//                AsyncImage(
//                    model              = File(message.mediaThumbPath!!),
//                    contentDescription = null,
//                    contentScale       = ContentScale.Crop,
//                    modifier           = Modifier.fillMaxSize()
//                )
//            }
//            // Нет ничего — плейсхолдер
//            else -> {
//                Box(
//                    modifier         = Modifier
//                        .fillMaxSize()
//                        .background(MaterialTheme.colorScheme.surfaceVariant),
//                    contentAlignment = Alignment.Center
//                ) {
//                    if (message.mediaState == MediaState.DOWNLOADING ||
//                        message.mediaState == MediaState.DECRYPTING) {
//                        CircularProgressIndicator(
//                            color    = Color.White,
//                            modifier = Modifier.size(32.dp)
//                        )
//                    } else {
//                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
//                            Icon(
//                                painter            = painterResource(R.drawable.ic_photo),
//                                contentDescription = null,
//                                modifier           = Modifier.size(40.dp),
//                                tint               = MaterialTheme.colorScheme.onSurfaceVariant
//                            )
//                            Spacer(Modifier.height(4.dp))
//                            Text(
//                                text  = "Нажмите для загрузки",
//                                style = MaterialTheme.typography.labelSmall,
//                                color = MaterialTheme.colorScheme.onSurfaceVariant
//                            )
//                        }
//                    }
//                }
//            }
//        }
//
//        // Оверлей прогресса загрузки поверх превью
//        if (uploadProgress != null) {
//            Box(
//                modifier         = Modifier
//                    .fillMaxSize()
//                    .background(Color.Black.copy(alpha = 0.35f)),
//                contentAlignment = Alignment.Center
//            ) {
//                Column(
//                    horizontalAlignment = Alignment.CenterHorizontally,
//                    verticalArrangement = Arrangement.Center
//                ) {
//                    CircularProgressIndicator(
//                        progress = uploadProgress / 100f,
//                        color    = Color.White,
//                        modifier = Modifier.size(36.dp),
//                        strokeWidth = 3.dp
//                    )
//                    Spacer(Modifier.height(6.dp))
//                    Text(
//                        text  = "$uploadProgress%",
//                        style = MaterialTheme.typography.labelSmall,
//                        color = Color.White
//                    )
//                    onCancelUpload?.let {
//                        Spacer(Modifier.height(4.dp))
//                        Text(
//                            text     = "Отмена",
//                            style    = MaterialTheme.typography.labelSmall,
//                            color    = Color.White.copy(alpha = 0.8f),
//                            modifier = Modifier.clickable { it() }
//                        )
//                    }
//                }
//            }
//        }
//    }
//}
//
//@Composable
//private fun VideoPreview(
//    message: DecryptedMessage,
//    media: MediaDto,
//    uploadProgress: Int? = null,
//    onCancelUpload: (() -> Unit)? = null
//) {
//    val aspectRatio = if (media.width != null && media.height != null && media.height!! > 0)
//        media.width!!.toFloat() / media.height!!.toFloat()
//    else 16f / 9f
//    val durationSec = message.media?.durationSec
//
//    Box(
//        modifier = Modifier
//            .fillMaxWidth()
//            .aspectRatio(aspectRatio.coerceIn(0.5f, 2f))
//            .background(Color.Black),
//        contentAlignment = Alignment.Center
//    ) {
//        // Превью кадра — сразу из thumbPath или localPath
//        val previewPath = message.mediaLocalPath ?: message.mediaThumbPath
//        if (previewPath != null) {
//            AsyncImage(
//                model              = File(previewPath),
//                contentDescription = null,
//                contentScale       = ContentScale.Crop,
//                modifier           = Modifier.fillMaxSize()
//            )
//        } else {
//            Icon(
//                painter            = painterResource(R.drawable.ic_video),
//                contentDescription = null,
//                tint               = Color.White.copy(alpha = 0.6f),
//                modifier           = Modifier.size(48.dp)
//            )
//        }
//
//        // Кнопка Play — только когда не грузится
//        if (uploadProgress == null && message.mediaState != MediaState.DOWNLOADING) {
//            Box(
//                modifier = Modifier
//                    .size(48.dp)
//                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
//                contentAlignment = Alignment.Center
//            ) {
//                Icon(
//                    painter            = painterResource(R.drawable.ic_play),
//                    contentDescription = "Воспроизвести",
//                    tint               = Color.White,
//                    modifier           = Modifier.size(24.dp)
//                )
//            }
//        }
//
//        // Длительность
//        durationSec?.let { dur ->
//            Box(
//                modifier = Modifier
//                    .align(Alignment.BottomEnd)
//                    .padding(6.dp)
//                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
//                    .padding(horizontal = 5.dp, vertical = 2.dp)
//            ) {
//                Text(
//                    text  = "%d:%02d".format(dur / 60, dur % 60),
//                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
//                    color = Color.White
//                )
//            }
//        }
//
//        // Оверлей прогресса (upload или download)
//        val showProgress = uploadProgress != null ||
//                message.mediaState == MediaState.DOWNLOADING ||
//                message.mediaState == MediaState.DECRYPTING
//
//        if (showProgress) {
//            Box(
//                modifier         = Modifier
//                    .fillMaxSize()
//                    .background(Color.Black.copy(alpha = 0.4f)),
//                contentAlignment = Alignment.Center
//            ) {
//                Column(
//                    horizontalAlignment = Alignment.CenterHorizontally,
//                    verticalArrangement = Arrangement.Center
//                ) {
//                    if (uploadProgress != null) {
//                        CircularProgressIndicator(
//                            progress    = uploadProgress / 100f,
//                            color       = Color.White,
//                            modifier    = Modifier.size(36.dp),
//                            strokeWidth = 3.dp
//                        )
//                        Spacer(Modifier.height(6.dp))
//                        Text(
//                            text  = "$uploadProgress%",
//                            style = MaterialTheme.typography.labelSmall,
//                            color = Color.White
//                        )
//                        onCancelUpload?.let {
//                            Spacer(Modifier.height(4.dp))
//                            Text(
//                                text     = "Отмена",
//                                style    = MaterialTheme.typography.labelSmall,
//                                color    = Color.White.copy(alpha = 0.8f),
//                                modifier = Modifier.clickable { it() }
//                            )
//                        }
//                    } else {
//                        CircularProgressIndicator(
//                            color    = Color.White,
//                            modifier = Modifier.size(32.dp)
//                        )
//                    }
//                }
//            }
//        }
//    }
//}

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

fun smartAspectRatio(media: MediaDto?): Float {
    if (media == null) return 3f / 4f
    val w = media.width ?: 0
    val h = media.height ?: 0
    if (w <= 0 || h <= 0) return 3f / 4f
    val ratio = w.toFloat() / h.toFloat()
    return when {
        ratio < 0.6f -> 9f / 16f   // вертикальное видео/портрет
        ratio > 1.4f -> 16f / 9f   // горизонтальное
        else         -> 3f / 4f    // квадрат/близко к нему
    }
}

@Composable
fun MediaCollageGrid(
    mediaMessages: List<DecryptedMessage>,
    onMediaClick: (DecryptedMessage) -> Unit,
    uploadProgressMap: Map<String, Int> = emptyMap(),
    onCancelUpload: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val count = mediaMessages.size
    val cornerRadius = 8.dp

    when (count) {
        1 -> {
            val msg   = mediaMessages[0]
            val ratio = smartAspectRatio(msg.media)
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(cornerRadius))
                    .clickable { onMediaClick(msg) }
            ) {
                MediaThumbContent(
                    message        = msg,
                    uploadProgress = uploadProgressMap[msg.id],
                    onCancelUpload = onCancelUpload?.let { { it(msg.id) } }
                )
            }
        }

        2 -> {
            // Два рядом, каждый 3:4
            Row(
                modifier            = modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(cornerRadius)),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                mediaMessages.forEachIndexed { i, msg ->
                    val shape = when (i) {
                        0    -> RoundedCornerShape(
                            topStart    = cornerRadius,
                            bottomStart = cornerRadius
                        )
                        else -> RoundedCornerShape(
                            topEnd    = cornerRadius,
                            bottomEnd = cornerRadius
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(shape)
                            .clickable { onMediaClick(msg) }
                    ) {
                        MediaThumbContent(
                            message        = msg,
                            uploadProgress = uploadProgressMap[msg.id],
                            onCancelUpload = onCancelUpload?.let { { it(msg.id) } }
                        )
                    }
                }
            }
        }

        3 -> {
            // Слева одно высокое (полная высота), справа два (делят пополам)
            val totalHeight = 280.dp
            Row(
                modifier              = modifier
                    .fillMaxWidth()
                    .height(totalHeight),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Левое — занимает всю высоту
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(
                            RoundedCornerShape(
                                topStart    = cornerRadius,
                                bottomStart = cornerRadius
                            )
                        )
                        .clickable { onMediaClick(mediaMessages[0]) }
                ) {
                    MediaThumbContent(
                        message        = mediaMessages[0],
                        uploadProgress = uploadProgressMap[mediaMessages[0].id],
                        onCancelUpload = onCancelUpload?.let { { it(mediaMessages[0].id) } }
                    )
                }

                // Правая колонка — два элемента строго одинаковой высоты
                Column(
                    modifier            = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(
                                RoundedCornerShape(topEnd = cornerRadius)
                            )
                            .clickable { onMediaClick(mediaMessages[1]) }
                    ) {
                        MediaThumbContent(
                            message        = mediaMessages[1],
                            uploadProgress = uploadProgressMap[mediaMessages[1].id],
                            onCancelUpload = onCancelUpload?.let { { it(mediaMessages[1].id) } }
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(
                                RoundedCornerShape(bottomEnd = cornerRadius)
                            )
                            .clickable { onMediaClick(mediaMessages[2]) }
                    ) {
                        MediaThumbContent(
                            message        = mediaMessages[2],
                            uploadProgress = uploadProgressMap[mediaMessages[2].id],
                            onCancelUpload = onCancelUpload?.let { { it(mediaMessages[2].id) } }
                        )
                    }
                }
            }
        }

        else -> {
            // 4+ — сетка 2x2, последняя ячейка с оверлеем "+N"
            val visible  = mediaMessages.take(4)
            val overflow = count - 4
            val rowHeight = 140.dp

            Column(
                modifier            = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                visible.chunked(2).forEachIndexed { rowIndex, rowItems ->
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .height(rowHeight),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        rowItems.forEachIndexed { colIndex, msg ->
                            val isLastCell = rowIndex == 1 && colIndex == 1
                            val shape = when {
                                rowIndex == 0 && colIndex == 0 -> RoundedCornerShape(topStart = cornerRadius)
                                rowIndex == 0 && colIndex == 1 -> RoundedCornerShape(topEnd = cornerRadius)
                                rowIndex == 1 && colIndex == 0 -> RoundedCornerShape(bottomStart = cornerRadius)
                                else                           -> RoundedCornerShape(bottomEnd = cornerRadius)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(shape)
                                    .clickable { onMediaClick(msg) }
                            ) {
                                MediaThumbContent(
                                    message        = msg,
                                    uploadProgress = uploadProgressMap[msg.id],
                                    onCancelUpload = onCancelUpload?.let { { it(msg.id) } }
                                )
                                if (isLastCell && overflow > 0) {
                                    Box(
                                        modifier         = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.55f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text       = "+$overflow",
                                            style      = MaterialTheme.typography.headlineMedium,
                                            color      = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                        // Нечётный ряд — пустой placeholder
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MediaThumbContent(
    message: DecryptedMessage,
    uploadProgress: Int? = null,
    onCancelUpload: (() -> Unit)? = null
) {
    val media       = message.media
    val isVideo     = media?.type?.uppercase() == "VIDEO"
    val localPath   = message.mediaLocalPath
    val thumbPath   = message.mediaThumbPath   // ← превью во время отправки
    val isUploading = uploadProgress != null

    var videoThumb by remember(localPath) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(localPath) {
        if (isVideo && localPath != null && videoThumb == null) {
            videoThumb = withContext(Dispatchers.IO) { extractVideoFrame(localPath) }
        }
    }

    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Фоновый контент
        when {
            localPath != null && isVideo -> {
                if (videoThumb != null) {
                    Image(
                        bitmap             = videoThumb!!.asImageBitmap(),
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(Color(0xFF1A1A1A)))
                }
            }
            localPath != null -> {
                AsyncImage(
                    model              = File(localPath),
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
            }
            // ← превью во время отправки (thumbPath есть, localPath ещё нет)
            thumbPath != null -> {
                AsyncImage(
                    model              = File(thumbPath),
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
            }
            else -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }

        // Остальной код без изменений — оверлей, прогресс, кнопка отмены, длительность, ошибка
        if (isUploading || message.mediaState == MediaState.DOWNLOADING ||
            message.mediaState == MediaState.DECRYPTING) {

            Box(
                modifier         = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                if (isUploading && uploadProgress != null) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress    = { uploadProgress / 100f },
                            modifier    = Modifier.size(52.dp),
                            color       = Color.White,
                            trackColor  = Color.White.copy(alpha = 0.25f),
                            strokeWidth = 3.dp
                        )
                        Text(
                            text  = "$uploadProgress%",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = Color.White
                        )
                    }
                    if (onCancelUpload != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(24.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .clickable { onCancelUpload() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter            = painterResource(R.drawable.close),
                                contentDescription = "Отмена",
                                tint               = Color.White,
                                modifier           = Modifier.size(14.dp)
                            )
                        }
                    }
                } else {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(36.dp),
                        color       = Color.White,
                        strokeWidth = 2.5.dp
                    )
                }
            }
        }

        if (isVideo) {
            val durationSec = media?.durationSec
            if (durationSec != null && durationSec > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text  = "%d:%02d".format(durationSec / 60, durationSec % 60),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = Color.White
                    )
                }
            }

            if (!isUploading && message.mediaState != MediaState.DOWNLOADING) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter            = painterResource(R.drawable.ic_play),
                        contentDescription = "Воспроизвести",
                        tint               = Color.White,
                        modifier           = Modifier.size(40.dp)
                    )
                }
            }
        }

        if (message.mediaState == MediaState.ERROR) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter            = painterResource(R.drawable.ic_error),
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.error,
                        modifier           = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Ошибка",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
    }
}

// Извлечение первого кадра — вызывается в IO dispatcher
fun extractVideoFrame(filePath: String): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return runCatching {
        retriever.setDataSource(filePath)
        // Берём кадр на 0 мкс — самый первый
        retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    }.also {
        retriever.release()
    }.getOrNull()
}