package com.vladdev.shared.crypto

import com.vladdev.shared.crypto.dto.EncryptedMessage
import kotlinx.serialization.InternalSerializationApi

// MediaCryptoHelper.kt
//
// Схема: тот же CryptoManager, тот же ratchet-ключ для конкретного сообщения.
// Медиафайл шифруется отдельным media-ключом, производным от message-ключа:
//   mediaKey = hkdfDerive(messageKey, "media_file")
//   thumbKey  = hkdfDerive(messageKey, "media_thumb")
//
// Это позволяет:
//   - не трогать ratchet state при шифровании медиа (только при отправке сообщения)
//   - передавать mediaKey получателю неявно — он вычислит его из того же messageKey

@OptIn(InternalSerializationApi::class)
class MediaCryptoHelper(private val crypto: CryptoManager) {

    /** Зашифровать файл перед отправкой на сервер.
     *  Возвращает зашифрованные байты и nonce (нужны для расшифровки). */
    fun encryptFile(
        plainBytes: ByteArray,
        messageKey: String       // hex-ключ, полученный из ratchetEncryptKey
    ): ByteArray {
        val mediaKey = crypto.hkdfDerive(messageKey, "media_file")
        val encrypted = crypto.encrypt(plainBytes, mediaKey)
        // Упакуем nonce (24 байта) + ciphertext в один blob
        // Формат: [nonce_hex_48chars][ciphertext_hex...]
        // Проще: Length-prefixed binary — nonce(24b) | cipher(N b)
        val nonceBytes   = encrypted.nonce.hexToByteArray()
        val cipherBytes  = encrypted.ciphertext.hexToByteArray()
        return nonceBytes + cipherBytes
    }

    fun encryptThumb(
        plainBytes: ByteArray,
        messageKey: String
    ): ByteArray {
        val thumbKey  = crypto.hkdfDerive(messageKey, "media_thumb")
        val encrypted = crypto.encrypt(plainBytes, thumbKey)
        val nonceBytes  = encrypted.nonce.hexToByteArray()
        val cipherBytes = encrypted.ciphertext.hexToByteArray()
        return nonceBytes + cipherBytes
    }

    /** Расшифровать blob, полученный от сервера. */
    fun decryptFile(
        encryptedBlob: ByteArray,
        messageKey: String
    ): ByteArray {
        val mediaKey    = crypto.hkdfDerive(messageKey, "media_file")
        val nonceBytes  = encryptedBlob.copyOfRange(0, 24)
        val cipherBytes = encryptedBlob.copyOfRange(24, encryptedBlob.size)
        return crypto.decrypt(
            EncryptedMessage(
                nonce = nonceBytes.toHexString(),
                ciphertext = cipherBytes.toHexString()
            ),
            mediaKey
        )
    }

    fun decryptThumb(
        encryptedBlob: ByteArray,
        messageKey: String
    ): ByteArray {
        val thumbKey    = crypto.hkdfDerive(messageKey, "media_thumb")
        val nonceBytes  = encryptedBlob.copyOfRange(0, 24)
        val cipherBytes = encryptedBlob.copyOfRange(24, encryptedBlob.size)
        return crypto.decrypt(
            EncryptedMessage(
                nonce      = nonceBytes.toHexString(),
                ciphertext = cipherBytes.toHexString()
            ),
            thumbKey
        )
    }

    // Helpers — дублируем из CryptoManager чтобы не ломать expect/actual
    private fun String.hexToByteArray(): ByteArray {
        check(length % 2 == 0)
        return ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
    private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
}