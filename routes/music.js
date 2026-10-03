const {
  sendJson,
  searchTracks,
  searchTracksByArtist,
  getRecommendations,
  getChartTracks,
  fetchStreamUrl,
  streamCache,
  STREAM_CACHE_TTL,
  httpGet,
  httpPostJSON,
  appRoot,
  SEARCH_LIMIT,
  SEARCH_MAX_LIMIT,
  findYtDlp,
  scImportPlaylist,
  YT_UA,
} = require('./utils.js');

const { spawn } = require('child_process');
const path = require('path');
const https = require('https');
const http = require('http');
const { URL } = require('url');

async function handleMusicRoutes(req, res, u) {
  // --- SEARCH ---
  if (u.pathname === '/api/search') {
    const q = u.searchParams.get('q')?.trim();
    if (!q) {
      sendJson(res, 400, { error: 'Empty query' });
      return true;
    }
    const limit = Math.min(Number(u.searchParams.get('limit')) || SEARCH_LIMIT, SEARCH_MAX_LIMIT);
    sendJson(res, 200, { tracks: await searchTracks(q, limit, true) });
    return true;
  }

  // --- ARTIST ---
  if (u.pathname === '/api/artist') {
    const name = u.searchParams.get('name')?.trim();
    if (!name) {
      sendJson(res, 400, { error: 'Artist name required' });
      return true;
    }
    const limit = Math.min(Math.max(Number(u.searchParams.get('limit')) || 50, 1), 100);
    const tracks = await searchTracksByArtist(name, limit);
    sendJson(res, 200, { artist: name, tracks });
    return true;
  }

  // --- RECOMMENDATIONS ---
  if (u.pathname === '/api/recommendations') {
    const limit = u.searchParams.get('limit');
    const t = await getRecommendations(limit);
    if (!t.length) {
      sendJson(res, 500, { error: 'No recommendations' });
      return true;
    }
    sendJson(res, 200, { tracks: t });
    return true;
  }

  // --- CHARTS (живой чарт Apple Music: что слушают прямо сейчас) ---
  if (u.pathname === '/api/charts') {
    const region = (u.searchParams.get('region') || 'ru').toLowerCase();
    const limit = Math.min(Math.max(Number(u.searchParams.get('limit')) || 30, 1), 50);
    try {
      const chart = await getChartTracks(region, limit);
      if (chart.length) {
        sendJson(res, 200, { tracks: chart });
        return true;
      }
    } catch (e) {
      console.error('[charts] не удалось получить чарт:', e.message);
    }
    // Чарт недоступен — отдаём обычные рекомендации, чтобы экран не пустовал.
    sendJson(res, 200, { tracks: await getRecommendations(limit) });
    return true;
  }

  // --- CUSTOM WAVE (seeds from playlists + recent) ---
  if (u.pathname === '/api/custom-wave') {
    const seedsParam = u.searchParams.get('seeds') || '';
    const trackSeedsParam = u.searchParams.get('trackSeeds') || '';
    const excludeParam = u.searchParams.get('exclude') || '';
    const limit = Math.min(Number(u.searchParams.get('limit')) || 20, 40);
    const seeds = seedsParam.split('|').filter(Boolean).slice(0, 8);
    const trackSeeds = trackSeedsParam.split('|').filter(Boolean).slice(0, 6);
    const excludeIds = new Set(excludeParam.split(',').filter(Boolean));
    if (!seeds.length && !trackSeeds.length) {
      sendJson(res, 400, { error: 'No seeds' });
      return true;
    }
    const totalSeeds = seeds.length + trackSeeds.length || 1;
    const perSeedLimit = Math.ceil(limit / totalSeeds) + 2;
    const results = await Promise.allSettled([
      // Plain artist seeds: bias toward that artist's actual songs
      ...seeds.map(seed => searchTracksByArtist(seed, perSeedLimit)),
      // Exact "artist title" seeds: keeps the wave anchored to songs you actually have
      ...trackSeeds.map(seed => searchTracks(seed, perSeedLimit, false)),
    ]);
    const allTracks = results.flatMap(r => (r.status === 'fulfilled' ? r.value : []));
    const normTrackKey = t => {
      const a = String(t.artist || '').toLowerCase().replace(/[^\p{L}\p{N}]/gu, '');
      const tit = String(t.title || '').toLowerCase().replace(/[^\p{L}\p{N}]/gu, '');
      return `${a}::${tit}`;
    };
    const seenIds = new Set();
    const seenNames = new Set();
    const unique = allTracks.filter(t => {
      if (!t || !t.id) return false;
      if (excludeIds.has(t.id)) return false;
      if (seenIds.has(t.id)) return false;
      const key = normTrackKey(t);
      if (key.length > 5 && seenNames.has(key)) return false;
      seenIds.add(t.id);
      if (key.length > 5) seenNames.add(key);
      return true;
    });
    // Ранжируем по похожести: совпадение артиста весит больше всего, потом —
    // совпадение слов в названии. Раньше список просто перемешивался, поэтому
    // в волну попадало что угодно, только не похожее на то, что вы слушаете.
    const norm = s =>
      String(s || '')
        .toLowerCase()
        .replace(/[^\p{L}\p{N}& ]/gu, ' ')
        .replace(/\s+/g, ' ')
        .trim();
    const words = s => norm(s).split(' ').filter(w => w.length >= 3);
    const seedArtists = [...seeds.map(norm), ...trackSeeds.map(s => norm(s.split(' ').slice(0, 2).join(' ')))].filter(
      Boolean,
    );
    const seedWords = new Set(trackSeeds.flatMap(words));
    const scored = unique.map(t => {
      const artist = norm(t.artist);
      let score = 0;
      if (seedArtists.some(a => artist === a)) score += 6;
      else if (seedArtists.some(a => a && (artist.includes(a) || a.includes(artist)))) score += 4;
      if (words(t.title).some(w => seedWords.has(w))) score += 0.75;
      return { t, score: score + Math.random() };
    });
    scored.sort((a, b) => b.score - a.score);
    sendJson(res, 200, { tracks: scored.map(s => s.t).slice(0, limit) });
    return true;
  }

  // Shared keep-alive agents for stream proxying
  const proxyHttpsAgent = new https.Agent({
    keepAlive: true,
    maxSockets: 32,
    keepAliveMsecs: 2000,
  });
  const proxyHttpAgent = new http.Agent({ keepAlive: true, maxSockets: 32, keepAliveMsecs: 2000 });

  // --- STREAM PROXY (NEW) ---
  if (u.pathname === '/api/stream') {
    const id = u.searchParams.get('id')?.trim();
    if (!id) {
      sendJson(res, 400, { error: 'No id' });
      return true;
    }
    const isDownload = u.searchParams.get('download') === '1';
    const upstreamTimeoutMs = isDownload ? 300000 : 30000;
    // Демо-треки офлайн-каталога отдаются локально, без YouTube
    const demo = require('./demo.js');
    if (demo.isDemoId(id)) {
      if (demo.serveDemoAudio(id, res)) return true;
    }
    try {
      const streamUrl = await fetchStreamUrl(id);
      if (!streamUrl) {
        sendJson(res, 502, { error: 'No stream available' });
        return true;
      }
      proxyStream(streamUrl, req, res, 0, upstreamTimeoutMs);
      return true;
    } catch (e) {
      console.error('Stream setup error:', e.message);
      sendJson(res, 502, { error: 'Stream setup failed' });
      return true;
    }
  }

  // Helper function for redirects & stream proxying
  function proxyStream(url, req, res, depth = 0, upstreamTimeoutMs = 30000) {
    if (depth > 5) {
      if (!res.headersSent) sendJson(res, 502, { error: 'Too many redirects' });
      return;
    }
    try {
      const remote = new URL(url);
      const transport = remote.protocol === 'https:' ? https : http;
      const agent = remote.protocol === 'https:' ? proxyHttpsAgent : proxyHttpAgent;

      const headers = {
        'User-Agent': YT_UA,
        Accept: '*/*',
        'Accept-Encoding': 'identity',
        'Accept-Language': 'en-US,en;q=0.9',
        Referer: 'https://www.youtube.com/',
        Origin: 'https://www.youtube.com',
        'Sec-Fetch-Dest': 'audio',
        'Sec-Fetch-Mode': 'cors',
        'Sec-Fetch-Site': 'cross-site',
      };
      if (req.headers.range) headers.Range = req.headers.range;

      const upstream = transport.request(remote, { method: 'GET', headers, agent }, upRes => {
        const sc = upRes.statusCode || 500;
        if ([301, 302, 303, 307, 308].includes(sc) && upRes.headers.location) {
          upRes.resume();
          proxyStream(upRes.headers.location, req, res, depth + 1, upstreamTimeoutMs);
          return;
        }
        const rh = {
          'Content-Type': upRes.headers['content-type'] || 'audio/webm',
          'Cache-Control': 'no-store',
          'Accept-Ranges': 'bytes',
        };
        if (upRes.headers['content-length']) rh['Content-Length'] = upRes.headers['content-length'];
        if (upRes.headers['content-range']) rh['Content-Range'] = upRes.headers['content-range'];
        res.writeHead(sc, rh);
        upRes.pipe(res);
      });

      const onClose = () => {
        if (!res.writableEnded) upstream.destroy();
      };
      res.once('close', onClose);

      upstream.on('error', e => {
        res.removeListener('close', onClose);
        console.error('Stream proxy error:', e.message);
        if (!res.headersSent) sendJson(res, 502, { error: 'Stream proxy failed' });
      });
      upstream.setTimeout(upstreamTimeoutMs, () => {
        upstream.destroy(new Error('Stream upstream timeout'));
      });
      upstream.end();
    } catch (e) {
      console.error('Stream redirect error:', e.message);
      if (!res.headersSent) sendJson(res, 502, { error: 'Stream redirect failed' });
    }
  }

  // --- AUDIO STREAM (legacy, returns URL) ---
  if (u.pathname === '/api/audio') {
    const id = u.searchParams.get('id')?.trim();
    if (!id) {
      sendJson(res, 400, { error: 'No id' });
      return true;
    }
    // Демо-треки офлайн-каталога отдаются локально
    const demoLib = require('./demo.js');
    if (demoLib.isDemoId(id)) {
      sendJson(res, 200, { url: '/demo/audio/' + id + '.wav' });
      return true;
    }
    // SoundCloud tracks - use fetchStreamUrl
    if (id.startsWith('sc_')) {
      try {
        const streamUrl = await fetchStreamUrl(id);
        if (!streamUrl) {
          sendJson(res, 502, { error: 'No stream' });
          return true;
        }
        sendJson(res, 200, { url: streamUrl });
        return true;
      } catch (e) {
        sendJson(res, 502, { error: e.message });
        return true;
      }
    }
    try {
      const streamUrl = await fetchStreamUrl(id);
      if (streamUrl) {
        sendJson(res, 200, { url: streamUrl });
        return true;
      }
    } catch (e) {
      console.log('[stream] Error fetching stream for', id, ':', e.message);
    }
    sendJson(res, 502, { error: 'No stream available' });
    return true;
  }

  // --- PRELOAD ---
  if (u.pathname === '/api/preload') {
    const ids = u.searchParams.get('ids')?.split(',').filter(Boolean) || [];
    ids.slice(0, 2).forEach(id => {
      fetchStreamUrl(id).catch(() => {});
    });
    sendJson(res, 200, { preloading: Math.min(ids.length, 2) });
    return true;
  }

  // --- LYRICS ---
  if (u.pathname === '/api/lyrics') {
    const track = u.searchParams.get('track')?.trim();
    const artist = u.searchParams.get('artist')?.trim();
    if (!track || !artist) {
      sendJson(res, 400, { error: 'track and artist required' });
      return true;
    }
    try {
      const normStr = s => (s || '').toLowerCase().replace(/[^\p{L}\p{N}\s]/gu, '').replace(/\s+/g, ' ').trim();
      const lrclibUrl = `https://lrclib.net/api/get?track_name=${encodeURIComponent(track)}&artist_name=${encodeURIComponent(artist)}`;
      const lyricsData = await httpGet(lrclibUrl, 8000);
      if (lyricsData && typeof lyricsData === 'object' && !lyricsData.message && (lyricsData.syncedLyrics || lyricsData.plainLyrics)) {
        const expT = normStr(track);
        const resT = normStr(lyricsData.trackName);
        if (resT === expT || (resT.length >= 3 && expT.includes(resT))) {
          sendJson(res, 200, {
            syncedLyrics: lyricsData.syncedLyrics || null,
            plainLyrics: lyricsData.plainLyrics || null,
            track: lyricsData.trackName || track,
            artist: lyricsData.artistName || artist,
          });
          return true;
        }
      }
      const searchUrl = `https://lrclib.net/api/search?track_name=${encodeURIComponent(track)}&artist_name=${encodeURIComponent(artist)}`;
      const searchResults = await httpGet(searchUrl, 8000);
      if (Array.isArray(searchResults) && searchResults.length > 0) {
        const expT = normStr(track);
        const expA = normStr(artist);
        const best = searchResults.find(r => {
          if (!r || (!r.syncedLyrics && !r.plainLyrics)) return false;
          const resT = normStr(r.trackName);
          const resA = normStr(r.artistName);
          const titleMatch = resT === expT || (resT.length >= 4 && expT.includes(resT)) || (expT.length >= 4 && resT.includes(expT));
          const artistMatch = !expA || resA === expA || (resA.length >= 3 && expA.includes(resA)) || (expA.length >= 3 && resA.includes(expA));
          return titleMatch && artistMatch;
        });
        if (best) {
          sendJson(res, 200, {
            syncedLyrics: best.syncedLyrics || null,
            plainLyrics: best.plainLyrics || null,
            track: best.trackName || track,
            artist: best.artistName || artist,
          });
          return true;
        }
      }
      sendJson(res, 200, { syncedLyrics: null, plainLyrics: null, track, artist });
      return true;
    } catch (e) {
      sendJson(res, 200, { syncedLyrics: null, plainLyrics: null, track, artist });
      return true;
    }
  }

  // --- STREAM URL (returns direct URL for fast download) ---
  if (u.pathname === '/api/stream-url') {
    const id = u.searchParams.get('id')?.trim();
    if (!id) {
      sendJson(res, 400, { error: 'No id' });
      return true;
    }
    try {
      const streamUrl = await fetchStreamUrl(id);
      if (!streamUrl) {
        sendJson(res, 502, { error: 'No stream' });
        return true;
      }
      sendJson(res, 200, { url: streamUrl });
    } catch (e) {
      sendJson(res, 502, { error: e.message });
    }
    return true;
  }

  // --- PLAYLIST IMPORT (YouTube + Spotify) ---
  async function fetchFullUrl(rawUrl, timeout = 12000) {
    return new Promise((resolve) => {
      let current = rawUrl;
      if (!current.startsWith('http://') && !current.startsWith('https://')) {
        current = 'https://' + current;
      }
      function follow(u, hops = 0) {
        if (hops > 6) return resolve({ finalUrl: u, body: '' });
        let parsed;
        try { parsed = new URL(u); } catch { return resolve({ finalUrl: u, body: '' }); }
        const transport = parsed.protocol === 'https:' ? https : http;
        const timer = setTimeout(() => { req.destroy(); resolve({ finalUrl: u, body: '' }); }, timeout);
        const req = transport.get(u, {
          headers: {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
            'Accept-Language': 'ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7',
            'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
          }
        }, res => {
          if ([301, 302, 303, 307, 308].includes(res.statusCode) && res.headers.location) {
            clearTimeout(timer);
            const loc = res.headers.location.startsWith('http')
              ? res.headers.location
              : parsed.origin + res.headers.location;
            return follow(loc, hops + 1);
          }
          let data = '';
          res.on('data', c => (data += c));
          res.on('end', () => {
            clearTimeout(timer);
            resolve({ finalUrl: u, body: data });
          });
        });
        req.on('error', () => {
          clearTimeout(timer);
          resolve({ finalUrl: u, body: '' });
        });
      }
      follow(current);
    });
  }

  // --- PLAYLIST EXTRACTION & STREAMING RESOLUTION HELPERS ---
  async function extractTracksFromUrl(url) {
    const rawUrl = String(url || '').trim();
    if (!rawUrl) throw new Error('URL не указан');

    // 1. SPOTIFY (Playlist / Album / Track)
    if (rawUrl.includes('spotify.com') || rawUrl.startsWith('spotify:')) {
      const playlistId = rawUrl.match(/playlist\/([a-zA-Z0-9]+)/)?.[1] || rawUrl.match(/spotify:playlist:([a-zA-Z0-9]+)/)?.[1];
      const albumId = rawUrl.match(/album\/([a-zA-Z0-9]+)/)?.[1] || rawUrl.match(/spotify:album:([a-zA-Z0-9]+)/)?.[1];
      const trackId = rawUrl.match(/track\/([a-zA-Z0-9]+)/)?.[1] || rawUrl.match(/spotify:track:([a-zA-Z0-9]+)/)?.[1];

      let playlistName = 'Spotify импорт';
      let playlistCover = '';
      const items = [];

      if (playlistId || albumId) {
        const targetType = playlistId ? 'playlist' : 'album';
        const targetId = playlistId || albumId;
        const embedUrl = `https://open.spotify.com/embed/${targetType}/${targetId}`;
        try {
          const html = await httpGet(embedUrl, 12000);
          const titleMatch = html.match(/<title>([^<]+)<\/title>/i);
          if (titleMatch && titleMatch[1]) {
            playlistName = titleMatch[1].replace(/ \| Spotify$/i, '').replace(/^Spotify - /i, '').trim();
          }
          const jsonMatch = html.match(/<script[^>]*id="__NEXT_DATA__"[^>]*>([\s\S]*?)<\/script>/);
          if (jsonMatch) {
            const nextData = JSON.parse(jsonMatch[1]);
            const entity = nextData?.props?.pageProps?.state?.data?.entity || nextData?.props?.pageProps?.state?.data?.playlist || {};
            if (entity.name || entity.title) playlistName = entity.name || entity.title;
            if (entity.coverArt?.sources?.[0]?.url) playlistCover = entity.coverArt.sources[0].url;

            const rawList = entity.trackList || nextData?.props?.pageProps?.tracks?.items || nextData?.props?.pageProps?.data?.playlist?.trackList || [];
            for (const it of rawList) {
              const trk = it.track || it;
              const title = trk.title || trk.name || '';
              const artist = trk.subtitle || trk.artists?.map(a => a.name).join(', ') || '';
              const itemCover = (trk.coverArt?.sources?.[0]?.url && trk.coverArt.sources[0].url !== playlistCover)
                ? trk.coverArt.sources[0].url
                : (trk.album?.images?.[0]?.url && trk.album.images[0].url !== playlistCover)
                ? trk.album.images[0].url
                : '';
              const duration = trk.duration ? trk.duration / (trk.duration > 1000 ? 1000 : 1) : 0;
              if (title) items.push({ title, artist, cover: itemCover, duration });
            }
          }
        } catch (e) {
          console.warn('[spotify] Embed parse failed:', e.message);
        }

        if (items.length === 0) {
          try {
            const mainUrl = `https://open.spotify.com/${targetType}/${targetId}`;
            const mainHtml = await httpGet(mainUrl, 12000);
            const mainMatch = mainHtml.match(/<script[^>]*id="__NEXT_DATA__"[^>]*>([\s\S]*?)<\/script>/);
            if (mainMatch) {
              const mainData = JSON.parse(mainMatch[1]);
              const pData = mainData?.props?.pageProps?.playlist || mainData?.props?.pageProps?.state?.data?.playlist || {};
              if (pData.name) playlistName = pData.name;
              const rawItems = pData?.tracks?.items || [];
              for (const it of rawItems) {
                const trk = it.track || it;
                const title = trk.name || trk.title || '';
                const artist = trk.artists?.map(a => a.name).join(', ') || trk.subtitle || '';
                const itemCover = (trk.album?.images?.[0]?.url && trk.album.images[0].url !== playlistCover)
                  ? trk.album.images[0].url
                  : (trk.coverArt?.sources?.[0]?.url && trk.coverArt.sources[0].url !== playlistCover)
                  ? trk.coverArt.sources[0].url
                  : '';
                const duration = (trk.duration_ms || trk.duration || 0) / 1000;
                if (title && !items.find(x => x.title === title && x.artist === artist)) {
                  items.push({ title, artist, cover: itemCover, duration });
                }
              }
            }
          } catch (e) {
            console.warn('[spotify] Main page parse failed:', e.message);
          }
        }
      } else if (trackId) {
        try {
          const html = await httpGet(`https://open.spotify.com/embed/track/${trackId}`, 10000);
          const jsonMatch = html.match(/<script[^>]*id="__NEXT_DATA__"[^>]*>([\s\S]*?)<\/script>/);
          if (jsonMatch) {
            const nextData = JSON.parse(jsonMatch[1]);
            const entity = nextData?.props?.pageProps?.state?.data?.entity || {};
            const title = entity.title || entity.name || 'Трек Spotify';
            const artist = entity.subtitle || entity.artists?.map(a => a.name).join(', ') || '';
            const cover = entity.coverArt?.sources?.[0]?.url || '';
            playlistName = `${artist} - ${title}`;
            items.push({ title, artist, cover, duration: 0 });
          }
        } catch (e) {}
      }

      if (items.length === 0) throw new Error('Не удалось извлечь треки из Spotify ссылки');
      return { name: playlistName, cover: playlistCover, items, isDirect: false };
    }

    // 2. SOUNDCLOUD
    if (rawUrl.includes('soundcloud.com')) {
      try {
        const result = await scImportPlaylist(rawUrl);
        if (result && Array.isArray(result.tracks) && result.tracks.length > 0) {
          return {
            name: result.name || 'SoundCloud импорт',
            cover: result.tracks[0]?.cover || '',
            items: result.tracks,
            isDirect: true,
          };
        }
      } catch (e) {
        console.warn('[soundcloud] Import fallback:', e.message);
      }
    }

    // 4. YOUTUBE & YOUTUBE MUSIC (Default / yt-dlp)
    try {
      const localYtdlpPath = process.env.YT_DLP_PATH || findYtDlp();
      const proc = spawn(
        localYtdlpPath,
        [
          '--no-check-certificates',
          '--no-warnings',
          '--quiet',
          '--flat-playlist',
          '--print',
          '%(playlist_title)s\t%(id)s\t%(title)s\t%(duration)s\t%(thumbnail)s\t%(uploader)s',
          '--playlist-end',
          '200',
          rawUrl,
        ],
        { stdio: ['ignore', 'pipe', 'pipe'], windowsHide: true }
      );
      const result = await new Promise((resolve, reject) => {
        let out = '';
        proc.stdout.on('data', d => { out += d; });
        proc.stderr.on('data', d => { console.error('[playlist]', d.toString().trim()); });
        proc.on('error', reject);
        proc.on('close', code => {
          if (out.trim()) resolve(out.trim());
          else reject(new Error('Код завершения ' + code));
        });
        setTimeout(() => {
          proc.kill();
          reject(new Error('Таймаут загрузки плейлиста'));
        }, 60000);
      });

      let detectedTitle = 'YouTube импорт';
      const tracks = [];
      for (const line of result.split('\n')) {
        if (!line.trim()) continue;
        const parts = line.split('\t');
        let plTitle = '', id = '', title = '', duration = 0, thumbnail = '', uploader = '';
        if (parts.length >= 6) {
          [plTitle, id, title, duration, thumbnail, uploader] = parts;
        } else if (parts.length >= 4) {
          [id, title, duration, thumbnail] = parts;
        }
        if (plTitle && plTitle !== 'NA') detectedTitle = plTitle.trim();
        if (!id || id === 'NA') continue;
        const cleanId = id.trim();
        // Guarantee unique video cover per YouTube track rather than shared playlist thumbnail
        let trackCover = `https://img.youtube.com/vi/${cleanId}/hqdefault.jpg`;
        if (thumbnail && thumbnail !== 'NA' && thumbnail.includes(cleanId)) {
          trackCover = thumbnail.trim();
        }
        tracks.push({
          id: cleanId,
          title: (title || '').trim() || 'Трек',
          artist: (uploader && uploader !== 'NA') ? uploader.trim() : '',
          duration: parseInt(duration) || 0,
          cover: trackCover,
        });
      }

      if (tracks.length === 0) throw new Error('В плейлисте не найдено треков');
      return { name: detectedTitle, cover: tracks[0]?.cover || '', items: tracks, isDirect: true };
    } catch (e) {
      throw new Error(`Ошибка извлечения плейлиста: ${e.message}`);
    }
  }

  // --- STREAMING PLAYLIST IMPORT ENDPOINT (SSE) ---
  if (u.pathname === '/api/playlist/stream') {
    const url = u.searchParams.get('url')?.trim();
    if (!url) {
      sendJson(res, 400, { error: 'Не указан URL плейлиста' });
      return true;
    }

    res.writeHead(200, {
      'Content-Type': 'text/event-stream; charset=utf-8',
      'Cache-Control': 'no-cache, no-transform',
      'Connection': 'keep-alive',
      'X-Accel-Buffering': 'no',
    });

    const sendEvent = (event, data) => {
      try {
        res.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`);
      } catch (e) {}
    };

    try {
      sendEvent('status', { step: 'extracting', message: 'Анализ ссылки и получение треков...' });
      const { name, cover, items, isDirect } = await extractTracksFromUrl(url);

      sendEvent('metadata', { name, cover, total: items.length, isDirect });

      if (isDirect) {
        // Direct YouTube / SoundCloud items with IDs already resolved
        const resolved = [];
        for (let i = 0; i < items.length; i++) {
          const item = items[i];
          resolved.push(item);
          sendEvent('progress', {
            current: i + 1,
            total: items.length,
            percent: Math.round(((i + 1) / items.length) * 100),
            track: item,
          });
        }
        sendEvent('complete', { name, total: resolved.length, tracks: resolved });
        res.end();
        return true;
      }

      // External metadata (Spotify, Yandex, Apple Music) — resolve audio on YouTube
      const resolved = [];
      const BATCH = 4;
      for (let i = 0; i < items.length; i += BATCH) {
        const batch = items.slice(i, i + BATCH);
        const batchResults = await Promise.allSettled(
          batch.map(async trk => {
            const query = trk.artist ? `${trk.artist} - ${trk.title}` : trk.title;
            const ytResults = await searchTracks(query, 1, false);
            if (ytResults && ytResults.length > 0) {
              const yt = ytResults[0];
              const uniqueCover = yt.cover || `https://img.youtube.com/vi/${yt.id}/hqdefault.jpg`;
              const trackCover = (trk.cover && trk.cover !== cover && !trk.cover.includes('default_playlist'))
                ? trk.cover
                : uniqueCover;
              return {
                id: yt.id,
                title: trk.title || yt.title,
                artist: trk.artist || yt.artist || '',
                duration: yt.duration || trk.duration || 0,
                cover: trackCover,
              };
            }
            return null;
          })
        );

        for (let j = 0; j < batchResults.length; j++) {
          const r = batchResults[j];
          const currIdx = i + j + 1;
          const trkObj = r.status === 'fulfilled' && r.value ? r.value : null;
          if (trkObj) resolved.push(trkObj);

          sendEvent('progress', {
            current: currIdx,
            total: items.length,
            percent: Math.round((currIdx / items.length) * 100),
            track: trkObj || batch[j],
          });
        }
      }

      sendEvent('complete', { name, total: resolved.length, tracks: resolved });
      res.end();
      return true;
    } catch (e) {
      console.error('[import-stream] Error:', e.message);
      sendEvent('error', { error: e.message || 'Ошибка импорта' });
      res.end();
      return true;
    }
  }

  // --- BATCH RESOLVE FROM TEXT / FILE ITEMS (SSE) ---
  if (u.pathname === '/api/playlist/resolve-stream' && req.method === 'POST') {
    let payload;
    try {
      payload = await parseBody(req);
    } catch (e) {
      sendJson(res, 400, { error: 'Неверное тело запроса' });
      return true;
    }

    const playlistName = payload?.name || 'Импортированный список';
    const items = Array.isArray(payload?.items) ? payload.items : [];

    if (items.length === 0) {
      sendJson(res, 400, { error: 'Список треков пуст' });
      return true;
    }

    res.writeHead(200, {
      'Content-Type': 'text/event-stream; charset=utf-8',
      'Cache-Control': 'no-cache, no-transform',
      'Connection': 'keep-alive',
      'X-Accel-Buffering': 'no',
    });

    const sendEvent = (event, data) => {
      try {
        res.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`);
      } catch (e) {}
    };

    sendEvent('metadata', { name: playlistName, total: items.length });

    const resolved = [];
    const BATCH = 4;
    for (let i = 0; i < items.length; i += BATCH) {
      const batch = items.slice(i, i + BATCH);
      const batchResults = await Promise.allSettled(
        batch.map(async trk => {
          const query = trk.artist ? `${trk.artist} - ${trk.title}` : (trk.title || String(trk));
          const ytResults = await searchTracks(query, 1, false);
          if (ytResults && ytResults.length > 0) {
            const yt = ytResults[0];
            const uniqueCover = yt.cover || `https://img.youtube.com/vi/${yt.id}/hqdefault.jpg`;
            const trackCover = trk.cover || uniqueCover;
            return {
              id: yt.id,
              title: trk.title || yt.title,
              artist: trk.artist || yt.artist || '',
              duration: yt.duration || trk.duration || 0,
              cover: trackCover,
            };
          }
          return null;
        })
      );

      for (let j = 0; j < batchResults.length; j++) {
        const r = batchResults[j];
        const currIdx = i + j + 1;
        const trkObj = r.status === 'fulfilled' && r.value ? r.value : null;
        if (trkObj) resolved.push(trkObj);

        sendEvent('progress', {
          current: currIdx,
          total: items.length,
          percent: Math.round((currIdx / items.length) * 100),
          track: trkObj || batch[j],
        });
      }
    }

    sendEvent('complete', { name: playlistName, total: resolved.length, tracks: resolved });
    res.end();
    return true;
  }

  // --- FALLBACK PLAYLIST ENDPOINT ---
  if (u.pathname === '/api/playlist') {
    const url = u.searchParams.get('url')?.trim();
    if (!url) {
      sendJson(res, 400, { error: 'Playlist URL required' });
      return true;
    }
    try {
      const { name, cover, items, isDirect } = await extractTracksFromUrl(url);
      if (isDirect) {
        sendJson(res, 200, { name, cover, tracks: items });
        return true;
      }
      const results = [];
      const BATCH = 5;
      for (let i = 0; i < items.length; i += BATCH) {
        const batch = items.slice(i, i + BATCH);
        const batchResults = await Promise.allSettled(
          batch.map(async trk => {
            const query = trk.artist ? `${trk.artist} - ${trk.title}` : trk.title;
            const ytResults = await searchTracks(query, 1, false);
            if (ytResults && ytResults.length > 0) {
              const yt = ytResults[0];
              const uniqueCover = yt.cover || `https://img.youtube.com/vi/${yt.id}/hqdefault.jpg`;
              const trackCover = (trk.cover && trk.cover !== cover) ? trk.cover : uniqueCover;
              return {
                id: yt.id,
                title: trk.title || yt.title,
                artist: trk.artist || yt.artist || '',
                duration: yt.duration || trk.duration || 0,
                cover: trackCover,
              };
            }
            return null;
          })
        );
        for (const r of batchResults) {
          if (r.status === 'fulfilled' && r.value) results.push(r.value);
        }
      }
      sendJson(res, 200, { name, cover, tracks: results });
      return true;
    } catch (e) {
      console.error('[playlist] Error:', e.message);
      sendJson(res, 502, { error: e.message || 'Ошибка загрузки плейлиста' });
      return true;
    }
  }

  // --- SOUNDCLOUD IMPORT (LEGACY) ---
  if (u.pathname === '/api/soundcloud/import') {
    const url = u.searchParams.get('url')?.trim();
    if (!url) {
      sendJson(res, 400, { error: 'URL required' });
      return true;
    }
    try {
      const result = await scImportPlaylist(url);
      if (result.error) {
        sendJson(res, 502, { error: result.error });
        return true;
      }
      sendJson(res, 200, { name: result.name, tracks: result.tracks });
      return true;
    } catch (e) {
      console.error('[soundcloud] Import error:', e.message);
      sendJson(res, 502, { error: 'Failed to import: ' + e.message });
      return true;
    }
  }

  return false;
}

module.exports = { handleMusicRoutes };
