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
   return {barWidth:bar.width,appWidth:app.width,brand,buttonHeight:button.minHeight,
    topText:document.querySelector('.deck-system-bar').textContent,
    hasVoiceOnlyMessage:document.querySelector('#gameDeckAudio').textContent};
  });
  assert.ok(mobile.barWidth>250&&mobile.barWidth<=390,JSON.stringify(mobile));
  assert.equal(mobile.brand,'4px');
  assert.equal(mobile.buttonHeight,'57px');
  assert.match(mobile.topText,/CYBERDECK/);
  assert.match(mobile.hasVoiceOnlyMessage,/BELUM TERSEDIA/);
  await page.screenshot({path:'/tmp/nadmo-cyberdeck-mobile-qc.png'});
  console.log('PASS real Chromium cyberdeck layout '+JSON.stringify({desktop,mobile}));
 }finally{await browser.close()}
})().catch(e=>{console.error('CYBERDECK_LAYOUT_QC_FAIL',e);process.exit(1)});
