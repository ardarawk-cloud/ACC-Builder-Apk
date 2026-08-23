#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: patch_acc_media_metadata.py <capacitor-build-dir>')

root = Path(sys.argv[1])
main_files = list((root / 'android/app/src/main/java').rglob('MainActivity.java'))
if not main_files:
    raise SystemExit('MainActivity.java not found')
main = main_files[0]
s = main.read_text(encoding='utf-8')

# Allow title-based filenames while retaining the unique process prefix used for temp-file discovery.
s = s.replace('if (!f.isFile() || !f.getName().startsWith(prefix + ".")) continue;',
              'if (!f.isFile() || !f.getName().startsWith(prefix)) continue;', 1)

old_publish = '''            String extension = ext(source);\n            String displayName = ("audio".equals(kind) ? "ACC_Audio_" : "ACC_Video_") + System.currentTimeMillis() + "." + extension;'''
new_publish = '''            String extension = ext(source);\n            String displayName = source.getName();\n            int prefixCut = displayName.indexOf(" - ");\n            if (prefixCut >= 0 && prefixCut + 3 < displayName.length()) displayName = displayName.substring(prefixCut + 3);\n            displayName = displayName.replaceAll("[\\\\/:*?\\\"<>|\\r\\n]+", "_");\n            if (displayName.length() == 0 || displayName.length() > 220) {\n                displayName = ("audio".equals(kind) ? "ACC_Audio_" : "ACC_Video_") + System.currentTimeMillis() + "." + extension;\n            }'''
if old_publish not in s:
    raise SystemExit('publish filename target not found')
s = s.replace(old_publish, new_publish, 1)

# Existing three-argument method remains as compatibility fallback. Give it metadata + readable output names too.
old_output = 'request.addOption("-o", new File(tempDir, processId + ".%(ext)s").getAbsolutePath());'
new_output = 'request.addOption("-o", new File(tempDir, processId + " - %(title).120B - %(uploader).80B.%(ext)s").getAbsolutePath());'
if old_output not in s:
    raise SystemExit('legacy output template target not found')
s = s.replace(old_output, new_output, 1)

old_audio = '''                        request.addOption("-x");\n                        request.addOption("--audio-format", "mp3");\n                        request.addOption("--audio-quality", "0");'''
new_audio = '''                        request.addOption("-x");\n                        request.addOption("--audio-format", "mp3");\n                        request.addOption("--audio-quality", "0");\n                        request.addOption("--embed-metadata");\n                        request.addOption("--embed-thumbnail");\n                        request.addOption("--convert-thumbnails", "jpg");'''
if old_audio not in s:
    raise SystemExit('legacy audio target not found')
s = s.replace(old_audio, new_audio, 1)

old_video = '''                        request.addOption("-f", videoFormat(quality));\n                        request.addOption("--merge-output-format", "mp4");'''
new_video = '''                        request.addOption("-f", videoFormat(quality));\n                        request.addOption("--merge-output-format", "mp4");\n                        request.addOption("--embed-metadata");'''
if old_video not in s:
    raise SystemExit('legacy video target not found')
s = s.replace(old_video, new_video, 1)

# Add a richer 4-argument bridge for the new UI bitrate selector.
insert_before = '''        }\n    }\n}\n'''
advanced = r'''

        @JavascriptInterface
        public void downloadYoutubeAdvanced(String rawUrl, String kind, String quality, String bitrate) {
            Uri uri = checkedUri(rawUrl);
            if (uri == null || !isYouTube(uri)) { complete(false, "Link YouTube tidak valid"); return; }
            final String mode = "audio".equalsIgnoreCase(kind) ? "audio" : "video";
            final String audioBitrate;
            if (bitrate != null && (bitrate.equals("128") || bitrate.equals("192") || bitrate.equals("256") || bitrate.equals("320"))) {
                audioBitrate = bitrate;
            } else {
                audioBitrate = "320";
            }
            new Thread(() -> {
                String processId = "acc" + System.currentTimeMillis();
                File tempDir = new File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "yt-temp");
                if (!tempDir.exists()) tempDir.mkdirs();
                try {
                    ensureEngine();
                    YoutubeDLRequest request = new YoutubeDLRequest(rawUrl.trim());
                    request.addOption("--no-playlist");
                    request.addOption("--no-mtime");
                    request.addOption("--newline");
                    request.addOption("--retries", "3");
                    request.addOption("--fragment-retries", "3");
                    request.addOption("--retry-sleep", "http:1");
                    request.addOption("--extractor-args", "youtube:player_client=default,web_embedded");
                    request.addOption("-o", new File(tempDir, processId + " - %(title).120B - %(uploader).80B.%(ext)s").getAbsolutePath());
                    if (mode.equals("audio")) {
                        request.addOption("-x");
                        request.addOption("--audio-format", "mp3");
                        request.addOption("--audio-quality", audioBitrate + "K");
                        request.addOption("--embed-metadata");
                        request.addOption("--embed-thumbnail");
                        request.addOption("--convert-thumbnails", "jpg");
                    } else {
                        request.addOption("-f", videoFormat(quality));
                        request.addOption("--merge-output-format", "mp4");
                        request.addOption("--embed-metadata");
                    }
                    Function3<Float, Long, String, Unit> callback = new Function3<Float, Long, String, Unit>() {
                        @Override public Unit invoke(Float p, Long eta, String line) {
                            progress(p == null ? 0f : p, line == null ? "Downloading…" : line);
                            return Unit.INSTANCE;
                        }
                    };
                    YoutubeDL.getInstance().execute(request, processId, callback);
                    File output = findOutput(tempDir, processId);
                    if (output == null) throw new Exception("File hasil tidak ditemukan");
                    progress(99f, "Menulis metadata dan menyimpan…");
                    String name = publish(output, mode);
                    output.delete();
                    complete(true, "Tersimpan: " + name);
                } catch (Exception e) {
                    try { YoutubeDL.getInstance().destroyProcessById(processId); } catch (Exception ignored) {}
                    String rawError = e.getMessage() == null ? "engine error" : e.getMessage();
                    String lowerError = rawError.toLowerCase(Locale.US);
                    String friendlyError;
                    if (lowerError.contains("403") || lowerError.contains("sabr") || lowerError.contains("older than 90 days")) {
                        friendlyError = "YouTube menolak stream sementara (403). Engine terbaru sudah dicoba otomatis. Coba lagi beberapa saat.";
                    } else {
                        friendlyError = rawError.length() > 220 ? rawError.substring(0, 220) + "…" : rawError;
                    }
                    complete(false, "Gagal: " + friendlyError);
                }
            }).start();
        }
'''
# Insert before ACCNative/MainActivity closing braces, only once.
pos = s.rfind(insert_before)
if pos < 0:
    raise SystemExit('MainActivity closing target not found')
