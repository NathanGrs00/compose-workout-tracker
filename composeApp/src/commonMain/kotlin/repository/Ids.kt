package repository

import kotlinx.datetime.Clock
import kotlin.uuid.Uuid

/** Current time in epoch milliseconds. */
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** New random UUID string used as a primary key, so rows can be created offline on any device. */
fun newId(): String = Uuid.random().toString()

internal fun List<String>.joinIds(): String = joinToString(",")
internal fun String.splitIds(): List<String> = split(",").filter { it.isNotBlank() }

/** Superset pairs as "a|b,c|d". */
internal fun List<Pair<String, String>>.joinPairs(): String = joinToString(",") { "${it.first}|${it.second}" }
internal fun String.splitPairs(): List<Pair<String, String>> = splitIds().mapNotNull { pair ->
    pair.split("|").takeIf { it.size == 2 && it.all(String::isNotBlank) }?.let { it[0] to it[1] }
}
internal fun List<Pair<String, String>>.toLists(): List<List<String>> = map { listOf(it.first, it.second) }
internal fun List<List<String>>.toPairs(): List<Pair<String, String>> = filter { it.size == 2 }.map { it[0] to it[1] }
