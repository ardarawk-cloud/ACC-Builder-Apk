(() => {
  'use strict';

  const $ = (s) => document.querySelector(s);

  function toast(message) {
    const node = $('#toast');
    if (!node) return;
    node.textContent = String(message || '');
    node.classList.add('show');
    clearTimeout(toast.timer);
    toast.timer = setTimeout(() => node.classList.remove('show'), 2600);
  }

  function installPcUi() {
    const version = $('.version');
    if (version) version.textContent = 'PC v1.0.0';

    const hero = document.querySelector('#home .hero p');
    if (hero) {
      hero.textContent = 'Tempel link, pilih video atau audio, lalu simpan langsung ke PC. Engine yt-dlp + FFmpeg berjalan lokal di Windows.';
    }

    const input = $('#urlInput');
    if (input) input.placeholder = 'YouTube, Spotify playlist, TikTok, Instagram, Facebook, X…';

    const legal = document.querySelector('#settings .legal');
    if (legal) {
      legal.innerHTML = '<b>ACC Media Downloader PC v1.0.0</b>Engine lokal yt-dlp + FFmpeg + Deno. Gunakan hanya untuk media yang Anda miliki atau memiliki izin untuk mengunduhnya.';
    }

    const ytBox = $('#ytBox');
    const qualityRow = $('#qualityRow');
    const dl = $('#ytDownloadBtn');
    if (ytBox && qualityRow && dl && !$('#audioBitrateRow')) {
      const row = document.createElement('div');
      row.id = 'audioBitrateRow';
      row.className = 'qualityrow';
      row.style.display = 'none';
      row.innerHTML = '<select id="audioBitrate" aria-label="Bitrate MP3"><option value="320">MP3 320 kbps</option><option value="256">MP3 256 kbps</option><option value="192">MP3 192 kbps</option><option value="128">MP3 128 kbps</option></select>';
      qualityRow.insertAdjacentElement('afterend', row);

      const syncMode = () => {
        const active = document.querySelector('.mode.on');
        row.style.display = active && active.dataset.mode === 'audio' ? 'flex' : 'none';
      };
      document.querySelectorAll('.mode').forEach((btn) => btn.addEventListener('click', () => setTimeout(syncMode, 0)));
      syncMode();

      dl.addEventListener('click', (event) => {
        if (!window.ACCNative || typeof window.ACCNative.downloadYoutubeAdvanced !== 'function') return;
        const raw = ($('#urlInput')?.value || '').trim();
        let host = '';
        try { host = new URL(raw).hostname.toLowerCase(); } catch (_) { return; }
        if (!(host === 'youtu.be' || host === 'youtube.com' || host.endsWith('.youtube.com'))) return;

        event.preventDefault();
        event.stopImmediatePropagation();
        const active = document.querySelector('.mode.on');
        const mode = active && active.dataset.mode === 'audio' ? 'audio' : 'video';
        const quality = $('#quality')?.value || 'best';
        const bitrate = $('#audioBitrate')?.value || '320';

        $('#progressBox')?.classList.add('show');
        if ($('#progressFill')) $('#progressFill').style.width = '2%';
        if ($('#progressPct')) $('#progressPct').textContent = '2%';
        if ($('#progressMsg')) $('#progressMsg').textContent = mode === 'audio' ? 'Menyiapkan MP3 ' + bitrate + ' kbps…' : 'Menyiapkan video…';
        dl.disabled = true;
        window.ACCNative.downloadYoutubeAdvanced(raw, mode, quality, bitrate);
      }, true);
    }
  }

  function onEngineEvent(event) {
    if (!event || !event.type) return;
    const a = Array.isArray(event.args) ? event.args : [];
    switch (event.type) {
      case 'app-progress':
        window.ACCApp?.onNativeProgress?.(a[0], a[1]);
        break;
      case 'app-complete':
        window.ACCApp?.onNativeComplete?.(a[0], a[1]);
        break;
      case 'social-progress':
        window.ACCSocial?.onNativeProgress?.(a[0], a[1]);
        break;
      case 'social-complete':
        window.ACCSocial?.onNativeComplete?.(a[0], a[1]);
        break;
      case 'spotify-playlist':
        window.ACCPlaylist?.onSpotifyPlaylist?.(a[0], a[1]);
        break;
      case 'spotify-track-progress':
        window.ACCPlaylist?.onTrackProgress?.(a[0], a[1], a[2]);
        break;
      case 'spotify-track-complete':
        window.ACCPlaylist?.onTrackComplete?.(a[0], a[1], a[2]);
        break;
      case 'yt-playlist':
        window.ACCYTPlaylist?.onPlaylist?.(a[0], a[1]);
        break;
      case 'yt-track-progress':
        window.ACCYTPlaylist?.onTrackProgress?.(a[0], a[1], a[2]);
        break;
      case 'yt-track-complete':
        window.ACCYTPlaylist?.onTrackComplete?.(a[0], a[1], a[2]);
        break;
      case 'toast':
        toast(a[0]);
        break;
    }
  }

  if (window.ACCNative?.onEvent) window.ACCNative.onEvent(onEngineEvent);

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', installPcUi, { once: true });
  } else {
    installPcUi();
  }
})();