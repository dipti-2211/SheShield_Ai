package com.sheshield.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.view.*
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import com.sheshield.app.R
import com.sheshield.app.data.model.TripState
import com.sheshield.app.databinding.FragmentActiveTripBinding
import com.sheshield.app.ui.MainActivity
import com.sheshield.app.ui.viewmodel.UiState

/**
 * Active trip screen showing the map, trip status, and safety check-in UI.
 * Uses MapLibre v10 (with Mapbox OSS SDK internals) + OpenStreetMap tiles (free, no API key).
 */
class ActiveTripFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentActiveTripBinding? = null
    private val binding get() = _binding!!
    private var countDownTimer: CountDownTimer? = null
    private var mapboxMap: MapboxMap? = null

    // Free OpenStreetMap raster tile style — no API key needed
    private val OSM_STYLE_JSON = """
    {
      "version": 8,
      "sources": {
        "osm": {
          "type": "raster",
          "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
          "tileSize": 256,
          "attribution": "© OpenStreetMap contributors"
        }
      },
      "layers": [{
        "id": "osm-tiles",
        "type": "raster",
        "source": "osm",
        "minzoom": 0,
        "maxzoom": 19
      }]
    }
    """.trimIndent()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        // MapLibre v10 init (token not required for OSM tiles)
        Mapbox.getInstance(requireContext())
        _binding = FragmentActiveTripBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync(this)

        val vm = (activity as MainActivity).tripViewModel

        vm.activeTrip.observe(viewLifecycleOwner) { trip ->
            if (trip == null) return@observe
            binding.tvDestination.text = "→ ${trip.destinationLabel}"

            // Pan map to destination
            mapboxMap?.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(trip.destinationLat, trip.destinationLng))
                        .zoom(14.0)
                        .build()
                )
            )

            when (trip.state) {
                TripState.ACTIVE -> showNormalState()
                TripState.CHECK_IN_PENDING -> {
                    val remaining = trip.checkInDeadlineMs - System.currentTimeMillis()
                    if (remaining > 0) showCheckInState(remaining)
                }
                TripState.SOS_ACTIVE -> showSosState()
                TripState.COMPLETED, TripState.CANCELLED -> {
                    findNavController().navigate(R.id.action_activeTrip_to_home)
                }
                else -> {}
            }
        }

        vm.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is UiState.TripEnded -> findNavController().navigate(R.id.action_activeTrip_to_home)
                is UiState.Error -> Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                else -> {}
            }
        }

        binding.btnImSafe.setOnClickListener { vm.confirmSafe() }

        binding.btnSos.setOnClickListener {
            vm.triggerSos()
            findNavController().navigate(R.id.action_activeTrip_to_sos)
        }

        binding.btnSosCheckin.setOnClickListener {
            vm.triggerSos()
            findNavController().navigate(R.id.action_activeTrip_to_sos)
        }

        binding.btnEndTrip.setOnClickListener { vm.endTrip() }
    }

    override fun onMapReady(map: MapboxMap) {
        mapboxMap = map
        map.setStyle(Style.Builder().fromJson(OSM_STYLE_JSON)) { style ->
            // Enable blue location dot if ACCESS_FINE_LOCATION is granted
            if (ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                try {
                    map.locationComponent.apply {
                        activateLocationComponent(
                            LocationComponentActivationOptions
                                .builder(requireContext(), style)
                                .build()
                        )
                        isLocationComponentEnabled = true
                    }
                } catch (_: Exception) {
                    // Blue dot is optional — map still works
                }
            }

            // Default position: Kolkata; updated when trip data loads
            map.cameraPosition = CameraPosition.Builder()
                .target(LatLng(22.5726, 88.3639))
                .zoom(13.0)
                .build()
        }
    }

    // ── State display helpers ─────────────────────────────────────────────────

    private fun showNormalState() {
        countDownTimer?.cancel()
        binding.checkInPanel.visibility = View.GONE
        binding.normalPanel.visibility = View.VISIBLE
        binding.tvStatus.text = "🟢 Monitoring Active"
        binding.tvStatus.setTextColor(resources.getColor(R.color.risk_low, null))
    }

    private fun showCheckInState(remainingMs: Long) {
        binding.normalPanel.visibility = View.GONE
        binding.checkInPanel.visibility = View.VISIBLE
        binding.tvStatus.text = "⚠️ Safety Check-In Required"
        binding.tvStatus.setTextColor(resources.getColor(R.color.risk_medium, null))

        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(remainingMs, 1000) {
            override fun onTick(ms: Long) {
                binding.tvCountdown.text = "SOS in ${ms / 1000}s"
            }
            override fun onFinish() {
                binding.tvCountdown.text = "Triggering SOS..."
                (activity as? MainActivity)?.tripViewModel?.triggerSos()
            }
        }.start()
    }

    private fun showSosState() {
        countDownTimer?.cancel()
        binding.normalPanel.visibility = View.GONE
        binding.checkInPanel.visibility = View.GONE
        binding.tvStatus.text = "🆘 SOS Active – Contacting help"
        binding.tvStatus.setTextColor(resources.getColor(R.color.risk_high, null))
    }

    // ── MapView lifecycle (required by MapLibre/Mapbox SDK) ──────────────────

    override fun onStart() { super.onStart(); _binding?.mapView?.onStart() }
    override fun onResume() { super.onResume(); _binding?.mapView?.onResume() }
    override fun onPause() { super.onPause(); _binding?.mapView?.onPause() }
    override fun onStop() { super.onStop(); _binding?.mapView?.onStop() }
    override fun onLowMemory() { super.onLowMemory(); _binding?.mapView?.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        _binding?.mapView?.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        countDownTimer?.cancel()
        _binding?.mapView?.onDestroy()
        _binding = null
        super.onDestroyView()
    }
}
