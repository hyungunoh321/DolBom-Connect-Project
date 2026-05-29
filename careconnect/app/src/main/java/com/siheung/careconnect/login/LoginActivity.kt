package com.siheung.careconnect.login

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.siheung.careconnect.databinding.ActivityLoginBinding
import com.siheung.careconnect.facilityadmin.AdminMainActivity
import com.siheung.careconnect.main.MainActivity
import com.siheung.careconnect.systemadmin.SystemAdminActivity
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            binding.tvLoginError.visibility = View.GONE

            when {
                username.isEmpty() -> {
                    binding.etUsername.error = "아이디를 입력해주세요."
                    binding.etUsername.requestFocus()
                }
                password.isEmpty() -> {
                    binding.etPassword.error = "비밀번호를 입력해주세요."
                    binding.etPassword.requestFocus()
                }
                else -> {
                    binding.btnLogin.isEnabled = false

                    lifecycleScope.launch {
                        try {
                            // SharedPreferences 캐시에서 email 우선 조회
                            // (앱 재실행 시 Supabase RLS로 인해 비인증 상태에서 DB 조회가 막힐 수 있음)
                            val prefs = getSharedPreferences("auth_cache", Context.MODE_PRIVATE)
                            val cachedEmail = prefs.getString("email_$username", null)

                            val loginEmail = if (cachedEmail != null) {
                                cachedEmail
                            } else {
                                val emailRow = SupabaseClientProvider.client.postgrest["users"]
                                    .select(Columns.raw("email")) {
                                        filter { eq("username", username) }
                                    }
                                    .decodeSingleOrNull<EmailRow>()

                                if (emailRow == null) {
                                    showError("아이디 또는 비밀번호가 잘못되었습니다.")
                                    binding.btnLogin.isEnabled = true
                                    return@launch
                                }
                                emailRow.email
                            }

                            SupabaseClientProvider.client.auth.signInWith(Email) {
                                this.email    = loginEmail
                                this.password = password
                            }

                            // 로그인 성공 시 email 캐시 저장 (다음 로그인부터 DB 조회 불필요)
                            prefs.edit().putString("email_$username", loginEmail).apply()

                            // 로그인 성공 → users 테이블에서 role 조회
                            val userId = SupabaseClientProvider.client.auth.currentUserOrNull()?.id ?: ""
                            val userRow = SupabaseClientProvider.client.postgrest["users"]
                                .select(Columns.raw("role")) {
                                    filter { eq("id", userId) }
                                }
                                .decodeSingleOrNull<UserRow>()

                            if (userRow == null) {
                                // 회원가입은 됐지만 users 테이블에 데이터가 없는 경우
                                // (이메일 인증 미완료 등 회원가입이 중간에 실패한 계정)
                                SupabaseClientProvider.client.auth.signOut()
                                showError("계정 정보를 찾을 수 없습니다.\n다시 회원가입해 주세요.")
                                binding.btnLogin.isEnabled = true
                                return@launch
                            }

                            Toast.makeText(this@LoginActivity, "로그인 성공", Toast.LENGTH_SHORT).show()

                            // FCM 토큰 DB 저장
                            val fcmToken = getSharedPreferences("careconnect_prefs", Context.MODE_PRIVATE)
                                .getString("fcm_token", null)
                            if (fcmToken != null) {
                                try {
                                    withContext(Dispatchers.IO) {
                                        SupabaseClientProvider.client.postgrest["users"].update(
                                            buildJsonObject { put("fcm_token", fcmToken) }
                                        ) { filter { eq("id", userId) } }
                                    }
                                } catch (_: Exception) { }
                            }

                            val intent = when (userRow.role) {
                                "관리자" -> {
                                    // facilities 테이블에서 manager_id로 facility_id 조회
                                    // DB facilities 컬럼: id, name, address, latitude, longitude, capacity, manager_id, created_at
                                    val facilityRow = SupabaseClientProvider.client.postgrest["facilities"]
                                        .select(Columns.raw("id")) {
                                            filter { eq("manager_id", userId) }
                                        }
                                        .decodeSingleOrNull<FacilityRow>()

                                    Intent(this@LoginActivity, AdminMainActivity::class.java).apply {
                                        putExtra("facility_id", facilityRow?.id ?: "")
                                    }
                                }
                                // TODO: 시스템 관리자 화면 연결 (팀원 작업 완료 후 주석 해제)
                                "시스템관리자" -> Intent(this@LoginActivity, SystemAdminActivity::class.java)
                                else -> {
                                    val childrenRows = SupabaseClientProvider.client.postgrest["children"]
                                        .select(Columns.raw("income_level")) {
                                            filter { eq("parent_id", userId) }
                                        }
                                        .decodeList<ChildRow>()
                                    Intent(this@LoginActivity, MainActivity::class.java).apply {
                                        putExtra("username", username)
                                        putExtra("children_count", childrenRows.size)
                                        childrenRows.firstOrNull()?.incomeLevel
                                            ?.let { putExtra("income_level", it) }
                                    }
                                }
                            }
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        } catch (e: Exception) {
                            val msg = e.message ?: ""
                            when {
                                msg.contains("Invalid login credentials") -> {
                                    showError("아이디 또는 비밀번호가 올바르지 않습니다.")
                                }
                                msg.contains("Email not confirmed") -> {
                                    showError("이메일 인증이 필요합니다. 메일함을 확인해주세요.")
                                }
                                else -> {
                                    showError("로그인에 실패했습니다. 다시 시도해주세요.")
                                }
                            }
                        } finally {
                            binding.btnLogin.isEnabled = true
                        }
                    }
                }
            }
        }

        binding.tvGoToSignUp.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun showError(message: String) {
        binding.tvLoginError.text = message
        binding.tvLoginError.visibility = View.VISIBLE
    }
}

// DB users 테이블: email 조회용
@Serializable
private data class EmailRow(
    val email: String
)

// DB users 테이블: role 조회용
@Serializable
private data class UserRow(
    val role: String
)

// DB facilities 테이블: manager_id로 facility id 조회용
@Serializable
private data class FacilityRow(
    val id: String
)

// DB children 테이블: 자녀 수 및 소득분위 조회용
@Serializable
private data class ChildRow(
    @SerialName("income_level") val incomeLevel: Int? = null
)
