from pathlib import Path
import shutil
import json

here = Path(__file__).resolve().parent
repo = here.parents[1]
src = repo / 'apps' / 'papa-sauce-lab' / 'index.html'
project = repo / 'apps' / 'papa-sauce-lab' / 'project.json'
out = here / 'app'
out.mkdir(parents=True, exist_ok=True)

html = src.read_text(encoding='utf-8')
version = str(json.loads(project.read_text(encoding='utf-8')).get('version', ''))
html = html.replace('__PSL_APP_VERSION__', version)

if 'PSL_DESKTOP_BUILD' not in html:
    html = html.replace('</head>', '<meta name="psl-build" content="PSL_DESKTOP_BUILD">\n<link rel="stylesheet" href="desktop.css">\n</head>', 1)
    html = html.replace('</body>', '<script src="bridge.js"></script>\n</body>', 1)

(out / 'index.html').write_text(html, encoding='utf-8')
shutil.copy2(here / 'desktop.css', out / 'desktop.css')
shutil.copy2(here / 'bridge.js', out / 'bridge.js')
print(f'Prepared Papa Sauce Lab Desktop v{version} from current mobile source')
