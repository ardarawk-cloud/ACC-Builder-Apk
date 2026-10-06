'use strict';

const { app, BrowserWindow, ipcMain, shell } = require('electron');
const path = require('path');
const fs = require('fs');
const { spawn } = require('child_process');
const { pipeline } = require('stream/promises');
const { Readable } = require('stream');

let mainWindow = null;

function desktopRoot() {
  return __dirname;
}

function binDir() {
  return app.isPackaged ? path.join(process.resourcesPath, 'bin') : path.join(desktopRoot(), 'bin');
}

function binPath(name) {
  return path.join(binDir(), name);
}

function downloadDir() {
  const dir = path.join(app.getPath('downloads'), 'ACC Media Downloader');
  fs.mkdirSync(dir, { recursive: true });
  return dir;
}

function emit(type, ...args) {
  if (!mainWindow || mainWindow.isDestroyed()) return;
  mainWindow.webContents.send('acc-event', { type, args });
}

function isHttpUrl(raw) {
  try {
    const u = new URL(String(raw || '').trim());
    return u.protocol === 'http:' || u.protocol === 'https:';
  } catch (_) {
    return false;
  }
}

function safeName(raw, fallback) {
  const name = String(raw || fallback || 'download')
    .replace(/[\\/:*?"<>|\r\n]+/g, '_')
    .replace(/[. ]+$/g, '')
    .trim();
  return (name || fallback || 'download').slice(0, 180);
}

function friendlyError(text) {
  const raw = String(text || 'engine error').trim();
  const low = raw.toLowerCase();
  if (low.includes('private') || low.includes('sign in') || low.includes('login') ||
      low.includes('members-only') || low.includes('age-restricted') ||
      low.includes('premium') || low.includes('drm')) {
    return 'Konten memerlukan login/izin atau tidak publik. ACC tidak membypass private content, membership, paywall, atau DRM.';
  }
  if (low.includes('unsupported url') || low.includes('not a valid url')) {
    return 'Link ini belum didukung oleh engine media terbaru.';
  }
  if (low.includes('403') || low.includes('forbidden')) {
    return 'Platform menolak akses media sementara (403). Coba lagi atau gunakan link publik asli.';
  }
  const lines = raw.split(/\r?\n/).filter(Boolean);
  return (lines[lines.length - 1] || raw).slice(0, 260);
}

function qualityFormat(quality) {
  const q = String(quality || 'best');
  if (q === 'best') return 'bestvideo+bestaudio/best';
  const h = Number(q);
  if ([360, 480, 720, 1080, 1440, 2160].includes(h)) {
    return 'bestvideo[height<=' + h + ']+bestaudio/best[height<=' + h + ']/best';
  }
  return 'bestvideo+bestaudio/best';
}

function commonArgs() {
  const args = [
    '--newline',
    '--windows-filenames',
    '--no-mtime',
    '--retries', '3',
    '--fragment-retries', '3',
    '--retry-sleep', 'http:1',
    '--ffmpeg-location', binDir()
  ];
  const deno = binPath('deno.exe');
  if (fs.existsSync(deno)) {
    args.push('--js-runtimes', 'deno:' + deno);
    args.push('--remote-components', 'ejs:github');
  }
  return args;
}

function mediaArgs(mode, quality, bitrate) {
  if (mode === 'audio') {
    const br = ['128', '192', '256', '320'].includes(String(bitrate)) ? String(bitrate) : '320';
    return [
      '-x',
      '--audio-format', 'mp3',
      '--audio-quality', br + 'K',
      '--embed-metadata',
      '--embed-thumbnail',
      '--convert-thumbnails', 'jpg'
    ];
  }
  return [
    '-f', qualityFormat(quality),
    '--merge-output-format', 'mp4',
    '--embed-metadata'
  ];
}

function parseProgress(line) {
  const m = String(line || '').match(/(?:\[download\]\s*)?(\d{1,3}(?:\.\d+)?)%/);
  if (!m) return null;
  const n = Math.max(0, Math.min(95, Number(m[1]) || 0));
  return n;
}

function runYtDlp(args, handlers = {}) {
  return new Promise((resolve, reject) => {
    const exe = binPath('yt-dlp.exe');
    if (!fs.existsSync(exe)) {
      reject(new Error('yt-dlp.exe tidak ditemukan di paket ACC Media PC.'));
      return;
    }
    const child = spawn(exe, args, {
      windowsHide: true,
      cwd: downloadDir()
    });
    let stdout = '';
    let stderr = '';
    let tail = '';

    const handle = (chunk, isErr) => {
      const text = chunk.toString('utf8');
      if (isErr) stderr += text; else stdout += text;
      tail += text;
      if (tail.length > 12000) tail = tail.slice(-12000);
      text.split(/\r?\n/).forEach((line) => {
        const p = parseProgress(line);
        if (p !== null && handlers.onProgress) handlers.onProgress(p, line.trim());
        if (handlers.onLine && line.trim()) handlers.onLine(line.trim());
      });
    };

    child.stdout.on('data', (d) => handle(d, false));
    child.stderr.on('data', (d) => handle(d, true));
    child.on('error', reject);
    child.on('close', (code) => {
      if (code === 0) {
        resolve({ stdout, stderr, tail });
      } else {
        reject(new Error(tail || stderr || ('yt-dlp exit code ' + code)));
      }
    });
  });
}

async function downloadWithYtDlp(source, mode, quality, bitrate, outputPrefix, onProgress) {
  const dir = downloadDir();
  const template = path.join(dir, (outputPrefix || '') + '%(title).120B - %(uploader).80B.%(ext)s');
  const args = [
    ...commonArgs(),
    '--no-playlist',
    '-o', template,
    ...mediaArgs(mode, quality, bitrate),
    source
  ];
  await runYtDlp(args, {
    onProgress: (p, line) => onProgress && onProgress(p, line)
  });
  return dir;
}

async function fetchText(url) {
  const response = await fetch(url, {
    redirect: 'follow',
    headers: {
      'user-agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) ACCMediaPC/1.0',
      'accept': 'text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8'
    }
  });
  if (!response.ok) throw new Error('HTTP ' + response.status);
  return await response.text();
}

function spotifyPlaylistId(rawUrl) {
  const u = new URL(String(rawUrl || '').trim());
  const host = u.hostname.toLowerCase().replace(/^www\./, '');
  if (!(host === 'open.spotify.com' || host === 'spotify.com' || host.endsWith('.spotify.com'))) {
    throw new Error('Link Spotify tidak valid');
  }
  const parts = u.pathname.split('/').filter(Boolean);
  const i = parts.findIndex((x) => x.toLowerCase() === 'playlist');
  if (i < 0 || !parts[i + 1]) throw new Error('Link bukan playlist Spotify');
  return parts[i + 1].replace(/[^A-Za-z0-9]/g, '');
}

function findTrackEntity(node) {
  if (!node || typeof node !== 'object') return null;
  if (!Array.isArray(node) && Array.isArray(node.trackList)) return node;
  if (Array.isArray(node)) {
    for (const child of node) {
      const found = findTrackEntity(child);
      if (found) return found;
    }
    return null;
  }
  for (const child of Object.values(node)) {
    const found = findTrackEntity(child);
    if (found) return found;
  }
  return null;
}

function artistFromTrack(item) {
  if (!item || typeof item !== 'object') return '';
  if (String(item.subtitle || '').trim()) return String(item.subtitle).trim();
  const track = item.track && typeof item.track === 'object' ? item.track : null;
  if (!track) return '';
  if (String(track.subtitle || '').trim()) return String(track.subtitle).trim();
  if (Array.isArray(track.artists)) {
    return track.artists.map((a) => a && a.name).filter(Boolean).join(', ');
  }
  return '';
}

async function importSpotify(rawUrl) {
  const id = spotifyPlaylistId(rawUrl);
  const html = await fetchText('https://open.spotify.com/embed/playlist/' + id);
  const marker = '<script id="__NEXT_DATA__" type="application/json">';
  const start0 = html.indexOf(marker);
  if (start0 < 0) throw new Error('Metadata playlist Spotify tidak ditemukan');
  const start = start0 + marker.length;
  const end = html.indexOf('</script>', start);
  if (end < 0) throw new Error('Metadata playlist Spotify tidak lengkap');
  const root = JSON.parse(html.slice(start, end));
  const entity = findTrackEntity(root);
  if (!entity || !Array.isArray(entity.trackList) || !entity.trackList.length) {
    throw new Error('Daftar lagu Spotify tidak ditemukan');
  }
  const tracks = [];
  for (const item of entity.trackList) {
    if (!item || typeof item !== 'object') continue;
    const nested = item.track && typeof item.track === 'object' ? item.track : null;
    const title = String(item.title || (nested && (nested.name || nested.title)) || item.name || '').trim();
    if (!title) continue;
    const artist = artistFromTrack(item);
    tracks.push({
      index: tracks.length,
      title,
      artist,
      query: (title + ' ' + artist + ' official audio').trim()
    });
  }
  if (!tracks.length) throw new Error('Tidak ada track yang bisa diimpor dari playlist');
  return {
    playlistId: id,
    title: String(entity.name || entity.title || 'Spotify Playlist'),
    count: tracks.length,
    tracks
  };
}

async function importYoutubePlaylist(rawUrl) {
  if (!isHttpUrl(rawUrl)) throw new Error('Link playlist tidak valid');
  const args = [
    ...commonArgs(),
    '--flat-playlist',
    '--dump-single-json',
    '--skip-download',
    '--ignore-errors',
    '--no-warnings',
    rawUrl
  ];
  const result = await runYtDlp(args);
  const raw = String(result.stdout || '').trim();
  const first = raw.indexOf('{');
  const last = raw.lastIndexOf('}');
  if (first < 0 || last <= first) throw new Error('Metadata playlist YouTube tidak ditemukan');
  const data = JSON.parse(raw.slice(first, last + 1));
  const entries = Array.isArray(data.entries) ? data.entries : [];
  const tracks = [];
  for (const entry of entries) {
    if (!entry || typeof entry !== 'object') continue;
    const id = String(entry.id || '').trim();
    const title = String(entry.title || entry.fulltitle || '').trim();
    let itemUrl = String(entry.webpage_url || entry.url || '').trim();
    if (!/^https?:/i.test(itemUrl) && id) itemUrl = 'https://www.youtube.com/watch?v=' + id;
    if (!title || !itemUrl) continue;
    const artist = String(entry.artist || entry.uploader || entry.channel || '').trim();
    tracks.push({ index: tracks.length, title, artist, url: itemUrl, id });
  }
  if (!tracks.length) throw new Error('Tidak ada track publik yang bisa diproses');
  return {
    title: String(data.title || 'YouTube Playlist'),
    count: tracks.length,
    source: String(rawUrl).includes('music.youtube.com') ? 'YouTube Music' : 'YouTube',
    tracks
  };
}

async function directDownload(rawUrl, rawName) {
  if (!isHttpUrl(rawUrl)) throw new Error('URL tidak valid');
  const response = await fetch(rawUrl, { redirect: 'follow' });
  if (!response.ok || !response.body) throw new Error('HTTP ' + response.status);
  const u = new URL(response.url || rawUrl);
  let name = rawName;
  if (!name || !name.includes('.')) {
    name = decodeURIComponent(u.pathname.split('/').filter(Boolean).pop() || 'download.bin');
  }
  name = safeName(name, 'download.bin');
  const target = path.join(downloadDir(), name);
  await pipeline(Readable.fromWeb(response.body), fs.createWriteStream(target));
  return target;
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1180,
    height: 800,
    minWidth: 900,
    minHeight: 620,
    backgroundColor: '#080a0f',
    title: 'ACC Media Downloader PC',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false
    }
  });
  mainWindow.removeMenu();
  mainWindow.loadFile(path.join(__dirname, 'web', 'index.html'));
}

