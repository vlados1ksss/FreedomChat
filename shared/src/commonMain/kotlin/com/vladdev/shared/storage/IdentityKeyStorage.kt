package com.vladdev.shared.storage

interface IdentityKeyStorage {
    suspend fun saveIdentityKeyPair(publicKey: String, privateKey: String)
    suspend fun saveSigningKeyPair(verifyKey: String, signKey: String)
    suspend fun getPublicKey(): String?
    suspend fun getPrivateKey(): String?
    suspend fun getVerifyKey(): String?
    suspend fun getSignKey(): String?
    suspend fun clear()
}