package com.williamchandra.kettoryn.data.repository

import androidx.room.withTransaction
import com.williamchandra.kettoryn.data.local.BodyMeasurementEntryEntity
import com.williamchandra.kettoryn.data.local.BodyMeasurementEntryWithValues
import com.williamchandra.kettoryn.data.local.BodyMeasurementValueEntity
import com.williamchandra.kettoryn.data.local.ExerciseEntity
import com.williamchandra.kettoryn.data.local.ExerciseSeedData
import com.williamchandra.kettoryn.data.local.KettorynDatabase
import com.williamchandra.kettoryn.data.local.LoggedSetEntity
import com.williamchandra.kettoryn.data.local.SessionExerciseEntity
import com.williamchandra.kettoryn.data.local.SessionStatus
import com.williamchandra.kettoryn.data.local.SessionWithExercises
import com.williamchandra.kettoryn.data.local.SetType
import com.williamchandra.kettoryn.data.local.TemplateExerciseEntity
import com.williamchandra.kettoryn.data.local.TemplateSetEntity
import com.williamchandra.kettoryn.data.local.TemplateWithExercises
import com.williamchandra.kettoryn.data.local.WorkoutSessionEntity
import com.williamchandra.kettoryn.data.local.WorkoutTemplateEntity
import com.williamchandra.kettoryn.data.local.CanonicalExerciseCatalog
import com.williamchandra.kettoryn.domain.MeasurementMetric
import com.williamchandra.kettoryn.domain.PersonalRecord
import com.williamchandra.kettoryn.domain.SetSample
import com.williamchandra.kettoryn.domain.UnitConverter
import com.williamchandra.kettoryn.domain.WorkoutMetricsCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

data class TemplateDraftSet(
    val suggestedReps: Int? = 8,
    val suggestedWeightGrams: Long? = null,
    val suggestedDurationSeconds: Int? = null,
    val suggestedDistanceMeters: Long? = null
)

data class TemplateDraftExercise(
    val exerciseId: Long?,
    val nameFallback: String = "",
    val muscleFallback: String = "",
    val equipmentFallback: String = "",
    val sets: List<TemplateDraftSet>
)

data class CompletedSessionSummary(
    val detail: SessionWithExercises,
    val totalVolumeGrams: Long,
    val completedWorkSets: Int,
    val exerciseCount: Int
) {
    val session: WorkoutSessionEntity get() = detail.session
}

data class ExerciseSessionHistoryPoint(
    val sessionId: Long,
    val sessionTitle: String,
    val startedAt: Long,
    val bestSetWeightGrams: Long,
    val bestSetReps: Int,
    val totalVolumeGrams: Long,
    val completedSetsCount: Int
)

data class ExerciseProgressSummary(
    val exerciseId: Long?,
    val exerciseName: String,
    val primaryMuscle: String,
    val equipment: String,
    val personalRecord: PersonalRecord?,
    val totalCompletedSets: Int,
    val totalVolumeGrams: Long,
    val historyPoints: List<ExerciseSessionHistoryPoint>
)

data class DatabaseSnapshot(
    val exercises: List<ExerciseEntity>,
    val templates: List<WorkoutTemplateEntity>,
    val templateExercises: List<TemplateExerciseEntity>,
    val templateSets: List<TemplateSetEntity>,
    val sessions: List<WorkoutSessionEntity>,
    val sessionExercises: List<SessionExerciseEntity>,
    val loggedSets: List<LoggedSetEntity>,
    val measurementEntries: List<BodyMeasurementEntryEntity>,
    val measurementValues: List<BodyMeasurementValueEntity>
)

class WorkoutRepository(private val db: KettorynDatabase, val customMediaDirectory: java.io.File? = null) {

    private val exerciseDao = db.exerciseDao()
    private val templateDao = db.templateDao()
    private val sessionDao = db.sessionDao()
    private val bodyMeasurementDao = db.bodyMeasurementDao()
    private val seedMutex = Mutex()
    private val sessionMutex = Mutex()

    suspend fun ensureSeeded() {
        seedMutex.withLock {
            ensureSeededUnlocked()
        }
    }

    private suspend fun ensureSeededUnlocked() {
        db.withTransaction {
            val slugs = exerciseDao.getBuiltInExercises().mapNotNull { it.sourceSlug }.toSet()
            val missing = CanonicalExerciseCatalog.entries.filter { it.slug !in slugs }.map { entry ->
                ExerciseEntity(name = entry.name, normalizedName = normalizeName(entry.name), primaryMuscle = entry.primaryMuscle,
                    equipment = entry.equipment, isBuiltIn = true, sourceSlug = entry.slug, exerciseType = entry.exerciseType,
                    secondaryMuscles = entry.secondaryMuscles, isStretch = entry.isStretch)
            }
            if (missing.isNotEmpty()) exerciseDao.insertExercises(missing)
        }
    }

