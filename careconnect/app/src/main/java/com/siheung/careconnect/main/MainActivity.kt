package com.siheung.careconnect.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import com.siheung.careconnect.R
import com.siheung.careconnect.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDrawer()
        setupMenuCards()
        setupLoginButton()
        setupNoticeSection()
    }

    // ── 사이드 드로어 설정 ──────────────────────────────────────
    private fun setupDrawer() {
        //val onClickListener = binding.btnMenu.setOnClickListener {
        //    binding.drawerLayout.openDrawer(GravityCompat.START)
        //}

        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            when (menuItem.itemId) {
                R.id.nav_home -> { /* 현재 화면 */ }
                //R.id.nav_benefits -> navigateTo(BenefitsActivity::class.java)
                //R.id.nav_reserve -> navigateTo(ReservationActivity::class.java)
                //R.id.nav_status -> navigateTo(ReservationStatusActivity::class.java)
                //R.id.nav_realtime -> navigateTo(RealtimeActivity::class.java)
            }
            true
        }
    }

    // ── 4개 메뉴 카드 클릭 이벤트 ──────────────────────────────
    private fun setupMenuCards() {
        binding.layoutCards.cardBenefits.setOnClickListener {
            //navigateTo(BenefitsActivity::class.java)
        }
        binding.layoutCards.cardReserve.setOnClickListener {
            //navigateTo(ReservationActivity::class.java)
        }
        binding.layoutCards.cardStatus.setOnClickListener {
            //navigateTo(ReservationStatusActivity::class.java)
        }
        binding.layoutCards.cardRealtime.setOnClickListener {
            //navigateTo(RealtimeActivity::class.java)
        }
    }

    // ── 로그인 버튼 ────────────────────────────────────────────
    private fun setupLoginButton() {
        //binding.btnLogin.setOnClickListener {
            // navigateTo(LoginActivity::class.java)
        //}
    }

    // ── 공지사항 더보기 ────────────────────────────────────────
    private fun setupNoticeSection() {
        //binding.tvNoticeMore.setOnClickListener {
            // 공지사항 목록 화면으로 이동 (추후 구현)
        //}
    }

    // ── 화면 전환 헬퍼 ────────────────────────────────────────
    private fun <T> navigateTo(destination: Class<T>) {
        startActivity(Intent(this, destination))
    }

    // 뒤로 가기 시 드로어가 열려 있으면 닫기
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}