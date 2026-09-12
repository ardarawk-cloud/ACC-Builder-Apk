from pathlib import Path

p = Path('apps/papa-sauce-lab/index.html')
s = p.read_text(encoding='utf-8')
marker = 'PSL_V6_PRO_POLISH'
if marker in s:
    print('polish already applied')
    raise SystemExit(0)

style = r'''
<style id="PSL_V6_PRO_POLISH">
:root{
  --psl-red:#a9122e;--psl-red-2:#c51f3e;--psl-red-soft:#fff1f3;
  --psl-cream:#fff9f0;--psl-bg:#f6f7f9;--psl-ink:#191c20;--psl-muted:#6c727a;
  --psl-line:#e8e9ed;--psl-green:#14804a;--psl-shadow:0 8px 24px rgba(22,27,35,.08);
}
html{background:var(--psl-bg)}
body{background:linear-gradient(180deg,#f7f8fa 0,#f4f5f7 100%);color:var(--psl-ink);font-family:Inter,-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Arial,sans-serif;padding-bottom:88px}
.header{background:linear-gradient(135deg,#981129 0%,#c51f3e 100%);padding:14px 16px 13px;box-shadow:0 6px 20px rgba(169,18,46,.22);border-bottom:1px solid rgba(255,255,255,.12)}
.header-left{gap:11px}.logo{width:42px;height:42px;border-radius:13px;overflow:hidden;box-shadow:0 4px 12px rgba(0,0,0,.18);background:#fff8ed}.logo>div{width:42px!important;height:42px!important;border-radius:13px!important;font-size:14px!important;letter-spacing:.5px}
.header-title{font-size:17px;font-weight:800;letter-spacing:-.2px}.header-subtitle{font-size:11px;opacity:.88;margin-top:1px}.header-actions{align-items:center}.icon-btn{width:38px;height:38px;background:rgba(255,255,255,.13);border:1px solid rgba(255,255,255,.2);backdrop-filter:blur(10px)}
.role-badge{font-size:10px;font-weight:800;letter-spacing:.4px;padding:5px 9px;border:1px solid rgba(255,255,255,.26);background:rgba(255,255,255,.14)!important;color:#fff!important}
.container{padding:14px 12px 24px;max-width:680px}
#opBoxV5{background:#fff!important;border:1px solid #eadde0!important;border-radius:16px!important;padding:14px!important;margin-bottom:14px!important;box-shadow:var(--psl-shadow)!important;position:relative;overflow:hidden}
#opBoxV5:before{content:"";position:absolute;left:0;top:0;bottom:0;width:4px;background:linear-gradient(#a9122e,#d93150)}
#opBoxV5 .form-label{font-size:11px;color:#7a2638;text-transform:uppercase;letter-spacing:.7px;font-weight:800;margin-bottom:7px}
#operationalDateV5{font-weight:750;color:#262a30;background:#fbfbfc;border:1px solid #dedfe4!important;height:44px}
#opBadgeV5{font-size:10px;font-weight:850;letter-spacing:.4px;white-space:nowrap;padding:6px 9px;border-radius:999px}
.quick-actions{grid-template-columns:repeat(4,minmax(0,1fr));gap:9px;margin-bottom:16px}
.quick-action{min-height:74px;border-radius:15px;padding:11px 6px;background:#fff;border:1px solid #ececef;box-shadow:0 4px 14px rgba(25,28,32,.055);font-size:10px;font-weight:720;color:#34383e;line-height:1.25}
.quick-action:active{background:#fff4f6;transform:translateY(1px)}
.quick-action.owner-only::after{top:5px;right:6px;font-size:9px;filter:saturate(.8)}
.grid-2{gap:10px}.card{border-radius:16px;border:1px solid var(--psl-line);box-shadow:0 5px 18px rgba(25,28,32,.055);padding:15px;margin-bottom:10px;background:#fff}
#dashboardCards .card{min-height:100px;position:relative;overflow:hidden;transition:transform .14s ease,box-shadow .14s ease}
#dashboardCards .card:active{transform:scale(.985)}
#dashboardCards .card:nth-child(8){grid-column:1/-1;background:linear-gradient(135deg,#fff 0%,#fff4f6 100%);border-color:#efcbd3;box-shadow:0 8px 24px rgba(169,18,46,.09)}
#dashboardCards .card:nth-child(8):before{content:"REGISTER";position:absolute;right:12px;top:11px;font-size:9px;letter-spacing:1.2px;color:#b31a37;font-weight:900;opacity:.7}
#dashboardCards .card:nth-child(8) .card-value{font-size:28px;color:#9f142f!important}
.card-title{font-size:10px;font-weight:800;letter-spacing:.8px;color:#7b8189;margin-bottom:7px}.card-value{font-size:20px;font-weight:820;letter-spacing:-.45px;color:#1f2328}.card-sub{font-size:10px;color:#8a9098}
.btn{border-radius:12px;min-height:44px;font-weight:760;letter-spacing:.1px;box-shadow:none}.btn-primary{background:linear-gradient(135deg,#a9122e,#c51f3e)}.btn-warning{background:#b86a10}.btn-success{background:#16804b}.btn-secondary{background:#f1f2f4;color:#2d3137}.btn-outline{border:1px solid #d82d4e;color:#aa1734}
.form-label{font-size:11px;font-weight:760;color:#656b73}.form-input,.form-select,.form-textarea{border:1px solid #dfe1e5;border-radius:12px;background:#fbfbfc;box-shadow:inset 0 1px 0 rgba(255,255,255,.7);font-size:15px}.form-input:focus,.form-select:focus,.form-textarea:focus{border-color:#c93451;box-shadow:0 0 0 3px rgba(197,31,62,.1);outline:none}.form-textarea{min-height:92px}
.channel-option{border:1px solid #dfe1e5;border-radius:12px;font-size:13px;font-weight:700}.channel-option.selected{border-color:#b71a37;background:#fff0f3;color:#9f142f;box-shadow:0 0 0 2px rgba(183,26,55,.07)}
.modal{background:rgba(12,15,20,.46);backdrop-filter:blur(3px)}.modal-content{border-radius:24px 24px 0 0;padding:21px 18px calc(20px + env(safe-area-inset-bottom));box-shadow:0 -18px 50px rgba(0,0,0,.16)}.modal-content:before{content:"";display:block;width:38px;height:4px;background:#d5d7db;border-radius:99px;margin:-8px auto 14px}.modal-title{font-size:18px;font-weight:820;letter-spacing:-.25px}.close-btn{background:#f2f3f5;color:#5d636b}
.transaction-item{padding:13px 4px;border-color:#eceef1}.transaction-type{font-size:13px;font-weight:760}.transaction-meta{font-size:10px;margin-top:3px}.transaction-amount{font-size:14px;font-weight:820}
.stock-item{padding:13px 4px;border-color:#eceef1}.stock-name{font-size:13px}.stock-qty{font-size:18px}
.report-row{padding:10px 0;border-color:#eceef1;font-size:13px}.report-label{color:#6c727a}.report-value{font-weight:780;color:#25292f}
.filter-bar{gap:7px}.filter-btn{border:1px solid #dfe1e5;background:#fff;padding:7px 13px;font-weight:700}.filter-btn.active{border-color:#b91b38;background:#fff0f3;color:#a31330}
.empty-state{padding:34px 18px;color:#8a9098}.empty-state .icon{font-size:38px}
bottom-nav{height:68px;padding:8px 4px calc(7px + env(safe-area-inset-bottom));background:rgba(255,255,255,.94);border-top:1px solid #e6e8eb;box-shadow:0 -8px 24px rgba(20,24,31,.08);backdrop-filter:blur(14px)}
.nav-item{min-width:58px;padding:4px 9px;border-radius:11px;font-size:9px;font-weight:740}.nav-item.active{color:#aa1734;background:#fff0f3}.nav-icon{font-size:19px}
.psl-pro-section{font-size:11px;font-weight:850;letter-spacing:.75px;text-transform:uppercase;color:#777d85;margin:4px 2px 9px}.psl-pro-version{font-size:9px;opacity:.72;margin-top:1px}.psl-pro-chip{display:inline-flex;align-items:center;gap:5px;padding:5px 8px;border-radius:999px;background:#eef8f2;color:#16693f;font-size:9px;font-weight:850;letter-spacing:.35px}.psl-pro-chip:before{content:"";width:6px;height:6px;border-radius:50%;background:#1e9b5b;box-shadow:0 0 0 3px rgba(30,155,91,.11)}
@media(max-width:390px){.quick-actions{grid-template-columns:repeat(4,1fr);gap:7px}.quick-action{font-size:9px;padding:10px 3px}.container{padding-left:10px;padding-right:10px}}
</style>
'''

