package com.siheung.careconnect.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import com.siheung.careconnect.R
import com.siheung.careconnect.benefits.BenefitsActivity
import com.siheung.careconnect.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDrawer()
        setupMenuCards()
    }

    // ── 사이드 드로어 설정 ──────────────────────────────────────
    private fun setupDrawer() {
        binding.layoutHeader.btnMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            when (menuItem.itemId) {
                R.id.nav_home -> { /* 현재 화면 */ }
                R.id.nav_benefits -> navigateTo(BenefitsActivity::class.java)
            }
            true
        }
    }

    // ── 4개 메뉴 카드 클릭 이벤트 ──────────────────────────────
    private fun setupMenuCards() {
        binding.layoutCards.cardBenefits.setOnClickListener {
            navigateTo(BenefitsActivity::class.java)
        }
        // 나머지 카드는 추후 구현
        binding.layoutCards.cardReserve.setOnClickListener { }
        binding.layoutCards.cardStatus.setOnClickListener { }
        binding.layoutCards.cardRealtime.setOnClickListener { }
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