package com.siheung.careconnect.realtime

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.siheung.careconnect.R

class NoticeListAdapter(
    private val items: MutableList<Notice>
) : RecyclerView.Adapter<NoticeListAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dot: View = itemView.findViewById(R.id.noticeDot)
        val tvText: TextView = itemView.findViewById(R.id.tvNoticeText)
        val tvDate: TextView = itemView.findViewById(R.id.tvNoticeDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notice, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val notice = items[position]
        holder.tvText.text = notice.text
        holder.tvDate.text = notice.date
        holder.dot.alpha = if (notice.isRead) 0.35f else 1.0f
    }

    override fun getItemCount(): Int = items.size
}
