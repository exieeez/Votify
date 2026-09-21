const { fetchStreamUrl } = require('../routes/utils.js');

async function test() {
  console.time('fetchStreamUrl');
  const url = await fetchStreamUrl('dQw4w9WgXcQ');
  console.timeEnd('fetchStreamUrl');
  console.log('Stream URL:', url ? url.substring(0, 80) + '...' : 'NULL');
}

test().catch(console.error);
