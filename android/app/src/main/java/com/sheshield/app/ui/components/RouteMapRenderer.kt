package com.sheshield.app.ui.components

import android.content.Context
import android.graphics.PointF
import android.os.Bundle
import com.mapbox.geojson.*
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import com.mapbox.mapboxsdk.maps.*
import com.mapbox.mapboxsdk.style.layers.*
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.*
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.sheshield.app.data.model.*

class RouteMapRenderer(context:Context,state:Bundle?=null){
    val view:MapView
    private var map:MapboxMap?=null
    private var style:Style?=null
    private var options:List<RouteOption> = emptyList()
    private var selected:String?=null
    private var current:LocationFix?=null
    private var follow=false
    private var avoided:List<AvoidArea> = emptyList()
    private var requestedOrigin:Place?=null
    private var requestedDestination:Place?=null
    var onPick:((Place)->Unit)?=null
    var onSelect:((String)->Unit)?=null
    var onReady:(()->Unit)?=null
    init{
        Mapbox.getInstance(context);view=MapView(context);view.onCreate(state)
        view.getMapAsync{m->map=m
            m.setStyle(Style.Builder().fromJson("""{"version":8,"sources":{"osm":{"type":"raster","tiles":["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],"tileSize":256,"attribution":"© OpenStreetMap contributors"}},"layers":[{"id":"osm","type":"raster","source":"osm","paint":{"raster-saturation":-0.65,"raster-contrast":-0.1}}]}""")){s->
                style=s;m.uiSettings.isCompassEnabled=false
                listOf("alternatives","selected","unknown","reports","high","medium","endpoints","position","avoidance").forEach{s.addSource(GeoJsonSource(it,FeatureCollection.fromFeatures(emptyArray<Feature>())))}
                s.addLayer(FillLayer("avoidance-fill","avoidance").withProperties(fillColor("#D65454"),fillOpacity(.25f)))
                s.addLayer(LineLayer("avoidance-outline","avoidance").withProperties(lineColor("#D65454"),lineWidth(2f)))
                s.addLayer(LineLayer("alternative-lines","alternatives").withProperties(lineColor("#9699AA"),lineWidth(4f),lineCap(Property.LINE_CAP_ROUND),lineJoin(Property.LINE_JOIN_ROUND)))
                s.addLayer(LineLayer("route-outline","selected").withProperties(lineColor("#FFFFFF"),lineWidth(9f),lineCap(Property.LINE_CAP_ROUND),lineJoin(Property.LINE_JOIN_ROUND)))
                s.addLayer(LineLayer("route-line","selected").withProperties(lineColor("#6554D9"),lineWidth(5f),lineCap(Property.LINE_CAP_ROUND),lineJoin(Property.LINE_JOIN_ROUND)))
                s.addLayer(LineLayer("unknown-line","unknown").withProperties(lineColor("#747887"),lineWidth(5f),lineDasharray(arrayOf(2f,2f))))
                s.addLayer(LineLayer("report-line","reports").withProperties(lineColor("#C98B2E"),lineWidth(5f)))
                s.addLayer(LineLayer("medium-line","medium").withProperties(lineColor("#C98B2E"),lineWidth(5f),lineCap(Property.LINE_CAP_ROUND)))
                s.addLayer(LineLayer("high-line","high").withProperties(lineColor("#D65454"),lineWidth(5f),lineCap(Property.LINE_CAP_ROUND)))
                s.addLayer(CircleLayer("endpoint-dots","endpoints").withProperties(circleRadius(6f),circleColor("#6554D9"),circleStrokeWidth(3f),circleStrokeColor("#FFFFFF")))
                s.addLayer(CircleLayer("position-halo","position").withProperties(circleRadius(18f),circleColor("#3285FF"),circleOpacity(.18f)))
                s.addLayer(CircleLayer("position-dot","position").withProperties(circleRadius(7f),circleColor("#3285FF"),circleStrokeWidth(3f),circleStrokeColor("#FFFFFF")))
                m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(22.56,88.35),13.0));draw(true);updatePosition();drawAvoidance();onReady?.invoke()
            }
            m.addOnMapLongClickListener{p->onPick?.invoke(Place("Selected map location",p.latitude,p.longitude));true}
            m.addOnMapClickListener{p->val screen=m.projection.toScreenLocation(p);val features=m.queryRenderedFeatures(android.graphics.RectF(screen.x-12,screen.y-12,screen.x+12,screen.y+12),"alternative-lines");val feature=features.firstOrNull();if(feature!=null){onSelect?.invoke(feature.getStringProperty("route_id"));true}else false}
            m.addOnCameraMoveStartedListener{reason->if(reason==MapboxMap.OnCameraMoveStartedListener.REASON_API_GESTURE)follow=false}
        }
    }
    private fun line(r:RouteOption)=Feature.fromGeometry(LineString.fromLngLats(r.geometry.map{Point.fromLngLat(it[0],it[1])})).apply{addStringProperty("route_id",r.routeId)}
    private fun source(id:String,features:List<Feature>){style?.getSourceAs<GeoJsonSource>(id)?.setGeoJson(FeatureCollection.fromFeatures(features))}
    fun routes(routes:List<RouteOption>,selectedId:String?,fit:Boolean=true){options=routes;selected=selectedId;draw(fit)}
    private fun draw(fit:Boolean){
        val m=map?:return;if(style==null)return
        source("alternatives",options.filter{it.routeId!=selected}.map{line(it)})
        val r=options.firstOrNull{it.routeId==selected}?:options.firstOrNull()
        source("selected",r?.let{listOf(line(it))}.orEmpty())
        listOf("unknown","reports","high","medium").forEach{level->source(level,r?.segments?.filter{(if(level=="reports")it.evidenceCount>0 else if(!r.isDemoData)level=="unknown" else it.level.equals(level,true))&&it.startIndex in r.geometry.indices&&it.endIndex in r.geometry.indices}?.map{s->Feature.fromGeometry(LineString.fromLngLats(r.geometry.subList(s.startIndex,s.endIndex+1).map{Point.fromLngLat(it[0],it[1])}))}.orEmpty())}
        val endpointPoints=r?.geometry?.takeIf{it.size>1}?.let{listOf(requestedOrigin?.let{p->listOf(p.longitude,p.latitude)}?:it.first(),requestedDestination?.let{p->listOf(p.longitude,p.latitude)}?:it.last())}.orEmpty()
        source("endpoints",endpointPoints.map{p->Feature.fromGeometry(Point.fromLngLat(p[0],p[1]))})
        if(fit&&r!=null&&r.geometry.size>1)view.post{runCatching{m.animateCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes((r.geometry+endpointPoints).map{LatLng(it[1],it[0])}).build(),Ui.dp(view.context,40)))}}
    }
    private fun updatePosition(){source("position",current?.let{listOf(Feature.fromGeometry(Point.fromLngLat(it.longitude,it.latitude)))}.orEmpty())}
    fun avoidAreas(areas:List<AvoidArea>){avoided=areas;drawAvoidance()}
    private fun drawAvoidance(){source("avoidance",avoided.map{area->
        val longitude=area.center[0];val latitude=area.center[1]
        val ring=(0..32).map{i->val angle=i*Math.PI/16;Point.fromLngLat(longitude+kotlin.math.cos(angle)*area.radiusMeters/(111320*kotlin.math.cos(latitude*Math.PI/180)),latitude+kotlin.math.sin(angle)*area.radiusMeters/111320)}
        Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)))
    })}
    fun location(fix:LocationFix){if(current==fix)return;current=fix;updatePosition();if(follow)map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(fix.latitude,fix.longitude),16.0),700)}
    fun recenter(){follow=true;current?.let{map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude,it.longitude),16.0))}}
    fun overview(){follow=false;draw(true)}
    fun endpoints(origin:Place?,destination:Place?,fit:Boolean=false){
        requestedOrigin=origin;requestedDestination=destination
        val fallback=options.firstOrNull{it.routeId==selected}?.geometry
        val points=listOfNotNull(origin?:fallback?.firstOrNull()?.let{Place("Walking start",it[1],it[0])},destination?:fallback?.lastOrNull()?.let{Place("Walking destination",it[1],it[0])})
        source("endpoints",points.map{Feature.fromGeometry(Point.fromLngLat(it.longitude,it.latitude))})
        if(fit&&style!=null){if(points.size==1)showPoint(points.first())else if(points.size>1)map?.animateCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(points.map{LatLng(it.latitude,it.longitude)}).build(),Ui.dp(view.context,40)))}
    }
    fun showPoint(place:Place){map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(place.latitude,place.longitude),15.0))}
    fun destroy(){view.onDestroy();map=null;style=null}
}
