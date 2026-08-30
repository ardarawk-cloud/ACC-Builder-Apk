package com.kin.app.auth

import com.kin.app.data.KinDao
import com.kin.app.data.KinProfileEntity
import com.kin.app.session.KinSessionStore
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject

class RemoteKinAuthRepository(
    baseUrl: String,
    private val dao: KinDao,
    private val sessionStore: KinSessionStore,
    private val tokenStore: KinTokenStore,
) : KinAuthRepository {
    private val apiBaseUrl = baseUrl.trimEnd('/')

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
        val accessToken = tokenStore.accessToken()
        val refreshToken = tokenStore.refreshToken()

        if (accessToken == null && refreshToken == null) {
            if (existingSession.signedIn) sessionStore.signOut()
            return@withContext KinAuthResult.Success
        }

        try {
            if (accessToken != null) {
                val me = request("GET", "/v1/me", bearerToken = accessToken)
                if (me.code in 200..299) {
                    cacheUser(JSONObject(me.body), onboardingComplete = null)
                    return@withContext KinAuthResult.Success
                }
                if (me.code != HttpURLConnection.HTTP_UNAUTHORIZED) {
                    return@withContext KinAuthResult.Error(errorDetail(me, "Could not sync your KIN profile."))
                }
            }

            if (refreshTokens()) {
                return@withContext KinAuthResult.Success
            }

            expireLocalSession()
            KinAuthResult.Error("Your KIN session expired. Log in again.")
        } catch (_: java.net.UnknownHostException) {
            // Offline-first behavior: keep a previously verified local session and Room cache usable.
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
            val response = authorizedRequest("PATCH", "/v1/me", payload)
            if (response.code !in 200..299) {
                return@withContext KinAuthResult.Error(errorDetail(response, "Could not update your KIN profile."))
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
        val refreshToken = tokenStore.refreshToken()
        if (refreshToken != null) {
            runCatching {
                post(
                    "/v1/auth/logout",
                    JSONObject().put("refresh_token", refreshToken),
                )
            }
        }
        tokenStore.clear()
        sessionStore.signOut()
        KinAuthResult.Success
    }

    private suspend fun authRequest(
        path: String,
        payload: JSONObject,
        onboardingComplete: Boolean,
    ): KinAuthResult {
        return try {
            val response = post(path, payload)
            if (response.code !in 200..299) {
                return KinAuthResult.Error(errorDetail(response, "KIN server rejected the request."))
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

    private suspend fun authorizedRequest(method: String, path: String, payload: JSONObject? = null): HttpResult {
        var accessToken = tokenStore.accessToken()
        if (accessToken == null) {
            if (!refreshTokens()) return HttpResult(HttpURLConnection.HTTP_UNAUTHORIZED, "")
            accessToken = tokenStore.accessToken()
        }

        var response = request(method, path, payload, accessToken)
        if (response.code == HttpURLConnection.HTTP_UNAUTHORIZED && refreshTokens()) {
            val renewedAccess = tokenStore.accessToken()
            if (renewedAccess != null) {
                response = request(method, path, payload, renewedAccess)
            }
        }
        return response
    }

    private suspend fun refreshTokens(): Boolean {
        val refreshToken = tokenStore.refreshToken() ?: return false
        val response = post(
            "/v1/auth/refresh",
            JSONObject().put("refresh_token", refreshToken),
        )
        if (response.code !in 200..299) return false
        persistAuthResponse(JSONObject(response.body), onboardingComplete = null)
        return true
    }

    private suspend fun persistAuthResponse(body: JSONObject, onboardingComplete: Boolean?) {
        tokenStore.save(
            accessToken = body.getString("access_token"),
            refreshToken = body.getString("refresh_token"),
        )
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
        tokenStore.clear()
        sessionStore.signOut()
    }

    private fun errorDetail(response: HttpResult, fallback: String): String {
        return runCatching { JSONObject(response.body).optString("detail") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: fallback
    }

    private fun post(path: String, payload: JSONObject): HttpResult =
        request("POST", path, payload)

    private fun request(
        method: String,
        path: String,
        payload: JSONObject? = null,
        bearerToken: String? = null,
    ): HttpResult {
        require(apiBaseUrl.startsWith("https://")) { "KIN remote API must use HTTPS" }
        val connection = (URL(apiBaseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/json")
            bearerToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (payload != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        if (payload != null) {
            connection.outputStream.use { stream ->
                stream.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
        }
        val code = connection.responseCode
        val input = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = input?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        return HttpResult(code, body)
    }

    private data class HttpResult(val code: Int, val body: String)
}
