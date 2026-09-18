(() => {
  'use strict';
  const $ = (id) => document.getElementById(id);
  const backdrop = $('drawerBackdrop');
  const panels = ['musicDrawer','drivePopup','mixerPopup'];
  function closePanels(){ panels.forEach(id=>{ const p=$(id); if(!p)return; if(id==='musicDrawer'){p.classList.remove('open');p.setAttribute('aria-hidden','true')} else p.hidden=true; }); if(backdrop) backdrop.hidden=true; }
  function openPanel(id){ closePanels(); const p=$(id); if(!p)return; if(id==='musicDrawer'){p.classList.add('open');p.setAttribute('aria-hidden','false'); if(!$('results')?.children.length) $('trendingBtn')?.click();} else p.hidden=false; if(backdrop) backdrop.hidden=false; }

  $('driveBtn')?.addEventListener('click',()=>openPanel('drivePopup'));
  $('mixerBtn')?.addEventListener('click',()=>openPanel('mixerPopup'));
  document.querySelectorAll('[data-open-mixer]').forEach(b=>b.addEventListener('click',()=>openPanel('mixerPopup')));
  document.querySelectorAll('[data-close-modal]').forEach(b=>b.addEventListener('click',closePanels));
  backdrop?.addEventListener('click',closePanels);
  $('openApiBtn')?.addEventListener('click',()=>{ closePanels(); $('settingsBtn')?.click(); });
  document.addEventListener('keydown',e=>{if(e.key==='Escape')closePanels()});

  // Track artwork mirrors the platter label without coupling it to engine internals.
  ['A','B'].forEach(id=>{
    const cover=$(`cover${id}`), label=$(`label${id}`), play=document.querySelector(`[data-action="play"][data-deck="${id}"]`), deck=document.querySelector(`.deck[data-deck="${id}"]`);
    const syncCover=()=>{ if(label){ label.src=cover?.src||''; label.style.opacity=cover?.src?'1':'.2'; } };
    if(cover) new MutationObserver(syncCover).observe(cover,{attributes:true,attributeFilter:['src']}); syncCover();
    const syncPlay=()=>deck?.classList.toggle('playing',!!play?.classList.contains('playing'));
    if(play) new MutationObserver(syncPlay).observe(play,{attributes:true,attributeFilter:['class']}); syncPlay();
  });

  // Visible CUE = transport cue point. PFL/headphone cue stays in Mixer popup.
  const cuePoints={A:0,B:0};
  function cueButton(id){return $(`cuePoint${id}`)}
  function updateCue(id){const b=cueButton(id); if(!b)return; b.classList.toggle('active',cuePoints[id]>0.02); b.textContent=cuePoints[id]>0.02?'CUE ✓':'CUE'}
  ['A','B'].forEach(id=>{
    cueButton(id)?.addEventListener('click',()=>{
      const a=$(`audio${id}`); if(!a?.src)return;
      if(!a.paused){ a.pause(); a.currentTime=Math.min(cuePoints[id]||0,a.duration||Infinity); const pb=document.querySelector(`[data-action="play"][data-deck="${id}"]`); if(pb){pb.textContent='▶';pb.classList.remove('playing')} }
      else if(Math.abs((a.currentTime||0)-(cuePoints[id]||0))>.18){ cuePoints[id]=a.currentTime||0; updateCue(id); }
      else { a.currentTime=cuePoints[id]||0; }
    });
    $(`audio${id}`)?.addEventListener('loadedmetadata',()=>{cuePoints[id]=0;updateCue(id)});
  });

  // Drive / Android Files
  $('drivePickerBtn')?.addEventListener('click',()=>$('driveInput')?.click());
  const nice=(n)=>String(n||'Local Track').replace(/\.[^.]+$/,'').replace(/_/g,' ');
  const size=(n)=>n<1048576?`${Math.max(1,Math.round(n/1024))} KB`:`${(n/1048576).toFixed(1)} MB`;
  function renderFiles(files){
    const list=$('driveList'), status=$('driveStatus'); if(!list||!status)return; list.innerHTML='';
    if(!files.length){status.textContent='Tidak ada file audio dipilih.';list.innerHTML='<div class="empty-state">Pilih MP3 / M4A / AAC / WAV / FLAC / OGG.</div>';return;}
    status.textContent=`${files.length} lagu siap.`;
    files.forEach(file=>{const row=document.createElement('div');row.className='drive-track';const meta=document.createElement('div');meta.innerHTML=`<b></b><span></span>`;meta.querySelector('b').textContent=nice(file.name);meta.querySelector('span').textContent=`${file.type||'audio'} • ${size(file.size)}`;const acts=document.createElement('div');acts.className='drive-load';['A','B'].forEach(id=>{const b=document.createElement('button');b.textContent=`LOAD ${id}`;b.addEventListener('click',async()=>{try{status.textContent=`Loading ${file.name} → Deck ${id}…`;await window.ACCDJCore?.loadLocal?.(id,file);cuePoints[id]=0;updateCue(id);status.textContent=`${file.name} → Deck ${id} siap.`;closePanels();}catch(err){console.error(err);status.textContent=`Gagal membuka ${file.name}.`;}});acts.appendChild(b)});row.append(meta,acts);list.appendChild(row)});
  }
  $('driveInput')?.addEventListener('change',e=>renderFiles(Array.from(e.target.files||[])));
})();
