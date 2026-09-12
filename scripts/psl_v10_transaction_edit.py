from pathlib import Path

p = Path('apps/papa-sauce-lab/index.html')
s = p.read_text(encoding='utf-8')

if 'PSL_V10_TRANSACTION_EDIT' in s:
    print('PSL v10 transaction edit already present')
    raise SystemExit(0)

js = r'''
<script id="PSL_V10_TRANSACTION_EDIT">
(() => {
  const esc10 = v => String(v ?? '').replace(/[&<>"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
  const rp10 = n => 'Rp ' + Number(n || 0).toLocaleString('id-ID');
  const opDate10 = () => document.getElementById('operationalDateV5')?.value || new Date().toISOString().slice(0,10);
  const active10 = a => (a || []).filter(x => x && x.status !== 'CORRECTED');
  const editable10 = t => t && t.status !== 'CORRECTED' && (t.type === 'sales' || t.type === 'expense');
  let editId10 = null;
  let lastDrill10 = null;

  async function isClosed10(date){
    try { return (await dbGetAll('snapshots')).some(x => x && x.date === date && x.type !== 'period_end'); }
    catch(e){ console.error(e); return false; }
  }

  const oldShowCorrection = UI.showCorrection?.bind(UI);
  UI.showCorrection = async function(id){
    editId10 = id;
    if(oldShowCorrection) oldShowCorrection(id); else UI.showModal('correction');
    try {
      const tx = await dbGet('transactions', id);
      const amount = document.getElementById('correctionValue');
      const reason = document.getElementById('correctionReason');
      if(amount && tx) amount.value = Number(tx.amount || 0);
      if(reason) reason.value = '';
    } catch(e){ console.error(e); }
  };

  Transactions.correct = async function(){
    const id = editId10;
    const amountEl = document.getElementById('correctionValue');
    const reasonEl = document.getElementById('correctionReason');
    const newValue = Number(amountEl?.value || 0);
    const reason = String(reasonEl?.value || '').trim();
    if(!id) return alert('Transaksi tidak dipilih');
    if(!newValue || newValue <= 0) return alert('Masukkan nominal baru');
    if(!reason) return alert('Masukkan alasan koreksi');

    const oldTx = await dbGet('transactions', id);
    if(!oldTx) return alert('Transaksi tidak ditemukan');
    if(oldTx.status === 'CORRECTED') return alert('Transaksi ini sudah pernah dikoreksi');
    const closed = await isClosed10(oldTx.date);
    if(closed && !confirm('Tanggal '+oldTx.date+' sudah CLOSED. Simpan koreksi nominal tanpa menimpa snapshot CLOSED?')) return;

    const now = new Date().toISOString();
    const newId = Date.now().toString(36)+'-'+Math.random().toString(36).slice(2,8);
    const oldValue = Number(oldTx.amount || 0);

    oldTx.status = 'CORRECTED';
    oldTx.correctedTo = newId;
    oldTx.correctionReason = reason;
    oldTx.correctedAt = now;
    oldTx.correctedBy = currentRole;
    await dbPut('transactions', oldTx);

    await dbPut('corrections', {
      id:'corr-'+newId,
      originalId:id,
      replacementId:newId,
      date:oldTx.date,
      oldValue,
      newValue,
      reason,
      user:currentRole,
      time:now,
      closedAtEdit:closed
    });

    const newTx = {
      ...oldTx,
      id:newId,
      amount:newValue,
      status:'ACTIVE',
      correctedFrom:id,
      correctionReason:reason,
      datetime:now
    };
    delete newTx.correctedTo;
    delete newTx.correctedAt;
    delete newTx.correctedBy;
    await dbPut('transactions', newTx);

    UI.closeModal('correction');
    editId10 = null;
    await UI.updateDashboard();
    await UI.renderTodayTransactions();
    await UI.renderHistory();
    if(lastDrill10) await UI.drillDown(lastDrill10);
    alert('Koreksi tersimpan: '+rp10(oldValue)+' → '+rp10(newValue)+(closed?' (snapshot CLOSED tetap tidak ditimpa)':''));
  };

  UI.renderTodayTransactions = async function(){
    const date = opDate10();
    const tx = active10(await Transactions.getByDate(date));
    const el = document.getElementById('todayTransactions');
    if(!el) return;
    if(!tx.length){ el.innerHTML='<div class="empty-state"><div class="icon">📭</div><p>Belum ada transaksi tanggal ini</p></div>'; return; }
    el.innerHTML = tx.slice().sort((a,b)=>String(b.datetime).localeCompare(String(a.datetime))).slice(0,30).map(t=>{
      const label=t.type==='sales'?'💰 Sales':t.type==='expense'?'📝 Pengeluaran':t.type==='hb_initial'?'🏁 HB Awal':t.type==='hb_add'?'➕ Tambah HB':t.type;
      const edit = editable10(t) ? `<button class="btn btn-secondary btn-sm" style="padding:7px 9px;min-width:42px" onclick="UI.showCorrection('${esc10(t.id)}')">✏️ Edit</button>` : '';
      return `<div class="transaction-item"><div class="transaction-info"><div class="transaction-type">${label}</div><div class="transaction-meta">Tanggal bisnis ${esc10(t.date)} · audit ${new Date(t.datetime).toLocaleString('id-ID')} · ${esc10(t.note||'')}</div>${t.correctedFrom?`<div class="correction-reason">Hasil koreksi · ${esc10(t.correctionReason||'')}</div>`:''}</div><div style="display:flex;align-items:center;gap:8px"><div class="transaction-amount ${t.type==='expense'?'negative':'positive'}">${rp10(t.amount)}</div>${edit}</div></div>`;
    }).join('');
  };

  UI.renderHistory = async function(filter='all',search=''){
    let f = await Transactions.getAll();
    if(filter!=='all') f=f.filter(t=>t.type===filter);
    if(search) f=f.filter(t=>JSON.stringify(t).toLowerCase().includes(search.toLowerCase()));
    const el=document.getElementById('historyList'); if(!el) return;
    el.innerHTML=f.length?f.sort((a,b)=>String(b.datetime).localeCompare(String(a.datetime))).slice(0,100).map(t=>{
      const label=t.type==='sales'?'💰 Sales':t.type==='expense'?'📝 Pengeluaran':t.type==='hb_initial'?'🏁 HB Awal':t.type==='hb_add'?'➕ Tambah HB':t.type;
      const edit=editable10(t)?`<button class="btn btn-secondary btn-sm" style="padding:7px 9px" onclick="UI.showCorrection('${esc10(t.id)}')">✏️ Edit</button>`:'';
      return `<div class="transaction-item"><div class="transaction-info"><div class="transaction-type">${label}${t.status==='CORRECTED'?' <span class="badge badge-red">CORRECTED</span>':''}</div><div class="transaction-meta">Tanggal bisnis ${esc10(t.date)} · audit ${new Date(t.datetime).toLocaleString('id-ID')} · ${esc10(t.note||'')}</div>${t.correctionReason?`<div class="correction-reason">Koreksi: ${esc10(t.correctionReason)}</div>`:''}</div><div style="display:flex;align-items:center;gap:8px"><div class="transaction-amount">${rp10(t.amount)}</div>${edit}</div></div>`;
    }).join(''):'<div class="empty-state">Belum ada riwayat.</div>';
  };

  const oldDrillDown = UI.drillDown?.bind(UI);
  UI.drillDown = async function(type){
    const editTypes=['totalOut','totalSales','cash','qris','gojek','grab'];
    if(!editTypes.includes(type)) { lastDrill10=null; return oldDrillDown ? oldDrillDown(type) : undefined; }
    lastDrill10=type;
    const date=opDate10();
    let list=active10(await Transactions.getByDate(date));
    if(type==='totalOut') list=list.filter(x=>x.type==='expense');
    else if(type==='totalSales') list=list.filter(x=>x.type==='sales');
    else list=list.filter(x=>x.type==='sales' && x.channel===type);
    const title = type==='totalOut'?'Detail totalOut':type==='totalSales'?'Detail totalSales':'Detail '+type;
    const html=list.length?list.map(x=>`<div class="report-row" style="align-items:center;gap:8px"><span style="flex:1">${esc10(x.note||x.category||x.channel||'')}</span><b>${rp10(x.amount)}</b>${editable10(x)?`<button class="btn btn-secondary btn-sm" onclick="UI.showCorrection('${esc10(x.id)}')">✏️</button>`:''}</div>`).join(''):'<p>Tidak ada transaksi.</p>';
    document.getElementById('detailTitle').textContent=title;
    document.getElementById('detailContent').innerHTML=html;
    UI.showModal('detail');
  };

  console.log('PSL v10 transaction edit active');
})();
</script>
'''

if '</body>' not in s:
    raise SystemExit('invalid HTML skeleton')
s = s.replace('</body>', js + '\n</body>', 1)
p.write_text(s, encoding='utf-8')
print('patched PSL v10 transaction edit')
