package com.siheung.careconnect.reservation

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.siheung.careconnect.R
import com.siheung.careconnect.databinding.ActivityBookingBinding

data class BookingFormData(
    var applicantName: String = "",
    var applicantPhone: String = "",
    var childName: String = "",
    var childBirthDate: String = "",
    var childId: String? = null
)

class BookingActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FACILITY_ID = "facility_id"
        const val EXTRA_FACILITY_NAME = "facility_name"
        const val EXTRA_FACILITY_ADDRESS = "facility_address"
        const val EXTRA_FACILITY_DISTRICT = "facility_district"
    }

    private lateinit var binding: ActivityBookingBinding

    lateinit var facility: ChildcareFacility
    val formData = BookingFormData()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val id = intent.getStringExtra(EXTRA_FACILITY_ID)
        val name = intent.getStringExtra(EXTRA_FACILITY_NAME)
        val address = intent.getStringExtra(EXTRA_FACILITY_ADDRESS)
        val district = intent.getStringExtra(EXTRA_FACILITY_DISTRICT)

        if (id == null || name == null || address == null) { finish(); return }

        facility = ChildcareFacility(
            id = id,
            name = name,
            address = address,
            latitude = 0.0,
            longitude = 0.0,
            district = district ?: ""
        )

        binding.btnBack.setOnClickListener { finish() }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, ApplicantChildInfoFragment())
                .commit()
        }
    }

    fun goToNextStep(currentStep: Int) {
        when (currentStep) {
            2 -> Toast.makeText(this, "다음 단계(기본정보)는 곧 구현됩니다.", Toast.LENGTH_SHORT).show()
        }
    }

    fun goToPrevStep(currentStep: Int) {
        when (currentStep) {
            2 -> finish()
        }
    }
}
