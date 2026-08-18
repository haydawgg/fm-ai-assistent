const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('fmAiAssistent', {
  getAppVersion: () => ipcRenderer.invoke('app:version'),
  openExternal: (url) => ipcRenderer.invoke('app:open-external', url),
  getBackendState: () => ipcRenderer.invoke('backend:state'),
});