package com.kin.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "kin_profile")
data class KinProfileEntity(
    @PrimaryKey val id: Int = 1,
    val displayName: String,
    val username: String,
    val email: String,
    val bio: String = "",
    val skinId: String = "kin-original",
)

@Entity(tableName = "kin_people")
data class KinPersonEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val handle: String,
    val privateNote: String = "",
)

@Entity(tableName = "kin_circles")
data class KinCircleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val builtIn: Boolean = false,
)

@Entity(
    tableName = "kin_person_circle",
    primaryKeys = ["personId", "circleId"],
    indices = [Index("circleId")],
)
data class KinPersonCircleCrossRef(
    val personId: String,
    val circleId: String,
)

data class KinPersonWithCircles(
    @Embedded val person: KinPersonEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = KinPersonCircleCrossRef::class,
            parentColumn = "personId",
            entityColumn = "circleId",
        ),
    )
    val circles: List<KinCircleEntity>,
)

@Dao
interface KinDao {
    @Query("SELECT * FROM kin_profile WHERE id = 1 LIMIT 1")
    fun observeProfile(): Flow<KinProfileEntity?>

    @Query("SELECT * FROM kin_profile WHERE id = 1 LIMIT 1")
    suspend fun getProfile(): KinProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: KinProfileEntity)

    @Query("SELECT * FROM kin_circles ORDER BY builtIn DESC, name ASC")
    fun observeCircles(): Flow<List<KinCircleEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCircles(circles: List<KinCircleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPerson(person: KinPersonEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun linkPersonCircle(link: KinPersonCircleCrossRef)

    @Query("DELETE FROM kin_person_circle WHERE personId = :personId")
    suspend fun clearPersonCircles(personId: String)

    @Query("UPDATE kin_people SET privateNote = :note WHERE id = :personId")
    suspend fun updatePrivateNote(personId: String, note: String)

    @Transaction
    @Query("SELECT * FROM kin_people ORDER BY displayName COLLATE NOCASE ASC")
    fun observePeople(): Flow<List<KinPersonWithCircles>>
}

@Database(
    entities = [
        KinProfileEntity::class,
        KinPersonEntity::class,
        KinCircleEntity::class,
        KinPersonCircleCrossRef::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class KinDatabase : RoomDatabase() {
    abstract fun kinDao(): KinDao

    companion object {
        fun create(context: Context): KinDatabase = Room.databaseBuilder(
            context.applicationContext,
            KinDatabase::class.java,
            "kin.db",
        ).build()
    }
}
