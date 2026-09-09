const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const nodemailer = require('nodemailer');
const { spawn } = require('child_process');
const fs = require('fs');
const fsPromises = require('fs/promises');
const path = require('path');
const https = require('https');
const http = require('http');
const { URL } = require('url');
const crypto = require('crypto');

const appRoot = path.dirname(__dirname);
const srcDir = path.resolve(process.env.VOTIFY_SRC_DIR || path.join(appRoot, 'src'));
const port = Number(process.env.VOTIFY_PORT || process.env.PORT || 17217);

const os = require('os');
function getConfigDir() {
  if (process.platform === 'win32') {
    return path.join(
      process.env.APPDATA || path.join(os.homedir(), 'AppData', 'Roaming'),
      'Votify'
    );
  }
  if (process.platform === 'darwin') {
    return path.join(os.homedir(), 'Library', 'Application Support', 'Votify');
  }
  // Linux and other Unix-like: XDG_CONFIG_HOME or ~/.config
  return path.join(process.env.XDG_CONFIG_HOME || path.join(os.homedir(), '.config'), 'Votify');
}

const PERSISTENT_DIR = getConfigDir();
if (!fs.existsSync(PERSISTENT_DIR)) {
  fs.mkdirSync(PERSISTENT_DIR, { recursive: true });
}

function findYtDlp() {
  const isWindows = process.platform === 'win32';
  const binaryName = isWindows ? 'yt-dlp.exe' : 'yt-dlp';

  const candidates = [
    path.join(process.resourcesPath || '', 'app.asar.unpacked', 'bin', binaryName),
    path.join(__dirname, '..', 'bin', binaryName),
    path.join(appRoot, 'bin', binaryName),
    path.join(appRoot, binaryName),
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
  return path.join(process.resourcesPath || appRoot, 'app.asar.unpacked', 'bin', binaryName);
}

const JWT_SECRET = (() => {
  const secretFile = path.join(PERSISTENT_DIR, '.jwt-secret');
  try {
    const existing = fs.readFileSync(secretFile, 'utf-8').trim();
    if (existing) return existing;
  } catch (e) {
    // ignore secret file read errors
  }
  const secret = 'votify-' + crypto.randomBytes(32).toString('hex');
  fs.writeFileSync(secretFile, secret, 'utf-8');
  return secret;
})();
const USERS_FILE = path.join(PERSISTENT_DIR, 'users.json');
const NETWORK_FILE = path.join(PERSISTENT_DIR, 'network.json');
const DATA_DIR = path.join(PERSISTENT_DIR, 'data');

let networkConfig = {
  streamSource: 'yt-dlp',
  audioQuality: 'medium',
};

function loadNetworkConfig() {
  try {
    if (fs.existsSync(NETWORK_FILE)) {
      const data = JSON.parse(fs.readFileSync(NETWORK_FILE, 'utf-8'));
      networkConfig.audioQuality = ['low', 'medium', 'high'].includes(data.audioQuality)
        ? data.audioQuality
        : networkConfig.audioQuality;
    }
  } catch (e) {
    /* ignore */
  }
}
loadNetworkConfig();

function saveNetworkConfig(config) {
  if (config && ['low', 'medium', 'high'].includes(config.audioQuality)) {
    networkConfig.audioQuality = config.audioQuality;
  }
  networkConfig.streamSource = 'yt-dlp';
  fs.writeFileSync(NETWORK_FILE, JSON.stringify(networkConfig, null, 2), 'utf-8');
  return networkConfig;
}

// Expose the active yt-dlp engine together with the persisted quality setting.
function getNetworkConfig() {
  return networkConfig;
}

const SEARCH_LIMIT = 12;
const SEARCH_MAX_LIMIT = 100;
const RECOMMENDATION_LIMIT = 16;
const RECOMMENDATION_SEEDS = [
  'Фортуна official audio',
  'Miyagi official audio',
  'Big Baby Tape official audio',
  'Тима Белорусских official audio',
  'Artik & Asti official audio',
  'Jah Khalib official audio',
  'Pharaoh official audio',
  'Эндшпиль official audio',
  'Макс Корж official audio',
  'Баста official audio',
  'Мот official audio',
  'Noize MC official audio',
  'Скриптонит official audio',
  'Oxxxymiron official audio',
  'Грибы official audio',
  'Время и Стекло official audio',
  'Звонкий official audio',
  'HammAli & Navai official audio',
  'Руки Вверх official audio',
  'Клава Кока official audio',
];

const BLOCKED_KEYWORDS = [
  '1 hour',
  '2 hour',
  '3 hour',
  '10 hour',
  'hour loop',
  'hours',
  '1hr',
  '2hr',
  'megamix',
  'mega mix',
  'dj set',
  'playlist',
  'compilation',
  'non stop',
  'non-stop',
  'full album',
  'album completo',
  'live stream',
  '24/7',
];

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.webmanifest': 'application/manifest+json',
};

