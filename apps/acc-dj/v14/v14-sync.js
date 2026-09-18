(() => {
  'use strict';
  const byId = (id) => document.getElementById(id);
  const linked = { A:false, B:false };
  const syncMode = { A:'BEAT', B:'BEAT' };
  const lastHardAlign = { A:0, B:0 };
  const stable = { A:0, B:0 };
  let master = 'A';

  const clamp=(v,min,max)=>Math.max(min,Math.min(max,v));
  const engine=()=>window.ACCDJ9 || window.ARDADJBPM;

  function bpm(id){ return Number(engine()?.sourceBpm?.(id)) || 0; }
  function deckInfo(id){ return engine()?.decks?.[id] || null; }
  function audio(id){ return byId(`audio${id}`); }

  function phase(id){
    const d=deckInfo(id), a=audio(id), b=bpm(id);
    if(!d || !a || !b || !Number.isFinite(d.anchor)) return null;
    const beat=60/b;
    return ((((a.currentTime-d.anchor)/beat)%1)+1)%1;
  }

  function phaseError(follower,leader){
    const a=phase(follower), b=phase(leader);
    if(a===null || b===null) return null;
    let e=a-b;
    if(e>.5)e-=1;
    if(e<-.5)e+=1;
    return e;
  }

  function gridReady(id){
    const d=deckInfo(id);
    return !!d && Number.isFinite(d.lockedBpm) && d.confidence>=7 && d.beatConfidence>=5 && Number.isFinite(d.anchor);
  }

  function setStatus(id,status){
    const btn=document.querySelector(`[data-action="sync"][data-deck="${id}"]`);
    const label=byId(`syncState${id}`);
    btn?.classList.toggle('synced',linked[id]);
    if(btn) btn.textContent=linked[id]?'SYNC ✓':'SYNC';
    if(label){
      label.className='sync-state';
      if(status==='LOCK')label.classList.add('locked');
      else if(['ALIGN','ANALYZE'].includes(status))label.classList.add('chasing');
      else if(['ARM','TEMPO'].includes(status))label.classList.add('armed');
      label.textContent=linked[id]?`${status} ${master}`:(id===master?'MASTER':'FREE');
    }
  }

  function setMaster(id){
    if(!['A','B'].includes(id))return;
    master=id;
    linked[id]=false;
    stable[id]=0;
    setStatus('A','FREE'); setStatus('B','FREE');
  }

  function setMode(id,mode){
    if(!['A','B'].includes(id))return;
    syncMode[id]=mode==='TEMPO'?'TEMPO':'BEAT';
    stable[id]=0;
    if(linked[id]) setStatus(id,syncMode[id]==='TEMPO'?'TEMPO':'ARM');
  }

  function nominalRate(id){
    const followerBpm=bpm(id), leaderBpm=bpm(master);
    const f=audio(id), l=audio(master);
    if(!followerBpm || !leaderBpm || !f || !l) return null;
    const rate=(leaderBpm*(l.playbackRate||1))/followerBpm;
    return Number.isFinite(rate)?rate:null;
  }

  function hardAlign(id,error){
    const f=audio(id), base=bpm(id);
    if(!f || !base || error===null)return;
    const beat=60/base;
    const seconds=clamp(-error*beat,-beat*.45,beat*.45);
    const next=clamp(f.currentTime+seconds,0,Number.isFinite(f.duration)?Math.max(0,f.duration-.02):1e9);
    try{f.currentTime=next;}catch(_){}
    lastHardAlign[id]=performance.now();
    stable[id]=0;
  }

  function alignOne(id){
    if(!linked[id] || id===master)return;
    const f=audio(id), l=audio(master);
    const rate=nominalRate(id);
    if(!f || !l || !rate){ setStatus(id,'ANALYZE'); return; }
    const pct=(rate-1)*100;
    if(Math.abs(pct)>12){ setStatus(id,'TEMPO'); return; }

    const pitch=byId(`pitch${id}`), pitchLabel=byId(`pitchLabel${id}`);
    if(pitch) pitch.value=clamp(pct,-8,8).toFixed(1);
    if(pitchLabel) pitchLabel.textContent=`${pct>=0?'+':''}${pct.toFixed(1)}%`;

    if(f.paused || l.paused){
      f.playbackRate=rate;
      stable[id]=0;
      setStatus(id,syncMode[id]==='TEMPO'?'TEMPO':'ARM');
      return;
    }

    if(syncMode[id]==='TEMPO'){
      f.playbackRate=rate;
      setStatus(id,'TEMPO');
      return;
    }

    if(!gridReady(id) || !gridReady(master)){
      f.playbackRate=rate;
      stable[id]=0;
      setStatus(id,'ANALYZE');
      return;
    }

    const error=phaseError(id,master);
    if(error===null){f.playbackRate=rate;setStatus(id,'ANALYZE');return;}
    const abs=Math.abs(error), now=performance.now();

    // Large phase errors are corrected by a small beat-position jump, not by
    // wildly changing playback speed. This avoids the audible "hunting" of v1.x.
    if(abs>.075 && now-lastHardAlign[id]>180){
      hardAlign(id,error);
      f.playbackRate=rate;
      setStatus(id,'ALIGN');
      return;
    }

    if(abs>.018){
      const micro=clamp(-error*.10,-.006,.006);
      f.playbackRate=rate*(1+micro);
      stable[id]=0;
      setStatus(id,'ALIGN');
    }else{
      f.playbackRate=rate;
      stable[id]++;
      setStatus(id,stable[id]>=6?'LOCK':'ALIGN');
    }
  }

  function toggle(id,event){
    if(event){event.preventDefault();event.stopImmediatePropagation();}
    if(id===master){
      const other=id==='A'?'B':'A';
      setStatus(id,'FREE');
      const label=byId(`syncState${id}`); if(label)label.textContent='MASTER';
      if(!audio(other)?.src) return;
      return;
    }
    if(!audio(id)?.src || !audio(master)?.src){setStatus(id,'ANALYZE');return;}
    linked[id]=!linked[id];
    stable[id]=0; lastHardAlign[id]=0;
    if(!linked[id]){setStatus(id,'FREE');return;}
    setStatus(id,syncMode[id]==='TEMPO'?'TEMPO':'ARM');
    const f=audio(id), l=audio(master);
    if(f && l && !f.paused && !l.paused && syncMode[id]==='BEAT' && gridReady(id) && gridReady(master)){
      const err=phaseError(id,master); if(err!==null) hardAlign(id,err);
    }
  }

  document.addEventListener('click',(event)=>{
    const m=event.target.closest?.('[data-action="master"]');
    if(m) setMaster(m.dataset.deck);
  },true);

  document.addEventListener('click',(event)=>{
    const b=event.target.closest?.('[data-action="sync"]');
    if(b) toggle(b.dataset.deck,event);
  },true);

  ['A','B'].forEach(id=>{
    byId(`pitch${id}`)?.addEventListener('pointerdown',()=>{
      if(linked[id]){linked[id]=false;setStatus(id,'FREE');}
    },true);
  });

  function loop(){alignOne('A');alignOne('B');requestAnimationFrame(loop);}
  requestAnimationFrame(loop);
  window.ARDADJSync={setMaster,toggle,setMode,getMode:(id)=>syncMode[id],linked,gridReady};
})();