package com.siheung.careconnect.mypage

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.siheung.careconnect.databinding.ActivityMyPageBinding
import com.siheung.careconnect.login.LoginActivity
import com.siheung.careconnect.login.SupabaseClientProvider
import com.siheung.careconnect.main.AppSessionState
import com.siheung.careconnect.main.MainActivity
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class MyPageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyPageBinding
    private var childExists = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyPageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnChangePassword.setOnClickListener { changePassword() }
        binding.btnSaveChild.setOnClickListener { saveChildInfo() }
        binding.btnLogout.setOnClickListener { logout() }

        if (!AppSessionState.isAuthenticatedInCurrentProcess ||
            SupabaseClientProvider.client.auth.currentUserOrNull() == null
        ) {
            Toast.makeText(this, "로그인 해주세요", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        loadMyPage()
    }

    private fun loadMyPage() {
        val userId = SupabaseClientProvider.client.auth.currentUserOrNull()?.id ?: return

        lifecycleScope.launch {
            val user = try {
                SupabaseClientProvider.client.postgrest["users"]
                    .select(Columns.raw("username,email")) {
                        filter { eq("id", userId) }
                    }
                    .decodeSingleOrNull<MyPageUserRow>()
            } catch (e: Exception) {
                null
            }

            val children = try {
                SupabaseClientProvider.client.postgrest["children"]
                    .select(Columns.raw("parent_id,name,birth_date,income_level")) {
                        filter { eq("parent_id", userId) }
                    }
                    .decodeList<MyPageChildRow>()
            } catch (e: Exception) {
                emptyList()
            }

            val firstChild = children.firstOrNull()
            childExists = firstChild != null

            val username = user?.username.orEmpty().ifBlank { "보호자" }
            val incomeLevel = children.mapNotNull { it.incomeLevel }.minOrNull()
            val incomeText = incomeLevel?.let { "소득분위 $it" } ?: "소득분위 미등록"

            binding.tvProfileName.text = "${username}님"
            binding.tvProfileInfo.text = "자녀 ${children.size}명 · $incomeText"
            binding.tvProfileEmail.text = user?.email.orEmpty()
            binding.etChildName.setText(firstChild?.name.orEmpty())
            binding.etChildBirthDate.setText(firstChild?.birthDate.orEmpty())
            binding.etIncomeLevel.setText(firstChild?.incomeLevel?.toString().orEmpty())
        }
    }

    private fun changePassword() {
        val password = binding.etNewPassword.text.toString().trim()
        val passwordConfirm = binding.etNewPasswordConfirm.text.toString().trim()

        when {
            password.length < 8 -> {
                binding.etNewPassword.error = "비밀번호는 8자 이상이어야 합니다."
                binding.etNewPassword.requestFocus()
                return
            }
            password != passwordConfirm -> {
                binding.etNewPasswordConfirm.error = "비밀번호가 일치하지 않습니다."
                binding.etNewPasswordConfirm.requestFocus()
                return
            }
        }

        lifecycleScope.launch {
            try {
                SupabaseClientProvider.client.auth.updateUser {
                    this.password = password
                }
                binding.etNewPassword.text?.clear()
                binding.etNewPasswordConfirm.text?.clear()
                Toast.makeText(this@MyPageActivity, "비밀번호가 변경되었습니다.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MyPageActivity, "비밀번호 변경에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveChildInfo() {
        val userId = SupabaseClientProvider.client.auth.currentUserOrNull()?.id ?: return
        val childName = binding.etChildName.text.toString().trim()
        val birthDate = binding.etChildBirthDate.text.toString().trim()
        val incomeLevel = binding.etIncomeLevel.text.toString().trim().toIntOrNull()

        if (childName.isBlank()) {
            binding.etChildName.error = "아이 이름을 입력해주세요."
            binding.etChildName.requestFocus()
            return
        }
        if (!birthDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            binding.etChildBirthDate.error = "YYYY-MM-DD 형식으로 입력해주세요."
            binding.etChildBirthDate.requestFocus()
            return
        }
        if (incomeLevel == null || incomeLevel !in 1..10) {
            binding.etIncomeLevel.error = "1~10 사이 숫자를 입력해주세요."
            binding.etIncomeLevel.requestFocus()
            return
        }

        lifecycleScope.launch {
            try {
                val childJson = buildJsonObject {
                    put("parent_id", userId)
                    put("name", childName)
                    put("birth_date", birthDate)
                    put("income_level", incomeLevel)
                }

                if (childExists) {
                    SupabaseClientProvider.client.postgrest["children"].update(childJson) {
                        filter { eq("parent_id", userId) }
                    }
                } else {
                    SupabaseClientProvider.client.postgrest["children"].insert(childJson)
                    childExists = true
                }

                Toast.makeText(this@MyPageActivity, "아이 정보가 저장되었습니다.", Toast.LENGTH_SHORT).show()
                loadMyPage()
            } catch (e: Exception) {
                Toast.makeText(this@MyPageActivity, "아이 정보 저장에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun logout() {
        lifecycleScope.launch {
            AppSessionState.isAuthenticatedInCurrentProcess = false
            runCatching { SupabaseClientProvider.client.auth.signOut() }
            Toast.makeText(this@MyPageActivity, "로그아웃되었습니다.", Toast.LENGTH_SHORT).show()
            val intent = Intent(this@MyPageActivity, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
        }
    }
}

@Serializable
private data class MyPageUserRow(
    val username: String = "",
    val email: String = ""
)

@Serializable
private data class MyPageChildRow(
    @SerialName("parent_id")
    val parentId: String = "",
    val name: String = "",
    @SerialName("birth_date")
    val birthDate: String = "",
    @SerialName("income_level")
    val incomeLevel: Int? = null
)
