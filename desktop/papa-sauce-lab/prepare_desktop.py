from pathlib import Path
import shutil
import json
import re

here = Path(__file__).resolve().parent
repo = here.parents[1]
src = repo / 'apps' / 'papa-sauce-lab' / 'index.html'
project = repo / 'apps' / 'papa-sauce-lab' / 'project.json'
out = here / 'app'
out.mkdir(parents=True, exist_ok=True)

html = src.read_text(encoding='utf-8')
meta_version = int(json.loads(project.read_text(encoding='utf-8')).get('version', 0) or 0)
markers = [int(x) for x in re.findall(r'PSL_V(\d+)', html)]
version = str(max([meta_version] + markers))
html = html.replace('__PSL_APP_VERSION__', version)

if 'PSL_DESKTOP_BUILD' not in html:
    html = html.replace('</head>', '<meta name="psl-build" content="PSL_DESKTOP_BUILD">\n<link rel="stylesheet" href="desktop.css">\n</head>', 1)
    html = html.replace('</body>', '<script src="bridge.js"></script>\n</body>', 1)

(out / 'index.html').write_text(html, encoding='utf-8')
shutil.copy2(here / 'desktop.css', out / 'desktop.css')
shutil.copy2(here / 'bridge.js', out / 'bridge.js')
print(f'Prepared Papa Sauce Lab Desktop v{version} from current mobile source')
