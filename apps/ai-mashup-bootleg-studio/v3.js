(()=>{
'use strict';
const q=s=>document.querySelector(s);
const planBtn=q('#remixPlan');
if(!planBtn) return;

planBtn.textContent='1. BUILD REMIX PLAN';
planBtn.classList.remove('primary');
planBtn.classList.add('ghost');

const targetPanel=planBtn.closest('.panel');
const renderBtn=document.createElement('button');
renderBtn.id='generateRemixPreview';
renderBtn.className='btn primary full';
renderBtn.style.marginTop='8px';
renderBtn.textContent='2. GENERATE REMIX PREVIEW';
planBtn.insertAdjacentElement('afterend',renderBtn);

const genreBpm={Funkot:150,Breakbeat:132,Koplo:150,Indobounce:138,Club:128,'Afro House':122,'Tech House':126};
const quick=document.createElement('button');
quick.className='btn cyan full';
quick.style.marginTop='8px';
quick.textContent='USE RECOMMENDED GENRE BPM';
renderBtn.insertAdjacentElement('afterend',quick);
quick.onclick=()=>{
  const g=q('#remixGenre').value;
  if(genreBpm[g]) q('#remixBpm').value=genreBpm[g];
  toast(`${g}: ${q('#remixBpm').value} BPM`);
};

const result=document.createElement('div');
result.className='panel result';
result.id='remixAudioResult';
result.innerHTML=`
  <h2>Remix Preview Ready</h2>
  <p class="hint" id="remixAudioInfo">Source + genre groove preview.</p>
  <div class="wave" id="remixWave"></div>
  <audio id="remixPlayer" controls></audio>
  <div class="meta">
    <div><b id="rGenre">-</b><span>Genre</span></div>
    <div><b id="rBpm">-</b><span>Target BPM</span></div>
    <div><b id="rKey">-</b><span>Target Key</span></div>
    <div><b id="rDur">-</b><span>Preview</span></div>
  </div>
  <div class="row" style="margin-top:10px">
    <button class="btn ghost grow" id="regenRemix">REGENERATE</button>
    <button class="btn cyan grow" id="exportRemix">EXPORT WAV</button>
  </div>
  <button class="btn primary full" style="margin-top:8px" id="saveRemixProject">SAVE REMIX PROJECT</button>
  <div class="notice">Preview lokal memakai tempo adaptation sederhana + groove sintetis. Render HQ dengan independent time-stretch/key-shift tetap membutuhkan render engine khusus.</div>`;
targetPanel.insertAdjacentElement('afterend',result);

let remixBlob=null;
let remixObjectUrl=null;
let lastMeta=null;

function renderWave(){
  const el=q('#remixWave');
  el.innerHTML='';
  for(let i=0;i<50;i++){
    const b=document.createElement('div');
    b.className='bar';
    b.style.height=(18+Math.random()*58)+'px';
    el.appendChild(b);
  }
}
function keyRootMidi(k){
  const m=String(k||'C').match(/^([A-G])([#b]?)/);
  const map={C:0,D:2,E:4,F:5,G:7,A:9,B:11};
  let n=map[m?.[1]||'C'];
  if(m?.[2]==='#')n++;
  if(m?.[2]==='b')n--;
  return 48+((n%12)+12)%12;
}
function hz(m){return 440*Math.pow(2,(m-69)/12)}
function pseudoNoise(i){const x=Math.sin(i*12.9898+78.233)*43758.5453;return (x-Math.floor(x))*2-1}

async function renderRemix(){
  if(typeof sources==='undefined'||!sources.length){toast('Pilih lagu dulu');return}
  const src=sources[0];
  if(!src.file){toast('File source utama tidak tersedia');return}
  if(!src.bpm||!src.key){toast('Analyzer source utama belum selesai');return}
  const tb=Number(q('#remixBpm').value);
  const tk=q('#remixKey').value;
  const genre=q('#remixGenre').value;
  if(!tb||tb<60||tb>220){toast('Target BPM tidak valid');return}

  renderBtn.disabled=true;
  renderBtn.textContent='RENDERING AUDIO...';
  try{
    const AC=window.AudioContext||window.webkitAudioContext;
    if(!AC) throw new Error('Audio engine tidak tersedia');
    const ctx=new AC();
    const ab=await src.file.arrayBuffer();
    const decoded=await ctx.decodeAudioData(ab.slice(0));
    try{await ctx.close()}catch{}

    const sr=22050;
    const dur=Math.min(24,Math.max(8,decoded.duration/Math.max(1,tb/src.bpm)));
    const N=Math.floor(sr*dur);
    const out=new Float32Array(N);
    const ratio=tb/src.bpm;
    const sourceSr=decoded.sampleRate;
    const ch=[];
    for(let c=0;c<decoded.numberOfChannels;c++) ch.push(decoded.getChannelData(c));
    const beat=60/tb;
    const root=keyRootMidi(tk);
    const bassSteps=[0,0,3,5,0,7,5,3];

    for(let i=0;i<N;i++){
      const t=i/sr;
      const srcPos=i*(sourceSr/sr)*ratio;
      const i0=Math.floor(srcPos);
      const f=srcPos-i0;
      let dry=0;
      if(i0+1<decoded.length){
        for(let c=0;c<ch.length;c++) dry+=(ch[c][i0]*(1-f)+ch[c][i0+1]*f);
        dry/=ch.length;
      }
      const phase=(t%beat)/beat;
      const beatIndex=Math.floor(t/beat)%4;
      let y=dry*.62;

      const kickEnv=Math.exp(-phase*28);
      const kickFreq=46+72*Math.exp(-phase*20);
      y+=Math.sin(2*Math.PI*kickFreq*t)*kickEnv*.58;

      if(beatIndex===1||beatIndex===3){
        const sn=Math.exp(-phase*30)*pseudoNoise(i);
        y+=sn*.20;
      }
      const half=beat/2;
      const hp=(t%half)/half;
      y+=pseudoNoise(i*3+17)*Math.exp(-hp*55)*.055;

      const bassStep=Math.floor(t/beat)%bassSteps.length;
      const bf=hz(root-12+bassSteps[bassStep]);
      y+=Math.sin(2*Math.PI*bf*t)*.11*(1-Math.exp(-phase*12));

      if(genre==='Funkot'||genre==='Indobounce'){
        const eighth=Math.floor(t/(beat/2));
        const lead=hz(root+12+[0,3,5,7][eighth%4]);
        y+=Math.sin(2*Math.PI*lead*t)*.035;
      }
      out[i]=Math.tanh(y*1.15)*.82;
    }

    remixBlob=wavEncode(out,sr);
    if(remixObjectUrl) URL.revokeObjectURL(remixObjectUrl);
    remixObjectUrl=URL.createObjectURL(remixBlob);
    q('#remixPlayer').src=remixObjectUrl;
    q('#rGenre').textContent=genre;
    q('#rBpm').textContent=tb;
    q('#rKey').textContent=tk;
    q('#rDur').textContent=Math.round(dur)+'s';
    q('#remixAudioInfo').textContent=`${src.name} · ${src.bpm}→${tb} BPM · ${src.key}→${tk}`;
    lastMeta={type:'Remix',genre,bpm:tb,key:tk,sourceBpm:src.bpm,sourceKey:src.key,sourceName:src.name,created:Date.now()};
    renderWave();
    result.classList.add('show');
    result.scrollIntoView({behavior:'smooth',block:'center'});
    toast('Remix preview siap diputar');
  }catch(e){
    console.error(e);
    toast('Render gagal: '+e.message);
  }finally{
    renderBtn.disabled=false;
    renderBtn.textContent='2. GENERATE REMIX PREVIEW';
  }
}

renderBtn.onclick=renderRemix;
q('#regenRemix').onclick=renderRemix;
q('#exportRemix').onclick=()=>{
  if(!remixBlob){toast('Generate preview dulu');return}
  const a=document.createElement('a');
  const u=URL.createObjectURL(remixBlob);
  a.href=u;
  a.download=`remix-${q('#remixGenre').value.toLowerCase().replace(/\s+/g,'-')}-${Date.now()}.wav`;
  a.click();
  setTimeout(()=>URL.revokeObjectURL(u),1200);
};
q('#saveRemixProject').onclick=()=>{
  if(!lastMeta){toast('Generate preview dulu');return}
  const p=store.get('ams_v21_projects',[]);
  p.unshift({...lastMeta,id:Date.now()});
  store.set('ams_v21_projects',p.slice(0,50));
  if(typeof renderProjects==='function') renderProjects();
  toast('Remix project tersimpan');
};
})();
