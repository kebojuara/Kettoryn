package com.williamchandra.kettoryn.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDateRangePickerState
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.williamchandra.kettoryn.domain.DateRangeCalculator
import com.williamchandra.kettoryn.domain.DateRangePreset
import com.williamchandra.kettoryn.domain.DateRangeSelection
import com.williamchandra.kettoryn.domain.WorkoutMetricsCalculator
import com.williamchandra.kettoryn.ui.theme.KettorynTheme
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// Vector derivation of the weighted K in the approved redesign-kit brand asset.
@Composable
fun ApprovedKLogoMark(
    modifier: Modifier = Modifier.size(width = 28.dp, height = 19.dp),
    structuralColor: Color = KettorynTheme.colors.foreground,
    limeColor: Color = KettorynTheme.colors.brandLime
) {
    Canvas(
        modifier = modifier.semantics {
            contentDescription = "Kettoryn logo"
        }
    ) {
        val sx = size.width / 738f
        val sy = size.height / 506f
        val scale = minOf(sx, sy)
        val ox = (size.width - 738f * scale) / 2f
        val oy = (size.height - 506f * scale) / 2f

        fun tx(x: Float): Float = ox + x * scale
        fun ty(y: Float): Float = oy + y * scale
        fun tr(r: Float): Float = r * scale

        fun leftRoundedBarPath(x1: Float, y1: Float, x2: Float, y2: Float, r: Float): Path {
            val X1 = tx(x1)
            val Y1 = ty(y1)
            val X2 = tx(x2)
            val Y2 = ty(y2)
            val R = tr(r)
            return Path().apply {
                moveTo(X1 + R, Y1)
                lineTo(X2, Y1)
                lineTo(X2, Y2)
                lineTo(X1 + R, Y2)
                quadraticTo(X1, Y2, X1, Y2 - R)
                lineTo(X1, Y1 + R)
                quadraticTo(X1, Y1, X1 + R, Y1)
                close()
            }
        }

        // 3 ascending left-rounded weight bars
        drawPath(leftRoundedBarPath(0f, 219f, 37f, 328f, 14f), color = structuralColor)
        drawPath(leftRoundedBarPath(51f, 154f, 97f, 394f, 18f), color = structuralColor)
        drawPath(leftRoundedBarPath(115f, 85f, 192f, 458f, 24f), color = structuralColor)

        // Upper spine
        val upperSpine = Path().apply {
            moveTo(tx(221f), ty(0f))
            lineTo(tx(344f), ty(0f))
            quadraticTo(tx(372f), ty(0f), tx(372f), ty(28f))
            lineTo(tx(372f), ty(139f))
            lineTo(tx(221f), ty(290f))
            close()
        }
        drawPath(upperSpine, color = structuralColor)

        // Lower spine
        val lowerSpine = Path().apply {
            moveTo(tx(221f), ty(306f))
            lineTo(tx(372f), ty(457f))
            lineTo(tx(372f), ty(477f))
            quadraticTo(tx(372f), ty(505f), tx(344f), ty(505f))
            lineTo(tx(221f), ty(505f))
            close()
        }
        drawPath(lowerSpine, color = structuralColor)

        // Lower diagonal leg
        val lowerLeg = Path().apply {
            moveTo(tx(271f), ty(289f))
            lineTo(tx(458f), ty(289f))
            lineTo(tx(705f), ty(505f))
            lineTo(tx(474f), ty(505f))
            lineTo(tx(259f), ty(300f))
            close()
        }
        drawPath(lowerLeg, color = structuralColor)

        // Upper diagonal lime arm
        val limeArm = Path().apply {
            moveTo(tx(277f), ty(284f))
            lineTo(tx(551f), ty(10f))
            lineTo(tx(737f), ty(10f))
            lineTo(tx(461f), ty(284f))
            close()
        }
        drawPath(limeArm, color = limeColor)
    }
}

@Composable
fun ContextualInfoButton(
    title: String,
    explanation: String,
    modifier: Modifier = Modifier
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(
                role = Role.Button,
                onClickLabel = "Learn about $title",
                onClick = { showDialog = true }
            )
            .semantics {
                contentDescription = "Info about $title"
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Got it")
                }
            }
        )
    }
}

/**
 * Flexible Date Range Filter Bar (F5):
 * Supports `This Week`, `Last Week`, `This Month`, `3 Months`, `1 Year`, and `Custom` (Material 3 DateRangePicker).
 * Always displays the exact resolved local start–end date span.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DateRangeFilterBar(
    selection: DateRangeSelection,
    onSelectionChange: (DateRangeSelection) -> Unit,
    modifier: Modifier = Modifier,
    nowMillis: Long = System.currentTimeMillis()
) {
    val resolved = remember(selection, nowMillis) {
        DateRangeCalculator.resolve(selection, nowMillis)
    }
    var showCustomPicker by rememberSaveable { mutableStateOf(false) }

    var expanded by remember { mutableStateOf(false) }
    val reflowRange = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp <= 360 || androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.25f
    FlowRow(
        maxItemsInEachRow = if (reflowRange) 1 else 2,
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            TextButton(onClick = { expanded = true }) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(selection.preset.label, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DateRangePreset.entries.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text(preset.label) },
                        onClick = {
                            expanded = false
                            if (preset == DateRangePreset.CUSTOM) showCustomPicker = true
                            else onSelectionChange(DateRangeSelection(preset = preset))
                        }
                    )
                }
            }
        }
        Text(
            resolved.formattedLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = if (reflowRange) androidx.compose.ui.text.style.TextAlign.Start else androidx.compose.ui.text.style.TextAlign.End,
            modifier = if (reflowRange) Modifier.fillMaxWidth() else Modifier.weight(1f)
        )
    }

    if (showCustomPicker) {
        CalendarJumpDialog(
            initialStart = resolved.startDate,
            initialEnd = resolved.endDate,
            onDismiss = { showCustomPicker = false },
            onApply = { start, end ->
                onSelectionChange(DateRangeSelection(DateRangePreset.CUSTOM, start.toEpochDay(), end.toEpochDay()))
                showCustomPicker = false
            }
        )
    }
}

@Composable
fun KettorynPanel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.medium
    val baseModifier = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(containerColor)
        .let {
            if (onClick != null) {
                it.clickable(role = Role.Button, onClick = onClick)
            } else {
                it
            }
        }
        .padding(contentPadding)

    Column(
        modifier = baseModifier,
        content = content
    )
}

/**
 * Clean Surface Group without internal padding so list items can sit flush separated by 1dp dividers.
 */
