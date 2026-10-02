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
        if(p.routes.any{it.coverage!="AVAILABLE"})cards.addView(Ui.text(c,"Evidence is incomplete. Routes are ordered by walking time; safety cannot be ranked.",14,true,R.color.risk_medium))
        cards.addView(Ui.space(c,8));cards.addView(Ui.text(c,"${p.origin.label} → ${p.destination.label}",18,true));cards.addView(Ui.space(c,12))
        p.routes.forEach{route->val body=Ui.col(c,16);val isSelected=selected?.routeId==route.routeId
            body.addView(Ui.text(c,(if(isSelected)"✓  " else "")+route.label,18,true));body.addView(Ui.space(c,4));body.addView(Ui.text(c,"${route.durationSeconds/60} min  ·  ${"%.1f".format(route.distanceMeters/1000)} km${if(route.extraMinutes>0)"  ·  +${route.extraMinutes} min" else ""}",16))
            body.addView(Ui.space(c,6));body.addView(Ui.text(c,if(route.coverage=="AVAILABLE")"${route.riskLevel.lowercase().replaceFirstChar{it.uppercase()}} reported exposure · index ${"%.0f".format(route.riskScore*100)}/100" else "Reported exposure unknown",14,true,Ui.riskColor(route.riskLevel)))
            route.passport?.let{passport->
                body.addView(Ui.text(c,"${passport.coveragePercent}% reporting coverage · ${route.incidentCount} nearby reports",13,tint=R.color.on_surface_secondary))
                if(passport.longestUnknownMeters>0)body.addView(Ui.text(c,"Longest evidence gap: ${passport.longestUnknownMeters} m",14,true,R.color.risk_medium))
                if(route.coverage=="AVAILABLE")body.addView(Ui.text(c,"Highest local exposure index: ${"%.0f".format(passport.peakExposure*100)}/100",13,tint=R.color.on_surface_secondary))
            }
            if(route.originSnapMeters>30)body.addView(Ui.text(c,"Walking access starts ${route.originSnapMeters} m from your starting pin",13,tint=R.color.on_surface_secondary))
            if(route.destinationSnapMeters>30)body.addView(Ui.text(c,"Walking access ends ${route.destinationSnapMeters} m from your destination pin",13,tint=R.color.on_surface_secondary))
            val card=Ui.card(c,body,isSelected);card.setOnClickListener{selected=route;main.planningViewModel.selectedRouteId=route.routeId;render()};cards.addView(card)
        }
        cards.addView(Ui.button(c,"Street evidence & gaps",true){selected?.let{evidence(it)}})
        cards.addView(Ui.space(c,12));cards.addView(Ui.text(c,p.attribution,12,tint=R.color.on_surface_secondary))
        renderer?.routes(p.routes,selected?.routeId);renderer?.endpoints(p.origin,p.destination)
    }
    private fun start(){val p=plan?:return;val route=selected?:return
        if(!LocationProvider(requireContext()).permitted()){main.requestLocation{if(it)start()else Ui.error(requireContext(),"Allow location access to run journey monitoring. Rehearsal still uses simulated positions.")};return}
        runAction{repo.start(p,route);main.requestNotifications();main.tripViewModel.startTracking();main.navigate(R.id.activeTripFragment)}
    }
    private fun evidence(route:RouteOption){val c=requireContext();val body=Ui.col(c,20)
        if(route.isDemoData)body.addView(Ui.badge(c,"FICTIONAL EVIDENCE · RECORDED DEMO"))
        body.addView(Ui.text(c,route.riskSummary,16));body.addView(Ui.space(c,12))
        route.passport?.let{p->
            body.addView(Ui.text(c,"Every stretch matters",20,true))
            body.addView(Ui.text(c,"${p.coveragePercent}% documented reporting coverage\n${p.longestUnknownMeters} m longest gap\n${p.contextReportCount} area reports too imprecise for street scoring",15))
            body.addView(Ui.space(c,12))
            p.stretches.forEach{stretch->
                val text=when(stretch.kind){"REPORTS"->"Nearby reports · ${if(stretch.coverage=="AVAILABLE")"reporting coverage available" else "coverage unknown"}";"NO_REPORTS"->"No matching reports in covered feed";else->"Evidence gap · safety unknown"}
                body.addView(Ui.text(c,"${stretch.fromMeters}–${stretch.toMeters} m: $text",14,tint=if(stretch.kind=="NO_REPORTS")R.color.on_surface_secondary else R.color.risk_medium));body.addView(Ui.space(c,8))
            }
            p.coverageWindows.forEach{w->body.addView(Ui.text(c,"${w.source}: ${w.from.take(10)} to ${w.to.take(10)} · updated ${w.updatedAt.take(10)}",13,tint=R.color.on_surface_secondary))}
        }
        body.addView(Ui.text(c,"150 m report corridor · route pieces up to 50 m\nEvaluated: ${route.evaluatedAt.take(10)}\nA nearby report can be on another street or across a barrier. Missing reports never establish safety. The index is not a probability of crime.",14,tint=R.color.on_surface_secondary))
        route.evidence.take(30).forEach{e->body.addView(Ui.space(c,16));body.addView(Ui.text(c,e.category.replace('_',' ').replaceFirstChar{ch->ch.uppercase()},16,true))
            body.addView(Ui.text(c,"${e.daysOld} days ago · "+if(e.precisionMeters>0)"estimated ${e.distanceMinMeters}–${e.distanceMaxMeters} m from route\nLocation uncertainty: ±${e.precisionMeters} m\n${e.source} · reviewed ${e.reviewedAt?.take(10)?:"unknown"}" else "${(e.distanceKm*1000).toInt()} m from route\n${e.source}",14,tint=R.color.on_surface_secondary))
            e.sourceUrl?.takeIf{it.startsWith("https://")}?.let{url->body.addView(Ui.button(c,"Open source report",true){startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(url)))})}
        }
        if(route.evidence.size>30)body.addView(Ui.text(c,"Showing 30 of ${route.evidence.size} matching reports."))
        if(route.evidence.isEmpty())body.addView(Ui.text(c,"No precise incident evidence is available here. If a stretch feels uncomfortable, use Walk with me during your journey."))
        MaterialAlertDialogBuilder(c).setTitle("Street evidence & gaps").setView(Ui.scroll(c,body)).setPositiveButton("Got it",null).show()
    }
    override fun onStart(){super.onStart();renderer?.view?.onStart()}
    override fun onResume(){super.onResume();renderer?.view?.onResume()}
    override fun onPause(){renderer?.view?.onPause();super.onPause()}
    override fun onStop(){renderer?.view?.onStop();super.onStop()}
    override fun onSaveInstanceState(out:Bundle){super.onSaveInstanceState(out);renderer?.view?.onSaveInstanceState(out)}
    override fun onDestroyView(){renderer?.destroy();renderer=null;super.onDestroyView()}
}
