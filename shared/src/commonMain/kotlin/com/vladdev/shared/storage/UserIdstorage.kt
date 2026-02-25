package com.vladdev.shared.storage


interface UserIdStorage {
    suspend fun saveUID(uid: String)
    suspend fun getUID(): String?
    suspend fun clear()
}

