package com.siheung.careconnect.reservation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.siheung.careconnect.R
import com.siheung.careconnect.databinding.FragmentBasicInfoBinding

class BasicInfoFragment : Fragment() {

    private var _binding: FragmentBasicInfoBinding? = null
    private val binding get() = _binding!!

    private val bookingActivity get() = requireActivity() as BookingActivity

    private val relationOptions = listOf("모", "부", "조모", "조부", "기타")
    private val hourOptions = (0..23).map { String.format("%02d", it) }
    private val minOptions = listOf("00", "10", "20", "30", "40", "50")

    private val dayCheckboxes get() = listOf(
        binding.cbDayMon, binding.cbDayTue, binding.cbDayWed,
        binding.cbDayThu, binding.cbDayFri
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBasicInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val fd = bookingActivity.formData
        binding.etChildNameReadonly.setText(fd.childName)
        binding.etBirthDateReadonly.setText(fd.childBirthDate)

        setupTimeSpinners()
        setupDayCheckboxes()
        addGuardianRow()
        setupNavigation()

        binding.btnPostcode.setOnClickListener {
            Toast.makeText(requireContext(), "주소를 직접 입력해주세요.", Toast.LENGTH_SHORT).show()
        }
        binding.btnAddGuardian.setOnClickListener { addGuardianRow() }
        binding.btnAddOrg.setOnClickListener { addOrgRow() }
    }

    private fun setupTimeSpinners() {
        val ctx = requireContext()
        fun makeAdapter(items: List<String>) =
            ArrayAdapter(ctx, android.R.layout.simple_spinner_item, items).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }

