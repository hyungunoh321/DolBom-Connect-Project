package com.example.myapplication

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClientProvider {
    val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = "https://xvdapkiyogxdvdhngnmh.supabase.co",
        supabaseKey = "sb_publishable_n1wemOA-TqY6mNhv3hdo6g_t85vclp4"
    ) {
        install(Auth)
        install(Postgrest)
    }
}