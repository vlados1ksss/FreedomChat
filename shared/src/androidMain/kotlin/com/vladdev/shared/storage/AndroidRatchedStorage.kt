package com.vladdev.shared.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.vladdev.shared.crypto.RatchetStorage
import com.vladdev.shared.crypto.dto.RatchetState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AndroidRatchetStorage(context: Context) : RatchetStorage {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "ratchet_states",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun saveState(chatId: String, state: RatchetState) {
        prefs.edit()
            .putString("ratchet_$chatId", json.encodeToString(state))
            .apply()
    }

    override suspend fun loadState(chatId: String): RatchetState? {
        val raw = prefs.getString("ratchet_$chatId", null) ?: return null
        return runCatching { json.decodeFromString<RatchetState>(raw) }.getOrNull()
    }

    override suspend fun clearState(chatId: String) {
        prefs.edit().remove("ratchet_$chatId").apply()
    }

    override suspend fun clearAll() {
        prefs.edit().clear().apply()
    }
    override suspend fun saveOutgoing(chatId: String, messageId: String, plaintext: String) {
        val key = "out_${chatId.replace("-", "")}_${messageId.replace("-", "")}"
        prefs.edit().putString(key, plaintext).apply()
    }

    override suspend fun loadOutgoing(chatId: String, messageId: String): String? {
        val key = "out_${chatId.replace("-", "")}_${messageId.replace("-", "")}"
        return prefs.getString(key, null)
    }
}