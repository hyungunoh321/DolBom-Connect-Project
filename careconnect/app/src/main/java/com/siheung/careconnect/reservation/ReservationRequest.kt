package com.siheung.careconnect.reservation

// Supabase reservations 테이블 insert용 데이터 클래스
// parent_id는 Supabase Auth RLS가 자동 처리하므로 제외
data class ReservationRequest(
    val facility_id: String,   // ChildcareFacility.id (UUID)
    val child_id: String,      // 선택한 자녀 UUID (children 테이블)
    val reserved_at: String,   // ISO 8601: "2026-05-16T09:00:00"
    val status: String = "대기"
)