ipcMain.on('acc-open-external', async (_event, raw) => {
  if (isHttpUrl(raw)) await shell.openExternal(raw);
});

ipcMain.on('acc-direct-download', async (_event, payload) => {
  try {
    await directDownload(payload && payload.url, payload && payload.name);
  } catch (e) {
    emit('toast', 'Download gagal: ' + friendlyError(e.message));
  }
});

ipcMain.on('acc-youtube-download', async (_event, payload) => {
  try {
    const mode = payload && payload.kind === 'audio' ? 'audio' : 'video';
    await downloadWithYtDlp(
      payload.url,
      mode,
      payload.quality || 'best',
      payload.bitrate || '320',
      '',
      (p, line) => emit('app-progress', p, line)
    );
    emit('app-complete', true, 'Selesai. Tersimpan di Downloads\\ACC Media Downloader');
  } catch (e) {
    emit('app-complete', false, 'Gagal: ' + friendlyError(e.message));
  }
});

ipcMain.on('acc-social-download', async (_event, payload) => {
  try {
    const mode = payload && payload.kind === 'audio' ? 'audio' : 'video';
    await downloadWithYtDlp(
      payload.url,
      mode,
      'best',
      '320',
      '',
      (p, line) => emit('social-progress', p, line)
    );
    emit('social-complete', true, 'Selesai. Tersimpan di Downloads\\ACC Media Downloader');
  } catch (e) {
    emit('social-complete', false, 'Gagal: ' + friendlyError(e.message));
  }
});

