from pathlib import Path

p = Path('apps/papa-sauce-lab/index.html')
s = p.read_text(encoding='utf-8')

if 'PSL_V8_FAST_INPUT' in s:
    print('PSL v8 fast input already present')
    raise SystemExit(0)

style = r'''
<style id="PSL_V8_FAST_INPUT_STYLE">
#pslFastInput{background:#fff;border:1px solid #e7e8ec;border-radius:16px;padding:14px;margin:0 0 14px;box-shadow:0 5px 18px rgba(25,28,32,.055)}
#pslFastInput .fi-head{display:flex;align-items:center;justify-content:space-between;gap:8px;margin-bottom:10px}
#pslFastInput .fi-title{font-size:13px;font-weight:850;color:#20242a}.fi-chip{font-size:9px;font-weight:850;letter-spacing:.45px;padding:5px 8px;border-radius:999px;background:#fff0f3;color:#a9122e}
#pslFastInput .fi-row{display:grid;grid-template-columns:1fr auto;gap:8px;align-items:center}.fi-amount{width:100%;height:48px;border:1px solid #dfe1e6;border-radius:13px;padding:0 13px;font-size:20px;font-weight:800;background:#fbfbfc;color:#1d2126;outline:none}
#pslFastInput .fi-amount:focus{border-color:#b91735;box-shadow:0 0 0 3px rgba(185,23,53,.08)}
#pslFastInput .fi-channels{display:grid;grid-template-columns:repeat(4,1fr);gap:7px;margin-top:9px}.fi-channel{min-height:50px;border:1px solid #e3e4e8;background:#fff;border-radius:12px;font-size:11px;font-weight:800;color:#30343a}.fi-channel:active{transform:scale(.97);background:#fff3f5}.fi-cash{border-color:#b8dec9}.fi-qris{border-color:#c7d8ff}.fi-gojek{border-color:#c8ead2}.fi-grab{border-color:#c8ead2}
#pslFastInput .fi-exp{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:12px;padding-top:12px;border-top:1px solid #ececf0}.fi-select,.fi-note{height:44px;border:1px solid #e0e1e5;border-radius:11px;padding:0 10px;background:#fbfbfc;font-size:12px}.fi-exp-btn{grid-column:1/-1;height:44px;border:0;border-radius:11px;background:#272b30;color:#fff;font-weight:800;font-size:12px}.fi-help{font-size:10px;color:#777d85;margin-top:8px;line-height:1.4}.psl-fast-toast{position:fixed;left:50%;bottom:88px;transform:translateX(-50%);z-index:9999;background:#1f2429;color:#fff;padding:10px 14px;border-radius:999px;font-size:12px;font-weight:750;box-shadow:0 8px 24px rgba(0,0,0,.2);opacity:0;transition:opacity .18s;pointer-events:none;white-space:nowrap}.psl-fast-toast.show{opacity:1}
</style>
'''

