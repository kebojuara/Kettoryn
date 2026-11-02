package com.williamchandra.kettoryn.domain

import com.williamchandra.kettoryn.data.local.SessionWithExercises
import java.util.Locale

data class MuscleShare(val muscle: String, val sets: Int, val percent: Int)
data class MuscleSplit(val shares: List<MuscleShare>, val unmappedSets: Int) {
    val mappedSets: Int get() = shares.sumOf { it.sets }
}

/** Session snapshot primary muscles only. Percentages describe sets, never muscle activation. */
object MuscleSplitCalculator {
    fun canonicalMuscle(raw: String): String? = when (raw.trim().lowercase(Locale.ROOT)) {
        "chest", "pectorals" -> "Chest"
        "back", "lats", "latissimus dorsi", "trapezius", "upper back", "lower back" -> "Back"
        "shoulders", "deltoids", "rear delts", "rear deltoids" -> "Shoulders"
        "biceps" -> "Biceps"
        "triceps" -> "Triceps"
        "quadriceps", "quads" -> "Quadriceps"
        "hamstrings" -> "Hamstrings"
        "calves" -> "Calves"
        "glutes", "gluteals" -> "Glutes"
        "core", "abs", "abdominals", "obliques" -> "Core"
        "forearms" -> "Forearms"
        "posterior chain" -> "Posterior Chain"
        "adductors" -> "Adductors"
        "abductors" -> "Abductors"
        else -> null
    }

    fun fromSession(session: SessionWithExercises): MuscleSplit = fromCounts(session.exercises.map {
        canonicalMuscle(it.sessionExercise.muscleSnapshot) to WorkoutMetricsCalculator.countCompletedWorkSetsFromEntities(it.sets)
    })

    fun fromCounts(counts: List<Pair<String?, Int>>): MuscleSplit {
        val grouped = counts.filter { it.first != null && it.second > 0 }.groupBy { it.first!! }
            .mapValues { (_, values) -> values.sumOf { it.second } }
        val total = grouped.values.sum()
        val unknown = counts.filter { it.first == null }.sumOf { it.second.coerceAtLeast(0) }
        if (total == 0) return MuscleSplit(emptyList(), unknown)
        val floors = grouped.mapValues { it.value * 100L / total }.mapValues { it.value.toInt() }.toMutableMap()
        val remainderOrder = grouped.keys.sortedWith(compareByDescending<String> { grouped.getValue(it) * 100L % total }.thenBy { it })
        repeat(100 - floors.values.sum()) { floors[remainderOrder[it]] = floors.getValue(remainderOrder[it]) + 1 }
        return MuscleSplit(grouped.map { MuscleShare(it.key, it.value, floors.getValue(it.key)) }
            .sortedWith(compareByDescending<MuscleShare> { it.sets }.thenBy { it.muscle }), unknown)
    }
}
