import test from 'node:test';
import assert from 'node:assert/strict';
import {fetchJson,routeLive} from '../src/providers.js';

const policy={retryTimeouts:true,retryTransientStatuses:true};
test('a routing timeout recovers on one retry with a fresh abort signal',async t=>{
  const signals=[];
  t.mock.method(globalThis,'fetch',async(url,options)=>{
    signals.push(options.signal);
    if(signals.length===1)throw new DOMException('Timed out','TimeoutError');
    return Response.json({recovered:true});
  });
  assert.deepEqual(await fetchJson('https://example.test',{},15000,policy),{recovered:true});
  assert.equal(signals.length,2);assert.notEqual(signals[0],signals[1]);
});
test('routing also retries a timeout while reading the response body',async t=>{
  let calls=0;
  t.mock.method(globalThis,'fetch',async()=>++calls===1?{ok:true,json:async()=>{throw new DOMException('Timed out','TimeoutError');}}:Response.json({ok:true}));
  assert.deepEqual(await fetchJson('https://example.test',{},15000,policy),{ok:true});assert.equal(calls,2);
});
test('persistent routing timeouts stop after two attempts with an actionable error',async t=>{
  let calls=0;t.mock.method(globalThis,'fetch',async()=>{calls++;throw new DOMException('Timed out','TimeoutError');});
  await assert.rejects(fetchJson('https://example.test',{},15000,policy),e=>e.code==='PROVIDER_UNAVAILABLE'&&e.message.includes('after retrying'));
  assert.equal(calls,2);
});
test('a temporary gateway failure recovers but rate limits and credential errors are not retried',async t=>{
  for(const status of [502,503,504,429,401,403,400]){
    let calls=0;const mock=t.mock.method(globalThis,'fetch',async()=>++calls===1?new Response('',{status}):Response.json({ok:true}));
    if([502,503,504].includes(status)){assert.deepEqual(await fetchJson('https://example.test',{},15000,policy),{ok:true});assert.equal(calls,2);}
    else{await assert.rejects(fetchJson('https://example.test',{},15000,policy));assert.equal(calls,1);}
    mock.mock.restore();
  }
});
test('ordinary lookups keep their existing timeout policy and malformed JSON is not retried',async t=>{
  let calls=0;const mock=t.mock.method(globalThis,'fetch',async()=>{calls++;throw new DOMException('Timed out','TimeoutError');});
  await assert.rejects(fetchJson('https://example.test'),e=>e.code==='PROVIDER_UNAVAILABLE');assert.equal(calls,1);mock.mock.restore();
  calls=0;t.mock.method(globalThis,'fetch',async()=>{calls++;return new Response('invalid json');});
  await assert.rejects(fetchJson('https://example.test',{},15000,policy),e=>e.code==='INVALID_PROVIDER_RESPONSE');assert.equal(calls,1);
});
test('walking routes request the bounded recovery policy',async()=>{
  let captured;
  await routeLive([88.35,22.56],[88.35,22.57],'test-key',{},async(url,options,timeout,recovery)=>{
    captured={timeout,recovery};
    return {features:[{geometry:{type:'LineString',coordinates:[[88.35,22.56],[88.35,22.57]]},properties:{summary:{distance:1200,duration:800}}}]};
  });
  assert.equal(captured.timeout,15000);assert.deepEqual(captured.recovery,policy);
});
