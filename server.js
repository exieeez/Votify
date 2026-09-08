const http = require('http');
const fs = require('fs');
const path = require('path');
const { sendJson, parseBody, saveNetworkConfig, getNetworkConfig } = require('./routes/utils.js');
const { handleAuthRoutes } = require('./routes/auth.js');
const { handleMusicRoutes } = require('./routes/music.js');
const { handleSmtpRoutes } = require('./routes/smtp.js');
const { handleSyncRoutes } = require('./routes/sync.js');

const pkg = require('./package.json');

const FIREBASE_CONFIG_FIELDS = [
  'apiKey',
  'authDomain',
  'projectId',
  'storageBucket',
  'messagingSenderId',
  'appId',
];

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
    // --- HEALTH / INFO ---
    if (u.pathname === '/' || u.pathname === '/api/health') {
      sendJson(res, 200, {
        ok: true,
        name: pkg.name,
        version: pkg.version,
        uptime: process.uptime(),
      });
      return;
    }

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

    // API-only server: no static UI is served from here.
    sendJson(res, 404, { error: 'Not found' });
  } catch (e) {
    sendJson(res, 500, { error: String(e.message || e) });
  }
});

const port = Number(process.env.VOTIFY_PORT || process.env.PORT || 17217);
const host = process.env.VOTIFY_HOST || '0.0.0.0';

server.on('error', err => {
  if (err.code === 'EADDRINUSE') {
    console.log(`Port ${port} busy, retrying...`);
    setTimeout(() => {
      try {
        server.listen(port, host);
      } catch (e) {
        // ignore port retry errors
      }
    }, 2000);
  } else {
    console.error('Server error:', err.message);
  }
});

if (require.main === module) {
  server.listen(port, host, () => {
    console.log(`Votify API server running at http://${host}:${port}`);
  });
}

process.on('uncaughtException', err => {
  console.error('Uncaught:', err.message);
});
process.on('unhandledRejection', err => {
  console.error('Unhandled rejection:', err?.message || err);
});

module.exports = server;
