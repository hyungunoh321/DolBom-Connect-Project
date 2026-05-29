package com.siheung.careconnect.benefits

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
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
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import java.time.LocalDate
import java.time.Period
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class BenefitsActivity : AppCompatActivity() {

    private companion object {
        const val BOKJIRO_APPLY_URL = "https://www.bokjiro.go.kr/ssis-tbu/index.do"
    }

    private lateinit var binding: ActivityBenefitsBinding
    private lateinit var adapter: PolicyAdapter
    private var selectedFilter = "전체"
    private var allPolicies: List<PolicyItem> = emptyList()

    private var childCount: Int = 1
    private var childAgeMonths: Int = 24
    private var incomeLevel: Int = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBenefitsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        childCount = intent.getIntExtra("child_count", 1)
        childAgeMonths = intent.getIntExtra("child_age_months", 24)
        incomeLevel = intent.getIntExtra("income_level", 5)

        setupToolbar()
        setupConditionBadge()
        setupRecyclerView()
        setupFilterChips(listOf("전체"))
        updateList("전체")
        loadUserCondition()
        loadPolicies()
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun setupConditionBadge() {
        val ageText = when {
            childAgeMonths < 12 -> "${childAgeMonths}개월"
            childAgeMonths < 24 -> "1세"
            childAgeMonths < 36 -> "2세"
            childAgeMonths < 48 -> "3세"
            childAgeMonths < 60 -> "4세"
            childAgeMonths < 72 -> "5세"
            childAgeMonths < 84 -> "6세"
            childAgeMonths < 96 -> "7세"
            else -> "${childAgeMonths / 12}세"
        }
        binding.tvResultCount.text = "자녀 ${childCount}명 · $ageText · 소득분위 $incomeLevel"
        binding.tvConditionBadge.text = "자녀 ${childCount}명 · 소득분위 $incomeLevel"
    }

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

    private fun setupRecyclerView() {
        adapter = PolicyAdapter { item -> showDetailBottomSheet(item) }
        binding.rvBenefits.layoutManager = LinearLayoutManager(this)
        binding.rvBenefits.adapter = adapter
    }

    private fun updateList(filter: String) {
        val categoryFiltered = if (filter == "전체") {
            allPolicies
        } else {
            allPolicies.filter { it.category == filter }
        }
        val conditionFiltered = categoryFiltered.filter { matchesCondition(it) }

        adapter.submitList(conditionFiltered)
        binding.tvResultCount.text = "총 ${conditionFiltered.size}개"
    }

    private fun matchesCondition(policy: PolicyItem): Boolean {
        val combined = (policy.target + " " + policy.tags.joinToString(" ")).lowercase()

        val incomeMatch = when {
            combined.contains("소득무관") || combined.contains("전체") -> true
            combined.contains("차상위") -> incomeLevel <= 2
            combined.contains("기초") -> incomeLevel == 1
            else -> {
                val match = Regex("소득분위\\s*(\\d+)\\s*이하").find(combined)
                if (match != null) incomeLevel <= (match.groupValues[1].toIntOrNull() ?: 10) else true
            }
        }

        val ageMatch = when {
            combined.contains("0~11개월") || combined.contains("만 0세") -> childAgeMonths in 0..11
            combined.contains("12~23개월") || combined.contains("만 1세") -> childAgeMonths in 12..23
            combined.contains("24~35개월") || combined.contains("2세") -> childAgeMonths in 24..35
            combined.contains("36~83개월") || combined.contains("3~5세") -> childAgeMonths in 36..83
            combined.contains("24~86개월") -> childAgeMonths in 24..86
            combined.contains("만 8세 미만") || combined.contains("0-8세") -> childAgeMonths < 96
            combined.contains("만 12세") -> childAgeMonths < 144
            combined.contains("0~1세") -> childAgeMonths < 24
            else -> true
        }

        return incomeMatch && ageMatch
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
                Toast.makeText(
                    this@BenefitsActivity,
                    "정책 정보를 불러오지 못했습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun loadUserCondition() {
        val userId = SupabaseClientProvider.client.auth.currentUserOrNull()?.id ?: return

        lifecycleScope.launch {
            val children = try {
                SupabaseClientProvider.client
                    .postgrest["children"]
                    .select(Columns.raw("parent_id,birth_date,income_level")) {
                        filter {
                            eq("parent_id", userId)
                        }
                    }
                    .decodeList<BenefitChildRow>()
            } catch (e: Exception) {
                emptyList()
            }

            if (children.isNotEmpty()) {
                childCount = children.size
                childAgeMonths = children.mapNotNull { it.ageMonths() }.minOrNull() ?: childAgeMonths
                incomeLevel = children.mapNotNull { it.incomeLevel }.minOrNull() ?: incomeLevel
                setupConditionBadge()
                updateList(selectedFilter)
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

        val applyUrl = item.resolveApplyUrl()
        val btnApply = view.findViewById<View>(R.id.btnApply)
        btnApply.visibility = if (item.isVisitOnlyApplication()) View.GONE else View.VISIBLE
        btnApply.setOnClickListener {
            if (applyUrl.isBlank()) {
                Toast.makeText(this, "신청 링크가 등록되지 않았습니다.", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(applyUrl.withHttpScheme())))
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun PolicyItem.isVisitOnlyApplication(): Boolean {
        val method = howToApply.lowercase()
        val hasVisit = method.contains("방문")
        val hasNonVisitMethod = listOf("온라인", "인터넷", "홈페이지", "웹", "전화", "팩스", "우편", "모바일")
            .any { method.contains(it) }

        return hasVisit && !hasNonVisitMethod
    }

    private fun PolicyItem.resolveApplyUrl(): String {
        if (howToApply.contains("복지로")) return BOKJIRO_APPLY_URL
        return applyUrl.ifBlank { howToApply.extractFirstUrl() }
    }

    private fun String.extractFirstUrl(): String {
        val urlRegex = Regex("""https?://[^\s,]+|www\.[^\s,]+""")
        return urlRegex.find(this)?.value.orEmpty()
    }

    private fun String.withHttpScheme(): String =
        if (startsWith("http://") || startsWith("https://")) this else "https://$this"
}

@Serializable
data class BenefitChildRow(
    @SerialName("parent_id")
    val parentId: String = "",
    @SerialName("birth_date")
    val birthDate: String = "",
    @SerialName("income_level")
    val incomeLevel: Int? = null
) {
    fun ageMonths(): Int? = runCatching {
        val birth = LocalDate.parse(birthDate.take(10))
        val period = Period.between(birth, LocalDate.now())
        period.years * 12 + period.months
    }.getOrNull()
}

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
    val applyUrl: String,
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
    @SerialName("howToApply")
    val howToApply: String = "",
    @SerialName("howtoapply")
    val howToApplyLower: String = "",
    @SerialName("how_to_apply")
    val howToApplySnake: String = "",
    @SerialName("apply_method")
    val applyMethod: String = "",
    @SerialName("application_method")
    val applicationMethod: String = "",
    @SerialName("applyUrl")
    val applyUrl: String = "",
    @SerialName("apply_url")
    val applyUrlSnake: String = "",
    @SerialName("url")
    val url: String = "",
    @SerialName("homepageUrl")
    val homepageUrl: String = "",
    @SerialName("homepage_url")
    val homepageUrlSnake: String = "",
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
        howToApply = listOf(howToApply, howToApplyLower, howToApplySnake, applyMethod, applicationMethod)
            .firstOrNull { it.isNotBlank() }
            .orEmpty(),
        applyUrl = listOf(applyUrl, applyUrlSnake, url, homepageUrl, homepageUrlSnake)
            .firstOrNull { it.isNotBlank() }
            .orEmpty(),
        documents = documents,
        isRecommended = isRecommended
    )
}

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

            tvTag1.text = item.tags.getOrNull(0).orEmpty()
            tvTag1.visibility = if (item.tags.isNotEmpty()) View.VISIBLE else View.GONE

            tvTag2.text = item.tags.getOrNull(1).orEmpty()
            tvTag2.visibility = if (item.tags.size > 1) View.VISIBLE else View.GONE

            tvRecommended.visibility = if (item.isRecommended) View.VISIBLE else View.GONE
            itemView.setOnClickListener { onItemClick(item) }
        }
    }
}
