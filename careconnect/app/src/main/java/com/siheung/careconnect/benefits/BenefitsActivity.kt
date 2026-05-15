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

    // ── 사용자 조건 (Intent로 전달받음) ──────────────────────
    // TODO: 로그인 연동 완료 후 Supabase children/users 테이블에서 실제 데이터로 교체
    private var childCount: Int = 1
    private var childAgeMonths: Int = 24  // 첫째 자녀 나이(개월)
    private var incomeLevel: Int = 5       // 소득분위 1~10

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBenefitsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Intent에서 사용자 조건 받기 (없으면 목데이터 기본값 사용)
        childCount     = intent.getIntExtra("child_count", 1)
        childAgeMonths = intent.getIntExtra("child_age_months", 24)
        incomeLevel    = intent.getIntExtra("income_level", 5)

        setupToolbar()
        setupConditionBadge()
        setupRecyclerView()
        setupFilterChips(listOf("전체"))
        updateList("전체")
        loadPolicies()
    }

    // ── 툴바 (뒤로가기) ───────────────────────────────────────
    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { finish() }
    }

    // ── 상단 조건 배지 ─────────────────────────────────────────
    private fun setupConditionBadge() {
        val ageText = when {
            childAgeMonths < 12  -> "${childAgeMonths}개월"
            childAgeMonths < 24  -> "1세"
            childAgeMonths < 36  -> "2세"
            childAgeMonths < 48  -> "3세"
            childAgeMonths < 60  -> "4세"
            childAgeMonths < 72  -> "5세"
            childAgeMonths < 84  -> "6세"
            childAgeMonths < 96  -> "7세"
            else                 -> "${childAgeMonths / 12}세"
        }
        binding.tvResultCount.text = "자녀 ${childCount}명 · ${ageText} · 소득분위 ${incomeLevel}"
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

    // ── 필터링 + 목록 업데이트 ────────────────────────────────
    private fun updateList(filter: String) {
        val categoryFiltered = if (filter == "전체") allPolicies
        else allPolicies.filter { it.category == filter }

        // 사용자 조건(나이/소득분위) 기반 필터링
        val conditionFiltered = categoryFiltered.filter { matchesCondition(it) }

        adapter.submitList(conditionFiltered)
        binding.tvResultCount.text = "총 ${conditionFiltered.size}개"
    }

    // ── 조건 매칭 로직 ────────────────────────────────────────
    // target, tags 텍스트를 기반으로 사용자 조건과 비교
    private fun matchesCondition(policy: PolicyItem): Boolean {
        val combined = (policy.target + " " + policy.tags.joinToString(" ")).lowercase()

        // ① 소득분위 조건
        val incomeMatch = when {
            combined.contains("소득무관") || combined.contains("전체") -> true
            combined.contains("차상위") -> incomeLevel <= 2
            combined.contains("기초") -> incomeLevel == 1
            else -> {
                val regex = Regex("소득분위\\s*(\\d+)\\s*이하")
                val match = regex.find(combined)
                if (match != null) incomeLevel <= (match.groupValues[1].toIntOrNull() ?: 10)
                else true
            }
        }

        // ② 자녀 나이(개월) 조건
        val ageMatch = when {
            combined.contains("0~11개월") || combined.contains("0세(0~11개월)") || combined.contains("만 0세") ->
                childAgeMonths in 0..11
            combined.contains("12~23개월") || combined.contains("1세(12~23개월)") || combined.contains("만 1세") ->
                childAgeMonths in 12..23
            combined.contains("24~35개월") || combined.contains("2세") ->
                childAgeMonths in 24..35
            combined.contains("36~83개월") || combined.contains("3~5세") ->
                childAgeMonths in 36..83
            combined.contains("24~86개월") ->
                childAgeMonths in 24..86
            combined.contains("만 8세 미만") || combined.contains("0-8세") ->
                childAgeMonths < 96
            combined.contains("만 12세") ->
                childAgeMonths < 144
            combined.contains("0~1세") ->
                childAgeMonths < 24
            combined.contains("0세~11개월") ->
                childAgeMonths in 0..11
            else -> true  // 나이 조건 명시 없으면 통과
        }

        return incomeMatch && ageMatch
    }

    // ── Supabase: policies 테이블 조회 ────────────────────────
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
        view.findViewById<TextView>(R.id.tvDetailHowToApply).text =
            item.howToApply.takeIf { it.isNotBlank() } ?: "신청 방법 정보를 준비 중입니다."
        view.findViewById<TextView>(R.id.tvDetailDocuments).text =
            if (item.documents.isEmpty()) "필요 서류 없음"
            else item.documents.joinToString("\n") { "· $it" }

        view.findViewById<View>(R.id.btnApply).setOnClickListener {
            dialog.dismiss()
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
    @SerialName("howtoapply")
    val howToApply: String = "",
    val documents: List<String> = emptyList(),
    @SerialName("is_recommended")
    val isRecommended: Boolean = false
) {
    fun toPolicyItem() = PolicyItem(
        id            = id,
        title         = title,
        amount        = amount,
        category      = category,
        tags          = tags,
        description   = description,
        target        = target,
        period        = period,
        howToApply    = howToApply,
        documents     = documents,
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
