package com.kin.app.network

import com.kin.app.auth.KinTokenStore
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

data class KinHttpResult(val code: Int, val body: String)

class KinApiClient(
    baseUrl: String,
    private val tokenStore: KinTokenStore,
) {
    private val apiBaseUrl = baseUrl.trimEnd('/')

    init {
        require(apiBaseUrl.startsWith("https://")) { "KIN remote API must use HTTPS" }
    }

    fun accessToken(): String? = tokenStore.accessToken()
    fun refreshToken(): String? = tokenStore.refreshToken()
    fun clearTokens() = tokenStore.clear()
    fun absoluteUrl(pathOrUrl: String): String =
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) pathOrUrl else "$apiBaseUrl/${pathOrUrl.trimStart('/')}"

    fun post(path: String, payload: JSONObject? = null): KinHttpResult =
        request("POST", path, payload)

    fun authorizedRequest(
        method: String,
        path: String,
        payload: JSONObject? = null,
    ): KinHttpResult {
        var access = tokenStore.accessToken()
        if (access == null) {
            if (refreshAuth() == null) return KinHttpResult(HttpURLConnection.HTTP_UNAUTHORIZED, "")
            access = tokenStore.accessToken()
        }

        var response = request(method, path, payload, access)
        if (response.code == HttpURLConnection.HTTP_UNAUTHORIZED && refreshAuth() != null) {
            tokenStore.accessToken()?.let { renewed ->
                response = request(method, path, payload, renewed)
            }
        }
        return response
    }

    fun authorizedBinaryRequest(
        method: String,
        path: String,
        bytes: ByteArray,
        contentType: String,
        fileName: String,
    ): KinHttpResult {
        var access = tokenStore.accessToken()
        if (access == null) {
            if (refreshAuth() == null) return KinHttpResult(HttpURLConnection.HTTP_UNAUTHORIZED, "")
            access = tokenStore.accessToken()
        }
        var response = binaryRequest(method, path, bytes, contentType, fileName, access)
        if (response.code == HttpURLConnection.HTTP_UNAUTHORIZED && refreshAuth() != null) {
            tokenStore.accessToken()?.let { renewed ->
                response = binaryRequest(method, path, bytes, contentType, fileName, renewed)
            }
        }
        return response
    }

    fun refreshAuth(): JSONObject? {
        val refresh = tokenStore.refreshToken() ?: return null
        val response = post(
            "/v1/auth/refresh",
            JSONObject().put("refresh_token", refresh),
        )
        if (response.code !in 200..299) return null
        val body = JSONObject(response.body)
        tokenStore.save(
            accessToken = body.getString("access_token"),
            refreshToken = body.getString("refresh_token"),
        )
        return body
    }

    fun persistAuthTokens(body: JSONObject) {
        tokenStore.save(
            accessToken = body.getString("access_token"),
            refreshToken = body.getString("refresh_token"),
        )
    }

    fun errorDetail(response: KinHttpResult, fallback: String): String {
        val detail = runCatching { JSONObject(response.body).optString("detail") }.getOrNull()
        if (response.code == HttpURLConnection.HTTP_NOT_FOUND && detail.equals("Not Found", ignoreCase = true)) {
            return fallback
        }
        return detail?.takeIf { it.isNotBlank() } ?: fallback
    }

    fun request(
        method: String,
        path: String,
        payload: JSONObject? = null,
        bearerToken: String? = null,
    ): KinHttpResult {
        val connection = (URL(absoluteUrl(path)).openConnection() as HttpURLConnection).apply {
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
        return readResult(connection)
    }

    private fun binaryRequest(
        method: String,
        path: String,
        bytes: ByteArray,
        contentType: String,
        fileName: String,
        bearerToken: String?,
    ): KinHttpResult {
        val safeName = fileName.filter { it.code in 32..126 }.take(80).ifBlank { "kin-upload" }
        val connection = (URL(absoluteUrl(path)).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 60_000
            doOutput = true
            setFixedLengthStreamingMode(bytes.size)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", contentType)
            setRequestProperty("X-KIN-Filename", safeName)
            bearerToken?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
        connection.outputStream.use { stream -> stream.write(bytes) }
        return readResult(connection)
    }

    private fun readResult(connection: HttpURLConnection): KinHttpResult {
        val code = connection.responseCode
        val input = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = input?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        return KinHttpResult(code, body)
    }
}
