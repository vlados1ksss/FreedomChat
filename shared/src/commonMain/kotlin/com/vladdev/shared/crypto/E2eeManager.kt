package com.vladdev.shared.crypto

import com.vladdev.shared.crypto.dto.EncryptedMessage
import com.vladdev.shared.crypto.dto.EncryptedPayload
import com.vladdev.shared.storage.IdentityKeyStorage
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(InternalSerializationApi::class, ExperimentalEncodingApi::class)
class E2eeManager(
    private val crypto: CryptoManager,
    private val identityStorage: IdentityKeyStorage,
    private val ratchetStorage: RatchetStorage
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val outgoingCache = mutableMapOf<String, String>()  // messageId → plaintext

    suspend fun encryptMessage(
        chatId: String,
        plaintext: String,
        theirPublicKeyHex: String
    ): String {
        val myPrivKey = identityStorage.getPrivateKey() ?: error("No identity private key")
        val myPubKey  = identityStorage.getPublicKey()  ?: error("No identity public key")

        val state = ratchetStorage.loadState(chatId) ?: run {
            val sharedSecret = crypto.computeSharedSecret(myPrivKey, theirPublicKeyHex)
            crypto.initRatchet(sharedSecret, myPubKey, theirPublicKeyHex).also {  // ← myPubKey
                ratchetStorage.saveState(chatId, it)
            }
        }

        val currentIndex = state.sendIndex
        val (messageKey, newState) = crypto.ratchetEncryptKey(state)
        ratchetStorage.saveState(chatId, newState)

        val encryptedMsg = crypto.encrypt(plaintext.encodeToByteArray(), messageKey)
        val payload = EncryptedPayload(
            ciphertext      = encryptedMsg.ciphertext,
            nonce           = encryptedMsg.nonce,
            messageIndex    = currentIndex,
            senderPublicKey = myPubKey
        )

        // Кэшируем plaintext для истории
        ratchetStorage.saveOutgoing(chatId, currentIndex, plaintext)

        return Base64Helper.encode(json.encodeToString(payload).encodeToByteArray())
    }
    suspend fun getOutgoingPlaintext(chatId: String, index: Int): String? =
        ratchetStorage.loadOutgoing(chatId, index)
    suspend fun decryptMessage(
        chatId: String,
        encryptedContent: String
    ): String? = runCatching {
        if (encryptedContent.isBlank()) return null

        val myPrivKey = identityStorage.getPrivateKey() ?: error("No identity private key")
        val myPubKey  = identityStorage.getPublicKey()  ?: error("No identity public key")

        val payloadBytes = try { Base64Helper.decode(encryptedContent) }
        catch (e: Exception) { return null }
        if (payloadBytes.isEmpty()) return null

        val payload = json.decodeFromString<EncryptedPayload>(payloadBytes.decodeToString())

        val state = ratchetStorage.loadState(chatId)?.let { saved ->
            if (saved.receiveIndex > payload.messageIndex) {
                val sharedSecret = crypto.computeSharedSecret(myPrivKey, payload.senderPublicKey)
                crypto.initRatchet(sharedSecret, myPubKey, payload.senderPublicKey).also {  // ← myPubKey
                    ratchetStorage.saveState(chatId, it)
                }
            } else saved
        } ?: run {
            val sharedSecret = crypto.computeSharedSecret(myPrivKey, payload.senderPublicKey)
            crypto.initRatchet(sharedSecret, myPubKey, payload.senderPublicKey).also {  // ← myPubKey
                ratchetStorage.saveState(chatId, it)
            }
        }

        val (messageKey, newState) = crypto.ratchetDecryptKey(state, payload.messageIndex)
        ratchetStorage.saveState(chatId, newState)

        crypto.decrypt(EncryptedMessage(payload.ciphertext, payload.nonce), messageKey)
            .decodeToString()
    }.getOrElse {
        println("E2EE decrypt error: ${it.message}")
        it.printStackTrace()
        null
    }

    suspend fun resetSession(chatId: String) = ratchetStorage.clearState(chatId)
    suspend fun resetAllSessions() = ratchetStorage.clearAll()
}