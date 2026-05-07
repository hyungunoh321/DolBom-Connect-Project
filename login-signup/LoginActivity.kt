package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.databinding.ActivityLoginBinding
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
            val email    = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            binding.tvLoginError.visibility = View.GONE

            when {
                email.isEmpty() -> {
                    binding.etEmail.error = "이메일을 입력해주세요."
                    binding.etEmail.requestFocus()
                }
                !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                    binding.etEmail.error = "올바른 이메일 형식이 아닙니다."
                    binding.etEmail.requestFocus()
                }
                password.isEmpty() -> {
                    binding.etPassword.error = "비밀번호를 입력해주세요."
                    binding.etPassword.requestFocus()
                }
                else -> {
                    binding.btnLogin.isEnabled = false

                    lifecycleScope.launch {
                        try {
                            SupabaseClientProvider.client.auth.signInWith(Email) {
                                this.email    = email
                                this.password = password
                            }

                            // 로그인 성공 → TODO: 메인 화면 이동
                            Toast.makeText(this@LoginActivity, "로그인 성공", Toast.LENGTH_SHORT).show()

                        } catch (e: Exception) {
                            val msg = e.message ?: ""
                            when {
                                msg.contains("Invalid login credentials") -> {
                                    showError("존재하지 않는 이메일이거나 비밀번호가 잘못되었습니다.")
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
    }

    private fun showError(message: String) {
        binding.tvLoginError.text       = message
        binding.tvLoginError.visibility = View.VISIBLE
    }
}