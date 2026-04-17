package com.vladdev.freedomchat.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vladdev.shared.auth.AuthRepository
import com.vladdev.shared.auth.LoginResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    var registrationError by mutableStateOf<String?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isLoggedIn by mutableStateOf(false)
        private set
    var usernameStatus by mutableStateOf<UsernameStatus>(UsernameStatus.None)
        private set

    private var checkUsernameJob: Job? = null
    init {
        viewModelScope.launch { isLoggedIn = repository.isLoggedIn() }
    }

    //Reset
    var resetUsername by mutableStateOf("")
        private set
    var resetEmail by mutableStateOf("")
        private set
    var resetCode by mutableStateOf("")
        private set
    var resetNewPassword by mutableStateOf("")
        private set
    var resetNewPasswordConfirm by mutableStateOf("")
        private set
    var resetError by mutableStateOf<String?>(null)
        private set
    var resetSuccessMessage by mutableStateOf<String?>(null)
        private set


    fun navigateTo(target: AuthScreen) { screen = target }

    private fun mapServerError(e: Throwable): String {
        val message = e.message ?: ""
        return when {
            message.contains("Invalid credentials", ignoreCase = true) ->
                "Неверное имя пользователя или пароль"

            message.contains("Username or email already exists", ignoreCase = true) ->
                "Это имя пользователя или почта уже заняты"

            message.contains("Failed to send email", ignoreCase = true) ->
                "Ошибка отправки письма. Попробуйте позже"

            message.contains("Invalid or expired code", ignoreCase = true) ->
                "Неверный или просроченный код"

            // Уточняем сетевые ошибки, чтобы не путать с ошибками логики
            message.contains("ConnectException") || message.contains("Unable to resolve host") ->
                "Сервер недоступен. Проверьте соединение."

            message.contains("timeout", ignoreCase = true) ->
                "Превышено время ожидания ответа от сервера."

            else -> "Произошла ошибка: ${e.localizedMessage ?: "неизвестная ошибка"}"
        }
    }
    private val usernameRegex = "^[a-zA-Z][a-zA-Z0-9._]{3,49}$".toRegex()
    fun onLoginUsernameChange(v: String) { loginUsername = v.trim(); loginError = null }
    fun onLoginPasswordChange(v: String) { loginPassword = v; loginError = null }

    fun login() {
        if (loginUsername.isBlank() || loginPassword.isBlank()) {
            loginError = "Заполните все поля"
            return
        }

        viewModelScope.launch(Dispatchers.IO) { // Работаем в IO потоке
            isLoading = true
            try {
                repository.login(loginUsername, loginPassword)
                    .onSuccess { withContext(Dispatchers.Main) { isLoggedIn = true } }
                    .onFailure { e -> withContext(Dispatchers.Main) { loginError = mapServerError(e) } }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { loginError = mapServerError(e) }
            } finally {
                withContext(Dispatchers.Main) { isLoading = false }
            }
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
    fun onRegUsernameChange(v: String) {
        val input = v.replace("@", "").trim()
        regUsername = input
        regUsernameError = null // Сбрасываем жесткую ошибку

        // 1. Первичная валидация (локально)
        if (input.isEmpty()) {
            usernameStatus = UsernameStatus.None
            checkUsernameJob?.cancel()
            return
        }

        if (input[0].isDigit() || input[0] == '.' || input[0] == '_') {
            usernameStatus = UsernameStatus.Error("Должно начинаться с буквы")
            return
        }

        if (!input.matches("^[a-zA-Z0-9._]*$".toRegex())) {
            usernameStatus = UsernameStatus.Error("Недопустимые символы")
            return
        }

        if (input.length < 4) {
            usernameStatus = UsernameStatus.Error("Минимум 4 символа")
            return
        }

        // 2. Если локально всё ок — запускаем серверную проверку с задержкой
        checkUsernameJob?.cancel()
        usernameStatus = UsernameStatus.Loading

        checkUsernameJob = viewModelScope.launch {
            delay(400) // Ждем 400мс после последнего ввода
            repository.isUsernameAvailable(input)
                .onSuccess { available ->
                    usernameStatus = if (available) UsernameStatus.Available
                    else UsernameStatus.Taken
                }
                .onFailure {
                    usernameStatus = UsernameStatus.None // Ошибка сети — просто молчим
                }
        }
    }
    fun onRegPasswordChange(v: String) { regPassword = v; regPasswordError = null }
    fun onRegPasswordConfirmChange(v: String) { regPasswordConfirm = v; regPasswordError = null }
    fun onRegEmailChange(v: String) { regEmail = v; regEmailError = null; registrationError = null }
    fun onRegNameChange(v: String) {
        regName = v
        regNameError = null
        registrationError = null
    }

    fun validateReg1(): Boolean {
        var isValid = true
        if (!regUsername.matches(usernameRegex)) {
            regUsernameError = "Некорректный формат (начните с буквы, минимум 4 симв.)"
            isValid = false
        }
        if (regPassword.length < 6) {
            regPasswordError = "Пароль слишком короткий (мин. 6)"
            isValid = false
        } else if (regPassword != regPasswordConfirm) {
            regPasswordError = "Пароли не совпадают"
            isValid = false
        }
        return isValid
    }

    fun validateReg2(): Boolean {
        if (regEmail.isNotBlank() && !regEmail.contains("@")) {
            regEmailError = "Некорректный email"; return false
        }
        return true
    }

    fun finishRegister() {
        viewModelScope.launch(Dispatchers.IO) {
            isLoading = true
            registrationError = null // Очищаем перед новой попыткой

            try {
                val name = regName.ifBlank { regUsername }
                repository.register(regUsername, name, regEmail, regPassword)
                    .onSuccess {
                        withContext(Dispatchers.Main) { isLoggedIn = true }
                    }
                    .onFailure { e ->
                        withContext(Dispatchers.Main) {
                            // Теперь записываем ошибку в общее поле для баннера
                            registrationError = mapServerError(e)
                        }
                    }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    registrationError = mapServerError(e)
                }
            } finally {
                withContext(Dispatchers.Main) { isLoading = false }
            }
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
    sealed class UsernameStatus {
        object None : UsernameStatus()
        object Loading : UsernameStatus()
        object Available : UsernameStatus()
        object Taken : UsernameStatus()
        data class Error(val message: String) : UsernameStatus()
    }

    //Reset password
    fun onResetUsernameChange(v: String) { resetUsername = v.trim(); resetError = null }
    fun onResetEmailChange(v: String) { resetEmail = v.trim(); resetError = null }
    fun onResetCodeChange(v: String) { if (v.length <= 6) resetCode = v; resetError = null }
    fun onResetNewPasswordChange(v: String) { resetNewPassword = v; resetError = null }
    fun onResetNewPasswordConfirmChange(v: String) { resetNewPasswordConfirm = v; resetError = null }

    fun requestResetCode() {
        if (resetUsername.isBlank() || resetEmail.isBlank()) {
            resetError = "Заполните все поля"; return
        }
        viewModelScope.launch {
            isLoading = true
            repository.requestPasswordReset(resetUsername, resetEmail)
                .onSuccess { navigateTo(AuthScreen.ResetVerify) }
                .onFailure { resetError = mapServerError(it) }
            isLoading = false
        }
    }

    fun verifyResetCode() {
        if (resetCode.length < 6) {
            resetError = "Введите 6-значный код"; return
        }
        viewModelScope.launch {
            isLoading = true
            repository.verifyResetCode(resetUsername, resetEmail, resetCode)
                .onSuccess { isValid ->
                    if (isValid) navigateTo(AuthScreen.ResetConfirm)
                    else resetError = "Неверный код"
                }
                .onFailure { resetError = mapServerError(it) }
            isLoading = false
        }
    }

    fun finalizeReset() {
        if (resetNewPassword.length < 6) {
            resetError = "Пароль слишком короткий"; return
        }
        if (resetNewPassword != resetNewPasswordConfirm) {
            resetError = "Пароли не совпадают"; return
        }

        viewModelScope.launch {
            isLoading = true
            repository.completePasswordReset(resetUsername, resetEmail, resetCode, resetNewPassword)
                .onSuccess {
                    resetSuccessMessage = "Пароль успешно изменен"
                    // Очищаем поля и идем на логин
                    resetUsername = ""; resetEmail = ""; resetCode = ""
                    resetNewPassword = ""; resetNewPasswordConfirm = ""
                    navigateTo(AuthScreen.Login)
                }
                .onFailure { resetError = mapServerError(it) }
            isLoading = false
        }
    }

}

enum class AuthScreen {
    Welcome, Login, ScanTransfer, Reg1, Reg2, Reg3,
    ResetRequest, ResetVerify, ResetConfirm
}
