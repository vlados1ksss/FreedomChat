package com.vladdev.freedomchat.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vladdev.freedomchat.ui.chats.ErrorBanner
import com.vladdev.freedomchat.ui.theme.FreedomChatTheme

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onSuccess: () -> Unit
) {
    LaunchedEffect(viewModel.isLoggedIn) {
        if (viewModel.isLoggedIn) onSuccess()
    }

    FreedomChatTheme {
        Scaffold(containerColor = MaterialTheme.colorScheme.surface) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = viewModel.screen,
                    transitionSpec = {
                        val toRight = targetState.ordinal > initialState.ordinal
                        val enter = slideInHorizontally { if (toRight) it else -it } + fadeIn(tween(220))
                        val exit  = slideOutHorizontally { if (toRight) -it else it } + fadeOut(tween(220))
                        enter togetherWith exit
                    },
                    label = "auth_screen"
                ) { screen ->
                    when (screen) {
                        AuthScreen.Welcome      -> WelcomeStep(viewModel)
                        AuthScreen.Login        -> LoginStep(viewModel)
                        AuthScreen.ScanTransfer -> ScanQrStep(viewModel)
                        AuthScreen.Reg1         -> Reg1Step(viewModel)
                        AuthScreen.Reg2         -> Reg2Step(viewModel)
                        AuthScreen.Reg3         -> Reg3Step(viewModel)
                    }
                }
            }
        }
    }
}