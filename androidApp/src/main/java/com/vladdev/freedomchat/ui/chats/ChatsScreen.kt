package com.vladdev.freedomchat.ui.chats

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vladdev.shared.chats.ChatRepository
import com.vladdev.shared.storage.AndroidUserIdStorage

@Composable
fun ChatsScreen(
    repository: ChatRepository,
    onOpenChat: (String) -> Unit
) {

    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
    val currentUserId = sharedPrefs.getString("userId", null)

    val viewModel = remember {
        ChatsViewModel(repository)
    }

    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {

            Text("Chats", style = MaterialTheme.typography.headlineMedium)

            Spacer(Modifier.height(16.dp))

            // ----- Requests Section -----
            if (viewModel.incomingRequests.isNotEmpty()) {

                Text("Incoming requests")

                Spacer(Modifier.height(8.dp))

                viewModel.incomingRequests.forEach { request ->

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {

                            Text("From: ${request.fromUsername}")

                            Row {
                                Button(onClick = { viewModel.acceptRequest(request.id) }) {
                                    Text("Accept")
                                }

                                Button(onClick = { viewModel.rejectRequest(request.id) }) {
                                    Text("Reject")
                                }
                            }
                        }
                    }
                }


                Spacer(Modifier.height(16.dp))
            }

            // ----- Chats Section -----
            Text("Your chats")

            Spacer(Modifier.height(8.dp))

            viewModel.chats.forEach { chat ->

                val otherUser = chat.participants
                    .firstOrNull { it.userId != currentUserId }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                            onOpenChat(chat.chatId)
                        }
                ) {
                    Text(
                        text = otherUser?.username ?: "Chat",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            viewModel.error?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = Color.Red)
            }

        }
    }

    if (showDialog) {
        CreateChatDialog(
            onDismiss = { showDialog = false },
            onCreate = {
                viewModel.sendRequest(it)
                showDialog = false
            }
        )
    }
}
