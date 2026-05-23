package com.siheung.careconnect.reservation

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.siheung.careconnect.R
import com.siheung.careconnect.databinding.ActivityBookingBinding
import kotlinx.serialization.Serializable

data class Guardian(
    var name: String = "",
    var relation: String = "모",
    var phone: String = ""
)

data class RelatedOrg(
    var orgName: String = "",
    var manager: String = "",
    var phone: String = "",
    var note: String = ""
)

data class BookingFormData(
    var applicantName: String = "",
    var applicantPhone: String = "",
    var childName: String = "",
    var childBirthDate: String = "",
    var childId: String? = null,
    var childGender: String = "남",
    var childContactPhone: String = "",
    var childSchoolSameAsCenter: Boolean = false,
    var childSchoolName: String = "",
    var childGrade: String = "",
    var childClass: String = "",
    var childHealthStatus: String = "양호",
    var childHealthNote: String = "",
    var childAddressMain: String = "",
    var childAddressDetail: String = "",
    var guardians: MutableList<Guardian> = mutableListOf(),
    var careDays: MutableList<String> = mutableListOf(),
    var careStartHour: Int = 0,
    var careStartMin: Int = 0,
    var careEndHour: Int = 2,
    var careEndMin: Int = 0,
    var relatedOrgs: MutableList<RelatedOrg> = mutableListOf(),
    var childNotes: String = "",
    var householdFeatures: MutableList<String> = mutableListOf(),
    var agreePersonalInfo: Boolean = false,
    var agreeTerms: Boolean = false,
)

@Serializable
data class ChildInsertResult(val id: String)

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
        val fragment: Fragment = when (currentStep) {
            2 -> BasicInfoFragment()
            3 -> AdditionalInfoFragment()
            4 -> TermsAgreementFragment()
            else -> return
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }

    fun goToPrevStep(currentStep: Int) {
        if (currentStep == 2) finish()
        else supportFragmentManager.popBackStack()
    }
}
