package com.siheung.careconnect.reservation

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem

/**
 * 보육기관 데이터 모델
 */
data class ChildcareFacility(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val status: String = "대기 중",
    var distance: Float = 0f // 현재 위치로부터의 거리 (meters)
) : ClusterItem {

    override fun getPosition(): LatLng = LatLng(latitude, longitude)
    override fun getTitle(): String = name
    override fun getSnippet(): String = status
    override fun getZIndex(): Float? = 0f
}
