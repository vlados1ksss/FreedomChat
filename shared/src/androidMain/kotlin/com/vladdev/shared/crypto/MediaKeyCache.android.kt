package com.vladdev.shared.crypto

import android.content.SharedPreferences

class AndroidMediaKeyCache(
    private val prefs: SharedPreferences
) : MediaKeyCache {

    override fun save(chatId: String, messageId: String, messageKeyHex: String) {
        prefs.edit()
            .putString("mediakey_${chatId}_${messageId}", messageKeyHex)
            .apply()
    }

    override fun get(chatId: String, messageId: String): String? =
        prefs.getString("mediakey_${chatId}_${messageId}", null)

    override fun delete(chatId: String, messageId: String) {
        prefs.edit()
            .remove("mediakey_${chatId}_${messageId}")
            .apply()
    }
}