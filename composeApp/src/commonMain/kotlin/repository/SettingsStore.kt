package repository

import config.AppConfig
import db.WorkoutDatabase

/**
 * Local-only settings. Supabase credentials entered in the app override the build-time
 * defaults from local.properties.
 */
class SettingsStore(private val db: WorkoutDatabase) {
    fun get(key: String): String? = db.settingQueries.get(key).executeAsOneOrNull()
    fun put(key: String, value: String) = db.settingQueries.put(key, value)

    val supabaseUrl: String get() = get(KEY_URL) ?: AppConfig.DEFAULT_SUPABASE_URL
    val supabaseKey: String get() = get(KEY_KEY) ?: AppConfig.DEFAULT_SUPABASE_KEY
    val isSyncConfigured: Boolean get() = supabaseUrl.isNotBlank() && supabaseKey.isNotBlank()

    fun saveSupabase(url: String, key: String) {
        db.transaction {
            put(KEY_URL, url.trim().trimEnd('/'))
            put(KEY_KEY, key.trim())
            // A different backend needs a full download.
            put(KEY_LAST_PULL, "0")
        }
    }

    var lastPullServerTime: Long
        get() = get(KEY_LAST_PULL)?.toLongOrNull() ?: 0L
        set(value) = put(KEY_LAST_PULL, value.toString())

    /** Barbell weight in kg for the plate calculator (local to this device). */
    var barWeight: Double
        get() = get(KEY_BAR_WEIGHT)?.toDoubleOrNull() ?: 20.0
        set(value) = put(KEY_BAR_WEIGHT, value.toString())

    var lastSyncedAt: Long?
        get() = get(KEY_LAST_SYNC)?.toLongOrNull()
        set(value) = put(KEY_LAST_SYNC, value?.toString() ?: "")

    companion object {
        const val KEY_URL = "supabase_url"
        const val KEY_KEY = "supabase_key"
        const val KEY_LAST_PULL = "last_pull_server_time"
        const val KEY_LAST_SYNC = "last_synced_at"
        const val KEY_BAR_WEIGHT = "bar_weight"
    }
}
