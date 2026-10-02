package com.sheshield.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.sheshield.app.R
import com.sheshield.app.data.model.*

object RouteEvidenceDialog {
    fun show(c:Context,route:RouteOption){
        val body=Ui.col(c,22)
        if(route.isDemoData)body.addView(Ui.badge(c,"FICTIONAL EVIDENCE · RECORDED DEMO"))
        route.decision?.let{decision->
            body.addView(Ui.text(c,decision.summary,17,true))
            decision.reasons.orEmpty().filter{it.kind=="TIME"}.forEach{body.addView(Ui.space(c,8));body.addView(Ui.text(c,it.text,14))}
        }
        val context=route.contextEvidence.orEmpty()
        val reports=route.evidence.orEmpty()
        val evaluated=route.evaluatedAt.orEmpty()
        body.addView(Ui.space(c,12))
        if(route.isDemoData)body.addView(Ui.text(c,route.riskSummary,15))
        else {
            val type=if(route.passport?.datasetVersion!=null)"street matches" else "nearby reports"
            body.addView(Ui.text(c,"${route.incidentCount} $type · ${context.size} wider-area references",15,true))
        }
        route.passport?.let{p->
            body.addView(Ui.text(c,"${p.coveragePercent}% reporting coverage · ${p.longestUnknownMeters} m longest gap",14))
        }
        if(evaluated.isNotBlank())body.addView(Ui.text(c,"Evidence snapshot: ${evaluated.take(16).replace('T',' ')} UTC",13,tint=R.color.on_surface_secondary))
        route.environment?.let{e->body.addView(Ui.card(c,Ui.rowItem(c,"Walking conditions","Lighting, walkways & nearby places",R.drawable.ic_walk){WalkingDetails.show(c,e)}))}
        if(reports.isNotEmpty()){
            body.addView(Ui.space(c,16));body.addView(Ui.text(c,"Source reports",20,true))
            reports.take(30).forEach{report(c,body,it,false)}
        }
        if(context.isNotEmpty()){
            body.addView(Ui.space(c,16));body.addView(Ui.section(c,"Area reports & history"))
            body.addView(Ui.text(c,"These sources do not locate an incident on your lane. Historical reports keep their original dates.",14))
            context.take(30).forEach{report(c,body,it,true)}
        }else if(reports.isEmpty())body.addView(Ui.text(c,"No independently located report has been matched to this walking street.",14))
        val observations=route.observations.orEmpty()
        body.addView(Ui.space(c,16));body.addView(Ui.text(c,"Street conditions",20,true))
        if(observations.isEmpty())body.addView(Ui.text(c,"No current street-condition observations were available at evaluation.",14))
        else {
            if(route.segments.orEmpty().any{it.observationConflict})body.addView(Ui.text(c,"Observations disagree on a section. Its conditions remain unresolved.",14,true,R.color.risk_medium))
            observations.forEach{o->
                val expired=runCatching{java.time.Instant.parse(o.expiresAt).toEpochMilli()<=System.currentTimeMillis()}.getOrDefault(true)
                body.addView(Ui.text(c,"${if(expired)"Expired · " else ""}${o.kind.replace('_',' ')} · ${o.locationLabel?:"reviewed section"}\nObserved ${o.observedAt} · expires ${o.expiresAt}\nApplies to ${o.timeOfDay.replace('_',' ')}",14))
                link(c,body,o.sourceUrl,"Open observation")
            }
        }
        route.passport?.let{p->
            body.addView(Ui.space(c,16));body.addView(Ui.text(c,"Along your walk",20,true))
            p.stretches.orEmpty().forEach{stretch->
                val message=when(stretch.kind){"REPORTS"->if(route.decision!=null&&p.datasetVersion!=null)"Report matched to this reviewed street section" else "Nearby reports; street relation uncertain";"NO_REPORTS"->"No matching records in the supplied reporting feed";else->"Reporting gap · safety unknown"}
                body.addView(Ui.text(c,"${stretch.fromMeters}–${stretch.toMeters} m: $message",14));body.addView(Ui.space(c,6))
            }
            body.addView(Ui.space(c,16));body.addView(Ui.text(c,"Collection and limits",20,true))
            if(p.datasetVersion!=null){
                body.addView(Ui.text(c,"Dataset ${p.datasetVersion} · updated ${p.datasetUpdatedAt?.take(10)?:"unknown"}",13,tint=R.color.on_surface_secondary))
                p.datasetCollection?.let{body.addView(Ui.text(c,it,14))}
                body.addView(Ui.text(c,"At evaluation, ${p.observationCoveragePercent}% had unexpired reviewed observations of both lighting and walking access.",14))
            }
            p.coverageWindows.orEmpty().forEach{w->body.addView(Ui.text(c,"${w.source}: ${w.from.take(10)} to ${w.to.take(10)} · updated ${w.updatedAt.take(10)}",13))}
            p.limitations?.let{body.addView(Ui.space(c,12));body.addView(Ui.text(c,it,14))}
        }
        body.addView(Ui.space(c,12));body.addView(Ui.text(c,"Small route pieces do not establish equally precise incident locations. Missing reports never establish safety.",13,tint=R.color.on_surface_secondary))
        Ui.sheet(c,"Route insights",body)
    }
    private fun report(c:Context,container:android.widget.LinearLayout,e:Evidence,context:Boolean){
        val body=Ui.col(c,16)
        val category=e.category.replace('_',' ').replaceFirstChar{it.uppercase()}
        body.addView(Ui.text(c,(if(e.historical)"Historical · " else "")+category,16,true))
        val date=e.occurred?.label?.takeIf{it.isNotBlank()}?:e.occurred?.start?.take(10)?:"${e.daysOld} days ago"
        body.addView(Ui.text(c,"$date\n${e.locationLabel?:e.source}",14))
        e.summary?.let{body.addView(Ui.text(c,it,14))}
        e.reportStatus?.let{body.addView(Ui.text(c,it.replace('_',' ')+" · source checked ${e.reviewedAt?.take(10)?:"unknown"}",13,tint=R.color.on_surface_secondary))}
        if(e.relation=="MATCHED_STREET")body.addView(Ui.text(c,"Location reviews recorded: ${e.reviewCount} · matched by street name and reviewed geometry",13))
        if(context){e.exclusionReason?.let{body.addView(Ui.text(c,it,14,tint=R.color.risk_medium))};e.locationReason?.let{body.addView(Ui.text(c,it,13,tint=R.color.on_surface_secondary))}}
        else if(e.relation==null)body.addView(Ui.text(c,"Estimated ${e.distanceMinMeters}–${e.distanceMaxMeters} m from route · location uncertainty ±${e.precisionMeters} m\nNearby geography can include another street.",13))
        val references=e.references.orEmpty()
        if(references.isEmpty())e.sourceUrl?.let{link(c,body,it,"Open source report")}
        else references.forEach{link(c,body,it.url,"Open ${it.source}")}
        container.addView(Ui.card(c,body))
    }
    private fun link(c:Context,body:android.widget.LinearLayout,url:String,label:String){
        val uri=Uri.parse(url);if(uri.scheme!="https"||uri.host.isNullOrBlank())return
        body.addView(Ui.button(c,label,true){runCatching{c.startActivity(Intent(Intent.ACTION_VIEW,uri))}.onFailure{Ui.error(c,"A browser could not open this source.")}})
    }
}
