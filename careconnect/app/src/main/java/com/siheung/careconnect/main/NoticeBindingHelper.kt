package com.siheung.careconnect.main

import android.view.View
import android.widget.TextView
import com.siheung.careconnect.R

/**
 * 공지사항 데이터 모델
 */
data class NoticeItem(
    val text: String,
    val date: String,
    val isRead: Boolean = false
)

/**
 * layout_main_notice.xml 에 포함된 item_notice 3개를
 * 데이터로 바인딩하는 헬퍼 오브젝트
 *
 * 사용법 (MainActivity.kt):
 *   NoticeBindingHelper.bind(
 *       rootView  = findViewById(android.R.id.content),
 *       items     = NoticeBindingHelper.sampleNotices
 *   )
 */
object NoticeBindingHelper {

    /** 샘플 공지 데이터 */
    val sampleNotices = listOf(
        NoticeItem(
            text = "5월 긴급돌봄 지원금 신청 마감 안내",
            date = "05.01",
            isRead = false
        ),
        NoticeItem(
            text = "어린이날 연휴 보육기관 운영 안내",
            date = "04.28",
            isRead = false
        ),
        NoticeItem(
            text = "2026년 시흥시 신규 보육 정책 안내",
            date = "04.15",
            isRead = true
        )
    )

    /**
     * @param rootView  activity 또는 fragment 의 루트 뷰
     * @param items     표시할 공지 목록 (최대 3개)
     */
    fun bind(rootView: View, items: List<NoticeItem>) {
        val noticeIds = listOf(R.id.notice1, R.id.notice2, R.id.notice3)

        items.take(3).forEachIndexed { index, item ->
            val noticeView = rootView.findViewById<View>(noticeIds[index])
            noticeView?.let {
                it.findViewById<TextView>(R.id.tvNoticeText)?.text = item.text
                it.findViewById<TextView>(R.id.tvNoticeDate)?.text = item.date

                // 읽음 여부에 따라 점 색상 변경
                val dot = it.findViewById<View>(R.id.noticeDot)
                dot?.alpha = if (item.isRead) 0.35f else 1.0f
            }
        }
    }
}
