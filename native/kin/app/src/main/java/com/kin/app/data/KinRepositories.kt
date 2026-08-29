package com.kin.app.data

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
    suspend fun savePost(post: KinPostEntity)
}

class LocalKinPostRepository(private val dao: KinDao) : KinPostRepository {
    override fun observePosts(): Flow<List<KinPostEntity>> = dao.observePosts()
    override suspend fun savePost(post: KinPostEntity) = dao.upsertPost(post)
}

interface KinRelationshipRepository {
    fun observeCircles(): Flow<List<KinCircleEntity>>
    fun observePeople(): Flow<List<KinPersonWithCircles>>
    suspend fun ensureStarterData()
    suspend fun savePrivateNote(personId: String, note: String)
    suspend fun setPersonCircles(personId: String, circleIds: List<String>)
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

        // v0.3.35 and earlier seeded three fake people for UI demos.
        // Remove them once so real social screens never look populated by fake friends.
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
}
