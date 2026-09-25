(() => {
  document.documentElement.dataset.pslDesktop = '1';
  document.title = 'Papa Sauce Lab Desktop';

  const addBadge = () => {
    const actions = document.querySelector('.header-actions');
    if (actions && !document.querySelector('.psl-desktop-badge')) {
      actions.insertAdjacentHTML('afterbegin', '<span class="psl-desktop-badge">DESKTOP</span>');
    }
  };

  async function fullBackup() {
    const stores = ['transactions','stock_movements','snapshots','settings','corrections','periods'];
    const data = { schema: 'PSL_DESKTOP_BACKUP_V1', exportDate: new Date().toISOString() };
    for (const store of stores) {
      try { data[store] = await dbGetAll(store); } catch (_) { data[store] = []; }
    }
    data.stock = data.stock_movements;
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'papa-sauce-lab-backup-' + new Date().toISOString().slice(0,10) + '.json';
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  async function fullImport(file) {
    if (!file) return;
    const text = await file.text();
    const data = JSON.parse(text);
    const mapping = [
      ['transactions', data.transactions],
      ['stock_movements', data.stock_movements || data.stock],
      ['snapshots', data.snapshots],
      ['settings', data.settings],
      ['corrections', data.corrections],
      ['periods', data.periods]
    ];
    let count = 0;
    for (const [store, rows] of mapping) {
      if (!Array.isArray(rows)) continue;
      for (const row of rows) {
        await dbPut(store, row);
        count++;
      }
    }
    if (typeof refreshV5 === 'function') await refreshV5();
    else {
      if (UI.updateDashboard) await UI.updateDashboard();
      if (UI.renderStock) await UI.renderStock();
      if (UI.renderHistory) await UI.renderHistory();
    }
    alert('Import selesai · ' + count + ' record masuk/terbarui.');
  }

  Export.jsonBackup = fullBackup;
  Export.jsonImport = fullImport;
  Import.json = async function(event) {
    const file = event.target.files && event.target.files[0];
    if (file) await fullImport(file);
    event.target.value = '';
  };

  UI.showSettings = function() {
    detailTitle.textContent = 'Pengaturan Desktop';
    detailContent.innerHTML = `
      <div class="form-group">
        <label class="form-label">Role</label>
        <select class="form-select" id="settingRole" onchange="Settings.setRole(this.value)">
          <option value="OWNER" ${currentRole==='OWNER'?'selected':''}>OWNER</option>
          <option value="STAFF" ${currentRole==='STAFF'?'selected':''}>STAFF</option>
        </select>
      </div>
      <div style="padding:12px;border-radius:12px;background:#fff8ec;border:1px solid #eadbc4;font-size:12px;line-height:1.5;margin-bottom:14px">
        <b>Pindah data HP → PC:</b><br>
        Di HP buka ⚙️ → Export JSON Backup. Pindahkan file JSON ke PC, lalu Import di sini.
      </div>
      <button class="btn btn-secondary btn-block" onclick="Export.jsonBackup()" style="margin-bottom:8px">📥 Export Backup Lengkap</button>
      <input type="file" accept=".json,application/json" onchange="Import.json(event)" style="display:none" id="importFile">
      <button class="btn btn-primary btn-block" onclick="document.getElementById('importFile').click()">📤 Import Backup dari HP / PC</button>
      <p style="font-size:11px;color:#777;margin-top:10px">Data Desktop tersimpan lokal di PC. Belum sinkron otomatis dengan HP.</p>
    `;
    UI.showModal('detail');
  };

  document.addEventListener('keydown', (e) => {
    if (!e.ctrlKey || e.altKey || e.metaKey) return;
    const map = { '1':'dashboard', '2':'transaksi', '3':'stock', '4':'laporan', '5':'riwayat' };
    if (map[e.key]) {
      e.preventDefault();
      Navigation.show(map[e.key]);
    }
  });

  addBadge();
  console.log('Papa Sauce Lab Desktop bridge active');
})();
