package repository

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClient {
    private val SUPABASE_URL = System.getProperty("supabase.url") ?: error("supabase.url not set")
    private val SUPABASE_KEY = System.getProperty("supabase.key") ?: error("supabase.key not set")

    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_KEY
    ) {
        install(Postgrest)
    }
}