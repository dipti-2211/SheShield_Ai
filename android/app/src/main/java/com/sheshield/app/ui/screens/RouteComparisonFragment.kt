package com.sheshield.app.ui.screens

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.sheshield.app.R
import com.sheshield.app.data.model.RouteOption
import com.sheshield.app.data.model.RiskLevel
import com.sheshield.app.databinding.FragmentRouteComparisonBinding
import com.sheshield.app.ui.MainActivity
import com.sheshield.app.ui.viewmodel.UiState

/**
 * Route comparison screen.
 * Displays route alternatives with risk scores and lets the user select one to start the trip.
 */
class RouteComparisonFragment : Fragment() {

    private var _binding: FragmentRouteComparisonBinding? = null
    private val binding get() = _binding!!
    private var selectedRoute: RouteOption? = null
    private var routes: List<RouteOption> = emptyList()
    private var plannedTripId: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentRouteComparisonBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val vm = (activity as MainActivity).tripViewModel

        vm.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is UiState.RoutesReady -> {
                    routes = state.routes
                    plannedTripId = state.tripId
                    showRoutes(routes)
                }
                is UiState.TripStarted -> {
                    findNavController().navigate(R.id.action_routeComparison_to_activeTrip)
                }
                is UiState.Loading -> {
                    binding.btnStartTrip.isEnabled = false
                }
                is UiState.Error -> {
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    binding.btnStartTrip.isEnabled = selectedRoute != null
                }
                else -> {}
            }
        }

        binding.btnStartTrip.setOnClickListener {
            val route = selectedRoute ?: run {
                Toast.makeText(requireContext(), "Please select a route", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Use demo origin for now - in production use actual location
            vm.startTrip(
                selectedRoute = route,
                originLat = 22.5726, originLng = 88.3639,
                destLat = 22.5800, destLng = 88.3900,
                destLabel = "Selected Destination"
            )
        }
    }

    private fun showRoutes(routes: List<RouteOption>) {
        binding.rvRoutes.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRoutes.adapter = RouteAdapter(routes) { route ->
            selectedRoute = route
            binding.btnStartTrip.isEnabled = true
            val isDemoNote = if (route.isDemoData) "\n⚠️ Demo data — not real crime records" else ""
            binding.tvSelectedRouteSummary.text =
                "${route.label}\n" +
                "Risk: ${route.riskLevel.name} (score: ${"%.0f".format(route.riskScore * 100)}%)\n" +
                "${route.riskSummary}$isDemoNote"
            binding.tvSelectedRouteSummary.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
