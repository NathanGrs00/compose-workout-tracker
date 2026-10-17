package analytics

import models.Exercise
import models.Rating
import ui.Format
import kotlin.math.roundToInt

/** A suggested target for the next session of an exercise, with a short explanation. */
data class Suggestion(
    val weight: Double,
    val reps: Int,
    val reason: String,
)

/**
 * Rep-range ("double") progression driven by how the previous sessions felt.
 *
 * - Hit the top of the rep range on every working set and it felt Good or Okay → add one weight step,
 *   restart at the bottom of the range.
 * - Good → same weight, one more rep.
 * - Okay → same weight, match your best set on every set.
 * - Bad → repeat the same weight and reps. Bad twice in a row, or below the rep range → lower the weight.
 * - Not rated → treated as Okay.
 *
 * Working sets are the sets at the session's top weight. Bodyweight exercises (weight 0) progress by reps only.
 */
object Progression {

    /**
     * @param history previous sessions of this exercise, most recent first (the current session excluded).
     * @param ratings rating per session id for this exercise.
     */
    fun suggest(exercise: Exercise, history: List<SessionPoint>, ratings: Map<String, Rating>): Suggestion? {
        val last = history.firstOrNull() ?: return null
        val rating = ratings[last.sessionId]
        val previousRating = history.getOrNull(1)?.let { ratings[it.sessionId] }
        val repMin = exercise.repMin.coerceAtLeast(1)
        val repMax = exercise.repMax.coerceAtLeast(repMin)
        val step = exercise.weightIncrement.takeIf { it > 0 } ?: 2.5

        val topWeight = last.topWeight
        val working = last.sets.filter { it.weight == topWeight }
        val lowestReps = working.minOf { it.reps }
        val bestReps = working.maxOf { it.reps }
        val feel = rating?.label?.lowercase()?.let { " and it felt $it" }.orEmpty()

        if (topWeight <= 0) {
            return when (rating) {
                Rating.BAD -> Suggestion(0.0, lowestReps, "Felt bad last time → repeat $lowestReps reps")
                else -> Suggestion(0.0, bestReps + 1, "Bodyweight: aim for one more rep than your best ($bestReps)")
            }
        }

        val w = Format.number(topWeight)
        return when {
            rating == Rating.BAD && previousRating == Rating.BAD -> {
                val lighter = roundToStep(topWeight * 0.9, step).coerceAtMost(topWeight - step).coerceAtLeast(step)
                Suggestion(lighter, repMin, "Bad twice in a row → drop ~10% to ${Format.number(lighter)} kg and rebuild")
            }
            rating == Rating.BAD && lowestReps < repMin -> {
                val lighter = (topWeight - step).coerceAtLeast(step)
                Suggestion(lighter, repMin, "Felt bad and below $repMin reps → go down to ${Format.number(lighter)} kg")
            }
            rating == Rating.BAD -> Suggestion(topWeight, lowestReps, "Felt bad last time → repeat $w kg × $lowestReps")
            lowestReps >= repMax -> {
                val heavier = topWeight + step
                Suggestion(heavier, repMin, "Hit $repMax reps on every set$feel → add ${Format.number(step)} kg")
            }
            rating == Rating.GOOD -> {
                val target = (lowestReps + 1).coerceIn(repMin, repMax)
                Suggestion(topWeight, target, "Felt good → same weight, aim for $target reps")
            }
            else -> {
                val target = bestReps.coerceIn(repMin, repMax)
                Suggestion(topWeight, target, "${if (rating == null) "Not rated" else "Felt okay"} → same weight, $target reps on every set")
            }
        }
    }

    /** Rounds to the nearest multiple of [step] (at least one step). */
    fun roundToStep(weight: Double, step: Double): Double {
        val steps = (weight / step).roundToInt().coerceAtLeast(1)
        return (steps * step * 100).roundToInt() / 100.0
    }
}
