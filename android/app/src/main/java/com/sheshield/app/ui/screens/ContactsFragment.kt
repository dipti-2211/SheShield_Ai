package com.sheshield.app.ui.screens

import android.os.Bundle
import android.view.*
import android.widget.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sheshield.app.R
import com.sheshield.app.data.model.TrustedContact
import com.sheshield.app.ui.components.Ui

class ContactsFragment:ScreenFragment(){
    private lateinit var list:LinearLayout
    override fun onCreateView(inflater:LayoutInflater,container:ViewGroup?,state:Bundle?):View{
        val c=requireContext();val body=page("Your circle",false);body.addView(Ui.text(c,"People you trust.",32,true));body.addView(Ui.space(c,10));body.addView(Ui.text(c,"Choose who receives your SOS. The first contact is called first; the next is tried when no one acknowledges.",16,tint=R.color.on_surface_secondary));body.addView(Ui.space(c,24))
        list=Ui.col(c);body.addView(list);body.addView(Ui.button(c,"Add a trusted contact"){edit(null)});body.addView(Ui.space(c,12));body.addView(Ui.text(c,"Contacts are saved on your phone. A journey keeps its starting contact list; edits apply to future journeys.",14,tint=R.color.on_surface_secondary));render();return Ui.scroll(c,body)
    }
    private fun render(){val c=requireContext();list.removeAllViews();val contacts=repo.contacts()
        if(contacts.isEmpty())list.addView(Ui.card(c,Ui.col(c,24).apply{addView(Ui.text(c,"Your circle starts here",20,true));addView(Ui.space(c,8));addView(Ui.text(c,"Add someone who knows you and can respond when you need help.",16,tint=R.color.on_surface_secondary))}))
        contacts.forEachIndexed{index,contact->val row=Ui.col(c,20);row.addView(Ui.badge(c,if(index==0)"FIRST CONTACT" else "CONTACT ${index+1}"));row.addView(Ui.space(c,12));row.addView(Ui.text(c,contact.name,20,true));row.addView(Ui.text(c,"•••• •••• ${contact.phone.takeLast(4)}",15,tint=R.color.on_surface_secondary))
            val card=Ui.card(c,row);card.setOnClickListener{MaterialAlertDialogBuilder(c).setTitle(contact.name).setItems(arrayOf("Edit contact","Make first contact","Delete contact")){_,choice->when(choice){0->edit(index);1->{val updated=repo.contacts().toMutableList();updated.removeAt(index);updated.add(0,contact);repo.saveContacts(updated);render()};2->Ui.confirm(c,"Remove ${contact.name}?","This contact will be removed from future journey alerts.","Remove"){repo.saveContacts(repo.contacts().filterIndexed{i,_->i!=index});render()}}}.show()};list.addView(card)}
    }
    private fun edit(index:Int?){val c=requireContext();val old=index?.let{repo.contacts().getOrNull(it)};val form=Ui.col(c,20);val name=Ui.input(c,"Name",old?.name?:"");val phone=Ui.input(c,"Phone number",old?.phone?:"",true);form.addView(name.first);form.addView(phone.first)
        val dialog=MaterialAlertDialogBuilder(c).setTitle(if(old==null)"Add to your circle" else "Edit contact").setView(form).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create()
        dialog.setOnShowListener{dialog.getButton(-1).setOnClickListener{
            val label=name.second.text.toString().trim();var number=phone.second.text.toString().replace(Regex("[\\s()-]"),"");if(number.matches(Regex("[6-9][0-9]{9}")))number="+91$number"
            if(label.isEmpty()){name.first.error="Enter a name";return@setOnClickListener}
            if(!number.matches(Regex("\\+[1-9][0-9]{7,14}"))){phone.first.error="Use an international number, for example +91…";return@setOnClickListener}
            val updated=repo.contacts().toMutableList();if(updated.withIndex().any{it.index!=index&&it.value.phone==number}){phone.first.error="This number is already in your circle";return@setOnClickListener}
            if(index==null&&updated.size>=10){phone.first.error="Your circle can contain up to ten contacts";return@setOnClickListener}
            val item=TrustedContact(label,number);if(index!=null)updated[index]=item else updated.add(item);repo.saveContacts(updated);render();dialog.dismiss()
        }};dialog.show()
    }
}