const SALT_ROUNDS = 10;
const resetCodes = new Map();

const streamCache = new Map();
const searchCache = new Map();
const STREAM_CACHE_TTL = 90 * 60 * 1000;
const SEARCH_CACHE_TTL = 10 * 60 * 1000;

const YT_UA = (() => {
  if (process.platform === 'win32') {
    return 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36';
  }
  if (process.platform === 'darwin') {
    return 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36';
  }
  // Linux
  return 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36';
})();

function sendJson(res, statusCode, payload) {
  res.writeHead(statusCode, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(payload));
}

function isBlockedTitle(title) {
  const lower = String(title || '').toLowerCase();
  return BLOCKED_KEYWORDS.some(k => lower.includes(k));
}

function generateToken(userId, email) {
  return jwt.sign({ userId, email }, JWT_SECRET, { expiresIn: '30d' });
}

function verifyToken(token) {
  try {
    return jwt.verify(token, JWT_SECRET);
  } catch {
    return null;
  }
}

function getAuthUser(req) {
  const auth = req.headers['authorization'];
  if (!auth || !auth.startsWith('Bearer ')) return null;
  return verifyToken(auth.slice(7));
}

function parseBody(req) {
  return new Promise((resolve, reject) => {
    let body = '';
    req.on('data', chunk => (body += chunk));
    req.on('end', () => {
      try {
        resolve(JSON.parse(body || '{}'));
      } catch {
        resolve({});
      }
    });
    req.on('error', reject);
  });
}

function generateResetCode() {
  return String(Math.floor(100000 + Math.random() * 900000));
}

function createEmailTransporter(config) {
  if (!config || !config.host) return null;
  return nodemailer.createTransport({
    host: config.host,
    port: config.port || 587,
    secure: config.port === 465,
    auth: { user: config.user, pass: config.pass },
  });
}

function loadUsers() {
  try {
    if (fs.existsSync(USERS_FILE)) {
      return JSON.parse(fs.readFileSync(USERS_FILE, 'utf-8'));
    }
  } catch (e) {
    // ignore users file read errors
  }
  return [];
}

function saveUsers(users) {
  fs.writeFileSync(USERS_FILE, JSON.stringify(users, null, 2), 'utf-8');
}

function httpGet(url, timeout) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('timeout')), timeout);
    const parsed = new URL(url);
    const transport = parsed.protocol === 'https:' ? https : http;
    const req = transport.get(
      url,
      {
        headers: {
          'User-Agent': YT_UA,
          'Accept-Language': 'en-US,en;q=0.9',
          Cookie:
            'CONSENT=PENDING+987; SOCS=CAESNQgDEitib3FfaWRlbnRpdHlmcm9udGVuZHVpc2VydmVyXzIwMjMwODI5LjA3X3AxGgJlbiACGgYIgJnRpwY',
        },
      },
      res => {
        if ([301, 302, 303, 307, 308].includes(res.statusCode) && res.headers.location) {
          clearTimeout(timer);
          const loc = res.headers.location.startsWith('http')
            ? res.headers.location
            : parsed.origin + res.headers.location;
          httpGet(loc, timeout).then(resolve).catch(reject);
          return;
        }
        let data = '';
        res.on('data', c => (data += c));
        res.on('end', () => {
          clearTimeout(timer);
          try {
            resolve(JSON.parse(data));
          } catch {
            resolve(data);
          }
        });
      }
    );
    req.on('error', e => {
      clearTimeout(timer);
      reject(e);
    });
  });
}

