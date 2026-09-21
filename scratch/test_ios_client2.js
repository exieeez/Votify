async function testClients(videoId) {
  console.log(`\nTesting videoId: ${videoId}`);
  const clients = [
    { name: 'ANDROID_VR', payload: { videoId, contentCheckOk: true, racyCheckOk: true, context: { client: { clientName: 'ANDROID_VR', clientVersion: '1.58.19', androidSdkVersion: 32, hl: 'en', gl: 'US' } } } },
    { name: 'ANDROID_TESTSUITE', payload: { videoId, contentCheckOk: true, racyCheckOk: true, context: { client: { clientName: 'ANDROID_TESTSUITE', clientVersion: '1.9', androidSdkVersion: 30, hl: 'en', gl: 'US' } } } },
    { name: 'WEB', payload: { videoId, contentCheckOk: true, racyCheckOk: true, context: { client: { clientName: 'WEB', clientVersion: '2.20240901.00.00', hl: 'en', gl: 'US' } } } },
    { name: 'MWEB', payload: { videoId, contentCheckOk: true, racyCheckOk: true, context: { client: { clientName: 'MWEB', clientVersion: '2.20240901.00.00', hl: 'en', gl: 'US' } } } }
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
      if (data && data.streamingData) {
        const formats = [
          ...(data.streamingData.adaptiveFormats || []),
          ...(data.streamingData.formats || [])
        ];
        const audioFormats = formats.filter(f => (f.mimeType && f.mimeType.startsWith('audio/')) || f.audioQuality);
        const directAudio = audioFormats.filter(f => f.url);
        if (directAudio.length > 0) {
          console.log(`  Client [${c.name}]: ${elapsed}ms -> GOT DIRECT URL! (${directAudio[0].url.substring(0, 60)}...)`);
          return;
        } else if (audioFormats.length > 0) {
          console.log(`  Client [${c.name}]: ${elapsed}ms -> ${audioFormats.length} formats present but decipher required (signatureCipher)`);
        } else {
          console.log(`  Client [${c.name}]: ${elapsed}ms -> streamingData present but 0 audio formats`);
        }
      } else {
        console.log(`  Client [${c.name}]: ${elapsed}ms -> status: ${data?.playabilityStatus?.status || 'NO_STREAMING_DATA'}`);
      }
    } catch (e) {
      console.log(`  Client [${c.name}]: error ${e.message}`);
    }
  }
}

async function run() {
  await testClients('dQw4w9WgXcQ');
  await testClients('kJQP7kiw5Fk');
  await testClients('5qap5aO4i9A');
  await testClients('L_jWHffIx5E');
}
run();
