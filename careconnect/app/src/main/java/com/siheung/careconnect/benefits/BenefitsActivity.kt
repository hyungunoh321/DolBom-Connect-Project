package com.siheung.careconnect.benefits

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.siheung.careconnect.R
import com.siheung.careconnect.databinding.ActivityBenefitsBinding
import com.siheung.careconnect.login.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class BenefitsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBenefitsBinding
    private lateinit var adapter: PolicyAdapter
    private var selectedFilter = "전체"
    private var allPolicies: List<PolicyItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBenefitsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupFilterChips(listOf("전체"))
        updateList("전체")
        loadPolicies()
    }

    // ── 툴바 (뒤로가기) ───────────────────────────────────────
    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { finish() }
    }

    // ── 필터 칩 ───────────────────────────────────────────────
    private fun setupFilterChips(filters: List<String>) {
        binding.chipGroupFilter.removeAllViews()
        filters.forEach { filter ->
            val chip = Chip(this).apply {
                text = filter
                isCheckable = true
                isChecked = filter == selectedFilter
                setChipBackgroundColorResource(
                    if (filter == selectedFilter) R.color.green_primary else R.color.bg_primary
                )
                setTextColor(
                    ContextCompat.getColor(
                        context,
                        if (filter == selectedFilter) R.color.white else R.color.text_secondary
                    )
                )
                chipStrokeWidth = 1f
                setChipStrokeColorResource(R.color.border_default)
                setOnClickListener { onFilterSelected(filter) }
            }
            binding.chipGroupFilter.addView(chip)
        }
    }

    private fun onFilterSelected(filter: String) {
        selectedFilter = filter
        updateList(filter)

        // 칩 색상 업데이트
        for (i in 0 until binding.chipGroupFilter.childCount) {
            val chip = binding.chipGroupFilter.getChildAt(i) as? Chip ?: continue
            val isSelected = chip.text == filter
            chip.setChipBackgroundColorResource(
                if (isSelected) R.color.green_primary else R.color.bg_primary
            )
            chip.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (isSelected) R.color.white else R.color.text_secondary
                )
            )
        }
    }

    // ── RecyclerView ──────────────────────────────────────────
    private fun setupRecyclerView() {
        adapter = PolicyAdapter { item -> showDetailBottomSheet(item) }
        binding.rvBenefits.layoutManager = LinearLayoutManager(this)
        binding.rvBenefits.adapter = adapter
    }

    private fun updateList(filter: String) {
        val filtered = if (filter == "전체") allPolicies
        else allPolicies.filter { it.category == filter }
        adapter.submitList(filtered)
        binding.tvResultCount.text = "총 ${filtered.size}개"
    }

    private fun loadPolicies() {
        lifecycleScope.launch {
            binding.tvResultCount.text = "불러오는 중..."

            try {
                val result = SupabaseClientProvider.client
                    .postgrest["policies"]
                    .select()

                allPolicies = result.decodeList<PolicyRow>().map { it.toPolicyItem() }
                selectedFilter = "전체"
                setupFilterChips(buildFilters(allPolicies))
                updateList(selectedFilter)
            } catch (e: Exception) {
                Log.e("BenefitsActivity", "Failed to load policies from Supabase", e)
                allPolicies = emptyList()
                setupFilterChips(listOf("전체"))
                updateList(selectedFilter)
                android.widget.Toast.makeText(
                    this@BenefitsActivity,
                    "정책 정보를 불러오지 못했습니다.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun buildFilters(policies: List<PolicyItem>): List<String> {
        val categories = policies
            .map { it.category }
            .filter { it.isNotBlank() }
            .distinct()
        return listOf("전체") + categories
    }

    // ── 상세 BottomSheet ──────────────────────────────────────
    private fun showDetailBottomSheet(item: PolicyItem) {
        val dialog = BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_benefit_detail, null)
        dialog.setContentView(view)

        view.findViewById<TextView>(R.id.tvDetailTitle).text = item.title
        view.findViewById<TextView>(R.id.tvDetailAmount).text = item.amount
        view.findViewById<TextView>(R.id.tvDetailDescription).text = item.description
        view.findViewById<TextView>(R.id.tvDetailTarget).text = item.target
        view.findViewById<TextView>(R.id.tvDetailPeriod).text = item.period
        view.findViewById<TextView>(R.id.tvDetailHowToApply).text = item.howToApply
        view.findViewById<TextView>(R.id.tvDetailDocuments).text =
            if (item.documents.isEmpty()) "필요 서류 없음"
            else item.documents.joinToString("\n") { "· $it" }

        view.findViewById<View>(R.id.btnApply).setOnClickListener {
            dialog.dismiss()
            // 추후 신청 화면으로 연결
        }

        dialog.show()
    }
}

// ── 데이터 모델 ────────────────────────────────────────────────
data class PolicyItem(
    val id: Long,
    val title: String,
    val amount: String,
    val category: String,
    val tags: List<String>,
    val description: String,
    val target: String,
    val period: String,
    val howToApply: String,
    val documents: List<String>,
    val isRecommended: Boolean
)

@Serializable
data class PolicyRow(
    val id: Long = 0,
    val title: String = "",
    val amount: String = "",
    val category: String = "",
    val tags: List<String> = emptyList(),
    val description: String = "",
    val target: String = "",
    val period: String = "",
    @SerialName("how_to_apply")
    val howToApply: String = "",
    val documents: List<String> = emptyList(),
    @SerialName("is_recommended")
    val isRecommended: Boolean = false
) {
    fun toPolicyItem() = PolicyItem(
        id = id,
        title = title,
        amount = amount,
        category = category,
        tags = tags,
        description = description,
        target = target,
        period = period,
        howToApply = howToApply,
        documents = documents,
        isRecommended = isRecommended
    )
}

// ── RecyclerView 어댑터 ────────────────────────────────────────
class PolicyAdapter(
    private val onItemClick: (PolicyItem) -> Unit
) : RecyclerView.Adapter<PolicyAdapter.ViewHolder>() {

    private var items: List<PolicyItem> = emptyList()

    fun submitList(list: List<PolicyItem>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_benefit, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvBenefitTitle)
        private val tvAmount: TextView = itemView.findViewById(R.id.tvBenefitAmount)
        private val tvTag1: TextView = itemView.findViewById(R.id.tvTag1)
        private val tvTag2: TextView = itemView.findViewById(R.id.tvTag2)
        private val tvRecommended: TextView = itemView.findViewById(R.id.tvRecommended)

        fun bind(item: PolicyItem) {
            tvTitle.text = item.title
            tvAmount.text = item.amount

            tvTag1.text = item.tags.getOrNull(0) ?: ""
            tvTag1.visibility = if (item.tags.isNotEmpty()) View.VISIBLE else View.GONE

            tvTag2.text = item.tags.getOrNull(1) ?: ""
            tvTag2.visibility = if (item.tags.size > 1) View.VISIBLE else View.GONE

            tvRecommended.visibility = if (item.isRecommended) View.VISIBLE else View.GONE

            itemView.setOnClickListener { onItemClick(item) }
        }
    }
}
