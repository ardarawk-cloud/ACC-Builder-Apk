package com.kin.app.auth

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
 * Development implementation only.
 *
 * The UI talks to KinAuthRepository from day one so the backend-backed implementation can
 * replace this class without rewriting Welcome/Login/Register screens.
 */
class PreviewKinAuthRepository : KinAuthRepository {
    override suspend fun login(identity: String, password: String): KinAuthResult {
        return if (identity.isBlank() || password.length < 4) {
            KinAuthResult.Error("Enter your account and password.")
        } else {
            KinAuthResult.Success
        }
    }

    override suspend fun register(registration: KinRegistration): KinAuthResult {
        val validEmail = registration.email.contains("@") &&
            registration.email.substringAfter("@").contains(".")

        return when {
            registration.displayName.isBlank() -> KinAuthResult.Error("Add your display name.")
            registration.username.length < 3 -> KinAuthResult.Error("Username must be at least 3 characters.")
            !validEmail -> KinAuthResult.Error("Enter a valid email address.")
            registration.password.length < 6 -> KinAuthResult.Error("Password must be at least 6 characters.")
            else -> KinAuthResult.Success
        }
    }
}
