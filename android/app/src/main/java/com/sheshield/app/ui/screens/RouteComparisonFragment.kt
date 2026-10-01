package com.sheshield.app.ui.screens

import android.os.Bundle
import android.view.*
import android.widget.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sheshield.app.R
import com.sheshield.app.data.model.*
import com.sheshield.app.ui.components.*
import com.sheshield.app.util.LocationProvider

class RouteComparisonFragment:ScreenFragment(){
    private var renderer:RouteMapRenderer?=null
    private lateinit var cards:LinearLayout
    private var plan:TripPlan?=null
    private var selected:RouteOption?=null
    override fun onCreateView(inflater:LayoutInflater,container:ViewGroup?,state:Bundle?):View{
        val c=requireContext();val root=Ui.col(c);root.setBackgroundColor(Ui.color(c,R.color.background))
        root.addView(Ui.col(c,20).apply{addView(Ui.header(c,"Choose your route",back={main.nav.popBackStack()}))})
        val map=RouteMapRenderer(c,state);renderer=map;map.onSelect={id->plan?.routes?.firstOrNull{it.routeId==id}?.let{selected=it;main.planningViewModel.selectedRouteId=id;render()}};root.addView(map.view,LinearLayout.LayoutParams(-1,0,1f))
        cards=Ui.col(c,20);val scroll=Ui.scroll(c,cards);root.addView(scroll,LinearLayout.LayoutParams(-1,Ui.dp(c,280)));root.addView(Ui.col(c,12).apply{addView(Ui.button(c,"Start journey"){start()})})
        return root
    }
    override fun onViewCreated(view:View,state:Bundle?){super.onViewCreated(view,state);runAction{plan=main.planningViewModel.plan?:repo.savedPlan();val p=plan?:error("Your route plan was not found. Calculate routes again.");if(main.planningViewModel.origin?.let{it!=p.origin}==true||main.planningViewModel.destination?.let{it!=p.destination}==true)error("Your endpoints changed. Calculate a new route for those locations.");selected=p.routes.firstOrNull{it.routeId==main.planningViewModel.selectedRouteId}?:p.routes.firstOrNull();render()}}
    private fun render(){val c=requireContext();val p=plan?:return;cards.removeAllViews()
        if(p.mode=="REHEARSAL")cards.addView(Ui.badge(c,if(p.routes.any{it.isDemoData})"RECORDED DEMO · FICTIONAL INCIDENTS" else "PRACTICE · REAL ROUTES, SIMULATED ALERTS"))
        cards.addView(Ui.space(c,8));cards.addView(Ui.text(c,"${p.origin.label} → ${p.destination.label}",18,true));cards.addView(Ui.space(c,12))
        p.routes.forEach{route->val body=Ui.col(c,16);val isSelected=selected?.routeId==route.routeId
            body.addView(Ui.text(c,(if(isSelected)"✓  " else "")+route.label,18,true));body.addView(Ui.space(c,4));body.addView(Ui.text(c,"${route.durationSeconds/60} min  ·  ${"%.1f".format(route.distanceMeters/1000)} km${if(route.extraMinutes>0)"  ·  +${route.extraMinutes} min" else ""}",16))
            body.addView(Ui.space(c,6));body.addView(Ui.text(c,if(route.coverage=="AVAILABLE")"${route.riskLevel.lowercase().replaceFirstChar{it.uppercase()}} reported exposure · index ${"%.0f".format(route.riskScore*100)}/100" else "Reported exposure unknown",14,true,Ui.riskColor(route.riskLevel)))
            if(route.originSnapMeters>30)body.addView(Ui.text(c,"Walking access starts ${route.originSnapMeters} m from your starting pin",13,tint=R.color.on_surface_secondary))
            if(route.destinationSnapMeters>30)body.addView(Ui.text(c,"Walking access ends ${route.destinationSnapMeters} m from your destination pin",13,tint=R.color.on_surface_secondary))
            val card=Ui.card(c,body,isSelected);card.setOnClickListener{selected=route;main.planningViewModel.selectedRouteId=route.routeId;render()};cards.addView(card)
        }
        cards.addView(Ui.button(c,"Why this route?",true){selected?.let{evidence(it)}})
        cards.addView(Ui.space(c,12));cards.addView(Ui.text(c,p.attribution,12,tint=R.color.on_surface_secondary))
        renderer?.routes(p.routes,selected?.routeId);renderer?.endpoints(p.origin,p.destination)
    }
    private fun start(){val p=plan?:return;val route=selected?:return
        if(!LocationProvider(requireContext()).permitted()){main.requestLocation{if(it)start()else Ui.error(requireContext(),"Allow location access to run journey monitoring. Rehearsal still uses simulated positions.")};return}
        runAction{repo.start(p,route);main.requestNotifications();main.tripViewModel.startTracking();main.navigate(R.id.activeTripFragment)}
    }
    private fun evidence(route:RouteOption){val c=requireContext();val body=Ui.col(c,20)
        body.addView(Ui.text(c,route.riskSummary,16));body.addView(Ui.space(c,12));body.addView(Ui.text(c,"500 m corridor · severity, recency and distance weighting\nEvaluated: ${route.evaluatedAt.take(10)}\nCoverage: ${route.coverage.lowercase()}\nThe exposure index is a comparison tool, not a probability of crime.",14,tint=R.color.on_surface_secondary))
        route.evidence.take(12).forEach{body.addView(Ui.space(c,16));body.addView(Ui.text(c,it.category.replace('_',' ').replaceFirstChar{ch->ch.uppercase()},16,true));body.addView(Ui.text(c,"${it.daysOld} days ago · ${(it.distanceKm*1000).toInt()} m from route\n${it.source}",14,tint=R.color.on_surface_secondary))}
        if(route.evidence.isEmpty())body.addView(Ui.text(c,"No incident evidence is available for this route."))
        MaterialAlertDialogBuilder(c).setTitle("Why this route?").setView(Ui.scroll(c,body)).setPositiveButton("Got it",null).show()
    }
    override fun onStart(){super.onStart();renderer?.view?.onStart()}
    override fun onResume(){super.onResume();renderer?.view?.onResume()}
    override fun onPause(){renderer?.view?.onPause();super.onPause()}
    override fun onStop(){renderer?.view?.onStop();super.onStop()}
    override fun onSaveInstanceState(out:Bundle){super.onSaveInstanceState(out);renderer?.view?.onSaveInstanceState(out)}
    override fun onDestroyView(){renderer?.destroy();renderer=null;super.onDestroyView()}
}
