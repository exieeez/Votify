async function testMoreClients(videoId) {
  console.log(`\n================================`);
  console.log(`Testing videoId: ${videoId}`);
  console.log(`================================`);

  // Fetch initial visitor data
  let visitorData = '';
  try {
    const initRes = await fetch('https://www.youtube.com/sw.js');
    // or fetch visitor data from InnerTube visitor_id
  } catch (e) {}
  
  const clients = [
    { name: 'TVHTML5', client: { clientName: 'TVHTML5', clientVersion: '7.20230405.08.01' } },
    { name: 'ANDROID_EMBEDDED', client: { clientName: 'ANDROID_EMBEDDED_PLAYER', clientVersion: '17.31.35', androidSdkVersion: 30 } },
    { name: 'YTMUSIC_ANDROID', client: { clientName: 'ANDROID_MUSIC', clientVersion: '5.26.1', androidSdkVersion: 30 } },
    { name: 'WEB_REMIX', client: { clientName: 'WEB_REMIX', clientVersion: '1.20240901.00.00' } },
    { name: 'TVHTML5_SIMPLY', client: { clientName: 'TVHTML5_SIMPLY_EMBEDDED_PLAYER', clientVersion: '2.0' } },
    { name: 'ANDROID_VR_OLD', client: { clientName: 'ANDROID_VR', clientVersion: '1.43.30', androidSdkVersion: 28 } }
  ];

  for (const c of clients) {
    const t0 = Date.now();
    try {
      const payload = {
        videoId: videoId,
        context: {
          client: {
            ...c.client,
            hl: 'en',
            gl: 'US'
          }
        }
      };
      const res = await fetch('https://www.youtube.com/youtubei/v1/player', {
        method: 'POST',
        headers: { 
          'Content-Type': 'application/json',
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
        },
        body: JSON.stringify(payload)
      });
      const data = await res.json();
      const elapsed = Date.now() - t0;
      if (data && data.streamingData) {
        const formats = [
          ...(data.streamingData.adaptiveFormats || []),
          ...(data.streamingData.formats || [])
        ];
        const directUrls = formats.filter(f => f.url && (f.mimeType?.startsWith('audio/') || f.audioQuality));
        if (directUrls.length > 0) {
          console.log(`[SUCCESS] Client ${c.name}: ${elapsed}ms -> ${directUrls.length} direct audio URLs! (${directUrls[0].url.substring(0, 60)}...)`);
        } else if (formats.length > 0) {
          console.log(`[CIPHER]  Client ${c.name}: ${elapsed}ms -> ${formats.length} formats but signatureCipher required`);
        } else {
          console.log(`[NO_FMT]  Client ${c.name}: ${elapsed}ms -> 0 formats`);
        }
      } else {
        console.log(`[STATUS]  Client ${c.name}: ${elapsed}ms -> ${data?.playabilityStatus?.status || 'NO_STREAMING_DATA'} (${data?.playabilityStatus?.reason || ''})`);
      }
    } catch (e) {
      console.log(`[ERR]     Client ${c.name}: ${e.message}`);
    }
  }
}

async function run() {
  await testMoreClients('3tmd-ClpJxA');
  await testMoreClients('fJ9rUzIMcZQ');
}
run();
