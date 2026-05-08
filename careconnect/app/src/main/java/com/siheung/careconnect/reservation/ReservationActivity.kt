package com.siheung.careconnect.reservation

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
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
import com.google.maps.android.clustering.ClusterManager
import com.siheung.careconnect.R
import com.siheung.careconnect.databinding.ActivityReservationBinding

class ReservationActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityReservationBinding
    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var clusterManager: ClusterManager<ChildcareFacility>
    private lateinit var facilityAdapter: FacilityAdapter

    private val siheungCenter = LatLng(37.3802, 126.8028) // 시흥시청 기준 중심점
    private var lastKnownLocation: Location? = null

    // 샘플 보육기관 데이터
    private val sampleFacilities = mutableListOf(
        ChildcareFacility("1", "시흥시청 어린이집", "시흥시 시청로 20", 37.3802, 126.8028),
        ChildcareFacility("2", "정왕 어린이집", "시흥시 정왕대로 233", 37.3444, 126.7317),
        ChildcareFacility("3", "배곧 어린이집", "시흥시 배곧3로 80", 37.3711, 126.7214),
        ChildcareFacility("4", "능곡 어린이집", "시흥시 능곡로 45", 37.3789, 126.8145),
        ChildcareFacility("5", "목감 어린이집", "시흥시 목감중앙로 20", 37.3828, 126.8778),
        ChildcareFacility("6", "은계 어린이집", "시흥시 은계중앙로 30", 37.4422, 126.8228)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReservationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // 지도 조각 초기화
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupUI()
        setupRecyclerView()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }

        // BottomSheet 설정
        val behavior = BottomSheetBehavior.from(binding.bottomSheet)
        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: android.view.View, newState: Int) {}
            override fun onSlide(bottomSheet: android.view.View, slideOffset: Float) {
                // 바텀시트가 올라오면 지도의 패딩을 조절하여 구글 로고 등이 가려지지 않게 함
                val padding = (slideOffset * 500).toInt().coerceAtLeast(0)
                if (::mMap.isInitialized) {
                    mMap.setPadding(0, 0, 0, padding + 200)
                }
            }
        })
    }

    private fun setupRecyclerView() {
        facilityAdapter = FacilityAdapter(emptyList()) { facility ->
            // 리스트 아이템 클릭 시 해당 위치로 카메라 이동
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(facility.position, 16f))
            BottomSheetBehavior.from(binding.bottomSheet).state = BottomSheetBehavior.STATE_COLLAPSED
        }
        binding.rvFacilities.apply {
            layoutManager = LinearLayoutManager(this@ReservationActivity)
            adapter = facilityAdapter
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        
        // 초기 카메라 위치: 시흥시청
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

        // 마커 클릭 시 동작
        clusterManager.setOnClusterItemClickListener { item ->
            binding.tvListTitle.text = item.name
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(item.position, 16f))
            // 바텀시트를 펼쳐서 상세 정보를 보여줄 수도 있음
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
        // 거리 계산 및 정렬
        sampleFacilities.forEach { facility ->
            val dest = Location("").apply {
                latitude = facility.latitude
                longitude = facility.longitude
            }
            facility.distance = currentLocation.distanceTo(dest)
        }

        val sortedList = sampleFacilities.sortedBy { it.distance }
        facilityAdapter.updateItems(sortedList)
    }

    private fun checkPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

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
        if (requestCode == 1000 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            updateLocationUI()
            getDeviceLocation()
        }
    }
}
