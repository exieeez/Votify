const https = require('https');

function httpPostJSON(urlStr, data, headers = {}, timeoutMs = 4000) {
  return new Promise((resolve, reject) => {
    const u = new URL(urlStr);
    const body = JSON.stringify(data);
    const req = https.request(u, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(body),
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)',
        ...headers
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
  { name: 'ANDROID_VR', clientName: 'ANDROID_VR', clientVersion: '1.58.19', androidSdkVersion: 32 },
  { name: 'ANDROID_UNPLUGGED', clientName: 'ANDROID_UNPLUGGED', clientVersion: '1.25.0', androidSdkVersion: 30 },
  { name: 'ANDROID_TESTSUITE', clientName: 'ANDROID_TESTSUITE', clientVersion: '1.9.0', androidSdkVersion: 30 },
  { name: 'ANDROID_LITE', clientName: 'ANDROID_LITE', clientVersion: '17.31.35', androidSdkVersion: 28 },
];

async function testVideo(videoId) {
  console.log(`\n--- Testing Video ID: ${videoId} ---`);
  for (const c of clients) {
    const start = Date.now();
    try {
      const payload = {
        videoId,
        context: {
          client: {
            clientName: c.clientName,
            clientVersion: c.clientVersion,
            androidSdkVersion: c.androidSdkVersion,
            hl: 'en',
            gl: 'US',
          }
        }
      };
      const res = await httpPostJSON('https://www.youtube.com/youtubei/v1/player', payload, {}, 3000);
      const elapsed = Date.now() - start;
      if (res && res.streamingData && Array.isArray(res.streamingData.adaptiveFormats)) {
        const audioFormats = res.streamingData.adaptiveFormats.filter(f => f.mimeType && f.mimeType.startsWith('audio/'));
        const direct = audioFormats.filter(f => f.url);
        console.log(`Client [${c.name}]: ${elapsed}ms -> Total audio formats: ${audioFormats.length}, Direct URLs: ${direct.length}`);
      } else {
        console.log(`Client [${c.name}]: ${elapsed}ms -> No streamingData adaptiveFormats`);
      }
    } catch (e) {
      console.log(`Client [${c.name}]: Error ${e.message}`);
    }
  }
}

async function run() {
  await testVideo('dQw4w9WgXcQ');
  await testVideo('kJQP7kiw5Fk');
}

run();
