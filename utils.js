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
  httpProxy: '',
  invidiousInstance: 'https://inv.tux.rs',
};

function loadNetworkConfig() {
  try {
    if (fs.existsSync(NETWORK_FILE)) {
      const data = JSON.parse(fs.readFileSync(NETWORK_FILE, 'utf-8'));
      networkConfig = { ...networkConfig, ...data };
    }
  } catch (e) {
    /* ignore */
  }
}
loadNetworkConfig();

function saveNetworkConfig(config) {
  networkConfig = { ...networkConfig, ...config };
  fs.writeFileSync(NETWORK_FILE, JSON.stringify(networkConfig, null, 2), 'utf-8');
}

const SEARCH_LIMIT = 12;
const SEARCH_MAX_LIMIT = 100;
const RECOMMENDATION_LIMIT = 16;
const RECOMMENDATION_SEEDS = [
  'The Weeknd official audio',
  'Billie Eilish official audio',
  'Dua Lipa official audio',
  'Drake official audio',
  'Metro Boomin official audio',
  'Travis Scott official audio',
  'Olivia Rodrigo official audio',
  'Kendrick Lamar official audio',
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
          const m = html.match(/var ytInitialData\s*=\s*(\{.+?\});\s*<\/script>/s);
          if (m) {
            let data;
            try {
              data = JSON.parse(m[1]);
            } catch (e) {
              console.error(`[search] HTML parse error:`, e.message);
            }
            if (data) {
              const contents =
                data?.contents?.twoColumnSearchResultsRenderer?.primaryContents?.sectionListRenderer
                  ?.contents?.[0]?.itemSectionRenderer?.contents;
              tracks = extractYTTracksFromContents(contents, limit);
              console.log(`[search] HTML scrape found ${tracks.length} tracks`);
            }
          } else {
            console.warn(`[search] Could not find ytInitialData in HTML`);
          }
        }
      } catch (e) {
        console.error(`[search] HTML scrape error:`, e.message);
      }
    }
  }

  if (tracks.length) {
    searchCache.set(cacheKey, { tracks, expires: Date.now() + SEARCH_CACHE_TTL });
  } else {
    console.warn(`[search] No tracks found for: "${query}"`);
  }
  return tracks.slice(0, limit);
}

async function fetchStreamUrl(videoId) {
  const cached = streamCache.get(videoId);
  if (cached && cached.expires > Date.now()) return cached.url;

  if (networkConfig.streamSource === 'invidious') {
    try {
      const instance = networkConfig.invidiousInstance.replace(/\/$/, '');
      const data = await httpGet(`${instance}/api/v1/videos/${videoId}`, 10000);
      if (data && data.formatStreams && data.formatStreams.length > 0) {
        // Находим аудио поток или лучший доступный
        const stream =
          data.adaptiveFormats.find(f => f.type.includes('audio')) || data.formatStreams[0];
        if (stream && stream.url) {
          streamCache.set(videoId, { url: stream.url, expires: Date.now() + STREAM_CACHE_TTL });
          return stream.url;
        }
      }
    } catch (e) {
      console.log('Invidious error for', videoId, ':', e.message);
    }
  }

  try {
    const ytdlpPath = process.env.YT_DLP_PATH || findYtDlp();
    const args = [
      '--no-check-certificates',
      '--no-warnings',
      '--quiet',
      '-g',
      '-f',
      'ba',
      'https://www.youtube.com/watch?v=' + videoId,
    ];

    if (networkConfig.httpProxy) {
      args.push('--proxy', networkConfig.httpProxy);
    }

    const proc = spawn(ytdlpPath, args, { stdio: ['ignore', 'pipe', 'pipe'], windowsHide: true });
    const url = await new Promise((resolve, reject) => {
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
      proc.on('close', () => {
        if (out.trim()) resolve(out.trim().split('\n')[0]);
        else reject(new Error('no output'));
      });
      setTimeout(() => {
        proc.kill();
        reject(new Error('timeout'));
      }, 20000);
    });
    if (url) {
      streamCache.set(videoId, { url, expires: Date.now() + STREAM_CACHE_TTL });
      return url;
    }
  } catch (e) {
    console.log('yt-dlp error for', videoId, ':', e.message);
  }
  return null;
}

