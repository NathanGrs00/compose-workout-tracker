package models

/**
 * How the weight of an exercise is entered.
 * - [WEIGHT]: normal load in kg (0 = bodyweight).
 * - [ASSISTED]: machine assistance in kg (e.g. assisted pull-up). Entered as a positive number,
 *   stored on sets as a negative weight, so "higher is harder" holds for progression and charts.
 */
enum class LoadType {
    WEIGHT,
    ASSISTED;

    companion object {
        fun fromName(name: String?): LoadType = entries.firstOrNull { it.name == name } ?: WEIGHT
    }
}
