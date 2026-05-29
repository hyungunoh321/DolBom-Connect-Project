package com.siheung.careconnect.reservation

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.siheung.careconnect.R
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ReservationStatusAdapter(
    private val onCancelClick: (ReservationItem) -> Unit
) : RecyclerView.Adapter<ReservationStatusAdapter.ViewHolder>() {

    private var items: List<ReservationItem> = emptyList()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvFacilityName: TextView = view.findViewById(R.id.tvFacilityName)
        val tvFacilityAddress: TextView = view.findViewById(R.id.tvFacilityAddress)
        val tvChildName: TextView = view.findViewById(R.id.tvChildName)
        val tvReservedAt: TextView = view.findViewById(R.id.tvReservedAt)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val btnCancel: Button = view.findViewById(R.id.btnCancel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reservation_status, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context

        holder.tvFacilityName.text = item.facilities?.name ?: "시설 정보 없음"
        holder.tvFacilityAddress.text = item.facilities?.address ?: ""
        holder.tvChildName.text = item.children?.name ?: "정보 없음"

        val kst = ZoneId.of("Asia/Seoul")
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

        // 돌봄 시간 표시 (start_time/end_time 있으면 우선 표시)
        val careTimeText = try {
            if (item.start_time != null && item.end_time != null) {
                val start = OffsetDateTime.parse(item.start_time).atZoneSameInstant(kst).format(timeFmt)
                val end   = OffsetDateTime.parse(item.end_time).atZoneSameInstant(kst).format(timeFmt)
                val date  = OffsetDateTime.parse(item.start_time).atZoneSameInstant(kst)
                "${date.year}년 ${date.monthValue}월 ${date.dayOfMonth}일  $start ~ $end"
            } else {
                val parts = item.reserved_at.take(10).split("-")
                "${parts[0]}년 ${parts[1].toInt()}월 ${parts[2].toInt()}일"
            }
        } catch (e: Exception) {
            item.reserved_at.take(10)
        }
        holder.tvReservedAt.text = careTimeText

        holder.tvStatus.text = item.status
        val badgeColor = when (item.status) {
            "대기" -> Color.parseColor("#FFA000")
            "확정" -> ctx.getColor(R.color.green_primary)
            "완료" -> Color.parseColor("#1976D2")
            "취소" -> Color.parseColor("#9E9E9E")
            else -> Color.LTGRAY
        }
        val density = ctx.resources.displayMetrics.density
        holder.tvStatus.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 4f * density
            setColor(badgeColor)
        }

        val cancellable = item.status == "대기" || item.status == "확정"
        holder.btnCancel.visibility = if (cancellable) View.VISIBLE else View.GONE
        holder.btnCancel.setOnClickListener { onCancelClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<ReservationItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
