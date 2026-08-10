const http = require('http');
const {
  sendJson,
  serveStatic,
  parseBody,
  saveNetworkConfig,
  getNetworkConfig,
} = require('./routes/utils.js');
const { handleAuthRoutes } = require('./routes/auth.js');
const { handleMusicRoutes } = require('./routes/music.js');
const { handleDiscordRoutes } = require('./routes/discord.js');
const { handleSmtpRoutes } = require('./routes/smtp.js');
const { handleSyncRoutes } = require('./routes/sync.js');

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

    // --- DISCORD ENDPOINTS ---
    if (await handleDiscordRoutes(req, res, u)) return;

    // --- SMTP CONFIG ---
    if (await handleSmtpRoutes(req, res, u)) return;

    // --- MUSIC ENDPOINTS ---
    if (await handleMusicRoutes(req, res, u)) return;

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
