package com.williamchandra.kettoryn.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises WHERE deletedAt IS NULL AND (isBuiltIn = 0 OR sourceSlug IS NOT NULL) ORDER BY name ASC")
    fun observeActiveExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE deletedAt IS NULL AND (isBuiltIn = 0 OR sourceSlug IS NOT NULL) ORDER BY name ASC")
    suspend fun getActiveExercises(): List<ExerciseEntity>

    @Query("SELECT * FROM exercises ORDER BY id ASC")
    suspend fun getAllExercises(): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE isBuiltIn = 1")
    suspend fun getBuiltInExercises(): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE id = :id LIMIT 1")
    suspend fun getExerciseById(id: Long): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE id = :id LIMIT 1")
    fun observeExerciseById(id: Long): Flow<ExerciseEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExercises(exercises: List<ExerciseEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllExercisesExact(exercises: List<ExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExercise(exercise: ExerciseEntity): Long

    @Update
    suspend fun updateExercise(exercise: ExerciseEntity)

    @Query("UPDATE exercises SET deletedAt = :deletedAt WHERE id = :id AND isBuiltIn = 0")
    suspend fun softDeleteCustomExercise(id: Long, deletedAt: Long): Int

    @Query("DELETE FROM exercises")
    suspend fun deleteAllExercises()
}

@Dao
interface TemplateDao {
    @Transaction
    @Query("SELECT * FROM workout_templates ORDER BY updatedAt DESC")
    fun observeTemplatesWithExercises(): Flow<List<TemplateWithExercises>>

    @Query("SELECT * FROM workout_templates ORDER BY id ASC")
    suspend fun getAllTemplates(): List<WorkoutTemplateEntity>

    @Query("SELECT * FROM template_exercises ORDER BY id ASC")
    suspend fun getAllTemplateExercises(): List<TemplateExerciseEntity>

    @Query("SELECT * FROM template_sets ORDER BY id ASC")
    suspend fun getAllTemplateSets(): List<TemplateSetEntity>

    @Transaction
    @Query("SELECT * FROM workout_templates WHERE id = :id LIMIT 1")
    suspend fun getTemplateWithExercises(id: Long): TemplateWithExercises?

    @Transaction
    @Query("SELECT * FROM workout_templates WHERE id = :id LIMIT 1")
    fun observeTemplateWithExercises(id: Long): Flow<TemplateWithExercises?>

    @Insert
    suspend fun insertTemplate(template: WorkoutTemplateEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllTemplatesExact(templates: List<WorkoutTemplateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllTemplateExercisesExact(exercises: List<TemplateExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllTemplateSetsExact(sets: List<TemplateSetEntity>)

    @Update
    suspend fun updateTemplate(template: WorkoutTemplateEntity)

    @Query("DELETE FROM workout_templates WHERE id = :id")
    suspend fun deleteTemplateById(id: Long)

    @Query("DELETE FROM workout_templates")
    suspend fun deleteAllTemplates()

    @Query("DELETE FROM template_exercises WHERE templateId = :templateId")
    suspend fun deleteTemplateExercises(templateId: Long)

    @Insert
    suspend fun insertTemplateExercise(exercise: TemplateExerciseEntity): Long

    @Insert
    suspend fun insertTemplateSets(sets: List<TemplateSetEntity>)
}

@Dao
interface SessionDao {
    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE status = 'ACTIVE' ORDER BY startedAt DESC LIMIT 1")
    fun observeActiveSession(): Flow<SessionWithExercises?>

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE status = 'ACTIVE' ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActiveSession(): SessionWithExercises?

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionWithExercises(sessionId: Long): SessionWithExercises?

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE id = :sessionId LIMIT 1")
    fun observeSessionWithExercises(sessionId: Long): Flow<SessionWithExercises?>

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE status = 'COMPLETED' ORDER BY startedAt DESC")
    fun observeCompletedSessions(): Flow<List<SessionWithExercises>>

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE status = 'COMPLETED' ORDER BY startedAt DESC")
    suspend fun getCompletedSessions(): List<SessionWithExercises>

    @Query("SELECT * FROM workout_sessions ORDER BY id ASC")
    suspend fun getAllSessions(): List<WorkoutSessionEntity>

    @Query("SELECT * FROM session_exercises ORDER BY id ASC")
    suspend fun getAllSessionExercises(): List<SessionExerciseEntity>

    @Query("SELECT * FROM logged_sets ORDER BY id ASC")
    suspend fun getAllLoggedSets(): List<LoggedSetEntity>

    @Insert
    suspend fun insertSession(session: WorkoutSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSessionsExact(sessions: List<WorkoutSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSessionExercisesExact(exercises: List<SessionExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllLoggedSetsExact(sets: List<LoggedSetEntity>)

    @Update
    suspend fun updateSession(session: WorkoutSessionEntity)

    @Query(
        """
        UPDATE workout_sessions 
        SET status = 'COMPLETED', endedAt = :endedAt, updatedAt = :endedAt 
        WHERE id = :sessionId AND status = 'ACTIVE'
        """
    )
    suspend fun markSessionCompletedIfActive(sessionId: Long, endedAt: Long): Int

    @Query("UPDATE workout_sessions SET title = :title, updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun updateSessionTitle(sessionId: Long, title: String, updatedAt: Long)

    @Query("DELETE FROM workout_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)

    @Query("DELETE FROM workout_sessions")
    suspend fun deleteAllSessions()

    @Insert
    suspend fun insertSessionExercise(sessionExercise: SessionExerciseEntity): Long

    @Query("SELECT * FROM session_exercises WHERE sessionId = :sessionId ORDER BY sortOrder ASC")
    suspend fun getSessionExercises(sessionId: Long): List<SessionExerciseEntity>

    @Query("DELETE FROM session_exercises WHERE id = :sessionExerciseId")
    suspend fun deleteSessionExerciseById(sessionExerciseId: Long)

    @Update
    suspend fun updateSessionExercises(exercises: List<SessionExerciseEntity>)

    @Insert
    suspend fun insertLoggedSet(set: LoggedSetEntity): Long

    @Insert
    suspend fun insertLoggedSets(sets: List<LoggedSetEntity>)

    @Query("SELECT * FROM logged_sets WHERE id = :setId LIMIT 1")
    suspend fun getLoggedSetById(setId: Long): LoggedSetEntity?

    @Query("SELECT exerciseTypeSnapshot FROM session_exercises WHERE id = :id")
    suspend fun getSessionExercisesForType(id: Long): String?

    @Query("SELECT * FROM logged_sets WHERE sessionExerciseId = :sessionExerciseId ORDER BY sortOrder ASC")
    suspend fun getSetsForSessionExercise(sessionExerciseId: Long): List<LoggedSetEntity>

    @Update
    suspend fun updateLoggedSet(set: LoggedSetEntity)

    @Update
    suspend fun updateLoggedSets(sets: List<LoggedSetEntity>)

    @Query("DELETE FROM logged_sets WHERE id = :setId")
    suspend fun deleteLoggedSetById(setId: Long)

    @Transaction
    @Query(
        """
        SELECT se.* FROM session_exercises se
        INNER JOIN workout_sessions ws ON ws.id = se.sessionId
        WHERE ws.status = 'COMPLETED'
          AND (se.exerciseId = :exerciseId OR LOWER(se.nameSnapshot) = LOWER(:exerciseName))
        ORDER BY ws.startedAt DESC
        LIMIT 1
        """
    )
    suspend fun getMostRecentCompletedSessionExercise(
        exerciseId: Long?,
        exerciseName: String
    ): SessionExerciseWithSets?
}

@Dao
interface BodyMeasurementDao {
    @Transaction
    @Query("SELECT * FROM body_measurement_entries ORDER BY recordedAt DESC, id DESC")
    fun observeEntriesWithValues(): Flow<List<BodyMeasurementEntryWithValues>>

    @Transaction
    @Query("SELECT * FROM body_measurement_entries ORDER BY recordedAt DESC, id DESC")
    suspend fun getEntriesWithValues(): List<BodyMeasurementEntryWithValues>

    @Transaction
    @Query("SELECT * FROM body_measurement_entries WHERE id = :id LIMIT 1")
    suspend fun getEntryWithValuesById(id: Long): BodyMeasurementEntryWithValues?

    @Query("SELECT * FROM body_measurement_entries ORDER BY id ASC")
    suspend fun getAllEntries(): List<BodyMeasurementEntryEntity>

    @Query("SELECT * FROM body_measurement_values ORDER BY id ASC")
    suspend fun getAllValues(): List<BodyMeasurementValueEntity>

    @Insert
    suspend fun insertEntry(entry: BodyMeasurementEntryEntity): Long

    @Update
    suspend fun updateEntry(entry: BodyMeasurementEntryEntity)

    @Query("DELETE FROM body_measurement_entries WHERE id = :id")
    suspend fun deleteEntryById(id: Long)

    @Query("DELETE FROM body_measurement_values WHERE entryId = :entryId")
    suspend fun deleteValuesForEntry(entryId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertValues(values: List<BodyMeasurementValueEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllEntriesExact(entries: List<BodyMeasurementEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllValuesExact(values: List<BodyMeasurementValueEntity>)

    @Query("DELETE FROM body_measurement_entries")
    suspend fun deleteAllEntries()
}

@Database(
    entities = [
        ExerciseEntity::class,
        WorkoutTemplateEntity::class,
        TemplateExerciseEntity::class,
        TemplateSetEntity::class,
        WorkoutSessionEntity::class,
        SessionExerciseEntity::class,
        LoggedSetEntity::class,
        BodyMeasurementEntryEntity::class,
        BodyMeasurementValueEntity::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(KettorynConverters::class)
abstract class KettorynDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun templateDao(): TemplateDao
    abstract fun sessionDao(): SessionDao
    abstract fun bodyMeasurementDao(): BodyMeasurementDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercises ADD COLUMN sourceSlug TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE exercises ADD COLUMN exerciseType TEXT NOT NULL DEFAULT 'weight_reps'")
                db.execSQL("ALTER TABLE exercises ADD COLUMN secondaryMuscles TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE exercises ADD COLUMN isStretch INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE exercises ADD COLUMN customImageKey TEXT DEFAULT NULL")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_exercises_sourceSlug ON exercises(sourceSlug)")
                listOf("template_exercises", "session_exercises").forEach { table ->
                    db.execSQL("ALTER TABLE $table ADD COLUMN sourceSlugSnapshot TEXT DEFAULT NULL")
                    db.execSQL("ALTER TABLE $table ADD COLUMN exerciseTypeSnapshot TEXT NOT NULL DEFAULT 'weight_reps'")
                }
                db.execSQL("ALTER TABLE template_sets ADD COLUMN suggestedDurationSeconds INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE template_sets ADD COLUMN suggestedDistanceMeters INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE logged_sets ADD COLUMN exerciseType TEXT NOT NULL DEFAULT 'weight_reps'")
                db.execSQL("ALTER TABLE logged_sets ADD COLUMN durationSeconds INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE logged_sets ADD COLUMN distanceMeters INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `body_measurement_entries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `recordedAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `note` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_body_measurement_entries_recordedAt`
                    ON `body_measurement_entries` (`recordedAt`)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `body_measurement_values` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `entryId` INTEGER NOT NULL,
                        `metricKey` TEXT NOT NULL,
                        `canonicalValue` INTEGER NOT NULL,
                        FOREIGN KEY(`entryId`) REFERENCES `body_measurement_entries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_body_measurement_values_entryId_metricKey`
                    ON `body_measurement_values` (`entryId`, `metricKey`)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_body_measurement_values_metricKey`
                    ON `body_measurement_values` (`metricKey`)
                    """.trimIndent()
                )
            }
        }
    }
}
