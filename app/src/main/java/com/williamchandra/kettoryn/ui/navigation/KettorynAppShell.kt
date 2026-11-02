package com.williamchandra.kettoryn.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.williamchandra.kettoryn.ui.KettorynViewModel
import com.williamchandra.kettoryn.ui.components.ActiveWorkoutMiniBanner
import com.williamchandra.kettoryn.ui.exercises.ExerciseDetailScreen
import com.williamchandra.kettoryn.ui.exercises.ExercisesScreen
import com.williamchandra.kettoryn.ui.history.HistoryDetailScreen
import com.williamchandra.kettoryn.ui.history.HistoryScreen
import com.williamchandra.kettoryn.ui.home.HomeScreen
import com.williamchandra.kettoryn.ui.progress.ProgressScreen
import com.williamchandra.kettoryn.ui.settings.SettingsScreen
import com.williamchandra.kettoryn.ui.templates.TemplateEditorScreen
import com.williamchandra.kettoryn.ui.templates.TemplatesScreen
import com.williamchandra.kettoryn.ui.theme.KettorynTheme
import com.williamchandra.kettoryn.ui.workout.ActiveWorkoutScreen

enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    HOME("home", "Home", Icons.Default.Home),
    WORKOUTS("workouts", "Routines", Icons.AutoMirrored.Filled.ListAlt),
    EXERCISES("exercises", "Exercises", Icons.Default.FitnessCenter),
    HISTORY("history", "History", Icons.Default.History),
    PROGRESS("progress", "Progress", Icons.Default.Assessment)
}

object Routes {
    const val HOME = "home"
    const val WORKOUTS = "workouts"
    const val EXERCISES = "exercises"
    const val HISTORY = "history"
    const val PROGRESS = "progress?exerciseName={exerciseName}"
    const val PROGRESS_BASE = "progress"
    const val SETTINGS = "settings"
    const val ACTIVE_WORKOUT = "active_workout"
    const val TEMPLATE_EDITOR = "template_editor"
    const val TEMPLATE_PICKER = "template_exercise_picker"
    const val SESSION_EXERCISE_PICKER = "session_exercise_picker/{sessionId}"
    const val EXERCISE_DETAIL = "exercise_detail/{exerciseId}"
    const val HISTORY_DETAIL = "history_detail/{sessionId}"
    const val EDIT_COMPLETED_SESSION = "edit_completed_session/{sessionId}"

    fun exerciseDetail(id: Long) = "exercise_detail/$id"
    fun historyDetail(sessionId: Long) = "history_detail/$sessionId"
    fun editCompletedSession(sessionId: Long) = "edit_completed_session/$sessionId"
    fun sessionExercisePicker(sessionId: Long) = "session_exercise_picker/$sessionId"
    fun progressForExercise(name: String) = "progress?exerciseName=${Uri.encode(name)}"
}