        binding.spinnerStartHour.adapter = makeAdapter(hourOptions)
        binding.spinnerStartMin.adapter = makeAdapter(minOptions)
        binding.spinnerEndHour.adapter = makeAdapter(hourOptions)
        binding.spinnerEndMin.adapter = makeAdapter(minOptions)
        binding.spinnerEndHour.setSelection(2)
    }

    private fun setupDayCheckboxes() {
        binding.cbDayAll.setOnCheckedChangeListener { _, isChecked ->
            dayCheckboxes.forEach { it.isChecked = isChecked }
        }
        dayCheckboxes.forEach { cb ->
            cb.setOnCheckedChangeListener { _, _ ->
                binding.cbDayAll.setOnCheckedChangeListener(null)
                binding.cbDayAll.isChecked = dayCheckboxes.all { it.isChecked }
                binding.cbDayAll.setOnCheckedChangeListener { _, checked ->
                    dayCheckboxes.forEach { it.isChecked = checked }
                }
            }
        }
    }

    private fun addGuardianRow() {
        val row = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_guardian_row, binding.llGuardianRows, false)
        val spinner = row.findViewById<Spinner>(R.id.spinnerRelation)
        spinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            relationOptions
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        row.findViewById<android.widget.ImageButton>(R.id.btnRemoveGuardian).setOnClickListener {
            if (binding.llGuardianRows.childCount > 1) {
                binding.llGuardianRows.removeView(row)
            } else {
                Toast.makeText(requireContext(), "보호자 정보는 최소 1개 이상 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }
        binding.llGuardianRows.addView(row)
    }

    private fun addOrgRow() {
        val row = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_org_row, binding.llOrgRows, false)
        binding.llOrgRows.addView(row)
    }

    private fun validateInputs(): Boolean {
        if (binding.rgGender.checkedRadioButtonId == -1) {
            Toast.makeText(requireContext(), "성별을 선택해주세요.", Toast.LENGTH_SHORT).show()
            return false
        }
        val selectedDays = mutableListOf<String>()
        val dayNames = listOf("월", "화", "수", "목", "금")
        dayCheckboxes.forEachIndexed { i, cb -> if (cb.isChecked) selectedDays.add(dayNames[i]) }
        if (selectedDays.isEmpty()) {
            Toast.makeText(requireContext(), "돌봄 필요 요일을 1개 이상 선택해주세요.", Toast.LENGTH_SHORT).show()
            return false
        }
        val guardianContainer = binding.llGuardianRows
        var hasValidGuardian = false
        for (i in 0 until guardianContainer.childCount) {
            val row = guardianContainer.getChildAt(i)
            val name = row.findViewById<EditText>(R.id.etGuardianName).text.toString().trim()
            val phone = row.findViewById<EditText>(R.id.etGuardianPhone).text.toString().trim()
            if (name.isNotEmpty() && phone.isNotEmpty()) {
                hasValidGuardian = true
                break
            }
        }
        if (!hasValidGuardian) {
            Toast.makeText(requireContext(), "보호자 정보(성명, 전화번호)를 입력해주세요.", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun saveToFormData() {
        val fd = bookingActivity.formData

        fd.childGender = if (binding.rbMale.isChecked) "남" else "여"
        fd.childContactPhone = binding.etChildPhone.text.toString().trim()
        fd.childSchoolSameAsCenter = binding.cbSchoolSameAsCenter.isChecked
        fd.childSchoolName = binding.etSchoolName.text.toString().trim()
        fd.childGrade = binding.etGrade.text.toString().trim()
        fd.childClass = binding.etClass.text.toString().trim()
        fd.childHealthStatus = when (binding.rgHealth.checkedRadioButtonId) {
            R.id.rbHealthGood -> "양호"
            R.id.rbHealthNormal -> "보통"
            R.id.rbHealthWeak -> "허약"
            else -> "기타"
        }
        fd.childHealthNote = binding.etHealthNote.text.toString().trim()
        fd.childAddressMain = binding.etAddressMain.text.toString().trim()
        fd.childAddressDetail = binding.etAddressDetail.text.toString().trim()

        val dayNames = listOf("월", "화", "수", "목", "금")
        fd.careDays.clear()
        dayCheckboxes.forEachIndexed { i, cb -> if (cb.isChecked) fd.careDays.add(dayNames[i]) }

        fd.careStartHour = binding.spinnerStartHour.selectedItemPosition
        fd.careStartMin = binding.spinnerStartMin.selectedItemPosition * 10
        fd.careEndHour = binding.spinnerEndHour.selectedItemPosition
        fd.careEndMin = binding.spinnerEndMin.selectedItemPosition * 10

        fd.guardians.clear()
        val guardianContainer = binding.llGuardianRows
        for (i in 0 until guardianContainer.childCount) {
            val row = guardianContainer.getChildAt(i)
            val name = row.findViewById<EditText>(R.id.etGuardianName).text.toString().trim()
            val relation = row.findViewById<Spinner>(R.id.spinnerRelation).selectedItem.toString()
            val phone = row.findViewById<EditText>(R.id.etGuardianPhone).text.toString().trim()
            if (name.isNotEmpty() && phone.isNotEmpty()) {
                fd.guardians.add(Guardian(name, relation, phone))
            }
        }

        fd.relatedOrgs.clear()
        val orgContainer = binding.llOrgRows
        for (i in 0 until orgContainer.childCount) {
            val row = orgContainer.getChildAt(i)
            val orgName = row.findViewById<EditText>(R.id.etOrgName).text.toString().trim()
            val manager = row.findViewById<EditText>(R.id.etOrgManager).text.toString().trim()
            val phone = row.findViewById<EditText>(R.id.etOrgPhone).text.toString().trim()
            val note = row.findViewById<EditText>(R.id.etOrgNote).text.toString().trim()
            if (orgName.isNotEmpty()) {
                fd.relatedOrgs.add(RelatedOrg(orgName, manager, phone, note))
            }
        }

        fd.childNotes = binding.etChildNotes.text.toString().trim()
    }

    private fun setupNavigation() {
        binding.btnPrevStep.setOnClickListener {
            bookingActivity.goToPrevStep(3)
        }
        binding.btnNextStep.setOnClickListener {
            if (validateInputs()) {
                saveToFormData()
                bookingActivity.goToNextStep(3)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