function httpPostJSON(url, body, timeout) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('timeout')), timeout);
    const parsed = new URL(url);
    const transport = parsed.protocol === 'https:' ? https : http;
    const payload = JSON.stringify(body);
    const req = transport.request(
      parsed,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Content-Length': Buffer.byteLength(payload),
          'User-Agent': YT_UA,
          Origin: 'https://music.youtube.com',
          Referer: 'https://music.youtube.com/',
        },
      },
      res => {
        let data = '';
        res.on('data', c => (data += c));
        res.on('end', () => {
          clearTimeout(timer);
          try {
            resolve(JSON.parse(data));
          } catch {
            resolve(data);
          }
        });
      }
    );
    req.on('error', e => {
      clearTimeout(timer);
      reject(e);
    });
    req.write(payload);
    req.end();
  });
}

function extractVideoId(raw) {
  if (!raw) return null;
  if (/^[a-zA-Z0-9_-]{11}$/.test(raw)) return raw;
  const m = raw.match(/[?&]v=([a-zA-Z0-9_-]{11})/);
  return m ? m[1] : null;
}

function makeTrack(id, title, artist, cover) {
  return {
    id,
    title: String(title || 'Unknown'),
    artist: String(artist || 'Unknown'),
    url: '/api/audio?id=' + encodeURIComponent(id),
    cover: cover || 'https://img.youtube.com/vi/' + id + '/hqdefault.jpg',
  };
}

function extractYTTracksFromContents(contents, limit) {
  const tracks = [],
    seen = new Set();
  if (!Array.isArray(contents)) return tracks;
  for (const item of contents) {
    if (tracks.length >= limit) break;
    const v = item.videoRenderer || item.playlistVideoRenderer;
    if (!v) continue;
    const id = v.videoId;
    if (!id || seen.has(id)) continue;
    seen.add(id);
    const title = v.title?.runs?.map(r => r.text).join('') || v.title?.simpleText || 'Unknown';
    if (isBlockedTitle(title)) continue;
    const artist =
      v.ownerText?.runs?.[0]?.text ||
      v.shortBylineText?.runs?.[0]?.text ||
      v.longBylineText?.runs?.[0]?.text ||
      'Unknown';
    const cover = v.thumbnail?.thumbnails?.pop()?.url;
    tracks.push(
      makeTrack(id, title, artist, cover && cover.startsWith('//') ? 'https:' + cover : cover)
    );
  }
  return tracks;
}

async function ytInnerTubeSearch(query, limit) {
  const body = {
    query: query + ' music',
    context: {
      client: {
        clientName: 'WEB',
        clientVersion: '2.20241126.01.00',
        hl: 'en',
        gl: 'US',
      },
    },
    params: 'EgIQAQ%3D%3D',
  };
  const data = await httpPostJSON(
    'https://www.youtube.com/youtubei/v1/search?prettyPrint=false',
    body,
    10000
  );
  if (!data || typeof data !== 'object') return [];
  const contents =
    data.contents?.twoColumnSearchResultsRenderer?.primaryContents?.sectionListRenderer
      ?.contents?.[0]?.itemSectionRenderer?.contents;
  return extractYTTracksFromContents(contents, limit);
}

