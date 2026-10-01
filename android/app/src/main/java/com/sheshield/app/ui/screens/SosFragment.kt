package com.sheshield.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.sheshield.app.R
import com.sheshield.app.databinding.FragmentSosBinding
import com.sheshield.app.ui.MainActivity

/**
 * SOS screen displayed while escalation is in progress.
 * Shows current status, who is being contacted, and provides
 * a direct emergency dial button (112 for India).
 */
class SosFragment : Fragment() {

    private var _binding: FragmentSosBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentSosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val vm = (activity as MainActivity).tripViewModel

        vm.activeTrip.observe(viewLifecycleOwner) { trip ->
            if (trip == null) return@observe
            binding.tvSosLocation.text = "Last location: ${
                "%.5f".format(trip.lastLatitude)}, ${"%.5f".format(trip.lastLongitude)}"
            binding.tvSosDest.text = "Trip to: ${trip.destinationLabel}"
        }

        // Emergency services dial (India: 112)
        binding.btnCallEmergency.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))
            startActivity(intent)
        }

        binding.btnCancelSos.setOnClickListener {
            vm.endTrip()
            findNavController().navigate(R.id.action_sos_to_home)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
