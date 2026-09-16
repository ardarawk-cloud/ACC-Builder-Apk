(() => {
  'use strict';

  const $ = (s) => document.querySelector(s);
  let playlist = null;
  let queue = [];
  let pos = 0;
  let mode = 'audio';
  let running = false;
  let stopAfterCurrent = false;
  let okCount = 0;
  let failCount = 0;

  function toast(message) {
    const node = $('#toast');
    if (!node) return;
    node.textContent = message;
    node.classList.add('show');
    clearTimeout(toast.timer);
    toast.timer = setTimeout(() => node.classList.remove('show'), 2800);
  }

  function isYoutubePlaylist(raw) {
    try {
      const u = new URL(String(raw || '').trim());
      if (!/^https?:$/.test(u.protocol)) return false;
      const h = u.hostname.toLowerCase().replace(/^www\./, '');
      const yt = h === 'youtube.com' || h.endsWith('.youtube.com') || h === 'youtu.be';
      return yt && !!u.searchParams.get('list');
    } catch (_) {
      return false;
    }
  }

  function ensureStyle() {
    if ($('#accYTPlaylistV6Style')) return;
    const style = document.createElement('style');
    style.id = 'accYTPlaylistV6Style';
    style.textContent = `
      .ytpl6{margin-top:14px;padding:14px;border:1px solid #293241;border-radius:18px;background:#0b1017}
      .ytpl6head{display:flex;align-items:center;justify-content:space-between;gap:12px}.ytpl6head b{font-size:13px}.ytpl6head span{font-size:10px;color:#ff6b7e}
      .ytpl6status{margin-top:10px;padding:11px 12px;border-radius:13px;background:#0f151e;border:1px solid #232c39;color:#98a3b5;font-size:11px;line-height:1.5}
      .ytpl6tools{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:11px}.ytpl6btn{height:44px;border:1px solid #303848;border-radius:13px;background:#151b24;color:#e7ebf1;font-size:11px;font-weight:900}.ytpl6btn.on{background:#2a1118;border-color:#ff3453;color:#ff6177}
      .ytpl6list{display:flex;flex-direction:column;gap:7px;max-height:420px;overflow:auto;margin-top:12px;padding-right:2px}.ytpl6row{display:grid;grid-template-columns:auto 1fr auto;align-items:center;gap:10px;padding:10px;border-radius:13px;border:1px solid #222b38;background:#10161f}.ytpl6row input{width:18px;height:18px;accent-color:#ff3151}.ytpl6copy{min-width:0}.ytpl6copy b{display:block;font-size:11px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.ytpl6copy span{display:block;margin-top:3px;font-size:9px;color:#7b8698;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.ytpl6state{font-size:9px;font-weight:900;color:#7f899b}.ytpl6state.ok{color:#65e3ad}.ytpl6state.fail{color:#ff6b7e}
      .ytpl6actions{display:grid;grid-template-columns:1fr auto;gap:8px;margin-top:10px}.ytpl6go{height:50px;border:0;border-radius:14px;background:linear-gradient(135deg,#ff3151,#e90d36);color:#fff;font-weight:950}.ytpl6stop{height:50px;padding:0 14px;border:1px solid #303848;border-radius:14px;background:#171d27;color:#f5f6fa;font-weight:900}
      .ytpl6progress{margin-top:11px}.ytpl6line{height:8px;border-radius:999px;overflow:hidden;background:#242b36}.ytpl6fill{height:100%;width:0;background:linear-gradient(90deg,#ff3151,#ff6c81);transition:width .25s}.ytpl6meta{display:flex;justify-content:space-between;gap:10px;margin-top:7px;font-size:10px;color:#7d8899}.ytpl6note{margin-top:10px;font-size:10px;line-height:1.5;color:#7d8899}
      .light .ytpl6,.light .ytpl6row,.light .ytpl6status{background:#fff;border-color:#dfe4ec;color:#202631}.light .ytpl6btn,.light .ytpl6stop{background:#edf0f5;color:#2c3440;border-color:#d9dee7}
    `;
    document.head.appendChild(style);
  }

  function ensureBox() {
    let box = $('#ytPlaylistV6Box');
    if (box) return box;
    const card = $('#resultCard');
    if (!card) return null;
    box = document.createElement('div');
    box.id = 'ytPlaylistV6Box';
    box.className = 'ytpl6';
    box.style.display = 'none';
    box.innerHTML = `
      <div class="ytpl6head"><b id="ytpl6Title">YouTube Playlist</b><span>PLAYLIST BATCH</span></div>
      <div id="ytpl6Status" class="ytpl6status">Menunggu playlist…</div>
      <div id="ytpl6Controls" hidden>
        <div class="ytpl6tools">
          <button id="ytpl6SelectAll" class="ytpl6btn" type="button">PILIH SEMUA</button>
          <button id="ytpl6Clear" class="ytpl6btn" type="button">KOSONGKAN</button>
          <button class="ytpl6btn on" type="button" data-ytpl6-mode="audio">♫ MP3 320 OUTPUT</button>
          <button class="ytpl6btn" type="button" data-ytpl6-mode="video">▣ VIDEO MP4</button>
        </div>
        <div id="ytpl6List" class="ytpl6list"></div>
        <div class="ytpl6actions">
          <button id="ytpl6Start" class="ytpl6go" type="button">↓ DOWNLOAD TERPILIH</button>
          <button id="ytpl6Stop" class="ytpl6stop" type="button">STOP</button>
        </div>
        <div class="ytpl6progress"><div class="ytpl6line"><div id="ytpl6Fill" class="ytpl6fill"></div></div><div class="ytpl6meta"><b id="ytpl6Count">0/0</b><span id="ytpl6Msg">Siap</span></div></div>
      </div>
      <div class="ytpl6note">Membaca playlist YouTube/YouTube Music asli dan menjaga urutannya. Track private, login-only, members-only, age-restricted, atau yang tidak tersedia akan dilewati tanpa menghentikan antrean. Gunakan hanya untuk media yang kamu miliki atau punya izin untuk menyimpan.</div>`;
    card.appendChild(box);

    box.querySelectorAll('[data-ytpl6-mode]').forEach((btn) => btn.addEventListener('click', () => {
      box.querySelectorAll('[data-ytpl6-mode]').forEach((b) => b.classList.remove('on'));
      btn.classList.add('on');
    }));
    $('#ytpl6SelectAll')?.addEventListener('click', () => box.querySelectorAll('.ytpl6row input').forEach((x) => { x.checked = true; }));
    $('#ytpl6Clear')?.addEventListener('click', () => box.querySelectorAll('.ytpl6row input').forEach((x) => { x.checked = false; }));
    $('#ytpl6Start')?.addEventListener('click', startQueue);
    $('#ytpl6Stop')?.addEventListener('click', () => {
      if (!running) return;
      stopAfterCurrent = true;
      $('#ytpl6Msg').textContent = 'Berhenti setelah track saat ini…';
    });
    return box;
  }

  function hideOthers() {
    ['#mainActionBtn', '#directBtn', '#favoriteBtn', '#ytBox', '#socialV4Box', '#playlistV5Box'].forEach((selector) => {
      const node = $(selector);
      if (node) node.style.display = 'none';
    });
  }

  function renderLoading(raw) {
    const card = $('#resultCard');
    const box = ensureBox();
    if (!card || !box) return;
    hideOthers();
    box.style.display = 'block';
    $('#ytpl6Controls').hidden = true;
    $('#ytpl6Status').textContent = 'Membaca playlist YouTube/YouTube Music…';
    $('#fileBadge').textContent = 'YT';
    $('#fileName').textContent = raw.includes('music.youtube.com') ? 'YouTube Music playlist' : 'YouTube playlist';
    $('#fileDomain').textContent = new URL(raw).hostname;
    $('#fileType').textContent = 'PLAYLIST';
    $('#fileSource').textContent = 'YouTube / YouTube Music';
    const status = $('#resultStatus');
    if (status) {
      status.textContent = 'IMPORT';
      status.className = 'statuspill';
    }
    card.classList.add('show');
  }

  function renderPlaylist(data) {
    playlist = data;
    const source = data.source || 'YouTube';
    $('#ytpl6Title').textContent = `${source} Playlist`;
    $('#ytpl6Status').textContent = `${data.title || source + ' Playlist'} · ${data.tracks.length} track`;
    const list = $('#ytpl6List');
    list.innerHTML = '';
    data.tracks.forEach((track, i) => {
      const row = document.createElement('label');
      row.className = 'ytpl6row';
      row.dataset.index = String(i);
      row.innerHTML = `<input type="checkbox" checked><div class="ytpl6copy"><b>${escapeHtml(`${String(i + 1).padStart(2, '0')}. ${track.title}`)}</b><span>${escapeHtml(track.artist || source)}</span></div><span class="ytpl6state">SIAP</span>`;
      list.appendChild(row);
    });
    $('#ytpl6Controls').hidden = false;
    $('#ytpl6Fill').style.width = '0%';
    $('#ytpl6Count').textContent = `0/${data.tracks.length}`;
    $('#ytpl6Msg').textContent = 'Pilih track lalu download';
  }

  function escapeHtml(text) {
    return String(text || '').replace(/[&<>'"]/g, (c) => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]));
  }

  function selectedTracks() {
    if (!playlist) return [];
    return Array.from(document.querySelectorAll('#ytpl6List .ytpl6row')).filter((row) => row.querySelector('input')?.checked).map((row) => {
      const index = Number(row.dataset.index);
      return { ...playlist.tracks[index], index };
    });
  }

  function startQueue() {
    if (running) return;
    if (!window.ACCNative || typeof window.ACCNative.downloadYoutubePlaylistTrack !== 'function') {
      toast('Install ACC Media v6 untuk playlist YouTube.');
      return;
    }
    queue = selectedTracks();
    if (!queue.length) {
      toast('Pilih minimal satu track.');
      return;
    }
    const active = document.querySelector('#ytPlaylistV6Box [data-ytpl6-mode].on');
    mode = active?.dataset.ytpl6Mode === 'video' ? 'video' : 'audio';
    pos = 0;
    okCount = 0;
    failCount = 0;
    stopAfterCurrent = false;
    running = true;
    $('#ytpl6Start').disabled = true;
    $('#ytpl6Msg').textContent = `Mulai ${queue.length} track…`;
    runNext();
  }

  function runNext() {
    if (!running) return;
    if (stopAfterCurrent || pos >= queue.length) {
      running = false;
      $('#ytpl6Start').disabled = false;
      const done = okCount + failCount;
      $('#ytpl6Fill').style.width = `${queue.length ? Math.round(done / queue.length * 100) : 0}%`;
      $('#ytpl6Count').textContent = `${done}/${queue.length}`;
      $('#ytpl6Msg').textContent = stopAfterCurrent ? `Dihentikan · ${okCount} berhasil · ${failCount} gagal` : `Selesai · ${okCount} berhasil · ${failCount} gagal`;
      toast($('#ytpl6Msg').textContent);
      return;
    }
    const track = queue[pos];
    const row = document.querySelector(`#ytpl6List .ytpl6row[data-index="${track.index}"]`);
    const state = row?.querySelector('.ytpl6state');
    if (state) {
      state.textContent = 'PROSES';
      state.className = 'ytpl6state';
    }
    $('#ytpl6Count').textContent = `${pos + 1}/${queue.length}`;
    $('#ytpl6Msg').textContent = track.title;
    window.ACCNative.downloadYoutubePlaylistTrack(track.url, mode, track.index);
  }

  window.ACCYTPlaylist = {
    onPlaylist(ok, payload) {
      if (!ok) {
        $('#ytpl6Status').textContent = payload || 'Gagal membaca playlist.';
        toast(payload || 'Gagal membaca playlist.');
        return;
      }
      try {
        const data = JSON.parse(payload);
        if (!data.tracks || !data.tracks.length) throw new Error('Playlist kosong');
        renderPlaylist(data);
      } catch (e) {
        $('#ytpl6Status').textContent = e.message || 'Metadata playlist tidak valid';
      }
    },
    onTrackProgress(index, value, message) {
      if (!running || !queue[pos] || queue[pos].index !== index) return;
      const base = pos / queue.length;
      const part = Math.max(0, Math.min(100, Number(value) || 0)) / 100 / queue.length;
      $('#ytpl6Fill').style.width = `${Math.round((base + part) * 100)}%`;
      if (message) $('#ytpl6Msg').textContent = message;
    },
    onTrackComplete(index, ok, message) {
      const row = document.querySelector(`#ytpl6List .ytpl6row[data-index="${index}"]`);
      const state = row?.querySelector('.ytpl6state');
      if (state) {
        state.textContent = ok ? 'OK' : 'SKIP';
        state.className = `ytpl6state ${ok ? 'ok' : 'fail'}`;
      }
      if (ok) okCount += 1; else failCount += 1;
      pos += 1;
      if (message) $('#ytpl6Msg').textContent = message;
      setTimeout(runNext, 250);
    }
  };

  function installIntercept() {
    const analyze = $('#analyzeBtn');
    if (!analyze) return;
    analyze.addEventListener('click', (event) => {
      const raw = ($('#urlInput')?.value || '').trim();
      if (!isYoutubePlaylist(raw)) return;
      event.preventDefault();
      event.stopImmediatePropagation();
      renderLoading(raw);
      if (!window.ACCNative || typeof window.ACCNative.importYoutubePlaylist !== 'function') {
        $('#ytpl6Status').textContent = 'Fitur playlist YouTube tersedia di ACC Media v6.';
        return;
      }
      window.ACCNative.importYoutubePlaylist(raw);
    }, true);
  }

  function install() {
    ensureStyle();
    ensureBox();
    installIntercept();
    const version = $('.version');
    if (version) version.textContent = 'v6.0.0';
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', install, { once: true });
  else install();
})();
