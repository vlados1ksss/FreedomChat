package com.vladdev.shared.storage

import android.content.Context
import android.util.Log
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
        Log.d("Ratchet", "saveState chatId=$chatId sendIndex=${state.sendIndex} receiveIndex=${state.receiveIndex}")

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

    // AndroidRatchetStorage — реализация
    override suspend fun saveIncomingPlaintext(chatId: String, messageId: String, plaintext: String) {
        val key = "in_${chatId.replace("-", "")}_${messageId.replace("-", "")}"
        prefs.edit().putString(key, plaintext).apply()
    }

    override suspend fun getIncomingPlaintext(chatId: String, messageId: String): String? {
        val key = "in_${chatId.replace("-", "")}_${messageId.replace("-", "")}"
        return prefs.getString(key, null)
    }
}