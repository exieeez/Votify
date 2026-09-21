const { spawn } = require('child_process');

function getFastYtDlpStream(videoId) {
  return new Promise((resolve, reject) => {
    const t0 = Date.now();
    const args = [
      '--no-check-certificates',
      '--no-warnings',
      '--no-playlist',
      '--quiet',
      '--extractor-args', 'youtube:player_client=android,ios',
      '--socket-timeout', '5',
      '-g',
      '-f', 'ba/b',
      `https://www.youtube.com/watch?v=${videoId}`
    ];
    const proc = spawn('yt-dlp', args, { stdio: ['ignore', 'pipe', 'pipe'] });
    let out = '';
    proc.stdout.on('data', d => out += d);
    proc.on('close', code => {
      const elapsed = Date.now() - t0;
      if (code === 0 && out.trim()) {
        const url = out.trim().split('\n')[0];
        console.log(`yt-dlp [${videoId}]: ${elapsed}ms -> SUCCESS! (${url.substring(0, 50)}...)`);
        resolve(url);
      } else {
        console.log(`yt-dlp [${videoId}]: ${elapsed}ms -> FAILED (code ${code})`);
        resolve(null);
      }
    });
  });
}

async function run() {
  await getFastYtDlpStream('dQw4w9WgXcQ');
  await getFastYtDlpStream('3tmd-ClpJxA');
  await getFastYtDlpStream('fJ9rUzIMcZQ');
  await getFastYtDlpStream('kffacxfA7G4');
  await getFastYtDlpStream('OPf0YbXqDm0');
}
run();
