package com.kalazacare.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

// The anon key is safe to embed client-side — every request it makes is still
// subject to the Row Level Security policies in supabase/seed.sql, exactly
// like Firebase's client config before it.
private const val SUPABASE_URL = "https://acafqfjpbilakvnaxamb.supabase.co"
private const val SUPABASE_ANON_KEY =
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFjYWZxZmpwYmlsYWt2bmF4YW1iIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODQ3MTUxNjksImV4cCI6MjEwMDI5MTE2OX0.pap7s1NvO0gESSGxtDn8ufk3ffv-xA2nMuUN8ruptRY"

object SupabaseClients {
    val main: SupabaseClient by lazy {
        createSupabaseClient(supabaseUrl = SUPABASE_URL, supabaseKey = SUPABASE_ANON_KEY) {
            install(Auth)
            install(Postgrest)
            install(Storage)
            install(Realtime)
        }
    }

    /**
     * A second, independent client used only for creating new staff accounts.
     * Supabase Auth's signUpWith(Email) signs the caller in as whichever user
     * just signed up, on that client instance — running staff creation here
     * keeps the Super Admin signed in on [main] throughout (mirrors the old
     * secondary-FirebaseAuth-instance trick).
     */
    val staffCreation: SupabaseClient by lazy {
        createSupabaseClient(supabaseUrl = SUPABASE_URL, supabaseKey = SUPABASE_ANON_KEY) {
            install(Auth)
        }
    }
}
