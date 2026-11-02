package com.williamchandra.kettoryn.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class WeightUnit(val label: String) {
    KG("kg"),
    LB("lb")
}

enum class LengthUnit(val label: String) {
    CM("cm"),
    IN("in")
}

enum class ThemeMode(val label: String) {
    DARK("Dark"),
    LIGHT("Light"),
    SYSTEM("System")
}

data class UserPreferences(
    val weightUnit: WeightUnit = WeightUnit.KG,
    val lengthUnit: LengthUnit = LengthUnit.CM,
    val defaultRestSeconds: Int = 90,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val restTimerEndEpochMillis: Long? = null,
    val exerciseRestOverrides: Map<Long, Int> = emptyMap(),
    val restTimerExerciseId: Long? = null,
    val pausedRestSeconds: Int? = null,
    val restTimerEndElapsedMillis: Long? = null
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kettoryn_preferences")

class UserPreferencesRepository(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.dataStore)

    private object Keys {
        val EXERCISE_REST = stringPreferencesKey("exercise_rest_v63")
        val REST_OWNER = longPreferencesKey("rest_owner_v63")
        val REST_ELAPSED = longPreferencesKey("rest_elapsed_v63")
        val REST_PAUSED = intPreferencesKey("rest_paused_v63")
        val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
        val LENGTH_UNIT = stringPreferencesKey("length_unit")
        val DEFAULT_REST_SECONDS = intPreferencesKey("default_rest_seconds")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val REST_TIMER_END_MILLIS = longPreferencesKey("rest_timer_end_millis")
    }

    val preferencesFlow: Flow<UserPreferences> = dataStore.data.map { prefs ->
        val unit = prefs[Keys.WEIGHT_UNIT]?.let {
            runCatching { WeightUnit.valueOf(it) }.getOrNull()
        } ?: WeightUnit.KG
        val lengthUnit = prefs[Keys.LENGTH_UNIT]?.let {
            runCatching { LengthUnit.valueOf(it) }.getOrNull()
        } ?: LengthUnit.CM
        val restSeconds = (prefs[Keys.DEFAULT_REST_SECONDS] ?: 90).coerceIn(0, 3600)
        val theme = prefs[Keys.THEME_MODE]?.let {
            runCatching { ThemeMode.valueOf(it) }.getOrNull()
        } ?: ThemeMode.DARK
        val restEnd = prefs[Keys.REST_TIMER_END_MILLIS]?.takeIf { it > 0L }

        UserPreferences(
            weightUnit = unit,
            lengthUnit = lengthUnit,
            defaultRestSeconds = restSeconds,
            themeMode = theme,
            restTimerEndEpochMillis = restEnd,
            exerciseRestOverrides = prefs[Keys.EXERCISE_REST].orEmpty().split(',').mapNotNull { entry ->
                val pair = entry.split(':'); val id = pair.getOrNull(0)?.toLongOrNull(); val sec = pair.getOrNull(1)?.toIntOrNull()
                if (id != null && sec != null && sec in 0..3600) id to sec else null
            }.toMap(),
            restTimerExerciseId = prefs[Keys.REST_OWNER],
            pausedRestSeconds = prefs[Keys.REST_PAUSED],
            restTimerEndElapsedMillis = prefs[Keys.REST_ELAPSED]
        )
    }

    suspend fun getSnapshot(): UserPreferences = preferencesFlow.first()

    suspend fun setWeightUnit(unit: WeightUnit) {
        dataStore.edit { it[Keys.WEIGHT_UNIT] = unit.name }
    }

    suspend fun setLengthUnit(unit: LengthUnit) {
        dataStore.edit { it[Keys.LENGTH_UNIT] = unit.name }
    }

    suspend fun setDefaultRestSeconds(seconds: Int) {
        dataStore.edit { it[Keys.DEFAULT_REST_SECONDS] = seconds.coerceIn(0, 3600) }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setRestTimerEndEpochMillis(endMillis: Long?) {
        dataStore.edit {
            if (endMillis == null || endMillis <= 0L) {
                it.remove(Keys.REST_TIMER_END_MILLIS); it.remove(Keys.REST_ELAPSED)
                it.remove(Keys.REST_OWNER)
                it.remove(Keys.REST_PAUSED)
            } else {
                it.remove(Keys.REST_PAUSED)
                it[Keys.REST_TIMER_END_MILLIS] = endMillis
                it[Keys.REST_ELAPSED] = android.os.SystemClock.elapsedRealtime() + (endMillis - System.currentTimeMillis()).coerceIn(0, 3_600_000)
            }
        }
    }

    suspend fun setExerciseRest(id: Long, seconds: Int) {
        dataStore.edit { prefs ->
            val values = prefs[Keys.EXERCISE_REST].orEmpty().split(',').filter { it.isNotBlank() && !it.startsWith("$id:") }
            prefs[Keys.EXERCISE_REST] = (values + "$id:${seconds.coerceIn(0, 3600)}").joinToString(",")
        }
    }

    suspend fun startExerciseRest(id: Long, seconds: Int) {
        dataStore.edit {
            it.remove(Keys.REST_PAUSED)
            if (seconds <= 0) { it.remove(Keys.REST_TIMER_END_MILLIS); it.remove(Keys.REST_ELAPSED); it.remove(Keys.REST_OWNER) }
            else { it[Keys.REST_OWNER] = id; it[Keys.REST_TIMER_END_MILLIS] = System.currentTimeMillis() + seconds.coerceAtMost(3600) * 1000L; it[Keys.REST_ELAPSED] = android.os.SystemClock.elapsedRealtime() + seconds.coerceAtMost(3600) * 1000L }
        }
    }

    suspend fun toggleRestPause() {
        dataStore.edit {
            val paused = it[Keys.REST_PAUSED]
            if (paused != null) {
                it[Keys.REST_TIMER_END_MILLIS] = System.currentTimeMillis() + paused.coerceIn(0, 3600) * 1000L
                it[Keys.REST_ELAPSED] = android.os.SystemClock.elapsedRealtime() + paused.coerceIn(0, 3600) * 1000L
                it.remove(Keys.REST_PAUSED)
            } else {
                val remaining = com.williamchandra.kettoryn.domain.RestTimerCalculator.remainingSeconds(
                    it[Keys.REST_TIMER_END_MILLIS], it[Keys.REST_ELAPSED], System.currentTimeMillis(), android.os.SystemClock.elapsedRealtime()).toInt()
                it[Keys.REST_PAUSED] = remaining
                it.remove(Keys.REST_TIMER_END_MILLIS); it.remove(Keys.REST_ELAPSED)
            }
        }
    }

    suspend fun adjustRestSeconds(delta: Int) {
        dataStore.edit {
            val paused = it[Keys.REST_PAUSED]
            if (paused != null) it[Keys.REST_PAUSED] = (paused + delta).coerceIn(0, 3600)
            else {
                val now = System.currentTimeMillis()
                val remaining = (((it[Keys.REST_TIMER_END_MILLIS] ?: now) - now) / 1000 + delta).coerceIn(0, 3600)
                it[Keys.REST_TIMER_END_MILLIS] = now + remaining * 1000
                it[Keys.REST_ELAPSED] = android.os.SystemClock.elapsedRealtime() + remaining * 1000
            }
        }
    }

    suspend fun replacePreferences(
        weightUnit: WeightUnit,
        lengthUnit: LengthUnit,
        defaultRestSeconds: Int,
        themeMode: ThemeMode
    ) {
        dataStore.edit {
            it[Keys.WEIGHT_UNIT] = weightUnit.name
            it[Keys.LENGTH_UNIT] = lengthUnit.name
            it[Keys.DEFAULT_REST_SECONDS] = defaultRestSeconds.coerceIn(0, 3600)
            it[Keys.THEME_MODE] = themeMode.name
            it.remove(Keys.REST_TIMER_END_MILLIS); it.remove(Keys.REST_ELAPSED)
            it.remove(Keys.REST_OWNER)
            it.remove(Keys.REST_PAUSED)
            it.remove(Keys.EXERCISE_REST)
        }
    }
}
