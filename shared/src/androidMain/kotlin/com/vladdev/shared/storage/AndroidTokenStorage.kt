package com.vladdev.shared.storage

import android.content.Context


class AndroidTokenStorage(
    context: Context
) : TokenStorage {

    private val prefs =
        context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    override suspend fun saveAccessToken(token: String) {
        prefs.edit().putString("access", token).apply()
    }

    override suspend fun saveRefreshToken(token: String) {
        prefs.edit().putString("refresh", token).apply()
    }

    override suspend fun getAccessToken(): String? {
        return prefs.getString("access", null)
    }

    override suspend fun getRefreshToken(): String? {
        return prefs.getString("refresh", null)
    }

    override suspend fun clear() {
        prefs.edit().clear().apply()
    }
}

