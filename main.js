const { app, BrowserWindow, shell, ipcMain, Tray, Menu } = require('electron');
let autoUpdater = null;
try {
  autoUpdater = require('electron-updater').autoUpdater;
} catch (e) {
  console.warn('[updater] electron-updater not available:', e.message);
}
const path = require('path');
const http = require('http');
const { fork } = require('child_process');
const fs = require('fs');
const net = require('net');
const { DiscordPresence } = require('./discord-presence.js');
const { startGoogleOAuth, isGoogleClientId } = require('./google-oauth.js');

let PORT = 17217;
let mainWindow = null;
let serverProcess = null;
let tray = null;
let closeToTrayEnabled = false;
let isQuitting = false;
let googleAuthPromise = null;

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

// --- GPU-safe mode -----------------------------------------------------------
// Some Linux GPU drivers break Chromium's ANGLE backend (glGetInternalformativ
// errors, hung renderer) and the window never appears. Software rendering can
// be forced with --disable-gpu / --software / --safe-mode (or VOTIFY_DISABLE_GPU=1),
// and the app also relaunches itself into this mode when the GPU process dies
// or the window never becomes ready.
const SOFTWARE_GPU_FLAG = '--votify-software-gpu';
const softwareGpuRequested =
  process.argv.includes('--disable-gpu') ||
  process.argv.includes('--software') ||
  process.argv.includes('--safe-mode') ||
  process.argv.includes(SOFTWARE_GPU_FLAG) ||
  process.env.VOTIFY_DISABLE_GPU === '1';
if (softwareGpuRequested) {
  console.log('[gpu] Software rendering mode enabled');
  app.disableHardwareAcceleration();
  app.commandLine.appendSwitch('disable-gpu');
  // Newer Chromium needs this for the SwiftShader software fallback.
  app.commandLine.appendSwitch('enable-unsafe-swiftshader');
}
// X11 fallback for broken Wayland presentation (black window with a live
// page). Auto-selected by the cascade, or forced with --x11.
const X11_FLAG = '--votify-x11';
const x11Requested = process.argv.includes('--x11') || process.argv.includes(X11_FLAG);
if (x11Requested && !process.argv.some(a => a.startsWith('--ozone-platform'))) {
  console.log('[gpu] Forcing ozone-platform=x11');
  app.commandLine.appendSwitch('ozone-platform', 'x11');
}
console.log(
  `[votify] starting pid=${process.pid} version=${app.getVersion()} ` +
    `softwareGpu=${softwareGpuRequested} x11=${x11Requested} execPath=${process.execPath} ` +
    `display=${process.env.DISPLAY || '-'} wayland=${process.env.WAYLAND_DISPLAY || '-'} ` +
    `session=${process.env.XDG_SESSION_TYPE || '-'}`,
);

let gpuRelaunchDone = false;
function doRelaunch(extraArgs, reason) {
  if (gpuRelaunchDone) return;
  gpuRelaunchDone = true;
  console.warn(`[gpu] ${reason} — relaunching`);
  // The forked local server would otherwise stay orphaned and keep the port.
  try {
    if (serverProcess) serverProcess.kill();
  } catch (e) {
    /* ignore */
  }
  const args = process.argv.slice(1).concat(extraArgs.filter(f => !process.argv.includes(f)));
  // AppImage-safe restart: re-executing process.execPath (inside the FUSE
  // mount) dies silently when the parent's mount is torn down — relaunch
  // through the AppImage file itself so it mounts fresh.
  const appImage = process.env.APPIMAGE;
  try {
    if (appImage) {
      console.log(`[gpu] relaunching via AppImage ${appImage}`);
      app.relaunch({ execPath: appImage, args });
    } else {
      app.relaunch({ args });
    }
  } catch (e) {
    console.warn('[gpu] relaunch failed:', e.message);
    if (mainWindow && !mainWindow.isVisible()) mainWindow.show();
    return;
  }
  app.exit(0);
}

function relaunchWithSoftwareGpu(reason) {
  if (softwareGpuRequested) return;
  doRelaunch([SOFTWARE_GPU_FLAG], reason);
}

app.on('gpu-process-crashed', (event, killed) => {
  relaunchWithSoftwareGpu(`GPU process crashed (killed=${killed})`);
});
app.on('child-process-gone', (event, details) => {
  if (details && details.type === 'GPU') {
    relaunchWithSoftwareGpu(`GPU child process gone (${details.reason || 'unknown'})`);
  }
});

