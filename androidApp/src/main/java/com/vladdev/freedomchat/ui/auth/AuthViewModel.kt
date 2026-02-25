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

    // --- Navigation state ---
    var screen by mutableStateOf(AuthScreen.Welcome)
        private set

    // --- Login fields ---
    var loginUsername by mutableStateOf("")
        private set
    var loginPassword by mutableStateOf("")
        private set
    var loginError by mutableStateOf<String?>(null)
        private set

    // --- Register fields ---
    var regUsername by mutableStateOf("")
        private set
    var regPassword by mutableStateOf("")
        private set
    var regPasswordConfirm by mutableStateOf("")
        private set
    var regEmail by mutableStateOf("")
        private set
    var regName by mutableStateOf("")
        private set

    var regUsernameError by mutableStateOf<String?>(null)
        private set
    var regPasswordError by mutableStateOf<String?>(null)
        private set
    var regEmailError by mutableStateOf<String?>(null)
        private set
    var regNameError by mutableStateOf<String?>(null)
        private set

    // --- Shared ---
    var isLoading by mutableStateOf(false)
        private set
    var isLoggedIn by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            isLoggedIn = repository.isLoggedIn()
        }
    }

    // region Navigation
    fun navigateTo(target: AuthScreen) {
        screen = target
    }

    fun goBack() {
        screen = when (screen) {
            AuthScreen.Login -> AuthScreen.Welcome
            AuthScreen.Reg1 -> AuthScreen.Welcome
            AuthScreen.Reg2 -> AuthScreen.Reg1
            AuthScreen.Reg3 -> AuthScreen.Reg2
            else -> AuthScreen.Welcome
        }
    }
    // endregion

    // region Login
    fun onLoginUsernameChange(v: String) { loginUsername = v; loginError = null }
    fun onLoginPasswordChange(v: String) { loginPassword = v; loginError = null }

    fun login() {
        viewModelScope.launch {
            isLoading = true
            repository.login(loginUsername, loginPassword)
                .onSuccess { isLoggedIn = true }
                .onFailure { loginError = it.message ?: "Неверный логин или пароль" }
            isLoading = false
        }
    }
    // endregion

    // region Register
    fun onRegUsernameChange(v: String) { regUsername = v; regUsernameError = null }
    fun onRegPasswordChange(v: String) { regPassword = v; regPasswordError = null }
    fun onRegPasswordConfirmChange(v: String) { regPasswordConfirm = v; regPasswordError = null }
    fun onRegEmailChange(v: String) { regEmail = v; regEmailError = null }
    fun onRegNameChange(v: String) { regName = v; regNameError = null }

    fun validateReg1(): Boolean {
        var ok = true
        if (regUsername.length < 4) {
            regUsernameError = "Минимум 4 символа"; ok = false
        }
        if (regPassword.length < 6) {
            regPasswordError = "Минимум 6 символов"; ok = false
        } else if (regPassword != regPasswordConfirm) {
            regPasswordError = "Пароли не совпадают"; ok = false
        }
        return ok
    }

    fun validateReg2(): Boolean {
        if (regEmail.isNotBlank() && !regEmail.contains("@")) {
            regEmailError = "Некорректный email"; return false
        }
        return true
    }

    fun finishRegister() {
        viewModelScope.launch {
            isLoading = true
            val name = regName.ifBlank { regUsername }
            repository.register(regUsername, name, regEmail, regPassword)
                .onSuccess { isLoggedIn = true }
                .onFailure { regUsernameError = it.message ?: "Ошибка регистрации" }
            isLoading = false
        }
    }
    // endregion
}

enum class AuthScreen { Welcome, Login, Reg1, Reg2, Reg3 }
