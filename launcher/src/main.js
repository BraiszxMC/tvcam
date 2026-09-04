'use strict';

const path = require('path');
const { app, BrowserWindow, ipcMain, shell } = require('electron');
const config = require('./config');
const discordAuth = require('./discord-auth');

const WINDOW_WIDTH = 900;
const WINDOW_HEIGHT = 600;

let mainWindow = null;

function createWindow() {
  mainWindow = new BrowserWindow({
    width: WINDOW_WIDTH,
    height: WINDOW_HEIGHT,
    // Ventana fija: ni se agranda ni se encoge, solo minimizar y cerrar.
    resizable: false,
    maximizable: false,
    fullscreenable: false,
    minimizable: true,
    closable: true,
    minWidth: WINDOW_WIDTH,
    maxWidth: WINDOW_WIDTH,
    minHeight: WINDOW_HEIGHT,
    maxHeight: WINDOW_HEIGHT,
    center: true,
    frame: false,
    transparent: false,
    backgroundColor: '#070b16',
    show: false,
    title: 'TVCam Launcher',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false
    }
  });

  mainWindow.setMenuBarVisibility(false);
  mainWindow.loadFile(path.join(__dirname, 'renderer', 'index.html'));

  mainWindow.once('ready-to-show', () => mainWindow.show());

  // Por si el gestor de ventanas se salta resizable:false.
  mainWindow.on('will-resize', (event) => event.preventDefault());
  mainWindow.on('maximize', () => mainWindow.unmaximize());
  mainWindow.on('enter-full-screen', () => mainWindow.setFullScreen(false));
  mainWindow.on('closed', () => { mainWindow = null; });

  // Nada de abrir ventanas nuevas dentro del launcher.
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https:\/\//.test(url)) shell.openExternal(url);
    return { action: 'deny' };
  });
}

// Una sola instancia: si abren otra, enfocamos la que ya hay.
if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (!mainWindow) return;
    if (mainWindow.isMinimized()) mainWindow.restore();
    mainWindow.focus();
  });

  app.whenReady().then(() => {
    createWindow();
    app.on('activate', () => {
      if (BrowserWindow.getAllWindows().length === 0) createWindow();
    });
  });

  app.on('window-all-closed', () => app.quit());
}

ipcMain.handle('app:info', () => ({
  version: app.getVersion(),
  studioName: config.studioName,
  isConfigured: config.isConfigured
}));

ipcMain.handle('window:minimize', () => {
  if (mainWindow) mainWindow.minimize();
});

ipcMain.handle('window:close', () => {
  if (mainWindow) mainWindow.close();
});

ipcMain.handle('discord:login', async () => {
  try {
    const user = await discordAuth.login();
    return { ok: true, user };
  } catch (err) {
    return { ok: false, error: err.message };
  }
});
