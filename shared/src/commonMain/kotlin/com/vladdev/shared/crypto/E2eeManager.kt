package com.vladdev.shared.crypto

import com.vladdev.shared.crypto.dto.EncryptedMessage
import com.vladdev.shared.crypto.dto.EncryptedPayload
import com.vladdev.shared.storage.IdentityKeyStorage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(InternalSerializationApi::class, ExperimentalEncodingApi::class)
class E2eeManager(
    private val crypto: CryptoManager,
    private val identityStorage: IdentityKeyStorage,
    private val ratchetStorage: RatchetStorage
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val chatMutexes = ConcurrentHashMap<String, Mutex>()

    private fun mutexFor(chatId: String): Mutex =
        chatMutexes.getOrPut(chatId) { Mutex() }

    suspend fun encryptMessage(chatId: String, plaintext: String, theirPublicKeyHex: String): String =
        mutexFor(chatId).withLock {
            encryptMessageInternal(chatId, plaintext, theirPublicKeyHex)
        }

    suspend fun encryptMessageWithKey(chatId: String, plaintext: String, theirPublicKeyHex: String): Pair<String, String> =
        mutexFor(chatId).withLock {
            encryptMessageWithKeyInternal(chatId, plaintext, theirPublicKeyHex)
        }
    suspend fun saveOutgoingPlaintext(chatId: String, messageId: String, plaintext: String) {
        ratchetStorage.saveOutgoing(chatId, messageId, plaintext)
    }

    suspend fun getOutgoingPlaintext(chatId: String, messageId: String): String? =
        ratchetStorage.loadOutgoing(chatId, messageId)
    suspend fun decryptMessage(chatId: String, encryptedContent: String): String? =
        mutexFor(chatId).withLock {
            decryptMessageInternal(chatId, encryptedContent)
        }

    suspend fun decryptMessageWithKey(chatId: String, encryptedContent: String): Pair<String, String>? =
        mutexFor(chatId).withLock {
            decryptMessageWithKeyInternal(chatId, encryptedContent)
        }
    private suspend fun encryptMessageInternal(
        chatId: String,
        plaintext: String,
        theirPublicKeyHex: String
    ): String {
        val myPrivKey = identityStorage.getPrivateKey() ?: error("No identity private key")
        val myPubKey  = identityStorage.getPublicKey()  ?: error("No identity public key")

        val state = ratchetStorage.loadState(chatId) ?: run {
            val sharedSecret = crypto.computeSharedSecret(myPrivKey, theirPublicKeyHex)
            crypto.initRatchet(sharedSecret, myPubKey, theirPublicKeyHex).also {
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
        return Base64Helper.encode(json.encodeToString(payload).encodeToByteArray())
    }

    private suspend fun encryptMessageWithKeyInternal(
        chatId: String,
        plaintext: String,
        theirPublicKeyHex: String
    ): Pair<String, String> {
        val myPrivKey = identityStorage.getPrivateKey() ?: error("No identity private key")
        val myPubKey  = identityStorage.getPublicKey()  ?: error("No identity public key")

        val state = ratchetStorage.loadState(chatId) ?: run {
            val sharedSecret = crypto.computeSharedSecret(myPrivKey, theirPublicKeyHex)
            crypto.initRatchet(sharedSecret, myPubKey, theirPublicKeyHex).also {
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
        val encoded = Base64Helper.encode(json.encodeToString(payload).encodeToByteArray())
        return encoded to messageKey
    }

    private suspend fun decryptMessageInternal(
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

        val savedState = ratchetStorage.loadState(chatId)

        val (messageKey, newState) = when {
            savedState == null -> {
                // Первое сообщение — инициализируем рэтчет
                val sharedSecret = crypto.computeSharedSecret(myPrivKey, payload.senderPublicKey)
                val state = crypto.initRatchet(sharedSecret, myPubKey, payload.senderPublicKey)
                ratchetStorage.saveState(chatId, state)
                crypto.ratchetDecryptKey(state, payload.messageIndex)
            }
            savedState.receiveIndex > payload.messageIndex -> {
                // Сообщение из истории — уже двигали рэтчет мимо этого индекса.
                // Пересчитываем ключ с нуля БЕЗ сохранения состояния.
                val sharedSecret = crypto.computeSharedSecret(myPrivKey, payload.senderPublicKey)
                val freshState = crypto.initRatchet(sharedSecret, myPubKey, payload.senderPublicKey)
                val (key, _) = crypto.ratchetDecryptKey(freshState, payload.messageIndex)
                return crypto.decrypt(
                    EncryptedMessage(payload.ciphertext, payload.nonce), key
                ).decodeToString()
            }
            else -> {
                // Нормальный случай — двигаем рэтчет вперёд
                val result = crypto.ratchetDecryptKey(savedState, payload.messageIndex)
                ratchetStorage.saveState(chatId, result.second)
                result
            }
        }

        // Сохраняем только если дошли сюда (savedState == null или нормальный случай)
        crypto.decrypt(
            EncryptedMessage(payload.ciphertext, payload.nonce), messageKey
        ).decodeToString()
    }.getOrElse {
        println("E2EE decrypt error: ${it.message}")
        null
    }

    private suspend fun decryptMessageWithKeyInternal(
        chatId: String,
        encryptedContent: String
    ): Pair<String, String>? {
        if (encryptedContent.isBlank()) return null

        val myPrivKey = identityStorage.getPrivateKey() ?: error("No identity private key")
        val myPubKey  = identityStorage.getPublicKey()  ?: error("No identity public key")

        return runCatching {
            val payloadBytes = try { Base64Helper.decode(encryptedContent) }
            catch (e: Exception) { return null }
            if (payloadBytes.isEmpty()) return null

            val payload = json.decodeFromString<EncryptedPayload>(payloadBytes.decodeToString())

            val savedState = ratchetStorage.loadState(chatId)

            when {
                savedState == null -> {
                    val sharedSecret = crypto.computeSharedSecret(myPrivKey, payload.senderPublicKey)
                    val state = crypto.initRatchet(sharedSecret, myPubKey, payload.senderPublicKey)
                    ratchetStorage.saveState(chatId, state)
                    val (messageKey, newState) = crypto.ratchetDecryptKey(state, payload.messageIndex)
                    ratchetStorage.saveState(chatId, newState)
                    val plaintext = crypto.decrypt(
                        EncryptedMessage(payload.ciphertext, payload.nonce), messageKey
                    ).decodeToString()
                    plaintext to messageKey
                }
                savedState.receiveIndex > payload.messageIndex -> {
                    // История — пересчитываем без сохранения
                    val sharedSecret = crypto.computeSharedSecret(myPrivKey, payload.senderPublicKey)
                    val freshState = crypto.initRatchet(sharedSecret, myPubKey, payload.senderPublicKey)
                    val (messageKey, _) = crypto.ratchetDecryptKey(freshState, payload.messageIndex)
                    val plaintext = crypto.decrypt(
                        EncryptedMessage(payload.ciphertext, payload.nonce), messageKey
                    ).decodeToString()
                    plaintext to messageKey
                }
                else -> {
                    val (messageKey, newState) = crypto.ratchetDecryptKey(savedState, payload.messageIndex)
                    ratchetStorage.saveState(chatId, newState)
                    val plaintext = crypto.decrypt(
                        EncryptedMessage(payload.ciphertext, payload.nonce), messageKey
                    ).decodeToString()
                    plaintext to messageKey
                }
            }
        }.getOrElse {
            println("E2EE decryptWithKey error: ${it.message}")
            null
        }
    }
    suspend fun ensureSessionInitialized(chatId: String, theirPublicKeyHex: String) {
        val existing = ratchetStorage.loadState(chatId)
        if (existing != null) return

        val myPrivKey    = identityStorage.getPrivateKey() ?: error("No identity private key")
        val myPubKey     = identityStorage.getPublicKey()  ?: error("No identity public key")
        val sharedSecret = crypto.computeSharedSecret(myPrivKey, theirPublicKeyHex)
        val newState     = crypto.initRatchet(sharedSecret, myPubKey, theirPublicKeyHex)
        ratchetStorage.saveState(chatId, newState)
    }
    suspend fun resetAllSessions() = ratchetStorage.clearAll()
// OLD VERSION
//    suspend fun saveIncomingPlaintext(chatId: String, messageId: String, plaintext: String) {
//        ratchetStorage.saveOutgoing(chatId, "in_$messageId", plaintext)
//    }
//
//    suspend fun getIncomingPlaintext(chatId: String, messageId: String): String? =
//        ratchetStorage.loadOutgoing(chatId, "in_$messageId")

    // NEW VERSION
    suspend fun getIncomingPlaintext(chatId: String, messageId: String): String? =
        ratchetStorage.getIncomingPlaintext(chatId, messageId)

    suspend fun saveIncomingPlaintext(chatId: String, messageId: String, plaintext: String) =
        ratchetStorage.saveIncomingPlaintext(chatId, messageId, plaintext)
}