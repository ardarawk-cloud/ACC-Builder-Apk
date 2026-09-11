(() => {
  const logo = document.querySelector('.logo');
  if (logo) {
    logo.textContent = '';
    logo.style.padding = '0';
    logo.style.overflow = 'hidden';
    logo.style.border = '0';
    logo.style.background = 'transparent';
    const img = document.createElement('img');
    img.src = 'icon.svg';
    img.alt = 'KAI 3D';
    img.style.width = '100%';
    img.style.height = '100%';
    img.style.display = 'block';
    img.style.objectFit = 'cover';
    logo.appendChild(img);
  }

  if (!document.querySelector('link[rel="icon"]')) {
    const favicon = document.createElement('link');
    favicon.rel = 'icon';
    favicon.type = 'image/svg+xml';
    favicon.href = 'icon.svg';
    document.head.appendChild(favicon);
  }
})();
