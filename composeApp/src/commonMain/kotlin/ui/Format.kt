package ui

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.roundToLong

/** Formatting helpers (multiplatform, so no String.format). */
object Format {
    private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    /** 60.0 -> "60", 62.5 -> "62.5", 61.25 -> "61.25" */
    fun number(value: Double, maxDecimals: Int = 2): String {
        var factor = 1L
        repeat(maxDecimals) { factor *= 10 }
        val rounded = (value * factor).roundToLong()
        val whole = rounded / factor
        var fraction = abs(rounded % factor).toString().padStart(maxDecimals, '0').trimEnd('0')
        val sign = if (rounded < 0 && whole == 0L) "-" else ""
        return if (fraction.isEmpty()) "$sign$whole" else "$sign$whole.$fraction"
    }

    fun weight(kg: Double): String = "${number(kg)} kg"

    /** Short set description: "60 × 8", "4.5 assist × 8" (assisted, negative weight) or "12 reps" (bodyweight). */
    fun set(weight: Double, reps: Int): String = when {
        weight > 0 -> "${number(weight)} × $reps"
        weight < 0 -> "${number(-weight)} assist × $reps"
        else -> "$reps reps"
    }

    /** Load description: "60 kg", "4.5 kg assist" or "bodyweight". */
    fun load(weight: Double): String = when {
        weight > 0 -> weight(weight)
        weight < 0 -> "${number(-weight)} kg assist"
        else -> "bodyweight"
    }

    /** Full set description: "60 kg × 8", "4.5 kg assist × 8" or "12 reps". */
    fun setLong(weight: Double, reps: Int): String = if (weight == 0.0) "$reps reps" else "${load(weight)} × $reps"

    /** 12500.0 -> "12.5k" */
    fun compact(value: Double): String = when {
        abs(value) >= 1_000_000 -> number(value / 1_000_000, 1) + "M"
        abs(value) >= 10_000 -> number(value / 1_000, 0) + "k"
        abs(value) >= 1_000 -> number(value / 1_000, 1) + "k"
        else -> number(value, 1)
    }

    /** 1234567 -> "1,234,567" */
    fun thousands(value: Double): String {
        val digits = value.roundToLong().toString()
        val negative = digits.startsWith("-")
        val grouped = digits.removePrefix("-").reversed().chunked(3).joinToString(",").reversed()
        return if (negative) "-$grouped" else grouped
    }

    fun localDate(millis: Long): LocalDate =
        Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).date

    /** "Mon 14 Oct" (adds the year when it isn't the current year). */
    fun date(millis: Long, now: Long = repository.nowMillis()): String {
        val date = localDate(millis)
        val year = if (date.year != localDate(now).year) " ${date.year}" else ""
        return "${days[date.dayOfWeek.ordinal]} ${date.dayOfMonth} ${months[date.monthNumber - 1]}$year"
    }

    /** "14 Oct" */
    fun shortDate(date: LocalDate): String = "${date.dayOfMonth} ${months[date.monthNumber - 1]}"

    fun monthYear(millis: Long): String {
        val date = localDate(millis)
        return "${months[date.monthNumber - 1]} ${date.year}"
    }

    fun time(millis: Long): String {
        val t = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
        return "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
    }

    /** "1:05:09" or "5:09" */
    fun duration(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        val ss = s.toString().padStart(2, '0')
        return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$ss" else "$m:$ss"
    }

    /** "1h 05m" or "45m" */
    fun durationShort(millis: Long): String {
        val minutes = (millis / 60_000).coerceAtLeast(0)
        return if (minutes >= 60) "${minutes / 60}h ${(minutes % 60).toString().padStart(2, '0')}m" else "${minutes}m"
    }

    /** "just now", "5 min ago", "3 h ago", "2 days ago" */
    fun ago(millis: Long, now: Long = repository.nowMillis()): String {
        val diff = (now - millis).coerceAtLeast(0) / 1000
        return when {
            diff < 60 -> "just now"
            diff < 3600 -> "${diff / 60} min ago"
            diff < 86_400 -> "${diff / 3600} h ago"
            diff < 2 * 86_400 -> "yesterday"
            else -> "${diff / 86_400} days ago"
        }
    }

    /** Parses user input like "62,5" or "62.5". */
    fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
}
