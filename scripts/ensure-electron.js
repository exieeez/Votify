const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

function findElectronPackage() {
  try {
    return path.dirname(require.resolve('electron/package.json'));
  } catch {
    return null;
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

const electronDir = findElectronPackage();
if (!electronDir) {
  console.error('[electron] Package is missing. Run npm install first.');
  process.exit(1);
}

if (getInstalledExecutable(electronDir)) process.exit(0);

const installer = path.join(electronDir, 'install.js');
if (!fs.existsSync(installer)) {
  console.error('[electron] Installer is missing. Remove node_modules and run npm install again.');
  process.exit(1);
}

console.log('[electron] Binary is missing because its install script was blocked; repairing...');
const installerEnv = { ...process.env };
// These variables are useful in CI, but make an interactive desktop checkout
// silently skip the binary that `npm start` requires. The repair is explicit,
// so always install into this project's local Electron package.
delete installerEnv.ELECTRON_SKIP_BINARY_DOWNLOAD;
delete installerEnv.ELECTRON_OVERRIDE_DIST_PATH;

const result = spawnSync(process.execPath, [installer], {
  cwd: electronDir,
  env: installerEnv,
  stdio: 'inherit',
});

if (result.error) {
  console.error(`[electron] Repair failed: ${result.error.message}`);
  process.exit(1);
}
if (result.status !== 0 || !getInstalledExecutable(electronDir)) {
  console.error('[electron] Repair did not produce a usable Electron binary.');
  process.exit(result.status || 1);
}

console.log('[electron] Binary installed successfully.');