    fun observeExercises(
        query: String = "",
        muscleFilter: String? = null,
        equipmentFilter: String? = null
    ): Flow<List<ExerciseEntity>> {
        val cleanQuery = query.trim().lowercase(Locale.ROOT)
        return exerciseDao.observeActiveExercises().map { list ->
            list.filter { ex ->
                val matchesQuery = cleanQuery.isEmpty() ||
                    ex.name.lowercase(Locale.ROOT).contains(cleanQuery) ||
                    ex.primaryMuscle.lowercase(Locale.ROOT).contains(cleanQuery) ||
                    ex.equipment.lowercase(Locale.ROOT).contains(cleanQuery) ||
                    "${ex.equipment} ${ex.name}".lowercase(Locale.ROOT).contains(cleanQuery)
                val matchesMuscle = muscleFilter.isNullOrBlank() ||
                    ex.primaryMuscle.equals(muscleFilter, ignoreCase = true)
                val matchesEquipment = equipmentFilter.isNullOrBlank() ||
                    ex.equipment.equals(equipmentFilter, ignoreCase = true)
                matchesQuery && matchesMuscle && matchesEquipment
            }
        }
    }

    fun observeExerciseById(id: Long): Flow<ExerciseEntity?> = exerciseDao.observeExerciseById(id)

    suspend fun getExerciseById(id: Long): ExerciseEntity? = exerciseDao.getExerciseById(id)

