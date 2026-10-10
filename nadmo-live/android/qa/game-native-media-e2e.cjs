'use strict';
/**
 * Real public Worker signaling E2E: simulates Android's native WebRTC protocol
 * with Chromium video+audio tracks, and confirms a second browser receives
 * an actual decoded frame plus audio track.
 *
 * This is NOT a substitute for testing Android MediaProjection on a device.
 */
const assert=require('node:assert/strict');
const puppeteer=require('puppeteer-core');
const delay=ms=>new Promise(r=>setTimeout(r,ms));
const url='https://nadmo-live-beta-20261009.ardarawk.workers.dev/app/';
(async function main(){
 const browser=await puppeteer.launch({
   executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',
   headless:true,
   args:['--no-sandbox','--disable-dev-shm-usage','--use-fake-device-for-media-stream',
    '--use-fake-ui-for-media-stream','--autoplay-policy=no-user-gesture-required']
 });
 try{
  const hc=await browser.createBrowserContext(),vc=await browser.createBrowserContext();
  const h=await hc.newPage(),v=await vc.newPage();
  await Promise.all([h.goto(url,{waitUntil:'domcontentloaded',timeout:30000}),v.goto(url,{waitUntil:'domcontentloaded',timeout:30000})]);
  const room=await h.evaluate(async()=>{
   // Set up the media track first; installing onopen after an async getUserMedia
   // can miss the already-open socket and leave the test waiting forever.
   const local=await navigator.mediaDevices.getUserMedia({video:true,audio:true});
   const ws=new WebSocket(location.origin.replace(/^http/,'ws')+'/ws');
   window.__host={ws,local,connections:new Map(),logs:[]};
   const send=m=>ws.send(JSON.stringify(m));
   function sendSignal(to,data){send({type:'signal',to,data})}
   async function offer(to){
    const existing=window.__host.connections.get(to);if(existing)existing.close();
    const pc=new RTCPeerConnection({iceServers:[{urls:'stun:stun.l.google.com:19302'}]});
    window.__host.connections.set(to,pc);
    for(const track of local.getTracks())pc.addTrack(track,local);
    pc.onicecandidate=e=>{if(e.candidate)sendSignal(to,{candidate:e.candidate.toJSON()})};
    const description=await pc.createOffer();
    await pc.setLocalDescription(description);
    sendSignal(to,{description:pc.localDescription,reset:true});
   }
   return new Promise((resolve,reject)=>{
    const timer=setTimeout(()=>reject(new Error('Native-protocol host room timeout '+window.__host.logs.join(','))),25000);
    ws.onopen=()=>send({type:'create',title:'GAME NATIVE MEDIA QA',category:'Gaming',mode:'public',hostName:'NADMO QA'});
    ws.onmessage=async event=>{
      const m=JSON.parse(event.data);
      window.__host.logs.push(m.type);
      if(m.type==='created'){window.__host.room=m.id;window.__host.token=m.resumeToken;clearTimeout(timer);resolve(m.id)}
      else if(m.type==='viewer-joined'){await offer(m.id)}
      else if(m.type==='signal'){
        const pc=window.__host.connections.get(m.from);
        if(!pc)return;
        if(m.data?.description?.type==='answer')await pc.setRemoteDescription(m.data.description);
        if(m.data?.candidate)try{await pc.addIceCandidate(m.data.candidate)}catch(error){console.warn('HOST ICE',error.message)}
      }else if(m.type==='error'){clearTimeout(timer);reject(new Error('SERVER '+m.message))}
    };
    ws.onerror=()=>{clearTimeout(timer);reject(new Error('Host signaling WebSocket failed'))};
   });
  });
  assert.match(room,/^[a-f0-9]{12}$/);
  console.log('PASS native-shaped host room created '+room);
  await v.evaluate(async id=>{
    const ws=new WebSocket(location.origin.replace(/^http/,'ws')+'/ws');
    const display=document.createElement('video');display.autoplay=true;display.muted=true;
    document.body.appendChild(display);
    window.__viewer={ws,video:display,pc:null,logs:[],iceQueue:[]};
    const send=m=>ws.send(JSON.stringify(m));
    function sendSignal(to,data){send({type:'signal',to,data})}
    ws.onopen=()=>send({type:'join',id});
    ws.onmessage=async event=>{
     const m=JSON.parse(event.data);window.__viewer.logs.push(m.type);
     if(m.type==='joined')window.__viewer.hostId=m.hostId;
     if(m.type==='signal'){
      if(m.data?.description?.type==='offer'){
       if(window.__viewer.pc)window.__viewer.pc.close();
       const pc=new RTCPeerConnection({iceServers:[{urls:'stun:stun.l.google.com:19302'}]});
       window.__viewer.pc=pc;
       pc.ontrack=e=>{
        if(e.track.kind==='video'){
         window.__viewer.video.srcObject=e.streams[0]||new MediaStream([e.track]);
         window.__viewer.video.play().catch(()=>{});
        }
       };
       pc.onicecandidate=e=>{if(e.candidate)sendSignal(m.from,{candidate:e.candidate.toJSON()})};
       await pc.setRemoteDescription(m.data.description);
       await pc.setLocalDescription(await pc.createAnswer());
       sendSignal(m.from,{description:pc.localDescription});
       for(const candidate of window.__viewer.iceQueue)await pc.addIceCandidate(candidate);
       window.__viewer.iceQueue=[];
      }
      if(m.data?.candidate){
       if(window.__viewer.pc?.remoteDescription)await window.__viewer.pc.addIceCandidate(m.data.candidate);
       else window.__viewer.iceQueue.push(m.data.candidate);
      }
     }
    };
   },room);
  try{
   await v.waitForFunction(()=>{
     const v=window.__viewer;
     return v&&v.video.videoWidth>0&&v.video.readyState>=2&&
       v.pc?.getReceivers().some(r=>r.track?.kind==='audio'&&r.track?.readyState==='live');
   },{timeout:35000,polling:250});
  }catch(error){
   const diagnostics=await Promise.all([
     h.evaluate(()=>({logs:window.__host?.logs,connections:[...(window.__host?.connections||[])].map(([id,p])=>({id,ice:p.iceConnectionState,conn:p.connectionState,local:p.localDescription?.type,remote:p.remoteDescription?.type}))})),
     v.evaluate(()=>({logs:window.__viewer?.logs,ice:window.__viewer?.pc?.iceConnectionState,
      conn:window.__viewer?.pc?.connectionState,ready:window.__viewer?.video?.readyState,
      width:window.__viewer?.video?.videoWidth,recv:window.__viewer?.pc?.getReceivers().map(x=>x.track?.kind)}))
   ]);
   console.log('GAME MEDIA DIAGNOSTICS '+JSON.stringify(diagnostics));throw error;
  }
  const result=await v.evaluate(()=>({
   width:window.__viewer.video.videoWidth,height:window.__viewer.video.videoHeight,
   videoReadyState:window.__viewer.video.readyState,
   audio:window.__viewer.pc.getReceivers().some(r=>r.track?.kind==='audio'&&r.track?.readyState==='live'),
   ice:window.__viewer.pc.iceConnectionState
  }));
  assert.ok(result.width>0&&result.height>0&&result.audio);
  console.log('PASS REAL WebRTC screen-like video decoded AND audio receiver '+JSON.stringify(result));
  await h.evaluate(()=>{window.__host.ws.send(JSON.stringify({type:'leave'}));for(const p of window.__host.connections.values())p.close();window.__host.local.getTracks().forEach(t=>t.stop())});
  await delay(200);
 }finally{
  try{
   await h.evaluate(()=>{const x=window.__host;if(x){if(x.ws.readyState===WebSocket.OPEN)x.ws.send(JSON.stringify({type:'leave'}));for(const p of x.connections.values())p.close();x.local.getTracks().forEach(t=>t.stop())}});
  }catch(e){}
  try{await v.evaluate(()=>{const x=window.__viewer;if(x?.ws?.readyState===WebSocket.OPEN)x.ws.send(JSON.stringify({type:'leave'}));x?.pc?.close()})}catch(e){}
  await browser.close()
 }
})().catch(e=>{console.error('GAME_NATIVE_MEDIA_E2E_FAILED',e);process.exit(1)});
