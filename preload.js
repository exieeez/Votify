const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('electronAPI', {
  minimize: () => ipcRenderer.invoke('minimize'),
  maximize: () => ipcRenderer.invoke('maximize'),
  close: () => ipcRenderer.invoke('close'),
  isMaximized: () => ipcRenderer.invoke('window-is-maximized'),
  getLaunchAtLogin: () => ipcRenderer.invoke('get-launch-at-login'),
  setLaunchAtLogin: enabled => ipcRenderer.invoke('set-launch-at-login', enabled),
  setCloseToTray: enabled => ipcRenderer.send('set-close-to-tray', enabled),
  throwCursor: (dx, dy) => ipcRenderer.invoke('throw-cursor', { dx, dy }),
});
