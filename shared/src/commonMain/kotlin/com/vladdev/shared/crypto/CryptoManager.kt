package com.vladdev.shared.crypto

import com.vladdev.shared.crypto.dto.EncryptedMessage
import com.vladdev.shared.crypto.dto.RatchetState
import kotlinx.serialization.InternalSerializationApi

expect class CryptoManager {

    /** Генерирует X25519 keypair. Возвращает (publicKeyHex, privateKeyHex) */
    fun generateIdentityKeyPair(): Pair<String, String>

    /** Ed25519 подпись для transfer */
    fun generateSigningKeyPair(): Pair<String, String>   // (verifyKeyHex, signKeyHex)
    fun sign(message: ByteArray, signPrivateKeyHex: String): String  // hex signature

    /** X25519 DH — вычислить общий секрет */
    fun computeSharedSecret(myPrivKeyHex: String, theirPubKeyHex: String): String

    /** ChaCha20-Poly1305 encrypt */
    @OptIn(InternalSerializationApi::class)
    fun encrypt(plaintext: ByteArray, keyHex: String): EncryptedMessage

    /** ChaCha20-Poly1305 decrypt */
    @OptIn(InternalSerializationApi::class)
    fun decrypt(msg: EncryptedMessage, keyHex: String): ByteArray
    // --- Ratchet ---
    /** Инициализировать новую сессию из shared secret */
    fun initRatchet(sharedSecretHex: String, myPublicKeyHex: String, theirPublicKeyHex: String): RatchetState

    /** Деривировать ключ для отправки, вернуть (sessionKeyHex, newState) */
    fun ratchetEncryptKey(state: RatchetState): Pair<String, RatchetState>

    /** Деривировать ключ для получения по messageIndex */
    fun ratchetDecryptKey(state: RatchetState, messageIndex: Int): Pair<String, RatchetState>

    /** HKDF-expand: derive child key from parent + info */
    fun hkdfDerive(keyMaterial: String, info: String): String
}