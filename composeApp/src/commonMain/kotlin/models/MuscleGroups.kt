package models

/** Common muscle groups used when adding exercises and for the muscle-group analysis. */
val muscleGroups = listOf(
    "Chest", "Front Delts", "Mid Delts", "Rear Delts", "Traps", "Lats", "Upper Back", "Lower Back",
    "Biceps", "Triceps", "Forearms", "Quads", "Hamstrings", "Glutes", "Calves", "Abs / Core", "Cardio"
)

/** Starter exercises that are barbell lifts (plate calculator + warm-ups). */
val starterBarbellExercises = setOf(
    "Bench Press", "Overhead Press", "Barbell Row", "Deadlift", "Squat", "Romanian Deadlift", "Barbell Curl", "Hip Thrust",
)

/** A starter library so a fresh install is usable straight away. */
val starterExercises = listOf(
    "Bench Press" to "Chest",
    "Incline Dumbbell Press" to "Chest",
    "Chest Fly" to "Chest",
    "Overhead Press" to "Front Delts",
    "Lateral Raise" to "Mid Delts",
    "Face Pull" to "Rear Delts",
    "Pull-up" to "Lats",
    "Lat Pulldown" to "Lats",
    "Barbell Row" to "Upper Back",
    "Seated Cable Row" to "Upper Back",
    "Deadlift" to "Lower Back",
    "Barbell Curl" to "Biceps",
    "Hammer Curl" to "Biceps",
    "Triceps Pushdown" to "Triceps",
    "Skull Crusher" to "Triceps",
    "Squat" to "Quads",
    "Leg Press" to "Quads",
    "Leg Extension" to "Quads",
    "Romanian Deadlift" to "Hamstrings",
    "Leg Curl" to "Hamstrings",
    "Hip Thrust" to "Glutes",
    "Calf Raise" to "Calves",
    "Plank" to "Abs / Core",
    "Hanging Leg Raise" to "Abs / Core",
)
