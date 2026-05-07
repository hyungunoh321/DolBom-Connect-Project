package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 로그인 버튼 클릭
        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

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
                    // TODO: Supabase Auth 로그인 API 연동 및 JWT 세션 저장
                    // TODO: JWT role 필드 기반 메인 화면 분기 이동
                    Toast.makeText(this, "로그인 성공 (임시)", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 회원가입 화면으로 이동
        binding.tvGoToSignUp.setOnClickListener {
            val intent = Intent(this, SignUpActivity::class.java)
            startActivity(intent)
        }
    }
}