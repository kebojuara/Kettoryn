package com.williamchandra.kettoryn.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.williamchandra.kettoryn.data.backup.BackupManager
import com.williamchandra.kettoryn.data.backup.StagedBackup
import com.williamchandra.kettoryn.data.local.BodyMeasurementEntryWithValues
import com.williamchandra.kettoryn.data.local.ExerciseEntity
import com.williamchandra.kettoryn.data.local.LoggedSetEntity
import com.williamchandra.kettoryn.data.local.SessionWithExercises
import com.williamchandra.kettoryn.data.local.SetType
import com.williamchandra.kettoryn.data.local.TemplateWithExercises
import com.williamchandra.kettoryn.data.preferences.LengthUnit
import com.williamchandra.kettoryn.data.preferences.ThemeMode
import com.williamchandra.kettoryn.data.preferences.UserPreferences
import com.williamchandra.kettoryn.data.preferences.UserPreferencesRepository
import com.williamchandra.kettoryn.data.preferences.WeightUnit
import com.williamchandra.kettoryn.data.repository.CompletedSessionSummary
import com.williamchandra.kettoryn.data.repository.TemplateDraftExercise
import com.williamchandra.kettoryn.data.repository.TemplateDraftSet
import com.williamchandra.kettoryn.data.repository.WorkoutRepository
import com.williamchandra.kettoryn.domain.DateRangePreset
import com.williamchandra.kettoryn.domain.DateRangeSelection
import com.williamchandra.kettoryn.domain.MeasurementMetric
import com.williamchandra.kettoryn.domain.UnitConverter
import com.williamchandra.kettoryn.ui.templates.EditableTemplateExerciseUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicLong

