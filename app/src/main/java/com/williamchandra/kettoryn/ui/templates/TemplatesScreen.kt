package com.williamchandra.kettoryn.ui.templates

import com.williamchandra.kettoryn.ui.visuals.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.williamchandra.kettoryn.data.local.ExerciseEntity
import com.williamchandra.kettoryn.data.local.TemplateWithExercises
import com.williamchandra.kettoryn.data.preferences.WeightUnit
import com.williamchandra.kettoryn.data.repository.TemplateDraftExercise
import com.williamchandra.kettoryn.data.repository.TemplateDraftSet
import com.williamchandra.kettoryn.domain.UnitConverter
import com.williamchandra.kettoryn.ui.components.EmptyStatePanel
import com.williamchandra.kettoryn.ui.components.ErrorStateBanner
import com.williamchandra.kettoryn.ui.components.KettorynPanel
import com.williamchandra.kettoryn.ui.components.TagChip
import com.williamchandra.kettoryn.ui.theme.KettorynTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    templates: List<TemplateWithExercises>,
    onCreateTemplate: () -> Unit,
    onEditTemplate: (Long) -> Unit,
    onDuplicateTemplate: (Long) -> Unit,
    onDeleteTemplate: (Long) -> Unit,
    onStartTemplate: (Long) -> Unit,
    onStartEmptyWorkout: () -> Unit
) {
    var templatePendingDelete by remember { mutableStateOf<TemplateWithExercises?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Routines", style = MaterialTheme.typography.headlineMedium) },
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
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Text("Saved routines", style = MaterialTheme.typography.titleLarge) }
            item {
                OutlinedButton(
                    onClick = onStartEmptyWorkout,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Start empty workout")
                }
            }

            item {
                Button(onClick = onCreateTemplate, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = MaterialTheme.shapes.small) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("New routine")
                }
            }

            if (templates.isEmpty()) {
                item {
                    EmptyStatePanel(
                        title = "No saved routines",
                        description = "Save your exercises and targets for next time.",
                        actionLabel = "Create Routine",
                        onActionClick = onCreateTemplate
                    )
                }
            } else {
                items(templates, key = { it.template.id }) { tpl ->
                    var menuOpen by remember { mutableStateOf(false) }
                    KettorynPanel(contentPadding = PaddingValues(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(tpl.template.title, style = MaterialTheme.typography.titleMedium,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(4.dp))
                                Text("${tpl.exercises.size} exercises · ${tpl.exercises.sumOf { it.sets.size }} sets",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box {
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(Icons.Default.MoreVert, "Options for ${tpl.template.title}")
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(text = { Text("Edit") }, onClick = {
                                        menuOpen = false
                                        onEditTemplate(tpl.template.id)
                                    })
                                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = {
                                        menuOpen = false
                                        onDuplicateTemplate(tpl.template.id)
                                    })
                                    DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = {
                                        menuOpen = false
                                        templatePendingDelete = tpl
                                    })
                                }
                            }
                        }
                        Text(
                            tpl.exercises.joinToString(" · ") { it.templateExercise.exerciseNameSnapshot }.ifEmpty { "No exercises" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Button(onClick = { onStartTemplate(tpl.template.id) }, enabled = tpl.exercises.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = MaterialTheme.shapes.small) {
                            Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Start routine")
                        }
                    }
                }
            }
        }
    }

    if (templatePendingDelete != null) {
        val target = templatePendingDelete!!
        AlertDialog(
            onDismissRequest = { templatePendingDelete = null },
            title = { Text("Delete template?") },
            text = {
                Text("\"${target.template.title}\" will be deleted. Completed workout history logged from this template will remain untouched.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        templatePendingDelete = null
                        onDeleteTemplate(target.template.id)
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
                TextButton(onClick = { templatePendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

data class EditableTemplateExerciseUi(
    val uid: Long,
    val exerciseId: Long?,
    val name: String,
    val primaryMuscle: String,
    val equipment: String,
    val targetSetsText: String = "3",
    val targetRepsText: String = "8",
    val targetWeightText: String = "",
    val exerciseType: String = "weight_reps"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TemplateEditorScreen(
    isEditing: Boolean,
    title: String,
    exercises: List<EditableTemplateExerciseUi>,
    weightUnit: WeightUnit,
    errorMessage: String?,
    onTitleChange: (String) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onRemoveExercise: (Int) -> Unit,
    onUpdateExerciseTargets: (index: Int, setsText: String, repsText: String, weightText: String) -> Unit,
    onOpenExercisePicker: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onClearError: () -> Unit
) {
    val compactEditor = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp <= 360 || androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.25f
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (compactEditor) "Routine" else if (isEditing) "Edit routine" else "New routine") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = onSave,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .heightIn(min = 40.dp),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text("Save")
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
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (errorMessage != null) {
                item {
                    ErrorStateBanner(
                        message = errorMessage,
                        onDismissOrRetry = onClearError
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text("Routine name") },
                    placeholder = { Text("e.g., Upper Body Strength") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                FlowRow(
                    maxItemsInEachRow = if (compactEditor) 1 else 2,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Exercises (${exercises.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    OutlinedButton(
                        onClick = onOpenExercisePicker,
                        modifier = (if (compactEditor) Modifier.fillMaxWidth() else Modifier).heightIn(min = 48.dp),
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, KettorynTheme.colors.accent)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = KettorynTheme.colors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Exercise", color = KettorynTheme.colors.accent)
                    }
                }
            }

            if (exercises.isEmpty()) {
                item {
                    EmptyStatePanel(
                        title = "No exercises in this routine",
                        description = "Add at least one exercise and configure its target sets and reps.",
                        actionLabel = "Add Exercise",
                        onActionClick = onOpenExercisePicker
                    )
                }
            } else {
                itemsIndexed(exercises, key = { _, item -> item.uid }) { index, item ->
                    KettorynPanel {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "${index + 1}. ${item.name}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TagChip(text = item.primaryMuscle)
                                    TagChip(text = item.equipment)
                                }
                            }
                            Row {
                                IconButton(
                                    onClick = { onMoveUp(index) },
                                    enabled = index > 0
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = "Move ${item.name} up"
                                    )
                                }
                                IconButton(
                                    onClick = { onMoveDown(index) },
                                    enabled = index < exercises.lastIndex
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDownward,
                                        contentDescription = "Move ${item.name} down"
                                    )
                                }
                                IconButton(onClick = { onRemoveExercise(index) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove ${item.name}",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        FlowRow(
                            maxItemsInEachRow = if (compactEditor) 1 else 3,
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = item.targetSetsText,
                                onValueChange = {
                                    onUpdateExerciseTargets(index, it, item.targetRepsText, item.targetWeightText)
                                },
                                label = { Text("Sets", maxLines = 1) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = item.targetRepsText,
                                onValueChange = {
                                    onUpdateExerciseTargets(index, item.targetSetsText, it, item.targetWeightText)
                                },
                                label = { Text(if (com.williamchandra.kettoryn.data.local.ExerciseKinds.isTimed(item.exerciseType)) "Seconds" else "Reps", maxLines = 1) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            if (item.exerciseType != "duration") OutlinedTextField(
                                value = item.targetWeightText,
                                onValueChange = {
                                    onUpdateExerciseTargets(index, item.targetSetsText, item.targetRepsText, it)
                                },
                                label = { Text(if (item.exerciseType == "distance_duration") "Distance" else if (item.exerciseType == "assisted_bodyweight") "Assist" else "Load", maxLines = 1) },
                                suffix = { Text(if (item.exerciseType == "distance_duration") "m" else weightUnit.label) },
                                placeholder = { Text("—") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

