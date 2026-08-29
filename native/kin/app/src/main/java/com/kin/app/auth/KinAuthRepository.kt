package com.kin.app.auth

import com.kin.app.data.KinDao
import com.kin.app.data.KinProfileEntity
import com.kin.app.session.KinSessionStore

data class KinRegistration(
    val displayName: String,
    val username: String,
    val email: String,
    val password: String,
)

sealed interface KinAuthResult {
    data object Success : KinAuthResult
    data class Error(val message: String) : KinAuthResult
}

interface KinAuthRepository {
    suspend fun login(identity: String, password: String): KinAuthResult
    suspend fun register(registration: KinRegistration): KinAuthResult
}

/**
 * Local development implementation.
 *
 * It persists the account/session shape used by the UI now. A remote implementation can replace
 * this class later without changing Welcome/Login/Register/Profile Onboarding screens.
 * Passwords are intentionally NOT persisted in this local preview implementation.
 */
class LocalKinAuthRepository(
    private val dao: KinDao,
    private val sessionStore: KinSessionStore,
) : KinAuthRepository {
    override suspend fun login(identity: String, password: String): KinAuthResult {
        if (identity.isBlank() || password.length < 4) {
            return KinAuthResult.Error("Enter your account and password.")
        }

        val profile = dao.getProfile()
        val displayName = profile?.displayName ?: identity.substringBefore("@").ifBlank { "KIN User" }
        val username = profile?.username ?: identity.substringBefore("@").replace(" ", "").lowercase()
        val email = profile?.email ?: if (identity.contains("@")) identity else ""

        sessionStore.signIn(
            displayName = displayName,
            username = username,
            email = email,
            onboardingComplete = profile != null,
        )
        return KinAuthResult.Success
    }

    override suspend fun register(registration: KinRegistration): KinAuthResult {
        val validEmail = registration.email.contains("@") &&
            registration.email.substringAfter("@").contains(".")

        val validationError = when {
            registration.displayName.isBlank() -> "Add your display name."
            registration.username.length < 3 -> "Username must be at least 3 characters."
            !validEmail -> "Enter a valid email address."
            registration.password.length < 6 -> "Password must be at least 6 characters."
            else -> null
        }
        if (validationError != null) return KinAuthResult.Error(validationError)

        dao.upsertProfile(
            KinProfileEntity(
                displayName = registration.displayName.trim(),
                username = registration.username.trim().removePrefix("@"),
                email = registration.email.trim(),
            ),
        )
        sessionStore.signIn(
            displayName = registration.displayName.trim(),
            username = registration.username.trim().removePrefix("@"),
            email = registration.email.trim(),
            onboardingComplete = false,
        )
        return KinAuthResult.Success
    }
}
