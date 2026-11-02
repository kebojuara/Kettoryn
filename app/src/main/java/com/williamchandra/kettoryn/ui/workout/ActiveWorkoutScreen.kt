package com.williamchandra.kettoryn.ui.workout

import com.williamchandra.kettoryn.ui.visuals.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.williamchandra.kettoryn.data.local.ExerciseKinds
import com.williamchandra.kettoryn.data.local.LoggedSetEntity
import com.williamchandra.kettoryn.data.local.SessionExerciseWithSets
import com.williamchandra.kettoryn.data.local.SessionWithExercises
import com.williamchandra.kettoryn.data.local.SetType
import com.williamchandra.kettoryn.data.preferences.WeightUnit
import com.williamchandra.kettoryn.domain.UnitConverter
import com.williamchandra.kettoryn.domain.WorkoutMetricsCalculator
import com.williamchandra.kettoryn.ui.components.EmptyStatePanel
import com.williamchandra.kettoryn.ui.components.ErrorStateBanner
import com.williamchandra.kettoryn.ui.components.KettorynPanel
import com.williamchandra.kettoryn.ui.components.MetricStrip
import com.williamchandra.kettoryn.ui.components.TagChip
import com.williamchandra.kettoryn.ui.theme.KettorynTheme
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    artworkExercises: List<com.williamchandra.kettoryn.data.local.ExerciseEntity> = emptyList(),
    sessionDetail: SessionWithExercises?,
    previousSetsByExerciseKey: Map<String, List<LoggedSetEntity>>,
    weightUnit: WeightUnit,
    defaultRestSeconds: Int,
    restTimerEndMillis: Long?,
    onNavigateBack: () -> Unit,
    onUpdateTitle: (Long, String) -> Unit,
    onOpenExercisePicker: () -> Unit,
    onRemoveExercise: (sessionId: Long, sessionExerciseId: Long) -> Unit,
    onAddSet: (sessionExerciseId: Long) -> Unit,
    onUpdateSet: (setId: Long, weightGrams: Long?, reps: Int?, isComplete: Boolean, setType: SetType) -> Unit,
    onDeleteSet: (sessionExerciseId: Long, setId: Long) -> Unit,
    onStartOrAdjustRestTimer: (endEpochMillis: Long?) -> Unit,
    onFinishWorkout: (sessionId: Long) -> Unit,
    onDiscardWorkout: (sessionId: Long) -> Unit,
    exerciseRestOverrides: Map<Long, Int> = emptyMap(),
    restTimerExerciseId: Long? = null,
    pausedRestSeconds: Int? = null,
    restTimerEndElapsedMillis: Long? = null,
    onAdjustRestSeconds: ((Int) -> Unit)? = null,
    onConfigureExerciseRest: (Long, Int) -> Unit = { _, _ -> },
    onStartExerciseRest: (Long, Int) -> Unit = { _, _ -> },
    onToggleRestPause: () -> Unit = {},
    onRestoreSet: (LoggedSetEntity) -> Unit = {},
    onUpdateTimedSet: (Long, Int?, Long?, Boolean, SetType) -> Unit = { _, _, _, _, _ -> },
    isEditingHistoricalSession: Boolean = false
) {
    if (sessionDetail == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Active Workout") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                EmptyStatePanel(
                    title = "No active workout",
                    description = "Start a workout from Home or Routines.",
                    actionLabel = "Back to Home",
                    onActionClick = onNavigateBack
                )
            }
        }
        return
    }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var restEditId by rememberSaveable { mutableStateOf<Long?>(null) }
    val session = sessionDetail.session
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session.startedAt, restTimerEndMillis) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val elapsedSeconds = if (isEditingHistoricalSession) {
        ((session.endedAt ?: session.startedAt) - session.startedAt).coerceAtLeast(0L) / 1000L
    } else {
        (nowMillis - session.startedAt).coerceAtLeast(0L) / 1000L
    }

    val remainingRestSeconds = remember(nowMillis, restTimerEndMillis) {
        val end = restTimerEndMillis ?: return@remember 0L
        com.williamchandra.kettoryn.domain.RestTimerCalculator.remainingSeconds(
            end, restTimerEndElapsedMillis, nowMillis, android.os.SystemClock.elapsedRealtime())
    }

    val allSets = sessionDetail.exercises.flatMap { it.sets }
    val completedWorkSets = WorkoutMetricsCalculator.countCompletedWorkSetsFromEntities(allSets)
    val totalCompletedSets = allSets.count { it.isComplete }
    val totalVolumeGrams = WorkoutMetricsCalculator.calculateTotalVolumeGramsFromEntities(allSets)

    var showFinishDialog by rememberSaveable { mutableStateOf(false) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    var showEditTitleDialog by rememberSaveable { mutableStateOf(false) }
    var showManualRestDialog by rememberSaveable { mutableStateOf(false) }
    var validationBanner by rememberSaveable { mutableStateOf<String?>(null) }
    val compactHeader = LocalConfiguration.current.screenWidthDp <= 360 || LocalDensity.current.fontScale >= 1.25f

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(
                            role = Role.Button,
                            onClickLabel = "Rename workout",
                            onClick = { showEditTitleDialog = true }
                        )
                    ) {
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = if (compactHeader) "Log" else session.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isEditingHistoricalSession) {
                                    if (compactHeader) "Editing" else "Editing completed workout"
                                } else {
                                    WorkoutMetricsCalculator.formatDurationSeconds(elapsedSeconds)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Rename workout",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Minimize workout"
                        )
                    }
                },
                actions = {
                    if (!isEditingHistoricalSession) {
                        IconButton(
                            onClick = { showManualRestDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = "Rest timer",
                                tint = if (remainingRestSeconds > 0L) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                    Button(
                        onClick = {
                            if (isEditingHistoricalSession) {
                                onNavigateBack()
                            } else {
                                showFinishDialog = true
                            }
                        },
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .heightIn(min = 48.dp),
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isEditingHistoricalSession) "Done" else "Finish",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (compactHeader) item { Text(session.title, style = MaterialTheme.typography.headlineMedium) }
            if (validationBanner != null) {
                item {
                    ErrorStateBanner(
                        message = validationBanner!!,
                        onDismissOrRetry = { validationBanner = null }
                    )
                }
            }

            if (!isEditingHistoricalSession && (remainingRestSeconds > 0L || pausedRestSeconds != null)) {
                item {
                    Column {
                    Text(sessionDetail.exercises.firstOrNull { it.sessionExercise.id == restTimerExerciseId }?.sessionExercise?.nameSnapshot ?: "Rest", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onToggleRestPause) { Text(if (pausedRestSeconds != null) "Resume rest" else "Pause rest") }
                    RestTimerBar(
                        remainingSeconds = pausedRestSeconds?.toLong() ?: remainingRestSeconds,
                        onAdjustSeconds = { delta ->
                            if (onAdjustRestSeconds != null) onAdjustRestSeconds(delta)
                            else {
                            val currentEnd = restTimerEndMillis ?: System.currentTimeMillis()
                            val updated = currentEnd + delta * 1000L
                            if (updated <= System.currentTimeMillis()) {
                                onStartOrAdjustRestTimer(null)
                            } else {
                                onStartOrAdjustRestTimer(updated)
                            }
                            }
                        },
                        onSkip = { onStartOrAdjustRestTimer(null) }
                    )
                    }
                }
            }

            // Live session summary strip
            item {
                MetricStrip(
                    metrics = listOf(
                        "Duration" to WorkoutMetricsCalculator.formatDurationSeconds(elapsedSeconds),
                        "Sets" to completedWorkSets.toString(),
                        "Volume" to UnitConverter.formatVolumeWithUnit(totalVolumeGrams, weightUnit)
                    ),
                    infoByLabel = mapOf(
                        "Volume" to (
                            "Session Volume" to
                                "Sum of Weight × Reps for all completed work sets in this session. Warm-up sets (W) and unchecked rows are excluded."
                        )
                    )
                )
            }

            if (sessionDetail.exercises.isEmpty()) {
                item {
                    EmptyStatePanel(
                        title = "No exercises added yet",
                        description = "Choose an exercise to start logging sets.",
                        actionLabel = "Add Exercise",
                        onActionClick = onOpenExercisePicker
                    )
                }
            } else {
                items(
                    items = sessionDetail.exercises,
                    key = { it.sessionExercise.id }
                ) { exWithSets ->
                    val key = "${exWithSets.sessionExercise.exerciseId ?: 0L}:${exWithSets.sessionExercise.nameSnapshot.lowercase()}"
                    val prevSets = previousSetsByExerciseKey[key].orEmpty()
                    ExerciseLoggingSection(
                        exerciseWithSets = exWithSets,
                        sourceExercise = artworkExercises.firstOrNull { it.id == exWithSets.sessionExercise.exerciseId },
                        previousSets = prevSets,
                        restSeconds = exerciseRestOverrides[exWithSets.sessionExercise.id] ?: defaultRestSeconds,
                        onEditRest = if (isEditingHistoricalSession) null else ({ restEditId = exWithSets.sessionExercise.id }),
                        weightUnit = weightUnit,
                        onAddSet = { onAddSet(exWithSets.sessionExercise.id) },
                        onUpdateSet = { setId, wGrams, reps, isDone, sType ->
                            onUpdateSet(setId, wGrams, reps, isDone, sType)
                            val wasDone = exWithSets.sets.firstOrNull { it.id == setId }?.isComplete == true
                            if (isDone && !wasDone && sType == SetType.WORK && !isEditingHistoricalSession) {
                                onStartExerciseRest(exWithSets.sessionExercise.id, exerciseRestOverrides[exWithSets.sessionExercise.id] ?: defaultRestSeconds)
                            }
                        },
                        onUpdateTimedSet = { setId, seconds, meters, complete, type ->
                            onUpdateTimedSet(setId, seconds, meters, complete, type)
                            val wasDone = exWithSets.sets.firstOrNull { it.id == setId }?.isComplete == true
                            if (complete && !wasDone && type == SetType.WORK && !isEditingHistoricalSession) {
                                onStartExerciseRest(exWithSets.sessionExercise.id, exerciseRestOverrides[exWithSets.sessionExercise.id] ?: defaultRestSeconds)
                            }
                        },
                        onDeleteSet = { setId ->
                            val deleted = exWithSets.sets.firstOrNull { it.id == setId }
                            onDeleteSet(exWithSets.sessionExercise.id, setId)
                            if (deleted != null) scope.launch {
                                if (snackbar.showSnackbar("Set deleted", "Undo") == SnackbarResult.ActionPerformed) onRestoreSet(deleted)
                            }
                        },
                        onRemoveExercise = {
                            onRemoveExercise(session.id, exWithSets.sessionExercise.id)
                        },
                        onInvalidCompletionAttempt = { msg ->
                            validationBanner = msg
                        }
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onOpenExercisePicker,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add Exercise",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (!isEditingHistoricalSession) {
                        TextButton(
                            onClick = { showDiscardDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Text(
                                text = "Discard Workout",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    if (showManualRestDialog) {
        val initialSec = defaultRestSeconds.takeIf { it > 0 } ?: 90
        var restInput by rememberSaveable { mutableStateOf(initialSec.toString()) }
        val parsedSec = UnitConverter.parseRestSeconds(restInput)
        AlertDialog(
            onDismissRequest = { showManualRestDialog = false },
            title = { Text("Rest Timer (Seconds)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = restInput,
                        onValueChange = { restInput = it.filter { ch -> ch.isDigit() }.take(4) },
                        singleLine = true,
                        label = { Text("Duration in seconds (1 - 3600)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = {
                            val hint = if (parsedSec != null && parsedSec > 0) {
                                "${parsedSec}s (${UnitConverter.formatRestSecondsHint(parsedSec)})"
                            } else {
                                "Enter 1 to 3600 seconds"
                            }
                            Text(hint)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = parsedSec != null && parsedSec > 0,
                    onClick = {
                        val sec = parsedSec ?: 90
                        onStartOrAdjustRestTimer(System.currentTimeMillis() + sec * 1000L)
                        showManualRestDialog = false
                    }
                ) {
                    Text("Start Timer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualRestDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    restEditId?.let { id ->
        var input by rememberSaveable(id) { mutableStateOf((exerciseRestOverrides[id] ?: defaultRestSeconds).toString()) }
        val seconds = input.toIntOrNull()?.takeIf { it in 0..3600 }
        AlertDialog(onDismissRequest = { restEditId = null }, title = { Text("Exercise rest") },
            text = { OutlinedTextField(input, { input = it }, label = { Text("Seconds · 0 turns rest off") },
                isError = seconds == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) },
            confirmButton = { TextButton(enabled = seconds != null, onClick = { onConfigureExerciseRest(id, seconds!!); restEditId = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { restEditId = null }) { Text("Cancel") } })
    }

    if (showEditTitleDialog) {
        var draftTitle by rememberSaveable { mutableStateOf(session.title) }
        AlertDialog(
            onDismissRequest = { showEditTitleDialog = false },
            title = { Text("Workout Title") },
            text = {
                OutlinedTextField(
                    value = draftTitle,
                    onValueChange = { draftTitle = it },
                    singleLine = true,
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateTitle(session.id, draftTitle)
                        showEditTitleDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditTitleDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showFinishDialog) {
        val incompleteSetsCount = allSets.count { !it.isComplete }
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = {
                Text(
                    if (totalCompletedSets == 0) {
                        "Finish with 0 completed sets?"
                    } else {
                        "Finish workout?"
                    }
                )
            },
            text = {
                val message = when {
                    totalCompletedSets == 0 ->
                        "No sets are marked complete yet. Finishing now will save this workout with 0 completed volume."
                    incompleteSetsCount > 0 ->
                        "$totalCompletedSets sets completed and $incompleteSetsCount unchecked sets. Unchecked sets are excluded from volume and personal records."
                    else ->
                        "Save \"${session.title}\" with $totalCompletedSets completed sets (${UnitConverter.formatVolumeWithUnit(totalVolumeGrams, weightUnit)} volume)?"
                }
                Text(message)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFinishDialog = false
                        onFinishWorkout(session.id)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Finish Workout")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishDialog = false }) {
                    Text("Keep Logging")
                }
            }
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard workout?") },
            text = {
                Text("This active session will be permanently discarded and will not be saved to workout history.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardDialog = false
                        onDiscardWorkout(session.id)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RestTimerBar(
    remainingSeconds: Long,
    onAdjustSeconds: (Int) -> Unit,
    onSkip: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = KettorynTheme.colors.softAccent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) {
        FlowRow(
            maxItemsInEachRow = if (LocalConfiguration.current.screenWidthDp <= 360 || LocalDensity.current.fontScale >= 1.25f) 1 else 2,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = KettorynTheme.colors.onSoftAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Rest",
                        style = MaterialTheme.typography.labelSmall,
                        color = KettorynTheme.colors.onSoftAccent.copy(alpha = 0.8f)
                    )
                    Text(
                        text = WorkoutMetricsCalculator.formatDurationSeconds(remainingSeconds),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = KettorynTheme.colors.onSoftAccent
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { onAdjustSeconds(-15) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = MaterialTheme.shapes.extraSmall,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Text("-15s", style = MaterialTheme.typography.labelMedium, color = KettorynTheme.colors.onSoftAccent)
                }
                OutlinedButton(
                    onClick = { onAdjustSeconds(15) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = MaterialTheme.shapes.extraSmall,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Text("+15s", style = MaterialTheme.typography.labelMedium, color = KettorynTheme.colors.onSoftAccent)
                }
                IconButton(
                    onClick = onSkip,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Skip rest timer",
                        tint = KettorynTheme.colors.onSoftAccent
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseLoggingSection(
    sourceExercise: com.williamchandra.kettoryn.data.local.ExerciseEntity?,
    exerciseWithSets: SessionExerciseWithSets,
    previousSets: List<LoggedSetEntity>,
    restSeconds: Int,
    onEditRest: (() -> Unit)?,
    weightUnit: WeightUnit,
    onAddSet: () -> Unit,
    onUpdateSet: (setId: Long, weightGrams: Long?, reps: Int?, isComplete: Boolean, setType: SetType) -> Unit,
    onDeleteSet: (setId: Long) -> Unit,
    onUpdateTimedSet: (Long, Int?, Long?, Boolean, SetType) -> Unit,
    onRemoveExercise: () -> Unit,
    onInvalidCompletionAttempt: (String) -> Unit
) {
    val sessionEx = exerciseWithSets.sessionExercise
    val sets = exerciseWithSets.sets

    KettorynPanel(contentPadding = PaddingValues(12.dp)) {
        val compactExercise = LocalConfiguration.current.screenWidthDp <= 340 || LocalDensity.current.fontScale >= 1.25f
        if (compactExercise) {
            Text(sessionEx.nameSnapshot, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (sourceExercise != null && !sourceExercise.isBuiltIn) CustomExerciseImage(sourceExercise.customImageKey, Modifier.size(48.dp), sessionEx.nameSnapshot)
                ExerciseVisualResolver.snapshot(sessionEx.nameSnapshot, sessionEx.muscleSnapshot, sessionEx.equipmentSnapshot, sourceExercise)?.let {
                    ExerciseArt(it, Modifier.size(48.dp))
                }
                Text("${sessionEx.muscleSnapshot} · ${sessionEx.equipmentSnapshot}", Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = onRemoveExercise) { Icon(Icons.Default.DeleteOutline, "Remove ${sessionEx.nameSnapshot} from workout") }
            }
        } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (sourceExercise != null && !sourceExercise.isBuiltIn) {
                CustomExerciseImage(sourceExercise.customImageKey, Modifier.size(64.dp), sessionEx.nameSnapshot)
                Spacer(Modifier.width(12.dp))
            }
            ExerciseVisualResolver.snapshot(sessionEx.nameSnapshot, sessionEx.muscleSnapshot, sessionEx.equipmentSnapshot, sourceExercise)?.let { art ->
                ExerciseArt(art, Modifier.size(64.dp))
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sessionEx.nameSnapshot,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "${sessionEx.muscleSnapshot} · ${sessionEx.equipmentSnapshot}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRemoveExercise) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Remove ${sessionEx.nameSnapshot} from workout",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        }

        Spacer(modifier = Modifier.height(10.dp))

        if (onEditRest != null) TextButton(onClick = onEditRest, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Default.Timer, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (restSeconds == 0) "Rest off" else "Rest ${restSeconds}s")
        }

        // Table Header: SET | PREVIOUS | WEIGHT | REPS | DONE
        val reflowSets = LocalDensity.current.fontScale >= 1.25f || LocalConfiguration.current.screenWidthDp <= 340 || sessionEx.exerciseTypeSnapshot != "weight_reps" ||
            previousSets.any { "${UnitConverter.formatGramsForInput(it.weightGrams, weightUnit)} ${weightUnit.label} × ${it.reps}".length > 10 }
        if (!reflowSets) Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SET",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(48.dp),
                textAlign = TextAlign.Center
            )
            Text(
                text = "PREV.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Text(
                text = weightUnit.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "REPS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "DONE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(48.dp),
                textAlign = TextAlign.Center
            )
        }

        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)

        var workSetCounter = 0
        sets.forEachIndexed { idx, setItem ->
            if (setItem.setType == SetType.WORK) {
                workSetCounter++
            }
            val displaySetNum = if (setItem.setType == SetType.WARMUP) "W" else workSetCounter.toString()
            val prevSet = previousSets.getOrNull(idx)
            val prevText = when {
                prevSet == null -> "—"
                prevSet.exerciseType == "distance_duration" -> "${prevSet.distanceMeters ?: 0} m · ${prevSet.durationSeconds ?: 0}s"
                prevSet.exerciseType == "duration" -> "${prevSet.durationSeconds ?: 0}s"
                prevSet.reps == null -> "—"
                prevSet.exerciseType == "bodyweight_reps" && (prevSet.weightGrams ?: 0) == 0L -> "${prevSet.reps} reps"
                prevSet.weightGrams != null -> "${UnitConverter.formatGramsForInput(prevSet.weightGrams, weightUnit)} ${weightUnit.label} × ${prevSet.reps}"
                else -> "${prevSet.reps} reps"
            }

            SetRowItem(
                setEntity = setItem,
                displaySetLabel = displaySetNum,
                previousText = prevText,
                weightUnit = weightUnit,
                onUpdate = { wGrams, reps, isDone, sType ->
                    onUpdateSet(setItem.id, wGrams, reps, isDone, sType)
                },
                forceReflow = reflowSets,
                onUpdateTimed = { seconds, meters, complete, type -> onUpdateTimedSet(setItem.id, seconds, meters, complete, type) },
                onDelete = { onDeleteSet(setItem.id) },
                onInvalidCompletionAttempt = onInvalidCompletionAttempt
            )
            if (idx < sets.lastIndex) {
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onAddSet,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Add Set", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SetRowItem(
    setEntity: LoggedSetEntity,
    displaySetLabel: String,
    previousText: String,
    weightUnit: WeightUnit,
    onUpdate: (weightGrams: Long?, reps: Int?, isComplete: Boolean, setType: SetType) -> Unit,
    onDelete: () -> Unit,
    forceReflow: Boolean,
    onUpdateTimed: (Int?, Long?, Boolean, SetType) -> Unit,
    onInvalidCompletionAttempt: (String) -> Unit
) {
    val timed = ExerciseKinds.isTimed(setEntity.exerciseType)
    val distance = setEntity.exerciseType == "distance_duration"
    val bodyweight = setEntity.exerciseType == "bodyweight_reps"
    var secondsText by rememberSaveable(setEntity.id) { mutableStateOf(setEntity.durationSeconds?.toString().orEmpty()) }
    var metersText by rememberSaveable(setEntity.id) { mutableStateOf(setEntity.distanceMeters?.toString().orEmpty()) }
    fun validTimed(seconds: String, meters: String) = seconds.toIntOrNull()?.let { it in 1..604800 } == true &&
        (!distance || meters.toLongOrNull()?.let { it in 0..10000000 } == true)
    var weightText by rememberSaveable(setEntity.id, weightUnit) {
        mutableStateOf(UnitConverter.formatGramsForInput(setEntity.weightGrams, weightUnit))
    }
    var repsText by rememberSaveable(setEntity.id) {
        mutableStateOf(setEntity.reps?.toString() ?: "")
    }

    // This row owns its saved edit buffers. Asynchronous Room acknowledgements can
    // arrive between keystrokes; feeding them back here corrupts decimals/fast entry.
    // A new row ID or weight-unit change initializes a fresh buffer above.

    val rowBackground = if (setEntity.isComplete) {
        KettorynTheme.colors.softAccent
    } else {
        Color.Transparent
    }

    var revealed by rememberSaveable(setEntity.id) { mutableStateOf(false) }
    var confirmDelete by rememberSaveable(setEntity.id) { mutableStateOf(false) }
    val revealPx = with(LocalDensity.current) { 104.dp.toPx() }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete set?") },
        text = { Text("This set will be removed. You can undo the deletion.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; revealed = false; onDelete() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })

    val reflow = forceReflow
    val actions: @Composable () -> Unit = {
        Box {
            var menuOpen by remember { mutableStateOf(false) }
            TextButton(
                onClick = { menuOpen = true },
                modifier = Modifier.width(48.dp).heightIn(min = 48.dp).semantics { contentDescription = "Set $displaySetLabel options" },
                contentPadding = PaddingValues(0.dp),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.textButtonColors(containerColor = KettorynTheme.colors.mutedSurface)
            ) { Text("$displaySetLabel ⋮", color = if (setEntity.setType == SetType.WARMUP) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text(if (setEntity.setType == SetType.WORK) "Make warm-up set" else "Make work set") }, onClick = {
                    menuOpen = false
                    val nextType = if (setEntity.setType == SetType.WORK) SetType.WARMUP else SetType.WORK
                    if (timed) onUpdateTimed(secondsText.toIntOrNull(), metersText.toLongOrNull(), setEntity.isComplete, nextType)
                    else onUpdate(UnitConverter.parseWeightToGrams(weightText, weightUnit), UnitConverter.parseReps(repsText), setEntity.isComplete, nextType)
                })
                DropdownMenuItem(text = { Text("Delete set", color = MaterialTheme.colorScheme.error) }, onClick = { menuOpen = false; confirmDelete = true })
            }
        }
    }
    val inputs: @Composable RowScope.() -> Unit = {
        if (timed) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Seconds", style = MaterialTheme.typography.bodySmall)
                CompactNumericField(secondsText, { value ->
                    secondsText = value
                    onUpdateTimed(value.toIntOrNull(), metersText.toLongOrNull(), setEntity.isComplete && validTimed(value, metersText), setEntity.setType)
                }, "0", KeyboardType.Number, "Set $displaySetLabel duration in seconds", Modifier.fillMaxWidth())
            }
            if (distance) {
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Meters", style = MaterialTheme.typography.bodySmall)
                    CompactNumericField(metersText, { value ->
                        metersText = value
                        onUpdateTimed(secondsText.toIntOrNull(), value.toLongOrNull(), setEntity.isComplete && validTimed(secondsText, value), setEntity.setType)
                    }, "0", KeyboardType.Number, "Set $displaySetLabel distance in meters", Modifier.fillMaxWidth())
                }
            }
        } else {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (reflow) Text(if (bodyweight) "Added ${weightUnit.label}" else if (setEntity.exerciseType == "assisted_bodyweight") "Assist ${weightUnit.label}" else weightUnit.label, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                CompactNumericField(weightText, { value ->
                    weightText = value
                    val weight = UnitConverter.parseWeightToGrams(value, weightUnit); val reps = UnitConverter.parseReps(repsText)
                    onUpdate(weight, reps, setEntity.isComplete && (weight != null || bodyweight) && reps != null, setEntity.setType)
                }, if (bodyweight) "—" else "0", KeyboardType.Decimal, "Set $displaySetLabel weight in ${weightUnit.label}", Modifier.fillMaxWidth())
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (reflow) Text("Reps", style = MaterialTheme.typography.bodySmall)
                CompactNumericField(repsText, { value ->
                    repsText = value
                    val weight = UnitConverter.parseWeightToGrams(weightText, weightUnit); val reps = UnitConverter.parseReps(value)
                    onUpdate(weight, reps, setEntity.isComplete && (weight != null || bodyweight) && reps != null, setEntity.setType)
                }, "0", KeyboardType.Number, "Set $displaySetLabel repetitions", Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.width(if (reflow) 64.dp else 48.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (reflow) Text("Done", style = MaterialTheme.typography.bodySmall, maxLines = 1)
            Row(
                modifier = Modifier.width(48.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isDone = setEntity.isComplete
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            if (isDone) MaterialTheme.colorScheme.primary else KettorynTheme.colors.mutedSurface
                        )
                        .border(
                            width = 1.dp,
                            color = if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = MaterialTheme.shapes.small
                        )
                        .clickable(
                            role = Role.Checkbox,
                            onClickLabel = if (isDone) "Mark set incomplete" else "Mark set complete"
                        ) {
                            if (timed) {
                                if (!isDone && !validTimed(secondsText, metersText)) onInvalidCompletionAttempt("Enter a valid duration${if (distance) " and distance" else ""} before completing this set.")
                                else onUpdateTimed(secondsText.toIntOrNull(), metersText.toLongOrNull(), !isDone, setEntity.setType)
                            } else {
                                val weight = UnitConverter.parseWeightToGrams(weightText, weightUnit)
                                val reps = UnitConverter.parseReps(repsText)
                                if (!isDone && ((weight == null && !bodyweight) || reps == null)) onInvalidCompletionAttempt("Enter valid ${if (bodyweight) "reps" else "weight and reps"} before completing this set.")
                                else onUpdate(weight, reps, !isDone, setEntity.setType)
                            }
                        }
                        .semantics {
                            stateDescription = if (isDone) "Completed" else "Incomplete"
                            contentDescription = if (isDone) {
                                "Set $displaySetLabel completed"
                            } else {
                                "Complete set $displaySetLabel"
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = if (isDone) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }


            }
        }
    }
    Box(Modifier.fillMaxWidth()) {
        if (revealed) TextButton(shape = MaterialTheme.shapes.small, onClick = { confirmDelete = true }, modifier = Modifier.align(Alignment.CenterEnd).width(104.dp).heightIn(min = 56.dp),
            colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) {
            Icon(Icons.Default.DeleteOutline, null, Modifier.size(18.dp)); Text("Delete")
        }
    Column(Modifier.fillMaxWidth().offset { IntOffset(if (revealed) -revealPx.toInt() else 0, 0) }
        .pointerInput(setEntity.id) {
            var distance = 0f
            detectHorizontalDragGestures(onDragStart = { distance = 0f }, onDragEnd = {
                if (distance < -48.dp.toPx()) revealed = true else if (distance > 48.dp.toPx()) revealed = false
            }) { _, amount -> distance += amount }
        }.background(if (setEntity.isComplete) rowBackground else MaterialTheme.colorScheme.surface).padding(vertical = 8.dp, horizontal = 2.dp)) {
        if (reflow) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                actions()
                Spacer(Modifier.width(12.dp))
                Text("Previous: $previousText", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) { inputs() }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                actions()
                Text(previousText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp))
                inputs()
            }
        }
    }
    }
}

@Composable
private fun CompactNumericField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
            fontFeatureSettings = "tnum"
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = ImeAction.Next
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .background(KettorynTheme.colors.mutedSurface)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .semantics { this.contentDescription = contentDescription },
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        textAlign = TextAlign.Center
                    )
                }
                innerTextField()
            }
        }
    )
}
