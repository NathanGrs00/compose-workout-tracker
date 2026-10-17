package repository

import kotlinx.datetime.Clock
import kotlin.uuid.Uuid

/** Current time in epoch milliseconds. */
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** New random UUID string used as a primary key, so rows can be created offline on any device. */
fun newId(): String = Uuid.random().toString()

internal fun List<String>.joinIds(): String = joinToString(",")
internal fun String.splitIds(): List<String> = split(",").filter { it.isNotBlank() }
