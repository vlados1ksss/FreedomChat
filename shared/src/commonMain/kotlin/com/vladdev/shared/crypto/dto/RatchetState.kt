package com.vladdev.shared.crypto.dto

import kotlinx.serialization.Serializable

@Serializable
data class RatchetState(
    val theirPublicKey: String,
    val rootKey: String,
    val sendChainKey: String,      // только для шифрования (Alice → Bob)
    val receiveChainKey: String,   // только для расшифровки (Bob → Alice)
    val sendIndex: Int = 0,
    val receiveIndex: Int = 0
)