const fs = require('fs');
const path = require('path');
const https = require('https');
const { execSync } = require('child_process');

const BIN_DIR = path.join(__dirname, '..', 'bin');
const YT_DLP_LINUX_URL = 'https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux';
const YT_DLP_WIN_URL = 'https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe';

if (!fs.existsSync(BIN_DIR)) {
  fs.mkdirSync(BIN_DIR, { recursive: true });
}

const ytDlpLinuxPath = path.join(BIN_DIR, 'yt-dlp');
const ytDlpWinPath = path.join(BIN_DIR, 'yt-dlp.exe');

async function downloadFile(url, dest) {
  return new Promise((resolve, reject) => {
    const file = fs.createWriteStream(dest);
    const request = url => {
      https
        .get(url, response => {
          if (response.statusCode === 301 || response.statusCode === 302) {
            if (response.headers.location) {
              request(response.headers.location);
              return;
            }
          }
          if (response.statusCode !== 200) {
            reject(new Error(`Failed to download: ${response.statusCode}`));
            return;
          }
          response.pipe(file);
          file.on('finish', () => file.close(resolve));
          file.on('error', err => {
            fs.unlink(dest, () => {});
            reject(err);
          });
        })
        .on('error', reject);
    };
    request(url);
  });
}

async function main() {
  try {
    console.log('Downloading yt-dlp binaries for Linux & Windows...');
    await downloadFile(YT_DLP_LINUX_URL, ytDlpLinuxPath);
    try { fs.chmodSync(ytDlpLinuxPath, 0o755); } catch(e) {}
    console.log('yt-dlp Linux downloaded:', ytDlpLinuxPath);

    await downloadFile(YT_DLP_WIN_URL, ytDlpWinPath);
    console.log('yt-dlp Windows downloaded:', ytDlpWinPath);
  } catch (err) {
    console.error('Failed to download yt-dlp binaries:', err.message);
  }
}

main();
