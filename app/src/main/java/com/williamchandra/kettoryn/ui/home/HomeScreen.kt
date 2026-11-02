package com.williamchandra.kettoryn.ui.home

import com.williamchandra.kettoryn.ui.visuals.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.williamchandra.kettoryn.data.local.SessionWithExercises
import com.williamchandra.kettoryn.data.local.TemplateWithExercises
import com.williamchandra.kettoryn.data.preferences.WeightUnit
import com.williamchandra.kettoryn.data.repository.CompletedSessionSummary
import com.williamchandra.kettoryn.domain.DateRangeCalculator
import com.williamchandra.kettoryn.domain.DateRangeSelection
import com.williamchandra.kettoryn.domain.UnitConverter
import com.williamchandra.kettoryn.domain.WorkoutMetricsCalculator
import com.williamchandra.kettoryn.ui.components.ApprovedKLogoMark
import com.williamchandra.kettoryn.ui.components.DateRangeFilterBar
import com.williamchandra.kettoryn.ui.components.EmptyStatePanel
import com.williamchandra.kettoryn.ui.components.KettorynPanel
import com.williamchandra.kettoryn.ui.components.KettorynSurfaceGroup
import com.williamchandra.kettoryn.ui.components.MetricStrip
import com.williamchandra.kettoryn.ui.components.SectionHeader
import com.williamchandra.kettoryn.ui.share.WorkoutShareSheet
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HomeDateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d • HH:mm", Locale.US)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    activeSession: SessionWithExercises?,
    templates: List<TemplateWithExercises>,
    completedSessions: List<CompletedSessionSummary>,
    weightUnit: WeightUnit,
    dateRangeSelection: DateRangeSelection,
    onDateRangeChange: (DateRangeSelection) -> Unit,
    onStartEmptyWorkout: () -> Unit,
    onResumeActiveWorkout: () -> Unit,
    onDiscardActiveWorkout: (Long) -> Unit,
    onStartTemplate: (Long) -> Unit,
    onOpenTemplatesTab: () -> Unit,
    onCreateTemplate: () -> Unit,
    onOpenSessionDetail: (Long) -> Unit,
    onOpenSettings: () -> Unit
) {
    val resolvedRange = remember(dateRangeSelection) {
        DateRangeCalculator.resolve(dateRangeSelection)
    }
    val filteredSessions = remember(completedSessions, resolvedRange) {
        completedSessions.filter { resolvedRange.contains(it.session.startedAt) }
    }
    val periodWorkouts = filteredSessions.size
    val periodSets = filteredSessions.sumOf { it.completedWorkSets }
    val periodVolumeGrams = filteredSessions.sumOf { it.totalVolumeGrams }
    val recentSessions = remember(completedSessions) { completedSessions.take(3) }

    var confirmDiscard by rememberSaveable { mutableStateOf<Long?>(null) }
    var sessionToShare by remember { mutableStateOf<SessionWithExercises?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ApprovedKLogoMark(modifier = Modifier.size(width = 28.dp, height = 19.dp))
                        Text(
                            text = "Kettoryn",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface
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
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                if (activeSession == null) {
                    Button(onClick = onStartEmptyWorkout, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Start workout")
                    }
                } else {
                    KettorynPanel(contentPadding = PaddingValues(20.dp)) {
                        Text("In progress", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text(activeSession.session.title, style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("${activeSession.exercises.size} exercises · ${WorkoutMetricsCalculator.countCompletedWorkSetsFromEntities(activeSession.exercises.flatMap { it.sets })} completed sets",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        var clock by remember(activeSession.session.id) { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
                        androidx.compose.runtime.LaunchedEffect(activeSession.session.id) {
                            while (true) { clock = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) }
                        }
                        Text(WorkoutMetricsCalculator.formatDurationSeconds(((clock - activeSession.session.startedAt).coerceAtLeast(0)) / 1000) + " elapsed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        FlowRow(maxItemsInEachRow = if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp <= 360 || androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.25f) 1 else 2,
                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = onResumeActiveWorkout, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Resume") }
                            OutlinedButton(onClick = { confirmDiscard = activeSession.session.id }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                                Text("Discard", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            // 2. Flexible Period Summary
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(title = "Your training")
                    DateRangeFilterBar(
                        selection = dateRangeSelection,
                        onSelectionChange = onDateRangeChange
                    )
                    MetricStrip(
                        metrics = listOf(
                            "Sessions" to periodWorkouts.toString(),
                            "Sets" to periodSets.toString(),
                            "Volume" to UnitConverter.formatVolumeWithUnit(periodVolumeGrams, weightUnit)
                        ),
                        infoByLabel = mapOf(
                            "Volume" to (
                                "Training Volume" to
                                    "Total weight lifted (Weight × Reps) across completed work sets in the selected date range. Warm-up and unchecked sets are excluded."
                            )
                        )
                    )
                }
            }

            // 3. Quick-Start Routines (Compact divider-separated rows inside a single surface)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(
                        title = "Routines",
                        actionLabel = if (templates.isNotEmpty()) "Manage" else "New Routine",
                        onActionClick = if (templates.isNotEmpty()) onOpenTemplatesTab else onCreateTemplate
                    )
                    if (templates.isEmpty()) {
                        EmptyStatePanel(
                            title = "No saved routines",
                            description = "Save a routine for your regular training days.",
                            actionLabel = "Create Routine",
                            onActionClick = onCreateTemplate
                        )
                    } else {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val stripState = rememberLazyListState()
                        val cardWidth = if (templates.size > 1) maxWidth - 32.dp else maxWidth
                        LazyRow(state = stripState, flingBehavior = rememberSnapFlingBehavior(stripState, SnapPosition.Start),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(templates.take(6), key = { it.template.id }) { tpl ->
                                androidx.compose.material3.Surface(
                                    onClick = { onStartTemplate(tpl.template.id) },
                                    modifier = Modifier.width(cardWidth),
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(tpl.template.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        }
                                        Text("${tpl.exercises.size} exercises · ${tpl.exercises.sumOf { it.sets.size }} sets", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Start routine", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }

            // 4. Recent Completed Workouts
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(title = "Recent Workouts")
                    if (recentSessions.isEmpty()) {
                        EmptyStatePanel(
                            title = "No workouts logged yet",
                            description = "Your completed sessions will appear here."
                        )
                    } else {
                        KettorynSurfaceGroup {
                            recentSessions.forEachIndexed { index, summary ->
                                val dateStr = Instant.ofEpochMilli(summary.session.startedAt)
                                    .atZone(ZoneId.systemDefault())
                                    .format(HomeDateFormatter)
                                val durationStr = WorkoutMetricsCalculator.formatSessionDuration(
                                    summary.session.startedAt,
                                    summary.session.endedAt
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(role = Role.Button) { onOpenSessionDetail(summary.session.id) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = summary.session.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "$dateStr • $durationStr",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${summary.exerciseCount} exercises • ${summary.completedWorkSets} sets • ${UnitConverter.formatVolumeWithUnit(summary.totalVolumeGrams, weightUnit)}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = { sessionToShare = summary.detail },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Share ${summary.session.title}",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                if (index < recentSessions.lastIndex) {
                                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDiscard != null && activeSession?.session?.id == confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = null },
            title = { Text("Discard this workout?") },
            text = { Text("Your unsaved sets will be lost.") },
            confirmButton = { TextButton(onClick = {
                confirmDiscard?.let(onDiscardActiveWorkout)
                confirmDiscard = null
            }) { Text("Discard", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = null }) { Text("Cancel") } }
        )
    }

    if (sessionToShare != null) {
        WorkoutShareSheet(
            sessionDetail = sessionToShare!!,
            weightUnit = weightUnit,
            onDismiss = { sessionToShare = null }
        )
    }
}
