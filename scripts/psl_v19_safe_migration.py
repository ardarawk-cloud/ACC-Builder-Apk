from pathlib import Path

p=Path('apps/papa-sauce-lab/index.html')
s=p.read_text(encoding='utf-8')

if 'PSL_V19_SAFE_MIGRATION' in s:
    print('PSL v19 safe migration already present')
    raise SystemExit(0)

style=r'''
<style id="PSL_V19_SAFE_MIGRATION_STYLE">
.psl19-backup-note{margin:10px 0;padding:10px 12px;border-radius:12px;background:#eef7ff;border:1px solid #cfe6fb;color:#36566f;font-size:11px;line-height:1.5}
.psl19-import-ok{margin-top:8px;font-size:11px;color:#267447;font-weight:800}
</style>
'''

js=r'''
<script id="PSL_V19_SAFE_MIGRATION">
(() => {
  const getAll19=async store=>{
    try{return await dbGetAll(store)}catch(e){return []}
  };
  const putMany19=async(store,arr)=>{
    if(!Array.isArray(arr))return 0;
    let n=0;
    for(const x of arr){if(x){await dbPut(store,x);n++;}}
    return n;
  };

  Export.jsonBackup=async function(){
    const [transactions,stock,snapshots,settings,corrections,periods]=await Promise.all([
      getAll19('transactions'),getAll19('stock_movements'),getAll19('snapshots'),
      getAll19('settings'),getAll19('corrections'),getAll19('periods')
    ]);
    const data={
      schema:'PapaSauceLabBackup',
      schemaVersion:2,
      exportDate:new Date().toISOString(),
      transactions,stock,snapshots,settings,corrections,periods
    };
    const blob=new Blob([JSON.stringify(data,null,2)],{type:'application/json'});
    const url=URL.createObjectURL(blob);
    const a=document.createElement('a');
    a.href=url;
    a.download=`papa-sauce-lab-backup-${new Date().toISOString().slice(0,10)}.json`;
    a.click();
    setTimeout(()=>URL.revokeObjectURL(url),1000);
  };

  Export.jsonImport=async function(file){
    if(!file)return;
    let data;
    try{data=JSON.parse(await file.text())}catch(e){return alert('File backup JSON tidak valid.');}
    const tx=Array.isArray(data.transactions)?data.transactions:[];
    const stock=Array.isArray(data.stock)?data.stock:(Array.isArray(data.stock_movements)?data.stock_movements:[]);
    const snaps=Array.isArray(data.snapshots)?data.snapshots:[];
    if(!tx.length&&!stock.length&&!snaps.length)return alert('Backup tidak berisi data Papa Sauce Lab yang dikenali.');
    if(!confirm(`Import backup?\nTransaksi: ${tx.length}\nStock movement: ${stock.length}\nSnapshot: ${snaps.length}`))return;

    const counts={};
    counts.transactions=await putMany19('transactions',tx);
    counts.stock=await putMany19('stock_movements',stock);
    counts.snapshots=await putMany19('snapshots',snaps);
    counts.settings=await putMany19('settings',data.settings||[]);
    counts.corrections=await putMany19('corrections',data.corrections||[]);
    counts.periods=await putMany19('periods',data.periods||[]);

    if(typeof refreshV5==='function')await refreshV5();
    else {
      if(UI.updateDashboard)await UI.updateDashboard();
      if(UI.renderTodayTransactions)await UI.renderTodayTransactions();
      if(UI.renderStock)await UI.renderStock();
      if(UI.renderHistory)await UI.renderHistory();
    }
    alert(`Import selesai.\nTransaksi: ${counts.transactions}\nStock: ${counts.stock}\nSnapshot: ${counts.snapshots}\nData lama v17/v18 kompatibel.`);
  };

  Import.json=async function(event){
    const file=event?.target?.files?.[0];
    if(file)await Export.jsonImport(file);
    if(event?.target)event.target.value='';
  };

  UI.showSettings=function(){
    document.getElementById('detailTitle').textContent='Pengaturan & Backup';
    document.getElementById('detailContent').innerHTML=`
      <div class="form-group">
        <label class="form-label">Role</label>
        <select class="form-select" id="settingRole" onchange="Settings.setRole(this.value)">
          <option value="OWNER" ${currentRole==='OWNER'?'selected':''}>OWNER</option>
          <option value="STAFF" ${currentRole==='STAFF'?'selected':''}>STAFF</option>
        </select>
      </div>
      <div class="psl19-backup-note"><b>Backup aman sebelum pindah APK.</b><br>Export menyimpan transaksi, stock movement, snapshot tutup hari, settings, correction audit, dan periode. Import juga kompatibel dengan backup JSON lama yang hanya berisi transaksi + stock + snapshot.</div>
      <button class="btn btn-secondary btn-block" onclick="Export.jsonBackup()" style="margin-bottom:8px">📥 Export JSON Backup</button>
      <input type="file" accept=".json,application/json" onchange="Import.json(event)" style="display:none" id="importFile19">
      <button class="btn btn-primary btn-block" onclick="document.getElementById('importFile19').click()">📤 Import JSON Backup</button>
      <div class="psl19-import-ok">PSL v19 · Safe Migration Ready</div>
    `;
    UI.showModal('detail');
  };

  console.log('PSL v19 safe migration active');
})();
</script>
'''

s=s.replace('</head>',style+'\n</head>',1)
s=s.replace('</body>',js+'\n</body>',1)
p.write_text(s,encoding='utf-8')
print('patched PSL v19 safe migration')
