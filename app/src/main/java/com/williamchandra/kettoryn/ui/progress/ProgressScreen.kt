package com.williamchandra.kettoryn.ui.progress

import com.williamchandra.kettoryn.ui.visuals.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.williamchandra.kettoryn.data.preferences.WeightUnit
import com.williamchandra.kettoryn.data.repository.CompletedSessionSummary
import com.williamchandra.kettoryn.domain.DateRangeCalculator
import com.williamchandra.kettoryn.domain.DateRangeSelection
import com.williamchandra.kettoryn.domain.PersonalRecord
import com.williamchandra.kettoryn.domain.SetSample
import com.williamchandra.kettoryn.domain.UnitConverter
import com.williamchandra.kettoryn.domain.WorkoutMetricsCalculator
import com.williamchandra.kettoryn.ui.components.DateRangeFilterBar
import com.williamchandra.kettoryn.ui.components.EmptyStatePanel
import com.williamchandra.kettoryn.ui.components.KettorynPanel
import com.williamchandra.kettoryn.ui.components.MetricStrip
import com.williamchandra.kettoryn.ui.components.SectionHeader
import com.williamchandra.kettoryn.ui.components.TagChip
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ShortDateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)

data class ExerciseAggregateStats(
    val key: String,
    val exerciseName: String,
    val primaryMuscle: String,
    val equipment: String,
    val personalRecord: PersonalRecord,
    val totalWorkSets: Int,
    val totalVolumeGrams: Long,
    val sessionPoints: List<SessionTrendPoint>
)

data class SessionTrendPoint(
    val startedAt: Long,
    val dateLabel: String,
    val bestWeightGrams: Long,
    val bestReps: Int,
    val sessionVolumeGrams: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    completedSessions: List<CompletedSessionSummary>,
    weightUnit: WeightUnit,
    trainingDateRange: DateRangeSelection,
    onTrainingDateRangeChange: (DateRangeSelection) -> Unit,
    initialSelectedExerciseName: String? = null
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Progress", style = MaterialTheme.typography.headlineMedium) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            TrainingProgressSubView(
                completedSessions = completedSessions,
                weightUnit = weightUnit,
                dateRangeSelection = trainingDateRange,
                onDateRangeChange = onTrainingDateRangeChange,
                initialSelectedExerciseName = initialSelectedExerciseName
            )
        }
    }
}

