package com.vladdev.shared.storage

import android.content.Context
import androidx.core.content.edit


class AndroidTokenStorage(
    context: Context
) : TokenStorage {

    private val prefs =
        context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    fun getRefreshTokenSync(): String? = prefs.getString("refresh", null)

    override suspend fun saveAccessToken(token: String) {
        prefs.edit { putString("access", token) }
    }

    override suspend fun saveRefreshToken(token: String) {
        prefs.edit { putString("refresh", token) }
    }

    override suspend fun getAccessToken(): String? {
        return prefs.getString("access", null)
    }

    override suspend fun getRefreshToken(): String? {
        return prefs.getString("refresh", null)
    }

    override suspend fun clear() {
        prefs.edit { clear() }
    }
}