    suspend fun createCustomExercise(
        name: String,
        primaryMuscle: String,
        equipment: String,
        instructions: String,
        exerciseType: String = "weight_reps",
        customImageKey: String? = null
    ): Long {
        validateCustomMetadata(exerciseType, customImageKey)
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "Exercise name is required." }
        val normalized = normalizeName(trimmedName)
        val active = exerciseDao.getActiveExercises()
        require(active.none { it.normalizedName == normalized }) {
            "An exercise named \"$trimmedName\" already exists."
        }
        return exerciseDao.insertExercise(
            ExerciseEntity(
                name = trimmedName,
                normalizedName = normalized,
                primaryMuscle = primaryMuscle.trim().ifEmpty { "Full Body" },
                equipment = equipment.trim().ifEmpty { "Bodyweight" },
                instructions = instructions.trim(),
                isBuiltIn = false,
                deletedAt = null,
                exerciseType = exerciseType,
                customImageKey = customImageKey
            )
        )
    }

    suspend fun updateCustomExercise(
        id: Long,
        name: String,
        primaryMuscle: String,
        equipment: String,
        instructions: String,
        exerciseType: String = "weight_reps",
        customImageKey: String? = null
    ) {
        validateCustomMetadata(exerciseType, customImageKey)
        val existing = exerciseDao.getExerciseById(id)
            ?: throw IllegalArgumentException("Exercise not found.")
        require(!existing.isBuiltIn) { "Built-in exercises cannot be edited. Create a custom exercise instead." }
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "Exercise name is required." }
        val normalized = normalizeName(trimmedName)
        val active = exerciseDao.getActiveExercises()
        require(active.none { it.id != id && it.normalizedName == normalized }) {
            "An exercise named \"$trimmedName\" already exists."
        }
        exerciseDao.updateExercise(
            existing.copy(
                name = trimmedName,
                normalizedName = normalized,
                primaryMuscle = primaryMuscle.trim().ifEmpty { existing.primaryMuscle },
                equipment = equipment.trim().ifEmpty { existing.equipment },
                instructions = instructions.trim(),
                exerciseType = exerciseType,
                customImageKey = customImageKey
            )
        )
    }

    private fun validateCustomMetadata(kind: String, image: String?) {
        require(kind in com.williamchandra.kettoryn.data.local.ExerciseKinds.labels) { "Choose a supported exercise type." }
        require(image == null || com.williamchandra.kettoryn.data.media.CustomExerciseMediaStore.isValidKey(image)) { "Invalid local image." }
    }

    fun readCustomMedia(snapshot: DatabaseSnapshot): Map<String, ByteArray> = snapshot.exercises.mapNotNull { it.customImageKey }
        .distinct().mapNotNull { key ->
            if (!com.williamchandra.kettoryn.data.media.CustomExerciseMediaStore.isValidKey(key)) return@mapNotNull null
            val dir = customMediaDirectory ?: return@mapNotNull null
            val file = java.io.File(dir, key)
            if (!file.isFile || file.length() > com.williamchandra.kettoryn.data.media.CustomExerciseMediaStore.MAX_MEDIA_BYTES) return@mapNotNull null
            val bytes = file.readBytes()
            if (com.williamchandra.kettoryn.data.media.CustomExerciseMediaStore.sha256(bytes) + ".webp" != key) return@mapNotNull null
            key to bytes
        }.toMap()

    fun restoreCustomMedia(media: Map<String, ByteArray>) {
        val dir = customMediaDirectory ?: return
        dir.mkdirs()
        media.forEach { (key, bytes) ->
            require(com.williamchandra.kettoryn.data.media.CustomExerciseMediaStore.isValidKey(key))
            require(com.williamchandra.kettoryn.data.media.CustomExerciseMediaStore.sha256(bytes) + ".webp" == key)
            val target = java.io.File(dir, key)
            if (!target.exists()) {
                val temp = java.io.File.createTempFile("restore-", ".tmp", dir)
                try { temp.writeBytes(bytes); check(temp.renameTo(target)) } finally { temp.delete() }
            }
        }
    }

    suspend fun deleteCustomExercise(
        id: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        return exerciseDao.softDeleteCustomExercise(id, nowMillis) > 0
    }

    // Templates
    fun observeTemplates(): Flow<List<TemplateWithExercises>> =
        templateDao.observeTemplatesWithExercises()

    fun observeTemplate(id: Long): Flow<TemplateWithExercises?> =
        templateDao.observeTemplateWithExercises(id)

    suspend fun getTemplate(id: Long): TemplateWithExercises? =
        templateDao.getTemplateWithExercises(id)

    suspend fun saveTemplate(
        templateId: Long?,
        title: String,
        exercises: List<TemplateDraftExercise>,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val cleanTitle = title.trim()
        require(cleanTitle.isNotEmpty()) { "Template title is required." }
        require(exercises.isNotEmpty()) { "Add at least one exercise to the template." }

        return db.withTransaction {
            val resolvedId = if (templateId == null || templateId == 0L) {
                templateDao.insertTemplate(
                    WorkoutTemplateEntity(
                        title = cleanTitle,
                        createdAt = nowMillis,
                        updatedAt = nowMillis
                    )
                )
            } else {
                val existing = templateDao.getTemplateWithExercises(templateId)?.template
                if (existing != null) {
                    templateDao.updateTemplate(
                        existing.copy(title = cleanTitle, updatedAt = nowMillis)
                    )
                    templateDao.deleteTemplateExercises(templateId)
                    templateId
                } else {
                    templateDao.insertTemplate(
                        WorkoutTemplateEntity(
                            title = cleanTitle,
                            createdAt = nowMillis,
                            updatedAt = nowMillis
                        )
                    )
                }
            }

            exercises.forEachIndexed { exIndex, draftEx ->
                val exEntity = draftEx.exerciseId?.let { exerciseDao.getExerciseById(it) }
                val templateExId = templateDao.insertTemplateExercise(
                    TemplateExerciseEntity(
                        templateId = resolvedId,
                        exerciseId = exEntity?.id,
                        sortOrder = exIndex,
                        exerciseNameSnapshot = exEntity?.name ?: draftEx.nameFallback.ifBlank { "Exercise" },
                        muscleSnapshot = exEntity?.primaryMuscle ?: draftEx.muscleFallback.ifBlank { "Full Body" },
                        equipmentSnapshot = exEntity?.equipment ?: draftEx.equipmentFallback.ifBlank { "Bodyweight" },
                        sourceSlugSnapshot = exEntity?.sourceSlug,
                        exerciseTypeSnapshot = exEntity?.exerciseType ?: "weight_reps"
                    )
                )
                val setsToInsert = draftEx.sets.ifEmpty { listOf(TemplateDraftSet()) }
                    .mapIndexed { setIndex, draftSet ->
                        TemplateSetEntity(
                            templateExerciseId = templateExId,
                            sortOrder = setIndex,
                            suggestedReps = draftSet.suggestedReps,
                            suggestedWeightGrams = if (exEntity?.exerciseType in listOf("duration", "distance_duration")) null else draftSet.suggestedWeightGrams,
                            suggestedDurationSeconds = draftSet.suggestedDurationSeconds ?: if (exEntity?.exerciseType in listOf("duration", "distance_duration")) 30 else null,
                            suggestedDistanceMeters = draftSet.suggestedDistanceMeters
                        )
                    }
                templateDao.insertTemplateSets(setsToInsert)
            }
            resolvedId
        }
    }

    suspend fun duplicateTemplate(
        templateId: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): Long? {
        val source = templateDao.getTemplateWithExercises(templateId) ?: return null
        val draftExercises = source.exercises.map { exWithSets ->
            TemplateDraftExercise(
                exerciseId = exWithSets.templateExercise.exerciseId,
                nameFallback = exWithSets.templateExercise.exerciseNameSnapshot,
                muscleFallback = exWithSets.templateExercise.muscleSnapshot,
                equipmentFallback = exWithSets.templateExercise.equipmentSnapshot,
                sets = exWithSets.sets.map {
                    TemplateDraftSet(
                        suggestedReps = it.suggestedReps,
                        suggestedWeightGrams = it.suggestedWeightGrams,
                        suggestedDurationSeconds = it.suggestedDurationSeconds,
                        suggestedDistanceMeters = it.suggestedDistanceMeters
                    )
                }
            )
        }
        return saveTemplate(
            templateId = null,
            title = "${source.template.title} (Copy)",
            exercises = draftExercises,
            nowMillis = nowMillis
        )
    }

    suspend fun deleteTemplate(templateId: Long) {
        templateDao.deleteTemplateById(templateId)
    }

    // Active Workout & Sessions
    fun observeActiveSession(): Flow<SessionWithExercises?> = sessionDao.observeActiveSession()

    suspend fun getActiveSession(): SessionWithExercises? = sessionDao.getActiveSession()

    fun observeSessionDetail(sessionId: Long): Flow<SessionWithExercises?> =
        sessionDao.observeSessionWithExercises(sessionId)

    suspend fun getSessionDetail(sessionId: Long): SessionWithExercises? =
        sessionDao.getSessionWithExercises(sessionId)

    suspend fun startEmptyWorkout(
        title: String = "Workout",
        initialExerciseId: Long? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        return sessionMutex.withLock {
            db.withTransaction {
                val existingActive = sessionDao.getActiveSession()
                if (existingActive != null) {
                    return@withTransaction existingActive.session.id
                }
                val sessionId = sessionDao.insertSession(
                    WorkoutSessionEntity(
                        title = title.trim().ifEmpty { "Workout" },
                        sourceTemplateId = null,
                        startedAt = nowMillis,
                        endedAt = null,
                        status = SessionStatus.ACTIVE,
                        updatedAt = nowMillis
                    )
                )
                if (initialExerciseId != null) {
                    insertExerciseIntoSessionInternal(sessionId, initialExerciseId)
                }
                sessionId
            }
        }
    }

    suspend fun startWorkoutFromTemplate(
        templateId: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        return sessionMutex.withLock {
            db.withTransaction {
                val existingActive = sessionDao.getActiveSession()
                if (existingActive != null) {
                    return@withTransaction existingActive.session.id
                }
                val template = templateDao.getTemplateWithExercises(templateId)
                    ?: throw IllegalArgumentException("Template not found.")
                require(template.exercises.isNotEmpty()) { "Cannot start a workout from an empty template." }

                val sessionId = sessionDao.insertSession(
                    WorkoutSessionEntity(
                        title = template.template.title,
                        sourceTemplateId = template.template.id,
                        startedAt = nowMillis,
                        endedAt = null,
                        status = SessionStatus.ACTIVE,
                        updatedAt = nowMillis
                    )
                )

                template.exercises.forEachIndexed { exIndex, templateEx ->
                    val liveExercise = templateEx.templateExercise.exerciseId?.let {
                        exerciseDao.getExerciseById(it)
                    }
                    val sessionExId = sessionDao.insertSessionExercise(
                        SessionExerciseEntity(
                            sessionId = sessionId,
                            exerciseId = liveExercise?.id ?: templateEx.templateExercise.exerciseId,
                            nameSnapshot = liveExercise?.name ?: templateEx.templateExercise.exerciseNameSnapshot,
                            muscleSnapshot = liveExercise?.primaryMuscle ?: templateEx.templateExercise.muscleSnapshot,
                            equipmentSnapshot = liveExercise?.equipment ?: templateEx.templateExercise.equipmentSnapshot,
                            sortOrder = exIndex,
                            sourceSlugSnapshot = templateEx.templateExercise.sourceSlugSnapshot,
                            exerciseTypeSnapshot = templateEx.templateExercise.exerciseTypeSnapshot
                        )
                    )
                    val plannedSets = templateEx.sets.ifEmpty {
                        listOf(
                            TemplateSetEntity(
                                templateExerciseId = templateEx.templateExercise.id,
                                sortOrder = 0,
                                suggestedReps = 8,
                                suggestedWeightGrams = null
                            )
                        )
                    }
                    val loggedSets = plannedSets.mapIndexed { setIndex, planned ->
                        LoggedSetEntity(
                            sessionExerciseId = sessionExId,
                            sortOrder = setIndex,
                            reps = if (templateEx.templateExercise.exerciseTypeSnapshot in listOf("duration", "distance_duration")) null else planned.suggestedReps,
                            weightGrams = planned.suggestedWeightGrams,
                            isComplete = false,
                            setType = SetType.WORK,
                            exerciseType = templateEx.templateExercise.exerciseTypeSnapshot,
                            durationSeconds = planned.suggestedDurationSeconds,
                            distanceMeters = planned.suggestedDistanceMeters
                        )
                    }
                    sessionDao.insertLoggedSets(loggedSets)
                }
                sessionId
            }
        }
    }

    suspend fun updateSessionTitle(
        sessionId: Long,
        title: String,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val clean = title.trim().ifEmpty { "Workout" }
        sessionDao.updateSessionTitle(sessionId, clean, nowMillis)
    }

    suspend fun addExerciseToActiveSession(
        sessionId: Long,
        exerciseId: Long
    ): Long {
        return db.withTransaction {
            insertExerciseIntoSessionInternal(sessionId, exerciseId)
        }
    }

    private suspend fun insertExerciseIntoSessionInternal(
        sessionId: Long,
        exerciseId: Long
    ): Long {
        val exercise = exerciseDao.getExerciseById(exerciseId)
            ?: throw IllegalArgumentException("Exercise not found.")
        val currentExercises = sessionDao.getSessionExercises(sessionId)
        val nextOrder = (currentExercises.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val sessionExId = sessionDao.insertSessionExercise(
            SessionExerciseEntity(
                sessionId = sessionId,
                exerciseId = exercise.id,
                nameSnapshot = exercise.name,
                muscleSnapshot = exercise.primaryMuscle,
                equipmentSnapshot = exercise.equipment,
                sortOrder = nextOrder,
                sourceSlugSnapshot = exercise.sourceSlug,
                exerciseTypeSnapshot = exercise.exerciseType
            )
        )
        val prev = sessionDao.getMostRecentCompletedSessionExercise(exercise.id, exercise.name)
        val defaultSets = (0 until 3).map { idx ->
            val prevSet = prev?.sets?.getOrNull(idx)
            LoggedSetEntity(
                sessionExerciseId = sessionExId,
                sortOrder = idx,
                reps = if (exercise.exerciseType in listOf("duration", "distance_duration")) null else prevSet?.reps,
                weightGrams = prevSet?.weightGrams,
                isComplete = false,
                setType = SetType.WORK,
                exerciseType = exercise.exerciseType,
                durationSeconds = prevSet?.durationSeconds,
                distanceMeters = prevSet?.distanceMeters
            )
        }
        sessionDao.insertLoggedSets(defaultSets)
        return sessionExId
    }

    suspend fun removeExerciseFromSession(sessionId: Long, sessionExerciseId: Long) {
        db.withTransaction {
            sessionDao.deleteSessionExerciseById(sessionExerciseId)
            val remaining = sessionDao.getSessionExercises(sessionId)
                .sortedBy { it.sortOrder }
                .mapIndexed { index, item -> item.copy(sortOrder = index) }
            sessionDao.updateSessionExercises(remaining)
        }
    }

    suspend fun addSetToExercise(sessionExerciseId: Long): Long {
        return db.withTransaction {
            val currentSets = sessionDao.getSetsForSessionExercise(sessionExerciseId)
            val lastSet = currentSets.maxByOrNull { it.sortOrder }
            val nextOrder = (lastSet?.sortOrder ?: -1) + 1
            sessionDao.insertLoggedSet(
                LoggedSetEntity(
                    sessionExerciseId = sessionExerciseId,
                    sortOrder = nextOrder,
                    reps = lastSet?.reps,
                    weightGrams = lastSet?.weightGrams,
                    isComplete = false,
                    setType = SetType.WORK,
                    exerciseType = lastSet?.exerciseType ?: sessionDao.getSessionExercisesForType(sessionExerciseId) ?: "weight_reps",
                    durationSeconds = lastSet?.durationSeconds,
                    distanceMeters = lastSet?.distanceMeters
                )
            )
        }
    }

    suspend fun updateSet(
        setId: Long,
        weightGrams: Long?,
        reps: Int?,
        isComplete: Boolean,
        setType: SetType
    ) {
        val current = sessionDao.getLoggedSetById(setId) ?: return
        val sample = SetSample(weightGrams, reps, isComplete, SetType.WORK, current.exerciseType, current.durationSeconds, current.distanceMeters)
        val validComplete = isComplete && WorkoutMetricsCalculator.isQualifyingWorkSet(sample)
        sessionDao.updateLoggedSet(
            current.copy(
                weightGrams = if (current.exerciseType in listOf("duration", "distance_duration")) null else weightGrams,
                reps = if (current.exerciseType in listOf("duration", "distance_duration")) null else reps,
                isComplete = validComplete,
                setType = setType
            )
        )
    }

    suspend fun updateTimedSet(setId: Long, seconds: Int?, meters: Long?, isComplete: Boolean? = null, setType: SetType? = null) {
        db.withTransaction {
            val current = sessionDao.getLoggedSetById(setId) ?: return@withTransaction
            require(current.exerciseType in listOf("duration", "distance_duration"))
            val validSeconds = seconds?.takeIf { it in 0..604800 }
            val validMeters = meters?.takeIf { it in 0..10000000 }
            val updated = current.copy(weightGrams = null, reps = null, durationSeconds = validSeconds, distanceMeters = validMeters, setType = setType ?: current.setType)
            val valid = WorkoutMetricsCalculator.isQualifyingWorkSet(SetSample(null, null, true, SetType.WORK, updated.exerciseType, validSeconds, validMeters))
            sessionDao.updateLoggedSet(updated.copy(isComplete = (isComplete ?: current.isComplete) && valid))
        }
    }

    suspend fun deleteSet(sessionExerciseId: Long, setId: Long) {
        db.withTransaction {
            sessionDao.deleteLoggedSetById(setId)
            val remaining = sessionDao.getSetsForSessionExercise(sessionExerciseId)
                .sortedBy { it.sortOrder }
                .mapIndexed { index, item -> item.copy(sortOrder = index) }
            sessionDao.updateLoggedSets(remaining)
        }
    }

    suspend fun restoreDeletedSet(set: LoggedSetEntity) {
        db.withTransaction {
            if (sessionDao.getAllSessionExercises().none { it.id == set.sessionExerciseId }) return@withTransaction
            if (sessionDao.getLoggedSetById(set.id) != null) return@withTransaction
            val remaining = sessionDao.getSetsForSessionExercise(set.sessionExerciseId).sortedBy { it.sortOrder }
            val position = set.sortOrder.coerceIn(0, remaining.size)
            sessionDao.updateLoggedSets(remaining.mapIndexed { index, item -> item.copy(sortOrder = if (index >= position) index + 1 else index) })
            sessionDao.insertLoggedSet(set.copy(sortOrder = position))
        }
    }

    suspend fun getPreviousPerformanceForExercise(
        exerciseId: Long?,
        exerciseName: String
    ): List<LoggedSetEntity> {
        return sessionDao.getMostRecentCompletedSessionExercise(exerciseId, exerciseName)
            ?.sets
            ?.filter { it.isComplete }
            .orEmpty()
    }

    /**
     * Atomically marks an active workout as COMPLETED.
     * Idempotent: returns true only on the first call that transitions ACTIVE -> COMPLETED.
     */
    suspend fun finishWorkout(
        sessionId: Long,
        endedAtMillis: Long = System.currentTimeMillis()
    ): Boolean {
        return sessionMutex.withLock {
            db.withTransaction {
                val updatedRows = sessionDao.markSessionCompletedIfActive(sessionId, endedAtMillis)
                updatedRows > 0
            }
        }
    }

    suspend fun discardWorkout(sessionId: Long) {
        sessionMutex.withLock {
            sessionDao.deleteSessionById(sessionId)
        }
    }

    suspend fun deleteCompletedSession(sessionId: Long) {
        sessionDao.deleteSessionById(sessionId)
    }

    fun observeCompletedSessions(): Flow<List<CompletedSessionSummary>> {
        return sessionDao.observeCompletedSessions().map { sessions ->
            sessions.map { detail ->
                val allSets = detail.exercises.flatMap { it.sets }
                CompletedSessionSummary(
                    detail = detail,
                    totalVolumeGrams = WorkoutMetricsCalculator.calculateTotalVolumeGramsFromEntities(allSets),
                    completedWorkSets = WorkoutMetricsCalculator.countCompletedWorkSetsFromEntities(allSets),
                    exerciseCount = detail.exercises.size
                )
            }
        }
    }

    fun observeExerciseProgress(exerciseId: Long, exerciseName: String): Flow<ExerciseProgressSummary?> {
        return sessionDao.observeCompletedSessions().map { sessions ->
            val historyPoints = mutableListOf<ExerciseSessionHistoryPoint>()
            val allQualifyingSamples = mutableListOf<SetSample>()
            var resolvedName = exerciseName
            var resolvedMuscle = ""
            var resolvedEquipment = ""

            for (session in sessions.sortedBy { it.session.startedAt }) {
                val matchingExercises = session.exercises.filter { se ->
                    (exerciseId > 0L && se.sessionExercise.exerciseId == exerciseId) ||
                        se.sessionExercise.nameSnapshot.equals(exerciseName, ignoreCase = true)
                }
                if (matchingExercises.isNotEmpty()) {
                    val firstMatch = matchingExercises.first().sessionExercise
                    resolvedName = firstMatch.nameSnapshot
                    resolvedMuscle = firstMatch.muscleSnapshot
                    resolvedEquipment = firstMatch.equipmentSnapshot

                    val samples = matchingExercises.flatMap { it.sets }.map {
                        SetSample(
                            weightGrams = it.weightGrams,
                            reps = it.reps,
                            isComplete = it.isComplete,
                            setType = it.setType,
                        exerciseType = it.exerciseType,
                        durationSeconds = it.durationSeconds,
                        distanceMeters = it.distanceMeters
                        )
                    }
                    allQualifyingSamples.addAll(samples)
                    val completedWork = samples.filter { WorkoutMetricsCalculator.isQualifyingWorkSet(it) }
                    if (completedWork.isNotEmpty()) {
                        val bestInSession = WorkoutMetricsCalculator.findPersonalRecord(completedWork)
                        val sessionVolume = WorkoutMetricsCalculator.calculateTotalVolumeGrams(completedWork)
                        historyPoints.add(
                            ExerciseSessionHistoryPoint(
                                sessionId = session.session.id,
                                sessionTitle = session.session.title,
                                startedAt = session.session.startedAt,
                                bestSetWeightGrams = bestInSession?.weightGrams ?: 0L,
                                bestSetReps = bestInSession?.reps ?: 0,
                                totalVolumeGrams = sessionVolume,
                                completedSetsCount = completedWork.size
                            )
                        )
                    }
                }
            }

            val liveEx = if (exerciseId > 0L) exerciseDao.getExerciseById(exerciseId) else null
            if (liveEx != null) {
                resolvedName = liveEx.name
                resolvedMuscle = liveEx.primaryMuscle
                resolvedEquipment = liveEx.equipment
            }

            ExerciseProgressSummary(
                exerciseId = exerciseId.takeIf { it > 0L },
                exerciseName = resolvedName,
                primaryMuscle = resolvedMuscle,
                equipment = resolvedEquipment,
                personalRecord = WorkoutMetricsCalculator.findPersonalRecord(allQualifyingSamples),
                totalCompletedSets = WorkoutMetricsCalculator.countCompletedWorkSets(allQualifyingSamples),
                totalVolumeGrams = WorkoutMetricsCalculator.calculateTotalVolumeGrams(allQualifyingSamples),
                historyPoints = historyPoints
            )
        }
    }

    // Body Measurements Journal (F6)
    fun observeMeasurementEntries(): Flow<List<BodyMeasurementEntryWithValues>> =
        bodyMeasurementDao.observeEntriesWithValues()

    suspend fun getMeasurementEntries(): List<BodyMeasurementEntryWithValues> =
        bodyMeasurementDao.getEntriesWithValues()

    suspend fun getMeasurementEntryById(id: Long): BodyMeasurementEntryWithValues? =
        bodyMeasurementDao.getEntryWithValuesById(id)

    suspend fun saveMeasurementEntry(
        entryId: Long?,
        recordedAt: Long,
        values: Map<MeasurementMetric, Long>,
        note: String = "",
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val enriched = values.toMutableMap()
        val weightGrams = enriched[MeasurementMetric.BODY_WEIGHT]
        val bodyFatBp = enriched[MeasurementMetric.BODY_FAT]
        if (weightGrams != null && bodyFatBp != null && !enriched.containsKey(MeasurementMetric.LEAN_BODY_MASS)) {
            enriched[MeasurementMetric.LEAN_BODY_MASS] = UnitConverter.computeLeanBodyMassGrams(weightGrams, bodyFatBp)
        }
        require(enriched.isNotEmpty()) { "Enter at least one body measurement value." }

        return db.withTransaction {
            val resolvedId = if (entryId == null || entryId == 0L) {
                bodyMeasurementDao.insertEntry(
                    BodyMeasurementEntryEntity(
                        recordedAt = recordedAt,
                        updatedAt = nowMillis,
                        note = note.trim()
                    )
                )
            } else {
                val existing = bodyMeasurementDao.getEntryWithValuesById(entryId)?.entry
                if (existing != null) {
                    bodyMeasurementDao.updateEntry(
                        existing.copy(
                            recordedAt = recordedAt,
                            updatedAt = nowMillis,
                            note = note.trim()
                        )
                    )
                    bodyMeasurementDao.deleteValuesForEntry(entryId)
                    entryId
                } else {
                    bodyMeasurementDao.insertEntry(
                        BodyMeasurementEntryEntity(
                            recordedAt = recordedAt,
                            updatedAt = nowMillis,
                            note = note.trim()
                        )
                    )
                }
            }

            val entities = enriched.entries.map { (metric, canonicalValue) ->
                BodyMeasurementValueEntity(
                    entryId = resolvedId,
                    metricKey = metric.key,
                    canonicalValue = canonicalValue
                )
            }
            bodyMeasurementDao.insertValues(entities)
            resolvedId
        }
    }

    suspend fun deleteMeasurementEntry(entryId: Long) {
        bodyMeasurementDao.deleteEntryById(entryId)
    }

    // Portable Backup & Restore Snapshot Operations (F2)
    suspend fun exportDatabaseSnapshot(): DatabaseSnapshot {
        return db.withTransaction {
            DatabaseSnapshot(
                exercises = exerciseDao.getAllExercises(),
                templates = templateDao.getAllTemplates(),
                templateExercises = templateDao.getAllTemplateExercises(),
                templateSets = templateDao.getAllTemplateSets(),
                sessions = sessionDao.getAllSessions(),
                sessionExercises = sessionDao.getAllSessionExercises(),
                loggedSets = sessionDao.getAllLoggedSets(),
                measurementEntries = bodyMeasurementDao.getAllEntries(),
                measurementValues = bodyMeasurementDao.getAllValues()
            )
        }
    }

    suspend fun replaceAllDatabaseData(snapshot: DatabaseSnapshot) {
        seedMutex.withLock {
            sessionMutex.withLock {
                db.withTransaction {
                    // Delete in child-to-parent order to respect FK constraints cleanly
                    bodyMeasurementDao.deleteAllEntries()
                    sessionDao.deleteAllSessions()
                    templateDao.deleteAllTemplates()
                    exerciseDao.deleteAllExercises()

                    // Re-insert in parent-to-child order preserving exact IDs
                    if (snapshot.exercises.isNotEmpty()) {
                        exerciseDao.insertAllExercisesExact(snapshot.exercises)
                    }
                    if (snapshot.templates.isNotEmpty()) {
                        templateDao.insertAllTemplatesExact(snapshot.templates)
                    }
                    if (snapshot.templateExercises.isNotEmpty()) {
                        templateDao.insertAllTemplateExercisesExact(snapshot.templateExercises)
                    }
                    if (snapshot.templateSets.isNotEmpty()) {
                        templateDao.insertAllTemplateSetsExact(snapshot.templateSets)
                    }
                    if (snapshot.sessions.isNotEmpty()) {
                        sessionDao.insertAllSessionsExact(snapshot.sessions)
                    }
                    if (snapshot.sessionExercises.isNotEmpty()) {
                        sessionDao.insertAllSessionExercisesExact(snapshot.sessionExercises)
                    }
                    if (snapshot.loggedSets.isNotEmpty()) {
                        sessionDao.insertAllLoggedSetsExact(snapshot.loggedSets)
                    }
                    if (snapshot.measurementEntries.isNotEmpty()) {
                        bodyMeasurementDao.insertAllEntriesExact(snapshot.measurementEntries)
                    }
                    if (snapshot.measurementValues.isNotEmpty()) {
                        bodyMeasurementDao.insertAllValuesExact(snapshot.measurementValues)
                    }
                }
                // Ensure any new built-in exercises are present without duplicating existing ones
                ensureSeededUnlocked()
            }
        }
    }

    companion object {
        fun normalizeName(name: String): String =
            name.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
    }
}
