package com.siheung.careconnect

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
            try {
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
                conn.doOutput = true

                val json = Json.encodeToString(
                    buildJsonObject {
                        put("token", fcmToken)
                        put("title", title)
                        put("body", body)
                    }
                )
                conn.outputStream.use { it.write(json.toByteArray()) }

                val code = conn.responseCode
                if (code !in 200..299) {
                    val error = conn.errorStream?.bufferedReader()?.readText() ?: "unknown"
                    throw RuntimeException("FCM 전송 실패 ($code): $error")
                }
            } finally {
                conn.disconnect()
            }
        }
    }
}