script = r'''
<script id="PSL_V6_PRO_POLISH_JS">
(() => {
  function polish(){
    const h=document.querySelector('.header-title');
    if(h){h.textContent='Papa Sauce Lab';}
    const hs=document.querySelector('.header-subtitle');
    if(hs && !document.querySelector('.psl-pro-version')){
      const v=document.createElement('div');v.className='psl-pro-version';v.textContent='ACCOUNTING · DAILY REPORT';hs.insertAdjacentElement('afterend',v);
    }
    const dash=document.getElementById('tab-dashboard');
    if(dash && !document.getElementById('pslProDashLabel')){
      const label=document.createElement('div');label.id='pslProDashLabel';label.className='psl-pro-section';label.textContent='Ringkasan Operasional';
      const grid=document.getElementById('dashboardCards'); if(grid) grid.insertAdjacentElement('beforebegin',label);
    }
    const op=document.getElementById('opBoxV5');
    if(op && !document.getElementById('pslOpChip')){
      const chip=document.createElement('span');chip.id='pslOpChip';chip.className='psl-pro-chip';chip.textContent='LEDGER AKTIF';
      const lab=op.querySelector('.form-label');if(lab)lab.insertAdjacentElement('afterend',chip);
    }
    document.querySelectorAll('.quick-action').forEach((b,i)=>{b.setAttribute('aria-label',b.textContent.trim().replace(/\s+/g,' '));});
    document.querySelectorAll('.card').forEach(c=>c.setAttribute('role','group'));
  }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',()=>setTimeout(polish,180)); else setTimeout(polish,180);
  const obs=new MutationObserver(()=>polish());
  obs.observe(document.documentElement,{childList:true,subtree:true});
})();
</script>
'''

s = s.replace('</head>', style + '\n</head>')
s = s.replace('</body>', script + '\n</body>')
p.write_text(s, encoding='utf-8')
print('PSL v6 professional polish applied')
