package com.kin.app

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kin.app.auth.KinAuthRepository
import com.kin.app.auth.LocalKinAuthRepository
import com.kin.app.data.KinDatabase
import com.kin.app.data.KinProfileRepository
import com.kin.app.data.KinRelationshipRepository
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
    private val database = KinDatabase.create(context)
    val sessionStore = KinSessionStore(context.applicationContext)
    val appearanceStore = KinAppearanceStore(context.applicationContext)
    val profileRepository: KinProfileRepository = LocalKinProfileRepository(database.kinDao())
    val relationshipRepository: KinRelationshipRepository = LocalKinRelationshipRepository(database.kinDao())
    val authRepository: KinAuthRepository = LocalKinAuthRepository(
        dao = database.kinDao(),
        sessionStore = sessionStore,
    )

    companion object {
        @Volatile private var instance: KinAppGraph? = null

        fun from(context: Context): KinAppGraph = instance ?: synchronized(this) {
            instance ?: KinAppGraph(context.applicationContext).also { instance = it }
        }
    }
}
