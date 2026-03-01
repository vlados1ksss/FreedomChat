package com.vladdev.freedomchat.ui.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.vladdev.freedomchat.MainApplication
import com.vladdev.freedomchat.R
import java.util.concurrent.Executors

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

        Spacer(Modifier.height(12.dp))

        TextButton(
            onClick  = { vm.navigateTo(AuthScreen.ScanTransfer) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Перенести аккаунт с другого устройства")
        }
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

@Composable
fun TransferQrStep(vm: AuthViewModel) {
    val challenge  = vm.transferChallenge ?: return
    val userId     = vm.transferUserId    ?: return
    val signKey    = // получаем из identityStorage синхронно
        (LocalContext.current.applicationContext as MainApplication)
            .identityStorage.let {
                var key: String? = null
                // читаем синхронно через runBlocking — только для QR генерации
                kotlinx.coroutines.runBlocking { key = it.getSignKey() }
                key
            } ?: return

    // QR содержит: userId:challenge:signKey
    val qrData = "$userId:$challenge:$signKey"

    AuthCard(showBack = true, onBack = vm::goBack) {
        StepHeader(
            title    = "Перенос на новое устройство",
            subtitle = "Отсканируйте QR на новом устройстве"
        )

        Spacer(Modifier.height(24.dp))

        QrCodeImage(
            data     = qrData,
            modifier = Modifier
                .size(240.dp)
                .align(Alignment.CenterHorizontally)
        )

        Spacer(Modifier.height(16.dp))

        val remaining = remember(vm.transferExpiresAt) {
            vm.transferExpiresAt?.let {
                val mins = ((it - System.currentTimeMillis()) / 60000).coerceAtLeast(0)
                "Истекает через $mins мин"
            } ?: ""
        }
        Text(
            text  = remaining,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Простая генерация QR через ZXing
@Composable
fun QrCodeImage(data: String, modifier: Modifier = Modifier) {
    val bitmap = remember(data) {
        val writer = com.google.zxing.qrcode.QRCodeWriter()
        val matrix = writer.encode(data, com.google.zxing.BarcodeFormat.QR_CODE, 512, 512)
        val bmp = android.graphics.Bitmap.createBitmap(512, 512, android.graphics.Bitmap.Config.RGB_565)
        for (x in 0 until 512) for (y in 0 until 512) {
            bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
        bmp
    }
    Image(
        bitmap      = bitmap.asImageBitmap(),
        contentDescription = "QR для переноса",
        modifier    = modifier
    )
}

@Composable
fun ScanQrStep(vm: AuthViewModel) {
    var cameraPermissionGranted by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraPermissionGranted = granted
    }

    LaunchedEffect(Unit) {
        launcher.launch(android.Manifest.permission.CAMERA)
    }

    AuthCard(showBack = true, onBack = vm::goBack) {
        StepHeader(
            title    = "Сканировать QR",
            subtitle = "Наведите камеру на QR со старого устройства"
        )

        Spacer(Modifier.height(24.dp))

        if (cameraPermissionGranted) {
            CameraQrScanner(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(16.dp)),
                onScanned = { qrData -> vm.completeTransfer(qrData) }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painterResource(R.drawable.ic_camera),  // любая иконка камеры
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text  = "Требуется доступ к камере",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { launcher.launch(android.Manifest.permission.CAMERA) }) {
                        Text("Разрешить")
                    }
                }
            }
        }
    }
}

@Composable
fun CameraQrScanner(
    modifier: Modifier = Modifier,
    onScanned: (String) -> Unit
) {
    val context         = LocalContext.current
    val lifecycleOwner  = LocalLifecycleOwner.current
    val scanned         = remember { mutableStateOf(false) }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor        = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    AndroidView(
        modifier = modifier,
        factory  = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(executor) { imageProxy ->
                    if (!scanned.value) {
                        val result = decodeQrFromProxy(imageProxy)  // без reader
                        if (result != null) {
                            println("QR scanned: ${result.take(20)}...")
                            scanned.value = true
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                onScanned(result)
                            }
                        }
                    }
                    imageProxy.close()
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                } catch (e: Exception) {
                    println("Camera bind error: ${e.message}")
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun decodeQrFromProxy(imageProxy: ImageProxy): String? {
    return try {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            println("QR: mediaImage is null")
            return null
        }

        val yPlane    = mediaImage.planes[0]
        val yBuffer   = yPlane.buffer
        val yBytes    = ByteArray(yBuffer.remaining()).also { yBuffer.get(it) }
        val width     = mediaImage.width
        val height    = mediaImage.height
        val rowStride = yPlane.rowStride

        val cleanBytes = if (rowStride == width) {
            yBytes
        } else {
            ByteArray(width * height).also { clean ->
                for (row in 0 until height) {
                    System.arraycopy(yBytes, row * rowStride, clean, row * width, width)
                }
            }
        }

        val source = PlanarYUVLuminanceSource(
            cleanBytes, width, height, 0, 0, width, height, false
        )
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        val hints  = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER       to true
        )

        // Новый reader на каждый кадр — потокобезопасно
        MultiFormatReader().decode(bitmap, hints).text

    } catch (e: NotFoundException) {
        null
    } catch (e: Exception) {
        println("QR decode error: ${e.javaClass.simpleName}: ${e.message}")
        null
    }
}
private fun tryDecodeQr(imageProxy: androidx.camera.core.ImageProxy): String? {
    return try {
        val buffer = imageProxy.planes[0].buffer
        val bytes  = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val source = com.google.zxing.PlanarYUVLuminanceSource(
            bytes,
            imageProxy.width, imageProxy.height,
            0, 0, imageProxy.width, imageProxy.height, false
        )
        val binarizer = com.google.zxing.common.HybridBinarizer(source)
        com.google.zxing.MultiFormatReader().decode(com.google.zxing.BinaryBitmap(binarizer))?.text
    } catch (e: Exception) { null }
}

