const { sendJson, parseBody, getAuthUser, PERSISTENT_DIR } = require('./utils.js');
const fs = require('fs');
const path = require('path');

// Per-user settings sync: the mobile app pushes/pulls its customization blob
// (Пресеты, темы, свайпы, фоны…). Files live in PERSISTENT_DIR/userdata/<userId>.json.
function userFile(userId) {
  const dir = path.join(PERSISTENT_DIR, 'userdata');
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
  return path.join(dir, userId + '.json');
}

async function handleSyncRoutes(req, res, u) {
  // --- SYNC GET: the user's saved settings blob ---
  if (u.pathname === '/api/sync/get' && req.method === 'GET') {
    const auth = getAuthUser(req);
    if (!auth) {
      sendJson(res, 401, { error: 'Not authenticated' });
      return true;
    }
    const file = userFile(auth.userId);
    const data = fs.existsSync(file)
      ? JSON.parse(fs.readFileSync(file, 'utf8'))
      : {};
    sendJson(res, 200, { settings: data.settings || '', savedAt: data.savedAt || 0 });
    return true;
  }

  // --- SYNC POST: overwrite the user's settings blob ---
  if (u.pathname === '/api/sync/push' && req.method === 'POST') {
    const auth = getAuthUser(req);
    if (!auth) {
      sendJson(res, 401, { error: 'Not authenticated' });
      return true;
    }
    const body = await parseBody(req);
    if (typeof body.settings !== 'string' || body.settings.length > 256 * 1024) {
      sendJson(res, 400, { error: 'settings (string, <=256KB) required' });
      return true;
    }
    const file = userFile(auth.userId);
    fs.writeFileSync(file, JSON.stringify({ settings: body.settings, savedAt: Date.now() }));
    sendJson(res, 200, { ok: true, savedAt: Date.now() });
    return true;
  }

  return false;
}

module.exports = { handleSyncRoutes };
