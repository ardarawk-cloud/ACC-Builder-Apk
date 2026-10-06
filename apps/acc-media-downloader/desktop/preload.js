'use strict';

const { contextBridge, ipcRenderer, clipboard } = require('electron');

contextBridge.exposeInMainWorld('ACCNative', {
  getClipboard: () => clipboard.readText() || '',
  openExternal: (url) => ipcRenderer.send('acc-open-external', String(url || '')),
  download: (url, name) => ipcRenderer.send('acc-direct-download', { url: String(url || ''), name: String(name || '') }),
  downloadYoutube: (url, kind, quality) => ipcRenderer.send('acc-youtube-download', {
    url: String(url || ''), kind: String(kind || 'video'), quality: String(quality || 'best'), bitrate: '320'
  }),
  downloadYoutubeAdvanced: (url, kind, quality, bitrate) => ipcRenderer.send('acc-youtube-download', {
    url: String(url || ''), kind: String(kind || 'video'), quality: String(quality || 'best'), bitrate: String(bitrate || '320')
  }),
  downloadSocial: (url, kind) => ipcRenderer.send('acc-social-download', {
    url: String(url || ''), kind: String(kind || 'video')
  }),
  importSpotifyPlaylist: (url) => ipcRenderer.send('acc-spotify-import', String(url || '')),
  downloadPlaylistTrack: (query, kind, index) => ipcRenderer.send('acc-spotify-track', {
    query: String(query || ''), kind: String(kind || 'audio'), index: Number(index) || 0
  }),
  importYoutubePlaylist: (url) => ipcRenderer.send('acc-youtube-playlist-import', String(url || '')),
  downloadYoutubePlaylistTrack: (url, kind, index) => ipcRenderer.send('acc-youtube-playlist-track', {
    url: String(url || ''), kind: String(kind || 'audio'), index: Number(index) || 0
  }),
  onEvent: (callback) => {
    if (typeof callback !== 'function') return;
    ipcRenderer.on('acc-event', (_event, payload) => callback(payload));
  }
});
