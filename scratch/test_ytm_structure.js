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

async function debugYtm() {
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
  const sections = res?.contents?.tabbedSearchResultsRenderer?.tabs?.[0]?.tabRenderer?.content?.sectionListRenderer?.contents || [];
  console.log('Sections keys:', sections.map(s => Object.keys(s)));
  if (sections[0]?.musicShelfRenderer) {
    console.log('MusicShelf contents length:', sections[0].musicShelfRenderer.contents?.length);
    console.log('First item keys:', Object.keys(sections[0].musicShelfRenderer.contents[0]));
    console.log('First item JSON:', JSON.stringify(sections[0].musicShelfRenderer.contents[0], null, 2).substring(0, 500));
  }
}

debugYtm();
