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
 * - Did more sets than planned (an unplanned extra set) and it didn't feel Bad → add one weight step.
 * - Good → same weight, one more rep.
 * - Okay → same weight, match your best set on every set.
 * - Bad → repeat the same weight and reps. Bad twice in a row, or below the rep range → lower the weight.
 * - Not rated → treated as Okay.
 *
 * Working sets are the sets at the session's top weight. Bodyweight exercises (weight 0) progress by reps only.
 * Assisted exercises store assistance as a negative weight, so progressing means less assistance, down to none.
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

        val assisted = exercise.isAssisted
        // Bodyweight: plain bodyweight exercises, or an assisted exercise already done without assistance.
        if (topWeight == 0.0 || (topWeight < 0 && !assisted)) {
            return when (rating) {
                Rating.BAD -> Suggestion(0.0, lowestReps, "Felt bad last time → repeat $lowestReps reps")
                else -> Suggestion(0.0, bestReps + 1, "Bodyweight: aim for one more rep than your best ($bestReps)")
            }
        }

        // For assisted exercises the weight is negative (−assistance): "heavier" means less assistance.
        val heavier = if (assisted) (topWeight + step).coerceAtMost(0.0) else topWeight + step
        val progressText = when {
            !assisted -> "add ${Format.number(step)} kg"
            heavier == 0.0 -> "try it without assistance"
            else -> "reduce assistance to ${Format.number(-heavier)} kg"
        }
        val same = if (assisted) "same assistance" else "same weight"
        return when {
            rating == Rating.BAD && previousRating == Rating.BAD -> {
                val lighter = if (assisted) {
                    topWeight - 2 * step
                } else {
                    roundToStep(topWeight * 0.9, step).coerceAtMost(topWeight - step).coerceAtLeast(step)
                }
                val what = if (assisted) "more assistance (${Format.load(lighter)})" else "drop ~10% to ${Format.number(lighter)} kg"
                Suggestion(lighter, repMin, "Bad twice in a row → $what and rebuild")
            }
            rating == Rating.BAD && lowestReps < repMin -> {
                val lighter = if (assisted) topWeight - step else (topWeight - step).coerceAtLeast(step)
                val what = if (assisted) "use more assistance (${Format.load(lighter)})" else "go down to ${Format.number(lighter)} kg"
                Suggestion(lighter, repMin, "Felt bad and below $repMin reps → $what")
            }
            rating == Rating.BAD -> Suggestion(topWeight, lowestReps, "Felt bad last time → repeat ${Format.setLong(topWeight, lowestReps)}")
            last.sets.size > exercise.targetSets.coerceAtLeast(1) -> {
                val target = lowestReps.coerceIn(repMin, repMax)
                Suggestion(
                    heavier, target,
                    "Did an extra set (${last.sets.size} of ${exercise.targetSets}) → energy left, $progressText",
                )
            }
            lowestReps >= repMax -> Suggestion(heavier, repMin, "Hit $repMax reps on every set$feel → $progressText")
            rating == Rating.GOOD -> {
                val target = (lowestReps + 1).coerceIn(repMin, repMax)
                Suggestion(topWeight, target, "Felt good → $same, aim for $target reps")
            }
            else -> {
                val target = bestReps.coerceIn(repMin, repMax)
                Suggestion(topWeight, target, "${if (rating == null) "Not rated" else "Felt okay"} → $same, $target reps on every set")
            }
        }
    }

    /** Rounds to the nearest multiple of [step] (at least one step). */
    fun roundToStep(weight: Double, step: Double): Double {
        val steps = (weight / step).roundToInt().coerceAtLeast(1)
        return (steps * step * 100).roundToInt() / 100.0
    }
}
