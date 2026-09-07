(function(){
'use strict';
var STYLE_ID='oracly-v5-hotfix-style';
var app=document.getElementById('app');
if(!app)return;
function addStyle(){
  if(document.getElementById(STYLE_ID))return;
  var st=document.createElement('style');st.id=STYLE_ID;
  st.textContent=`
  .energy{cursor:pointer!important;touch-action:manipulation!important;pointer-events:auto!important;border-radius:13px!important;padding:6px 2px!important;transition:transform .12s ease,background .12s ease!important}
  .energy:active,.energy.oracly-pressed{transform:scale(.94)!important;background:#1a1325!important}
  .energy b{width:42px!important;height:42px!important;border-color:#4a385e!important;box-shadow:inset 0 0 18px #7e54a51c!important}
  .bottomnav,.navitem{pointer-events:auto!important;touch-action:manipulation!important}
  .navitem:active{background:#21192d!important;color:#fff0b2!important;transform:scale(.97)!important}
  .tarot-back{font-size:0!important;border:2px solid #c9a55c!important;box-shadow:0 15px 35px #0009,inset 0 0 0 5px #100b18,inset 0 0 0 6px #7f6137!important;background:radial-gradient(circle at 50% 42%,#39254c 0 16%,transparent 17%),radial-gradient(circle at 50% 42%,transparent 0 27%,#5b3c74 28% 29%,transparent 30%),radial-gradient(circle at 50% 42%,transparent 0 40%,#3e2b53 41% 42%,transparent 43%),linear-gradient(160deg,#1b102c,#070711 72%)!important;position:relative!important;overflow:hidden!important}
  .tarot-back:before{content:'☾';position:absolute;inset:0;display:grid;place-items:center;font-size:44px;color:#f2d58e;text-shadow:0 0 16px #d9ad5666}
  .tarot-back:after{content:'✦';position:absolute;top:10px;left:11px;font-size:12px;color:#ddb96c;filter:drop-shadow(54px 104px 0 #ddb96c)}
  .face-art{height:164px!important;background:radial-gradient(circle at 50% 28%,#e7c87822 0 16%,transparent 17%),linear-gradient(160deg,#382149,#100b18 57%,#5e4523)!important;position:relative!important;display:grid!important;place-items:center!important;border-bottom:1px solid #8f6d34}
  .face-art:before{content:'☾  ✦  ☽';position:absolute;top:10px;font-size:10px;letter-spacing:3px;color:#d9b86f}
  .face-art:after{content:'✧';position:absolute;bottom:10px;font-size:16px;color:#caa55d}
  .face-art{font-size:38px!important;text-shadow:0 0 16px #f5dc9750}
  .oracly-focus-row{outline:1px solid #d9ad56!important;box-shadow:0 0 0 3px #d9ad5620,0 0 24px #d9ad5620!important}
  .oracly-toast{position:fixed;z-index:9999;left:50%;bottom:92px;transform:translateX(-50%);background:#171220;border:1px solid #735a34;color:#f2d58e;padding:10px 14px;border-radius:999px;font:12px system-ui;box-shadow:0 10px 28px #0008;white-space:nowrap;pointer-events:none}
  `;
  document.head.appendChild(st);
}
function labelKind(el){var t=(el.textContent||'').toLowerCase();if(t.indexOf('love')>=0)return'love';if(t.indexOf('career')>=0)return'career';if(t.indexOf('money')>=0)return'money';return'mood'}
function toast(msg){var old=document.querySelector('.oracly-toast');if(old)old.remove();var d=document.createElement('div');d.className='oracly-toast';d.textContent=msg;document.body.appendChild(d);setTimeout(function(){d.remove()},1100)}
function ensureMoodRow(){
  var title=Array.from(app.querySelectorAll('.title')).find(function(x){return (x.textContent||'').trim()==='Daily Reading'});if(!title)return;
  var list=app.querySelector('.list');if(!list)return;
  var hasMood=Array.from(list.querySelectorAll('.rtitle')).some(function(x){return (x.textContent||'').trim()==='Mood'});if(hasMood)return;
  var row=document.createElement('div');row.className='rowstatic';row.setAttribute('data-energy-kind','mood');row.innerHTML='<div class="rico">☾</div><div class="rmain"><div class="rtitle">Mood</div><div class="rdesc">Your emotional pace benefits from fewer distractions today. Give yourself room to respond instead of reacting.</div></div>';
  list.appendChild(row);
}
function enhanceEnergy(){
  app.querySelectorAll('.energy').forEach(function(el){
    el.setAttribute('role','button');el.setAttribute('tabindex','0');el.setAttribute('aria-label','Open '+(el.textContent||'').trim()+' reading');el.setAttribute('data-energy-kind',labelKind(el));
  });
  app.querySelectorAll('.rowstatic').forEach(function(row){var t=row.querySelector('.rtitle');if(t)row.setAttribute('data-energy-kind',labelKind(t))});
  ensureMoodRow();
}
function findDailyButton(){return app.querySelector('.menu-item[data-route="daily"],button[data-route="daily"]')}
function focusEnergy(kind){
  ensureMoodRow();
  var rows=Array.from(app.querySelectorAll('[data-energy-kind]')).filter(function(x){return x.classList.contains('rowstatic')||x.classList.contains('rowbtn')});
  var target=rows.find(function(x){return x.getAttribute('data-energy-kind')===kind});
  if(!target){target=Array.from(app.querySelectorAll('.rowstatic,.rowbtn')).find(function(x){return labelKind(x)===kind})}
  if(target){target.classList.add('oracly-focus-row');try{target.scrollIntoView({behavior:'smooth',block:'center'})}catch(e){target.scrollIntoView()};setTimeout(function(){target.classList.remove('oracly-focus-row')},1500)}
}
function openEnergy(el){
  var kind=el.getAttribute('data-energy-kind')||labelKind(el),names={love:'Love',career:'Career',money:'Money',mood:'Mood'};
  try{if(navigator.vibrate)navigator.vibrate(12)}catch(e){}
  var daily=findDailyButton();if(daily){daily.click();setTimeout(function(){enhanceEnergy();focusEnergy(kind);toast(names[kind]+' reading')},80);return}
  var home=app.querySelector('.navitem[data-route="home"]');if(home){home.click();setTimeout(function(){var d=findDailyButton();if(d){d.click();setTimeout(function(){enhanceEnergy();focusEnergy(kind);toast(names[kind]+' reading')},80)}},70)}
}
addStyle();enhanceEnergy();
var mo=new MutationObserver(function(){enhanceEnergy()});mo.observe(app,{childList:true,subtree:true});
document.addEventListener('pointerdown',function(e){var en=e.target.closest&&e.target.closest('.energy');if(en&&app.contains(en))en.classList.add('oracly-pressed')},true);
document.addEventListener('pointerup',function(e){var en=e.target.closest&&e.target.closest('.energy');if(en&&app.contains(en))en.classList.remove('oracly-pressed')},true);
document.addEventListener('click',function(e){var en=e.target.closest&&e.target.closest('.energy');if(!en||!app.contains(en))return;e.preventDefault();e.stopPropagation();openEnergy(en)},true);
document.addEventListener('keydown',function(e){var en=e.target.closest&&e.target.closest('.energy');if(en&&app.contains(en)&&(e.key==='Enter'||e.key===' ')){e.preventDefault();openEnergy(en)}},true);
window.ORACLY_V5_QC=function(){var es=app.querySelectorAll('.energy');var navs=app.querySelectorAll('.navitem');return{pass:es.length===4&&navs.length===4,energyControls:es.length,bottomNav:navs.length,tarotBack:!!app.querySelector('.tarot-back')}};
})();