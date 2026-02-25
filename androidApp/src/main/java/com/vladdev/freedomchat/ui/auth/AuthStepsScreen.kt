package com.vladdev.freedomchat.ui.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vladdev.freedomchat.R

// ─── Welcome ────────────────────────────────────────────────────────────────

@Composable
fun WelcomeStep(vm: AuthViewModel) {
    AuthCard(showBack = false, onBack = {}) {

        Spacer(Modifier.height(8.dp))

        Icon(
            painterResource(R.drawable.forum),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "FreedomChat",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Общайтесь свободно",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(36.dp))

        Button(
            onClick = { vm.navigateTo(AuthScreen.Reg1) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) { Text("Создать аккаунт") }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = { vm.navigateTo(AuthScreen.Login) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) { Text("Войти") }
    }
}

// ─── Login ───────────────────────────────────────────────────────────────────

@Composable
fun LoginStep(vm: AuthViewModel) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AuthCard(showBack = true, onBack = vm::goBack) {

        StepHeader(
            title = "Добро пожаловать",
            subtitle = "Введите данные для входа"
        )

        AuthTextField(
            value = vm.loginUsername,
            onValueChange = vm::onLoginUsernameChange,
            label = "Имя пользователя",
            error = if (vm.loginError != null) vm.loginError else null,
            modifier = Modifier.focusRequester(focusRequester)
        )

        Spacer(Modifier.height(12.dp))

        AuthTextField(
            value = vm.loginPassword,
            onValueChange = vm::onLoginPasswordChange,
            label = "Пароль",
            isPassword = true,
            error = vm.loginError // показываем ошибку и под паролем
        )

        Spacer(Modifier.height(24.dp))

        PrimaryButton(
            text = "Войти",
            loading = vm.isLoading,
            onClick = vm::login
        )
    }
}

// ─── Reg step 1: username + password ─────────────────────────────────────────

@Composable
fun Reg1Step(vm: AuthViewModel) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AuthCard(showBack = true, onBack = vm::goBack) {

        StepHeader(
            title = "Создать аккаунт",
            subtitle = "Шаг 1 из 3  •  Данные для входа"
        )

        AuthTextField(
            value = vm.regUsername,
            onValueChange = vm::onRegUsernameChange,
            label = "Имя пользователя (@username)",
            error = vm.regUsernameError,
            modifier = Modifier.focusRequester(focusRequester)
        )

        Spacer(Modifier.height(12.dp))

        AuthTextField(
            value = vm.regPassword,
            onValueChange = vm::onRegPasswordChange,
            label = "Пароль",
            isPassword = true,
            error = vm.regPasswordError
        )

        Spacer(Modifier.height(12.dp))

        AuthTextField(
            value = vm.regPasswordConfirm,
            onValueChange = vm::onRegPasswordConfirmChange,
            label = "Повторите пароль",
            isPassword = true,
            error = if (vm.regPasswordError != null && vm.regPassword != vm.regPasswordConfirm)
                vm.regPasswordError else null
        )

        Spacer(Modifier.height(24.dp))

        PrimaryButton(
            text = "Далее →",
            loading = false,
            onClick = { if (vm.validateReg1()) vm.navigateTo(AuthScreen.Reg2) }
        )
    }
}

// ─── Reg step 2: email (optional) ────────────────────────────────────────────

@Composable
fun Reg2Step(vm: AuthViewModel) {
    AuthCard(showBack = true, onBack = vm::goBack) {

        StepHeader(
            title = "Привязка почты",
            subtitle = "Шаг 2 из 3  •  Необязательно"
        )

        AuthTextField(
            value = vm.regEmail,
            onValueChange = vm::onRegEmailChange,
            label = "Email",
            keyboardType = KeyboardType.Email,
            error = vm.regEmailError
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Почта поможет восстановить доступ к аккаунту",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        PrimaryButton(
            text = "Далее →",
            loading = false,
            onClick = { if (vm.validateReg2()) vm.navigateTo(AuthScreen.Reg3) }
        )

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = { vm.navigateTo(AuthScreen.Reg3) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) { Text("Пропустить") }
    }
}

// ─── Reg step 3: display name ─────────────────────────────────────────────────

@Composable
fun Reg3Step(vm: AuthViewModel) {
    AuthCard(showBack = true, onBack = vm::goBack) {

        StepHeader(
            title = "Как вас зовут?",
            subtitle = "Шаг 3 из 3  •  Отображаемое имя"
        )

        AuthTextField(
            value = vm.regName,
            onValueChange = vm::onRegNameChange,
            label = "Имя (можно изменить позже)",
            placeholder = vm.regUsername,
            error = vm.regNameError
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Это имя увидят другие пользователи",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        PrimaryButton(
            text = "Начать общение →",
            loading = vm.isLoading,
            onClick = vm::finishRegister
        )
    }
}