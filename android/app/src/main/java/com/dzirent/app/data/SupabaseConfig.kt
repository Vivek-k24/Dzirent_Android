package com.dzirent.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage

/**
 * Supabase Configuration for Dzirent Android App
 *
 * Put your SUPABASE_URL and SUPABASE_ANON_KEY below.
 */
object SupabaseConfig {
    const val SUPABASE_URL = "https://your-project-id.supabase.co"
    const val SUPABASE_ANON_KEY = "your-anon-key-here"

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_ANON_KEY
        ) {
            install(Auth)
            install(Postgrest)
            install(Storage)
            install(Realtime)
        }
    }

    val auth get() = client.auth
    val postgrest get() = client.postgrest
    val storage get() = client.storage
    val realtime get() = client.realtime
}
