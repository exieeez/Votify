const fs = require('fs');
const path = require('path');
const https = require('https');
const { execSync } = require('child_process');

const BIN_DIR = path.join(__dirname, '..', 'bin');
const YT_DLP_URL = 'https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux';

if (!fs.existsSync(BIN_DIR)) {
  fs.mkdirSync(BIN_DIR, { recursive: true });
}

const ytDlpPath = path.join(BIN_DIR, 'yt-dlp');

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
    console.log('Downloading yt-dlp for Linux...');
    await downloadFile(YT_DLP_URL, ytDlpPath);
    fs.chmodSync(ytDlpPath, 0o755);
    console.log('yt-dlp downloaded and made executable:', ytDlpPath);

    // Verify it works
    try {
      execSync(`${ytDlpPath} --version`, { stdio: 'pipe' });
      console.log('yt-dlp verified successfully');
    } catch (e) {
      console.warn('yt-dlp verification failed, but file exists');
    }
  } catch (err) {
    console.error('Failed to download yt-dlp:', err.message);
    process.exit(1);
  }
}

main();