s = s[:pos] + advanced + s[pos:]
main.write_text(s, encoding='utf-8')

# Inject bitrate UI + route downloads to the advanced native bridge.
v3 = root / 'www/v3.js'
if not v3.exists():
    raise SystemExit('v3.js missing from build')
t = v3.read_text(encoding='utf-8')
needle = '''  function installVersion() {\n    const v = $('.version');\n    if (v) v.textContent = 'v3.1.0';\n  }'''
replacement = r'''  function installMediaPolish() {
    const ytBox = $('#ytBox');
    const qualityRow = $('#qualityRow');
    const dl = $('#ytDownloadBtn');
    if (!ytBox || !qualityRow || !dl || $('#audioBitrateRow')) return;

    const row = document.createElement('div');
    row.id = 'audioBitrateRow';
    row.className = 'qualityrow';
    row.style.display = 'none';
    row.innerHTML = '<select id="audioBitrate" aria-label="Bitrate MP3"><option value="320">MP3 320 kbps</option><option value="256">MP3 256 kbps</option><option value="192">MP3 192 kbps</option><option value="128">MP3 128 kbps</option></select>';
    qualityRow.insertAdjacentElement('afterend', row);

    const syncMode = () => {
      const active = $('.mode.on');
      const audio = active && active.dataset.mode === 'audio';
      row.style.display = audio ? 'flex' : 'none';
    };
    $$('.mode').forEach(btn => btn.addEventListener('click', () => setTimeout(syncMode, 0)));
    syncMode();

    dl.addEventListener('click', (e) => {
      if (!window.ACCNative || typeof window.ACCNative.downloadYoutubeAdvanced !== 'function') return;
      const input = $('#urlInput');
      const raw = (input && input.value || '').trim();
      if (!isYouTubeUrl(raw)) return;
      e.preventDefault();
      e.stopImmediatePropagation();
      const active = $('.mode.on');
      const mode = active && active.dataset.mode === 'audio' ? 'audio' : 'video';
      const quality = $('#quality') ? $('#quality').value : '1080';
      const bitrate = $('#audioBitrate') ? $('#audioBitrate').value : '320';
      const box = $('#progressBox');
      const fill = $('#progressFill');
      const pct = $('#progressPct');
      const msg = $('#progressMsg');
      if (box) box.classList.add('show');
      if (fill) fill.style.width = '2%';
      if (pct) pct.textContent = '2%';
      if (msg) msg.textContent = mode === 'audio' ? `Menyiapkan MP3 ${bitrate} kbps…` : 'Menyiapkan video…';
      dl.disabled = true;
      window.ACCNative.downloadYoutubeAdvanced(raw, mode, quality, bitrate);
    }, true);
  }

  function installVersion() {
    const v = $('.version');
    if (v) v.textContent = 'v3.2.0';
  }'''
if needle not in t:
    raise SystemExit('v3 version function target not found')
t = t.replace(needle, replacement, 1)
t = t.replace('    installSpotifyIntercept();\n', '    installSpotifyIntercept();\n    installMediaPolish();\n', 1)
v3.write_text(t, encoding='utf-8')

print('Applied ACC Media v3.2 metadata, filename, thumbnail and bitrate upgrade')
