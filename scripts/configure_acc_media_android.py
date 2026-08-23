#!/usr/bin/env python3
from pathlib import Path
import re
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: configure_acc_media_android.py <capacitor-build-dir>")

root = Path(sys.argv[1])
android = root / "android"
main_files = list((android / "app/src/main/java").rglob("MainActivity.java"))
if not main_files:
    raise SystemExit("MainActivity.java not found")
main = main_files[0]
old = main.read_text(encoding="utf-8")
m = re.search(r"^package\s+([^;]+);", old, re.M)
if not m:
    raise SystemExit("MainActivity package not found")
package = m.group(1)

java = r'''package __PACKAGE__;

import android.app.DownloadManager;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.widget.Toast;

import com.getcapacitor.BridgeActivity;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.ffmpeg.FFmpeg;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

import kotlin.Unit;
import kotlin.jvm.functions.Function3;
import org.json.JSONObject;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getBridge() != null && getBridge().getWebView() != null) {
            getBridge().getWebView().addJavascriptInterface(new ACCNative(this), "ACCNative");
        }
    }

    public static class ACCNative {
        private final MainActivity activity;
        private volatile boolean engineReady = false;

        ACCNative(MainActivity activity) { this.activity = activity; }

        private Uri checkedUri(String raw) {
            if (raw == null) return null;
            Uri uri = Uri.parse(raw.trim());
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) return null;
            return uri;
        }

        private boolean isYouTube(Uri uri) {
            String host = uri == null ? null : uri.getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.US);
            if (host.startsWith("www.")) host = host.substring(4);
            return host.equals("youtube.com") || host.endsWith(".youtube.com") || host.equals("youtu.be");
        }

        private void toast(String text) {
            activity.runOnUiThread(() -> Toast.makeText(activity, text, Toast.LENGTH_SHORT).show());
        }

        private void js(String code) {
            activity.runOnUiThread(() -> {
                if (activity.getBridge() != null && activity.getBridge().getWebView() != null) {
                    activity.getBridge().getWebView().evaluateJavascript(code, null);
                }
            });
        }

        private void progress(float value, String message) {
            js("window.ACCApp&&window.ACCApp.onNativeProgress(" + value + "," + JSONObject.quote(message == null ? "Downloading…" : message) + ");");
        }

        private void complete(boolean ok, String message) {
            js("window.ACCApp&&window.ACCApp.onNativeComplete(" + ok + "," + JSONObject.quote(message == null ? "" : message) + ");");
        }

        private synchronized void ensureEngine() throws Exception {
            if (engineReady) return;
            progress(1f, "Menyiapkan yt-dlp…");
            YoutubeDL.getInstance().init(activity.getApplicationContext());
            FFmpeg.getInstance().init(activity.getApplicationContext());
            engineReady = true;
        }

        @JavascriptInterface
        public String getClipboard() {
            try {
                ClipboardManager cb = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cb != null && cb.hasPrimaryClip() && cb.getPrimaryClip() != null && cb.getPrimaryClip().getItemCount() > 0) {
                    CharSequence text = cb.getPrimaryClip().getItemAt(0).coerceToText(activity);
                    return text == null ? "" : text.toString();
                }
            } catch (Exception ignored) {}
            return "";
        }

        @JavascriptInterface
        public void openExternal(String rawUrl) {
            try {
                Uri uri = checkedUri(rawUrl);
                if (uri == null) { toast("URL tidak valid"); return; }
                activity.startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (Exception e) {
                toast("Tidak ada aplikasi untuk membuka link");
            }
        }

        @JavascriptInterface
        public void download(String rawUrl, String requestedName) {
            try {
                Uri uri = checkedUri(rawUrl);
                if (uri == null || isYouTube(uri)) { toast("Gunakan tombol Video/Audio untuk link YouTube"); return; }
                String name = requestedName == null ? "media" : requestedName.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_");
                if (name.length() == 0 || name.length() > 180) name = URLUtil.guessFileName(rawUrl, null, null);
                DownloadManager.Request request = new DownloadManager.Request(uri)
                        .setTitle(name)
                        .setDescription("ACC Media Downloader")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setAllowedOverMetered(true)
                        .setAllowedOverRoaming(true)
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
                DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
                if (manager == null) { toast("Android Download Manager tidak tersedia"); return; }
                manager.enqueue(request);
                toast("Download dimulai");
            } catch (Exception e) {
                toast("Gagal memulai download");
            }
        }

        private String videoFormat(String quality) {
            if (quality == null || quality.equals("best")) return "bestvideo+bestaudio/best";
            String q = quality.replaceAll("[^0-9]", "");
            if (q.length() == 0) q = "1080";
            return "bestvideo[height<=" + q + "]+bestaudio/best[height<=" + q + "]/best";
        }

        private File findOutput(File dir, String prefix) {
            File[] files = dir.listFiles();
            if (files == null) return null;
            File best = null;
            for (File f : files) {
                if (!f.isFile() || !f.getName().startsWith(prefix + ".")) continue;
                String n = f.getName().toLowerCase(Locale.US);
                if (n.endsWith(".part") || n.endsWith(".ytdl") || n.endsWith(".json")) continue;
                if (best == null || f.lastModified() > best.lastModified()) best = f;
            }
            return best;
        }

        private String ext(File file) {
            String n = file.getName();
            int i = n.lastIndexOf('.');
            return i >= 0 ? n.substring(i + 1).toLowerCase(Locale.US) : "bin";
        }

        private String mimeFor(String extension, String kind) {
            if ("audio".equals(kind)) return "audio/mpeg";
            if (extension.equals("mp4")) return "video/mp4";
            if (extension.equals("webm")) return "video/webm";
            if (extension.equals("mkv")) return "video/x-matroska";
            return "application/octet-stream";
        }

        private String publish(File source, String kind) throws Exception {
            String extension = ext(source);
            String displayName = ("audio".equals(kind) ? "ACC_Audio_" : "ACC_Video_") + System.currentTimeMillis() + "." + extension;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentResolver resolver = activity.getContentResolver();
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(extension, kind));
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ACC Media Downloader");
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                Uri target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (target == null) throw new Exception("Tidak bisa membuat file di Downloads");
                try (InputStream in = new FileInputStream(source); OutputStream out = resolver.openOutputStream(target)) {
                    if (out == null) throw new Exception("Tidak bisa membuka file tujuan");
                    byte[] buf = new byte[1024 * 128];
                    int len;
                    while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                }
                values.clear();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                resolver.update(target, values, null, null);
            } else {
                File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "ACC Media Downloader");
                if (!folder.exists() && !folder.mkdirs()) throw new Exception("Tidak bisa membuat folder Downloads");
                File target = new File(folder, displayName);
                try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(target)) {
                    byte[] buf = new byte[1024 * 128];
                    int len;
                    while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                }
            }
            return displayName;
        }

        @JavascriptInterface
        public void downloadYoutube(String rawUrl, String kind, String quality) {
            Uri uri = checkedUri(rawUrl);
            if (uri == null || !isYouTube(uri)) { complete(false, "Link YouTube tidak valid"); return; }
            final String mode = "audio".equalsIgnoreCase(kind) ? "audio" : "video";
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
                    request.addOption("-o", new File(tempDir, processId + ".%(ext)s").getAbsolutePath());
                    if (mode.equals("audio")) {
                        request.addOption("-x");
                        request.addOption("--audio-format", "mp3");
                        request.addOption("--audio-quality", "0");
                    } else {
                        request.addOption("-f", videoFormat(quality));
                        request.addOption("--merge-output-format", "mp4");
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
                    progress(99f, "Menyimpan ke Downloads…");
                    String name = publish(output, mode);
                    output.delete();
                    complete(true, "Tersimpan: " + name);
                } catch (Exception e) {
                    try { YoutubeDL.getInstance().destroyProcessById(processId); } catch (Exception ignored) {}
                    complete(false, "Gagal: " + (e.getMessage() == null ? "engine error" : e.getMessage()));
                }
            }).start();
        }
    }
}
'''.replace("__PACKAGE__", package)
main.write_text(java, encoding="utf-8")

