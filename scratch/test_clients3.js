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

async function testHls(videoId) {
  const clients = [
    { name: 'ANDROID_VR', clientName: 'ANDROID_VR', clientVersion: '1.58.19', androidSdkVersion: 32 },
    { name: 'ANDROID_MUSIC', clientName: 'ANDROID_MUSIC', clientVersion: '6.42.52', androidSdkVersion: 31 },
    { name: 'ANDROID', clientName: 'ANDROID', clientVersion: '19.29.37', androidSdkVersion: 30 }
  ];

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
      const res = await httpPostJSON('https://www.youtube.com/youtubei/v1/player', payload, 3000);
      const elapsed = Date.now() - start;
      if (res && res.streamingData) {
        const formats = res.streamingData.adaptiveFormats || [];
        const hlsUrl = res.streamingData.hlsManifestUrl;
        const directAudio = formats.filter(f => f.mimeType && f.mimeType.startsWith('audio/') && f.url);
        console.log(`[${videoId}] Client [${c.name}]: ${elapsed}ms -> Direct audio: ${directAudio.length}, HLS: ${hlsUrl ? 'YES' : 'NO'}`);
        if (directAudio.length > 0) {
          return directAudio[0].url;
        }
        if (hlsUrl) {
          console.log(` -> HLS URL: ${hlsUrl.substring(0, 90)}...`);
          return hlsUrl;
        }
      } else {
        console.log(`[${videoId}] Client [${c.name}]: ${elapsed}ms -> No streamingData`);
      }
    } catch (e) {
      console.log(`[${videoId}] Client [${c.name}]: Error ${e.message}`);
    }
  }
  return null;
}

async function run() {
  const ids = ['dQw4w9WgXcQ', 'kJQP7kiw5Fk', '3tmd-ClpJxA'];
  for (const id of ids) {
    await testHls(id);
  }
}

run();
