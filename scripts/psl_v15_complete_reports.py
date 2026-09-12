from pathlib import Path

p=Path('apps/papa-sauce-lab/index.html')
s=p.read_text(encoding='utf-8')

if 'PSL_V15_COMPLETE_REPORTS' in s:
    print('PSL v15 complete reports already present')
    raise SystemExit(0)

style=r'''
<style id="PSL_V15_COMPLETE_REPORTS_STYLE">
.psl15-summary{margin-top:12px;padding:14px;border:1px solid #eadfe1;border-radius:16px;background:#fff}
.psl15-title{font-size:12px;font-weight:900;letter-spacing:.6px;text-transform:uppercase;color:#8f2039;margin-bottom:8px}
.psl15-sub{font-size:11px;color:#777;margin:-2px 0 10px}
.psl15-grid{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin:10px 0}
.psl15-kpi{border:1px solid #ece8e9;border-radius:12px;padding:10px;background:#fffafa}
.psl15-kpi span{display:block;font-size:10px;color:#777;text-transform:uppercase;font-weight:800;letter-spacing:.35px;margin-bottom:5px}
.psl15-kpi b{font-size:18px;color:#222}.psl15-green{color:#16724a!important}.psl15-red{color:#b4233f!important}
.psl15-table-head,.psl15-product-row{display:grid;grid-template-columns:minmax(0,1fr) 58px 58px 58px;gap:7px;align-items:center}
.psl15-table-head{font-size:9px;font-weight:900;text-transform:uppercase;color:#777;padding:8px 0;border-bottom:1px solid #e9e5e6}
.psl15-product-row{font-size:11px;padding:8px 0;border-bottom:1px solid #f0ecec}.psl15-product-row:last-child{border-bottom:0}
.psl15-table-head b,.psl15-product-row b{text-align:right}
.psl15-period-note{font-size:10px;color:#777;margin-top:6px;line-height:1.35}
@media(max-width:380px){.psl15-table-head,.psl15-product-row{grid-template-columns:minmax(0,1fr) 50px 50px 50px;gap:5px;font-size:10px}}
</style>
'''

