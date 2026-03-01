package com.vladdev.freedomchat.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vladdev.shared.storage.IdentityKeyStorage
import com.vladdev.shared.user.ProfileRepository

class ProfileViewModelFactory(
    private val repository: ProfileRepository,
    private val identityStorage: IdentityKeyStorage
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ProfileViewModel(repository, identityStorage) as T
    }
}