package com.williamchandra.kettoryn.data.local

object ExerciseKinds {
    val labels = linkedMapOf("weight_reps" to "Weight & reps", "bodyweight_reps" to "Bodyweight reps",
        "assisted_bodyweight" to "Assistance & reps", "duration" to "Duration", "distance_duration" to "Distance & duration")
    fun isTimed(kind: String) = kind == "duration" || kind == "distance_duration"
    fun label(kind: String) = labels[kind] ?: labels.getValue("weight_reps")
}
