package com.vladdev.freedomchat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vladdev.freedomchat.ui.auth.AuthScreen
import com.vladdev.freedomchat.ui.auth.AuthViewModel
import com.vladdev.freedomchat.ui.chats.ChatScreen
import com.vladdev.freedomchat.ui.chats.ChatsScreen
import com.vladdev.shared.auth.AuthRepository
import com.vladdev.shared.chats.ChatRepository

@Composable
fun AppNavGraph(authRepository: AuthRepository, chatRepository: ChatRepository) {

    val navController = rememberNavController()

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
                onOpenChat = { chatId ->
                    navController.navigate("chat/$chatId")
                }
            )
        }

        composable("chat/{chatId}") { backStackEntry ->

            val chatId = backStackEntry.arguments?.getString("chatId")!!

            ChatScreen(chatId)
        }

    }
}
