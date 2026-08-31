package com.kin.app.session

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.kinSessionDataStore by preferencesDataStore(name = "kin_session")

data class KinSession(
    val signedIn: Boolean = false,
    val displayName: String = "",
    val username: String = "",
    val email: String = "",
    val onboardingComplete: Boolean = false,
)

class KinSessionStore(private val context: Context) {
    private object Keys {
        val SIGNED_IN = booleanPreferencesKey("signed_in")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val USERNAME = stringPreferencesKey("username")
        val EMAIL = stringPreferencesKey("email")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    }

    val session: Flow<KinSession> = context.kinSessionDataStore.data.map { preferences ->
        KinSession(
            signedIn = preferences[Keys.SIGNED_IN] ?: false,
            displayName = preferences[Keys.DISPLAY_NAME].orEmpty(),
            username = preferences[Keys.USERNAME].orEmpty(),
            email = preferences[Keys.EMAIL].orEmpty(),
            onboardingComplete = preferences[Keys.ONBOARDING_COMPLETE] ?: false,
        )
    }

    suspend fun signIn(
        displayName: String,
        username: String,
        email: String,
        onboardingComplete: Boolean,
    ) {
        context.kinSessionDataStore.edit { preferences ->
            preferences[Keys.SIGNED_IN] = true
            preferences[Keys.DISPLAY_NAME] = displayName
            preferences[Keys.USERNAME] = username
            preferences[Keys.EMAIL] = email
            preferences[Keys.ONBOARDING_COMPLETE] = onboardingComplete
        }
    }

    suspend fun completeOnboarding() {
        context.kinSessionDataStore.edit { preferences ->
            preferences[Keys.ONBOARDING_COMPLETE] = true
        }
    }

    suspend fun signOut() {
        context.kinSessionDataStore.edit { preferences ->
            preferences[Keys.SIGNED_IN] = false
        }
    }
}
