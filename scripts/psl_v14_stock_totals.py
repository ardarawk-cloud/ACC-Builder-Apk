from pathlib import Path

p=Path('apps/papa-sauce-lab/index.html')
s=p.read_text(encoding='utf-8')

if 'PSL_V14_STOCK_TOTALS' in s:
    print('PSL v14 stock totals already present')
    raise SystemExit(0)

style=r'''
<style id="PSL_V14_STOCK_TOTALS_STYLE">
.psl14-metric-card{cursor:pointer}
.psl14-metric-card .card-title{display:flex;align-items:center;justify-content:space-between;gap:8px}
.psl14-mini{font-size:9px;font-weight:850;letter-spacing:.45px;color:#8d5d65;background:#fff3f5;border:1px solid #f0d5db;border-radius:999px;padding:4px 7px}
.psl14-production{color:#16724a!important}
.psl14-stockout{color:#b4233f!important}
.psl14-report-box{margin:12px 0 6px;padding:12px;border:1px solid #eadfe1;border-radius:14px;background:linear-gradient(180deg,#fffafb,#fff)}
.psl14-report-title{font-size:11px;font-weight:900;letter-spacing:.65px;text-transform:uppercase;color:#8e1f38;margin-bottom:8px}
.psl14-product-row{display:grid;grid-template-columns:minmax(0,1fr) 62px 62px;gap:8px;padding:8px 0;border-bottom:1px solid #eee8e8;font-size:12px;align-items:center}
.psl14-product-row:last-child{border-bottom:0}.psl14-product-row b{text-align:right}.psl14-in{color:#16724a}.psl14-out{color:#b4233f}
</style>
'''

