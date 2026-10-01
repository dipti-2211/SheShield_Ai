package com.sheshield.app.ui.screens

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.sheshield.app.data.model.TrustedContact
import com.sheshield.app.databinding.FragmentContactsBinding
import com.sheshield.app.ui.MainActivity

/**
 * Trusted contact management screen.
 * Contacts are stored locally in SharedPreferences (no cloud account needed).
 * They are transmitted to the backend at trip-start to drive escalation.
 */
class ContactsFragment : Fragment() {

    private var _binding: FragmentContactsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentContactsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val vm = (activity as MainActivity).tripViewModel

        val contacts = vm.getTrustedContacts().toMutableList()

        fun refresh() {
            binding.tvContactList.text = if (contacts.isEmpty()) {
                "No contacts added yet."
            } else {
                contacts.joinToString("\n") { "• ${it.name} – ${it.phone}" }
            }
        }

        refresh()

        binding.btnAddContact.setOnClickListener {
            val name = binding.etContactName.text.toString().trim()
            val phone = binding.etContactPhone.text.toString().trim()
            if (name.isEmpty() || phone.isEmpty()) {
                Toast.makeText(requireContext(), "Enter both name and phone number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!phone.startsWith("+")) {
                Toast.makeText(requireContext(), "Phone must be in E.164 format (e.g. +919876543210)", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            contacts.add(TrustedContact(name, phone))
            vm.saveTrustedContacts(contacts)
            binding.etContactName.text?.clear()
            binding.etContactPhone.text?.clear()
            refresh()
            Toast.makeText(requireContext(), "Contact saved", Toast.LENGTH_SHORT).show()
        }

        binding.btnClearContacts.setOnClickListener {
            contacts.clear()
            vm.saveTrustedContacts(contacts)
            refresh()
        }

        // Demo mode toggle
        binding.switchDemoMode.isChecked = vm.isDemoMode()
        binding.switchDemoMode.setOnCheckedChangeListener { _, checked ->
            vm.setDemoMode(checked)
            Toast.makeText(requireContext(),
                if (checked) "Demo mode ON – using fixture data" else "Demo mode OFF – using live backend",
                Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
