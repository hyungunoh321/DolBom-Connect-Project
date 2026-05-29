package com.siheung.careconnect.reservation

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.siheung.careconnect.databinding.FragmentTermsAgreementBinding
import com.siheung.careconnect.login.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TermsAgreementFragment : Fragment() {

    private var _binding: FragmentTermsAgreementBinding? = null
    private val binding get() = _binding!!

    private val bookingActivity get() = requireActivity() as BookingActivity

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTermsAgreementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val updateSubmitButton = { _: Any? ->
            binding.btnSubmit.isEnabled =
                binding.cbAgreePersonalInfo.isChecked && binding.cbAgreeTerms.isChecked
        }
        binding.cbAgreePersonalInfo.setOnCheckedChangeListener { _, _ -> updateSubmitButton(null) }
        binding.cbAgreeTerms.setOnCheckedChangeListener { _, _ -> updateSubmitButton(null) }

        binding.btnPrevStep.setOnClickListener { bookingActivity.goToPrevStep(5) }
        binding.btnSubmit.setOnClickListener { submitApplication() }
    }

    private fun submitApplication() {
        binding.btnSubmit.isEnabled = false
        val fd = bookingActivity.formData
        val supabase = SupabaseClientProvider.client
        val currentUserId = supabase.auth.currentUserOrNull()?.id
        if (currentUserId == null) {
            Toast.makeText(requireContext(), "로그인 정보를 확인해주세요.", Toast.LENGTH_SHORT).show()
            binding.btnSubmit.isEnabled = true
            return
        }

        lifecycleScope.launch {
            try {
                val childId = getOrCreateChildId(currentUserId, fd)

                val zone = ZoneId.of("Asia/Seoul")
                val today = LocalDate.now(zone)
                val startTime = today.atTime(LocalTime.of(fd.careStartHour, fd.careStartMin))
                    .atZone(zone).toOffsetDateTime()
                val endTime = today.atTime(LocalTime.of(fd.careEndHour, fd.careEndMin))
                    .atZone(zone).toOffsetDateTime()
                val fmt = DateTimeFormatter.ISO_OFFSET_DATE_TIME

                withContext(Dispatchers.IO) {
                    supabase.postgrest["reservations"].insert(
                        buildJsonObject {
                            put("parent_id", currentUserId)
                            put("facility_id", bookingActivity.facility.id)
                            put("child_id", childId)
                            put("status", "대기")
                            put("start_time", startTime.format(fmt))
                            put("end_time", endTime.format(fmt))
                            put("reserved_at", OffsetDateTime.now(zone).format(fmt))
                        }
                    )
                }

                Toast.makeText(requireContext(), "신청이 완료되었습니다!", Toast.LENGTH_LONG).show()
                requireActivity().finish()

            } catch (e: Exception) {
                Log.e("TermsAgreement", "신청 실패: ${e.message}", e)
                Toast.makeText(requireContext(), "신청에 실패했습니다. 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
                binding.btnSubmit.isEnabled = true
            }
        }
    }

    private suspend fun getOrCreateChildId(userId: String, fd: BookingFormData): String {
        fd.childId?.let { return it }

        withContext(Dispatchers.IO) {
            SupabaseClientProvider.client.postgrest["children"].insert(
                buildJsonObject {
                    put("parent_id", userId)
                    put("name", fd.childName)
                    put("birth_date", fd.childBirthDate)
                    put("gender", fd.childGender)
                }
            )
        }

        return withContext(Dispatchers.IO) {
            SupabaseClientProvider.client.postgrest["children"]
                .select {
                    filter {
                        eq("parent_id", userId)
                        eq("name", fd.childName)
                        eq("birth_date", fd.childBirthDate)
                    }
                }
                .decodeList<ChildItem>()
                .last().id
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
