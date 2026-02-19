package com.vladdev.shared.storage

interface UserIdStorage {

    suspend fun saveUID(userId: String)
    suspend fun getUID(): String?
    suspend fun clearUID()
}