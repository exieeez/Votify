const https = require('https');

function httpPostJSON(urlStr, data, timeoutMs = 5000) {
  return new Promise((resolve, reject) => {
    const u = new URL(urlStr);
    const body = JSON.stringify(data);
    const req = https.request(u, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(body),
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)',
      },
      timeout: timeoutMs,
    }, res => {
      let out = '';
      res.on('data', d => out += d);
      res.on('end', () => {
        try { resolve(JSON.parse(out)); } catch (e) { reject(e); }
      });
    });
    req.on('error', reject);
    req.on('timeout', () => { req.destroy(); reject(new Error('timeout')); });
    req.write(body);
    req.end();
  });
}

function parseYtmResults(res, limit = 15) {
  const tracks = [];
  const seen = new Set();
  const sections = res?.contents?.tabbedSearchResultsRenderer?.tabs?.[0]?.tabRenderer?.content?.sectionListRenderer?.contents || [];

  for (const sec of sections) {
    if (tracks.length >= limit) break;

    // 1. Hero Card
    if (sec.musicCardShelfRenderer) {
      const card = sec.musicCardShelfRenderer;
      const title = card.title?.runs?.map(r => r.text).join('') || '';
      const artist = card.subtitle?.runs?.map(r => r.text).join('') || '';
      const videoId = card.buttons?.find(b => b.buttonRenderer?.navigationEndpoint?.watchEndpoint?.videoId)?.buttonRenderer?.navigationEndpoint?.watchEndpoint?.videoId || card.onTap?.watchEndpoint?.videoId;
      const cover = card.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.pop()?.url || '';
      if (videoId && !seen.has(videoId)) {
        seen.add(videoId);
        tracks.push({ id: videoId, title, artist, cover });
      }
    }

    // 2. Shelf items
    const items = sec.musicShelfRenderer?.contents || sec.itemSectionRenderer?.contents || [];
    for (const item of items) {
      if (tracks.length >= limit) break;
      const r = item.musicResponsiveListItemRenderer || item.videoRenderer;
      if (!r) continue;

      const videoId = r.videoId || r.playlistItemData?.videoId || r.flexColumns?.[0]?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.[0]?.navigationEndpoint?.watchEndpoint?.videoId;
      if (!videoId || seen.has(videoId)) continue;

      let title = r.title?.runs?.map(x => x.text).join('') || r.title?.simpleText || 'Unknown';
      let artist = r.ownerText?.runs?.[0]?.text || r.shortBylineText?.runs?.[0]?.text || 'Unknown';

      if (r.flexColumns) {
        title = r.flexColumns[0]?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.map(x => x.text).join('') || title;
        if (r.flexColumns[1]) {
          artist = r.flexColumns[1]?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.map(x => x.text).join('') || artist;
        }
      }

      const thumbnails = r.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails || r.thumbnail?.thumbnails || [];
      const cover = thumbnails.pop()?.url || '';
      seen.add(videoId);
      tracks.push({ id: videoId, title, artist, cover });
    }
  }

  return tracks;
}

async function run() {
  const body = {
    query: 'Дора',
    context: {
      client: {
        clientName: 'WEB_REMIX',
        clientVersion: '1.20241126.01.00',
        hl: 'ru',
        gl: 'RU',
      },
    },
  };
  const res = await httpPostJSON('https://music.youtube.com/youtubei/v1/search?prettyPrint=false', body);
  const tracks = parseYtmResults(res);
  console.log(`Parsed ${tracks.length} tracks from YTM:`);
  tracks.forEach((t, i) => console.log(`${i+1}. [${t.id}] ${t.title} - ${t.artist}`));
}

run();