class KettorynViewModel(
    private val workoutRepository: WorkoutRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val preferencesState: StateFlow<UserPreferences> = preferencesRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UserPreferences()
        )

    val exercisesState: StateFlow<List<ExerciseEntity>> = workoutRepository.observeExercises()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val templatesState: StateFlow<List<TemplateWithExercises>> = workoutRepository.observeTemplates()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val activeSessionState: StateFlow<SessionWithExercises?> = workoutRepository.observeActiveSession()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    val completedSessionsState: StateFlow<List<CompletedSessionSummary>> = workoutRepository.observeCompletedSessions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val measurementEntriesState: StateFlow<List<BodyMeasurementEntryWithValues>> = workoutRepository.observeMeasurementEntries()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    // Date range filter state for Home, History, and Progress
    private val _homeDateRange = MutableStateFlow(DateRangeSelection(DateRangePreset.THIS_WEEK))
    val homeDateRange: StateFlow<DateRangeSelection> = _homeDateRange.asStateFlow()

    private val _historyDateRange = MutableStateFlow(DateRangeSelection(DateRangePreset.LAST_3_MONTHS))
    val historyDateRange: StateFlow<DateRangeSelection> = _historyDateRange.asStateFlow()

    private val _progressDateRange = MutableStateFlow(DateRangeSelection(DateRangePreset.LAST_3_MONTHS))
    val progressDateRange: StateFlow<DateRangeSelection> = _progressDateRange.asStateFlow()

    private val _measurementsDateRange = MutableStateFlow(DateRangeSelection(DateRangePreset.LAST_3_MONTHS))
    val measurementsDateRange: StateFlow<DateRangeSelection> = _measurementsDateRange.asStateFlow()

    fun updateHomeDateRange(selection: DateRangeSelection) {
        _homeDateRange.value = selection
    }

    fun updateHistoryDateRange(selection: DateRangeSelection) {
        _historyDateRange.value = selection
    }

    fun updateProgressDateRange(selection: DateRangeSelection) {
        _progressDateRange.value = selection
    }

    fun updateMeasurementsDateRange(selection: DateRangeSelection) {
        _measurementsDateRange.value = selection
    }

    // Previous performance map keyed by "${exerciseId ?: 0L}:${nameSnapshot.lowercase()}"
    val previousSetsMapState: StateFlow<Map<String, List<LoggedSetEntity>>> = combine(
        activeSessionState,
        completedSessionsState
    ) { active, completed ->
        if (active == null) return@combine emptyMap()
        val result = mutableMapOf<String, List<LoggedSetEntity>>()
        for (ex in active.exercises) {
            val exId = ex.sessionExercise.exerciseId
            val name = ex.sessionExercise.nameSnapshot
            val key = "${exId ?: 0L}:${name.lowercase()}"
            val mostRecentMatch = completed.firstOrNull { summary ->
                summary.detail.exercises.any { se ->
                    (exId != null && se.sessionExercise.exerciseId == exId) ||
                        se.sessionExercise.nameSnapshot.equals(name, ignoreCase = true)
                }
            }?.detail?.exercises?.firstOrNull { se ->
                (exId != null && se.sessionExercise.exerciseId == exId) ||
                    se.sessionExercise.nameSnapshot.equals(name, ignoreCase = true)
            }
            if (mostRecentMatch != null) {
                result[key] = mostRecentMatch.sets.filter { it.isComplete }
            }
        }
        result
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyMap()
    )

    // Exercise search & filters
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedMuscle = MutableStateFlow<String?>(null)
    val selectedMuscle: StateFlow<String?> = _selectedMuscle.asStateFlow()

    private val _selectedEquipment = MutableStateFlow<String?>(null)
    val selectedEquipment: StateFlow<String?> = _selectedEquipment.asStateFlow()

    fun updateSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun updateSelectedMuscle(muscle: String?) {
        _selectedMuscle.value = muscle
    }

    fun updateSelectedEquipment(equip: String?) {
        _selectedEquipment.value = equip
    }

    // Template Editor draft state
    private val uidCounter = AtomicLong(1L)
    private val _editingTemplateId = MutableStateFlow<Long?>(null)
    val editingTemplateId: StateFlow<Long?> = _editingTemplateId.asStateFlow()

    private val _templateDraftTitle = MutableStateFlow("")
    val templateDraftTitle: StateFlow<String> = _templateDraftTitle.asStateFlow()

    private val _templateDraftExercises = MutableStateFlow<List<EditableTemplateExerciseUi>>(emptyList())
    val templateDraftExercises: StateFlow<List<EditableTemplateExerciseUi>> = _templateDraftExercises.asStateFlow()

    private val _templateEditorError = MutableStateFlow<String?>(null)
    val templateEditorError: StateFlow<String?> = _templateEditorError.asStateFlow()

    fun startNewTemplateDraft() {
        _editingTemplateId.value = null
        _templateDraftTitle.value = ""
        _templateDraftExercises.value = emptyList()
        _templateEditorError.value = null
    }

    fun loadTemplateIntoDraft(templateId: Long) {
        val unit = preferencesState.value.weightUnit
        val tpl = templatesState.value.firstOrNull { it.template.id == templateId }
        if (tpl != null) {
            _editingTemplateId.value = tpl.template.id
            _templateDraftTitle.value = tpl.template.title
            _templateDraftExercises.value = tpl.exercises.map { ex ->
                val firstSet = ex.sets.firstOrNull()
                EditableTemplateExerciseUi(
                    uid = uidCounter.getAndIncrement(),
                    exerciseId = ex.templateExercise.exerciseId,
                    name = ex.templateExercise.exerciseNameSnapshot,
                    primaryMuscle = ex.templateExercise.muscleSnapshot,
                    equipment = ex.templateExercise.equipmentSnapshot,
                    targetSetsText = ex.sets.size.coerceAtLeast(1).toString(),
                    targetRepsText = if (com.williamchandra.kettoryn.data.local.ExerciseKinds.isTimed(ex.templateExercise.exerciseTypeSnapshot)) (firstSet?.suggestedDurationSeconds ?: 30).toString() else (firstSet?.suggestedReps ?: 8).toString(),
                    targetWeightText = if (ex.templateExercise.exerciseTypeSnapshot == "distance_duration") firstSet?.suggestedDistanceMeters?.toString().orEmpty() else UnitConverter.formatGramsForInput(firstSet?.suggestedWeightGrams, unit),
                    exerciseType = ex.templateExercise.exerciseTypeSnapshot
                )
            }
            _templateEditorError.value = null
        }
    }

    fun updateTemplateDraftTitle(title: String) {
        _templateDraftTitle.value = title
        _templateEditorError.value = null
    }

    fun addExerciseToTemplateDraft(exercise: ExerciseEntity) {
        val current = _templateDraftExercises.value.toMutableList()
        current.add(
            EditableTemplateExerciseUi(
                uid = uidCounter.getAndIncrement(),
                exerciseId = exercise.id,
                name = exercise.name,
                primaryMuscle = exercise.primaryMuscle,
                equipment = exercise.equipment,
                targetSetsText = "3",
                targetRepsText = if (com.williamchandra.kettoryn.data.local.ExerciseKinds.isTimed(exercise.exerciseType)) "30" else "8",
                targetWeightText = "",
                exerciseType = exercise.exerciseType
            )
        )
        _templateDraftExercises.value = current
        _templateEditorError.value = null
    }

    fun moveTemplateDraftExerciseUp(index: Int) {
        val current = _templateDraftExercises.value.toMutableList()
        if (index in 1..current.lastIndex) {
            val item = current.removeAt(index)
            current.add(index - 1, item)
            _templateDraftExercises.value = current
        }
    }

    fun moveTemplateDraftExerciseDown(index: Int) {
        val current = _templateDraftExercises.value.toMutableList()
        if (index in 0 until current.lastIndex) {
            val item = current.removeAt(index)
            current.add(index + 1, item)
            _templateDraftExercises.value = current
        }
    }

    fun removeTemplateDraftExercise(index: Int) {
        val current = _templateDraftExercises.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _templateDraftExercises.value = current
        }
    }

    fun updateTemplateDraftExerciseTargets(
        index: Int,
        setsText: String,
        repsText: String,
        weightText: String
    ) {
        val current = _templateDraftExercises.value.toMutableList()
        if (index in current.indices) {
            current[index] = current[index].copy(
                targetSetsText = setsText,
                targetRepsText = repsText,
                targetWeightText = weightText
            )
            _templateDraftExercises.value = current
        }
    }

    fun clearTemplateEditorError() {
        _templateEditorError.value = null
    }

    fun saveTemplateDraft(onSuccess: () -> Unit) {
        val cleanTitle = _templateDraftTitle.value.trim()
        if (cleanTitle.isEmpty()) {
            _templateEditorError.value = "Enter a template routine name."
            return
        }
        val items = _templateDraftExercises.value
        if (items.isEmpty()) {
            _templateEditorError.value = "Add at least one exercise to save this template."
            return
        }
        val unit = preferencesState.value.weightUnit
        val mapped = items.map { ui ->
            val setCount = ui.targetSetsText.trim().toIntOrNull()?.coerceIn(1, 20) ?: 3
            val reps = UnitConverter.parseReps(ui.targetRepsText) ?: 8
            val weightGrams = UnitConverter.parseWeightToGrams(ui.targetWeightText, unit)
            TemplateDraftExercise(
                exerciseId = ui.exerciseId,
                nameFallback = ui.name,
                muscleFallback = ui.primaryMuscle,
                equipmentFallback = ui.equipment,
                sets = List(setCount) {
                    TemplateDraftSet(
                        suggestedReps = if (com.williamchandra.kettoryn.data.local.ExerciseKinds.isTimed(ui.exerciseType)) null else reps,
                        suggestedWeightGrams = if (com.williamchandra.kettoryn.data.local.ExerciseKinds.isTimed(ui.exerciseType)) null else weightGrams,
                        suggestedDurationSeconds = if (com.williamchandra.kettoryn.data.local.ExerciseKinds.isTimed(ui.exerciseType)) ui.targetRepsText.toIntOrNull()?.takeIf { it in 1..604800 } else null,
                        suggestedDistanceMeters = if (ui.exerciseType == "distance_duration") ui.targetWeightText.toLongOrNull()?.takeIf { it in 0..10000000 } else null
                    )
                }
            )
        }
        viewModelScope.launch {
            runCatching {
                workoutRepository.saveTemplate(
                    templateId = _editingTemplateId.value,
                    title = cleanTitle,
                    exercises = mapped
                )
            }.onSuccess {
                onSuccess()
            }.onFailure { err ->
                _templateEditorError.value = err.message ?: "Could not save template."
            }
        }
    }

    fun duplicateTemplate(templateId: Long) {
        viewModelScope.launch {
            workoutRepository.duplicateTemplate(templateId)
        }
    }

    fun deleteTemplate(templateId: Long) {
        viewModelScope.launch {
            workoutRepository.deleteTemplate(templateId)
        }
    }

    // Custom exercises
    fun createCustomExerciseSync(
        name: String,
        muscle: String,
        equipment: String,
        instructions: String,
        exerciseType: String = "weight_reps",
        customImageKey: String? = null
    ): Result<Long> = runBlocking {
        runCatching {
            workoutRepository.createCustomExercise(name, muscle, equipment, instructions, exerciseType, customImageKey)
        }
    }

    fun updateCustomExerciseSync(
        id: Long,
        name: String,
        muscle: String,
        equipment: String,
        instructions: String,
        exerciseType: String = "weight_reps",
        customImageKey: String? = null
    ): Result<Unit> = runBlocking {
        runCatching {
            workoutRepository.updateCustomExercise(id, name, muscle, equipment, instructions, exerciseType, customImageKey)
        }
    }

    fun deleteCustomExercise(id: Long, onDeleted: () -> Unit) {
        viewModelScope.launch {
            workoutRepository.deleteCustomExercise(id)
            onDeleted()
        }
    }

    // Active Workout actions
    fun startEmptyWorkout(
        title: String = "Workout",
        initialExerciseId: Long? = null,
        onStarted: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val id = workoutRepository.startEmptyWorkout(title = title, initialExerciseId = initialExerciseId)
            onStarted(id)
        }
    }

    fun startWorkoutFromTemplate(templateId: Long, onStarted: (Long) -> Unit) {
        viewModelScope.launch {
            runCatching {
                workoutRepository.startWorkoutFromTemplate(templateId)
            }.onSuccess { id ->
                onStarted(id)
            }
        }
    }

    fun updateSessionTitle(sessionId: Long, title: String) {
        viewModelScope.launch {
            workoutRepository.updateSessionTitle(sessionId, title)
        }
    }

    fun addExerciseToSession(sessionId: Long, exerciseId: Long, onAdded: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                workoutRepository.addExerciseToActiveSession(sessionId, exerciseId)
            }.onSuccess {
                onAdded()
            }
        }
    }

    fun removeExerciseFromSession(sessionId: Long, sessionExerciseId: Long) {
        viewModelScope.launch {
            workoutRepository.removeExerciseFromSession(sessionId, sessionExerciseId)
            if (preferencesState.value.restTimerExerciseId == sessionExerciseId) preferencesRepository.setRestTimerEndEpochMillis(null)
        }
    }

    fun addSetToExercise(sessionExerciseId: Long) {
        viewModelScope.launch {
            workoutRepository.addSetToExercise(sessionExerciseId)
        }
    }

    fun updateSet(
        setId: Long,
        weightGrams: Long?,
        reps: Int?,
        isComplete: Boolean,
        setType: SetType
    ) {
        viewModelScope.launch {
            workoutRepository.updateSet(setId, weightGrams, reps, isComplete, setType)
        }
    }

    fun updateTimedSet(id: Long, seconds: Int?, meters: Long?, complete: Boolean, type: SetType) {
        viewModelScope.launch { workoutRepository.updateTimedSet(id, seconds, meters, complete, type) }
    }

    fun deleteSet(sessionExerciseId: Long, setId: Long) {
        viewModelScope.launch {
            workoutRepository.deleteSet(sessionExerciseId, setId)
            if (preferencesState.value.restTimerExerciseId == sessionExerciseId) preferencesRepository.setRestTimerEndEpochMillis(null)
        }
    }

    fun restoreDeletedSet(set: com.williamchandra.kettoryn.data.local.LoggedSetEntity) {
        viewModelScope.launch { workoutRepository.restoreDeletedSet(set) }
    }
    fun setExerciseRest(id: Long, seconds: Int) { viewModelScope.launch { preferencesRepository.setExerciseRest(id, seconds) } }
    fun startExerciseRest(id: Long, seconds: Int) { viewModelScope.launch { preferencesRepository.startExerciseRest(id, seconds) } }
    fun adjustRestSeconds(delta: Int) { viewModelScope.launch { preferencesRepository.adjustRestSeconds(delta) } }
    fun toggleRestPause() { viewModelScope.launch { preferencesRepository.toggleRestPause() } }

    fun finishWorkout(sessionId: Long, onFinished: () -> Unit) {
        viewModelScope.launch {
            workoutRepository.finishWorkout(sessionId)
            preferencesRepository.setRestTimerEndEpochMillis(null)
            onFinished()
        }
    }

    fun discardWorkout(sessionId: Long, onDiscarded: () -> Unit) {
        viewModelScope.launch {
            workoutRepository.discardWorkout(sessionId)
            preferencesRepository.setRestTimerEndEpochMillis(null)
            onDiscarded()
        }
    }

    fun deleteCompletedSession(sessionId: Long, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            workoutRepository.deleteCompletedSession(sessionId)
            onDeleted()
        }
    }

    // Body Measurements Journal (F6)
    fun saveMeasurementEntry(
        entryId: Long?,
        recordedAt: Long,
        values: Map<MeasurementMetric, Long>,
        note: String = "",
        onSaved: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            runCatching {
                workoutRepository.saveMeasurementEntry(
                    entryId = entryId,
                    recordedAt = recordedAt,
                    values = values,
                    note = note
                )
            }.onSuccess {
                onSaved()
            }.onFailure { err ->
                onError(err.message ?: "Could not save measurements.")
            }
        }
    }

    fun deleteMeasurementEntry(entryId: Long) {
        viewModelScope.launch {
            workoutRepository.deleteMeasurementEntry(entryId)
        }
    }

    // Settings & Rest Timer
    fun setWeightUnit(unit: WeightUnit) {
        viewModelScope.launch {
            preferencesRepository.setWeightUnit(unit)
        }
    }

    fun setLengthUnit(unit: LengthUnit) {
        viewModelScope.launch {
            preferencesRepository.setLengthUnit(unit)
        }
    }

    fun setDefaultRestSeconds(seconds: Int) {
        viewModelScope.launch {
            preferencesRepository.setDefaultRestSeconds(seconds)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun setRestTimerEndMillis(endMillis: Long?) {
        viewModelScope.launch {
            preferencesRepository.setRestTimerEndEpochMillis(endMillis)
        }
    }

    // Portable Backup & Restore (F2)
    fun exportBackup(
        outputStream: OutputStream,
        onComplete: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    outputStream.use { os ->
                        BackupManager.exportBackupToStream(
                            outputStream = os,
                            workoutRepository = workoutRepository,
                            preferencesRepository = preferencesRepository
                        )
                    }
                }
            }
            onComplete(res)
        }
    }

    fun inspectBackup(
        inputStream: InputStream,
        onResult: (Result<StagedBackup>) -> Unit
    ) {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    inputStream.use { ins ->
                        BackupManager.inspectAndStageBackup(ins)
                    }
                }
            }
            onResult(res)
        }
    }

    fun applyStagedBackup(
        staged: StagedBackup,
        onComplete: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    BackupManager.applyStagedBackup(
                        staged = staged,
                        workoutRepository = workoutRepository,
                        preferencesRepository = preferencesRepository
                    )
                }
            }
            onComplete(res)
        }
    }

    fun observeExerciseById(id: Long) = workoutRepository.observeExerciseById(id)

    fun observeSessionById(sessionId: Long) = workoutRepository.observeSessionDetail(sessionId)

    companion object {
        fun provideFactory(
            workoutRepository: WorkoutRepository,
            preferencesRepository: UserPreferencesRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return KettorynViewModel(workoutRepository, preferencesRepository) as T
            }
        }
    }
}
