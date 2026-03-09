package com.vladdev.shared.crypto

import com.vladdev.shared.crypto.dto.RatchetState

// crypto/RatchetStorage.kt

// RatchetStorage interface
interface RatchetStorage {
    suspend fun saveState(chatId: String, state: RatchetState)
    suspend fun loadState(chatId: String): RatchetState?
    suspend fun clearState(chatId: String)
    suspend fun clearAll()

    // Новые методы по messageId
    suspend fun saveOutgoing(chatId: String, messageId: String, plaintext: String)
    suspend fun loadOutgoing(chatId: String, messageId: String): String?

    // Старые методы — помечаем deprecated, реализуем как no-op
    @Deprecated("Use saveOutgoing(chatId, messageId, plaintext)")
    suspend fun saveOutgoing(chatId: String, index: Int, plaintext: String) { /* no-op */ }

    @Deprecated("Use loadOutgoing(chatId, messageId)")
    suspend fun loadOutgoing(chatId: String, index: Int): String? = null  // ← вот этот null не ломает старые сообщения
}