const { spawn } = require('child_process');

function testYtDlp(videoId) {
  return new Promise((resolve) => {
    const start = Date.now();
    const args = [
      '--no-check-certificates',
      '--no-warnings',
      '--no-playlist',
      '--quiet',
      '-g',
      '-f',
      'ba/b',
      '--socket-timeout',
      '6',
      '--extractor-args',
      'youtube:player_client=android',
      'https://www.youtube.com/watch?v=' + videoId
    ];
    const proc = spawn('yt-dlp', args, { stdio: ['ignore', 'pipe', 'pipe'] });
    let out = '';
    proc.stdout.on('data', d => {
      out += d;
      const line = out.trim();
      if (line && line.startsWith('http')) {
        proc.kill();
        resolve({ elapsed: Date.now() - start, url: line.split('\n')[0] });
      }
    });
    proc.on('close', () => {
      resolve({ elapsed: Date.now() - start, url: out.trim().split('\n')[0] || 'NONE' });
    });
    setTimeout(() => {
      proc.kill();
      resolve({ elapsed: Date.now() - start, url: 'TIMEOUT' });
    }, 10000);
  });
}

async function run() {
  console.log('Testing yt-dlp with player_client=android...');
  const res = await testYtDlp('kJQP7kiw5Fk');
  console.log(`Result: ${res.elapsed}ms -> URL: ${res.url ? res.url.substring(0, 80) + '...' : 'NONE'}`);
}

run();
