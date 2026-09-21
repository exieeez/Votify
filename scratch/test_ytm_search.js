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
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36',
        'Origin': 'https://music.youtube.com',
        'Referer': 'https://music.youtube.com/',
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

function extractYtmTracks(res, limit = 10) {
  const tracks = [];
  try {
    const sections = res?.contents?.tabbedSearchResultsRenderer?.tabs?.[0]?.tabRenderer?.content?.sectionListRenderer?.contents || [];
    for (const sec of sections) {
      const shelf = sec.musicShelfRenderer || sec.musicCardShelfRenderer;
      if (!shelf) continue;
      const contents = shelf.contents || [];
      for (const item of contents) {
        const r = item.musicResponsiveListItemRenderer;
        if (!r) continue;
        const videoId = r.playlistItemData?.videoId || r.flexColumns?.[0]?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.[0]?.navigationEndpoint?.watchEndpoint?.videoId;
        if (!videoId) continue;
        const title = r.flexColumns?.[0]?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.map(x => x.text).join('') || 'Unknown';
        const artist = r.flexColumns?.[1]?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.map(x => x.text).join('') || 'Unknown';
        const thumbnails = r.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails || [];
        const cover = thumbnails.pop()?.url || '';
        tracks.push({ id: videoId, title, artist, cover });
        if (tracks.length >= limit) break;
      }
    }
  } catch (e) {
    console.error('Error extracting YTM tracks:', e);
  }
  return tracks;
}

async function testYtmSearch(query) {
  const body = {
    query,
    context: {
      client: {
        clientName: 'WEB_REMIX',
        clientVersion: '1.20241126.01.00',
        hl: 'ru',
        gl: 'RU',
      },
    },
    params: 'EgWKAQIYAWoKEAkQBRAKEAMQBA%3D%3D',
  };
  const res = await httpPostJSON('https://music.youtube.com/youtubei/v1/search?prettyPrint=false', body);
  const tracks = extractYtmTracks(res, 5);
  console.log(`Found ${tracks.length} tracks for "${query}":`);
  tracks.forEach((t, i) => console.log(` ${i+1}. [${t.id}] ${t.title} - ${t.artist}`));
}

testYtmSearch('Дора');
