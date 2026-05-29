package com.siheung.careconnect.facilityadmin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.siheung.careconnect.NotificationHelper
import com.siheung.careconnect.R
import com.siheung.careconnect.databinding.ActivityAdminReservationBinding
import com.siheung.careconnect.login.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AdminReservationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminReservationBinding
    private var selectedDate: LocalDate = LocalDate.now()
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val kst = ZoneId.of("Asia/Seoul")

    private var facilityId: String = ""

    private val reservations = mutableListOf<AdminReservation>()
    private lateinit var reservationAdapter: AdminReservationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminReservationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        facilityId = intent.getStringExtra("facility_id") ?: ""

        setupToolbar()
        setupCalendar()
        setupRecyclerView()
        loadFacilityName()
        loadReservations()
    }

    private fun loadFacilityName() {
        binding.tvFacilityName.text = "내 시설 예약 현황"
    }

    // ── 툴바 (뒤로가기) ──────────────────────────────────────
    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { finish() }
    }

    // ── 달력 뷰 (날짜 선택기) ────────────────────────────────
    private fun setupCalendar() {
        updateDateLabel()
        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
            updateDateLabel()
            loadReservations()
        }
    }

    private fun updateDateLabel() {
        binding.tvSelectedDate.text =
            selectedDate.format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 (E)"))
    }

    // ── RecyclerView ──────────────────────────────────────────
    private fun setupRecyclerView() {
        reservationAdapter = AdminReservationAdapter(reservations) { reservation, newStatus ->
            updateStatus(reservation, newStatus)
        }
        binding.rvReservations.apply {
            layoutManager = LinearLayoutManager(this@AdminReservationActivity)
            adapter = reservationAdapter
        }
    }

    // ── Supabase: 날짜별 예약 목록 조회 ─────────────────────
    private fun loadReservations() {
        showLoading(true)
        binding.tvEmpty.visibility = View.GONE

        lifecycleScope.launch {
            try {
                // KST 기준 하루 범위를 UTC offset으로 변환하여 필터
                val kstStart = selectedDate.atStartOfDay(kst).toOffsetDateTime()
                val kstEnd   = selectedDate.plusDays(1).atStartOfDay(kst).toOffsetDateTime()
                val isoFmt   = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                // facilityId 필터 없이 RLS가 관리자 본인의 시설 예약만 반환
                val result = SupabaseClientProvider.client.postgrest["reservations"]
                    .select(Columns.raw("id, status, start_time, end_time, parent_id, children(name), facilities(name)")) {
                        filter {
                            gte("start_time", kstStart.format(isoFmt))
                            lt("start_time",  kstEnd.format(isoFmt))
                        }
                    }
                    .decodeList<ReservationRow>()

                reservations.clear()
                reservations.addAll(result.mapNotNull { row ->
                    if (row.startTime == null || row.endTime == null) return@mapNotNull null
                    val start = OffsetDateTime.parse(row.startTime)
                        .atZoneSameInstant(kst).toLocalTime()
                        .format(DateTimeFormatter.ofPattern("HH:mm"))
                    val end = OffsetDateTime.parse(row.endTime)
                        .atZoneSameInstant(kst).toLocalTime()
                        .format(DateTimeFormatter.ofPattern("HH:mm"))
                    AdminReservation(
                        id           = row.id,
                        childName    = row.children?.name ?: "미상",
                        facilityName = row.facilities?.name ?: "",
                        startTime    = start,
                        endTime      = end,
                        status       = row.status,
                        parentId     = row.parentId
                    )
                })
                reservationAdapter.notifyDataSetChanged()
                binding.tvEmpty.visibility = if (reservations.isEmpty()) View.VISIBLE else View.GONE

            } catch (e: Exception) {
                Toast.makeText(this@AdminReservationActivity, "조회 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                showLoading(false)
            }
        }
    }

    // ── Supabase: 예약 상태 변경 (확정 / 완료) + FCM 발송 ───────────────
    private fun updateStatus(reservation: AdminReservation, newStatus: String) {
        showLoading(true)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    SupabaseClientProvider.client.postgrest["reservations"].update(
                        buildJsonObject { put("status", newStatus) }
                    ) {
                        filter { eq("id", reservation.id) }
                    }
                }

                // 보호자 FCM 토큰 조회 후 알림 발송
                if (reservation.parentId.isNotEmpty()) {
                    try {
                        val tokenRow = withContext(Dispatchers.IO) {
                            SupabaseClientProvider.client.postgrest["users"]
                                .select(Columns.raw("fcm_token")) {
                                    filter { eq("id", reservation.parentId) }
                                }
                                .decodeSingleOrNull<FcmTokenRow>()
                        }
                        val fcmToken = tokenRow?.fcmToken
                        if (!fcmToken.isNullOrEmpty()) {
                            val title = when (newStatus) {
                                "확정" -> "예약이 확정되었습니다"
                                "완료" -> "예약이 완료되었습니다"
                                "취소" -> "예약이 취소되었습니다"
                                else   -> "예약 상태가 변경되었습니다"
                            }
                            val body = "${reservation.childName} 아동의 예약이 $newStatus 처리되었습니다."
                            NotificationHelper.sendNotification(fcmToken, title, body)
                        }
                    } catch (_: Exception) { }
                }

                Toast.makeText(
                    this@AdminReservationActivity,
                    "'${reservation.childName}' 예약이 $newStatus 처리되었습니다.",
                    Toast.LENGTH_SHORT
                ).show()
                loadReservations()
            } catch (e: Exception) {
                Toast.makeText(this@AdminReservationActivity, "변경 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                showLoading(false)
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }
}