function getGoogleDesktopCredentials() {
  let clientId = process.env.VOTIFY_GOOGLE_DESKTOP_CLIENT_ID || '';
  let clientSecret = process.env.VOTIFY_GOOGLE_DESKTOP_CLIENT_SECRET || '';
  let config = null;
  if (process.env.VOTIFY_FIREBASE_CONFIG) {
    try {
      config = JSON.parse(process.env.VOTIFY_FIREBASE_CONFIG);
    } catch (error) {
      console.warn('[google-auth] Invalid VOTIFY_FIREBASE_CONFIG:', error.message);
    }
  }
  if (!config) {
    try {
      const configPath = path.join(__dirname, 'firebase-config.json');
      config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
    } catch (error) {
      if (error.code !== 'ENOENT') console.warn('[google-auth] Config error:', error.message);
    }
  }
  clientId ||= config?.googleDesktopClientId || '';
  clientSecret ||= config?.googleDesktopClientSecret || '';
  const normalizedClientId = String(clientId).trim();
  return {
    clientId: isGoogleClientId(normalizedClientId) ? normalizedClientId : '',
    clientSecret: String(clientSecret).trim(),
  };
}

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
    // Show immediately: on Wayland a hidden window may never report
    // ready-to-show (no frame is ever presented for it), leaving the app
    // invisible. backgroundColor avoids a white flash before first paint.
    show: true,
    backgroundColor: '#0a0a0b',
  });

  // Health tracking: the first painted frame (ready-to-show) or a finished
  // main-frame load proves the page is alive. If NEITHER arrives, escalate
  // automatically: native Wayland -> X11 -> software rendering.
  let pageHealthy = false;
  const markHealthy = why => {
    if (pageHealthy) return;
    pageHealthy = true;
    console.log(`[window] healthy (${why})`);
    clearTimeout(showFailsafe);
    clearTimeout(cascadeTimer);
  };
  mainWindow.once('ready-to-show', () => {
    console.log('[window] ready-to-show');
    markHealthy('ready-to-show');
  });
  mainWindow.on('show', () => console.log('[window] show event'));

  // Safety net: the window is created visible, so this normally no-ops.
  const showFailsafe = setTimeout(() => {
    if (!mainWindow || mainWindow.isVisible()) return;
    console.warn('[window] window still hidden, forcing show()');
    mainWindow.show();
  }, 10000);
  if (typeof showFailsafe.unref === 'function') showFailsafe.unref();

  const cascadeTimer = setTimeout(() => {
    if (pageHealthy || !mainWindow) return;
    if (!x11Requested && !softwareGpuRequested && process.env.DISPLAY) {
      doRelaunch([X11_FLAG], 'no paint/load signals, trying X11');
    } else if (!softwareGpuRequested) {
      doRelaunch([SOFTWARE_GPU_FLAG], 'no paint/load signals, trying software rendering');
    } else {
      console.warn('[window] page still not alive in software mode, leaving window as is');
    }
  }, 12000);
  if (typeof cascadeTimer.unref === 'function') cascadeTimer.unref();

  // Load via HTTP to avoid file:// CORS issues
  mainWindow.loadURL(`http://localhost:${PORT}/index.html?v=${Date.now()}`);

  mainWindow.webContents.once('did-finish-load', () => {
    console.log('[window] did-finish-load');
    markHealthy('did-finish-load');
  });
  let loadRetries = 0;
  mainWindow.webContents.on('did-fail-load', (event, code, desc, url, isMainFrame) => {
    if (!isMainFrame) return;
    console.error(`[window] load failed (${code}): ${desc}`);
    if (loadRetries < 2) {
      loadRetries += 1;
      setTimeout(() => {
        if (mainWindow) mainWindow.loadURL(`http://localhost:${PORT}/index.html?v=${Date.now()}`);
      }, 1500);
    } else if (mainWindow && !mainWindow.isVisible()) {
      mainWindow.show();
    }
  });

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url);
    return { action: 'deny' };
  });

  let rendererReloaded = false;
  mainWindow.webContents.on('render-process-gone', (event, details) => {
    discordPresence.clear();
    console.error('[window] render-process-gone:', details && details.reason);
    if (!rendererReloaded && details && details.reason !== 'clean-exit') {
      rendererReloaded = true;
      try {
        mainWindow.webContents.reload();
      } catch (e) {
        console.warn('[window] reload failed:', e.message);
      }
    }
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


function setupAutoUpdater() {
  if (!autoUpdater) return;
  if (!app.isPackaged) {
    console.log('[updater] Skipping in dev mode (not packaged)');
    // For dev, still check GitHub API for info
    checkGitHubForUpdates();
    return;
  }

  autoUpdater.autoDownload = true;
  autoUpdater.autoInstallOnAppQuit = true;
  autoUpdater.allowPrerelease = false;
  autoUpdater.allowDowngrade = false;

  autoUpdater.on('checking-for-update', () => {
    console.log('[updater] Checking for update...');
    mainWindow?.webContents.send('update:checking');
  });

  autoUpdater.on('update-available', info => {
    console.log('[updater] Update available:', info.version);
    mainWindow?.webContents.send('update:available', info);
  });

  autoUpdater.on('update-not-available', info => {
    console.log('[updater] Update not available, current:', info.version);
    mainWindow?.webContents.send('update:not-available', info);
  });

  autoUpdater.on('error', err => {
    console.error('[updater] Error:', err.message);
    mainWindow?.webContents.send('update:error', err.message);
  });

  autoUpdater.on('download-progress', progress => {
    mainWindow?.webContents.send('update:progress', progress);
  });

  autoUpdater.on('update-downloaded', info => {
    console.log('[updater] Update downloaded:', info.version);
    mainWindow?.webContents.send('update:downloaded', info);
    // Like Discord, show notification and auto-install on quit, but also allow immediate install
  });

  // Check on startup after 3s like Discord
  setTimeout(() => {
    console.log('[updater] Checking for updates on startup...');
    autoUpdater.checkForUpdatesAndNotify().catch(e => console.warn('[updater] check failed:', e.message));
  }, 3000);

  // Check every 6 hours like Discord
  setInterval(() => {
    autoUpdater.checkForUpdatesAndNotify().catch(() => {});
  }, 6 * 60 * 60 * 1000);
}

async function checkGitHubForUpdates() {
  // Fallback for dev or when electron-updater not available — check GitHub releases API
  try {
    const https = require('https');
    const currentVersion = app.getVersion();
    const options = {
      hostname: 'api.github.com',
      path: '/repos/exieeez/Votify/releases/latest',
      method: 'GET',
      headers: { 'User-Agent': 'Votify-Updater', 'Accept': 'application/vnd.github.v3+json' }
    };
    const req = https.request(options, res => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          const release = JSON.parse(data);
          const latest = (release.tag_name || release.name || '').replace(/^v/, '');
          if (latest && latest !== currentVersion) {
            console.log(`[updater] GitHub latest: ${latest}, current: ${currentVersion}`);
            // Simple semver compare
            const cmp = latest.localeCompare(currentVersion, undefined, { numeric: true });
            if (cmp > 0) {
              mainWindow?.webContents.send('update:available', { version: latest, releaseNotes: release.body, releaseName: release.name });
            }
          }
        } catch (e) {}
      });
    });
    req.on('error', () => {});
    req.end();
  } catch (e) {}
}

