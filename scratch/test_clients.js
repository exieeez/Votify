const https = require('https');

function httpPostJSON(urlStr, data, timeoutMs = 4000) {
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

const clients = [
  { name: 'TVHTML5', clientName: 'TVHTML5', clientVersion: '7.20240901.00.00' },
  { name: 'ANDROID_VR', clientName: 'ANDROID_VR', clientVersion: '1.58.19' },
  { name: 'IOS', clientName: 'IOS', clientVersion: '19.29.1', userAgent: 'com.google.ios.youtube/19.29.1 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X; en_US)' },
  { name: 'WEB_REMIX', clientName: 'WEB_REMIX', clientVersion: '1.20240901.00.00' },
  { name: 'EMBEDDED_PLAYER', clientName: 'EMBEDDED_PLAYER', clientVersion: '1.20240901.00.00' }
];

async function testClient(c, videoId) {
  const start = Date.now();
  try {
    const payload = {
      videoId,
      context: {
        client: {
          clientName: c.clientName,
          clientVersion: c.clientVersion,
          hl: 'en',
          gl: 'US',
        }
      }
    };
    const res = await httpPostJSON('https://www.youtube.com/youtubei/v1/player', payload, 3000);
    const elapsed = Date.now() - start;
    if (res && res.streamingData && Array.isArray(res.streamingData.adaptiveFormats)) {
      const audioFormats = res.streamingData.adaptiveFormats.filter(f => f.mimeType && f.mimeType.startsWith('audio/'));
      const direct = audioFormats.filter(f => f.url);
      console.log(`Client [${c.name}]: ${elapsed}ms -> Total audio formats: ${audioFormats.length}, Direct URLs: ${direct.length}`);
      if (direct.length > 0) {
        console.log(` -> SAMPLE DIRECT URL: ${direct[0].url.substring(0, 90)}...`);
      }
    } else {
      console.log(`Client [${c.name}]: ${elapsed}ms -> No streamingData adaptiveFormats`);
    }
  } catch (e) {
    console.log(`Client [${c.name}]: Error ${e.message}`);
  }
}

async function run() {
  console.log('Testing InnerTube clients for video dQw4w9WgXcQ...');
  for (const c of clients) {
    await testClient(c, 'dQw4w9WgXcQ');
  }
}

run();
