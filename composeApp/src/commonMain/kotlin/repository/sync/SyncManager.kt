package repository.sync

import db.WorkoutDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import repository.SettingsStore
import repository.nowMillis

enum class SyncStatus { NotConfigured, Idle, Syncing, Offline, Error }

data class SyncState(
    val status: SyncStatus = SyncStatus.NotConfigured,
    val lastSyncedAt: Long? = null,
    val message: String? = null,
)

/**
 * Two-way sync between the local database and Supabase.
 *
 * 1. Push: upload every dirty local row (upsert by id), then mark it clean unless it was edited meanwhile.
 * 2. Pull: download rows changed on the server since the last pull and apply them,
 *    unless the local copy has a newer unsynced edit.
 *
 * Runs shortly after local edits, periodically, and on demand.
 */
class SyncManager(
    private val db: WorkoutDatabase,
    private val settings: SettingsStore,
    private val localChanges: Flow<Unit>,
    private val scope: CoroutineScope,
    private val remoteFactory: (url: String, key: String) -> RemoteStore = ::SupabaseRemoteStore,
    private val clock: () -> Long = ::nowMillis,
) {
    private val tables = syncTables(db)
    private val mutex = Mutex()
    private val manualRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var remote: RemoteStore? = null
    private var remoteConfig: Pair<String, String>? = null
    private var jobs: List<Job> = emptyList()

    private val _state = MutableStateFlow(
        SyncState(
            status = if (settings.isSyncConfigured) SyncStatus.Idle else SyncStatus.NotConfigured,
            lastSyncedAt = settings.lastSyncedAt,
        )
    )
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /** Starts background syncing: on launch, ~2s after local edits, and every [intervalMillis]. */
    @OptIn(FlowPreview::class)
    fun start(intervalMillis: Long = 2 * 60_000L) {
        if (jobs.isNotEmpty()) return
        jobs = listOf(
            scope.launch {
                merge(localChanges.debounce(2_000), manualRequests).collect { syncNow() }
            },
            scope.launch {
                while (true) {
                    syncNow()
                    delay(intervalMillis)
                }
            },
        )
    }

    /** Asks for a sync soon (e.g. when the app comes back to the foreground). */
    fun requestSync() {
        manualRequests.tryEmit(Unit)
    }

    /** Saves new Supabase credentials and syncs everything with the new backend. */
    fun reconfigure(url: String, key: String) {
        settings.saveSupabase(url, key)
        _state.update { it.copy(status = if (settings.isSyncConfigured) SyncStatus.Idle else SyncStatus.NotConfigured, message = null) }
        requestSync()
    }

    /** Runs one full push + pull. Safe to call concurrently; calls are serialized. */
    suspend fun syncNow(): Boolean = mutex.withLock {
        if (!settings.isSyncConfigured) {
            _state.update { it.copy(status = SyncStatus.NotConfigured, message = null) }
            return false
        }
        _state.update { it.copy(status = SyncStatus.Syncing) }
        try {
            val remote = remoteFor(settings.supabaseUrl, settings.supabaseKey)
            tables.forEach { push(remote, it) }
            pull(remote)
            val now = clock()
            settings.lastSyncedAt = now
            _state.value = SyncState(SyncStatus.Idle, lastSyncedAt = now)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val offline = e.isNetworkError()
            _state.update {
                it.copy(
                    status = if (offline) SyncStatus.Offline else SyncStatus.Error,
                    message = when {
                        offline -> "No connection, changes are saved on this device"
                        e.isOutdatedSchema() -> "Supabase tables are out of date: run supabase/schema.sql again in the SQL editor"
                        else -> (e.message ?: e.toString()).take(300)
                    },
                )
            }
            false
        }
    }

    private suspend fun remoteFor(url: String, key: String): RemoteStore {
        val config = url to key
        remote?.let { if (remoteConfig == config) return it else it.close() }
        return remoteFactory(url, key).also {
            remote = it
            remoteConfig = config
        }
    }

    private suspend fun <T> push(remote: RemoteStore, table: SyncTable<T>) {
        val dirty = table.dirtyRows()
        dirty.chunked(PAGE_SIZE).forEach { chunk ->
            val json = chunk.map { row ->
                JsonObject(json.encodeToJsonElement(table.serializer, row).jsonObject - "server_updated_at")
            }
            remote.upsert(table.remoteName, json)
            db.transaction {
                chunk.forEach { table.markClean(table.id(it), table.updatedAt(it)) }
            }
        }
    }

    private suspend fun pull(remote: RemoteStore) {
        // Re-read a small overlap window so rows committed slightly out of order aren't missed.
        val since = (settings.lastPullServerTime - PULL_OVERLAP_MILLIS).coerceAtLeast(0)
        var maxSeen = settings.lastPullServerTime
        tables.forEach { table ->
            maxSeen = maxOf(maxSeen, pullTable(remote, table, since))
        }
        settings.lastPullServerTime = maxSeen
    }

    private suspend fun <T> pullTable(remote: RemoteStore, table: SyncTable<T>, since: Long): Long {
        var maxSeen = 0L
        var offset = 0
        while (true) {
            val page = remote.fetchChangedSince(table.remoteName, since, offset, PAGE_SIZE)
                .map { json.decodeFromJsonElement(table.serializer, it) }
            db.transaction {
                page.forEach { row -> applyRemote(table, row) }
            }
            page.forEach { row -> maxSeen = maxOf(maxSeen, table.serverUpdatedAt(row) ?: 0L) }
            if (page.size < PAGE_SIZE) break
            offset += PAGE_SIZE
        }
        return maxSeen
    }

    private fun <T> applyRemote(table: SyncTable<T>, row: T) {
        val local = table.localVersion(table.id(row))
        val keepLocal = local != null && local.second && local.first > table.updatedAt(row)
        if (!keepLocal) table.writeClean(row)
    }

    companion object {
        const val PAGE_SIZE = 500
        const val PULL_OVERLAP_MILLIS = 5 * 60_000L

        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

private fun Throwable.isNetworkError(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        val name = current::class.simpleName.orEmpty()
        if (name in NETWORK_EXCEPTIONS) return true
        current = current.cause
    }
    return false
}

/** PostgREST errors for a missing table/column, i.e. schema.sql hasn't been re-run after an app update. */
private fun Throwable.isOutdatedSchema(): Boolean {
    val text = (message ?: "") + (cause?.message ?: "")
    return listOf("schema cache", "does not exist", "PGRST204", "PGRST205", "42P01", "42703").any { it in text }
}

private val NETWORK_EXCEPTIONS = setOf(
    "UnknownHostException", "ConnectException", "SocketTimeoutException", "NoRouteToHostException",
    "HttpRequestTimeoutException", "ConnectTimeoutException", "HttpRequestException", "UnresolvedAddressException",
    "SocketException", "SSLException",
)
