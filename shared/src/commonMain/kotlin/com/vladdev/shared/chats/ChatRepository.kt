package com.vladdev.shared.chats

import com.vladdev.shared.chats.dto.ChatDto
import com.vladdev.shared.chats.dto.ChatRequestDto
import com.vladdev.shared.chats.dto.DecryptedMessage
import com.vladdev.shared.chats.dto.ForwardedMessagePayload
import com.vladdev.shared.chats.dto.MediaDto
import com.vladdev.shared.chats.dto.MediaState
import com.vladdev.shared.chats.dto.MessageDto
import com.vladdev.shared.chats.dto.SearchUserResponse
import com.vladdev.shared.crypto.CryptoManager
import com.vladdev.shared.crypto.E2eeManager
import com.vladdev.shared.crypto.MediaCryptoHelper
import com.vladdev.shared.crypto.MediaKeyCache
import com.vladdev.shared.storage.IdentityKeyStorage
import com.vladdev.shared.storage.UserIdStorage
import com.vladdev.shared.user.dto.PresenceResponse
import com.vladdev.shared.user.dto.UserProfileResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.InternalSerializationApi
import java.io.File
import com.vladdev.shared.crypto.PlatformFile
import com.vladdev.shared.crypto.copyUriToFile
import com.vladdev.shared.crypto.encryptThumbFromBitmap
import com.vladdev.shared.crypto.platformCacheDir
import com.vladdev.shared.crypto.platformFileFromUri
import com.vladdev.shared.crypto.platformFileToJavaFile
import com.vladdev.shared.crypto.platformTempFile



