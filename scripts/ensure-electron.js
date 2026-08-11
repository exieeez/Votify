const fs = require('fs');
const path = require('path');

function findElectronPackage() {
  try {
    return path.dirname(require.resolve('electron/package.json'));
  } catch {
    return null;
  }
}

function getPlatformExecutable(platform = process.platform) {
  switch (platform) {
    case 'darwin':
    case 'mas':
      return 'Electron.app/Contents/MacOS/Electron';
    case 'win32':
      return 'electron.exe';
    case 'freebsd':
    case 'openbsd':
    case 'linux':
      return 'electron';
    default:
      throw new Error(`Electron builds are not available for platform: ${platform}`);
  }
}

function getInstalledExecutable(electronDir) {
  const pathFile = path.join(electronDir, 'path.txt');
  if (!fs.existsSync(pathFile)) return null;
  const executableName = fs.readFileSync(pathFile, 'utf8').trim();
  if (!executableName) return null;
  const executablePath = path.join(electronDir, 'dist', executableName);
  return fs.existsSync(executablePath) ? executablePath : null;
}

function linkSystemElectron(electronDir) {
  if (process.platform !== 'linux') return null;
  const candidates = [process.env.VOTIFY_SYSTEM_ELECTRON, '/usr/bin/electron35'];
  const systemElectron = candidates.find(candidate => candidate && fs.existsSync(candidate));
  if (!systemElectron) return null;

  const distDir = path.join(electronDir, 'dist');
  const localElectron = path.join(distDir, 'electron');
  fs.rmSync(distDir, { recursive: true, force: true });
  fs.mkdirSync(distDir, { recursive: true });
  fs.symlinkSync(systemElectron, localElectron);
  fs.writeFileSync(path.join(electronDir, 'path.txt'), 'electron');
  console.log(`[electron] Using system Electron: ${systemElectron}`);
  return localElectron;
}

async function installElectronBinary(electronDir) {
  const { downloadArtifact } = require('@electron/get');
  const extract = require('extract-zip');
  const electronPackage = require(path.join(electronDir, 'package.json'));
  const platform = process.env.npm_config_platform || process.platform;
  const arch = process.env.npm_config_arch || process.arch;
  const executableName = getPlatformExecutable(platform);

  console.log(
    `[electron] Downloading Electron ${electronPackage.version} for ${platform}-${arch}...`
  );
  const zipPath = await downloadArtifact({
    version: electronPackage.version,
    artifactName: 'electron',
    platform,
    arch,
    force: process.env.force_no_cache === 'true',
    cacheRoot: process.env.electron_config_cache,
    checksums: require(path.join(electronDir, 'checksums.json')),
  });

  const distDir = path.join(electronDir, 'dist');
  fs.rmSync(distDir, { recursive: true, force: true });
  fs.mkdirSync(distDir, { recursive: true });
  await extract(zipPath, { dir: distDir });

  const extractedTypes = path.join(distDir, 'electron.d.ts');
  if (fs.existsSync(extractedTypes)) {
    fs.renameSync(extractedTypes, path.join(electronDir, 'electron.d.ts'));
  }
  fs.writeFileSync(path.join(electronDir, 'path.txt'), executableName);

  const executablePath = path.join(distDir, executableName);
  if (process.platform !== 'win32' && fs.existsSync(executablePath)) {
    fs.chmodSync(executablePath, 0o755);
  }
  return executablePath;
}

async function main() {
  const electronDir = findElectronPackage();
  if (!electronDir) {
    throw new Error('Electron package is missing. Run npm install first.');
  }

  if (getInstalledExecutable(electronDir)) return;

  const systemElectron = linkSystemElectron(electronDir);
  if (systemElectron) return;

  console.log('[electron] Binary is missing; starting direct repair...');
  const executablePath = await installElectronBinary(electronDir);
  if (!fs.existsSync(executablePath) || !getInstalledExecutable(electronDir)) {
    throw new Error('Repair finished without producing a usable Electron binary.');
  }
  console.log(`[electron] Binary installed successfully: ${executablePath}`);
}

main().catch(error => {
  console.error(`[electron] Repair failed: ${error?.stack || error}`);
  if (process.platform === 'linux') {
    console.error(
      '[electron] Arch Linux fallback: install the official package with `sudo pacman -S electron35`, then run `npm start` again.'
    );
  }
  process.exit(1);
});
