import ui.Format
import ui.charts.niceTicks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FormatTest {
    @Test
    fun numbers() {
        assertEquals("60", Format.number(60.0))
        assertEquals("62.5", Format.number(62.5))
        assertEquals("61.25", Format.number(61.25))
        assertEquals("0.1", Format.number(0.1))
        assertEquals("-0.5", Format.number(-0.5))
        assertEquals("1.3", Format.number(1.26, 1))
    }

    @Test
    fun compactAndThousands() {
        assertEquals("950", Format.compact(950.0))
        assertEquals("1.5k", Format.compact(1500.0))
        assertEquals("25k", Format.compact(25_000.0))
        assertEquals("1,234,567", Format.thousands(1_234_567.0))
        assertEquals("999", Format.thousands(999.0))
    }

    @Test
    fun assistedSets() {
        assertEquals("4.5 assist × 8", Format.set(-4.5, 8))
        assertEquals("4.5 kg assist × 8", Format.setLong(-4.5, 8))
        assertEquals("80 kg × 8", Format.setLong(80.0, 8))
        assertEquals("12 reps", Format.setLong(0.0, 12))
        assertEquals("bodyweight", Format.load(0.0))
    }

    @Test
    fun durations() {
        assertEquals("0:05", Format.duration(5_000))
        assertEquals("1:05:09", Format.duration(3_909_000))
        assertEquals("1h 05m", Format.durationShort(3_900_000))
    }

    @Test
    fun parseDecimalAcceptsComma() {
        assertEquals(62.5, Format.parseDecimal("62,5"))
        assertEquals(null, Format.parseDecimal("abc"))
    }

    @Test
    fun niceTicksCoverRange() {
        val ticks = niceTicks(83.0, 117.0)
        assertTrue(ticks.first() <= 83.0 && ticks.last() >= 117.0)
        assertEquals(listOf(80.0, 90.0, 100.0, 110.0, 120.0), ticks)
        assertTrue(niceTicks(5.0, 5.0).size >= 2)
    }
}
