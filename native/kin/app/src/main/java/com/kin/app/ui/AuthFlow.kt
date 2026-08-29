package com.kin.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private enum class AuthRoute { WELCOME, LOGIN, REGISTER }

private val authColors = lightColorScheme(
    primary = Color(0xFF7C5CFC),
    secondary = Color(0xFFFF7FA6),
    tertiary = Color(0xFF4FB7A8),
    surface = Color(0xFFFFFBFF),
    background = Color(0xFFF8F5FF),
)

@Composable
fun KinEntry() {
    var signedIn by rememberSaveable { mutableStateOf(false) }
    var route by rememberSaveable { mutableStateOf(AuthRoute.WELCOME) }

    if (signedIn) {
        KinApp()
        return
    }

    MaterialTheme(colorScheme = authColors) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (route) {
                AuthRoute.WELCOME -> WelcomeScreen(
                    onLogin = { route = AuthRoute.LOGIN },
                    onRegister = { route = AuthRoute.REGISTER },
                )
                AuthRoute.LOGIN -> LoginScreen(
                    onBack = { route = AuthRoute.WELCOME },
                    onSignedIn = { signedIn = true },
                    onRegister = { route = AuthRoute.REGISTER },
                )
                AuthRoute.REGISTER -> RegisterScreen(
                    onBack = { route = AuthRoute.WELCOME },
                    onRegistered = { signedIn = true },
                    onLogin = { route = AuthRoute.LOGIN },
                )
            }
        }
    }
}

@Composable
private fun WelcomeScreen(
    onLogin: () -> Unit,
    onRegister: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "KIN",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = "Your Space. Your People.",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Keep the people in your life close, organized, and remembered.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onLogin,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text("Log in")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onRegister,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text("Create account")
        }
    }
}

@Composable
private fun LoginScreen(
    onBack: () -> Unit,
    onSignedIn: () -> Unit,
    onRegister: () -> Unit,
) {
    var identity by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AuthFormFrame(
        title = "Welcome back",
        subtitle = "Log in with your email or username.",
        onBack = onBack,
    ) {
        OutlinedTextField(
            value = identity,
            onValueChange = { identity = it; error = null },
            label = { Text("Email or username") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                if (identity.isBlank() || password.length < 4) {
                    error = "Enter your account and password."
                } else {
                    onSignedIn()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text("Log in")
        }
        TextButton(onClick = onRegister, modifier = Modifier.fillMaxWidth()) {
            Text("New to KIN? Create account")
        }
    }
}

@Composable
private fun RegisterScreen(
    onBack: () -> Unit,
    onRegistered: () -> Unit,
    onLogin: () -> Unit,
) {
    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AuthFormFrame(
        title = "Create your space",
        subtitle = "Start with the basics. You can customize your KIN space next.",
        onBack = onBack,
    ) {
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it; error = null },
            label = { Text("Display name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it.trim(); error = null },
            label = { Text("Username") },
            prefix = { Text("@") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it.trim(); error = null },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                val validEmail = email.contains("@") && email.substringAfter("@").contains(".")
                when {
                    displayName.isBlank() -> error = "Add your display name."
                    username.length < 3 -> error = "Username must be at least 3 characters."
                    !validEmail -> error = "Enter a valid email address."
                    password.length < 6 -> error = "Password must be at least 6 characters."
                    else -> onRegistered()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text("Create account")
        }
        TextButton(onClick = onLogin, modifier = Modifier.fillMaxWidth()) {
            Text("Already have KIN? Log in")
        }
    }
}

@Composable
private fun AuthFormFrame(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Back") }
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        content()
    }
}
