#!/usr/bin/env python3
from pathlib import Path
import shutil
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: patch_acc_media_youtube_stall_v7.py <capacitor-build-dir>')

root = Path(sys.argv[1])
repo_root = Path(__file__).resolve().parents[1]
main_files = list((root / 'android/app/src/main/java').rglob('MainActivity.java'))
if not main_files:
    raise SystemExit('MainActivity.java not found')
main = main_files[0]
s = main.read_text(encoding='utf-8')

FAST_ARGS = 'youtube:player_client=visionos,android_vr,web_embedded;player_skip=js'
s = s.replace('youtube:player_client=default,web_embedded', FAST_ARGS)

# Add bounded network timeouts and use non-JS YouTube clients first. This avoids
# the long QuickJS EJS challenge path that can sit at 100% before a file exists.
needle = '                    request.addOption("--retry-sleep", "http:1");\n'
parts = s.split(needle)
if len(parts) > 1:
    rebuilt = parts[0]
    for tail in parts[1:]:
        rebuilt += needle
        lookahead = tail[:500]
        if '--socket-timeout' not in lookahead:
            rebuilt += '                    request.addOption("--socket-timeout", "20");\n'
        if '--extractor-args' not in lookahead:
            rebuilt += f'                    request.addOption("--extractor-args", "{FAST_ARGS}");\n'
        rebuilt += tail
    s = rebuilt

# Playlist metadata extraction also gets the fast no-JS clients and a socket timeout.
s = s.replace('                    request.addOption("--retries", "3");\n                    request.addOption("--extractor-args", "' + FAST_ARGS + '");',
              '                    request.addOption("--retries", "3");\n                    request.addOption("--socket-timeout", "20");\n                    request.addOption("--extractor-args", "' + FAST_ARGS + '");')

# Never show 100% until the file is actually published to Downloads.
helper_marker = '        @JavascriptInterface\n        public void downloadYoutubeAdvanced'
if 'private float safeProgress(Float value, String line)' not in s:
    helper = r'''
        private float safeProgress(Float value, String line) {
            float p = value == null ? 0f : value;
            String text = line == null ? "" : line.toLowerCase(Locale.US);
            if (text.contains("solving js") || text.contains("[jsc") || text.contains("javascript challenge")) {
                return Math.min(p, 15f);
            }
            if (text.contains("downloading webpage") || text.contains("player api") || text.contains("extracting url")) {
                return Math.min(p, 12f);
            }
            return Math.min(p, 95f);
        }

'''
    pos = s.find(helper_marker)
    if pos < 0:
        raise SystemExit('advanced YouTube bridge marker not found')
    s = s[:pos] + helper + s[pos:]

replacements = {
    'progress(p == null ? 0f : p, line == null ? "Downloading…" : line);':
        'progress(safeProgress(p, line), line == null ? "Downloading…" : line);',
    'socialProgress(p == null ? 0f : p, line == null ? "Downloading…" : line);':
        'socialProgress(safeProgress(p, line), line == null ? "Downloading…" : line);',
    'playlistTrackProgress(index, p == null ? 0f : p, line == null ? "Downloading…" : line);':
        'playlistTrackProgress(index, safeProgress(p, line), line == null ? "Downloading…" : line);',
    'ytPlaylistTrackProgress(index, p == null ? 0f : p, line == null ? "Downloading…" : line);':
        'ytPlaylistTrackProgress(index, safeProgress(p, line), line == null ? "Downloading…" : line);',
}
for old, new in replacements.items():
    s = s.replace(old, new)

main.write_text(s, encoding='utf-8')

src = repo_root / 'apps/acc-media-downloader/hotfix-v7.js'
if not src.exists():
    raise SystemExit('hotfix-v7.js source missing')
www = root / 'www'
www.mkdir(parents=True, exist_ok=True)
shutil.copyfile(src, www / 'hotfix-v7.js')
index = www / 'index.html'
if not index.exists():
    raise SystemExit('built index.html missing')
html = index.read_text(encoding='utf-8')
marker = '<script src="hotfix-v7.js"></script>'
if marker not in html:
    pos = html.lower().rfind('</body>')
    if pos < 0:
        raise SystemExit('index.html has no closing body')
    html = html[:pos] + marker + '\n' + html[pos:]
    index.write_text(html, encoding='utf-8')

print('Applied ACC Media v6.1 YouTube stall/progress hotfix')