js=r'''
<script id="PSL_V14_STOCK_TOTALS">
(() => {
  const esc14=v=>String(v??'').replace(/[&<>\"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;'}[c]));
  const opDate14=()=>document.getElementById('operationalDateV5')?.value || new Date().toISOString().slice(0,10);
  async function moves14(date){
    const all=await dbGetAll('stock_movements');
    return (all||[]).filter(x=>x&&x.date===date&&x.status!=='CORRECTED');
  }
  async function metrics14(date){
    const moves=await moves14(date), per={};
    (PRODUCTS||[]).forEach(p=>per[p]={production:0,out:0});
    for(const x of moves){
      if(!per[x.product])per[x.product]={production:0,out:0};
      if(x.type==='restock')per[x.product].production+=Number(x.qty||0);
      if(x.type==='stockout')per[x.product].out+=Number(x.qty||0);
    }
    const production=moves.filter(x=>x.type==='restock').reduce((a,x)=>a+Number(x.qty||0),0);
    const out=moves.filter(x=>x.type==='stockout').reduce((a,x)=>a+Number(x.qty||0),0);
    return {moves,per,production,out};
  }
  function ensureCards14(){
    if(document.getElementById('cardTotalProduction14'))return;
    const stock=document.getElementById('cardTotalStock');
    const grid=stock?.closest('.grid-2');
    if(!grid)return;
    grid.insertAdjacentHTML('beforeend',`
      <div class="card psl14-metric-card" onclick="UI.drillDown('totalProduction14')">
        <div class="card-title">Total Produksi <span class="psl14-mini">HARI TERPILIH</span></div>
        <div class="card-value psl14-production" id="cardTotalProduction14">0</div>
        <div class="card-sub">Total stock masuk / produksi</div>
      </div>
      <div class="card psl14-metric-card" onclick="UI.drillDown('totalStockOut14')">
        <div class="card-title">Total Stock Out <span class="psl14-mini">HARI TERPILIH</span></div>
        <div class="card-value psl14-stockout" id="cardTotalStockOut14">0</div>
        <div class="card-sub">Total stock keluar</div>
      </div>`);
  }
  async function renderCards14(){
    ensureCards14();
    const m=await metrics14(opDate14());
    const a=document.getElementById('cardTotalProduction14'),b=document.getElementById('cardTotalStockOut14');
    if(a)a.textContent=m.production;if(b)b.textContent=m.out;
    return m;
  }

  const oldUpdate14=UI.updateDashboard?.bind(UI);
  UI.updateDashboard=async function(){
    if(oldUpdate14)await oldUpdate14();
    await renderCards14();
  };

  const oldStock14=UI.renderStock?.bind(UI);
  UI.renderStock=async function(){
    if(oldStock14)await oldStock14();
    const m=await metrics14(opDate14());
    const summary=document.querySelector('#stockLevels .psl12-stock-summary');
    if(summary){
      summary.innerHTML=`<div class="psl12-stock-kpi"><span>Total Stock</span><b>${document.getElementById('cardTotalStock')?.textContent||0}</b></div><div class="psl12-stock-kpi"><span>Total Produksi</span><b class="psl14-production">+${m.production}</b></div><div class="psl12-stock-kpi"><span>Total Stock Out</span><b class="psl14-stockout">-${m.out}</b></div>`;
    }
  };

  const oldDrill14=UI.drillDown?.bind(UI);
  UI.drillDown=async function(type){
    if(type!=='totalProduction14'&&type!=='totalStockOut14')return oldDrill14?oldDrill14(type):undefined;
    const date=opDate14(),m=await metrics14(date),isProd=type==='totalProduction14';
    const rows=Object.entries(m.per).filter(([,v])=>(isProd?v.production:v.out)>0).map(([p,v])=>`<div class="report-row"><span>${esc14(p)}</span><b class="${isProd?'psl14-in':'psl14-out'}">${isProd?'+':'-'}${isProd?v.production:v.out}</b></div>`).join('')||'<div class="empty-state">Belum ada data.</div>';
    document.getElementById('detailTitle').textContent=(isProd?'Total Produksi':'Total Stock Out')+' · '+date;
    document.getElementById('detailContent').innerHTML=`<div class="report-row"><span><b>${isProd?'TOTAL PRODUKSI':'TOTAL STOCK OUT'}</b></span><b class="${isProd?'psl14-in':'psl14-out'}">${isProd?m.production:m.out}</b></div>${rows}`;
    UI.showModal('detail');
  };

  const oldReport14=Reports.generate?.bind(Reports);
  Reports.generate=async function(){
    if(oldReport14)await oldReport14();
    const date=document.getElementById('reportDate')?.value||opDate14(),m=await metrics14(date),root=document.getElementById('reportContent');
    if(!root)return;
    root.querySelectorAll('.psl14-report-box').forEach(x=>x.remove());
    const rows=Object.entries(m.per).filter(([,v])=>v.production||v.out).map(([p,v])=>`<div class="psl14-product-row"><span>${esc14(p)}</span><b class="psl14-in">+${v.production}</b><b class="psl14-out">-${v.out}</b></div>`).join('')||'<div class="empty-state">Belum ada pergerakan stock.</div>';
    const box=document.createElement('div');box.className='psl14-report-box';box.innerHTML=`<div class="psl14-report-title">Ringkasan Produksi & Stock Out</div><div class="report-row"><span>Total Produksi</span><b class="psl14-in">${m.production}</b></div><div class="report-row"><span>Total Stock Out</span><b class="psl14-out">${m.out}</b></div><div class="psl14-product-row" style="font-size:9px;font-weight:900;text-transform:uppercase;color:#777"><span>Produk</span><b>Produksi</b><b>Out</b></div>${rows}`;
    const section=root.querySelector('.report-section');
    if(section)section.appendChild(box);else root.prepend(box);
  };

  let tries=0;const timer=setInterval(async()=>{tries++;if(db&&document.getElementById('cardTotalStock')){clearInterval(timer);await renderCards14();await UI.renderStock();}else if(tries>=50)clearInterval(timer);},100);
  console.log('PSL v14 stock totals active');
})();
</script>
'''

if '</head>' not in s or '</body>' not in s:
    raise SystemExit('invalid html skeleton')
s=s.replace('</head>',style+'\n</head>',1)
s=s.replace('</body>',js+'\n</body>',1)
p.write_text(s,encoding='utf-8')
print('patched PSL v14 stock totals')
