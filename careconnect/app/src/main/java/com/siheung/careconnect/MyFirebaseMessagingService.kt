package com.siheung.careconnect

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.siheung.careconnect.login.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val title = remoteMessage.notification?.title ?: "알림"
        val body = remoteMessage.notification?.body ?: ""
        showNotification(title, body)
    }

    override fun onNewToken(token: String) {
        getSharedPreferences("careconnect_prefs", Context.MODE_PRIVATE)
            .edit().putString("fcm_token", token).apply()

        val userId = SupabaseClientProvider.client.auth.currentUserOrNull()?.id ?: return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                SupabaseClientProvider.client.postgrest["users"].update(
                    buildJsonObject { put("fcm_token", token) }
                ) { filter { eq("id", userId) } }
            } catch (_: Exception) { }
        }
    }

    private fun showNotification(title: String, body: String) {
        val channelId = "careconnect_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId, "CareConnect 알림",
            NotificationManager.IMPORTANCE_HIGH
        )
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .build()

        manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }
}