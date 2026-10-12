import assert from 'node:assert/strict';
import WebSocket from 'ws';
const url='wss://live.nadmo.id/ws';
const ws=new WebSocket(url,{headers:{Origin:'https://live.nadmo.id'},handshakeTimeout:15000});
let roomId='',closed=false;
const deadline=setTimeout(()=>{console.error('FAIL: Native-compatible WS room create timed out');process.exit(1)},20000);
ws.on('open',()=>{console.log('WS handshake 101');ws.send(JSON.stringify({type:'create',title:'NADMO QA CONNECT TEST',category:'Gaming',broadcastKind:'gaming',mode:'public',hostName:'Host'}))});
ws.on('message',raw=>{
 const m=JSON.parse(String(raw));
 if(m.type==='created'){
  assert.equal(m.broadcastKind,'gaming');
  assert.match(m.id,/^[a-f0-9]{12}$/);
  roomId=m.id;
  console.log('NATIVE-LIKE GAME HOST ROOM ACK PASS');
  ws.send(JSON.stringify({type:'leave'}));
 }else if(m.type==='left'){closed=true;ws.close();clearTimeout(deadline);console.log('ROOM CLEANUP PASS')}
 else if(m.type==='error'){clearTimeout(deadline);console.error('WORKER DENIED HOST:',m.message);process.exit(1)}
});
ws.on('error',err=>{clearTimeout(deadline);console.error('Native-compatible WS error',err.message);process.exit(1)});
ws.on('close',()=>{clearTimeout(deadline);if(!roomId||!closed)process.exitCode=1;});
