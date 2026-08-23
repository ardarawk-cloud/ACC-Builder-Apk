#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: patch_acc_media_youtube_403.py <capacitor-build-dir>')

root = Path(sys.argv[1])
main_files = list((root / 'android/app/src/main/java').rglob('MainActivity.java'))
if not main_files:
    raise SystemExit('MainActivity.java not found')
main = main_files[0]
s = main.read_text(encoding='utf-8')

old = '''            YoutubeDL.getInstance().init(activity.getApplicationContext());
            FFmpeg.getInstance().init(activity.getApplicationContext());
            engineReady = true;'''
new = '''            YoutubeDL.getInstance().init(activity.getApplicationContext());
            FFmpeg.getInstance().init(activity.getApplicationContext());
            progress(2f, "Memperbarui engine YouTube…");
            try {
                YoutubeDL.getInstance().updateYoutubeDL(
                    activity.getApplicationContext(),
                    YoutubeDL.UpdateChannel._NIGHTLY
                );
            } catch (Exception ignored) {
                // Bundled engine remains available if the updater cannot reach GitHub.
            }
            engineReady = true;'''
if old not in s:
    raise SystemExit('ensureEngine patch target not found')
s = s.replace(old, new, 1)

old2 = '''                    request.addOption("--newline");
                    request.addOption("-o", new File(tempDir, processId + ".%(ext)s").getAbsolutePath());'''
new2 = '''                    request.addOption("--newline");
                    request.addOption("--retries", "3");
                    request.addOption("--fragment-retries", "3");
                    request.addOption("--retry-sleep", "http:1");
                    request.addOption("--extractor-args", "youtube:player_client=default,web_embedded");
                    request.addOption("-o", new File(tempDir, processId + ".%(ext)s").getAbsolutePath());'''
if old2 not in s:
    raise SystemExit('request patch target not found')
s = s.replace(old2, new2, 1)

old3 = '''                    complete(false, "Gagal: " + (e.getMessage() == null ? "engine error" : e.getMessage()));'''
new3 = '''                    String rawError = e.getMessage() == null ? "engine error" : e.getMessage();
                    String lowerError = rawError.toLowerCase(Locale.US);
                    String friendlyError;
                    if (lowerError.contains("403") || lowerError.contains("sabr") || lowerError.contains("older than 90 days")) {
                        friendlyError = "YouTube menolak stream sementara (403). Engine terbaru sudah dicoba otomatis. Coba tekan Download lagi; bila masih gagal, link/client YouTube sedang membutuhkan kompatibilitas baru.";
                    } else {
                        friendlyError = rawError.length() > 220 ? rawError.substring(0, 220) + "…" : rawError;
                    }
                    complete(false, "Gagal: " + friendlyError);'''
if old3 not in s:
    raise SystemExit('error sanitizer patch target not found')
s = s.replace(old3, new3, 1)
main.write_text(s, encoding='utf-8')

v3 = root / 'www/v3.js'
if v3.exists():
    t = v3.read_text(encoding='utf-8')
    t = t.replace("v.textContent = 'v3.0.0'", "v.textContent = 'v3.1.0'")
    v3.write_text(t, encoding='utf-8')

print('Applied ACC YouTube 403 compatibility hotfix + v3.1.0 label')
