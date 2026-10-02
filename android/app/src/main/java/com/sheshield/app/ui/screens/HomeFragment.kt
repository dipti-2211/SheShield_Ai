package com.sheshield.app.ui.screens

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sheshield.app.R
import com.sheshield.app.ui.components.Ui
import com.sheshield.app.data.model.TripState
import kotlinx.coroutines.launch

class HomeFragment:ScreenFragment(){
    override fun onCreateView(inflater:LayoutInflater,container:ViewGroup?,state:Bundle?):View{
        val c=requireContext();val body=Ui.col(c,20)
        body.addView(Ui.header(c,"SheShield",settings={main.navigate(R.id.settingsFragment)}));body.addView(Ui.space(c,28))
        body.addView(Ui.text(c,"A little more confidence\non your way.",32,true));body.addView(Ui.space(c,10));body.addView(Ui.text(c,"See the evidence gaps. Ask your circle to walk with you.",16,tint=R.color.on_surface_secondary));body.addView(Ui.space(c,24))
        body.addView(Ui.badge(c,if(repo.demo())"PRACTICE · KOLKATA" else "LIVE JOURNEY"));body.addView(Ui.space(c,20))
        val search=Ui.col(c,20).apply{addView(Ui.text(c,"Where to?",24,true));addView(Ui.space(c,6));addView(Ui.text(c,"Search a destination or choose on the map",15,tint=R.color.on_surface_secondary));addView(Ui.button(c,"Plan a journey  →"){main.navigate(R.id.tripPlanningFragment)})}
        body.addView(Ui.card(c,search))
        val resume=Ui.col(c,20);val resumeTitle=Ui.text(c,"Journey in progress",18,true);resume.addView(resumeTitle);resume.addView(Ui.button(c,"Continue journey"){main.navigate(R.id.activeTripFragment)})
        val resumeCard=Ui.card(c,resume);resumeCard.visibility=View.GONE;body.addView(resumeCard);body.addView(Ui.sosButton(c){sos()})
        val circle=Ui.col(c,20);circle.addView(Ui.text(c,"Your safety circle",18,true));circle.addView(Ui.space(c,8));circle.addView(Ui.text(c,"${repo.contacts().size} trusted contacts · you control who receives alerts",15,tint=R.color.on_surface_secondary));circle.addView(Ui.button(c,"Manage your circle",true){main.navigate(R.id.contactsFragment)});body.addView(Ui.card(c,circle))
        val mode=Ui.col(c,20);mode.addView(Ui.text(c,if(repo.demo())"Explore the full journey" else "Ready when you are",18,true));mode.addView(Ui.space(c,8));mode.addView(Ui.text(c,if(repo.demo())"Plan real walking routes with simulated positions and alerts. The recorded Kolkata demo is available in the planner." else "Location stays on your phone until a journey starts. Contacts receive location only when you request help or miss a check-in.",15,tint=R.color.on_surface_secondary));mode.addView(Ui.button(c,if(repo.demo())"Enable live journey alerts" else "Use practice mode",true){runAction{if(repo.active()!=null){Ui.error(c,"Finish the current journey before switching modes.");return@runAction};repo.setDemo(!repo.demo());main.planningViewModel.reset();main.nav.navigate(R.id.homeFragment,null,androidx.navigation.NavOptions.Builder().setPopUpTo(R.id.homeFragment,true).build())}});body.addView(Ui.card(c,mode))
        body.addView(Ui.space(c,12));body.addView(Ui.text(c,"An unfamiliar last kilometre? Start a journey, then use Walk with me. Your check-in does not depend on crime reports being available.",13,tint=R.color.on_surface_secondary));body.addView(Ui.space(c,20))
        return Ui.scroll(c,body)
    }
    override fun onViewCreated(view:View,state:Bundle?){super.onViewCreated(view,state)
        main.tripViewModel.activeTrip.observe(viewLifecycleOwner){trip->
            val scroll=view as ScrollView;val body=scroll.getChildAt(0) as LinearLayout
            val cards=(0 until body.childCount).map{body.getChildAt(it)}.filterIsInstance<com.google.android.material.card.MaterialCardView>()
            cards.getOrNull(1)?.visibility=if(trip!=null)View.VISIBLE else View.GONE
        }
        if(!repo.prefs.getBoolean("onboarded",false))view.post{if(isAdded)MaterialAlertDialogBuilder(requireContext()).setTitle("Welcome to SheShield")
            .setMessage("Compare walking routes using available reported-incident evidence. During a journey, a check-in can alert your trusted contacts if you don't respond. You can always request SOS or open your phone's emergency dialer.\n\nStart with a labeled rehearsal, then configure live routing in Settings.")
            .setPositiveButton("Let's explore"){_,_->repo.prefs.edit().putBoolean("onboarded",true).apply()}.setNeutralButton("Set up my circle"){_,_->repo.prefs.edit().putBoolean("onboarded",true).apply();main.navigate(R.id.contactsFragment)}.show()}
    }
}