# youtubedl-android 0.18.1 requires API 24; Capacitor 7 defaults to 23.
variables = android / "variables.gradle"
if variables.exists():
    v = variables.read_text(encoding="utf-8")
    v, count = re.subn(r"minSdkVersion\s*=\s*\d+", "minSdkVersion = 24", v, count=1)
    if count == 0:
        raise SystemExit("minSdkVersion not found in variables.gradle")
    variables.write_text(v, encoding="utf-8")
else:
    raise SystemExit("variables.gradle not found")

gradle = android / "app/build.gradle"
text = gradle.read_text(encoding="utf-8")
marker = "io.github.junkfood02.youtubedl-android:library:0.18.1"
if marker not in text:
    text += """

dependencies {
    implementation 'io.github.junkfood02.youtubedl-android:library:0.18.1'
    implementation 'io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1'
}
"""
    gradle.write_text(text, encoding="utf-8")

manifest = android / "app/src/main/AndroidManifest.xml"
s = manifest.read_text(encoding="utf-8")
s = re.sub(r'\sandroid:(?:extractNativeLibs|usesCleartextTraffic|icon|roundIcon)="[^"]*"', '', s)
s = s.replace('<application', '<application android:extractNativeLibs="true" android:usesCleartextTraffic="true" android:icon="@drawable/acc_launcher" android:roundIcon="@drawable/acc_launcher"', 1)
if 'WRITE_EXTERNAL_STORAGE' not in s:
    s = s.replace('<application', '<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="28" />\n    <application', 1)
manifest.write_text(s, encoding="utf-8")

res = android / "app/src/main/res/drawable"
res.mkdir(parents=True, exist_ok=True)
(res / "acc_launcher.xml").write_text('''<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#0B0E14" android:pathData="M0,0H108V108H0Z"/>
    <path android:fillColor="#00000000" android:strokeColor="#D7DCE4" android:strokeWidth="6" android:strokeLineCap="round" android:pathData="M19,60 A36,36 0,1 0,89 60"/>
    <path android:fillColor="#FF2848" android:pathData="M25,63 L45,18 C49,9 59,9 63,18 L83,63 L70,63 L54,29 L39,63 Z"/>
    <path android:fillColor="#FFFFFF" android:pathData="M48,55 H60 V73 H70 L54,90 L38,73 H48 Z"/>
</vector>\n''', encoding="utf-8")
print(f"Configured ACC Media native Android at {android} with minSdk 24")
