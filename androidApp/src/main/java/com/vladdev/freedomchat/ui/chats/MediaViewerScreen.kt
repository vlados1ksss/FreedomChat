package com.vladdev.freedomchat.ui.chats

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.vladdev.freedomchat.R
import com.vladdev.shared.chats.dto.DecryptedMessage
import java.io.File

// MediaViewerScreen.kt

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun MediaViewerScreen(
    message: DecryptedMessage,
    onDismiss: () -> Unit
) {
    val media    = message.media ?: run { onDismiss(); return }
    val filePath = message.mediaLocalPath ?: run { onDismiss(); return }
    val context  = LocalContext.current

    BackHandler { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
    ) {
        when (media.type.uppercase()) {
            "PHOTO" -> FullscreenPhoto(filePath = filePath, onDismiss = onDismiss)
            "VIDEO" -> FullscreenVideo(filePath = filePath, onDismiss = onDismiss)
            else    -> { onDismiss() }
        }

        // Верхняя панель
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    painter           = painterResource(R.drawable.close),
                    contentDescription = "Закрыть",
                    tint              = Color.White
                )
            }
            Spacer(Modifier.weight(1f))
            // Кнопка скачать в галерею
            IconButton(onClick = {
                saveMediaToGallery(context, filePath, media.mimeType)
            }) {
                Icon(
                    painter           = painterResource(R.drawable.ic_download),
                    contentDescription = "Сохранить",
                    tint              = Color.White
                )
            }
        }
    }
}

@Composable
private fun FullscreenPhoto(filePath: String, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale  = (scale * zoomChange).coerceIn(1f, 5f)
        offset += panChange
    }

    AsyncImage(
        model              = File(filePath),
        contentDescription = null,
        contentScale       = ContentScale.Fit,
        modifier           = Modifier
            .fillMaxSize()
            .graphicsLayer(
                scaleX         = scale,
                scaleY         = scale,
                translationX   = offset.x,
                translationY   = offset.y
            )
            .transformable(state = transformState)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale  = if (scale > 1f) 1f else 2.5f
                        offset = Offset.Zero
                    },
                    onTap = { onDismiss() }
                )
            }
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun FullscreenVideo(filePath: String, onDismiss: () -> Unit) {
    val context = LocalContext.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(Uri.fromFile(File(filePath)))
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .clickable { /* перехватываем клики чтобы не закрыть при тапе */ }
    )
}

private fun saveMediaToGallery(context: Context, filePath: String, mimeType: String) {
    try {
        val file   = File(filePath)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collection = if (mimeType.startsWith("video"))
                    MediaStore.Video.Media.RELATIVE_PATH
                else
                    MediaStore.Images.Media.RELATIVE_PATH
                put(collection, "DCIM/Freedomchat")
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
    } catch (e: Exception) {
        e.printStackTrace()
    }
}