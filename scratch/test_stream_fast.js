const { fetchStreamUrl } = require('../routes/utils.js');

async function benchmark() {
  const tracks = ['dQw4w9WgXcQ', 'kJQP7kiw5Fk', '5qap5aO4i9A', 'L_jWHffIx5E'];
  console.log('--- START BENCHMARK ---');
  for (const id of tracks) {
    const t0 = Date.now();
    const url = await fetchStreamUrl(id);
    const dt = Date.now() - t0;
    console.log(`Track [${id}]: ${dt}ms -> ${url ? 'OK (' + url.substring(0, 60) + '...)' : 'FAILED'}`);
  }
}

benchmark().catch(console.error);
