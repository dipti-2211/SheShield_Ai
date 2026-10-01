package com.sheshield.app.ui.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.sheshield.app.R
import com.sheshield.app.data.model.TripState
import com.sheshield.app.databinding.FragmentHomeBinding
import com.sheshield.app.ui.MainActivity

/**
 * Home screen: shows safety status, SOS button, and navigate-to-plan-trip.
 * Also shows a "Resume Trip" card when a trip is active.
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val vm = (activity as MainActivity).tripViewModel

        // Observe active trip to show resume card
        vm.activeTrip.observe(viewLifecycleOwner) { trip ->
            if (trip != null && trip.state in listOf(
                    TripState.ACTIVE, TripState.CHECK_IN_PENDING, TripState.SOS_ACTIVE)) {
                binding.resumeTripCard.visibility = View.VISIBLE
                binding.resumeTripLabel.text = "Active trip → ${trip.destinationLabel}"
            } else {
                binding.resumeTripCard.visibility = View.GONE
            }
        }

        binding.btnPlanTrip.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_tripPlanning)
        }

        binding.btnSos.setOnClickListener {
            vm.triggerSos()
            findNavController().navigate(R.id.action_home_to_sos)
        }

        binding.btnContacts.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_contacts)
        }

        binding.resumeTripCard.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_activeTrip)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
