(() => {
  'use strict';

  const $ = (s) => document.querySelector(s);
  const $$ = (s) => Array.from(document.querySelectorAll(s));
  let spotifyState = null;

  function isSpotifyUrl(raw) {
    try {
      const u = new URL(String(raw || '').trim());
      const h = u.hostname.toLowerCase().replace(/^www\./, '');
      return h === 'open.spotify.com' || h === 'spotify.com' || h.endsWith('.spotify.com') || h === 'spotify.link';
    } catch (_) {
      return false;
    }
  }

  function isYouTubeUrl(raw) {
    try {
      const u = new URL(String(raw || '').trim());
      const h = u.hostname.toLowerCase().replace(/^www\./, '');
      return h === 'youtube.com' || h.endsWith('.youtube.com') || h === 'youtu.be';
    } catch (_) {
      return false;
    }
  }

  function spotifyKind(raw) {
    try {
      const p = new URL(raw).pathname.split('/').filter(Boolean);
      if (p.includes('album')) return 'ALBUM';
      if (p.includes('playlist')) return 'PLAYLIST';
      if (p.includes('track')) return 'TRACK';
      if (p.includes('show')) return 'PODCAST';
      if (p.includes('episode')) return 'EPISODE';
      if (p[0] === 's') return 'SHARE LINK';
    } catch (_) {}
    return 'SPOTIFY';
  }

  function openExternal(url) {
    try {
      if (window.ACCNative && typeof window.ACCNative.openExternal === 'function') {
        window.ACCNative.openExternal(url);
      } else {
        window.open(url, '_blank', 'noopener');
      }
    } catch (_) {
      location.href = url;
    }
  }

  function copyText(text) {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(() => toastV3('Link Spotify disalin.')).catch(() => fallbackCopy(text));
    } else fallbackCopy(text);
  }

  function fallbackCopy(text) {
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.style.position = 'fixed';
    ta.style.opacity = '0';
    document.body.appendChild(ta);
    ta.select();
    try { document.execCommand('copy'); toastV3('Link disalin.'); } catch (_) { toastV3('Gagal menyalin link.'); }
    ta.remove();
  }

  function toastV3(msg) {
    const t = $('#toast');
    if (!t) return;
    t.textContent = msg;
    t.classList.add('show');
    clearTimeout(toastV3.timer);
    toastV3.timer = setTimeout(() => t.classList.remove('show'), 2400);
  }

  function renderSpotify(raw) {
    const u = new URL(raw.trim());
    spotifyState = { url: u.href, kind: spotifyKind(u.href), domain: u.hostname };

    const card = $('#resultCard');
    if (!card) return;
    $('#fileBadge').textContent = 'SP';
    $('#fileName').textContent = `Spotify ${spotifyState.kind.toLowerCase()}`;
    $('#fileDomain').textContent = spotifyState.domain;
    $('#fileType').textContent = spotifyState.kind;
    $('#fileSource').textContent = 'Spotify';
    const status = $('#resultStatus');
    status.textContent = 'RESMI';
    status.className = 'statuspill';
    status.style.background = '#123126';
    status.style.color = '#55e49c';

    const fav = $('#favoriteBtn');
    fav.textContent = '⧉ SALIN LINK';
    fav.classList.remove('on');
    const action = $('#mainActionBtn');
    action.textContent = 'BUKA SPOTIFY';

    const notice = $('#resultNotice');
    notice.hidden = false;
    notice.textContent = 'Spotify helper: buka album/playlist langsung di Spotify lalu gunakan fitur Download/Offline resmi Spotify jika tersedia. ACC tidak mengekstrak atau merip stream Spotify.';
    card.classList.add('show');
    toastV3('Link Spotify terdeteksi.');
  }

  function installSpotifyIntercept() {
    const analyze = $('#analyzeBtn');
    if (analyze) {
      analyze.addEventListener('click', (e) => {
        const raw = ($('#urlInput')?.value || '').trim();
        if (!isSpotifyUrl(raw)) {
          spotifyState = null;
          return;
        }
        e.preventDefault();
        e.stopImmediatePropagation();
        try { renderSpotify(raw); } catch (_) { toastV3('Link Spotify tidak valid.'); }
      }, true);
    }

    const main = $('#mainActionBtn');
    if (main) {
      main.addEventListener('click', (e) => {
        if (!spotifyState) return;
        e.preventDefault();
        e.stopImmediatePropagation();
        openExternal(spotifyState.url);
      }, true);
    }

    const fav = $('#favoriteBtn');
    if (fav) {
      fav.addEventListener('click', (e) => {
        if (!spotifyState) return;
        e.preventDefault();
        e.stopImmediatePropagation();
        copyText(spotifyState.url);
      }, true);
    }
  }

  function guessName(url, index) {
    try {
      const u = new URL(url);
      let n = decodeURIComponent(u.pathname.split('/').filter(Boolean).pop() || `media-${index + 1}`);
      n = n.replace(/[\\/:*?"<>|\r\n]+/g, '_');
      if (!n || n.length > 160) n = `media-${index + 1}`;
      return n;
    } catch (_) {
      return `media-${index + 1}`;
    }
  }

  function injectBatchPage() {
    if ($('#batch')) return;
    const style = document.createElement('style');
    style.textContent = `
      .nav{grid-template-columns:repeat(5,1fr)!important}
      .batchbox{padding:15px}.batcharea{width:100%;min-height:210px;resize:vertical;border-radius:16px;border:1px solid #293141;background:#0a0e14;color:#f7f8fb;padding:14px;font:12px/1.55 ui-monospace,SFMono-Regular,Menlo,monospace;outline:none}.batcharea:focus{border-color:#ff3554;box-shadow:0 0 0 3px rgba(255,53,84,.11)}
      .batchsummary{margin-top:12px;padding:12px;border:1px solid #202735;border-radius:14px;background:#0d1219;color:#8e98aa;font-size:11px;line-height:1.55}.batchsummary b{color:#f0f3f8}
      .batchnote{margin-top:12px;padding:12px;border-radius:14px;border:1px solid #2a3040;background:#11161e;color:#8c96a8;font-size:11px;line-height:1.55}.spotifyhint{margin-top:14px;padding:14px;border:1px solid #1c5536;border-radius:16px;background:#0c1c14;color:#8cdcb2;font-size:11px;line-height:1.55}.spotifyhint b{display:block;color:#1ed760;font-size:13px;margin-bottom:5px}
      .light .batcharea,.light .batchsummary,.light .batchnote{background:#fff;color:#2c3440;border-color:#dce1e9}.light .spotifyhint{background:#effcf4;color:#35664c;border-color:#bde7cd}
    `;
    document.head.appendChild(style);

    const page = document.createElement('main');
    page.id = 'batch';
    page.className = 'page';
    page.innerHTML = `
      <section class="hero">
        <div class="eyebrow">Batch downloader</div>
        <h1>Download banyak file sekaligus.</h1>
        <p>Tempel beberapa direct-media URL, satu URL per baris. ACC akan mengantrekan semuanya ke Android Download Manager.</p>
      </section>
      <section class="card batchbox">
        <textarea id="batchUrls" class="batcharea" spellcheck="false" placeholder="https://example.com/track-01.mp3\nhttps://example.com/video-02.mp4\nhttps://example.com/photo-03.jpg"></textarea>
        <button id="batchDownloadBtn" class="primary" type="button">DOWNLOAD SEMUA</button>
        <div id="batchSummary" class="batchsummary"><b>0 URL</b><br>Masukkan direct URL yang memang boleh kamu simpan.</div>
        <div class="batchnote">Batch mode hanya untuk direct HTTP/HTTPS file yang kamu miliki atau punya izin untuk diunduh. Link YouTube dan Spotify dilewati dari batch ini dan ditangani oleh mode masing-masing.</div>
        <div class="spotifyhint"><b>Spotify Album / Playlist Helper</b>Tempel link album atau playlist Spotify di halaman Home. ACC akan mendeteksinya dan membuka link langsung ke Spotify sehingga kamu bisa memakai opsi Download/Offline resmi untuk album/playlist tersebut.</div>
      </section>`;

    const bottom = $('.bottom');
    document.querySelector('.app')?.insertBefore(page, bottom || null);

    const nav = $('.nav');
    if (nav) {
      const btn = document.createElement('button');
      btn.className = 'navbtn';
      btn.type = 'button';
      btn.id = 'batchNavBtn';
      btn.innerHTML = '<span class="ni">⇩</span><span>BATCH</span>';
      nav.appendChild(btn);
      btn.addEventListener('click', () => showBatch());

      $$('.navbtn').filter(b => b !== btn).forEach(b => {
        b.addEventListener('click', () => {
          page.classList.remove('active');
          btn.classList.remove('active');
        }, true);
      });
    }

    const area = $('#batchUrls');
    const summary = $('#batchSummary');
    const renderSummary = () => {
      const lines = (area.value || '').split(/\r?\n/).map(x => x.trim()).filter(Boolean);
      let direct = 0, special = 0, invalid = 0;
      lines.forEach(raw => {
        try {
          const u = new URL(raw);
          if (!/^https?:$/.test(u.protocol)) { invalid++; return; }
          if (isYouTubeUrl(raw) || isSpotifyUrl(raw)) special++; else direct++;
        } catch (_) { invalid++; }
      });
      summary.innerHTML = `<b>${lines.length} URL</b><br>${direct} direct siap · ${special} YouTube/Spotify dilewati · ${invalid} tidak valid`;
    };
    area.addEventListener('input', renderSummary);

    $('#batchDownloadBtn').addEventListener('click', () => {
      const lines = (area.value || '').split(/\r?\n/).map(x => x.trim()).filter(Boolean);
      const direct = [];
      let skipped = 0;
      lines.forEach(raw => {
        try {
          const u = new URL(raw);
          if (!/^https?:$/.test(u.protocol) || isYouTubeUrl(raw) || isSpotifyUrl(raw)) { skipped++; return; }
          direct.push(u.href);
        } catch (_) { skipped++; }
      });
      if (!direct.length) { toastV3('Tidak ada direct-media URL yang bisa diunduh.'); return; }

      direct.forEach((url, i) => {
        setTimeout(() => {
          try {
            if (window.ACCNative && typeof window.ACCNative.download === 'function') {
              window.ACCNative.download(url, guessName(url, i));
            } else {
              const a = document.createElement('a');
              a.href = url;
              a.download = guessName(url, i);
              a.rel = 'noopener';
              document.body.appendChild(a);
              a.click();
              a.remove();
            }
          } catch (_) {}
        }, i * 450);
      });
      toastV3(`${direct.length} file masuk antrean${skipped ? ` · ${skipped} dilewati` : ''}.`);
    });
  }

  function showBatch() {
    $$('.page').forEach(p => p.classList.remove('active'));
    $$('.navbtn').forEach(b => b.classList.remove('active'));
    $('#batch')?.classList.add('active');
    $('#batchNavBtn')?.classList.add('active');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function installVersion() {
    const v = $('.version');
    if (v) v.textContent = 'v3.0.0';
  }

  function boot() {
    installVersion();
    injectBatchPage();
    installSpotifyIntercept();
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();
