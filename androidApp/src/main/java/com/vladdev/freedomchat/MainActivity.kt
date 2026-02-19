package com.vladdev.freedomchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.vladdev.freedomchat.ui.theme.FreedomChatTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = (application as MainApplication).authRepository
        val chatRepository = (application as MainApplication).chatRepository
        setContent {
            AppNavGraph(repository, chatRepository = chatRepository)
        }
    }
}

