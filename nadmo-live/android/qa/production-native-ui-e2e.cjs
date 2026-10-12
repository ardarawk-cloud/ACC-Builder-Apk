'use strict';
// Browser regression for the online document used by the Android shell.
// Native commands are stubbed and camera frames are synthetic. This is not device QC.
const assert=require('node:assert/strict');
const puppeteer=require('puppeteer-core');
(async()=>{
 const browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/chromium',
  headless:true,args:['--no-sandbox','--disable-dev-shm-usage','--autoplay-policy=no-user-gesture-required']});
 try{
  const page=await browser.newPage();
  await page.setViewport({width:390,height:844,isMobile:true,hasTouch:true});
  await page.evaluateOnNewDocument(()=>{
   window.__nativeCommands=[];
   window.NadmoGame={postMessage:value=>window.__nativeCommands.push(JSON.parse(value))};
  });
  const errors=[];
  page.on('pageerror',e=>errors.push(String(e)));
  await page.setCacheEnabled(false);
  const response=await page.goto('https://live.nadmo.id/app/',{waitUntil:'domcontentloaded',timeout:40000});
  assert.equal(response.status(),200);
  await page.waitForSelector('#mobileLiveHud');
  const bridge=await page.evaluate(()=>{
   window.dispatchEvent(new CustomEvent('nadmo-game-status',{detail:{state:'live',room:'synthetic-qa',
    message:'Synthetic native status',chatNotificationsEnabled:true,chatOverlayEnabled:true}}));
   return {revision:document.querySelector('meta[name="nadmo-ui-revision"]')?.content,
    screen:document.querySelector('#gameDeckScreen').textContent,
    launchHidden:document.querySelector('#gameLaunchGame').hidden,
    shareHidden:document.querySelector('#gameShareRoom').hidden,
    stopHidden:document.querySelector('#gameStopLive').hidden};
  });
  assert.ok(bridge.revision);
  assert.match(bridge.screen,/AKTIF/);
  assert.equal(bridge.launchHidden,false);
  assert.equal(bridge.shareHidden,false);
  assert.equal(bridge.stopHidden,false);
  await page.$eval('#gameLaunchGame',el=>el.click());
  assert.ok(await page.evaluate(()=>window.__nativeCommands.some(c=>c.type==='launch')),
   'production UI must still send the native GAME launch command');
  const pausedUi=await page.evaluate(()=>{
   window.dispatchEvent(new CustomEvent('nadmo-game-status',{detail:{state:'paused',room:'synthetic-qa',
    message:'SIARAN DIJEDA',chatNotificationsEnabled:true,chatOverlayEnabled:true}}));
   return {screen:document.querySelector('#gameDeckScreen').textContent,
    mic:document.querySelector('#gameDeckMic').textContent,
    launch:document.querySelector('#gameLaunchGame').hidden,
    stop:document.querySelector('#gameStopLive').hidden,
    share:document.querySelector('#gameShareRoom').hidden};
  });
  assert.match(pausedUi.screen,/JEDA PRIVASI/,'GAME UI must explain privacy pause');
  assert.match(pausedUi.mic,/JEDA/,'mic status cannot claim sound on air while paused');
  assert.equal(pausedUi.launch,false,'BUKA GAME must work while stream is in safe pause');
  assert.equal(pausedUi.stop,false,'AKHIRI LIVE must remain reachable during pause');
  assert.equal(pausedUi.share,false,'existing room should remain shareable during pause');
  const sources=[];
  for(const [width,height] of [[1280,720],[720,1280]]){
   await page.evaluate(({width,height})=>{
    const canvas=document.createElement('canvas');canvas.width=width;canvas.height=height;
    canvas.getContext('2d').fillRect(0,0,width,height);
    window.__rawCamera=canvas.captureStream(10);
    Object.defineProperty(navigator.mediaDevices,'getUserMedia',{configurable:true,value:async constraints=>{
     window.__cameraConstraints=constraints;return window.__rawCamera;
    }});
    document.querySelector('#preview').click();
   },{width,height});
   await page.waitForFunction(()=>document.querySelector('#camera').srcObject===window.__rawCamera,{timeout:10000});
   await page.evaluate(async()=>{
    const watch=document.querySelector('#watch'),video=document.querySelector('#remote');
    watch.classList.remove('hide');watch.dataset.hostTheme='electric';
    document.body.classList.add('live-immersive','watch-viewer');
    document.body.classList.remove('watch-game','watch-clean');
    video.srcObject=window.__rawCamera;video.muted=true;await video.play();
   });
   await page.waitForFunction((width)=>document.querySelector('#remote').videoWidth===width,{timeout:10000},width);
   const snapshot=await page.evaluate(()=>{
    const video=document.querySelector('#remote'),watch=document.querySelector('#watch');
    const settings=window.__rawCamera.getVideoTracks()[0].getSettings();
    return {source:{width:settings.width,height:settings.height},
     decoded:{width:video.videoWidth,height:video.videoHeight},
     fit:getComputedStyle(video).objectFit,filter:getComputedStyle(video).filter,
     landscape:watch.classList.contains('camera-source-landscape'),
     sameStream:document.querySelector('#camera').srcObject===window.__rawCamera,
     constraints:window.__cameraConstraints.video,
     hud:getComputedStyle(document.querySelector('#mobileLiveHud')).display};
   });
   assert.deepEqual(snapshot.source,{width,height});
   assert.deepEqual(snapshot.decoded,{width,height});
   assert.equal(snapshot.sameStream,true,'camera must preserve source track; no low-resolution compositor');
   assert.equal(snapshot.fit,'contain','mobile camera must not reintroduce full-screen face crop');
   assert.equal(snapshot.landscape,width>height);
   assert.equal(snapshot.filter,'none');
   assert.equal(snapshot.hud,'block');
   assert.equal(snapshot.constraints.width.ideal,960);
   assert.equal(snapshot.constraints.height.ideal,1280);
   sources.push(snapshot);
   await page.evaluate(()=>{window.__rawCamera.getTracks().forEach(t=>t.stop());document.querySelector('#remote').srcObject=null});
  }
  for(const [start,end,clean] of [[30,260,true],[270,40,false]]){
   await page.evaluate(({start,end})=>{
    const video=document.querySelector('#remote');
    video.dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,pointerType:'touch',pointerId:9,clientX:start,clientY:450}));
    video.dispatchEvent(new PointerEvent('pointerup',{bubbles:true,pointerType:'touch',pointerId:9,clientX:end,clientY:450}));
   },{start,end});
   assert.deepEqual(await page.evaluate(()=>({clean:document.body.classList.contains('watch-clean'),
    hud:getComputedStyle(document.querySelector('#mobileLiveHud')).display})),{clean,hud:clean?'none':'block'});
  }
  assert.deepEqual(errors,[],'production document must initialize without script errors');
  console.log('PRODUCTION_NATIVE_UI_BROWSER_PASS '+JSON.stringify({bridge,pausedUi,sources,
   limits:'Native bridge stub and synthetic camera; physical Android capture, GAME audio and upgrade still require device QC'}));
 }finally{await browser.close()}
})().catch(e=>{console.error('PRODUCTION_NATIVE_UI_BROWSER_FAIL',e.stack||e);process.exitCode=1});
