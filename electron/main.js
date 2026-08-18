import { app, BrowserWindow, ipcMain, shell } from 'electron';
import { fileURLToPath } from 'url';
import path from 'path';
import { startBackend, stopBackend, getBackendState } from './java-launcher.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const BACKEND_URL = process.env.FM_AI_BACKEND_URL || 'http://127.0.0.1:8080';

let mainWindow = null;

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

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('http://') || url.startsWith('https://')) {
      shell.openExternal(url);
    }
    return { action: 'deny' };
  });

  mainWindow.webContents.on('will-navigate', (event, url) => {
    if (url.startsWith('http://127.0.0.1:8080') || url.startsWith('http://localhost:8080')) {
      return;
    }
    if (url.startsWith('http://') || url.startsWith('https://')) {
      event.preventDefault();
      shell.openExternal(url);
    }
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

function waitForBackend(url, timeoutMs = 120_000) {
  const started = Date.now();
  return new Promise((resolve, reject) => {
    const poll = () => {
      fetch(url, { signal: AbortSignal.timeout(2000) })
        .then((response) => {
          if (response.ok || response.status < 500) {
            resolve();
          } else {
            retry();
          }
        })
        .catch(() => retry());
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

app.whenReady().then(async () => {
  ipcMain.handle('app:version', () => app.getVersion());
  ipcMain.handle('app:open-external', (_event, url) => {
    if (typeof url === 'string' && (url.startsWith('http://') || url.startsWith('https://'))) {
      shell.openExternal(url);
    }
  });
  ipcMain.handle('backend:state', () => getBackendState());

  createWindow();

  const started = await startBackend();
  if (!started) {
    const state = getBackendState();
    if (state.external) {
      mainWindow.loadURL(BACKEND_URL);
      return;
    }
    showError(state.error || 'Failed to start Java backend');
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
});

function showError(message) {
  if (!mainWindow || mainWindow.isDestroyed()) {
    return;
  }
  mainWindow.loadFile(path.join(__dirname, 'error.html'), { query: { message } });
}

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

app.on('before-quit', () => {
  stopBackend();
});

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) {
    createWindow();
  }
});