package com.siheung.careconnect.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import com.siheung.careconnect.R
import com.siheung.careconnect.benefits.BenefitsActivity
import com.siheung.careconnect.databinding.ActivityMainBinding
import com.siheung.careconnect.login.LoginActivity
import com.siheung.careconnect.login.SupabaseClientProvider
import com.siheung.careconnect.mypage.MyPageActivity
import com.siheung.careconnect.realtime.RealtimeActivity
import com.siheung.careconnect.reservation.ReservationActivity
import com.siheung.careconnect.reservation.ReservationStatusActivity
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setupDrawer()
        setupMenuCards()
        setupBackPress()
    }

    override fun onResume() {
        super.onResume()
        updateUserInfo()
    }

    // ── 로그인 정보 반영 (Supabase에서 최신 정보 직접 조회) ──────────
    private fun updateUserInfo() {
        val userId = SupabaseClientProvider.client.auth.currentUserOrNull()?.id

        val drawerHeader = binding.navigationView.getHeaderView(0)
        val tvDrawerUserName = drawerHeader.findViewById<TextView>(R.id.tvDrawerUserName)
        val tvDrawerUserInfo = drawerHeader.findViewById<TextView>(R.id.tvDrawerUserInfo)

        if (userId == null) {
            binding.layoutHero.tvUserName.text = "시흥시 보호자님"
            binding.layoutHero.tvUserBadge.visibility = View.GONE
            binding.layoutHeader.btnLogin.text = "로그인"
            tvDrawerUserName.text = "게스트"
            tvDrawerUserInfo.text = "로그인 후 이용 가능합니다"
            return
        }

        lifecycleScope.launch {
            val user = runCatching {
                withContext(Dispatchers.IO) {
                    SupabaseClientProvider.client.postgrest["users"]
                        .select(Columns.raw("username")) { filter { eq("id", userId) } }
                        .decodeSingleOrNull<MainUserRow>()
                }
            }.getOrNull()

            val children = runCatching {
                withContext(Dispatchers.IO) {
                    SupabaseClientProvider.client.postgrest["children"]
                        .select(Columns.raw("income_level")) { filter { eq("parent_id", userId) } }
                        .decodeList<MainChildRow>()
                }
            }.getOrElse { emptyList() }

            val username = user?.username?.ifBlank { null }
            val childrenCount = children.size
            val incomeLevel = children.mapNotNull { it.incomeLevel }.minOrNull()

            if (username != null) {
                binding.layoutHero.tvUserName.text = "${username}님"
                val badgeText = when {
                    incomeLevel != null -> "자녀 ${childrenCount}명 · 소득분위 ${incomeLevel}"
                    childrenCount > 0   -> "자녀 ${childrenCount}명"
                    else                -> null
                }
                if (badgeText != null) {
                    binding.layoutHero.tvUserBadge.text = badgeText
                    binding.layoutHero.tvUserBadge.visibility = View.VISIBLE
                } else {
                    binding.layoutHero.tvUserBadge.visibility = View.GONE
                }
                binding.layoutHeader.btnLogin.text = "${username}님"
                tvDrawerUserName.text = "${username}님"
                tvDrawerUserInfo.text = if (childrenCount > 0) "시흥시 · 자녀 ${childrenCount}명" else "시흥시"
            } else {
                binding.layoutHero.tvUserName.text = "시흥시 보호자님"
                binding.layoutHero.tvUserBadge.visibility = View.GONE
                binding.layoutHeader.btnLogin.text = "로그인"
                tvDrawerUserName.text = "게스트"
                tvDrawerUserInfo.text = "로그인 후 이용 가능합니다"
            }
        }
    }

    // ── 사이드 드로어 설정 ──────────────────────────────────────
    private fun setupDrawer() {
        binding.layoutHeader.btnMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
        binding.layoutHeader.btnLogin.setOnClickListener {
            navigateTo(LoginActivity::class.java)
        }
        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            when (menuItem.itemId) {
                R.id.nav_home -> { /* 현재 화면 */ }
                R.id.nav_benefits -> navigateTo(BenefitsActivity::class.java)
                R.id.nav_reserve -> navigateTo(ReservationActivity::class.java)
                R.id.nav_login -> navigateTo(LoginActivity::class.java)
                R.id.nav_status -> navigateTo(ReservationStatusActivity::class.java)
                R.id.nav_realtime -> navigateTo(RealtimeActivity::class.java)
                R.id.nav_mypage -> navigateTo(MyPageActivity::class.java)
            }
            true
        }
    }

    // ── 4개 메뉴 카드 클릭 이벤트 ──────────────────────────────
    private fun setupMenuCards() {
        binding.layoutCards.cardBenefits.setOnClickListener {
            navigateTo(BenefitsActivity::class.java)
        }
        binding.layoutCards.cardReserve.setOnClickListener {
            navigateTo(ReservationActivity::class.java)
        }
        binding.layoutCards.cardStatus.setOnClickListener {
            navigateTo(ReservationStatusActivity::class.java)
        }
        binding.layoutCards.cardRealtime.setOnClickListener {
            navigateTo(RealtimeActivity::class.java)
        }
    }

    // ── 화면 전환 헬퍼 ────────────────────────────────────────
    private fun <T> navigateTo(destination: Class<T>) {
        startActivity(Intent(this, destination))
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }
}

@Serializable
private data class MainUserRow(val username: String = "")

@Serializable
private data class MainChildRow(
    @SerialName("income_level") val incomeLevel: Int? = null
)