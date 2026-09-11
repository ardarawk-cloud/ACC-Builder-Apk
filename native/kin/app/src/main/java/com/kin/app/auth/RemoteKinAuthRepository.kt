package com.kin.app.auth

import com.kin.app.data.KinDao
import com.kin.app.data.KinProfileEntity
import com.kin.app.network.KinApiClient
import com.kin.app.session.KinSessionStore
import java.net.HttpURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject

class RemoteKinAuthRepository(
    private val apiClient: KinApiClient,
    private val dao: KinDao,
    private val sessionStore: KinSessionStore,
) : KinAuthRepository {
    override suspend fun login(identity: String, password: String): KinAuthResult = withContext(Dispatchers.IO) {
        if (identity.isBlank() || password.isBlank()) {
            return@withContext KinAuthResult.Error("Enter your account and password.")
        }
        val payload = JSONObject()
            .put("identity", identity.trim())
            .put("password", password)
        authRequest("/v1/auth/login", payload, onboardingComplete = true)
    }

    override suspend fun register(registration: KinRegistration): KinAuthResult = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("display_name", registration.displayName.trim())
            .put("username", registration.username.trim().removePrefix("@"))
            .put("email", registration.email.trim())
            .put("password", registration.password)
        authRequest("/v1/auth/register", payload, onboardingComplete = false)
    }

    override suspend fun restoreSession(): KinAuthResult = withContext(Dispatchers.IO) {
        val existingSession = sessionStore.session.first()
        val accessToken = apiClient.accessToken()
        val refreshToken = apiClient.refreshToken()

        if (accessToken == null && refreshToken == null) {
            if (existingSession.signedIn) sessionStore.signOut()
            return@withContext KinAuthResult.Success
        }

        try {
            if (accessToken != null) {
                val me = apiClient.request("GET", "/v1/me", bearerToken = accessToken)
                if (me.code in 200..299) {
                    cacheUser(JSONObject(me.body), onboardingComplete = null)
                    return@withContext KinAuthResult.Success
                }
                if (me.code != HttpURLConnection.HTTP_UNAUTHORIZED) {
                    return@withContext KinAuthResult.Error(apiClient.errorDetail(me, "Could not sync your KIN profile."))
                }
            }

            val refreshed = apiClient.refreshAuth()
            if (refreshed != null) {
                cacheUser(refreshed.getJSONObject("user"), onboardingComplete = null)
                return@withContext KinAuthResult.Success
            }

            expireLocalSession()
            KinAuthResult.Error("Your KIN session expired. Log in again.")
        } catch (_: java.net.UnknownHostException) {
            if (existingSession.signedIn) KinAuthResult.Success
            else KinAuthResult.Error("KIN server is unreachable. Check your connection.")
        } catch (_: java.net.SocketTimeoutException) {
            if (existingSession.signedIn) KinAuthResult.Success
            else KinAuthResult.Error("KIN server took too long to respond.")
        } catch (_: Exception) {
            if (existingSession.signedIn) KinAuthResult.Success
            else KinAuthResult.Error("Could not restore your KIN session.")
        }
    }

    override suspend fun updateProfile(update: KinProfileUpdate): KinAuthResult = withContext(Dispatchers.IO) {
        if (update.displayName == null && update.bio == null && update.skinId == null) {
            return@withContext KinAuthResult.Success
        }

        val payload = JSONObject().apply {
            update.displayName?.let { put("display_name", it.trim()) }
            update.bio?.let { put("bio", it.trim()) }
            update.skinId?.let { put("skin_id", it.trim()) }
        }

        try {
            val response = apiClient.authorizedRequest("PATCH", "/v1/me", payload)
            if (response.code !in 200..299) {
                return@withContext KinAuthResult.Error(apiClient.errorDetail(response, "Could not update your KIN profile."))
            }
            cacheUser(JSONObject(response.body), onboardingComplete = null)
            KinAuthResult.Success
        } catch (_: java.net.UnknownHostException) {
            KinAuthResult.Error("KIN server is unreachable. Check your connection.")
        } catch (_: java.net.SocketTimeoutException) {
            KinAuthResult.Error("KIN server took too long to respond.")
        } catch (_: Exception) {
            KinAuthResult.Error("Could not update your KIN profile right now.")
        }
    }

    override suspend fun logout(): KinAuthResult = withContext(Dispatchers.IO) {
        val refreshToken = apiClient.refreshToken()
        if (refreshToken != null) {
            runCatching {
                apiClient.post(
                    "/v1/auth/logout",
                    JSONObject().put("refresh_token", refreshToken),
                )
            }
        }
        apiClient.clearTokens()
        sessionStore.signOut()
        KinAuthResult.Success
    }

    private suspend fun authRequest(
        path: String,
        payload: JSONObject,
        onboardingComplete: Boolean,
    ): KinAuthResult {
        return try {
            val response = apiClient.post(path, payload)
            if (response.code !in 200..299) {
                return KinAuthResult.Error(apiClient.errorDetail(response, "KIN server rejected the request."))
            }
            persistAuthResponse(JSONObject(response.body), onboardingComplete)
            KinAuthResult.Success
        } catch (_: java.net.UnknownHostException) {
            KinAuthResult.Error("KIN server is unreachable. Check your connection.")
        } catch (_: java.net.SocketTimeoutException) {
            KinAuthResult.Error("KIN server took too long to respond.")
        } catch (_: Exception) {
            KinAuthResult.Error("Could not connect to KIN right now.")
        }
    }

    private suspend fun persistAuthResponse(body: JSONObject, onboardingComplete: Boolean?) {
        apiClient.persistAuthTokens(body)
        cacheUser(body.getJSONObject("user"), onboardingComplete)
    }

    private suspend fun cacheUser(user: JSONObject, onboardingComplete: Boolean?) {
        val profile = KinProfileEntity(
            displayName = user.getString("display_name"),
            username = user.getString("username"),
            email = user.getString("email"),
            bio = user.optString("bio"),
            skinId = user.optString("skin_id", "kin-original"),
        )
        dao.upsertProfile(profile)
        val current = sessionStore.session.first()
        sessionStore.signIn(
            displayName = profile.displayName,
            username = profile.username,
            email = profile.email,
            onboardingComplete = onboardingComplete ?: current.onboardingComplete,
        )
    }

    private suspend fun expireLocalSession() {
        apiClient.clearTokens()
        sessionStore.signOut()
    }
}
