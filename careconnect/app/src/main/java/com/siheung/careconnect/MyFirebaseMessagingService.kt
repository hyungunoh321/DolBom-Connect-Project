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

    companion object {
        const val CHANNEL_ID = "careconnect_channel"

        fun createNotificationChannel(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "CareConnect 알림", NotificationManager.IMPORTANCE_HIGH)
                )
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // notification 페이로드 (앱 포그라운드 상태일 때)
        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "알림"
        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: ""

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
        createNotificationChannel(this)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }
}
