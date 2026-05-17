package com.siheung.careconnect.reservation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.siheung.careconnect.R
import java.util.Locale

class FacilityAdapter(
    private var items: List<ChildcareFacility>,
    private val onItemClick: (ChildcareFacility) -> Unit,
    private val onReserveClick: (ChildcareFacility) -> Unit
) : RecyclerView.Adapter<FacilityAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvFacilityName)
        val tvAddress: TextView = view.findViewById(R.id.tvFacilityAddress)
        val tvDistance: TextView = view.findViewById(R.id.tvFacilityDistance)
        val tvStatus: TextView = view.findViewById(R.id.tvStatusBadge)
        val btnReserve: Button = view.findViewById(R.id.btnReserve)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_facility, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvName.text = item.name
        holder.tvAddress.text = item.address
        holder.tvStatus.text = item.status

        if (item.distance > 0f) {
            val distanceKm = item.distance / 1000
            holder.tvDistance.text = String.format(Locale.getDefault(), "내 위치에서 %.1fkm", distanceKm)
        } else {
            holder.tvDistance.text = "거리 정보 없음"
        }

        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.btnReserve.setOnClickListener { onReserveClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<ChildcareFacility>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun moveToTop(facilityId: String) {
        val idx = items.indexOfFirst { it.id == facilityId }
        if (idx <= 0) return
        val reordered = items.toMutableList()
        reordered.add(0, reordered.removeAt(idx))
        items = reordered
        notifyDataSetChanged()
    }
}
