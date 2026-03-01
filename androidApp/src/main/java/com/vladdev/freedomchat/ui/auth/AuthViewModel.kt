package com.vladdev.freedomchat.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vladdev.shared.auth.AuthRepository
import com.vladdev.shared.auth.LoginResult
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi

@OptIn(InternalSerializationApi::class)
class AuthViewModel(
    private val repository: AuthRepository
) : ViewModel() {

    var screen by mutableStateOf(AuthScreen.Welcome)
        private set

    // Login
    var loginUsername by mutableStateOf("")
        private set
    var loginPassword by mutableStateOf("")
        private set
    var loginError by mutableStateOf<String?>(null)
        private set

    // Transfer
    var transferChallenge by mutableStateOf<String?>(null)
        private set
    var transferExpiresAt by mutableStateOf<Long?>(null)
        private set
    var transferUserId by mutableStateOf<String?>(null)
        private set

    // Register
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

    var isLoading by mutableStateOf(false)
        private set
    var isLoggedIn by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch { isLoggedIn = repository.isLoggedIn() }
    }

    fun navigateTo(target: AuthScreen) { screen = target }


    fun onLoginUsernameChange(v: String) { loginUsername = v; loginError = null }
    fun onLoginPasswordChange(v: String) { loginPassword = v; loginError = null }

    fun login() {
        viewModelScope.launch {
            isLoading = true
            repository.login(loginUsername, loginPassword)
                .onSuccess { result ->
                    when (result) {
                        is LoginResult.SameDevice -> isLoggedIn = true
                        is LoginResult.NewDevice  -> isLoggedIn = true
                        // Новое устройство просто логинится с новым keypair —
                        // история недоступна, это ожидаемо для E2EE
                    }
                }
                .onFailure { loginError = it.message ?: "Неверный логин или пароль" }
            isLoading = false
        }
    }

    // Завершить перенос на НОВОМ устройстве после сканирования QR
    fun completeTransfer(scannedData: String) {
        viewModelScope.launch {
            isLoading = true
            println("completeTransfer raw length: ${scannedData.length}")
            runCatching {
                // split с limit=3 — третья часть содержит всё остальное включая возможные ":"
                val parts = scannedData.split(":", limit = 3)
                println("completeTransfer parts: ${parts.size}")
                require(parts.size == 3) { "Invalid QR format, got ${parts.size} parts" }

                val userId     = parts[0]
                val challenge  = parts[1]
                val oldSignKey = parts[2]

                println("userId=${userId.take(8)} challenge=${challenge.take(8)} signKey=${oldSignKey.take(8)}")

                repository.completeTransfer(userId, challenge, oldSignKey)
                    .onSuccess {
                        println("Transfer complete, isLoggedIn=true")
                        isLoggedIn = true
                    }
                    .onFailure {
                        println("Transfer failed: ${it.message}")
                        loginError = it.message ?: "Ошибка переноса"
                    }
            }.onFailure {
                println("completeTransfer exception: ${it.message}")
                loginError = it.message ?: "Неверный QR код"
            }
            isLoading = false
        }
    }

    // Register
    fun onRegUsernameChange(v: String) { regUsername = v; regUsernameError = null }
    fun onRegPasswordChange(v: String) { regPassword = v; regPasswordError = null }
    fun onRegPasswordConfirmChange(v: String) { regPasswordConfirm = v; regPasswordError = null }
    fun onRegEmailChange(v: String) { regEmail = v; regEmailError = null }
    fun onRegNameChange(v: String) { regName = v; regNameError = null }

    fun validateReg1(): Boolean {
        var ok = true
        if (regUsername.length < 4) { regUsernameError = "Минимум 4 символа"; ok = false }
        if (regPassword.length < 6) { regPasswordError = "Минимум 6 символов"; ok = false }
        else if (regPassword != regPasswordConfirm) { regPasswordError = "Пароли не совпадают"; ok = false }
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

    // AuthViewModel — goBack для нового экрана
    fun goBack() {
        screen = when (screen) {
            AuthScreen.Login        -> AuthScreen.Welcome
            AuthScreen.ScanTransfer -> AuthScreen.Welcome
            AuthScreen.Reg1         -> AuthScreen.Welcome
            AuthScreen.Reg2         -> AuthScreen.Reg1
            AuthScreen.Reg3         -> AuthScreen.Reg2
            else                    -> AuthScreen.Welcome
        }
    }
}

enum class AuthScreen { Welcome, Login, ScanTransfer, Reg1, Reg2, Reg3 }
