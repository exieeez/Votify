const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('electronAPI', {
  minimize: () => ipcRenderer.invoke('minimize'),
  maximize: () => ipcRenderer.invoke('maximize'),
  close: () => ipcRenderer.invoke('close'),
  isMaximized: () => ipcRenderer.invoke('window-is-maximized'),
  getLaunchAtLogin: () => ipcRenderer.invoke('get-launch-at-login'),
  setLaunchAtLogin: enabled => ipcRenderer.invoke('set-launch-at-login', enabled),
  setCloseToTray: enabled => ipcRenderer.send('set-close-to-tray', enabled),
  updateDiscordPresence: playback => ipcRenderer.send('discord-presence:update', playback),
  clearDiscordPresence: () => ipcRenderer.send('discord-presence:clear'),
  signInWithGoogle: () => ipcRenderer.invoke('google-auth:start'),
  throwCursor: (dx, dy) => ipcRenderer.invoke('throw-cursor', { dx, dy }),
  // Auto-updater like Discord
  checkForUpdates: () => ipcRenderer.invoke('check-for-updates'),
  installUpdate: () => ipcRenderer.invoke('install-update'),
  onUpdateChecking: cb => ipcRenderer.on('update:checking', cb),
  onUpdateAvailable: cb => ipcRenderer.on('update:available', (e, info) => cb(info)),
  onUpdateNotAvailable: cb => ipcRenderer.on('update:not-available', (e, info) => cb(info)),
  onUpdateProgress: cb => ipcRenderer.on('update:progress', (e, p) => cb(p)),
  onUpdateDownloaded: cb => ipcRenderer.on('update:downloaded', (e, info) => cb(info)),
  onUpdateError: cb => ipcRenderer.on('update:error', (e, err) => cb(err)),
});
