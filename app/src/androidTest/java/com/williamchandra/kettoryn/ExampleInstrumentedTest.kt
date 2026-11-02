package com.williamchandra.kettoryn

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.williamchandra.kettoryn.data.local.KettorynDatabase
import com.williamchandra.kettoryn.data.local.SetType
import com.williamchandra.kettoryn.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    private lateinit var db: KettorynDatabase
    private lateinit var repository: WorkoutRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, KettorynDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = WorkoutRepository(db)
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) {
            db.close()
        }
    }

    @Test
    fun useAppContextAndVerifyDatabaseSeedAndLogging() {
        runTest {
            val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
            assertEquals("com.williamchandra.kettoryn", appContext.packageName)

            repository.ensureSeeded()
            val exercises = repository.observeExercises().first()
            assertTrue(exercises.size >= 30)

            val sessionId = repository.startEmptyWorkout("Instrumented Test Session", initialExerciseId = exercises.first().id)
            val sets = repository.getSessionDetail(sessionId)!!.exercises.first().sets
            repository.updateSet(sets.first().id, weightGrams = 60_000L, reps = 10, isComplete = true, setType = SetType.WORK)
            assertTrue(repository.finishWorkout(sessionId))

            val completed = repository.observeCompletedSessions().first()
            assertEquals(1, completed.size)
            assertEquals(600_000L, completed.first().totalVolumeGrams)
        }
    }
}
