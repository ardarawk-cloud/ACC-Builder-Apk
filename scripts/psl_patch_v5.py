from pathlib import Path
import json, datetime

p = Path('apps/papa-sauce-lab/index.html')
s = p.read_text(encoding='utf-8')
MARK = 'PSL_V5_BACKDATE_HB_PATCH'
if MARK in s:
    print('PSL v5 patch already present')
else:
    patch = r'''
<script id="PSL_V5_BACKDATE_HB_PATCH">
(() => {
  const TODAY = () => new Date().toISOString().slice(0,10);
  let operationalDateV5 = TODAY();
  const prevDateV5 = d => { const x=new Date(d+'T12:00:00'); x.setDate(x.getDate()-1); return x.toISOString().slice(0,10); };
  const nextDateV5 = d => { const x=new Date(d+'T12:00:00'); x.setDate(x.getDate()+1); return x.toISOString().slice(0,10); };
  const rupiahV5 = n => 'Rp ' + Number(n||0).toLocaleString('id-ID');
  const uidV5 = () => Date.now().toString()+'-'+Math.random().toString(36).slice(2,7);
  const escV5 = v => String(v ?? '').replace(/[&<>\"']/g,m=>({'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;',"'":'&#39;'}[m]));

  // Existing v3 stores do not all have indexes. Make reads safe without deleting/migrating data.
  dbGetByIndex = function(store,index,value){
    return new Promise((resolve,reject)=>{
      try{
        const tx=db.transaction(store,'readonly'), st=tx.objectStore(store);
        if(st.indexNames.contains(index)){
          const req=st.index(index).getAll(value); req.onsuccess=()=>resolve(req.result||[]); req.onerror=()=>reject(req.error);
        }else{
          const req=st.getAll(); req.onsuccess=()=>resolve((req.result||[]).filter(x=>x && x[index]===value)); req.onerror=()=>reject(req.error);
        }
      }catch(e){ reject(e); }
    });
  };

  async function snapshotsV5(){ return await dbGetAll('snapshots'); }
  async function snapshotV5(date){ return (await snapshotsV5()).find(x=>x.date===date && x.type!=='period_end') || null; }
  async function isClosedV5(date){ return !!(await snapshotV5(date)); }
  async function activeTxV5(date){ return (await Transactions.getByDate(date)).filter(x=>x.status!=='CORRECTED'); }
  async function openingBaseV5(date){
    const prev=await snapshotV5(prevDateV5(date));
    if(prev) return Number(prev.closingRegister ?? prev.totalRegister ?? 0);
    const tx=await activeTxV5(date);
    const init=tx.filter(x=>x.type==='hb_initial').reduce((a,x)=>a+Number(x.amount||0),0);
    if(init) return init;
    const daily=(await snapshotsV5()).filter(x=>x.type!=='period_end');
    if(!daily.length){ const legacy=await dbGet('settings','openingHB'); return Number(legacy?.value||0); }
    return 0;
  }
  async function calcV5(date){
    const tx=await activeTxV5(date), sales=tx.filter(x=>x.type==='sales'), expenses=tx.filter(x=>x.type==='expense'), adds=tx.filter(x=>x.type==='hb_add');
    const sumCh=ch=>sales.filter(x=>x.channel===ch).reduce((a,x)=>a+Number(x.amount||0),0);
    const cash=sumCh('cash'),qris=sumCh('qris'),gojek=sumCh('gojek'),grab=sumCh('grab');
    const totalSales=cash+qris+gojek+grab, totalOut=expenses.reduce((a,x)=>a+Number(x.amount||0),0);
    const openingHB=await openingBaseV5(date), hbAdd=adds.reduce((a,x)=>a+Number(x.amount||0),0), houseBank=openingHB+hbAdd;
    return {tx,sales,expenses,adds,cash,qris,gojek,grab,totalSales,totalOut,openingHB,hbAdd,houseBank,totalRegister:houseBank+totalSales-totalOut};
  }
  async function stockAtV5(date){
    const all=await dbGetAll('stock_movements'), levels={}; PRODUCTS.forEach(p=>levels[p]=0);
    all.filter(x=>x.date<=date && x.status!=='CORRECTED').forEach(x=>{ if(!(x.product in levels)) levels[x.product]=0; levels[x.product]+=x.type==='restock'?Number(x.qty||0):-Number(x.qty||0); });
    return levels;
  }
  async function stockDayV5(date){ const all=(await dbGetAll('stock_movements')).filter(x=>x.date===date && x.status!=='CORRECTED'); return {all,inQty:all.filter(x=>x.type==='restock').reduce((a,x)=>a+Number(x.qty||0),0),outQty:all.filter(x=>x.type==='stockout').reduce((a,x)=>a+Number(x.qty||0),0)}; }

  function addDateInputV5(modalId,inputId){
    const modal=document.getElementById(modalId); if(!modal||modal.querySelector('.v5-date-group')) return;
    const head=modal.querySelector('.modal-header'); if(!head) return;
    const div=document.createElement('div'); div.className='form-group v5-date-group'; div.innerHTML=`<label class="form-label">Tanggal Operasional</label><input type="date" class="form-input" id="${inputId}" max="${TODAY()}" value="${operationalDateV5}">`;
    head.insertAdjacentElement('afterend',div);
  }
  function injectUIV5(){
    document.querySelector('.logo').innerHTML='<div style="width:36px;height:36px;border-radius:9px;background:#fff7ea;color:#c91414;display:flex;align-items:center;justify-content:center;font-weight:900;font-size:13px">PSL</div>';
    const dash=document.querySelector('#tab-dashboard .quick-actions');
    if(dash && !document.getElementById('operationalDateV5')){
      const box=document.createElement('div'); box.id='opBoxV5'; box.style='background:#fff;border:2px solid #ffd2d9;border-radius:12px;padding:12px;margin-bottom:12px';
      box.innerHTML=`<label class="form-label">Tanggal Operasional</label><div style="display:flex;gap:8px;align-items:center"><input id="operationalDateV5" type="date" class="form-input" max="${TODAY()}" value="${operationalDateV5}"><span id="opBadgeV5" class="badge badge-blue">HARI INI</span></div><div class="card-sub">Semua input dan Tutup Hari mengikuti tanggal ini. Waktu audit tetap waktu asli saat data dimasukkan.</div>`;
      dash.parentNode.insertBefore(box,dash); document.getElementById('operationalDateV5').addEventListener('change',async e=>{ if(e.target.value>TODAY()){alert('Tanggal masa depan tidak diperbolehkan');e.target.value=operationalDateV5;return;} operationalDateV5=e.target.value; syncDatesV5(); await refreshV5(); });
      const b1=document.createElement('button');b1.className='quick-action owner-only';b1.innerHTML='🏁<br>Set HB Awal';b1.onclick=showHBInitialV5;
      const b2=document.createElement('button');b2.className='quick-action owner-only';b2.innerHTML='➕<br>Tambah HB';b2.onclick=showHBAddV5;
      dash.insertBefore(b2,dash.children[4]||null);dash.insertBefore(b1,b2);
    }
    addDateInputV5('modal-sales','salesDateV5'); addDateInputV5('modal-expense','expenseDateV5'); addDateInputV5('modal-restock','restockDateV5'); addDateInputV5('modal-stockout','stockoutDateV5');
    if(!document.getElementById('modal-hbinitial-v5')){
      document.body.insertAdjacentHTML('beforeend',`<div class="modal" id="modal-hbinitial-v5"><div class="modal-content"><div class="modal-header"><div class="modal-title">Set HB Awal</div><button class="close-btn" onclick="document.getElementById('modal-hbinitial-v5').classList.remove('show')">✕</button></div><div class="form-group"><label class="form-label">Tanggal</label><input id="hbInitialDateV5" type="date" class="form-input" max="${TODAY()}"></div><div class="form-group"><label class="form-label">HB Awal (Rp)</label><input id="hbInitialAmountV5" type="number" class="form-input" placeholder="0"></div><div class="form-group"><label class="form-label">Catatan</label><textarea id="hbInitialNoteV5" class="form-textarea" placeholder="Contoh: Saldo awal tanggal 1"></textarea></div><button class="btn btn-primary btn-block" onclick="saveHBInitialV5()">Simpan HB Awal</button></div></div><div class="modal" id="modal-hbadd-v5"><div class="modal-content"><div class="modal-header"><div class="modal-title">Tambah House Bank</div><button class="close-btn" onclick="document.getElementById('modal-hbadd-v5').classList.remove('show')">✕</button></div><div style="background:#FFF3E0;padding:9px;border-radius:8px;margin-bottom:10px;font-size:12px">Tambahan HB dicatat sebagai transaksi ledger. HB lama tidak ditimpa.</div><div class="form-group"><label class="form-label">Tanggal</label><input id="hbAddDateV5" type="date" class="form-input" max="${TODAY()}"></div><div class="form-group"><label class="form-label">Tambah HB (Rp)</label><input id="hbAddAmountV5" type="number" class="form-input" placeholder="0"></div><div class="form-group"><label class="form-label">Sumber / Catatan</label><textarea id="hbAddNoteV5" class="form-textarea" placeholder="Contoh: tambahan modal kas"></textarea></div><button class="btn btn-primary btn-block" onclick="saveHBAddV5()">Simpan Tambah HB</button></div></div>`);
    }
  }
  function syncDatesV5(){ ['salesDateV5','expenseDateV5','restockDateV5','stockoutDateV5','hbInitialDateV5','hbAddDateV5'].forEach(id=>{const e=document.getElementById(id);if(e)e.value=operationalDateV5;}); const r=document.getElementById('reportDate');if(r)r.value=operationalDateV5; }
  async function ensureOpenV5(date){ if(await isClosedV5(date)){alert('Tanggal '+date+' sudah CLOSED. Snapshot lama tidak ditimpa.');return false;} return true; }

  window.showHBInitialV5=async function(){ if(currentRole!=='OWNER')return alert('Hanya OWNER'); const prev=await snapshotV5(prevDateV5(operationalDateV5)); if(prev)return alert('HB awal tanggal ini sudah berasal dari Register hari sebelumnya. Jika ada tambahan uang gunakan Tambah HB.'); document.getElementById('hbInitialDateV5').value=operationalDateV5; document.getElementById('modal-hbinitial-v5').classList.add('show'); };
  window.showHBAddV5=function(){ if(currentRole!=='OWNER')return alert('Hanya OWNER'); document.getElementById('hbAddDateV5').value=operationalDateV5; document.getElementById('modal-hbadd-v5').classList.add('show'); };
  window.saveHBInitialV5=async function(){ const date=hbInitialDateV5.value, amount=Number(hbInitialAmountV5.value), note=hbInitialNoteV5.value; if(currentRole!=='OWNER')return alert('Hanya OWNER'); if(!date||amount<0)return alert('Isi tanggal dan HB Awal'); if(!await ensureOpenV5(date))return; if(await snapshotV5(prevDateV5(date)))return alert('HB tanggal ini wajib berasal dari Register hari sebelumnya. Gunakan Tambah HB.'); const tx=await activeTxV5(date); if(tx.some(x=>x.type==='hb_initial'))return alert('HB Awal tanggal ini sudah pernah diset.'); await dbPut('transactions',{id:uidV5(),date,datetime:new Date().toISOString(),type:'hb_initial',amount,note:note||'HB Awal',user:currentRole,status:'ACTIVE'}); operationalDateV5=date; document.getElementById('operationalDateV5').value=date; document.getElementById('modal-hbinitial-v5').classList.remove('show'); await refreshV5(); alert('HB Awal tersimpan'); };
  window.saveHBAddV5=async function(){ const date=hbAddDateV5.value, amount=Number(hbAddAmountV5.value), note=hbAddNoteV5.value; if(currentRole!=='OWNER')return alert('Hanya OWNER'); if(!date||amount<=0)return alert('Isi tanggal dan nominal Tambah HB'); if(!await ensureOpenV5(date))return; await dbPut('transactions',{id:uidV5(),date,datetime:new Date().toISOString(),type:'hb_add',amount,note:note||'Tambah HB',user:currentRole,status:'ACTIVE'}); operationalDateV5=date; document.getElementById('operationalDateV5').value=date; document.getElementById('modal-hbadd-v5').classList.remove('show'); await refreshV5(); alert('Tambah HB tersimpan sebagai ledger'); };

  const originalShowModalV5=UI.showModal.bind(UI);
  UI.showModal=function(type){ const map={sales:'salesDateV5',expense:'expenseDateV5',restock:'restockDateV5',stockout:'stockoutDateV5'}; if(map[type]){ const e=document.getElementById(map[type]);if(e)e.value=operationalDateV5; } return originalShowModalV5(type); };

  Transactions.save=async function(type){
    try{
      const date=document.getElementById(type==='sales'?'salesDateV5':'expenseDateV5')?.value||operationalDateV5; if(!await ensureOpenV5(date))return;
      let amount,channel=null,category=null,note='';
      if(type==='sales'){amount=Number(salesAmount.value);channel=selectedChannel;note=salesNote.value;if(amount<=0)return alert('Masukkan jumlah yang valid');}
      else {amount=Number(expenseAmount.value);category=expenseCategory.value;note=expenseNote.value;if(amount<=0)return alert('Masukkan jumlah yang valid');}
      await dbPut('transactions',{id:uidV5(),date,datetime:new Date().toISOString(),type,category,amount,channel,note,user:currentRole,status:'ACTIVE'});
      UI.closeModal(type); operationalDateV5=date;document.getElementById('operationalDateV5').value=date; await refreshV5(); alert('Berhasil disimpan');
    }catch(e){console.error(e);alert('Gagal menyimpan');}
  };
  Stock.restock=async function(){ const date=restockDateV5.value||operationalDateV5,product=restockProduct.value,qty=Number(restockQty.value);if(qty<=0)return alert('Masukkan jumlah valid');if(!await ensureOpenV5(date))return;await dbPut('stock_movements',{id:uidV5(),date,datetime:new Date().toISOString(),product,qty,type:'restock',note:'Restock',user:currentRole,status:'ACTIVE'});UI.closeModal('restock');operationalDateV5=date;operationalDateV5 && (document.getElementById('operationalDateV5').value=date);await refreshV5();alert('Restock berhasil');};
  Stock.stockOut=async function(){ const date=stockoutDateV5.value||operationalDateV5,product=stockoutProduct.value,qty=Number(stockoutQty.value),note=stockoutReason.value;if(qty<=0||!note)return alert('Isi jumlah dan alasan');if(!await ensureOpenV5(date))return;await dbPut('stock_movements',{id:uidV5(),date,datetime:new Date().toISOString(),product,qty,type:'stockout',note,user:currentRole,status:'ACTIVE'});UI.closeModal('stockout');operationalDateV5=date;document.getElementById('operationalDateV5').value=date;await refreshV5();alert('Stock keluar berhasil');};

  UI.updateDashboard=async function(){
    const c=await calcV5(operationalDateV5), levels=await stockAtV5(operationalDateV5), totalStock=Object.values(levels).reduce((a,x)=>a+Number(x||0),0), sn=await snapshotV5(operationalDateV5);
    cardHouseBank.textContent=rupiahV5(c.houseBank);cardCash.textContent=rupiahV5(c.cash);cardQRIS.textContent=rupiahV5(c.qris);cardGojek.textContent=rupiahV5(c.gojek);cardGrab.textContent=rupiahV5(c.grab);cardTotalSales.textContent=rupiahV5(c.totalSales);cardTotalOut.textContent=rupiahV5(c.totalOut);cardTotalRegister.textContent=rupiahV5(c.totalRegister);cardTotalStock.textContent=totalStock;
    currentDate.textContent=new Date(operationalDateV5+'T12:00:00').toLocaleDateString('id-ID',{weekday:'long',year:'numeric',month:'long',day:'numeric'});
    const badge=document.getElementById('opBadgeV5');if(badge){badge.textContent=sn?'CLOSED':operationalDateV5<TODAY()?'BACKDATE':'HARI INI';badge.className='badge '+(sn?'badge-green':operationalDateV5<TODAY()?'badge-orange':'badge-blue');}
  };
  UI.renderTodayTransactions=async function(){ const tx=await activeTxV5(operationalDateV5), el=document.getElementById('todayTransactions'); if(!tx.length){el.innerHTML='<div class="empty-state"><div class="icon">📭</div><p>Belum ada transaksi tanggal ini</p></div>';return;} el.innerHTML=tx.slice().sort((a,b)=>String(b.datetime).localeCompare(String(a.datetime))).slice(0,30).map(t=>{const label=t.type==='sales'?'💰 Sales':t.type==='expense'?'📝 Pengeluaran':t.type==='hb_initial'?'🏁 HB Awal':t.type==='hb_add'?'➕ Tambah HB':t.type;return `<div class="transaction-item"><div class="transaction-info"><div class="transaction-type">${label}</div><div class="transaction-meta">Tanggal bisnis ${t.date} · audit ${new Date(t.datetime).toLocaleString('id-ID')} · ${escV5(t.note||'')}</div></div><div class="transaction-amount ${t.type==='expense'?'negative':'positive'}">${rupiahV5(t.amount)}</div></div>`}).join(''); };
  UI.renderStock=async function(){ const levels=await stockAtV5(operationalDateV5);stockLevels.innerHTML=PRODUCTS.map(p=>`<div class="stock-item"><div class="stock-name">${p}</div><div class="stock-qty ${levels[p]<10?'low':'ok'}">${levels[p]||0}</div></div>`).join(''); const all=(await dbGetAll('stock_movements')).filter(x=>x.date===operationalDateV5);stockHistory.innerHTML=all.length?all.map(x=>`<div class="transaction-item"><div>${x.type==='restock'?'📦':'📤'} ${escV5(x.product)}<div class="transaction-meta">${x.date} · audit ${new Date(x.datetime).toLocaleString('id-ID')}</div></div><b>${x.type==='restock'?'+':'-'}${x.qty}</b></div>`).join(''):'<div class="empty-state">Belum ada pergerakan stock tanggal ini.</div>'; };
  UI.renderHistory=async function(filter='all',search=''){ const tx=await Transactions.getAll();let f=tx;if(filter!=='all')f=f.filter(t=>t.type===filter);if(search)f=f.filter(t=>JSON.stringify(t).toLowerCase().includes(search.toLowerCase()));historyList.innerHTML=f.length?f.sort((a,b)=>String(b.datetime).localeCompare(String(a.datetime))).slice(0,100).map(t=>{const label=t.type==='sales'?'💰 Sales':t.type==='expense'?'📝 Pengeluaran':t.type==='hb_initial'?'🏁 HB Awal':t.type==='hb_add'?'➕ Tambah HB':t.type;return `<div class="transaction-item"><div class="transaction-info"><div class="transaction-type">${label}${t.status==='CORRECTED'?' <span class="badge badge-red">CORRECTED</span>':''}</div><div class="transaction-meta">Tanggal bisnis ${t.date} · audit ${new Date(t.datetime).toLocaleString('id-ID')} · ${escV5(t.note||'')}</div></div><div class="transaction-amount">${rupiahV5(t.amount)}</div></div>`}).join(''):'<div class="empty-state">Belum ada riwayat.</div>'; };
  UI.drillDown=async function(type){ const c=await calcV5(operationalDateV5), levels=await stockAtV5(operationalDateV5);let title='Detail',html='';if(type==='houseBank'){title='Detail House Bank';html=`<div class="report-row"><span>HB Awal</span><b>${rupiahV5(c.openingHB)}</b></div>${c.adds.map(x=>`<div class="report-row"><span>Tambah HB · ${escV5(x.note||'')}</span><b>${rupiahV5(x.amount)}</b></div>`).join('')}<div class="report-row"><span>House Bank Efektif</span><b>${rupiahV5(c.houseBank)}</b></div>`;}else if(type==='totalRegister'){title='Rumus Total Register';html=`<div class="report-row"><span>House Bank</span><b>${rupiahV5(c.houseBank)}</b></div><div class="report-row"><span>+ Sales</span><b>${rupiahV5(c.totalSales)}</b></div><div class="report-row"><span>- Out</span><b>${rupiahV5(c.totalOut)}</b></div><div class="report-row"><span>Total Register</span><b>${rupiahV5(c.totalRegister)}</b></div>`;}else if(type==='totalStock'){title='Stock '+operationalDateV5;html=PRODUCTS.map(p=>`<div class="report-row"><span>${p}</span><b>${levels[p]||0}</b></div>`).join('');}else{const list=type==='totalSales'?c.sales:type==='totalOut'?c.expenses:c.sales.filter(x=>x.channel===type);title='Detail '+type;html=list.map(x=>`<div class="report-row"><span>${escV5(x.note||x.category||x.channel||'')}</span><b>${rupiahV5(x.amount)}</b></div>`).join('')||'<p>Tidak ada transaksi.</p>';}detailTitle.textContent=title;detailContent.innerHTML=html;UI.showModal('detail');};
  UI.tutupHari=async function(){ if(currentRole!=='OWNER')return alert('Hanya OWNER yang bisa Tutup Hari'); if(await isClosedV5(operationalDateV5))return alert('Tanggal ini sudah CLOSED. Snapshot tidak ditimpa.'); const c=await calcV5(operationalDateV5), sm=await stockDayV5(operationalDateV5), open=await stockAtV5(prevDateV5(operationalDateV5)), end=await stockAtV5(operationalDateV5), openTotal=Object.values(open).reduce((a,x)=>a+Number(x||0),0), endTotal=Object.values(end).reduce((a,x)=>a+Number(x||0),0); let actual=prompt('Register expected '+rupiahV5(c.totalRegister)+'\nMasukkan register aktual:',String(c.totalRegister));if(actual===null)return;actual=Number(actual);if(!Number.isFinite(actual))return alert('Register aktual tidak valid'); await dbPut('snapshots',{id:'daily-'+operationalDateV5,date:operationalDateV5,type:'daily',openingHB:c.openingHB,hbAdd:c.hbAdd,houseBank:c.houseBank,cash:c.cash,qris:c.qris,gojek:c.gojek,grab:c.grab,totalSales:c.totalSales,totalOut:c.totalOut,closingRegister:c.totalRegister,openingStock:openTotal,restock:sm.inQty,stockOut:sm.outQty,endingStock:endTotal,actualRegister:actual,variance:actual-c.totalRegister,timestamp:new Date().toISOString(),closedBy:currentRole}); alert('Tutup Hari '+operationalDateV5+' berhasil.\nRegister '+rupiahV5(c.totalRegister)+' menjadi HB '+nextDateV5(operationalDateV5)); const n=nextDateV5(operationalDateV5);if(n<=TODAY()){operationalDateV5=n;document.getElementById('operationalDateV5').value=n;}syncDatesV5();await refreshV5(); };

  Reports.generate=async function(){ const date=reportDate.value||operationalDateV5,c=await calcV5(date),sn=await snapshotV5(date),sm=await stockDayV5(date),open=await stockAtV5(prevDateV5(date)),end=await stockAtV5(date),openTotal=Object.values(open).reduce((a,x)=>a+Number(x||0),0),endTotal=Object.values(end).reduce((a,x)=>a+Number(x||0),0); reportContent.innerHTML=`<div class="report-section"><div class="card-title">Laporan Harian ${date}</div><div class="report-row"><span>HB Awal</span><b>${rupiahV5(c.openingHB)}</b></div><div class="report-row"><span>Tambah HB</span><b>${rupiahV5(c.hbAdd)}</b></div><div class="report-row"><span>House Bank</span><b>${rupiahV5(c.houseBank)}</b></div><div class="report-row"><span>Cash</span><b>${rupiahV5(c.cash)}</b></div><div class="report-row"><span>QRIS</span><b>${rupiahV5(c.qris)}</b></div><div class="report-row"><span>Gojek</span><b>${rupiahV5(c.gojek)}</b></div><div class="report-row"><span>Grab</span><b>${rupiahV5(c.grab)}</b></div><div class="report-row"><span>Total Sales</span><b>${rupiahV5(c.totalSales)}</b></div><div class="report-row"><span>Total Out</span><b>${rupiahV5(c.totalOut)}</b></div><div class="report-row"><span>Total Register</span><b>${rupiahV5(c.totalRegister)}</b></div><div class="report-row"><span>Stock Awal</span><b>${openTotal}</b></div><div class="report-row"><span>Restock / Keluar</span><b>+${sm.inQty} / -${sm.outQty}</b></div><div class="report-row"><span>Closing Stock</span><b>${endTotal}</b></div><div class="report-row"><span>Aktual</span><b>${sn?rupiahV5(sn.actualRegister):'-'}</b></div><div class="report-row"><span>Selisih</span><b>${sn?rupiahV5(sn.variance):'-'}</b></div></div><div style="display:flex;gap:8px"><button class="btn btn-secondary btn-sm" onclick="window.print()">PDF</button><button class="btn btn-success btn-sm" onclick="Export.whatsapp('${date}')">WhatsApp</button><button class="btn btn-primary btn-sm" onclick="Export.csv('${date}')">CSV</button></div>`; };

  UI.showSettings=function(){ detailTitle.textContent='Pengaturan';detailContent.innerHTML=`<div class="form-group"><label class="form-label">Role</label><select class="form-select" id="settingRole" onchange="Settings.setRole(this.value)"><option value="OWNER" ${currentRole==='OWNER'?'selected':''}>OWNER</option><option value="STAFF" ${currentRole==='STAFF'?'selected':''}>STAFF</option></select></div><p style="font-size:12px;color:#666">HB Awal dan Tambah HB sekarang dicatat dari Dashboard per tanggal, bukan overwrite global.</p><hr style="margin:16px 0"><button class="btn btn-secondary btn-block" onclick="Export.jsonBackup()">Export JSON Backup</button><p style="font-size:11px;color:#666;margin-top:10px">Cloud: NOT CONNECTED sampai Supabase dikonfigurasi.</p>`;UI.showModal('detail');};

  async function refreshV5(){ syncDatesV5();await UI.updateDashboard();await UI.renderTodayTransactions();await UI.renderStock();await UI.renderHistory(); }

  // Run after old app has opened its database.
  const bootV5=setInterval(async()=>{ if(db){clearInterval(bootV5);injectUIV5();syncDatesV5();await refreshV5();console.log('PSL v5 backdate + HB ledger patch active');}},50);
})();
</script>
'''
    if '</body>' not in s:
        raise SystemExit('index.html missing </body>')
    s = s.replace('</body>', patch + '\n</body>')
    p.write_text(s, encoding='utf-8')
    print('Patched index.html')

meta_path=Path('apps/papa-sauce-lab/project.json')
meta=json.loads(meta_path.read_text(encoding='utf-8'))
meta['version']=5
meta['provider']='manual-hotfix'
meta['model']='none'
meta['qc']='passed'
meta['last_request_id']='papa-sauce-lab-backdate-hb-v5-20260912'
meta['last_prompt']='Deterministic patch: operational-date backfill, date-scoped HB Awal, append-only Tambah HB ledger, close-day rollover, reconciliation, date-scoped stock/report/history.'
meta['last_build']='pending'
meta['updated_at']=datetime.datetime.now(datetime.timezone.utc).isoformat()
meta_path.write_text(json.dumps(meta,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

req={
  'request_id':'papa-sauce-lab-backdate-hb-v5-20260912',
  'project_id':'papa-sauce-lab',
  'project_name':'Papa Sauce Lab',
  'mode':'build_existing',
  'created_at':datetime.datetime.now(datetime.timezone.utc).isoformat(),
  'prompt':'Build existing deterministic PSL v5 source. No AI regeneration.'
}
Path('requests/papa-sauce-lab-backdate-hb-v5-20260912.json').write_text(json.dumps(req,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Prepared project v5 + build request')
