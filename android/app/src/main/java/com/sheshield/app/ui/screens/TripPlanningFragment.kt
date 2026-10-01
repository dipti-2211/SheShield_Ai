package com.sheshield.app.ui.screens

import android.os.Bundle
import android.view.*
import android.widget.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sheshield.app.R
import com.sheshield.app.data.model.*
import com.sheshield.app.ui.components.*
import com.sheshield.app.util.LocationProvider

class TripPlanningFragment:ScreenFragment(){
    private var picker:RouteMapRenderer?=null
    private lateinit var originButton:com.google.android.material.button.MaterialButton
    private lateinit var destinationButton:com.google.android.material.button.MaterialButton
    private lateinit var coordinates:TextView
    private lateinit var findButton:com.google.android.material.button.MaterialButton
    private val vm get()=main.planningViewModel
    override fun onCreateView(inflater:LayoutInflater,container:ViewGroup?,state:Bundle?):View{
        val c=requireContext();val body=page("Plan your journey")
        body.addView(Ui.badge(c,if(repo.demo())"PRACTICE · REAL ROUTES, SIMULATED ALERTS" else "LIVE WALKING ROUTES"))
        body.addView(Ui.space(c,16));body.addView(Ui.text(c,"Where are you heading?",30,true));body.addView(Ui.space(c,20))
        val endpoints=Ui.col(c,16)
        originButton=Ui.button(c,"Choose a starting point",true){chooseEndpoint(true)}
        destinationButton=Ui.button(c,"Choose your destination",true){chooseEndpoint(false)}
        coordinates=Ui.text(c,"",13,tint=R.color.on_surface_secondary)
        endpoints.addView(originButton);endpoints.addView(Ui.space(c,8));endpoints.addView(destinationButton);endpoints.addView(Ui.space(c,12));endpoints.addView(coordinates)
        body.addView(Ui.card(c,endpoints))
        body.addView(Ui.button(c,"Use my current location",true){currentLocation()})
        body.addView(Ui.button(c,"Choose points on the map",true){chooseMap(null)})
        findButton=Ui.button(c,"Find walking routes"){
            if(vm.origin==null||vm.destination==null){Ui.error(c,"Select both endpoints first. Search a place, use your position, or pick points on the map.");return@button}
            runAction{
                val origin=vm.origin!!;val destination=vm.destination!!
                findButton.isEnabled=false;originButton.isEnabled=false;destinationButton.isEnabled=false;findButton.text="Calculating your routes…"
                vm.clearPlan()
                try{
                    val plan=repo.plan(origin,destination)
                    if(vm.origin!=origin||vm.destination!=destination)return@runAction
                    vm.plan=plan;vm.selectedRouteId=plan.routes.firstOrNull()?.routeId;main.navigate(R.id.routeComparisonFragment)
                }finally{if(isAdded){findButton.isEnabled=true;originButton.isEnabled=true;destinationButton.isEnabled=true;findButton.text="Find walking routes"}}
            }
        };body.addView(findButton);body.addView(Ui.space(c,20))
        body.addView(Ui.text(c,"Each route is calculated for the coordinates shown above. Practice mode uses real walking routes and simulated journey alerts.",14,tint=R.color.on_surface_secondary))
        body.addView(Ui.space(c,20));body.addView(Ui.button(c,"Open the recorded Kolkata demo",true){recordedDemo()})
        body.addView(Ui.text(c,"The recorded demo is a fixed Esplanade–Victoria Memorial journey with fictional evidence. It works offline.",13,tint=R.color.on_surface_secondary))
        refresh();return Ui.scroll(c,body)
    }
    private fun refresh(){
        originButton.text="From · ${vm.origin?.label?:"Choose a starting point"}"
        destinationButton.text="To · ${vm.destination?.label?:"Choose your destination"}"
        coordinates.text=listOfNotNull(vm.origin?.let{"Start: ${"%.5f".format(it.latitude)}, ${"%.5f".format(it.longitude)}"},vm.destination?.let{"Destination: ${"%.5f".format(it.latitude)}, ${"%.5f".format(it.longitude)}"}).joinToString("\n")
    }
    private fun select(place:Place,origin:Boolean){if(origin)vm.chooseOrigin(place)else vm.chooseDestination(place);refresh()}
    private fun chooseEndpoint(origin:Boolean){val c=requireContext();val items=if(origin)arrayOf("Search for a starting point","Choose on map","Use my current location")else arrayOf("Search for a destination","Choose on map")
        MaterialAlertDialogBuilder(c).setTitle(if(origin)"Starting point" else "Destination").setItems(items){_,which->when(which){0->search(origin);1->chooseMap(origin);2->currentLocation()}}.show()
    }
    private fun search(origin:Boolean){
        val c=requireContext();val content=Ui.col(c,16);val field=Ui.input(c,"Search a place in Kolkata");content.addView(field.first)
        val results=Ui.col(c);val list=Ui.scroll(c,results)
        val dialog=MaterialAlertDialogBuilder(c).setTitle(if(origin)"Find starting point" else "Find destination").setView(content).setNegativeButton("Close",null).create()
        val button=Ui.button(c,"Search places"){
            runAction{results.removeAllViews();results.addView(Ui.text(c,"Finding places…"));try{
                val places=repo.search(field.second.text.toString().trim());results.removeAllViews()
                if(places.isEmpty())results.addView(Ui.text(c,"No places found. Try a landmark or select a map point."))
                val counts=places.groupingBy{it.label}.eachCount()
                places.forEach{place->val label=if((counts[place.label]?:0)>1)"${place.label}\n${"%.5f".format(place.latitude)}, ${"%.5f".format(place.longitude)}" else place.label;results.addView(Ui.button(c,label,true){select(place,origin);dialog.dismiss()})}
            }catch(e:Exception){results.removeAllViews();throw e}}
        }
        content.addView(button);content.addView(list,LinearLayout.LayoutParams(-1,Ui.dp(c,280)));dialog.show()
    }
    private fun currentLocation(){val provider=LocationProvider(requireContext());if(!provider.permitted()){main.requestLocation{if(it)currentLocation()else Ui.error(requireContext(),"You can select a starting point on the map instead.")};return}
        runAction{val fix=provider.current();select(Place("Current location",fix.latitude,fix.longitude),true)}
    }
    private fun chooseMap(origin:Boolean?){
        val c=requireContext();val map=RouteMapRenderer(c);picker=map
        val content=Ui.col(c,12);content.addView(Ui.text(c,"Press and hold the exact point you want to use.",15));content.addView(map.view,LinearLayout.LayoutParams(-1,Ui.dp(c,360)));map.view.onStart();map.view.onResume()
        map.onReady={map.endpoints(vm.origin,vm.destination,true)}
        val dialog=MaterialAlertDialogBuilder(c).setTitle("Choose a map point").setView(content).setNegativeButton("Close",null).create()
        map.onPick={place->
            if(origin!=null){select(place,origin);dialog.dismiss()}
            else MaterialAlertDialogBuilder(c).setTitle("Use this point as…").setItems(arrayOf("Starting point","Destination")){_,which->select(place,which==0);dialog.dismiss()}.show()
        }
        dialog.setOnDismissListener{map.view.onPause();map.view.onStop();map.destroy();picker=null};dialog.show()
    }
    private fun recordedDemo(){runAction{if(repo.active()!=null)error("Finish your current journey first.");repo.setDemo(true);vm.reset();val plan=repo.recordedPlan();vm.plan=plan;vm.selectedRouteId=plan.routes.firstOrNull()?.routeId;main.navigate(R.id.routeComparisonFragment)}}
    override fun onDestroyView(){picker?.destroy();picker=null;super.onDestroyView()}
}
