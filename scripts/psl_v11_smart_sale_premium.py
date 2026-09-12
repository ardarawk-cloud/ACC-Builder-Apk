from pathlib import Path

p = Path('apps/papa-sauce-lab/index.html')
s = p.read_text(encoding='utf-8')

if 'PSL_V11_SMART_SALE_PREMIUM' in s:
    print('PSL v11 smart sale premium already present')
    raise SystemExit(0)

style = r'''
<style id="PSL_V11_PREMIUM_STYLE">
:root{
  --lux-burgundy:#761329;
  --lux-burgundy-2:#a11634;
  --lux-gold:#b99755;
  --lux-gold-soft:#e8d7b1;
  --lux-ivory:#fffaf1;
  --lux-paper:#fffdf9;
  --lux-ink:#1b1b1d;
  --lux-muted:#717178;
  --lux-line:#ece7df;
  --lux-shadow:0 12px 34px rgba(58,25,32,.09);
}
body{background:linear-gradient(180deg,#f6f3f1 0%,#f7f7f8 42%,#f1f2f4 100%)!important;color:var(--lux-ink)!important}
.header{background:linear-gradient(135deg,#681022 0%,#8f1730 55%,#a91b39 100%)!important;border-bottom:1px solid rgba(232,215,177,.45)!important;box-shadow:0 10px 28px rgba(74,15,29,.24)!important}
.logo{background:linear-gradient(145deg,#fffdf7,#f6ead4)!important;border:1px solid rgba(185,151,85,.48)!important;box-shadow:0 6px 18px rgba(0,0,0,.18)!important}
.psl-safe-logo{color:#8b1730!important}
.header-title{font-weight:850!important;letter-spacing:-.25px!important}.header-subtitle{letter-spacing:.4px!important;text-transform:uppercase!important;opacity:.78!important}
.role-badge{border-color:rgba(232,215,177,.48)!important;background:rgba(255,255,255,.1)!important}
#opBoxV5{background:linear-gradient(180deg,#fffdf9,#fffaf4)!important;border:1px solid #eadfcf!important;box-shadow:var(--lux-shadow)!important}
#opBoxV5:before{background:linear-gradient(#9b1834,#c39a55)!important;width:3px!important}
#opBadgeV5{border:1px solid rgba(185,151,85,.22)!important}
#pslFastInput{background:linear-gradient(160deg,#fffefb 0%,#fffaf2 100%)!important;border:1px solid #e6d8be!important;border-radius:20px!important;padding:16px!important;box-shadow:0 14px 36px rgba(72,34,42,.10)!important;position:relative;overflow:hidden}
#pslFastInput:before{content:"";position:absolute;left:0;right:0;top:0;height:3px;background:linear-gradient(90deg,#751329,#b31d3f 58%,#c6a461)}
#pslFastInput .fi-title{font-size:15px!important;font-weight:900!important;letter-spacing:-.2px!important;color:#251d20!important}
#pslFastInput .fi-chip{background:#f5ead4!important;color:#7b1730!important;border:1px solid #e4cfaa!important;letter-spacing:.65px!important}
#pslFastInput .fi-amount{background:#fff!important;border:1px solid #ded8d2!important;border-radius:14px!important;box-shadow:inset 0 1px 0 rgba(255,255,255,.8)!important}
#pslFastInput .fi-amount:focus,#pslFastInput .fi-select:focus,#pslFastInput .fi-note:focus{border-color:#a98243!important;box-shadow:0 0 0 3px rgba(185,151,85,.12)!important;outline:none!important}
.psl11-sale-meta{display:grid;grid-template-columns:minmax(0,1fr) 86px;gap:8px;margin-top:9px}
.psl11-sale-meta select,.psl11-sale-meta input{height:44px;border:1px solid #ded8d2;border-radius:12px;background:#fff;padding:0 11px;font-size:12px;color:#272327;min-width:0}
.psl11-sale-label{font-size:9px;font-weight:850;letter-spacing:.65px;color:#8a6a35;text-transform:uppercase;margin:11px 0 6px}
#pslFastInput .fi-channels{gap:8px!important}
#pslFastInput .fi-channel{border:1px solid #e1d9cf!important;background:rgba(255,255,255,.9)!important;border-radius:13px!important;min-height:52px!important;box-shadow:0 4px 12px rgba(47,37,39,.045)!important;font-weight:850!important}
#pslFastInput .fi-channel:active{background:#f7ede1!important;transform:scale(.97)}
.psl11-auto-note{margin-top:8px;padding:8px 10px;border:1px solid #ebddc6;border-radius:11px;background:#fff9ef;color:#6f5b3e;font-size:10px;line-height:1.35}
.psl11-auto-note b{color:#79152d}
#pslFastInput .fi-exp{border-top:1px solid #e8dfd4!important;margin-top:14px!important;padding-top:14px!important}
#pslFastInput .fi-exp-btn{background:linear-gradient(135deg,#222327,#313238)!important;border:1px solid #3a3b40!important;border-radius:12px!important;box-shadow:0 7px 16px rgba(30,31,34,.12)!important}
.quick-actions{gap:10px!important}.quick-action{background:linear-gradient(180deg,#fff,#fffdf9)!important;border:1px solid #e9e3dc!important;border-radius:17px!important;box-shadow:0 8px 20px rgba(48,40,42,.055)!important;font-weight:760!important}.quick-action:active{background:#fff6eb!important}
.card{background:linear-gradient(180deg,#fff,#fffdfb)!important;border:1px solid #ece7e1!important;border-radius:18px!important;box-shadow:0 8px 24px rgba(38,34,36,.055)!important}.card-title{color:#7b7276!important;font-weight:800!important;letter-spacing:.65px!important}.card-value{letter-spacing:-.5px!important}
.btn-primary{background:linear-gradient(135deg,#7a142b,#ad1c3c)!important;box-shadow:0 8px 18px rgba(122,20,43,.16)!important}.btn-success{background:linear-gradient(135deg,#11633e,#16814f)!important}.btn{border-radius:12px!important}
.form-input,.form-select,.form-textarea{border:1px solid #dedde1!important;background:#fff!important;border-radius:13px!important}.form-input:focus,.form-select:focus,.form-textarea:focus{border-color:#a98243!important;box-shadow:0 0 0 3px rgba(185,151,85,.11)!important;outline:none!important}
.modal{background:rgba(24,18,20,.56)!important;backdrop-filter:blur(5px)}.modal-content{border-radius:26px 26px 0 0!important;border-top:1px solid #e4d4b8!important;box-shadow:0 -16px 48px rgba(32,19,24,.16)!important;background:linear-gradient(180deg,#fffefc,#fff)!important}.modal-content:before{content:"";display:block;width:48px;height:4px;border-radius:99px;background:#d7d2cf;margin:-5px auto 18px}
.close-btn{background:#f3f1ef!important;color:#555!important}
.transaction-item{border-bottom:1px solid #eee9e4!important}.transaction-amount{letter-spacing:-.25px!important}
bottom-nav{background:rgba(255,253,250,.96)!important;border-top:1px solid #e9e3da!important;box-shadow:0 -10px 28px rgba(44,33,37,.07)!important;backdrop-filter:blur(14px)}.nav-item.active{color:#8b1731!important;background:#fff1f3!important;border-radius:14px!important}.nav-item{font-weight:730!important}
@media (max-width:380px){.psl11-sale-meta{grid-template-columns:minmax(0,1fr) 78px}}
</style>
'''

