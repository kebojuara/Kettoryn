package com.williamchandra.kettoryn.domain

import com.williamchandra.kettoryn.data.local.*
import org.junit.Assert.*
import org.junit.Test

class MuscleSplitCalculatorTest {
    @Test fun percentagesUseLargestRemainderAndKeepUnknownOutsideDenominator() {
        val split = MuscleSplitCalculator.fromCounts(listOf("Chest" to 1, "Back" to 1, "Core" to 1, null to 7))
        assertEquals(100, split.shares.sumOf { it.percent })
        assertEquals(listOf(34, 33, 33), split.shares.sortedBy { it.muscle }.map { it.percent })
        assertEquals(7, split.unmappedSets)
        assertEquals(3, split.mappedSets)
    }
    @Test fun emptyAndSingleMuscleAreDefined() {
        assertTrue(MuscleSplitCalculator.fromCounts(emptyList()).shares.isEmpty())
        assertEquals(100, MuscleSplitCalculator.fromCounts(listOf("Chest" to 9)).shares.single().percent)
        assertNull(MuscleSplitCalculator.canonicalMuscle("Bambang"))
    }
    @Test fun usesRecordedMuscleAndOnlyQualifyingWorkSetsAcrossExercises() {
        val session = SessionWithExercises(WorkoutSessionEntity(id = 1, title = "Superset", startedAt = 1, updatedAt = 1), listOf(
            SessionExerciseWithSets(SessionExerciseEntity(id = 1, exerciseId = null, sessionId = 1, nameSnapshot = "Edited later", muscleSnapshot = "Chest", equipmentSnapshot = "Barbell", sortOrder = 0), listOf(
                LoggedSetEntity(id = 1, sessionExerciseId = 1, sortOrder = 0, reps = 8, weightGrams = 10000, isComplete = true),
                LoggedSetEntity(id = 2, sessionExerciseId = 1, sortOrder = 1, reps = 8, weightGrams = 10000, isComplete = true, setType = SetType.WARMUP),
                LoggedSetEntity(id = 3, sessionExerciseId = 1, sortOrder = 2, reps = null, weightGrams = 10000, isComplete = true))),
            SessionExerciseWithSets(SessionExerciseEntity(id = 2, exerciseId = null, sessionId = 1, nameSnapshot = "Row", muscleSnapshot = "Back", equipmentSnapshot = "Cable", sortOrder = 1), listOf(
                LoggedSetEntity(id = 4, sessionExerciseId = 2, sortOrder = 0, reps = 8, weightGrams = 10000, isComplete = true),
                LoggedSetEntity(id = 5, sessionExerciseId = 2, sortOrder = 1, reps = 8, weightGrams = 10000, isComplete = false)))))
        val split = MuscleSplitCalculator.fromSession(session)
        assertEquals(2, split.mappedSets)
        assertEquals(setOf("Chest", "Back"), split.shares.map { it.muscle }.toSet())
        assertTrue(split.shares.all { it.percent == 50 })
    }
}