async function ytSearchScrape(query, limit) {
  console.log(
    `[search] Searching for: "${query}" (limit: ${limit}) using ${networkConfig.streamSource}`
  );
  const cacheKey = query.toLowerCase().trim() + ':' + networkConfig.streamSource;
  const cached = searchCache.get(cacheKey);
  if (cached && cached.expires > Date.now()) {
    console.log(`[search] Cache hit for: "${query}"`);
    return cached.tracks.slice(0, limit);
  }

  let tracks = [];

  if (networkConfig.streamSource === 'soundcloud') {
    try {
      tracks = await scSearch(query, limit);
      console.log(`[search] SoundCloud found ${tracks.length} tracks`);
    } catch (e) {
      console.error(`[search] SoundCloud error:`, e.message);
    }
    searchCache.set(cacheKey, { tracks, expires: Date.now() + SEARCH_CACHE_TTL });
    return tracks.slice(0, limit);
  }

  if (networkConfig.streamSource === 'invidious') {
    try {
      const instance = networkConfig.invidiousInstance.replace(/\/$/, '');
      const data = await httpGet(
        `${instance}/api/v1/search?q=${encodeURIComponent(query + ' music')}&type=video`,
        10000
      );
      if (Array.isArray(data)) {
        tracks = data
          .slice(0, limit)
          .map(v =>
            makeTrack(
              v.videoId,
              v.title,
              v.author,
              v.videoThumbnails?.find(t => t.quality === 'high')?.url || v.videoThumbnails?.[0]?.url
            )
          );
        console.log(`[search] Invidious found ${tracks.length} tracks`);
      }
    } catch (e) {
      console.error(`[search] Invidious search error:`, e.message);
    }
    // Fallback to InnerTube/HTML when Invidious fails
    if (tracks.length < 3) {
      console.log(`[search] Invidious returned few results, falling back to InnerTube...`);
      try {
        tracks = await ytInnerTubeSearch(query, limit);
        console.log(`[search] InnerTube fallback found ${tracks.length} tracks`);
      } catch (e) {
        console.error(`[search] InnerTube fallback error:`, e.message);
      }
      if (tracks.length < 3) {
        console.log(`[search] Falling back to HTML scraping...`);
        try {
          const html = await httpGet(
            'https://www.youtube.com/results?search_query=' +
              encodeURIComponent(query + ' music') +
              '&sp=EgIQAQ%3D%3D',
            8000
          );
          if (typeof html === 'string') {
            let m = html.match(/var ytInitialData\s*=\s*(\{.+?\});\s*<\/script>/s);
            if (!m) {
              m = html.match(/ytInitialData\s*=\s*(\{.+?\});\s*<\/script>/s);
            }
            if (m) {
              let data;
              try {
                data = JSON.parse(m[1]);
              } catch (e) {
                console.error(`[search] HTML parse error:`, e.message);
                data = null;
              }
              if (data) {
                const contents =
                  data?.contents?.twoColumnSearchResultsRenderer?.primaryContents
                    ?.sectionListRenderer?.contents?.[0]?.itemSectionRenderer?.contents;
                if (Array.isArray(contents)) {
                  tracks = extractYTTracksFromContents(contents, limit);
                  console.log(`[search] HTML scrape fallback found ${tracks.length} tracks`);
                } else {
                  console.warn(`[search] No valid contents found in HTML data`);
                }
              }
            } else {
              console.warn(`[search] Could not find ytInitialData in HTML`);
              console.log(
                `[search] Available patterns:`,
                html.match(/ytInitialData/g)?.length || 0
              );
            }
          } else {
            console.warn(`[search] Invalid HTML response type:`, typeof html);
          }
        } catch (e) {
          console.error(`[search] HTML scrape fallback error:`, e.message);
        }
      }
    }
  } else {
    try {
      tracks = await ytInnerTubeSearch(query, limit);
      console.log(`[search] InnerTube found ${tracks.length} tracks`);
    } catch (e) {
      console.error(`[search] InnerTube error:`, e.message);
    }

    if (tracks.length < 3) {
      console.log(`[search] Falling back to HTML scraping...`);
      try {
        const html = await httpGet(
          'https://www.youtube.com/results?search_query=' +
            encodeURIComponent(query + ' music') +
            '&sp=EgIQAQ%3D%3D',
          8000
        );
        if (typeof html === 'string') {
          let m = html.match(/var ytInitialData\s*=\s*(\{.+?\});\s*<\/script>/s);
          if (!m) {
            m = html.match(/ytInitialData\s*=\s*(\{.+?\});\s*<\/script>/s);
          }
          if (m) {
            let data;
            try {
              data = JSON.parse(m[1]);
            } catch (e) {
              console.error(`[search] HTML parse error:`, e.message);
              data = null;
            }
            if (data) {
              const contents =
                data?.contents?.twoColumnSearchResultsRenderer?.primaryContents?.sectionListRenderer
                  ?.contents?.[0]?.itemSectionRenderer?.contents;
              if (Array.isArray(contents)) {
                tracks = extractYTTracksFromContents(contents, limit);
                console.log(`[search] HTML scrape fallback found ${tracks.length} tracks`);
              } else {
                console.warn(`[search] No valid contents found in HTML data`);
              }
            }
          } else {
            console.warn(`[search] Could not find ytInitialData in HTML`);
            console.log(`[search] Available patterns:`, html.match(/ytInitialData/g)?.length || 0);
          }
        } else {
          console.warn(`[search] Invalid HTML response type:`, typeof html);
        }
      } catch (e) {
        console.error(`[search] HTML scrape fallback error:`, e.message);
      }
    }
  }

  if (tracks.length < limit) {
    const subQueries = [`${query} song`, `${query} full`, `${query} audio`, `${query} official`];
    const existingIds = new Set(tracks.map(t => t.id));
    for (const sq of subQueries) {
      if (tracks.length >= limit) break;
      try {
        const more = await ytInnerTubeSearch(sq, limit);
        if (Array.isArray(more)) {
          for (const tr of more) {
            if (tr && tr.id && !existingIds.has(tr.id)) {
              tracks.push(tr);
              existingIds.add(tr.id);
              if (tracks.length >= limit) break;
            }
          }
        }
      } catch (e) {
        /* ignore fallback search errors */
      }
    }
  }

  searchCache.set(cacheKey, { tracks, expires: Date.now() + SEARCH_CACHE_TTL });
  return tracks.slice(0, limit);
}

