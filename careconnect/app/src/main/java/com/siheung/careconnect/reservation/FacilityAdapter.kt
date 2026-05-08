package com.siheung.careconnect.reservation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.siheung.careconnect.R
import java.util.Locale

class FacilityAdapter(
    private var items: List<ChildcareFacility>,
    private val onItemClick: (ChildcareFacility) -> Unit
) : RecyclerView.Adapter<FacilityAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvFacilityName)
        val tvDistance: TextView = view.findViewById(R.id.tvFacilityDistance)
        val tvStatus: TextView = view.findViewById(R.id.tvStatusBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_facility, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvName.text = item.name
        
        // 거리 포맷팅 (km)
        val distanceKm = item.distance / 1000
        holder.tvDistance.text = String.format(Locale.getDefault(), "내 위치에서 %.1fkm", distanceKm)
        
        holder.tvStatus.text = item.status

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<ChildcareFacility>) {
        items = newItems
        notifyDataSetChanged()
    }
}
