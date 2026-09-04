'use strict';

const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('launcher', {
  getInfo: () => ipcRenderer.invoke('app:info'),
  minimize: () => ipcRenderer.invoke('window:minimize'),
  close: () => ipcRenderer.invoke('window:close'),
  loginWithDiscord: () => ipcRenderer.invoke('discord:login')
});
