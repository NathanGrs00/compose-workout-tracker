package repository.sync

import db.WorkoutDatabase
import kotlinx.serialization.KSerializer
import repository.dto.ExerciseDto
import repository.dto.ExerciseRatingDto
import repository.dto.LoggedSetDto
import repository.dto.WorkoutDayDto
import repository.dto.WorkoutSessionDto
import repository.joinIds
import repository.joinPairs
import repository.splitIds
import repository.splitPairs
import repository.toLists
import repository.toPairs
import repository.dto.SessionNoteDto
import repository.dto.BodyWeightDto

/**
 * Describes how one table is synced: which local rows need uploading, how to mark them
 * uploaded, and how to apply a row downloaded from the server.
 */
class SyncTable<T>(
    val remoteName: String,
    val serializer: KSerializer<T>,
    val id: (T) -> String,
    val updatedAt: (T) -> Long,
    val serverUpdatedAt: (T) -> Long?,
    val dirtyRows: () -> List<T>,
    val markClean: (id: String, updatedAt: Long) -> Unit,
    /** Local row's (updatedAt, dirty) or null if it doesn't exist locally. */
    val localVersion: (id: String) -> Pair<Long, Boolean>?,
    /** Writes a downloaded row locally, marked clean. */
    val writeClean: (T) -> Unit,
)

private fun Long.bool() = this != 0L
private fun Boolean.long() = if (this) 1L else 0L