async function searchTracks(query, limit, useCache = true) {
  const cacheKey = query.toLowerCase().trim() + ':' + networkConfig.streamSource;
  if (useCache) {
    const cached = searchCache.get(cacheKey);
    if (cached && cached.expires > Date.now() && cached.tracks.length >= limit) {
      console.log(`[search] Cache hit for: "${query}"`);
      return cached.tracks.slice(0, limit);
    }
  }
  const tracks = await ytSearchScrape(query, limit);
  // Офлайн-фолбэк: нет сети — показываем локальный демо-каталог
  if (!tracks.length) {
    const demo = require('./demo.js').searchDemo(query, limit);
    if (demo.length) {
      console.log(`[demo] Offline catalog fallback for: "${query}"`);
      return demo;
    }
  }
  if (useCache) {
    searchCache.set(cacheKey, { tracks, expires: Date.now() + SEARCH_CACHE_TTL });
  }
  return tracks.slice(0, limit);
}

async function searchTracksByArtist(name, limit) {
  const query = `${name} official audio`;
  const tracks = await searchTracks(query, limit, false);
  if (!tracks.length) {
    const demo = require('./demo.js').searchDemoByArtist(name, limit);
    if (demo.length) return demo;
  }
  return tracks;
}

async function getRecommendations(limit = RECOMMENDATION_LIMIT) {
  // This route is called during application start.  Previously each seed was
  // queried one after another; when YouTube was unavailable that kept the
  // startup request pending for several minutes.  A small parallel batch gives
  // a varied home page without making launch depend on a long network chain.
  const seedCount = Math.min(6, RECOMMENDATION_SEEDS.length);
  const seeds = RECOMMENDATION_SEEDS.slice(0, seedCount);
  const results = await Promise.allSettled(seeds.map(seed => searchTracks(seed, 3, false)));
  const allTracks = results.flatMap(result => (result.status === 'fulfilled' ? result.value : []));
  const seen = new Set();
  const unique = allTracks.filter(t => {
    if (seen.has(t.id)) return false;
    seen.add(t.id);
    return true;
  });
  if (!unique.length) {
    const demo = require('./demo.js').demoAll(limit);
    if (demo.length) {
      console.log('[demo] Offline recommendations fallback');
      return demo;
    }
  }
  return unique.slice(0, limit);
}

const AUDIO_QUALITY_FORMATS = {
  high: 'bestaudio[abr>=192]/bestaudio/best',
  medium: 'bestaudio[abr<=192]/bestaudio/best',
  low: 'worstaudio/bestaudio[abr<=96]/bestaudio/best',
};

