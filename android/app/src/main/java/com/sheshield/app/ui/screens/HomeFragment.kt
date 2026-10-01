package com.sheshield.app.ui.screens

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.sheshield.app.R
import com.sheshield.app.data.model.TripState
import com.sheshield.app.databinding.FragmentHomeBinding
import com.sheshield.app.ui.MainActivity

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, state: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Wire navigation — always works regardless of ViewModel state
        binding.btnPlanTrip.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_tripPlanning)
        }
        binding.btnSos.setOnClickListener {
            try {
                (activity as? MainActivity)?.tripViewModel?.triggerSos()
            } catch (e: Exception) {
                Log.e("HomeFragment", "SOS vm error", e)
            }
            findNavController().navigate(R.id.action_home_to_sos)
        }
        binding.btnContacts.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_contacts)
        }
        binding.resumeTripCard.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_activeTrip)
        }

        // Observe active trip safely
        try {
            val vm = (activity as? MainActivity)?.tripViewModel ?: return
            vm.activeTrip.observe(viewLifecycleOwner) { trip ->
                val isActive = trip != null && trip.state in listOf(
                    TripState.ACTIVE, TripState.CHECK_IN_PENDING, TripState.SOS_ACTIVE
                )
                binding.resumeTripCard.visibility = if (isActive) View.VISIBLE else View.GONE
                if (isActive) {
                    binding.resumeTripLabel.text = "Active trip → ${trip!!.destinationLabel}"
                }
            }
        } catch (e: Exception) {
            Log.e("HomeFragment", "ViewModel observe error", e)
            binding.resumeTripCard.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
