package com.sheshield.app.ui.screens

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.sheshield.app.R
import com.sheshield.app.data.model.RiskLevel
import com.sheshield.app.data.model.RouteOption
import com.sheshield.app.databinding.ItemRouteBinding
import kotlin.math.roundToInt

class RouteAdapter(
    private val routes: List<RouteOption>,
    private val onSelect: (RouteOption) -> Unit
) : RecyclerView.Adapter<RouteAdapter.ViewHolder>() {

    private var selectedIdx = -1

    inner class ViewHolder(val b: ItemRouteBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemRouteBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = routes.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val route = routes[position]
        val b = holder.b
        val ctx = b.root.context

        b.tvRouteLabel.text = route.label
        b.tvDuration.text = "${route.durationSeconds / 60} min"
        b.tvDistance.text = "${"%.1f".format(route.distanceMeters / 1000)} km"
        b.tvRiskLevel.text = route.riskLevel.name
        b.tvIncidents.text = "${route.incidentCount} reported incidents nearby"
        b.tvRiskSummary.text = route.riskSummary

        if (route.isDemoData) {
            b.tvDemoLabel.visibility = android.view.View.VISIBLE
        }

        // Risk colour coding
        val riskColor = when (route.riskLevel) {
            RiskLevel.LOW    -> ContextCompat.getColor(ctx, R.color.risk_low)
            RiskLevel.MEDIUM -> ContextCompat.getColor(ctx, R.color.risk_medium)
            RiskLevel.HIGH   -> ContextCompat.getColor(ctx, R.color.risk_high)
            else             -> ContextCompat.getColor(ctx, R.color.risk_unknown)
        }
        b.tvRiskLevel.setTextColor(riskColor)
        b.riskBar.setIndicatorColor(riskColor)
        b.riskBar.progress = (route.riskScore * 100).roundToInt()

        // Selection highlight
        b.root.isSelected = (position == selectedIdx)
        b.root.strokeColor = if (position == selectedIdx)
            ContextCompat.getColor(ctx, R.color.purple_primary)
        else
            ContextCompat.getColor(ctx, R.color.surface_variant)

        b.root.setOnClickListener {
            val prev = selectedIdx
            selectedIdx = position
            notifyItemChanged(prev)
            notifyItemChanged(position)
            onSelect(route)
        }
    }
}
