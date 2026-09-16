(() => {
  'use strict';

  function install() {
    const version = document.querySelector('.version');
    if (version) version.textContent = 'v6.1.0';

    if (!window.ACCApp || window.ACCApp.__v61Wrapped) return;
    const originalProgress = window.ACCApp.onNativeProgress;
    if (typeof originalProgress === 'function') {
      window.ACCApp.onNativeProgress = function(value, message) {
        let p = Math.max(0, Math.min(95, Number(value) || 0));
        let text = String(message || 'Memproses…');
        if (/solving js|\[jsc|javascript challenge/i.test(text)) {
          p = Math.min(p, 15);
          text = 'Kompatibilitas YouTube…';
        }
        return originalProgress.call(this, p, text);
      };
    }
    window.ACCApp.__v61Wrapped = true;
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', install, { once: true });
  } else {
    install();
  }
})();