ipcMain.on('acc-spotify-import', async (_event, rawUrl) => {
  try {
    const data = await importSpotify(rawUrl);
    emit('spotify-playlist', true, JSON.stringify(data));
  } catch (e) {
    emit('spotify-playlist', false, friendlyError(e.message));
  }
});

ipcMain.on('acc-spotify-track', async (_event, payload) => {
  const index = Number(payload && payload.index) || 0;
  try {
    const mode = payload && payload.kind === 'video' ? 'video' : 'audio';
    const source = 'ytsearch1:' + String(payload.query || '').trim();
    const prefix = String(index + 1).padStart(2, '0') + ' - ';
    await downloadWithYtDlp(source, mode, 'best', '320', prefix, (p, line) => {
      emit('spotify-track-progress', index, p, line);
    });
    emit('spotify-track-complete', index, true, 'Tersimpan');
  } catch (e) {
    emit('spotify-track-complete', index, false, friendlyError(e.message));
  }
});

ipcMain.on('acc-youtube-playlist-import', async (_event, rawUrl) => {
  try {
    const data = await importYoutubePlaylist(rawUrl);
    emit('yt-playlist', true, JSON.stringify(data));
  } catch (e) {
    emit('yt-playlist', false, friendlyError(e.message));
  }
});

ipcMain.on('acc-youtube-playlist-track', async (_event, payload) => {
  const index = Number(payload && payload.index) || 0;
  try {
    const mode = payload && payload.kind === 'video' ? 'video' : 'audio';
    const prefix = String(index + 1).padStart(2, '0') + ' - ';
    await downloadWithYtDlp(payload.url, mode, 'best', '320', prefix, (p, line) => {
      emit('yt-track-progress', index, p, line);
    });
    emit('yt-track-complete', index, true, 'Tersimpan');
  } catch (e) {
    emit('yt-track-complete', index, false, friendlyError(e.message));
  }
});

app.whenReady().then(() => {
  createWindow();
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
