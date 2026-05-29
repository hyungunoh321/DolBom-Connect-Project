package com.siheung.careconnect.facilityadmin

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import com.siheung.careconnect.databinding.ActivityAdminMainBinding

class AdminMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminMainBinding
    private var facilityId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        facilityId = intent.getStringExtra("facility_id") ?: ""

        setupToolbar()
        setupMenuCards()
    }

    // ── 툴바 (뒤로가기) ──────────────────────────────────────
    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { finish() }
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