/** All synced tables, in upload order. */
fun syncTables(db: WorkoutDatabase): List<SyncTable<*>> = listOf(
    SyncTable(
        remoteName = "exercises",
        serializer = ExerciseDto.serializer(),
        id = { it.id }, updatedAt = { it.updatedAt }, serverUpdatedAt = { it.serverUpdatedAt },
        dirtyRows = {
            db.exerciseQueries.selectDirty().executeAsList().map {
                ExerciseDto(
                    it.id, it.name, it.muscle_group, it.rep_min.toInt(), it.rep_max.toInt(), it.weight_increment,
                    it.target_sets.toInt(), it.rest_seconds.toInt(), it.updated_at, it.deleted.bool(),
                    isBarbell = it.is_barbell.bool(), setupNote = it.setup_note, loadType = it.load_type,
                )
            }
        },
        markClean = { id, updatedAt -> db.exerciseQueries.markClean(id, updatedAt) },
        localVersion = { id -> db.exerciseQueries.selectById(id).executeAsOneOrNull()?.let { it.updated_at to it.dirty.bool() } },
        writeClean = {
            db.exerciseQueries.upsert(
                it.id, it.name, it.muscleGroup, it.repMin.toLong(), it.repMax.toLong(), it.weightIncrement,
                it.targetSets.toLong(), it.restSeconds.toLong(), it.isBarbell.long(), it.setupNote, it.loadType,
                it.updatedAt, it.deleted.long(), 0,
            )
        },
    ),
    SyncTable(
        remoteName = "workout_days",
        serializer = WorkoutDayDto.serializer(),
        id = { it.id }, updatedAt = { it.updatedAt }, serverUpdatedAt = { it.serverUpdatedAt },
        dirtyRows = {
            db.workoutDayQueries.selectDirty().executeAsList().map {
                WorkoutDayDto(
                    it.id, it.name, it.exercise_ids.splitIds(), it.updated_at, it.deleted.bool(),
                    supersets = it.supersets.splitPairs().toLists(),
                )
            }
        },
        markClean = { id, updatedAt -> db.workoutDayQueries.markClean(id, updatedAt) },
        localVersion = { id -> db.workoutDayQueries.selectById(id).executeAsOneOrNull()?.let { it.updated_at to it.dirty.bool() } },
        writeClean = {
            db.workoutDayQueries.upsert(
                it.id, it.name, it.exerciseIds.joinIds(), it.supersets.toPairs().joinPairs(), it.updatedAt, it.deleted.long(), 0,
            )
        },
    ),
    SyncTable(
        remoteName = "workout_sessions",
        serializer = WorkoutSessionDto.serializer(),
        id = { it.id }, updatedAt = { it.updatedAt }, serverUpdatedAt = { it.serverUpdatedAt },
        dirtyRows = {
            db.workoutSessionQueries.selectDirty().executeAsList().map {
                WorkoutSessionDto(
                    it.id, it.workout_day_id, it.name, it.exercise_ids.splitIds(),
                    it.started_at, it.finished_at, it.updated_at, it.deleted.bool(),
                    supersets = it.supersets.splitPairs().toLists(),
                )
            }
        },
        markClean = { id, updatedAt -> db.workoutSessionQueries.markClean(id, updatedAt) },
        localVersion = { id -> db.workoutSessionQueries.selectById(id).executeAsOneOrNull()?.let { it.updated_at to it.dirty.bool() } },
        writeClean = {
            db.workoutSessionQueries.upsert(
                it.id, it.workoutDayId, it.name, it.exerciseIds.joinIds(), it.supersets.toPairs().joinPairs(),
                it.startedAt, it.finishedAt, it.updatedAt, it.deleted.long(), 0,
            )
        },
    ),
    SyncTable(
        remoteName = "logged_sets",
        serializer = LoggedSetDto.serializer(),
        id = { it.id }, updatedAt = { it.updatedAt }, serverUpdatedAt = { it.serverUpdatedAt },
        dirtyRows = {
            db.loggedSetQueries.selectDirty().executeAsList().map {
                LoggedSetDto(
                    it.id, it.session_id, it.exercise_id, it.weight, it.reps.toInt(), it.performed_at,
                    it.updated_at, it.deleted.bool(),
                )
            }
        },
        markClean = { id, updatedAt -> db.loggedSetQueries.markClean(id, updatedAt) },
        localVersion = { id -> db.loggedSetQueries.selectById(id).executeAsOneOrNull()?.let { it.updated_at to it.dirty.bool() } },
        writeClean = {
            db.loggedSetQueries.upsert(
                it.id, it.sessionId, it.exerciseId, it.weight, it.reps.toLong(), it.performedAt,
                it.updatedAt, it.deleted.long(), 0,
            )
        },
    ),
    SyncTable(
        remoteName = "exercise_ratings",
        serializer = ExerciseRatingDto.serializer(),
        id = { it.id }, updatedAt = { it.updatedAt }, serverUpdatedAt = { it.serverUpdatedAt },
        dirtyRows = {
            db.exerciseRatingQueries.selectDirty().executeAsList().map {
                ExerciseRatingDto(it.id, it.session_id, it.exercise_id, it.rating, it.updated_at, it.deleted.bool())
            }
        },
        markClean = { id, updatedAt -> db.exerciseRatingQueries.markClean(id, updatedAt) },
        localVersion = { id -> db.exerciseRatingQueries.selectById(id).executeAsOneOrNull()?.let { it.updated_at to it.dirty.bool() } },
        writeClean = {
            db.exerciseRatingQueries.upsert(it.id, it.sessionId, it.exerciseId, it.rating, it.updatedAt, it.deleted.long(), 0)
        },
    ),
    SyncTable(
        remoteName = "session_notes",
        serializer = SessionNoteDto.serializer(),
        id = { it.id }, updatedAt = { it.updatedAt }, serverUpdatedAt = { it.serverUpdatedAt },
        dirtyRows = {
            db.sessionNoteQueries.selectDirty().executeAsList().map {
                SessionNoteDto(it.id, it.session_id, it.exercise_id, it.note, it.updated_at, it.deleted.bool())
            }
        },
        markClean = { id, updatedAt -> db.sessionNoteQueries.markClean(id, updatedAt) },
        localVersion = { id -> db.sessionNoteQueries.selectById(id).executeAsOneOrNull()?.let { it.updated_at to it.dirty.bool() } },
        writeClean = {
            db.sessionNoteQueries.upsert(it.id, it.sessionId, it.exerciseId, it.note, it.updatedAt, it.deleted.long(), 0)
        },
    ),
    SyncTable(
        remoteName = "body_weights",
        serializer = BodyWeightDto.serializer(),
        id = { it.id }, updatedAt = { it.updatedAt }, serverUpdatedAt = { it.serverUpdatedAt },
        dirtyRows = {
            db.bodyWeightQueries.selectDirty().executeAsList().map {
                BodyWeightDto(it.id, it.measured_at, it.weight, it.updated_at, it.deleted.bool())
            }
        },
        markClean = { id, updatedAt -> db.bodyWeightQueries.markClean(id, updatedAt) },
        localVersion = { id -> db.bodyWeightQueries.selectById(id).executeAsOneOrNull()?.let { it.updated_at to it.dirty.bool() } },
        writeClean = {
            db.bodyWeightQueries.upsert(it.id, it.measuredAt, it.weight, it.updatedAt, it.deleted.long(), 0)
        },
    ),
)
