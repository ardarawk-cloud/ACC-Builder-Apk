package com.kin.app

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kin.app.auth.KinAuthRepository
import com.kin.app.auth.KinTokenStore
import com.kin.app.auth.LocalKinAuthRepository
import com.kin.app.auth.RemoteKinAuthRepository
import com.kin.app.data.KinDatabase
import com.kin.app.data.KinPostRepository
import com.kin.app.data.KinProfileRepository
import com.kin.app.data.KinRelationshipRepository
import com.kin.app.data.LocalKinPostRepository
import com.kin.app.data.LocalKinProfileRepository
import com.kin.app.data.LocalKinRelationshipRepository
import com.kin.app.session.KinSessionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.kinAppearanceDataStore by preferencesDataStore(name = "kin_appearance")

data class KinAppearance(
    val background: String = "Soft",
    val cards: String = "Rounded",
    val font: String = "Clean",
    val layout: String = "Classic",
)

class KinAppearanceStore(private val context: Context) {
    private object Keys {
        val BACKGROUND = stringPreferencesKey("background")
        val CARDS = stringPreferencesKey("cards")
        val FONT = stringPreferencesKey("font")
        val LAYOUT = stringPreferencesKey("layout")
    }

    val appearance: Flow<KinAppearance> = context.kinAppearanceDataStore.data.map { preferences ->
        KinAppearance(
            background = preferences[Keys.BACKGROUND] ?: "Soft",
            cards = preferences[Keys.CARDS] ?: "Rounded",
            font = preferences[Keys.FONT] ?: "Clean",
            layout = preferences[Keys.LAYOUT] ?: "Classic",
        )
    }

    suspend fun save(appearance: KinAppearance) {
        context.kinAppearanceDataStore.edit { preferences ->
            preferences[Keys.BACKGROUND] = appearance.background
            preferences[Keys.CARDS] = appearance.cards
            preferences[Keys.FONT] = appearance.font
            preferences[Keys.LAYOUT] = appearance.layout
        }
    }
}

class KinAppGraph private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val database = KinDatabase.create(appContext)
    private val dao = database.kinDao()
    private val tokenStore = KinTokenStore(appContext)

    val sessionStore = KinSessionStore(appContext)
    val appearanceStore = KinAppearanceStore(appContext)
    val profileRepository: KinProfileRepository = LocalKinProfileRepository(dao)
    val postRepository: KinPostRepository = LocalKinPostRepository(dao)
    val relationshipRepository: KinRelationshipRepository = LocalKinRelationshipRepository(dao)

    val authRepository: KinAuthRepository = BuildConfig.KIN_API_BASE_URL.trim().let { apiBaseUrl ->
        if (apiBaseUrl.isBlank()) {
            LocalKinAuthRepository(
                dao = dao,
                sessionStore = sessionStore,
            )
        } else {
            require(apiBaseUrl.startsWith("https://")) { "KIN_API_BASE_URL must use HTTPS" }
            RemoteKinAuthRepository(
                baseUrl = apiBaseUrl,
                dao = dao,
                sessionStore = sessionStore,
                tokenStore = tokenStore,
            )
        }
    }

    companion object {
        @Volatile private var instance: KinAppGraph? = null

        fun from(context: Context): KinAppGraph = instance ?: synchronized(this) {
            instance ?: KinAppGraph(context.applicationContext).also { instance = it }
        }
    }
}
