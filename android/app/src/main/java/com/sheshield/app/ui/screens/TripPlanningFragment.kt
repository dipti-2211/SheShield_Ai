package com.sheshield.app.ui.screens

import android.location.Geocoder
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.sheshield.app.R
import com.sheshield.app.databinding.FragmentTripPlanningBinding
import com.sheshield.app.ui.MainActivity
import com.sheshield.app.ui.viewmodel.UiState
import java.util.Locale

/**
 * Trip planning screen.
 * User enters a destination; the app geocodes it and calls planTrip.
 * Shows a loading state while the backend calculates route risk.
 */
class TripPlanningFragment : Fragment() {

    private var _binding: FragmentTripPlanningBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentTripPlanningBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val vm = (activity as MainActivity).tripViewModel

        vm.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is UiState.Loading -> setLoadingState(true)
                is UiState.RoutesReady -> {
                    setLoadingState(false)
                    // Navigate to route comparison
                    findNavController().navigate(R.id.action_tripPlanning_to_routeComparison)
                }
                is UiState.Error -> {
                    setLoadingState(false)
                    Toast.makeText(requireContext(),
                        state.message + if (state.isNetworkError) " (using demo data)" else "",
                        Toast.LENGTH_LONG).show()
                    // Still navigate if routes were recovered with demo data
                    if (state.isNetworkError) {
                        findNavController().navigate(R.id.action_tripPlanning_to_routeComparison)
                    }
                }
                else -> setLoadingState(false)
            }
        }

        binding.btnFindRoutes.setOnClickListener {
            val destText = binding.etDestination.text.toString().trim()
            if (destText.isEmpty()) {
                binding.etDestination.error = "Enter a destination"
                return@setOnClickListener
            }
            geocodeAndPlan(destText)
        }
    }

    private fun geocodeAndPlan(address: String) {
        setLoadingState(true)
        // Use a rough fixed origin for demo (Kolkata, matching original workflow coords)
        // In a real app, use FusedLocationProviderClient.lastLocation
        val originLat = 22.5726
        val originLng = 88.3639

        try {
            val gc = Geocoder(requireContext(), Locale.getDefault())
            @Suppress("DEPRECATION")
            val results = gc.getFromLocationName(address, 1)
            if (results.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "Location not found", Toast.LENGTH_SHORT).show()
                setLoadingState(false)
                return
            }
            val dest = results[0]
            (activity as MainActivity).tripViewModel.planTrip(
                originLat = originLat,
                originLng = originLng,
                destLat = dest.latitude,
                destLng = dest.longitude,
                destLabel = address
            )
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Geocoding failed: ${e.message}", Toast.LENGTH_SHORT).show()
            setLoadingState(false)
        }
    }

    private fun setLoadingState(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnFindRoutes.isEnabled = !loading
        binding.etDestination.isEnabled = !loading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