async function fetchStreamUrl(videoId) {
  const quality = networkConfig.audioQuality || 'medium';
  const cacheKey = `${videoId}::${quality}`;
  const cached = streamCache.get(cacheKey);
  if (cached && cached.expires > Date.now()) {
    return cached.url;
  }

  // SoundCloud tracks
  if (String(videoId).startsWith('sc_')) {
    try {
      const url = await scGetStreamUrl(videoId);
      if (url) {
        streamCache.set(cacheKey, { url, expires: Date.now() + STREAM_CACHE_TTL });
        return url;
      }
    } catch (e) {
      console.log('[soundcloud] Stream error for', videoId, ':', e.message);
    }
    return null;
  }

  // Resolve YouTube streams with the bundled yt-dlp binary.
  const fetchYtDlp = async () => {
    const ytdlpPath = process.env.YT_DLP_PATH || findYtDlp();
    const ytdlpArgs = [
      '--no-check-certificates',
      '--no-warnings',
      '--no-playlist',
      '--quiet',
      '-g',
      '-f',
      AUDIO_QUALITY_FORMATS[quality] || 'ba/b',
      '--socket-timeout',
      '12',
      '--extractor-args',
      'youtube:player_client=android,web',
      '--user-agent',
      YT_UA,
    ];
    ytdlpArgs.push('https://www.youtube.com/watch?v=' + videoId);

    const proc = spawn(ytdlpPath, ytdlpArgs, { stdio: ['ignore', 'pipe', 'pipe'], windowsHide: true });
    return new Promise((resolve, reject) => {
      let out = '';
      proc.stdout.on('data', d => {
        out += d;
        const line = out.trim();
        if (line && line.startsWith('http')) {
          proc.kill();
          resolve(line.split('\n')[0]);
        }
      });
      proc.on('error', reject);
      proc.on('close', code => {
        if (out.trim()) resolve(out.trim().split('\n')[0]);
        else reject(new Error('exit ' + code));
      });
      setTimeout(() => {
        proc.kill();
        reject(new Error('timeout'));
      }, 20000);
    });
  };

  try {
    const url = await fetchYtDlp();
    if (url) {
      streamCache.set(cacheKey, { url, expires: Date.now() + STREAM_CACHE_TTL });
      return url;
    }
  } catch (error) {
    console.error('[yt-dlp] Stream resolution failed for', videoId, ':', error.message);
  }

  return null;
}

async function serveStatic(filePath, res) {
  if (!filePath || filePath === '/') filePath = '/index.html';
  if (filePath.startsWith('/')) filePath = filePath.slice(1);
  const fullPath = path.join(srcDir, filePath);
  if (!fullPath.startsWith(srcDir)) {
    sendJson(res, 403, { error: 'Forbidden' });
    return;
  }
  try {
    const stat = await fsPromises.stat(fullPath);
    if (stat.isDirectory()) {
      sendJson(res, 403, { error: 'Forbidden' });
      return;
    }
    const ext = path.extname(fullPath);
    const contentType = MIME_TYPES[ext] || 'application/octet-stream';
    const data = await fsPromises.readFile(fullPath);
    res.writeHead(200, {
      'Content-Type': contentType,
      'Cache-Control': 'no-store, no-cache, must-revalidate',
      Pragma: 'no-cache',
      Expires: '0',
    });
    res.end(data);
  } catch (e) {
    sendJson(res, 404, { error: 'Not found' });
  }
}

// --- SOUNDCLOUD ---
let scClientId = '';
let scClientIdExpiry = 0;

