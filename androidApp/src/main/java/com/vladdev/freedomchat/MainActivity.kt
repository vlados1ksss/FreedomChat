package com.vladdev.freedomchat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vladdev.freedomchat.ui.UpdateBottomSheet
import com.vladdev.freedomchat.ui.theme.FreedomChatTheme
import com.vladdev.shared.auth.NetworkDiagnostics
import com.vladdev.shared.auth.NetworkState
import com.vladdev.shared.auth.dto.CheckUpdateResponse
import com.vladdev.shared.auth.dto.RefreshResult

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        println("Notification permission: $granted")
    }
    private val _openChatId = mutableStateOf<String?>(null)
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as MainApplication
        val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
        } else {
            packageManager.getPackageInfo(packageName, 0).versionCode
        }
        _openChatId.value = intent.getStringExtra("open_chat_id")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }

        app.authRepository.onSessionCreated = { isNewDevice ->
            app.createSession(isNewDevice)
        }
        app.authRepository.onSessionDestroyed = { app.destroySession() }

        setContent {
            var retryKey by remember { mutableStateOf(0) }
            var startDestination by remember { mutableStateOf<String?>(null) }
            var networkError by remember { mutableStateOf(false) }
            var updateInfo by remember { mutableStateOf<CheckUpdateResponse?>(null) }
            var showUpdateSheet by remember { mutableStateOf(false) }
            val sheetState = rememberModalBottomSheetState()

            LaunchedEffect(Unit) {
                app.authRepository.checkForUpdates(currentVersionCode).onSuccess { update ->
                    if (update != null) {
                        updateInfo = update
                        showUpdateSheet = true
                    }
                }
            }

            LaunchedEffect(retryKey) {
                startDestination = null
                networkError = false

                if (!app.authRepository.isLoggedIn()) {
                    startDestination = "auth"
                    return@LaunchedEffect
                }

                when (app.authRepository.refreshTokensResult()) {
                    RefreshResult.Success -> {
                        app.createSession()
                        startDestination = "chats"
                    }
                    RefreshResult.Unauthorized -> {
                        startDestination = "auth"
                    }
                    RefreshResult.NetworkError -> {
                        networkError = true
                    }
                }
            }

            val openChatId by _openChatId
            FreedomChatTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    val context = LocalContext.current
                    when {
                        startDestination == null && !networkError -> SplashScreen()
                        networkError -> OfflineScreen(onRetry = { retryKey++ })
                        else -> AppNavGraph(
                            app = app,
                            startDestination = startDestination!!,
                            openChatId = openChatId
                        )
                    }
                    if (showUpdateSheet && updateInfo != null) {
                        UpdateBottomSheet(
                            versionName = updateInfo!!.version,
                            onDismiss = { showUpdateSheet = false },
                            onUpdate = {
                                context.openUrl("http://176.124.199.31/freedomchat")
                                startActivity(intent)
                            }
                        )
                    }
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        _openChatId.value = intent.getStringExtra("open_chat_id")
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SplashScreen() {
    FreedomChatTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {

            var visible by remember { mutableStateOf(false) }

            // Запускаем анимацию при старте
            LaunchedEffect(Unit) {
                visible = true
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {

                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(
                        animationSpec = tween(durationMillis = 600)
                    ) + scaleIn(
                        initialScale = 0.9f,
                        animationSpec = tween(600)
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {

                        Text(
                            "FreedomChat",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(Modifier.height(16.dp))

                        LoadingIndicator(modifier = Modifier.size(84.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OfflineScreen(
    diagnostics: NetworkDiagnostics = remember { NetworkDiagnostics() },
    onRetry: () -> Unit
) {
    var state by remember { mutableStateOf<NetworkState>(NetworkState.Checking) }

    LaunchedEffect(Unit) {
        state = diagnostics.check()
    }

    FreedomChatTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
            ) {
                Text(
                    text = "FreedomChat",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                )

                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                val context = LocalContext.current
                when (state) {

                    NetworkState.Checking -> {
                        LoadingIndicator(modifier = Modifier.size(84.dp))
                    }

                    NetworkState.ServerDown -> {
                        OfflineContent(
                            icon = R.drawable.repair,
                            title = "Технические работы на сервере",
                            description = "Сервер временно недоступен",
                            actions = listOf(
                                "Попробуйте позже",
                                "Установите актуальную версию приложения"
                            ),
                            buttonText = "Установить",
                            onButtonClick = {
                                context.openUrl("http://176.124.199.31/freedomchat")
                            },
                            onRetry = onRetry
                        )
                    }

                    NetworkState.NoInternet -> {
                        OfflineContent(
                            icon = R.drawable.wifi_off,
                            title = "Нет подключения к Интернету",
                            description = "Проверьте подключение",
                            actions = listOf(
                                "Проверьте настройки сети",
                                "Попробуйте включить/выключить VPN"
                            ),
                            onRetry = onRetry
                        )
                    }

                    NetworkState.WhiteListMode -> {
                        OfflineContent(
                            icon = R.drawable.white_list,
                            title = "Ограниченный доступ к интернету",
                            description = "Возможно включен режим белых списков",
                            actions = listOf(
                                "Используйте VPN",
                                "Проверьте через запрос к yandex.ru"
                            ),
                            onRetry = onRetry
                        )
                    }
                }
            }}
        }
    }
}
@Composable
private fun OfflineContent(
    icon: Int,
    title: String,
    description: String,
    actions: List<String>,
    buttonText: String? = null,
    onButtonClick: (() -> Unit)? = null,
    onRetry: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            painterResource(icon),
            null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )

        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        // 🔷 ВОТ НОВЫЙ БЛОК
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                actions.forEach {
                    Text(
                        text = "• $it",
                        style = MaterialTheme.typography.bodyMedium,
                        color =MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (buttonText != null && onButtonClick != null) {
            Button(
                onClick = onButtonClick,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(buttonText)
            }
        }

        FilledTonalButton(
            onClick = onRetry,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("Повторить")
        }
    }
}
fun Context.openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    startActivity(intent)
}