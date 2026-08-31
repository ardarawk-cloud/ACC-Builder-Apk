package com.kin.app.data

import com.kin.app.network.KinApiClient
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class RemoteKinChatRepository(
    private val dao: KinDao,
    private val apiClient: KinApiClient,
) : KinChatRepository {
    override fun observeMessages(personId: String): Flow<List<KinMessageEntity>> = dao.observeMessages(personId)

    override suspend fun refreshMessages(personId: String, username: String): KinPeopleResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.authorizedRequest("GET", "/v1/chats/${encode(username)}/messages")
            if (response.code !in 200..299) {
                return@withContext KinPeopleResult.Error(apiClient.errorDetail(response, "Could not refresh this chat."))
            }
            val ownUsername = dao.getProfile()?.username.orEmpty()
            val array = JSONArray(response.body)
            val messages = buildList {
                for (index in 0 until array.length()) {
                    add(messageFromJson(array.getJSONObject(index), personId, ownUsername))
                }
            }
            dao.clearMessages(personId)
            if (messages.isNotEmpty()) dao.upsertMessages(messages)
            KinPeopleResult.Success(Unit)
        } catch (_: java.net.UnknownHostException) {
            KinPeopleResult.Error("KIN server is unreachable. Showing saved messages.")
        } catch (_: java.net.SocketTimeoutException) {
            KinPeopleResult.Error("KIN chat refresh took too long.")
        } catch (_: Exception) {
            KinPeopleResult.Error("Could not refresh this KIN chat right now.")
        }
    }

    override suspend fun sendMessage(personId: String, username: String, text: String): KinPeopleResult<KinMessageEntity> = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.authorizedRequest(
                "POST",
                "/v1/chats/${encode(username)}/messages",
                JSONObject().put("text", text.trim()),
            )
            if (response.code !in 200..299) {
                return@withContext KinPeopleResult.Error(apiClient.errorDetail(response, "Could not send this message."))
            }
            val ownUsername = dao.getProfile()?.username.orEmpty()
            val saved = messageFromJson(JSONObject(response.body), personId, ownUsername)
            dao.upsertMessage(saved)
            KinPeopleResult.Success(saved)
        } catch (_: java.net.UnknownHostException) {
            KinPeopleResult.Error("KIN server is unreachable. Message not sent.")
        } catch (_: java.net.SocketTimeoutException) {
            KinPeopleResult.Error("KIN took too long to send this message.")
        } catch (_: Exception) {
            KinPeopleResult.Error("Could not send this KIN message right now.")
        }
    }

    private fun messageFromJson(json: JSONObject, personId: String, ownUsername: String): KinMessageEntity {
        val sender = json.getJSONObject("sender")
        val senderUsername = sender.getString("username")
        return KinMessageEntity(
            id = json.getString("id"),
            otherPersonId = personId,
            senderId = sender.getInt("id").toString(),
            senderDisplayName = sender.getString("display_name"),
            senderUsername = senderUsername,
            text = json.getString("text"),
            mine = senderUsername == ownUsername,
            createdAt = parseTimestamp(json.getString("created_at")),
        )
    }

    private fun parseTimestamp(raw: String): Long = runCatching {
        OffsetDateTime.parse(raw).toInstant().toEpochMilli()
    }.getOrElse {
        LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC).toEpochMilli()
    }

    private fun encode(value: String): String = URLEncoder.encode(value.trim().removePrefix("@"), Charsets.UTF_8.name())
}
