package com.vladdev.freedomchat.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vladdev.shared.auth.AuthRepository
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: AuthRepository
) : ViewModel() {

    var username by mutableStateOf("")
        private set

    var password by mutableStateOf("")
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var isLoggedIn by mutableStateOf(false)
        private set

    init {
        checkAuth()
    }

    private fun checkAuth() {
        viewModelScope.launch {
            isLoggedIn = repository.isLoggedIn()
        }
    }

    fun onUsernameChange(value: String) {
        username = value
    }

    fun onPasswordChange(value: String) {
        password = value
    }

    fun register() {
        viewModelScope.launch {
            try {
                isLoading = true
                repository.register(username, password)
                isLoggedIn = true
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                isLoading = false
            }
        }
    }

    fun login() {
        viewModelScope.launch {
            try {
                isLoading = true
                repository.login(username, password)
                isLoggedIn = true
            } catch (e: Exception) {
                errorMessage = "Invalid credentials"
            } finally {
                isLoading = false
            }
        }
    }
}
