package com.vladdev.freedomchat

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

val Context.chatsDataStore: DataStore<Preferences> by preferencesDataStore(name = "chats_prefs")