package com.vladdev.shared.chats

import com.vladdev.shared.chats.dto.MessageDto
import com.vladdev.shared.chats.dto.MessageStatus
import kotlinx.serialization.InternalSerializationApi

// com/vladdev/shared/chats/WsEvents.kt

sealed class WsIncomingEvent

data class IncomingMessage @OptIn(InternalSerializationApi::class) constructor(val message: MessageDto) : WsIncomingEvent()
data class IncomingStatus(val messageId: String, val userId: String, val status: MessageStatus) : WsIncomingEvent()
data class IncomingDelete(val messageId: String, val deleteForAll: Boolean) : WsIncomingEvent()