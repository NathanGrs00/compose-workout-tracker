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
import models.LoggedSet
import models.WorkoutDay
import models.WorkoutSession
import models.starterExercises
import repository.mappers.toExercise
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

    fun addExercise(name: String, muscleGroup: String): Exercise {
        val exercise = Exercise(id = newId(), name = name.trim(), muscleGroup = muscleGroup)
        db.exerciseQueries.upsert(exercise.id, exercise.name, exercise.muscleGroup, editTime(null), 0, 1)
        changed()
        return exercise
    }

    fun updateExercise(exercise: Exercise) {
        val old = db.exerciseQueries.selectById(exercise.id).executeAsOneOrNull()
        db.exerciseQueries.upsert(exercise.id, exercise.name.trim(), exercise.muscleGroup, editTime(old?.updated_at), 0, 1)
        changed()
    }

    fun deleteExercise(id: String) {
        val old = db.exerciseQueries.selectById(id).executeAsOneOrNull() ?: return
        db.exerciseQueries.upsert(id, old.name, old.muscle_group, editTime(old.updated_at), 1, 1)
        changed()
    }

    /** Adds the starter exercises that aren't in the library yet (matched by name). */
    fun addStarterExercises() {
        val existing = db.exerciseQueries.selectActive().executeAsList().map { it.name.lowercase() }.toSet()
        db.transaction {
            starterExercises.filter { it.first.lowercase() !in existing }.forEach { (name, group) ->
                db.exerciseQueries.upsert(newId(), name, group, editTime(null), 0, 1)
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

    // ---------------------------------------------------------------- Sync status

    /** Number of local changes that still need to be uploaded. */
    val pendingChanges: Flow<Long> = combine(
        db.exerciseQueries.countDirty().asFlow().mapToOneOrNull(io),
        db.workoutDayQueries.countDirty().asFlow().mapToOneOrNull(io),
        db.workoutSessionQueries.countDirty().asFlow().mapToOneOrNull(io),
        db.loggedSetQueries.countDirty().asFlow().mapToOneOrNull(io),
    ) { a, b, c, d -> (a ?: 0) + (b ?: 0) + (c ?: 0) + (d ?: 0) }
}

/** Moves [item] one step in [direction] (-1 = up, +1 = down). */
fun <T> List<T>.moved(item: T, direction: Int): List<T> {
    val from = indexOf(item)
    val to = from + direction
    if (from < 0 || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
