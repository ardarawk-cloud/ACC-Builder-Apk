(function(){
'use strict';
var major=[
  'The Fool','The Magician','The High Priestess','The Empress','The Emperor','The Hierophant','The Lovers','The Chariot','Strength','The Hermit','Wheel of Fortune','Justice','The Hanged Man','Death','Temperance','The Devil','The Tower','The Star','The Moon','The Sun','Judgement','The World'
];
var rankNo={Ace:'01',Two:'02',Three:'03',Four:'04',Five:'05',Six:'06',Seven:'07',Eight:'08',Nine:'09',Ten:'10',Page:'11',Knight:'12',Queen:'13',King:'14'};
var suitPrefix={Cups:'cups',Pentacles:'pents',Swords:'swords',Wands:'wands'};
function fileFor(name){
  var mi=major.indexOf(name);
  if(mi>=0)return 'major-'+String(mi).padStart(2,'0')+'.jpg';
  var m=/^(Ace|Two|Three|Four|Five|Six|Seven|Eight|Nine|Ten|Page|Knight|Queen|King) of (Cups|Pentacles|Swords|Wands)$/.exec(name);
  if(!m)return null;
  return suitPrefix[m[2]]+'-'+rankNo[m[1]]+'.jpg';
}
function addStyle(){
  if(document.getElementById('oracly-rws-style'))return;
  var s=document.createElement('style');
  s.id='oracly-rws-style';
  s.textContent='\
.face{border-color:#a88245!important;background:#0d0b10!important;box-shadow:0 16px 38px #0009!important}\
.face-art{height:auto!important;aspect-ratio:1115/1920!important;display:block!important;background:#0c0a0e!important;overflow:hidden!important}\
.face-art img.rws-card-image{display:block!important;width:100%!important;height:100%!important;object-fit:cover!important;object-position:center!important}\
.reading-cards.one{max-width:238px!important;margin:18px auto 20px!important}\
.reading-cards.one .face-art{height:auto!important;aspect-ratio:1115/1920!important}\
.face.rws-ready .face-name{display:none!important}\
.reading-cards.one .pos{font-size:12px!important;margin-top:8px!important}\
.rws-fallback{height:100%;display:grid;place-items:center;padding:12px;text-align:center;color:#e6cf91;font:600 13px Georgia,serif;background:linear-gradient(155deg,#241638,#0d0b16 58%,#5e4526)}';
  document.head.appendChild(s);
}
function paintFace(face,index){
  var nameEl=face.querySelector('.face-name');
  var art=face.querySelector('.face-art');
  if(!nameEl||!art)return;
  var name=(nameEl.textContent||'').trim();
  var file=fileFor(name);
  if(!file)return;
  if(art.getAttribute('data-rws-file')===file)return;
  art.setAttribute('data-rws-file',file);
  art.innerHTML='';
  var img=new Image();
  img.className='rws-card-image';
  img.alt=name+' tarot card illustration';
  img.decoding='async';
  img.loading='eager';
  img.onload=function(){face.classList.add('rws-ready')};
  img.onerror=function(){
    face.classList.remove('rws-ready');
    art.innerHTML='<div class="rws-fallback">'+name+'<br><small>Card artwork unavailable</small></div>';
  };
  img.src='tarot/'+file;
  art.appendChild(img);
}
function paintAll(){
  addStyle();
  var faces=document.querySelectorAll('.face');
  for(var i=0;i<faces.length;i++)paintFace(faces[i],i);
}
function boot(){
  addStyle();
  paintAll();
  var root=document.getElementById('app');
  if(!root)return;
  var queued=false;
  var obs=new MutationObserver(function(){
    if(queued)return;
    queued=true;
    requestAnimationFrame(function(){queued=false;paintAll()});
  });
  obs.observe(root,{childList:true,subtree:true});
  window.__ORACLY_RWS_ART={paint:paintAll,fileFor:fileFor,majorCount:major.length};
}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',boot);else boot();
})();
