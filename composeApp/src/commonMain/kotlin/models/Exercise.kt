package models

/**
 * This is a class to represent an exercise, which consists of an ID, a name, and a muscle group.
 * [repMin]..[repMax] is the target rep range and [weightIncrement] the weight step (kg),
 * both used to suggest the next weight and reps. [targetSets] is the planned number of sets per workout
 * and [restSeconds] the rest between sets.
 */
data class Exercise(
    val id: String,
    val name: String,
    val muscleGroup: String,
    val repMin: Int = 8,
    val repMax: Int = 12,
    val weightIncrement: Double = 2.5,
    val targetSets: Int = 3,
    val restSeconds: Int = 90,
    /** Barbell lifts get a plate calculator and warm-up suggestions. */
    val isBarbell: Boolean = false,
    /** Permanent setup note, e.g. "seat height 4, wide grip". */
    val setupNote: String = "",
    /** Normal weight, or machine assistance (see [LoadType]). */
    val loadType: LoadType = LoadType.WEIGHT,
) {
    val isAssisted: Boolean get() = loadType == LoadType.ASSISTED
}
