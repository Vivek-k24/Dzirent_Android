package com.dzirent.app.data

import com.dzirent.app.BuildConfig
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
 * SUPABASE_URL and SUPABASE_ANON_KEY are loaded from local.properties via BuildConfig.
 */
object SupabaseConfig {
    val SUPABASE_URL: String = BuildConfig.SUPABASE_URL
    val SUPABASE_ANON_KEY: String = BuildConfig.SUPABASE_ANON_KEY

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
