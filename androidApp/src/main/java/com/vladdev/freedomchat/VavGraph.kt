package com.vladdev.freedomchat

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vladdev.freedomchat.ui.auth.AuthScreen
import com.vladdev.freedomchat.ui.auth.AuthViewModel
import com.vladdev.freedomchat.ui.chats.ChatScreen
import com.vladdev.freedomchat.ui.chats.ChatsScreen
import com.vladdev.freedomchat.ui.profile.ProfileScreen
import com.vladdev.shared.auth.AuthRepository
import com.vladdev.shared.chats.ChatRepository

@Composable
fun AppNavGraph(authRepository: AuthRepository, chatRepository: ChatRepository) {

    val navController = rememberNavController()
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    NavHost(
        navController = navController,
        startDestination = "auth"
    ) {

        composable("auth") {
            val viewModel = remember {
                AuthViewModel(authRepository)
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

            ChatsScreen(
                repository = chatRepository,
                onOpenChat = { chatId, username ->
                    navController.navigate("chat/$chatId/${Uri.encode(username)}")
                },
                onOpenProfile = {
                    navController.navigate("profile")
                }
            )
        }

        composable("chat/{chatId}/{username}") { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId")!!
            val username = backStackEntry.arguments?.getString("username")!!
            val currentUserId = sharedPrefs.getString("userId", null)
            ChatScreen(
                repository = chatRepository,
                chatId = chatId,
                interlocutorUsername = username,
                currentUserId = currentUserId,
                onBack = { navController.navigate(route = "chats") }
            )
        }

        composable("profile") {

            ProfileScreen(

                onBack = {
                    navController.popBackStack()
                },
                onLogout = {
                    sharedPrefs.edit().clear().apply()

                    navController.navigate("auth") {
                        popUpTo("chats") { inclusive = true }
                    }
                }
            )
        }
    }
}