js = r'''
<script id="PSL_V8_FAST_INPUT">
(() => {
  const uidFast=()=>Date.now().toString(36)+'-'+Math.random().toString(36).slice(2,8);
  const todayFast=()=>new Date().toISOString().slice(0,10);
  function opDateFast(){ return document.getElementById('operationalDateV5')?.value || todayFast(); }
  function toastFast(msg){
    let t=document.getElementById('pslFastToast');
    if(!t){t=document.createElement('div');t.id='pslFastToast';t.className='psl-fast-toast';document.body.appendChild(t);}
    t.textContent=msg;t.classList.add('show');clearTimeout(t._tm);t._tm=setTimeout(()=>t.classList.remove('show'),1200);
  }
  async function closedFast(date){
    try{return (await dbGetAll('snapshots')).some(x=>x && x.date===date && x.type!=='period_end');}catch(e){console.error(e);return false;}
  }
  async function refreshFast(){
    try{await UI.updateDashboard();await UI.renderTodayTransactions();await UI.renderHistory();}catch(e){console.error('fast refresh',e);}
  }
  async function saveFastSale(channel){
    const el=document.getElementById('fastSalesAmount');
    const amount=Number(el?.value||0), date=opDateFast();
    if(!amount || amount<=0){toastFast('Isi nominal dulu');el?.focus();return;}
    if(await closedFast(date)){toastFast('Tanggal sudah CLOSED');return;}
    try{
      await dbPut('transactions',{id:uidFast(),date,datetime:new Date().toISOString(),type:'sales',category:null,amount,channel,note:'Input Cepat',user:currentRole,status:'ACTIVE'});
      el.value='';
      await refreshFast();
      if(navigator.vibrate) navigator.vibrate(20);
      toastFast(channel.toUpperCase()+' tersimpan');
      setTimeout(()=>el.focus(),40);
    }catch(e){console.error(e);toastFast('Gagal menyimpan');}
  }
  async function saveFastExpense(){
    const a=document.getElementById('fastExpenseAmount'),c=document.getElementById('fastExpenseCategory'),n=document.getElementById('fastExpenseNote');
    const amount=Number(a?.value||0),category=c?.value||'operasional',note=(n?.value||'').trim(),date=opDateFast();
    if(!amount || amount<=0){toastFast('Isi nominal pengeluaran');a?.focus();return;}
    if(await closedFast(date)){toastFast('Tanggal sudah CLOSED');return;}
    try{
      await dbPut('transactions',{id:uidFast(),date,datetime:new Date().toISOString(),type:'expense',category,amount,channel:null,note:note||category,user:currentRole,status:'ACTIVE'});
      a.value='';n.value='';
      await refreshFast();
      if(navigator.vibrate) navigator.vibrate(20);
      toastFast('Pengeluaran tersimpan');
      setTimeout(()=>a.focus(),40);
    }catch(e){console.error(e);toastFast('Gagal menyimpan');}
  }
  function injectFast(){
    if(document.getElementById('pslFastInput')) return true;
    const anchor=document.getElementById('opBoxV5');
    if(!anchor) return false;
    const box=document.createElement('section');box.id='pslFastInput';
    box.innerHTML=`<div class="fi-head"><div class="fi-title">Input Cepat</div><span class="fi-chip">1 NOMINAL · 1 TAP</span></div>
      <input id="fastSalesAmount" class="fi-amount" type="number" inputmode="numeric" min="0" step="1000" placeholder="Nominal sales">
      <div class="fi-channels">
        <button class="fi-channel fi-cash" data-ch="cash">CASH</button>
        <button class="fi-channel fi-qris" data-ch="qris">QRIS</button>
        <button class="fi-channel fi-gojek" data-ch="gojek">GOJEK</button>
        <button class="fi-channel fi-grab" data-ch="grab">GRAB</button>
      </div>
      <div class="fi-exp">
        <input id="fastExpenseAmount" class="fi-amount" style="height:44px;font-size:16px" type="number" inputmode="numeric" min="0" step="1000" placeholder="Pengeluaran">
        <select id="fastExpenseCategory" class="fi-select"><option value="bahan baku">Bahan baku</option><option value="packaging">Packaging</option><option value="operasional" selected>Operasional</option><option value="transport">Transport</option><option value="kebersihan">Kebersihan</option><option value="peralatan">Peralatan</option><option value="lainnya">Lainnya</option></select>
        <input id="fastExpenseNote" class="fi-note" style="grid-column:1/-1" placeholder="Catatan (opsional)">
        <button id="fastExpenseSave" class="fi-exp-btn">SIMPAN PENGELUARAN</button>
      </div>
      <div class="fi-help">Mengikuti Tanggal Operasional di atas. Sales: ketik nominal lalu tap CASH/QRIS/GOJEK/GRAB. Tidak perlu buka modal.</div>`;
    anchor.insertAdjacentElement('afterend',box);
    box.querySelectorAll('[data-ch]').forEach(b=>b.addEventListener('click',()=>saveFastSale(b.dataset.ch)));
    document.getElementById('fastExpenseSave').addEventListener('click',saveFastExpense);
    document.getElementById('fastSalesAmount').addEventListener('keydown',e=>{if(e.key==='Enter') saveFastSale('cash');});
    return true;
  }
  let tries=0;const timer=setInterval(()=>{tries++;if(injectFast()||tries>=40)clearInterval(timer);},100);
})();
</script>
'''

if '</head>' not in s or '</body>' not in s:
    raise SystemExit('invalid HTML skeleton')
s = s.replace('</head>', style + '\n</head>', 1)
s = s.replace('</body>', js + '\n</body>', 1)
p.write_text(s, encoding='utf-8')
print('patched PSL v8 fast input')
