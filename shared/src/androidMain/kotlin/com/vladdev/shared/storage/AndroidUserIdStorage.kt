package com.vladdev.shared.storage

import android.content.Context

class AndroidUserIdStorage(
    private val context: Context
) : UserIdStorage {

    private val prefs =
        context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    override suspend fun saveUID(userId: String) {
        prefs.edit().putString("userId", userId).apply()
    }

    override suspend fun getUID(): String? {
        return prefs.getString("userId", null)
    }

    override suspend fun clearUID() {
        prefs.edit().clear().apply()
    }
}
