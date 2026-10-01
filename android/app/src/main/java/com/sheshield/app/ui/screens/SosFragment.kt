package com.sheshield.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.sheshield.app.R
import com.sheshield.app.databinding.FragmentSosBinding
import com.sheshield.app.ui.MainActivity
import com.sheshield.app.util.SmsHelper

/**
 * SOS screen displayed while escalation is in progress.
 *
 * On this screen:
 *  - Shows the GPS coordinates of the last known location
 *  - Shows which contacts have been/are being notified
 *  - Allows calling emergency services (112) directly
 *  - Allows re-sending SMS if the user wants
 *  - Provides a cancel/end-trip button
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

            val lat = trip.lastLatitude
            val lng = trip.lastLongitude
            binding.tvSosLocation.text =
                "📍 Last location: ${"%.5f".format(lat)}, ${"%.5f".format(lng)}\n" +
                "maps.google.com/?q=$lat,$lng"
            binding.tvSosDest.text = "Trip to: ${trip.destinationLabel}"

            // Show which contacts are being notified
            val contacts = vm.getTrustedContacts()
            binding.tvSosContacts.text = if (contacts.isEmpty()) {
                "⚠️ No trusted contacts saved.\nGo to Contacts to add them."
            } else {
                "📱 SMS alert sent to:\n" + contacts.joinToString("\n") { "• ${it.name} (${it.phone})" }
            }
        }

        // Re-send SMS button — useful if the first send failed
        binding.btnResendSms.setOnClickListener {
            val trip = vm.activeTrip.value ?: return@setOnClickListener
            val contacts = vm.getTrustedContacts()
            if (contacts.isEmpty()) {
                Toast.makeText(requireContext(), "No trusted contacts saved", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(requireContext(), "SMS permission not granted", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            SmsHelper.sendSosMessages(
                context = requireContext(),
                phones = contacts.map { it.phone },
                latitude = trip.lastLatitude,
                longitude = trip.lastLongitude
            )
            Toast.makeText(requireContext(), "SOS SMS re-sent to ${contacts.size} contact(s)", Toast.LENGTH_SHORT).show()
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
