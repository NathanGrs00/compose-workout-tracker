package models

/** How an exercise felt in a workout. */
enum class Rating(val label: String) {
    GOOD("Good"),
    OKAY("Okay"),
    BAD("Bad");

    companion object {
        fun fromName(name: String): Rating? = entries.firstOrNull { it.name == name }
    }
}

/** The rating given to one exercise in one workout session. */
data class ExerciseRating(
    val sessionId: String,
    val exerciseId: String,
    val rating: Rating,
)
