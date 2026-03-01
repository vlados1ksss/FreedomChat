package com.vladdev.freedomchat.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vladdev.shared.auth.AuthRepository
import com.vladdev.shared.auth.dto.TransferChallengeResponse
import com.vladdev.shared.storage.IdentityKeyStorage
import com.vladdev.shared.user.ProfileRepository
import com.vladdev.shared.user.dto.UserProfileResponse
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi

@OptIn(InternalSerializationApi::class)
class ProfileViewModel(
    private val repository: ProfileRepository,
    private val identityStorage: IdentityKeyStorage
) : ViewModel() {

    @OptIn(InternalSerializationApi::class)
    var profile by mutableStateOf<UserProfileResponse?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    // Редактирование имени
    var editedName by mutableStateOf("")
        private set
    var nameError by mutableStateOf<String?>(null)
        private set
    var isEditingName by mutableStateOf(false)
        private set

    // Редактирование email
    var editedEmail by mutableStateOf("")
        private set
    var emailError by mutableStateOf<String?>(null)
        private set
    var isEditingEmail by mutableStateOf(false)
        private set

    // Смена пароля
    var showChangePassword by mutableStateOf(false)
        private set
    var currentPassword by mutableStateOf("")
        private set
    var newPassword by mutableStateOf("")
        private set
    var newPasswordConfirm by mutableStateOf("")
        private set
    var passwordError by mutableStateOf<String?>(null)
        private set
    var passwordSuccess by mutableStateOf(false)
        private set

    // Просмотр пароля — запрос текущего
    var showVerifyDialog by mutableStateOf(false)
        private set
    var verifyPassword by mutableStateOf("")
        private set
    var verifyError by mutableStateOf<String?>(null)
        private set
    var isPasswordVerified by mutableStateOf(false)
        private set

    // Удаление аккаунта
    var showDeleteDialog by mutableStateOf(false)
        private set
    var deletePassword by mutableStateOf("")
        private set
    var deleteConfirmChecked by mutableStateOf(false)
        private set
    var deleteError by mutableStateOf<String?>(null)
        private set

    var loggedOut by mutableStateOf(false)
        private set

    var transferChallenge by mutableStateOf<String?>(null)
        private set
    var transferExpiresAt by mutableStateOf<Long?>(null)
        private set
    var showTransferSheet by mutableStateOf(false)
        private set
    var transferSignKey by mutableStateOf<String?>(null)
        private set

    init { loadProfile() }

    @OptIn(InternalSerializationApi::class)
    fun loadProfile() {
        viewModelScope.launch {
            isLoading = true
            repository.getProfile()
                .onSuccess {
                    profile = it
                    editedName = it.name
                    editedEmail = it.email ?: ""
                }
            isLoading = false
        }
    }

    // region Name
    fun startEditName() { isEditingName = true; nameError = null }
    fun onNameChange(v: String) { editedName = v; nameError = null }
    @OptIn(InternalSerializationApi::class)
    fun cancelEditName() { isEditingName = false; editedName = profile?.name ?: "" }
    @OptIn(InternalSerializationApi::class)
    fun saveName() {
        if (editedName.isBlank()) { nameError = "Имя не может быть пустым"; return }
        viewModelScope.launch {
            isLoading = true
            repository.updateName(editedName)
                .onSuccess { profile = profile?.copy(name = editedName); isEditingName = false }
                .onFailure { nameError = it.message }
            isLoading = false
        }
    }
    // endregion

    // region Email
    fun startEditEmail() { isEditingEmail = true; emailError = null }
    fun onEmailChange(v: String) { editedEmail = v; emailError = null }
    @OptIn(InternalSerializationApi::class)
    fun cancelEditEmail() { isEditingEmail = false; editedEmail = profile?.email ?: "" }
    @OptIn(InternalSerializationApi::class)
    fun saveEmail() {
        if (!editedEmail.contains("@")) { emailError = "Некорректный email"; return }
        viewModelScope.launch {
            isLoading = true
            repository.updateEmail(editedEmail)
                .onSuccess { profile = profile?.copy(email = editedEmail); isEditingEmail = false }
                .onFailure { emailError = it.message }
            isLoading = false
        }
    }
    // endregion

    // region Password verify
    fun onVerifyPasswordChange(v: String) { verifyPassword = v; verifyError = null }
    fun openVerifyDialog() { showVerifyDialog = true; verifyPassword = ""; verifyError = null; isPasswordVerified = false }
    fun closeVerifyDialog() { showVerifyDialog = false }
    fun submitVerify() {
        viewModelScope.launch {
            isLoading = true
            val ok = repository.verifyPassword(verifyPassword)
            if (ok) { isPasswordVerified = true; showVerifyDialog = false; showChangePassword = true }
            else verifyError = "Неверный пароль"
            isLoading = false
        }
    }
    // endregion

    // region Change password
    fun onCurrentPasswordChange(v: String) { currentPassword = v; passwordError = null }
    fun onNewPasswordChange(v: String) { newPassword = v; passwordError = null }
    fun onNewPasswordConfirmChange(v: String) { newPasswordConfirm = v; passwordError = null }
    fun closeChangePassword() {
        showChangePassword = false
        currentPassword = ""; newPassword = ""; newPasswordConfirm = ""
        passwordError = null; passwordSuccess = false; isPasswordVerified = false
    }
    fun submitChangePassword() {
        if (newPassword.length < 6) { passwordError = "Минимум 6 символов"; return }
        if (newPassword != newPasswordConfirm) { passwordError = "Пароли не совпадают"; return }
        viewModelScope.launch {
            isLoading = true
            repository.changePassword(currentPassword, newPassword)
                .onSuccess { passwordSuccess = true }
                .onFailure { passwordError = it.message ?: "Ошибка" }
            isLoading = false
        }
    }
    // endregion

    // region Delete
    fun openDeleteDialog() { showDeleteDialog = true; deletePassword = ""; deleteConfirmChecked = false; deleteError = null }
    fun closeDeleteDialog() { showDeleteDialog = false }
    fun onDeletePasswordChange(v: String) { deletePassword = v; deleteError = null }
    fun onDeleteConfirmChecked(v: Boolean) { deleteConfirmChecked = v }
    fun submitDelete() {
        if (!deleteConfirmChecked) { deleteError = "Подтвердите удаление"; return }
        viewModelScope.launch {
            isLoading = true
            repository.deleteAccount(deletePassword)
                .onSuccess {
                    repository.logout() // очищает TokenStorage и UserIdStorage
                    loggedOut = true
                }
                .onFailure {
                    deleteError = it.message ?: "Неверный пароль"
                    isLoading = false
                }
        }
    }
    // endregion
    fun logout() {
        viewModelScope.launch {
            repository.logout()
            loggedOut = true
        }
    }

    fun openTransfer() {
        viewModelScope.launch {
            isLoading = true
            val signKey = identityStorage.getSignKey()
            println("ProfileVM: signKey=${signKey?.take(8)}")

            repository.requestTransferChallenge()
                .onSuccess { response ->
                    println("ProfileVM: challenge=${response.challenge.take(8)}")
                    transferSignKey   = signKey
                    transferChallenge = response.challenge
                    transferExpiresAt = response.expiresAt
                    showTransferSheet = true
                }
                .onFailure {
                    println("ProfileVM: transfer challenge failed: ${it.message}")
                    it.printStackTrace()
                }
            isLoading = false
        }
    }



    fun closeTransfer() {
        showTransferSheet = false
        transferChallenge = null
        transferExpiresAt = null
    }
}