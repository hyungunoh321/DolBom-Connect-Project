package com.siheung.careconnect

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import com.siheung.careconnect.BuildConfig

object NotificationHelper {

    private const val SUPABASE_URL = "https://xvdapkiyogxdvdhngnmh.supabase.co"
    private const val SUPABASE_ANON_KEY = BuildConfig.SUPABASE_ANON_KEY


    suspend fun sendNotification(fcmToken: String, title: String, body: String) {
        withContext(Dispatchers.IO) {
            val url = URL("$SUPABASE_URL/functions/v1/send-notification")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
            conn.doOutput = true

            val json = """{"token":"$fcmToken","title":"$title","body":"$body"}"""
            conn.outputStream.write(json.toByteArray())
            conn.responseCode
        }
    }
}