package com.siheung.careconnect.reservation

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class ChildcareFacility(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val district: String = "",
    @SerialName("manager_id") val managerId: String? = null,
    @Transient var phone: String = "",
    @Transient val status: String = "예약 가능",
    @Transient var distance: Float = 0f
) : ClusterItem {

    override fun getPosition(): LatLng = LatLng(latitude, longitude)
    override fun getTitle(): String = name
    override fun getSnippet(): String = status
    override fun getZIndex(): Float? = 0f
}
