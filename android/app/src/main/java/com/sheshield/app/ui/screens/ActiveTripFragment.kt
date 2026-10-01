package com.sheshield.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.annotations.PolylineOptions
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import com.sheshield.app.R
import com.sheshield.app.data.model.RouteOption
import com.sheshield.app.data.model.TripState
import com.sheshield.app.databinding.FragmentActiveTripBinding
import com.sheshield.app.ui.MainActivity
import com.sheshield.app.ui.viewmodel.UiState

/**
 * Active trip screen showing the map, trip status, and safety check-in UI.
 *
 * Map features:
 *  - Blue pulsing dot  = current user location (via MapLibre LocationComponent)
 *  - Blue polyline     = the selected route geometry
 *  - Red marker        = destination
 *
 * SOS: tapping the SOS button triggers SMS to all trusted contacts via SmsHelper.
 */
class ActiveTripFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentActiveTripBinding? = null
    private val binding get() = _binding!!
    private var countDownTimer: CountDownTimer? = null
    private var mapboxMap: MapboxMap? = null

    // Route to draw (set once the active trip loads)
    private var pendingRoute: RouteOption? = null
    private var routeDrawn = false

    // SMS permission launcher
    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                requireContext(),
                "SMS permission denied — contacts won't receive text alerts",
                Toast.LENGTH_LONG
            ).show()
        }
    }

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

        // Request SMS permission proactively so SOS can text contacts
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
        }

        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync(this)

        val vm = (activity as MainActivity).tripViewModel

        vm.activeTrip.observe(viewLifecycleOwner) { trip ->
            if (trip == null) return@observe
            binding.tvDestination.text = "→ ${trip.destinationLabel}"

            // Try to get the matching route geometry from the ViewModel
            val route = vm.plannedRoutes.firstOrNull { it.routeId == trip.selectedRouteId }
                ?: vm.plannedRoutes.firstOrNull()

            if (route != null && !routeDrawn) {
                pendingRoute = route
                drawRouteOnMap(route, trip.destinationLat, trip.destinationLng)
            } else {
                // No route geometry — just pan to destination
                mapboxMap?.animateCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .target(LatLng(trip.destinationLat, trip.destinationLng))
                            .zoom(14.0)
                            .build()
                    )
                )
            }

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

    // ── Map ready ─────────────────────────────────────────────────────────────

    override fun onMapReady(map: MapboxMap) {
        mapboxMap = map
        map.setStyle(Style.Builder().fromJson(OSM_STYLE_JSON)) { style ->
            // Enable blue pulsing location dot if permission granted
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

            // Default camera; gets updated when route/trip data loads
            map.cameraPosition = CameraPosition.Builder()
                .target(LatLng(22.5726, 88.3639))
                .zoom(13.0)
                .build()

            // Draw pending route if it arrived before the map was ready
            pendingRoute?.let { route ->
                val vm = (activity as? MainActivity)?.tripViewModel
                val trip = vm?.activeTrip?.value
                if (trip != null) {
                    drawRouteOnMap(route, trip.destinationLat, trip.destinationLng)
                }
            }
        }
    }

    // ── Route drawing ─────────────────────────────────────────────────────────

    /**
     * Draws the selected route as a blue polyline with:
     *  - Blue start marker  (current/origin location from route[0])
     *  - Red destination marker
     *  - Blue polyline along route geometry
     * Then fits the camera to show the entire route.
     */
    private fun drawRouteOnMap(route: RouteOption, destLat: Double, destLng: Double) {
        val map = mapboxMap ?: return
        if (map.style == null) {
            // Map not ready yet — will draw when onMapReady fires
            pendingRoute = route
            return
        }

        // Clear previous overlays
        map.clear()
        routeDrawn = true

        val routePoints = route.geometry.map { LatLng(it.latitude, it.longitude) }

        if (routePoints.size >= 2) {
            // ── Blue route polyline ──────────────────────────────────────────
            map.addPolyline(
                PolylineOptions()
                    .addAll(routePoints)
                    .color(Color.parseColor("#1E88E5"))   // Material Blue 600
                    .width(5f)
            )

            // ── Blue start marker (origin = first geometry point) ────────────
            map.addMarker(
                MarkerOptions()
                    .position(routePoints.first())
                    .title("Start")
                    .snippet("Your starting location")
            )
        }

        // ── Red destination marker ───────────────────────────────────────────
        val destPoint = LatLng(destLat, destLng)
        map.addMarker(
            MarkerOptions()
                .position(destPoint)
                .title("Destination")
                .snippet("Your destination")
        )

        // ── Fit camera to show full route ────────────────────────────────────
        if (routePoints.size >= 2) {
            try {
                val bounds = LatLngBounds.Builder()
                    .includes(routePoints)
                    .build()
                map.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(bounds, 80 /* padding dp */)
                )
            } catch (_: Exception) {
                // Fallback: zoom to midpoint
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(routePoints[routePoints.size / 2], 14.0)
                )
            }
        } else {
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(destPoint, 14.0)
            )
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
