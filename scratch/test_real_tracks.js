const { fetchStreamUrl } = require('../routes/utils.js');

async function testReal() {
  const realIds = [
    'dQw4w9WgXcQ',
    '3tmd-ClpJxA',
    'fJ9rUzIMcZQ',
    'kffacxfA7G4',
    'OPf0YbXqDm0'
  ];

  for (const id of realIds) {
    const t0 = Date.now();
    try {
      const url = await fetchStreamUrl(id);
      const elapsed = Date.now() - t0;
      if (url) {
        console.log(`Track [${id}]: ${elapsed}ms -> OK! (${url.substring(0, 50)}...)`);
      } else {
        console.log(`Track [${id}]: ${elapsed}ms -> NO STREAM`);
      }
    } catch (e) {
      console.log(`Track [${id}]: ${elapsed}ms -> ERROR: ${e.message}`);
    }
  }
}

testReal();
