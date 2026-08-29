package com.kin.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

@Entity(tableName = "kin_posts")
data class KinPostEntity(
    @PrimaryKey val id: String,
    val authorDisplayName: String,
    val authorUsername: String,
    val text: String,
    val audience: String,
    val feeling: String? = null,
    val listening: String? = null,
    val location: String? = null,
    val withPeople: String? = null,
    val createdAt: Long,
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

    @Query("DELETE FROM kin_people WHERE id = :personId")
    suspend fun deletePerson(personId: String)

    @Query("UPDATE kin_people SET privateNote = :note WHERE id = :personId")
    suspend fun updatePrivateNote(personId: String, note: String)

    @Transaction
    @Query("SELECT * FROM kin_people ORDER BY displayName COLLATE NOCASE ASC")
    fun observePeople(): Flow<List<KinPersonWithCircles>>

    @Query("SELECT * FROM kin_posts ORDER BY createdAt DESC")
    fun observePosts(): Flow<List<KinPostEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPost(post: KinPostEntity)
}

@Database(
    entities = [
        KinProfileEntity::class,
        KinPersonEntity::class,
        KinCircleEntity::class,
        KinPersonCircleCrossRef::class,
        KinPostEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class KinDatabase : RoomDatabase() {
    abstract fun kinDao(): KinDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `kin_posts` (
                        `id` TEXT NOT NULL,
                        `authorDisplayName` TEXT NOT NULL,
                        `authorUsername` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `audience` TEXT NOT NULL,
                        `feeling` TEXT,
                        `listening` TEXT,
                        `location` TEXT,
                        `withPeople` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
            }
        }

        fun create(context: Context): KinDatabase = Room.databaseBuilder(
            context.applicationContext,
            KinDatabase::class.java,
            "kin.db",
        )
            .addMigrations(MIGRATION_1_2)
            .build()
    }
}
