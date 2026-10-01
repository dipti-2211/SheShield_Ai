package com.sheshield.app.ui.screens

import android.location.Geocoder
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.sheshield.app.R
import com.sheshield.app.databinding.FragmentTripPlanningBinding
import com.sheshield.app.ui.MainActivity
import com.sheshield.app.ui.viewmodel.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
                    findNavController().navigate(R.id.action_tripPlanning_to_routeComparison)
                }
                is UiState.Error -> {
                    setLoadingState(false)
                    Toast.makeText(requireContext(),
                        state.message + if (state.isNetworkError) " (using demo data)" else "",
                        Toast.LENGTH_LONG).show()
                    // Still navigate if routes were recovered with demo data
                    if (state.isNetworkError && vm.plannedRoutes.isNotEmpty()) {
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
        // Fixed origin (Kolkata) for demo – in production use FusedLocationProviderClient
        val originLat = 22.5726
        val originLng = 88.3639

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    try {
                        val gc = Geocoder(requireContext(), Locale.getDefault())
                        @Suppress("DEPRECATION")
                        val results = gc.getFromLocationName(address, 1)
                        if (!results.isNullOrEmpty()) {
                            Pair(results[0].latitude, results[0].longitude)
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        null
                    }
                }

                if (result != null) {
                    (activity as MainActivity).tripViewModel.planTrip(
                        originLat = originLat,
                        originLng = originLng,
                        destLat = result.first,
                        destLng = result.second,
                        destLabel = address
                    )
                } else {
                    // Geocoding failed or returned no results.
                    // Fall back to a Kolkata demo destination so the app still works.
                    Toast.makeText(
                        requireContext(),
                        "Could not geocode \"$address\" – using demo destination",
                        Toast.LENGTH_SHORT
                    ).show()
                    (activity as MainActivity).tripViewModel.planTrip(
                        originLat = originLat,
                        originLng = originLng,
                        destLat = 22.5900,
                        destLng = 88.3800,
                        destLabel = address
                    )
                }
            } catch (e: Exception) {
                setLoadingState(false)
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
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
