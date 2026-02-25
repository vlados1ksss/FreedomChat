package com.vladdev.shared.storage

import android.content.Context
import androidx.core.content.edit

class AndroidUserIdStorage(context: Context) : UserIdStorage {
    private val prefs = context.getSharedPreferences("user", Context.MODE_PRIVATE)

    override suspend fun saveUID(uid: String) { prefs.edit { putString("uid", uid) } }
    override suspend fun getUID(): String? = prefs.getString("uid", null)
    override suspend fun clear() { prefs.edit { clear() } }

    // Синхронная версия для вызова вне корутин
    fun getUIDSync(): String? = prefs.getString("uid", null)
}