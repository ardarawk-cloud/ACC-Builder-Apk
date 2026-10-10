from pathlib import Path

p=Path('apps/papa-sauce-lab/index.html')
s=p.read_text(encoding='utf-8')

if 'PSL_V29_RESET_CLOSED_RANGE' in s:
    print('PSL v29 reset closed range already present')
    raise SystemExit(0)

patch=r'''
<style id="PSL_V29_RESET_CLOSED_RANGE_STYLE">
.psl29-reset-box{margin-top:12px;padding:12px;border:1px solid #efcaca;border-radius:12px;background:#fff7f7}
.psl29-reset-box b{display:block;margin-bottom:5px;color:#a5162d}
.psl29-reset-box p{font-size:11px;line-height:1.45;color:#6d6060;margin:0 0 9px}
</style>
<script id="PSL_V29_RESET_CLOSED_RANGE">
(function(){
  function del29(store,key){
    return new Promise((resolve,reject)=>{
      try{
        const tx=db.transaction(store,'readwrite');
        const req=tx.objectStore(store).delete(key);
        req.onsuccess=()=>resolve(true);
        req.onerror=()=>reject(req.error);
      }catch(e){reject(e)}
    });
  }
  function inRange29(d,a,b){return !!d && d>=a && d<=b}
  function uid29(){return 'reset-'+Date.now().toString(36)+'-'+Math.random().toString(36).slice(2,8)}
  function today29(){return PSLLocalDate()}

  async function collect29(start,end){
    const [transactions,stock,snapshots,periods]=await Promise.all([
      dbGetAll('transactions'),
      dbGetAll('stock_movements'),
      dbGetAll('snapshots'),
      dbGetAll('periods')
    ]);
    return {
      transactions:(transactions||[]).filter(x=>x&&inRange29(x.date,start,end)),
      stock:(stock||[]).filter(x=>x&&inRange29(x.date,start,end)),
      snapshots:(snapshots||[]).filter(x=>x&&inRange29(x.date,start,end)),
      periods:(periods||[]).filter(x=>x&&(
        inRange29(x.date,start,end)||
        inRange29(x.start,start,end)||
        inRange29(x.end,start,end)||
        inRange29(x.startDate,start,end)||
        inRange29(x.endDate,start,end)
      ))
    };
  }

  async function hasLater29(end){
    const [transactions,stock,snapshots]=await Promise.all([
      dbGetAll('transactions'),
      dbGetAll('stock_movements'),
      dbGetAll('snapshots')
    ]);
    const rows=[...(transactions||[]),...(stock||[]),...(snapshots||[])];
    return rows.some(x=>x&&x.date&&x.date>end&&x.date<=today29());
  }

  async function reset29(){
    if(currentRole!=='OWNER')return alert('Hanya OWNER yang bisa reset hari.');
    const op=document.getElementById('operationalDateV5')?.value||today29();
    let start=prompt('Reset mulai tanggal (YYYY-MM-DD):',op);
    if(start===null)return;
    start=String(start).trim();
    if(!/^\d{4}-\d{2}-\d{2}$/.test(start))return alert('Format tanggal salah');
    let end=prompt('Reset sampai tanggal (YYYY-MM-DD):',start);
    if(end===null)return;
    end=String(end).trim();
    if(!/^\d{4}-\d{2}-\d{2}$/.test(end))return alert('Format tanggal salah');
    if(start>end)return alert('Tanggal awal harus <= tanggal akhir');
    if(end>today29())return alert('Tanggal masa depan tidak boleh');
    if(await hasLater29(end))return alert('Ada data setelah '+end+'. Reset harus mencakup sampai hari terakhir yang sudah terisi supaya rantai HB/stock tidak putus.');

    const data=await collect29(start,end);
    const total=data.transactions.length+data.stock.length+data.snapshots.length+data.periods.length;
    if(!total)return alert('Tidak ada data pada '+start+' s/d '+end+'.');

    const msg=
      'RESET '+start+' s/d '+end+'?\n\n'+
      'Transaksi: '+data.transactions.length+'\n'+
      'Stock movement: '+data.stock.length+'\n'+
      'Snapshot tutup hari: '+data.snapshots.length+'\n'+
      'Period record: '+data.periods.length+'\n\n'+
      'Tanggal sebelum '+start+' TIDAK disentuh. Saldo/HB akan kembali mengikuti penutupan hari terakhir sebelum '+start+'.\n\n'+
      'Data yang direset diarsipkan ke audit corrections.';
    if(!confirm(msg))return;

    const confirmWord=prompt('Ketik RESET untuk lanjut:','');
    if(confirmWord!=='RESET')return alert('Dibatalkan.');

    const auditId=uid29();
    await dbPut('corrections',{
      id:auditId,
      action:'RESET_DATE_RANGE',
      startDate:start,
      endDate:end,
      time:new Date().toISOString(),
      user:currentRole,
      archive:data
    });

    for(const x of data.transactions)await del29('transactions',x.id);
    for(const x of data.stock)await del29('stock_movements',x.id);
    for(const x of data.snapshots)await del29('snapshots',x.id);
    for(const x of data.periods)await del29('periods',x.id);

    localStorage.setItem('PSL_OPERATIONAL_DATE_V27',start);
    const el=document.getElementById('operationalDateV5');
    if(el){
      el.value=start;
      el.dispatchEvent(new Event('change',{bubbles:true}));
    }

    try{
      if(UI.updateDashboard)await UI.updateDashboard();
      if(UI.renderTodayTransactions)await UI.renderTodayTransactions();
      if(UI.renderStock)await UI.renderStock();
      if(UI.renderHistory)await UI.renderHistory();
    }catch(e){console.error(e)}

    alert('Reset selesai. '+start+' s/d '+end+' sudah kosong. Mulai input ulang dari '+start+'.');
  }

  window.PSLReset29={run:reset29};

  function inject29(){
    const trans=document.querySelector('#tab-transaksi .card:first-child');
    if(!trans||document.getElementById('psl29ResetBox'))return;
    const box=document.createElement('div');
    box.id='psl29ResetBox';
    box.className='psl29-reset-box owner-only';
    box.innerHTML='<b>Reset Hari yang Salah</b><p>Untuk mengulang hari yang sudah terlanjur CLOSED. Tanggal sebelumnya tetap aman; data range yang direset diarsipkan untuk audit.</p><button class="btn btn-secondary btn-block" onclick="PSLReset29.run()">Reset Rentang Tanggal</button>';
    trans.appendChild(box);
  }

  let tries=0;
  const timer=setInterval(()=>{
    tries++;
    if(typeof db!=='undefined'&&db&&document.getElementById('operationalDateV5')){
      clearInterval(timer);inject29();
    }else if(tries>=60)clearInterval(timer);
  },100);

  console.log('PSL v29 reset closed range active');
})();
</script>
'''

if '</body>' not in s:
    raise SystemExit('invalid html')
s=s.replace('</body>',patch+'\n</body>',1)
p.write_text(s,encoding='utf-8')
print('patched PSL v29 reset closed range')
