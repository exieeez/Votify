// Offline downloads: save tracks to disk, play without network (like the phone).
const fs = require('fs');
const path = require('path');
const http = require('http');
const https = require('https');
const { sendJson, parseBody, fetchStreamUrl, PERSISTENT_DIR } = require('./utils.js');

const DL_DIR = path.join(PERSISTENT_DIR, 'downloads');
const INDEX_FILE = path.join(DL_DIR, 'downloads.json');
const MAX_BYTES = 100 * 1024 * 1024;

let index = null;
const jobs = new Map();

function ensureDir() {
  fs.mkdirSync(DL_DIR, { recursive: true });
}

function getIndex() {
  if (index) return index;
  try {
    const raw = JSON.parse(fs.readFileSync(INDEX_FILE, 'utf8'));
    index = raw && typeof raw === 'object' ? raw : {};
  } catch {
    index = {};
  }
  return index;
}

function saveIndex() {
  try {
    ensureDir();
    fs.writeFileSync(INDEX_FILE, JSON.stringify(index));
  } catch (e) {
    console.error('[offline] index save failed:', e.message);
  }
}

function safeId(id) {
  const s = String(id || '');
  return /^[A-Za-z0-9_-]{4,64}$/.test(s) ? s : null;
}

function audioExt(mime) {
  const m = String(mime || '')
    .split(';')[0]
    .trim()
    .toLowerCase();
  if (m === 'audio/webm') return '.webm';
  if (m === 'audio/mp4' || m === 'audio/x-m4a' || m === 'audio/aac') return '.m4a';
  if (m === 'audio/mpeg') return '.mp3';
  if (m === 'audio/ogg' || m === 'audio/opus') return '.ogg';
  if (m === 'audio/flac' || m === 'audio/x-flac') return '.flac';
  if (m.startsWith('audio/')) return '.audio';
  return null;
}

function imageExt(mime) {
  const m = String(mime || '')
    .split(';')[0]
    .trim()
    .toLowerCase();
  if (m === 'image/jpeg') return '.jpg';
  if (m === 'image/png') return '.png';
  if (m === 'image/webp') return '.webp';
  if (m === 'image/gif') return '.gif';
  return '.jpg';
}

function fetchToFile(url, file, onProgress, redirects = 0) {
  return new Promise((resolve, reject) => {
    if (redirects > 5) return reject(new Error('too many redirects'));
    const transport = String(url).startsWith('https:') ? https : http;
    const req = transport.get(
      url,
      { headers: { 'User-Agent': 'Votify/1.0' } },
      res => {
        if ([301, 302, 303, 307, 308].includes(res.statusCode) && res.headers.location) {
          res.resume();
          const loc = /^https?:\/\//.test(res.headers.location)
            ? res.headers.location
            : new URL(res.headers.location, url).toString();
          fetchToFile(loc, file, onProgress, redirects + 1).then(resolve, reject);
          return;
        }
        if (res.statusCode !== 200) {
          res.resume();
          reject(new Error('http ' + res.statusCode));
          return;
        }
        const total = Number(res.headers['content-length']) || 0;
        const mime = String(res.headers['content-type'] || '')
          .split(';')[0]
          .trim();
        try {
          ensureDir();
        } catch (e) {
          reject(e);
          return;
        }
        const tmp = file + '.part';
        const out = fs.createWriteStream(tmp);
        let received = 0;
        let failed = false;
        const fail = e => {
          if (failed) return;
          failed = true;
          try {
            req.destroy();
          } catch {}
          out.destroy();
          try {
            fs.unlinkSync(tmp);
          } catch {}
          reject(e);
        };
        res.on('data', c => {
          received += c.length;
          if (received > MAX_BYTES) fail(new Error('file too big'));
          else if (onProgress) {
            try {
              onProgress(received, total);
            } catch {}
          }
        });
        res.on('error', fail);
        out.on('error', fail);
        out.on('finish', () => {
          if (failed) return;
          try {
            fs.renameSync(tmp, file);
          } catch (e) {
            fail(e);
            return;
          }
          resolve({ mime, bytes: received });
        });
        res.pipe(out);
      }
    );
    req.on('error', e => reject(e));
    req.setTimeout(60000, () => {
      try {
        req.destroy(new Error('timeout'));
      } catch {}
    });
  });
}