async function scGetClientId() {
  if (scClientId && Date.now() < scClientIdExpiry) return scClientId;
  try {
    const html = await httpGet('https://soundcloud.com', 10000);
    if (typeof html !== 'string') return '';
    // SoundCloud changes asset names and client_id syntax regularly. Search
    // several current bundles instead of relying on one exact filename.
    const scriptUrls = [...html.matchAll(/(?:src|href)="(https?:\/\/[^" ]+\.js[^" ]*)"/gi)].map(
      m => m[1]
    );
    const scripts = await Promise.all([
      Promise.resolve(html),
      ...scriptUrls.slice(-10).map(url => httpGet(url, 10000).catch(() => '')),
    ]);
    for (const script of scripts) {
      if (typeof script !== 'string') continue;
      const idMatch =
        script.match(/client_id["']?\s*[:=]\s*["']([a-zA-Z0-9_-]{16,})["']/) ||
        script.match(/client_id(?:=|%3D|["':])([a-zA-Z0-9_-]{16,})/i);
      if (idMatch) {
        scClientId = idMatch[1];
        break;
      }
    }
    if (!scClientId) return '';
    scClientIdExpiry = Date.now() + 3600000;
    console.log('[soundcloud] Got client_id:', scClientId.slice(0, 8) + '...');
    return scClientId;
  } catch (e) {
    console.error('[soundcloud] Failed to get client_id:', e.message);
    return '';
  }
}

async function scSearch(query, limit = 12) {
  const clientId = await scGetClientId();
  if (!clientId) return [];
  try {
    const url = `https://api-v2.soundcloud.com/search/tracks?q=${encodeURIComponent(query)}&limit=${limit}&client_id=${clientId}`;
    const data = await httpGet(url, 10000);
    if (!data || !data.collection) return [];
    return data.collection
      .filter(t => t.streamable && t.kind === 'track')
      .slice(0, limit)
      .map(t => {
        const id = 'sc_' + t.id;
        const artwork = (t.artwork_url || '').replace('-large', '-t500x500');
        return makeTrack(id, t.title, t.user?.username || 'Unknown', artwork);
      });
  } catch (e) {
    console.error('[soundcloud] Search error:', e.message);
    return [];
  }
}

async function scGetStreamUrl(trackId) {
  const numericId = String(trackId).replace('sc_', '');
  const clientId = await scGetClientId();
  if (!clientId) return null;
  try {
    const url = `https://api-v2.soundcloud.com/tracks/${numericId}/streams?client_id=${clientId}`;
    const data = await httpGet(url, 10000);
    if (data && data.http_mp3_128_url) return data.http_mp3_128_url;

    // The legacy /streams endpoint is no longer returned consistently.
    // Resolve an advertised transcoding endpoint and prefer progressive MP3;
    // an HLS manifest cannot be passed through our byte-stream proxy as audio.
    const track = await httpGet(
      `https://api-v2.soundcloud.com/tracks/${numericId}?client_id=${clientId}`,
      10000
    );
    const transcodings = track?.media?.transcodings || [];
    const progressive =
      transcodings.find(t => t?.format?.protocol === 'progressive') ||
      transcodings.find(t => t?.format?.mime_type?.includes('audio/mpeg'));
    if (progressive?.url) {
      const separator = progressive.url.includes('?') ? '&' : '?';
      const resolved = await httpGet(`${progressive.url}${separator}client_id=${clientId}`, 10000);
      if (resolved?.url) return resolved.url;
    }

    // Last resort for older tracks/accounts where only the legacy HLS URL is
    // available. Chromium may still handle it on supported platforms.
    if (data && data.hls_mp3_128_url) return data.hls_mp3_128_url;
    return null;
  } catch (e) {
    console.error('[soundcloud] Stream error:', e.message);
    return null;
  }
}

async function scImportPlaylist(playlistUrl) {
  const clientId = await scGetClientId();
  if (!clientId) return { error: 'No client_id' };
  try {
    // Resolve the URL to get playlist/user info
    const resolveUrl = `https://api-v2.soundcloud.com/resolve?url=${encodeURIComponent(playlistUrl)}&client_id=${clientId}`;
    const data = await httpGet(resolveUrl, 15000);
    if (!data) return { error: 'Not found' };

    let tracks = [];
    let name = 'SoundCloud Playlist';

    if (data.kind === 'playlist') {
      name = data.title || 'SoundCloud Playlist';
      tracks = (data.tracks || [])
        .filter(t => t && t.streamable)
        .map(t => {
          const artwork = (t.artwork_url || '').replace('-large', '-t500x500');
          return makeTrack('sc_' + t.id, t.title, t.user?.username || 'Unknown', artwork);
        });
    } else if (data.kind === 'user') {
      name = data.username || 'SoundCloud Likes';
      // Fetch user's tracks
      const tracksUrl = `https://api-v2.soundcloud.com/users/${data.id}/tracks?limit=50&client_id=${clientId}`;
      const tracksData = await httpGet(tracksUrl, 15000);
      if (tracksData && tracksData.collection) {
        tracks = tracksData.collection
          .filter(t => t.streamable)
          .map(t => {
            const artwork = (t.artwork_url || '').replace('-large', '-t500x500');
            return makeTrack('sc_' + t.id, t.title, t.user?.username || 'Unknown', artwork);
          });
      }
    }

    return { name, tracks };
  } catch (e) {
    console.error('[soundcloud] Import error:', e.message);
    return { error: e.message };
  }
}

module.exports = {
  sendJson,
  serveStatic,
  parseBody,
  saveNetworkConfig,
  getNetworkConfig,
  networkConfig,
  searchTracks,
  searchTracksByArtist,
  getRecommendations,
  fetchStreamUrl,
  streamCache,
  STREAM_CACHE_TTL,
  httpGet,
  httpPostJSON,
  appRoot,
  SEARCH_LIMIT,
  SEARCH_MAX_LIMIT,
  findYtDlp,
  loadNetworkConfig,
  PERSISTENT_DIR,
  scSearch,
  scGetStreamUrl,
  scImportPlaylist,
  YT_UA,
};