@OptIn(InternalSerializationApi::class)
class ChatRepository(
    private val api: ChatApi,
    private val identityStorage: IdentityKeyStorage,
    private val crypto: CryptoManager,
    private val e2ee: E2eeManager,
    private val userIdStorage: UserIdStorage,
    val mediaCrypto: MediaCryptoHelper,
    private val mediaKeyCache: MediaKeyCache,
    private val cacheDir: File
) {
    private val chatFlows = mutableMapOf<String, MutableSharedFlow<WsIncomingEvent>>()

    private fun getOrCreateFlow(chatId: String): MutableSharedFlow<WsIncomingEvent> =
        chatFlows.getOrPut(chatId) { MutableSharedFlow(replay = 50) }

    fun eventsFlow(chatId: String): Flow<WsIncomingEvent> = getOrCreateFlow(chatId)

    suspend fun openChat(chatId: String, scope: CoroutineScope) {
        api.openChatWebSocket(chatId, scope) { event ->
            getOrCreateFlow(chatId).tryEmit(event)
        }
    }
    suspend fun searchUserById(userId: String): Result<UserProfileResponse> =
        runCatching { api.getUserById(userId) }
    suspend fun sendMessage(
        chatId: String,
        plaintext: String,
        theirUserId: String,
        replyToId: String? = null, // уже расшифрованный текст оригинала — передаём как есть
    ) {
        val theirPublicKey = api.getPublicKey(theirUserId).publicKey
        val encryptedContent = e2ee.encryptMessage(chatId, plaintext, theirPublicKey)
        val messageId = api.sendMessageWS(
            chatId = chatId,
            encryptedContent = encryptedContent,
            replyToId = replyToId
        )
        e2ee.saveOutgoingPlaintext(chatId, messageId, plaintext)
    }

    suspend fun getMessages(chatId: String, myUserId: String): List<DecryptedMessage> {
        val raw = api.getMessages(chatId)

        // Разделяем на свои и чужие
        // Свои — не трогают рэтчет, берём из storage
        // Чужие — должны обрабатываться строго по messageIndex
        val result = mutableListOf<DecryptedMessage>()

        for (msg in raw) {
            val decrypted = when {
                msg.deletedForAll -> null

                msg.media != null -> decryptMediaMessage(msg, chatId, myUserId)

                msg.encryptedContent.isBlank() -> null

                msg.senderId == myUserId -> {
                    val plaintext = e2ee.getOutgoingPlaintext(chatId, msg.id)
                    plaintext?.let { buildDecrypted(msg, it) }
                }

                else -> {
                    // Сначала проверяем — может уже расшифровывали раньше
                    val cached = e2ee.getIncomingPlaintext(chatId, msg.id)
                    if (cached != null) {
                        buildDecrypted(msg, cached)
                    } else {
                        // Первый раз — расшифровываем и сохраняем
                        val plaintext = e2ee.decryptMessage(chatId, msg.encryptedContent)
                        plaintext?.also {
                            e2ee.saveIncomingPlaintext(chatId, msg.id, it)
                        }?.let { buildDecrypted(msg, it) }
                    }
                }
            }
            decrypted?.let { result.add(it) }
        }

        return result
    }

    suspend fun sendRead(chatId: String, messageId: String, userId: String) {
        api.sendReadWS(chatId, messageId, userId)
    }

    // ChatRepository
    suspend fun editMessage(chatId: String, messageId: String, newPlaintext: String, theirUserId: String) {
        val theirPublicKey = api.getPublicKey(theirUserId).publicKey
        val encrypted = e2ee.encryptMessage(chatId, newPlaintext, theirPublicKey)

        // Сначала сохраняем локально — до отправки по WS
        e2ee.saveOutgoingPlaintext(chatId, messageId, newPlaintext)

        api.sendEditWS(
            chatId           = chatId,
            messageId        = messageId,
            senderId         = userIdStorage.getUID() ?: "",
            encryptedContent = encrypted
        )
    }

    suspend fun deleteMessages(chatId: String, messageIds: List<String>, forAll: Boolean) {
        messageIds.forEach { id ->
            api.deleteMessageWS(chatId, id, forAll)
        }
    }

    suspend fun deleteMessage(chatId: String, messageId: String, forAll: Boolean) {
        api.deleteMessageWS(chatId, messageId, forAll)
    }

    suspend fun loadChats(): Result<List<ChatDto>> = runCatching {
        val raw = api.getChats()
        val myUserId = userIdStorage.getUID()

        raw.map { chat ->
            val decryptedLast = chat.lastMessage?.let { msg ->
                decryptLastMessage(msg, chat.chatId, myUserId)
            }
            chat.copy(lastMessage = decryptedLast)
        }
    }

    suspend fun searchUser(username: String): Result<SearchUserResponse> =
        runCatching { api.searchUser(username) }

    suspend fun createDirectChat(userId: String): Result<String> =
        runCatching { api.createDirectChat(userId).chatId }

    suspend fun loadRequests(): Result<List<ChatRequestDto>> =
        runCatching { api.getIncomingRequests() }

    suspend fun sendRequest(username: String): Result<String> =
        runCatching { api.sendRequest(username).requestId }

    suspend fun accept(requestId: String): Result<String> =
        runCatching { api.accept(requestId).chatId }

    suspend fun reject(requestId: String): Result<Unit> =
        runCatching { api.reject(requestId) }

    suspend fun getChatMuted(chatId: String): Boolean =
        runCatching { api.getChatMuted(chatId) }.getOrDefault(false)

    suspend fun setChatMuted(chatId: String, muted: Boolean) =
        api.setChatMuted(chatId, muted)


    private suspend fun decryptLastMessage(
        msg: MessageDto,
        chatId: String,
        myUserId: String?
    ): MessageDto {
        if (msg.deletedForAll || msg.encryptedContent.isBlank()) return msg

        val plaintext = if (msg.senderId == myUserId) {
            e2ee.getOutgoingPlaintext(chatId, msg.id)
        } else {
            e2ee.getIncomingPlaintext(chatId, msg.id)
        }

        return msg.copy(plaintextPreview = plaintext)
    }

    suspend fun decryptPreview(msg: MessageDto, chatId: String, myUserId: String): String? {
        if (msg.deletedForAll || msg.encryptedContent.isBlank()) return null
        return if (msg.senderId == myUserId) {
            e2ee.getOutgoingPlaintext(chatId, msg.id)
        } else {
            e2ee.getIncomingPlaintext(chatId, msg.id)
        }
    }

    suspend fun decryptEditPreview(
        chatId: String,
        messageId: String,
        encryptedContent: String,
        myUserId: String,
        senderId: String
    ): String? = if (senderId == myUserId) {
        e2ee.getOutgoingPlaintext(chatId, messageId)
    } else {
        e2ee.getIncomingPlaintext(chatId, messageId)
    }


    suspend fun markChatAsRead(chatId: String): Result<Unit> =
        runCatching { api.markChatAsRead(chatId) }

    suspend fun openChatBackground(chatId: String, scope: CoroutineScope) {
        api.openChatWebSocket(chatId, scope) { event ->
            getOrCreateFlow(chatId).tryEmit(event)
        }
    }

    suspend fun detachFromChat(chatId: String) {
        // Ничего не делаем с WS и flow — они живут в ChatsViewModel
        // Просто сигнализируем что активный чат покинут
    }

    // Вызывается из ChatsViewModel при уничтожении экрана списка
    suspend fun closeChatBackground(chatId: String) {
        api.closeChatWebSocket(chatId)
        chatFlows.remove(chatId)
    }


    // ChatRepository
    suspend fun forwardMessages(
        targetChatId: String,
        messages: List<DecryptedMessage>,
        theirUserId: String,
        nameResolver: (DecryptedMessage) -> String
    ) {
        val theirPublicKey = api.getPublicKey(theirUserId).publicKey
        e2ee.ensureSessionInitialized(targetChatId, theirPublicKey)

        val payloads = messages.sortedBy { it.createdAt }.map { msg ->
            val text = msg.text ?: ""
            val encrypted = e2ee.encryptMessage(targetChatId, text, theirPublicKey)
            Triple(text, encrypted, msg)
        }

        val sentIds = api.sendForward(
            targetChatId,
            payloads.map { (_, encrypted, msg) ->
                ForwardedMessagePayload(
                    encryptedContent    = encrypted,
                    originalSenderId    = msg.senderId,
                    originalSenderName  = nameResolver(msg),
                    originalCreatedAt   = msg.createdAt
                )
            }
        )

        sentIds.zip(payloads).forEach { (msgId, triple) ->
            val (text, _, _) = triple
            e2ee.saveOutgoingPlaintext(targetChatId, msgId, text)
        }
    }

    suspend fun pinMessage(chatId: String, messageId: String, unpin: Boolean) {
        api.sendPinWS(chatId, messageId, unpin)
    }

    suspend fun getPinnedMessages(chatId: String, myUserId: String): List<DecryptedMessage> {
        val raw = api.getPinnedMessages(chatId)
        return raw.mapNotNull { msg ->
            val text = if (msg.senderId == myUserId)
                e2ee.getOutgoingPlaintext(chatId, msg.id)
            else
                e2ee.decryptMessage(chatId, msg.encryptedContent)
            if (text == null) null
            else DecryptedMessage(
                id               = msg.id,
                chatId           = msg.chatId,
                senderId         = msg.senderId,
                text             = text,
                createdAt        = msg.createdAt,
                deletedForAll    = false,
                statuses         = emptyList(),
                pinnedAt         = msg.pinnedAt
            )
        }
    }

    private val backgroundSubscribers = mutableSetOf<String>()

    fun registerBackgroundSubscriber(chatId: String) {
        backgroundSubscribers.add(chatId)
    }

    fun unregisterBackgroundSubscriber(chatId: String) {
        backgroundSubscribers.remove(chatId)
    }
    suspend fun sendTyping(chatId: String, isTyping: Boolean) {
        runCatching { api.sendTypingWS(chatId, isTyping) }
    }
    suspend fun getPresence(userId: String): Result<PresenceResponse> =
        runCatching { api.getPresence(userId) }

    suspend fun deleteChat(chatId: String): Result<Unit> =
        runCatching { api.deleteChat(chatId) }

    suspend fun clearHistory(chatId: String): Result<Unit> =
        runCatching { api.clearHistory(chatId) }

    suspend fun sendReaction(
        chatId: String,
        messageId: String,
        emoji: String,
        userId: String,
        remove: Boolean
    ) {
        api.sendReactionWS(chatId, messageId, emoji, userId, remove)
    }

//    suspend fun sendMediaMessage(
//        chatId: String,
//        theirUserId: String,
//        mediaType: String,
//        mimeType: String,
//        fileName: String,
//        plainFilePlatform: PlatformFile,   // ← было: plainFileBytes: ByteArray
//        plainThumbBytes: ByteArray?,
//        metadataJson: String?,
//        caption: String = "",
//        replyToId: String? = null,
//        onProgress: (Int) -> Unit = {},
//        onUploaded: ((mediaId: String) -> Unit)? = null,
//        context: Any? = null               // нужен на Android для cacheDir
//    ) {
//        val theirPublicKey = api.getPublicKey(theirUserId).publicKey
//        val captionText    = caption.ifBlank { "\u200B" }
//        val (encryptedCaption, messageKey) = e2ee.encryptMessageWithKey(
//            chatId            = chatId,
//            plaintext         = captionText,
//            theirPublicKeyHex = theirPublicKey
//        )
//
//        // Шифруем файл стримингом во временный файл
//        val cacheDir   = platformCacheDir(context)
//        val tempOutput = platformTempFile(cacheDir, "enc_tmp_send_${System.currentTimeMillis()}")
//
//        mediaCrypto.encryptFile(          // ← было: mediaCrypto.encryptBytes(...)
//            input      = plainFilePlatform,
//            output     = tempOutput,
//            messageKey = messageKey,
//            onProgress = { pct ->
//                // Шифрование: 0..40%
//                onProgress((pct * 0.4f).toInt())
//            }
//        )
//
//        // Превью — маленькое, можно в память через encryptThumb
//        val encryptedThumb = plainThumbBytes?.let {
//            mediaCrypto.encryptThumb(it, messageKey)
//        }
//
//        val encryptedFile = tempOutput.file
//            ?: error("PlatformFile missing File after encryption")
//
//        val uploadResponse = api.uploadMediaFile(   // ← используем File-версию, не ByteArray
//            chatId           = chatId,
//            type             = mediaType,
//            mimeType         = mimeType,
//            originalFileName = fileName,
//            encryptedFile    = encryptedFile,
//            encryptedThumb   = encryptedThumb,
//            metadataJson     = metadataJson,
//            onProgress       = { pct ->
//                // Upload: 40..99%
//                onProgress(40 + (pct * 0.6f).toInt())
//            }
//        )
//
//        encryptedFile.delete()
//
//        check(uploadResponse.status == "READY") { "Media upload not ready" }
//
//        onUploaded?.invoke(uploadResponse.mediaId)
//
//        val messageId = api.sendMessageWithMediaWS(
//            chatId           = chatId,
//            encryptedContent = encryptedCaption,
//            mediaId          = uploadResponse.mediaId,
//            replyToId        = replyToId
//        )
//
//        if (caption.isNotBlank()) e2ee.saveOutgoingPlaintext(chatId, messageId, caption)
//        mediaKeyCache.save(chatId, messageId, messageKey)
//    }


    suspend fun downloadAndDecryptMedia(
        messageId: String,
        chatId: String,
        media: MediaDto,
        senderId: String,
        context: Any? = null    // ← добавить
    ): String {
        cachedMediaPath(media.id, media.mimeType)?.let { return it }

        val messageKey = mediaKeyCache.get(chatId, messageId)
            ?: error("No mediaKey cached for message $messageId")

        println( "Downloading media ${media.id} for message $messageId")

        val encryptedBlob = api.downloadMedia(media.id, chatId)
        println( "Downloaded ${encryptedBlob.size} bytes")

        val extension  = extensionFromMime(media.mimeType)
        val cacheDir   = platformCacheDir(context)
        val encTempFile = platformTempFile(cacheDir, "enc_tmp_${media.id}.$extension")
        val plainFile   = platformTempFile(cacheDir, "media_${media.id}.$extension")

        encTempFile.writeBytes(encryptedBlob)
        println( "Written encrypted file, size=${encTempFile.length}")

        try {
            mediaCrypto.decryptFile(
                input      = encTempFile,
                output     = plainFile,
                messageKey = messageKey
            )
            println("plainFile path=${plainFile.absolutePath} exists=${plainFile.exists} size=${plainFile.length}")
            println( "Decrypted OK, plain size=${plainFile.length}")
        } catch (e: Exception) {
            println("decryptFile FAILED: ${e.message}")
            throw e
        }

        encTempFile.delete()
        return plainFile.absolutePath
    }

    suspend fun downloadAndDecryptThumb(
        messageId: String,
        chatId: String,
        media: MediaDto
    ): String? {
        if (media.thumbUrl == null) return null

        // Проверяем кэш превью
        cachedThumbPath(media.id)?.let { return it }

        val messageKey = mediaKeyCache.get(chatId, messageId) ?: return null

        val encryptedBlob = api.downloadThumb(media.id, chatId)
        val plainBytes    = mediaCrypto.decryptThumb(encryptedBlob, messageKey)

        val cacheFile = File(cacheDir, "thumb_${media.id}.jpg")
        cacheFile.writeBytes(plainBytes)

        return cacheFile.absolutePath
    }
    private fun cachedMediaPath(mediaId: String, mimeType: String): String? {
        val ext  = extensionFromMime(mimeType)
        val file = File(cacheDir, "media_${mediaId}.$ext")
        return if (file.exists() && file.length() > 0) file.absolutePath else null
    }

    private fun cachedThumbPath(mediaId: String): String? {
        // Превью всегда сохраняем как jpg
        val file = File(cacheDir, "thumb_${mediaId}.jpg")
        return if (file.exists() && file.length() > 0) file.absolutePath else null
    }
    // ---------------------------------------------------------------
    // Обновляем getMessages — обрабатываем медиасообщения
    // ---------------------------------------------------------------



    private suspend fun decryptMediaMessage(
        msg: MessageDto,
        chatId: String,
        myUserId: String
    ): DecryptedMessage? {
        val isMine = msg.senderId == myUserId
        val media  = msg.media ?: return null

        val captionText: String? = when {
            isMine -> e2ee.getOutgoingPlaintext(chatId, msg.id)
                ?.takeIf { it != "\u200B" }
            else -> {
                // Сначала проверяем кэш plaintext
                val cachedPlaintext = e2ee.getIncomingPlaintext(chatId, msg.id)
                if (cachedPlaintext != null) {
                    // Plaintext есть — но нужен ещё messageKey для медиа
                    // Проверяем mediaKeyCache
                    if (mediaKeyCache.get(chatId, msg.id) == null) {
                        // Ключа нет — придётся расшифровать снова чтобы получить ключ
                        val result = e2ee.decryptMessageWithKey(chatId, msg.encryptedContent)
                        if (result != null) {
                            mediaKeyCache.save(chatId, msg.id, result.second)
                        }
                    }
                    cachedPlaintext.takeIf { it != "\u200B" }
                } else {
                    // Первый раз — расшифровываем
                    val result = e2ee.decryptMessageWithKey(chatId, msg.encryptedContent)
                    if (result != null) {
                        val (plaintext, messageKey) = result
                        mediaKeyCache.save(chatId, msg.id, messageKey)
                        e2ee.saveIncomingPlaintext(chatId, msg.id, plaintext)
                        plaintext.takeIf { it != "\u200B" }
                    } else null
                }
            }
        }

        val cachedPath = cachedMediaPath(media.id, media.mimeType)
        val mediaState = if (cachedPath != null) MediaState.READY else MediaState.IDLE

        return DecryptedMessage(
            id                = msg.id,
            chatId            = msg.chatId,
            senderId          = msg.senderId,
            text              = captionText,
            createdAt         = msg.createdAt,
            deletedForAll     = false,
            statuses          = msg.statuses,
            editedAt          = msg.editedAt,
            replyToId         = msg.replyToId,
            forwardedFromId   = msg.forwardedFromId,
            forwardedFromName = msg.forwardedFromName,
            pinnedAt          = msg.pinnedAt,
            reactions         = msg.reactions,
            media             = media,
            mediaLocalPath    = cachedPath,
            mediaState        = mediaState
        )
    }

    // ---------------------------------------------------------------
    // Обновляем обработку WS IncomingMessage — медиасообщения
    // ---------------------------------------------------------------

    /**
     * Вызывается из ViewModel при получении IncomingMessage.
     * Если сообщение содержит медиа — кэшируем messageKey сразу при получении.
     */
    suspend fun processIncomingMessage(
        msg: MessageDto,
        chatId: String,
        myUserId: String
    ): DecryptedMessage? {
        return if (msg.media != null) {
            decryptMediaMessage(msg, chatId, myUserId)
        } else {
            // Обычная логика текстовых сообщений
            when {
                msg.deletedForAll             -> null
                msg.encryptedContent.isBlank() -> null
                msg.senderId == myUserId       -> {
                    val text = e2ee.getOutgoingPlaintext(chatId, msg.id)
                    text?.let { buildDecrypted(msg, it) }
                }
                else -> {
                    val text = e2ee.decryptMessage(chatId, msg.encryptedContent)
                    text?.also { e2ee.saveIncomingPlaintext(chatId, msg.id, it) }
                        ?.let { buildDecrypted(msg, it) }
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private fun buildDecrypted(msg: MessageDto, text: String): DecryptedMessage {
        val media      = msg.media
        val cachedPath = media?.let { cachedMediaPath(it.id, it.mimeType) }
        val mediaState = if (cachedPath != null) MediaState.READY else MediaState.IDLE

        return DecryptedMessage(
            id                = msg.id,
            chatId            = msg.chatId,
            senderId          = msg.senderId,
            text              = text,
            createdAt         = msg.createdAt,
            statuses          = msg.statuses,
            editedAt          = msg.editedAt,
            replyToId         = msg.replyToId,
            replyToSenderId   = null,
            forwardedFromId   = msg.forwardedFromId,
            forwardedFromName = msg.forwardedFromName,
            pinnedAt          = msg.pinnedAt,
            reactions         = msg.reactions,
            media             = media,
            mediaLocalPath    = cachedPath,
            mediaState        = mediaState
        )
    }

    suspend fun deleteMessage(
        chatId: String,
        messageId: String,
        forAll: Boolean,
        mediaId: String? = null     // НОВОЕ
    ) {
        api.deleteMessageWS(chatId, messageId, forAll)
        // При удалении у всех — удаляем медиафайл с сервера
        if (forAll && mediaId != null) {
            runCatching { api.deleteMedia(mediaId) }
        }
    }

    suspend fun sendMediaMessageFromUri(
        chatId: String,
        theirUserId: String,
        mediaType: String,
        mimeType: String,
        fileName: String,
        uri: Any,
        thumbBitmap: Any?,
        metadataJson: String?,
        caption: String = "",
        replyToId: String? = null,
        context: Any? = null,
        onEncryptProgress: (Int) -> Unit = {},
        onUploadProgress: (Int) -> Unit = {},
        onUploaded: ((mediaId: String, localPath: String) -> Unit)? = null
    ) {
        val theirPublicKey = api.getPublicKey(theirUserId).publicKey
        val captionText    = caption.ifBlank { "\u200B" }
        val (encryptedCaption, messageKey) = e2ee.encryptMessageWithKey(
            chatId            = chatId,
            plaintext         = captionText,
            theirPublicKeyHex = theirPublicKey
        )
        println("Sent messageKey length=${messageKey.length} value=${messageKey.take(16)}...")

        val cacheDir   = platformCacheDir(context)
        val tempOutput = platformTempFile(cacheDir, "enc_tmp_send_${System.currentTimeMillis()}")

        // expect/actual — платформенная реализация знает про Uri
        val inputFile = platformFileFromUri(context, uri)

        mediaCrypto.encryptFile(
            input      = inputFile,
            output     = tempOutput,
            messageKey = messageKey,
            onProgress = { pct -> onEncryptProgress((pct * 0.4f).toInt()) }
        )

        // encryptThumb передаём как лямбду — никакого платформенного кода в commonMain
        val encryptedThumb = encryptThumbFromBitmap(
            bitmap       = thumbBitmap,
            messageKey   = messageKey,
            encryptThumb = mediaCrypto::encryptThumb
        )

        // platformFileToJavaFile — expect/actual, возвращает java.io.File на Android
        val uploadResponse = api.uploadMediaFile(
            chatId           = chatId,
            type             = mediaType,
            mimeType         = mimeType,
            originalFileName = fileName,
            encryptedFile = tempOutput,
            encryptedThumb   = encryptedThumb,
            metadataJson     = metadataJson,
            onProgress       = { pct -> onUploadProgress(40 + (pct * 0.6f).toInt()) }
        )

        tempOutput.delete()
        check(uploadResponse.status == "READY") { "Media upload not ready" }

        val ext       = extensionFromMime(mimeType)
        val cacheFile = platformTempFile(cacheDir, "media_${uploadResponse.mediaId}.$ext")
        copyUriToFile(context, uri, cacheFile)  // expect/actual

        mediaKeyCache.save(chatId, "pending_${uploadResponse.mediaId}", messageKey)
        onUploaded?.invoke(uploadResponse.mediaId, cacheFile.absolutePath)

        val messageId = api.sendMessageWithMediaWS(
            chatId           = chatId,
            encryptedContent = encryptedCaption,
            mediaId          = uploadResponse.mediaId,
            replyToId        = replyToId
        )

        if (caption.isNotBlank()) e2ee.saveOutgoingPlaintext(chatId, messageId, caption)
        mediaKeyCache.save(chatId, messageId, messageKey)
    }
}

