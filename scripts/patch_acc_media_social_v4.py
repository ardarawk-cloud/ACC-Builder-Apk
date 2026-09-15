#!/usr/bin/env python3
from pathlib import Path
import shutil
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: patch_acc_media_social_v4.py <capacitor-build-dir>')

root = Path(sys.argv[1])
repo_root = Path(__file__).resolve().parents[1]
main_files = list((root / 'android/app/src/main/java').rglob('MainActivity.java'))
if not main_files:
    raise SystemExit('MainActivity.java not found')
main = main_files[0]
s = main.read_text(encoding='utf-8')

if 'public void downloadSocial(String rawUrl, String kind)' not in s:
    insert_before = '    }\n}\n'
    pos = s.rfind(insert_before)
    if pos < 0:
        raise SystemExit('ACCNative closing target not found')

    social_bridge = r'''

        private boolean socialHostMatches(String host, String domain) {
            return host.equals(domain) || host.endsWith("." + domain);
        }

        private boolean isSupportedSocial(Uri uri) {
            String host = uri == null ? null : uri.getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.US);
            if (host.startsWith("www.")) host = host.substring(4);
            return socialHostMatches(host, "tiktok.com")
                    || socialHostMatches(host, "instagram.com")
                    || socialHostMatches(host, "facebook.com")
                    || socialHostMatches(host, "fb.watch")
                    || socialHostMatches(host, "x.com")
                    || socialHostMatches(host, "twitter.com");
        }

        private void socialProgress(float value, String message) {
            js("window.ACCSocial&&window.ACCSocial.onNativeProgress(" + value + "," + JSONObject.quote(message == null ? "Downloading…" : message) + ");");
        }

        private void socialComplete(boolean ok, String message) {
            js("window.ACCSocial&&window.ACCSocial.onNativeComplete(" + ok + "," + JSONObject.quote(message == null ? "" : message) + ");");
        }

        @JavascriptInterface
        public void downloadSocial(String rawUrl, String kind) {
            Uri uri = checkedUri(rawUrl);
            if (uri == null || !isSupportedSocial(uri)) {
                socialComplete(false, "Link social media belum didukung");
                return;
            }
            final String mode = "audio".equalsIgnoreCase(kind) ? "audio" : "video";
            new Thread(() -> {
                String processId = "social" + System.currentTimeMillis();
                File tempDir = new File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "social-temp");
                if (!tempDir.exists()) tempDir.mkdirs();
                try {
                    socialProgress(1f, "Menyiapkan engine media…");
                    ensureEngine();
                    YoutubeDLRequest request = new YoutubeDLRequest(rawUrl.trim());
                    request.addOption("--no-playlist");
                    request.addOption("--no-mtime");
                    request.addOption("--newline");
                    request.addOption("--retries", "3");
                    request.addOption("--fragment-retries", "3");
                    request.addOption("--retry-sleep", "http:1");
                    request.addOption("-o", new File(tempDir, processId + " - %(title).120B - %(uploader).80B.%(ext)s").getAbsolutePath());
                    if (mode.equals("audio")) {
                        request.addOption("-x");
                        request.addOption("--audio-format", "mp3");
                        request.addOption("--audio-quality", "320K");
                        request.addOption("--embed-metadata");
                        request.addOption("--embed-thumbnail");
                        request.addOption("--convert-thumbnails", "jpg");
                    } else {
                        request.addOption("-f", "bestvideo+bestaudio/best");
                        request.addOption("--merge-output-format", "mp4");
                        request.addOption("--embed-metadata");
                    }
                    Function3<Float, Long, String, Unit> callback = new Function3<Float, Long, String, Unit>() {
                        @Override public Unit invoke(Float p, Long eta, String line) {
                            socialProgress(p == null ? 0f : p, line == null ? "Downloading…" : line);
                            return Unit.INSTANCE;
                        }
                    };
                    YoutubeDL.getInstance().execute(request, processId, callback);
                    File output = findOutput(tempDir, processId);
                    if (output == null) throw new Exception("File hasil tidak ditemukan");
                    socialProgress(99f, "Menyimpan ke Downloads…");
                    String name = publish(output, mode);
                    output.delete();
                    socialComplete(true, "Tersimpan: " + name);
                } catch (Exception e) {
                    try { YoutubeDL.getInstance().destroyProcessById(processId); } catch (Exception ignored) {}
                    String rawError = e.getMessage() == null ? "engine error" : e.getMessage();
                    String lower = rawError.toLowerCase(Locale.US);
                    String friendly;
                    if (lower.contains("login") || lower.contains("cookie") || lower.contains("private") || lower.contains("sign in") || lower.contains("age-restricted")) {
                        friendly = "Konten memerlukan login/izin atau tidak publik. ACC tidak membypass pembatasan akun, private content, paywall, atau DRM.";
                    } else if (lower.contains("unsupported url") || lower.contains("not a valid url")) {
                        friendly = "Link ini belum didukung oleh engine media terbaru.";
                    } else if (lower.contains("403") || lower.contains("forbidden")) {
                        friendly = "Platform menolak akses media sementara (403). Coba lagi nanti atau gunakan link publik asli dari post/reel/video.";
                    } else {
                        friendly = rawError.length() > 220 ? rawError.substring(0, 220) + "…" : rawError;
                    }
                    socialComplete(false, "Gagal: " + friendly);
                }
            }).start();
        }
'''
    s = s[:pos] + social_bridge + s[pos:]
    main.write_text(s, encoding='utf-8')

social_src = repo_root / 'apps/acc-media-downloader/social-v4.js'
if not social_src.exists():
    raise SystemExit('social-v4.js source missing')
www = root / 'www'
www.mkdir(parents=True, exist_ok=True)
shutil.copyfile(social_src, www / 'social-v4.js')

index = www / 'index.html'
if not index.exists():
    raise SystemExit('built index.html missing')
html = index.read_text(encoding='utf-8')
marker = '<script src="social-v4.js"></script>'
if marker not in html:
    pos = html.lower().rfind('</body>')
    if pos < 0:
        raise SystemExit('index.html has no closing body')
    html = html[:pos] + marker + '\n' + html[pos:]
    index.write_text(html, encoding='utf-8')

print('Applied ACC Media v4 social downloader bridge + UI extension')
