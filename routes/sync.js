const { sendJson, parseBody } = require('./utils.js');

async function handleSyncRoutes(req, res, u) {
  // --- SYNC GET ---
  if (u.pathname === '/api/sync/get' && req.method === 'GET') {
    sendJson(res, 200, {
      settings: {},
      playlists: { 'Избранное': [] }
    });
    return true;
  }

  // --- SYNC POST ---
  if (u.pathname === '/api/sync/push' && req.method === 'POST') {
    sendJson(res, 200, { message: 'Sync disabled (local mode)' });
    return true;
  }

  return false;
}

module.exports = { handleSyncRoutes };
