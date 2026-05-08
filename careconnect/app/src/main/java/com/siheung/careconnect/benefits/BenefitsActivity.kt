package com.siheung.careconnect.benefits

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.siheung.careconnect.R
import com.siheung.careconnect.databinding.ActivityBenefitsBinding

class BenefitsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBenefitsBinding
    private lateinit var adapter: BenefitAdapter
    private var selectedFilter = "전체"

    // ── 전체 혜택 데이터 ──────────────────────────────────────
    private val allBenefits = listOf(
        BenefitItem(
            id = 1,
            title = "시흥시 긴급돌봄 지원금",
            amount = "월 30만원",
            category = "긴급돌봄",
            tags = listOf("자녀 2명 이상", "소득분위 5이하"),
            description = "맞벌이 가정의 긴급 돌봄 비용을 지원합니다. 보육기관 이용 시 발생하는 비용의 일부를 시에서 보조합니다.",
            target = "자녀 2명 이상 + 소득분위 5분위 이하 가정",
            period = "2026.01.01 ~ 2026.12.31",
            howToApply = "시흥시청 복지과 방문 또는 복지로(www.bokjiro.go.kr) 온라인 신청",
            documents = listOf("주민등록등본", "소득확인서", "아동 기본증명서", "재직증명서"),
            isRecommended = true
        ),
        BenefitItem(
            id = 2,
            title = "영아 양육수당",
            amount = "월 20만원",
            category = "양육",
            tags = listOf("24개월 미만"),
            description = "어린이집을 이용하지 않는 영아를 가정에서 양육하는 경우 지원합니다.",
            target = "만 24개월 미만 영아를 가정에서 양육하는 가정",
            period = "신청월부터 24개월 도달 전월까지",
            howToApply = "읍·면·동 주민센터 방문 또는 복지로 온라인 신청",
            documents = listOf("주민등록등본", "영아 기본증명서"),
            isRecommended = false
        ),
        BenefitItem(
            id = 3,
            title = "다자녀 특별 혜택",
            amount = "연 50만원",
            category = "다자녀",
            tags = listOf("자녀 3명 이상", "시흥시 거주"),
            description = "세 자녀 이상 가정을 위한 특별 교육비 지원 사업입니다.",
            target = "시흥시 거주 자녀 3명 이상 가정",
            period = "2026.03.01 ~ 2026.11.30",
            howToApply = "시흥시청 가족정책과 방문 신청",
            documents = listOf("주민등록등본", "가족관계증명서", "소득확인서"),
            isRecommended = true
        ),
        BenefitItem(
            id = 4,
            title = "한부모 돌봄 지원",
            amount = "월 15만원",
            category = "한부모",
            tags = listOf("한부모 가정", "소득분위 3이하"),
            description = "한부모 가정의 안정적인 돌봄 환경 조성을 위해 돌봄 비용을 지원합니다.",
            target = "한부모 가정 + 소득분위 3분위 이하",
            period = "2026.01.01 ~ 2026.12.31",
            howToApply = "읍·면·동 주민센터 방문 신청",
            documents = listOf("한부모가족 증명서", "소득확인서", "주민등록등본"),
            isRecommended = false
        ),
        BenefitItem(
            id = 5,
            title = "보육료 지원",
            amount = "월 최대 49만원",
            category = "긴급돌봄",
            tags = listOf("어린이집 이용", "만 0~5세"),
            description = "어린이집을 이용하는 만 0~5세 아동의 보육료를 지원합니다.",
            target = "어린이집 이용 만 0~5세 아동",
            period = "상시 신청",
            howToApply = "읍·면·동 주민센터 방문 또는 복지로 온라인 신청",
            documents = listOf("주민등록등본", "아동 기본증명서"),
            isRecommended = false
        ),
        BenefitItem(
            id = 6,
            title = "아동수당",
            amount = "월 10만원",
            category = "양육",
            tags = listOf("만 8세 미만"),
            description = "만 8세 미만 아동에게 월 10만원의 아동수당을 지급합니다.",
            target = "만 8세 미만 아동",
            period = "상시 신청",
            howToApply = "읍·면·동 주민센터 방문 또는 복지로 온라인 신청",
            documents = listOf("주민등록등본", "아동 기본증명서"),
            isRecommended = true
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBenefitsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupFilterChips()
        setupRecyclerView()
        updateList("전체")
    }

    // ── 툴바 (뒤로가기) ───────────────────────────────────────
    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { finish() }
    }

    // ── 필터 칩 ───────────────────────────────────────────────
    private fun setupFilterChips() {
        val filters = listOf("전체", "긴급돌봄", "양육", "다자녀", "한부모")
        filters.forEach { filter ->
            val chip = Chip(this).apply {
                text = filter
                isCheckable = true
                isChecked = filter == "전체"
                setChipBackgroundColorResource(
                    if (filter == "전체") R.color.green_primary else R.color.bg_primary
                )
                setTextColor(
                    ContextCompat.getColor(
                        context,
                        if (filter == "전체") R.color.white else R.color.text_secondary
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
        adapter = BenefitAdapter { item -> showDetailBottomSheet(item) }
        binding.rvBenefits.layoutManager = LinearLayoutManager(this)
        binding.rvBenefits.adapter = adapter
    }

    private fun updateList(filter: String) {
        val filtered = if (filter == "전체") allBenefits
        else allBenefits.filter { it.category == filter }
        adapter.submitList(filtered)
        binding.tvResultCount.text = "총 ${filtered.size}개"
    }

    // ── 상세 BottomSheet ──────────────────────────────────────
    private fun showDetailBottomSheet(item: BenefitItem) {
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
            item.documents.joinToString("\n") { "· $it" }

        view.findViewById<View>(R.id.btnApply).setOnClickListener {
            dialog.dismiss()
            // 추후 신청 화면으로 연결
        }

        dialog.show()
    }
}

// ── 데이터 모델 ────────────────────────────────────────────────
data class BenefitItem(
    val id: Int,
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

// ── RecyclerView 어댑터 ────────────────────────────────────────
class BenefitAdapter(
    private val onItemClick: (BenefitItem) -> Unit
) : RecyclerView.Adapter<BenefitAdapter.ViewHolder>() {

    private var items: List<BenefitItem> = emptyList()

    fun submitList(list: List<BenefitItem>) {
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

        fun bind(item: BenefitItem) {
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
