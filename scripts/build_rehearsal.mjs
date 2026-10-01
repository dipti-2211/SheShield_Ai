import {readFileSync,writeFileSync,mkdirSync} from 'node:fs';
import {createRequire} from 'node:module';
const {scoreRoute}=createRequire(import.meta.url)('../n8n/risk_engine.js');
const s=JSON.parse(readFileSync(new URL('../demo/kolkata_scenario.json',import.meta.url)));
const routes=s.routes.map(r=>{const risk=scoreRoute(s.incidents,r.geometry,.5,{now:Date.parse(s.evaluated_at)});return {...r,risk_level:risk.level,risk_score:risk.score,incident_count:risk.incident_count,segments:risk.segments,evidence:risk.supporting_evidence,coverage:'AVAILABLE',evaluated_at:s.evaluated_at,route_revision:1,is_demo_data:true,risk_summary:`${risk.incident_count} fictional incidents influence this route. These records are only for rehearsal.`};});
const fastest=Math.min(...routes.map(r=>r.duration_seconds));routes.sort((a,b)=>a.risk_score-b.risk_score||a.duration_seconds-b.duration_seconds);routes.forEach((r,i)=>{r.label=i===0?'Lower reported exposure':r.duration_seconds===fastest?'Fastest walking route':'Alternative walking route';r.extra_minutes=Math.round((r.duration_seconds-fastest)/60);});
const plan={id:'kolkata-rehearsal-v2',mode:'REHEARSAL',origin:s.origin,destination:s.destination,routes,attribution:s.attribution,geometry_source:s.geometry_source,expires_at_ms:0};
const dir=new URL('../android/app/src/main/assets/',import.meta.url);mkdirSync(dir,{recursive:true});writeFileSync(new URL('rehearsal_plan.json',dir),JSON.stringify(plan));
console.log('Bundled rehearsal uses the production scoring engine.');