js = r'''
<script id="PSL_V11_SMART_SALE_PREMIUM">
(() => {
  const uid11=()=>Date.now().toString(36)+'-'+Math.random().toString(36).slice(2,9);
  const date11=()=>document.getElementById('operationalDateV5')?.value || new Date().toISOString().slice(0,10);
  const products11=()=>{ try{return Array.from(PRODUCTS||[]);}catch(e){return ['Ayam Woku','Cumi','Tongkol','Babi Rica','Babi Kecap','Sup Brenebone','Sup Grangasem'];} };
  const esc11=v=>String(v??'').replace(/[&<>\"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;'}[c]));
  function toast11(msg){
    let t=document.getElementById('pslFastToast');
    if(!t){t=document.createElement('div');t.id='pslFastToast';t.className='psl-fast-toast';document.body.appendChild(t);}
    t.textContent=msg;t.classList.add('show');clearTimeout(t._tm);t._tm=setTimeout(()=>t.classList.remove('show'),1450);
  }
  async function closed11(date){
    try{return (await dbGetAll('snapshots')).some(x=>x&&x.date===date&&x.type!=='period_end');}catch(e){console.error(e);return false;}
  }
  async function stock11(product,date){
    const all=await dbGetAll('stock_movements');
    return all.filter(x=>x&&x.product===product&&x.date<=date&&x.status!=='CORRECTED').reduce((n,x)=>n+(x.type==='restock'?Number(x.qty||0):-Number(x.qty||0)),0);
  }
  async function refresh11(){
    try{await UI.updateDashboard();await UI.renderTodayTransactions();await UI.renderStock();await UI.renderHistory();}catch(e){console.error('PSL v11 refresh',e);}
  }
  function atomicSale11(sale,stockMove){
    return new Promise((resolve,reject)=>{
      try{
        const stores=stockMove?['transactions','stock_movements']:['transactions'];
        const tx=db.transaction(stores,'readwrite');
        tx.objectStore('transactions').put(sale);
        if(stockMove)tx.objectStore('stock_movements').put(stockMove);
        tx.oncomplete=()=>resolve();tx.onerror=()=>reject(tx.error||new Error('DB transaction failed'));tx.onabort=()=>reject(tx.error||new Error('DB transaction aborted'));
      }catch(e){reject(e);}
    });
  }
  async function saveSmart11(channel){
    const amountEl=document.getElementById('fastSalesAmount');
    const productEl=document.getElementById('smartSaleProduct11');
    const qtyEl=document.getElementById('smartSaleQty11');
    const amount=Number(amountEl?.value||0),product=productEl?.value||'',qty=Number(qtyEl?.value||0),date=date11();
    if(!amount||amount<=0){toast11('Isi nominal sales dulu');amountEl?.focus();return;}
    if(await closed11(date)){toast11('Tanggal sudah CLOSED');return;}
    if(product && (!qty||qty<=0)){toast11('Isi jumlah produk');qtyEl?.focus();return;}
    if(product){
      const available=await stock11(product,date);
      if(available<qty && !confirm('Stock '+product+' tercatat '+available+'. Tetap simpan sales '+qty+'?'))return;
    }
    const now=new Date().toISOString(),saleId=uid11(),stockId=product?uid11():null;
    const sale={id:saleId,date,datetime:now,type:'sales',category:null,amount,channel,note:product?('Smart Sale · '+product+' x'+qty):'Smart Sale',user:currentRole,status:'ACTIVE',linkedStockMovementId:stockId,smartSale:true};
    const move=product?{id:stockId,date,datetime:now,product,qty,type:'stockout',note:'Auto dari Sales · '+channel.toUpperCase()+' · Rp '+amount.toLocaleString('id-ID'),user:currentRole,status:'ACTIVE',linkedSaleId:saleId,smartSale:true}:null;
    try{
      await atomicSale11(sale,move);
      amountEl.value='';if(qtyEl)qtyEl.value=product?'1':'';
      await refresh11();
      if(navigator.vibrate)navigator.vibrate(24);
      toast11(product?(channel.toUpperCase()+' + stock -'+qty+' tersimpan'):(channel.toUpperCase()+' tersimpan'));
      setTimeout(()=>amountEl?.focus(),40);
    }catch(e){console.error(e);toast11('Gagal menyimpan Smart Sale');}
  }
  async function saveExpense11(){
    const a=document.getElementById('fastExpenseAmount'),c=document.getElementById('fastExpenseCategory'),n=document.getElementById('fastExpenseNote');
    const amount=Number(a?.value||0),category=c?.value||'operasional',note=(n?.value||'').trim(),date=date11();
    if(!amount||amount<=0){toast11('Isi nominal pengeluaran');a?.focus();return;}
    if(await closed11(date)){toast11('Tanggal sudah CLOSED');return;}
    try{await dbPut('transactions',{id:uid11(),date,datetime:new Date().toISOString(),type:'expense',category,amount,channel:null,note:note||category,user:currentRole,status:'ACTIVE'});a.value='';n.value='';await refresh11();if(navigator.vibrate)navigator.vibrate(20);toast11('Pengeluaran tersimpan');}catch(e){console.error(e);toast11('Gagal menyimpan');}
  }
  function upgrade11(){
    const box=document.getElementById('pslFastInput');if(!box||box.dataset.v11==='1')return !!box;
    box.dataset.v11='1';
    const opts=products11().map(p=>'<option value="'+esc11(p)+'">'+esc11(p)+'</option>').join('');
    box.innerHTML=`<div class="fi-head"><div class="fi-title">Smart Sale</div><span class="fi-chip">SALE + STOCK</span></div>
      <input id="fastSalesAmount" class="fi-amount" type="number" inputmode="numeric" min="0" step="1000" placeholder="Nominal sales">
      <div class="psl11-sale-label">Produk terjual · opsional</div>
      <div class="psl11-sale-meta"><select id="smartSaleProduct11"><option value="">Tanpa produk / sales saja</option>${opts}</select><input id="smartSaleQty11" type="number" inputmode="numeric" min="1" step="1" value="1" placeholder="Qty"></div>
      <div class="fi-channels"><button class="fi-channel fi-cash" data-v11ch="cash">CASH</button><button class="fi-channel fi-qris" data-v11ch="qris">QRIS</button><button class="fi-channel fi-gojek" data-v11ch="gojek">GOJEK</button><button class="fi-channel fi-grab" data-v11ch="grab">GRAB</button></div>
      <div class="psl11-auto-note"><b>Sekali simpan.</b> Kalau pilih produk, pemasukan tercatat dan stock otomatis keluar. Kalau tanpa produk, hanya pemasukan.</div>
      <div class="fi-exp"><input id="fastExpenseAmount" class="fi-amount" style="height:44px;font-size:16px" type="number" inputmode="numeric" min="0" step="1000" placeholder="Pengeluaran"><select id="fastExpenseCategory" class="fi-select"><option value="bahan baku">Bahan baku</option><option value="packaging">Packaging</option><option value="operasional" selected>Operasional</option><option value="transport">Transport</option><option value="kebersihan">Kebersihan</option><option value="peralatan">Peralatan</option><option value="lainnya">Lainnya</option></select><input id="fastExpenseNote" class="fi-note" style="grid-column:1/-1" placeholder="Catatan (opsional)"><button id="fastExpenseSave" class="fi-exp-btn">SIMPAN PENGELUARAN</button></div>`;
    box.querySelectorAll('[data-v11ch]').forEach(b=>b.addEventListener('click',()=>saveSmart11(b.dataset.v11ch)));
    document.getElementById('fastExpenseSave')?.addEventListener('click',saveExpense11);
    document.getElementById('fastSalesAmount')?.addEventListener('keydown',e=>{if(e.key==='Enter')saveSmart11('cash');});
    const qa=[...document.querySelectorAll('.quick-action')].find(b=>/Input Sales/i.test(b.textContent||''));
    if(qa){qa.title='Smart Sale: pemasukan + potong stock otomatis';}
    return true;
  }
  let tries=0;const timer=setInterval(()=>{tries++;if(upgrade11()||tries>=50)clearInterval(timer);},100);
})();
</script>
'''

if '</head>' not in s or '</body>' not in s:
    raise SystemExit('invalid HTML skeleton')
s = s.replace('</head>', style + '\n</head>', 1)
s = s.replace('</body>', js + '\n</body>', 1)
p.write_text(s, encoding='utf-8')
print('patched PSL v11 smart sale premium')
