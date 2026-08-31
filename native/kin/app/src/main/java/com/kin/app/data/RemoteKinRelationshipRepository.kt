package com.kin.app.data

import com.kin.app.network.KinApiClient
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class RemoteKinRelationshipRepository(
    private val dao: KinDao,
    private val apiClient: KinApiClient,
) : KinRelationshipRepository {
    private val local = LocalKinRelationshipRepository(dao)

    override fun observeCircles(): Flow<List<KinCircleEntity>> = local.observeCircles()
    override fun observePeople(): Flow<List<KinPersonWithCircles>> = local.observePeople()
    override suspend fun ensureStarterData() = local.ensureStarterData()
    override suspend fun savePrivateNote(personId: String, note: String) = local.savePrivateNote(personId, note)
    override suspend fun setPersonCircles(personId: String, circleIds: List<String>) = local.setPersonCircles(personId, circleIds)

    override suspend fun syncConnections(): KinPeopleResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.authorizedRequest("GET", "/v1/connections")
            if (response.code !in 200..299) {
                return@withContext KinPeopleResult.Error(apiClient.errorDetail(response, "Could not sync your KIN connections."))
            }
            val array = JSONArray(response.body)
            val remoteIds = mutableSetOf<String>()
            for (index in 0 until array.length()) {
                val person = personFromJson(array.getJSONObject(index), relationship = "friends")
                remoteIds += person.id
                cachePerson(person)
            }
            dao.getPeopleIds().filterNot { it in remoteIds }.forEach { staleId ->
                dao.clearPersonCircles(staleId)
                dao.clearMessages(staleId)
                dao.deletePerson(staleId)
            }
            KinPeopleResult.Success(Unit)
        } catch (_: java.net.UnknownHostException) {
            KinPeopleResult.Error("KIN server is unreachable. Your saved connections are still available.")
        } catch (_: java.net.SocketTimeoutException) {
            KinPeopleResult.Error("KIN server took too long to sync connections.")
        } catch (_: Exception) {
            KinPeopleResult.Error("Could not sync KIN connections right now.")
        }
    }

    override suspend fun searchPeople(query: String): KinPeopleResult<List<KinRemotePerson>> = withContext(Dispatchers.IO) {
        val normalized = query.trim().removePrefix("@").trim()
        if (normalized.isBlank()) return@withContext KinPeopleResult.Success(emptyList())
        try {
            val encoded = encode(normalized)
            val response = apiClient.authorizedRequest("GET", "/v1/people/search?q=$encoded")
            if (response.code !in 200..299) {
                return@withContext KinPeopleResult.Error(apiClient.errorDetail(response, "Could not search KIN people."))
            }
            val array = JSONArray(response.body)
            val people = buildList {
                for (index in 0 until array.length()) add(personFromJson(array.getJSONObject(index)))
            }
            KinPeopleResult.Success(people)
        } catch (_: java.net.UnknownHostException) {
            KinPeopleResult.Error("KIN server is unreachable. Check your connection.")
        } catch (_: java.net.SocketTimeoutException) {
            KinPeopleResult.Error("KIN search took too long to respond.")
        } catch (_: Exception) {
            KinPeopleResult.Error("Could not search KIN right now.")
        }
    }

    override suspend fun loadFriendRequests(): KinPeopleResult<KinFriendRequests> = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.authorizedRequest("GET", "/v1/friend-requests")
            if (response.code !in 200..299) {
                return@withContext KinPeopleResult.Error(apiClient.errorDetail(response, "Could not load friend requests."))
            }
            val root = JSONObject(response.body)
            KinPeopleResult.Success(
                KinFriendRequests(
                    incoming = friendRequestsFromArray(root.getJSONArray("incoming"), "incoming_pending"),
                    outgoing = friendRequestsFromArray(root.getJSONArray("outgoing"), "outgoing_pending"),
                ),
            )
        } catch (_: java.net.UnknownHostException) {
            KinPeopleResult.Error("KIN server is unreachable. Check your connection.")
        } catch (_: java.net.SocketTimeoutException) {
            KinPeopleResult.Error("KIN friend requests took too long to load.")
        } catch (_: Exception) {
            KinPeopleResult.Error("Could not load KIN friend requests right now.")
        }
    }

    override suspend fun sendFriendRequest(username: String): KinPeopleResult<KinRemotePerson> = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.authorizedRequest("POST", "/v1/friend-requests/${encode(username.trim().removePrefix("@"))}")
            if (response.code !in 200..299) {
                return@withContext KinPeopleResult.Error(apiClient.errorDetail(response, "Could not send friend request."))
            }
            KinPeopleResult.Success(personFromJson(JSONObject(response.body)))
        } catch (_: java.net.UnknownHostException) {
            KinPeopleResult.Error("KIN server is unreachable. Check your connection.")
        } catch (_: java.net.SocketTimeoutException) {
            KinPeopleResult.Error("KIN took too long to send the request.")
        } catch (_: Exception) {
            KinPeopleResult.Error("Could not send friend request right now.")
        }
    }

    override suspend fun acceptFriendRequest(requestId: Int): KinPeopleResult<KinRemotePerson> = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.authorizedRequest("POST", "/v1/friend-requests/$requestId/accept")
            if (response.code !in 200..299) {
                return@withContext KinPeopleResult.Error(apiClient.errorDetail(response, "Could not accept friend request."))
            }
            val person = personFromJson(JSONObject(response.body))
            cachePerson(person)
            KinPeopleResult.Success(person)
        } catch (_: java.net.UnknownHostException) {
            KinPeopleResult.Error("KIN server is unreachable. Check your connection.")
        } catch (_: java.net.SocketTimeoutException) {
            KinPeopleResult.Error("KIN took too long to accept the request.")
        } catch (_: Exception) {
            KinPeopleResult.Error("Could not accept friend request right now.")
        }
    }

    override suspend fun declineFriendRequest(requestId: Int): KinPeopleResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.authorizedRequest("DELETE", "/v1/friend-requests/$requestId")
            if (response.code !in 200..299) {
                return@withContext KinPeopleResult.Error(apiClient.errorDetail(response, "Could not remove friend request."))
            }
            KinPeopleResult.Success(Unit)
        } catch (_: Exception) {
            KinPeopleResult.Error("Could not update friend request right now.")
        }
    }

    override suspend fun removeConnection(personId: String, username: String): KinPeopleResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.authorizedRequest("DELETE", "/v1/connections/${encode(username)}")
            if (response.code !in 200..299) {
                return@withContext KinPeopleResult.Error(apiClient.errorDetail(response, "Could not remove this connection."))
            }
            removeLocalPerson(personId)
            KinPeopleResult.Success(Unit)
        } catch (_: Exception) {
            KinPeopleResult.Error("Could not remove this KIN connection right now.")
        }
    }

    override suspend fun loadBlockedPeople(): KinPeopleResult<List<KinRemotePerson>> = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.authorizedRequest("GET", "/v1/blocks")
            if (response.code !in 200..299) {
                return@withContext KinPeopleResult.Error(apiClient.errorDetail(response, "Could not load blocked people."))
            }
            val array = JSONArray(response.body)
            val people = buildList {
                for (index in 0 until array.length()) add(personFromJson(array.getJSONObject(index), "blocked"))
            }
            KinPeopleResult.Success(people)
        } catch (_: Exception) {
            KinPeopleResult.Error("Could not load blocked people right now.")
        }
    }

    override suspend fun blockPerson(personId: String, username: String): KinPeopleResult<KinRemotePerson> = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.authorizedRequest("POST", "/v1/blocks/${encode(username)}")
            if (response.code !in 200..299) {
                return@withContext KinPeopleResult.Error(apiClient.errorDetail(response, "Could not block this person."))
            }
            val person = personFromJson(JSONObject(response.body), "blocked")
            removeLocalPerson(personId)
            KinPeopleResult.Success(person)
        } catch (_: Exception) {
            KinPeopleResult.Error("Could not block this person right now.")
        }
    }

    override suspend fun unblockPerson(username: String): KinPeopleResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.authorizedRequest("DELETE", "/v1/blocks/${encode(username)}")
            if (response.code !in 200..299) {
                return@withContext KinPeopleResult.Error(apiClient.errorDetail(response, "Could not unblock this person."))
            }
            KinPeopleResult.Success(Unit)
        } catch (_: Exception) {
            KinPeopleResult.Error("Could not unblock this person right now.")
        }
    }

    private suspend fun removeLocalPerson(personId: String) {
        dao.clearPersonCircles(personId)
        dao.clearMessages(personId)
        dao.deletePerson(personId)
    }

    private suspend fun cachePerson(person: KinRemotePerson) {
        dao.insertPersonIfMissing(KinPersonEntity(id = person.id, displayName = person.displayName, handle = person.handle))
        dao.updatePersonIdentity(personId = person.id, displayName = person.displayName, handle = person.handle)
    }

    private fun friendRequestsFromArray(array: JSONArray, relationship: String): List<KinFriendRequest> = buildList {
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            add(KinFriendRequest(id = item.getInt("id"), person = personFromJson(item.getJSONObject("user"), relationship)))
        }
    }

    private fun personFromJson(json: JSONObject, relationship: String? = null): KinRemotePerson = KinRemotePerson(
        id = json.getInt("id").toString(),
        displayName = json.getString("display_name"),
        username = json.getString("username"),
        bio = json.optString("bio"),
        skinId = json.optString("skin_id", "kin-original"),
        relationship = relationship ?: json.optString("relationship", "none"),
    )

    private fun encode(value: String): String = URLEncoder.encode(value.trim().removePrefix("@"), Charsets.UTF_8.name())
}