// ── 데이터 클래스 ─────────────────────────────────────────────
@Serializable
private data class FacilityNameRow(val name: String)

data class AdminReservation(
    val id: String,
    val childName: String,
    val facilityName: String = "",
    val startTime: String,
    val endTime: String,
    val status: String,
    val parentId: String = ""
)

@Serializable
private data class ReservationRow(
    val id: String,
    val status: String,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("end_time")   val endTime: String? = null,
    @SerialName("parent_id")  val parentId: String = "",
    val children: ChildRef? = null,
    val facilities: FacilityRef? = null
)

@Serializable
private data class ChildRef(val name: String)

@Serializable
private data class FacilityRef(val name: String)

@Serializable
internal data class FcmTokenRow(
    @SerialName("fcm_token") val fcmToken: String? = null
)

// ── RecyclerView 어댑터 ───────────────────────────────────────
class AdminReservationAdapter(
    private val items: List<AdminReservation>,
    private val onStatusChange: (AdminReservation, String) -> Unit
) : RecyclerView.Adapter<AdminReservationAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvFacilityName: TextView = itemView.findViewById(R.id.tvFacilityName)
        private val tvChildName: TextView = itemView.findViewById(R.id.tvChildName)
        private val tvTimeRange: TextView = itemView.findViewById(R.id.tvTimeRange)
        private val tvStatus:    TextView = itemView.findViewById(R.id.tvStatus)
        private val btnCancel:   Button   = itemView.findViewById(R.id.btnCancel)
        private val btnConfirm:  Button   = itemView.findViewById(R.id.btnConfirm)
        private val btnComplete: Button   = itemView.findViewById(R.id.btnComplete)

        fun bind(res: AdminReservation) {
            tvFacilityName.text = res.facilityName
            tvChildName.text = res.childName
            tvTimeRange.text = "${res.startTime} ~ ${res.endTime}"
            tvStatus.text    = res.status

            val (bgColor, textColor) = when (res.status) {
                "대기" -> Pair(R.color.amber_light,  R.color.amber_primary)
                "확정" -> Pair(R.color.green_light,  R.color.green_primary)
                "완료" -> Pair(R.color.bg_secondary, R.color.text_secondary)
                "취소" -> Pair(R.color.bg_secondary, R.color.text_secondary)
                else   -> Pair(R.color.bg_secondary, R.color.text_secondary)
            }
            tvStatus.setBackgroundColor(ContextCompat.getColor(itemView.context, bgColor))
            tvStatus.setTextColor(ContextCompat.getColor(itemView.context, textColor))

            // 취소 버튼: 대기·확정 상태에서만 표시
            val cancellable = res.status == "대기" || res.status == "확정"
            btnCancel.visibility   = if (cancellable) View.VISIBLE else View.GONE
            btnConfirm.visibility  = if (res.status == "대기") View.VISIBLE else View.GONE
            btnComplete.visibility = if (res.status == "확정") View.VISIBLE else View.GONE

            btnCancel.setOnClickListener   { onStatusChange(res, "취소") }
            btnConfirm.setOnClickListener  { onStatusChange(res, "확정") }
            btnComplete.setOnClickListener { onStatusChange(res, "완료") }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_reservation, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size
}