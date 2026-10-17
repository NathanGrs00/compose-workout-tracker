package repository.sync

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonObject

/**
 * [RemoteStore] backed by Supabase (PostgREST). See supabase/schema.sql for the tables.
 */
class SupabaseRemoteStore(url: String, key: String) : RemoteStore {
    private val client = createSupabaseClient(supabaseUrl = url, supabaseKey = key) {
        install(Postgrest)
    }

    override suspend fun upsert(table: String, rows: List<JsonObject>) {
        if (rows.isEmpty()) return
        client.from(table).upsert(rows)
    }

    override suspend fun fetchChangedSince(table: String, since: Long, offset: Int, limit: Int): List<JsonObject> =
        client.from(table).select {
            filter { gt("server_updated_at", since) }
            order("server_updated_at", Order.ASCENDING)
            order("id", Order.ASCENDING)
            range(offset.toLong(), (offset + limit - 1).toLong())
        }.decodeList<JsonObject>()

    override suspend fun close() {
        runCatching { client.close() }
    }
}
