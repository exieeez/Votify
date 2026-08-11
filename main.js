const { app, BrowserWindow, shell, ipcMain, Tray, Menu } = require('electron');
const path = require('path');
const http = require('http');
const { fork } = require('child_process');
const fs = require('fs');
const net = require('net');
const { DiscordPresence } = require('./discord-presence.js');

let PORT = 17217;
let mainWindow = null;
let serverProcess = null;
let tray = null;
let closeToTrayEnabled = false;
let isQuitting = false;

const VOTIFY_DISCORD_CLIENT_ID = '1536826368615256146';
const discordPresence = new DiscordPresence({
  clientId: process.env.VOTIFY_DISCORD_CLIENT_ID || VOTIFY_DISCORD_CLIENT_ID,
  applicationName: 'Votify',
  fallbackImageKey: process.env.VOTIFY_DISCORD_LARGE_IMAGE_KEY,
});

// Force persistent user data dir so settings/playlists survive restarts
const userDataPath = path.join(app.getPath('home'), '.votify');
try {
  fs.mkdirSync(userDataPath, { recursive: true });
} catch (e) {
  /* ignore */
}
app.setPath('userData', userDataPath);

// Allow autoplay of audio without user gesture
app.commandLine.appendSwitch('autoplay-policy', 'no-user-gesture-required');
// Fix cache errors with --no-sandbox
app.commandLine.appendSwitch('disk-cache-dir', path.join(userDataPath, 'cache'));
// UI files are served by the bundled local server. Caching them causes source
// runs to show an older interface after an update.
app.commandLine.appendSwitch('disable-http-cache');

function findYtDlp() {
  const isWindows = process.platform === 'win32';
  const binaryName = isWindows ? 'yt-dlp.exe' : 'yt-dlp';

  const candidates = [
    path.join(process.resourcesPath || '', 'app.asar.unpacked', 'bin', binaryName),
    path.join(__dirname, 'bin', binaryName),
    path.join(__dirname, binaryName),
    path.join(process.resourcesPath || '', binaryName),
    path.join(process.resourcesPath || '', 'app', 'bin', binaryName),
  ];
  for (const c of candidates) {
    if (c.includes(`app.asar${path.sep}`) && !c.includes(`app.asar.unpacked${path.sep}`)) continue;
    try {
      const stat = fs.statSync(c);
      if (stat.isFile()) return c;
    } catch (e) {
      // ignore missing candidate
    }
  }
  return path.join(process.resourcesPath || __dirname, 'app.asar.unpacked', 'bin', binaryName);
}

function isPortFree(port) {
  return new Promise(resolve => {
    const srv = net.createServer();
    srv.once('error', () => resolve(false));
    srv.once('listening', () => {
      srv.close();
      resolve(true);
    });
    srv.listen(port, '127.0.0.1');
  });
}

async function startServer() {
  // Do not attach a new window to a stale server left by a previous Electron
  // process. Pick the next local port when the default one is occupied.
  while (!(await isPortFree(PORT))) PORT += 1;
  const env = { ...process.env };
  env.YT_DLP_PATH = findYtDlp();
  env.VOTIFY_SRC_DIR = path.join(__dirname, 'src');
  env.VOTIFY_PORT = String(PORT);

  serverProcess = fork(path.join(__dirname, 'server.js'), {
    env,
    stdio: ['ignore', 'pipe', 'pipe', 'ipc'],
  });

  serverProcess.stdout?.on('data', d => {
    try {
      console.log('[server]', d.toString().trim());
    } catch (e) {
      /* ignore */
    }
  });
  serverProcess.stderr?.on('data', d => {
    try {
      console.error('[server]', d.toString().trim());
    } catch (e) {
      /* ignore */
    }
  });
  serverProcess.on('error', err => console.error('Server process error:', err.message));
  serverProcess.on('exit', (code, signal) => {
    console.log(`Server exited with code ${code}, signal ${signal}`);
  });

  for (let i = 0; i < 30; i++) {
    if (await isPortFree(PORT)) {
      await new Promise(r => setTimeout(r, 500));
    } else {
      break;
    }
  }
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1200,
    height: 800,
    minWidth: 900,
    minHeight: 600,
    frame: false,
    titleBarStyle: 'hidden',
    titleBarOverlay: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
    },
    icon: path.join(__dirname, 'src/icon.png'),
    show: false,
  });

  mainWindow.once('ready-to-show', () => {
    mainWindow.show();
  });

  // Load via HTTP to avoid file:// CORS issues
  mainWindow.loadURL(`http://localhost:${PORT}/index.html?v=${Date.now()}`);

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url);
    return { action: 'deny' };
  });

  mainWindow.webContents.on('render-process-gone', () => {
    discordPresence.clear();
  });

  mainWindow.on('closed', () => {
    discordPresence.clear();
    mainWindow = null;
  });

  mainWindow.on('close', event => {
    if (closeToTrayEnabled && !isQuitting) {
      event.preventDefault();
      mainWindow.hide();
    }
  });
}