async function startDownload(id, meta) {
  if (jobs.has(id)) return;
  const job = { state: 'downloading', received: 0, total: 0, error: null };
  jobs.set(id, job);
  try {
    const streamUrl = await fetchStreamUrl(id);
    if (!streamUrl) throw new Error('no stream');
    const tmpAudio = path.join(DL_DIR, id + '.dl');
    const { mime, bytes } = await fetchToFile(streamUrl, tmpAudio, (r, t) => {
      job.received = r;
      job.total = t;
    });
    const ext = audioExt(mime);
    if (!ext) {
      try {
        fs.unlinkSync(tmpAudio);
      } catch {}
      throw new Error('not audio: ' + (mime || '?'));
    }
    const audioFile = path.join(DL_DIR, id + ext);
    fs.renameSync(tmpAudio, audioFile);
    let coverFile = '';
    if (meta.cover && /^https?:\/\//.test(meta.cover)) {
      try {
        const tmpCover = path.join(DL_DIR, id + '.cover.dl');
        const cr = await fetchToFile(meta.cover, tmpCover, null);
        coverFile = id + '.cover' + imageExt(cr.mime);
        fs.renameSync(tmpCover, path.join(DL_DIR, coverFile));
      } catch {
        coverFile = '';
      }
    }
    const idx = getIndex();
    idx[id] = {
      id,
      title: String(meta.title || id).slice(0, 200),
      artist: String(meta.artist || '').slice(0, 200),
      duration: Number(meta.duration) || 0,
      file: path.basename(audioFile),
      coverFile,
      mime,
      size: bytes,
      at: Date.now(),
    };
    saveIndex();
    job.state = 'done';
    console.log(`[offline] downloaded ${id} (${(bytes / 1048576).toFixed(1)} MB)`);
  } catch (e) {
    job.state = 'error';
    job.error = e.message;
    console.log('[offline] download failed for', id, '-', e.message);
    ['.dl', '.dl.part'].forEach(suf => {
      try {
        fs.unlinkSync(path.join(DL_DIR, id + suf));
      } catch {}
    });
  }
}

function fileInDir(name) {
  const base = path.basename(String(name || ''));
  if (!base || base === '.' || base === '..') return null;
  const full = path.join(DL_DIR, base);
  if (!full.startsWith(DL_DIR + path.sep)) return null;
  return full;
}

function serveFile(req, res, file, mime) {
  let stat;
  try {
    stat = fs.statSync(file);
    if (!stat.isFile()) throw new Error('no');
  } catch {
    sendJson(res, 404, { error: 'not found' });
    return;
  }
  const total = stat.size;
  const range = req.headers.range;
  if (range) {
    const m = String(range).match(/bytes=(\d*)-(\d*)/);
    const start = m && m[1] ? Number(m[1]) : 0;
    const end = m && m[2] ? Number(m[2]) : total - 1;
    if (
      !m ||
      Number.isNaN(start) ||
      Number.isNaN(end) ||
      start >= total ||
      end >= total ||
      start > end
    ) {
      res.writeHead(416, { 'Content-Range': `bytes */${total}` });
      res.end();
      return;
    }
    res.writeHead(206, {
      'Content-Type': mime,
      'Content-Length': end - start + 1,
      'Content-Range': `bytes ${start}-${end}/${total}`,
      'Accept-Ranges': 'bytes',
      'Cache-Control': 'public, max-age=31536000',
    });
    fs.createReadStream(file, { start, end }).pipe(res);
    return;
  }
  res.writeHead(200, {
    'Content-Type': mime,
    'Content-Length': total,
    'Accept-Ranges': 'bytes',
    'Cache-Control': 'public, max-age=31536000',
  });
  fs.createReadStream(file).pipe(res);
}

