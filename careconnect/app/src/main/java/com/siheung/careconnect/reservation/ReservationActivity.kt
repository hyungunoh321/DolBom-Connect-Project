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
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
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
import java.util.Calendar

class ReservationActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityReservationBinding
    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var clusterManager: ClusterManager<ChildcareFacility>
    private lateinit var facilityAdapter: FacilityAdapter

    private val siheungCenter = LatLng(37.3802, 126.8028)
    private var lastKnownLocation: Location? = null

    private val sampleFacilities = mutableListOf(
        ChildcareFacility("fac-001", "시흥시청 어린이집", "시흥시 시청로 20",
            37.3802, 126.8028, phone = "031-310-2000", district = "신천"),
        ChildcareFacility("fac-002", "정왕 어린이집", "시흥시 정왕대로 233",
            37.3444, 126.7317, phone = "031-310-3000", district = "정왕"),
        ChildcareFacility("fac-003", "배곧 어린이집", "시흥시 배곧3로 80",
            37.3711, 126.7214, phone = "031-310-4000", district = "배곧"),
        ChildcareFacility("fac-004", "능곡 어린이집", "시흥시 능곡로 45",
            37.3789, 126.8145, phone = "031-310-5000", district = "신천"),
        ChildcareFacility("fac-005", "목감 어린이집", "시흥시 목감중앙로 20",
            37.3828, 126.8778, phone = "031-310-6000", district = "목감"),
        ChildcareFacility("fac-006", "은계 어린이집", "시흥시 은계중앙로 30",
            37.4422, 126.8228, phone = "031-310-7000", district = "은계")
    )

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
        updateResultCount(sampleFacilities.size)
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
        // 돌봄서비스·대상연령 칩은 향후 Supabase 필터링 시 연동
    }

    private fun applyDistrictFilter(district: String) {
        val filtered = if (district == "전체") sampleFacilities.toList()
                       else sampleFacilities.filter { it.district == district }
        facilityAdapter.updateItems(filtered)
        updateResultCount(filtered.size)

        // 지도 클러스터도 갱신
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
            datePicker.minDate = cal.timeInMillis  // 과거 날짜 선택 불가
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

        // 자녀 선택 스피너 — 추후 Supabase children 테이블 조회로 교체
        val childNames = listOf("첫째 아이", "둘째 아이", "셋째 아이")
        val spinner = dialog.findViewById<Spinner>(R.id.spinnerChild)
        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            childNames
        )

        dialog.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.findViewById<Button>(R.id.btnConfirm).setOnClickListener {
            val selectedChildIndex = spinner.selectedItemPosition
            // TODO: 실제 child_id는 Supabase children 테이블에서 조회 후 할당
            val request = ReservationRequest(
                facility_id = facility.id,
                child_id = "child-uuid-placeholder-$selectedChildIndex",
                reserved_at = reservedAt
            )
            Log.d("ReservationRequest", "request=$request")
            Toast.makeText(this, "예약이 완료되었습니다 (대기)", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
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
        clusterManager.addItems(sampleFacilities)
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
        sampleFacilities.forEach { facility ->
            val dest = Location("").apply {
                latitude = facility.latitude
                longitude = facility.longitude
            }
            facility.distance = currentLocation.distanceTo(dest)
        }
        val sorted = sampleFacilities.sortedBy { it.distance }
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
