package com.vladdev.shared.auth.dto

import kotlinx.serialization.Serializable

@Serializable
data class CheckUpdateResponse(
    val version: String,
    val versionCode: Int
)