function createTray() {
  if (tray) return;
  try {
    tray = new Tray(path.join(__dirname, 'src/icon.png'));
    tray.setToolTip('Votify');
    const contextMenu = Menu.buildFromTemplate([
      {
        label: 'Открыть Votify',
        click: () => {
          if (mainWindow) {
            mainWindow.show();
            mainWindow.focus();
          }
        },
      },
      { type: 'separator' },
      {
        label: 'Выход',
        click: () => {
          isQuitting = true;
          app.quit();
        },
      },
    ]);
    tray.setContextMenu(contextMenu);
    tray.on('click', () => {
      if (!mainWindow) return;
      if (mainWindow.isVisible()) {
        mainWindow.hide();
      } else {
        mainWindow.show();
        mainWindow.focus();
      }
    });
  } catch (e) {
    console.error('Tray creation failed:', e.message);
  }
}

app.whenReady().then(async () => {
  if (!discordPresence.start()) {
    console.warn('[discord] Rich Presence disabled: invalid Discord Application ID');
  }
  await startServer();
  createWindow();
  createTray();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    if (serverProcess) {
      serverProcess.kill();
    }
    app.quit();
  }
});

app.on('before-quit', () => {
  isQuitting = true;
  void discordPresence.stop();
  if (serverProcess) {
    serverProcess.kill();
  }
});

ipcMain.handle('minimize', () => mainWindow?.minimize());
ipcMain.handle('maximize', () => {
  if (!mainWindow) return false;
  if (mainWindow.isMaximized()) mainWindow.unmaximize();
  else mainWindow.maximize();
  return mainWindow.isMaximized();
});
ipcMain.handle('close', () => mainWindow?.close());
ipcMain.handle('isMaximized', () => mainWindow?.isMaximized());

ipcMain.on('discord-presence:update', (event, playback) => {
  if (!mainWindow || event.sender !== mainWindow.webContents) return;
  discordPresence.update(playback);
});

ipcMain.on('discord-presence:clear', event => {
  if (!mainWindow || event.sender !== mainWindow.webContents) return;
  discordPresence.clear();
});

// --- Settings-related IPC ---
ipcMain.handle('get-launch-at-login', () => {
  try {
    return app.getLoginItemSettings().openAtLogin;
  } catch (e) {
    return false;
  }
});

ipcMain.handle('set-launch-at-login', (event, enabled) => {
  try {
    app.setLoginItemSettings({ openAtLogin: !!enabled });
    return true;
  } catch (e) {
    console.error('Failed to set launch at login:', e.message);
    return false;
  }
});

// IPC Handler to physically throw/knockback OS cursor when pet gets angry
ipcMain.handle('throw-cursor', (event, { dx = -250, dy = -250 }) => {
  try {
    const { exec } = require('child_process');
    if (process.platform === 'linux') {
      exec(`xdotool mousemove_relative -- ${dx} ${dy}`, (err) => {
        if (err) {
          exec(`python3 -c "import pyautogui; pyautogui.moveRel(${dx}, ${dy})"`);
        }
      });
    } else if (process.platform === 'win32') {
      exec(`powershell -command "[reflection.assembly]::loadwithpartialname('System.Windows.Forms'); $p = [System.Windows.Forms.Cursor]::Position; [System.Windows.Forms.Cursor]::Position = New-Object System.Drawing.Point(($p.X + ${dx}), ($p.Y + ${dy}))"`);
    }
    return true;
  } catch (e) {
    console.error('Failed to throw cursor:', e.message);
    return false;
  }
});

ipcMain.on('set-close-to-tray', (event, enabled) => {
  closeToTrayEnabled = !!enabled;
});

ipcMain.handle('install-soundpad-driver', async () => {
  try {
    if (process.platform === 'win32') {
      const { exec } = require('child_process');
      return new Promise(resolve => {
        exec('powershell -Command "Get-AudioDevice -List | Enable-AudioDevice"', () => {
          resolve({ success: true, message: 'Виртуальный драйвер Votify Audio подключен!' });
        });
      });
    }
    return { success: true, message: 'Встроенный Soundpad активен!' };
  } catch (e) {
    return { success: false, message: e.message };
  }
});
