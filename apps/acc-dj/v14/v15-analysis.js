(() => {
  'use strict';
  const $ = (id) => document.getElementById(id);
  const state = {
    A: fresh(),
    B: fresh()
  };
  const COLORS = { A:'#42b9ff', B:'#ffad4a' };
  function fresh() {
    return {
      ready:false, analyzing:false, failed:false, peaks:null, duration:0,
      bpm:0, anchor:0, confidence:0, source:'', token:0
    };
  }
  const clamp=(v,a,b)=>Math.max(a,Math.min(b,v));
  const mod=(v,m)=>((v%m)+m)%m;

  function setGridUi(id, text, ready=false) {
    const el=$(`grid${id}`);
    if(!el)return;
    el.textContent=text;
    el.classList.toggle('grid-ready',ready);
    el.classList.toggle('grid-analyzing',!ready && /ANALYZE|GRID…/.test(text));
  }

  function setBpmUi(id,bpm) {
    const el=$(`bpm${id}`);
    if(el && bpm>0) el.textContent=`BPM ${Number(bpm).toFixed(1)}`;
  }

  function reset(id, hintedBpm=0) {
    const token=(state[id]?.token||0)+1;
    state[id]=fresh();
    state[id].token=token;
    if(Number(hintedBpm)>0) {
      state[id].bpm=Number(hintedBpm);
      setBpmUi(id,state[id].bpm);
    }
    setGridUi(id,'ANALYZE');
    return token;
  }

  function channelArrays(buffer) {
    const out=[];
    for(let ch=0;ch<buffer.numberOfChannels;ch++) out.push(buffer.getChannelData(ch));
    return out;
  }

  const yieldUi = () => new Promise(resolve => setTimeout(resolve, 0));

  async function makePeaks(buffer, token, id) {
    const channels=channelArrays(buffer);
    const chCount=Math.max(1,channels.length);
    const len=buffer.length;
    const bins=clamp(Math.round(buffer.duration*20),1200,4800);
    const peaks=new Float32Array(bins);

    for(let b=0;b<bins;b++) {
      if(state[id].token!==token) throw new Error('Analysis superseded');
      const start=Math.floor((b/bins)*len);
      const end=Math.max(start+1,Math.floor(((b+1)/bins)*len));
      const step=Math.max(1,Math.floor((end-start)/56));
      let max=0;
      for(let i=start;i<end;i+=step) {
        let v=0;
        for(let ch=0;ch<channels.length;ch++) v+=channels[ch][i]||0;
        const a=Math.abs(v/chCount);
        if(a>max)max=a;
      }
      peaks[b]=Math.min(1,max);
      if((b&63)===63) await yieldUi();
    }
    return peaks;
  }

  async function onsetEnvelope(buffer, token, id) {
    const channels=channelArrays(buffer);
    const chCount=Math.max(1,channels.length);
    const hop=1024;
    const frame=2048;
    const fps=buffer.sampleRate/hop;
    const count=Math.max(1,Math.floor((buffer.length-frame)/hop));
    const energy=new Float32Array(count);
    let lp=0;

    for(let f=0;f<count;f++) {
      if(state[id].token!==token) throw new Error('Analysis superseded');
      const start=f*hop;
      let sum=0,n=0;
      for(let i=start;i<start+frame && i<buffer.length;i+=16) {
        let x=0;
        for(let ch=0;ch<channels.length;ch++) x+=channels[ch][i]||0;
        x/=chCount;
        // One-pole low-pass proxy: emphasize kick/bass instead of hi-hat transients.
        lp += .24*(x-lp);
        sum += lp*lp;
        n++;
      }
      energy[f]=Math.sqrt(sum/Math.max(1,n));
      if((f%160)===159) await yieldUi();
    }

    const flux=new Float32Array(count);
    let avg=0;
    for(let i=1;i<count;i++) {
      const d=Math.max(0,energy[i]-energy[i-1]);
      avg=i===1?d:avg*.985+d*.015;
      flux[i]=Math.max(0,d-avg*1.05);
    }
    return {flux,fps};
  }

  function estimateBpm(flux,fps,hint) {
    const hinted=Number(hint);
    if(hinted>=55 && hinted<=210) return hinted;
    const minBpm=68, maxBpm=190;
    const minLag=Math.max(2,Math.floor(fps*60/maxBpm));
    const maxLag=Math.max(minLag+1,Math.ceil(fps*60/minBpm));
    let bestLag=0,bestScore=-Infinity;
    for(let lag=minLag;lag<=maxLag;lag++) {
      let dot=0,a2=0,b2=0;
      for(let i=lag;i<flux.length;i++) {
        const a=flux[i],b=flux[i-lag];
        dot+=a*b;a2+=a*a;b2+=b*b;
      }
      const score=dot/Math.sqrt((a2||1)*(b2||1));
      if(score>bestScore){bestScore=score;bestLag=lag;}
    }
    if(!bestLag)return 0;
    let bpm=60*fps/bestLag;
    while(bpm<82)bpm*=2;
    while(bpm>168)bpm/=2;
    return Math.round(bpm*10)/10;
  }

  function estimateAnchor(flux,fps,bpm) {
    if(!bpm)return {anchor:0,confidence:0};
    const beat=60/bpm;
    const bins=72;
    const hist=new Float64Array(bins);
    let mean=0;
    for(let i=0;i<flux.length;i++)mean+=flux[i];
    mean/=Math.max(1,flux.length);
    let variance=0;
    for(let i=0;i<flux.length;i++){const d=flux[i]-mean;variance+=d*d;}
    const std=Math.sqrt(variance/Math.max(1,flux.length));
    const threshold=mean+std*.55;
    for(let i=1;i<flux.length-1;i++) {
      const v=flux[i];
      if(v<threshold || v<flux[i-1] || v<flux[i+1])continue;
      const t=i/fps;
      const phase=mod(t,beat)/beat;
      const bi=Math.min(bins-1,Math.floor(phase*bins));
      hist[bi]+=v;
      hist[(bi+1)%bins]+=v*.35;
      hist[(bi-1+bins)%bins]+=v*.35;
    }
    let best=0,total=0;
    for(let i=0;i<bins;i++){total+=hist[i];if(hist[i]>hist[best])best=i;}
    const avg=total/Math.max(1,bins);
    const confidence=avg>0?hist[best]/avg:0;
    return {anchor:((best+.5)/bins)*beat,confidence};
  }

  async function decode(arrayBuffer) {
    if(window.ARDADJCore?.decodeAudio) return await window.ARDADJCore.decodeAudio(arrayBuffer);
    const Ctx=window.AudioContext||window.webkitAudioContext;
    const ctx=new Ctx();
    try{return await ctx.decodeAudioData(arrayBuffer.slice(0));}
    finally{try{await ctx.close();}catch(_){}}
  }

  async function fetchArrayBuffer(url, timeout=7000) {
    const controller=new AbortController();
    const timer=setTimeout(()=>controller.abort(),timeout);
    try {
      const r=await fetch(url,{signal:controller.signal,cache:'no-store'});
      if(!r.ok)throw new Error(`Audio HTTP ${r.status}`);
      const size=Number(r.headers.get('content-length')||0);
      if(size>55*1024*1024)throw new Error('Audio too large for analysis');
      return await r.arrayBuffer();
    } finally { clearTimeout(timer); }
  }

  async function analyze(id, payload) {
    const hintedBpm=Number(payload?.track?.bpm)||0;
    const token=reset(id,hintedBpm);
    state[id].analyzing=true;
    state[id].source=payload?.file?'local':'stream';
    try {
      let bytes;
      if(payload?.file) bytes=await payload.file.arrayBuffer();
      else if(payload?.url) bytes=await fetchArrayBuffer(payload.url);
      else throw new Error('No analyzable audio source');
      if(state[id].token!==token)return;
      setGridUi(id,'GRID…');
      const buffer=await decode(bytes);
      if(state[id].token!==token)return;
      await new Promise(r=>setTimeout(r,0));
      const peaks=await makePeaks(buffer,token,id);
      state[id].peaks=peaks;
      state[id].duration=buffer.duration;
      await yieldUi();
      const {flux,fps}=await onsetEnvelope(buffer,token,id);
      const bpm=estimateBpm(flux,fps,hintedBpm);
      const phase=estimateAnchor(flux,fps,bpm);
      if(state[id].token!==token)return;
      Object.assign(state[id],{
        ready:!!(bpm && phase.confidence>1.05),
        analyzing:false,
        failed:false,
        peaks,
        duration:buffer.duration,
        bpm,
        anchor:phase.anchor,
        confidence:phase.confidence
      });
      if(bpm)setBpmUi(id,bpm);
      setGridUi(id,state[id].ready?'GRID ✓':'GRID ?',state[id].ready);
      window.dispatchEvent(new CustomEvent('arda-analysis-ready',{detail:{id,analysis:{...state[id],peaks:null}}}));
    } catch(err) {
      console.warn('ARDA DJ offline analysis failed',id,err);
      if(state[id].token!==token)return;
      state[id].analyzing=false;
      state[id].failed=true;
      state[id].ready=false;
      if(hintedBpm)setBpmUi(id,hintedBpm);
      setGridUi(id,hintedBpm?'GRID —':'ANALYZE');
      window.dispatchEvent(new CustomEvent('arda-analysis-failed',{detail:{id,error:String(err?.message||err)}}));
    }
  }

  function get(id){return state[id]||fresh();}
  function getBpm(id){return Number(state[id]?.bpm)||0;}
  function getAnchor(id){return Number(state[id]?.anchor)||0;}
  function gridReady(id){return !!state[id]?.ready;}

  function nudge(id,ms) {
    if(!state[id]?.bpm)return;
    state[id].anchor += Number(ms||0)/1000;
    state[id].ready=true;
    setGridUi(id,'GRID ✓',true);
    window.dispatchEvent(new CustomEvent('arda-grid-edited',{detail:{id}}));
  }
  function setBeatHere(id) {
    const a=$(`audio${id}`);
    if(!a || !state[id]?.bpm)return;
    state[id].anchor=a.currentTime||0;
    state[id].ready=true;
    state[id].confidence=Math.max(2,state[id].confidence||0);
    setGridUi(id,'GRID ✓',true);
    window.dispatchEvent(new CustomEvent('arda-grid-edited',{detail:{id}}));
  }
  function scaleBpm(id,factor) {
    if(!state[id]?.bpm)return;
    state[id].bpm=clamp(state[id].bpm*Number(factor||1),45,240);
    state[id].ready=true;
    setBpmUi(id,state[id].bpm);
    setGridUi(id,'GRID ✓',true);
    window.dispatchEvent(new CustomEvent('arda-grid-edited',{detail:{id}}));
  }

  function drawPeaks(ctx, peaks, startIndex, endIndex, w, h, color) {
    const mid=h/2;
    ctx.strokeStyle=color;
    ctx.lineWidth=1;
    ctx.beginPath();
    for(let x=0;x<w;x++) {
      const t=x/Math.max(1,w-1);
      const idx=clamp(Math.floor(startIndex+(endIndex-startIndex)*t),0,peaks.length-1);
      const amp=peaks[idx]||0;
      const y=amp*(h*.47);
      ctx.moveTo(x,mid-y);ctx.lineTo(x,mid+y);
    }
    ctx.stroke();
  }

  function drawGrid(ctx,id,start,end,w,h) {
    const d=state[id];
    if(!d?.ready || !d.bpm)return;
    const beat=60/d.bpm;
    let n=Math.floor((start-d.anchor)/beat)-1;
    for(;d.anchor+n*beat<=end+beat;n++) {
      const t=d.anchor+n*beat;
      if(t<start)continue;
      const x=((t-start)/(end-start))*w;
      const major=mod(n,4)===0;
      ctx.strokeStyle=major?'rgba(255,255,255,.55)':'rgba(255,255,255,.18)';
      ctx.lineWidth=major?1.5:1;
      ctx.beginPath();ctx.moveTo(x,0);ctx.lineTo(x,h);ctx.stroke();
    }
  }

  function drawFallback(id,canvas) {
    const d=state[id];
    const c=canvas.getContext('2d'),w=canvas.width,h=canvas.height;
    c.fillStyle='#05080b';c.fillRect(0,0,w,h);
    c.strokeStyle='rgba(255,255,255,.12)';
    c.beginPath();c.moveTo(0,h/2);c.lineTo(w,h/2);c.stroke();
    c.fillStyle=d?.failed?'#c98f8f':'#8c96a2';
    c.font='700 24px system-ui,sans-serif';
    c.textAlign='center';
    c.textBaseline='middle';
    c.fillText(d?.analyzing?'ANALYZING TRACK…':(d?.failed?'WAVEFORM UNAVAILABLE':'LOAD TRACK'),w/2,h/2);
    c.fillStyle='rgba(255,255,255,.85)';
    c.fillRect(Math.floor(w*.42),0,2,h);
  }

  function drawDeck(id) {
    const a=$(`audio${id}`);
    const canvas=$(`wave${id}`);
    const overview=$(`overview${id}`);
    if(!a||!canvas||!overview)return;
    const d=state[id];
    const c=canvas.getContext('2d'),w=canvas.width,h=canvas.height;
    c.fillStyle='#05080b';c.fillRect(0,0,w,h);
    if(d?.peaks?.length && d.duration>0) {
      const bpm=d.bpm||120;
      const span=clamp((60/bpm)*16,5.5,10);
      const start=(a.currentTime||0)-span*.42;
      const end=start+span;
      const si=(start/d.duration)*d.peaks.length;
      const ei=(end/d.duration)*d.peaks.length;
      drawGrid(c,id,start,end,w,h);
      drawPeaks(c,d.peaks,si,ei,w,h,COLORS[id]);
      c.fillStyle='rgba(255,255,255,.92)';
      c.fillRect(Math.floor(w*.42),0,2,h);
    } else {
      drawFallback(id,canvas);
    }

    const o=overview.getContext('2d'),ow=overview.width,oh=overview.height;
    o.fillStyle='#080b0f';o.fillRect(0,0,ow,oh);
    if(d?.peaks?.length) {
      drawPeaks(o,d.peaks,0,d.peaks.length-1,ow,oh,id==='A'?'#7ecfff':'#ffc276');
      const x=clamp(((a.currentTime||0)/(d.duration||a.duration||1))*ow,0,ow);
      o.fillStyle='rgba(255,255,255,.95)';o.fillRect(x-1,0,2,oh);
    }
  }

  function loop(){
    drawDeck('A');drawDeck('B');
    requestAnimationFrame(loop);
  }
  requestAnimationFrame(loop);

  window.addEventListener('arda-track-loaded',(e)=>{
    const id=e.detail?.id;
    if(!['A','B'].includes(id))return;
    analyze(id,e.detail);
  });

  window.ARDADJAnalysis={
    ownsWaveform:true,get,getBpm,getAnchor,gridReady,nudge,setBeatHere,scaleBpm,analyze
  };
})();