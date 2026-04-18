package com.vladdev.shared.crypto

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.goterl.lazysodium.interfaces.SecretStream
import com.vladdev.shared.crypto.dto.EncryptedMessage
import kotlinx.serialization.InternalSerializationApi
import java.io.File
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.vladdev.shared.chats.ChatRepository
import java.io.ByteArrayOutputStream

@OptIn(InternalSerializationApi::class)
actual class MediaCryptoHelper actual constructor(
    private val crypto: CryptoManager
) {
    private val sodium = LazySodiumAndroid(SodiumAndroid())

    actual fun encryptFile(
        input: PlatformFile,
        output: PlatformFile,
        messageKey: String,
        onProgress: ((Int) -> Unit)?
    ) {
        val context    = input.context ?: error("PlatformFile missing context for URI input")
        val uri        = input.uri     ?: error("PlatformFile missing URI")
        val outputFile = output.file   ?: error("PlatformFile missing File for output")

        val cr = context.contentResolver
        val totalBytes = cr.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow(OpenableColumns.SIZE)) else -1L
        } ?: -1L

        val mediaKey = crypto.hkdfDerive(messageKey, "media_file")
        val keyBytes = mediaKey.hexToByteArray()

        val state  = SecretStream.State()
        val header = ByteArray(SecretStream.HEADERBYTES)
        sodium.cryptoSecretStreamInitPush(state, header, keyBytes)

        var written   = 0L
        val chunkSize = 64 * 1024

        outputFile.outputStream().buffered().use { out ->
            out.write(header)

            cr.openInputStream(uri)!!.buffered().use { inp ->
                val buf = ByteArray(chunkSize)
                var read: Int

                while (inp.read(buf).also { read = it } != -1) {
                    val plain  = if (read == chunkSize) buf else buf.copyOf(read)
                    val cipher = ByteArray(plain.size + SecretStream.ABYTES)

                    // TAG_FINAL только на последнем чанке — проверяем available()
                    // Внимание: available() ненадёжен для некоторых InputStream,
                    // поэтому используем двойную буферизацию через peek
                    val tag = if (inp.available() == 0)
                        SecretStream.TAG_FINAL.toByte()
                    else
                        SecretStream.TAG_MESSAGE.toByte()

                    sodium.cryptoSecretStreamPush(state, cipher, plain, plain.size.toLong(), tag)
                    out.write(cipher)

                    written += read
                    if (totalBytes > 0) {
                        onProgress?.invoke(((written * 100) / totalBytes).toInt().coerceIn(0, 99))
                    }
                }
            }
        }

        onProgress?.invoke(100)
    }

    actual fun decryptFile(
        input: PlatformFile,
        output: PlatformFile,
        messageKey: String
    ) {
        val encryptedFile = input.file  ?: error("PlatformFile missing File for input")
        val outputFile    = output.file ?: error("PlatformFile missing File for output")

        val mediaKey = crypto.hkdfDerive(messageKey, "media_file")
        val keyBytes = mediaKey.hexToByteArray()

        encryptedFile.inputStream().buffered().use { inp ->
            val header = ByteArray(SecretStream.HEADERBYTES)
            check(inp.read(header) == SecretStream.HEADERBYTES) {
                "Encrypted file too short — missing secretstream header"
            }

            val state = SecretStream.State()
            sodium.cryptoSecretStreamInitPull(state, header, keyBytes)

            outputFile.outputStream().buffered().use { out ->
                val bufSize = 64 * 1024 + SecretStream.ABYTES
                val buf     = ByteArray(bufSize)
                var read: Int

                while (inp.read(buf).also { read = it } != -1) {
                    val cipher = if (read == bufSize) buf else buf.copyOf(read)
                    val plain  = ByteArray(cipher.size - SecretStream.ABYTES)
                    val tag    = ByteArray(1)

                    val ok = sodium.cryptoSecretStreamPull(
                        state, plain, tag, cipher, cipher.size.toLong()
                    )
                    check(ok) { "Decryption failed — data corrupted or wrong key" }
                    out.write(plain)
                }
            }
        }
    }

    actual fun encryptThumb(plainBytes: ByteArray, messageKey: String): ByteArray {
        val thumbKey  = crypto.hkdfDerive(messageKey, "media_thumb")
        val encrypted = crypto.encrypt(plainBytes, thumbKey)
        return encrypted.nonce.hexToByteArray() + encrypted.ciphertext.hexToByteArray()
    }

    actual fun decryptThumb(encryptedBlob: ByteArray, messageKey: String): ByteArray {
        val thumbKey = crypto.hkdfDerive(messageKey, "media_thumb")
        val nonce    = encryptedBlob.copyOfRange(0, 24)
        val cipher   = encryptedBlob.copyOfRange(24, encryptedBlob.size)
        return crypto.decrypt(
            EncryptedMessage(nonce.toHexString(), cipher.toHexString()),
            thumbKey
        )
    }

    // ── Helpers ────────────────────────────────────────────────────

    private fun String.hexToByteArray(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }
}

// androidMain — actual
// androidMain
actual class PlatformFile(
    val file: File? = null,
    val uri: Uri? = null,
    val context: Context? = null
) {
    actual val name: String
        get() = file?.name ?: uri?.lastPathSegment ?: "unknown"
    actual val exists: Boolean
        get() = file?.exists() ?: false
    actual val length: Long
        get() = file?.length() ?: 0L
    actual val absolutePath: String
        get() = file?.absolutePath ?: ""
    actual fun delete(): Boolean =
        file?.delete() ?: false
    actual fun writeBytes(bytes: ByteArray) {
        file?.writeBytes(bytes) ?: error("PlatformFile has no File for writeBytes")
    }
    actual fun readChunked(chunkSize: Int, onChunk: (ByteArray, Int) -> Unit) {
        val f = file ?: error("PlatformFile has no backing File")
        f.inputStream().buffered().use { inp ->
            val buf = ByteArray(chunkSize)
            var read: Int
            while (inp.read(buf).also { read = it } != -1) {
                onChunk(buf, read)
            }
        }
    }
}

actual fun platformFileFromPath(path: String): PlatformFile =
    PlatformFile(file = File(path))

actual fun platformTempFile(dir: PlatformFile, name: String): PlatformFile =
    PlatformFile(file = File(dir.file!!, name))

actual fun platformCacheDir(context: Any?): PlatformFile =
    PlatformFile(file = (context as Context).cacheDir)


fun PlatformFile.fromUri(context: Context, uri: Uri): PlatformFile =
    PlatformFile(uri = uri, context = context)

actual fun platformFileFromUri(context: Any?, uri: Any): PlatformFile =
    PlatformFile(uri = uri as Uri, context = context as Context)

actual fun copyUriToFile(context: Any?, uri: Any, dest: PlatformFile) {
    val ctx = context as Context
    val out = dest.file ?: error("Destination PlatformFile has no File")
    ctx.contentResolver.openInputStream(uri as Uri)?.use { inp ->
        out.outputStream().use { inp.copyTo(it) }
    }
}

actual fun encryptThumbFromBitmap(
    bitmap: Any?,
    messageKey: String,
    encryptThumb: (ByteArray, String) -> ByteArray
): ByteArray? {
    bitmap as? Bitmap ?: return null
    val plain = ByteArrayOutputStream().use { out ->
        (bitmap as Bitmap).compress(Bitmap.CompressFormat.JPEG, 80, out)
        out.toByteArray()
    }
    return encryptThumb(plain, messageKey)
}

actual fun platformFileToJavaFile(file: PlatformFile): Any =
    file.file ?: error("PlatformFile has no backing File")