function coverMime(name) {
  const ext = path.extname(String(name || '')).toLowerCase();
  if (ext === '.png') return 'image/png';
  if (ext === '.webp') return 'image/webp';
  if (ext === '.gif') return 'image/gif';
  return 'image/jpeg';
}

async function handleOfflineRoutes(req, res, u) {
  // --- LIST ---
  if (u.pathname === '/api/offline/list' && req.method === 'GET') {
    const tracks = Object.values(getIndex()).sort((a, b) => (b.at || 0) - (a.at || 0));
    sendJson(res, 200, { tracks });
    return true;
  }

  // --- DOWNLOAD (start job, returns immediately) ---
  if (u.pathname === '/api/offline/download' && req.method === 'POST') {
    const body = await parseBody(req).catch(() => ({}));
    const src = body && typeof body === 'object' ? body : {};
    const id = safeId(src.id || u.searchParams.get('id'));
    if (!id) {
      sendJson(res, 400, { error: 'bad id' });
      return true;
    }
    if (getIndex()[id]) {
      sendJson(res, 200, { state: 'done' });
      return true;
    }
    if (!jobs.has(id)) {
      startDownload(id, {
        title: src.title || u.searchParams.get('title') || '',
        artist: src.artist || u.searchParams.get('artist') || '',
        cover: src.cover || u.searchParams.get('cover') || '',
        duration: src.duration || u.searchParams.get('duration') || 0,
      });
    }
    sendJson(res, 200, { started: true });
    return true;
  }

  // --- PROGRESS ---
  if (u.pathname === '/api/offline/progress' && req.method === 'GET') {
    const id = safeId(u.searchParams.get('id'));
    if (!id) {
      sendJson(res, 400, { error: 'bad id' });
      return true;
    }
    if (getIndex()[id]) {
      sendJson(res, 200, { state: 'done' });
      return true;
    }
    const job = jobs.get(id);
    if (!job) {
      sendJson(res, 200, { state: 'missing' });
      return true;
    }
    sendJson(res, 200, {
      state: job.state,
      received: job.received,
      total: job.total,
      error: job.error,
    });
    return true;
  }

  // --- DELETE ---
  if (u.pathname === '/api/offline' && req.method === 'DELETE') {
    const id = safeId(u.searchParams.get('id'));
    if (!id) {
      sendJson(res, 400, { error: 'bad id' });
      return true;
    }
    const idx = getIndex();
    const entry = idx[id];
    if (entry) {
      [entry.file, entry.coverFile].forEach(f => {
        if (!f) return;
        const full = fileInDir(f);
        if (full) {
          try {
            fs.unlinkSync(full);
          } catch {}
        }
      });
      delete idx[id];
      saveIndex();
    }
    jobs.delete(id);
    sendJson(res, 200, { deleted: true });
    return true;
  }

  // --- AUDIO FILE (range-capable) ---
  if (u.pathname.startsWith('/api/offline/audio/') && req.method === 'GET') {
    const id = safeId(u.pathname.slice('/api/offline/audio/'.length));
    const entry = id && getIndex()[id];
    if (!entry || !entry.file) {
      sendJson(res, 404, { error: 'not downloaded' });
      return true;
    }
    const full = fileInDir(entry.file);
    if (!full) {
      sendJson(res, 404, { error: 'not found' });
      return true;
    }
    serveFile(req, res, full, entry.mime || 'audio/mpeg');
    return true;
  }

  // --- COVER FILE ---
  if (u.pathname.startsWith('/api/offline/cover/') && req.method === 'GET') {
    const id = safeId(u.pathname.slice('/api/offline/cover/'.length));
    const entry = id && getIndex()[id];
    if (!entry || !entry.coverFile) {
      sendJson(res, 404, { error: 'no cover' });
      return true;
    }
    const full = fileInDir(entry.coverFile);
    if (!full) {
      sendJson(res, 404, { error: 'not found' });
      return true;
    }
    serveFile(req, res, full, coverMime(entry.coverFile));
    return true;
  }

  return false;
}

module.exports = { handleOfflineRoutes };
