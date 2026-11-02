package com.williamchandra.kettoryn.ui.exercises

import com.williamchandra.kettoryn.ui.visuals.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.material.icons.filled.Check
import kotlinx.coroutines.launch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.williamchandra.kettoryn.data.local.CanonicalExerciseCatalog
import com.williamchandra.kettoryn.data.local.ExerciseEntity
import com.williamchandra.kettoryn.data.local.ExerciseSeedData
import com.williamchandra.kettoryn.ui.components.EmptyStatePanel
import com.williamchandra.kettoryn.ui.components.ErrorStateBanner
import com.williamchandra.kettoryn.ui.components.KettorynPanel
import com.williamchandra.kettoryn.ui.components.TagChip
import com.williamchandra.kettoryn.ui.theme.KettorynTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExercisesScreen(
    exercises: List<ExerciseEntity>,
    searchQuery: String,
    selectedMuscle: String?,
    selectedEquipment: String?,
    onSearchQueryChange: (String) -> Unit,
    onMuscleSelect: (String?) -> Unit,
    onEquipmentSelect: (String?) -> Unit,
    onExerciseClick: (ExerciseEntity) -> Unit,
    onCreateCustomExercise: (name: String, muscle: String, equipment: String, instructions: String, exerciseType: String, imageKey: String?) -> Result<Long>,
    pickerModeTitle: String? = null,
    onBackFromPicker: (() -> Unit)? = null
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var dialogInitialName by rememberSaveable { mutableStateOf("") }

    val filtered = remember(exercises, searchQuery, selectedMuscle, selectedEquipment) {
        val cleanQuery = searchQuery.trim()
        exercises.filter { ex ->
            val matchesQuery = cleanQuery.isEmpty() ||
                ex.name.contains(cleanQuery, ignoreCase = true) ||
                ex.primaryMuscle.contains(cleanQuery, ignoreCase = true) ||
                ex.equipment.contains(cleanQuery, ignoreCase = true) || "${ex.equipment} ${ex.name}".contains(cleanQuery, ignoreCase = true)
            val matchesMuscle = selectedMuscle == null || ex.primaryMuscle.equals(selectedMuscle, ignoreCase = true)
            val matchesEquip = selectedEquipment == null || ex.equipment.equals(selectedEquipment, ignoreCase = true)
            matchesQuery && matchesMuscle && matchesEquip
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = pickerModeTitle ?: "Exercises",
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    if (onBackFromPicker != null) {
                        IconButton(onClick = onBackFromPicker) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = { ContextualCreateFAB("Create custom exercise") {
            dialogInitialName = searchQuery.trim()
            showCreateDialog = true
        } },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search input directly accessible at top
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                placeholder = { Text("Search exercises") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                },
                label = { Text("Search") },
                singleLine = true,
                shape = MaterialTheme.shapes.small
            )

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExerciseFilterMenu(selectedMuscle ?: "All muscles", (CanonicalExerciseCatalog.muscles + ExerciseSeedData.MUSCLE_GROUPS).distinct().sorted(), onSelect = onMuscleSelect)
                ExerciseFilterMenu(selectedEquipment ?: "All equipment", (CanonicalExerciseCatalog.equipment + ExerciseSeedData.EQUIPMENT_OPTIONS).distinct().sorted(), onSelect = onEquipmentSelect)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (filtered.isEmpty()) {
                    item {
                        EmptyStatePanel(
                            title = "No matching exercises",
                            description = if (searchQuery.isNotBlank() || selectedMuscle != null || selectedEquipment != null) {
                                "Try another search or change your filters."
                            } else {
                                "No exercises available."
                            },
                            actionLabel = "Create Custom Exercise",
                            onActionClick = {
                                dialogInitialName = searchQuery.trim()
                                showCreateDialog = true
                            }
                        )
                    }
                } else {
                    items(filtered, key = { it.id }) { exercise ->
                        androidx.compose.material3.Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable(role = Role.Button) { onExerciseClick(exercise) }
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ExerciseVisualResolver.resolve(exercise)?.let { art ->
                                    ExerciseArt(art, Modifier.size(width = 72.dp, height = 72.dp))
                                    Spacer(Modifier.width(16.dp))
                                }
                                if (!exercise.isBuiltIn) {
                                    CustomExerciseImage(exercise.customImageKey, Modifier.size(64.dp), exercise.name)
                                    Spacer(Modifier.width(12.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.height(4.dp))
                                    Text("${exercise.primaryMuscle} · ${exercise.equipment}",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (!exercise.isBuiltIn) Text("Custom", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                if (pickerModeTitle != null) {
                                    Icon(Icons.Default.Add, "Add ${exercise.name}", tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 8.dp).size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CustomExerciseDialog(
            title = "Create Custom Exercise",
            initialName = dialogInitialName,
            initialMuscle = selectedMuscle ?: ExerciseSeedData.MUSCLE_GROUPS.first(),
            initialEquipment = selectedEquipment ?: ExerciseSeedData.EQUIPMENT_OPTIONS.first(),
            initialInstructions = "",
            onDismiss = { showCreateDialog = false },
            onSave = { name, muscle, equip, instr, kind, photo ->
                onCreateCustomExercise(name, muscle, equip, instr, kind, photo).map { }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseDetailScreen(
    exercise: ExerciseEntity?,
    onBack: () -> Unit,
    onStartWorkoutWithExercise: (Long) -> Unit,
    onViewProgress: (Long, String) -> Unit,
    onUpdateCustomExercise: (id: Long, name: String, muscle: String, equipment: String, instructions: String, exerciseType: String, imageKey: String?) -> Result<Unit>,
    onCreateCustomFromBuiltIn: (name: String, muscle: String, equipment: String, instructions: String, exerciseType: String, imageKey: String?) -> Result<Long>,
    onDeleteCustomExercise: (Long) -> Unit
) {
    var showEditDialog by rememberSaveable { mutableStateOf(false) }
    var showCloneCustomDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Exercise") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (exercise != null && !exercise.isBuiltIn) {
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit custom exercise"
                            )
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete custom exercise",
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
        if (exercise == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(20.dp)
            ) {
                EmptyStatePanel(
                    title = "Exercise not found",
                    description = "This exercise may have been removed.",
                    actionLabel = "Go Back",
                    onActionClick = onBack
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(exercise.name, style = MaterialTheme.typography.headlineMedium)
            var frameIndex by rememberSaveable(exercise.id) { mutableStateOf(0) }
            if (!exercise.isBuiltIn) CustomExerciseImage(exercise.customImageKey, Modifier.fillMaxWidth().aspectRatio(1f), exercise.name)
            else ExerciseArt(ExerciseVisualResolver.frame(exercise, frameIndex),
                Modifier.fillMaxWidth().aspectRatio(1f), "${exercise.name} ${listOf("start", "middle", "end")[frameIndex]} position")
            if (exercise.sourceSlug != null) FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                listOf("Start", "Middle", "End").forEachIndexed { index, label ->
                    TextButton(onClick = { frameIndex = index }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(label, color = if (frameIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration = if (frameIndex == index) androidx.compose.ui.text.style.TextDecoration.Underline else androidx.compose.ui.text.style.TextDecoration.None)
                    }
                }
            }
            Text("${exercise.primaryMuscle} · ${exercise.equipment}", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(com.williamchandra.kettoryn.data.local.ExerciseKinds.label(exercise.exerciseType), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!exercise.isBuiltIn && exercise.instructions.isNotBlank()) Text(exercise.instructions, style = MaterialTheme.typography.bodyLarge)
            if (exercise.isBuiltIn) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                OutlinedButton(onClick = { showCloneCustomDialog = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Create custom variation")
                }
            }
        }
    }

    if (showEditDialog && exercise != null) {
        CustomExerciseDialog(
            title = "Edit Custom Exercise",
            initialName = exercise.name,
            initialMuscle = exercise.primaryMuscle,
            initialEquipment = exercise.equipment,
            initialInstructions = exercise.instructions,
            initialType = exercise.exerciseType,
            initialImageKey = if (exercise.isBuiltIn) null else exercise.customImageKey,
            onDismiss = { showEditDialog = false },
            onSave = { name, muscle, equip, instr, kind, photo ->
                onUpdateCustomExercise(exercise.id, name, muscle, equip, instr, kind, photo)
            }
        )
    }

    if (showCloneCustomDialog && exercise != null) {
        CustomExerciseDialog(
            title = "Create Custom Variation",
            initialName = "${exercise.name} (Custom)",
            initialMuscle = exercise.primaryMuscle,
            initialEquipment = exercise.equipment,
            initialInstructions = exercise.instructions,
            initialType = exercise.exerciseType,
            initialImageKey = if (exercise.isBuiltIn) null else exercise.customImageKey,
            onDismiss = { showCloneCustomDialog = false },
            onSave = { name, muscle, equip, instr, kind, photo ->
                onCreateCustomFromBuiltIn(name, muscle, equip, instr, kind, photo).map { }
            }
        )
    }

    if (showDeleteConfirm && exercise != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete custom exercise?") },
            text = {
                Text("\"${exercise.name}\" will be removed from your exercise library. Any past workouts that logged this exercise will keep their saved history.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteCustomExercise(exercise.id)
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CustomExerciseDialog(
    title: String,
    initialName: String,
    initialMuscle: String,
    initialEquipment: String,
    initialInstructions: String,
    onDismiss: () -> Unit,
    initialType: String = "weight_reps",
    initialImageKey: String? = null,
    onSave: (name: String, muscle: String, equipment: String, instructions: String, exerciseType: String, imageKey: String?) -> Result<Unit>
) {
    var selectedType by rememberSaveable { mutableStateOf(initialType) }
    var photoKey by rememberSaveable { mutableStateOf(initialImageKey) }
    var preparingPhoto by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val media = remember(context) { com.williamchandra.kettoryn.data.media.CustomExerciseMediaStore(context) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var photoError by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            preparingPhoto = true
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { media.import(uri) } }
            result.onSuccess { photoKey = it; photoError = null }.onFailure { photoError = it.message ?: "Unable to open image." }
            preparingPhoto = false
        }
    }
    var name by rememberSaveable { mutableStateOf(initialName) }
    var selectedMuscle by rememberSaveable { mutableStateOf(initialMuscle) }
    var selectedEquipment by rememberSaveable { mutableStateOf(initialEquipment) }
    var instructions by rememberSaveable { mutableStateOf(initialInstructions) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (errorMessage != null) {
                    ErrorStateBanner(
                        message = errorMessage!!,
                        onDismissOrRetry = { errorMessage = null }
                    )
                }

                CustomExerciseImage(photoKey, Modifier.size(128.dp), "Square crop preview")
                Text(if (photoKey == null) "Add image (optional)" else "Square crop preview", style = MaterialTheme.typography.bodyMedium)
                if (preparingPhoto) Text("Preparing image…", style = MaterialTheme.typography.bodySmall)
                photoError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(enabled = !preparingPhoto, onClick = { picker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text(if (photoKey == null) "Choose image" else "Change image")
                    }
                    if (photoKey != null) TextButton(onClick = { photoKey = null }) { Text("Remove image") }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text("Exercise Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                ExerciseFilterMenu("Muscle: $selectedMuscle", (CanonicalExerciseCatalog.muscles + ExerciseSeedData.MUSCLE_GROUPS).distinct().sorted(), allowAll = false) { it?.let { selectedMuscle = it } }
                ExerciseFilterMenu("Equipment: $selectedEquipment", (CanonicalExerciseCatalog.equipment + ExerciseSeedData.EQUIPMENT_OPTIONS).distinct().sorted(), allowAll = false) { it?.let { selectedEquipment = it } }
                ExerciseFilterMenu("Type: ${com.williamchandra.kettoryn.data.local.ExerciseKinds.label(selectedType)}", com.williamchandra.kettoryn.data.local.ExerciseKinds.labels.values.toList(), allowAll = false) { label ->
                    selectedType = com.williamchandra.kettoryn.data.local.ExerciseKinds.labels.entries.firstOrNull { it.value == label }?.key ?: selectedType
                }

                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text("Instructions (optional)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !preparingPhoto,
                onClick = {
                    val result = onSave(name, selectedMuscle, selectedEquipment, instructions, selectedType, photoKey)
                    result.onSuccess {
                        onDismiss()
                    }.onFailure { err ->
                        errorMessage = err.message ?: "Failed to save exercise."
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseFilterMenu(label: String, options: List<String>, allowAll: Boolean = true, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var bottom by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var anchorWidth by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val view = androidx.compose.ui.platform.LocalView.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val width = with(density) { maxOf(anchorWidth.toDp(), 220.dp) }.coerceAtMost(androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp - 32.dp)
    val available = with(density) { (view.rootView.height - bottom).toDp() } - 24.dp
    val choices: @Composable () -> Unit = {
        if (allowAll) DropdownMenuItem(text = { Text("All", style = MaterialTheme.typography.bodyLarge) }, onClick = { expanded = false; onSelect(null) })
        options.forEach { option ->
            val selected = label == option || label.endsWith(": $option")
            DropdownMenuItem(text = { Text(option, style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                trailingIcon = if (selected) ({ Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.primary) }) else null,
                onClick = { expanded = false; onSelect(option) })
        }
    }
    Box(Modifier.onGloballyPositioned { coordinates -> bottom = coordinates.boundsInWindow().bottom.toInt(); anchorWidth = coordinates.size.width }) {
        TextButton(onClick = { keyboard?.hide(); expanded = true }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(label, maxLines = 2, style = MaterialTheme.typography.bodyLarge)
            Icon(Icons.Default.ExpandMore, null, Modifier.size(18.dp))
        }
        if (expanded && available >= 120.dp) {
            val provider = remember(density) { object : androidx.compose.ui.window.PopupPositionProvider {
                override fun calculatePosition(anchorBounds: androidx.compose.ui.unit.IntRect, windowSize: androidx.compose.ui.unit.IntSize,
                    layoutDirection: androidx.compose.ui.unit.LayoutDirection, popupContentSize: androidx.compose.ui.unit.IntSize): androidx.compose.ui.unit.IntOffset {
                    val margin = with(density) { 16.dp.roundToPx() }
                    return androidx.compose.ui.unit.IntOffset(anchorBounds.left.coerceIn(margin, maxOf(margin, windowSize.width - popupContentSize.width - margin)),
                        anchorBounds.bottom + with(density) { 4.dp.roundToPx() })
                }
            } }
            androidx.compose.ui.window.Popup(popupPositionProvider = provider, onDismissRequest = { expanded = false },
                properties = androidx.compose.ui.window.PopupProperties(focusable = true)) {
                androidx.compose.material3.Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 8.dp) {
                    Column(Modifier.width(width).heightIn(max = minOf(280.dp, available)).verticalScroll(rememberScrollState())) { choices() }
                }
            }
        } else if (expanded) {
            androidx.compose.material3.ModalBottomSheet(onDismissRequest = { expanded = false }) {
                Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(20.dp))
                Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) { choices() }
            }
        }
    }
}
