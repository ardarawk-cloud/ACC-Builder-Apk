package com.kin.app

import android.content.Context
import com.kin.app.auth.KinAuthRepository
import com.kin.app.auth.LocalKinAuthRepository
import com.kin.app.data.KinDatabase
import com.kin.app.data.KinProfileRepository
import com.kin.app.data.KinRelationshipRepository
import com.kin.app.data.LocalKinProfileRepository
import com.kin.app.data.LocalKinRelationshipRepository
import com.kin.app.session.KinSessionStore

class KinAppGraph private constructor(context: Context) {
    private val database = KinDatabase.create(context)
    val sessionStore = KinSessionStore(context.applicationContext)
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