function proxyAudio(remoteUrl, req, res, depth = 0) {
  if (depth > 5) {
    sendJson(res, 502, { error: 'Too many redirects' });
    return;
  }
  const remote = new URL(remoteUrl);
  const transport = remote.protocol === 'https:' ? https : http;
  const headers = { 'User-Agent': req.headers['user-agent'] || 'Mozilla/5.0' };
  if (req.headers.range) headers.Range = req.headers.range;
  const upstream = transport.request(remote, { method: 'GET', headers }, upRes => {
    const sc = upRes.statusCode || 500;
    if ([301, 302, 303, 307, 308].includes(sc) && upRes.headers.location) {
      upRes.resume();
      proxyAudio(upRes.headers.location, req, res, depth + 1);
      return;
    }
    const rh = {
      'Content-Type': upRes.headers['content-type'] || 'audio/webm',
      'Cache-Control': 'no-store',
    };
    if (upRes.headers['content-length']) rh['Content-Length'] = upRes.headers['content-length'];
    if (upRes.headers['content-range']) rh['Content-Range'] = upRes.headers['content-range'];
    if (upRes.headers['accept-ranges']) rh['Accept-Ranges'] = upRes.headers['accept-ranges'];
    res.writeHead(sc, rh);
    upRes.pipe(res);
  });
  upstream.on('error', e => {
    if (!res.headersSent) sendJson(res, 502, { error: e.message });
    else res.destroy(e);
  });
  req.on('close', () => upstream.destroy());
  upstream.end();
}

async function searchTracks(query, limit, filter) {
  try {
    const fast = await ytSearchScrape(query, limit);
    return fast;
  } catch {
    return [];
  }
}

async function searchTracksByArtist(artistName, limit) {
  const safeName = String(artistName || '').trim();
  if (!safeName) return [];
  try {
    const fast = await ytSearchScrape(safeName, limit);
    return fast.slice(0, limit);
  } catch {
    return [];
  }
}

async function getRecommendations(limit) {
  const finalLimit = Number(limit) || RECOMMENDATION_LIMIT;
  const tracks = [];
  const seen = new Set();
  for (const seed of RECOMMENDATION_SEEDS) {
    const results = await searchTracks(seed, 5, true).catch(() => []);
    for (const t of results) {
      if (seen.has(t.id)) continue;
      seen.add(t.id);
      tracks.push(t);
      if (tracks.length >= finalLimit) return tracks;
    }
  }
  return tracks;
}

async function serveStatic(reqPath, res) {
  const norm = reqPath === '/' ? '/index.html' : reqPath;
  const fp = path.normalize(path.join(srcDir, norm));
  if (!fp.startsWith(srcDir)) {
    sendJson(res, 403, { error: 'Forbidden' });
    return;
  }
  try {
    const content = await fsPromises.readFile(fp);
    res.writeHead(200, {
      'Content-Type': MIME_TYPES[path.extname(fp).toLowerCase()] || 'application/octet-stream',
    });
    res.end(content);
  } catch {
    sendJson(res, 404, { error: 'Not found' });
  }
}

module.exports = {
  // constants
  SEARCH_LIMIT,
  SEARCH_MAX_LIMIT,
  RECOMMENDATION_LIMIT,
  RECOMMENDATION_SEEDS,
  BLOCKED_KEYWORDS,
  MIME_TYPES,
  SALT_ROUNDS,
  STREAM_CACHE_TTL,
  SEARCH_CACHE_TTL,
  YT_UA,
  JWT_SECRET,
  USERS_FILE,
  PERSISTENT_DIR,
  DATA_DIR,
  resetCodes,
  streamCache,
  searchCache,
  // functions
  sendJson,
  isBlockedTitle,
  generateToken,
  verifyToken,
  getAuthUser,
  parseBody,
  generateResetCode,
  createEmailTransporter,
  loadUsers,
  saveUsers,
  httpGet,
  httpPostJSON,
  extractVideoId,
  makeTrack,
  extractYTTracksFromContents,
  ytInnerTubeSearch,
  ytSearchScrape,
  fetchStreamUrl,
  proxyAudio,
  searchTracks,
  searchTracksByArtist,
  getRecommendations,
  serveStatic,
  saveNetworkConfig,
  networkConfig,
  appRoot,
  findYtDlp,
  bcrypt,
};
