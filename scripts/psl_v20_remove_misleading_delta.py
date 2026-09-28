from pathlib import Path

p=Path('apps/papa-sauce-lab/index.html')
s=p.read_text(encoding='utf-8')

if 'PSL_V20_REMOVE_MISLEADING_DELTA' in s:
    print('PSL v20 already applied')
    raise SystemExit(0)

old = '''<div class="psl15-grid"><div class="psl15-kpi"><span>Total Sales</span><b>${rp15(m.totalSales)}</b></div><div class="psl15-kpi"><span>Total Pengeluaran</span><b class="psl15-red">${rp15(m.totalOut)}</b></div><div class="psl15-kpi"><span>Total Produksi</span><b class="psl15-green">${m.production}</b></div><div class="psl15-kpi"><span>Total Stock Out</span><b class="psl15-red">${m.stockOut}</b></div></div>
      <div class="report-row"><span>Cash</span><b>${rp15(m.channels.cash)}</b></div><div class="report-row"><span>QRIS</span><b>${rp15(m.channels.qris)}</b></div><div class="report-row"><span>Gojek</span><b>${rp15(m.channels.gojek)}</b></div><div class="report-row"><span>Grab</span><b>${rp15(m.channels.grab)}</b></div>
      <div class="report-row"><span>Selisih Sales - Pengeluaran</span><b class="${m.net<0?'psl15-red':'psl15-green'}">${rp15(m.net)}</b></div><div class="report-row"><span>Jumlah Entri Sales</span><b>${m.salesCount}</b></div><div class="report-row"><span>Jumlah Entri Pengeluaran</span><b>${m.expenseCount}</b></div>'''

new = '''<div class="psl15-grid"><div class="psl15-kpi"><span>Sales Lab</span><b>${rp15(m.totalSales)}</b></div><div class="psl15-kpi"><span>Pengeluaran Lab</span><b class="psl15-red">${rp15(m.totalOut)}</b></div><div class="psl15-kpi"><span>Total Produksi</span><b class="psl15-green">${m.production}</b></div><div class="psl15-kpi"><span>Total Stock Out</span><b class="psl15-red">${m.stockOut}</b></div></div>
      <div class="report-row"><span>Cash</span><b>${rp15(m.channels.cash)}</b></div><div class="report-row"><span>QRIS</span><b>${rp15(m.channels.qris)}</b></div><div class="report-row"><span>Gojek</span><b>${rp15(m.channels.gojek)}</b></div><div class="report-row"><span>Grab</span><b>${rp15(m.channels.grab)}</b></div>
      <div class="report-row"><span>Jumlah Entri Sales Lab</span><b>${m.salesCount}</b></div><div class="report-row"><span>Jumlah Entri Pengeluaran Lab</span><b>${m.expenseCount}</b></div>'''

if old not in s:
    raise SystemExit('target v15 report block not found')
s=s.replace(old,new,1)

# Keep v18 cleanup compatibility but remove the misleading phrase entirely from runtime source.
s=s.replace("if(label==='Selisih Sales - Pengeluaran')row.remove();","",1)

marker = r'''
<script id="PSL_V20_REMOVE_MISLEADING_DELTA">
(() => {
  const clean20=()=>{
    const root=document.getElementById('reportContent');
    if(!root)return;
    root.querySelectorAll('.report-row').forEach(row=>{
      const label=(row.querySelector('span')?.textContent||'').trim().toLowerCase();
      if(label.includes('sales') && label.includes('pengeluaran') && label.includes('selisih')) row.remove();
    });
  };
  const wrap=name=>{
    const old=Reports[name]?.bind(Reports);
    if(!old)return;
    Reports[name]=async function(){await old(); clean20();};
  };
  ['generate','generateWeekly','generateMonthly','generateAll'].forEach(wrap);
  clean20();
  console.log('PSL v20 misleading delta removed');
})();
</script>
'''
s=s.replace('</body>',marker+'\n</body>',1)
p.write_text(s,encoding='utf-8')
print('patched PSL v20')
