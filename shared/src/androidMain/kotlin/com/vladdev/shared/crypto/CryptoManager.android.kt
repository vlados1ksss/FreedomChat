@file:OptIn(InternalSerializationApi::class)

package com.vladdev.shared.crypto

import com.vladdev.shared.crypto.dto.EncryptedMessage
import kotlinx.serialization.InternalSerializationApi
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.DiffieHellman
import com.goterl.lazysodium.interfaces.SecretBox
import com.goterl.lazysodium.interfaces.Sign
import com.goterl.lazysodium.interfaces.Random
import com.goterl.lazysodium.interfaces.GenericHash
import com.vladdev.shared.crypto.dto.RatchetState
actual class CryptoManager {

    private val sodium = LazySodiumAndroid(SodiumAndroid())

    actual fun generateIdentityKeyPair(): Pair<String, String> {
        val keyPair = sodium.cryptoBoxKeypair()     // X25519
        return keyPair.publicKey.asHexString to keyPair.secretKey.asHexString
    }

    actual fun generateSigningKeyPair(): Pair<String, String> {
        val keyPair = sodium.cryptoSignKeypair()    // Ed25519
        // lazysodium: publicKey = verify key (32b), secretKey = sign key (64b)
        return keyPair.publicKey.asHexString to keyPair.secretKey.asHexString
    }

    actual fun sign(message: ByteArray, signPrivateKeyHex: String): String {
        val sigBytes  = ByteArray(Sign.BYTES)
        val privBytes = signPrivateKeyHex.hexToByteArray()
        sodium.cryptoSignDetached(sigBytes, message, message.size.toLong(), privBytes)
        return sigBytes.toHexString()
    }

    actual fun computeSharedSecret(myPrivKeyHex: String, theirPubKeyHex: String): String {
        val shared   = ByteArray(DiffieHellman.SCALARMULT_BYTES)
        val myPriv   = myPrivKeyHex.hexToByteArray()
        val theirPub = theirPubKeyHex.hexToByteArray()

        check(sodium.cryptoScalarMult(shared, myPriv, theirPub)) {
            "X25519 DH failed"
        }

        // cryptoGenericHash(out, outLen, in, inLen) — чистый ByteArray вариант
        val hashed = ByteArray(GenericHash.BYTES)
        sodium.cryptoGenericHash(hashed, hashed.size, shared, shared.size.toLong())
        return hashed.toHexString()
    }

    actual fun encrypt(plaintext: ByteArray, keyHex: String): EncryptedMessage {
        val key    = keyHex.hexToByteArray()
        val nonce = sodium.randomBytesBuf(SecretBox.NONCEBYTES)
        val cipher = ByteArray(plaintext.size + SecretBox.MACBYTES)
        sodium.cryptoSecretBoxEasy(cipher, plaintext, plaintext.size.toLong(), nonce, key)
        return EncryptedMessage(cipher.toHexString(), nonce.toHexString())
    }

    actual fun decrypt(msg: EncryptedMessage, keyHex: String): ByteArray {
        val key    = keyHex.hexToByteArray()
        val nonce  = msg.nonce.hexToByteArray()
        val cipher = msg.ciphertext.hexToByteArray()
        val plain  = ByteArray(cipher.size - SecretBox.MACBYTES)
        val ok = sodium.cryptoSecretBoxOpenEasy(plain, cipher, cipher.size.toLong(), nonce, key)
        check(ok) { "Decryption failed — message corrupted or wrong key" }
        return plain
    }

    // helpers
    private fun String.hexToByteArray(): ByteArray {
        check(length % 2 == 0)
        return ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
    private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

    // expect
    // expect
    actual fun initRatchet(
        sharedSecretHex: String,
        myPublicKeyHex: String,
        theirPublicKeyHex: String
    ): RatchetState {
        val rootKey = hkdfDerive(sharedSecretHex, "root")

        // Детерминированный порядок: меньший ключ → "initiator"
        val iAmInitiator = myPublicKeyHex < theirPublicKeyHex

        val chainA = hkdfDerive(sharedSecretHex, "chain_a")
        val chainB = hkdfDerive(sharedSecretHex, "chain_b")

        // Initiator отправляет по chainA, получает по chainB
        // Responder отправляет по chainB, получает по chainA
        val sendChainKey    = if (iAmInitiator) chainA else chainB
        val receiveChainKey = if (iAmInitiator) chainB else chainA

        return RatchetState(
            theirPublicKey    = theirPublicKeyHex,
            rootKey           = rootKey,
            sendChainKey      = sendChainKey,
            receiveChainKey   = receiveChainKey,
            sendIndex         = 0,
            receiveIndex      = 0
        )
    }
    actual fun ratchetEncryptKey(state: RatchetState): Pair<String, RatchetState> {
        val indexHex     = state.sendIndex.toString(16).padStart(8, '0')
        val messageKey   = hkdfDerive(state.sendChainKey + indexHex, "message")
        val newChainKey  = hkdfDerive(state.sendChainKey, "next_chain")
        return messageKey to state.copy(
            sendChainKey = newChainKey,
            sendIndex    = state.sendIndex + 1
        )
    }

    // ratchetDecryptKey — двигаем только receiveChain
    actual fun ratchetDecryptKey(state: RatchetState, messageIndex: Int): Pair<String, RatchetState> {
        var chainKey = state.receiveChainKey
        repeat(messageIndex - state.receiveIndex) {
            chainKey = hkdfDerive(chainKey, "next_chain")
        }
        val indexHex    = messageIndex.toString(16).padStart(8, '0')
        val messageKey  = hkdfDerive(chainKey + indexHex, "message")
        val newChainKey = hkdfDerive(chainKey, "next_chain")
        return messageKey to state.copy(
            receiveChainKey = newChainKey,
            receiveIndex    = messageIndex + 1
        )
    }

    actual fun hkdfDerive(keyMaterial: String, info: String): String {
        // Используем BLAKE2b (cryptoGenericHash) как KDF
        // output = BLAKE2b(key=keyMaterial, message=info)
        val keyBytes  = keyMaterial.hexToByteArray()
        val infoBytes = info.encodeToByteArray()
        val out       = ByteArray(32)
        // cryptoGenericHash(out, outLen, in, inLen, key, keyLen)
        sodium.cryptoGenericHash(out, out.size, infoBytes, infoBytes.size.toLong(), keyBytes, keyBytes.size)
        return out.toHexString()
    }
}