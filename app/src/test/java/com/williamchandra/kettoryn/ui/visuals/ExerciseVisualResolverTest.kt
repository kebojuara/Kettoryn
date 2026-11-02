package com.williamchandra.kettoryn.ui.visuals

import com.williamchandra.kettoryn.R
import com.williamchandra.kettoryn.data.local.ExerciseEntity
import com.williamchandra.kettoryn.data.local.ExerciseSeedData
import org.junit.Assert.*
import org.junit.Test

class ExerciseVisualResolverTest {
    private val bench = ExerciseEntity(1, "Barbell Bench Press", "barbell bench press", "Chest", "Barbell", isBuiltIn = true)
    @Test fun `imported numeric IDs do not change a verified built-in identity`() {
        assertEquals(R.drawable.wg_bench_press, ExerciseVisualResolver.resolve(bench))
        assertEquals(ExerciseVisualResolver.resolve(bench), ExerciseVisualResolver.resolve(bench.copy(id = 9001)))
    }
    @Test fun `custom labels including a canonical name always remain text only`() {
        listOf("Bambang", "Barbell Bench Press", "Cable Chest Fly", "Front Squat").forEach { name ->
            assertNull(ExerciseVisualResolver.resolve(bench.copy(name = name, normalizedName = name.lowercase(), isBuiltIn = false)))
        }
    }
    @Test fun `all 44 seed exercises use only the five visually approved exact mappings`() {
        val approved = setOf("Barbell Bench Press", "Flat Dumbbell Press", "Barbell Back Squat", "Barbell Front Squat", "Close-Grip Bench Press")
        assertTrue(ExerciseSeedData.BUILT_IN_EXERCISES.size >= 15)
        ExerciseSeedData.BUILT_IN_EXERCISES.forEachIndexed { i, seed ->
            val ex = ExerciseEntity(i.toLong(), seed.name, seed.name.lowercase(), seed.primaryMuscle, seed.equipment, isBuiltIn = true)
            assertEquals(seed.name, seed.name in approved, ExerciseVisualResolver.resolve(ex) != null)
        }
    }
    @Test fun `variant equipment and muscle mismatches cannot borrow a pose`() {
        listOf("Dumbbell", "Cable", "Bodyweight", "Machine", "Kettlebell", "EZ Bar").forEach { gear -> assertNull(ExerciseVisualResolver.resolve(bench.copy(equipment = gear))) }
        listOf("Incline Barbell Bench Press", "Decline Barbell Bench Press", "Wide-Grip Bench Press").forEach { name -> assertNull(ExerciseVisualResolver.resolve(bench.copy(name = name, normalizedName = name.lowercase()))) }
        assertNull(ExerciseVisualResolver.resolve(bench.copy(primaryMuscle = "Back")))
    }
    @Test fun `unknown deleted custom or renamed snapshot never guesses imagery`() {
        assertNull(ExerciseVisualResolver.snapshot("Barbell Bench Press", "Chest", "Barbell", null))
        assertNull(ExerciseVisualResolver.snapshot("Custom press", "Chest", "Barbell", bench))
        assertNull(ExerciseVisualResolver.snapshot(bench.name, "Chest", "Barbell", bench.copy(isBuiltIn = false)))
        assertEquals(R.drawable.wg_bench_press, ExerciseVisualResolver.snapshot(bench.name, "Chest", "Barbell", bench))
    }
    @Test fun `only harmless whitespace and casing normalize`() {
        assertEquals(R.drawable.wg_bench_press, ExerciseVisualResolver.resolve(bench.copy(normalizedName = " BARBELL   BENCH PRESS ")))
        assertNull(ExerciseVisualResolver.resolve(bench.copy(normalizedName = "bench")))
    }
}
