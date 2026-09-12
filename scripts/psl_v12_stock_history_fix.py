from pathlib import Path

p = Path('apps/papa-sauce-lab/index.html')
s = p.read_text(encoding='utf-8')

if 'PSL_V12_STOCK_HISTORY_FIX' in s:
    print('PSL v12 stock/history fix already present')
    raise SystemExit(0)

style = r'''
<style id="PSL_V12_STOCK_HISTORY_STYLE">
.psl12-stock-summary{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;padding:4px 0 14px;margin-bottom:2px;border-bottom:1px solid #eee8e1}
.psl12-stock-kpi{background:linear-gradient(180deg,#fffaf0,#fff);border:1px solid #eadcc4;border-radius:13px;padding:10px 8px;text-align:center}
.psl12-stock-kpi span{display:block;font-size:9px;font-weight:850;letter-spacing:.55px;color:#8c774f;text-transform:uppercase;margin-bottom:4px}
.psl12-stock-kpi b{font-size:18px;color:#211f20}
.psl12-stock-hint{font-size:9px;color:#9a835c;font-weight:800;letter-spacing:.35px;margin-top:7px}
.psl12-stock-line{display:grid;grid-template-columns:minmax(0,1fr) 52px 52px 52px;gap:4px;align-items:center;padding:10px 0;border-bottom:1px solid #eee9e3;font-size:11px}
.psl12-stock-line.head{font-size:9px;font-weight:850;color:#857a73;text-transform:uppercase;letter-spacing:.4px}
.psl12-stock-line strong{text-align:right;font-size:13px}
.psl12-stock-line .plus{color:#16724a;text-align:right}.psl12-stock-line .minus{color:#b4233f;text-align:right}.psl12-stock-line .open{text-align:right;color:#6f737a}
.psl12-move-qty{font-size:15px;font-weight:850;min-width:44px;text-align:right}
.psl12-move-qty.in{color:#16724a}.psl12-move-qty.out{color:#b4233f}
@media(max-width:380px){.psl12-stock-line{grid-template-columns:minmax(0,1fr) 44px 44px 44px;font-size:10px}}
</style>
'''

