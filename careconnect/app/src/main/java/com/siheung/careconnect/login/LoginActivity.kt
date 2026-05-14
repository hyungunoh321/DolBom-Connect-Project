package com.siheung.careconnect.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.siheung.careconnect.databinding.ActivityLoginBinding
import com.siheung.careconnect.facilityadmin.AdminMainActivity
import com.siheung.careconnect.main.MainActivity
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
                            // TODO: username → email 매핑 쿼리 추가 (현재는 임시로 username을 email로 사용)
                            SupabaseClientProvider.client.auth.signInWith(Email) {
                                this.email    = username
                                this.password = password
                            }

                            // 로그인 성공 → users 테이블에서 role 조회
                            // DB users 컬럼: id, username, password_hash, role, created_at
                            val userId = SupabaseClientProvider.client.auth.currentUserOrNull()?.id ?: ""
                            val userRow = SupabaseClientProvider.client.postgrest["users"]
                                .select(Columns.raw("role")) {
                                    filter { eq("id", userId) }
                                }
                                .decodeSingle<UserRow>()

                            Toast.makeText(this@LoginActivity, "로그인 성공", Toast.LENGTH_SHORT).show()

                            val intent = when (userRow.role) {
                                "보육원관리자" -> {
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
                                // "시스템관리자" -> Intent(this@LoginActivity, com.siheung.careconnect.system.SystemMainActivity::class.java)
                                else -> Intent(this@LoginActivity, MainActivity::class.java)
                            }
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()

                        } catch (e: Exception) {
                            val msg = e.message ?: ""
                            when {
                                msg.contains("Invalid login credentials") -> {
                                    showError("아이디 또는 비밀번호가 잘못되었습니다.")
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
        binding.tvLoginError.text       = message
        binding.tvLoginError.visibility = View.VISIBLE
    }
}

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