package com.siheung.careconnect.reservation

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem

/**
 * 보육기관 데이터 모델
 */
data class ChildcareFacility(
    val id: String,          // Supabase facilities.id (UUID)
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val phone: String = "",
    val district: String = "",   // 행정구역 필터용
    val status: String = "예약 가능",
    var distance: Float = 0f
) : ClusterItem {

    override fun getPosition(): LatLng = LatLng(latitude, longitude)
    override fun getTitle(): String = name
    override fun getSnippet(): String = status
    override fun getZIndex(): Float? = 0f
}
