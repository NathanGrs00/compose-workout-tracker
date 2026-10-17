import analytics.RestState
import analytics.RestTimer
import models.Exercise
import models.LoggedSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RestTimerTest {
    private val bench = Exercise("bench", "Bench", "Chest", targetSets = 3, restSeconds = 120)
    private val curl = Exercise("curl", "Curl", "Biceps", targetSets = 2, restSeconds = 60)
    private val exercises = mapOf("bench" to bench, "curl" to curl)
    private val ids = listOf("bench", "curl")
    private fun set(id: String, exercise: String, at: Long) = LoggedSet(id, "s", exercise, 50.0, 8, at)

    @Test
    fun noSetsNoRest() {
        assertEquals(RestState.None, RestTimer.state(emptyList(), ids, exercises, 0))
    }

    @Test
    fun countsDownTheLastExercisesRestTime() {
        val sets = listOf(set("1", "bench", 0), set("2", "curl", 10_000))
        val state = RestTimer.state(sets, ids, exercises, now = 40_000)
        assertIs<RestState.Resting>(state)
        assertEquals(30_000, state.remainingMillis) // curl rest is 60 s, 30 s elapsed
    }

    @Test
    fun doneAfterRestTime() {
        val sets = listOf(set("1", "bench", 0))
        val state = RestTimer.state(sets, ids, exercises, now = 125_000)
        assertIs<RestState.Done>(state)
        assertEquals(5_000, state.overMillis)
        assertEquals("1", state.lastSetId)
        // Signal disappears after a while.
        assertEquals(RestState.None, RestTimer.state(sets, ids, exercises, now = 120_000 + RestTimer.DONE_VISIBLE_MILLIS + 1))
    }

    @Test
    fun noRestWhenAllPlannedSetsAreDone() {
        val sets = listOf(set("1", "bench", 0), set("2", "bench", 1), set("3", "bench", 2), set("4", "curl", 3), set("5", "curl", 4))
        assertEquals(RestState.None, RestTimer.state(sets, ids, exercises, now = 10))
    }

    @Test
    fun zeroRestDisablesTimer() {
        val noRest = mapOf("bench" to bench.copy(restSeconds = 0))
        assertEquals(RestState.None, RestTimer.state(listOf(set("1", "bench", 0)), listOf("bench"), noRest, now = 1))
    }
}