js = r'''
<script id="PSL_V12_STOCK_HISTORY_FIX">
(() => {
  const esc12=v=>String(v??'').replace(/[&<>\"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;'}[c]));
  const rp12=n=>'Rp '+Number(n||0).toLocaleString('id-ID');
  const date12=()=>document.getElementById('operationalDateV5')?.value || new Date().toISOString().slice(0,10);
  const prev12=d=>{const x=new Date(d+'T12:00:00');x.setDate(x.getDate()-1);return x.toISOString().slice(0,10)};
  let historyFilter12='all';

  async function allMoves12(){try{return await dbGetAll('stock_movements')}catch(e){console.error(e);return []}}
  async function levels12(date){
    const levels={};(PRODUCTS||[]).forEach(p=>levels[p]=0);
    (await allMoves12()).filter(x=>x&&x.date<=date&&x.status!=='CORRECTED').forEach(x=>{
      if(!(x.product in levels))levels[x.product]=0;
      levels[x.product]+=x.type==='restock'?Number(x.qty||0):-Number(x.qty||0);
    });
    return levels;
  }
  async function dayMoves12(date){return (await allMoves12()).filter(x=>x&&x.date===date&&x.status!=='CORRECTED')}
  const editable12=t=>t&&t.__kind==='tx'&&t.status!=='CORRECTED'&&(t.type==='sales'||t.type==='expense');

  UI.renderHistory=async function(filter='all',search=''){
    historyFilter12=filter||historyFilter12||'all';
    const [tx,moves]=await Promise.all([Transactions.getAll(),allMoves12()]);
    let rows=[...(tx||[]).map(x=>({...x,__kind:'tx'})),...(moves||[]).map(x=>({...x,__kind:'stock'}))];
    if(historyFilter12!=='all') rows=rows.filter(x=>x.type===historyFilter12);
    const q=String(search||'').trim().toLowerCase();
    if(q) rows=rows.filter(x=>JSON.stringify(x).toLowerCase().includes(q));
    rows.sort((a,b)=>String(b.datetime||'').localeCompare(String(a.datetime||'')));
    const el=document.getElementById('historyList');if(!el)return;
    if(!rows.length){el.innerHTML='<div class="empty-state"><div class="icon">📭</div><p>Belum ada riwayat</p></div>';return;}
    el.innerHTML=rows.slice(0,150).map(x=>{
      if(x.__kind==='stock'){
        const incoming=x.type==='restock';
        const badges=(x.isCorrection?' <span class="badge badge-orange">KOREKSI</span>':'')+(x.smartSale?' <span class="badge badge-blue">AUTO SALE</span>':'')+(x.status==='CORRECTED'?' <span class="badge badge-red">CORRECTED</span>':'');
        return `<div class="transaction-item"><div class="transaction-info"><div class="transaction-type">${incoming?'📦 Restock':'📤 Stock Keluar'} · ${esc12(x.product)}${badges}</div><div class="transaction-meta">Tanggal bisnis ${esc12(x.date)} · audit ${x.datetime?new Date(x.datetime).toLocaleString('id-ID'):'-'} · ${esc12(x.note||'')}</div></div><div class="psl12-move-qty ${incoming?'in':'out'}">${incoming?'+':'-'}${Number(x.qty||0)}</div></div>`;
      }
      const label=x.type==='sales'?'💰 Sales':x.type==='expense'?'📝 Pengeluaran':x.type==='hb_initial'?'🏁 HB Awal':x.type==='hb_add'?'➕ Tambah HB':esc12(x.type||'Transaksi');
      const edit=editable12(x)?`<button class="btn btn-secondary btn-sm" style="padding:7px 9px" onclick="UI.showCorrection('${esc12(x.id)}')">✏️ Edit</button>`:'';
      return `<div class="transaction-item"><div class="transaction-info"><div class="transaction-type">${label}${x.status==='CORRECTED'?' <span class="badge badge-red">CORRECTED</span>':''}</div><div class="transaction-meta">Tanggal bisnis ${esc12(x.date)} · audit ${x.datetime?new Date(x.datetime).toLocaleString('id-ID'):'-'} · ${esc12(x.note||'')}</div>${x.correctionReason?`<div class="correction-reason">Koreksi: ${esc12(x.correctionReason)}</div>`:''}</div><div style="display:flex;align-items:center;gap:8px"><div class="transaction-amount">${rp12(x.amount)}</div>${edit}</div></div>`;
    }).join('');
  };

  UI.filterHistory=function(filter,btn){
    historyFilter12=filter||'all';
    document.querySelectorAll('.filter-btn').forEach(b=>b.classList.remove('active'));
    btn?.classList.add('active');
    const q=document.getElementById('historySearch')?.value||'';
    UI.renderHistory(historyFilter12,q);
  };
  UI.searchHistory=function(){UI.renderHistory(historyFilter12,document.getElementById('historySearch')?.value||'')};

  UI.renderStock=async function(){
    const date=date12(),levels=await levels12(date),moves=await dayMoves12(date);
    const total=Object.values(levels).reduce((a,x)=>a+Number(x||0),0);
    const inQty=moves.filter(x=>x.type==='restock').reduce((a,x)=>a+Number(x.qty||0),0);
    const outQty=moves.filter(x=>x.type==='stockout').reduce((a,x)=>a+Number(x.qty||0),0);
    const sl=document.getElementById('stockLevels');
    if(sl)sl.innerHTML=`<div class="psl12-stock-summary"><div class="psl12-stock-kpi"><span>Total Stock</span><b>${total}</b></div><div class="psl12-stock-kpi"><span>Restock</span><b>+${inQty}</b></div><div class="psl12-stock-kpi"><span>Keluar</span><b>-${outQty}</b></div></div>`+(PRODUCTS||[]).map(p=>`<div class="stock-item"><div class="stock-name">${esc12(p)}</div><div class="stock-qty ${Number(levels[p]||0)<10?'low':'ok'}">${Number(levels[p]||0)}</div></div>`).join('');
    const sh=document.getElementById('stockHistory');
    if(sh)sh.innerHTML=moves.length?moves.slice().sort((a,b)=>String(b.datetime||'').localeCompare(String(a.datetime||''))).map(x=>`<div class="transaction-item"><div><div class="transaction-type">${x.type==='restock'?'📦':'📤'} ${esc12(x.product)}${x.isCorrection?' <span class="badge badge-orange">KOREKSI</span>':''}${x.smartSale?' <span class="badge badge-blue">AUTO SALE</span>':''}</div><div class="transaction-meta">${esc12(x.date)} · ${x.datetime?new Date(x.datetime).toLocaleString('id-ID'):'-'} · ${esc12(x.note||'')}</div></div><div class="psl12-move-qty ${x.type==='restock'?'in':'out'}">${x.type==='restock'?'+':'-'}${Number(x.qty||0)}</div></div>`).join(''):'<div class="empty-state">Belum ada pergerakan stock tanggal ini.</div>';
  };

  const oldDrill12=UI.drillDown?.bind(UI);
  UI.drillDown=async function(type){
    if(type!=='totalStock')return oldDrill12?oldDrill12(type):undefined;
    const date=date12(),open=await levels12(prev12(date)),end=await levels12(date),moves=await dayMoves12(date);
    const total=Object.values(end).reduce((a,x)=>a+Number(x||0),0);
    const inQty=moves.filter(x=>x.type==='restock').reduce((a,x)=>a+Number(x.qty||0),0);
    const outQty=moves.filter(x=>x.type==='stockout').reduce((a,x)=>a+Number(x.qty||0),0);
    const rows=(PRODUCTS||[]).map(p=>{
      const pin=moves.filter(x=>x.product===p&&x.type==='restock').reduce((a,x)=>a+Number(x.qty||0),0);
      const pout=moves.filter(x=>x.product===p&&x.type==='stockout').reduce((a,x)=>a+Number(x.qty||0),0);
      return `<div class="psl12-stock-line"><span>${esc12(p)}</span><span class="open">${Number(open[p]||0)}</span><span class="plus">+${pin}</span><strong>${Number(end[p]||0)}</strong></div>`;
    }).join('');
    document.getElementById('detailTitle').textContent='Rincian Stock · '+date;
    document.getElementById('detailContent').innerHTML=`<div class="psl12-stock-summary"><div class="psl12-stock-kpi"><span>Total</span><b>${total}</b></div><div class="psl12-stock-kpi"><span>Restock</span><b>+${inQty}</b></div><div class="psl12-stock-kpi"><span>Keluar</span><b>-${outQty}</b></div></div><div class="psl12-stock-line head"><span>Produk</span><span>Awal</span><span>Masuk</span><span>Akhir</span></div>${rows}`;
    UI.showModal('detail');
  };

  function hint12(){
    const v=document.getElementById('cardTotalStock');
    const card=v?.closest('.card');
    if(card&&!card.querySelector('.psl12-stock-hint'))card.insertAdjacentHTML('beforeend','<div class="psl12-stock-hint">KETUK UNTUK RINCIAN STOK</div>');
  }
  let tries=0;const timer=setInterval(async()=>{tries++;if(db&&document.getElementById('operationalDateV5')){clearInterval(timer);hint12();await UI.renderStock();await UI.renderHistory(historyFilter12,document.getElementById('historySearch')?.value||'');}else if(tries>=50)clearInterval(timer);},100);
  console.log('PSL v12 stock/history fix active');
})();
</script>
'''

if '</head>' not in s or '</body>' not in s:
    raise SystemExit('invalid html skeleton')
s = s.replace('</head>', style + '\n</head>', 1)
s = s.replace('</body>', js + '\n</body>', 1)
p.write_text(s, encoding='utf-8')
print('patched PSL v12 stock/history fix')
