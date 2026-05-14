package com.siheung.careconnect.facilityadmin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.siheung.careconnect.R
import com.siheung.careconnect.databinding.ActivityAdminScheduleBinding
import com.siheung.careconnect.login.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class AdminScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminScheduleBinding
    private var selectedDate: LocalDate = LocalDate.now()
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    // LoginActivity에서 Intent extra로 전달받음
    private var facilityId: String = ""

    private val slots = mutableListOf<ScheduleSlot>()
    private lateinit var slotAdapter: ScheduleSlotAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        facilityId = intent.getStringExtra("facility_id") ?: ""

        setupToolbar()
        setupCalendar()
        setupRecyclerView()
        setupAddButton()
        loadSlots()
    }

    // ── 툴바 (뒤로가기) ──────────────────────────────────────
    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { finish() }
    }

    // ── 달력 뷰 ──────────────────────────────────────────────
    private fun setupCalendar() {
        updateDateLabel()
        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
            updateDateLabel()
            loadSlots()
        }
    }

    private fun updateDateLabel() {
        binding.tvSelectedDate.text =
            selectedDate.format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 (E)"))
    }

    // ── RecyclerView ──────────────────────────────────────────
    private fun setupRecyclerView() {
        slotAdapter = ScheduleSlotAdapter(slots) { slot -> deleteSlot(slot) }
        binding.rvSlots.apply {
            layoutManager = LinearLayoutManager(this@AdminScheduleActivity)
            adapter = slotAdapter
        }
    }

    // ── 슬롯 추가 버튼 ────────────────────────────────────────
    private fun setupAddButton() {
        binding.btnAddSlot.setOnClickListener {
            val startTime   = binding.etStartTime.text.toString().trim()
            val endTime     = binding.etEndTime.text.toString().trim()
            val capacityStr = binding.etCapacity.text.toString().trim()

            if (startTime.isEmpty() || endTime.isEmpty() || capacityStr.isEmpty()) {
                Toast.makeText(this, "시작 시간, 종료 시간, 정원을 모두 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val timeRegex = Regex("^([01]\\d|2[0-3]):([0-5]\\d)$")
            if (!timeRegex.matches(startTime) || !timeRegex.matches(endTime)) {
                Toast.makeText(this, "시간은 HH:mm 형식으로 입력해주세요. (예: 09:00)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (startTime >= endTime) {
                Toast.makeText(this, "종료 시간이 시작 시간보다 늦어야 합니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val capacity = capacityStr.toIntOrNull()
            if (capacity == null || capacity <= 0) {
                Toast.makeText(this, "정원은 1 이상의 숫자로 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            insertSlot(startTime, endTime, capacity)
        }
    }

    // ── Supabase: 날짜별 슬롯 목록 조회 ─────────────────────
    private fun loadSlots() {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val result = SupabaseClientProvider.client.postgrest["schedules"]
                    .select(Columns.ALL) {
                        filter {
                            eq("facility_id", facilityId)
                            eq("date", selectedDate.format(dateFormatter))
                        }
                    }
                    .decodeList<ScheduleSlot>()

                slots.clear()
                slots.addAll(result)
                slotAdapter.notifyDataSetChanged()
                binding.tvEmptySlots.visibility = if (slots.isEmpty()) View.VISIBLE else View.GONE

            } catch (e: Exception) {
                Toast.makeText(this@AdminScheduleActivity, "조회 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                showLoading(false)
            }
        }
    }

    // ── Supabase: 슬롯 추가 ──────────────────────────────────
    private fun insertSlot(startTime: String, endTime: String, capacity: Int) {
        showLoading(true)
        lifecycleScope.launch {
            try {
                SupabaseClientProvider.client.postgrest["schedules"].insert(
                    buildJsonObject {
                        put("facility_id", facilityId)
                        put("date",        selectedDate.format(dateFormatter))
                        put("start_time",  startTime)
                        put("end_time",    endTime)
                        put("capacity",    capacity)
                    }
                )
                binding.etStartTime.text?.clear()
                binding.etEndTime.text?.clear()
                binding.etCapacity.text?.clear()
                Toast.makeText(this@AdminScheduleActivity, "슬롯이 추가되었습니다.", Toast.LENGTH_SHORT).show()
                loadSlots()
            } catch (e: Exception) {
                Toast.makeText(this@AdminScheduleActivity, "추가 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                showLoading(false)
            }
        }
    }

    // ── Supabase: 슬롯 삭제 ──────────────────────────────────
    private fun deleteSlot(slot: ScheduleSlot) {
        if (slot.id == null) return
        showLoading(true)
        lifecycleScope.launch {
            try {
                SupabaseClientProvider.client.postgrest["schedules"].delete {
                    filter { eq("id", slot.id) }
                }
                Toast.makeText(this@AdminScheduleActivity, "삭제되었습니다.", Toast.LENGTH_SHORT).show()
                loadSlots()
            } catch (e: Exception) {
                Toast.makeText(this@AdminScheduleActivity, "삭제 실패: ${e.message}", Toast.LENGTH_SHORT).show()
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
data class ScheduleSlot(
    val id: String? = null,
    @SerialName("facility_id") val facilityId: String,
    val date: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time")   val endTime: String,
    val capacity: Int
)

// ── RecyclerView 어댑터 ───────────────────────────────────────
class ScheduleSlotAdapter(
    private val items: List<ScheduleSlot>,
    private val onDelete: (ScheduleSlot) -> Unit
) : RecyclerView.Adapter<ScheduleSlotAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTimeRange: TextView = itemView.findViewById(R.id.tvTimeRange)
        private val tvCapacity:  TextView = itemView.findViewById(R.id.tvCapacity)
        private val btnDelete:   Button   = itemView.findViewById(R.id.btnDelete)

        fun bind(slot: ScheduleSlot) {
            tvTimeRange.text = "${slot.startTime} ~ ${slot.endTime}"
            tvCapacity.text  = "정원 ${slot.capacity}명"
            btnDelete.setOnClickListener { onDelete(slot) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schedule_slot, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size
}