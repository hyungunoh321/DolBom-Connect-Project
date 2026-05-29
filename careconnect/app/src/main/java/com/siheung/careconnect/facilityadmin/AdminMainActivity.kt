package com.siheung.careconnect.facilityadmin

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.siheung.careconnect.MyFirebaseMessagingService
import com.siheung.careconnect.databinding.ActivityAdminMainBinding
import com.siheung.careconnect.login.LoginActivity
import com.siheung.careconnect.login.SupabaseClientProvider
import com.siheung.careconnect.main.AppSessionState
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

class AdminMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminMainBinding
    private var facilityId: String = ""

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        facilityId = intent.getStringExtra("facility_id") ?: ""

        requestNotificationPermissionIfNeeded()
        MyFirebaseMessagingService.createNotificationChannel(this)

        setupToolbar()
        setupMenuCards()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ── 툴바 ──────────────────────────────────────────────────
    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnLogout.setOnClickListener {
            lifecycleScope.launch {
                AppSessionState.isAuthenticatedInCurrentProcess = false
                runCatching { SupabaseClientProvider.client.auth.signOut() }
                Toast.makeText(this@AdminMainActivity, "로그아웃되었습니다.", Toast.LENGTH_SHORT).show()
                val intent = Intent(this@AdminMainActivity, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
        }
    }

    // ── 3개 메뉴 카드 클릭 이벤트 ────────────────────────────
    private fun setupMenuCards() {
        binding.cardSchedule.setOnClickListener {
            startActivity(Intent(this, AdminScheduleActivity::class.java).apply {
                putExtra("facility_id", facilityId)
            })
        }
        binding.cardReservation.setOnClickListener {
            startActivity(Intent(this, AdminReservationActivity::class.java).apply {
                putExtra("facility_id", facilityId)
            })
        }
        binding.cardNotification.setOnClickListener {
            startActivity(Intent(this, AdminNotificationActivity::class.java).apply {
                putExtra("facility_id", facilityId)
            })
        }
    }
}