@Composable
private fun TrainingProgressSubView(
    completedSessions: List<CompletedSessionSummary>,
    weightUnit: WeightUnit,
    dateRangeSelection: DateRangeSelection,
    onDateRangeChange: (DateRangeSelection) -> Unit,
    initialSelectedExerciseName: String?
) {
    val resolvedRange = remember(dateRangeSelection) {
        DateRangeCalculator.resolve(dateRangeSelection)
    }
    val filteredSessions = remember(completedSessions, resolvedRange) {
        completedSessions.filter { resolvedRange.contains(it.session.startedAt) }
    }
    val totalSessions = filteredSessions.size
    val totalWorkSets = filteredSessions.sumOf { it.completedWorkSets }
    val totalVolumeGrams = filteredSessions.sumOf { it.totalVolumeGrams }

    val exerciseStats = remember(filteredSessions) {
        val grouped = linkedMapOf<String, MutableList<Pair<Long, com.williamchandra.kettoryn.data.local.SessionExerciseWithSets>>>()
        filteredSessions.sortedBy { it.session.startedAt }.forEach { summary ->
            summary.detail.exercises.forEach { se ->
                val key = se.sessionExercise.nameSnapshot.lowercase(Locale.ROOT)
                grouped.getOrPut(key) { mutableListOf() }.add(summary.session.startedAt to se)
            }
        }

        grouped.mapNotNull { (key, entries) ->
            val firstMeta = entries.last().second.sessionExercise
            val allSamples = mutableListOf<SetSample>()
            val points = mutableListOf<SessionTrendPoint>()

            entries.forEach { (startedAt, seWithSets) ->
                val samples = seWithSets.sets.map {
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
                val qualifying = samples.filter { WorkoutMetricsCalculator.isQualifyingWorkSet(it) }
                if (qualifying.isNotEmpty()) {
                    allSamples.addAll(qualifying)
                    val best = WorkoutMetricsCalculator.findPersonalRecord(qualifying)
                    val vol = WorkoutMetricsCalculator.calculateTotalVolumeGrams(qualifying)
                    if (best != null) {
                        val dateLabel = Instant.ofEpochMilli(startedAt)
                            .atZone(ZoneId.systemDefault())
                            .format(ShortDateFormatter)
                        points.add(
                            SessionTrendPoint(
                                startedAt = startedAt,
                                dateLabel = dateLabel,
                                bestWeightGrams = best.weightGrams,
                                bestReps = best.reps,
                                sessionVolumeGrams = vol
                            )
                        )
                    }
                }
            }

            val pr = WorkoutMetricsCalculator.findPersonalRecord(allSamples) ?: return@mapNotNull null
            ExerciseAggregateStats(
                key = key,
                exerciseName = firstMeta.nameSnapshot,
                primaryMuscle = firstMeta.muscleSnapshot,
                equipment = firstMeta.equipmentSnapshot,
                personalRecord = pr,
                totalWorkSets = WorkoutMetricsCalculator.countCompletedWorkSets(allSamples),
                totalVolumeGrams = WorkoutMetricsCalculator.calculateTotalVolumeGrams(allSamples),
                sessionPoints = points
            )
        }.sortedByDescending { it.totalVolumeGrams }
    }

    var selectedExerciseKey by rememberSaveable(initialSelectedExerciseName, exerciseStats.size) {
        val match = initialSelectedExerciseName?.lowercase(Locale.ROOT)?.let { target ->
            exerciseStats.firstOrNull { it.key == target }?.key
        }
        mutableStateOf(match ?: exerciseStats.firstOrNull()?.key)
    }

    val selectedStats = exerciseStats.firstOrNull { it.key == selectedExerciseKey } ?: exerciseStats.firstOrNull()

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
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
                        "Sessions" to totalSessions.toString(),
                        "Sets" to totalWorkSets.toString(),
                        "Volume" to UnitConverter.formatVolumeWithUnit(totalVolumeGrams, weightUnit)
                    ),
                    infoByLabel = mapOf(
                        "Volume" to (
                            "Training Volume" to
                                "Volume is calculated as Weight × Reps across completed work sets (excluding warm-up sets) in the selected date range."
                        )
                    )
                )
            }
        }

        item {
            TrainingVolumeChart(filteredSessions, weightUnit)
        }

        if (exerciseStats.isEmpty()) {
            item {
                EmptyStatePanel(
                    title = "No exercise records in range",
                    description = "Complete a workout or choose a wider period."
                )
            }
        } else {

            item {
                SectionHeader(
                    title = "Personal Records",
                    infoTitle = "Personal Records (PR)",
                    infoExplanation = "Heaviest completed work set in this period. Equal weights are ranked by reps."
                )
            }

            items(exerciseStats, key = { it.key }) { stat ->
                val isSelected = selectedStats?.key == stat.key
                KettorynPanel(
                    onClick = { selectedExerciseKey = stat.key; scope.launch { listState.animateScrollToItem(3 + exerciseStats.size) } },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                ) {
                    val record = "${UnitConverter.formatGramsForInput(stat.personalRecord.weightGrams, weightUnit)} ${weightUnit.label} × ${stat.personalRecord.reps}"
                    androidx.compose.foundation.layout.BoxWithConstraints {
                        val stacked = maxWidth < 300.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f
                        if (stacked) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stat.exerciseName, style = MaterialTheme.typography.titleMedium)
                            Text("${stat.primaryMuscle} · ${stat.totalWorkSets} sets", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(record, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stat.exerciseName, style = MaterialTheme.typography.titleMedium)
                                Text("${stat.primaryMuscle} · ${stat.totalWorkSets} sets", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(record, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, softWrap = false)
                        }
                    }
                }
            }
            if (selectedStats != null) {
                item {
                    ExerciseTrendPanel(
                        stats = selectedStats,
                        weightUnit = weightUnit
                    )
                }
            }

        }
    }
}

