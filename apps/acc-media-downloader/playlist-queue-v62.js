(() => {
  'use strict';

  const $ = (s) => document.querySelector(s);
  const state = {
    playlist: null,
    queue: [],
    pos: 0,
    mode: 'audio',
    running: false,
    stopAfterCurrent: false,
    ok: 0,
    fail: 0,
    watchdog: null,
    currentIndex: null
  };

  function isYoutubePlaylist(raw) {
    try {
      const u = new URL(String(raw || '').trim());
      const host = u.hostname.toLowerCase().replace(/^www\./, '');
      const yt = host === 'youtube.com' || host.endsWith('.youtube.com') || host === 'youtu.be';
      return yt && !!u.searchParams.get('list');
    } catch (_) {
      return false;
    }
  }

  function toast(message) {
    const node = $('#toast');
    if (!node) return;
    node.textContent = message;
    node.classList.add('show');
    clearTimeout(toast.timer);
    toast.timer = setTimeout(() => node.classList.remove('show'), 2600);
  }

  function setVersion() {
    const v = $('.version');
    if (v) v.textContent = 'v6.2.0';
  }

  function rowFor(index) {
    return document.querySelector(`#ytpl6List .ytpl6row[data-index="${index}"]`);
  }

  function setRowState(index, text, cls) {
    const node = rowFor(index)?.querySelector('.ytpl6state');
    if (!node) return;
    node.textContent = text;
    node.className = `ytpl6state${cls ? ` ${cls}` : ''}`;
  }

  function progress(doneFraction, message) {
    const fill = $('#ytpl6Fill');
    const msg = $('#ytpl6Msg');
    if (fill) fill.style.width = `${Math.max(0, Math.min(100, Math.round(doneFraction * 100)))}%`;
    if (msg && message) msg.textContent = message;
  }

  function finishQueue() {
    clearTimeout(state.watchdog);
    state.watchdog = null;
    state.running = false;
    state.currentIndex = null;
    const start = $('#ytpl6Start');
    if (start) start.disabled = false;
    const done = state.ok + state.fail;
    const count = $('#ytpl6Count');
    if (count) count.textContent = `${done}/${state.queue.length}`;
    progress(state.queue.length ? done / state.queue.length : 0,
      state.stopAfterCurrent
        ? `Dihentikan · ${state.ok} berhasil · ${state.fail} gagal`
        : `Selesai · ${state.ok} berhasil · ${state.fail} gagal`);
    toast(state.stopAfterCurrent ? 'Antrean dihentikan.' : `Playlist selesai: ${state.ok} berhasil, ${state.fail} gagal.`);
  }

  function advance(ok, message, index) {
    if (!state.running) return;
    if (state.currentIndex !== index) return; // ignore stale/native callbacks from an older track
    clearTimeout(state.watchdog);
    state.watchdog = null;
    setRowState(index, ok ? 'OK' : 'SKIP', ok ? 'ok' : 'fail');
    if (ok) state.ok += 1; else state.fail += 1;
    state.pos += 1;
    state.currentIndex = null;
    const done = state.ok + state.fail;
    const count = $('#ytpl6Count');
    if (count) count.textContent = `${done}/${state.queue.length}`;
    progress(state.queue.length ? done / state.queue.length : 0, message || (ok ? 'Tersimpan' : 'Dilewati'));
    if (state.stopAfterCurrent || state.pos >= state.queue.length) {
      finishQueue();
      return;
    }
    setTimeout(runNext, 450);
  }

  function runNext() {
    if (!state.running) return;
    if (state.stopAfterCurrent || state.pos >= state.queue.length) {
      finishQueue();
      return;
    }
    const track = state.queue[state.pos];
    state.currentIndex = track.index;
    setRowState(track.index, 'PROSES', '');
    const count = $('#ytpl6Count');
    if (count) count.textContent = `${state.pos + 1}/${state.queue.length}`;
    progress(state.pos / state.queue.length, track.title || `Track ${state.pos + 1}`);

    // Safety net: a lost native callback must not freeze a 50+ song wedding playlist.
    clearTimeout(state.watchdog);
    state.watchdog = setTimeout(() => {
      if (!state.running || state.currentIndex !== track.index) return;
      advance(false, 'Timeout · track dilewati, lanjut berikutnya', track.index);
    }, 90000);

    try {
      window.ACCNative.downloadYoutubePlaylistTrack(track.url, state.mode, track.index);
    } catch (e) {
      advance(false, 'Gagal memulai track · lanjut berikutnya', track.index);
    }
  }

  function selectedTracks() {
    if (!state.playlist?.tracks?.length) return [];
    return Array.from(document.querySelectorAll('#ytpl6List .ytpl6row'))
      .filter((row) => row.querySelector('input')?.checked)
      .map((row) => {
        const index = Number(row.dataset.index);
        return { ...state.playlist.tracks[index], index };
      });
  }

  function startQueue(event) {
    event?.preventDefault();
    event?.stopImmediatePropagation();
    if (state.running) return;
    if (!state.playlist?.tracks?.length) {
      toast('Analisis playlist dulu.');
      return;
    }
    if (!window.ACCNative || typeof window.ACCNative.downloadYoutubePlaylistTrack !== 'function') {
      toast('Engine playlist native tidak tersedia.');
      return;
    }
    state.queue = selectedTracks();
    if (!state.queue.length) {
      toast('Pilih minimal satu track.');
      return;
    }
    const active = document.querySelector('#ytPlaylistV6Box [data-ytpl6-mode].on');
    state.mode = active?.dataset.ytpl6Mode === 'video' ? 'video' : 'audio';
    state.pos = 0;
    state.ok = 0;
    state.fail = 0;
    state.stopAfterCurrent = false;
    state.running = true;
    state.currentIndex = null;
    const start = $('#ytpl6Start');
    if (start) start.disabled = true;
    $('#ytpl6List')?.querySelectorAll('.ytpl6row').forEach((row) => {
      const i = Number(row.dataset.index);
      if (state.queue.some((t) => t.index === i)) setRowState(i, 'SIAP', '');
    });
    progress(0, `Mulai ${state.queue.length} track…`);
    runNext();
  }

  function installPlaylistCallbacks() {
    if (!window.ACCYTPlaylist || window.ACCYTPlaylist.__v62Wrapped) return false;
    const originalPlaylist = window.ACCYTPlaylist.onPlaylist;

    window.ACCYTPlaylist.onPlaylist = function(ok, payload) {
      if (ok) {
        try {
          const parsed = JSON.parse(payload);
          if (parsed?.tracks?.length) state.playlist = parsed;
        } catch (_) {
          state.playlist = null;
        }
      } else {
        state.playlist = null;
      }
      return typeof originalPlaylist === 'function'
        ? originalPlaylist.call(this, ok, payload)
        : undefined;
    };

    window.ACCYTPlaylist.onTrackProgress = function(index, value, message) {
      if (!state.running || state.currentIndex !== index || !state.queue.length) return;
      const base = state.pos / state.queue.length;
      const part = Math.max(0, Math.min(95, Number(value) || 0)) / 100 / state.queue.length;
      progress(base + part, message || 'Downloading…');
    };

    window.ACCYTPlaylist.onTrackComplete = function(index, ok, message) {
      advance(!!ok, message || (ok ? 'Tersimpan' : 'Dilewati'), index);
    };

    window.ACCYTPlaylist.__v62Wrapped = true;
    return true;
  }

  function installControls() {
    const start = $('#ytpl6Start');
    const stop = $('#ytpl6Stop');
    if (start && !start.dataset.v62) {
      start.dataset.v62 = '1';
      start.addEventListener('click', startQueue, true);
    }
    if (stop && !stop.dataset.v62) {
      stop.dataset.v62 = '1';
      stop.addEventListener('click', (event) => {
        if (!state.running) return;
        event.preventDefault();
        event.stopImmediatePropagation();
        state.stopAfterCurrent = true;
        const msg = $('#ytpl6Msg');
        if (msg) msg.textContent = 'Berhenti setelah track saat ini…';
      }, true);
    }
  }

  function installEnterPlaylistGuard() {
    const input = $('#urlInput');
    if (!input || input.dataset.v62Enter) return;
    input.dataset.v62Enter = '1';
    input.addEventListener('keydown', (event) => {
      if (event.key !== 'Enter') return;
      const raw = input.value.trim();
      if (!isYoutubePlaylist(raw)) return;
      event.preventDefault();
      event.stopImmediatePropagation();
      $('#analyzeBtn')?.click();
    }, true);
  }

  function install() {
    setVersion();
    installEnterPlaylistGuard();
    const tryInstall = () => {
      installControls();
      return installPlaylistCallbacks();
    };
    if (!tryInstall()) {
      let attempts = 0;
      const timer = setInterval(() => {
        attempts += 1;
        installControls();
        if (installPlaylistCallbacks() || attempts > 30) clearInterval(timer);
      }, 100);
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', install, { once: true });
  } else {
    install();
  }
})();
