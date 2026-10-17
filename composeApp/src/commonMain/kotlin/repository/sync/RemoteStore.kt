package repository.sync

import kotlinx.serialization.json.JsonObject

/**
 * The cloud side of sync. Rows are plain JSON objects so the same logic works for every table.
 */
interface RemoteStore {
    /** Inserts or updates rows by primary key `id`. */
    suspend fun upsert(table: String, rows: List<JsonObject>)

    /** Rows whose `server_updated_at` is greater than [since], ordered by it, one page at a time. */
    suspend fun fetchChangedSince(table: String, since: Long, offset: Int, limit: Int): List<JsonObject>

    suspend fun close() {}
}