@Composable
fun KettorynAppShell(
    viewModel: KettorynViewModel,
    navController: NavHostController = rememberNavController()
) {
    val preferences by viewModel.preferencesState.collectAsStateWithLifecycle()
    val exercises by viewModel.exercisesState.collectAsStateWithLifecycle()
    val templates by viewModel.templatesState.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSessionState.collectAsStateWithLifecycle()
    val completedSessions by viewModel.completedSessionsState.collectAsStateWithLifecycle()
    val previousSetsMap by viewModel.previousSetsMapState.collectAsStateWithLifecycle()

    val homeDateRange by viewModel.homeDateRange.collectAsStateWithLifecycle()
    val historyDateRange by viewModel.historyDateRange.collectAsStateWithLifecycle()
    val progressDateRange by viewModel.progressDateRange.collectAsStateWithLifecycle()

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedMuscle by viewModel.selectedMuscle.collectAsStateWithLifecycle()
    val selectedEquipment by viewModel.selectedEquipment.collectAsStateWithLifecycle()

    val editingTemplateId by viewModel.editingTemplateId.collectAsStateWithLifecycle()
    val templateDraftTitle by viewModel.templateDraftTitle.collectAsStateWithLifecycle()
    val templateDraftExercises by viewModel.templateDraftExercises.collectAsStateWithLifecycle()
    val templateEditorError by viewModel.templateEditorError.collectAsStateWithLifecycle()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Routes.HOME

    val isTopLevelRoute = currentRoute in setOf(
        Routes.HOME,
        Routes.WORKOUTS,
        Routes.EXERCISES,
        Routes.HISTORY,
        Routes.PROGRESS,
        Routes.PROGRESS_BASE
    )
    val showMiniBanner = activeSession != null && currentRoute != Routes.ACTIVE_WORKOUT &&
        !currentRoute.startsWith("session_exercise_picker")

    Scaffold(
        bottomBar = {
            Column {
                if (showMiniBanner && activeSession != null) {
                    val completedCount = activeSession!!.exercises.sumOf { ex ->
                        ex.sets.count { it.isComplete }
                    }
                    ActiveWorkoutMiniBanner(
                        title = activeSession!!.session.title,
                        startedAtMillis = activeSession!!.session.startedAt,
                        completedSetsCount = completedCount,
                        onDiscardClick = { viewModel.discardWorkout(activeSession!!.session.id) {} },
                        onResumeClick = {
                            navController.navigate(Routes.ACTIVE_WORKOUT) {
                                launchSingleTop = true
                            }
                        }
                    )
                }

                if (isTopLevelRoute) {
                    val palette = KettorynTheme.colors
                    HorizontalDivider(thickness = 1.dp, color = palette.divider)
                    val narrow = LocalConfiguration.current.screenWidthDp <= 360
                    val largeFont = LocalDensity.current.fontScale >= 1.25f
                    val focusNavigation = largeFont || narrow
                    NavigationBar(
                        modifier = Modifier.heightIn(min = if (largeFont) 112.dp else if (narrow) 100.dp else 88.dp),
                        containerColor = palette.surface,
                        tonalElevation = 0.dp
                    ) {
                        TopLevelDestination.entries.forEach { dest ->
                            val selected = when (dest) {
                                TopLevelDestination.PROGRESS ->
                                    currentRoute.startsWith(Routes.PROGRESS_BASE)
                                else -> currentRoute == dest.route
                            }
                            val navigate = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = false }
                                    launchSingleTop = true
                                    restoreState = false
                                }
                            }
                            if (focusNavigation) {
                                Column(
                                    modifier = (if (selected) Modifier.weight(1f) else Modifier.width(if (narrow) 48.dp else 56.dp))
                                        .height(80.dp)
                                        .selectable(selected = selected, role = Role.Tab, onClick = navigate),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier.background(
                                            androidx.compose.ui.graphics.Color.Transparent,
                                            RoundedCornerShape(24.dp)
                                        ).padding(horizontal = 12.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(dest.icon, contentDescription = if (selected) null else dest.label,
                                            tint = if (selected) palette.accent else palette.secondary)
                                    }
                                    if (selected) Text(
                                        dest.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.accent,
                                        maxLines = 1,
                                        softWrap = false,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            } else {
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = navigate,
                                    icon = { Icon(dest.icon, contentDescription = dest.label) },
                                    label = { Text(dest.label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = palette.accent,
                                        selectedTextColor = palette.accent,
                                        indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                        unselectedIconColor = palette.secondary,
                                        unselectedTextColor = palette.secondary
                                    )
                                )
                            }
                        }
                    }
                } else if (showMiniBanner) {
                    androidx.compose.foundation.layout.Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { outerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .fillMaxSize()
                .padding(outerPadding)
                .consumeWindowInsets(outerPadding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    activeSession = activeSession,
                    templates = templates,
                    completedSessions = completedSessions,
                    weightUnit = preferences.weightUnit,
                    dateRangeSelection = homeDateRange,
                    onDateRangeChange = viewModel::updateHomeDateRange,
                    onStartEmptyWorkout = {
                        viewModel.startEmptyWorkout {
                            navController.navigate(Routes.ACTIVE_WORKOUT) {
                                launchSingleTop = true
                            }
                        }
                    },
                    onDiscardActiveWorkout = { id -> viewModel.discardWorkout(id) {} },
                    onResumeActiveWorkout = {
                        navController.navigate(Routes.ACTIVE_WORKOUT) {
                            launchSingleTop = true
                        }
                    },
                    onStartTemplate = { templateId ->
                        viewModel.startWorkoutFromTemplate(templateId) {
                            navController.navigate(Routes.ACTIVE_WORKOUT) {
                                launchSingleTop = true
                            }
                        }
                    },
                    onOpenTemplatesTab = {
                        navController.navigate(Routes.WORKOUTS) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = false
                        }
                    },
                    onCreateTemplate = {
                        viewModel.startNewTemplateDraft()
                        navController.navigate(Routes.TEMPLATE_EDITOR)
                    },
                    onOpenSessionDetail = { sessionId ->
                        navController.navigate(Routes.historyDetail(sessionId))
                    },
                    onOpenSettings = {
                        navController.navigate(Routes.SETTINGS)
                    }
                )
            }

            composable(Routes.WORKOUTS) {
                TemplatesScreen(
                    templates = templates,
                    onCreateTemplate = {
                        viewModel.startNewTemplateDraft()
                        navController.navigate(Routes.TEMPLATE_EDITOR)
                    },
                    onEditTemplate = { templateId ->
                        viewModel.loadTemplateIntoDraft(templateId)
                        navController.navigate(Routes.TEMPLATE_EDITOR)
                    },
                    onDuplicateTemplate = { templateId ->
                        viewModel.duplicateTemplate(templateId)
                    },
                    onDeleteTemplate = { templateId ->
                        viewModel.deleteTemplate(templateId)
                    },
                    onStartTemplate = { templateId ->
                        viewModel.startWorkoutFromTemplate(templateId) {
                            navController.navigate(Routes.ACTIVE_WORKOUT) {
                                launchSingleTop = true
                            }
                        }
                    },
                    onStartEmptyWorkout = {
                        viewModel.startEmptyWorkout {
                            navController.navigate(Routes.ACTIVE_WORKOUT) {
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }

            composable(Routes.TEMPLATE_EDITOR) {
                TemplateEditorScreen(
                    isEditing = editingTemplateId != null,
                    title = templateDraftTitle,
                    exercises = templateDraftExercises,
                    weightUnit = preferences.weightUnit,
                    errorMessage = templateEditorError,
                    onTitleChange = viewModel::updateTemplateDraftTitle,
                    onMoveUp = viewModel::moveTemplateDraftExerciseUp,
                    onMoveDown = viewModel::moveTemplateDraftExerciseDown,
                    onRemoveExercise = viewModel::removeTemplateDraftExercise,
                    onUpdateExerciseTargets = viewModel::updateTemplateDraftExerciseTargets,
                    onOpenExercisePicker = {
                        navController.navigate(Routes.TEMPLATE_PICKER)
                    },
                    onSave = {
                        viewModel.saveTemplateDraft {
                            navController.popBackStack()
                        }
                    },
                    onBack = { navController.popBackStack() },
                    onClearError = viewModel::clearTemplateEditorError
                )
            }

            composable(Routes.TEMPLATE_PICKER) {
                ExercisesScreen(
                    exercises = exercises,
                    searchQuery = searchQuery,
                    selectedMuscle = selectedMuscle,
                    selectedEquipment = selectedEquipment,
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    onMuscleSelect = viewModel::updateSelectedMuscle,
                    onEquipmentSelect = viewModel::updateSelectedEquipment,
                    onExerciseClick = { picked ->
                        viewModel.addExerciseToTemplateDraft(picked)
                        navController.popBackStack()
                    },
                    onCreateCustomExercise = viewModel::createCustomExerciseSync,
                    pickerModeTitle = "Select Exercise for Routine",
                    onBackFromPicker = { navController.popBackStack() }
                )
            }

            composable(Routes.EXERCISES) {
                ExercisesScreen(
                    exercises = exercises,
                    searchQuery = searchQuery,
                    selectedMuscle = selectedMuscle,
                    selectedEquipment = selectedEquipment,
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    onMuscleSelect = viewModel::updateSelectedMuscle,
                    onEquipmentSelect = viewModel::updateSelectedEquipment,
                    onExerciseClick = { ex ->
                        navController.navigate(Routes.exerciseDetail(ex.id))
                    },
                    onCreateCustomExercise = viewModel::createCustomExerciseSync
                )
            }

            composable(
                route = Routes.EXERCISE_DETAIL,
                arguments = listOf(navArgument("exerciseId") { type = NavType.LongType })
            ) { entry ->
                val exId = entry.arguments?.getLong("exerciseId") ?: 0L
                val exercise by viewModel.observeExerciseById(exId).collectAsStateWithLifecycle(initialValue = null)
                ExerciseDetailScreen(
                    exercise = exercise,
                    onBack = { navController.popBackStack() },
                    onStartWorkoutWithExercise = { id ->
                        val active = activeSession
                        if (active != null) {
                            viewModel.addExerciseToSession(active.session.id, id) {
                                navController.navigate(Routes.ACTIVE_WORKOUT) {
                                    launchSingleTop = true
                                }
                            }
                        } else {
                            viewModel.startEmptyWorkout(
                                title = exercise?.name ?: "Workout",
                                initialExerciseId = id
                            ) {
                                navController.navigate(Routes.ACTIVE_WORKOUT) {
                                    launchSingleTop = true
                                }
                            }
                        }
                    },
                    onViewProgress = { _, name ->
                        navController.navigate(Routes.progressForExercise(name))
                    },
                    onUpdateCustomExercise = viewModel::updateCustomExerciseSync,
                    onCreateCustomFromBuiltIn = viewModel::createCustomExerciseSync,
                    onDeleteCustomExercise = { id ->
                        viewModel.deleteCustomExercise(id) {
                            navController.popBackStack()
                        }
                    }
                )
            }

            composable(Routes.ACTIVE_WORKOUT) {
                ActiveWorkoutScreen(
                    artworkExercises = exercises,
                    sessionDetail = activeSession,
                    previousSetsByExerciseKey = previousSetsMap,
                    weightUnit = preferences.weightUnit,
                    defaultRestSeconds = preferences.defaultRestSeconds,
                    restTimerEndMillis = preferences.restTimerEndEpochMillis,
                    exerciseRestOverrides = preferences.exerciseRestOverrides,
                    restTimerExerciseId = preferences.restTimerExerciseId,
                    pausedRestSeconds = preferences.pausedRestSeconds,
                    restTimerEndElapsedMillis = preferences.restTimerEndElapsedMillis,
                    onAdjustRestSeconds = viewModel::adjustRestSeconds,
                    onConfigureExerciseRest = viewModel::setExerciseRest,
                    onStartExerciseRest = viewModel::startExerciseRest,
                    onToggleRestPause = viewModel::toggleRestPause,
                    onRestoreSet = viewModel::restoreDeletedSet,
                    onNavigateBack = { navController.popBackStack() },
                    onUpdateTitle = viewModel::updateSessionTitle,
                    onOpenExercisePicker = {
                        val sId = activeSession?.session?.id ?: return@ActiveWorkoutScreen
                        navController.navigate(Routes.sessionExercisePicker(sId))
                    },
                    onRemoveExercise = viewModel::removeExerciseFromSession,
                    onAddSet = viewModel::addSetToExercise,
                    onUpdateSet = viewModel::updateSet,
                    onUpdateTimedSet = viewModel::updateTimedSet,
                    onDeleteSet = viewModel::deleteSet,
                    onStartOrAdjustRestTimer = viewModel::setRestTimerEndMillis,
                    onFinishWorkout = { sId ->
                        viewModel.finishWorkout(sId) {
                            navController.popBackStack()
                            navController.navigate(Routes.HISTORY) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                            }
                        }
                    },
                    onDiscardWorkout = { sId ->
                        viewModel.discardWorkout(sId) {
                            navController.popBackStack()
                        }
                    },
                    isEditingHistoricalSession = false
                )
            }

            composable(
                route = Routes.SESSION_EXERCISE_PICKER,
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
            ) { entry ->
                val sessionId = entry.arguments?.getLong("sessionId") ?: 0L
                ExercisesScreen(
                    exercises = exercises,
                    searchQuery = searchQuery,
                    selectedMuscle = selectedMuscle,
                    selectedEquipment = selectedEquipment,
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    onMuscleSelect = viewModel::updateSelectedMuscle,
                    onEquipmentSelect = viewModel::updateSelectedEquipment,
                    onExerciseClick = { picked ->
                        viewModel.addExerciseToSession(sessionId, picked.id) {
                            navController.popBackStack()
                        }
                    },
                    onCreateCustomExercise = viewModel::createCustomExerciseSync,
                    pickerModeTitle = "Add Exercise to Workout",
                    onBackFromPicker = { navController.popBackStack() }
                )
            }

            composable(Routes.HISTORY) {
                HistoryScreen(
                    artworkExercises = exercises,
                    completedSessions = completedSessions,
                    weightUnit = preferences.weightUnit,
                    dateRangeSelection = historyDateRange,
                    onDateRangeChange = viewModel::updateHistoryDateRange,
                    onOpenSessionDetail = { sId ->
                        navController.navigate(Routes.historyDetail(sId))
                    },
                    onDeleteSession = { sId ->
                        viewModel.deleteCompletedSession(sId)
                    },
                    onStartWorkout = {
                        viewModel.startEmptyWorkout {
                            navController.navigate(Routes.ACTIVE_WORKOUT) {
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }

            composable(
                route = Routes.HISTORY_DETAIL,
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
            ) { entry ->
                val sessionId = entry.arguments?.getLong("sessionId") ?: 0L
                val sessionDetail by viewModel.observeSessionById(sessionId)
                    .collectAsStateWithLifecycle(initialValue = null)

                HistoryDetailScreen(
                    sessionDetail = sessionDetail,
                    weightUnit = preferences.weightUnit,
                    onBack = { navController.popBackStack() },
                    onEditSession = { sId ->
                        navController.navigate(Routes.editCompletedSession(sId))
                    },
                    onDeleteSession = { sId ->
                        viewModel.deleteCompletedSession(sId) {
                            navController.popBackStack()
                        }
                    }
                )
            }

            composable(
                route = Routes.EDIT_COMPLETED_SESSION,
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
            ) { entry ->
                val sessionId = entry.arguments?.getLong("sessionId") ?: 0L
                val sessionDetail by viewModel.observeSessionById(sessionId)
                    .collectAsStateWithLifecycle(initialValue = null)

                ActiveWorkoutScreen(
                    artworkExercises = exercises,
                    sessionDetail = sessionDetail,
                    previousSetsByExerciseKey = emptyMap(),
                    weightUnit = preferences.weightUnit,
                    defaultRestSeconds = preferences.defaultRestSeconds,
                    restTimerEndMillis = null,
                    onRestoreSet = viewModel::restoreDeletedSet,
                    onNavigateBack = { navController.popBackStack() },
                    onUpdateTitle = viewModel::updateSessionTitle,
                    onOpenExercisePicker = {
                        navController.navigate(Routes.sessionExercisePicker(sessionId))
                    },
                    onRemoveExercise = viewModel::removeExerciseFromSession,
                    onAddSet = viewModel::addSetToExercise,
                    onUpdateSet = viewModel::updateSet,
                    onUpdateTimedSet = viewModel::updateTimedSet,
                    onDeleteSet = viewModel::deleteSet,
                    onStartOrAdjustRestTimer = {},
                    onFinishWorkout = { navController.popBackStack() },
                    onDiscardWorkout = { navController.popBackStack() },
                    isEditingHistoricalSession = true
                )
            }

            composable(
                route = Routes.PROGRESS,
                arguments = listOf(
                    navArgument("exerciseName") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { entry ->
                val initialExerciseName = entry.arguments?.getString("exerciseName")
                ProgressScreen(
                    completedSessions = completedSessions,
                    weightUnit = preferences.weightUnit,
                    trainingDateRange = progressDateRange,
                    onTrainingDateRangeChange = viewModel::updateProgressDateRange,
                    initialSelectedExerciseName = initialExerciseName
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    preferences = preferences,
                    onWeightUnitChange = viewModel::setWeightUnit,
                    onDefaultRestSecondsChange = viewModel::setDefaultRestSeconds,
                    onThemeModeChange = viewModel::setThemeMode,
                    onExportBackup = viewModel::exportBackup,
                    onInspectBackup = viewModel::inspectBackup,
                    onApplyStagedBackup = viewModel::applyStagedBackup,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
