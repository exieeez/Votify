const { spawn } = require('child_process');

function testYtDlp(args, label) {
  return new Promise((resolve) => {
    const start = Date.now();
    const proc = spawn('yt-dlp', args, { stdio: ['ignore', 'pipe', 'pipe'] });
    let out = '';
    proc.stdout.on('data', d => {
      out += d;
      const line = out.trim();
      if (line && line.startsWith('http')) {
        proc.kill();
        resolve({ label, elapsed: Date.now() - start, url: line.split('\n')[0] });
      }
    });
    proc.on('close', code => {
      resolve({ label, elapsed: Date.now() - start, url: out.trim().split('\n')[0] || 'NONE' });
    });
    setTimeout(() => {
      proc.kill();
      resolve({ label, elapsed: Date.now() - start, url: 'TIMEOUT' });
    }, 10000);
  });
}

async function run() {
  const videoId = 'kJQP7kiw5Fk';
  console.log(`Testing yt-dlp options for ${videoId}...`);

  const tests = [
    { label: 'Current args (android,web)', args: ['-g', '-f', 'ba/b', '--extractor-args', 'youtube:player_client=android,web', 'https://www.youtube.com/watch?v=' + videoId] },
    { label: 'Fast mweb client', args: ['-g', '-f', '140/ba/b', '--extractor-args', 'youtube:player_client=mweb', 'https://www.youtube.com/watch?v=' + videoId] },
    { label: 'Fast ios client', args: ['-g', '-f', '140/ba/b', '--extractor-args', 'youtube:player_client=ios', 'https://www.youtube.com/watch?v=' + videoId] },
    { label: 'Default yt-dlp args', args: ['-g', '-f', 'ba/b', 'https://www.youtube.com/watch?v=' + videoId] },
  ];

  for (const t of tests) {
    const res = await testYtDlp(t.args, t.label);
    console.log(`Result [${res.label}]: ${res.elapsed}ms -> URL: ${res.url ? res.url.substring(0, 70) + '...' : 'NONE'}`);
  }
}

run();
