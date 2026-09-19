(() => {
  'use strict';
  const $=(id)=>document.getElementById(id);
  const backdrop=$('drawerBackdrop');
  const panels=['musicDrawer','drivePopup','mixerPopup','padsPopup','fxPopup'];
  let targetDeck=null;
  let performanceDeck='A';
  let fxDeck='A';
  const cuePoints={A:0,B:0};
  const hotCues={A:[null,null,null,null],B:[null,null,null,null]};
  const beatLoops={A:null,B:null};
  const rolls={A:null,B:null};
  const fxState={A:{type:null,amount:.45},B:{type:null,amount:.45}};

  function closePanels(){
    panels.forEach(id=>{
      const p=$(id); if(!p)return;
      if(id==='musicDrawer'){p.classList.remove('open');p.setAttribute('aria-hidden','true');}
      else p.hidden=true;
    });
    if(backdrop)backdrop.hidden=true;
  }

  function openPanel(id){
    panels.forEach(pid=>{
      if(pid===id)return;
      const p=$(pid);if(!p)return;
      if(pid==='musicDrawer'){p.classList.remove('open');p.setAttribute('aria-hidden','true');}
      else p.hidden=true;
    });
    const p=$(id);if(!p)return;
    if(id==='musicDrawer'){
      p.classList.add('open');p.setAttribute('aria-hidden','false');
      if(!$('results')?.children.length && $('libraryMessage')) {
        $('libraryMessage').textContent = 'Pilih DRIVE / FILES, TRENDING, genre, atau cari lagu.';
        $('libraryMessage').style.color = '';
      }
    }else p.hidden=false;
    if(backdrop)backdrop.hidden=false;
  }

  function setTargetDeck(id){
    targetDeck=['A','B'].includes(id)?id:null;
    const badge=$('deckTargetBadge');
    if(badge){
      badge.textContent=targetDeck?`LOAD TO DECK ${targetDeck}`:'Pilih deck dari cover';
      badge.classList.toggle('targeted',!!targetDeck);
    }
    decorateResults();
  }

  function decorateResults(){
    document.querySelectorAll('#results .result-card').forEach(card=>{
      card.classList.toggle('target-mode',!!targetDeck);
      card.querySelectorAll('[data-load]').forEach(btn=>{
        const hit=!!targetDeck && btn.dataset.load===targetDeck;
        btn.classList.toggle('target-load',hit);
        if(hit)btn.textContent=`LOAD DECK ${targetDeck}`;
        else if(!targetDeck)btn.textContent=`LOAD ${btn.dataset.load}`;
      });
    });
  }

  new MutationObserver(decorateResults).observe($('results'),{childList:true,subtree:true});

  document.querySelectorAll('[data-select-track]').forEach(el=>el.addEventListener('click',()=>{
    setTargetDeck(el.dataset.selectTrack);
    openPanel('musicDrawer');
  }));

  $('driveBtn')?.addEventListener('click',()=>{setTargetDeck(null);openPanel('drivePopup');});
  $('mixerBtn')?.addEventListener('click',()=>openPanel('mixerPopup'));
  $('centerMixerBtn')?.addEventListener('click',()=>openPanel('mixerPopup'));
  $('driveFromLibraryBtn')?.addEventListener('click',()=>openPanel('drivePopup'));
  document.querySelectorAll('[data-open-mixer]').forEach(b=>b.addEventListener('click',()=>openPanel('mixerPopup')));
  document.querySelectorAll('[data-close-modal]').forEach(b=>b.addEventListener('click',closePanels));
  backdrop?.addEventListener('click',closePanels);
  $('openApiBtn')?.addEventListener('click',()=>{closePanels();$('settingsBtn')?.click();});
  document.addEventListener('keydown',e=>{if(e.key==='Escape')closePanels();});

  $('results')?.addEventListener('click',e=>{
    const btn=e.target.closest?.('[data-load]');
    if(btn && targetDeck && btn.dataset.load===targetDeck)setTimeout(()=>setTargetDeck(null),200);
  },true);

  ['A','B'].forEach(id=>{
    const cover=$(`cover${id}`),label=$(`label${id}`),
      play=document.querySelector(`[data-action="play"][data-deck="${id}"]`),
      deck=document.querySelector(`.deck[data-deck="${id}"]`),
      audio=$(`audio${id}`);
    const syncCover=()=>{if(label){label.src=cover?.src||'';label.style.opacity=cover?.src?'1':'.2';}};
    if(cover)new MutationObserver(syncCover).observe(cover,{attributes:true,attributeFilter:['src']});syncCover();

    const syncPlay=()=>{
      const isPlaying=audio ? !audio.paused && !audio.ended : !!play?.classList.contains('playing');
      deck?.classList.toggle('playing',isPlaying);
    };
    if(play)new MutationObserver(syncPlay).observe(play,{attributes:true,attributeFilter:['class']});
    audio?.addEventListener('play',syncPlay);
    audio?.addEventListener('pause',syncPlay);
    audio?.addEventListener('ended',syncPlay);
    syncPlay();
  });

  function cueButton(id){return $(`cuePoint${id}`);}
  function updateCue(id){
    const b=cueButton(id);if(!b)return;
    b.classList.toggle('active',cuePoints[id]>0.02);
    b.textContent=cuePoints[id]>0.02?'CUE ✓':'CUE';
  }
  ['A','B'].forEach(id=>{
    cueButton(id)?.addEventListener('click',()=>{
      const a=$(`audio${id}`);if(!a?.src)return;
      const point=cuePoints[id]||0;
      if(!a.paused){
        a.pause();a.currentTime=Math.min(point,a.duration||Infinity);
        const pb=document.querySelector(`[data-action="play"][data-deck="${id}"]`);
        if(pb){pb.textContent='▶';pb.classList.remove('playing');}
      }else if(Math.abs((a.currentTime||0)-point)>.18){
        cuePoints[id]=a.currentTime||0;updateCue(id);
      }else a.currentTime=point;
    });
    $(`audio${id}`)?.addEventListener('loadedmetadata',()=>{cuePoints[id]=0;hotCues[id]=[null,null,null,null];beatLoops[id]=null;rolls[id]=null;updateCue(id);});
  });

  $('drivePickerBtn')?.addEventListener('click',()=>$('driveInput')?.click());
  const nice=n=>String(n||'Local Track').replace(/\.[^.]+$/,'').replace(/_/g,' ');
  const size=n=>n<1048576?`${Math.max(1,Math.round(n/1024))} KB`:`${(n/1048576).toFixed(1)} MB`;
  function renderFiles(files){
    const list=$('driveList'),status=$('driveStatus');if(!list||!status)return;
    list.innerHTML='';
    if(!files.length){status.textContent='Tidak ada file audio dipilih.';list.innerHTML='<div class="empty-state">Pilih MP3 / M4A / AAC / WAV / FLAC / OGG.</div>';return;}
    status.textContent=`${files.length} lagu siap${targetDeck?` → Deck ${targetDeck}`:''}.`;
    files.forEach(file=>{
      const row=document.createElement('div');row.className='drive-track';
      const meta=document.createElement('div');meta.innerHTML='<b></b><span></span>';
      meta.querySelector('b').textContent=nice(file.name);
      meta.querySelector('span').textContent=`${file.type||'audio'} • ${size(file.size)}`;
      const acts=document.createElement('div');acts.className='drive-load';
      const decks=targetDeck?[targetDeck]:['A','B'];
      decks.forEach(id=>{
        const b=document.createElement('button');b.textContent=`LOAD ${id}`;
        b.addEventListener('click',async()=>{
          try{
            status.textContent=`Loading ${file.name} → Deck ${id}…`;
            await window.ARDADJCore?.loadLocal?.(id,file);
            cuePoints[id]=0;hotCues[id]=[null,null,null,null];beatLoops[id]=null;rolls[id]=null;updateCue(id);
            status.textContent=`${file.name} → Deck ${id} siap.`;
            setTargetDeck(null);closePanels();
          }catch(err){console.error(err);status.textContent=`Gagal membuka ${file.name}.`;}
        });
        acts.appendChild(b);
      });
      row.append(meta,acts);list.appendChild(row);
    });
  }
  $('driveInput')?.addEventListener('change',e=>renderFiles(Array.from(e.target.files||[])));

  function sourceBpm(id){return Number(window.ARDADJAnalysis?.getBpm?.(id))||Number(window.ACCDJ9?.sourceBpm?.(id))||0;}
  function beatAnchor(id){
    if(window.ARDADJAnalysis?.gridReady?.(id))return Number(window.ARDADJAnalysis.getAnchor(id))||0;
    const d=window.ACCDJ9?.decks?.[id];
    return Number.isFinite(d?.anchor)?d.anchor:0;
  }
  function quantizedBeat(id,time){
    const bpm=sourceBpm(id);if(!bpm)return time;
    const beat=60/bpm,anchor=beatAnchor(id);
    return Math.max(0,anchor+Math.round((time-anchor)/beat)*beat);
  }
  function updatePadUi(){
    $('padsTitle').textContent=`DECK ${performanceDeck} • PERFORMANCE`;
    document.querySelectorAll('[data-sync-mode]').forEach(b=>b.classList.toggle('active',b.dataset.syncMode===(window.ARDADJSync?.getMode?.(performanceDeck)||'BEAT')));
    document.querySelectorAll('[data-hotcue]').forEach(b=>{
      const n=Number(b.dataset.hotcue)-1;
      b.classList.toggle('hot-set',hotCues[performanceDeck][n]!=null);
      b.textContent=hotCues[performanceDeck][n]!=null?`HOT CUE ${n+1} ✓`:`HOT CUE ${n+1}`;
    });
    document.querySelectorAll('[data-beatloop]').forEach(b=>{
      b.classList.toggle('loop-active',beatLoops[performanceDeck]?.beats===Number(b.dataset.beatloop));
    });
  }
  document.querySelectorAll('[data-open-pads]').forEach(b=>b.addEventListener('click',()=>{
    performanceDeck=b.dataset.openPads;updatePadUi();openPanel('padsPopup');
  }));
  document.querySelectorAll('[data-sync-mode]').forEach(b=>b.addEventListener('click',()=>{
    window.ARDADJSync?.setMode?.(performanceDeck,b.dataset.syncMode);updatePadUi();
  }));
  document.querySelectorAll('[data-hotcue]').forEach(b=>b.addEventListener('click',()=>{
    const a=$(`audio${performanceDeck}`);if(!a?.src)return;
    const n=Number(b.dataset.hotcue)-1;
    if(hotCues[performanceDeck][n]==null)hotCues[performanceDeck][n]=quantizedBeat(performanceDeck,a.currentTime||0);
    else a.currentTime=hotCues[performanceDeck][n];
    updatePadUi();
  }));

  function setBeatLoop(id,beats){
    const a=$(`audio${id}`),bpm=sourceBpm(id);if(!a?.src||!bpm)return;
    const beat=60/bpm,start=quantizedBeat(id,a.currentTime||0);
    beatLoops[id]={start,end:start+beat*beats,beats};
    updatePadUi();
  }
  document.querySelectorAll('[data-beatloop]').forEach(b=>b.addEventListener('click',()=>setBeatLoop(performanceDeck,Number(b.dataset.beatloop))));
  $('loopExitBtn')?.addEventListener('click',()=>{beatLoops[performanceDeck]=null;updatePadUi();});
  document.querySelectorAll('[data-grid-nudge]').forEach(b=>b.addEventListener('click',()=>{
    window.ARDADJAnalysis?.nudge?.(performanceDeck,Number(b.dataset.gridNudge));
  }));
  document.querySelector('[data-grid-set]')?.addEventListener('click',()=>{
    window.ARDADJAnalysis?.setBeatHere?.(performanceDeck);
  });
  document.querySelectorAll('[data-grid-bpm]').forEach(b=>b.addEventListener('click',()=>{
    window.ARDADJAnalysis?.scaleBpm?.(performanceDeck,Number(b.dataset.gridBpm));
  }));

  function updateFxUi(){
    $('fxTitle').textContent=`DECK ${fxDeck} • FX`;
    const s=fxState[fxDeck];
    $('fxAmount').value=s.amount;
    $('fxAmountLabel').textContent=`${Math.round(s.amount*100)}%`;
    document.querySelectorAll('[data-fx]').forEach(b=>b.classList.toggle('active',b.dataset.fx===s.type));
  }
  function rollWindow(id,amount){
    const a=$(`audio${id}`),bpm=sourceBpm(id);if(!a?.src||!bpm)return null;
    const beat=60/bpm;
    const beats=amount>.72?.25:amount>.42?.5:1;
    const start=quantizedBeat(id,a.currentTime||0);
    return {start,end:start+beat*beats};
  }
  function applyFx(){
    const s=fxState[fxDeck];
    if(s.type==='ROLL'){
      window.ARDADJCore?.setFx?.(fxDeck,'OFF',0,false);
      rolls[fxDeck]=rollWindow(fxDeck,s.amount);
    }else{
      rolls[fxDeck]=null;
      window.ARDADJCore?.setFx?.(fxDeck,s.type||'OFF',s.amount,!!s.type);
    }
    updateFxUi();
  }
  document.querySelectorAll('[data-open-fx]').forEach(b=>b.addEventListener('click',()=>{
    fxDeck=b.dataset.openFx;updateFxUi();openPanel('fxPopup');
  }));
  document.querySelectorAll('[data-fx]').forEach(b=>b.addEventListener('click',()=>{
    fxState[fxDeck].type=fxState[fxDeck].type===b.dataset.fx?null:b.dataset.fx;applyFx();
  }));
  $('fxAmount')?.addEventListener('input',e=>{fxState[fxDeck].amount=Number(e.target.value);applyFx();});
  $('fxOffBtn')?.addEventListener('click',()=>{
    fxState[fxDeck].type=null;rolls[fxDeck]=null;window.ARDADJCore?.setFx?.(fxDeck,'OFF',0,false);updateFxUi();
  });

  function performanceLoop(){
    ['A','B'].forEach(id=>{
      const a=$(`audio${id}`);if(!a||a.paused)return;
      const r=rolls[id];
      if(r && a.currentTime>=r.end-.008){try{a.currentTime=r.start;}catch(_){}return;}
      const l=beatLoops[id];
      if(l && a.currentTime>=l.end-.008){try{a.currentTime=l.start;}catch(_){}}
    });
    requestAnimationFrame(performanceLoop);
  }
  requestAnimationFrame(performanceLoop);

  const syncNativeImmersive=()=>{
    const enabled=document.body.classList.contains('focus-mode');
    try{window.AndroidDJ?.setImmersive?.(enabled);}catch(_){}
  };
  new MutationObserver(syncNativeImmersive).observe(document.body,{attributes:true,attributeFilter:['class']});
  document.addEventListener('fullscreenchange',syncNativeImmersive);
  window.addEventListener('load',syncNativeImmersive);
})();