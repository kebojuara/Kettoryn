package com.williamchandra.kettoryn.ui.history

import com.williamchandra.kettoryn.ui.visuals.*
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.williamchandra.kettoryn.data.local.SessionWithExercises
import com.williamchandra.kettoryn.data.local.SetType
import com.williamchandra.kettoryn.data.preferences.WeightUnit
import com.williamchandra.kettoryn.data.repository.CompletedSessionSummary
import com.williamchandra.kettoryn.domain.DateRangeCalculator
import com.williamchandra.kettoryn.domain.DateRangeSelection
import com.williamchandra.kettoryn.domain.MuscleSplitCalculator
import com.williamchandra.kettoryn.domain.UnitConverter
import com.williamchandra.kettoryn.domain.WorkoutMetricsCalculator
import com.williamchandra.kettoryn.ui.components.DateRangeFilterBar
import com.williamchandra.kettoryn.ui.components.EmptyStatePanel
import com.williamchandra.kettoryn.ui.components.KettorynPanel
import com.williamchandra.kettoryn.ui.components.MetricStrip
import com.williamchandra.kettoryn.ui.components.TagChip
import com.williamchandra.kettoryn.ui.share.WorkoutShareSheet
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HistoryDateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy • HH:mm", Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    artworkExercises: List<com.williamchandra.kettoryn.data.local.ExerciseEntity>,
    completedSessions: List<CompletedSessionSummary>,
    weightUnit: WeightUnit,
    dateRangeSelection: DateRangeSelection,
    onDateRangeChange: (DateRangeSelection) -> Unit,
    onOpenSessionDetail: (Long) -> Unit,
    onDeleteSession: (Long) -> Unit,
    onStartWorkout: () -> Unit
) {
    var sessionPendingDelete by remember { mutableStateOf<CompletedSessionSummary?>(null) }
    var sessionToShare by remember { mutableStateOf<SessionWithExercises?>(null) }

    val resolvedRange = remember(dateRangeSelection) {
        DateRangeCalculator.resolve(dateRangeSelection)
    }
    val filteredSessions = remember(completedSessions, resolvedRange) {
        completedSessions.filter { resolvedRange.contains(it.session.startedAt) }
    }
    val filteredSets = filteredSessions.sumOf { it.completedWorkSets }
    val filteredVolume = filteredSessions.sumOf { it.totalVolumeGrams }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "History",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
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
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DateRangeFilterBar(
                        selection = dateRangeSelection,
                        onSelectionChange = onDateRangeChange
                    )
                    MetricStrip(
                        metrics = listOf(
                            "Sessions" to filteredSessions.size.toString(),
                            "Sets" to filteredSets.toString(),
                            "Volume" to UnitConverter.formatVolumeWithUnit(filteredVolume, weightUnit)
                        ),
                        infoByLabel = mapOf(
                            "Volume" to (
                                "Period Volume" to
                                    "Total lifted weight (Weight × Reps) across completed work sets in the selected date range."
                            )
                        )
                    )
                }
            }

            if (completedSessions.isEmpty()) {
                item {
                    EmptyStatePanel(
                        title = "No workouts logged yet",
                        description = "Your completed workouts will appear here.",
                        actionLabel = "Start Workout",
                        onActionClick = onStartWorkout
                    )
                }
            } else if (filteredSessions.isEmpty()) {
                item {
                    EmptyStatePanel(
                        title = "No workouts in this date range",
                        description = "Choose a wider period to see earlier sessions."
                    )
                }
            } else {
                val groups = filteredSessions.sortedByDescending { it.session.startedAt }.groupBy {
                    Instant.ofEpochMilli(it.session.startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
                }
                groups.forEach { (date, sessions) ->
                    item(key = "date-$date") {
                        Text(
                            "•  " + date.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US)),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
                        )
                    }
                    items(sessions, key = { it.session.id }) { summary ->
                        var menuOpen by remember { mutableStateOf(false) }
                        androidx.compose.material3.Surface(shape = MaterialTheme.shapes.medium) {
                            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Column(Modifier.weight(1f)) {
                                        Text(summary.session.title, style = MaterialTheme.typography.titleLarge)
                                        Spacer(Modifier.height(8.dp))
                                        val duration = WorkoutMetricsCalculator.formatSessionDuration(summary.session.startedAt, summary.session.endedAt)
                                        Text("$duration · ${UnitConverter.formatVolumeWithUnit(summary.totalVolumeGrams, weightUnit)}",
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${summary.completedWorkSets} completed sets", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { sessionToShare = summary.detail }) { Icon(Icons.Default.Share, "Share ${summary.session.title}") }
                                    Box {
                                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Workout options") }
                                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                            DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = {
                                                menuOpen = false; sessionPendingDelete = summary
                                            })
                                        }
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpenSessionDetail(summary.session.id) }) {
                                    summary.detail.exercises.take(3).forEach { performed ->
                                        val ex = performed.sessionExercise
                                        val original = artworkExercises.firstOrNull { it.id == ex.exerciseId }
                                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            ExerciseVisualResolver.snapshot(ex.nameSnapshot, ex.muscleSnapshot, ex.equipmentSnapshot, original)?.let { art ->
                                                ExerciseArt(art, Modifier.size(64.dp))
                                                Spacer(Modifier.width(16.dp))
                                            }
                                            Column(Modifier.weight(1f)) {
                                                Text(ex.nameSnapshot, style = MaterialTheme.typography.bodyMedium)
                                                val count = WorkoutMetricsCalculator.countCompletedWorkSetsFromEntities(performed.sets)
                                                Text("$count completed sets", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Text(if (summary.detail.exercises.size > 3) "Show all ${summary.detail.exercises.size} exercises ›" else "Session details ›",
                                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (sessionToShare != null) {
        WorkoutShareSheet(
            sessionDetail = sessionToShare!!,
            weightUnit = weightUnit,
            onDismiss = { sessionToShare = null }
        )
    }

    if (sessionPendingDelete != null) {
        val target = sessionPendingDelete!!
        AlertDialog(
            onDismissRequest = { sessionPendingDelete = null },
            title = { Text("Delete workout record?") },
            text = {
                Text("Delete \"${target.session.title}\" from your history? Total volume and personal records will recalculate automatically.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        sessionPendingDelete = null
                        onDeleteSession(target.session.id)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionPendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailScreen(
    sessionDetail: SessionWithExercises?,
    weightUnit: WeightUnit,
    onBack: () -> Unit,
    onEditSession: (Long) -> Unit,
    onDeleteSession: (Long) -> Unit
) {
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var showShareSheet by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Workout",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (sessionDetail != null) {
                        IconButton(onClick = { showShareSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share workout",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { onEditSession(sessionDetail.session.id) }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit workout"
                            )
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete workout",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (sessionDetail == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                EmptyStatePanel(
                    title = "Workout not found",
                    description = "This workout record may have been deleted.",
                    actionLabel = "Back to History",
                    onActionClick = onBack
                )
            }
            return@Scaffold
        }

        val session = sessionDetail.session
        val dateStr = Instant.ofEpochMilli(session.startedAt)
            .atZone(ZoneId.systemDefault())
            .format(HistoryDateFormatter)
        val durationStr = WorkoutMetricsCalculator.formatSessionDuration(session.startedAt, session.endedAt)
        val allSets = sessionDetail.exercises.flatMap { it.sets }
        val completedWorkSets = WorkoutMetricsCalculator.countCompletedWorkSetsFromEntities(allSets)
        val totalVolumeGrams = WorkoutMetricsCalculator.calculateTotalVolumeGramsFromEntities(allSets)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(session.title, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                MetricStrip(
                    metrics = listOf(
                        "Duration" to durationStr,
                        "Sets" to completedWorkSets.toString(),
                        "Volume" to UnitConverter.formatVolumeWithUnit(totalVolumeGrams, weightUnit)
                    ),
                    infoByLabel = mapOf(
                        "Volume" to (
                            "Workout Volume" to
                                "Sum of Weight × Reps across completed work sets in this workout."
                        )
                    )
                )
            }

            item {
                val split = remember(sessionDetail) { MuscleSplitCalculator.fromSession(sessionDetail) }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Muscle Split", style = MaterialTheme.typography.titleLarge)
                    if (split.shares.isEmpty()) Text("No mapped completed work sets", style = MaterialTheme.typography.bodyMedium)
                    split.shares.forEach { share ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(share.muscle, style = MaterialTheme.typography.bodyMedium)
                            Text("${share.percent}%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        androidx.compose.material3.LinearProgressIndicator(progress = { share.percent / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp))
                    }
                    Text("Completed work sets by primary muscle · ${split.mappedSets} mapped" +
                        (if (split.unmappedSets > 0) " · ${split.unmappedSets} unmapped" else ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (sessionDetail.exercises.isEmpty()) {
                item {
                    EmptyStatePanel(
                        title = "No exercises recorded",
                        description = "This session was finished without any exercises."
                    )
                }
            } else {
                items(sessionDetail.exercises, key = { it.sessionExercise.id }) { exWithSets ->
                    val ex = exWithSets.sessionExercise
                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Text(ex.nameSnapshot, style = MaterialTheme.typography.titleLarge)
                        Text("${ex.muscleSnapshot} · ${ex.equipmentSnapshot}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth()) {
                            val timed=com.williamchandra.kettoryn.data.local.ExerciseKinds.isTimed(ex.exerciseTypeSnapshot)
                            val loadLabel=when(ex.exerciseTypeSnapshot) { "bodyweight_reps" -> "Added (${weightUnit.label})";"assisted_bodyweight" -> "Assist (${weightUnit.label})";else -> "Weight (${weightUnit.label})" }
                            listOf("Set", if(timed) "Duration" else loadLabel, if(ex.exerciseTypeSnapshot=="distance_duration") "Distance (m)" else if(timed) "Status" else "Reps").forEach { label ->
                                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                            }
                        }
                        var workNum = 0
                        exWithSets.sets.forEach { set ->
                            if (set.setType == SetType.WORK) workNum++
                            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                listOf(if (set.setType == SetType.WARMUP) "W" else workNum.toString(),
                                    if(com.williamchandra.kettoryn.data.local.ExerciseKinds.isTimed(ex.exerciseTypeSnapshot)) set.durationSeconds?.let { WorkoutMetricsCalculator.formatDurationSeconds(it.toLong()) } ?: "—" else set.weightGrams?.let { UnitConverter.formatGramsForInput(it, weightUnit) } ?: "—",
                                    if(ex.exerciseTypeSnapshot=="distance_duration") set.distanceMeters?.toString() ?: "—" else if(ex.exerciseTypeSnapshot=="duration") { if(set.isComplete) "Done" else "Skipped" } else set.reps?.toString() ?: "—").forEach { value ->
                                    Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                                        color = if (set.isComplete) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showShareSheet && sessionDetail != null) {
        WorkoutShareSheet(
            sessionDetail = sessionDetail,
            weightUnit = weightUnit,
            onDismiss = { showShareSheet = false }
        )
    }

    if (showDeleteConfirm && sessionDetail != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete workout?") },
            text = {
                Text("Delete \"${sessionDetail.session.title}\" from your workout history? This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteSession(sessionDetail.session.id)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
