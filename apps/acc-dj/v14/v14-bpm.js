(() => {
  'use strict';
  const byId = (id) => document.getElementById(id);
  const decks = {
    A: { bpm: NaN, lockedBpm: NaN, confidence: 0, anchor: NaN, beatConfidence: 0, onsets: [], prev: null, fluxAvg: 0, energyAvg: 0, lastOnset: -99, candidates: [], trackKey: '', analysisStart: 0 },
    B: { bpm: NaN, lockedBpm: NaN, confidence: 0, anchor: NaN, beatConfidence: 0, onsets: [], prev: null, fluxAvg: 0, energyAvg: 0, lastOnset: -99, candidates: [], trackKey: '', analysisStart: 0 }
  };

  function normalizeTempo(value) {
    let bpm = Number(value);
    if (!Number.isFinite(bpm) || bpm <= 0) return null;
    while (bpm < 78) bpm *= 2;
    while (bpm > 160) bpm /= 2;
    return bpm;
  }

  function metaBpm(id) {
    const text = byId(`bpm${id}`)?.textContent || '';
    if (/AUTO|LOCK/.test(text)) return null;
    const match = text.match(/([0-9]+(?:\.[0-9]+)?)/);
    return normalizeTempo(match ? Number(match[1]) : NaN);
  }

  function sourceBpm(id) {
    const d = decks[id];
    if (Number.isFinite(d.lockedBpm)) return d.lockedBpm;
    if (d.confidence >= 4 && Number.isFinite(d.bpm)) return d.bpm;
    return metaBpm(id);
  }

  function resetDeck(id, key, now) {
    const d = decks[id];
    d.bpm = NaN; d.lockedBpm = NaN; d.confidence = 0; d.anchor = NaN; d.beatConfidence = 0;
    d.onsets = []; d.prev = null; d.fluxAvg = 0; d.energyAvg = 0; d.lastOnset = -99; d.candidates = [];
    d.trackKey = key; d.analysisStart = now || 0;
    const grid = byId(`grid${id}`); if (grid) { grid.textContent = 'ANALYZE'; grid.classList.remove('grid-ready'); }
  }

  function median(values) {
    if (!values.length) return NaN;
    const a = [...values].sort((x,y)=>x-y); const m = Math.floor(a.length/2);
    return a.length % 2 ? a[m] : (a[m-1] + a[m]) / 2;
  }

  function estimateTempo(d) {
    const times = d.onsets;
    if (times.length < 8 || Number.isFinite(d.lockedBpm)) return;
    const hist = new Map();
    const from = Math.max(0, times.length - 36);
    for (let i = from + 1; i < times.length; i++) {
      for (let j = Math.max(from, i - 10); j < i; j++) {
        const dt = times[i] - times[j];
        if (dt < .28 || dt > 2.2) continue;
        const bpm = normalizeTempo(60 / dt);
        if (!bpm) continue;
        const key = Math.round(bpm * 2) / 2;
        hist.set(key, (hist.get(key) || 0) + 1 / Math.sqrt(i - j));
      }
    }
    if (!hist.size) return;
    const ranked = [...hist.entries()].sort((a,b) => b[1] - a[1]);
    const peak = ranked[0][0];
    d.candidates.push(peak);
    if (d.candidates.length > 12) d.candidates.shift();
    const med = median(d.candidates);
    const close = d.candidates.filter(v => Math.abs(v - med) <= 1.25);
    d.bpm = median(close.length >= 4 ? close : d.candidates);
    d.confidence = close.length;

    // Lock only after repeated agreement. Once locked, FREE playback never changes BPM again.
    if (d.candidates.length >= 8 && close.length >= 7) {
      const spread = Math.max(...close) - Math.min(...close);
      if (spread <= 1.5) d.lockedBpm = Math.round(median(close) * 10) / 10;
    }
  }

  function attachAnalysers() {
    const list = window.__accDjAnalysers || [];
    if (!decks.A.analyser && list[0]) { decks.A.analyser = list[0].node; decks.A.context = list[0].context; }
    if (!decks.B.analyser && list[1]) { decks.B.analyser = list[1].node; decks.B.context = list[1].context; }
  }

  function analyze(id) {
    attachAnalysers();
    const d = decks[id];
    const audio = byId(`audio${id}`);
    if (!d.analyser || !audio) return;
    const key = `${audio.currentSrc || audio.src}|${audio.duration || 0}`;
    if (key && key !== d.trackKey) resetDeck(id, key, audio.currentTime);
    if (audio.paused) return;

    const data = new Uint8Array(d.analyser.frequencyBinCount);
    d.analyser.getByteFrequencyData(data);
    if (!d.prev || d.prev.length !== data.length) d.prev = new Uint8Array(data.length);
    const binHz = d.context.sampleRate / d.analyser.fftSize;
    const lo = Math.max(1, Math.floor(42 / binHz));
    const hi = Math.min(data.length - 1, Math.ceil(230 / binHz));
    let flux = 0, energy = 0, bins = 0;
    for (let i = lo; i <= hi; i++) {
      flux += Math.max(0, data[i] - (d.prev[i] || 0));
      energy += data[i]; d.prev[i] = data[i]; bins++;
    }
    flux /= Math.max(1, bins); energy /= Math.max(1, bins);
    d.fluxAvg = d.fluxAvg ? d.fluxAvg * .965 + flux * .035 : flux;
    d.energyAvg = d.energyAvg ? d.energyAvg * .97 + energy * .03 : energy;
    const now = audio.currentTime;
    const bpm = sourceBpm(id) || 120;
    const beat = 60 / bpm;
    const threshold = Math.max(3.2, d.fluxAvg * 1.85);
    const enough = energy > Math.max(22, d.energyAvg * .78);
    if (flux < threshold || !enough || now - d.lastOnset < Math.max(.20, beat * .34)) return;
    d.lastOnset = now; d.onsets.push(now); if (d.onsets.length > 48) d.onsets.shift();
    estimateTempo(d);

    const realBpm = sourceBpm(id) || bpm;
    const realBeat = 60 / realBpm;
    if (!Number.isFinite(d.anchor)) { d.anchor = now; d.beatConfidence = 1; }
    else {
      const n = Math.round((now - d.anchor) / realBeat);
      const error = now - (d.anchor + n * realBeat);
      if (Math.abs(error) <= realBeat * .20) {
        // Once BPM + beatgrid are stable, freeze the anchor instead of letting
        // every transient slowly pull the grid around during a mix.
        if (!(Number.isFinite(d.lockedBpm) && d.beatConfidence >= 6)) {
          d.anchor += error * (d.beatConfidence >= 4 ? .05 : .12);
        }
        d.beatConfidence = Math.min(12, d.beatConfidence + 1);
      }
    }

    const bpmEl = byId(`bpm${id}`);
    if (bpmEl) {
      if (Number.isFinite(d.lockedBpm)) bpmEl.textContent = `BPM ${d.lockedBpm.toFixed(1)} LOCK`;
      else if (Number.isFinite(d.bpm) && d.confidence >= 4) bpmEl.textContent = `BPM ${d.bpm.toFixed(1)} ANALYZE`;
    }
    const grid = byId(`grid${id}`);
    if (grid && Number.isFinite(d.lockedBpm) && d.beatConfidence >= 4) { grid.textContent = 'GRID ✓'; grid.classList.add('grid-ready'); }
  }

  function loop() { analyze('A'); analyze('B'); requestAnimationFrame(loop); }
  window.ACCDJ9 = { decks, sourceBpm };
  window.ARDADJBPM = { decks, sourceBpm, resetDeck };
  requestAnimationFrame(loop);
})();
