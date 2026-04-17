package com.vladdev.freedomchat.ui.chats

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.vladdev.freedomchat.R
import com.vladdev.shared.chats.dto.DecryptedMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

// MediaViewerScreen.kt

@androidx.annotation.OptIn(UnstableApi::class)
// MediaViewerScreen.kt — полная замена

@Composable
fun MediaViewerScreen(
    initialMessage: DecryptedMessage,
    allMessages: List<DecryptedMessage>,   // весь список для пролистывания
    onDismiss: () -> Unit
) {
    // Все медиасообщения чата в хронологическом порядке
    val mediaMessages = remember(allMessages) {
        allMessages
            .filter { it.media != null && it.mediaLocalPath != null }
            .sortedBy { it.createdAt }   // старые слева — соответствует коллажу
    }
    val initialIndex = mediaMessages.indexOfFirst { it.id == initialMessage.id }
        .coerceAtLeast(0)

    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount   = { mediaMessages.size }
    )

    val context = LocalContext.current

    // Toast-state
    var showSavedToast by remember { mutableStateOf(false) }

    // Свайп вниз для закрытия
    var dragOffsetY     by remember { mutableFloatStateOf(0f) }
    val dismissThreshold = 200f
    val bgAlpha = (1f - (dragOffsetY / 600f)).coerceIn(0.3f, 1f)

    BackHandler { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = bgAlpha))
            .systemBarsPadding()
    ) {
        // Горизонтальный пейджер
        HorizontalPager(
            state    = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, dragOffsetY.roundToInt()) }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd    = {
                            if (dragOffsetY > dismissThreshold) onDismiss()
                            else dragOffsetY = 0f
                        },
                        onDragCancel = { dragOffsetY = 0f },
                        onVerticalDrag = { _, delta ->
                            if (delta > 0 || dragOffsetY > 0) {
                                dragOffsetY = (dragOffsetY + delta).coerceAtLeast(0f)
                            }
                        }
                    )
                },
            // Ключевое: разрешаем пейджеру перехватывать жесты у дочерних элементов
            userScrollEnabled = true,
            beyondViewportPageCount = 1
        ) { page ->
            val msg = mediaMessages.getOrNull(page) ?: return@HorizontalPager
            when (msg.media?.type?.uppercase()) {
                "VIDEO" -> FullscreenVideo(
                    filePath  = msg.mediaLocalPath!!,
                    isVisible = page == pagerState.currentPage
                )
                else -> FullscreenPhoto(filePath = msg.mediaLocalPath!!)
            }
        }

        // Верхняя панель
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)
                    )
                )
                .padding(horizontal = 4.dp, vertical = 8.dp)
                .offset { IntOffset(0, dragOffsetY.roundToInt()) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    painter           = painterResource(R.drawable.close),
                    contentDescription = "Закрыть",
                    tint              = Color.White
                )
            }

            // Счётчик страниц
            if (mediaMessages.size > 1) {
                Text(
                    text  = "${pagerState.currentPage + 1} / ${mediaMessages.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            } else {
                Spacer(Modifier.weight(1f))
            }

            IconButton(onClick = {
                val msg = mediaMessages.getOrNull(pagerState.currentPage)
                if (msg?.mediaLocalPath != null && msg.media != null) {
                    val saved = saveMediaToGallery(context, msg.mediaLocalPath!!, msg.media!!.mimeType)
                    if (saved) showSavedToast = true
                }
            }) {
                Icon(
                    painter           = painterResource(R.drawable.ic_download),
                    contentDescription = "Сохранить",
                    tint              = Color.White
                )
            }
        }

        // Кастомный toast "Медиафайл сохранён"
        AnimatedVisibility(
            visible  = showSavedToast,
            enter    = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit     = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        ) {
            LaunchedEffect(showSavedToast) {
                if (showSavedToast) {
                    delay(2000)
                    showSavedToast = false
                }
            }
            Surface(
                shape           = RoundedCornerShape(12.dp),
                color           = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation  = 4.dp,
                modifier        = Modifier.padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier          = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter           = painterResource(R.drawable.ic_download),
                        contentDescription = null,
                        tint              = MaterialTheme.colorScheme.onSurface,
                        modifier          = Modifier.size(18.dp)
                    )
                    Text(
                        text  = "Сохранено в галерею",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun FullscreenPhoto(filePath: String) {
    var scale  by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Анимированные значения для плавности
    val animScale  = remember { Animatable(1f) }
    val animOffsetX = remember { Animatable(0f) }
    val animOffsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Двойной тап — zoom in/out
                detectTapGestures(
                    onDoubleTap = {
                        scope.launch {
                            if (scale > 1f) {
                                launch { animScale.animateTo(1f, spring(stiffness = Spring.StiffnessMedium)) }
                                launch { animOffsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                                launch { animOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                                scale  = 1f
                                offset = Offset.Zero
                            } else {
                                val targetScale = 2.5f
                                launch { animScale.animateTo(targetScale, spring(stiffness = Spring.StiffnessMedium)) }
                                scale = targetScale
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                var lastTranslation = Offset.Zero

                awaitEachGesture {
                    // Ждём первого touch
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val zoomChange = event.calculateZoom()
                        val panChange  = event.calculatePan()

                        if (zoomChange != 1f || panChange != Offset.Zero) {
                            val newScale = (scale * zoomChange).coerceIn(1f, 6f)
                            val maxX = ((newScale - 1f) * size.width  / 2f).coerceAtLeast(0f)
                            val maxY = ((newScale - 1f) * size.height / 2f).coerceAtLeast(0f)

                            val newOffset = if (newScale > 1f) {
                                Offset(
                                    x = (offset.x + panChange.x).coerceIn(-maxX, maxX),
                                    y = (offset.y + panChange.y).coerceIn(-maxY, maxY)
                                )
                            } else Offset.Zero

                            scale  = newScale
                            offset = newOffset

                            scope.launch {
                                launch { animScale.snapTo(newScale) }
                                launch { animOffsetX.snapTo(newOffset.x) }
                                launch { animOffsetY.snapTo(newOffset.y) }
                            }

                            // Потребляем события только при zoom — при scale==1 отдаём пейджеру
                            if (zoomChange != 1f || scale > 1f) {
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    // Snap back если вышли за границы
                    if (scale <= 1f) {
                        scope.launch {
                            launch { animScale.animateTo(1f, spring()) }
                            launch { animOffsetX.animateTo(0f, spring()) }
                            launch { animOffsetY.animateTo(0f, spring()) }
                        }
                        scale  = 1f
                        offset = Offset.Zero
                    }
                }
            }
    ) {
        AsyncImage(
            model              = File(filePath),
            contentDescription = null,
            contentScale       = ContentScale.Fit,
            modifier           = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX       = animScale.value
                    scaleY       = animScale.value
                    translationX = animOffsetX.value
                    translationY = animOffsetY.value
                }
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun FullscreenVideo(filePath: String, isVisible: Boolean) {
    val context = LocalContext.current

    var thumbBitmap by remember(filePath) { mutableStateOf<Bitmap?>(null) }
    var isPlayerReady by remember { mutableStateOf(false) }

    LaunchedEffect(filePath) {
        thumbBitmap = withContext(Dispatchers.IO) { extractVideoFrame(filePath) }
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(filePath))))
            prepare()
        }
    }

    // Слушаем готовность плеера
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) isPlayerReady = true
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(isVisible) {
        if (isVisible) exoPlayer.play() else exoPlayer.pause()
    }

    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory  = { ctx -> PlayerView(ctx).apply { player = exoPlayer } },
            modifier = Modifier.fillMaxSize()
        )

        // Показываем стоп-кадр пока плеер не готов
        if (!isPlayerReady && thumbBitmap != null) {
            Image(
                bitmap           = thumbBitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale     = ContentScale.Fit,
                modifier         = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }
    }
}

// Возвращает true если сохранено успешно
private fun saveMediaToGallery(
    context: Context,
    filePath: String,
    mimeType: String
): Boolean = runCatching {
    val file   = File(filePath)
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relativePath = if (mimeType.startsWith("video"))
                "DCIM/Freedomchat" else "Pictures/Freedomchat"
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        }
    }
    val collection = if (mimeType.startsWith("video"))
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    else
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    context.contentResolver.insert(collection, values)?.also { uri ->
        context.contentResolver.openOutputStream(uri)?.use { out ->
            file.inputStream().copyTo(out)
        }
    }
    true
}.getOrDefault(false)