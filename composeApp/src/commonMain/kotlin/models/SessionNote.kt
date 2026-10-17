package models

/** A note on one exercise in one workout, e.g. "left shoulder tight". */
data class SessionNote(
    val sessionId: String,
    val exerciseId: String,
    val note: String,
)

/** A body weight measurement in kg at [measuredAt] (epoch ms). */
data class BodyWeightEntry(
    val id: String,
    val measuredAt: Long,
    val weight: Double,
)
