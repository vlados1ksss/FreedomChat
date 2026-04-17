package com.vladdev.shared.crypto

// commonMain
// commonMain
// commonMain — PlatformFile.kt
expect class PlatformFile {
    val name: String
    val exists: Boolean
    val length: Long
    val absolutePath: String
    fun delete(): Boolean
    fun writeBytes(bytes: ByteArray)
    // Стриминговое чтение: вызывает onChunk для каждого куска
    fun readChunked(chunkSize: Int, onChunk: (ByteArray, Int) -> Unit)
}
expect fun platformFileFromPath(path: String): PlatformFile
expect fun platformTempFile(dir: PlatformFile, name: String): PlatformFile
expect fun platformCacheDir(context: Any?): PlatformFile
expect fun platformFileFromUri(context: Any?, uri: Any): PlatformFile
expect fun copyUriToFile(context: Any?, uri: Any, dest: PlatformFile)
expect fun encryptThumbFromBitmap(
    bitmap: Any?,
    messageKey: String,
    encryptThumb: (ByteArray, String) -> ByteArray
): ByteArray?
expect fun platformFileToJavaFile(file: PlatformFile): Any // возвращает java.io.File на Android