package com.kin.app.data

import com.kin.app.network.KinApiClient
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class RemoteKinPostRepository(
    private val dao: KinDao,
    private val apiClient: KinApiClient,
) : KinPostRepository {
    override fun observePosts(): Flow<List<KinPostEntity>> = dao.observePosts()

    override suspend fun refreshFeed(): KinPeopleResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.authorizedRequest("GET", "/v1/feed")
            if (response.code !in 200..299) {
                return@withContext KinPeopleResult.Error(apiClient.errorDetail(response, "Could not refresh Home."))
            }
            val array = JSONArray(response.body)
            val posts = buildList {
                for (index in 0 until array.length()) add(postFromJson(array.getJSONObject(index)))
            }
            dao.clearPosts()
            if (posts.isNotEmpty()) dao.upsertPosts(posts)
            KinPeopleResult.Success(Unit)
        } catch (_: java.net.UnknownHostException) {
            KinPeopleResult.Error("KIN server is unreachable. Showing saved Home posts.")
        } catch (_: java.net.SocketTimeoutException) {
            KinPeopleResult.Error("KIN Home refresh took too long.")
        } catch (_: Exception) {
            KinPeopleResult.Error("Could not refresh KIN Home right now.")
        }
    }

    override suspend fun publishPost(post: KinPostEntity, allowedUserIds: List<String>): KinPeopleResult<KinPostEntity> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject()
                .put("text", post.text)
                .put("audience", audienceToApi(post.audience))
                .put("allowed_user_ids", JSONArray(allowedUserIds.mapNotNull { it.toIntOrNull() }))
            post.feeling?.let { payload.put("feeling", it) }
            post.listening?.let { payload.put("listening", it) }
            post.location?.let { payload.put("location", it) }
            post.withPeople?.let { payload.put("with_people", it) }

            val response = apiClient.authorizedRequest("POST", "/v1/posts", payload)
            if (response.code !in 200..299) {
                return@withContext KinPeopleResult.Error(apiClient.errorDetail(response, "Could not publish this post."))
            }
            val saved = postFromJson(JSONObject(response.body))
            dao.upsertPost(saved)
            KinPeopleResult.Success(saved)
        } catch (_: java.net.UnknownHostException) {
            KinPeopleResult.Error("KIN server is unreachable. Your post was not published.")
        } catch (_: java.net.SocketTimeoutException) {
            KinPeopleResult.Error("KIN took too long to publish this post.")
        } catch (_: Exception) {
            KinPeopleResult.Error("Could not publish this post right now.")
        }
    }

    override suspend fun editPost(postId: String, text: String): KinPeopleResult<KinPostEntity> = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.authorizedRequest(
                "PATCH",
                "/v1/posts/$postId",
                JSONObject().put("text", text.trim()),
            )
            if (response.code !in 200..299) {
                return@withContext KinPeopleResult.Error(apiClient.errorDetail(response, "Could not edit this post."))
            }
            val saved = postFromJson(JSONObject(response.body))
            dao.upsertPost(saved)
            KinPeopleResult.Success(saved)
        } catch (_: Exception) {
            KinPeopleResult.Error("Could not edit this post right now.")
        }
    }

    override suspend fun deletePost(postId: String): KinPeopleResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.authorizedRequest("DELETE", "/v1/posts/$postId")
            if (response.code !in 200..299) {
                return@withContext KinPeopleResult.Error(apiClient.errorDetail(response, "Could not delete this post."))
            }
            dao.deletePost(postId)
            KinPeopleResult.Success(Unit)
        } catch (_: Exception) {
            KinPeopleResult.Error("Could not delete this post right now.")
        }
    }

    private fun postFromJson(json: JSONObject): KinPostEntity {
        val author = json.getJSONObject("author")
        return KinPostEntity(
            id = json.getString("id"),
            authorDisplayName = author.getString("display_name"),
            authorUsername = author.getString("username"),
            text = json.optString("text"),
            audience = audienceFromApi(json.optString("audience", "friends")),
            feeling = nullableString(json, "feeling"),
            listening = nullableString(json, "listening"),
            location = nullableString(json, "location"),
            withPeople = nullableString(json, "with_people"),
            createdAt = parseTimestamp(json.getString("created_at")),
        )
    }

    private fun nullableString(json: JSONObject, key: String): String? =
        if (json.isNull(key)) null else json.optString(key).takeIf { it.isNotBlank() }

    private fun parseTimestamp(raw: String): Long = runCatching {
        OffsetDateTime.parse(raw).toInstant().toEpochMilli()
    }.getOrElse {
        LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC).toEpochMilli()
    }

    private fun audienceToApi(value: String): String = when (value) {
        "Public" -> "public"
        "Circle" -> "selected"
        "Only Me" -> "only_me"
        else -> "friends"
    }

    private fun audienceFromApi(value: String): String = when (value) {
        "public" -> "Public"
        "selected" -> "Circle"
        "only_me" -> "Only Me"
        else -> "Friends"
    }
}
