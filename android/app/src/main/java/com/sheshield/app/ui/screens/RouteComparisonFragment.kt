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
    private var departureEnabled=false
    private var departureSeconds=120
    override fun onCreateView(inflater:LayoutInflater,container:ViewGroup?,state:Bundle?):View{
        val c=requireContext();val root=Ui.col(c);root.setBackgroundColor(Ui.color(c,R.color.background))
        root.addView(Ui.col(c,20).apply{addView(Ui.header(c,"Choose your route",back={main.nav.popBackStack()}))})
        val map=RouteMapRenderer(c,state);renderer=map;map.onArea={a->WalkingDetails.area(c,a){id->avoidArea(id)}};map.onPlace={p->WalkingDetails.place(c,p)};map.onSelect={id->plan?.routes?.firstOrNull{it.routeId==id}?.let{selected=it;main.planningViewModel.selectedRouteId=id;render()}};root.addView(map.view,LinearLayout.LayoutParams(-1,0,1f))
        root.addView(Ui.row(c).apply{
            addView(Ui.button(c,"Map details",true){WalkingDetails.layer(c,map)}.apply{layoutParams=LinearLayout.LayoutParams(0,Ui.dp(c,48),1f)})
            addView(Ui.button(c,"Route preferences",true){preferences()}.apply{layoutParams=LinearLayout.LayoutParams(0,Ui.dp(c,48),1f)})
        })
        cards=Ui.col(c,20);val scroll=Ui.scroll(c,cards);root.addView(scroll,LinearLayout.LayoutParams(-1,Ui.dp(c,minOf(240,maxOf(150,(resources.displayMetrics.heightPixels/resources.displayMetrics.density*.24f).toInt())))));root.addView(Ui.col(c,12).apply{
            addView(Ui.button(c,"Why this route? · sources & gaps",true){selected?.let{RouteEvidenceDialog.show(c,it)}})
            departureEnabled=repo.prefs.getBoolean("departure_default",false);departureSeconds=repo.prefs.getInt("departure_window",120).takeIf{it in listOf(120,300)}?:120
            val departure=CheckBox(c).apply{text="Check on me if I leave my route";isChecked=departureEnabled;setOnCheckedChangeListener{_,checked->departureEnabled=checked;repo.prefs.edit().putBoolean("departure_default",checked).apply()}}
            addView(departure)
            val windowButton=Ui.button(c,"Departure check-in · ${departureSeconds/60} min",true){}
            windowButton.setOnClickListener{
                MaterialAlertDialogBuilder(c).setTitle("Time to confirm you are okay").setItems(arrayOf("2 minutes","5 minutes")){_,i->departureSeconds=if(i==0)120 else 300;repo.prefs.edit().putInt("departure_window",departureSeconds).apply();windowButton.text="Departure check-in · ${departureSeconds/60} min";Toast.makeText(c,"Departure check-in: ${departureSeconds/60} minutes",Toast.LENGTH_SHORT).show()}.setNegativeButton("Cancel",null).show()
            }
            addView(windowButton.apply{minHeight=Ui.dp(c,40);minimumHeight=Ui.dp(c,40)})
            addView(Ui.button(c,"Start journey"){start()})
        })
        return root
    }
    override fun onViewCreated(view:View,state:Bundle?){super.onViewCreated(view,state);runAction{plan=main.planningViewModel.plan?:repo.savedPlan();val p=plan?:error("Your route plan was not found. Calculate routes again.");if(main.planningViewModel.origin?.let{it!=p.origin}==true||main.planningViewModel.destination?.let{it!=p.destination}==true)error("Your endpoints changed. Calculate a new route for those locations.");selected=p.routes.firstOrNull{it.routeId==main.planningViewModel.selectedRouteId}?:p.routes.firstOrNull();render()}}
    private fun render(){val c=requireContext();val p=plan?:return;cards.removeAllViews()
        if(p.mode=="REHEARSAL")cards.addView(Ui.badge(c,if(p.routes.any{it.isDemoData})"RECORDED DEMO · FICTIONAL INCIDENTS" else "PRACTICE · REAL ROUTES, SIMULATED ALERTS"))
        if(p.routes.any{!it.isDemoData})cards.addView(Ui.text(c,"Compare walking time and inspect the supporting reports. Safety remains unknown.",14,true,R.color.risk_medium))
        cards.addView(Ui.space(c,8));cards.addView(Ui.text(c,"${p.origin.label.substringBefore(',')} → ${p.destination.label.substringBefore(',')}",18,true).apply{maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END;contentDescription="${p.origin.label} to ${p.destination.label}"});cards.addView(Ui.space(c,12))
        p.routes.forEach{route->val body=Ui.col(c,16);val isSelected=selected?.routeId==route.routeId
            body.addView(Ui.text(c,(if(isSelected)"✓  " else "")+route.label,18,true));body.addView(Ui.space(c,4));body.addView(Ui.text(c,"${(route.durationSeconds+59)/60} min  ·  ${"%.1f".format(route.distanceMeters/1000)} km${if(route.extraMinutes>0)"  ·  +${route.extraMinutes} min" else ""}",16))
            body.addView(Ui.space(c,6));body.addView(Ui.text(c,if(route.isDemoData&&route.coverage=="AVAILABLE")"${route.riskLevel.lowercase().replaceFirstChar{it.uppercase()}} reported exposure · index ${"%.0f".format(route.riskScore*100)}/100" else "Reported exposure unknown",14,true,Ui.riskColor(if(route.isDemoData)route.riskLevel else "UNKNOWN")))
            route.passport?.let{passport->
                val reportType=if(passport.datasetVersion!=null)"street reports" else "nearby reports"
                body.addView(Ui.text(c,"${passport.coveragePercent}% reporting coverage · ${route.incidentCount} $reportType",13,tint=R.color.on_surface_secondary))
                if(passport.contextReportCount>0)body.addView(Ui.text(c,"${passport.contextReportCount} wider-area reports · open sources for their location limits",13,tint=R.color.on_surface_secondary))
                if(passport.longestUnknownMeters>0)body.addView(Ui.text(c,"Longest evidence gap: ${passport.longestUnknownMeters} m",14,true,R.color.risk_medium))
                if(route.isDemoData&&route.coverage=="AVAILABLE")body.addView(Ui.text(c,"Highest local exposure index: ${"%.0f".format(passport.peakExposure*100)}/100",13,tint=R.color.on_surface_secondary))
            }
            route.environment?.let{e->
                body.addView(Ui.text(c,"Lighting: ${e.summary.lightingKnownMeters} m mapped/observed · ${e.summary.unknownLightingMeters} m unknown",13,tint=R.color.on_surface_secondary))
                body.addView(Ui.text(c,"${e.summary.mappedWalkwayMeters} m mapped walking infrastructure · pedestrian activity unknown",13,tint=R.color.on_surface_secondary))
                if(e.reportAreas.orEmpty().isNotEmpty())body.addView(Ui.text(c,"${e.reportAreas.orEmpty().size} area report boundary · inspect map details",13,true,R.color.risk_medium))
                if(e.summary.restrictedMeters>0)body.addView(Ui.text(c,"Mapped access restriction along this option; check the source before choosing.",14,true,R.color.risk_medium))
            }
            if(route.originSnapMeters>30)body.addView(Ui.text(c,"Walking access starts ${route.originSnapMeters} m from your starting pin",13,tint=R.color.on_surface_secondary))
            if(route.destinationSnapMeters>30)body.addView(Ui.text(c,"Walking access ends ${route.destinationSnapMeters} m from your destination pin",13,tint=R.color.on_surface_secondary))
            val card=Ui.card(c,body,isSelected);card.setOnClickListener{selected=route;main.planningViewModel.selectedRouteId=route.routeId;render()};cards.addView(card)
        }
        cards.addView(Ui.space(c,12));cards.addView(Ui.text(c,p.attribution,12,tint=R.color.on_surface_secondary))
        renderer?.routes(p.routes,selected?.routeId);renderer?.endpoints(p.origin,p.destination)
    }
    private fun preferences(){val p=plan?:return;val c=requireContext()
        if(p.routes.all{it.isDemoData}){Ui.error(c,"Recorded rehearsal uses its fictional comparison. Plan a real walk to inspect walking conditions.");return}
        val available=p.routes.firstOrNull()?.environment?.preferenceAvailability
        val options=mutableListOf("Fastest walking route");val keys=mutableListOf("FASTEST")
        if(available?.lighting==true){options.add("Prefer mapped lighting");keys.add("LIGHTING")}
        if(available?.nearbyPlaces==true){options.add("Keep mapped places nearby");keys.add("NEARBY_PLACES")}
        MaterialAlertDialogBuilder(c).setTitle("Choose your route preference").setItems(options.toTypedArray()){_,index->
            MaterialAlertDialogBuilder(c).setTitle("Maximum additional walking time").setItems(arrayOf("3 minutes","5 minutes","10 minutes")){_,extra->runAction{
                val updated=repo.comparePlan(p,keys[index],listOf(3,5,10)[extra]);plan=updated;main.planningViewModel.plan=updated;selected=updated.routes.firstOrNull();main.planningViewModel.selectedRouteId=selected?.routeId;render()
            }}.setNegativeButton("Cancel",null).show()
        }.setNeutralButton("Data availability"){_,_->MaterialAlertDialogBuilder(c).setMessage("Lighting preference: ${if(available?.lighting==true)"comparison available" else "insufficient coverage"}\nNearby-place preference: ${if(available?.nearbyPlaces==true)"comparison available" else "insufficient mapped connections/hours"}\n\nPreferences appear when enough data supports comparing these options. Walking time remains available.").setPositiveButton("OK",null).show()}.setNegativeButton("Cancel",null).show()
    }
    private fun avoidArea(id:String){val p=plan?:return
        Ui.confirm(requireContext(),"Find routes around this area?","This is an area mentioned by a report; its streets have not been classified as dangerous. Routes cannot avoid the whole area if your start or destination is inside it.","Find alternatives"){
            runAction{val updated=repo.plan(p.origin,p.destination,(p.avoidAreaIds.orEmpty()+id).distinct());plan=updated;main.planningViewModel.plan=updated;selected=updated.routes.firstOrNull();main.planningViewModel.selectedRouteId=selected?.routeId;render()}
        }
    }
    private fun start(){val p=plan?:return;val route=selected?:return
        if(!LocationProvider(requireContext()).permitted()){main.requestLocation{if(it)start()else Ui.error(requireContext(),"Allow location access to run journey monitoring. Rehearsal still uses simulated positions.")};return}
        val begin={runAction{repo.start(p,route,DepartureProtection(departureEnabled,departureSeconds));main.requestNotifications();main.tripViewModel.startTracking();main.navigate(R.id.activeTripFragment)}}
        if(departureEnabled)Ui.confirm(requireContext(),"Departure check-ins",if(p.mode=="REHEARSAL")"Practice will simulate a check-in after a sustained departure. No real calls or SMS." else "If you leave the path for about 45 seconds with accurate GPS, you will have ${departureSeconds/60} minutes to confirm. A missed check-in requests calls and SMS to: ${repo.contacts().joinToString{it.name}.ifBlank{"no contacts selected — add your circle before relying on alerts"}}. Wait for server confirmation; delivery depends on connectivity and account eligibility.","Start protected journey"){begin()} else begin()
    }
    override fun onStart(){super.onStart();renderer?.view?.onStart()}
    override fun onResume(){super.onResume();renderer?.view?.onResume()}
    override fun onPause(){renderer?.view?.onPause();super.onPause()}
    override fun onStop(){renderer?.view?.onStop();super.onStop()}
    override fun onSaveInstanceState(out:Bundle){super.onSaveInstanceState(out);renderer?.view?.onSaveInstanceState(out)}
    override fun onDestroyView(){renderer?.destroy();renderer=null;super.onDestroyView()}
}
