package com.vladdev.freedomchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.vladdev.freedomchat.ui.theme.FreedomChatTheme
import com.vladdev.shared.auth.dto.RefreshResult

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as MainApplication

        app.authRepository.onSessionCreated = { isNewDevice ->
            app.createSession(isNewDevice)
        }
        app.authRepository.onSessionDestroyed = { app.destroySession() }

        setContent {
            var retryKey by remember { mutableStateOf(0) }
            var startDestination by remember { mutableStateOf<String?>(null) }
            var networkError by remember { mutableStateOf(false) }

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

            FreedomChatTheme {
                when {
                    // Пока определяем — показываем сплэш (без мигания authScreen)
                    startDestination == null && !networkError -> SplashScreen()
                    networkError -> OfflineScreen(onRetry = { retryKey++ })
                    else -> AppNavGraph(app = app, startDestination = startDestination!!)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SplashScreen() {
    FreedomChatTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator(modifier = Modifier.size(84.dp))
            }
        }
    }
}

@Composable
private fun OfflineScreen(onRetry: () -> Unit) {
    FreedomChatTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        painterResource(R.drawable.wifi_off), null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Нет соединения с сервером",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Проверьте подключение к интернету",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onRetry,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Icon(painterResource(R.drawable.refresh), null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Повторить")
                    }
                }
            }
        }
    }
}