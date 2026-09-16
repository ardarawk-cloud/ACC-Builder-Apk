#!/usr/bin/env python3
from pathlib import Path
import shutil
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: patch_acc_media_youtube_playlist_v6.py <capacitor-build-dir>')

root = Path(sys.argv[1])
repo_root = Path(__file__).resolve().parents[1]
main_files = list((root / 'android/app/src/main/java').rglob('MainActivity.java'))
if not main_files:
    raise SystemExit('MainActivity.java not found')
main = main_files[0]
s = main.read_text(encoding='utf-8')

if 'public void importYoutubePlaylist(String rawUrl)' not in s:
    insert_before = '    }\n}\n'
    pos = s.rfind(insert_before)
    if pos < 0:
        raise SystemExit('ACCNative closing target not found')

    bridge = r'''

        private boolean isYoutubePlaylist(Uri uri) {
            if (uri == null || !isYouTube(uri)) return false;
            String list = uri.getQueryParameter("list");
            return list != null && !list.trim().isEmpty();
        }

        private void ytPlaylistImportResult(boolean ok, String payload) {
            js("window.ACCYTPlaylist&&window.ACCYTPlaylist.onPlaylist(" + ok + "," + JSONObject.quote(payload == null ? "" : payload) + ");");
        }

        private void ytPlaylistTrackProgress(int index, float value, String message) {
            js("window.ACCYTPlaylist&&window.ACCYTPlaylist.onTrackProgress(" + index + "," + value + "," + JSONObject.quote(message == null ? "Downloading…" : message) + ");");
        }

        private void ytPlaylistTrackComplete(int index, boolean ok, String message) {
            js("window.ACCYTPlaylist&&window.ACCYTPlaylist.onTrackComplete(" + index + "," + ok + "," + JSONObject.quote(message == null ? "" : message) + ");");
        }

        @JavascriptInterface
        public void importYoutubePlaylist(String rawUrl) {
            Uri uri = checkedUri(rawUrl);
            if (uri == null || !isYoutubePlaylist(uri)) {
                ytPlaylistImportResult(false, "Link bukan playlist YouTube/YouTube Music");
                return;
            }
            new Thread(() -> {
                String processId = "ytplimport_" + System.currentTimeMillis();
                try {
                    ensureEngine();
                    YoutubeDLRequest request = new YoutubeDLRequest(rawUrl.trim());
                    request.addOption("--flat-playlist");
                    request.addOption("--dump-single-json");
                    request.addOption("--skip-download");
                    request.addOption("--ignore-errors");
                    request.addOption("--no-warnings");
                    request.addOption("--retries", "3");
                    request.addOption("--extractor-args", "youtube:player_client=default,web_embedded");
                    com.yausername.youtubedl_android.YoutubeDLResponse response = YoutubeDL.getInstance().execute(request, processId, null);
                    String raw = response == null ? "" : response.getOut();
                    if (raw == null) raw = "";
                    raw = raw.trim();
                    int first = raw.indexOf('{');
                    int last = raw.lastIndexOf('}');
                    if (first < 0 || last <= first) throw new Exception("Metadata playlist YouTube tidak ditemukan");
                    JSONObject data = new JSONObject(raw.substring(first, last + 1));
                    org.json.JSONArray entries = data.optJSONArray("entries");
                    if (entries == null || entries.length() == 0) throw new Exception("Playlist kosong atau tidak dapat dibaca");

                    org.json.JSONArray tracks = new org.json.JSONArray();
                    for (int i = 0; i < entries.length(); i++) {
                        JSONObject entry = entries.optJSONObject(i);
                        if (entry == null) continue;
                        String id = entry.optString("id", "").trim();
                        String title = entry.optString("title", "").trim();
                        if (title.isEmpty()) title = entry.optString("fulltitle", "").trim();
                        String itemUrl = entry.optString("webpage_url", "").trim();
                        if (itemUrl.isEmpty()) itemUrl = entry.optString("url", "").trim();
                        if (!itemUrl.startsWith("http")) {
                            if (!id.isEmpty()) itemUrl = "https://www.youtube.com/watch?v=" + id;
                        }
                        if (title.isEmpty() || itemUrl.isEmpty()) continue;
                        String artist = entry.optString("artist", "").trim();
                        if (artist.isEmpty()) artist = entry.optString("uploader", "").trim();
                        if (artist.isEmpty()) artist = entry.optString("channel", "").trim();
                        JSONObject track = new JSONObject();
                        track.put("index", tracks.length());
                        track.put("title", title);
                        track.put("artist", artist);
                        track.put("url", itemUrl);
                        track.put("id", id);
                        tracks.put(track);
                    }
                    if (tracks.length() == 0) throw new Exception("Tidak ada track publik yang bisa diproses");
                    JSONObject payload = new JSONObject();
                    payload.put("title", data.optString("title", "YouTube Playlist"));
                    payload.put("count", tracks.length());
                    payload.put("source", uri.getHost() != null && uri.getHost().toLowerCase(Locale.US).contains("music.youtube") ? "YouTube Music" : "YouTube");
                    payload.put("tracks", tracks);
                    ytPlaylistImportResult(true, payload.toString());
                } catch (Exception e) {
                    try { YoutubeDL.getInstance().destroyProcessById(processId); } catch (Exception ignored) {}
                    String msg = e.getMessage() == null ? "Gagal membaca playlist YouTube" : e.getMessage();
                    String lower = msg.toLowerCase(Locale.US);
                    if (lower.contains("private") || lower.contains("sign in") || lower.contains("login") || lower.contains("members-only")) {
                        msg = "Playlist atau sebagian track memerlukan login/izin. ACC tidak membypass private content, login, membership, paywall, atau DRM.";
                    }
                    ytPlaylistImportResult(false, msg.length() > 240 ? msg.substring(0, 240) + "…" : msg);
                }
            }).start();
        }

        @JavascriptInterface
        public void downloadYoutubePlaylistTrack(String rawUrl, String kind, int index) {
            Uri uri = checkedUri(rawUrl);
            if (uri == null || !isYouTube(uri)) {
                ytPlaylistTrackComplete(index, false, "URL track YouTube tidak valid");
                return;
            }
            final String mode = "video".equalsIgnoreCase(kind) ? "video" : "audio";
            new Thread(() -> {
                String processId = "ytpl_" + index + "_" + System.currentTimeMillis();
                File tempDir = new File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "youtube-playlist-temp");
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
                    String order = String.format(Locale.US, "%03d", index + 1);
                    request.addOption("-o", new File(tempDir, processId + " - " + order + " - %(title).120B - %(uploader).80B.%(ext)s").getAbsolutePath());
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
                            ytPlaylistTrackProgress(index, p == null ? 0f : p, line == null ? "Downloading…" : line);
                            return Unit.INSTANCE;
                        }
                    };
                    YoutubeDL.getInstance().execute(request, processId, callback);
                    File output = findOutput(tempDir, processId);
                    if (output == null) throw new Exception("File hasil tidak ditemukan");
                    ytPlaylistTrackProgress(index, 99f, "Menyimpan ke Downloads…");
                    String name = publish(output, mode);
                    output.delete();
                    ytPlaylistTrackComplete(index, true, "Tersimpan: " + name);
                } catch (Exception e) {
                    try { YoutubeDL.getInstance().destroyProcessById(processId); } catch (Exception ignored) {}
                    String msg = e.getMessage() == null ? "engine error" : e.getMessage();
                    String lower = msg.toLowerCase(Locale.US);
                    if (lower.contains("private") || lower.contains("sign in") || lower.contains("login") || lower.contains("members-only") || lower.contains("age-restricted")) {
                        msg = "Track memerlukan login/izin atau tidak publik; dilewati.";
                    } else if (lower.contains("403") || lower.contains("sabr") || lower.contains("forbidden")) {
                        msg = "YouTube menolak stream sementara (403); track dilewati.";
                    }
                    ytPlaylistTrackComplete(index, false, msg.length() > 220 ? msg.substring(0, 220) + "…" : msg);
                }
            }).start();
        }
'''
    s = s[:pos] + bridge + s[pos:]
    main.write_text(s, encoding='utf-8')

src = repo_root / 'apps/acc-media-downloader/youtube-playlist-v6.js'
if not src.exists():
    raise SystemExit('youtube-playlist-v6.js source missing')
www = root / 'www'
www.mkdir(parents=True, exist_ok=True)
shutil.copyfile(src, www / 'youtube-playlist-v6.js')
index = www / 'index.html'
if not index.exists():
    raise SystemExit('built index.html missing')
html = index.read_text(encoding='utf-8')
marker = '<script src="youtube-playlist-v6.js"></script>'
if marker not in html:
    pos = html.lower().rfind('</body>')
    if pos < 0:
        raise SystemExit('index.html has no closing body')
    html = html[:pos] + marker + '\n' + html[pos:]
    index.write_text(html, encoding='utf-8')

print('Applied ACC Media v6 YouTube/YouTube Music playlist importer + sequential batch downloader')
