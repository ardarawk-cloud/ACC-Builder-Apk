from pathlib import Path
import re

p = Path('apps/papa-sauce-lab/index.html')
s = p.read_text()

new_js = r'''<script id="PSL_V6_PRO_POLISH_JS">
(() => {
  let done = false;
  function polish(){
    try {
      const h=document.querySelector('.header-title');
      if(h && h.textContent!=='Papa Sauce Lab') h.textContent='Papa Sauce Lab';

      const logo=document.querySelector('.logo');
      if(logo && !logo.querySelector('.psl-safe-logo')){
        logo.innerHTML='<div class="psl-safe-logo" style="width:42px;height:42px;border-radius:13px;background:#fff7ea;color:#a9122e;display:flex;align-items:center;justify-content:center;font-weight:900;font-size:14px;letter-spacing:.5px;border:1px solid rgba(255,255,255,.45)">PSL</div>';
      }

      const hs=document.querySelector('.header-subtitle');
      if(hs && !document.querySelector('.psl-pro-version')){
        const v=document.createElement('div');
        v.className='psl-pro-version';
        v.textContent='ACCOUNTING · DAILY REPORT';
        hs.insertAdjacentElement('afterend',v);
      }

      const dash=document.getElementById('tab-dashboard');
      if(dash && !document.getElementById('pslProDashLabel')){
        const label=document.createElement('div');
        label.id='pslProDashLabel';
        label.className='psl-pro-section';
        label.textContent='Ringkasan Operasional';
        const grid=document.getElementById('dashboardCards');
        if(grid) grid.insertAdjacentElement('beforebegin',label);
      }

      const op=document.getElementById('opBoxV5');
      if(op && !document.getElementById('pslOpChip')){
        const chip=document.createElement('span');
        chip.id='pslOpChip';
        chip.className='psl-pro-chip';
        chip.textContent='LEDGER AKTIF';
        const lab=op.querySelector('.form-label');
        if(lab) lab.insertAdjacentElement('afterend',chip);
      }

      document.querySelectorAll('.quick-action').forEach(b=>{
        const label=b.textContent.trim().replace(/\s+/g,' ');
        if(b.getAttribute('aria-label')!==label) b.setAttribute('aria-label',label);
      });
      document.querySelectorAll('.card').forEach(c=>{
        if(c.getAttribute('role')!=='group') c.setAttribute('role','group');
      });

      if(document.getElementById('opBoxV5')) done=true;
    } catch(e) {
      console.error('PSL polish error', e);
    }
  }

  function boot(){
    polish();
    let tries=0;
    const timer=setInterval(()=>{
      polish();
      tries++;
      if(done || tries>=20) clearInterval(timer);
    },150);
  }

  if(document.readyState==='loading') document.addEventListener('DOMContentLoaded',boot,{once:true});
  else boot();
})();
</script>'''

pat = re.compile(r'<script id="PSL_V6_PRO_POLISH_JS">[\s\S]*?</script>')
if not pat.search(s):
    raise SystemExit('v6 polish JS block not found')
s = pat.sub(new_js, s, count=1)

# Safety: remove any remaining MutationObserver in the polish block/path to prevent main-thread mutation loops.
if 'const obs=new MutationObserver' in s:
    raise SystemExit('unsafe observer still present')

p.write_text(s)
print('patched PSL v7 freeze/logo hotfix')
