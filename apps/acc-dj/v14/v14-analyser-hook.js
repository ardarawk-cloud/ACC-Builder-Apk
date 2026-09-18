(() => {
  const Native = window.AudioContext || window.webkitAudioContext;
  if (!Native || !Native.prototype?.createAnalyser) return;
  const original = Native.prototype.createAnalyser;
  window.__accDjAnalysers = [];
  Native.prototype.createAnalyser = function(...args) {
    const node = original.apply(this, args);
    window.__accDjAnalysers.push({ node, context: this });
    return node;
  };
})();
