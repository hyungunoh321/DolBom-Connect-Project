package com.siheung.careconnect.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
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
    private var userSummary: MainUserSummary? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateUserViews(null)
        setupDrawer()
        setupMenuCards()
        setupBackPress()
        resetSavedLoginOnAppStart()
    }

    override fun onResume() {
        super.onResume()
        loadUserSummary()
    }

    private fun setupDrawer() {
        binding.layoutHeader.btnMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.layoutHeader.btnLogin.setOnClickListener {
            if (isLoggedIn()) logout() else navigateTo(LoginActivity::class.java)
        }

        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            when (menuItem.itemId) {
                R.id.nav_home     -> Unit
                R.id.nav_login    -> navigateTo(LoginActivity::class.java)
                R.id.nav_benefits -> navigateIfLoggedIn(BenefitsActivity::class.java)
                R.id.nav_reserve  -> navigateIfLoggedIn(ReservationActivity::class.java)
                R.id.nav_status   -> navigateIfLoggedIn(ReservationStatusActivity::class.java)
                R.id.nav_realtime -> navigateIfLoggedIn(RealtimeActivity::class.java)
                R.id.nav_mypage   -> navigateIfLoggedIn(MyPageActivity::class.java)
            }
            true
        }
    }

    private fun setupMenuCards() {
        binding.layoutCards.cardBenefits.setOnClickListener {
            navigateIfLoggedIn(BenefitsActivity::class.java)
        }
        binding.layoutCards.cardReserve.setOnClickListener {
            navigateIfLoggedIn(ReservationActivity::class.java)
        }
        binding.layoutCards.cardStatus.setOnClickListener {
            navigateIfLoggedIn(ReservationStatusActivity::class.java)
        }
        binding.layoutCards.cardRealtime.setOnClickListener {
            navigateIfLoggedIn(RealtimeActivity::class.java)
        }
    }

    private fun resetSavedLoginOnAppStart() {
        lifecycleScope.launch {
            if (!AppSessionState.hasStartedInCurrentProcess && isLauncherStart()) {
                AppSessionState.hasStartedInCurrentProcess = true
                AppSessionState.isAuthenticatedInCurrentProcess = false
                runCatching { SupabaseClientProvider.client.auth.signOut() }
            }
            loadUserSummary()
        }
    }

    private fun isLauncherStart(): Boolean =
        intent?.action == Intent.ACTION_MAIN &&
            intent?.categories?.contains(Intent.CATEGORY_LAUNCHER) == true

    private fun loadUserSummary() {
        if (!AppSessionState.isAuthenticatedInCurrentProcess) {
            userSummary = null
            updateUserViews(null)
            return
        }

        val userId = SupabaseClientProvider.client.auth.currentUserOrNull()?.id
        if (userId.isNullOrBlank()) {
            AppSessionState.isAuthenticatedInCurrentProcess = false
            userSummary = null
            updateUserViews(null)
            return
        }

        lifecycleScope.launch {
            val summary = try {
                val user = withContext(Dispatchers.IO) {
                    SupabaseClientProvider.client.postgrest["users"]
                        .select(Columns.raw("username")) {
                            filter { eq("id", userId) }
                        }
                        .decodeSingleOrNull<MainUserRow>()
                }

                val children = withContext(Dispatchers.IO) {
                    SupabaseClientProvider.client.postgrest["children"]
                        .select(Columns.raw("income_level")) {
                            filter { eq("parent_id", userId) }
                        }
                        .decodeList<MainChildRow>()
                }

                MainUserSummary(
                    username = user?.username.orEmpty(),
                    childCount = children.size,
                    incomeLevel = children.mapNotNull { it.incomeLevel }.minOrNull()
                )
            } catch (_: Exception) {
                null
            }

            userSummary = summary
            updateUserViews(summary)
        }
    }

    private fun updateUserViews(summary: MainUserSummary?) {
        val drawerHeader = binding.navigationView.getHeaderView(0)
        val drawerUserName = drawerHeader.findViewById<TextView>(R.id.tvDrawerUserName)
        val drawerUserInfo = drawerHeader.findViewById<TextView>(R.id.tvDrawerUserInfo)

        if (summary == null) {
            binding.layoutHeader.btnLogin.text = "로그인"
            binding.layoutHero.tvUserName.text = "로그인 해주세요"
            binding.layoutHero.tvUserBadge.visibility = View.GONE
            drawerUserName.text = "로그인 해주세요"
            drawerUserInfo.visibility = View.GONE
            return
        }

        val displayName = summary.username.ifBlank { "보호자" }
        val badgeText = summary.conditionText()
        binding.layoutHeader.btnLogin.text = "로그아웃"
        binding.layoutHero.tvUserName.text = "${displayName}님"
        binding.layoutHero.tvUserBadge.text = badgeText
        binding.layoutHero.tvUserBadge.visibility = View.VISIBLE
        drawerUserName.text = "${displayName}님"
        drawerUserInfo.text = "시흥시 · 자녀 ${summary.childCount}명"
        drawerUserInfo.visibility = View.VISIBLE
    }

    private fun isLoggedIn(): Boolean =
        AppSessionState.isAuthenticatedInCurrentProcess &&
            SupabaseClientProvider.client.auth.currentUserOrNull() != null

    private fun <T> navigateIfLoggedIn(destination: Class<T>) {
        if (!isLoggedIn()) {
            showLoginRequired()
            return
        }
        navigateTo(destination)
    }

    private fun showLoginRequired() {
        Toast.makeText(this, "로그인 해주세요", Toast.LENGTH_SHORT).show()
    }

    private fun logout() {
        lifecycleScope.launch {
            AppSessionState.isAuthenticatedInCurrentProcess = false
            runCatching { SupabaseClientProvider.client.auth.signOut() }
            userSummary = null
            updateUserViews(null)
            Toast.makeText(this@MainActivity, "로그아웃되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

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

private data class MainUserSummary(
    val username: String,
    val childCount: Int,
    val incomeLevel: Int?
) {
    fun conditionText(): String {
        val incomeText = incomeLevel?.let { "소득분위 $it" } ?: "소득분위 미등록"
        return "자녀 ${childCount}명 · $incomeText"
    }
}

@Serializable
internal data class MainUserRow(val username: String = "")

@Serializable
internal data class MainChildRow(
    @SerialName("income_level") val incomeLevel: Int? = null
)
