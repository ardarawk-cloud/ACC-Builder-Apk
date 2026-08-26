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

(()=>{
'use strict';
const q=s=>document.querySelector(s);
if(!q('#generateLocal')||q('#generateFullAiV5')) return;

const GENRE_PROFILES={
  Funkot:'high-energy funkot club track, around 150 BPM, punchy four-on-the-floor kick, rolling offbeat bass, bright rave synth stabs, rapid drum fills, energetic risers, euphoric melodic hooks, sharp drop transitions, dense dancefloor energy, Indonesian club feel',
  Breakbeat:'energetic Indonesian breakbeat club track, syncopated broken drums, rolling bass, bright synth hooks, dramatic builds and powerful drops',
  Koplo:'modern electronic koplo dance track, fast kendang-inspired percussion, punchy bass, catchy synth hooks, festive build and drop energy',
  Indobounce:'modern Indonesian bounce club track, bouncy bass, hard kick, bright lead synths, rhythmic vocal-friendly arrangement, energetic drops',
  Club:'modern high-energy club dance track, strong four-on-the-floor kick, driving bass, memorable synth hook, clean builds and drops',
  'Afro House':'deep afro house track, organic percussion, warm bass, hypnotic groove, atmospheric melodic layers, elegant progressive build',
  'Tech House':'punchy tech house track, tight drums, rolling bass groove, sparse hook, crisp transitions and club-ready drop'
};

const localBtn=q('#generateLocal');
localBtn.textContent='LOCAL DEMO PREVIEW';
localBtn.classList.remove('primary');
localBtn.classList.add('ghost');

const aiBtn=document.createElement('button');
aiBtn.id='generateFullAiV5';
aiBtn.className='btn primary full';
aiBtn.style.marginTop='12px';
aiBtn.textContent='GENERATE FULL AI SONG';
localBtn.insertAdjacentElement('beforebegin',aiBtn);

const aiStatus=document.createElement('div');
aiStatus.id='fullAiStatusV5';
aiStatus.className='notice';
aiStatus.innerHTML='<b>REAL MUSIC ENGINE</b><br>Eleven Music v2 belum dikonfigurasi. Masukkan API key di Settings.';
aiBtn.insertAdjacentElement('afterend',aiStatus);

const settingsPage=q('#settings');
const settingsPanel=settingsPage?.querySelector('.panel');
if(settingsPanel){
  const card=document.createElement('div');
  card.style.marginTop='16px';
  card.innerHTML=`
    <h2>Eleven Music v2 · Personal Lab</h2>
    <p class="hint">Untuk testing pribadi. Key disimpan hanya di localStorage HP ini dan tidak ditulis ke GitHub/repo.</p>
    <div class="field"><label>ElevenLabs API Key</label><input id="elevenMusicKey" type="password" autocomplete="off" placeholder="sk_... atau xi_..."></div>
    <div class="row" style="margin-top:9px"><button class="btn ghost grow" id="testElevenMusic">TEST CONNECTION</button><button class="btn primary grow" id="saveElevenMusic">SAVE KEY</button></div>
    <div class="field"><label>Full Song Duration</label><select id="aiSongLength"><option value="60">1 menit</option><option value="90">1.5 menit</option><option value="120" selected>2 menit</option><option value="180">3 menit</option></select></div>
    <div id="elevenMusicConn" class="notice">NOT CONFIGURED</div>
    <div class="notice">Untuk APK publik nanti key akan dipindah ke backend. Jangan bagikan APK personal yang berisi key tersimpan.</div>`;
  settingsPanel.appendChild(card);
}

const keyInput=q('#elevenMusicKey');
const savedKey=localStorage.getItem('ams_eleven_music_key')||'';
if(keyInput) keyInput.value=savedKey;
function setConn(ok,msg){
  const el=q('#elevenMusicConn');
  if(el){el.className=ok?'good':'notice';el.textContent=msg;}
  aiStatus.className=ok?'good':'notice';
  aiStatus.innerHTML=ok?'<b>ELEVEN MUSIC v2 CONNECTED</b><br>Full AI Song siap dipakai.':'<b>REAL MUSIC ENGINE</b><br>'+msg;
}
if(savedKey) setConn(true,'API key tersimpan · belum dites pada sesi ini');

q('#saveElevenMusic')?.addEventListener('click',()=>{
  const k=(keyInput?.value||'').trim();
  if(!k){localStorage.removeItem('ams_eleven_music_key');setConn(false,'API key dihapus');return;}
  localStorage.setItem('ams_eleven_music_key',k);
  setConn(true,'API key tersimpan lokal');
  toast('Eleven Music key tersimpan');
});

async function apiJson(path,body,key){
  const res=await fetch('https://api.elevenlabs.io'+path,{method:'POST',headers:{'Content-Type':'application/json','xi-api-key':key},body:JSON.stringify(body)});
  const txt=await res.text();
  if(!res.ok){
    let msg=txt;
    try{const j=JSON.parse(txt);msg=j?.detail?.message||j?.detail?.status||j?.detail||txt}catch{}
    throw new Error(typeof msg==='string'?msg:JSON.stringify(msg));
  }
  return txt?JSON.parse(txt):{};
}

q('#testElevenMusic')?.addEventListener('click',async()=>{
  const key=(keyInput?.value||localStorage.getItem('ams_eleven_music_key')||'').trim();
  if(!key){toast('Masukkan API key dulu');return;}
  const b=q('#testElevenMusic');b.disabled=true;b.textContent='TESTING...';
  try{
    await apiJson('/v1/music/plan',{prompt:'Original energetic electronic club instrumental test, no vocals',music_length_ms:3000,model_id:'music_v2'},key);
    localStorage.setItem('ams_eleven_music_key',key);
    setConn(true,'CONNECTED · Eleven Music v2');
    toast('Music engine connected');
  }catch(e){setConn(false,'Connection failed: '+e.message);toast('Connection gagal');}
  finally{b.disabled=false;b.textContent='TEST CONNECTION';}
});

function compactLyrics(s){return String(s||'').replace(/\r/g,'').trim().slice(0,2600)}
function buildPrompt(){
  const lyrics=compactLyrics(q('#lyrics')?.value);
  const genre=(typeof selectedGenre!=='undefined'&&selectedGenre)||'Funkot';
  const mood=q('#mood')?.value||'Party / Euphoric';
  const vocal=q('#vocalType')?.value||'Female';
  const profile=GENRE_PROFILES[genre]||GENRE_PROFILES.Funkot;
  const vocalText=vocal==='Instrumental'?'instrumental only, no sung vocals':`${vocal.toLowerCase()} lead vocal, natural expressive singing in Indonesian, clear diction, catchy chorus delivery`;
  return `Create a completely original ${profile}. Mood: ${mood}. ${vocalText}. Professional club mix, strong low end, clean master, memorable original melody, no imitation of any specific artist or existing song. Structure: short DJ-friendly intro, verse, pre-chorus/build, big chorus/drop, second verse, break, final chorus/drop, clean outro. Use ONLY these supplied original lyrics exactly where suitable; do not add brand names or copyrighted lyrics.\n\nLYRICS:\n${lyrics}`.slice(0,4050);
}

let aiBlob=null,aiUrl=null;
async function generateFullSong(){
  const key=(localStorage.getItem('ams_eleven_music_key')||'').trim();
  const lyrics=compactLyrics(q('#lyrics')?.value);
  if(!key){toast('Isi Eleven Music API key di Settings');q('.nav[data-page="settings"]')?.click();return;}
  if(!lyrics && q('#vocalType')?.value!=='Instrumental'){toast('Masukkan lirik dulu');return;}
  const seconds=Number(q('#aiSongLength')?.value||120);
  aiBtn.disabled=true;aiBtn.textContent='AI COMPOSING SONG...';
  aiStatus.className='notice';aiStatus.innerHTML='<b>GENERATING...</b><br>Membuat composition plan dan full audio. Proses cloud bisa memakan waktu.';
  try{
    const prompt=buildPrompt();
    const plan=await apiJson('/v1/music/plan',{prompt,music_length_ms:seconds*1000,model_id:'music_v2'},key);
    aiStatus.innerHTML='<b>ARRANGEMENT READY</b><br>Rendering full music + vocal...';
    const res=await fetch('https://api.elevenlabs.io/v1/music?output_format=mp3_48000_192',{method:'POST',headers:{'Content-Type':'application/json','xi-api-key':key},body:JSON.stringify({composition_plan:plan,model_id:'music_v2',store_for_inpainting:false,sign_with_c2pa:true})});
    if(!res.ok){const t=await res.text();throw new Error(t.slice(0,500)||('HTTP '+res.status));}
    aiBlob=await res.blob();
    if(aiUrl) URL.revokeObjectURL(aiUrl);
    aiUrl=URL.createObjectURL(aiBlob);
    const player=q('#player');
    if(player) player.src=aiUrl;
    q('#result')?.classList.add('show');
    if(q('#metaGenre')) q('#metaGenre').textContent=(typeof selectedGenre!=='undefined'?selectedGenre:'Funkot');
    if(q('#metaDuration')) q('#metaDuration').textContent=seconds+'s';
    if(q('#metaSeed')) q('#metaSeed').textContent='AI-v2';
    aiStatus.className='good';aiStatus.innerHTML='<b>FULL AI SONG READY</b><br>Music + vocal berhasil dibuat oleh Eleven Music v2.';
    q('#result')?.scrollIntoView({behavior:'smooth',block:'center'});
    toast('Full AI Song siap diputar');
    let dl=q('#downloadAiSongV5');
    if(!dl){dl=document.createElement('button');dl.id='downloadAiSongV5';dl.className='btn cyan full';dl.style.marginTop='8px';dl.textContent='EXPORT FULL AI SONG MP3';q('#saveTrack')?.insertAdjacentElement('afterend',dl);}
    dl.onclick=()=>{if(!aiBlob)return;const a=document.createElement('a');const u=URL.createObjectURL(aiBlob);a.href=u;a.download=`ai-${String(typeof selectedGenre!=='undefined'?selectedGenre:'song').toLowerCase().replace(/\s+/g,'-')}-${Date.now()}.mp3`;a.click();setTimeout(()=>URL.revokeObjectURL(u),1500)};
  }catch(e){
    console.error(e);
    const cors=/Failed to fetch|NetworkError|CORS/i.test(String(e.message));
    aiStatus.className='notice';
    aiStatus.innerHTML='<b>GENERATION FAILED</b><br>'+(cors?'Koneksi langsung dari WebView diblokir. Tahap berikutnya harus lewat backend proxy.':e.message);
    toast('AI generation gagal');
  }finally{aiBtn.disabled=false;aiBtn.textContent='GENERATE FULL AI SONG';}
}
aiBtn.addEventListener('click',generateFullSong);
})();
