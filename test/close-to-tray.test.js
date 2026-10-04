const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

test('PC configuration enables closeToTray and disables backgroundThrottling for uninterrupted playback', () => {
  const mainJsPath = path.join(__dirname, '..', 'main.js');
  const mainJs = fs.readFileSync(mainJsPath, 'utf8');

  // Verify closeToTray is enabled by default in main process
  assert.match(mainJs, /let closeToTrayEnabled = true;/);

  // Verify backgroundThrottling is set to false in BrowserWindow webPreferences
  assert.match(mainJs, /backgroundThrottling:\s*false/);

  // Verify close event prevents default and hides window when closeToTray is active
  assert.match(mainJs, /mainWindow\.on\('close',\s*event\s*=>\s*\{[\s\S]*if\s*\(closeToTrayEnabled\s*&&\s*!isQuitting\)\s*\{[\s\S]*event\.preventDefault\(\);[\s\S]*mainWindow\.hide\(\);/);

  // Verify tray has restore handling and player actions
  assert.match(mainJs, /mainWindow\.restore\(\);/);
  assert.match(mainJs, /mainWindow\.webContents\.send\('player-action',\s*'play-pause'\);/);
});

test('desktop preload exposes setCloseToTray and onPlayerAction', () => {
  const preloadPath = path.join(__dirname, '..', 'preload.js');
  const preloadJs = fs.readFileSync(preloadPath, 'utf8');

  assert.match(preloadJs, /setCloseToTray:\s*enabled\s*=>\s*ipcRenderer\.send\('set-close-to-tray',\s*enabled\)/);
  assert.match(preloadJs, /onPlayerAction:\s*cb\s*=>\s*ipcRenderer\.on\('player-action',\s*\(e,\s*action\)\s*=>\s*cb\(action\)\)/);
});

test('renderer defaults closeToTray to true and wires player actions', () => {
  const srcMainJsPath = path.join(__dirname, '..', 'src', 'main.js');
  const srcMainJs = fs.readFileSync(srcMainJsPath, 'utf8');

  // Verify default in appSettings
  assert.match(srcMainJs, /closeToTray:\s*true,/);

  // Verify UI toggle is wired to sync to Electron
  assert.match(srcMainJs, /wireInput\('toggle-close-to-tray',\s*'closeToTray',\s*true/);

  // Verify onPlayerAction listener triggers playback controls
  assert.match(srcMainJs, /window\.electronAPI\.onPlayerAction/);
});
