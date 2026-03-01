package com.vladdev.freedomchat.ui.profile

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vladdev.freedomchat.R
import com.vladdev.freedomchat.ui.auth.QrCodeImage
import com.vladdev.freedomchat.ui.chats.UserAvatar
import com.vladdev.freedomchat.ui.theme.FreedomChatTheme
import kotlinx.serialization.InternalSerializationApi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, InternalSerializationApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onLogout: () -> Unit,
    onBack: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(viewModel.loggedOut) {
        if (viewModel.loggedOut) onLogout()
    }

    FreedomChatTheme {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Профиль") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.arrow_back), "Назад")
                        }
                    }
                )
            }
        ) { padding ->

            if (viewModel.isLoading && viewModel.profile == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ContainedLoadingIndicator()
                }
                return@Scaffold
            }

            val profile = viewModel.profile ?: return@Scaffold

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                // Avatar + status badge
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box {
                            Surface(
                                modifier = Modifier.size(88.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = profile.name.take(1).uppercase(),
                                        style = MaterialTheme.typography.headlineLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            if (profile.status != "standard") {
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(2.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = when (profile.status) {
                                        "admin"    -> MaterialTheme.colorScheme.error
                                        "verified" -> MaterialTheme.colorScheme.primary
                                        "service"  -> MaterialTheme.colorScheme.tertiary
                                        else       -> Color.Transparent
                                    }
                                ) {
                                    Text(
                                        text = when (profile.status) {
                                            "admin"    -> "admin"
                                            "verified" -> "✓"
                                            "service"  -> "bot"
                                            else       -> ""
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = "Аккаунт с ${
                                SimpleDateFormat("d MMMM yyyy", Locale("ru"))
                                    .format(Date(profile.createdAt))
                            }",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // --- Карточка с данными профиля ---
                ProfileCard {

                    // Имя
                    if (viewModel.isEditingName) {
                        EditableRow(
                            value = viewModel.editedName,
                            onValueChange = viewModel::onNameChange,
                            error = viewModel.nameError,
                            label = "Отображаемое имя",
                            onSave = viewModel::saveName,
                            onCancel = viewModel::cancelEditName
                        )
                    } else {
                        ProfileRow(
                            icon = painterResource(R.drawable.ic_profile),
                            label = "Имя",
                            value = profile.name,
                            actionIcon = painterResource(R.drawable.edit),
                            onAction = viewModel::startEditName
                        )
                    }

                    ProfileDivider()

                    // Username
                    ProfileRow(
                        icon = painterResource(R.drawable.ic_username),
                        label = "Имя пользователя",
                        value = "@${profile.username}",
                        actionIcon = painterResource(R.drawable.content_copy),
                        onAction = {
                            clipboardManager.setText(AnnotatedString("@${profile.username}"))
                            Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                        }
                    )

                    ProfileDivider()

                    // Email
                    if (viewModel.isEditingEmail) {
                        EditableRow(
                            value = viewModel.editedEmail,
                            onValueChange = viewModel::onEmailChange,
                            error = viewModel.emailError,
                            label = "Email",
                            keyboardType = KeyboardType.Email,
                            onSave = viewModel::saveEmail,
                            onCancel = viewModel::cancelEditEmail
                        )
                    } else {
                        ProfileRow(
                            icon = painterResource(R.drawable.email),
                            label = "Почта",
                            value = profile.email ?: "Не привязана",
                            valueAlpha = if (profile.email == null) 0.45f else 1f,
                            actionIcon = painterResource(R.drawable.edit),
                            onAction = viewModel::startEditEmail
                        )
                    }

                    ProfileDivider()

                    // Пароль
                    ProfileRow(
                        icon = painterResource(R.drawable.lock),
                        label = "Пароль",
                        value = "••••••••",
                        actionIcon = painterResource(R.drawable.edit),
                        onAction = viewModel::openVerifyDialog
                    )
                }

                // --- Кнопка выйти ---
                OutlinedButton(
                    onClick = viewModel::logout,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.secondary
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                ) {
                    Icon(painterResource(R.drawable.logout), null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Выйти из аккаунта")
                }

                OutlinedButton(
                    onClick  = viewModel::openTransfer,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(16.dp)
                ) {
                    Icon(painterResource(R.drawable.qr_code), null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Перенести на другое устройство")
                }

                // --- Кнопка удалить ---
                TextButton(
                    onClick = viewModel::openDeleteDialog,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                ) {
                    Icon(painterResource(R.drawable.delete_forever), null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Удалить аккаунт", style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // --- Диалог верификации пароля (перед сменой) ---
    if (viewModel.showVerifyDialog) {
        PasswordDialog(
            title = "Введите текущий пароль",
            subtitle = "Для изменения пароля подтвердите личность",
            confirmText = "Подтвердить",
            password = viewModel.verifyPassword,
            error = viewModel.verifyError,
            loading = viewModel.isLoading,
            onPasswordChange = viewModel::onVerifyPasswordChange,
            onConfirm = viewModel::submitVerify,
            onDismiss = viewModel::closeVerifyDialog
        )
    }

    // --- Диалог смены пароля ---
    if (viewModel.showChangePassword) {
        ChangePasswordDialog(viewModel)
    }

    // --- Диалог удаления аккаунта ---
    if (viewModel.showDeleteDialog) {
        DeleteAccountDialog(viewModel)
    }

    // ProfileScreen.kt — убираем сломанный remember блок
    if (viewModel.showTransferSheet) {
        TransferBottomSheet(
            userId    = viewModel.profile?.userId ?: "",
            challenge = viewModel.transferChallenge ?: "",
            signKey   = viewModel.transferSignKey ?: "",   // ← берём из viewModel
            expiresAt = viewModel.transferExpiresAt,
            onDismiss = viewModel::closeTransfer
        )
    }
}

// ─── Диалог смены пароля ──────────────────────────────────────────────────────

@Composable
private fun ChangePasswordDialog(vm: ProfileViewModel) {
    FreedomChatTheme {
    AlertDialog(
        onDismissRequest = vm::closeChangePassword,
        title = { Text("Изменить пароль") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (vm.passwordSuccess) {
                    Text(
                        "Пароль успешно изменён",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    DialogPasswordField(
                        value = vm.currentPassword,
                        onValueChange = vm::onCurrentPasswordChange,
                        label = "Текущий пароль"
                    )
                    DialogPasswordField(
                        value = vm.newPassword,
                        onValueChange = vm::onNewPasswordChange,
                        label = "Новый пароль"
                    )
                    DialogPasswordField(
                        value = vm.newPasswordConfirm,
                        onValueChange = vm::onNewPasswordConfirmChange,
                        label = "Повторите новый пароль",
                        error = vm.passwordError
                    )
                }
            }
        },
        confirmButton = {
            if (vm.passwordSuccess) {
                TextButton(onClick = vm::closeChangePassword) { Text("Закрыть") }
            } else {
                TextButton(onClick = vm::submitChangePassword, enabled = !vm.isLoading) {
                    Text("Сохранить")
                }
            }
        },
        dismissButton = {
            if (!vm.passwordSuccess) {
                TextButton(onClick = vm::closeChangePassword) { Text("Отмена") }
            }
        }
    )
}}

// ─── Диалог удаления аккаунта ─────────────────────────────────────────────────

@Composable
private fun DeleteAccountDialog(vm: ProfileViewModel) {
    FreedomChatTheme {
    AlertDialog(
        onDismissRequest = vm::closeDeleteDialog,
        title = { Text("Удалить аккаунт") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Это действие необратимо. Все ваши данные будут удалены.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DialogPasswordField(
                    value = vm.deletePassword,
                    onValueChange = vm::onDeletePasswordChange,
                    label = "Введите пароль",
                    error = vm.deleteError
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        vm.onDeleteConfirmChecked(!vm.deleteConfirmChecked)
                    }
                ) {
                    Checkbox(
                        checked = vm.deleteConfirmChecked,
                        onCheckedChange = vm::onDeleteConfirmChecked,
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.error
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Я подтверждаю удаление аккаунта",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = vm::submitDelete,
                enabled = vm.deleteConfirmChecked && !vm.isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) { Text("Удалить") }
        },
        dismissButton = {
            TextButton(onClick = vm::closeDeleteDialog) { Text("Отмена") }
        }
    )
}}

// ─── Переиспользуемые компоненты ──────────────────────────────────────────────

@Composable
private fun ProfileCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            content()
        }
    }
}

@Composable
private fun ProfileRow(
    icon: Painter,
    label: String,
    value: String,
    valueAlpha: Float = 1f,
    actionIcon: Painter,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = valueAlpha))
        }
        IconButton(onClick = onAction, modifier = Modifier.size(36.dp)) {
            Icon(actionIcon, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun EditableRow(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            isError = error != null,
            supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.align(Alignment.End)) {
            TextButton(onClick = onCancel) { Text("Отмена") }
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onSave) { Text("Сохранить") }
        }
    }
}

@Composable
private fun ProfileDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun PasswordDialog(
    title: String,
    subtitle: String,
    confirmText: String,
    password: String,
    error: String?,
    loading: Boolean,
    onPasswordChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    FreedomChatTheme {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                DialogPasswordField(password, onPasswordChange, "Пароль", error)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !loading) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}}

@Composable
private fun DialogPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferBottomSheet(
    userId: String,
    challenge: String,
    signKey: String,
    expiresAt: Long?,
    onDismiss: () -> Unit
) {
    val qrData = "$userId:$challenge:$signKey"

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text  = "Перенос аккаунта",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text  = "Отсканируйте QR на новом устройстве",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))

            QrCodeImage(
                data     = qrData,
                modifier = Modifier.size(240.dp)
            )

            Spacer(Modifier.height(16.dp))

            expiresAt?.let {
                val remainingMs = it - System.currentTimeMillis()
                val mins = (remainingMs / 60000).coerceAtLeast(0)
                Text(
                    text  = "QR действителен $mins мин",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (mins < 2)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Временно под QR кодом
            Text(
                text = "QR data length: ${qrData.length}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "userId: ${userId.take(8)}...",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "challenge: ${challenge.take(8)}...",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "signKey: ${signKey?.take(20)}...",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(16.dp))

            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    }
}