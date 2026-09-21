async function testAllClientsForVideo(videoId) {
  console.log(`\n================================`);
  console.log(`Testing client types for videoId: ${videoId}`);
  console.log(`================================`);
  
  const clients = [
    { name: 'ANDROID_VR', client: { clientName: 'ANDROID_VR', clientVersion: '1.58.19', androidSdkVersion: 32 } },
    { name: 'ANDROID_MUSIC', client: { clientName: 'ANDROID_MUSIC', clientVersion: '6.42.52', androidSdkVersion: 31 } },
    { name: 'ANDROID_TESTSUITE', client: { clientName: 'ANDROID_TESTSUITE', clientVersion: '1.9', androidSdkVersion: 30 } },
    { name: 'ANDROID_CREATOR', client: { clientName: 'ANDROID_CREATOR', clientVersion: '23.49.100', androidSdkVersion: 31 } },
    { name: 'IOS', client: { clientName: 'IOS', clientVersion: '19.45.4', deviceMake: 'Apple', deviceModel: 'iPhone16,2', osVersion: '17.5.1' } },
    { name: 'IOS_MUSIC', client: { clientName: 'IOS_MUSIC', clientVersion: '6.42.52', deviceMake: 'Apple', deviceModel: 'iPhone16,2' } },
    { name: 'TVHTML5_SIMPLY_EMBEDDED_PLAYER', client: { clientName: 'TVHTML5_SIMPLY_EMBEDDED_PLAYER', clientVersion: '2.0' } },
    { name: 'WEB_EMBEDDED_PLAYER', client: { clientName: 'WEB_EMBEDDED_PLAYER', clientVersion: '1.20240901.00.00' } },
    { name: 'MEDIA_CONNECT', client: { clientName: 'MEDIA_CONNECT', clientVersion: '1.0' } },
    { name: 'WEB_UNPLUGGED', client: { clientName: 'WEB_UNPLUGGED', clientVersion: '1.20240901.00.00' } }
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
        },
        playbackContext: {
          contentPlaybackContext: {
            signatureTimestamp: 19800
          }
        }
      };
      const res = await fetch('https://www.youtube.com/youtubei/v1/player', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'User-Agent': 'Mozilla/5.0' },
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
  await testAllClientsForVideo('3tmd-ClpJxA');
  await testAllClientsForVideo('fJ9rUzIMcZQ');
  await testAllClientsForVideo('kffacxfA7G4');
}
run();
