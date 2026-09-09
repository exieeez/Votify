const http = require('http');
const fs = require('fs');
const path = require('path');
const {
  sendJson,
  serveStatic,
  parseBody,
  saveNetworkConfig,
  getNetworkConfig,
} = require('./routes/utils.js');
const { handleAuthRoutes } = require('./routes/auth.js');
const { handleMusicRoutes } = require('./routes/music.js');
const { handleSmtpRoutes } = require('./routes/smtp.js');
const { handleSyncRoutes } = require('./routes/sync.js');

const FIREBASE_CONFIG_FIELDS = [
  'apiKey',
  'authDomain',
  'projectId',
  'storageBucket',
  'messagingSenderId',
  'appId',
];
const FIREBASE_VENDOR_FILES = {
  '/vendor/firebase-app-compat.js': 'firebase-app-compat.js',
  '/vendor/firebase-auth-compat.js': 'firebase-auth-compat.js',
  '/vendor/firebase-firestore-compat.js': 'firebase-firestore-compat.js',
};

function loadFirebaseConfig() {
  let rawConfig = null;
  if (process.env.VOTIFY_FIREBASE_CONFIG) {
    rawConfig = JSON.parse(process.env.VOTIFY_FIREBASE_CONFIG);
  } else {
    const configPath = path.join(__dirname, 'firebase-config.json');
    if (fs.existsSync(configPath)) rawConfig = JSON.parse(fs.readFileSync(configPath, 'utf8'));
  }
  if (!rawConfig) return null;
  if (rawConfig.private_key || rawConfig.privateKey || rawConfig.type === 'service_account') {
    throw new Error('Service Account JSON cannot be used as Firebase Web Config');
  }
  const config = {};
  FIREBASE_CONFIG_FIELDS.forEach(field => {
    if (rawConfig[field]) config[field] = String(rawConfig[field]);
  });
  const required = ['apiKey', 'authDomain', 'projectId', 'appId'];
  if (required.some(field => !config[field])) throw new Error('Incomplete Firebase Web Config');
  return config;
}

function serveFirebaseVendor(pathname, res) {
  const filename = FIREBASE_VENDOR_FILES[pathname];
  if (!filename) return false;
  const fullPath = path.join(__dirname, 'node_modules', 'firebase', filename);
  try {
    const data = fs.readFileSync(fullPath);
    res.writeHead(200, {
      'Content-Type': 'application/javascript; charset=utf-8',
      'Cache-Control': 'public, max-age=31536000, immutable',
    });
    res.end(data);
  } catch (error) {
    sendJson(res, 500, { error: `Firebase SDK is unavailable: ${error.message}` });
  }
  return true;
}

function setCorsHeaders(res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, Range');
  res.setHeader('Access-Control-Expose-Headers', 'Content-Range, Content-Length');
}

const server = http.createServer(async (req, res) => {
  setCorsHeaders(res);

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  const u = new URL(req.url, `http://${req.headers.host}`);
  try {
    if (u.pathname === '/api/firebase/config' && req.method === 'GET') {
      try {
        const config = loadFirebaseConfig();
        if (!config) {
          sendJson(res, 503, { error: 'Firebase Web Config not found' });
        } else {
          sendJson(res, 200, { config });
        }
      } catch (error) {
        sendJson(res, 500, { error: error.message });
      }
      return;
    }
    if (serveFirebaseVendor(u.pathname, res)) return;

    // Корень — редирект на уникальный URL: ломает любые кэши прокси/браузера,
    // чтобы превью всегда получало свежий index.html и свежие ассеты
    if (u.pathname === '/' && req.method === 'GET') {
      res.writeHead(302, {
        Location: '/index.html?ts=' + Date.now(),
        'Cache-Control': 'no-store, no-cache, must-revalidate',
        Pragma: 'no-cache',
      });
      res.end();
      return;
    }

    // --- NETWORK ENDPOINTS ---
    if (u.pathname === '/api/network/settings' && req.method === 'GET') {
      sendJson(res, 200, getNetworkConfig());
      return;
    }
    if (u.pathname === '/api/network/settings' && req.method === 'POST') {
      const body = await parseBody(req);
      const updatedConfig = saveNetworkConfig(body);
      sendJson(res, 200, { updated: true, config: updatedConfig });
      return;
    }

    // --- SYNC ENDPOINTS ---
    if (await handleSyncRoutes(req, res, u)) return;

    // --- AUTH ENDPOINTS ---
    if (await handleAuthRoutes(req, res, u)) return;

    // --- SMTP CONFIG ---
    if (await handleSmtpRoutes(req, res, u)) return;

    // --- MUSIC ENDPOINTS ---
    if (await handleMusicRoutes(req, res, u)) return;

    // --- DEMO (offline catalog covers/audio) ---
    if (require('./routes/demo.js').handleDemoRoutes(req, res, u)) return;

    // --- STATIC FILES ---
    await serveStatic(u.pathname, res);
  } catch (e) {
    sendJson(res, 500, { error: String(e.message || e) });
  }
});

const port = Number(process.env.VOTIFY_PORT || process.env.PORT || 17217);

server.on('error', err => {
  if (err.code === 'EADDRINUSE') {
    console.log(`Port ${port} busy, retrying...`);
    setTimeout(() => {
      try {
        server.listen(port, '0.0.0.0');
      } catch (e) {
        // ignore port retry errors
      }
    }, 2000);
  } else {
    console.error('Server error:', err.message);
  }
});

server.listen(port, '0.0.0.0', () => {
  console.log(`Votify server running at http://0.0.0.0:${port}`);
  if (!process.env.VOTIFY_PORT) {
    try {
      const { exec } = require('child_process');
      const url = `http://127.0.0.1:${port}`;
      const isWindows = process.platform === 'win32';
      const isMac = process.platform === 'darwin';
      const cmd = isWindows ? `start ${url}` : isMac ? `open ${url}` : `xdg-open ${url}`;
      exec(cmd);
    } catch (e) {
      // ignore browser open errors
    }
  }
});

process.on('uncaughtException', err => {
  console.error('Uncaught:', err.message);
});
process.on('unhandledRejection', err => {
  console.error('Unhandled rejection:', err?.message || err);
});

module.exports = server;