js=r'''
<script id="PSL_V15_COMPLETE_REPORTS">
(() => {
  const rp15=n=>'Rp '+Number(n||0).toLocaleString('id-ID');
  const esc15=v=>String(v??'').replace(/[&<>\"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;'}[c]));
  const opDate15=()=>document.getElementById('operationalDateV5')?.value||new Date().toISOString().slice(0,10);
  const fmt15=d=>{const y=d.getFullYear(),m=String(d.getMonth()+1).padStart(2,'0'),day=String(d.getDate()).padStart(2,'0');return `${y}-${m}-${day}`};
  const inRange15=(d,a,b)=>d&&d>=a&&d<=b;
  async function all15(){return {tx:await dbGetAll('transactions'),mv:await dbGetAll('stock_movements')}}
  async function summary15(start,end){
    const {tx,mv}=await all15();
    const active=(tx||[]).filter(x=>x&&x.status==='ACTIVE'&&inRange15(x.date,start,end));
    const moves=(mv||[]).filter(x=>x&&x.status!=='CORRECTED'&&inRange15(x.date,start,end));
    const sales=active.filter(x=>x.type==='sales'),expenses=active.filter(x=>x.type==='expense');
    const totalSales=sales.reduce((a,x)=>a+Number(x.amount||0),0),totalOut=expenses.reduce((a,x)=>a+Number(x.amount||0),0);
    const channels={cash:0,qris:0,gojek:0,grab:0};sales.forEach(x=>{const c=String(x.channel||'').toLowerCase();if(c in channels)channels[c]+=Number(x.amount||0)});
    const products=new Set([...(window.PRODUCTS||[])]);(mv||[]).forEach(x=>x?.product&&products.add(x.product));
    const per={};for(const p of products)per[p]={production:0,out:0,opening:0,closing:0};
    const apply=(obj,x,sign)=>{if(!x?.product)return;if(!obj[x.product])obj[x.product]={production:0,out:0,opening:0,closing:0};const q=Number(x.qty||0);if(x.type==='restock')obj[x.product][sign]+=q;if(x.type==='stockout')obj[x.product][sign]-=q};
    for(const x of (mv||[]).filter(x=>x&&x.status!=='CORRECTED'&&x.date<start))apply(per,x,'opening');
    for(const x of (mv||[]).filter(x=>x&&x.status!=='CORRECTED'&&x.date<=end))apply(per,x,'closing');
    for(const x of moves){if(!per[x.product])per[x.product]={production:0,out:0,opening:0,closing:0};if(x.type==='restock')per[x.product].production+=Number(x.qty||0);if(x.type==='stockout')per[x.product].out+=Number(x.qty||0)}
    const production=moves.filter(x=>x.type==='restock').reduce((a,x)=>a+Number(x.qty||0),0),stockOut=moves.filter(x=>x.type==='stockout').reduce((a,x)=>a+Number(x.qty||0),0);
    const openingStock=Object.values(per).reduce((a,x)=>a+Number(x.opening||0),0),closingStock=Object.values(per).reduce((a,x)=>a+Number(x.closing||0),0);
    return {start,end,totalSales,totalOut,net:totalSales-totalOut,salesCount:sales.length,expenseCount:expenses.length,channels,production,stockOut,openingStock,closingStock,per};
  }
  function rows15(m){
    const list=Object.entries(m.per).filter(([,v])=>v.production||v.out||v.closing).sort((a,b)=>a[0].localeCompare(b[0]));
    if(!list.length)return '<div class="empty-state">Belum ada data stock.</div>';
    return `<div class="psl15-table-head"><span>Produk</span><b>Prod</b><b>Out</b><b>Sisa</b></div>`+list.map(([p,v])=>`<div class="psl15-product-row"><span>${esc15(p)}</span><b class="psl15-green">+${v.production}</b><b class="psl15-red">-${v.out}</b><b>${v.closing}</b></div>`).join('');
  }
  function render15(title,m){
    const root=document.getElementById('reportContent');if(!root)return;
    root.innerHTML=`<div class="psl15-summary"><div class="psl15-title">${esc15(title)}</div><div class="psl15-sub">${m.start} s/d ${m.end}</div>
      <div class="psl15-grid"><div class="psl15-kpi"><span>Total Sales</span><b>${rp15(m.totalSales)}</b></div><div class="psl15-kpi"><span>Total Pengeluaran</span><b class="psl15-red">${rp15(m.totalOut)}</b></div><div class="psl15-kpi"><span>Total Produksi</span><b class="psl15-green">${m.production}</b></div><div class="psl15-kpi"><span>Total Stock Out</span><b class="psl15-red">${m.stockOut}</b></div></div>
      <div class="report-row"><span>Cash</span><b>${rp15(m.channels.cash)}</b></div><div class="report-row"><span>QRIS</span><b>${rp15(m.channels.qris)}</b></div><div class="report-row"><span>Gojek</span><b>${rp15(m.channels.gojek)}</b></div><div class="report-row"><span>Grab</span><b>${rp15(m.channels.grab)}</b></div>
      <div class="report-row"><span>Selisih Sales - Pengeluaran</span><b class="${m.net<0?'psl15-red':'psl15-green'}">${rp15(m.net)}</b></div><div class="report-row"><span>Jumlah Entri Sales</span><b>${m.salesCount}</b></div><div class="report-row"><span>Jumlah Entri Pengeluaran</span><b>${m.expenseCount}</b></div>
      <div class="report-row"><span>Stock Awal Periode</span><b>${m.openingStock}</b></div><div class="report-row"><span>Total Produksi Periode</span><b class="psl15-green">+${m.production}</b></div><div class="report-row"><span>Total Stock Out Periode</span><b class="psl15-red">-${m.stockOut}</b></div><div class="report-row"><span>Stock Akhir Periode</span><b>${m.closingStock}</b></div>
      <div class="psl15-title" style="margin-top:14px">Rincian Produk</div>${rows15(m)}<div class="psl15-period-note">Jumlah Transaksi generik dihapus. Laporan sekarang memisahkan entri Sales, Pengeluaran, Produksi, Stock Out, serta saldo stock.</div></div>`;
  }
  function installButtons15(){
    const tab=document.getElementById('tab-laporan');if(!tab)return;
    const date=document.getElementById('reportDate');if(date&&!date.dataset.psl15){date.dataset.psl15='1';date.value=opDate15();const title=date.closest('.card')?.querySelector('.card-title');if(title)title.textContent='Periode Acuan · otomatis ikut Tanggal Operasional';}
    const wrap=date?.parentElement?.querySelector('div[style*="display:flex"]');if(wrap&&!document.getElementById('psl15DailyBtn')){wrap.style.flexWrap='wrap';wrap.insertAdjacentHTML('afterbegin','<button id="psl15DailyBtn" class="btn btn-secondary btn-sm" onclick="Reports.generate()">Harian</button>');wrap.insertAdjacentHTML('beforeend','<button id="psl15AllBtn" class="btn btn-secondary btn-sm" onclick="Reports.generateAll()">Semua Data</button>');}
  }
  Reports.generateWeekly=async function(){const anchor=document.getElementById('reportDate')?.value||opDate15(),d=new Date(anchor+'T12:00:00'),day=d.getDay(),delta=day===0?-6:1-day,startD=new Date(d);startD.setDate(d.getDate()+delta);const endD=new Date(startD);endD.setDate(startD.getDate()+6);const start=fmt15(startD),end=fmt15(endD);render15('Laporan Mingguan',await summary15(start,end));};
  Reports.generateMonthly=async function(){const anchor=document.getElementById('reportDate')?.value||opDate15(),[y,m]=anchor.split('-').map(Number),start=`${y}-${String(m).padStart(2,'0')}-01`,end=fmt15(new Date(y,m,0,12));render15('Laporan Bulanan '+anchor.slice(0,7),await summary15(start,end));};
  Reports.generateAll=async function(){const {tx,mv}=await all15(),dates=[...(tx||[]).map(x=>x?.date),...(mv||[]).map(x=>x?.date)].filter(Boolean).sort(),end=opDate15(),start=dates[0]||end;render15('Rangkuman Semua Data',await summary15(start,end));};
  const oldGenerate15=Reports.generate?.bind(Reports);Reports.generate=async function(){installButtons15();const d=document.getElementById('reportDate');if(d&&!d.value)d.value=opDate15();if(oldGenerate15)await oldGenerate15();};
  let tries=0;const timer=setInterval(()=>{tries++;if(document.getElementById('tab-laporan')){clearInterval(timer);installButtons15();}else if(tries>40)clearInterval(timer)},100);
  console.log('PSL v15 complete reports active');
})();
</script>
'''

if '</head>' not in s or '</body>' not in s:
    raise SystemExit('invalid html skeleton')
s=s.replace('</head>',style+'\n</head>',1)
s=s.replace('</body>',js+'\n</body>',1)
p.write_text(s,encoding='utf-8')
print('patched PSL v15 complete reports')
