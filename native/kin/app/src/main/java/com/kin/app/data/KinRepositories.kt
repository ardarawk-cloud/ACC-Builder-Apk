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

        val starterPeople = listOf(
            Triple(KinPersonEntity("maya", "Maya", "@maya", "Met through studio project"), listOf("work", "close-friends"), Unit),
            Triple(KinPersonEntity("raka", "Raka", "@raka", "Usually online at night"), listOf("gaming"), Unit),
            Triple(KinPersonEntity("nadia", "Nadia", "@nadia", "Family circle"), listOf("family"), Unit),
        )
        starterPeople.forEach { (person, circleIds, _) ->
            dao.upsertPerson(person)
            dao.clearPersonCircles(person.id)
            circleIds.forEach { circleId ->
                dao.linkPersonCircle(KinPersonCircleCrossRef(person.id, circleId))
            }
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
