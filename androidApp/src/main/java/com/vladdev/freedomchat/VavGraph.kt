package com.vladdev.freedomchat

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vladdev.freedomchat.ui.auth.AuthScreen
import com.vladdev.freedomchat.ui.auth.AuthViewModel
import com.vladdev.freedomchat.ui.chats.ChatScreen
import com.vladdev.freedomchat.ui.chats.ChatViewModel
import com.vladdev.freedomchat.ui.chats.ChatsScreen
import com.vladdev.freedomchat.ui.chats.ChatsViewModel
import com.vladdev.freedomchat.ui.profile.ProfileScreen
import com.vladdev.freedomchat.ui.profile.ProfileViewModel
import com.vladdev.shared.auth.AuthRepository
import com.vladdev.shared.chats.ChatRepository
import com.vladdev.shared.user.ProfileRepository

@Composable
fun AppNavGraph(app: MainApplication, startDestination: String = "auth") {
    val navController = rememberNavController()
    LaunchedEffect(Unit) {
        app.authRepository.sessionExpiredFlow.collect {
            navController.navigate("auth") {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
    NavHost(navController = navController, startDestination = startDestination) {

        composable("auth") {
            val viewModel: AuthViewModel = viewModel {
                AuthViewModel(app.authRepository)
            }
            AuthScreen(
                viewModel = viewModel,
                onSuccess = {
                    navController.navigate("chats") {
                        popUpTo("auth") { inclusive = true }
                    }
                }
            )
        }

        composable("chats") {
            val chatRepo = app.chatRepository ?: run {
                LaunchedEffect(Unit) {
                    navController.navigate("auth") { popUpTo(0) { inclusive = true } }
                }
                return@composable
            }

            ChatsScreen(
                repository = chatRepo,
                currentUserId = app.userIdStorage.getUIDSync(),
                // добавляем theirUserId в сигнатуру колбэка
                onOpenChat = { chatId, theirUserId, name, status ->
                    navController.navigate(
                        "chat/$chatId/${Uri.encode(theirUserId)}/${Uri.encode(name)}/${Uri.encode(status)}"
                    )
                },
                onOpenProfile = { navController.navigate("profile") }
            )
        }

        composable("chat/{chatId}/{userId}/{name}/{status}") { backStackEntry ->
            val chatId       = backStackEntry.arguments?.getString("chatId")!!
            val theirUserId  = backStackEntry.arguments?.getString("userId")!!
            val name         = backStackEntry.arguments?.getString("name")!!
            val status       = backStackEntry.arguments?.getString("status") ?: "standard"

            val chatRepo = app.chatRepository
            if (chatRepo == null) {
                LaunchedEffect(Unit) {
                    navController.navigate("auth") { popUpTo(0) { inclusive = true } }
                }
                return@composable
            }

            val currentUserId = app.userIdStorage.getUIDSync()

            ChatScreen(
                chatId                = chatId,
                repository            = chatRepo,
                e2ee                  = app.e2ee,
                interlocutorUsername  = name,
                interlocutorUserId    = theirUserId,
                interlocutorStatus    = status,
                currentUserId         = currentUserId,
                onBack                = { navController.popBackStack() }
            )
        }

        composable("profile") {
            val profileRepo = app.profileRepository
            val identityStorage = app.identityStorage
            if (profileRepo == null) {
                LaunchedEffect(Unit) {
                    navController.navigate("auth") { popUpTo(0) { inclusive = true } }
                }
                return@composable
            }

            val userId = app.userIdStorage.getUIDSync()
            val viewModel: ProfileViewModel = viewModel(key = userId) {
                ProfileViewModel(profileRepo, identityStorage = identityStorage )
            }

            ProfileScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate("auth") {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}
