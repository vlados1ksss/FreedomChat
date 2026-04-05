package com.vladdev.shared.crypto

interface MediaKeyCache {
    fun save(chatId: String, messageId: String, messageKeyHex: String)
    fun get(chatId: String, messageId: String): String?
    fun delete(chatId: String, messageId: String)
}