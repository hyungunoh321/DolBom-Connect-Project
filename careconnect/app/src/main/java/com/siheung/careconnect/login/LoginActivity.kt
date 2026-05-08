package com.siheung.careconnect.login
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.siheung.careconnect.databinding.ActivityLoginBinding
import com.siheung.careconnect.main.MainActivity
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch

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
                            // Supabase Auth는 이메일 기반 → users 테이블에서 username으로 email 조회 후 로그인
                            // TODO: username → email 매핑 쿼리 추가 (현재는 임시로 username을 email로 사용)
                            SupabaseClientProvider.client.auth.signInWith(Email) {
                                this.email    = username
                                this.password = password
                            }

                            // 로그인 성공 → TODO: 역할(role) 확인 후 화면 분기
                            Toast.makeText(this@LoginActivity, "로그인 성공", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this@LoginActivity, MainActivity::class.java)
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