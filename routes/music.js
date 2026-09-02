const {
  sendJson,
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
    const seen = new Set();
    const unique = allTracks.filter(t => {
      if (!t || !t.id) return false;
      if (excludeIds.has(t.id)) return false;
      if (seen.has(t.id)) return false;
      seen.add(t.id);
      return true;
    });
    // Shuffle
    for (let i = unique.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [unique[i], unique[j]] = [unique[j], unique[i]];
    }
    sendJson(res, 200, { tracks: unique.slice(0, limit) });
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
    const cached = streamCache.get(id);
    if (cached && cached.expires > Date.now()) {
      sendJson(res, 200, { url: cached.url });
      return true;
    }
    try {
      const localYtdlpPath = process.env.YT_DLP_PATH || findYtDlp();
      const proc = spawn(
        localYtdlpPath,
        [
          '--no-check-certificates',
          '--no-warnings',
          '--quiet',
          '-g',
          '-f',
          'ba/b',
          '--socket-timeout',
          '15',
          '--max-filesize',
          '50M',
          '--extractor-args',
          'youtube:player_client=android,web',
          '--user-agent',
          YT_UA,
          'https://www.youtube.com/watch?v=' + id,
        ],
        { stdio: ['ignore', 'pipe', 'pipe'], windowsHide: true }
      );
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
        proc.on('close', code => {
          if (out.trim()) resolve(out.trim().split('\n')[0]);
          else reject(new Error('exit ' + code));
        });
        setTimeout(() => {
          proc.kill();
          reject(new Error('timeout'));
        }, 30000);
      });
      streamCache.set(id, { url, expires: Date.now() + STREAM_CACHE_TTL });
      sendJson(res, 200, { url });
      return true;
    } catch (e) {
      console.log('yt-dlp -g error for', id, ':', e.message);
    }
    sendJson(res, 502, { error: 'No stream available' });
    return true;
  }

  // --- PRELOAD ---
  if (u.pathname === '/api/preload') {
    const ids = u.searchParams.get('ids')?.split(',').filter(Boolean) || [];
    for (const id of ids.slice(0, 3)) {
      fetchStreamUrl(id).catch(() => {});
    }
    sendJson(res, 200, { preloading: ids.length });
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
      const lrclibUrl = `https://lrclib.net/api/get?track_name=${encodeURIComponent(track)}&artist_name=${encodeURIComponent(artist)}`;
      const lyricsData = await httpGet(lrclibUrl, 8000);
      if (lyricsData && typeof lyricsData === 'object' && !lyricsData.message) {
        sendJson(res, 200, {
          syncedLyrics: lyricsData.syncedLyrics || null,
          plainLyrics: lyricsData.plainLyrics || null,
          track: lyricsData.trackName || track,
          artist: lyricsData.artistName || artist,
        });
        return true;
      }
      const searchUrl = `https://lrclib.net/api/search?track_name=${encodeURIComponent(track)}&artist_name=${encodeURIComponent(artist)}`;
      const searchResults = await httpGet(searchUrl, 8000);
      if (Array.isArray(searchResults) && searchResults.length > 0) {
        const best = searchResults[0];
        sendJson(res, 200, {
          syncedLyrics: best.syncedLyrics || null,
          plainLyrics: best.plainLyrics || null,
          track: best.trackName || track,
          artist: best.artistName || artist,
        });
        return true;
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
  if (u.pathname === '/api/playlist') {
    const url = u.searchParams.get('url')?.trim();
    if (!url) {
      sendJson(res, 400, { error: 'Playlist URL required' });
      return true;
    }

    const isSpotify = url.includes('open.spotify.com/playlist/');

    // --- SPOTIFY PLAYLIST ---
    if (isSpotify) {
      try {
        const playlistId = url.match(/playlist\/([a-zA-Z0-9]+)/)?.[1];
        if (!playlistId) throw new Error('Invalid Spotify URL');

        const tracks = [];

        // Approach 1: Fetch the embed page and extract __NEXT_DATA__
        const embedUrl = 'https://open.spotify.com/embed/playlist/' + playlistId;
        const html = await httpGet(embedUrl, 15000);

        const jsonMatch = html.match(/<script[^>]*id="__NEXT_DATA__"[^>]*>([\s\S]*?)<\/script>/);
        if (jsonMatch) {
          try {
            const nextData = JSON.parse(jsonMatch[1]);
            // Try all known paths
            const trackList =
              nextData?.props?.pageProps?.state?.data?.entity?.trackList ||
              nextData?.props?.pageProps?.tracks?.items ||
              nextData?.props?.pageProps?.state?.data?.playlist?.trackList ||
              nextData?.props?.pageProps?.data?.playlist?.trackList ||
              nextData?.props?.pageProps?.entity?.trackList ||
              [];
            for (const item of trackList) {
              const track = item.track || item;
              const title = track.title || track.name || '';
              const artist = track.subtitle || track.artists?.map(a => a.name).join(', ') || '';
              const cover =
                track.coverArt?.sources?.[0]?.url || track.album?.images?.[0]?.url || '';
              if (title) {
                tracks.push({ title, artist, cover, id: '', duration: track.duration || 0 });
              }
            }
          } catch (e) {
            console.error('[spotify] JSON parse error:', e.message);
          }
        }

        // Approach 2: Also try the official oembed + search API
        if (tracks.length === 0) {
          // Extract track data from meta tags in the embed page
          const metaMatches = html.matchAll(
            /<meta[^>]*property="og:description"[^>]*content="([^"]*)"/gi
          );
          for (const m of metaMatches) {
            const desc = m[1];
            // Format: "Playlist · Artist · 93 songs"
            // This doesn't give us individual tracks, skip
          }
        }

        // Approach 3: Use Spotify's public web API (no auth needed for public playlists)
        if (tracks.length === 0 || tracks.length < 10) {
          try {
            const apiUrl = `https://open.spotify.com/playlist/${playlistId}`;
            const mainHtml = await httpGet(apiUrl, 15000);
            // Extract from the main page's embedded JSON
            const mainJsonMatch = mainHtml.match(
              /<script[^>]*id="__NEXT_DATA__"[^>]*>([\s\S]*?)<\/script>/
            );
            if (mainJsonMatch) {
              const mainData = JSON.parse(mainJsonMatch[1]);
              const items =
                mainData?.props?.pageProps?.playlist?.tracks?.items ||
                mainData?.props?.pageProps?.state?.data?.playlist?.tracks?.items ||
                mainData?.props?.pageProps?.initialData?.playlist?.tracks?.items ||
                [];
              for (const item of items) {
                const track = item.track || item;
                const title = track.name || track.title || '';
                const artist = track.artists?.map(a => a.name).join(', ') || track.subtitle || '';
                const cover =
                  track.album?.images?.[0]?.url || track.coverArt?.sources?.[0]?.url || '';
                if (title && !tracks.find(t => t.title === title && t.artist === artist)) {
                  tracks.push({
                    title,
                    artist,
                    cover,
                    id: '',
                    duration: (track.duration_ms || track.duration || 0) / 1000,
                  });
                }
              }
            }
          } catch (e) {
            console.error('[spotify] Main page parse error:', e.message);
          }
        }

        if (!tracks.length) {
          sendJson(res, 502, {
            error: 'Не удалось извлечь треки из Spotify плейлиста. Попробуйте YouTube ссылку.',
          });
          return true;
        }

        console.log(
          `[spotify] Extracted ${tracks.length} tracks from metadata, searching YouTube...`
        );

        // Search each track on YouTube to get IDs — batch 5 at a time
        const results = [];
        const { searchTracks } = require('./utils.js');
        const BATCH = 5;
        for (let i = 0; i < tracks.length; i += BATCH) {
          const batch = tracks.slice(i, i + BATCH);
          const batchResults = await Promise.allSettled(
            batch.map(async track => {
              const query = track.artist ? track.artist + ' - ' + track.title : track.title;
              const ytResults = await searchTracks(query, 1, false);
              if (ytResults && ytResults.length > 0) {
                const yt = ytResults[0];
                return {
                  id: yt.id,
                  title: track.title || yt.title,
                  artist: track.artist || yt.artist || '',
                  duration: yt.duration || track.duration || 0,
                  cover:
                    track.cover ||
                    yt.cover ||
                    'https://img.youtube.com/vi/' + yt.id + '/hqdefault.jpg',
                };
              }
              return null;
            })
          );
          for (const r of batchResults) {
            if (r.status === 'fulfilled' && r.value) results.push(r.value);
          }
          // Log progress
          if ((i + BATCH) % 20 === 0 || i + BATCH >= tracks.length) {
            console.log(
              `[spotify] YouTube search: ${Math.min(i + BATCH, tracks.length)}/${tracks.length}`
            );
          }
        }

        sendJson(res, 200, { tracks: results });
        return true;
      } catch (e) {
        console.error('[spotify] Error:', e.message);
        sendJson(res, 502, { error: 'Ошибка загрузки Spotify плейлиста: ' + e.message });
        return true;
      }
    }

    // --- YOUTUBE PLAYLIST ---
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
          '%(id)s\t%(title)s\t%(duration)s\t%(thumbnail)s',
          '--playlist-end',
          '200',
          url,
        ],
        { stdio: ['ignore', 'pipe', 'pipe'], windowsHide: true }
      );
      const result = await new Promise((resolve, reject) => {
        let out = '';
        proc.stdout.on('data', d => {
          out += d;
        });
        proc.stderr.on('data', d => {
          console.error('[playlist]', d.toString().trim());
        });
        proc.on('error', reject);
        proc.on('close', code => {
          if (out.trim()) resolve(out.trim());
          else reject(new Error('exit ' + code));
        });
        setTimeout(() => {
          proc.kill();
          reject(new Error('timeout'));
        }, 120000);
      });

      const tracks = result
        .split('\n')
        .filter(Boolean)
        .map(line => {
          const [id, title, duration, thumbnail] = line.split('\t');
          if (!id || id === 'NA') return null;
          return {
            id: id.trim(),
            title: (title || '').trim() || 'Unknown',
            duration: parseInt(duration) || 0,
            cover: thumbnail
              ? thumbnail.trim()
              : 'https://img.youtube.com/vi/' + id.trim() + '/hqdefault.jpg',
            artist: '',
          };
        })
        .filter(Boolean);

      sendJson(res, 200, { tracks });
      return true;
    } catch (e) {
      console.error('[playlist] Error:', e.message);
      sendJson(res, 502, { error: 'Failed to load playlist: ' + e.message });
      return true;
    }
  }

  // --- SOUNDCLOUD IMPORT ---
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
