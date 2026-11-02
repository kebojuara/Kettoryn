package com.williamchandra.kettoryn.ui.share

import com.williamchandra.kettoryn.data.local.SessionWithExercises
import com.williamchandra.kettoryn.data.preferences.WeightUnit
import com.williamchandra.kettoryn.domain.UnitConverter
import com.williamchandra.kettoryn.domain.WorkoutMetricsCalculator

enum class ShareTemplate(val label: String) {
    SUMMARY("Summary"), EXERCISES("Exercises")
}

data class WorkoutShareOptions(
    val template: ShareTemplate = ShareTemplate.SUMMARY,
    val darkCardStyle: Boolean = true,
    val showWorkoutTitle: Boolean = true,
    val showWeightsAndVolume: Boolean = true,
    val showExerciseList: Boolean = true,
    val showMuscleMap: Boolean = true
)

data class ShareExercise(val name: String, val completedSets: Int)

data class WorkoutShareContent(
    val title: String,
    val duration: String,
    val volume: String,
    val completedSets: Int,
    val exercises: List<ShareExercise>,
    val remainingExercises: Int
) {
    companion object {
        fun from(detail: SessionWithExercises, unit: WeightUnit, options: WorkoutShareOptions): WorkoutShareContent {
            val allSets = detail.exercises.flatMap { it.sets }
            val completedExercises = detail.exercises.mapNotNull { exercise ->
                val count = WorkoutMetricsCalculator.countCompletedWorkSetsFromEntities(exercise.sets)
                if (count == 0) null else ShareExercise(exercise.sessionExercise.nameSnapshot, count)
            }
            return WorkoutShareContent(
                title = if (options.showWorkoutTitle) detail.session.title.ifBlank { "Workout" } else "Workout",
                duration = WorkoutMetricsCalculator.formatSessionDuration(detail.session.startedAt, detail.session.endedAt),
                volume = UnitConverter.formatVolumeWithUnit(
                    WorkoutMetricsCalculator.calculateTotalVolumeGramsFromEntities(allSets), unit
                ),
                completedSets = WorkoutMetricsCalculator.countCompletedWorkSetsFromEntities(allSets),
                exercises = completedExercises.take(5),
                remainingExercises = (completedExercises.size - 5).coerceAtLeast(0)
            )
        }
    }
}
