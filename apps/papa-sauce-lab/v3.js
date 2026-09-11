// Papa Sauce Lab v4 runtime patch: historical/backdate accounting
(() => {
  const BALI_TZ = 'Asia/Makassar';
  let operationalDate = baliDateISO();

  function baliDateISO(d = new Date()) {
    const parts = new Intl.DateTimeFormat('en-CA', {
      timeZone: BALI_TZ, year: 'numeric', month: '2-digit', day: '2-digit'
    }).formatToParts(d);
    const map = Object.fromEntries(parts.map(p => [p.type, p.value]));
    return `${map.year}-${map.month}-${map.day}`;
  }

  function baliNowISOForBusinessDate(date) {
    const t = new Intl.DateTimeFormat('en-GB', {
      timeZone: BALI_TZ, hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
    }).format(new Date());
    return `${date}T${t}+08:00`;
  }

  function addDaysISO(date, days) {
    const d = new Date(`${date}T12:00:00+08:00`);
    d.setUTCDate(d.getUTCDate() + days);
    return d.toISOString().slice(0, 10);
  }

  function fmtDateID(date) {
    return new Date(`${date}T12:00:00+08:00`).toLocaleDateString('id-ID', {
      timeZone: BALI_TZ, weekday: 'long', day: 'numeric', month: 'long', year: 'numeric'
    });
  }

  function uid(prefix = 'ID') {
    return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  }

  async function allSnapshots() {
    return (await dbGetAll('snapshots')).filter(s => s && s.date);
  }

  async function snapshotForDate(date) {
    const snaps = await allSnapshots();
    const matches = snaps.filter(s => s.date === date && s.type !== 'period_end');
    return matches.sort((a,b) => new Date(b.timestamp || 0) - new Date(a.timestamp || 0))[0] || null;
  }

  async function latestSnapshotBefore(date) {
    const snaps = (await allSnapshots())
      .filter(s => s.type !== 'period_end' && s.date < date && typeof s.closingRegister === 'number')
      .sort((a,b) => a.date.localeCompare(b.date));
    return snaps.length ? snaps[snaps.length - 1] : null;
  }

  async function openingHBFor(date) {
    const prevExact = await snapshotForDate(addDaysISO(date, -1));
    if (prevExact && typeof prevExact.closingRegister === 'number') return prevExact.closingRegister;
    const latest = await latestSnapshotBefore(date);
    if (latest && typeof latest.closingRegister === 'number') return latest.closingRegister;
    const setting = await dbGet('settings', 'openingHB');
    return setting && Number.isFinite(Number(setting.value)) ? Number(setting.value) : 0;
  }

  async function stockMovementsForDate(date) {
    return (await dbGetAll('stock_movements')).filter(m => m.date === date);
  }

  async function stockLevelsAsOf(date, includeDate = true) {
    const movements = await dbGetAll('stock_movements');
    const levels = {};
    PRODUCTS.forEach(p => levels[p] = 0);
    movements
      .filter(m => includeDate ? m.date <= date : m.date < date)
      .sort((a,b) => (a.date || '').localeCompare(b.date || '') || new Date(a.datetime || 0) - new Date(b.datetime || 0))
      .forEach(m => {
        if (!Object.prototype.hasOwnProperty.call(levels, m.product)) levels[m.product] = 0;
        if (m.type === 'restock') levels[m.product] += Number(m.qty || 0);
        if (m.type === 'stockout') levels[m.product] -= Number(m.qty || 0);
      });
    return levels;
  }

  async function dateClosed(date) {
    return !!(await snapshotForDate(date));
  }

  async function totalsFor(date) {
    const transactions = await Transactions.getByDate(date);
    const sales = transactions.filter(t => t.type === 'sales' && t.status === 'ACTIVE');
    const expenses = transactions.filter(t => t.type === 'expense' && t.status === 'ACTIVE');
    const cash = sales.filter(s => s.channel === 'cash').reduce((a,b) => a + Number(b.amount || 0), 0);
    const qris = sales.filter(s => s.channel === 'qris').reduce((a,b) => a + Number(b.amount || 0), 0);
    const gojek = sales.filter(s => s.channel === 'gojek').reduce((a,b) => a + Number(b.amount || 0), 0);
    const grab = sales.filter(s => s.channel === 'grab').reduce((a,b) => a + Number(b.amount || 0), 0);
    const totalSales = cash + qris + gojek + grab;
    const totalOut = expenses.reduce((a,b) => a + Number(b.amount || 0), 0);
    const openingHB = await openingHBFor(date);
    const totalRegister = openingHB + totalSales - totalOut;
    return { transactions, sales, expenses, cash, qris, gojek, grab, totalSales, totalOut, openingHB, totalRegister };
  }

  function ensureBackdateUI() {
    const header = document.querySelector('.header');
    const container = document.querySelector('.container');
    if (!header || !container || document.getElementById('operationalDateBar')) return;

    const style = document.createElement('style');
    style.textContent = `
      #operationalDateBar{background:#fff3e0;border-bottom:1px solid #efc07e;padding:10px 12px;position:sticky;top:64px;z-index:90}
      #operationalDateBar .op-wrap{max-width:600px;margin:auto;display:flex;align-items:center;gap:8px;flex-wrap:wrap}
      #operationalDateBar label{font-size:12px;font-weight:800;color:#7a3e00}
      #operationalDateBar input{flex:1;min-width:150px;padding:9px 10px;border:1px solid #d9a55e;border-radius:8px;background:white;font-size:15px}
      #backdateBadge{font-size:10px;font-weight:800;padding:5px 8px;border-radius:999px;background:#C41E3A;color:#fff;display:none}
      #closedBadge{font-size:10px;font-weight:800;padding:5px 8px;border-radius:999px;background:#455a64;color:#fff;display:none}
      .psl-logo-inline{width:38px;height:38px;border-radius:9px;background:#fff8e7;color:#C41E3A;display:flex;align-items:center;justify-content:center;font-weight:900;font-size:15px;border:2px solid rgba(255,255,255,.85);box-shadow:inset 0 0 0 2px #C41E3A}
    `;
    document.head.appendChild(style);

    const bar = document.createElement('div');
    bar.id = 'operationalDateBar';
    bar.innerHTML = `<div class="op-wrap"><label for="operationalDateInput">Tanggal Operasional</label><input id="operationalDateInput" type="date"><span id="backdateBadge">BACKDATE</span><span id="closedBadge">SUDAH DITUTUP</span></div>`;
    header.insertAdjacentElement('afterend', bar);

    const input = document.getElementById('operationalDateInput');
    input.max = baliDateISO();
    input.value = operationalDate;
    input.addEventListener('change', async () => {
      if (!input.value) return;
      if (input.value > baliDateISO()) {
        UI.alert('Tanggal masa depan tidak diperbolehkan');
        input.value = operationalDate;
        return;
      }
      operationalDate = input.value;
      await refreshOperationalView();
    });

    const logo = document.querySelector('.logo');
    if (logo) logo.innerHTML = '<div class="psl-logo-inline">PSL</div>';
  }

  async function refreshOperationalView() {
    const today = baliDateISO();
    const back = document.getElementById('backdateBadge');
    const closed = document.getElementById('closedBadge');
    if (back) back.style.display = operationalDate < today ? 'inline-block' : 'none';
    const isClosed = await dateClosed(operationalDate);
    if (closed) closed.style.display = isClosed ? 'inline-block' : 'none';
    const currentDate = document.getElementById('currentDate');
    if (currentDate) currentDate.textContent = fmtDateID(operationalDate);
    const reportDate = document.getElementById('reportDate');
    if (reportDate) reportDate.value = operationalDate;
    await UI.updateDashboard();
    await UI.renderTodayTransactions();
    await UI.renderStock();
  }

  const originalSave = Transactions.save.bind(Transactions);
  Transactions.save = async function(type) {
    try {
      if (await dateClosed(operationalDate)) return UI.alert('Tanggal ini sudah Tutup Hari. Pilih tanggal lain atau lakukan koreksi dari Riwayat.');
      let amount, channel = null, category = null, note = '';
      if (type === 'sales') {
        amount = parseInt(document.getElementById('salesAmount').value);
        channel = selectedChannel;
        note = document.getElementById('salesNote').value || '';
        if (!amount || amount <= 0) return UI.alert('Masukkan jumlah yang valid');
      } else if (type === 'expense') {
        amount = parseInt(document.getElementById('expenseAmount').value);
        category = document.getElementById('expenseCategory').value;
        note = document.getElementById('expenseNote').value || '';
        if (!amount || amount <= 0) return UI.alert('Masukkan jumlah yang valid');
      } else {
        return originalSave(type);
      }
      const transaction = {
        id: uid(type === 'sales' ? 'SAL' : 'EXP'),
        date: operationalDate,
        datetime: baliNowISOForBusinessDate(operationalDate),
        auditCreatedAt: new Date().toISOString(),
        type, category, amount, channel, note,
        product: null, qty: null,
        user: currentRole, status: 'ACTIVE',
        correctedFrom: null, correctionReason: null, correctionTime: null, correctionUser: null
      };
      await dbPut('transactions', transaction);
      UI.closeModal(type);
      await refreshOperationalView();
      UI.alert(`Tersimpan untuk ${fmtDateID(operationalDate)}`);
    } catch (e) {
      console.error('Backdate save error', e);
      UI.alert('Gagal menyimpan transaksi');
    }
  };

  Stock.restock = async function() {
    if (await dateClosed(operationalDate)) return UI.alert('Tanggal ini sudah Tutup Hari.');
    const product = document.getElementById('restockProduct').value;
    const qty = parseInt(document.getElementById('restockQty').value);
    if (!qty || qty <= 0) return UI.alert('Masukkan jumlah valid');
    const movement = {
      id: uid('STIN'), date: operationalDate, datetime: baliNowISOForBusinessDate(operationalDate),
      auditCreatedAt: new Date().toISOString(), product, qty, type: 'restock', note: 'Restock', user: currentRole
    };
    await dbPut('stock_movements', movement);
    UI.closeModal('restock');
    await refreshOperationalView();
    UI.alert(`Restock tersimpan untuk ${fmtDateID(operationalDate)}`);
  };

  Stock.stockOut = async function() {
    if (await dateClosed(operationalDate)) return UI.alert('Tanggal ini sudah Tutup Hari.');
    const product = document.getElementById('stockoutProduct').value;
    const qty = parseInt(document.getElementById('stockoutQty').value);
    const reason = document.getElementById('stockoutReason').value || '';
    if (!qty || qty <= 0) return UI.alert('Masukkan jumlah valid');
    if (!reason) return UI.alert('Masukkan alasan');
    const before = await stockLevelsAsOf(operationalDate, true);
    if ((before[product] || 0) - qty < 0) return UI.alert(`Stock ${product} tidak cukup`);
    const movement = {
      id: uid('STOUT'), date: operationalDate, datetime: baliNowISOForBusinessDate(operationalDate),
      auditCreatedAt: new Date().toISOString(), product, qty, type: 'stockout', note: reason, user: currentRole
    };
    await dbPut('stock_movements', movement);
    UI.closeModal('stockout');
    await refreshOperationalView();
    UI.alert(`Stock keluar tersimpan untuk ${fmtDateID(operationalDate)}`);
  };

  UI.updateDashboard = async function() {
    const t = await totalsFor(operationalDate);
    const levels = await stockLevelsAsOf(operationalDate, true);
    const totalStock = Object.values(levels).reduce((a,b) => a + Number(b || 0), 0);
    const set = (id, val) => { const el = document.getElementById(id); if (el) el.textContent = val; };
    set('cardHouseBank', `Rp ${t.openingHB.toLocaleString('id-ID')}`);
    set('cardCash', `Rp ${t.cash.toLocaleString('id-ID')}`);
    set('cardQRIS', `Rp ${t.qris.toLocaleString('id-ID')}`);
    set('cardGojek', `Rp ${t.gojek.toLocaleString('id-ID')}`);
    set('cardGrab', `Rp ${t.grab.toLocaleString('id-ID')}`);
    set('cardTotalSales', `Rp ${t.totalSales.toLocaleString('id-ID')}`);
    set('cardTotalOut', `Rp ${t.totalOut.toLocaleString('id-ID')}`);
    set('cardTotalRegister', `Rp ${t.totalRegister.toLocaleString('id-ID')}`);
    set('cardTotalStock', String(totalStock));
  };

  UI.renderTodayTransactions = async function() {
    const financial = await Transactions.getByDate(operationalDate);
    const stock = await stockMovementsForDate(operationalDate);
    const items = [
      ...financial.map(t => ({...t, _kind:'finance'})),
      ...stock.map(s => ({...s, _kind:'stock'}))
    ].sort((a,b) => new Date(b.auditCreatedAt || b.datetime || 0) - new Date(a.auditCreatedAt || a.datetime || 0));
    const container = document.getElementById('todayTransactions');
    if (!container) return;
    if (!items.length) {
      container.innerHTML = `<div class="empty-state"><div class="icon">📭</div><p>Belum ada transaksi untuk ${fmtDateID(operationalDate)}</p></div>`;
      return;
    }
    container.innerHTML = items.slice(0, 30).map(t => {
      if (t._kind === 'stock') {
        return `<div class="transaction-item"><div class="transaction-info"><div class="transaction-type">${t.type === 'restock' ? '📦 Restock' : '📤 Stock Keluar'}</div><div class="transaction-meta">${t.product} • ${t.note || ''} • audit ${new Date(t.auditCreatedAt || t.datetime).toLocaleString('id-ID')}</div></div><div class="transaction-amount ${t.type === 'stockout' ? 'negative':'positive'}">${t.type === 'stockout' ? '-':'+'}${t.qty}</div></div>`;
      }
      return `<div class="transaction-item"><div class="transaction-info"><div class="transaction-type">${t.type === 'sales' ? '💰 Sales':'📝 Pengeluaran'} ${t.status === 'CORRECTED' ? '<span class="badge badge-red">CORRECTED</span>':''}</div><div class="transaction-meta">${t.channel ? CHANNELS[t.channel] : t.category || ''} • audit ${new Date(t.auditCreatedAt || t.datetime).toLocaleString('id-ID')}</div></div><div class="transaction-amount ${t.type === 'expense' ? 'negative':'positive'}">Rp ${Number(t.amount||0).toLocaleString('id-ID')}</div></div>`;
    }).join('');
  };

  UI.renderStock = async function() {
    const levels = await stockLevelsAsOf(operationalDate, true);
    const container = document.getElementById('stockLevels');
    if (!container) return;
    container.innerHTML = PRODUCTS.map(p => `<div class="stock-item"><div class="stock-name">${p}</div><div class="stock-qty ${levels[p] < 10 ? 'low':'ok'}">${levels[p]}</div></div>`).join('');
  };

  UI.renderHistory = async function(filter = 'all', search = '') {
    const txs = await Transactions.getAll();
    const sms = await dbGetAll('stock_movements');
    let items = [
      ...txs.map(t => ({...t, _source:'transactions'})),
      ...sms.map(s => ({...s, _source:'stock', status:s.status || 'ACTIVE'}))
    ];
    if (filter !== 'all') {
      if (filter === 'restock' || filter === 'stockout') items = items.filter(i => i._source === 'stock' && i.type === filter);
      else items = items.filter(i => i._source === 'transactions' && i.type === filter);
    }
    if (search) {
      const q = search.toLowerCase();
      items = items.filter(i => [i.note,i.channel,i.category,i.product,i.date].filter(Boolean).some(v => String(v).toLowerCase().includes(q)));
    }
    items.sort((a,b) => (b.date || '').localeCompare(a.date || '') || new Date(b.auditCreatedAt || b.datetime || 0) - new Date(a.auditCreatedAt || a.datetime || 0));
    const container = document.getElementById('historyList');
    if (!container) return;
    if (!items.length) return container.innerHTML = '<div class="empty-state"><div class="icon">📭</div><p>Belum ada riwayat</p></div>';
    container.innerHTML = items.slice(0,100).map(i => {
      if (i._source === 'stock') return `<div class="transaction-item"><div class="transaction-info"><div class="transaction-type">${i.type === 'restock' ? '📦 Restock':'📤 Stock Keluar'}</div><div class="transaction-meta">Tanggal bisnis ${i.date} • ${i.product} • audit ${new Date(i.auditCreatedAt || i.datetime).toLocaleString('id-ID')}</div></div><div class="transaction-amount ${i.type === 'stockout'?'negative':'positive'}">${i.type === 'stockout'?'-':'+'}${i.qty}</div></div>`;
      return `<div class="transaction-item"><div class="transaction-info"><div class="transaction-type">${i.type === 'sales'?'💰 Sales':'📝 Pengeluaran'} ${i.status === 'CORRECTED' ? '<span class="badge badge-red">CORRECTED</span>':''}</div><div class="transaction-meta">Tanggal bisnis ${i.date} • audit ${new Date(i.auditCreatedAt || i.datetime).toLocaleString('id-ID')} • ${i.user}</div></div><div style="display:flex;align-items:center;gap:8px"><div class="transaction-amount ${i.type === 'expense'?'negative':'positive'}">Rp ${Number(i.amount||0).toLocaleString('id-ID')}</div>${currentRole === 'OWNER' && i.status === 'ACTIVE' ? `<button class="btn btn-sm btn-outline" onclick="UI.showCorrection('${i.id}')">✏️</button>`:''}</div></div>`;
    }).join('');
  };

  UI.drillDown = async function(type) {
    const t = await totalsFor(operationalDate);
    const levels = await stockLevelsAsOf(operationalDate, true);
    const channelMap = {cash:t.sales.filter(s=>s.channel==='cash'),qris:t.sales.filter(s=>s.channel==='qris'),gojek:t.sales.filter(s=>s.channel==='gojek'),grab:t.sales.filter(s=>s.channel==='grab')};
    let title = `Detail ${fmtDateID(operationalDate)}`;
    let content = '';
    if (type === 'houseBank') content = `<div class="report-row"><span class="report-label">House Bank</span><span class="report-value">Rp ${t.openingHB.toLocaleString('id-ID')}</span></div>`;
    else if (type === 'totalRegister') content = `<div class="report-row"><span class="report-label">House Bank</span><span class="report-value">Rp ${t.openingHB.toLocaleString('id-ID')}</span></div><div class="report-row"><span class="report-label">Total Sales</span><span class="report-value">Rp ${t.totalSales.toLocaleString('id-ID')}</span></div><div class="report-row"><span class="report-label">Total Out</span><span class="report-value red">Rp ${t.totalOut.toLocaleString('id-ID')}</span></div><div class="report-row"><b>Total Register</b><b>Rp ${t.totalRegister.toLocaleString('id-ID')}</b></div>`;
    else if (type === 'totalStock') content = PRODUCTS.map(p => `<div class="report-row"><span>${p}</span><b>${levels[p]}</b></div>`).join('');
    else {
      const list = type === 'totalSales' ? t.sales : type === 'totalOut' ? t.expenses : (channelMap[type] || []);
      content = list.length ? list.map(x => `<div class="transaction-item"><div>${x.channel ? CHANNELS[x.channel] : x.category} ${x.note ? '• '+x.note:''}</div><b>Rp ${Number(x.amount||0).toLocaleString('id-ID')}</b></div>`).join('') : '<p>Tidak ada transaksi.</p>';
    }
    document.getElementById('detailTitle').textContent = title;
    document.getElementById('detailContent').innerHTML = content;
    UI.showModal('detail');
  };

  UI.tutupHari = async function() {
    if (currentRole !== 'OWNER') return UI.alert('Hanya OWNER yang bisa Tutup Hari');
    const existing = await snapshotForDate(operationalDate);
    if (existing) return UI.alert(`Tanggal ${operationalDate} sudah ditutup. Snapshot tidak akan ditimpa.`);
    const t = await totalsFor(operationalDate);
    const movements = await stockMovementsForDate(operationalDate);
    const openingLevels = await stockLevelsAsOf(operationalDate, false);
    const endingLevels = await stockLevelsAsOf(operationalDate, true);
    const openingStock = Object.values(openingLevels).reduce((a,b)=>a+Number(b||0),0);
    const endingStock = Object.values(endingLevels).reduce((a,b)=>a+Number(b||0),0);
    const restock = movements.filter(m=>m.type==='restock').reduce((a,b)=>a+Number(b.qty||0),0);
    const stockOut = movements.filter(m=>m.type==='stockout').reduce((a,b)=>a+Number(b.qty||0),0);
    const actualRaw = prompt(`Actual Register ${fmtDateID(operationalDate)}\nExpected: Rp ${t.totalRegister.toLocaleString('id-ID')}\nKosongkan jika sama.`,'');
    const actualRegister = actualRaw === null || actualRaw.trim() === '' ? t.totalRegister : Number(String(actualRaw).replace(/[^0-9-]/g,''));
    if (!Number.isFinite(actualRegister)) return UI.alert('Actual Register tidak valid');
    const variance = actualRegister - t.totalRegister;
    const snapshot = {
      id: uid('SNAP'), date: operationalDate, type:'daily_close',
      openingHB:t.openingHB, cash:t.cash, qris:t.qris, gojek:t.gojek, grab:t.grab,
      totalSales:t.totalSales, totalOut:t.totalOut, closingRegister:t.totalRegister,
      openingStock, restock, stockOut, endingStock,
      openingStockLevels:openingLevels, endingStockLevels:endingLevels,
      actualRegister, variance, closedBy:currentRole, timestamp:new Date().toISOString()
    };
    await dbPut('snapshots', snapshot);
    const next = addDaysISO(operationalDate, 1);
    UI.alert(`Tutup Hari ${operationalDate} berhasil.\nRegister: Rp ${t.totalRegister.toLocaleString('id-ID')}\nHB ${next}: Rp ${t.totalRegister.toLocaleString('id-ID')}\nSelisih: Rp ${variance.toLocaleString('id-ID')}`);
    if (operationalDate < baliDateISO()) {
      operationalDate = next > baliDateISO() ? baliDateISO() : next;
      const input = document.getElementById('operationalDateInput');
      if (input) input.value = operationalDate;
    }
    await refreshOperationalView();
  };

  Reports.generate = async function() {
    const date = document.getElementById('reportDate').value || operationalDate;
    const t = await totalsFor(date);
    const movements = await stockMovementsForDate(date);
    const openingLevels = await stockLevelsAsOf(date, false);
    const endingLevels = await stockLevelsAsOf(date, true);
    const openingStock = Object.values(openingLevels).reduce((a,b)=>a+Number(b||0),0);
    const endingStock = Object.values(endingLevels).reduce((a,b)=>a+Number(b||0),0);
    const restock = movements.filter(m=>m.type==='restock').reduce((a,b)=>a+Number(b.qty||0),0);
    const stockOut = movements.filter(m=>m.type==='stockout').reduce((a,b)=>a+Number(b.qty||0),0);
    const snap = await snapshotForDate(date);
    const actual = snap ? Number(snap.actualRegister ?? snap.closingRegister) : t.totalRegister;
    const variance = snap ? Number(snap.variance || 0) : actual - t.totalRegister;
    const expenseByCategory = {};
    t.expenses.forEach(e => expenseByCategory[e.category || 'lainnya'] = (expenseByCategory[e.category || 'lainnya'] || 0) + Number(e.amount || 0));
    const expenseHtml = Object.entries(expenseByCategory).map(([k,v])=>`<div class="report-row"><span>${k}</span><span>Rp ${v.toLocaleString('id-ID')}</span></div>`).join('') || '<div class="report-row"><span>Tidak ada pengeluaran</span><span>Rp 0</span></div>';
    document.getElementById('reportContent').innerHTML = `<div class="report-section"><div class="card-title">Laporan Harian ${date} ${snap ? '• CLOSED':''}</div><div class="report-row"><span>Opening House Bank</span><b>Rp ${t.openingHB.toLocaleString('id-ID')}</b></div><div class="report-row"><span>Cash</span><span>Rp ${t.cash.toLocaleString('id-ID')}</span></div><div class="report-row"><span>QRIS</span><span>Rp ${t.qris.toLocaleString('id-ID')}</span></div><div class="report-row"><span>Gojek</span><span>Rp ${t.gojek.toLocaleString('id-ID')}</span></div><div class="report-row"><span>Grab</span><span>Rp ${t.grab.toLocaleString('id-ID')}</span></div><div class="report-row"><b>Total Sales</b><b>Rp ${t.totalSales.toLocaleString('id-ID')}</b></div><div class="card-title" style="margin-top:12px">Detail Pengeluaran</div>${expenseHtml}<div class="report-row"><b>Total Out</b><b>Rp ${t.totalOut.toLocaleString('id-ID')}</b></div><div class="report-row"><b>Total Register</b><b>Rp ${t.totalRegister.toLocaleString('id-ID')}</b></div><div class="card-title" style="margin-top:12px">Stock</div><div class="report-row"><span>Stock Awal</span><span>${openingStock}</span></div><div class="report-row"><span>Restock</span><span>+${restock}</span></div><div class="report-row"><span>Stock Keluar</span><span>-${stockOut}</span></div><div class="report-row"><span>Stock Akhir</span><b>${endingStock}</b></div><div class="card-title" style="margin-top:12px">Rekonsiliasi</div><div class="report-row"><span>Expected</span><span>Rp ${t.totalRegister.toLocaleString('id-ID')}</span></div><div class="report-row"><span>Actual</span><span>Rp ${actual.toLocaleString('id-ID')}</span></div><div class="report-row"><b>Selisih</b><b class="${variance < 0 ? 'variance-negative':'variance-positive'}">Rp ${variance.toLocaleString('id-ID')}</b></div></div><div style="display:flex;gap:8px;flex-wrap:wrap"><button class="btn btn-primary btn-sm" onclick="Export.csv('${date}')">📄 CSV</button><button class="btn btn-secondary btn-sm" onclick="window.print()">🖨️ PDF</button><button class="btn btn-success btn-sm" onclick="Export.whatsapp('${date}')">📱 WhatsApp</button></div>`;
  };

  const originalShow = Navigation.show.bind(Navigation);
  Navigation.show = function(tab) {
    originalShow(tab);
    if (tab === 'laporan') {
      const r = document.getElementById('reportDate');
      if (r) r.value = operationalDate;
      Reports.generate();
    }
  };

  function bootPatch() {
    if (!db) return setTimeout(bootPatch, 120);
    ensureBackdateUI();
    refreshOperationalView();
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', () => setTimeout(bootPatch, 0));
  else setTimeout(bootPatch, 0);
})();