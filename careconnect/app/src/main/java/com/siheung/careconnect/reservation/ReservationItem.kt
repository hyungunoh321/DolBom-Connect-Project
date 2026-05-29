package com.siheung.careconnect.reservation

import kotlinx.serialization.Serializable

@Serializable
data class ReservationItem(
    val id: String,
    val parent_id: String? = null,
    val facility_id: String? = null,
    val child_id: String? = null,
    val reserved_at: String,
    val status: String,
    val start_time: String? = null,
    val end_time: String? = null,
    val facilities: FacilityRef? = null,
    val children: ChildRef? = null
)

@Serializable
data class FacilityRef(
    val name: String,
    val address: String
)

@Serializable
data class ChildRef(
    val name: String,
    val birth_date: String
)
