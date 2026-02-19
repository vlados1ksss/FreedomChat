package com.vladdev.shared.storage

interface TokenStorage {

    suspend fun saveAccessToken(token: String)
    suspend fun saveRefreshToken(token: String)

    suspend fun getAccessToken(): String?
    suspend fun getRefreshToken(): String?

    suspend fun clear()
}
