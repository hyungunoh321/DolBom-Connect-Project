package com.siheung.careconnect.reservation

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.siheung.careconnect.databinding.ActivityReservationStatusBinding
import com.siheung.careconnect.login.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ReservationStatusActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReservationStatusBinding
    private lateinit var adapter: ReservationStatusAdapter
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val channel by lazy {
        val userId = SupabaseClientProvider.client.auth.currentUserOrNull()?.id ?: "guest"
        SupabaseClientProvider.client.channel("reservation-status-$userId")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReservationStatusBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = ReservationStatusAdapter { item -> showCancelDialog(item) }
        binding.rvReservations.layoutManager = LinearLayoutManager(this)
        binding.rvReservations.adapter = adapter

        loadReservations()
        subscribeToRealtime()
    }

    private fun subscribeToRealtime() {
        // 예약 상태 변경 시 자동 갱신
        channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "reservations"
        }.onEach {
            withContext(Dispatchers.Main) { loadReservations() }
        }.launchIn(lifecycleScope)

        lifecycleScope.launch { channel.subscribe(blockUntilSubscribed = true) }
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanupScope.launch {
            try {
                channel.unsubscribe()
                SupabaseClientProvider.client.realtime.removeChannel(channel)
            } catch (_: Exception) { }
        }.invokeOnCompletion { cleanupScope.cancel() }
    }

    private fun loadReservations() {
        val userId = SupabaseClientProvider.client.auth.currentUserOrNull()?.id
        if (userId == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE
        binding.rvReservations.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val items = withContext(Dispatchers.IO) {
                    SupabaseClientProvider.client.postgrest["reservations"]
                        .select(columns = Columns.raw("*, facilities(name, address), children(name, birth_date)")) {
                            filter { eq("parent_id", userId) }
                            order("reserved_at", Order.DESCENDING)
                        }
                        .decodeList<ReservationItem>()
                }
                binding.progressBar.visibility = View.GONE
                if (items.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                } else {
                    binding.rvReservations.visibility = View.VISIBLE
                    adapter.updateItems(items)
                }
            } catch (e: Exception) {
                Log.e("ReservationStatus", "조회 실패: ${e.message}", e)
                binding.progressBar.visibility = View.GONE
                Toast.makeText(
                    this@ReservationStatusActivity,
                    "예약 현황을 불러오지 못했습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showCancelDialog(item: ReservationItem) {
        val facilityName = item.facilities?.name ?: "해당 시설"
        MaterialAlertDialogBuilder(this)
            .setTitle("예약 취소")
            .setMessage("${facilityName} 예약을 취소하시겠습니까?")
            .setPositiveButton("취소하기") { _, _ -> cancelReservation(item.id) }
            .setNegativeButton("닫기", null)
            .show()
    }

    private fun cancelReservation(reservationId: String) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    SupabaseClientProvider.client.postgrest["reservations"]
                        .update(buildJsonObject { put("status", "취소") }) {
                            filter { eq("id", reservationId) }
                        }
                }
                Toast.makeText(this@ReservationStatusActivity, "예약이 취소되었습니다.", Toast.LENGTH_SHORT).show()
                loadReservations()
            } catch (e: Exception) {
                Log.e("ReservationStatus", "취소 실패: ${e.message}", e)
                Toast.makeText(
                    this@ReservationStatusActivity,
                    "취소에 실패했습니다. 다시 시도해주세요.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
