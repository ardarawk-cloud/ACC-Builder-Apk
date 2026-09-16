#!/usr/bin/env python3
from pathlib import Path
import shutil
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: patch_acc_media_playlist_v5.py <capacitor-build-dir>')

root = Path(sys.argv[1])
repo_root = Path(__file__).resolve().parents[1]
main_files = list((root / 'android/app/src/main/java').rglob('MainActivity.java'))
if not main_files:
    raise SystemExit('MainActivity.java not found')
main = main_files[0]
s = main.read_text(encoding='utf-8')

if 'public void importSpotifyPlaylist(String rawUrl)' not in s:
    insert_before = '    }\n}\n'
    pos = s.rfind(insert_before)
    if pos < 0:
        raise SystemExit('ACCNative closing target not found')

    bridge = r'''

        private boolean spotifyHost(String host) {
            if (host == null) return false;
            host = host.toLowerCase(Locale.US);
            if (host.startsWith("www.")) host = host.substring(4);
            return host.equals("open.spotify.com") || host.equals("spotify.com") || host.endsWith(".spotify.com") || host.equals("spotify.link");
        }

        private String readUrlText(String rawUrl) throws Exception {
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(rawUrl).openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) ACCMedia/5.0");
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 400) {
                connection.disconnect();
                throw new Exception("Spotify HTTP " + code);
            }
            StringBuilder out = new StringBuilder();
            try (InputStream in = connection.getInputStream()) {
                byte[] buffer = new byte[32768];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    out.append(new String(buffer, 0, len, java.nio.charset.StandardCharsets.UTF_8));
                }
            } finally {
                connection.disconnect();
            }
            return out.toString();
        }

        private String resolveSpotifyPlaylistId(String rawUrl) throws Exception {
            Uri uri = checkedUri(rawUrl);
            if (uri == null || !spotifyHost(uri.getHost())) throw new Exception("Link Spotify tidak valid");
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.US);
            if (host.equals("spotify.link")) {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(rawUrl).openConnection();
                c.setInstanceFollowRedirects(true);
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) ACCMedia/5.0");
                c.getResponseCode();
                uri = Uri.parse(c.getURL().toString());
                c.disconnect();
            }
            java.util.List<String> segments = uri.getPathSegments();
            for (int i = 0; i + 1 < segments.size(); i++) {
                if ("playlist".equalsIgnoreCase(segments.get(i))) {
                    String id = segments.get(i + 1).replaceAll("[^A-Za-z0-9]", "");
                    if (id.length() >= 10) return id;
                }
            }
            throw new Exception("Link bukan playlist Spotify");
        }

        private JSONObject findTrackEntity(Object node) {
            if (node instanceof JSONObject) {
                JSONObject object = (JSONObject) node;
                if (object.optJSONArray("trackList") != null) return object;
                java.util.Iterator<String> keys = object.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    Object child = object.opt(key);
                    JSONObject found = findTrackEntity(child);
                    if (found != null) return found;
                }
            } else if (node instanceof org.json.JSONArray) {
                org.json.JSONArray array = (org.json.JSONArray) node;
                for (int i = 0; i < array.length(); i++) {
                    JSONObject found = findTrackEntity(array.opt(i));
                    if (found != null) return found;
                }
            }
            return null;
        }

        private String artistFromTrack(JSONObject item) {
            String artist = item.optString("subtitle", "").trim();
            if (!artist.isEmpty()) return artist;
            JSONObject track = item.optJSONObject("track");
            if (track != null) {
                artist = track.optString("subtitle", "").trim();
                if (!artist.isEmpty()) return artist;
                org.json.JSONArray artists = track.optJSONArray("artists");
                if (artists != null) {
                    StringBuilder names = new StringBuilder();
                    for (int i = 0; i < artists.length(); i++) {
                        JSONObject a = artists.optJSONObject(i);
                        if (a == null) continue;
                        String name = a.optString("name", "").trim();
                        if (name.isEmpty()) continue;
                        if (names.length() > 0) names.append(", ");
                        names.append(name);
                    }
                    if (names.length() > 0) return names.toString();
                }
            }
            return "";
        }

        private void playlistImportResult(boolean ok, String payload) {
            js("window.ACCPlaylist&&window.ACCPlaylist.onSpotifyPlaylist(" + ok + "," + JSONObject.quote(payload == null ? "" : payload) + ");");
        }

        private void playlistTrackProgress(int index, float value, String message) {
            js("window.ACCPlaylist&&window.ACCPlaylist.onTrackProgress(" + index + "," + value + "," + JSONObject.quote(message == null ? "Downloading…" : message) + ");");
        }

        private void playlistTrackComplete(int index, boolean ok, String message) {
            js("window.ACCPlaylist&&window.ACCPlaylist.onTrackComplete(" + index + "," + ok + "," + JSONObject.quote(message == null ? "" : message) + ");");
        }

        @JavascriptInterface
        public void importSpotifyPlaylist(String rawUrl) {
            new Thread(() -> {
                try {
                    String playlistId = resolveSpotifyPlaylistId(rawUrl);
                    String html = readUrlText("https://open.spotify.com/embed/playlist/" + playlistId);
                    String marker = "<script id=\"__NEXT_DATA__\" type=\"application/json\">";
                    int start = html.indexOf(marker);
                    if (start < 0) throw new Exception("Metadata playlist Spotify tidak ditemukan");
                    start += marker.length();
                    int end = html.indexOf("</script>", start);
                    if (end < 0) throw new Exception("Metadata playlist Spotify tidak lengkap");
                    JSONObject rootJson = new JSONObject(html.substring(start, end));
                    JSONObject entity = findTrackEntity(rootJson);
                    if (entity == null) throw new Exception("Daftar lagu Spotify tidak ditemukan");
                    org.json.JSONArray rawTracks = entity.optJSONArray("trackList");
                    if (rawTracks == null || rawTracks.length() == 0) throw new Exception("Playlist Spotify kosong atau tidak dapat dibaca");

                    org.json.JSONArray tracks = new org.json.JSONArray();
                    for (int i = 0; i < rawTracks.length(); i++) {
                        JSONObject item = rawTracks.optJSONObject(i);
                        if (item == null) continue;
                        String title = item.optString("title", "").trim();
                        JSONObject nested = item.optJSONObject("track");
                        if (title.isEmpty() && nested != null) title = nested.optString("name", nested.optString("title", "")).trim();
                        if (title.isEmpty()) title = item.optString("name", "").trim();
                        if (title.isEmpty()) continue;
                        String artist = artistFromTrack(item);
                        JSONObject track = new JSONObject();
                        track.put("index", tracks.length());
                        track.put("title", title);
                        track.put("artist", artist);
                        track.put("query", (title + " " + artist + " official audio").trim());
                        tracks.put(track);
                    }
                    if (tracks.length() == 0) throw new Exception("Tidak ada track yang bisa diimpor dari playlist");
                    JSONObject payload = new JSONObject();
                    payload.put("playlistId", playlistId);
                    payload.put("title", entity.optString("name", entity.optString("title", "Spotify Playlist")));
                    payload.put("count", tracks.length());
                    payload.put("tracks", tracks);
                    playlistImportResult(true, payload.toString());
                } catch (Exception e) {
                    String msg = e.getMessage() == null ? "Gagal membaca playlist Spotify" : e.getMessage();
                    playlistImportResult(false, msg.length() > 240 ? msg.substring(0, 240) + "…" : msg);
                }
            }).start();
        }

        @JavascriptInterface
        public void downloadPlaylistTrack(String query, String kind, int index) {
            if (query == null || query.trim().isEmpty()) {
                playlistTrackComplete(index, false, "Query lagu kosong");
                return;
            }
            final String mode = "video".equalsIgnoreCase(kind) ? "video" : "audio";
            new Thread(() -> {
                String processId = "pl" + index + "_" + System.currentTimeMillis();
                File tempDir = new File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "playlist-temp");
                if (!tempDir.exists()) tempDir.mkdirs();
                try {
                    ensureEngine();
                    YoutubeDLRequest request = new YoutubeDLRequest("ytsearch1:" + query.trim());
                    request.addOption("--no-playlist");
                    request.addOption("--no-mtime");
                    request.addOption("--newline");
                    request.addOption("--retries", "3");
                    request.addOption("--fragment-retries", "3");
                    request.addOption("--retry-sleep", "http:1");
                    String order = String.format(Locale.US, "%02d", index + 1);
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
                            playlistTrackProgress(index, p == null ? 0f : p, line == null ? "Downloading…" : line);
                            return Unit.INSTANCE;
                        }
                    };
                    YoutubeDL.getInstance().execute(request, processId, callback);
                    File output = findOutput(tempDir, processId);
                    if (output == null) throw new Exception("Hasil pencarian/download tidak ditemukan");
                    playlistTrackProgress(index, 99f, "Menyimpan ke Downloads…");
                    String name = publish(output, mode);
                    output.delete();
                    playlistTrackComplete(index, true, "Tersimpan: " + name);
                } catch (Exception e) {
                    try { YoutubeDL.getInstance().destroyProcessById(processId); } catch (Exception ignored) {}
                    String msg = e.getMessage() == null ? "engine error" : e.getMessage();
                    playlistTrackComplete(index, false, msg.length() > 220 ? msg.substring(0, 220) + "…" : msg);
                }
            }).start();
        }
'''
    s = s[:pos] + bridge + s[pos:]
    main.write_text(s, encoding='utf-8')

src = repo_root / 'apps/acc-media-downloader/playlist-v5.js'
if not src.exists():
    raise SystemExit('playlist-v5.js source missing')
www = root / 'www'
www.mkdir(parents=True, exist_ok=True)
shutil.copyfile(src, www / 'playlist-v5.js')
index = www / 'index.html'
html = index.read_text(encoding='utf-8')
marker = '<script src="playlist-v5.js"></script>'
if marker not in html:
    pos = html.lower().rfind('</body>')
    if pos < 0:
        raise SystemExit('index.html has no closing body')
    html = html[:pos] + marker + '\n' + html[pos:]
    index.write_text(html, encoding='utf-8')

print('Applied ACC Media v5 Spotify playlist importer + sequential batch downloader')
