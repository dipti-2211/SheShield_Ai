import {validPoint,haversine} from './risk.js';
export class ApiError extends Error {constructor(code,message,status=400,retryable=false){super(message);Object.assign(this,{code,status,retryable});}}
export async function fetchJson(url,options={},timeout=15000) {
  let r;for(let attempt=0;attempt<2;attempt++){try{r=await fetch(url,{...options,signal:AbortSignal.timeout(timeout)});break;}catch(e){if(attempt===1||e.name==='TimeoutError')throw new ApiError('PROVIDER_UNAVAILABLE','The external service did not respond. Try again.',503,true);await new Promise(resolve=>setTimeout(resolve,250));}}
  if(!r.ok)throw new ApiError(r.status===429?'RATE_LIMITED':'PROVIDER_ERROR',`Provider returned HTTP ${r.status}.`,503,true);
  try{return await r.json();}catch{throw new ApiError('INVALID_PROVIDER_RESPONSE','The provider returned an invalid response.',502,true);}
}
export function parseOrs(body,origin,destination) {
  if(!Array.isArray(body.features))throw new ApiError('INVALID_GEOMETRY','Routing returned no GeoJSON routes.',502);
  const seen=new Set();
  return body.features.flatMap((f,index)=>{
    const geometry=f.geometry?.coordinates,summary=f.properties?.summary;
    if(f.geometry?.type!=='LineString'||!Array.isArray(geometry)||geometry.length<2||!geometry.every(validPoint)||!Number.isFinite(summary?.distance)||!Number.isFinite(summary?.duration)||!(summary.distance>0)||!(summary.duration>0))throw new ApiError('INVALID_GEOMETRY','Routing returned invalid geometry or metrics.',502);
    const originSnap=haversine(origin[1],origin[0],geometry[0][1],geometry[0][0])*1000,destinationSnap=haversine(destination[1],destination[0],geometry.at(-1)[1],geometry.at(-1)[0])*1000;
    if(originSnap>200||destinationSnap>200)throw new ApiError('ENDPOINT_TOO_FAR','A selected endpoint is too far from an accessible walking path. Choose a nearby entrance or street.',422);
    const key=geometry.map(p=>p.map(x=>x.toFixed(5)).join(',')).join(';');if(seen.has(key))return [];seen.add(key);
    return [{route_id:`route-${index}`,geometry,duration_seconds:Math.round(summary.duration),distance_meters:Math.round(summary.distance),origin_snap_meters:Math.round(originSnap),destination_snap_meters:Math.round(destinationSnap),steps:(f.properties.segments||[]).flatMap(s=>s.steps||[])}];
  });
}
export async function routeLive(origin,destination,key,options={},request=fetchJson) {
  if(!key)throw new ApiError('ROUTING_NOT_CONFIGURED','Routing is not configured. Add ORS_API_KEY to the API environment.',503);
  const body={coordinates:[origin,destination],instructions:true,alternative_routes:{target_count:3,share_factor:.8,weight_factor:1.6}};
  if(options.avoid_polygons)body.options={avoid_polygons:options.avoid_polygons};
  const response=await request('https://api.openrouteservice.org/v2/directions/foot-walking/geojson',{method:'POST',headers:{Authorization:key,'Content-Type':'application/json'},body:JSON.stringify(body)});
  const routes=parseOrs(response,origin,destination);if(!routes.length)throw new ApiError('NO_ROUTES','No walking route found. Choose another destination.',422);
  return routes;
}
export async function searchPlaces(q,key,request=fetchJson) {
  if(q.length<3)return [];
  // Greater Kolkata, including Howrah and the airport. A focus point alone is not a filter.
  const within=places=>places.filter(p=>p.label&&Number.isFinite(p.latitude)&&Number.isFinite(p.longitude)&&p.latitude>=22.35&&p.latitude<=22.80&&p.longitude>=88.15&&p.longitude<=88.60);
  const terms=q.toLowerCase().split(/[^a-z0-9]+/).filter(t=>t&&!['kolkata','calcutta','india','west','bengal','wb'].includes(t));
  if(key){
    const body=await request(`https://api.openrouteservice.org/geocode/search?text=${encodeURIComponent(q)}&boundary.country=IND&boundary.rect.min_lon=88.15&boundary.rect.min_lat=22.35&boundary.rect.max_lon=88.60&boundary.rect.max_lat=22.80&focus.point.lat=22.56&focus.point.lon=88.35&size=6`,{headers:{Authorization:key}});
    const places=within((body.features||[]).map(f=>({id:f.properties?.id,label:f.properties?.label,latitude:f.geometry?.coordinates?.[1],longitude:f.geometry?.coordinates?.[0]})));
    const matches=places.filter(p=>terms.every(t=>p.label.toLowerCase().includes(t)));
    if(matches.length)return matches;
  }
  const body=await request(`https://nominatim.openstreetmap.org/search?format=jsonv2&countrycodes=in&viewbox=88.15,22.80,88.60,22.35&bounded=1&limit=6&q=${encodeURIComponent(q)}`,{headers:{'User-Agent':'SheShield/2.0 (Kolkata journey companion)'}});
  if(!Array.isArray(body))throw new ApiError('INVALID_PROVIDER_RESPONSE','Place search returned an invalid response.',502,true);
  return within(body.map(p=>({id:String(p.place_id),label:p.display_name,latitude:Number(p.lat),longitude:Number(p.lon)})));
}
