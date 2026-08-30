package com.kin.app.auth

import com.kin.app.data.KinDao
import com.kin.app.data.KinProfileEntity
import com.kin.app.session.KinSessionStore
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
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

    private suspend fun authRequest(
        path: String,
        payload: JSONObject,
        onboardingComplete: Boolean,
    ): KinAuthResult {
        return try {
            val response = post(path, payload)
            if (response.code !in 200..299) {
                val detail = runCatching { JSONObject(response.body).optString("detail") }.getOrNull()
                return KinAuthResult.Error(detail?.takeIf { it.isNotBlank() } ?: "KIN server rejected the request.")
            }

            val body = JSONObject(response.body)
            val user = body.getJSONObject("user")
            val accessToken = body.getString("access_token")
            val refreshToken = body.getString("refresh_token")
            tokenStore.save(accessToken, refreshToken)

            val profile = KinProfileEntity(
                displayName = user.getString("display_name"),
                username = user.getString("username"),
                email = user.getString("email"),
                bio = user.optString("bio"),
                skinId = user.optString("skin_id", "kin-original"),
            )
            dao.upsertProfile(profile)
            sessionStore.signIn(
                displayName = profile.displayName,
                username = profile.username,
                email = profile.email,
                onboardingComplete = onboardingComplete,
            )
            KinAuthResult.Success
        } catch (_: java.net.UnknownHostException) {
            KinAuthResult.Error("KIN server is unreachable. Check your connection.")
        } catch (_: java.net.SocketTimeoutException) {
            KinAuthResult.Error("KIN server took too long to respond.")
        } catch (_: Exception) {
            KinAuthResult.Error("Could not connect to KIN right now.")
        }
    }

    private fun post(path: String, payload: JSONObject): HttpResult {
        require(apiBaseUrl.startsWith("https://")) { "KIN remote API must use HTTPS" }
        val connection = (URL(apiBaseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 12_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        connection.outputStream.use { stream ->
            stream.write(payload.toString().toByteArray(Charsets.UTF_8))
        }
        val code = connection.responseCode
        val input = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = input?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        return HttpResult(code, body)
    }

    private data class HttpResult(val code: Int, val body: String)
}
