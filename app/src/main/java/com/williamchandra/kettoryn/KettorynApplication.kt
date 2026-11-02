package com.williamchandra.kettoryn

import android.app.Application
import androidx.room.Room
import com.williamchandra.kettoryn.data.local.KettorynDatabase
import com.williamchandra.kettoryn.data.preferences.UserPreferencesRepository
import com.williamchandra.kettoryn.data.repository.WorkoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AppContainer(context: Application) {
    val database: KettorynDatabase = Room.databaseBuilder(
        context,
        KettorynDatabase::class.java,
        "kettoryn.db"
    )
        .addMigrations(KettorynDatabase.MIGRATION_1_2, KettorynDatabase.MIGRATION_2_3)
        .build()

    val workoutRepository: WorkoutRepository = WorkoutRepository(database, java.io.File(context.filesDir, "custom-exercise-images"))
    val preferencesRepository: UserPreferencesRepository = UserPreferencesRepository(context)

    fun close() {
        runCatching { database.close() }
    }
}

class KettorynApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch {
            container.workoutRepository.ensureSeeded()
        }
    }

    override fun onTerminate() {
        appScope.cancel()
        if (::container.isInitialized) {
            container.close()
        }
        super.onTerminate()
    }
}
