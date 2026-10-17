import analytics.Progression
import analytics.SessionPoint
import models.Exercise
import models.LoggedSet
import models.Rating
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProgressionTest {
    private val bench = Exercise("bench", "Bench", "Chest", repMin = 8, repMax = 12, weightIncrement = 2.5)

    private fun session(id: String, date: Long, vararg sets: Pair<Double, Int>): SessionPoint {
        val logged = sets.mapIndexed { i, (w, r) -> LoggedSet("$id-$i", id, "bench", w, r, date + i) }
        return SessionPoint(
            sessionId = id, date = date,
            topWeight = logged.maxOf { it.weight }, bestE1rm = 0.0,
            volume = logged.sumOf { it.volume }, maxReps = logged.maxOf { it.reps },
            totalReps = logged.sumOf { it.reps }, sets = logged,
        )
    }

    @Test
    fun noHistoryNoSuggestion() {
        assertNull(Progression.suggest(bench, emptyList(), emptyMap()))
    }

    @Test
    fun goodAtTopOfRangeAddsWeight() {
        val last = session("s1", 100, 80.0 to 12, 80.0 to 12, 80.0 to 12)
        val s = Progression.suggest(bench, listOf(last), mapOf("s1" to Rating.GOOD))!!
        assertEquals(82.5, s.weight)
        assertEquals(8, s.reps)
    }

    @Test
    fun okayAtTopOfRangeAlsoAddsWeight() {
        val last = session("s1", 100, 80.0 to 12, 80.0 to 12)
        val s = Progression.suggest(bench, listOf(last), mapOf("s1" to Rating.OKAY))!!
        assertEquals(82.5, s.weight)
    }

    @Test
    fun goodBelowTopAddsARep() {
        val last = session("s1", 100, 80.0 to 9, 80.0 to 8, 80.0 to 8)
        val s = Progression.suggest(bench, listOf(last), mapOf("s1" to Rating.GOOD))!!
        assertEquals(80.0, s.weight)
        assertEquals(9, s.reps)
    }

    @Test
    fun okayMatchesBestSet() {
        val last = session("s1", 100, 80.0 to 10, 80.0 to 9, 80.0 to 8)
        val s = Progression.suggest(bench, listOf(last), mapOf("s1" to Rating.OKAY))!!
        assertEquals(80.0, s.weight)
        assertEquals(10, s.reps)
    }

    @Test
    fun unratedIsTreatedAsOkay() {
        val last = session("s1", 100, 80.0 to 10, 80.0 to 9)
        val s = Progression.suggest(bench, listOf(last), emptyMap())!!
        assertEquals(80.0 to 10, s.weight to s.reps)
    }

    @Test
    fun badRepeatsEvenAtTopOfRange() {
        val last = session("s1", 100, 80.0 to 12, 80.0 to 12)
        val s = Progression.suggest(bench, listOf(last), mapOf("s1" to Rating.BAD))!!
        assertEquals(80.0 to 12, s.weight to s.reps)
    }

    @Test
    fun badBelowRangeDropsOneStep() {
        val last = session("s1", 100, 80.0 to 7, 80.0 to 6)
        val s = Progression.suggest(bench, listOf(last), mapOf("s1" to Rating.BAD))!!
        assertEquals(77.5 to 8, s.weight to s.reps)
    }

    @Test
    fun badTwiceDeloadsTenPercent() {
        val last = session("s2", 200, 80.0 to 9, 80.0 to 8)
        val before = session("s1", 100, 80.0 to 9, 80.0 to 8)
        val s = Progression.suggest(bench, listOf(last, before), mapOf("s1" to Rating.BAD, "s2" to Rating.BAD))!!
        assertEquals(72.5, s.weight) // 72 rounded to the nearest 2.5 step
        assertEquals(8, s.reps)
    }

    @Test
    fun workingSetsAreTheTopWeightSets() {
        // Warm-up at 60 kg is ignored; working sets at 80 kg hit 12.
        val last = session("s1", 100, 60.0 to 5, 80.0 to 12, 80.0 to 12)
        val s = Progression.suggest(bench, listOf(last), mapOf("s1" to Rating.GOOD))!!
        assertEquals(82.5, s.weight)
    }

    @Test
    fun customStepIsUsed() {
        val raise = bench.copy(weightIncrement = 1.0)
        val last = session("s1", 100, 10.0 to 12, 10.0 to 12)
        assertEquals(11.0, Progression.suggest(raise, listOf(last), mapOf("s1" to Rating.GOOD))!!.weight)
    }

    @Test
    fun bodyweightProgressesByReps() {
        val last = session("s1", 100, 0.0 to 10, 0.0 to 8)
        val s = Progression.suggest(bench, listOf(last), mapOf("s1" to Rating.GOOD))!!
        assertEquals(0.0 to 11, s.weight to s.reps)
    }

    @Test
    fun extraSetAddsWeight() {
        // 3 sets planned, 4 done, not at the top of the range: still add weight because of the extra set.
        val last = session("s1", 100, 80.0 to 9, 80.0 to 9, 80.0 to 8, 80.0 to 8)
        val s = Progression.suggest(bench.copy(targetSets = 3), listOf(last), mapOf("s1" to Rating.OKAY))!!
        assertEquals(82.5 to 8, s.weight to s.reps)
        val unrated = Progression.suggest(bench.copy(targetSets = 3), listOf(last), emptyMap())!!
        assertEquals(82.5, unrated.weight)
    }

    @Test
    fun extraSetButBadDoesNotAddWeight() {
        val last = session("s1", 100, 80.0 to 9, 80.0 to 9, 80.0 to 8, 80.0 to 8)
        val s = Progression.suggest(bench.copy(targetSets = 3), listOf(last), mapOf("s1" to Rating.BAD))!!
        assertEquals(80.0, s.weight)
    }

    @Test
    fun plannedSetsOnlyIsNoExtra() {
        val last = session("s1", 100, 80.0 to 9, 80.0 to 9, 80.0 to 8)
        val s = Progression.suggest(bench.copy(targetSets = 3), listOf(last), mapOf("s1" to Rating.OKAY))!!
        assertEquals(80.0, s.weight)
    }

    @Test
    fun roundToStep() {
        assertEquals(75.0, Progression.roundToStep(74.9, 2.5))
        assertEquals(72.5, Progression.roundToStep(72.0, 2.5))
        assertEquals(2.5, Progression.roundToStep(1.0, 2.5))
    }
}
