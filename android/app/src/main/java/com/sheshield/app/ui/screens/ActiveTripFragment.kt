package com.sheshield.app.ui.screens

import android.os.Bundle
import android.os.CountDownTimer
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.sheshield.app.R
import com.sheshield.app.data.model.TripState
import com.sheshield.app.databinding.FragmentActiveTripBinding
import com.sheshield.app.ui.MainActivity
import com.sheshield.app.ui.viewmodel.UiState

/**
 * Active trip screen showing the map, trip status, and safety check-in UI.
 * Shows a countdown when CHECK_IN_PENDING; reveals the SOS state when active.
 */
class ActiveTripFragment : Fragment() {

    private var _binding: FragmentActiveTripBinding? = null
    private val binding get() = _binding!!
    private var countDownTimer: CountDownTimer? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentActiveTripBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val vm = (activity as MainActivity).tripViewModel

        vm.activeTrip.observe(viewLifecycleOwner) { trip ->
            if (trip == null) return@observe
            binding.tvDestination.text = "→ ${trip.destinationLabel}"

            when (trip.state) {
                TripState.ACTIVE -> {
                    showNormalState()
                }
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

        binding.btnImSafe.setOnClickListener {
            vm.confirmSafe()
        }

        binding.btnSos.setOnClickListener {
            vm.triggerSos()
            findNavController().navigate(R.id.action_activeTrip_to_sos)
        }

        binding.btnEndTrip.setOnClickListener {
            vm.endTrip()
        }
    }

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

    override fun onDestroyView() {
        countDownTimer?.cancel()
        _binding = null
        super.onDestroyView()
    }
}
