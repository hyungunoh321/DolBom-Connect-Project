package com.siheung.careconnect.facilityadmin

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import com.siheung.careconnect.databinding.ActivityAdminMainBinding

class AdminMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
            startActivity(Intent(this, AdminScheduleActivity::class.java))
        }
        binding.cardReservation.setOnClickListener {
            startActivity(Intent(this, AdminReservationActivity::class.java))
        }
        binding.cardNotification.setOnClickListener {
            startActivity(Intent(this, AdminNotificationActivity::class.java))
        }
    }
}