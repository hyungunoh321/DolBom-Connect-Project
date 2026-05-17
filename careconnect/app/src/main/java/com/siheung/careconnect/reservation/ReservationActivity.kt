package com.siheung.careconnect.reservation

import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.Dialog
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.maps.android.clustering.ClusterManager
import com.siheung.careconnect.R
import com.siheung.careconnect.databinding.ActivityReservationBinding
import com.siheung.careconnect.login.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class ReservationActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityReservationBinding
    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var clusterManager: ClusterManager<ChildcareFacility>
    private lateinit var facilityAdapter: FacilityAdapter

    private val siheungCenter = LatLng(37.3802, 126.8028)
    private var lastKnownLocation: Location? = null
    private val facilities = mutableListOf<ChildcareFacility>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReservationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupUI()
        setupRecyclerView()
        setupFilterChips()
        updateResultCount(0)
        loadFacilities()
    }

    private fun loadFacilities() {
        lifecycleScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    SupabaseClientProvider.client.postgrest["facilities"]
                        .select()
                        .decodeList<ChildcareFacility>()
                }
                loaded.forEach { it.district = deriveDistrict(it.address) }
                facilities.clear()
                facilities.addAll(loaded)
                facilityAdapter.updateItems(facilities)
                updateResultCount(facilities.size)
                if (::clusterManager.isInitialized) {
                    clusterManager.clearItems()
                    clusterManager.addItems(facilities)
                    clusterManager.cluster()
                }
                lastKnownLocation?.let { sortAndDisplayFacilities(it) }
            } catch (e: Exception) {
                Log.e("ReservationActivity", "시설 조회 실패: ${e.message}")
                Toast.makeText(this@ReservationActivity, "시설 정보를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deriveDistrict(address: String): String = when {
        "정왕본동" in address -> "정왕본동"
        "정왕1동"  in address -> "정왕1동"
        "정왕2동"  in address -> "정왕2동"
        "정왕3동"  in address -> "정왕3동"
        "정왕4동"  in address -> "정왕4동"
        "배곤1동"  in address || "배곧1동" in address -> "배곤1동"
        "배곤2동"  in address || "배곧2동" in address -> "배곤2동"
        "대야동"   in address -> "대야동"
        "신현동"   in address -> "신현동"
        "신천동"   in address -> "신천동"
        "은행동"   in address -> "은행동"
        "매화동"   in address -> "매화동"
        "목감동"   in address -> "목감동"
        "군자동"   in address -> "군자동"
        "과림동"   in address -> "과림동"
        "연성동"   in address -> "연성동"
        "능곡동"   in address -> "능곡동"
        "월곳동"   in address -> "월곳동"
        "장곡동"   in address -> "장곡동"
        "거북섬동" in address -> "거북섬동"
        else -> "신천동"
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }

        val behavior = BottomSheetBehavior.from(binding.bottomSheet)
        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: android.view.View, newState: Int) {}
            override fun onSlide(bottomSheet: android.view.View, slideOffset: Float) {
                val padding = (slideOffset * 500).toInt().coerceAtLeast(0)
                if (::mMap.isInitialized) {
                    mMap.setPadding(0, 0, 0, padding + 200)
                }
            }
        })
    }

    private fun setupRecyclerView() {
        facilityAdapter = FacilityAdapter(
            emptyList(),
            onItemClick = { facility ->
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(facility.position, 16f))
                BottomSheetBehavior.from(binding.bottomSheet).state =
                    BottomSheetBehavior.STATE_COLLAPSED
            },
            onReserveClick = { facility ->
                showDatePicker(facility)
            }
        )
        binding.rvFacilities.apply {
            layoutManager = LinearLayoutManager(this@ReservationActivity)
            adapter = facilityAdapter
        }
    }

    private fun setupFilterChips() {
        binding.cgDistrict.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val chipText = group.findViewById<Chip>(checkedIds[0])?.text?.toString() ?: "전체"
            applyDistrictFilter(chipText)
        }
    }

    private fun applyDistrictFilter(district: String) {
        val filtered = if (district == "전체") facilities.toList()
                       else facilities.filter { it.district == district }
        facilityAdapter.updateItems(filtered)
        updateResultCount(filtered.size)

        if (::clusterManager.isInitialized) {
            clusterManager.clearItems()
            clusterManager.addItems(filtered)
            clusterManager.cluster()
        }
    }

    private fun updateResultCount(count: Int) {
        binding.tvResultCount.text = "총 결과 ${count}개"
    }

    // ── 날짜 선택 ──────────────────────────────────────────────────

    private fun showDatePicker(facility: ChildcareFacility) {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                showTimeSlotDialog(facility, year, month + 1, day)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = cal.timeInMillis
        }.show()
    }

    // ── 시간대 선택 다이얼로그 ─────────────────────────────────────

    private fun showTimeSlotDialog(facility: ChildcareFacility, year: Int, month: Int, day: Int) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_time_slot)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                (resources.displayMetrics.widthPixels * 0.88).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }

        val cgTime = dialog.findViewById<ChipGroup>(R.id.cgTimeSlot)
        dialog.findViewById<Button>(R.id.btnNext).setOnClickListener {
            val checkedId = cgTime.checkedChipId
            if (checkedId == android.view.View.NO_ID) {
                Toast.makeText(this, "시간을 선택해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val timeStr = dialog.findViewById<Chip>(checkedId).text.toString()
            val reservedAt = String.format("%04d-%02d-%02dT%s:00", year, month, day, timeStr)
            dialog.dismiss()
            showBookingConfirmDialog(facility, reservedAt, year, month, day, timeStr)
        }

        dialog.show()
    }

    // ── 예약 확인 다이얼로그 ───────────────────────────────────────

    private fun showBookingConfirmDialog(
        facility: ChildcareFacility,
        reservedAt: String,
        year: Int, month: Int, day: Int,
        time: String
    ) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_booking_confirm)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                (resources.displayMetrics.widthPixels * 0.88).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }

        dialog.findViewById<TextView>(R.id.tvFacilityName).text = facility.name
        dialog.findViewById<TextView>(R.id.tvReservedAt).text =
            "${year}년 ${month}월 ${day}일  $time"

        dialog.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        val btnConfirm = dialog.findViewById<Button>(R.id.btnConfirm)
        btnConfirm.setOnClickListener {
            val parentId = SupabaseClientProvider.client.auth.currentUserOrNull()?.id
            if (parentId == null) {
                Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnConfirm.isEnabled = false
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        SupabaseClientProvider.client.postgrest["reservations"].insert(
                            ReservationRequest(
                                parent_id = parentId,
                                facility_id = facility.id,
                                reserved_at = reservedAt
                            )
                        )
                    }
                    Toast.makeText(this@ReservationActivity, "예약이 완료되었습니다 (대기)", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } catch (e: Exception) {
                    Log.e("ReservationActivity", "예약 실패: ${e.message}")
                    Toast.makeText(this@ReservationActivity, "예약에 실패했습니다. 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
                    btnConfirm.isEnabled = true
                }
            }
        }

        dialog.show()
    }

    // ── 지도 콜백 ─────────────────────────────────────────────────

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(siheungCenter, 13f))
        setupClusterManager()
        updateLocationUI()
        getDeviceLocation()
    }

    private fun setupClusterManager() {
        clusterManager = ClusterManager(this, mMap)
        mMap.setOnCameraIdleListener(clusterManager)
        mMap.setOnMarkerClickListener(clusterManager)
        clusterManager.addItems(facilities)
        clusterManager.cluster()

        clusterManager.setOnClusterItemClickListener { item ->
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(item.position, 16f))
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateLocationUI() {
        if (checkPermission()) {
            mMap.isMyLocationEnabled = true
            mMap.uiSettings.isMyLocationButtonEnabled = true
        } else {
            requestPermission()
        }
    }

    @SuppressLint("MissingPermission")
    private fun getDeviceLocation() {
        if (checkPermission()) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    lastKnownLocation = location
                    sortAndDisplayFacilities(location)
                }
            }
        }
    }

    private fun sortAndDisplayFacilities(currentLocation: Location) {
        facilities.forEach { facility ->
            val dest = Location("").apply {
                latitude = facility.latitude
                longitude = facility.longitude
            }
            facility.distance = currentLocation.distanceTo(dest)
        }
        val sorted = facilities.sortedBy { it.distance }
        facilityAdapter.updateItems(sorted)
    }

    private fun checkPermission() = ActivityCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1000
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1000 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            updateLocationUI()
            getDeviceLocation()
        }
    }
}
