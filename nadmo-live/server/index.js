'use strict';
/**
 * NADMO LIVE v0.3 BETA SIGNALING — demo only.
 * Ephemeral single-process rooms; no payments and no user identity verification.
 * WebRTC P2P mesh maximum 4 viewers per host. Not a production SFU.
 */
const http=require('http');
const crypto=require('crypto');
const fs=require('fs');
const path=require('path');
const {WebSocketServer,WebSocket}=require('ws');

const PORT=Number(process.env.PORT)||3000;
const MAX_VIEWERS=4;
const rooms=new Map();
const participants=new Map();
const htmlFile=path.resolve(__dirname,'../android/app/src/main/assets/index.html');
const hash=value=>crypto.createHash('sha256').update(value).digest();
const randomId=()=>crypto.randomBytes(6).toString('hex');
const text=(s,n)=>typeof s==='string'?s.trim().slice(0,n):'';

function allowedOrigin(origin,host) {
 if(!origin)return false;
 try {
  const u=new URL(origin);
  return u.protocol==='https:' && (u.host==='appassets.androidplatform.net'||u.host===host);
 }catch(e){return false}
}
function send(ws,obj){if(ws.readyState===WebSocket.OPEN)ws.send(JSON.stringify(obj))}
function fail(ws,message){send(ws,{type:'error',message})}
function roomSummary(room){return {id:room.id,title:room.title,category:room.category,viewers:room.viewers.size}}
function stop(ws){
 const id=ws.roomId;
 if(!id)return;
 const room=rooms.get(id);
 ws.roomId=null;
 if(!room)return;
 if(room.host===ws){
  for(const viewer of room.viewers){
   send(viewer,{type:'error',message:'Host mengakhiri siaran.'});
   viewer.roomId=null;
   viewer.close(1000,'host ended');
  }
  rooms.delete(id);
 }else{
  room.viewers.delete(ws);
  send(room.host,{type:'viewer-left',id:ws.clientId});
 }
}
function acceptMessage(ws,message){
 const now=Date.now();
 if(now-ws.lastWindow>1000){ws.lastWindow=now;ws.events=0}
 if(++ws.events>35){ws.close(1008,'rate limit');return}
 if(!message||typeof message!=='object'||Array.isArray(message))return fail(ws,'Pesan tidak valid');
 const type=message.type;
 if(type==='leave'){stop(ws);return}
 if(type==='create'){
  if(ws.roomId)return fail(ws,'Keluar room sebelumnya terlebih dahulu.');
  if(rooms.size>=30)return fail(ws,'Jumlah room beta sudah penuh.');
  const title=text(message.title,60)||'NADMO LIVE';
  const category=text(message.category,28)||'Social';
  const mode=message.mode==='password'?'password':'public';
  const password=text(message.password,32);
  if(mode==='password'&&password.length<4)return fail(ws,'Kode akses minimal 4 karakter.');
  const id=randomId(),room={id,title,category,mode,passwordHash:mode==='password'?hash(password):null,host:ws,viewers:new Set()};
  rooms.set(id,room);ws.roomId=id;send(ws,{type:'created',id,selfId:ws.clientId});
  return;
 }
 if(type==='join'){
  if(ws.roomId)return fail(ws,'Keluar room sebelumnya terlebih dahulu.');
  const id=text(message.id,30),room=rooms.get(id);
  if(!room)return fail(ws,'Room tidak ditemukan atau sudah selesai.');
  if(room.viewers.size>=MAX_VIEWERS)return fail(ws,'Room beta penuh (maksimal 4 penonton).');
  if(room.mode==='password'){
   const pass=text(message.password,32),candidate=hash(pass);
   if(!crypto.timingSafeEqual(candidate,room.passwordHash))return fail(ws,'Kode akses salah.');
  }
  room.viewers.add(ws);ws.roomId=id;
  send(ws,{type:'joined',id,title:room.title,selfId:ws.clientId,hostId:room.host.clientId});
  send(room.host,{type:'viewer-joined',id:ws.clientId});
  return;
 }
 const room=rooms.get(ws.roomId);
 if(!room)return fail(ws,'Belum tergabung dalam room.');
 if(type==='chat'){
  const content=text(message.text,250);
  if(!content)return;
  if(now-(ws.lastChat||0)<600)return fail(ws,'Tunggu sebentar sebelum mengirim chat.');
  ws.lastChat=now;
  const payload={type:'chat',name:room.host===ws?'Host':'Viewer',text:content};
  send(room.host,payload);for(const viewer of room.viewers)send(viewer,payload);
  return;
 }
 if(type==='signal'){
  const to=text(message.to,30),peer=participants.get(to);
  if(!peer||peer.roomId!==room.id||peer===ws)return;
  if(room.host!==ws&&room.host!==peer)return;
  if(!message.data||typeof message.data!=='object')return;
  const data=message.data;
  if(data.description && !['offer','answer'].includes(data.description.type))return;
  send(peer,{type:'signal',from:ws.clientId,data});
  return;
 }
 fail(ws,'Perintah tidak tersedia.');
}
const httpServer=http.createServer((req,res)=>{
 const rawOrigin=req.headers.origin||'',host=req.headers.host||'';
 if(allowedOrigin(rawOrigin,host)){
  res.setHeader('Access-Control-Allow-Origin',rawOrigin);
  res.setHeader('Vary','Origin');
 }
 res.setHeader('X-Content-Type-Options','nosniff');
 res.setHeader('Cache-Control','no-store');
 res.setHeader('Referrer-Policy','no-referrer');
 if(req.method==='OPTIONS'){
  if(!allowedOrigin(rawOrigin,host)){res.writeHead(403);res.end();return}
  res.setHeader('Access-Control-Allow-Methods','GET,OPTIONS');
  res.writeHead(204);res.end();return;
 }
 if(req.method!=='GET'){res.writeHead(405);res.end();return}
 const url=new URL(req.url,'http://localhost');
 if(url.pathname==='/health'){
  res.setHeader('Content-Type','application/json');
  res.end(JSON.stringify({ok:true,service:'nadmo-live-beta',activeRooms:rooms.size,maxViewers:MAX_VIEWERS}));
  return;
 }
 if(url.pathname==='/api/rooms'){
  res.setHeader('Content-Type','application/json');
  res.end(JSON.stringify({rooms:[...rooms.values()].filter(r=>r.mode==='public').map(roomSummary)}));
  return;
 }
 if(url.pathname==='/'||url.pathname==='/index.html'){
  res.setHeader('Content-Type','text/html; charset=utf-8');
  res.setHeader('Content-Security-Policy',"default-src 'self'; connect-src 'self' wss: https:; img-src 'self' data:; media-src 'self' blob:; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'");
  fs.createReadStream(htmlFile).pipe(res);
  return;
 }
 res.writeHead(404);res.end('Not found');
});
const wss=new WebSocketServer({server:httpServer,path:'/ws',maxPayload:10*1024,verifyClient:(info,done)=>{
 done(allowedOrigin(info.origin,info.req.headers.host||''),allowedOrigin(info.origin,info.req.headers.host||'')?200:403,'Forbidden');
}});
wss.on('connection',ws=>{
 ws.clientId=randomId();ws.roomId=null;ws.events=0;ws.lastWindow=Date.now();ws.lastChat=0;participants.set(ws.clientId,ws);
 ws.on('message',raw=>{let payload;try{payload=JSON.parse(String(raw))}catch(e){return fail(ws,'JSON tidak valid')};try{acceptMessage(ws,payload)}catch(e){console.error('message error',e);fail(ws,'Kesalahan server')}});
 ws.on('close',()=>{stop(ws);participants.delete(ws.clientId)});
 ws.on('error',()=>{});
});
httpServer.listen(PORT,'0.0.0.0',()=>console.log('NADMO LIVE beta on port '+PORT));
