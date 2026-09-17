(() => {
  'use strict';
  const $ = (id) => document.getElementById(id);

  const shade = $('popupShade');
  const hideAllPopups = () => {
    document.querySelectorAll('.ui-popup').forEach((p) => {
      p.classList.remove('open');
      p.hidden = true;
    });
    if (shade) shade.hidden = true;
  };
  const showPopup = (id) => {
    document.querySelectorAll('.ui-popup').forEach((p) => {
      p.classList.remove('open');
      p.hidden = true;
    });
    const popup = $(id);
    if (!popup) return;
    popup.hidden = false;
    if (shade) shade.hidden = false;
    requestAnimationFrame(() => popup.classList.add('open'));
  };

  // Remove the duplicated top/center controls from v1.1.
  document.querySelectorAll('.pro-tools .tool-btn[data-popup="eqPopup"], .pro-tools .tool-btn[data-popup="mixerPopup"], .pro-tools .tool-btn[data-popup="beatPopup"]').forEach((el) => el.remove());
  document.querySelector('.center-tools')?.remove();

  // The center strip is now an actual mixer, not a second navigation menu.
  const center = document.querySelector('.center-mixer');
  if (center) {
    const masterButton = document.createElement('button');
    masterButton.type = 'button';
    masterButton.className = 'v12-mix-button';
    masterButton.textContent = 'MASTER / CUE';
    masterButton.addEventListener('click', () => showPopup('mixerPopup'));
    const meters = center.querySelector('.meters');
    center.insertBefore(masterButton, meters || null);
  }

  // Keep performance controls as popups close to each deck.
  ['A', 'B'].forEach((id) => {
    const deck = document.querySelector(`.deck[data-deck="${id}"]`);
    const body = deck?.querySelector('.deck-body');
    if (!body) return;
    const tools = document.createElement('div');
    tools.className = 'deck-float-tools';
    const eq = document.createElement('button');
    eq.type = 'button';
    eq.textContent = 'EQ';
    eq.addEventListener('click', () => showPopup('eqPopup'));
    const more = document.createElement('button');
    more.type = 'button';
    more.textContent = '•••';
    more.addEventListener('click', () => showPopup('beatPopup'));
    tools.append(eq, more);
    body.appendChild(tools);
  });

  // Add Drive / Android Files source next to the streaming library.
  const topTools = document.querySelector('.pro-tools');
  if (topTools && !$('v12DriveBtn')) {
    const driveBtn = document.createElement('button');
    driveBtn.id = 'v12DriveBtn';
    driveBtn.className = 'tool-btn';
    driveBtn.type = 'button';
    driveBtn.innerHTML = '<strong>☁</strong><span>DRIVE</span>';
    const settings = $('uiSettingsBtn');
    topTools.insertBefore(driveBtn, settings || null);
    driveBtn.addEventListener('click', () => showPopup('drivePopup'));
  }

  const popup = document.createElement('section');
  popup.id = 'drivePopup';
  popup.className = 'ui-popup';
  popup.hidden = true;
  popup.innerHTML = `
    <div class="popup-head">
      <div><strong>DRIVE / FILES</strong><div style="font-size:9px;color:#84909c;margin-top:3px">Google Drive, penyimpanan HP, SD card, atau provider Android lain</div></div>
      <button class="popup-close" id="driveCloseBtn" type="button">✕</button>
    </div>
    <div class="drive-hero">
      <div><strong>Cloud music tanpa memenuhi storage HP</strong><p>Tekan OPEN DRIVE / FILES. Di file picker Android pilih Google Drive lalu ganti akun dari menu Drive bila kamu punya beberapa akun. Pilih satu atau banyak lagu sekaligus.</p></div>
      <button id="drivePickerBtn" class="drive-open" type="button">OPEN DRIVE / FILES</button>
    </div>
    <input id="driveInput" type="file" accept="audio/*,.mp3,.m4a,.aac,.wav,.flac,.ogg,.opus" multiple hidden>
    <div id="driveStatus" style="font-size:9px;color:#87929e;margin-top:10px">Belum ada file dipilih.</div>
    <div id="driveList" class="drive-list"><div class="drive-empty">Pilih lagu dari Drive / Files. File hanya dibaca saat dipakai dan tidak disalin permanen ke library ACC DJ.</div></div>`;
  document.body.appendChild(popup);

  $('driveCloseBtn')?.addEventListener('click', hideAllPopups);
  $('drivePickerBtn')?.addEventListener('click', () => $('driveInput')?.click());

  const humanSize = (bytes) => {
    const n = Number(bytes) || 0;
    if (n < 1024 * 1024) return `${Math.max(1, Math.round(n / 1024))} KB`;
    return `${(n / (1024 * 1024)).toFixed(1)} MB`;
  };
  const niceTitle = (name) => String(name || 'Local Track').replace(/\.[^.]+$/, '').replace(/[_]+/g, ' ');

  function renderDriveFiles(files) {
    const root = $('driveList');
    const status = $('driveStatus');
    if (!root || !status) return;
    root.innerHTML = '';
    if (!files.length) {
      status.textContent = 'Tidak ada file audio dipilih.';
      root.innerHTML = '<div class="drive-empty">Pilih MP3 / M4A / AAC / WAV / FLAC / OGG dari Drive atau penyimpanan HP.</div>';
      return;
    }
    status.textContent = `${files.length} lagu siap dipilih untuk Deck A / B.`;
    files.forEach((file) => {
      const row = document.createElement('div');
      row.className = 'drive-track';
      const meta = document.createElement('div');
      const title = document.createElement('b');
      title.textContent = niceTitle(file.name);
      const sub = document.createElement('span');
      sub.textContent = `${file.type || 'audio'} • ${humanSize(file.size)}`;
      meta.append(title, sub);
      const actions = document.createElement('div');
      actions.className = 'drive-load';
      ['A', 'B'].forEach((deckId) => {
        const button = document.createElement('button');
        button.type = 'button';
        button.textContent = `LOAD ${deckId}`;
        button.addEventListener('click', async () => {
          if (!window.ACCDJCore?.loadLocal) {
            status.textContent = 'Local loader belum siap. Tutup lalu buka ulang ACC DJ.';
            return;
          }
          try {
            status.textContent = `Loading ${file.name} → Deck ${deckId}…`;
            await window.ACCDJCore.loadLocal(deckId, file);
            status.textContent = `${file.name} → Deck ${deckId} siap.`;
            hideAllPopups();
          } catch (error) {
            console.error(error);
            status.textContent = `Gagal membuka ${file.name}. Coba format audio lain atau download file Drive dulu.`;
          }
        });
        actions.appendChild(button);
      });
      row.append(meta, actions);
      root.appendChild(row);
    });
  }

  $('driveInput')?.addEventListener('change', (event) => renderDriveFiles(Array.from(event.target.files || [])));
  shade?.addEventListener('click', () => {
    if (!$('drivePopup')?.hidden) hideAllPopups();
  });
})();
