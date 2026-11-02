package com.williamchandra.kettoryn.ui.visuals

import com.williamchandra.kettoryn.data.local.CanonicalExerciseCatalog
import com.williamchandra.kettoryn.R
import com.williamchandra.kettoryn.data.local.ExerciseEntity
import java.util.Locale

/** Import IDs are unstable. Exact poses require built-in identity AND matching metadata. */
object ExerciseVisualResolver {
    private data class Identity(val name: String, val muscle: String, val equipment: String)
    private val poses = mapOf(
        Identity("barbell bench press", "chest", "barbell") to R.drawable.wg_bench_press,
        Identity("flat dumbbell press", "chest", "dumbbell") to R.drawable.wg_dumbbell_bench_press,
        Identity("barbell back squat", "quadriceps", "barbell") to R.drawable.wg_squat,
        Identity("barbell front squat", "quadriceps", "barbell") to R.drawable.wg_front_squat,
        Identity("close-grip bench press", "triceps", "barbell") to R.drawable.wg_close_grip_bench_press
    )
    private fun String.canonical() = trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
    fun resolve(exercise: ExerciseEntity): Int? = frame(exercise, 0)

    fun frame(exercise: ExerciseEntity, index: Int): Int? {
        if (!exercise.isBuiltIn || index !in 0..2) return null
        exercise.sourceSlug?.let { slug ->
            val official = CanonicalExerciseCatalog.bySlug[slug] ?: return null
            if (exercise.name != official.name || exercise.primaryMuscle != official.primaryMuscle ||
                exercise.equipment != official.equipment || exercise.exerciseType != official.exerciseType) return null
            return official.frames[index]
        }
        val start = poses[Identity(exercise.normalizedName.canonical(), exercise.primaryMuscle.canonical(), exercise.equipment.canonical())]
        return if (index == 0) start else if (index == 2) alternate(start) else null
    }

    /** A snapshot alone cannot prove built-in origin; verify against its surviving exercise relation. */
    fun snapshot(name: String, muscle: String, equipment: String, source: ExerciseEntity?): Int? =
        if (source != null && source.isBuiltIn && source.name.canonical() == name.canonical() &&
            source.primaryMuscle.canonical() == muscle.canonical() && source.equipment.canonical() == equipment.canonical()) resolve(source)
        else null

    fun alternate(resource: Int?): Int? = when (resource) {
        R.drawable.wg_bench_press -> R.drawable.wg_bench_press_end
        R.drawable.wg_dumbbell_bench_press -> R.drawable.wg_dumbbell_bench_press_end
        R.drawable.wg_squat -> R.drawable.wg_squat_end
        R.drawable.wg_front_squat -> R.drawable.wg_front_squat_end
        R.drawable.wg_close_grip_bench_press -> R.drawable.wg_close_grip_bench_press_end
        else -> null
    }
}
