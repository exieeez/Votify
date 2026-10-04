const fs = require('fs');
const path = require('path');
const { sendJson, parseBody, PERSISTENT_DIR } = require('./utils.js');

const PLAYLISTS_FILE = path.join(PERSISTENT_DIR, 'public_playlists.json');

// Default initial curated public playlists so that search immediately has community content
const DEFAULT_PUBLIC_PLAYLISTS = [
  {
    id: 'pub-top-50',
    title: 'Votify Топ 50',
    author: 'Votify',
    cover: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=400&q=80',
    description: 'Главные хиты и популярные треки недели',
    createdAt: Date.now() - 86400000 * 5,
    updatedAt: Date.now(),
    tracks: [
      { id: 't-1', title: 'Starboy', artist: 'The Weeknd, Daft Punk', duration: 230, cover: 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=300' },
      { id: 't-2', title: 'Blinding Lights', artist: 'The Weeknd', duration: 200, cover: 'https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=300' },
      { id: 't-3', title: 'After Hours', artist: 'The Weeknd', duration: 361, cover: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=300' },
      { id: 't-4', title: 'Save Your Tears', artist: 'The Weeknd', duration: 215, cover: 'https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=300' },
      { id: 't-5', title: 'Die For You', artist: 'The Weeknd', duration: 260, cover: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=300' }
    ]
  },
  {
    id: 'pub-chill-lofi',
    title: 'Вечерний Chill & Lo-Fi',
    author: 'Lo-Fi Lounge',
    cover: 'https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=400&q=80',
    description: 'Спокойная музыка для работы, учебы и отдыха',
    createdAt: Date.now() - 86400000 * 10,
    updatedAt: Date.now(),
    tracks: [
      { id: 'c-1', title: 'Midnight City Beats', artist: 'Chillhop Music', duration: 154, cover: 'https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=300' },
      { id: 'c-2', title: 'Coffee and Rain', artist: 'Lofi Fruits Music', duration: 132, cover: 'https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=300' },
      { id: 'c-3', title: 'Late Night Walks', artist: 'Kupla', duration: 178, cover: 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=300' },
      { id: 'c-4', title: 'Warm Breeze', artist: 'Kudasai', duration: 145, cover: 'https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=300' }
    ]
  },
  {
    id: 'pub-russian-rock',
    title: 'Русский Рок Легенды',
    author: 'Rock Community',
    cover: 'https://images.unsplash.com/photo-1498038432885-c6f3f1b912ee?w=400&q=80',
    description: 'Лучшие песни русского рока всех времен',
    createdAt: Date.now() - 86400000 * 15,
    updatedAt: Date.now(),
    tracks: [
      { id: 'r-1', title: 'Группа крови', artist: 'Кино', duration: 285, cover: 'https://images.unsplash.com/photo-1498038432885-c6f3f1b912ee?w=300' },
      { id: 'r-2', title: 'Звезда по имени Солнце', artist: 'Кино', duration: 225, cover: 'https://images.unsplash.com/photo-1498038432885-c6f3f1b912ee?w=300' },
      { id: 'r-3', title: 'Пачка сигарет', artist: 'Кино', duration: 268, cover: 'https://images.unsplash.com/photo-1498038432885-c6f3f1b912ee?w=300' },
      { id: 'r-4', title: 'Кукушка', artist: 'Кино', duration: 399, cover: 'https://images.unsplash.com/photo-1498038432885-c6f3f1b912ee?w=300' },
      { id: 'r-5', title: 'Спокойная ночь', artist: 'Кино', duration: 367, cover: 'https://images.unsplash.com/photo-1498038432885-c6f3f1b912ee?w=300' }
    ]
  }
];

function loadPublicPlaylists() {
  try {
    if (fs.existsSync(PLAYLISTS_FILE)) {
      const data = JSON.parse(fs.readFileSync(PLAYLISTS_FILE, 'utf8'));
      if (Array.isArray(data) && data.length > 0) return data;
    }
  } catch (e) {
    console.error('[playlists] Error reading public playlists:', e.message);
  }
  try {
    fs.writeFileSync(PLAYLISTS_FILE, JSON.stringify(DEFAULT_PUBLIC_PLAYLISTS, null, 2), 'utf8');
  } catch (e) {}
  return [...DEFAULT_PUBLIC_PLAYLISTS];
}

function savePublicPlaylists(playlists) {
  try {
    fs.writeFileSync(PLAYLISTS_FILE, JSON.stringify(playlists, null, 2), 'utf8');
  } catch (e) {
    console.error('[playlists] Error saving public playlists:', e.message);
  }
}

async function handlePlaylistRoutes(req, res, u) {
  // 1. GET /api/playlists: Search or list public playlists
  if (u.pathname === '/api/playlists' && req.method === 'GET') {
    const q = (u.searchParams.get('q') || '').trim().toLowerCase();
    const limit = Math.min(Number(u.searchParams.get('limit')) || 50, 100);
    const all = loadPublicPlaylists();

    let matched = all;
    if (q) {
      matched = all.filter(p => {
        const titleMatch = (p.title || p.name || '').toLowerCase().includes(q);
        const authorMatch = (p.author || '').toLowerCase().includes(q);
        const descMatch = (p.description || '').toLowerCase().includes(q);
        const trackMatch = Array.isArray(p.tracks) && p.tracks.some(t => {
          const tName = (t.title || t.name || t.t || '').toLowerCase();
          const tArtist = (t.artist || t.a || '').toLowerCase();
          return tName.includes(q) || tArtist.includes(q);
        });
        return titleMatch || authorMatch || descMatch || trackMatch;
      });
    }

    sendJson(res, 200, {
      ok: true,
      total: matched.length,
      playlists: matched.slice(0, limit)
    });
    return true;
  }

  // 2. GET /api/playlists/get: Fetch single playlist by ID or title
  if (u.pathname === '/api/playlists/get' && req.method === 'GET') {
    const id = u.searchParams.get('id');
    const title = u.searchParams.get('title') || u.searchParams.get('name');
    const all = loadPublicPlaylists();

    const found = all.find(p => (id && p.id === id) || (title && (p.title === title || p.name === title)));
    if (!found) {
      sendJson(res, 404, { ok: false, error: 'Playlist not found' });
      return true;
    }

    sendJson(res, 200, { ok: true, playlist: found });
    return true;
  }

  // 3. POST /api/playlists/publish: Publish or update playlists
  if (u.pathname === '/api/playlists/publish' && req.method === 'POST') {
    const body = await parseBody(req);
    const incoming = Array.isArray(body?.playlists)
      ? body.playlists
      : body?.playlist
        ? [body.playlist]
        : [];

    if (!incoming.length) {
      sendJson(res, 400, { ok: false, error: 'playlists array or playlist object required' });
      return true;
    }

    const all = loadPublicPlaylists();
    let updatedCount = 0;
    const now = Date.now();

    for (const item of incoming) {
      const title = (item.title || item.name || '').trim();
      if (!title) continue;

      const rawTracks = Array.isArray(item.tracks) ? item.tracks : [];
      const tracks = rawTracks.map((t, idx) => ({
        id: String(t.id || `pl-track-${idx}-${Date.now()}`),
        title: String(t.title || t.name || t.t || 'Без названия'),
        artist: String(t.artist || t.artistName || t.a || 'Неизвестный исполнитель'),
        cover: String(t.cover || t.thumbnail || t.c || ''),
        duration: Number(t.duration || t.d || 180),
        album: String(t.album || '')
      }));

      const author = String(item.author || item.ownerName || 'Пользователь Votify').trim();
      const cover = String(item.cover || (tracks[0] && tracks[0].cover) || '');
      const description = String(item.description || '').trim();
      const existingIdx = all.findIndex(p =>
        (item.id && p.id === item.id) ||
        ((p.title || p.name || '').toLowerCase() === title.toLowerCase() && (p.author || '').toLowerCase() === author.toLowerCase())
      );

      const playlistRecord = {
        id: item.id || `pub-pl-${Date.now()}-${Math.random().toString(36).substring(2, 7)}`,
        title,
        author,
        cover,
        description,
        trackCount: tracks.length,
        tracks,
        createdAt: existingIdx >= 0 ? (all[existingIdx].createdAt || now) : now,
        updatedAt: now
      };

      if (existingIdx >= 0) {
        all[existingIdx] = playlistRecord;
      } else {
        all.unshift(playlistRecord);
      }
      updatedCount++;
    }

    savePublicPlaylists(all);
    sendJson(res, 200, { ok: true, publishedCount: updatedCount, totalPlaylists: all.length });
    return true;
  }

  return false;
}

module.exports = {
  handlePlaylistRoutes,
  loadPublicPlaylists,
  savePublicPlaylists
};
