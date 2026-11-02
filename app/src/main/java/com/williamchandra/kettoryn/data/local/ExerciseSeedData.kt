package com.williamchandra.kettoryn.data.local

object ExerciseSeedData {
    data class SeedItem(
        val name: String,
        val primaryMuscle: String,
        val equipment: String,
        val instructions: String
    )

    val MUSCLE_GROUPS = listOf(
        "Chest",
        "Back",
        "Quadriceps",
        "Hamstrings",
        "Glutes",
        "Shoulders",
        "Biceps",
        "Triceps",
        "Core",
        "Calves",
        "Full Body"
    )

    val EQUIPMENT_OPTIONS = listOf(
        "Barbell",
        "Dumbbell",
        "Cable",
        "Machine",
        "Bodyweight",
        "Kettlebell",
        "EZ Bar"
    )

    val BUILT_IN_EXERCISES: List<SeedItem> = listOf(
        // Chest
        SeedItem(
            name = "Barbell Bench Press",
            primaryMuscle = "Chest",
            equipment = "Barbell",
            instructions = "Lie flat on bench, grip bar slightly wider than shoulders, lower under control to mid-chest, and press to lockout."
        ),
        SeedItem(
            name = "Incline Dumbbell Press",
            primaryMuscle = "Chest",
            equipment = "Dumbbell",
            instructions = "Set bench to 30 degrees. Lower dumbbells to upper chest with elbows at 45 degrees, then press up."
        ),
        SeedItem(
            name = "Flat Dumbbell Press",
            primaryMuscle = "Chest",
            equipment = "Dumbbell",
            instructions = "Lie on flat bench and press dumbbells from chest level to full extension above shoulders."
        ),
        SeedItem(
            name = "Cable Chest Fly",
            primaryMuscle = "Chest",
            equipment = "Cable",
            instructions = "Step forward with slight elbow bend, bring handles together in an arc in front of chest."
        ),
        SeedItem(
            name = "Push-Up",
            primaryMuscle = "Chest",
            equipment = "Bodyweight",
            instructions = "Brace core in plank position, lower chest to floor with elbows tucked at 45 degrees, and push back up."
        ),
        SeedItem(
            name = "Chest Dip",
            primaryMuscle = "Chest",
            equipment = "Bodyweight",
            instructions = "Lean torso slightly forward on parallel bars, lower until shoulders are below elbows, and press up."
        ),

        // Back
        SeedItem(
            name = "Conventional Deadlift",
            primaryMuscle = "Back",
            equipment = "Barbell",
            instructions = "Stand with mid-foot under bar, hinge hips, brace lats and core, and drive the floor away keeping bar close to shins."
        ),
        SeedItem(
            name = "Barbell Bent-Over Row",
            primaryMuscle = "Back",
            equipment = "Barbell",
            instructions = "Hinge torso to 45 degrees, pull bar toward lower ribs while squeezing shoulder blades together."
        ),
        SeedItem(
            name = "Pull-Up",
            primaryMuscle = "Back",
            equipment = "Bodyweight",
            instructions = "Grip bar slightly wider than shoulders from a dead hang and pull chest toward the bar."
        ),
        SeedItem(
            name = "Chin-Up",
            primaryMuscle = "Back",
            equipment = "Bodyweight",
            instructions = "Use a shoulder-width supinated (underhand) grip and pull until chin clears the bar."
        ),
        SeedItem(
            name = "Lat Pulldown",
            primaryMuscle = "Back",
            equipment = "Cable",
            instructions = "Sit tall with thighs secured, pull bar down to upper chest while driving elbows toward hips."
        ),
        SeedItem(
            name = "Seated Cable Row",
            primaryMuscle = "Back",
            equipment = "Cable",
            instructions = "Sit with neutral spine, pull handle to abdomen, pause briefly, and control the return."
        ),
        SeedItem(
            name = "One-Arm Dumbbell Row",
            primaryMuscle = "Back",
            equipment = "Dumbbell",
            instructions = "Brace one hand on bench, row dumbbell toward hip crease while keeping torso level."
        ),
        SeedItem(
            name = "Chest-Supported Machine Row",
            primaryMuscle = "Back",
            equipment = "Machine",
            instructions = "Rest chest against pad and pull handles back focusing on mid-back retraction."
        ),

        // Quadriceps
        SeedItem(
            name = "Barbell Back Squat",
            primaryMuscle = "Quadriceps",
            equipment = "Barbell",
            instructions = "Rest bar across upper traps, brace core, squat down until hips crease below knees, and drive back up."
        ),
        SeedItem(
            name = "Barbell Front Squat",
            primaryMuscle = "Quadriceps",
            equipment = "Barbell",
            instructions = "Rack bar across front deltoids with high elbows, keep torso upright, and squat to full depth."
        ),
        SeedItem(
            name = "Leg Press",
            primaryMuscle = "Quadriceps",
            equipment = "Machine",
            instructions = "Place feet shoulder-width on sled, lower until knees reach 90 degrees without lower back rounding, and press."
        ),
        SeedItem(
            name = "Bulgarian Split Squat",
            primaryMuscle = "Quadriceps",
            equipment = "Dumbbell",
            instructions = "Elevate rear foot on bench, lower back knee toward floor while keeping front foot planted, then drive up."
        ),
        SeedItem(
            name = "Goblet Squat",
            primaryMuscle = "Quadriceps",
            equipment = "Kettlebell",
            instructions = "Hold kettlebell or dumbbell at chest height, sit between hips with upright posture, and stand tall."
        ),
        SeedItem(
            name = "Leg Extension",
            primaryMuscle = "Quadriceps",
            equipment = "Machine",
            instructions = "Adjust pad above ankles, extend knees fully with a brief pause at top, and lower slowly."
        ),

        // Hamstrings & Glutes
        SeedItem(
            name = "Romanian Deadlift",
            primaryMuscle = "Hamstrings",
            equipment = "Barbell",
            instructions = "Keep slight knee bend, push hips straight back until hamstring stretch is reached, then squeeze glutes to stand."
        ),
        SeedItem(
            name = "Dumbbell Romanian Deadlift",
            primaryMuscle = "Hamstrings",
            equipment = "Dumbbell",
            instructions = "Track dumbbells close to thighs while hinging hips back with a flat spine."
        ),
        SeedItem(
            name = "Lying Leg Curl",
            primaryMuscle = "Hamstrings",
            equipment = "Machine",
            instructions = "Keep hips pressed into bench, curl pad toward glutes, and control the eccentric phase."
        ),
        SeedItem(
            name = "Seated Leg Curl",
            primaryMuscle = "Hamstrings",
            equipment = "Machine",
            instructions = "Lock lap pad snugly over thighs and curl lower leg pad back and under seat."
        ),
        SeedItem(
            name = "Barbell Hip Thrust",
            primaryMuscle = "Glutes",
            equipment = "Barbell",
            instructions = "Brace upper back on bench, drive through heels to full hip extension with chin tucked."
        ),
        SeedItem(
            name = "Kettlebell Swing",
            primaryMuscle = "Glutes",
            equipment = "Kettlebell",
            instructions = "Hike bell behind hips and snap hips forward explosively to float bell to chest height."
        ),

        // Shoulders
        SeedItem(
            name = "Overhead Barbell Press",
            primaryMuscle = "Shoulders",
            equipment = "Barbell",
            instructions = "Brace glutes and abs, press bar vertically from collarbones to overhead lockout."
        ),
        SeedItem(
            name = "Seated Dumbbell Shoulder Press",
            primaryMuscle = "Shoulders",
            equipment = "Dumbbell",
            instructions = "Sit against back support and press dumbbells overhead without overarching lower back."
        ),
        SeedItem(
            name = "Dumbbell Lateral Raise",
            primaryMuscle = "Shoulders",
            equipment = "Dumbbell",
            instructions = "Raise dumbbells out to sides in scapular plane up to shoulder height with soft elbows."
        ),
        SeedItem(
            name = "Cable Face Pull",
            primaryMuscle = "Shoulders",
            equipment = "Cable",
            instructions = "Pull rope attachment toward forehead while externally rotating shoulders so hands finish by ears."
        ),
        SeedItem(
            name = "Rear Delt Dumbbell Fly",
            primaryMuscle = "Shoulders",
            equipment = "Dumbbell",
            instructions = "Hinge at hips and sweep dumbbells outward to target posterior deltoids."
        ),

        // Biceps
        SeedItem(
            name = "Barbell Bicep Curl",
            primaryMuscle = "Biceps",
            equipment = "Barbell",
            instructions = "Keep elbows pinned by sides and curl bar upward without swinging torso."
        ),
        SeedItem(
            name = "EZ Bar Preacher Curl",
            primaryMuscle = "Biceps",
            equipment = "EZ Bar",
            instructions = "Rest upper arms flat on preacher pad, lower bar to near full extension, and curl up."
        ),
        SeedItem(
            name = "Incline Dumbbell Curl",
            primaryMuscle = "Biceps",
            equipment = "Dumbbell",
            instructions = "Sit on incline bench letting arms hang behind torso for a deep bicep stretch, then curl."
        ),
        SeedItem(
            name = "Hammer Curl",
            primaryMuscle = "Biceps",
            equipment = "Dumbbell",
            instructions = "Curl dumbbells with a neutral (palms facing each other) grip to target brachialis and forearms."
        ),

        // Triceps
        SeedItem(
            name = "Cable Tricep Pushdown",
            primaryMuscle = "Triceps",
            equipment = "Cable",
            instructions = "Keep elbows locked at sides and extend rope or bar attachment down until arms are straight."
        ),
        SeedItem(
            name = "EZ Bar Skullcrusher",
            primaryMuscle = "Triceps",
            equipment = "EZ Bar",
            instructions = "Lie on bench, hinge at elbows to lower bar toward forehead, then extend back to lockout."
        ),
        SeedItem(
            name = "Overhead Cable Tricep Extension",
            primaryMuscle = "Triceps",
            equipment = "Cable",
            instructions = "Face away from cable column and extend rope overhead keeping upper arms stationary."
        ),
        SeedItem(
            name = "Close-Grip Bench Press",
            primaryMuscle = "Triceps",
            equipment = "Barbell",
            instructions = "Grip bar shoulder-width apart, keep elbows tucked close to ribs, and press from lower chest."
        ),

        // Core & Calves
        SeedItem(
            name = "Hanging Leg Raise",
            primaryMuscle = "Core",
            equipment = "Bodyweight",
            instructions = "Hang from bar and curl pelvis upward bringing knees or straight legs toward chest."
        ),
        SeedItem(
            name = "Cable Crunch",
            primaryMuscle = "Core",
            equipment = "Cable",
            instructions = "Kneel below high pulley holding rope by head and flex spine to bring ribs toward hips."
        ),
        SeedItem(
            name = "Plank",
            primaryMuscle = "Core",
            equipment = "Bodyweight",
            instructions = "Support body on forearms and toes, maintaining a rigid line from head to heels."
        ),
        SeedItem(
            name = "Standing Calf Raise",
            primaryMuscle = "Calves",
            equipment = "Machine",
            instructions = "Lower heels below platform for a full stretch, then drive onto balls of feet and pause at top."
        ),
        SeedItem(
            name = "Seated Calf Raise",
            primaryMuscle = "Calves",
            equipment = "Machine",
            instructions = "Place balls of feet on platform with pad over knees, lower into full stretch, and press up."
        )
    )
}

