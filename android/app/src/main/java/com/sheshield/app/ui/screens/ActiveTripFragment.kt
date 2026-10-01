package com.sheshield.app.ui.screens

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.sheshield.app.R
import com.sheshield.app.data.model.*
import com.sheshield.app.ui.components.*
import com.sheshield.app.service.TripTrackingService
import com.sheshield.app.util.*
import kotlinx.coroutines.*

class ActiveTripFragment:ScreenFragment(){
    private var renderer:RouteMapRenderer?=null
    private lateinit var status:TextView
    private lateinit var metrics:TextView
    private lateinit var maneuver:TextView
    private lateinit var countdown:TextView
    private lateinit var normal:LinearLayout
    private lateinit var check:LinearLayout
    private lateinit var sosPanel:LinearLayout
    private lateinit var strip:ExposureStrip
    private var demoControls:LinearLayout?=null
    private lateinit var recalc:com.google.android.material.button.MaterialButton
    private var loop:Job?=null
    private var routeRevision=""
    private var current:ActiveTrip?=null
    override fun onCreateView(inflater:LayoutInflater,container:ViewGroup?,state:Bundle?):View{
        val c=requireContext();val root=Ui.col(c);root.setBackgroundColor(Ui.color(c,R.color.background));root.addView(Ui.col(c,20).apply{addView(Ui.header(c,"Your journey",back={main.navigate(R.id.homeFragment)}))})
        val frame=FrameLayout(c);val map=RouteMapRenderer(c,state);renderer=map;frame.addView(map.view,FrameLayout.LayoutParams(-1,-1))
        frame.addView(Ui.icon(c,R.drawable.ic_location,"Recenter on your position"){map.recenter()},FrameLayout.LayoutParams(Ui.dp(c,48),Ui.dp(c,48),Gravity.BOTTOM or Gravity.END).apply{setMargins(0,0,Ui.dp(c,16),Ui.dp(c,36))})
        frame.addView(Ui.button(c,"SOS",danger=true){sos()},FrameLayout.LayoutParams(Ui.dp(c,100),Ui.dp(c,54),Gravity.TOP or Gravity.END).apply{setMargins(0,Ui.dp(c,12),Ui.dp(c,16),0)})
        root.addView(frame,LinearLayout.LayoutParams(-1,0,1f))
        val panel=Ui.col(c,20);status=Ui.text(c,"Preparing monitoring…",14,true,R.color.risk_low);panel.addView(status);panel.addView(Ui.space(c,10))
        metrics=Ui.text(c,"Waiting for your location",26,true);panel.addView(metrics);maneuver=Ui.text(c,"Continue along your selected route",15,tint=R.color.on_surface_secondary);panel.addView(maneuver)
        strip=ExposureStrip(c);panel.addView(strip,LinearLayout.LayoutParams(-1,Ui.dp(c,32)))
        normal=Ui.col(c);normal.addView(Ui.button(c,"Share journey",true){current?.let{trip->runAction{val text="SheShield journey to ${trip.destinationLabel}. Last recorded location: "+if(trip.lastUpdateMs>0)"https://maps.google.com/?q=${trip.lastLatitude},${trip.lastLongitude} at ${java.util.Date(trip.lastUpdateMs)}" else "not available yet";val url=runCatching{repo.share(trip)}.getOrNull();share(text+(url?.let{"\nLive trip link: $it"}?:""))}}})
        recalc=Ui.button(c,"Recalculate from my position",true){current?.let{trip->runAction{val route=repo.reroute(trip);Ui.confirm(c,"Update your route?","${route.durationSeconds/60} min · ${"%.1f".format(route.distanceMeters/1000)} km","Use route"){runAction{repo.acceptRoute(route)}}}}};recalc.visibility=View.GONE;normal.addView(recalc)
        normal.addView(Ui.button(c,"End journey",true){end()});normal.addView(Ui.sosButton(c){sos()})
        if(repo.demo()){val row=Ui.row(c);row.addView(Ui.button(c,"Pause / play",true){service(TripTrackingService.ACTION_PAUSE)}.apply{layoutParams=LinearLayout.LayoutParams(0,Ui.dp(c,48),1f)});row.addView(Ui.button(c,"Next check-in",true){service(TripTrackingService.ACTION_SKIP)}.apply{layoutParams=LinearLayout.LayoutParams(0,Ui.dp(c,48),1f)});normal.addView(row,0);demoControls=row;row.visibility=View.GONE}
        panel.addView(normal)
        check=Ui.col(c);check.addView(Ui.text(c,"Are you safe?",24,true));check.addView(Ui.text(c,"Your route enters an elevated reported-exposure segment. Please check in.",15,tint=R.color.on_surface_secondary));countdown=Ui.text(c,"",28,true,R.color.risk_medium);check.addView(countdown)
        check.addView(Ui.button(c,"I'm safe"){main.tripViewModel.confirmSafe()});check.addView(Ui.button(c,"Request SOS now",danger=true){sos()});check.visibility=View.GONE;panel.addView(check)
        sosPanel=Ui.col(c);sosPanel.addView(Ui.text(c,"SOS is active",24,true,R.color.sos_red));sosPanel.addView(Ui.button(c,"View contact updates"){main.navigate(R.id.sosFragment)});sosPanel.visibility=View.GONE;panel.addView(sosPanel)
        val scroll=Ui.scroll(c,panel);root.addView(scroll,LinearLayout.LayoutParams(-1,Ui.dp(c,330)));return root
    }
    override fun onViewCreated(view:View,state:Bundle?){super.onViewCreated(view,state)
        main.tripViewModel.activeTrip.observe(viewLifecycleOwner){trip->current=trip;if(trip!=null)render(trip)}
        runAction{val t=repo.active();if(t==null){main.navigate(R.id.homeFragment);return@runAction};main.tripViewModel.startTracking()}
        loop=viewLifecycleOwner.lifecycleScope.launch{while(isActive){current?.let{render(it)};delay(1000)}}
    }
    private fun render(t:ActiveTrip){
        val c=requireContext();val route=t.route();demoControls?.visibility=if(route?.isDemoData==true)View.VISIBLE else View.GONE
        if(route!=null&&routeRevision!="${t.tripId}:${route.revision}"){renderer?.routes(listOf(route),route.routeId);renderer?.endpoints(null,Place(t.destinationLabel,t.destinationLat,t.destinationLng));strip.route=route;routeRevision="${t.tripId}:${route.revision}"}
        if(t.lastUpdateMs>0)renderer?.location(LocationFix(t.lastLatitude,t.lastLongitude,t.accuracyMeters,t.lastUpdateMs))
        val age=if(t.lastUpdateMs>0)(System.currentTimeMillis()-t.lastUpdateMs)/1000 else Long.MAX_VALUE
        val problem=repo.prefs.getString("tracking_problem",null)?:repo.prefs.getString("sync_error",null)?:if(route==null)"Saved route unavailable. End this journey and calculate a new route." else null
        status.text=when{t.isRehearsal->if(route?.isDemoData==true)"REHEARSAL · simulated location and contact alerts" else "PRACTICE · real route, simulated position and alerts";problem!=null->problem;age>60->"Location stale · precise segment alerts paused";t.syncStatus=="OFFLINE"->"Local monitoring · server unavailable";t.syncStatus=="PENDING"->"Monitoring · confirmation pending sync";else->"Monitoring · position updated ${age}s ago"}
        status.setTextColor(Ui.color(c,if(problem!=null||age>60&&!t.isRehearsal)R.color.risk_medium else R.color.risk_low))
        if(route!=null){val projection=TripMath.project(LatLng(t.lastLatitude,t.lastLongitude),route);val duration=route.durationSeconds*(projection.remainingMeters/route.distanceMeters).coerceIn(0.0,1.0)
            metrics.text="${(duration/60).toInt().coerceAtLeast(1)} min  ·  ${"%.1f".format(projection.remainingMeters/1000)} km"
            strip.progress=(1-projection.remainingMeters/route.distanceMeters).toFloat()
            maneuver.text=if(projection.remainingMeters<30&&route.destinationSnapMeters>30)"Walking access ends here. Your destination pin is ${route.destinationSnapMeters} m away." else if(repo.prefs.getBoolean("arrival_ready",false))"You're near your destination. Confirm arrival when ready." else route.steps.firstOrNull{it.wayPoints.lastOrNull()?.let{i->i>=projection.index}==true}?.instruction?:t.destinationLabel
        }
        normal.visibility=if(t.state==TripState.ACTIVE)View.VISIBLE else View.GONE;check.visibility=if(t.state==TripState.CHECK_IN_PENDING)View.VISIBLE else View.GONE;sosPanel.visibility=if(t.state==TripState.SOS_ACTIVE)View.VISIBLE else View.GONE
        recalc.visibility=if(!t.isRehearsal&&repo.prefs.getInt("off_route_fixes",0)>=3)View.VISIBLE else View.GONE
        if(t.state==TripState.CHECK_IN_PENDING){val seconds=((t.checkInDeadlineMs-System.currentTimeMillis())/1000).coerceAtLeast(0);countdown.text="${seconds/60}:${"%02d".format(seconds%60)} until contact escalation"}
    }
    private fun service(action:String){requireContext().startService(Intent(requireContext(),TripTrackingService::class.java).setAction(action))}
    private fun end(){val t=current?:return;val host=main;Ui.confirm(requireContext(),"Finish this journey?","Location monitoring and pending check-ins will stop.","Finish journey"){
        main.tripViewModel.endTrip{val mins=((System.currentTimeMillis()-t.startedAtMs)/60000).coerceAtLeast(1);host.navigate(R.id.homeFragment);com.google.android.material.dialog.MaterialAlertDialogBuilder(host).setTitle("You've finished your journey")
            .setMessage("${t.destinationLabel}\n$mins minutes · monitoring stopped\nYour journey is saved in Activity.").setPositiveButton("Done",null).show()}
    }}
    override fun onStart(){super.onStart();renderer?.view?.onStart()}
    override fun onResume(){super.onResume();renderer?.view?.onResume()}
    override fun onPause(){renderer?.view?.onPause();super.onPause()}
    override fun onStop(){renderer?.view?.onStop();super.onStop()}
    override fun onSaveInstanceState(out:Bundle){super.onSaveInstanceState(out);renderer?.view?.onSaveInstanceState(out)}
    override fun onDestroyView(){loop?.cancel();renderer?.destroy();renderer=null;current=null;demoControls=null;routeRevision="";super.onDestroyView()}
}