@Composable
fun KettorynSurfaceGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface),
        content = content
    )
}

data class MetricColumnItem(
    val label: String,
    val value: String,
    val infoTitle: String? = null,
    val infoExplanation: String? = null
)

@Composable
fun MetricStrip(
    metrics: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    infoByLabel: Map<String, Pair<String, String>> = emptyMap()
) {
    val measurer = rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val valueStyle = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum")
    val labelStyle = MaterialTheme.typography.bodySmall
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val weights = metrics.map { if (it.first == "Volume") 1.65f else if (it.first == "Sets") 0.75f else 0.85f }
        val available = with(density) { maxWidth.toPx() }
        val reflow = metrics.indices.any { i ->
            val cellWidth = available * weights[i] / weights.sum() - with(density) { 20.dp.toPx() }
            measurer.measure(metrics[i].second, valueStyle, softWrap = false).size.width > cellWidth ||
                measurer.measure(metrics[i].first, labelStyle, softWrap = false).size.width +
                    (if (infoByLabel[metrics[i].first] != null) with(density) { 18.dp.toPx() } else 0f) > cellWidth
        }
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
            if (reflow && metrics.size == 3) {
                Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        metrics.take(2).forEach { (label, value) ->
                            MetricCell(label, value, infoByLabel[label], Modifier.weight(1f))
                        }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline)
                    val (label, value) = metrics.last()
                    MetricCell(label, value, infoByLabel[label], Modifier.fillMaxWidth())
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    metrics.forEachIndexed { index, (label, value) ->
                        MetricCell(label, value, infoByLabel[label], Modifier.weight(weights[index]))
                        if (index < metrics.lastIndex) VerticalDivider(Modifier.height(44.dp), color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCell(label: String, value: String, info: Pair<String, String>?, modifier: Modifier) {
    var infoOpen by remember { mutableStateOf(false) }
    Column(
        modifier.heightIn(min = 64.dp).clickable(enabled = info != null, role = Role.Button,
            onClickLabel = "About $label") { infoOpen = true }.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            if (info != null) Icon(Icons.Outlined.Info, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value.replace(' ', '\u00a0'), style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"), maxLines = 1,
            softWrap = false, textAlign = TextAlign.Center,
            modifier = Modifier.horizontalScroll(rememberScrollState()))
    }
    if (infoOpen && info != null) AlertDialog(onDismissRequest = { infoOpen = false },
        title = { Text(info.first) }, text = { Text(info.second) },
        confirmButton = { TextButton(onClick = { infoOpen = false }) { Text("Got it") } })
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    infoTitle: String? = null,
    infoExplanation: String? = null,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (infoTitle != null && infoExplanation != null) {
                ContextualInfoButton(
                    title = infoTitle,
                    explanation = infoExplanation
                )
            }
        }
        if (actionLabel != null && onActionClick != null) {
            TextButton(
                onClick = onActionClick,
                modifier = Modifier.heightIn(min = 34.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun EmptyStatePanel(
    title: String,
    description: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    KettorynPanel(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (actionLabel != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onActionClick,
                modifier = Modifier.heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
fun LoadingStateView(
    message: String = "Loading workout data...",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ErrorStateBanner(
    message: String,
    onDismissOrRetry: (() -> Unit)? = null,
    actionText: String = "Dismiss",
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            if (onDismissOrRetry != null) {
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onDismissOrRetry,
                    modifier = Modifier.heightIn(min = 36.dp),
                    shape = MaterialTheme.shapes.extraSmall,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = actionText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
fun TagChip(
    text: String,
    modifier: Modifier = Modifier,
    isAccent: Boolean = false
) {
    val bgColor = if (isAccent) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        KettorynTheme.colors.mutedSurface
    }
    val textColor = if (isAccent) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(bgColor)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            maxLines = 1
        )
    }
}

@Composable
fun ActiveWorkoutMiniBanner(
    title: String,
    startedAtMillis: Long,
    completedSetsCount: Int,
    onResumeClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDiscardClick: () -> Unit = {}
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(startedAtMillis) { while (true) { nowMillis = System.currentTimeMillis(); delay(1000) } }
    val elapsed = WorkoutMetricsCalculator.formatDurationSeconds((nowMillis - startedAtMillis).coerceAtLeast(0) / 1000)
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).heightIn(min = 64.dp).clickable(role = Role.Button, onClick = onResumeClick)
                    .semantics { contentDescription = "Resume workout" }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.FitnessCenter, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Resume · $elapsed", style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = { confirmDiscard = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.DeleteOutline, "Discard workout", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false },
        title = { Text("Discard ongoing workout?") },
        text = { Text("Active sets will be lost. Your completed workouts will stay saved.") },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; onDiscardClick() }) { Text("Discard", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Cancel") } })
}