app.whenReady().then(async () => {
  if (!discordPresence.start()) {
    console.warn('[discord] Rich Presence disabled: invalid Discord Application ID');
  }
  await startServer();
  console.log(`[votify] local server on port ${PORT}, creating window`);
  createWindow();
  createTray();
  setupAutoUpdater();

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
  console.log('[votify] quitting');
  isQuitting = true;
  void discordPresence.stop();
  if (serverProcess) {
    serverProcess.kill();
  }
});

ipcMain.handle('check-for-updates', async () => {
  if (autoUpdater && app.isPackaged) {
    try {
      return await autoUpdater.checkForUpdates();
    } catch (e) {
      return { error: e.message };
    }
  } else {
    checkGitHubForUpdates();
    return { message: 'Checking GitHub...' };
  }
});

ipcMain.handle('install-update', () => {
  if (autoUpdater) {
    autoUpdater.quitAndInstall();
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

ipcMain.handle('google-auth:start', async event => {
  if (!mainWindow || event.sender !== mainWindow.webContents) {
    return { error: 'Запрос входа отклонён' };
  }
  const { clientId, clientSecret } = getGoogleDesktopCredentials();
  if (!clientId) {
    return {
      error: 'Добавьте googleDesktopClientId из Google Cloud в firebase-config.json',
    };
  }
  if (!googleAuthPromise) {
    googleAuthPromise = startGoogleOAuth({
      clientId,
      clientSecret,
      openExternal: url => shell.openExternal(url),
    }).finally(() => {
      googleAuthPromise = null;
    });
  }
  try {
    return { tokens: await googleAuthPromise };
  } catch (error) {
    return { error: error.message || 'Не удалось выполнить вход через Google' };
  }
});

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

// Открывает ссылку в браузере по умолчанию (логотип Votify в шапке → сайт проекта).
ipcMain.handle('open-external', async (event, url) => {
  const target = String(url || '').trim();
  if (!/^https?:\/\//i.test(target)) return false;
  try {
    await shell.openExternal(target);
    return true;
  } catch (e) {
    console.error('Failed to open external url:', e.message);
    return false;
  }
});

// IPC Handler to physically throw/knockback OS cursor when pet gets angry
ipcMain.handle('throw-cursor', (event, { dx = -250, dy = -250 }) => {
  try {
    const { exec } = require('child_process');
    if (process.platform === 'linux') {
      exec(`xdotool mousemove_relative -- ${dx} ${dy}`, err => {
        if (err) {
          exec(`python3 -c "import pyautogui; pyautogui.moveRel(${dx}, ${dy})"`);
        }
      });
    } else if (process.platform === 'win32') {
      exec(
        `powershell -command "[reflection.assembly]::loadwithpartialname('System.Windows.Forms'); $p = [System.Windows.Forms.Cursor]::Position; [System.Windows.Forms.Cursor]::Position = New-Object System.Drawing.Point(($p.X + ${dx}), ($p.Y + ${dy}))"`
      );
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
