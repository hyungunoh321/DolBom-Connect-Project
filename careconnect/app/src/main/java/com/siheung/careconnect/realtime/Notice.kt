package com.siheung.careconnect.realtime

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Notice(
    val id: Long = 0,
    val text: String = "",
    val date: String = "",
    @SerialName("is_read")
    val isRead: Boolean = false
)
