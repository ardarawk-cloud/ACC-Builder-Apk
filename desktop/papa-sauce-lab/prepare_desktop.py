from pathlib import Path
import shutil

here = Path(__file__).resolve().parent
repo = here.parents[1]
src = repo / 'apps' / 'papa-sauce-lab' / 'index.html'
out = here / 'app'
out.mkdir(parents=True, exist_ok=True)

html = src.read_text(encoding='utf-8')
if 'PSL_DESKTOP_BUILD' not in html:
    html = html.replace('</head>', '<meta name="psl-build" content="PSL_DESKTOP_BUILD">\n<link rel="stylesheet" href="desktop.css">\n</head>', 1)
    html = html.replace('</body>', '<script src="bridge.js"></script>\n</body>', 1)

(out / 'index.html').write_text(html, encoding='utf-8')
shutil.copy2(here / 'desktop.css', out / 'desktop.css')
shutil.copy2(here / 'bridge.js', out / 'bridge.js')
print('Prepared Papa Sauce Lab Desktop from current mobile source')
