import Fastify from 'fastify';
import {randomUUID,randomBytes,createHash,createHmac,timingSafeEqual} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {Store} from './store.js';
import {validPoint,segmentDistanceKm} from './risk.js';
import {auditDataset,assessRoute,labelRoutes} from './evidence.js';
import {ApiError,routeLive,searchPlaces} from './providers.js';
import {freshOrigin,pointAhead,avoidancePolygon,avoidsAreas,differentAhead} from './rerouting.js';
import {voiceAlertMessage,smsAlertMessage} from './alert_messages.js';

const digest=s=>createHash('sha256').update(s).digest('hex');
const terminal=new Set(['COMPLETED','CANCELLED']);
const finalDelivery=new Set(['ACKNOWLEDGED','FAILED','NO_ANSWER','BUSY','CANCELLED','COMPLETED_UNCONFIRMED']);
const id=()=>randomUUID();
const point=p=>[p.longitude,p.latitude];
function location(p){if(!p||!validPoint(point(p)))throw new ApiError('INVALID_LOCATION','Choose a valid location.');return p;}
function contacts(items){if(!Array.isArray(items)||items.length>10)throw new ApiError('INVALID_CONTACTS','Choose up to ten contacts.');
 const seen=new Set();return items.map(c=>{const phone=String(c.phone||'').replace(/[\s()-]/g,'');if(!/^\+[1-9]\d{7,14}$/.test(phone)||!String(c.name||'').trim()||seen.has(phone))throw new ApiError('INVALID_CONTACTS','Each contact needs a name and a unique international phone number.');seen.add(phone);return {name:c.name.trim().slice(0,80),phone};});}

