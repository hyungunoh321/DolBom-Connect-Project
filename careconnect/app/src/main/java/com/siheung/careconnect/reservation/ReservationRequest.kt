package com.siheung.careconnect.reservation

import kotlinx.serialization.Serializable

@Serializable
data class ReservationRequest(
    val parent_id: String,
    val facility_id: String,
    val child_id: String? = null,
    val reserved_at: String,
    val status: String = "대기"
)
