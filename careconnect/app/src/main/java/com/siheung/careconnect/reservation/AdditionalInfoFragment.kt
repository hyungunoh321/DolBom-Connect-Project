package com.siheung.careconnect.reservation

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.siheung.careconnect.R
import com.siheung.careconnect.databinding.FragmentAdditionalInfoBinding

class AdditionalInfoFragment : Fragment() {

    private var _binding: FragmentAdditionalInfoBinding? = null
    private val binding get() = _binding!!

    private val bookingActivity get() = requireActivity() as BookingActivity

    private var file1Uri: Uri? = null
    private var file2Uri: Uri? = null

    private val pickFile1 = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            file1Uri = it
            binding.tvFile1Name.text = getFileName(it)
            binding.tvFile1Name.setTextColor(requireContext().getColor(android.R.color.darker_gray))
        }
    }

    private val pickFile2 = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            file2Uri = it
            binding.tvFile2Name.text = getFileName(it)
            binding.tvFile2Name.setTextColor(requireContext().getColor(android.R.color.darker_gray))
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdditionalInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupHouseholdCheckboxes()

        binding.btnPickFile1.setOnClickListener { pickFile1.launch("*/*") }
        binding.btnPickFile2.setOnClickListener { pickFile2.launch("*/*") }
        binding.btnAddFile.setOnClickListener { addExtraFileRow() }

        binding.btnPrevStep.setOnClickListener { bookingActivity.goToPrevStep(4) }
        binding.btnNextStep.setOnClickListener {
            if (validateInputs()) {
                saveToFormData()
                bookingActivity.goToNextStep(4)
            }
        }
    }

    private fun setupHouseholdCheckboxes() {
        val options = listOf(
            binding.cbMultiChild, binding.cbSingleParent,
            binding.cbGrandparent, binding.cbDisabled
        )
        binding.cbHouseholdNone.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) options.forEach { it.isChecked = false }
            binding.layoutHouseholdOptions.isEnabled = !isChecked
            options.forEach { it.isEnabled = !isChecked }
        }
    }

    private fun addExtraFileRow() {
        val container = binding.llExtraFiles
        val rowIndex = container.childCount

        val extraPickLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { selected ->
                val tvName = container.getChildAt(rowIndex)
                    ?.findViewById<TextView>(R.id.tvExtraFileName)
                tvName?.text = getFileName(selected)
            }
        }

        val row = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_extra_file_row, container, false)
        row.findViewById<Button>(R.id.btnPickExtraFile).setOnClickListener {
            extraPickLauncher.launch("*/*")
        }
        container.addView(row)
    }

    private fun validateInputs(): Boolean {
        if (file1Uri == null) {
            Toast.makeText(requireContext(), "주민등록등본을 첨부해주세요.", Toast.LENGTH_SHORT).show()
            return false
        }
        if (file2Uri == null) {
            Toast.makeText(requireContext(), "건강보험료납부확인서를 첨부해주세요.", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun saveToFormData() {
        val fd = bookingActivity.formData
        fd.householdFeatures.clear()
        if (binding.cbMultiChild.isChecked) fd.householdFeatures.add("다자녀")
        if (binding.cbSingleParent.isChecked) fd.householdFeatures.add("한부모")
        if (binding.cbGrandparent.isChecked) fd.householdFeatures.add("조손")
        if (binding.cbDisabled.isChecked) fd.householdFeatures.add("장애인")
    }

    private fun getFileName(uri: Uri): String {
        var name = "파일 선택됨"
        requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
