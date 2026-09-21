async function testClients(videoId) {
  console.log(`\nTesting videoId: ${videoId}`);
  const clients = [
    { name: 'ANDROID_VR', payload: { videoId, context: { client: { clientName: 'ANDROID_VR', clientVersion: '1.58.19', androidSdkVersion: 32, hl: 'en', gl: 'US' } } } },
    { name: 'IOS', payload: { videoId, context: { client: { clientName: 'IOS', clientVersion: '19.45.4', deviceMake: 'Apple', deviceModel: 'iPhone16,2', userAgent: 'com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X; en_US)', osVersion: '17.5.1.21F90', hl: 'en', gl: 'US' } } } },
    { name: 'TVHTML5', payload: { videoId, context: { client: { clientName: 'TVHTML5_SIMPLY_EMBEDDED_PLAYER', clientVersion: '2.0', hl: 'en', gl: 'US' } } } },
    { name: 'WEB_EMBEDDED', payload: { videoId, context: { client: { clientName: 'WEB_EMBEDDED_PLAYER', clientVersion: '1.20240901.00.00', hl: 'en', gl: 'US' } } } }
  ];

  for (const c of clients) {
    const t0 = Date.now();
    try {
      const res = await fetch('https://www.youtube.com/youtubei/v1/player', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'User-Agent': 'Mozilla/5.0' },
        body: JSON.stringify(c.payload)
      });
      const data = await res.json();
      const elapsed = Date.now() - t0;
      if (data && data.streamingData && Array.isArray(data.streamingData.adaptiveFormats)) {
        const audioFormats = data.streamingData.adaptiveFormats.filter(f => f.mimeType && f.mimeType.startsWith('audio/') && f.url);
        if (audioFormats.length > 0) {
          console.log(`  Client [${c.name}]: ${elapsed}ms -> GOT DIRECT URL! (${audioFormats[0].url.substring(0, 60)}...)`);
          return;
        } else {
          console.log(`  Client [${c.name}]: ${elapsed}ms -> adaptiveFormats present but no direct url property (decipher needed)`);
        }
      } else {
        console.log(`  Client [${c.name}]: ${elapsed}ms -> no streamingData / adaptiveFormats`);
      }
    } catch (e) {
      console.log(`  Client [${c.name}]: error ${e.message}`);
    }
  }
}

async function run() {
  await testClients('kJQP7kiw5Fk');
  await testClients('5qap5aO4i9A');
  await testClients('L_jWHffIx5E');
}
run();