export function buildApp(options={}) {
 const config={...process.env,...options.config},store=options.store||new Store(config.DATABASE_PATH||(options.disableWorker?':memory:':'./data/sheshield.sqlite')),now=options.now||Date.now;
 const app=Fastify({logger:false,bodyLimit:256*1024,trustProxy:false});
 const scenario=options.scenario||JSON.parse(readFileSync(config.DEMO_PATH||new URL('../../demo/kolkata_scenario.json',import.meta.url)));
 let data=auditDataset({},now()),dataError=null;
 if(config.INCIDENT_DATA_PATH){try{data=auditDataset(JSON.parse(readFileSync(config.INCIDENT_DATA_PATH)),now());dataError=data.audit.error||null;}catch(e){dataError='Incident dataset could not be read or validated.';}}
 const cache=new Map(),rate=new Map();let lastSearch=0;
 app.addContentTypeParser('application/x-www-form-urlencoded',{parseAs:'string'},(_,body,done)=>done(null,Object.fromEntries(new URLSearchParams(body))));
 app.setErrorHandler((error,request,reply)=>reply.code(error.status||error.statusCode||500).send({code:error.code||'INTERNAL_ERROR',message:error.status||error.statusCode?error.message:'Something went wrong. Try again.',retryable:error.retryable||false,request_id:request.id}));
 app.addHook('onRequest',async(req,reply)=>{
  reply.header('Cache-Control','no-store');reply.header('Referrer-Policy','no-referrer');reply.header('X-Content-Type-Options','nosniff');
  const key=req.ip,count=rate.get(key)||{at:now(),n:0};if(now()-count.at>60000){count.at=now();count.n=0;}count.n++;rate.set(key,count);
  if(count.n>180)throw new ApiError('RATE_LIMITED','Too many requests. Try again shortly.',429,true);
 });
 async function auth(req){const token=req.headers.authorization?.replace(/^Bearer /,'');if(!token)throw new ApiError('UNAUTHORIZED','A session is required.',401);
  const session=store.get('session',digest(token));if(!session||session.expires_at_ms<now())throw new ApiError('UNAUTHORIZED','Session expired. Reconnect to the API.',401);req.owner=session.owner;}
 async function worker(req){if(!config.WORKER_TOKEN||req.headers['x-worker-token']!==config.WORKER_TOKEN)throw new ApiError('UNAUTHORIZED','Invalid worker credential.',401);}
 function owned(kind,key,owner){const item=store.get(kind,key);if(!item||item.owner!==owner)throw new ApiError('NOT_FOUND','This item was not found.',404);return item;}
 function saveTrip(t){t.version=(t.version||0)+1;t.updated_at_ms=now();return store.put('trip',t);}
 function activeTrip(key,owner){const t=owned('trip',key,owner);if(terminal.has(t.state))throw new ApiError('TRIP_ENDED','This journey has ended.',409);return t;}
 function command(req,fn){const key=req.headers['idempotency-key'];if(!key)throw new ApiError('IDEMPOTENCY_REQUIRED','A unique command ID is required.');
  const hash=digest(req.method+req.url+JSON.stringify(req.body||{}));return store.transaction(()=>{
   const row=store.db.prepare('SELECT * FROM commands WHERE owner=? AND key=?').get(req.owner,key);
   if(row){if(row.body_hash!==hash)throw new ApiError('CONFLICT','This command ID was already used for a different action.',409);return JSON.parse(row.response);}
   const result=fn();store.db.prepare('INSERT INTO commands VALUES (?,?,?,?)').run(req.owner,key,hash,JSON.stringify(result));return result;
  });}
 function addTimeline(incident,status,message,contact=null){incident.timeline.push({id:id(),status,message,contact,at_ms:now()});incident.updated_at_ms=now();}
 function smsConfigured(contact){return config.LIVE_ALERTS_ENABLED==='true'&&config.LIVE_SMS_ENABLED==='true'&&config.N8N_WEBHOOK_URL&&config.WORKER_TOKEN&&config.PUBLIC_BASE_URL&&config.TWILIO_AUTH_TOKEN&&(config.TEST_RECIPIENT_ALLOWLIST||'').split(',').map(s=>s.trim()).includes(contact.phone);}
 function createSmsAttempts(incident){
  if(incident.mode!=='LIVE')return;
  incident.sms_attempt_ids=[];
  for(const contact of incident.contacts){
   const ready=smsConfigured(contact);
   const sms={id:id(),owner:incident.owner,incident_id:incident.id,contact,mode:'LIVE',status:ready?'QUEUED':'UNAVAILABLE',provider_id:null,created_at_ms:now(),updated_at_ms:now()};
   incident.sms_attempt_ids.push(sms.id);store.put('sms_attempt',sms);
   addTimeline(incident,'SMS_'+sms.status,ready?`${contact.name}: cloud SMS queued`:`${contact.name}: cloud SMS unavailable with current configuration`,contact.name);
  }
  store.put('sos',incident);
 }
 function updateSms(sms,status,providerId){
  const incident=store.get('sos',sms.incident_id);if(!incident)return;
  if(providerId&&sms.provider_id&&sms.provider_id!==providerId)throw new ApiError('INVALID_CALLBACK','Unknown message.',400);
  const final=new Set(['DELIVERED','UNDELIVERED','FAILED','CANCELLED']);
  if(final.has(sms.status)||sms.status===status)return;
  const order={QUEUED:0,DISPATCHING:1,REQUEST_UNKNOWN:2,REQUESTED:2,SENDING:3,SENT:4};
  if(order[status]!=null&&order[sms.status]!=null&&order[status]<order[sms.status])return;
  sms.status=status;sms.updated_at_ms=now();if(providerId)sms.provider_id=providerId;store.put('sms_attempt',sms);
  addTimeline(incident,'SMS_'+status,`${sms.contact.name}: cloud SMS ${status.toLowerCase().replaceAll('_',' ')}`,sms.contact.name);store.put('sos',incident);
 }
 function createAttempt(incident){
  if(incident.cancelled||incident.status==='ACKNOWLEDGED')return;
  const i=incident.next_contact_index||0;if(i>=incident.contacts.length){incident.status='EXHAUSTED';addTimeline(incident,'EXHAUSTED','No contact has acknowledged yet. You can call emergency services.');store.put('sos',incident);return;}
  const contact=incident.contacts[i];incident.next_contact_index=i+1;
  const enabled=config.LIVE_ALERTS_ENABLED==='true',allowed=(config.TEST_RECIPIENT_ALLOWLIST||'').split(',').map(s=>s.trim());
  const ready=incident.mode==='REHEARSAL'||enabled&&config.N8N_WEBHOOK_URL&&config.WORKER_TOKEN&&config.PUBLIC_BASE_URL&&config.TWILIO_AUTH_TOKEN&&allowed.includes(contact.phone);
  const attempt={id:id(),owner:incident.owner,incident_id:incident.id,contact_index:i,contact,mode:incident.mode,status:ready?'QUEUED':'UNAVAILABLE',created_at_ms:now(),updated_at_ms:now(),provider_id:null};
  incident.attempt_ids.push(attempt.id);incident.status=ready?'CONTACTING':'UNAVAILABLE';
  addTimeline(incident,attempt.status,ready?`Preparing to contact ${contact.name}`:'Remote calls are unavailable for this recipient. Use the device call or text options.',contact.name);
  store.put('attempt',attempt);store.put('sos',incident);
  if(!ready&&incident.next_contact_index<incident.contacts.length)createAttempt(incident);
 }
 function createSos(owner,b){
  const t=b.trip_id?activeTrip(b.trip_id,owner):null;
  if(t?.sos_id)return owned('sos',t.sos_id,owner);
  const mode=t?.mode||b.mode;const existing=!t&&store.list('sos',owner).find(s=>!s.trip_id&&s.mode===mode&&!s.cancelled&&['REQUESTED','CONTACTING','PENDING','REQUEST_UNKNOWN'].includes(s.status)&&now()-s.created_at_ms<300000);if(existing)return existing;if(!['LIVE','REHEARSAL'].includes(mode))throw new ApiError('INVALID_MODE','Choose live or rehearsal mode.');
  const list=mode==='REHEARSAL'?[{name:'Aditi (demo)',phone:'+910000000001'},{name:'Riya (demo)',phone:'+910000000002'}]:contacts(t?.contacts||b.contacts||[]);
  const loc=b.location||t?.last_location||null;if(loc)location(loc);
  const incident={id:id(),owner,trip_id:t?.id||null,mode,trigger:b.trigger==='TIMEOUT'?'TIMEOUT':'MANUAL',location:loc,contacts:list,status:'REQUESTED',cancelled:false,attempt_ids:[],timeline:[],created_at_ms:now(),updated_at_ms:now()};
  addTimeline(incident,'REQUESTED',mode==='REHEARSAL'?'Rehearsal alert created. No real messages or calls.':'SOS saved. Preparing available contact channels.');
  if(!list.length){incident.status='NO_CONTACTS';addTimeline(incident,'NO_CONTACTS','No trusted contacts configured. The emergency dialer is available.');}
  store.put('sos',incident);if(t){t.state='SOS_ACTIVE';t.sos_id=incident.id;if(t.check_in)t.check_in.status='SOS';saveTrip(t);}if(list.length)createAttempt(incident);createSmsAttempts(incident);return incident;
 }
 function updateAttempt(attempt,status,providerId){
  const incident=store.get('sos',attempt.incident_id);if(!incident)return;
  if(incident.cancelled||finalDelivery.has(attempt.status)&&status!=='ACKNOWLEDGED'||attempt.status===status)return;
  const order={QUEUED:0,DISPATCHING:1,REQUESTED:2,REQUEST_UNKNOWN:2,RINGING:3,IN_PROGRESS:4};if(order[status]!=null&&order[attempt.status]!=null&&order[status]<order[attempt.status])return;
  attempt.status=status;attempt.updated_at_ms=now();if(providerId)attempt.provider_id=providerId;store.put('attempt',attempt);
  addTimeline(incident,status,`${attempt.contact.name}: ${status.toLowerCase().replaceAll('_',' ')}`,attempt.contact.name);
  if(status==='ACKNOWLEDGED'){incident.status='ACKNOWLEDGED';for(const key of incident.attempt_ids){if(key===attempt.id)continue;const other=store.get('attempt',key);if(other&&['QUEUED','DISPATCHING'].includes(other.status)){other.status='CANCELLED';store.put('attempt',other);}}}
  store.put('sos',incident);
  if(['FAILED','NO_ANSWER','BUSY','COMPLETED_UNCONFIRMED'].includes(status))createAttempt(incident);
 }
 function presentSos(s){return {...s,attempts:s.attempt_ids.map(k=>store.get('attempt',k)),sms_attempts:(s.sms_attempt_ids||[]).map(k=>store.get('sms_attempt',k))};}

 app.get('/health',async()=>({status:'ok',version:'2.0.0'}));
 app.get('/ready',async()=>({status:'ok',routing:Boolean(config.ORS_API_KEY),risk_data:data.incidents.length>0,data_error:dataError,n8n:Boolean(config.N8N_WEBHOOK_URL&&config.WORKER_TOKEN),callbacks:Boolean(config.PUBLIC_BASE_URL&&config.TWILIO_AUTH_TOKEN),live_alerts:config.LIVE_ALERTS_ENABLED==='true',cloud_sms:config.LIVE_SMS_ENABLED==='true'&&config.LIVE_ALERTS_ENABLED==='true',rehearsal:true}));
 app.get('/v1/evidence/status',{preHandler:auth},async()=>({audit:data.audit,sources:data.sources,coverage_areas:data.areas.length,policy:'Reviewed public-space locations within 100 m precision; 50 m route analysis; no inference from city totals.'}));
 app.post('/v1/sessions',async(req)=>{
  if(config.ENROLLMENT_CODE&&req.body?.enrollment_code!==config.ENROLLMENT_CODE)throw new ApiError('ENROLLMENT_REQUIRED','Enter the API enrollment code.',403);
  const token=randomBytes(32).toString('hex'),owner=id();store.put('session',{id:digest(token),owner,expires_at_ms:now()+30*86400000});return {session_token:token,installation_id:owner};
 });
 app.get('/v1/places',{preHandler:auth},async(req)=>{
  const q=String(req.query.q||'').trim();if(q.length<3||q.length>200)throw new ApiError('INVALID_QUERY','Search with 3–200 characters.');
  const cached=cache.get(q);if(cached&&now()-cached.at<3600000)return {places:cached.places};
  if(now()-lastSearch<1100)throw new ApiError('RATE_LIMITED','Please wait a moment before searching again.',429,true);lastSearch=now();
  const places=await (options.searchPlaces||searchPlaces)(q,config.ORS_API_KEY);cache.set(q,{places,at:now()});return {places};
 });
 app.post('/v1/plans',{preHandler:auth},async(req)=>{
  const b=req.body||{},mode=b.mode,recorded=mode==='REHEARSAL'&&b.recorded_scenario===true;if(!['LIVE','REHEARSAL'].includes(mode))throw new ApiError('INVALID_MODE','Choose live or rehearsal mode.');
  const origin=recorded?scenario.origin:location(b.origin),destination=recorded?scenario.destination:location(b.destination);
  const routes=recorded?structuredClone(scenario.routes):await (options.routeLive||routeLive)(point(origin),point(destination),config.ORS_API_KEY);
  const at=recorded?Date.parse(scenario.evaluated_at):now();
  const scored=labelRoutes(routes.map(r=>assessRoute(r,data,at,recorded?scenario.incidents:null)));
  const plan={id:id(),owner:req.owner,mode,origin,destination,routes:scored,created_at_ms:now(),expires_at_ms:now()+3600000,attribution:recorded?scenario.attribution:'OpenRouteService / OpenStreetMap contributors',geometry_source:recorded?scenario.geometry_source:'OpenRouteService walking directions'};
  return store.put('plan',plan);
 });
 app.post('/v1/trips',{preHandler:auth},async(req)=>command(req,()=>{
  const b=req.body||{},plan=owned('plan',b.plan_id,req.owner);if(plan.expires_at_ms<now())throw new ApiError('PLAN_EXPIRED','Calculate routes again.',409);
  const existing=store.list('trip',req.owner).find(t=>!terminal.has(t.state));if(existing)throw new ApiError('ACTIVE_TRIP_EXISTS','Finish the current journey first.',409);
  const route=plan.routes.find(r=>r.route_id===b.route_id);if(!route)throw new ApiError('INVALID_ROUTE','Select a route from this plan.');
  return saveTrip({id:id(),owner:req.owner,mode:plan.mode,state:'ACTIVE',origin:plan.origin,destination:plan.destination,route,contacts:plan.mode==='REHEARSAL'?[]:contacts(b.contacts||[]),last_location:null,check_in:null,sos_id:null,started_at_ms:now(),ended_at_ms:null,last_sequence:-1,version:0});
 }));
 app.get('/v1/trips',{preHandler:auth},async req=>({trips:store.list('trip',req.owner).sort((a,b)=>b.started_at_ms-a.started_at_ms)}));
 app.get('/v1/trips/:id',{preHandler:auth},async req=>owned('trip',req.params.id,req.owner));
 app.post('/v1/trips/:id/locations',{preHandler:auth},async(req)=>{
  const t=activeTrip(req.params.id,req.owner),b=req.body||{},loc=location(b);if(!Number.isFinite(b.timestamp_ms)||b.timestamp_ms>now()+60000||b.timestamp_ms<now()-600000||!Number.isInteger(b.sequence)||!Number.isFinite(b.accuracy_meters)||b.accuracy_meters<0||b.accuracy_meters>10000)throw new ApiError('INVALID_FIX','Location is stale or invalid.');
  if(b.sequence>t.last_sequence){t.last_sequence=b.sequence;t.last_location=loc;saveTrip(t);}return t;
 });
 app.post('/v1/trips/:id/check-ins',{preHandler:auth},async req=>command(req,()=>{
  const t=activeTrip(req.params.id,req.owner),b=req.body||{};
  if(t.state==='SOS_ACTIVE')throw new ApiError('SOS_ACTIVE','An SOS incident is already active.',409);
  if(t.check_in?.status==='PENDING')return t;
  const personal=b.kind==='PERSONAL';
  if(b.kind!=null&&!['PERSONAL','SEGMENT'].includes(b.kind))throw new ApiError('INVALID_KIND','Choose a personal or route check-in.');
  const segment=personal?null:t.route.segments.find(s=>s.segment_id===b.segment_id);
  if(!personal&&(!segment||!['MEDIUM','HIGH'].includes(segment.risk_level)))throw new ApiError('INVALID_SEGMENT','This segment does not require a check-in.');
  if(b.location){const fix=location(b.location);if(!Number.isFinite(fix.timestamp_ms)||Math.abs(now()-fix.timestamp_ms)>600000||!(fix.accuracy_meters>=0&&fix.accuracy_meters<=50))throw new ApiError('INVALID_FIX','Check-ins require a recent, accurate location.');t.last_location=fix;}
  if(!personal&&(!t.last_location||segmentDistanceKm(point(t.last_location),t.route.geometry[segment.start_index],t.route.geometry[segment.end_index])>.1))throw new ApiError('LOCATION_MISMATCH','The current location is outside this segment.');
  const seconds=personal?b.window_seconds:(t.mode==='REHEARSAL'?20:300);
  if(!Number.isInteger(seconds)||seconds<(t.mode==='REHEARSAL'?20:60)||seconds>1800)throw new ApiError('INVALID_WINDOW','Choose a watch lasting 1–30 minutes.');
  const windowMs=seconds*1000,deadline=b.deadline_ms??now()+windowMs;
  if(!Number.isFinite(deadline)||deadline>now()+windowMs+5000||deadline<now()-600000)throw new ApiError('INVALID_DEADLINE','The check-in deadline is invalid.');
  const allowed=(config.TEST_RECIPIENT_ALLOWLIST||'').split(',').map(x=>x.trim());
  const eligible=t.contacts.filter(c=>allowed.includes(c.phone)).length;
  const deliveryReady=t.mode==='REHEARSAL'||Boolean(config.LIVE_ALERTS_ENABLED==='true'&&config.N8N_WEBHOOK_URL&&config.WORKER_TOKEN&&config.PUBLIC_BASE_URL&&config.TWILIO_AUTH_TOKEN&&eligible>0);
  t.check_in={id:String(b.event_id||id()),kind:personal?'PERSONAL':'SEGMENT',segment_id:personal?'personal':b.segment_id,status:'PENDING',created_at_ms:Math.min(now(),deadline-windowMs),deadline_ms:deadline,server_registered_at_ms:now(),delivery_ready:deliveryReady,eligible_contacts:eligible,companion_seen_at_ms:null};
  t.state='CHECK_IN_PENDING';return saveTrip(t);
 }));
 app.post('/v1/trips/:id/check-ins/:eventId/resolve',{preHandler:auth},async req=>command(req,()=>{
  const t=activeTrip(req.params.id,req.owner);if(t.check_in?.id!==req.params.eventId)throw new ApiError('STALE_CHECK_IN','This check-in is no longer current.',409);
  if(req.body?.status==='SAFE'){
   const at=req.body.resolved_at_ms??now();if(!Number.isFinite(at)||at>now()+5000||at<t.check_in.created_at_ms-5000||at<now()-600000)throw new ApiError('INVALID_ACKNOWLEDGEMENT','The acknowledgement time is invalid.');
   if(at>t.check_in.deadline_ms){if(t.check_in.status==='PENDING')createSos(req.owner,{trip_id:t.id,trigger:'TIMEOUT'});return store.get('trip',t.id);}
   if(t.check_in.status==='SOS'){const incident=t.sos_id&&store.get('sos',t.sos_id);if(incident?.trigger!=='TIMEOUT')return t;cancelIncident(incident);t.sos_id=null;}
   else if(t.check_in.status!=='PENDING')return t;
   t.check_in.status='SAFE';t.state='ACTIVE';return saveTrip(t);
  }
  if(t.check_in.status!=='PENDING')return t;
  if(req.body?.status==='SOS'){createSos(req.owner,{trip_id:t.id,trigger:'MANUAL'});return store.get('trip',t.id);}throw new ApiError('INVALID_STATUS','Choose SAFE or SOS.');
 }));
 app.post('/v1/trips/:id/end',{preHandler:auth},async req=>command(req,()=>{
  const t=owned('trip',req.params.id,req.owner);if(terminal.has(t.state))return t;
  if(t.sos_id)cancelIncident(owned('sos',t.sos_id,req.owner));t.state=req.body?.cancelled?'CANCELLED':'COMPLETED';t.ended_at_ms=now();if(t.check_in?.status==='PENDING')t.check_in.status='CANCELLED';return saveTrip(t);
 }));
 app.delete('/v1/trips/:id',{preHandler:auth},async req=>{const t=owned('trip',req.params.id,req.owner);if(!terminal.has(t.state))throw new ApiError('TRIP_ACTIVE','End this journey before deleting it.',409);store.remove('trip',t.id);return {status:'DELETED'};});
 app.post('/v1/trips/:id/reroute',{preHandler:auth},async req=>{
  const t=activeTrip(req.params.id,req.owner);if(t.mode==='REHEARSAL')throw new ApiError('DEMO_ROUTE','Rehearsal uses a recorded route.',409);
  if(!['ACTIVE','CHECK_IN_PENDING'].includes(t.state))throw new ApiError('SOS_ACTIVE','Resolve the current SOS before changing route.',409);
  const origin=freshOrigin(req.body?.origin,now()),areas=[...(t.avoid_areas||[])];
  if(req.body?.avoid_ahead_meters!=null){
   const ahead=req.body.avoid_ahead_meters;if(!Number.isFinite(ahead)||ahead<80||ahead>500)throw new ApiError('INVALID_AVOIDANCE','Choose a stretch 80–500 m ahead.');
   if(areas.length>=5)throw new ApiError('AVOIDANCE_LIMIT','This journey already avoids five areas. Start a new journey to change them.',409);
   const center=pointAhead(t.route.geometry,origin,ahead);
   if(segmentDistanceKm(origin,center,center)*1000<65||segmentDistanceKm(point(t.destination),center,center)*1000<65)throw new ApiError('AVOIDANCE_TOO_CLOSE','That area includes your position or destination. Choose another stretch.',422);
   areas.push({center,radius_meters:35});
  }
  const routes=await (options.routeLive||routeLive)(origin,point(t.destination),config.ORS_API_KEY,areas.length?{avoid_polygons:avoidancePolygon(areas)}:{});
  const latest=activeTrip(t.id,req.owner);if(latest.route.route_revision!==t.route.route_revision||!['ACTIVE','CHECK_IN_PENDING'].includes(latest.state))throw new ApiError('ROUTE_CHANGED','The journey changed while calculating. Request alternatives again.',409);
  const usable=routes.filter(r=>(r.origin_snap_meters||0)<=50&&avoidsAreas(r,areas)&&differentAhead(r,t.route,origin));
  if(!usable.length)throw new ApiError('NO_ALTERNATIVE','No different walking route was found from your position with these avoidances. Your current route and check-in remain active.',422,true);
  const scored=labelRoutes(usable.map(r=>({...assessRoute(r,data,now()),route_revision:(t.route.route_revision||1)+1})));
  const proposal={id:t.id,owner:t.owner,proposal_id:id(),routes:scored,route:scored[0],origin:req.body.origin,avoid_areas:areas,base_revision:t.route.route_revision,expires_at_ms:now()+300000};store.put('reroute',proposal);
  return {...scored[0],trip_id:t.id,proposal_id:proposal.proposal_id,routes:scored,origin:proposal.origin,avoid_areas:areas,expires_at_ms:proposal.expires_at_ms};
 });
 app.post('/v1/trips/:id/route',{preHandler:auth},async req=>command(req,()=>{
  const t=activeTrip(req.params.id,req.owner),proposal=owned('reroute',t.id,req.owner),body=req.body||{};
  const selected=body.route_id?proposal.routes.find(r=>r.route_id===body.route_id):proposal.route;
  if(body.expected_check_in_id!==undefined&&(body.expected_check_in_id===''?null:body.expected_check_in_id)!==(t.check_in?.status==='PENDING'?t.check_in.id:null))throw new ApiError('CHECK_IN_CHANGED','Your check-in changed. Wait for confirmation and try accepting again.',409,true);
  if(!['ACTIVE','CHECK_IN_PENDING'].includes(t.state)||proposal.expires_at_ms<=now()||proposal.base_revision!==t.route.route_revision||body.proposal_id&&body.proposal_id!==proposal.proposal_id||!selected||(body.route_revision??body.route?.route_revision)!==selected.route_revision)throw new ApiError('INVALID_ROUTE','Calculate the updated route again.',409);
  if(!t.last_location)throw new ApiError('FRESH_GPS_REQUIRED','Wait for a GPS update before accepting this route.',422,true);
  const position=freshOrigin(t.last_location,now());
  if(segmentDistanceKm(position,point(proposal.origin),point(proposal.origin))*1000>75)throw new ApiError('POSITION_CHANGED','You moved away from the proposed start. Find alternatives again.',409,true);
  t.route=selected;t.avoid_areas=proposal.avoid_areas;store.remove('reroute',t.id);return saveTrip(t);
 }));
 app.post('/v1/sos',{preHandler:auth},async req=>command(req,()=>presentSos(createSos(req.owner,req.body||{}))));
 app.get('/v1/sos/:id',{preHandler:auth},async req=>presentSos(owned('sos',req.params.id,req.owner)));
 function cancelIncident(s){if(s.cancelled)return s;s.cancelled=true;s.status='CANCELLED';addTimeline(s,'CANCELLED','Future escalation cancelled. Messages already sent cannot be recalled.');for(const key of s.attempt_ids){const a=store.get('attempt',key);if(!finalDelivery.has(a.status)){a.status='CANCELLED';store.put('attempt',a);}}for(const key of s.sms_attempt_ids||[]){const sms=store.get('sms_attempt',key);if(sms?.status==='QUEUED'){sms.status='CANCELLED';store.put('sms_attempt',sms);}}return store.put('sos',s);}
 app.post('/v1/sos/:id/cancel',{preHandler:auth},async req=>command(req,()=>{
  const s=cancelIncident(owned('sos',req.params.id,req.owner));if(s.trip_id){const t=store.get('trip',s.trip_id);if(t&&!terminal.has(t.state)){t.state='ACTIVE';t.sos_id=null;saveTrip(t);}}return presentSos(s);
 }));
 app.post('/internal/attempts/:id/claim',{preHandler:worker},async req=>store.transaction(()=>{
  const a=store.get('attempt',req.params.id);if(!a||a.mode!=='LIVE'||a.status!=='DISPATCHING')throw new ApiError('ALREADY_CLAIMED','This attempt is unavailable or already claimed.',409);
  const s=store.get('sos',a.incident_id);if(s.cancelled)throw new ApiError('CANCELLED','This alert was cancelled.',409);a.status='REQUESTED';store.put('attempt',a);
  const base=config.PUBLIC_BASE_URL.replace(/\/$/,'');
  return {attempt_id:a.id,to:a.contact.phone,message:voiceAlertMessage(s,now()),callback_url:`${base}/v1/provider/twilio/status?attempt_id=${a.id}`,ack_url:`${base}/v1/provider/twilio/ack?attempt_id=${a.id}`};
 }));
 app.post('/internal/attempts/:id/result',{preHandler:worker},async req=>{const a=store.get('attempt',req.params.id);if(!a)throw new ApiError('NOT_FOUND','Attempt missing.',404);updateAttempt(a,req.body?.call_sid?'REQUESTED':req.body?.outcome_unknown?'REQUEST_UNKNOWN':'FAILED',req.body?.call_sid);return {status:'ok'};});
 app.post('/internal/sms/:id/claim',{preHandler:worker},async req=>store.transaction(()=>{
  const sms=store.get('sms_attempt',req.params.id);if(!sms||sms.status!=='DISPATCHING')throw new ApiError('ALREADY_CLAIMED','This SMS is unavailable or already claimed.',409);
  if(!smsConfigured(sms.contact))throw new ApiError('DELIVERY_DISABLED','Cloud SMS is disabled or unavailable for this contact.',409);
  const incident=store.get('sos',sms.incident_id);if(incident.cancelled)throw new ApiError('CANCELLED','This alert was cancelled.',409);
  sms.status='REQUESTED';store.put('sms_attempt',sms);
  return {attempt_id:sms.id,to:sms.contact.phone,message:smsAlertMessage(incident),callback_url:`${config.PUBLIC_BASE_URL.replace(/\/$/,'')}/v1/provider/twilio/sms-status?attempt_id=${sms.id}`};
 }));
 app.post('/internal/sms/:id/result',{preHandler:worker},async req=>{
  const sms=store.get('sms_attempt',req.params.id);if(!sms)throw new ApiError('NOT_FOUND','SMS attempt missing.',404);
  if(!['REQUESTED','REQUEST_UNKNOWN','SENDING','SENT','DELIVERED','UNDELIVERED','FAILED'].includes(sms.status))throw new ApiError('ALREADY_CLAIMED','Claim the SMS before recording a result.',409);
  const sid=req.body?.message_sid;if(sid&&!/^SM[a-f0-9]{32}$/i.test(sid))throw new ApiError('INVALID_PROVIDER_ID','Invalid message ID.');
  updateSms(sms,sid?'REQUESTED':req.body?.outcome_unknown?'REQUEST_UNKNOWN':'FAILED',sid);return {status:'ok'};
 });
 function validateSignature(req){
  if(!config.TWILIO_AUTH_TOKEN||!config.PUBLIC_BASE_URL)throw new ApiError('UNAUTHORIZED','Callbacks are not configured.',403);
  const url=config.PUBLIC_BASE_URL.replace(/\/$/,'')+req.raw.url;
  const input=url+Object.keys(req.body||{}).sort().map(k=>k+req.body[k]).join('');
  const signature=createHmac('sha1',config.TWILIO_AUTH_TOKEN).update(input).digest('base64'),given=String(req.headers['x-twilio-signature']||'');
  if(given.length!==signature.length||!timingSafeEqual(Buffer.from(given),Buffer.from(signature)))throw new ApiError('UNAUTHORIZED','Invalid callback signature.',403);
 }
 function validateTwilio(req){
  validateSignature(req);
  const a=store.get('attempt',req.query.attempt_id);if(!a||a.mode!=='LIVE'||a.provider_id&&a.provider_id!==req.body.CallSid)throw new ApiError('INVALID_CALLBACK','Unknown call.',400);return a;
 }
 app.post('/v1/provider/twilio/status',async req=>{const a=validateTwilio(req);const statuses={initiated:'REQUESTED',queued:'REQUESTED',ringing:'RINGING','in-progress':'IN_PROGRESS',completed:'COMPLETED_UNCONFIRMED','no-answer':'NO_ANSWER',busy:'BUSY',failed:'FAILED',canceled:'CANCELLED'};const status=statuses[req.body.CallStatus];if(status)updateAttempt(a,status,req.body.CallSid);return {status:'ok'};});
 app.post('/v1/provider/twilio/ack',async(req,reply)=>{const a=validateTwilio(req);if(req.body.Digits==='1')updateAttempt(a,'ACKNOWLEDGED',req.body.CallSid);return reply.type('text/xml').send('<Response><Say>Thank you. Your acknowledgement has been recorded.</Say><Hangup/></Response>');});
 app.post('/v1/provider/twilio/sms-status',async req=>{
  validateSignature(req);const sms=store.get('sms_attempt',req.query.attempt_id),sid=req.body.MessageSid;
  if(!sms||!/^SM[a-f0-9]{32}$/i.test(sid||'')||sms.provider_id&&sms.provider_id!==sid||['QUEUED','UNAVAILABLE','CANCELLED'].includes(sms.status))throw new ApiError('INVALID_CALLBACK','Unknown SMS.',400);
  const statuses={accepted:'REQUESTED',queued:'REQUESTED',sending:'SENDING',sent:'SENT',delivered:'DELIVERED',undelivered:'UNDELIVERED',failed:'FAILED'};
  const status=statuses[req.body.MessageStatus];if(status)updateSms(sms,status,sid);return {status:'ok'};
 });
 app.register(async shareApi=>{
  // This action needs no input. Older clients can label an empty POST as binary.
  // Keep compatibility local to this endpoint and reject unsupported nonempty bodies.
  shareApi.addContentTypeParser('*',{parseAs:'buffer'},(_,body,done)=>{
   if(body.length===0)return done(null,{});
   done(new ApiError('UNSUPPORTED_MEDIA_TYPE','Unsupported Media Type',415));
  });
  shareApi.post('/v1/trips/:id/share',{preHandler:auth},async req=>{
   const t=activeTrip(req.params.id,req.owner);if(!config.PUBLIC_BASE_URL)throw new ApiError('SHARING_NOT_CONFIGURED','A public HTTPS connection is needed for a live link.',503);
   const token=randomBytes(24).toString('hex');store.put('share',{id:digest(token),owner:req.owner,trip_id:t.id,expires_at_ms:now()+7200000});return {url:`${config.PUBLIC_BASE_URL}/share/${token}`,expires_at_ms:now()+7200000};
  });
 });
 function sharedTrip(token){const share=store.get('share',digest(token));if(!share||share.expires_at_ms<=now())throw new ApiError('EXPIRED','This trip link has expired or was revoked.',410);const trip=store.get('trip',share.trip_id);if(!trip)throw new ApiError('NOT_FOUND','Journey removed.',404);if(terminal.has(trip.state))throw new ApiError('TRIP_ENDED','This journey has ended. Location sharing has stopped.',410);return {share,trip};}
 app.get('/share/:token/data',async req=>{const {trip:t}=sharedTrip(req.params.token);return {state:t.state,destination:t.destination.label,location:t.last_location,geometry:t.route.geometry,mode:t.mode,check_in:t.check_in,server_now_ms:now()};});
 app.post('/share/:token/companion',async req=>store.transaction(()=>{
  const {trip:t}=sharedTrip(req.params.token);
  if(t.check_in?.status!=='PENDING'||req.body?.event_id!==t.check_in.id||t.check_in.deadline_ms<=now())throw new ApiError('STALE_CHECK_IN','This watch is no longer pending.',409);
  // Link possession permits acknowledgement only; it cannot resolve SAFE or extend the deadline.
  if(!t.check_in.companion_seen_at_ms){t.check_in.companion_seen_at_ms=now();saveTrip(t);}
  return {status:'WATCHING',at_ms:t.check_in.companion_seen_at_ms};
 }));
 app.delete('/v1/trips/:id/share',{preHandler:auth},async req=>{owned('trip',req.params.id,req.owner);for(const link of store.list('share',req.owner))if(link.trip_id===req.params.id)store.remove('share',link.id);return {status:'REVOKED'};});
 app.get('/share/:token',async(req,reply)=>reply.type('text/html').send(readFileSync(new URL('./share.html',import.meta.url),'utf8')));
 async function tick(){
  for(const t of store.list('trip'))if(t.state==='CHECK_IN_PENDING'&&t.check_in?.status==='PENDING'&&t.check_in.deadline_ms<=now())store.transaction(()=>createSos(t.owner,{trip_id:t.id,trigger:'TIMEOUT'}));
  for(const a of store.list('attempt')){
   if(a.mode==='LIVE'&&a.status==='DISPATCHING'&&now()-a.updated_at_ms>60000){updateAttempt(a,'REQUEST_UNKNOWN');continue;}
   if(a.mode==='REHEARSAL'&&!finalDelivery.has(a.status)&&a.status!=='UNAVAILABLE'){
    const elapsed=now()-a.created_at_ms;if(elapsed>6000)updateAttempt(a,a.contact_index===0?'NO_ANSWER':'ACKNOWLEDGED');else if(elapsed>2000&&a.status==='QUEUED')updateAttempt(a,'RINGING');
   }else if(a.mode==='LIVE'&&a.status==='QUEUED'){
    a.status='DISPATCHING';a.updated_at_ms=now();store.put('attempt',a);
    try{const r=await (options.dispatch||fetch)(config.N8N_WEBHOOK_URL,{method:'POST',headers:{'Content-Type':'application/json','X-Worker-Token':config.WORKER_TOKEN},body:JSON.stringify({attempt_id:a.id,api_base_url:config.PUBLIC_BASE_URL}),signal:AbortSignal.timeout(15000)});if(!r.ok)updateAttempt(store.get('attempt',a.id),'FAILED');}
    catch{const latest=store.get('attempt',a.id);if(latest.status==='DISPATCHING'){latest.status='REQUEST_UNKNOWN';store.put('attempt',latest);const s=store.get('sos',a.incident_id);addTimeline(s,'REQUEST_UNKNOWN','Call request outcome is unknown. No duplicate call will be placed; use the device call or text options.');store.put('sos',s);}}
   }
  }
  for(const sms of store.list('sms_attempt')){
   if(sms.status==='DISPATCHING'&&now()-sms.updated_at_ms>60000){updateSms(sms,'REQUEST_UNKNOWN');continue;}
   if(sms.status!=='QUEUED')continue;
   const incident=store.get('sos',sms.incident_id);if(!incident||incident.cancelled){sms.status='CANCELLED';store.put('sms_attempt',sms);continue;}
   if(!smsConfigured(sms.contact)){updateSms(sms,'UNAVAILABLE');continue;}
   sms.status='DISPATCHING';sms.updated_at_ms=now();store.put('sms_attempt',sms);
   try{const response=await (options.dispatch||fetch)(config.N8N_WEBHOOK_URL,{method:'POST',headers:{'Content-Type':'application/json','X-Worker-Token':config.WORKER_TOKEN},body:JSON.stringify({channel:'SMS',attempt_id:sms.id,api_base_url:config.PUBLIC_BASE_URL}),signal:AbortSignal.timeout(15000)});if(!response.ok)updateSms(store.get('sms_attempt',sms.id),'FAILED');}
   catch{const latest=store.get('sms_attempt',sms.id);if(latest.status==='DISPATCHING')updateSms(latest,'REQUEST_UNKNOWN');}
  }
 }
 let ticking=false;const interval=options.disableWorker?null:setInterval(async()=>{if(ticking)return;ticking=true;try{await tick();}catch{}finally{ticking=false;}},1000);
 app.addHook('onClose',async()=>{if(interval)clearInterval(interval);if(!options.store)store.close();});
 app.decorate('store',store);app.decorate('tick',tick);
 return app;
}
