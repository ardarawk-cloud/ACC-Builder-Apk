(function(){
  'use strict';
  function appRoot(){ return document.getElementById('app'); }
  function visibleText(){ var a=appRoot(); return a ? (a.innerText||'').trim() : ''; }
  function startupGuard(){
    var a=appRoot();
    if(!a) return;
    if(visibleText().length < 8){
      a.innerHTML='<main style="min-height:100vh;padding:48px 22px;background:linear-gradient(180deg,#0d0918,#05050a);color:#f8f1e4;font-family:system-ui"><div style="max-width:420px;margin:auto;border:1px solid #69405b;background:#1b101d;border-radius:18px;padding:20px"><div style="font-family:Georgia,serif;font-size:28px;color:#f3d58a">ORACLY</div><h2 style="margin:14px 0 8px">Startup check failed</h2><p style="color:#c7bdd4;line-height:1.55">The interface did not finish loading. This build should not be used as a release candidate.</p><div id="oracly-startup-error" style="font-size:12px;color:#e7a9b6;margin-top:16px">Runtime watchdog detected an empty application root.</div></div></main>';
    }
  }
  window.ORACLY_QC=function(){
    var report=[];
    function check(name,fn){
      try{ var ok=!!fn(); report.push({name:name,pass:ok}); if(!ok) throw new Error(name); }
      catch(e){ report.push({name:name,pass:false,error:String(e&&e.message||e)}); }
    }
    check('startup-root',function(){return !!appRoot() && visibleText().length>8;});
    check('navigation-api',function(){return typeof go==='function' && typeof render==='function';});
    if(typeof go==='function'){
      check('home',function(){go('home');return /Your Card Today/i.test(visibleText()) && /Daily Reading/i.test(visibleText());});
      check('daily-reading',function(){go('daily');return /Daily Reading/i.test(visibleText()) && /Lucky Number/i.test(visibleText());});
      check('tarot-menu',function(){go('tarot');return /Card of The Day/i.test(visibleText()) && /3 Card Reading/i.test(visibleText());});
      check('horoscope-list',function(){go('horoscope');return document.querySelectorAll('.zodiac').length===12 && /Pisces/i.test(visibleText());});
      check('compatibility',function(){go('compatibility');return /Compatibility/i.test(visibleText()) && /Match/i.test(visibleText());});
      check('profile',function(){go('profile');return /My Readings/i.test(visibleText()) && /Birth Profile/i.test(visibleText());});
      check('history',function(){go('history');return /My Readings/i.test(visibleText());});
      try{go('home');}catch(e){}
    }
    var pass=report.length>0 && report.every(function(x){return x.pass;});
    window.__ORACLY_QC_RESULT={pass:pass,report:report,at:new Date().toISOString()};
    return window.__ORACLY_QC_RESULT;
  };
  window.addEventListener('error',function(ev){window.__ORACLY_LAST_ERROR=String(ev.message||'Unknown runtime error');});
  setTimeout(startupGuard,700);
})();