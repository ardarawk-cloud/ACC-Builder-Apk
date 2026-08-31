package com.kin.app.data

import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface KinProfileRepository {
    fun observeProfile(): Flow<KinProfileEntity?>
    suspend fun saveProfile(profile: KinProfileEntity)
}

class LocalKinProfileRepository(private val dao: KinDao) : KinProfileRepository {
    override fun observeProfile(): Flow<KinProfileEntity?> = dao.observeProfile()
    override suspend fun saveProfile(profile: KinProfileEntity) = dao.upsertProfile(profile)
}

interface KinPostRepository {
    fun observePosts(): Flow<List<KinPostEntity>>
    suspend fun refreshFeed(): KinPeopleResult<Unit>
    suspend fun publishPost(post: KinPostEntity, allowedUserIds: List<String> = emptyList()): KinPeopleResult<KinPostEntity>
    suspend fun editPost(postId: String, text: String): KinPeopleResult<KinPostEntity>
    suspend fun deletePost(postId: String): KinPeopleResult<Unit>
}

class LocalKinPostRepository(private val dao: KinDao) : KinPostRepository {
    override fun observePosts(): Flow<List<KinPostEntity>> = dao.observePosts()

    override suspend fun refreshFeed(): KinPeopleResult<Unit> = KinPeopleResult.Success(Unit)

    override suspend fun publishPost(post: KinPostEntity, allowedUserIds: List<String>): KinPeopleResult<KinPostEntity> {
        dao.upsertPost(post)
        return KinPeopleResult.Success(post)
    }

    override suspend fun editPost(postId: String, text: String): KinPeopleResult<KinPostEntity> {
        return KinPeopleResult.Error("Remote post editing is unavailable in local-only mode.")
    }

    override suspend fun deletePost(postId: String): KinPeopleResult<Unit> {
        dao.deletePost(postId)
        return KinPeopleResult.Success(Unit)
    }
}

interface KinRelationshipRepository {
    fun observeCircles(): Flow<List<KinCircleEntity>>
    fun observePeople(): Flow<List<KinPersonWithCircles>>
    suspend fun ensureStarterData()
    suspend fun savePrivateNote(personId: String, note: String)
    suspend fun setPersonCircles(personId: String, circleIds: List<String>)

    suspend fun syncConnections(): KinPeopleResult<Unit>
    suspend fun searchPeople(query: String): KinPeopleResult<List<KinRemotePerson>>
    suspend fun loadFriendRequests(): KinPeopleResult<KinFriendRequests>
    suspend fun sendFriendRequest(username: String): KinPeopleResult<KinRemotePerson>
    suspend fun acceptFriendRequest(requestId: Int): KinPeopleResult<KinRemotePerson>
    suspend fun declineFriendRequest(requestId: Int): KinPeopleResult<Unit>
    suspend fun removeConnection(personId: String, username: String): KinPeopleResult<Unit>
    suspend fun loadBlockedPeople(): KinPeopleResult<List<KinRemotePerson>>
    suspend fun blockPerson(personId: String, username: String): KinPeopleResult<KinRemotePerson>
    suspend fun unblockPerson(username: String): KinPeopleResult<Unit>
}

class LocalKinRelationshipRepository(private val dao: KinDao) : KinRelationshipRepository {
    override fun observeCircles(): Flow<List<KinCircleEntity>> = dao.observeCircles()
    override fun observePeople(): Flow<List<KinPersonWithCircles>> = dao.observePeople()

    override suspend fun ensureStarterData() {
        dao.insertCircles(
            listOf(
                KinCircleEntity("close-friends", "Close Friends", true),
                KinCircleEntity("family", "Family", true),
                KinCircleEntity("work", "Work", true),
                KinCircleEntity("school", "School", true),
                KinCircleEntity("gaming", "Gaming", true),
                KinCircleEntity("client", "Client", true),
                KinCircleEntity("acquaintance", "Acquaintance", true),
            ),
        )
        listOf("maya", "raka", "nadia").forEach { demoId ->
            dao.clearPersonCircles(demoId)
            dao.deletePerson(demoId)
        }
    }

    override suspend fun savePrivateNote(personId: String, note: String) {
        dao.updatePrivateNote(personId, note)
    }

    override suspend fun setPersonCircles(personId: String, circleIds: List<String>) {
        dao.clearPersonCircles(personId)
        circleIds.distinct().forEach { circleId ->
            dao.linkPersonCircle(KinPersonCircleCrossRef(personId, circleId))
        }
    }

    override suspend fun syncConnections(): KinPeopleResult<Unit> = KinPeopleResult.Success(Unit)
    override suspend fun searchPeople(query: String): KinPeopleResult<List<KinRemotePerson>> = KinPeopleResult.Success(emptyList())
    override suspend fun loadFriendRequests(): KinPeopleResult<KinFriendRequests> = KinPeopleResult.Success(KinFriendRequests())
    override suspend fun sendFriendRequest(username: String): KinPeopleResult<KinRemotePerson> = KinPeopleResult.Error("KIN server is not configured for real connections.")
    override suspend fun acceptFriendRequest(requestId: Int): KinPeopleResult<KinRemotePerson> = KinPeopleResult.Error("KIN server is not configured for real connections.")
    override suspend fun declineFriendRequest(requestId: Int): KinPeopleResult<Unit> = KinPeopleResult.Error("KIN server is not configured for real connections.")

    override suspend fun removeConnection(personId: String, username: String): KinPeopleResult<Unit> {
        dao.clearPersonCircles(personId)
        dao.deletePerson(personId)
        return KinPeopleResult.Success(Unit)
    }

    override suspend fun loadBlockedPeople(): KinPeopleResult<List<KinRemotePerson>> = KinPeopleResult.Success(emptyList())
    override suspend fun blockPerson(personId: String, username: String): KinPeopleResult<KinRemotePerson> = KinPeopleResult.Error("Blocking requires the KIN server.")
    override suspend fun unblockPerson(username: String): KinPeopleResult<Unit> = KinPeopleResult.Error("Unblocking requires the KIN server.")
}

interface KinChatRepository {
    fun observeMessages(personId: String): Flow<List<KinMessageEntity>>
    suspend fun refreshMessages(personId: String, username: String): KinPeopleResult<Unit>
    suspend fun sendMessage(personId: String, username: String, text: String): KinPeopleResult<KinMessageEntity>
}

class LocalKinChatRepository(private val dao: KinDao) : KinChatRepository {
    override fun observeMessages(personId: String): Flow<List<KinMessageEntity>> = dao.observeMessages(personId)

    override suspend fun refreshMessages(personId: String, username: String): KinPeopleResult<Unit> = KinPeopleResult.Success(Unit)

    override suspend fun sendMessage(personId: String, username: String, text: String): KinPeopleResult<KinMessageEntity> {
        val message = KinMessageEntity(
            id = UUID.randomUUID().toString(),
            otherPersonId = personId,
            senderId = "local",
            senderDisplayName = "You",
            senderUsername = "local",
            text = text.trim(),
            mine = true,
            createdAt = System.currentTimeMillis(),
        )
        dao.upsertMessage(message)
        return KinPeopleResult.Success(message)
    }
}
