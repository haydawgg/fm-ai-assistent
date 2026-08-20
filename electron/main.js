import { app, BrowserWindow, ipcMain, shell } from 'electron';
import { fileURLToPath } from 'url';
import path from 'path';
import {
  startBackend,
  stopBackend,
  getBackendState,
  markBackendReady,
  isExpectedBackendResponse,
} from './java-launcher.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
let BACKEND_URL = process.env.FM_AI_BACKEND_URL || 'http://127.0.0.1:8080';
let BACKEND_ORIGIN = null;
try {
  BACKEND_ORIGIN = new URL(BACKEND_URL).origin;
} catch {
  BACKEND_URL = 'http://127.0.0.1:8080';
  BACKEND_ORIGIN = new URL(BACKEND_URL).origin;
}
const BACKEND_HEALTH_URL = new URL('/actuator/health', BACKEND_URL).toString();

if (process.platform === 'win32') {
  app.setAppUserModelId('com.github.fmaiassistent');
}

const gotSingleInstanceLock = app.requestSingleInstanceLock();
if (!gotSingleInstanceLock) {
  app.quit();
}

let mainWindow = null;
let quitting = false;
const UI_ZOOM_MIN = 0.8;
const UI_ZOOM_MAX = 1.5;
const UI_ZOOM_STEP = 0.1;

function adjustUiZoom(webContents, direction) {
  const current = webContents.getZoomFactor();
  const next = Math.max(
    UI_ZOOM_MIN,
    Math.min(UI_ZOOM_MAX, Math.round((current + direction * UI_ZOOM_STEP) * 10) / 10),
  );
  if (next !== current) {
    webContents.setZoomFactor(next);
  }
}

function updateSplashStatus(message) {
  if (!mainWindow || mainWindow.isDestroyed()) {
    return;
  }
  mainWindow.webContents.executeJavaScript(
    `document.getElementById('status-message')?.replaceChildren(document.createTextNode(${JSON.stringify(message)}));`,
  ).catch(() => {});
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1440,
    height: 900,
    minWidth: 1024,
    minHeight: 640,
    title: 'FM AI Assistent',
    icon: path.join(__dirname, 'assets', 'icon.png'),
    backgroundColor: '#0f172a',
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });

  mainWindow.setMenuBarVisibility(false);
  mainWindow.loadFile(path.join(__dirname, 'splash.html'));

  mainWindow.webContents.on('before-input-event', (event, input) => {
    if (input.type !== 'keyDown' || !input.control || input.alt || input.meta) {
      return;
    }
    const isZoomIn = input.key === '+'
      || input.code === 'NumpadAdd'
      || (input.code === 'Equal' && input.shift);
    const isZoomOut = input.key === '-'
      || input.code === 'NumpadSubtract'
      || input.code === 'Minus';
    if (!isZoomIn && !isZoomOut) {
      return;
    }
    event.preventDefault();
    adjustUiZoom(mainWindow.webContents, isZoomIn ? 1 : -1);
  });

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('http://') || url.startsWith('https://')) {
      void shell.openExternal(url).catch(() => {});
    }
    return { action: 'deny' };
  });

  mainWindow.webContents.on('will-navigate', (event, url) => {
    let origin = null;
    try {
      origin = new URL(url).origin;
    } catch {
      // invalid URL; block it below
    }
    if (origin === BACKEND_ORIGIN) {
      return;
    }
    event.preventDefault();
    if (url.startsWith('http://') || url.startsWith('https://')) {
      void shell.openExternal(url).catch(() => {});
    }
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

function waitForBackend(url, timeoutMs = 120_000) {
  const started = Date.now();
  updateSplashStatus('Starting backend…');
  return new Promise((resolve, reject) => {
    const poll = () => {
      const state = getBackendState();
      if (state.state === 'error') {
        reject(new Error(state.error || 'Backend failed to start'));
        return;
      }
      fetch(BACKEND_HEALTH_URL, { signal: AbortSignal.timeout(2000) })
        .then(async (response) => {
        if (response.ok && await isExpectedBackendResponse(response)) {
          markBackendReady();
          resolve();
        } else {
          updateSplashStatus(`Initializing backend… ${Math.floor((Date.now() - started) / 1000)}s`);
          retry();
        }
      })
        .catch(() => {
          updateSplashStatus(`Waiting for backend… ${Math.floor((Date.now() - started) / 1000)}s`);
          retry();
        });
    };
    const retry = () => {
      if (Date.now() - started > timeoutMs) {
        reject(new Error('Backend did not become ready in time'));
      } else {
        setTimeout(poll, 1500);
      }
    };
    poll();
  });
}

function showError(message) {
  if (!mainWindow || mainWindow.isDestroyed()) {
    return;
  }
  mainWindow.loadFile(path.join(__dirname, 'error.html'), { query: { message } });
}

async function loadAppIntoWindow() {
  if (!mainWindow || mainWindow.isDestroyed()) {
    return;
  }
  const state = getBackendState();
  if (state.state === 'ready') {
    mainWindow.loadURL(BACKEND_URL);
    return;
  }
  if (state.state === 'error') {
    showError(state.error || 'Failed to start Java backend');
    return;
  }
  let started = false;
  try {
    started = await startBackend();
  } catch (error) {
    showError(`Backend did not start: ${error.message}`);
    return;
  }
  if (!started) {
    const current = getBackendState();
    if (current.external) {
      mainWindow.loadURL(BACKEND_URL);
      return;
    }
    showError(current.error || 'Failed to start Java backend');
    return;
  }
  try {
    await waitForBackend(BACKEND_URL);
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.loadURL(BACKEND_URL);
    }
  } catch (error) {
    showError(`Backend did not start: ${error.message}`);
  }
}

if (gotSingleInstanceLock) {
  app.on('second-instance', () => {
    if (!mainWindow || mainWindow.isDestroyed()) {
      createWindow();
      loadAppIntoWindow();
      return;
    }
    if (mainWindow.isMinimized()) {
      mainWindow.restore();
    }
    mainWindow.focus();
  });

  app.whenReady().then(async () => {
    ipcMain.handle('app:version', () => app.getVersion());
    ipcMain.handle('app:open-external', (_event, url) => {
      if (typeof url === 'string' && (url.startsWith('http://') || url.startsWith('https://'))) {
        return shell.openExternal(url).catch(() => undefined);
      }
      return undefined;
    });
    ipcMain.handle('backend:state', () => getBackendState());

    createWindow();
    await loadAppIntoWindow();
  });

  app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') {
      app.quit();
    }
  });

  app.on('before-quit', (event) => {
    event.preventDefault();
    if (quitting) {
      return;
    }
    quitting = true;
    stopBackend().finally(() => {
      app.exit(0);
    });
  });

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
      loadAppIntoWindow();
    }
  });
}
