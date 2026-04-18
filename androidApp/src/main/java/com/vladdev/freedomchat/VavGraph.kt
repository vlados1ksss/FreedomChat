package com.vladdev.freedomchat

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vladdev.freedomchat.ui.interLocutorProfile.InterlocutorProfileScreen
import com.vladdev.freedomchat.ui.interLocutorProfile.InterlocutorProfileViewModel
import com.vladdev.freedomchat.ui.auth.AuthScreen
import com.vladdev.freedomchat.ui.auth.AuthViewModel
import com.vladdev.freedomchat.ui.chats.ChatScreen
import com.vladdev.freedomchat.ui.chats.ChatsScreen
import com.vladdev.freedomchat.ui.chats.ChatsViewModel
import com.vladdev.freedomchat.ui.profile.ProfileScreen
import com.vladdev.freedomchat.ui.profile.ProfileViewModel
import kotlinx.serialization.InternalSerializationApi

@OptIn(InternalSerializationApi::class)
@Composable
fun AppNavGraph(app: MainApplication, startDestination: String = "auth", openChatId: String? = null ) {
    val navController = rememberNavController()
    LaunchedEffect(Unit) {
        app.authRepository.sessionExpiredFlow.collect {
            navController.navigate("auth") {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
    LaunchedEffect(openChatId, startDestination) {
        if (openChatId == null) return@LaunchedEffect
        if (startDestination != "chats") return@LaunchedEffect

        val chatRepo = app.chatRepository ?: return@LaunchedEffect
        val currentUserId = app.userIdStorage.getUIDSync() ?: return@LaunchedEffect

        try {
            val chats = chatRepo.loadChats().getOrNull() ?: return@LaunchedEffect
            val chat  = chats.find { it.chatId == openChatId } ?: return@LaunchedEffect
            val their = chat.participants.firstOrNull { it.userId != currentUserId }
                ?: return@LaunchedEffect

            navController.navigate(
                "chat/$openChatId/${Uri.encode(their.userId)}/" +
                        "${Uri.encode(their.name)}/${Uri.encode(their.username)}/${Uri.encode(their.status)}"
            )
        } catch (e: Exception) {
            println("Deep link navigation error: ${e.message}")
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

            val chatsViewModel: ChatsViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return ChatsViewModel(
                            repository    = chatRepo,
                            dataStore     = app.chatsDataStore,
                            currentUserId = app.userIdStorage.getUIDSync()
                        ) as T
                    }
                }
            )

            ChatsScreen(
                viewModel     = chatsViewModel,
                currentUserId = app.userIdStorage.getUIDSync(),
                onOpenChat    = { chatId, theirUserId, name, username, status ->
                    navController.navigate(
                        "chat/$chatId/${Uri.encode(theirUserId)}/${Uri.encode(name)}" +
                                "/${Uri.encode(username)}/${Uri.encode(status)}"
                    )
                },
                onOpenProfile = { navController.navigate("profile") }
            )
        }

        composable("chat/{chatId}/{userId}/{name}/{username}/{status}") { backStackEntry ->
            val chatId      = backStackEntry.arguments?.getString("chatId")!!
            val theirUserId = backStackEntry.arguments?.getString("userId")!!
            val name        = backStackEntry.arguments?.getString("name")!!
            val username    = backStackEntry.arguments?.getString("username")!!
            val status      = backStackEntry.arguments?.getString("status") ?: "standard"

            val chatRepo = app.chatRepository ?: run {
                LaunchedEffect(Unit) {
                    navController.navigate("auth") { popUpTo(0) { inclusive = true } }
                }
                return@composable
            }
            val currentUserId = app.userIdStorage.getUIDSync()

            val chatsEntry = remember(navController) {
                try { navController.getBackStackEntry("chats") } catch (e: Exception) { null }
            }
            val chatsViewModel: ChatsViewModel? = chatsEntry?.let { entry ->
                viewModel(
                    viewModelStoreOwner = entry,
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            @Suppress("UNCHECKED_CAST")
                            return ChatsViewModel(
                                repository    = chatRepo,
                                dataStore     = app.chatsDataStore,
                                currentUserId = currentUserId
                            ) as T
                        }
                    }
                )
            }
            val availableChats = chatsViewModel?.chats ?: emptyList()

            ChatScreen(
                chatId               = chatId,
                repository           = chatRepo,
                e2ee                 = app.e2ee,
                interlocutorUsername = name,
                interlocutorNick     = username,
                interlocutorUserId   = theirUserId,
                interlocutorStatus   = status,
                currentUserId        = currentUserId,
                onBack               = { navController.popBackStack() },
                availableChats       = availableChats,
                onOpenInterlocutorProfile = { userId, uName, uNick, uStatus ->
                    val existingChatId = availableChats
                        .firstOrNull { chat -> chat.participants.any { it.userId == userId } }
                        ?.chatId
                    navController.openInterlocutorProfile(
                        theirUserId = userId,
                        name        = uName,
                        username    = uNick,
                        status      = uStatus,
                        chatId      = existingChatId
                    )
                }
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

        composable(
            "interlocutor/{userId}/{name}/{username}/{status}/{chatId}",
            arguments = listOf(
                navArgument("chatId") { nullable = true; defaultValue = null }
            )
        ) { back ->
            val theirUserId  = back.arguments?.getString("userId")!!
            val name         = back.arguments?.getString("name")!!
            val username     = back.arguments?.getString("username")!!
            val status       = back.arguments?.getString("status") ?: "standard"
            val existingChatId = back.arguments?.getString("chatId")

            val chatRepo = app.chatRepository ?: run {
                LaunchedEffect(Unit) {
                    navController.navigate("auth") { popUpTo(0) { inclusive = true } }
                }
                return@composable
            }

            val viewModel: InterlocutorProfileViewModel = viewModel(
                key = "interlocutor_$theirUserId",
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return InterlocutorProfileViewModel(
                            chatRepository = chatRepo,
                            theirUserId = theirUserId,
                            theirName = name,
                            theirUsername = username,
                            theirStatus = status,
                            existingChatId = existingChatId
                        ) as T
                    }
                }
            )

            InterlocutorProfileScreen(
                viewModel  = viewModel,
                onBack     = { navController.popBackStack() },
                onOpenChat = { chatId, uid, n, s ->
                    navController.navigate(
                        "chat/$chatId/${Uri.encode(uid)}/${Uri.encode(n)}" +
                                "/${Uri.encode(username)}/${Uri.encode(s)}"
                    ) {
                        popUpTo(
                            "interlocutor/${Uri.encode(theirUserId)}/${Uri.encode(name)}" +
                                    "/${Uri.encode(username)}/${Uri.encode(status)}/$existingChatId"
                        ) { inclusive = true }
                    }
                },
                onHistoryCleared = {
                    // Возвращаемся в чат (профиль закрывается)
                    navController.popBackStack()
                    // ChatViewModel получит WS событие и очистит messages автоматически
                },
                onChatDeleted = {
                    // Выходим из профиля и из чата, попадаем на список чатов
                    navController.popBackStack() // закрыть профиль
                    navController.popBackStack() // закрыть чат
                    // ChatsViewModel получит WS событие и удалит чат из списка автоматически
                }
            )
        }
    }

}
fun NavController.openInterlocutorProfile(
    theirUserId: String,
    name: String,
    username: String,
    status: String,
    chatId: String? = null
) {
    // Если username пустой — используем name как fallback
    val safeUsername = username.ifBlank { name }
    val safeStatus = status.ifBlank { "standard" }

    navigate(
        "interlocutor/${Uri.encode(theirUserId)}/${Uri.encode(name)}" +
                "/${Uri.encode(safeUsername)}/${Uri.encode(safeStatus)}/${chatId ?: "null"}"
    )
}