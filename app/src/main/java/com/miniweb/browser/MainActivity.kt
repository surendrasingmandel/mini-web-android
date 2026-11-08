package com.miniweb.browser

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.miniweb.browser.data.BrowserDatabase
import com.miniweb.browser.data.BrowserPreferences
import com.miniweb.browser.ui.BrowserScreen
import com.miniweb.browser.ui.theme.MiniWebTheme
import kotlinx.coroutines.delay

class MainActivity : FragmentActivity() {
    private lateinit var preferences: BrowserPreferences
    private lateinit var appLockStore: AppLockStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9001)
        }
        enableEdgeToEdge()
        preferences = BrowserPreferences(applicationContext)
        appLockStore = AppLockStore(applicationContext)
        val database = BrowserDatabase.get(applicationContext)

        setContent {
            val settings by preferences.settings.collectAsState(
                initial = com.miniweb.browser.data.BrowserSettings()
            )
            var splash by remember { mutableStateOf(true) }
            var unlocked by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf("") }
            var changePinMode by remember { mutableStateOf(false) }
            var currentPin by remember { mutableStateOf("") }
            var newPin by remember { mutableStateOf("") }
            var confirmPin by remember { mutableStateOf("") }
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) unlocked = false
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            LaunchedEffect(Unit) {
                delay(1200)
                splash = false
            }

            MiniWebTheme(theme = settings.theme, accent = settings.accent) {
                when {
                    splash -> Box(
                        Modifier.fillMaxSize().background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(R.drawable.splash_branding),
                            contentDescription = "Mini Web Splash Screen",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }

                    !unlocked -> AppLockScreen(
                        error = error,
                        onUnlock = { pin ->
                            if (appLockStore.verifyPin(pin)) {
                                unlocked = true
                                error = ""
                                currentPin = ""
                            } else {
                                error = "Incorrect PIN or password"
                            }
                        },
                        onBiometric = { showBiometricPrompt { unlocked = true; error = "" } },
                        onChangePin = { changePinMode = true; error = "" }
                    )

                    else -> BrowserScreen(
                        settings = settings,
                        preferences = preferences,
                        dao = database.browserDao()
                    )
                }

                if (changePinMode) {
                    AlertDialog(
                        onDismissRequest = {
                            changePinMode = false
                            currentPin = ""; newPin = ""; confirmPin = ""
                        },
                        title = { Text("Change app PIN") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = currentPin,
                                    onValueChange = { currentPin = it.take(32) },
                                    label = { Text("Current PIN / password") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = newPin,
                                    onValueChange = { newPin = it.take(32) },
                                    label = { Text("New PIN / password (4-32 characters)") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = confirmPin,
                                    onValueChange = { confirmPin = it.take(32) },
                                    label = { Text("Confirm new PIN / password") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    singleLine = true
                                )
                                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                when {
                                    !appLockStore.verifyPin(currentPin) -> error = "Current PIN is incorrect"
                                    newPin.length < 4 -> error = "New PIN/password must have at least 4 characters"
                                    newPin != confirmPin -> error = "New PIN/passwords do not match"
                                    newPin == AppLockStore.RECOVERY_PIN -> error = "Choose a different PIN; 2055 remains the recovery PIN"
                                    else -> {
                                        appLockStore.setPin(newPin)
                                        error = "PIN changed successfully"
                                        changePinMode = false
                                        currentPin = ""; newPin = ""; confirmPin = ""
                                    }
                                }
                            }) { Text("Save") }
                        },
                        dismissButton = {
                            TextButton(onClick = { changePinMode = false; error = "" }) { Text("Cancel") }
                        }
                    )
                }
            }
        }
    }

    private fun showBiometricPrompt(onSuccess: () -> Unit) {
        val manager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
        if (manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) return

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Mini Web")
            .setSubtitle("Use your fingerprint to continue")
            .setNegativeButtonText("Use PIN / password")
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(info)
    }

}

@Composable
private fun AppLockScreen(
    error: String,
    onUnlock: (String) -> Unit,
    onBiometric: () -> Unit,
    onChangePin: () -> Unit
) {
    var value by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    val biometricAvailable = remember {
        BiometricManager.from(androidx.compose.ui.platform.LocalContext.current)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(68.dp))
        Spacer(Modifier.height(16.dp))
        Text("Mini Web is locked", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Authenticate to continue browsing", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { value = it.take(32) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("PIN or password") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { reveal = !reveal }) {
                    Icon(
                        imageVector = if (reveal) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (reveal) "Hide password" else "Show password"
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = RoundedCornerShape(16.dp)
        )
        if (error.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onUnlock(value) }, modifier = Modifier.fillMaxWidth(), enabled = value.isNotBlank()) {
            Text("Unlock Mini Web")
        }
        if (biometricAvailable) {
            OutlinedButton(onClick = onBiometric, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Fingerprint, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Unlock with fingerprint")
            }
        }
        TextButton(onClick = onChangePin) { Text("Change PIN / password") }
        Spacer(Modifier.height(12.dp))
        Text("Recovery PIN: 2055", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
