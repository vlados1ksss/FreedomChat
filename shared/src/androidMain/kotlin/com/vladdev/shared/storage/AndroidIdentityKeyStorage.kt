package com.vladdev.shared.storage

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class AndroidIdentityKeyStorage(context: Context) : IdentityKeyStorage {

    // EncryptedSharedPreferences защищает ключи через Android Keystore
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "identity_keys",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override suspend fun saveIdentityKeyPair(publicKey: String, privateKey: String) {
        prefs.edit {
            putString("identity_pub", publicKey)
            putString("identity_priv", privateKey)
        }
    }

    override suspend fun saveSigningKeyPair(verifyKey: String, signKey: String) {
        prefs.edit {
            putString("sign_verify", verifyKey)
            putString("sign_key", signKey)
        }
    }

    override suspend fun getPublicKey()  = prefs.getString("identity_pub", null)
    override suspend fun getPrivateKey() = prefs.getString("identity_priv", null)
    override suspend fun getVerifyKey()  = prefs.getString("sign_verify", null)
    override suspend fun getSignKey()    = prefs.getString("sign_key", null)

    override suspend fun clear() {
        prefs.edit().clear().apply()
    }
}