@Composable
private fun ExerciseTrendPanel(
    stats: ExerciseAggregateStats,
    weightUnit: WeightUnit
) {
    val points = stats.sessionPoints.takeLast(8)
    fun best(weight: Long, reps: Int) = "${UnitConverter.formatGramsWithUnit(weight, weightUnit)} × $reps"
    KettorynPanel {
        Text(stats.exerciseName, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text("Best  ${best(stats.personalRecord.weightGrams, stats.personalRecord.reps)}", style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary)
        points.lastOrNull()?.let { Text("Latest  ${best(it.bestWeightGrams, it.bestReps)}", style = MaterialTheme.typography.bodyMedium) }
        val dates=points.groupBy { Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).toLocalDate() }
            .map { (_,values) -> values.maxBy { it.bestWeightGrams } }.sortedBy { it.startedAt }
        if(dates.size>=2) {
            Spacer(Modifier.height(16.dp))
            Text("Best work set (${weightUnit.label})",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            val accent=MaterialTheme.colorScheme.primary
            val grid=MaterialTheme.colorScheme.outline
            Canvas(Modifier.fillMaxWidth().height(96.dp).semantics { contentDescription=dates.joinToString("; ") { "${it.dateLabel}: ${best(it.bestWeightGrams,it.bestReps)}" } }) {
                val maximum=dates.maxOf { it.bestWeightGrams }.coerceAtLeast(1)
                val start=dates.first().startedAt;val span=(dates.last().startedAt-start).coerceAtLeast(1)
                val coords=dates.map { point -> Offset(6.dp.toPx()+((point.startedAt-start).toDouble()/span*(size.width-12.dp.toPx())).toFloat(),
                    size.height-6.dp.toPx()-(point.bestWeightGrams.toDouble()/maximum*(size.height-12.dp.toPx())).toFloat()) }
                drawLine(grid,Offset(0f,size.height-6.dp.toPx()),Offset(size.width,size.height-6.dp.toPx()),1.dp.toPx())
                coords.zipWithNext().forEach { (from,to) -> drawLine(accent,from,to,3.dp.toPx()) }
                coords.forEach { drawCircle(accent,4.dp.toPx(),it) }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(dates.first().dateLabel,style=MaterialTheme.typography.bodySmall)
                Text(dates.last().dateLabel,style=MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("Date", Modifier.weight(0.8f), style = MaterialTheme.typography.bodySmall)
            Text("Best", Modifier.weight(1.3f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            Text("Volume", Modifier.weight(1.1f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
        points.reversed().forEach { point ->
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(point.dateLabel, Modifier.weight(0.8f), style = MaterialTheme.typography.bodySmall)
                Text(best(point.bestWeightGrams, point.bestReps), Modifier.weight(1.3f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                Text(UnitConverter.formatVolumeWithUnit(point.sessionVolumeGrams, weightUnit), Modifier.weight(1.1f),
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
        }
    }
}


@Composable
private fun TrainingVolumeChart(sessions: List<CompletedSessionSummary>, unit: WeightUnit) {
    val points = sessions.sortedBy { it.session.startedAt }
    val accent = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val total = points.sumOf { it.totalVolumeGrams }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Training volume", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(UnitConverter.formatVolumeWithUnit(total, unit), style = MaterialTheme.typography.headlineLarge)
        Text("Completed work-set volume", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        KettorynPanel {
            if (points.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Your training starts here.\nComplete a workout to plot volume.", style = MaterialTheme.typography.bodyMedium)
                }
            } else if (points.map { Instant.ofEpochMilli(it.session.startedAt).atZone(ZoneId.systemDefault()).toLocalDate() }.distinct().size < 2) {
                Text("${points.size} ${if (points.size == 1) "session" else "sessions"} · ${Instant.ofEpochMilli(points.last().session.startedAt).atZone(ZoneId.systemDefault()).format(ShortDateFormatter)}", style = MaterialTheme.typography.bodyMedium)
            } else {
                val description = points.joinToString("; ") { summary ->
                    "${Instant.ofEpochMilli(summary.session.startedAt).atZone(ZoneId.systemDefault()).format(ShortDateFormatter)}: ${UnitConverter.formatVolumeWithUnit(summary.totalVolumeGrams, unit)}"
                }
                Canvas(Modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = description }) {
                    val max = points.maxOf { it.totalVolumeGrams }.coerceAtLeast(1).toDouble()
                    repeat(4) { row ->
                        val y = size.height * row / 3f
                        drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    }
                    val start = points.first().session.startedAt
                    val span = (points.last().session.startedAt - start).coerceAtLeast(1)
                    val coords = points.map {
                        Offset(if (points.size == 1) size.width / 2 else 8.dp.toPx() + ((it.session.startedAt - start).toDouble() / span * (size.width - 16.dp.toPx())).toFloat(),
                            size.height - (it.totalVolumeGrams / max * (size.height - 12.dp.toPx())).toFloat())
                    }
                    coords.zipWithNext().forEach { (a, b) -> drawLine(accent, a, b, 3.dp.toPx()) }
                    coords.forEach { drawCircle(accent, 4.dp.toPx(), it) }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(Instant.ofEpochMilli(points.first().session.startedAt).atZone(ZoneId.systemDefault()).format(ShortDateFormatter), style = MaterialTheme.typography.bodySmall)
                    Text(if (points.size == 1) "1 session" else Instant.ofEpochMilli(points.last().session.startedAt).atZone(ZoneId.systemDefault()).format(ShortDateFormatter), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
