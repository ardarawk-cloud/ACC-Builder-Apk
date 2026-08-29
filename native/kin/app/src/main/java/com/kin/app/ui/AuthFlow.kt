package com.kin.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kin.app.KinAppGraph
import com.kin.app.auth.KinAuthRepository
import com.kin.app.auth.KinAuthResult
import com.kin.app.auth.KinRegistration
import com.kin.app.data.KinProfileEntity
import com.kin.app.data.KinProfileRepository
import com.kin.app.data.KinRelationshipRepository
import com.kin.app.session.KinSession
import com.kin.app.session.KinSessionStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    val graph = KinAppGraph.from(LocalContext.current)
    val session by graph.sessionStore.session.collectAsStateWithLifecycle(initialValue = KinSession())
    var route by rememberSaveable { mutableStateOf(AuthRoute.WELCOME) }

    MaterialTheme(colorScheme = authColors) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            when {
                session.signedIn && !session.onboardingComplete -> ProfileOnboardingScreen(
                    session = session,
                    profileRepository = graph.profileRepository,
                    relationshipRepository = graph.relationshipRepository,
                    sessionStore = graph.sessionStore,
                )
                session.signedIn -> KinApp(
                    graph = graph,
                    session = session,
                )
                else -> when (route) {
                    AuthRoute.WELCOME -> WelcomeScreen(
                        onLogin = { route = AuthRoute.LOGIN },
                        onRegister = { route = AuthRoute.REGISTER },
                    )
                    AuthRoute.LOGIN -> LoginScreen(
                        repository = graph.authRepository,
                        onBack = { route = AuthRoute.WELCOME },
                        onRegister = { route = AuthRoute.REGISTER },
                    )
                    AuthRoute.REGISTER -> RegisterScreen(
                        repository = graph.authRepository,
                        onBack = { route = AuthRoute.WELCOME },
                        onLogin = { route = AuthRoute.LOGIN },
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeScreen(onLogin: () -> Unit, onRegister: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("KIN", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black)
        Text("Your Space. Your People.", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Text(
            "Keep the people in your life close, organized, and remembered.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(40.dp))
        Button(onClick = onLogin, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Text("Log in")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onRegister, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Text("Create account")
        }
    }
}

@Composable
private fun LoginScreen(
    repository: KinAuthRepository,
    onBack: () -> Unit,
    onRegister: () -> Unit,
) {
    var identity by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AuthFormFrame("Welcome back", "Log in with your email or username.", onBack) {
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
                scope.launch {
                    working = true
                    when (val result = repository.login(identity.trim(), password)) {
                        KinAuthResult.Success -> error = null
                        is KinAuthResult.Error -> error = result.message
                    }
                    working = false
                }
            },
            enabled = !working,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
        ) { Text(if (working) "Logging in…" else "Log in") }
        TextButton(onClick = onRegister, modifier = Modifier.fillMaxWidth()) {
            Text("New to KIN? Create account")
        }
    }
}

@Composable
private fun RegisterScreen(
    repository: KinAuthRepository,
    onBack: () -> Unit,
    onLogin: () -> Unit,
) {
    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AuthFormFrame("Create your space", "Start with the basics. Your KIN space comes next.", onBack) {
        OutlinedTextField(displayName, { displayName = it; error = null }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(username, { username = it.trim(); error = null }, label = { Text("Username") }, prefix = { Text("@") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(email, { email = it.trim(); error = null }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        OutlinedTextField(password, { password = it; error = null }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                scope.launch {
                    working = true
                    when (val result = repository.register(KinRegistration(displayName, username, email, password))) {
                        KinAuthResult.Success -> error = null
                        is KinAuthResult.Error -> error = result.message
                    }
                    working = false
                }
            },
            enabled = !working,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
        ) { Text(if (working) "Creating…" else "Create account") }
        TextButton(onClick = onLogin, modifier = Modifier.fillMaxWidth()) {
            Text("Already have KIN? Log in")
        }
    }
}

@Composable
private fun ProfileOnboardingScreen(
    session: KinSession,
    profileRepository: KinProfileRepository,
    relationshipRepository: KinRelationshipRepository,
    sessionStore: KinSessionStore,
) {
    var profile by remember { mutableStateOf<KinProfileEntity?>(null) }
    var bio by rememberSaveable { mutableStateOf("") }
    var skinId by rememberSaveable { mutableStateOf("kin-original") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(session.displayName, session.username, session.email) {
        val storedProfile = profileRepository.observeProfile().first()
        val resolvedProfile = storedProfile ?: KinProfileEntity(
            displayName = session.displayName.ifBlank { "KIN User" },
            username = session.username.ifBlank { "kinuser" },
            email = session.email,
        )
        if (storedProfile == null) {
            profileRepository.saveProfile(resolvedProfile)
        }
        profile = resolvedProfile
        bio = resolvedProfile.bio
        skinId = resolvedProfile.skinId
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Make it yours", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Set up your space once. You can change everything later.")
        Text(
            profile?.username?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: "Preparing your profile…",
            style = MaterialTheme.typography.titleMedium,
        )
        OutlinedTextField(
            value = bio,
            onValueChange = { bio = it.take(160) },
            label = { Text("Short bio") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )
        Text("Starter skin", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("kin-original" to "KIN", "midnight" to "Midnight", "y2k" to "Y2K").forEach { (id, label) ->
                AssistChip(
                    onClick = { skinId = id },
                    label = { Text(if (skinId == id) "✓ $label" else label) },
                )
            }
        }
        Text("Starter Circles are prepared: Close Friends, Family, Work, School, Gaming, Client and Acquaintance.")
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val current = profile ?: return@Button
                scope.launch {
                    saving = true
                    profileRepository.saveProfile(current.copy(bio = bio.trim(), skinId = skinId))
                    relationshipRepository.ensureStarterData()
                    sessionStore.completeOnboarding()
                    saving = false
                }
            },
            enabled = profile != null && !saving,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
        ) { Text(if (saving) "Saving…" else "Enter KIN") }
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
