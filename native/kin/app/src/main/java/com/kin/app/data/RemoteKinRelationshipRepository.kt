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
    override suspend fun setPersonCircles(personId: String, circleIds: List<String>) =
        local.setPersonCircles(personId, circleIds)

    override suspend fun syncConnections(): KinPeopleResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.authorizedRequest("GET", "/v1/connections")
            if (response.code !in 200..299) {
                return@withContext KinPeopleResult.Error(
                    apiClient.errorDetail(response, "Could not sync your KIN connections."),
                )
            }
            val array = JSONArray(response.body)
            for (index in 0 until array.length()) {
                cachePerson(personFromJson(array.getJSONObject(index), relationship = "friends"))
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
            val encoded = URLEncoder.encode(normalized, Charsets.UTF_8.name())
            val response = apiClient.authorizedRequest("GET", "/v1/people/search?q=$encoded")
            if (response.code !in 200..299) {
                return@withContext KinPeopleResult.Error(
                    apiClient.errorDetail(response, "Could not search KIN people."),
                )
            }
            val array = JSONArray(response.body)
            val people = buildList {
                for (index in 0 until array.length()) {
                    add(personFromJson(array.getJSONObject(index)))
                }
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
                return@withContext KinPeopleResult.Error(
                    apiClient.errorDetail(response, "Could not load friend requests."),
                )
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

    override suspend fun sendFriendRequest(username: String): KinPeopleResult<KinRemotePerson> =
        withContext(Dispatchers.IO) {
            try {
                val normalized = username.trim().removePrefix("@")
                val encoded = URLEncoder.encode(normalized, Charsets.UTF_8.name())
                val response = apiClient.authorizedRequest("POST", "/v1/friend-requests/$encoded")
                if (response.code !in 200..299) {
                    return@withContext KinPeopleResult.Error(
                        apiClient.errorDetail(response, "Could not send friend request."),
                    )
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

    override suspend fun acceptFriendRequest(requestId: Int): KinPeopleResult<KinRemotePerson> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiClient.authorizedRequest("POST", "/v1/friend-requests/$requestId/accept")
                if (response.code !in 200..299) {
                    return@withContext KinPeopleResult.Error(
                        apiClient.errorDetail(response, "Could not accept friend request."),
                    )
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
                return@withContext KinPeopleResult.Error(
                    apiClient.errorDetail(response, "Could not remove friend request."),
                )
            }
            KinPeopleResult.Success(Unit)
        } catch (_: java.net.UnknownHostException) {
            KinPeopleResult.Error("KIN server is unreachable. Check your connection.")
        } catch (_: java.net.SocketTimeoutException) {
            KinPeopleResult.Error("KIN took too long to update the request.")
        } catch (_: Exception) {
            KinPeopleResult.Error("Could not update friend request right now.")
        }
    }

    private suspend fun cachePerson(person: KinRemotePerson) {
        dao.insertPersonIfMissing(
            KinPersonEntity(
                id = person.id,
                displayName = person.displayName,
                handle = person.handle,
            ),
        )
        // Updating only public identity fields preserves privateNote and Circle links already stored locally.
        dao.updatePersonIdentity(
            personId = person.id,
            displayName = person.displayName,
            handle = person.handle,
        )
    }

    private fun friendRequestsFromArray(array: JSONArray, relationship: String): List<KinFriendRequest> = buildList {
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            add(
                KinFriendRequest(
                    id = item.getInt("id"),
                    person = personFromJson(item.getJSONObject("user"), relationship),
                ),
            )
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
}
