package com.vladdev.shared.auth.dto

import kotlinx.serialization.Serializable

@Serializable
data class CheckUsernameResponse(val available: Boolean)