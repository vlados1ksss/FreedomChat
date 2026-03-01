package com.vladdev.shared.crypto

import com.vladdev.shared.crypto.dto.RatchetState

// crypto/RatchetStorage.kt

// RatchetStorage interface
interface RatchetStorage {
    suspend fun saveState(chatId: String, state: RatchetState)
    suspend fun loadState(chatId: String): RatchetState?
    suspend fun clearState(chatId: String)
    suspend fun clearAll()
    suspend fun saveOutgoing(chatId: String, index: Int, plaintext: String)
    suspend fun loadOutgoing(chatId: String, index: Int): String?
}

