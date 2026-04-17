package com.vladdev.shared.crypto

expect class MediaCryptoHelper(crypto: CryptoManager) {

    /**
     * Шифрует файл стримингом (secretstream).
     * input/output — платформенные обёртки над URI (Android) или File (Desktop/iOS).
     * Не загружает весь файл в память.
     */
    fun encryptFile(
        input: PlatformFile,
        output: PlatformFile,
        messageKey: String,
        onProgress: ((Int) -> Unit)?
    )

    /**
     * Расшифровывает файл стримингом.
     */
    fun decryptFile(
        input: PlatformFile,
        output: PlatformFile,
        messageKey: String
    )

    /**
     * Шифрует маленький blob (превью) — в память, без стриминга.
     * Возвращает [nonce(24b) | ciphertext].
     */
    fun encryptThumb(plainBytes: ByteArray, messageKey: String): ByteArray

    /**
     * Расшифровывает blob превью.
     * Ожидает формат [nonce(24b) | ciphertext].
     */
    fun decryptThumb(encryptedBlob: ByteArray, messageKey: String): ByteArray
}
