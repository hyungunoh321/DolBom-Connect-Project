package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.databinding.ActivitySignUpBinding

class SignUpActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySignUpBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 비밀번호 재확인 실시간 검사
        binding.etPasswordConfirm.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val pw = binding.etPassword.text.toString()
                val pwConfirm = s.toString()
                if (pwConfirm.isNotEmpty() && pw != pwConfirm) {
                    binding.tvPasswordError.visibility = android.view.View.VISIBLE
                } else {
                    binding.tvPasswordError.visibility = android.view.View.GONE
                }
            }
        })

        // 가입 완료 버튼 클릭
        binding.btnSignUp.setOnClickListener {
            if (validateForm()) {
                val email      = binding.etEmail.text.toString().trim()
                val childName  = binding.etChildName.text.toString().trim()
                val birthDate  = binding.etChildBirthDate.text.toString().trim()
                val gender     = if (binding.rbMale.isChecked) "남아" else "여아"
                val note       = binding.etChildNote.text.toString().trim()

                // TODO: Supabase Auth signUp(email, password) 연동
                //       children 테이블 insert (name, birth_date, gender, note)
                Toast.makeText(this, "회원가입 완료!\n$email 님 환영합니다.", Toast.LENGTH_SHORT).show()

                // 로그인 화면으로 복귀
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
                finish()
            }
        }

        // 상단 뒤로가기 버튼
        binding.btnBack.setOnClickListener {
            finish()
        }

    }

    private fun validateForm(): Boolean {
        val email         = binding.etEmail.text.toString().trim()
        val password      = binding.etPassword.text.toString().trim()
        val passwordConfirm = binding.etPasswordConfirm.text.toString().trim()
        val childName     = binding.etChildName.text.toString().trim()
        val birthDate     = binding.etChildBirthDate.text.toString().trim()
        val isGenderSelected = binding.rbMale.isChecked || binding.rbFemale.isChecked

        // 이메일 검사
        if (email.isEmpty()) {
            binding.etEmail.error = "이메일을 입력해주세요."
            binding.etEmail.requestFocus()
            return false
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "올바른 이메일 형식이 아닙니다."
            binding.etEmail.requestFocus()
            return false
        }

        // 비밀번호 검사
        if (password.isEmpty()) {
            binding.etPassword.error = "비밀번호를 입력해주세요."
            binding.etPassword.requestFocus()
            return false
        }
        if (password.length < 8) {
            binding.etPassword.error = "비밀번호는 8자 이상이어야 합니다."
            binding.etPassword.requestFocus()
            return false
        }

        // 비밀번호 재확인
        if (passwordConfirm.isEmpty()) {
            binding.etPasswordConfirm.error = "비밀번호를 다시 입력해주세요."
            binding.etPasswordConfirm.requestFocus()
            return false
        }
        if (password != passwordConfirm) {
            binding.etPasswordConfirm.error = "비밀번호가 일치하지 않습니다."
            binding.etPasswordConfirm.requestFocus()
            return false
        }

        // 자녀 이름
        if (childName.isEmpty()) {
            binding.etChildName.error = "자녀 이름을 입력해주세요."
            binding.etChildName.requestFocus()
            return false
        }

        // 자녀 생년월일
        if (birthDate.isEmpty()) {
            binding.etChildBirthDate.error = "자녀 생년월일을 입력해주세요."
            binding.etChildBirthDate.requestFocus()
            return false
        }
        if (!birthDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            binding.etChildBirthDate.error = "YYYY-MM-DD 형식으로 입력해주세요."
            binding.etChildBirthDate.requestFocus()
            return false
        }

        // 성별 선택
        if (!isGenderSelected) {
            Toast.makeText(this, "자녀 성별을 선택해주세요.", Toast.LENGTH_SHORT).show()
            return false
        }

        // 필수 약관 동의
        if (!binding.cbAgreement.isChecked) {
            Toast.makeText(this, "이용약관 및 개인정보 처리방침에 동의해주세요.", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!binding.cbPrivacy.isChecked) {
            Toast.makeText(this, "자녀 정보 수집·이용에 동의해주세요.", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }
}