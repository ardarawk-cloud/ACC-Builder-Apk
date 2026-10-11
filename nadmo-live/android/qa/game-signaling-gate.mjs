import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {request} from 'node:https';
import {randomBytes} from 'node:crypto';

// Validate actual public Worker handshake. No rooms are created and no
// account data is sent. This catches Android OkHttp's missing Origin problem.
const source=readFileSync(new URL('../app/src/main/java/id/nadmo/live/GameCaptureService.java',import.meta.url),'utf8');
const server='nadmo-live-beta-20261009.ardarawk.workers.dev';
const origin='https://'+server;
assert.ok(source.includes('new Request.Builder().url(WS_URL).header("Origin",WS_ORIGIN)'),'Native Android must send accepted Origin');
assert.ok(source.includes('private static final String WS_ORIGIN="'+origin+'"'),'Native Origin must match Worker host');
assert.ok(source.includes('private static final String WS_URL="wss://'+server+'/ws"'),'Native WebSocket endpoint mismatch');

async function handshake(useOrigin){
 const headers={
  Connection:'Upgrade',Upgrade:'websocket',
  'Sec-WebSocket-Key':randomBytes(16).toString('base64'),
  'Sec-WebSocket-Version':'13'
 };
 if(useOrigin)headers.Origin=origin;
 return new Promise((resolve,reject)=>{
  let settled=false;
  const settle=(fn,val)=>{if(settled)return;settled=true;clearTimeout(kill);fn(val)};
  const req=request('https://'+server+'/ws',{method:'GET',headers},res=>{
   const code=res.statusCode;res.resume();settle(resolve,code);
  });
  req.on('upgrade',res=>{settle(resolve,res.statusCode);req.destroy()});
  req.on('error',err=>settle(reject,err));
  const kill=setTimeout(()=>{req.destroy();settle(reject,new Error('WebSocket handshake timeout'))},15000);
  req.end();
 });
}
const denied=await handshake(false);
assert.equal(denied,403,'Backend must reject missing Origin; this reproduces prior Android bug');
const accepted=await handshake(true);
assert.equal(accepted,101,'Backend must accept Android with matching Origin');
console.log('PASS REAL Worker WS: no Origin => 403, Android Origin => 101');
