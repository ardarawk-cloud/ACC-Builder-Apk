(() => {
  'use strict';

  const $ = (id) => document.getElementById(id);
  const APP_NAME = 'ACC_DJ_PWA';
  const PUBLIC_API = 'https://api.audius.co/v1';
  const LEGACY_PUBLIC_API = 'https://discoveryprovider.audius.co/v1';

  const state = {
    sdk: null,
    apiKey: localStorage.getItem('accdj_audius_api_key') || '',
    ctx: null,
    splitCue: false,
    crossfader: 0,
    decks: {},
    merger: null,
    masterBus: null,
    cueBus: null,
    masterGain: null,
    normalOut: null,
    audioReady: false,
    musicLoaded: false,
    lastQuery: '',
    lastTrending: true,
    searchOffset: 0,
    pageSize: 18,
    renderedTrackIds: new Set(),
    deferredInstallPrompt: null,
    focusMode: false,
    masterDeck: 'A',
    searchSeq: 0,
    searchAbort: null,
  };

  function formatTime(seconds) {
    if (!Number.isFinite(seconds)) return '00:00';
    const s = Math.max(0, Math.floor(seconds));
    return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
  }

  function setMessage(text, error = false) {
    const el = $('libraryMessage');
    el.textContent = text;
    el.style.color = error ? '#fb7185' : '';
  }

  function openMusic() {
    const drawer = $('musicDrawer');
    const backdrop = $('drawerBackdrop');
    drawer.classList.add('open');
    drawer.setAttribute('aria-hidden', 'false');
    if (backdrop) backdrop.hidden = false;
    if (!state.musicLoaded) {
      state.musicLoaded = true;
      setMessage('Pilih DRIVE / FILES, TRENDING, genre, atau cari lagu.');
    }
  }

  function closeMusic() {
    const drawer = $('musicDrawer');
    const backdrop = $('drawerBackdrop');
    drawer.classList.remove('open');
    drawer.setAttribute('aria-hidden', 'true');
    if (backdrop) backdrop.hidden = true;
    try { state.searchAbort?.abort(); } catch (_) {}
    state.searchAbort = null;
  }

  function effectiveBpm(id) {
    const d = state.decks[id];
    const bpm = Number(window.ARDADJAnalysis?.getBpm?.(id)) || Number(d?.track?.bpm);
    if (!bpm || !Number.isFinite(bpm)) return null;
    return bpm * (Number(d.audio.playbackRate) || 1);
  }

  function updateBpmDisplay(id) {
    const d = state.decks[id];
    const base = Number(window.ARDADJAnalysis?.getBpm?.(id)) || Number(d?.track?.bpm);
    if (!base || !Number.isFinite(base)) {
      $(`bpm${id}`).textContent = d?.track ? 'BPM ANALYZE' : 'BPM —';
      return;
    }
    const eff = effectiveBpm(id);
    const changed = Math.abs((d.audio.playbackRate || 1) - 1) > 0.0005;
    $(`bpm${id}`).textContent = changed ? `BPM ${eff.toFixed(1)}*` : `BPM ${Number(base).toFixed(1)}`;
  }

  function beatLengthMedia(id) {
    const bpm = Number(state.decks[id]?.track?.bpm);
    return bpm > 0 ? 60 / bpm : null;
  }

  function phaseFromAnchor(id) {
    const d = state.decks[id];
    const beat = beatLengthMedia(id);
    if (!d || !beat) return null;
    const anchor = Number.isFinite(d.beatAnchor) ? d.beatAnchor : 0;
    return ((((d.audio.currentTime - anchor) / beat) % 1) + 1) % 1;
  }

  function normalizedPhaseError(targetId, masterId) {
    const a = phaseFromAnchor(targetId), b = phaseFromAnchor(masterId);
    if (a === null || b === null) return null;
    let error = a - b;
    if (error > .5) error -= 1;
    if (error < -.5) error += 1;
    return error;
  }

  function updateGridUi(id) {
    const d = state.decks[id], el = $(`grid${id}`);
    if (!el) return;
    const ready = (d?.beatConfidence || 0) >= 3;
    el.textContent = ready ? 'GRID ✓' : (d?.track ? 'ANALYZE' : 'GRID —');
    el.classList.toggle('grid-ready', ready);
  }

  function observeBeat(id, spectrum) {
    const d = state.decks[id];
    if (!d?.track || d.audio.paused) return;
    const bpm = Number(d.track.bpm), beat = bpm > 0 ? 60 / bpm : 0;
    if (!beat) return;
    const bins = Math.min(16, spectrum.length);
    let low = 0;
    for (let i=1;i<bins;i++) low += spectrum[i];
    low /= Math.max(1,bins-1);
    const flux = Math.max(0, low - (d.prevLowEnergy || low));
    d.prevLowEnergy = low;
    d.fluxAvg = d.fluxAvg ? d.fluxAvg * .94 + flux * .06 : flux;
    const threshold = Math.max(4.5, (d.fluxAvg || 0) * 2.35);
    const nowMedia = d.audio.currentTime;
    const refractory = Math.max(.18, beat * .38);
    if (flux < threshold || nowMedia - (d.lastOnsetMedia ?? -99) < refractory) return;
    d.lastOnsetMedia = nowMedia;

    if (!Number.isFinite(d.beatAnchor)) {
      d.beatAnchor = nowMedia;
      d.beatConfidence = 1;
    } else {
      const n = Math.round((nowMedia - d.beatAnchor) / beat);
      const predicted = d.beatAnchor + n * beat;
      let err = nowMedia - predicted;
      if (Math.abs(err) <= beat * .22) {
        const weight = d.beatConfidence >= 5 ? .08 : .20;
        d.beatAnchor += err * weight;
        d.beatConfidence = Math.min(10, (d.beatConfidence || 0) + 1);
      } else if (nowMedia - (d.lastAnchorResetMedia || 0) > beat * 8) {
        d.beatAnchor = nowMedia;
        d.beatConfidence = Math.max(1, (d.beatConfidence || 0) - 2);
        d.lastAnchorResetMedia = nowMedia;
      }
    }
    updateGridUi(id);
  }

  function setMasterDeck(id, quiet=false) {
    if (!state.decks[id]?.track && state.audioReady) {
      if (!quiet) setMessage(`Load lagu ke Deck ${id} dulu untuk MASTER.`, true);
      return;
    }
    state.masterDeck = id;
    document.querySelectorAll('[data-action="master"]').forEach(b=>b.classList.toggle('active',b.dataset.deck===id));
    const other = id === 'A' ? 'B' : 'A';
    if (state.decks[other]?.syncActive) {
      state.decks[other].syncMasterId = id;
      beginBeatChase(other);
    }
    if (state.decks[id]?.syncActive) disableSync(id,true);
    if (!quiet) setMessage(`Deck ${id} = MASTER CLOCK.`);
  }

  function setSyncUi(id, active, masterId = null, mode = 'LOCK') {
    const btn = document.querySelector(`[data-action="sync"][data-deck="${id}"]`);
    const label = $(`syncState${id}`);
    btn?.classList.toggle('synced', active);
    document.querySelector(`.deck[data-deck="${id}"]`)?.classList.toggle('sync-follow', active);
    if (btn) btn.textContent = active ? 'SYNC ✓' : 'SYNC';
    if (label) {
      label.className = 'sync-state';
      if (active) label.classList.add(mode === 'LOCK' ? 'locked' : mode === 'CHASE' ? 'chasing' : 'armed');
      label.textContent = active ? `${mode} ${masterId}` : (state.masterDeck === id ? 'MASTER' : 'FREE');
    }
  }

  function disableSync(id, quiet = false) {
    const d = state.decks[id]; if (!d) return;
    if (d.syncActive && Number.isFinite(d.syncNominalRate) && d.syncNominalRate > 0) d.audio.playbackRate = d.syncNominalRate;
    d.syncActive=false; d.syncMasterId=null; d.syncMode='FREE'; d.syncStableCount=0; d.syncNominalRate=1; d.lastSyncAt=0;
    setSyncUi(id,false); updateBpmDisplay(id);
    if (!quiet && d.track) setMessage(`Deck ${id} SYNC dilepas.`);
  }

  function beginBeatChase(targetId) {
    const d=state.decks[targetId]; if(!d?.syncActive)return;
    d.syncMode='CHASE'; d.syncStableCount=0; d.lastSyncAt=0; d.chaseStartedAt=performance.now();
    setSyncUi(targetId,true,d.syncMasterId,'CHASE');
  }

  function maintainSync(targetId) {
    const target=state.decks[targetId]; if(!target?.syncActive||!target.track)return;
    const masterId=target.syncMasterId, master=state.decks[masterId];
    if(!master?.track)return disableSync(targetId,true);
    const now=performance.now(); if(now-(target.lastSyncAt||0)<25)return; target.lastSyncAt=now;
    const targetBase=Number(target.track.bpm), masterNow=effectiveBpm(masterId); if(!targetBase||!masterNow)return;
    const nominal=masterNow/targetBase; target.syncNominalRate=nominal;
    if(target.audio.paused||master.audio.paused){target.audio.playbackRate=nominal;target.syncMode='ARM';target.syncStableCount=0;setSyncUi(targetId,true,masterId,'ARM');return;}
    const error=normalizedPhaseError(targetId,masterId); if(error===null)return;
    const abs=Math.abs(error); let correction=0;
    const gridReady=(target.beatConfidence||0)>=2 && (master.beatConfidence||0)>=2;
    if(abs>.12){ correction=Math.max(-.24,Math.min(.24,-error*1.15)); target.syncMode='CHASE'; target.syncStableCount=0; }
    else if(abs>.035){ correction=Math.max(-.085,Math.min(.085,-error*.75)); target.syncMode='CHASE'; target.syncStableCount=0; }
    else if(abs>.012){ correction=Math.max(-.026,Math.min(.026,-error*.42)); target.syncMode='CHASE'; target.syncStableCount=0; }
    else { correction=Math.max(-.004,Math.min(.004,-error*.10)); target.syncStableCount=(target.syncStableCount||0)+1; if(target.syncStableCount>=7)target.syncMode='LOCK'; }
    target.audio.playbackRate=nominal*(1+correction);
    setSyncUi(targetId,true,masterId,target.syncMode==='LOCK'?'LOCK':'CHASE');
    if(!gridReady && $(`syncState${targetId}`)) $(`syncState${targetId}`).title='Beat anchor masih dianalisis';
    updateBpmDisplay(targetId);
  }

  async function syncDeck(targetId) {
    await initAudio();
    let masterId=state.masterDeck;
    if(masterId===targetId){
      const other=targetId==='A'?'B':'A';
      if(!state.decks[other]?.track)return setMessage('Deck ini MASTER. Load deck lain lalu SYNC deck follower.',true);
      return setMessage(`Deck ${targetId} adalah MASTER. Tekan SYNC di Deck ${other}.`,true);
    }
    const target=state.decks[targetId], master=state.decks[masterId];
    if(!target?.track||!master?.track)return setMessage('Load lagu ke MASTER dan follower dulu untuk SYNC.',true);
    if(target.syncActive){disableSync(targetId);return;}
    const targetBase=Number(target.track.bpm), masterNow=effectiveBpm(masterId);
    if(!targetBase||!masterNow)return setMessage('BPM metadata salah satu track tidak tersedia.',true);
    const rate=masterNow/targetBase, pct=(rate-1)*100;
    if(Math.abs(pct)>10)return setMessage(`Beda tempo terlalu jauh (${pct>0?'+':''}${pct.toFixed(1)}%). Pilih track BPM lebih dekat.`,true);
    target.syncActive=true;target.syncMasterId=masterId;target.syncNominalRate=rate;target.syncStableCount=0;target.audio.playbackRate=rate;
    $(`pitch${targetId}`).value=Math.max(-8,Math.min(8,pct)).toFixed(1);$(`pitchLabel${targetId}`).textContent=`${pct>=0?'+':''}${pct.toFixed(1)}%`;
    updateBpmDisplay(targetId);
    if(!target.audio.paused&&!master.audio.paused){beginBeatChase(targetId);maintainSync(targetId);setMessage(`Deck ${targetId} CHASE → MASTER ${masterId}.`);}else{target.syncMode='ARM';setSyncUi(targetId,true,masterId,'ARM');setMessage(`Deck ${targetId} armed. PLAY kapan pun → otomatis chase MASTER ${masterId}.`);}
  }

  function nudgeDeck(id, direction) {
    const d = state.decks[id];
    const bpm = Number(d?.track?.bpm);
    if (!d?.track || !bpm) return setMessage(`Deck ${id} belum punya BPM untuk NUDGE.`, true);
    const step = (60 / bpm) * 0.08 * Number(direction);
    d.audio.currentTime = Math.max(0, d.audio.currentTime + step);
    setMessage(`Deck ${id} nudge ${direction < 0 ? '◀' : '▶'} ${(Math.abs(step) * 1000).toFixed(0)} ms.`);
  }


  function initSdk() {
    if (!state.apiKey || typeof window.audiusSdk !== 'function') {
      state.sdk = null;
      return;
    }
    try {
      state.sdk = window.audiusSdk({ apiKey: state.apiKey });
    } catch (err) {
      console.warn('Audius SDK init failed', err);
      state.sdk = null;
    }
  }

  async function initAudio() {
    if (state.audioReady) {
      if (state.ctx?.state === 'suspended') await state.ctx.resume();
      return;
    }

    const AudioContext = window.AudioContext || window.webkitAudioContext;
    if (!AudioContext) throw new Error('Web Audio API tidak didukung browser ini.');

    const ctx = new AudioContext({ latencyHint: 'interactive' });
    state.ctx = ctx;
    state.masterBus = ctx.createGain();
    state.cueBus = ctx.createGain();
    state.masterGain = ctx.createGain();
    state.normalOut = ctx.createGain();
    state.merger = ctx.createChannelMerger(2);

    state.masterBus.connect(state.masterGain);
    state.masterGain.connect(state.normalOut);
    state.normalOut.connect(ctx.destination);

    ['A', 'B'].forEach((id) => createDeckGraph(id));
    state.masterGain.gain.value = Number($('masterVol').value);
    updateCrossfader();
    state.audioReady = true;
    $('audioStatus').textContent = `Audio ${Math.round(ctx.sampleRate / 1000)}kHz`;
    await ctx.resume();
    drawLoop();
  }

  function createDeckGraph(id) {
    const audio = $(`audio${id}`);
    const source = state.ctx.createMediaElementSource(audio);
    const low = state.ctx.createBiquadFilter();
    const mid = state.ctx.createBiquadFilter();
    const high = state.ctx.createBiquadFilter();
    const postEq = state.ctx.createGain();
    const crossGain = state.ctx.createGain();
    const cueGain = state.ctx.createGain();
    const analyser = state.ctx.createAnalyser();
    const fxFilter = state.ctx.createBiquadFilter();
    const fxDry = state.ctx.createGain();
    const fxDelay = state.ctx.createDelay(2.0);
    const fxFeedback = state.ctx.createGain();
    const fxDelayWet = state.ctx.createGain();
    const fxConvolver = state.ctx.createConvolver();
    const fxReverbWet = state.ctx.createGain();
    const fxSum = state.ctx.createGain();

    low.type = 'lowshelf'; low.frequency.value = 250;
    mid.type = 'peaking'; mid.frequency.value = 1200; mid.Q.value = 0.8;
    high.type = 'highshelf'; high.frequency.value = 5000;
    cueGain.gain.value = 0;
    analyser.fftSize = 256;
    analyser.smoothingTimeConstant = 0.75;

    fxFilter.type = 'lowpass';
    fxFilter.frequency.value = 22000;
    fxFilter.Q.value = 0.7;
    fxDry.gain.value = 1;
    fxDelay.delayTime.value = 0.25;
    fxFeedback.gain.value = 0.28;
    fxDelayWet.gain.value = 0;
    fxReverbWet.gain.value = 0;
    const impulseLength = Math.max(1, Math.floor(state.ctx.sampleRate * 1.25));
    const impulse = state.ctx.createBuffer(2, impulseLength, state.ctx.sampleRate);
    for (let ch = 0; ch < impulse.numberOfChannels; ch++) {
      const data = impulse.getChannelData(ch);
      for (let i = 0; i < impulseLength; i++) {
        const decay = Math.pow(1 - i / impulseLength, 2.6);
        data[i] = (Math.random() * 2 - 1) * decay;
      }
    }
    fxConvolver.buffer = impulse;

    source.connect(low).connect(mid).connect(high).connect(postEq);
    postEq.connect(fxFilter);
    fxFilter.connect(fxDry).connect(fxSum);
    fxFilter.connect(fxDelay);
    fxDelay.connect(fxDelayWet).connect(fxSum);
    fxDelay.connect(fxFeedback).connect(fxDelay);
    fxFilter.connect(fxConvolver).connect(fxReverbWet).connect(fxSum);
    fxSum.connect(crossGain).connect(state.masterBus);
    postEq.connect(cueGain).connect(state.cueBus);
    postEq.connect(analyser);

    state.decks[id] = {
      audio, source, low, mid, high, postEq, crossGain, cueGain, analyser,
      fxFilter, fxDry, fxDelay, fxFeedback, fxDelayWet, fxConvolver, fxReverbWet, fxSum,
      cue: false,
      loop: false,
      track: null,
      syncActive: false,
      syncMasterId: null,
      syncNominalRate: 1,
      syncMode: 'FREE',
      syncStableCount: 0,
      lastSyncAt: 0,
      beatAnchor: NaN, beatConfidence: 0, prevLowEnergy: 0, fluxAvg: 0, lastOnsetMedia: -99, lastAnchorResetMedia: 0,
    };
  }

  function updateCrossfader() {
    if (!state.audioReady) return;
    const x = Number($('crossfader').value); // -1..1
    state.crossfader = x;
    const t = (x + 1) * Math.PI / 4;
    state.decks.A.crossGain.gain.setTargetAtTime(Math.cos(t), state.ctx.currentTime, 0.01);
    state.decks.B.crossGain.gain.setTargetAtTime(Math.sin(t), state.ctx.currentTime, 0.01);
  }

  function setSplitCue(enabled) {
    if (!state.audioReady) return;
    state.splitCue = enabled;
    const btn = $('splitCueBtn');

    try { state.normalOut.disconnect(); } catch (_) {}
    try { state.merger.disconnect(); } catch (_) {}
    try { state.masterGain.disconnect(); } catch (_) {}
    try { state.cueBus.disconnect(); } catch (_) {}

    if (enabled) {
      // Each ChannelMerger input is mono: input 0 => L, input 1 => R.
      state.masterGain.connect(state.merger, 0, 0);
      state.cueBus.connect(state.merger, 0, 1);
      state.merger.connect(state.ctx.destination);
      btn.textContent = 'SPLIT CUE: ON';
      btn.classList.add('active');
      $('cueHelp').textContent = 'LEFT = master mono • RIGHT = cue mono';
    } else {
      state.masterGain.connect(state.normalOut);
      state.normalOut.connect(state.ctx.destination);
      btn.textContent = 'SPLIT CUE: OFF';
      btn.classList.remove('active');
      $('cueHelp').textContent = 'Normal: master stereo.';
    }
  }

  function withTimeout(promise, ms, label = 'Request timeout') {
    let timer;
    return Promise.race([
      promise,
      new Promise((_, reject) => { timer = setTimeout(() => reject(new Error(label)), ms); })
    ]).finally(() => clearTimeout(timer));
  }

  async function fetchJsonWithTimeout(url, ms = 6500) {
    try { state.searchAbort?.abort(); } catch (_) {}
    const controller = new AbortController();
    state.searchAbort = controller;
    const timer = setTimeout(() => controller.abort(), ms);
    try {
      const response = await fetch(url, { signal: controller.signal, cache: 'no-store' });
      if (!response.ok) throw new Error(`Audius HTTP ${response.status}`);
      return await response.json();
    } finally {
      clearTimeout(timer);
      if (state.searchAbort === controller) state.searchAbort = null;
    }
  }

  async function publicAudiusTracks(query, trending, genre, offset) {
    const params = new URLSearchParams({ app_name: APP_NAME, limit: String(state.pageSize), offset: String(offset) });
    if (genre) params.set('genre', genre);
    if (!trending) params.set('query', query);
    const path = trending ? 'tracks/trending' : 'tracks/search';
    const url = `${PUBLIC_API}/${path}?${params}`;

    try { state.searchAbort?.abort(); } catch (_) {}
    const controller = new AbortController();
    state.searchAbort = controller;
    const timer = setTimeout(() => controller.abort(), 4200);
    try {
      const response = await fetch(url, { signal: controller.signal, cache: 'no-store' });
      if (!response.ok) throw new Error(`Audius HTTP ${response.status}`);
      const json = await response.json();
      return json?.data || [];
    } finally {
      clearTimeout(timer);
      if (state.searchAbort === controller) state.searchAbort = null;
    }
  }

  async function searchTracks(query, trending = false, append = false) {
    const genre = $('genreSelect').value;
    const offset = append ? state.searchOffset + state.pageSize : 0;
    const requestId = ++state.searchSeq;
    if (!append) {
      state.lastQuery = query;
      state.lastTrending = trending;
      state.searchOffset = 0;
      state.renderedTrackIds = new Set();
      $('results').innerHTML = '';
    }
    setMessage(append ? 'Mengambil track berikutnya…' : 'Menghubungkan ke Audius…');

    try {
      // Browser/mobile browsing uses the official REST endpoint directly.
      // The SDK is still available for streaming, but not allowed to hold the Library UI.
      let tracks = await publicAudiusTracks(query, trending, genre, offset);
      if (requestId !== state.searchSeq) return;

      state.searchOffset = offset;
      const rawCount = tracks.length;
      const beforeFilter = tracks.length;
      tracks = tracks.filter(isPlayableTrack);
      renderResults(tracks, append);
      const hidden = Math.max(0, beforeFilter - tracks.length);
      const totalShown = state.renderedTrackIds.size;
      $('moreBtn').hidden = rawCount < state.pageSize;

      if (tracks.length || append) {
        setMessage(`${totalShown} track tersedia${hidden ? ` • ${hidden} non-streamable dilewati` : ''}.`);
      } else {
        setMessage('Tidak ada track streamable. Coba genre/search lain atau DRIVE / FILES.', true);
      }
    } catch (err) {
      if (requestId !== state.searchSeq) return;
      console.warn('Audius browse unavailable', err);
      const timeout = err?.name === 'AbortError' || /timeout/i.test(String(err?.message || ''));
      setMessage(timeout
        ? 'Audius timeout. Library tetap aktif — DRIVE / FILES bisa dipakai sekarang.'
        : 'Audius tidak tersedia. Library tetap aktif — gunakan DRIVE / FILES atau coba lagi.', true);
      $('moreBtn').hidden = true;
    }
  }

  function boolish(value) {
    if (value === true || value === false) return value;
    if (typeof value === 'string') {
      const v = value.trim().toLowerCase();
      if (v === 'true' || v === '1' || v === 'yes') return true;
      if (v === 'false' || v === '0' || v === 'no') return false;
    }
    return null;
  }

  function isPlayableTrack(t) {
    if (!t || !t.id) return false;
    const streamable = boolish(t.isStreamable ?? t.is_streamable);
    if (streamable === false) return false;

    const gated = boolish(t.isStreamGated ?? t.is_stream_gated);
    if (gated === true) return false;

    const allowed = t.allowedApiKeys ?? t.allowed_api_keys;
    if (Array.isArray(allowed) && allowed.length) {
      if (!state.apiKey || !allowed.map(String).includes(String(state.apiKey))) return false;
    }
    return true;
  }

  async function getStreamUrl(track) {
    // Prefer the current official SDK URL builder so api_key is attached correctly.
    if (state.sdk && state.apiKey && typeof state.sdk.tracks?.getTrackStreamUrl === 'function') {
      return await state.sdk.tracks.getTrackStreamUrl({
        trackId: track.id,
        apiKey: state.apiKey
      });
    }

    const url = new URL(`${PUBLIC_API}/tracks/${encodeURIComponent(track.id)}/stream`);
    if (state.apiKey) url.searchParams.set('api_key', state.apiKey);
    return url.toString();
  }

  function legacyStreamUrl(track) {
    const url = new URL(`${LEGACY_PUBLIC_API}/tracks/${encodeURIComponent(track.id)}/stream`);
    url.searchParams.set('app_name', APP_NAME);
    if (state.apiKey) url.searchParams.set('api_key', state.apiKey);
    return url.toString();
  }

  function normalizeTrack(t) {
    const artwork = t.artwork || {};
    const user = t.user || {};
    return {
      id: String(t.id),
      title: t.title || 'Untitled',
      artist: user.name || user.handle || t.user?.name || 'Audius Artist',
      artwork: artwork._480x480 || artwork['480x480'] || artwork._150x150 || artwork['150x150'] || 'icons/placeholder.svg',
      genre: t.genre || '—',
      bpm: t.bpm || t.tempo || null,
      key: t.musicalKey || t.musical_key || null,
      duration: Number(t.duration) || 0,
      isStreamable: t.isStreamable ?? t.is_streamable ?? null,
      isStreamGated: t.isStreamGated ?? t.is_stream_gated ?? null,
      allowedApiKeys: t.allowedApiKeys ?? t.allowed_api_keys ?? null,
    };
  }

  function renderResults(tracks, append = false) {
    const root = $('results');
    if (!append) root.innerHTML = '';
    tracks.map(normalizeTrack).forEach(track => {
      if (state.renderedTrackIds.has(track.id)) return;
      state.renderedTrackIds.add(track.id);
      const card = document.createElement('div');
      card.className = 'result-card';
      card.innerHTML = `
        <img src="${escapeHtml(track.artwork)}" alt="" loading="lazy" />
        <div>
          <h3 title="${escapeHtml(track.title)}">${escapeHtml(track.title)}</h3>
          <p>${escapeHtml(track.artist)}</p>
          <div class="result-meta">${escapeHtml(track.genre)}${track.bpm ? ` • ${track.bpm} BPM` : ''}${track.key ? ` • ${escapeHtml(track.key)}` : ''}</div>
        </div>
        <div class="load-actions">
          <button data-load="A">LOAD A</button>
          <button data-load="B">LOAD B</button>
        </div>`;
      card.querySelector('[data-load="A"]').addEventListener('click', () => loadTrack('A', track));
      card.querySelector('[data-load="B"]').addEventListener('click', () => loadTrack('B', track));
      root.appendChild(card);
    });
  }

  function escapeHtml(value) {
    return String(value ?? '')
      .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;').replaceAll("'", '&#039;');
  }

  async function loadTrack(id, track) {
    await initAudio();
    const deck = state.decks[id];
    deck.audio.pause();
    deck.audio.currentTime = 0;
    disableSync(id, true);
    deck.audio.playbackRate = 1;
    $(`pitch${id}`).value = 0;
    $(`pitchLabel${id}`).textContent = '0.0%';
    deck.track = track;
    deck.beatAnchor = NaN; deck.beatConfidence = 0; deck.prevLowEnergy = 0; deck.fluxAvg = 0; deck.lastOnsetMedia = -99; updateGridUi(id);

    // v3: build the stream URL through Audius' current SDK/API path.
    // This fixes the v2 bug where search used the new SDK but playback still used an old public stream URL.
    deck.streamAttempt = 0;
    deck.primaryStreamUrl = await getStreamUrl(track);
    deck.fallbackStreamUrl = legacyStreamUrl(track);
    deck.audio.src = deck.primaryStreamUrl;
    deck.audio.load();

    $(`title${id}`).textContent = track.title;
    $(`artist${id}`).textContent = track.artist;
    $(`cover${id}`).src = track.artwork || 'icons/placeholder.svg';
    updateBpmDisplay(id);
    $(`key${id}`).textContent = `KEY ${track.key || '—'}`;
    $(`duration${id}`).textContent = formatTime(track.duration);
    $(`seek${id}`).value = 0;
    $(`time${id}`).textContent = '00:00';
    setSyncUi(id, false);
    setMessage(`${track.title} → Deck ${id} loaded.`);
    window.dispatchEvent(new CustomEvent('arda-track-loaded',{detail:{id,track,url:deck.primaryStreamUrl}}));
    closeMusic();
  }

  async function togglePlay(id) {
    await initAudio();
    const deck = state.decks[id];
    if (!deck.track) return setMessage(`Load track ke Deck ${id} dulu.`, true);
    const btn = document.querySelector(`[data-action="play"][data-deck="${id}"]`);
    if (deck.audio.paused) {
      try {
        await deck.audio.play();
        if (!state.decks[state.masterDeck]?.track || (state.decks[state.masterDeck]?.audio.paused && !deck.syncActive)) setMasterDeck(id, true);
        if (deck.syncActive) {
          const master = state.decks[deck.syncMasterId];
          if (master?.track && !master.audio.paused) {
            beginBeatChase(id);
            // Run immediately once instead of waiting for the animation loop.
            maintainSync(id);
          }
        }
        btn.textContent = 'Ⅱ';
        btn.classList.add('playing');
      } catch (err) {
        console.error(err);
        setMessage(`Playback Deck ${id} gagal. Coba track lain atau periksa koneksi/API.`, true);
      }
    } else {
      deck.audio.pause();
      btn.textContent = '▶';
      btn.classList.remove('playing');
    }
  }

  async function toggleCue(id) {
    await initAudio();
    const deck = state.decks[id];
    deck.cue = !deck.cue;
    deck.cueGain.gain.setTargetAtTime(deck.cue ? 1 : 0, state.ctx.currentTime, 0.01);
    const btn = document.querySelector(`[data-action="cue"][data-deck="${id}"]`);
    btn.classList.toggle('active', deck.cue);
    if (!state.splitCue) $('cueHelp').textContent = 'Aktifkan SPLIT CUE agar cue keluar di kanal RIGHT.';
  }

  function setEq(id, band, value) {
    if (!state.audioReady) return;
    state.decks[id][band].gain.setTargetAtTime(Number(value), state.ctx.currentTime, 0.015);
  }

  function setPitch(id, percent) {
    const deck = state.decks[id];
    if (!deck) return;
    if (deck.syncActive) disableSync(id, true);
    const p = Number(percent);
    deck.audio.playbackRate = 1 + p / 100;
    $(`pitchLabel${id}`).textContent = `${p >= 0 ? '+' : ''}${p.toFixed(1)}%`;
    updateBpmDisplay(id);
    document.querySelector(`[data-action="sync"][data-deck="${id}"]`)?.classList.remove('synced');
  }

  function drawLoop() {
    if (!state.audioReady) return;
    ['A', 'B'].forEach(id => {
      const deck = state.decks[id];
      // v1.5+ owns sync and beatgrid. Keep the legacy engine completely dormant
      // so two sync loops cannot fight over playbackRate / phase.
      if (!window.ARDADJSync) maintainSync(id);
      const analyser = deck.analyser;
      const data = new Uint8Array(analyser.frequencyBinCount);
      analyser.getByteFrequencyData(data);
      if (!window.ARDADJAnalysis) observeBeat(id, data);
      if (!window.ARDADJAnalysis?.ownsWaveform) {
        const canvas = $(`wave${id}`);
        const c = canvas.getContext('2d');
        const w = canvas.width, h = canvas.height;
        c.clearRect(0, 0, w, h);
        c.fillStyle = '#080c15'; c.fillRect(0,0,w,h);
        const timeData = new Uint8Array(analyser.fftSize);
        analyser.getByteTimeDomainData(timeData);
        c.strokeStyle = id === 'A' ? '#42b9ff' : '#ffad4a';
        c.lineWidth = 1.5;
        c.beginPath();
        for (let x=0;x<w;x++) {
          const idx=Math.floor((x/w)*timeData.length);
          const y=(timeData[idx]/255)*h;
          if(x===0)c.moveTo(x,y);else c.lineTo(x,y);
        }
        c.stroke();
      }
      const avg = data.reduce((a,b)=>a+b,0) / (data.length*255 || 1);
      $(`meter${id}`).value = Math.min(1, avg * 2.2);

      const audio = deck.audio;
      if (Number.isFinite(audio.duration) && audio.duration > 0) {
        $(`seek${id}`).value = Math.floor((audio.currentTime / audio.duration) * 1000);
        $(`time${id}`).textContent = formatTime(audio.currentTime);
        $(`duration${id}`).textContent = formatTime(audio.duration);
      }
    });
    requestAnimationFrame(drawLoop);
  }

  function isStandalone() {
    return window.matchMedia?.('(display-mode: standalone)').matches || window.navigator.standalone === true;
  }

  function syncDjModeButton() {
    const btn = $('djModeBtn');
    if (!btn) return;
    const active = document.body.classList.contains('focus-mode');
    btn.classList.toggle('active', active);
    btn.textContent = active ? '×' : '⛶';
    btn.setAttribute('aria-label', active ? 'Keluar DJ Mode' : 'Masuk DJ Mode');
    btn.setAttribute('title', active ? 'Keluar DJ Mode' : 'DJ Mode');
  }

  async function toggleDjMode() {
    const entering = !document.body.classList.contains('focus-mode');
    try {
      if (entering) {
        await document.documentElement.requestFullscreen?.();
        try { await screen.orientation?.lock?.('landscape'); } catch (_) {}
        document.body.classList.add('focus-mode');
        state.focusMode = true;
      } else {
        if (document.fullscreenElement) await document.exitFullscreen?.();
        document.body.classList.remove('focus-mode');
        state.focusMode = false;
      }
    } catch (err) {
      document.body.classList.toggle('focus-mode');
      state.focusMode = document.body.classList.contains('focus-mode');
    }
    syncDjModeButton();
  }

  async function installPwa() {
    const prompt = state.deferredInstallPrompt;
    if (prompt) {
      prompt.prompt();
      try { await prompt.userChoice; } catch (_) {}
      state.deferredInstallPrompt = null;
      $('installBtn').hidden = true;
      return;
    }
    alert('Untuk install ARDA DJ: buka menu Chrome (⋮) lalu pilih “Install app” atau “Add to Home screen”. Setelah terpasang, buka dari ikon ARDA DJ agar browser bar hilang.');
  }

  function bindUi() {
    $('apiKeyInput').value = state.apiKey;
    initSdk();
    document.querySelectorAll('[data-action="master"]').forEach(b=>b.classList.toggle('active',b.dataset.deck===state.masterDeck));
    $('djModeBtn').addEventListener('click', toggleDjMode);
    syncDjModeButton();
    document.addEventListener('fullscreenchange', () => {
      // Android's file picker can temporarily drop DOM fullscreen. Do not leave
      // DJ layout just because a song was selected outside the WebView.
      document.body.classList.toggle('focus-mode', !!state.focusMode);
      syncDjModeButton();
    });
    const restoreDjMode = () => {
      if (state.focusMode) document.body.classList.add('focus-mode');
      syncDjModeButton();
    };
    window.addEventListener('focus', restoreDjMode);
    document.addEventListener('visibilitychange', () => {
      if (!document.hidden) restoreDjMode();
    });
    $('installBtn').addEventListener('click', installPwa);
    if (!isStandalone()) $('installBtn').hidden = false;

    $('musicBtn').addEventListener('click', () => {
      const drawer = $('musicDrawer');
      if (drawer.classList.contains('open')) closeMusic(); else openMusic();
    });
    $('closeMusicBtn').addEventListener('click', closeMusic);
    $('drawerBackdrop')?.addEventListener('click', closeMusic);
    document.querySelectorAll('[data-genre]').forEach(btn => btn.addEventListener('click', () => {
      document.querySelectorAll('[data-genre]').forEach(x => x.classList.remove('active'));
      btn.classList.add('active');
      $('genreSelect').value = btn.dataset.genre;
      searchTracks('', true);
    }));

    $('settingsBtn').addEventListener('click', () => $('settingsDialog').showModal());
    $('saveKeyBtn').addEventListener('click', () => {
      state.apiKey = $('apiKeyInput').value.trim();
      if (state.apiKey) localStorage.setItem('accdj_audius_api_key', state.apiKey);
      else localStorage.removeItem('accdj_audius_api_key');
      initSdk();
      setMessage(state.apiKey ? 'Audius API key aktif. Reload MUSIC/Trending untuk hasil paling stabil.' : 'Public Mode aktif. Sebagian track dapat dibatasi provider.', !state.apiKey);
    });
    $('clearKeyBtn').addEventListener('click', () => {
      state.apiKey = '';
      $('apiKeyInput').value = '';
      localStorage.removeItem('accdj_audius_api_key');
      initSdk();
    });

    $('searchBtn').addEventListener('click', () => {
      const q = $('searchInput').value.trim();
      if (!q) return setMessage('Ketik judul / artist dulu.', true);
      searchTracks(q, false);
    });
    $('searchInput').addEventListener('keydown', e => {
      if (e.key === 'Enter') $('searchBtn').click();
    });
    $('trendingBtn').addEventListener('click', () => searchTracks('', true));
    $('moreBtn').addEventListener('click', () => searchTracks(state.lastQuery, state.lastTrending, true));

    document.querySelectorAll('[data-action="master"]').forEach(b => b.addEventListener('click', () => setMasterDeck(b.dataset.deck)));
    document.querySelectorAll('[data-action="play"]').forEach(b => b.addEventListener('click', () => togglePlay(b.dataset.deck)));
    document.querySelectorAll('[data-action="sync"]').forEach(b => b.addEventListener('click', () => syncDeck(b.dataset.deck)));
    document.querySelectorAll('[data-action="nudge"]').forEach(b => b.addEventListener('click', () => nudgeDeck(b.dataset.deck, Number(b.dataset.dir))));
    document.querySelectorAll('[data-action="cue"]').forEach(b => b.addEventListener('click', () => toggleCue(b.dataset.deck)));
    document.querySelectorAll('[data-action="restart"]').forEach(b => b.addEventListener('click', async () => {
      await initAudio(); const a = state.decks[b.dataset.deck].audio; a.currentTime = 0;
    }));
    document.querySelectorAll('[data-action="loop"]').forEach(b => b.addEventListener('click', async () => {
      await initAudio(); const d = state.decks[b.dataset.deck]; d.loop = !d.loop; d.audio.loop = d.loop; b.classList.toggle('looping', d.loop);
    }));

    ['A','B'].forEach(id => {
      ['low','mid','high'].forEach(band => $(`${band}${id}`).addEventListener('input', e => setEq(id, band, e.target.value)));
      $(`pitch${id}`).addEventListener('input', e => setPitch(id, e.target.value));
      $(`seek${id}`).addEventListener('input', e => {
        const a = state.decks[id]?.audio;
        if (a && Number.isFinite(a.duration) && a.duration > 0) a.currentTime = (Number(e.target.value)/1000) * a.duration;
      });
      $(`audio${id}`).addEventListener('ended', () => {
        const btn = document.querySelector(`[data-action="play"][data-deck="${id}"]`);
        btn.textContent = '▶'; btn.classList.remove('playing');
      });
      $(`audio${id}`).addEventListener('error', () => {
        const d = state.decks[id];
        if (!d?.track) return;
        const mediaCode = d.audio.error?.code || 0;
        // One automatic fallback for Public Mode / provider edge differences.
        if (d.streamAttempt === 0 && d.fallbackStreamUrl && d.fallbackStreamUrl !== d.audio.src) {
          d.streamAttempt = 1;
          d.audio.src = d.fallbackStreamUrl;
          d.audio.load();
          setMessage(`Deck ${id}: mencoba stream fallback…`);
          return;
        }
        setMessage(`Track ini tidak bisa di-stream ke Deck ${id} (media ${mediaCode || '?'}). Track otomatis dianggap unavailable.`, true);
      });
      $(`audio${id}`).addEventListener('canplay', () => {
        const d = state.decks[id];
        if (d?.track) setMessage(`${d.track.title} → Deck ${id} siap dimainkan${state.apiKey ? ' • API key aktif' : ''}.`);
      });
    });

    $('crossfader').addEventListener('input', updateCrossfader);
    $('masterVol').addEventListener('input', async e => {
      await initAudio(); state.masterGain.gain.setTargetAtTime(Number(e.target.value), state.ctx.currentTime, .01);
    });
    $('splitCueBtn').addEventListener('click', async () => { await initAudio(); setSplitCue(!state.splitCue); });

    document.addEventListener('fullscreenchange', () => {
      if (!document.fullscreenElement && state.focusMode) {
        state.focusMode = false;
        document.body.classList.remove('focus-mode');
        $('djModeBtn').classList.remove('active');
        $('djModeBtn').textContent = 'DJ MODE';
      }
    });

    // First gesture prepares audio on mobile.
    document.addEventListener('pointerdown', () => {
      if (state.ctx?.state === 'suspended') state.ctx.resume().catch(()=>{});
    }, { passive:true });
  }

  window.addEventListener('beforeinstallprompt', (event) => {
    event.preventDefault();
    state.deferredInstallPrompt = event;
    const btn = $('installBtn');
    if (btn && !isStandalone()) btn.hidden = false;
  });
  window.addEventListener('appinstalled', () => {
    state.deferredInstallPrompt = null;
    const btn = $('installBtn');
    if (btn) btn.hidden = true;
  });

  if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => navigator.serviceWorker.register('./sw.js').catch(console.warn));
  }
  bindUi();
  if (new URLSearchParams(location.search).get('mode') === 'dj') setTimeout(()=>document.body.classList.add('focus-mode'),50);
  window.ARDADJCore = window.ARDADJCore || {};

  window.ARDADJCore.loadLocal = async function(id, file) {
    if (!file || !['A','B'].includes(id)) throw new Error('Invalid local track');
    await initAudio();
    const deck = state.decks[id];
    if (!deck) throw new Error('Deck not ready');
    deck.audio.pause();
    try { if (deck.localObjectUrl) URL.revokeObjectURL(deck.localObjectUrl); } catch (_) {}
    disableSync(id, true);
    deck.audio.playbackRate = 1;
    const pitch = $(`pitch${id}`); if (pitch) pitch.value = 0;
    const pitchLabel = $(`pitchLabel${id}`); if (pitchLabel) pitchLabel.textContent = '0.0%';
    const cleanName = String(file.name || 'Local Track').replace(/\.[^.]+$/, '').replace(/_/g, ' ');
    deck.track = {
      id: `local:${file.name}:${file.size}:${file.lastModified}`, title: cleanName,
      artist: 'Drive / Files', artwork: './icons/placeholder.svg', genre: 'Local',
      bpm: null, key: null, duration: 0, local: true
    };
    deck.beatAnchor = NaN; deck.beatConfidence = 0; deck.prevLowEnergy = 0; deck.fluxAvg = 0;
    deck.lastOnsetMedia = -99; deck.lastAnchorResetMedia = 0;
    updateGridUi(id);
    const url = URL.createObjectURL(file);
    deck.localObjectUrl = url; deck.primaryStreamUrl = url; deck.fallbackStreamUrl = ''; deck.streamAttempt = 1;
    deck.audio.src = url; deck.audio.load();
    $(`title${id}`).textContent = cleanName;
    $(`artist${id}`).textContent = 'Drive / Files';
    const cover = $(`cover${id}`); if (cover) cover.src = './icons/placeholder.svg';
    $(`bpm${id}`).textContent = 'BPM ANALYZE';
    $(`key${id}`).textContent = 'KEY —';
    $(`grid${id}`).textContent = 'ANALYZE';
    $(`seek${id}`).value = 0; $(`time${id}`).textContent = '00:00'; $(`duration${id}`).textContent = '00:00';
    setSyncUi(id, false); setMessage(`${cleanName} → Deck ${id} loaded dari Drive / Files.`);
    window.dispatchEvent(new CustomEvent('arda-track-loaded',{detail:{id,track:deck.track,file,url}}));
    return true;
  };

  window.ARDADJCore.setFx = async function(id, type, amount, enabled) {
    await initAudio();
    const d = state.decks[id]; if (!d) return false;
    const a = Math.max(0, Math.min(1, Number(amount) || 0));
    const now = state.ctx.currentTime;
    const ramp = (param, value, tc = .025) => param.setTargetAtTime(value, now, tc);
    ramp(d.fxFilter.frequency, 22000); ramp(d.fxFilter.Q, .7); ramp(d.fxDry.gain, 1);
    ramp(d.fxDelayWet.gain, 0); ramp(d.fxReverbWet.gain, 0); ramp(d.fxFeedback.gain, .28);
    if (!enabled || !type || type === 'OFF') return true;
    if (type === 'FILTER') {
      const freq = 18000 * Math.pow(0.035, a);
      ramp(d.fxFilter.frequency, Math.max(280, freq), .018); ramp(d.fxFilter.Q, .8 + a * 4.2, .018);
    } else if (type === 'ECHO') {
      const bpm = Number(window.ARDADJAnalysis?.getBpm?.(id)) || Number(window.ACCDJ9?.sourceBpm?.(id)) || Number(d.track?.bpm) || 120;
      const beat = 60 / bpm;
      ramp(d.fxDelay.delayTime, Math.max(.06, Math.min(.75, beat * .5)), .01);
      ramp(d.fxDelayWet.gain, .12 + a * .52); ramp(d.fxFeedback.gain, .18 + a * .52); ramp(d.fxDry.gain, 1 - a * .18);
    } else if (type === 'REVERB') {
      ramp(d.fxReverbWet.gain, .10 + a * .58); ramp(d.fxDry.gain, 1 - a * .16);
    }
    return true;
  };
  window.ARDADJCore.decodeAudio = async function(arrayBuffer) {
    await initAudio();
    return await state.ctx.decodeAudioData(arrayBuffer.slice(0));
  };
  window.ARDADJCore.getAnalyser = function(id) { return state.decks[id]?.analyser || null; };
  window.ARDADJCore.getDeck = function(id) { return state.decks[id] || null; };
  window.ACCDJCore = window.ARDADJCore;
})();
