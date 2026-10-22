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
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/**
 * Supabase Configuration for Dzirent Android App
 *
 * SUPABASE_URL and SUPABASE_ANON_KEY are loaded from local.properties via BuildConfig.
 */
@OptIn(ExperimentalSerializationApi::class)
object SupabaseConfig {
    val SUPABASE_URL: String = BuildConfig.SUPABASE_URL
    val SUPABASE_ANON_KEY: String = BuildConfig.SUPABASE_ANON_KEY

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_ANON_KEY
        ) {
            // ignoreUnknownKeys: extra DB columns never break decoding.
            // coerceInputValues: a NULL column falls back to the model's default value.
            // encodeDefaults = false: inserts/updates never send app-only fields
            //   (photos, lender, distanceKm) or null ids/timestamps the DB fills in.
            defaultSerializer = KotlinXSerializer(
                Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                    encodeDefaults = false
                }
            )
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
