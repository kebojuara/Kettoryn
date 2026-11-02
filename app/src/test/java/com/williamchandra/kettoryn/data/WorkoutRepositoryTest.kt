package com.williamchandra.kettoryn.data

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.williamchandra.kettoryn.data.backup.BackupManager
import com.williamchandra.kettoryn.data.backup.BackupValidationException
import com.williamchandra.kettoryn.data.local.KettorynDatabase
import com.williamchandra.kettoryn.data.local.SetType
import com.williamchandra.kettoryn.data.preferences.LengthUnit
import com.williamchandra.kettoryn.data.preferences.ThemeMode
import com.williamchandra.kettoryn.data.preferences.UserPreferencesRepository
import com.williamchandra.kettoryn.data.preferences.WeightUnit
import com.williamchandra.kettoryn.data.repository.TemplateDraftExercise
import com.williamchandra.kettoryn.data.repository.TemplateDraftSet
import com.williamchandra.kettoryn.data.repository.WorkoutRepository
import com.williamchandra.kettoryn.domain.MeasurementMetric
import com.williamchandra.kettoryn.ui.share.WorkoutShareOptions
import com.williamchandra.kettoryn.ui.share.WorkoutShareRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WorkoutRepositoryTest {

    private val directDispatcher = Executor { it.run() }.asCoroutineDispatcher()
    private lateinit var db: KettorynDatabase
    private lateinit var repository: WorkoutRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, KettorynDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCoroutineContext(directDispatcher)
            .build()
        repository = WorkoutRepository(db)
    }

    private fun closeDatabaseCleanly(database: KettorynDatabase) {
        runCatching { database.close() }
        runCatching { database.openHelper.close() }
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) {
            closeDatabaseCleanly(db)
        }
    }

    @Test
    fun `seed exercises is idempotent across multiple invocations`() {
        runTest {
            repository.ensureSeeded()
            val firstCount = repository.observeExercises().first().size
            assertEquals("Full canonical catalog", 302, firstCount)

            repository.ensureSeeded()
            val secondCount = repository.observeExercises().first().size
            assertEquals(firstCount, secondCount)
        }
    }

    @Test
    fun `search and filter exercises handles case, trimming, and muscle filter`() {
        runTest {
            repository.ensureSeeded()
            val benchResults = repository.observeExercises(query = "  BENCH  ", muscleFilter = null).first()
            assertTrue(benchResults.isNotEmpty())
            assertTrue(benchResults.all { it.name.contains("bench", ignoreCase = true) || it.equipment.contains("bench", ignoreCase = true) })

            val chestBenchResults = repository.observeExercises(query = "bench", muscleFilter = "Chest").first()
            assertTrue(chestBenchResults.isNotEmpty())
            assertTrue(chestBenchResults.all { it.primaryMuscle == "Chest" })
        }
    }

    @Test
    fun `custom exercise CRUD and soft delete preserves historical workout snapshots`() {
        runTest {
            repository.ensureSeeded()
            val customId = repository.createCustomExercise(
                name = "Zercher Squat",
                primaryMuscle = "Quadriceps",
                equipment = "Barbell",
                instructions = "Hold barbell in crook of elbows."
            )
            assertTrue(customId > 0)

            val sessionId = repository.startEmptyWorkout("Leg Day", nowMillis = 1000L)
            repository.addExerciseToActiveSession(sessionId, customId)
            val sets = repository.getSessionDetail(sessionId)!!.exercises.first().sets
            val firstSetId = sets.first().id
            repository.updateSet(
                setId = firstSetId,
                weightGrams = 100_000L,
                reps = 5,
                isComplete = true,
                setType = SetType.WORK
            )
            repository.finishWorkout(sessionId, endedAtMillis = 2000L)

            repository.deleteCustomExercise(customId)

            val library = repository.observeExercises().first()
            assertFalse(library.any { it.id == customId })

            val completedDetail = repository.getSessionDetail(sessionId)
            assertNotNull(completedDetail)
            assertEquals(1, completedDetail!!.exercises.size)
            val snapshotEx = completedDetail.exercises.first()
            assertEquals("Zercher Squat", snapshotEx.sessionExercise.nameSnapshot)
            assertEquals("Quadriceps", snapshotEx.sessionExercise.muscleSnapshot)
            assertEquals("Barbell", snapshotEx.sessionExercise.equipmentSnapshot)
            assertEquals(100_000L, snapshotEx.sets.first().weightGrams)
            assertEquals(5, snapshotEx.sets.first().reps)
        }
    }

    @Test
    fun `template creation, duplication, deletion and starting workout preserves history independence`() {
        runTest {
            repository.ensureSeeded()
            val exercises = repository.observeExercises().first()
            val squat = exercises.first { it.name.contains("Squat", ignoreCase = true) }
            val bench = exercises.first { it.name.contains("Bench Press", ignoreCase = true) }

            val templateId = repository.saveTemplate(
                templateId = null,
                title = "Upper Lower A",
                exercises = listOf(
                    TemplateDraftExercise(
                        exerciseId = squat.id,
                        sets = listOf(
                            TemplateDraftSet(suggestedReps = 5, suggestedWeightGrams = 100_000L),
                            TemplateDraftSet(suggestedReps = 5, suggestedWeightGrams = 100_000L)
                        )
                    ),
                    TemplateDraftExercise(
                        exerciseId = bench.id,
                        sets = listOf(
                            TemplateDraftSet(suggestedReps = 8, suggestedWeightGrams = 80_000L)
                        )
                    )
                ),
                nowMillis = 1000L
            )

            val duplicatedId = repository.duplicateTemplate(templateId, nowMillis = 1500L)
            assertNotNull(duplicatedId)
            assertEquals(2, repository.observeTemplates().first().size)

            val sessionId = repository.startWorkoutFromTemplate(templateId, nowMillis = 2000L)
            val activeDetail = repository.getSessionDetail(sessionId)!!
            assertEquals("Upper Lower A", activeDetail.session.title)
            assertEquals(2, activeDetail.exercises.size)
            assertEquals(2, activeDetail.exercises[0].sets.size)
            assertEquals(100_000L, activeDetail.exercises[0].sets[0].weightGrams)
            assertEquals(5, activeDetail.exercises[0].sets[0].reps)
            assertFalse(activeDetail.exercises[0].sets[0].isComplete)

            repository.updateSet(
                setId = activeDetail.exercises[0].sets[0].id,
                weightGrams = 102_500L,
                reps = 5,
                isComplete = true,
                setType = SetType.WORK
            )
            repository.finishWorkout(sessionId, endedAtMillis = 5000L)

            repository.deleteTemplate(templateId)
            repository.deleteTemplate(duplicatedId!!)
            assertTrue(repository.observeTemplates().first().isEmpty())

            val history = repository.observeCompletedSessions().first()
            assertEquals(1, history.size)
            assertEquals("Upper Lower A", history.first().session.title)
            assertEquals(102_500L * 5, history.first().totalVolumeGrams)
        }
    }

    @Test
    fun `only one active workout allowed and finishing twice is idempotent`() {
        runTest {
            val firstSessionId = repository.startEmptyWorkout("Morning Push", nowMillis = 1000L)
            val secondAttemptId = repository.startEmptyWorkout("Another Push", nowMillis = 2000L)
            assertEquals(firstSessionId, secondAttemptId)

            val firstFinish = repository.finishWorkout(firstSessionId, endedAtMillis = 3000L)
            val secondFinish = repository.finishWorkout(firstSessionId, endedAtMillis = 4000L)
            assertTrue(firstFinish)
            assertFalse(secondFinish)

            val completed = repository.observeCompletedSessions().first()
            assertEquals(1, completed.size)
            assertEquals(3000L, completed.first().session.endedAt)
            assertNull(repository.observeActiveSession().first())
        }
    }

    @Test
    fun `discarding active workout removes it without creating completed history`() {
        runTest {
            val sessionId = repository.startEmptyWorkout("Draft Workout", nowMillis = 1000L)
            assertNotNull(repository.observeActiveSession().first())

            repository.discardWorkout(sessionId)
            assertNull(repository.observeActiveSession().first())
            assertTrue(repository.observeCompletedSessions().first().isEmpty())
        }
    }

    @Test
    fun `exercise progress summary calculates PR, total sets, volume and session points accurately`() {
        runTest {
            repository.ensureSeeded()
            val bench = repository.observeExercises(query = "Barbell Bench Press").first().first()

            val s1 = repository.startEmptyWorkout("Push 1", initialExerciseId = bench.id, nowMillis = 10_000L)
            val s1Sets = repository.getSessionDetail(s1)!!.exercises.first().sets
            repository.updateSet(s1Sets[0].id, weightGrams = 80_000L, reps = 8, isComplete = true, setType = SetType.WORK)
            repository.updateSet(s1Sets[1].id, weightGrams = 85_000L, reps = 5, isComplete = true, setType = SetType.WORK)
            repository.finishWorkout(s1, endedAtMillis = 12_000L)

            val s2 = repository.startEmptyWorkout("Push 2", initialExerciseId = bench.id, nowMillis = 20_000L)
            val s2Sets = repository.getSessionDetail(s2)!!.exercises.first().sets
            assertEquals(80_000L, s2Sets[0].weightGrams)
            assertEquals(8, s2Sets[0].reps)
            repository.updateSet(s2Sets[0].id, weightGrams = 90_000L, reps = 3, isComplete = true, setType = SetType.WORK)
            repository.finishWorkout(s2, endedAtMillis = 22_000L)

            val progress = repository.observeExerciseProgress(bench.id, bench.name).first()
            assertNotNull(progress)
            assertEquals(90_000L, progress!!.personalRecord?.weightGrams)
            assertEquals(3, progress.personalRecord?.reps)
            assertEquals(3, progress.totalCompletedSets)
            assertEquals(80_000L * 8 + 85_000L * 5 + 90_000L * 3, progress.totalVolumeGrams)
            assertEquals(2, progress.historyPoints.size)
        }
    }

    @Test
    fun `active session and typed sets survive database reopen simulating process death`() {
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbFile = context.getDatabasePath("process_death_test.db")
            dbFile.delete()

            val db1 = Room.databaseBuilder(context, KettorynDatabase::class.java, "process_death_test.db")
                .addMigrations(KettorynDatabase.MIGRATION_1_2, KettorynDatabase.MIGRATION_2_3)
                .allowMainThreadQueries()
                .setQueryCoroutineContext(directDispatcher)
                .build()
            val sessionId = try {
                val repo1 = WorkoutRepository(db1)
                repo1.ensureSeeded()
                val squat = repo1.observeExercises(query = "Barbell Squat").first().first()

                val sid = repo1.startEmptyWorkout("Leg Day Recovery", initialExerciseId = squat.id, nowMillis = 50_000L)
                val initialSets = repo1.getSessionDetail(sid)!!.exercises.first().sets
                repo1.updateSet(initialSets[0].id, weightGrams = 60_000L, reps = 8, isComplete = true, setType = SetType.WORK)
                repo1.updateSet(initialSets[1].id, weightGrams = 65_000L, reps = 6, isComplete = true, setType = SetType.WORK)
                repo1.updateSet(initialSets[2].id, weightGrams = 70_000L, reps = 4, isComplete = false, setType = SetType.WORK)
                sid
            } finally {
                closeDatabaseCleanly(db1)
            }

            val db2 = Room.databaseBuilder(context, KettorynDatabase::class.java, "process_death_test.db")
                .addMigrations(KettorynDatabase.MIGRATION_1_2, KettorynDatabase.MIGRATION_2_3)
                .allowMainThreadQueries()
                .setQueryCoroutineContext(directDispatcher)
                .build()
            try {
                val repo2 = WorkoutRepository(db2)
                repo2.ensureSeeded()

                val recovered = repo2.observeActiveSession().first()
                assertNotNull(recovered)
                assertEquals(sessionId, recovered!!.session.id)
                assertEquals("Leg Day Recovery", recovered.session.title)
                val recoveredSets = recovered.exercises.first().sets
                assertEquals(3, recoveredSets.size)
                assertEquals(60_000L, recoveredSets[0].weightGrams)
                assertTrue(recoveredSets[0].isComplete)
                assertEquals(65_000L, recoveredSets[1].weightGrams)
                assertTrue(recoveredSets[1].isComplete)
                assertEquals(70_000L, recoveredSets[2].weightGrams)
                assertEquals(4, recoveredSets[2].reps)
                assertFalse(recoveredSets[2].isComplete)
            } finally {
                closeDatabaseCleanly(db2)
                dbFile.delete()
            }
        }
    }

    @Test
    fun `editing and deleting completed workout recalculates history and progress`() {
        runTest {
            repository.ensureSeeded()
            val deadlift = repository.observeExercises(query = "Barbell Deadlift").first().first()

            val sId = repository.startEmptyWorkout("Pull Session", initialExerciseId = deadlift.id, nowMillis = 10_000L)
            val sets = repository.getSessionDetail(sId)!!.exercises.first().sets
            repository.updateSet(sets[0].id, weightGrams = 140_000L, reps = 5, isComplete = true, setType = SetType.WORK)
            repository.finishWorkout(sId, endedAtMillis = 15_000L)

            assertEquals(700_000L, repository.observeCompletedSessions().first().first().totalVolumeGrams)

            repository.updateSet(sets[0].id, weightGrams = 150_000L, reps = 5, isComplete = true, setType = SetType.WORK)
            assertEquals(750_000L, repository.observeCompletedSessions().first().first().totalVolumeGrams)
            assertEquals(150_000L, repository.observeExerciseProgress(deadlift.id, deadlift.name).first()?.personalRecord?.weightGrams)

            repository.deleteCompletedSession(sId)
            assertTrue(repository.observeCompletedSessions().first().isEmpty())
            assertNull(repository.observeExerciseProgress(deadlift.id, deadlift.name).first()?.personalRecord)
        }
    }

    @Test
    fun `migration 1 to 2 preserves all v1 workout data and adds body measurement tables`() {
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val migDbFile = context.getDatabasePath("migration_1_2_test.db")
            migDbFile.parentFile?.mkdirs()
            migDbFile.delete()

            // Create a real v1 SQLite database and populate exercises, templates, and sessions
            val rawDb = SQLiteDatabase.openOrCreateDatabase(migDbFile, null)
            try {
                rawDb.execSQL("CREATE TABLE IF NOT EXISTS `exercises` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `normalizedName` TEXT NOT NULL, `primaryMuscle` TEXT NOT NULL, `equipment` TEXT NOT NULL, `instructions` TEXT NOT NULL, `isBuiltIn` INTEGER NOT NULL, `deletedAt` INTEGER)")
                rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_exercises_normalizedName` ON `exercises` (`normalizedName`)")
                rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_exercises_primaryMuscle` ON `exercises` (`primaryMuscle`)")
                rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_exercises_deletedAt` ON `exercises` (`deletedAt`)")

                rawDb.execSQL("CREATE TABLE IF NOT EXISTS `workout_templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)")
                rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_templates_updatedAt` ON `workout_templates` (`updatedAt`)")

                rawDb.execSQL("CREATE TABLE IF NOT EXISTS `template_exercises` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `templateId` INTEGER NOT NULL, `exerciseId` INTEGER, `sortOrder` INTEGER NOT NULL, `exerciseNameSnapshot` TEXT NOT NULL, `muscleSnapshot` TEXT NOT NULL, `equipmentSnapshot` TEXT NOT NULL, FOREIGN KEY(`templateId`) REFERENCES `workout_templates`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )")
                rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_template_exercises_templateId_sortOrder` ON `template_exercises` (`templateId`, `sortOrder`)")
                rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_template_exercises_exerciseId` ON `template_exercises` (`exerciseId`)")

                rawDb.execSQL("CREATE TABLE IF NOT EXISTS `template_sets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `templateExerciseId` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `suggestedReps` INTEGER, `suggestedWeightGrams` INTEGER, FOREIGN KEY(`templateExerciseId`) REFERENCES `template_exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_template_sets_templateExerciseId_sortOrder` ON `template_sets` (`templateExerciseId`, `sortOrder`)")

                rawDb.execSQL("CREATE TABLE IF NOT EXISTS `workout_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `sourceTemplateId` INTEGER, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER, `status` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`sourceTemplateId`) REFERENCES `workout_templates`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )")
                rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_sessions_status_startedAt` ON `workout_sessions` (`status`, `startedAt`)")
                rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_sessions_sourceTemplateId` ON `workout_sessions` (`sourceTemplateId`)")

                rawDb.execSQL("CREATE TABLE IF NOT EXISTS `session_exercises` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `exerciseId` INTEGER, `nameSnapshot` TEXT NOT NULL, `muscleSnapshot` TEXT NOT NULL, `equipmentSnapshot` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, FOREIGN KEY(`sessionId`) REFERENCES `workout_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )")
                rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_session_exercises_sessionId_sortOrder` ON `session_exercises` (`sessionId`, `sortOrder`)")
                rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_session_exercises_exerciseId` ON `session_exercises` (`exerciseId`)")

                rawDb.execSQL("CREATE TABLE IF NOT EXISTS `logged_sets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionExerciseId` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `reps` INTEGER, `weightGrams` INTEGER, `isComplete` INTEGER NOT NULL, `setType` TEXT NOT NULL, FOREIGN KEY(`sessionExerciseId`) REFERENCES `session_exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_logged_sets_sessionExerciseId_sortOrder` ON `logged_sets` (`sessionExerciseId`, `sortOrder`)")

                rawDb.execSQL("INSERT INTO `exercises` VALUES (1, 'Viking Press', 'viking press', 'Shoulders', 'Barbell', '', 0, NULL)")
                rawDb.execSQL("INSERT INTO `workout_sessions` VALUES (10, 'Legacy v1 Session', NULL, 1000, 2000, 'COMPLETED', 2000)")
                rawDb.execSQL("INSERT INTO `session_exercises` VALUES (20, 10, 1, 'Viking Press', 'Shoulders', 'Barbell', 0)")
                rawDb.execSQL("INSERT INTO `logged_sets` VALUES (30, 20, 0, 8, 75000, 1, 'WORK')")
                rawDb.version = 1
            } finally {
                rawDb.close()
            }

            // Open with Room v2 + MIGRATION_1_2
            val migratedDb = Room.databaseBuilder(context, KettorynDatabase::class.java, "migration_1_2_test.db")
                .addMigrations(KettorynDatabase.MIGRATION_1_2, KettorynDatabase.MIGRATION_2_3)
                .allowMainThreadQueries()
                .setQueryCoroutineContext(directDispatcher)
                .build()
            try {
                val migratedRepo = WorkoutRepository(migratedDb)

                val completed = migratedRepo.observeCompletedSessions().first()
                assertEquals(1, completed.size)
                assertEquals("Legacy v1 Session", completed.first().session.title)
                assertEquals(600_000L, completed.first().totalVolumeGrams)

                // Insert a body measurement entry on the migrated v2 DB
                val entryId = migratedRepo.saveMeasurementEntry(
                    entryId = null,
                    recordedAt = 5000L,
                    values = mapOf(
                        MeasurementMetric.BODY_WEIGHT to 78_500L,
                        MeasurementMetric.WAIST to 8100L
                    )
                )
                assertTrue(entryId > 0L)
                val entries = migratedRepo.observeMeasurementEntries().first()
                assertEquals(1, entries.size)
                assertEquals(78_500L, entries.first().valueFor(MeasurementMetric.BODY_WEIGHT))
                assertEquals(8100L, entries.first().valueFor(MeasurementMetric.WAIST))
            } finally {
                closeDatabaseCleanly(migratedDb)
                migDbFile.delete()
            }
        }
    }

    @Test
    fun `body measurements journal supports partial entries, auto lean body mass, edit and delete`() {
        runTest {
            val entryId = repository.saveMeasurementEntry(
                entryId = null,
                recordedAt = 100_000L,
                values = mapOf(
                    MeasurementMetric.BODY_WEIGHT to 80_000L,
                    MeasurementMetric.BODY_FAT to 1500L, // 15.0% -> Lean Body Mass should auto-derive to 68,000g
                    MeasurementMetric.WAIST to 8200L,
                    MeasurementMetric.LEFT_BICEP to 3850L
                ),
                note = "Morning check-in"
            )

            val loaded = repository.getMeasurementEntryById(entryId)
            assertNotNull(loaded)
            assertEquals(80_000L, loaded!!.valueFor(MeasurementMetric.BODY_WEIGHT))
            assertEquals(1500L, loaded.valueFor(MeasurementMetric.BODY_FAT))
            assertEquals(68_000L, loaded.valueFor(MeasurementMetric.LEAN_BODY_MASS))
            assertEquals(8200L, loaded.valueFor(MeasurementMetric.WAIST))
            assertEquals(3850L, loaded.valueFor(MeasurementMetric.LEFT_BICEP))
            // Unentered metrics remain null, never 0
            assertNull(loaded.valueFor(MeasurementMetric.NECK))
            assertNull(loaded.valueFor(MeasurementMetric.RIGHT_CALF))

            // Update entry
            repository.saveMeasurementEntry(
                entryId = entryId,
                recordedAt = 100_000L,
                values = mapOf(
                    MeasurementMetric.BODY_WEIGHT to 79_000L,
                    MeasurementMetric.WAIST to 8100L
                ),
                note = "Updated"
            )
            val updated = repository.getMeasurementEntryById(entryId)!!
            assertEquals(79_000L, updated.valueFor(MeasurementMetric.BODY_WEIGHT))
            assertEquals(8100L, updated.valueFor(MeasurementMetric.WAIST))
            assertNull(updated.valueFor(MeasurementMetric.LEFT_BICEP))

            // Delete entry
            repository.deleteMeasurementEntry(entryId)
            assertTrue(repository.observeMeasurementEntries().first().isEmpty())
        }
    }

    @Test
    fun `portable backup and restore roundtrip preserves all entities and preferences and rejects corrupt archives safely`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val prefsFile1 = File(context.filesDir, "datastore/backup_test_1_${System.nanoTime()}.preferences_pb").apply { delete() }
            val prefsFile2 = File(context.filesDir, "datastore/backup_test_2_${System.nanoTime()}.preferences_pb").apply { delete() }
            val dsScope1 = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val dsScope2 = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val destDb = Room.inMemoryDatabaseBuilder(context, KettorynDatabase::class.java)
                .allowMainThreadQueries()
                .setQueryCoroutineContext(directDispatcher)
                .build()
            try {
                val prefsRepo1 = UserPreferencesRepository(
                    PreferenceDataStoreFactory.create(scope = dsScope1, produceFile = { prefsFile1 })
                )
                val prefsRepo2 = UserPreferencesRepository(
                    PreferenceDataStoreFactory.create(scope = dsScope2, produceFile = { prefsFile2 })
                )

                // Populate source DB + Preferences
                repository.ensureSeeded()
                val customExId = repository.createCustomExercise(
                    name = "Pendlay Row (Heavy)",
                    primaryMuscle = "Back",
                    equipment = "Barbell",
                    instructions = "Strict floor row"
                )
                val tplId = repository.saveTemplate(
                    templateId = null,
                    title = "Pull Power",
                    exercises = listOf(
                        TemplateDraftExercise(
                            exerciseId = customExId,
                            sets = listOf(TemplateDraftSet(suggestedReps = 5, suggestedWeightGrams = 100_000L))
                        )
                    ),
                    nowMillis = 10_000L
                )
                val sId = repository.startWorkoutFromTemplate(tplId, nowMillis = 20_000L)
                val sets = repository.getSessionDetail(sId)!!.exercises.first().sets
                repository.updateSet(sets[0].id, weightGrams = 105_000L, reps = 5, isComplete = true, setType = SetType.WORK)
                repository.finishWorkout(sId, endedAtMillis = 25_000L)

                // Also start an active workout and log a body measurement
                val activeId = repository.startEmptyWorkout("Active Evening Arms", initialExerciseId = customExId, nowMillis = 30_000L)
                val mId = repository.saveMeasurementEntry(
                    entryId = null,
                    recordedAt = 35_000L,
                    values = mapOf(
                        MeasurementMetric.BODY_WEIGHT to 82_400L,
                        MeasurementMetric.CHEST to 10450L,
                        MeasurementMetric.WAIST to 8150L
                    ),
                    note = "Pre-transfer"
                )

                prefsRepo1.setWeightUnit(WeightUnit.LB)
                prefsRepo1.setLengthUnit(LengthUnit.IN)
                prefsRepo1.setDefaultRestSeconds(135)
                prefsRepo1.setThemeMode(ThemeMode.LIGHT)

                // Export to bytes
                val baos = ByteArrayOutputStream()
                BackupManager.exportBackupToStream(
                    outputStream = baos,
                    workoutRepository = repository,
                    preferencesRepository = prefsRepo1,
                    nowMillis = 40_000L
                )
                val archiveBytes = baos.toByteArray()
                assertTrue(archiveBytes.isNotEmpty())

                // Stage & inspect preview
                val staged = BackupManager.inspectAndStageBackup(ByteArrayInputStream(archiveBytes))
                assertEquals(1, staged.preview.customExercisesCount)
                assertEquals(1, staged.preview.templatesCount)
                assertEquals(1, staged.preview.completedWorkoutsCount)
                assertEquals(1, staged.preview.activeWorkoutsCount)
                assertEquals(1, staged.preview.measurementEntriesCount)
                assertEquals(3, staged.preview.measurementValuesCount)
                assertEquals(WeightUnit.LB, staged.preview.weightUnit)
                assertEquals(LengthUnit.IN, staged.preview.lengthUnit)

                // Restore onto a brand-new destination DB (simulating fresh phone)
                val destRepo = WorkoutRepository(destDb)

                BackupManager.applyStagedBackup(staged, destRepo, prefsRepo2)

                assertEquals(1, destRepo.observeTemplates().first().size)
                assertEquals(1, destRepo.observeCompletedSessions().first().size)
                assertEquals(525_000L, destRepo.observeCompletedSessions().first().first().totalVolumeGrams)
                assertEquals(activeId, destRepo.observeActiveSession().first()?.session?.id)
                val restoredMeas = destRepo.observeMeasurementEntries().first()
                assertEquals(1, restoredMeas.size)
                assertEquals(mId, restoredMeas.first().entry.id)
                assertEquals(82_400L, restoredMeas.first().valueFor(MeasurementMetric.BODY_WEIGHT))
                assertEquals(10450L, restoredMeas.first().valueFor(MeasurementMetric.CHEST))

                val restoredPrefs = prefsRepo2.getSnapshot()
                assertEquals(WeightUnit.LB, restoredPrefs.weightUnit)
                assertEquals(LengthUnit.IN, restoredPrefs.lengthUnit)
                assertEquals(135, restoredPrefs.defaultRestSeconds)
                assertEquals(ThemeMode.LIGHT, restoredPrefs.themeMode)

                // Now verify corrupt archive and tampered checksum fail BEFORE touching destRepo!
                try {
                    BackupManager.inspectAndStageBackup(ByteArrayInputStream("not a zip".toByteArray()))
                    fail("Expected BackupValidationException for non-zip bytes")
                } catch (_: BackupValidationException) {
                    // Expected
                }

                // Tamper with data.json inside the zip so checksum fails
                val tamperedOut = ByteArrayOutputStream()
                ZipInputStream(ByteArrayInputStream(archiveBytes)).use { zis ->
                    ZipOutputStream(tamperedOut).use { zos ->
                        while (true) {
                            val entry = zis.nextEntry ?: break
                            val bytes = zis.readBytes()
                            zos.putNextEntry(ZipEntry(entry.name))
                            if (entry.name == "data.json") {
                                val modified = String(bytes, StandardCharsets.UTF_8).replace("Pull Power", "Tampered")
                                zos.write(modified.toByteArray(StandardCharsets.UTF_8))
                            } else {
                                zos.write(bytes)
                            }
                            zos.closeEntry()
                        }
                    }
                }

                try {
                    BackupManager.inspectAndStageBackup(ByteArrayInputStream(tamperedOut.toByteArray()))
                    fail("Expected BackupValidationException for checksum mismatch")
                } catch (e: BackupValidationException) {
                    assertTrue(e.message!!.contains("checksum", ignoreCase = true))
                }

                // Unsupported future schemaVersion (e.g. schemaVersion = 99) is rejected safely
                val futureSchemaOut = ByteArrayOutputStream()
                ZipInputStream(ByteArrayInputStream(archiveBytes)).use { zis ->
                    ZipOutputStream(futureSchemaOut).use { zos ->
                        while (true) {
                            val entry = zis.nextEntry ?: break
                            val bytes = zis.readBytes()
                            zos.putNextEntry(ZipEntry(entry.name))
                            if (entry.name == "manifest.json") {
                                val modified = String(bytes, StandardCharsets.UTF_8)
                                    .replace("\"schemaVersion\": 3", "\"schemaVersion\": 99")
                                zos.write(modified.toByteArray(StandardCharsets.UTF_8))
                            } else {
                                zos.write(bytes)
                            }
                            zos.closeEntry()
                        }
                    }
                }
                try {
                    BackupManager.inspectAndStageBackup(ByteArrayInputStream(futureSchemaOut.toByteArray()))
                    fail("Expected BackupValidationException for unsupported schemaVersion")
                } catch (e: BackupValidationException) {
                    assertTrue(e.message!!.contains("schema version", ignoreCase = true))
                }

                // Broken foreign-key relationship (even with recomputed SHA-256) is rejected before restore
                val brokenSnapshot = staged.snapshot.copy(
                    sessionExercises = staged.snapshot.sessionExercises.map { it.copy(sessionId = 999_999L) }
                )
                val brokenFkOut = ByteArrayOutputStream()
                BackupManager.writeBackupArchive(
                    outputStream = brokenFkOut,
                    snapshot = brokenSnapshot,
                    prefs = prefsRepo1.getSnapshot(),
                    nowMillis = 45_000L
                )
                try {
                    BackupManager.inspectAndStageBackup(ByteArrayInputStream(brokenFkOut.toByteArray()))
                    fail("Expected BackupValidationException for broken foreign key")
                } catch (e: BackupValidationException) {
                    assertTrue(e.message!!.contains("missing session", ignoreCase = true))
                }

                // Verify destRepo data is 100% untouched after failed imports
                assertEquals("Pull Power", destRepo.observeTemplates().first().first().template.title)
                assertEquals(1, destRepo.observeCompletedSessions().first().size)
            } finally {
                closeDatabaseCleanly(destDb)
                dsScope1.cancel()
                dsScope2.cancel()
                prefsFile1.delete()
                prefsFile2.delete()
            }
        }
    }

    @Test
    fun `workout share renderer creates compact cards in dark and light styles`() {
        runTest {
            repository.ensureSeeded()
            val bench = repository.observeExercises(query = "Barbell Bench Press").first().first()
            val sId = repository.startEmptyWorkout("Upper Strength", initialExerciseId = bench.id, nowMillis = 10_000L)
            val sets = repository.getSessionDetail(sId)!!.exercises.first().sets
            repository.updateSet(sets[0].id, weightGrams = 90_000L, reps = 5, isComplete = true, setType = SetType.WORK)
            repository.finishWorkout(sId, endedAtMillis = 10_000L + 45 * 60_000L)

            val detail = repository.getSessionDetail(sId)!!
            val darkBitmap = WorkoutShareRenderer.renderShareCardBitmap(
                detail = detail,
                weightUnit = WeightUnit.KG,
                options = WorkoutShareOptions(darkCardStyle = true)
            )
            assertEquals(1200, darkBitmap.width)
            assertEquals(800, darkBitmap.height)

            val lightBitmap = WorkoutShareRenderer.renderShareCardBitmap(
                detail = detail,
                weightUnit = WeightUnit.LB,
                options = WorkoutShareOptions(
                    showWorkoutTitle = false,
                    showWeightsAndVolume = false,
                    darkCardStyle = false
                )
            )
            assertEquals(1200, lightBitmap.width)
            assertEquals(800, lightBitmap.height)

            val context = ApplicationProvider.getApplicationContext<Context>()
            val savedFile = WorkoutShareRenderer.saveBitmapToCacheFile(context, darkBitmap)
            assertTrue(savedFile.exists())
            assertTrue(savedFile.length() > 1000L)
            savedFile.delete()
        }
    }
}
