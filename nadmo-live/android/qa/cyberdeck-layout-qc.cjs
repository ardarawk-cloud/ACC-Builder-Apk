'use strict';
// Uses real Chromium layout: catches regressions where a landscape GAME is
// letterboxed inside the old forced-portrait PC viewer.
const assert=require('node:assert/strict');
const puppeteer=require('puppeteer-core');
const path=require('node:path');
(async()=>{
 const browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/chromium',
  headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 try{
  const page=await browser.newPage();
  const local='file://'+path.resolve(__dirname,'../app/src/main/assets/game-live-qa.html');
  await page.setViewport({width:1366,height:768,deviceScaleFactor:1});
  await page.goto(local,{waitUntil:'domcontentloaded',timeout:20000});
  const desktop=await page.evaluate(()=>{
   document.body.classList.add('live-immersive','watch-viewer','watch-game','watch-landscape');
   document.querySelector('#watch').classList.remove('hide');
   const stage=document.querySelector('#watch .live-stage').getBoundingClientRect();
   const player=document.querySelector('#watch .live-stage>.viewfinder').getBoundingClientRect();
   const chat=document.querySelector('#watch .live-chat').getBoundingClientRect();
   return {stageWidth:stage.width,videoWidth:player.width,videoHeight:player.height,
    chatWidth:chat.width,videoFit:getComputedStyle(document.querySelector('#remote')).objectFit};
  });
  assert.ok(desktop.stageWidth>550,JSON.stringify(desktop));
  assert.ok(desktop.videoWidth>desktop.stageWidth*.83,
   'Landscape GAME must fill the PC video theater instead of narrow 9:16 letterboxing: '+JSON.stringify(desktop));
  assert.ok(desktop.chatWidth>=290,'PC chat must remain visible');
  assert.equal(desktop.videoFit,'contain','Crop-free display of Mobile Legends');
  await page.screenshot({path:'/tmp/nadmo-cyberdeck-desktop-qc.png'});
  await page.setViewport({width:390,height:844,deviceScaleFactor:1,isMobile:true});
  const mobile=await page.evaluate(()=>{
   document.body.classList.remove('live-immersive','watch-viewer','watch-game','watch-landscape');
   document.querySelector('#watch').classList.add('hide');
   const bar=document.querySelector('.deck-system-bar').getBoundingClientRect();
   const app=document.querySelector('.app').getBoundingClientRect();
   const brand=getComputedStyle(document.querySelector('.deck-system-bar')).borderLeftWidth;
   const button=getComputedStyle(document.querySelector('#createRoom'));
   return {barWidth:bar.width,appWidth:app.width,brand,borderLeftStyle:getComputedStyle(document.querySelector('.deck-system-bar')).borderLeftStyle,borderLeftColor:getComputedStyle(document.querySelector('.deck-system-bar')).borderLeftColor,display:getComputedStyle(document.querySelector('.deck-system-bar')).display,accent:getComputedStyle(document.documentElement).getPropertyValue('--neon'),htmlTheme:document.documentElement.dataset.nadmoUiTheme,buttonHeight:button.minHeight,
    topText:document.querySelector('.deck-system-bar').textContent,
    hasVoiceOnlyMessage:document.querySelector('#gameDeckAudio').textContent};
  });
  assert.ok(mobile.barWidth>250&&mobile.barWidth<=390,JSON.stringify(mobile));
  console.log('CYBERDECK_MOBILE_DIAG '+JSON.stringify(mobile));
  assert.equal(mobile.brand,'4px');
  assert.equal(mobile.buttonHeight,'57px');
  assert.match(mobile.topText,/CYBERDECK/);
  assert.match(mobile.hasVoiceOnlyMessage,/BELUM TERSEDIA/);
  await page.screenshot({path:'/tmp/nadmo-cyberdeck-mobile-qc.png'});
  // Device build must include the ME cover patch; testing only the deployed
  // Worker would not protect the bundled Android HTML in MainActivity.
  const cover=await page.evaluate(async()=>{
   const settings=document.querySelector('#settings');
   settings.classList.remove('hide');
   const me=document.querySelector('#betaSocialProfile');
   me.dataset.coverPhoto='true';
   me.dataset.theme='cyberbw';
   const hero=me.querySelector('.profile-hero');
   const photo=hero.querySelector('img.profile-cover-photo');
   const preview=document.querySelector('#coverPreviewImg');
   if(!photo||!preview)return {missing:true};
   const data='data:image/svg+xml,'+encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="675"><rect width="1200" height="675" fill="#240c3b"/><rect x="0" width="300" height="675" fill="#f25dc5"/><circle cx="650" cy="330" r="120" fill="#e4fd72"/></svg>');
   photo.hidden=false;photo.src=data;preview.hidden=false;preview.src=data;
   await Promise.all([photo.decode(),preview.decode()]);
   const rect=hero.getBoundingClientRect(),img=photo.getBoundingClientRect();
   return {missing:false,heroW:rect.width,heroH:rect.height,imgW:img.width,imgH:img.height,
    fit:getComputedStyle(photo).objectFit,position:getComputedStyle(photo).position,
    bg:getComputedStyle(hero).backgroundImage,filter:getComputedStyle(hero).filter,
    matching:photo.src===preview.src};
  });
  assert.ok(!cover.missing,'ME must have actual image cover element and editor preview');
  assert.ok(cover.heroW>280&&Math.abs(cover.heroW/cover.heroH-16/9)<.1,'ME cover must be 16:9 '+JSON.stringify(cover));
  assert.ok(Math.abs(cover.imgW-cover.heroW)<3&&Math.abs(cover.imgH-cover.heroH)<3,'ME cover is zoomed '+JSON.stringify(cover));
  assert.equal(cover.fit,'contain','ME must not crop the actual cover');
  assert.equal(cover.position,'absolute');
  assert.equal(cover.bg,'none','The old CSS background can zoom the cover');
  assert.equal(cover.filter,'none','BW theme must preserve photo colors');
  assert.ok(cover.matching,'ME and editor preview must display the same source image');
  await page.screenshot({path:'/tmp/nadmo-me-cover-mobile-uncropped-qc.png'});
  console.log('BUNDLED_ME_COVER_16_9_QC_PASS '+JSON.stringify(cover));

  // Camera-specific regression: Android wide sensor must not become a giant
  // blank black portrait page. Portrait source must be full bleed. The game
  // landscape visual contract is intentionally unchanged.
  const testCameraLayout=async(kind)=>{
   return page.evaluate((kind)=>{
    const body=document.body,watch=document.querySelector('#watch'),video=document.querySelector('#remote');
    watch.classList.remove('hide');
    body.classList.add('live-immersive');
    body.classList.toggle('watch-host',kind.includes('host'));
    body.classList.toggle('watch-viewer',kind.includes('viewer')||kind.includes('game'));
    body.classList.toggle('watch-game',kind.includes('game'));
    body.classList.toggle('watch-camera-wide',kind.includes('wide'));
    body.classList.toggle('watch-landscape',kind.includes('wide')||kind.includes('game'));
    const bg=document.querySelector('#cameraBackdrop'),stage=document.querySelector('#watch .live-stage');
    const r=video.getBoundingClientRect(),rs=stage.getBoundingClientRect();
    return {kind,fit:getComputedStyle(video).objectFit,bgDisplay:bg?getComputedStyle(bg).display:'none',
      top:r.top,width:r.width,height:r.height,stageWidth:rs.width,stageHeight:rs.height,
      screenWidth:innerWidth,screenHeight:innerHeight};
   },kind);
  };
  for(const mode of ['host-portrait','viewer-portrait']){
   const layout=await testCameraLayout(mode);
   assert.equal(layout.fit,'cover','Portrait LIVE camera must cover the phone: '+JSON.stringify(layout));
   assert.equal(layout.bgDisplay,'none','A portrait camera should not incur duplicate video rendering');
   assert.ok(layout.height>=layout.screenHeight*.90,'Portrait video must fill screen height: '+JSON.stringify(layout));
  }
  // Same portrait framing even when the Android sensor returns horizontal frames:
  // the capture composer, not a CSS letterbox, converts the remote track to 9:16.
  for(const mode of ['host-wide','viewer-wide']){
   const layout=await testCameraLayout(mode);
   assert.equal(layout.fit,'cover','Camera must fill phone portrait in every source orientation: '+JSON.stringify(layout));
   assert.equal(layout.bgDisplay,'none','No blurred backdrop or horizontal camera strip');
   assert.ok(layout.height>=layout.screenHeight*.90,'Wide source cannot shrink the LIVE viewport: '+JSON.stringify(layout));
  }
  const stillGame=await testCameraLayout('viewer-game-wide');
  assert.equal(stillGame.fit,'contain','Game must remain wide and uncropped');
  assert.equal(stillGame.bgDisplay,'none','Game must not receive a camera backdrop');
  await page.evaluate(()=>{document.body.classList.remove('live-immersive','watch-host','watch-viewer','watch-game','watch-camera-wide','watch-landscape');document.querySelector('#watch').classList.add('hide');});
  console.log('CAMERA_MOBILE_FULL_PORTRAIT_QC_PASS');
  // Anti-zoom QA: emulate an Android 640x360 front camera using a real
  // MediaStream and verify that both side markers survive the composed 9:16
  // stream. The old center crop threw away >60% of horizontal sensor pixels.
  const antiZoom=await page.evaluate(async()=>{
   const canvas=document.createElement('canvas');canvas.width=640;canvas.height=360;
   const context=canvas.getContext('2d');
   context.fillStyle='#191919';context.fillRect(0,0,640,360);
   context.fillStyle='#ff1900';context.fillRect(100,0,26,360);
   context.fillStyle='#00eaff';context.fillRect(514,0,26,360);
   const fake=canvas.captureStream(20);
   const md=navigator.mediaDevices;
   if(!md||!md.getUserMedia)throw new Error('Browser MediaDevices unsupported in QC environment');
   const real=md.getUserMedia.bind(md);
   Object.defineProperty(md,'getUserMedia',{configurable:true,value:async()=>fake});
   let result;
   try{
    result=await openLiveCamera(false);
    await new Promise(r=>setTimeout(r,450));
    const frame=result?.controller?.canvas;
    if(!frame)throw new Error('Expected landscape safe-frame compositor');
    const ctx=frame.getContext('2d'),left=Array.from(ctx.getImageData(25,320,1,1).data),
     right=Array.from(ctx.getImageData(330,320,1,1).data);
    return {width:frame.width,height:frame.height,left,right,
      sourceWidth:result.controller.video.videoWidth,sourceHeight:result.controller.video.videoHeight};
   }finally{
    Object.defineProperty(md,'getUserMedia',{configurable:true,value:real});
    if(result?.controller){
     clearInterval(result.controller.timer);
     result.controller.video.pause();
     result.controller.video.srcObject=null;
     result.controller.video.remove();
    }
    result?.stream?.getTracks().forEach(t=>t.stop());
    fake.getTracks().forEach(t=>t.stop());
   }
  });
  assert.equal(antiZoom.width,360);
  assert.equal(antiZoom.height,640);
  assert.equal(antiZoom.sourceWidth,640);
  assert.equal(antiZoom.sourceHeight,360);
  assert.ok(antiZoom.left[0]>150&&antiZoom.left[1]<110,'Left side of original camera lost due to zoom: '+JSON.stringify(antiZoom));
  assert.ok(antiZoom.right[2]>120&&antiZoom.right[0]<110,'Right side of original camera lost due to zoom: '+JSON.stringify(antiZoom));
  console.log('CAMERA_ANTIZOOM_MEDIA_QC_PASS '+JSON.stringify(antiZoom));
  console.log('PASS real Chromium cyberdeck layout '+JSON.stringify({desktop,mobile}));
 }finally{await browser.close()}
})().catch(e=>{console.error('CYBERDECK_LAYOUT_QC_FAIL',e);process.exit(1)});
