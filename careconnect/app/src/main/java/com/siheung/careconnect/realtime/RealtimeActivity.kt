package com.siheung.careconnect.realtime

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.siheung.careconnect.databinding.ActivityRealtimeBinding
import com.siheung.careconnect.login.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

// Supabase에 아래 테이블이 필요합니다:
// CREATE TABLE notices (
//   id BIGSERIAL PRIMARY KEY,
//   text TEXT NOT NULL,
//   date TEXT NOT NULL,
//   is_read BOOLEAN DEFAULT FALSE,
//   created_at TIMESTAMPTZ DEFAULT NOW()
// );
// ALTER TABLE notices REPLICA IDENTITY FULL;
// Supabase 대시보드 → Database → Replication에서 notices 테이블 활성화 필요

class RealtimeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRealtimeBinding
    private val notices = mutableListOf<Notice>()
    private lateinit var adapter: NoticeListAdapter
    private val supabase = SupabaseClientProvider.client
    private val channel by lazy { supabase.channel("notices-channel") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRealtimeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        setupRecyclerView()
        loadInitialData()
        observeConnectionStatus()
        subscribeToRealtime()
    }

    private fun setupRecyclerView() {
        adapter = NoticeListAdapter(notices)
        binding.rvNotices.layoutManager = LinearLayoutManager(this)
        binding.rvNotices.adapter = adapter
    }

    private fun loadInitialData() {
        lifecycleScope.launch {
            try {
                val result = supabase.postgrest["notices"]
                    .select()
                    .decodeList<Notice>()
                notices.clear()
                notices.addAll(result)
                adapter.notifyDataSetChanged()
                updateEmptyState()
            } catch (e: Exception) {
                // 테이블이 없거나 연결 실패 시 샘플 데이터 표시
                updateEmptyState()
            }
        }
    }

    private fun observeConnectionStatus() {
        channel.status.onEach { status ->
            withContext(Dispatchers.Main) {
                when (status) {
                    RealtimeChannel.Status.SUBSCRIBED -> {
                        binding.statusDot.alpha = 1.0f
                        binding.tvStatus.text = "실시간 연결됨"
                    }
                    RealtimeChannel.Status.UNSUBSCRIBED -> {
                        binding.statusDot.alpha = 0.35f
                        binding.tvStatus.text = "연결 끊김"
                    }
                    else -> {
                        binding.statusDot.alpha = 0.35f
                        binding.tvStatus.text = "연결 중..."
                    }
                }
            }
        }.launchIn(lifecycleScope)
    }

    private fun subscribeToRealtime() {
        // INSERT 감지
        channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "notices"
        }.onEach { action ->
            val notice = action.decodeRecord<Notice>()
            withContext(Dispatchers.Main) {
                notices.add(0, notice)
                adapter.notifyItemInserted(0)
                binding.rvNotices.scrollToPosition(0)
                updateEmptyState()
            }
        }.launchIn(lifecycleScope)

        // UPDATE 감지
        channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "notices"
        }.onEach { action ->
            val updated = action.decodeRecord<Notice>()
            withContext(Dispatchers.Main) {
                val index = notices.indexOfFirst { it.id == updated.id }
                if (index >= 0) {
                    notices[index] = updated
                    adapter.notifyItemChanged(index)
                }
            }
        }.launchIn(lifecycleScope)

        // DELETE 감지
        channel.postgresChangeFlow<PostgresAction.Delete>(schema = "public") {
            table = "notices"
        }.onEach { action ->
            val id = action.oldRecord["id"]?.jsonPrimitive?.long
            withContext(Dispatchers.Main) {
                val index = notices.indexOfFirst { it.id == id }
                if (index >= 0) {
                    notices.removeAt(index)
                    adapter.notifyItemRemoved(index)
                    updateEmptyState()
                }
            }
        }.launchIn(lifecycleScope)

        lifecycleScope.launch {
            channel.subscribe(blockUntilSubscribed = true)
        }
    }

    private fun updateEmptyState() {
        if (notices.isEmpty()) {
            binding.rvNotices.visibility = android.view.View.GONE
            binding.tvEmpty.visibility = android.view.View.VISIBLE
        } else {
            binding.rvNotices.visibility = android.view.View.VISIBLE
            binding.tvEmpty.visibility = android.view.View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                channel.unsubscribe()
                supabase.removeChannel(channel)
            } catch (_: Exception) { }
        }
    }
}
