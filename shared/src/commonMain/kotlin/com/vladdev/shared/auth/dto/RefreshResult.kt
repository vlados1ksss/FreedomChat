package com.vladdev.shared.auth.dto

sealed class RefreshResult {
    object Success : RefreshResult()
    object Unauthorized : RefreshResult()
    object NetworkError : RefreshResult()
}