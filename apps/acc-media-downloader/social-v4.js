(() => {
  'use strict';

  const $ = (s) => document.querySelector(s);
  const $$ = (s) => Array.from(document.querySelectorAll(s));
  let currentSocial = null;

  const SOURCES = [
    { key: 'tiktok', name: 'TikTok', badge: 'TT', hosts: ['tiktok.com', 'vm.tiktok.com', 'vt.tiktok.com'] },
    { key: 'instagram', name: 'Instagram', badge: 'IG', hosts: ['instagram.com'] },
    { key: 'facebook', name: 'Facebook', badge: 'FB', hosts: ['facebook.com', 'fb.watch'] },
    { key: 'x', name: 'X / Twitter', badge: 'X', hosts: ['x.com', 'twitter.com'] }
  ];

  function normalizedHost(raw) {
    try {
      return new URL(String(raw || '').trim()).hostname.toLowerCase().replace(/^www\./, '');
    } catch (_) {
      return '';
    }
  }

  function hostMatches(host, domain) {
    return host === domain || host.endsWith('.' + domain);
  }

  function detectSocial(raw) {
    let url;
    try {
      url = new URL(String(raw || '').trim());
      if (!/^https?:$/.test(url.protocol)) return null;
    } catch (_) {
      return null;
    }
    const host = normalizedHost(url.href);
    const source = SOURCES.find((item) => item.hosts.some((d) => hostMatches(host, d)));
    return source ? { ...source, url: url.href, host } : null;
  }

  function toast(message) {
    const node = $('#toast');
    if (!node) return;
    node.textContent = message;
    node.classList.add('show');
    clearTimeout(toast.timer);
    toast.timer = setTimeout(() => node.classList.remove('show'), 2600);
  }

  function ensureStyles() {
    if ($('#accSocialV4Style')) return;
    const style = document.createElement('style');
    style.id = 'accSocialV4Style';
    style.textContent = `
      .socialv4{margin-top:14px;padding:14px;border-radius:17px;border:1px solid #293241;background:#0b1017}
      .socialv4head{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:10px}
      .socialv4head b{font-size:12px}.socialv4head span{font-size:10px;color:#7d889a}
      .socialv4modes{display:grid;grid-template-columns:1fr 1fr;gap:8px}
      .socialv4mode{height:46px;border:1px solid #303848;border-radius:14px;background:#141a23;color:#9ba4b5;font-weight:900;font-size:12px}
      .socialv4mode.on{border-color:#ff3453;background:#2a1118;color:#ff6177}
      .socialv4actions{display:grid;grid-template-columns:1fr auto;gap:8px;margin-top:10px}
      .socialv4download{height:50px;border:0;border-radius:14px;background:linear-gradient(135deg,#ff3151,#e90d36);color:#fff;font-weight:950}
      .socialv4open{height:50px;padding:0 14px;border:1px solid #303848;border-radius:14px;background:#171d27;color:#f5f6fa;font-weight:900}
      .socialv4progress{display:none;margin-top:12px}.socialv4progress.show{display:block}
      .socialv4line{height:8px;border-radius:999px;overflow:hidden;background:#242b36}.socialv4fill{height:100%;width:0;background:linear-gradient(90deg,#ff3151,#ff6c81);transition:width .25s}
      .socialv4text{display:flex;justify-content:space-between;gap:10px;margin-top:7px;font-size:10px;color:#7e899b}.socialv4text span:last-child{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
      .socialv4note{margin-top:10px;color:#7e899b;font-size:10px;line-height:1.5}
      .light .socialv4{background:#fff;border-color:#dfe4ec}.light .socialv4mode,.light .socialv4open{background:#edf0f5;color:#2c3440;border-color:#d9dee7}
      .light .socialv4mode.on{background:#ffe8ec;color:#d9183b;border-color:#ff8fa1}
    `;
    document.head.appendChild(style);
  }

  function ensureBox() {
    let box = $('#socialV4Box');
    if (box) return box;
    const card = $('#resultCard');
    if (!card) return null;
    box = document.createElement('div');
    box.id = 'socialV4Box';
    box.className = 'socialv4';
    box.style.display = 'none';
    box.innerHTML = `
      <div class="socialv4head"><b id="socialV4Title">Social media</b><span>PUBLIC LINK</span></div>
      <div class="socialv4modes">
        <button class="socialv4mode on" type="button" data-social-mode="video">▣ VIDEO (MP4)</button>
        <button class="socialv4mode" type="button" data-social-mode="audio">♫ AUDIO (MP3 320)</button>
      </div>
      <div class="socialv4actions">
        <button id="socialV4Download" class="socialv4download" type="button">↓ DOWNLOAD SEKARANG</button>
        <button id="socialV4Open" class="socialv4open" type="button">BUKA</button>
      </div>
      <div id="socialV4Progress" class="socialv4progress">
        <div class="socialv4line"><div id="socialV4Fill" class="socialv4fill"></div></div>
        <div class="socialv4text"><b id="socialV4Pct">0%</b><span id="socialV4Msg">Menyiapkan engine…</span></div>
      </div>
      <div class="socialv4note">Hanya link publik yang didukung. ACC tidak membypass login, konten private, paywall, atau DRM. Gunakan hanya untuk media yang kamu miliki atau punya izin untuk menyimpan.</div>`;
    card.appendChild(box);

    $$('#socialV4Box .socialv4mode').forEach((btn) => {
      btn.addEventListener('click', () => {
        $$('#socialV4Box .socialv4mode').forEach((b) => b.classList.remove('on'));
        btn.classList.add('on');
      });
    });

    $('#socialV4Open')?.addEventListener('click', () => {
      if (!currentSocial) return;
      try {
        if (window.ACCNative && typeof window.ACCNative.openExternal === 'function') {
          window.ACCNative.openExternal(currentSocial.url);
        } else {
          window.open(currentSocial.url, '_blank', 'noopener');
        }
      } catch (_) {
        location.href = currentSocial.url;
      }
    });

    $('#socialV4Download')?.addEventListener('click', () => {
      if (!currentSocial) return;
      if (!window.ACCNative || typeof window.ACCNative.downloadSocial !== 'function') {
        toast('Fitur social download hanya tersedia di APK Android v4.');
        return;
      }
      const active = $('#socialV4Box .socialv4mode.on');
      const mode = active?.dataset.socialMode === 'audio' ? 'audio' : 'video';
      const progress = $('#socialV4Progress');
      const fill = $('#socialV4Fill');
      const pct = $('#socialV4Pct');
      const msg = $('#socialV4Msg');
      const button = $('#socialV4Download');
      progress?.classList.add('show');
      if (fill) fill.style.width = '2%';
      if (pct) pct.textContent = '2%';
      if (msg) msg.textContent = mode === 'audio' ? 'Menyiapkan MP3 320 kbps…' : 'Menyiapkan video…';
      if (button) button.disabled = true;
      window.ACCNative.downloadSocial(currentSocial.url, mode);
    });
    return box;
  }

  function restoreBaseUi() {
    currentSocial = null;
    const box = $('#socialV4Box');
    if (box) box.style.display = 'none';
    ['#mainActionBtn', '#directBtn', '#favoriteBtn'].forEach((selector) => {
      const node = $(selector);
      if (node) node.style.removeProperty('display');
    });
    const yt = $('#ytBox');
    if (yt) yt.style.removeProperty('display');
  }

  function renderSocial(info) {
    currentSocial = info;
    const card = $('#resultCard');
    const box = ensureBox();
    if (!card || !box) return;

    $('#fileBadge') && ($('#fileBadge').textContent = info.badge);
    $('#fileName') && ($('#fileName').textContent = `${info.name} media`);
    $('#fileDomain') && ($('#fileDomain').textContent = info.host);
    $('#fileType') && ($('#fileType').textContent = 'PUBLIC POST');
    $('#fileSource') && ($('#fileSource').textContent = info.name);
    const status = $('#resultStatus');
    if (status) {
      status.textContent = 'SOCIAL';
      status.className = 'statuspill';
      status.style.background = '#35151b';
      status.style.color = '#ff6b7e';
    }
    const title = $('#socialV4Title');
    if (title) title.textContent = `${info.name} downloader`;
    ['#mainActionBtn', '#directBtn', '#favoriteBtn'].forEach((selector) => {
      const node = $(selector);
      if (node) node.style.display = 'none';
    });
    const yt = $('#ytBox');
    if (yt) yt.style.display = 'none';
    box.style.display = 'block';
    card.classList.add('show');
    toast(`${info.name} link terdeteksi.`);
  }

  function installAnalyzeIntercept() {
    const analyze = $('#analyzeBtn');
    if (!analyze) return;
    analyze.addEventListener('click', (event) => {
      const raw = ($('#urlInput')?.value || '').trim();
      const info = detectSocial(raw);
      if (!info) {
        restoreBaseUi();
        return;
      }
      event.preventDefault();
      event.stopImmediatePropagation();
      renderSocial(info);
    }, true);
  }

  function installBatchGuard() {
    const area = $('#batchUrls');
    const button = $('#batchDownloadBtn');
    const summary = $('#batchSummary');
    if (!area || !button || !summary) return;

    const update = () => {
      const lines = (area.value || '').split(/\r?\n/).map((v) => v.trim()).filter(Boolean);
      let social = 0;
      lines.forEach((raw) => { if (detectSocial(raw)) social += 1; });
      if (social > 0) {
        summary.innerHTML = `<b>${lines.length} URL</b><br>${social} social link · buka Home untuk download social satu per satu`;
      }
    };
    area.addEventListener('input', () => setTimeout(update, 0));
    button.addEventListener('click', (event) => {
      const lines = (area.value || '').split(/\r?\n/).map((v) => v.trim()).filter(Boolean);
      if (!lines.some((raw) => detectSocial(raw))) return;
      event.preventDefault();
      event.stopImmediatePropagation();
      toast('Social link belum diproses lewat Batch. Gunakan Home agar format video/audio bisa dipilih.');
    }, true);
  }

  window.ACCSocial = {
    onNativeProgress(value, message) {
      const n = Math.max(0, Math.min(100, Number(value) || 0));
      const fill = $('#socialV4Fill');
      const pct = $('#socialV4Pct');
      const msg = $('#socialV4Msg');
      if (fill) fill.style.width = `${n}%`;
      if (pct) pct.textContent = `${Math.round(n)}%`;
      if (msg) msg.textContent = message || 'Downloading…';
    },
    onNativeComplete(ok, message) {
      const button = $('#socialV4Download');
      if (button) button.disabled = false;
      const fill = $('#socialV4Fill');
      const pct = $('#socialV4Pct');
      const msg = $('#socialV4Msg');
      if (ok) {
        if (fill) fill.style.width = '100%';
        if (pct) pct.textContent = '100%';
        if (msg) msg.textContent = message || 'Selesai';
        toast(message || 'Download selesai.');
      } else {
        if (msg) msg.textContent = message || 'Download gagal';
        toast(message || 'Download gagal.');
      }
    }
  };

  function install() {
    ensureStyles();
    ensureBox();
    installAnalyzeIntercept();
    installBatchGuard();
    const version = $('.version');
    if (version) version.textContent = 'v4.0.0';
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', install, { once: true });
  } else {
    install();
  }
})();
