#!/usr/bin/env python3
from pathlib import Path
import shutil
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: patch_acc_media_playlist_queue_v62.py <capacitor-build-dir>')

root = Path(sys.argv[1])
repo_root = Path(__file__).resolve().parents[1]
main_files = list((root / 'android/app/src/main/java').rglob('MainActivity.java'))
if not main_files:
    raise SystemExit('MainActivity.java not found')
main = main_files[0]
s = main.read_text(encoding='utf-8')

# YouTube changed android_vr behaviour in 2026; keep the fast no-JS path on
# visionOS + embeddable fallback instead of a client that increasingly needs PO tokens.
s = s.replace(
    'youtube:player_client=visionos,android_vr,web_embedded;player_skip=js',
    'youtube:player_client=visionos,web_embedded;player_skip=js'
)
main.write_text(s, encoding='utf-8')

src = repo_root / 'apps/acc-media-downloader/playlist-queue-v62.js'
if not src.exists():
    raise SystemExit('playlist-queue-v62.js source missing')
www = root / 'www'
www.mkdir(parents=True, exist_ok=True)
shutil.copyfile(src, www / 'playlist-queue-v62.js')
index = www / 'index.html'
if not index.exists():
    raise SystemExit('built index.html missing')
html = index.read_text(encoding='utf-8')
marker = '<script src="playlist-queue-v62.js"></script>'
if marker not in html:
    pos = html.lower().rfind('</body>')
    if pos < 0:
        raise SystemExit('index.html has no closing body')
    html = html[:pos] + marker + '\n' + html[pos:]
    index.write_text(html, encoding='utf-8')

print('Applied ACC Media v6.2 robust playlist queue controller')
