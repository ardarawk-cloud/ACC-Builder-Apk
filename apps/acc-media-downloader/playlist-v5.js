(() => {
  'use strict';

  const $ = (s) => document.querySelector(s);
  let playlistState = null;
  let queue = [];
  let queuePos = 0;
  let queueMode = 'audio';
  let queueRunning = false;
  let stopAfterCurrent = false;
  let successCount = 0;
  let failCount = 0;

  function toast(message) {
    const node = $('#toast');
    if (!node) return;
    node.textContent = message;
    node.classList.add('show');
    clearTimeout(toast.timer);
    toast.timer = setTimeout(() => node.classList.remove('show'), 2800);
  }

  function spotifyPlaylistUrl(raw) {
    try {
      const u = new URL(String(raw || '').trim());
      if (!/^https?:$/.test(u.protocol)) return false;
      const h = u.hostname.toLowerCase().replace(/^www\./, '');
      if (h === 'spotify.link') return true;
      if (!(h === 'open.spotify.com' || h === 'spotify.com' || h.endsWith('.spotify.com'))) return false;
      return u.pathname.split('/').filter(Boolean).includes('playlist');
    } catch (_) {
      return false;
    }
  }

  function ensureStyle() {
    if ($('#accPlaylistV5Style')) return;
    const style = document.createElement('style');
    style.id = 'accPlaylistV5Style';
    style.textContent = `
      .pl5{margin-top:14px;padding:14px;border:1px solid #293241;border-radius:18px;background:#0b1017}
      .pl5head{display:flex;align-items:center;justify-content:space-between;gap:12px}.pl5head b{font-size:13px}.pl5head span{font-size:10px;color:#65e3ad}
      .pl5status{margin-top:10px;padding:11px 12px;border-radius:13px;background:#0f151e;border:1px solid #232c39;color:#98a3b5;font-size:11px;line-height:1.5}
      .pl5tools{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:11px}.pl5btn{height:44px;border:1px solid #303848;border-radius:13px;background:#151b24;color:#e7ebf1;font-size:11px;font-weight:900}.pl5btn.on{background:#2a1118;border-color:#ff3453;color:#ff6177}
      .pl5actions{display:grid;grid-template-columns:1fr auto;gap:8px;margin-top:10px}.pl5go{height:50px;border:0;border-radius:14px;background:linear-gradient(135deg,#ff3151,#e90d36);color:#fff;font-weight:950}.pl5stop{height:50px;padding:0 14px;border:1px solid #303848;border-radius:14px;background:#171d27;color:#f5f6fa;font-weight:900}
      .pl5list{display:flex;flex-direction:column;gap:7px;max-height:420px;overflow:auto;margin-top:12px;padding-right:2px}.pl5row{display:grid;grid-template-columns:auto 1fr auto;align-items:center;gap:10px;padding:10px;border-radius:13px;border:1px solid #222b38;background:#10161f}.pl5row input{width:18px;height:18px;accent-color:#ff3151}.pl5copy{min-width:0}.pl5copy b{display:block;font-size:11px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.pl5copy span{display:block;margin-top:3px;font-size:9px;color:#7b8698;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.pl5state{font-size:9px;font-weight:900;color:#7f899b}.pl5state.ok{color:#65e3ad}.pl5state.fail{color:#ff6b7e}.pl5progress{margin-top:11px}.pl5line{height:8px;border-radius:999px;overflow:hidden;background:#242b36}.pl5fill{height:100%;width:0;background:linear-gradient(90deg,#ff3151,#ff6c81);transition:width .25s}.pl5meta{display:flex;justify-content:space-between;gap:10px;margin-top:7px;font-size:10px;color:#7d8899}.pl5note{margin-top:10px;font-size:10px;line-height:1.5;color:#7d8899}
      .light .pl5,.light .pl5row,.light .pl5status{background:#fff;border-color:#dfe4ec;color:#202631}.light .pl5btn,.light .pl5stop{background:#edf0f5;color:#2c3440;border-color:#d9dee7}
    `;
    document.head.appendChild(style);
  }

  function ensureBox() {
    let box = $('#playlistV5Box');
    if (box) return box;
    const card = $('#resultCard');
    if (!card) return null;
    box = document.createElement('div');
    box.id = 'playlistV5Box';
    box.className = 'pl5';
    box.style.display = 'none';
    box.innerHTML = `
      <div class="pl5head"><b id="pl5Title">Spotify Playlist</b><span>PLAYLIST BATCH</span></div>
      <div id="pl5Status" class="pl5status">Menunggu playlist…</div>
      <div id="pl5Controls" hidden>
        <div class="pl5tools">
          <button id="pl5SelectAll" class="pl5btn" type="button">PILIH SEMUA</button>
          <button id="pl5Clear" class="pl5btn" type="button">KOSONGKAN</button>
          <button class="pl5btn on" type="button" data-pl5-mode="audio">♫ MP3 320 OUTPUT</button>
          <button class="pl5btn" type="button" data-pl5-mode="video">▣ VIDEO MP4</button>
        </div>
        <div id="pl5List" class="pl5list"></div>
        <div class="pl5actions">
          <button id="pl5Start" class="pl5go" type="button">↓ DOWNLOAD TERPILIH</button>
          <button id="pl5Stop" class="pl5stop" type="button">STOP</button>
        </div>
        <div class="pl5progress"><div class="pl5line"><div id="pl5Fill" class="pl5fill"></div></div><div class="pl5meta"><b id="pl5Count">0/0</b><span id="pl5Msg">Siap</span></div></div>
      </div>
      <div class="pl5note">Spotify hanya dipakai untuk membaca judul/artis playlist. Audio/video dicari dari sumber publik YouTube/YouTube Music melalui engine ACC. Hasil pencocokan otomatis bisa berbeda versi/remix; cek file sebelum event. Tidak ada bypass Spotify Premium, login, private content, paywall, atau DRM.</div>`;
    card.appendChild(box);

    box.querySelectorAll('[data-pl5-mode]').forEach((btn) => {
      btn.addEventListener('click', () => {
        box.querySelectorAll('[data-pl5-mode]').forEach((b) => b.classList.remove('on'));
        btn.classList.add('on');
      });
    });
    $('#pl5SelectAll')?.addEventListener('click', () => box.querySelectorAll('.pl5row input').forEach((x) => { x.checked = true; }));
    $('#pl5Clear')?.addEventListener('click', () => box.querySelectorAll('.pl5row input').forEach((x) => { x.checked = false; }));
    $('#pl5Start')?.addEventListener('click', startQueue);
    $('#pl5Stop')?.addEventListener('click', () => {
      if (!queueRunning) return;
      stopAfterCurrent = true;
      $('#pl5Msg').textContent = 'Berhenti setelah lagu saat ini…';
    });
    return box;
  }

  function hideOtherModes() {
    ['#mainActionBtn', '#directBtn', '#favoriteBtn', '#ytBox', '#socialV4Box'].forEach((selector) => {
      const node = $(selector);
      if (node) node.style.display = 'none';
    });
  }

  function renderLoading(raw) {
    const card = $('#resultCard');
    const box = ensureBox();
    if (!card || !box) return;
    hideOtherModes();
    box.style.display = 'block';
    $('#pl5Controls').hidden = true;
    $('#pl5Status').textContent = 'Membaca daftar lagu dari Spotify…';
    $('#fileBadge').textContent = 'PL';
    $('#fileName').textContent = 'Spotify playlist';
    $('#fileDomain').textContent = new URL(raw).hostname;
    $('#fileType').textContent = 'PLAYLIST';
    $('#fileSource').textContent = 'Spotify → YouTube search';
    const status = $('#resultStatus');
    if (status) {
      status.textContent = 'IMPORT';
      status.className = 'statuspill';
      status.style.background = '#123126';
      status.style.color = '#65e3ad';
    }
    card.classList.add('show');
  }

  function renderTracks(data) {
    playlistState = data;
    const title = data.title || 'Spotify Playlist';
    $('#pl5Title').textContent = title;
    $('#fileName').textContent = title;
    $('#pl5Status').textContent = `${data.tracks.length} lagu terbaca. Pilih lagu lalu download berurutan.`;
    const list = $('#pl5List');
    list.innerHTML = '';
    data.tracks.forEach((track, i) => {
      const row = document.createElement('label');
      row.className = 'pl5row';
      row.dataset.index = String(i);
      row.innerHTML = `<input type="checkbox" checked><div class="pl5copy"><b>${escapeHtml(`${String(i + 1).padStart(2, '0')}. ${track.title}`)}</b><span>${escapeHtml(track.artist || 'Artist tidak terbaca')}</span></div><span class="pl5state" id="pl5State${i}">SIAP</span>`;
      list.appendChild(row);
    });
    $('#pl5Controls').hidden = false;
    $('#pl5Count').textContent = `0/${data.tracks.length}`;
    $('#pl5Msg').textContent = 'Siap';
    $('#pl5Fill').style.width = '0%';
  }

  function escapeHtml(value) {
    return String(value || '').replace(/[&<>'"]/g, (c) => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]));
  }

  function startQueue() {
    if (queueRunning || !playlistState) return;
    if (!window.ACCNative || typeof window.ACCNative.downloadPlaylistTrack !== 'function') {
      toast('Playlist batch hanya tersedia di APK ACC Media v5.');
      return;
    }
    queue = Array.from(document.querySelectorAll('#pl5List .pl5row')).filter((row) => row.querySelector('input')?.checked).map((row) => Number(row.dataset.index));
    if (!queue.length) {
      toast('Pilih minimal satu lagu.');
      return;
    }
    const active = document.querySelector('#playlistV5Box [data-pl5-mode].on');
    queueMode = active?.dataset.pl5Mode === 'video' ? 'video' : 'audio';
    queuePos = 0;
    queueRunning = true;
    stopAfterCurrent = false;
    successCount = 0;
    failCount = 0;
    $('#pl5Start').disabled = true;
    $('#pl5Msg').textContent = 'Memulai antrean…';
    downloadNext();
  }

  function downloadNext() {
    if (!queueRunning) return;
    if (stopAfterCurrent || queuePos >= queue.length) {
      finishQueue(stopAfterCurrent ? 'Antrean dihentikan.' : 'Antrean selesai.');
      return;
    }
    const index = queue[queuePos];
    const track = playlistState.tracks[index];
    const state = $(`#pl5State${index}`);
    if (state) {
      state.textContent = 'PROSES';
      state.className = 'pl5state';
    }
    $('#pl5Msg').textContent = `${track.title} — ${track.artist || ''}`;
    window.ACCNative.downloadPlaylistTrack(track.query, queueMode, index);
  }

  function finishQueue(message) {
    queueRunning = false;
    $('#pl5Start').disabled = false;
    $('#pl5Msg').textContent = `${message} ${successCount} sukses · ${failCount} gagal`;
    const done = queue.length ? Math.round(((successCount + failCount) / queue.length) * 100) : 0;
    $('#pl5Fill').style.width = `${done}%`;
    toast(`${successCount} sukses · ${failCount} gagal`);
  }

  function handleAnalyze(raw, event) {
    if (!spotifyPlaylistUrl(raw)) return false;
    event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation();
    renderLoading(raw);
    if (!window.ACCNative || typeof window.ACCNative.importSpotifyPlaylist !== 'function') {
      $('#pl5Status').textContent = 'APK ini belum punya engine playlist v5.';
      toast('Install ACC Media v5 untuk playlist batch.');
      return true;
    }
    window.ACCNative.importSpotifyPlaylist(raw);
    return true;
  }

  window.ACCPlaylist = {
    isPlaylistUrl: spotifyPlaylistUrl,
    onSpotifyPlaylist(ok, payload) {
      if (!ok) {
        $('#pl5Status').textContent = payload || 'Gagal membaca playlist Spotify.';
        toast(payload || 'Gagal membaca playlist.');
        return;
      }
      try {
        const data = JSON.parse(payload);
        renderTracks(data);
        toast(`${data.tracks.length} lagu berhasil diimpor.`);
      } catch (_) {
        $('#pl5Status').textContent = 'Data playlist tidak dapat dibaca.';
      }
    },
    onTrackProgress(index, value, message) {
      if (!queueRunning) return;
      const local = Math.max(0, Math.min(100, Number(value) || 0));
      const base = queuePos;
      const overall = ((base + local / 100) / Math.max(queue.length, 1)) * 100;
      $('#pl5Fill').style.width = `${overall}%`;
      $('#pl5Count').textContent = `${base + 1}/${queue.length}`;
      if (message) $('#pl5Msg').textContent = message;
    },
    onTrackComplete(index, ok, message) {
      const state = $(`#pl5State${index}`);
      if (state) {
        state.textContent = ok ? 'OK' : 'GAGAL';
        state.className = `pl5state ${ok ? 'ok' : 'fail'}`;
      }
      if (ok) successCount += 1; else failCount += 1;
      queuePos += 1;
      const overall = (queuePos / Math.max(queue.length, 1)) * 100;
      $('#pl5Fill').style.width = `${overall}%`;
      $('#pl5Count').textContent = `${queuePos}/${queue.length}`;
      if (message) $('#pl5Msg').textContent = message;
      setTimeout(downloadNext, 450);
    }
  };

  function install() {
    ensureStyle();
    ensureBox();
    document.addEventListener('click', (event) => {
      const target = event.target;
      if (!(target instanceof Element) || target.id !== 'analyzeBtn') return;
      const raw = ($('#urlInput')?.value || '').trim();
      handleAnalyze(raw, event);
    }, true);
    const version = $('.version');
    if (version) version.textContent = 'v5.0.0';
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', install, {once:true});
  else install();
})();
