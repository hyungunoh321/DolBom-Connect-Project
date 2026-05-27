package com.siheung.careconnect.login

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.siheung.careconnect.databinding.ActivitySignUpBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
                val pw        = binding.etPassword.text.toString()
                val pwConfirm = s.toString()
                binding.tvPasswordError.visibility =
                    if (pwConfirm.isNotEmpty() && pw != pwConfirm) View.VISIBLE else View.GONE
            }
        })

        // 입력완료 버튼
        binding.btnSignUp.setOnClickListener {
            if (validateForm()) {
                val email       = binding.etEmail.text.toString().trim()
                val password    = binding.etPassword.text.toString().trim()
                val username    = binding.etUsername.text.toString().trim()
                val childName   = binding.etChildName.text.toString().trim()
                val birthDate   = binding.etChildBirthDate.text.toString().trim()
                val incomeLevel = binding.etIncomeLevel.text.toString().trim().toIntOrNull()
                // TODO: DB children 테이블에 gender 컬럼 추가 후 아래 주석 해제
                // val gender = if (binding.rbMale.isChecked) "남아" else "여아"

                binding.btnSignUp.isEnabled = false

                lifecycleScope.launch {
                    try {
                        // 1. username 중복 체크
                        val existingUser = SupabaseClientProvider.client.postgrest["users"]
                            .select(Columns.raw("id")) {
                                filter { eq("username", username) }
                            }
                            .decodeSingleOrNull<IdRow>()

                        if (existingUser != null) {
                            binding.etUsername.error = "이미 사용 중인 아이디입니다."
                            binding.etUsername.requestFocus()
                            binding.btnSignUp.isEnabled = true
                            return@launch
                        }

                        // 2. Supabase Auth 회원가입
                        //    이메일 인증 비활성화 시 signUpWith 이후 자동으로 세션이 생성됨
                        SupabaseClientProvider.client.auth.signUpWith(Email) {
                            this.email    = email
                            this.password = password
                        }

                        // 3. 세션 확인 (이메일 인증 비활성화 → 자동 로그인됨)
                        //    이메일 인증이 필요한 경우 currentUserOrNull()이 null을 반환함
                        val userId = SupabaseClientProvider.client.auth.currentUserOrNull()?.id
                        if (userId == null) {
                            Toast.makeText(
                                this@SignUpActivity,
                                "이메일 인증 메일을 보냈습니다. 인증 후 로그인해주세요.",
                                Toast.LENGTH_LONG
                            ).show()
                            startActivity(
                                Intent(this@SignUpActivity, LoginActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                }
                            )
                            finish()
                            return@launch
                        }

                        // 4. users 테이블에 추가 정보 저장
                        // DB users 컬럼: id, username, email, role, created_at
                        SupabaseClientProvider.client.postgrest["users"].insert(
                            buildJsonObject {
                                put("id",       userId)
                                put("username", username)
                                put("email",    email)
                                put("role",     "보호자")
                            }
                        )

                        // 5. children 테이블에 자녀 정보 저장
                        // DB children 컬럼: id, parent_id, name, birth_date, income_level
                        val childJson = buildJsonObject {
                            put("parent_id",  userId)
                            put("name",       childName)
                            put("birth_date", birthDate)
                            if (incomeLevel != null) put("income_level", incomeLevel)
                        }
                        SupabaseClientProvider.client.postgrest["children"].insert(childJson)

                        // 회원가입 완료 후 email 캐시 저장 (재로그인 시 DB 조회 불필요)
                        getSharedPreferences("auth_cache", Context.MODE_PRIVATE)
                            .edit()
                            .putString("email_$username", email)
                            .apply()

                        Toast.makeText(
                            this@SignUpActivity,
                            "회원가입이 완료되었습니다!",
                            Toast.LENGTH_LONG
                        ).show()

                        val intent = Intent(this@SignUpActivity, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        startActivity(intent)
                        finish()

                    } catch (e: Exception) {
                        val msg = e.message ?: ""
                        when {
                            msg.contains("already registered") || msg.contains("already exists") ||
                            msg.contains("User already registered") -> {
                                binding.etEmail.error = "이미 사용 중인 이메일입니다."
                                binding.etEmail.requestFocus()
                            }
                            msg.contains("users_username_key") -> {
                                binding.etUsername.error = "이미 사용 중인 아이디입니다."
                                binding.etUsername.requestFocus()
                            }
                            else -> {
                                Toast.makeText(
                                    this@SignUpActivity,
                                    "오류: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } finally {
                        binding.btnSignUp.isEnabled = true
                    }
                }
            }
        }

        binding.btnBack.setOnClickListener { finish() }
    }

    private fun validateForm(): Boolean {
        val username        = binding.etUsername.text.toString().trim()
        val email           = binding.etEmail.text.toString().trim()
        val password        = binding.etPassword.text.toString().trim()
        val passwordConfirm = binding.etPasswordConfirm.text.toString().trim()
        val childName       = binding.etChildName.text.toString().trim()
        val birthDate       = binding.etChildBirthDate.text.toString().trim()
        val incomeLevelStr  = binding.etIncomeLevel.text.toString().trim()
        // TODO: DB children 테이블에 gender 컬럼 추가 후 아래 주석 해제
        // val isGenderSelected = binding.rbMale.isChecked || binding.rbFemale.isChecked

        if (username.isEmpty()) {
            binding.etUsername.error = "아이디를 입력해주세요."
            binding.etUsername.requestFocus()
            return false
        }
        if (username.length < 4) {
            binding.etUsername.error = "아이디는 4자 이상이어야 합니다."
            binding.etUsername.requestFocus()
            return false
        }
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
        if (childName.isEmpty()) {
            binding.etChildName.error = "자녀 이름을 입력해주세요."
            binding.etChildName.requestFocus()
            return false
        }
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
        if (incomeLevelStr.isNotEmpty()) {
            val level = incomeLevelStr.toIntOrNull()
            if (level == null || level < 1 || level > 10) {
                binding.etIncomeLevel.error = "소득분위는 1~10 사이 숫자여야 합니다."
                binding.etIncomeLevel.requestFocus()
                return false
            }
        }
        // TODO: DB children 테이블에 gender 컬럼 추가 후 아래 주석 해제
        // if (!isGenderSelected) {
        //     Toast.makeText(this, "자녀 성별을 선택해주세요.", Toast.LENGTH_SHORT).show()
        //     return false
        // }
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

// username 중복 체크용
@Serializable
private data class IdRow(val id: String)