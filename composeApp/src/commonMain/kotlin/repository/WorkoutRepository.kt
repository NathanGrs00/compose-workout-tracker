package repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import db.WorkoutDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import models.Exercise
import models.ExerciseRating
import models.Rating
import models.LoggedSet
import models.WorkoutDay
import models.WorkoutSession
import models.starterExercises
import repository.mappers.toExercise
import repository.mappers.toExerciseRating
import repository.mappers.toLoggedSet
import repository.mappers.toWorkoutDay
import repository.mappers.toWorkoutSession

/**
 * Local, offline-first data access. Every write goes to the on-device SQLite database first
 * and is marked dirty; [repository.sync.SyncManager] uploads it to Supabase when online.
 */
class WorkoutRepository(
    private val db: WorkoutDatabase,
    private val clock: () -> Long = ::nowMillis,
) {
    private val io = Dispatchers.Default
    private val _localChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 64)

    /** Emits after every local write, so sync can be scheduled. */
    val localChanges: SharedFlow<Unit> = _localChanges.asSharedFlow()

    private fun changed() {
        _localChanges.tryEmit(Unit)
    }

    /** A strictly increasing edit timestamp, so "mark clean after upload" can detect newer edits. */
    private fun editTime(previous: Long?): Long = maxOf(clock(), (previous ?: 0L) + 1)

    // ---------------------------------------------------------------- Exercises

    val exercises: Flow<List<Exercise>> =
        db.exerciseQueries.selectActive().asFlow().mapToList(io).map { rows -> rows.map { it.toExercise() } }

    fun addExercise(
        name: String,
        muscleGroup: String,
        repMin: Int = 8,
        repMax: Int = 12,
        weightIncrement: Double = 2.5,
        targetSets: Int = 3,
        restSeconds: Int = 90,
    ): Exercise {
        val exercise = Exercise(newId(), name.trim(), muscleGroup, repMin, repMax, weightIncrement, targetSets, restSeconds)
        writeExercise(exercise, previousUpdatedAt = null, deleted = false)
        changed()
        return exercise
    }

    fun updateExercise(exercise: Exercise) {
        val old = db.exerciseQueries.selectById(exercise.id).executeAsOneOrNull()
        writeExercise(exercise.copy(name = exercise.name.trim()), old?.updated_at, deleted = false)
        changed()
    }

    fun deleteExercise(id: String) {
        val old = db.exerciseQueries.selectById(id).executeAsOneOrNull() ?: return
        writeExercise(old.toExercise(), old.updated_at, deleted = true)
        changed()
    }

    private fun writeExercise(exercise: Exercise, previousUpdatedAt: Long?, deleted: Boolean) {
        val repMin = exercise.repMin.coerceAtLeast(1)
        db.exerciseQueries.upsert(
            exercise.id, exercise.name, exercise.muscleGroup,
            repMin.toLong(), exercise.repMax.coerceAtLeast(repMin).toLong(), exercise.weightIncrement.coerceAtLeast(0.25),
            exercise.targetSets.coerceAtLeast(1).toLong(), exercise.restSeconds.coerceAtLeast(0).toLong(),
            editTime(previousUpdatedAt), if (deleted) 1 else 0, 1,
        )
    }

    /** Adds the starter exercises that aren't in the library yet (matched by name). */
    fun addStarterExercises() {
        val existing = db.exerciseQueries.selectActive().executeAsList().map { it.name.lowercase() }.toSet()
        db.transaction {
            starterExercises.filter { it.first.lowercase() !in existing }.forEach { (name, group) ->
                writeExercise(Exercise(newId(), name, group), previousUpdatedAt = null, deleted = false)
            }
        }
        changed()
    }

    // ---------------------------------------------------------------- Routines (workout days)

    val workoutDays: Flow<List<WorkoutDay>> =
        db.workoutDayQueries.selectActive().asFlow().mapToList(io).map { rows -> rows.map { it.toWorkoutDay() } }

    fun saveWorkoutDay(day: WorkoutDay) {
        val old = db.workoutDayQueries.selectById(day.id).executeAsOneOrNull()
        db.workoutDayQueries.upsert(day.id, day.name.trim(), day.exerciseIds.joinIds(), editTime(old?.updated_at), 0, 1)
        changed()
    }

    fun deleteWorkoutDay(id: String) {
        val old = db.workoutDayQueries.selectById(id).executeAsOneOrNull() ?: return
        db.workoutDayQueries.upsert(id, old.name, old.exercise_ids, editTime(old.updated_at), 1, 1)
        changed()
    }

    // ---------------------------------------------------------------- Workout sessions

    /** All sessions, newest first. */
    val sessions: Flow<List<WorkoutSession>> =
        db.workoutSessionQueries.selectActive().asFlow().mapToList(io).map { rows -> rows.map { it.toWorkoutSession() } }

    /** The workout currently in progress, if any. */
    val inProgressSession: Flow<WorkoutSession?> =
        db.workoutSessionQueries.selectInProgress().asFlow().mapToOneOrNull(io).map { it?.toWorkoutSession() }

    fun session(id: String): Flow<WorkoutSession?> =
        db.workoutSessionQueries.selectById(id).asFlow().mapToOneOrNull(io)
            .map { row -> row?.takeIf { it.deleted == 0L }?.toWorkoutSession() }

    fun startSession(day: WorkoutDay?): WorkoutSession {
        val now = clock()
        val session = WorkoutSession(
            id = newId(),
            workoutDayId = day?.id,
            name = day?.name ?: "Workout",
            exerciseIds = day?.exerciseIds ?: emptyList(),
            startedAt = now,
            finishedAt = null,
        )
        writeSession(session, previousUpdatedAt = null)
        changed()
        return session
    }

    fun updateSession(session: WorkoutSession) {
        val old = db.workoutSessionQueries.selectById(session.id).executeAsOneOrNull()
        writeSession(session, old?.updated_at)
        changed()
    }

    fun addExerciseToSession(sessionId: String, exerciseId: String) {
        val session = currentSession(sessionId) ?: return
        if (exerciseId in session.exerciseIds) return
        updateSession(session.copy(exerciseIds = session.exerciseIds + exerciseId))
    }

    /** Removes an exercise from a session, including the sets logged for it. */
    fun removeExerciseFromSession(sessionId: String, exerciseId: String) {
        val session = currentSession(sessionId) ?: return
        db.transaction {
            db.loggedSetQueries.selectForSession(sessionId).executeAsList()
                .filter { it.exercise_id == exerciseId }
                .forEach { softDeleteSet(it.id) }
            setRating(sessionId, exerciseId, null)
            updateSession(session.copy(exerciseIds = session.exerciseIds - exerciseId))
        }
        changed()
    }

    /** Moves an exercise one position up (-1) or down (+1) within a session. */
    fun moveExerciseInSession(sessionId: String, exerciseId: String, direction: Int) {
        val session = currentSession(sessionId) ?: return
        updateSession(session.copy(exerciseIds = session.exerciseIds.moved(exerciseId, direction)))
    }

    /**
     * Finishes a workout. A workout without any logged sets is discarded instead.
     * @return true if the workout was saved, false if it was discarded.
     */
    fun finishSession(id: String): Boolean {
        val session = currentSession(id) ?: return false
        val hasSets = db.loggedSetQueries.selectForSession(id).executeAsList().isNotEmpty()
        if (!hasSets) {
            deleteSession(id)
            return false
        }
        updateSession(session.copy(finishedAt = clock()))
        return true
    }

    /** Soft-deletes a session and all of its sets. */
    fun deleteSession(id: String) {
        val old = db.workoutSessionQueries.selectById(id).executeAsOneOrNull() ?: return
        db.transaction {
            db.loggedSetQueries.selectForSession(id).executeAsList().forEach { softDeleteSet(it.id) }
            db.exerciseRatingQueries.selectForSession(id).executeAsList().forEach { setRating(id, it.exercise_id, null) }
            db.workoutSessionQueries.upsert(
                old.id, old.workout_day_id, old.name, old.exercise_ids, old.started_at, old.finished_at,
                editTime(old.updated_at), 1, 1,
            )
        }
        changed()
    }

    private fun currentSession(id: String): WorkoutSession? =
        db.workoutSessionQueries.selectById(id).executeAsOneOrNull()?.takeIf { it.deleted == 0L }?.toWorkoutSession()

    private fun writeSession(session: WorkoutSession, previousUpdatedAt: Long?) {
        db.workoutSessionQueries.upsert(
            session.id, session.workoutDayId, session.name.trim().ifBlank { "Workout" }, session.exerciseIds.joinIds(),
            session.startedAt, session.finishedAt, editTime(previousUpdatedAt), 0, 1,
        )
    }

    // ---------------------------------------------------------------- Sets

    /** Every logged set (all sessions), oldest first. Used for analytics and "last time" hints. */
    val allSets: Flow<List<LoggedSet>> =
        db.loggedSetQueries.selectActive().asFlow().mapToList(io).map { rows -> rows.map { it.toLoggedSet() } }

    fun setsForSession(sessionId: String): Flow<List<LoggedSet>> =
        db.loggedSetQueries.selectForSession(sessionId).asFlow().mapToList(io).map { rows -> rows.map { it.toLoggedSet() } }

    fun logSet(sessionId: String, exerciseId: String, weight: Double, reps: Int): LoggedSet {
        val now = clock()
        val set = LoggedSet(newId(), sessionId, exerciseId, weight, reps, performedAt = now)
        db.transaction {
            db.loggedSetQueries.upsert(set.id, sessionId, exerciseId, weight, reps.toLong(), now, editTime(null), 0, 1)
            addExerciseToSession(sessionId, exerciseId)
        }
        changed()
        return set
    }

    fun updateSet(set: LoggedSet) {
        val old = db.loggedSetQueries.selectById(set.id).executeAsOneOrNull() ?: return
        db.loggedSetQueries.upsert(
            set.id, old.session_id, old.exercise_id, set.weight, set.reps.toLong(), old.performed_at,
            editTime(old.updated_at), 0, 1,
        )
        changed()
    }

    fun deleteSet(id: String) {
        softDeleteSet(id)
        changed()
    }

    private fun softDeleteSet(id: String) {
        val old = db.loggedSetQueries.selectById(id).executeAsOneOrNull() ?: return
        db.loggedSetQueries.upsert(
            old.id, old.session_id, old.exercise_id, old.weight, old.reps, old.performed_at,
            editTime(old.updated_at), 1, 1,
        )
    }

    // ---------------------------------------------------------------- Ratings

    /** All ratings ("how did it go") for every session and exercise. */
    val ratings: Flow<List<ExerciseRating>> =
        db.exerciseRatingQueries.selectActive().asFlow().mapToList(io).map { rows -> rows.mapNotNull { it.toExerciseRating() } }

    /** Sets (or with null, clears) how an exercise went in a session. */
    fun setRating(sessionId: String, exerciseId: String, rating: Rating?) {
        val id = "$sessionId:$exerciseId"
        val old = db.exerciseRatingQueries.selectById(id).executeAsOneOrNull()
        if (rating == null && (old == null || old.deleted == 1L)) return
        db.exerciseRatingQueries.upsert(
            id, sessionId, exerciseId, rating?.name ?: old!!.rating, editTime(old?.updated_at), if (rating == null) 1 else 0, 1,
        )
        changed()
    }

    // ---------------------------------------------------------------- Sync status

    /** Number of local changes that still need to be uploaded. */
    val pendingChanges: Flow<Long> = combine(
        db.exerciseQueries.countDirty().asFlow().mapToOneOrNull(io),
        db.workoutDayQueries.countDirty().asFlow().mapToOneOrNull(io),
        db.workoutSessionQueries.countDirty().asFlow().mapToOneOrNull(io),
        db.loggedSetQueries.countDirty().asFlow().mapToOneOrNull(io),
        db.exerciseRatingQueries.countDirty().asFlow().mapToOneOrNull(io),
    ) { a, b, c, d, e -> (a ?: 0) + (b ?: 0) + (c ?: 0) + (d ?: 0) + (e ?: 0) }
}

/** Moves [item] one step in [direction] (-1 = up, +1 = down). */
fun <T> List<T>.moved(item: T, direction: Int): List<T> {
    val from = indexOf(item)
    val to = from + direction
    if (from < 0 || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
