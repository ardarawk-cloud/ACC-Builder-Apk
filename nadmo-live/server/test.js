'use strict';
const assert=require('node:assert/strict');
const {spawn}=require('node:child_process');
const {WebSocket}=require('ws');

const base='http://127.0.0.1:31977';
const origin='https://appassets.androidplatform.net';
const delay=ms=>new Promise(r=>setTimeout(r,ms));
function connect(){
 return new Promise((resolve,reject)=>{
  const ws=new WebSocket('ws://127.0.0.1:31977/ws',{headers:{Origin:origin}});
  ws.once('open',()=>resolve(ws));ws.once('error',reject);
 });
}
function next(ws,expected){
 return new Promise((resolve,reject)=>{
  const t=setTimeout(()=>{ws.off('message',onMessage);reject(Error('Timed out '+expected))},3000);
  function onMessage(b){
   const m=JSON.parse(String(b));
   if(m.type!==expected)return;
   clearTimeout(t);ws.off('message',onMessage);resolve(m);
  }
  ws.on('message',onMessage);
 });
}
async function main(){
 const child=spawn(process.execPath,['index.js'],{cwd:__dirname,env:{...process.env,PORT:'31977'},stdio:'pipe'});
 const sockets=[];
 try{
  let ready=false;
  for(let i=0;i<50;i++){try{const x=await fetch(base+'/health');if(x.ok){ready=true;break}}catch(e){}await delay(80)}
  assert.ok(ready,'service health');
  const health=await (await fetch(base+'/health')).json();assert.equal(health.ok,true);
  assert.deepEqual((await (await fetch(base+'/api/rooms')).json()).rooms,[]);
  const host=await connect();sockets.push(host);
  const created=next(host,'created');
  host.send(JSON.stringify({type:'create',title:'NADMO Test',category:'Podcast',mode:'public'}));
  const room=await created;assert.ok(room.id);
  const listing=await (await fetch(base+'/api/rooms')).json();assert.equal(listing.rooms.length,1);
  const viewer=await connect();sockets.push(viewer);
  const joined=next(viewer,'joined');const event=next(host,'viewer-joined');
  viewer.send(JSON.stringify({type:'join',id:room.id}));
  assert.equal((await joined).id,room.id);assert.ok((await event).id);
  const chat=next(host,'chat');viewer.send(JSON.stringify({type:'chat',text:'Tes chat'}));assert.equal((await chat).text,'Tes chat');
  viewer.close();host.close();await delay(100);
  const privateHost=await connect();sockets.push(privateHost);
  const priPromise=next(privateHost,'created');
  privateHost.send(JSON.stringify({type:'create',title:'Private Demo',mode:'password',password:'aman123'}));
  const pri=await priPromise;
  const after=await (await fetch(base+'/api/rooms')).json();assert.equal(after.rooms.length,0);
  const guest=await connect();sockets.push(guest);
  const bad=next(guest,'error');guest.send(JSON.stringify({type:'join',id:pri.id,password:'salah'}));
  assert.match((await bad).message,/salah/);
  const good=next(guest,'joined');guest.send(JSON.stringify({type:'join',id:pri.id,password:'aman123'}));assert.equal((await good).id,pri.id);
  console.log('PASS: health / public listing / create / join / chat / private access');
 }finally{for(const s of sockets)s.close();child.kill('SIGTERM')}
}
main().catch(e=>{console.error(e);process.exitCode=1});
