(() => {
  'use strict';
  const $=(id)=>document.getElementById(id);
  const linked={A:false,B:false};
  const mode={A:'BEAT',B:'BEAT'};
  const stable={A:0,B:0};
  const lastHard={A:0,B:0};
  let master='A';

  const clamp=(v,a,b)=>Math.max(a,Math.min(b,v));
  const audio=(id)=>$(`audio${id}`);
  const analysis=()=>window.ARDADJAnalysis;

  function bpm(id){
    return Number(analysis()?.getBpm?.(id)) || Number(window.ACCDJ9?.sourceBpm?.(id)) || 0;
  }
  function anchor(id){
    if(analysis()?.gridReady?.(id)) return Number(analysis().getAnchor(id))||0;
    const d=window.ACCDJ9?.decks?.[id];
    return Number.isFinite(d?.anchor)?d.anchor:NaN;
  }
  function gridReady(id){
    if(analysis()?.gridReady?.(id)) return true;
    const d=window.ACCDJ9?.decks?.[id];
    return !!d && Number.isFinite(d.lockedBpm) && d.confidence>=7 && d.beatConfidence>=5 && Number.isFinite(d.anchor);
  }

  function setStatus(id,status){
    const btn=document.querySelector(`[data-action="sync"][data-deck="${id}"]`);
    const label=$(`syncState${id}`);
    btn?.classList.toggle('synced',linked[id]);
    if(btn)btn.textContent=linked[id]?'SYNC ✓':'SYNC';
    if(label){
      label.className='sync-state';
      if(status==='LOCK')label.classList.add('locked');
      else if(['ALIGN','GRID'].includes(status))label.classList.add('chasing');
      else if(['ARM','TEMPO'].includes(status))label.classList.add('armed');
      label.textContent=linked[id]?`${status} ${master}`:(id===master?'MASTER':'FREE');
    }
  }

  function setMaster(id){
    if(!['A','B'].includes(id))return;
    master=id;
    linked[id]=false;
    stable[id]=0;
    setStatus('A','FREE');
    setStatus('B','FREE');
  }

  function setMode(id,next){
    mode[id]=next==='TEMPO'?'TEMPO':'BEAT';
    stable[id]=0;
    if(linked[id])setStatus(id,mode[id]==='TEMPO'?'TEMPO':(gridReady(id)&&gridReady(master)?'ALIGN':'GRID'));
  }

  function nominalRate(id){
    const fb=bpm(id),mb=bpm(master);
    const f=audio(id),m=audio(master);
    if(!fb||!mb||!f||!m)return null;
    return (mb*(m.playbackRate||1))/fb;
  }

  function phase(id){
    const a=audio(id),b=bpm(id),an=anchor(id);
    if(!a||!b||!Number.isFinite(an))return null;
    const beat=60/b;
    return ((((a.currentTime-an)/beat)%1)+1)%1;
  }

  function phaseError(id){
    const fp=phase(id),mp=phase(master);
    if(fp===null||mp===null)return null;
    let e=fp-mp;
    if(e>.5)e-=1;
    if(e<-.5)e+=1;
    return e;
  }

  function updatePitchUi(id,rate){
    const pct=(rate-1)*100;
    const p=$(`pitch${id}`),l=$(`pitchLabel${id}`);
    if(p)p.value=clamp(pct,-8,8).toFixed(1);
    if(l)l.textContent=`${pct>=0?'+':''}${pct.toFixed(1)}%`;
  }

  function hardAlign(id){
    const f=audio(id),base=bpm(id),e=phaseError(id);
    if(!f||!base||e===null)return false;
    const beat=60/base;
    const shift=clamp(-e*beat,-beat*.42,beat*.42);
    const max=Number.isFinite(f.duration)?Math.max(0,f.duration-.03):1e9;
    try{f.currentTime=clamp(f.currentTime+shift,0,max);}catch(_){return false;}
    lastHard[id]=performance.now();
    stable[id]=0;
    return true;
  }

  function align(id,force=false){
    if(!linked[id]||id===master)return;
    const f=audio(id),m=audio(master),rate=nominalRate(id);
    if(!f||!m||!rate){setStatus(id,'GRID');return;}
    const pct=(rate-1)*100;
    if(Math.abs(pct)>15){setStatus(id,'TEMPO');return;}
    updatePitchUi(id,rate);

    if(f.paused||m.paused){
      f.playbackRate=rate;
      stable[id]=0;
      setStatus(id,mode[id]==='TEMPO'?'TEMPO':'ARM');
      return;
    }

    if(mode[id]==='TEMPO'){
      f.playbackRate=rate;
      setStatus(id,'TEMPO');
      return;
    }

    if(!gridReady(id)||!gridReady(master)){
      f.playbackRate=rate;
      stable[id]=0;
      setStatus(id,'GRID');
      return;
    }

    const e=phaseError(id);
    if(e===null){f.playbackRate=rate;setStatus(id,'GRID');return;}
    const abs=Math.abs(e),now=performance.now();

    if(force || (abs>.10 && now-lastHard[id]>900)){
      hardAlign(id);
      f.playbackRate=rate;
      setStatus(id,'ALIGN');
      return;
    }

    if(abs>.022){
      const micro=clamp(-e*.045,-.0035,.0035);
      f.playbackRate=rate*(1+micro);
      stable[id]=0;
      setStatus(id,'ALIGN');
    }else{
      f.playbackRate=rate;
      stable[id]++;
      setStatus(id,stable[id]>=5?'LOCK':'ALIGN');
    }
  }

  function toggle(id,event){
    if(event){event.preventDefault();event.stopImmediatePropagation();}
    if(id===master){
      const other=id==='A'?'B':'A';
      if(audio(other)?.src){
        setStatus(id,'FREE');
        const l=$(`syncState${id}`);if(l)l.textContent='MASTER';
      }
      return;
    }
    if(!audio(id)?.src||!audio(master)?.src){setStatus(id,'GRID');return;}
    linked[id]=!linked[id];
    stable[id]=0;
    lastHard[id]=0;
    if(!linked[id]){
      const r=nominalRate(id);
      if(r)audio(id).playbackRate=r;
      setStatus(id,'FREE');
      return;
    }
    const r=nominalRate(id);
    if(r){audio(id).playbackRate=r;updatePitchUi(id,r);}
    setStatus(id,mode[id]==='TEMPO'?'TEMPO':(gridReady(id)&&gridReady(master)?'ALIGN':'GRID'));
    if(mode[id]==='BEAT'&&gridReady(id)&&gridReady(master)&&!audio(id).paused&&!audio(master).paused)align(id,true);
  }

  document.addEventListener('click',(e)=>{
    const m=e.target.closest?.('[data-action="master"]');
    if(m)setMaster(m.dataset.deck);
  },true);

  document.addEventListener('click',(e)=>{
    const b=e.target.closest?.('[data-action="sync"]');
    if(b)toggle(b.dataset.deck,e);
  },true);

  ['A','B'].forEach(id=>{
    const a=audio(id);
    a?.addEventListener('play',()=>{
      const other=id==='A'?'B':'A';
      if(!linked[id] && (!audio(other)?.src || audio(other)?.paused)) setMaster(id);
      if(linked[id]&&mode[id]==='BEAT')setTimeout(()=>align(id,true),0);
    });
    a?.addEventListener('seeking',()=>{if(linked[id]&&mode[id]==='BEAT')stable[id]=0;});
    $(`pitch${id}`)?.addEventListener('pointerdown',()=>{
      if(linked[id]){linked[id]=false;setStatus(id,'FREE');}
    },true);
  });

  window.addEventListener('arda-analysis-ready',(e)=>{
    const id=e.detail?.id;
    if(linked[id]&&mode[id]==='BEAT')align(id,true);
    const other=id==='A'?'B':'A';
    if(linked[other]&&master===id&&mode[other]==='BEAT')align(other,true);
  });
  window.addEventListener('arda-grid-edited',(e)=>{
    const id=e.detail?.id;
    if(linked[id]&&mode[id]==='BEAT')align(id,true);
    const other=id==='A'?'B':'A';
    if(master===id&&linked[other]&&mode[other]==='BEAT')align(other,true);
  });

  setInterval(()=>{align('A');align('B');},120);

  window.ARDADJSync={
    setMaster,toggle,setMode,getMode:(id)=>mode[id],linked,gridReady,getMaster:()=>master,align
  };
})();