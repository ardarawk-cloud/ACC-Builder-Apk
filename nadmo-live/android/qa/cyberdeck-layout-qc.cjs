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
  console.log('PASS real Chromium cyberdeck layout '+JSON.stringify({desktop,mobile}));
 }finally{await browser.close()}
})().catch(e=>{console.error('CYBERDECK_LAYOUT_QC_FAIL',e);process.exit(1)});
