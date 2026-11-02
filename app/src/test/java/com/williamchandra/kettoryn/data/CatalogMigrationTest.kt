package com.williamchandra.kettoryn.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.williamchandra.kettoryn.data.local.*
import com.williamchandra.kettoryn.data.repository.WorkoutRepository
import com.williamchandra.kettoryn.data.backup.BackupManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CatalogMigrationTest {
    private fun fixture(name: String) = javaClass.classLoader!!.getResourceAsStream("migration-baseline/$name")!!

    @Test fun actualV2DatabaseUpgradesWithoutChangingAnyExistingFieldOrId() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "v63-migration-${System.nanoTime()}.db"
        val file = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        fixture("v2-populated.db").use { input -> file.outputStream().use { input.copyTo(it) } }
        val expected = fixture("row-manifest.json").bufferedReader().use { JSONObject(it.readText()) }
        val db = Room.databaseBuilder(context, KettorynDatabase::class.java, name)
            .addMigrations(KettorynDatabase.MIGRATION_1_2, KettorynDatabase.MIGRATION_2_3).allowMainThreadQueries().build()
        val repo = WorkoutRepository(db)
        try {
            repo.ensureSeeded(); repo.ensureSeeded()
            for (table in expected.keys()) {
                if (table in listOf("android_metadata", "room_master_table")) continue
                val entry = expected.getJSONObject(table)
                val columns = entry.getJSONArray("columns")
                val rows = entry.getJSONArray("rows")
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONArray(i)
                    val projection = (0 until columns.length()).joinToString(",") { columns.getString(it) }
                    db.openHelper.writableDatabase.query("SELECT $projection FROM $table WHERE id = ?", arrayOf(row.getLong(0))).use { cursor ->
                        assertTrue("Preserved row $table/${row.getLong(0)}", cursor.moveToFirst())
                        for (j in 0 until columns.length()) {
                            val actual = if (cursor.isNull(j)) null else cursor.getString(j)
                            val before = if (row.isNull(j)) null else row.get(j).toString()
                            assertEquals("Unchanged $table/${row.getLong(0)}/${columns.getString(j)}", before, actual)
                        }
                    }
                }
            }
            val visible = repo.observeExercises().first()
            assertEquals(302, visible.count { it.isBuiltIn })
            assertEquals(302, visible.mapNotNull { it.sourceSlug }.distinct().size)
            assertEquals("Bambang", visible.single { !it.isBuiltIn }.name)
            val oldRoutine = repo.observeTemplates().first().single()
            assertEquals("Push Day", oldRoutine.template.title)
            assertTrue(oldRoutine.exercises.any { it.templateExercise.exerciseNameSnapshot == "Barbell Bench Press" })
            assertNotNull(repo.getActiveSession())
            assertEquals(1, repo.observeCompletedSessions().first().size)
            val old = repo.getExerciseById(1)!!
            assertNull(old.sourceSlug)
            assertEquals("Barbell Bench Press", old.name)
        } finally { db.close(); file.delete() }
    }

    @Test fun oldFormatBackupKeepsLegacyIdentityAndCanonicalSeedIsIdempotent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val staged = fixture("old-format-backup.kettoryn").use { BackupManager.inspectAndStageBackup(it) }
        assertEquals(2, staged.preview.schemaVersion)
        assertEquals(45, staged.snapshot.exercises.size)
        val db = Room.inMemoryDatabaseBuilder(context, KettorynDatabase::class.java).allowMainThreadQueries().build()
        val repo = WorkoutRepository(db)
        try {
            repo.replaceAllDatabaseData(staged.snapshot); repo.ensureSeeded(); repo.ensureSeeded()
            assertEquals(303, repo.observeExercises().first().size)
            val before = repo.exportDatabaseSnapshot()
            repo.replaceAllDatabaseData(before); repo.ensureSeeded()
            assertEquals(before, repo.exportDatabaseSnapshot())
            assertEquals(staged.snapshot.loggedSets, before.loggedSets)
            assertEquals(staged.snapshot.sessions, before.sessions)
            assertEquals(staged.snapshot.templateExercises, before.templateExercises)
            assertEquals(staged.snapshot.exercises.single { !it.isBuiltIn }, before.exercises.single { !it.isBuiltIn })
        } finally { db.close() }
    }

    @Test fun canonicalTypesDoNotInventCardioWeightsOrStrengthRecords() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, KettorynDatabase::class.java).allowMainThreadQueries().build()
        val repo = WorkoutRepository(db)
        try {
            repo.ensureSeeded()
            val running = repo.observeExercises().first().single { it.sourceSlug == "running" }
            val id = repo.startEmptyWorkout("Run", running.id)
            val detail = repo.getSessionDetail(id)!!
            assertEquals("running", detail.exercises.single().sessionExercise.sourceSlugSnapshot)
            val set = detail.exercises.single().sets.first()
            repo.updateTimedSet(set.id, 1800, 5000)
            repo.updateSet(set.id, null, null, true, SetType.WORK)
            repo.finishWorkout(id)
            val summary = repo.observeCompletedSessions().first().single()
            assertEquals(1, summary.completedWorkSets)
            assertEquals(0L, summary.totalVolumeGrams)
            assertNull(summary.detail.exercises.single().sets.first().weightGrams)
            assertNull(summary.detail.exercises.single().sets.first().reps)
        } finally { db.close() }
    }
}
