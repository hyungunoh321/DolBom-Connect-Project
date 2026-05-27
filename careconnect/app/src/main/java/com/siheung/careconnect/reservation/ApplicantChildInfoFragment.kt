package com.siheung.careconnect.reservation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.siheung.careconnect.databinding.FragmentApplicantChildInfoBinding
import com.siheung.careconnect.login.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(val id: String, val username: String, val email: String? = null)

@Serializable
data class ChildItem(
    val id: String,
    val parent_id: String? = null,
    val name: String,
    val birth_date: String
)

class ApplicantChildInfoFragment : Fragment() {

    private var _binding: FragmentApplicantChildInfoBinding? = null
    private val binding get() = _binding!!

    private val bookingActivity get() = requireActivity() as BookingActivity

    private var isPhoneVerified = false
    private var isDuplicateChecked = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentApplicantChildInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindFacilityInfo()
        prefillApplicantName()
        setupPhoneVerification()
        setupDuplicateCheck()
        setupNavigation()
    }

    private fun bindFacilityInfo() {
        val facility = bookingActivity.facility
        binding.tvFacilityName.text = facility.name
        binding.tvFacilityAddress.text = facility.address
        binding.tvFacilityDistrict.text = facility.district
    }

    private fun prefillApplicantName() {
        val userId = SupabaseClientProvider.client.auth.currentUserOrNull()?.id ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val profile = withContext(Dispatchers.IO) {
                    SupabaseClientProvider.client.postgrest["users"]
                        .select { filter { eq("id", userId) } }
                        .decodeSingle<UserProfile>()
                }
                binding.etApplicantName.setText(profile.username)
            } catch (_: Exception) {
                // pre-fill 실패 시 빈 필드로 진행
            }
        }
    }

    private fun setupPhoneVerification() {
        binding.btnSendCode.setOnClickListener {
            val p1 = binding.etPhone1.text.toString().trim()
            val p2 = binding.etPhone2.text.toString().trim()
            if (p1.length != 4 || p2.length != 4) {
                Toast.makeText(requireContext(), "전화번호를 올바르게 입력해주세요 (각 4자리)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            binding.layoutVerification.isVisible = true
            binding.tvVerificationResult.text = ""
            Toast.makeText(requireContext(), "인증번호가 전송되었습니다 (테스트: 111111)", Toast.LENGTH_SHORT).show()
        }

        binding.btnVerify.setOnClickListener {
            val code = binding.etVerificationCode.text.toString().trim()
            if (code.length != 6) {
                binding.tvVerificationResult.text = "인증번호 6자리를 입력해주세요"
                binding.tvVerificationResult.setTextColor(requireContext().getColor(android.R.color.holo_red_light))
                binding.tvVerificationResult.isVisible = true
                return@setOnClickListener
            }
            // mock 검증: 테스트 코드 "111111" 또는 6자리 숫자 모두 수락
            isPhoneVerified = true
            binding.tvVerificationResult.text = "인증이 완료되었습니다"
            binding.tvVerificationResult.setTextColor(requireContext().getColor(android.R.color.holo_green_dark))
            binding.tvVerificationResult.isVisible = true
            binding.btnVerify.isEnabled = false
            binding.btnSendCode.isEnabled = false
        }
    }

    private fun setupDuplicateCheck() {
        binding.btnCheckDuplicate.setOnClickListener {
            val childName = binding.etChildName.text.toString().trim()
            if (childName.isEmpty()) {
                Toast.makeText(requireContext(), "아동 성명을 먼저 입력해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val userId = SupabaseClientProvider.client.auth.currentUserOrNull()?.id
            if (userId == null) {
                Toast.makeText(requireContext(), "로그인이 필요합니다", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val existing = withContext(Dispatchers.IO) {
                        SupabaseClientProvider.client.postgrest["children"]
                            .select {
                                filter {
                                    eq("parent_id", userId)
                                    eq("name", childName)
                                }
                            }
                            .decodeList<ChildItem>()
                    }
                    binding.tvDuplicateResult.isVisible = true
                    if (existing.isNotEmpty()) {
                        isDuplicateChecked = false
                        binding.tvDuplicateResult.text = "이미 등록된 아동입니다"
                        binding.tvDuplicateResult.setTextColor(requireContext().getColor(android.R.color.holo_red_light))
                    } else {
                        isDuplicateChecked = true
                        binding.tvDuplicateResult.text = "신청이 가능합니다"
                        binding.tvDuplicateResult.setTextColor(requireContext().getColor(android.R.color.holo_green_dark))
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "확인 중 오류가 발생했습니다", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupNavigation() {
        binding.btnPrevStep.setOnClickListener {
            bookingActivity.goToPrevStep(2)
        }

        binding.btnNextStep.setOnClickListener {
            if (!validateInputs()) return@setOnClickListener

            val formData = bookingActivity.formData
            formData.applicantName = binding.etApplicantName.text.toString().trim()
            formData.applicantPhone = "010-${binding.etPhone1.text}-${binding.etPhone2.text}"
            formData.childName = binding.etChildName.text.toString().trim()
            formData.childBirthDate = binding.etBirthDate.text.toString().trim()

            bookingActivity.goToNextStep(2)
        }
    }

    private fun validateInputs(): Boolean {
        if (binding.etApplicantName.text.toString().isBlank()) {
            Toast.makeText(requireContext(), "신청자명을 입력해주세요", Toast.LENGTH_SHORT).show()
            return false
        }
        val p1 = binding.etPhone1.text.toString()
        val p2 = binding.etPhone2.text.toString()
        if (p1.length != 4 || p2.length != 4) {
            Toast.makeText(requireContext(), "연락처를 올바르게 입력해주세요", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!isPhoneVerified) {
            Toast.makeText(requireContext(), "연락처 인증을 완료해주세요", Toast.LENGTH_SHORT).show()
            return false
        }
        if (binding.etChildName.text.toString().isBlank()) {
            Toast.makeText(requireContext(), "아동 성명을 입력해주세요", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!isDuplicateChecked) {
            Toast.makeText(requireContext(), "아동 중복확인을 완료해주세요", Toast.LENGTH_SHORT).show()
            return false
        }
        val birthDate = binding.etBirthDate.text.toString().trim()
        if (birthDate.length != 8 || !birthDate.all { it.isDigit() }) {
            Toast.makeText(requireContext(), "생년월일을 8자리 숫자로 입력해주세요 (예: 20101231)", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
