const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const test = require('node:test');
const { configureGoogleOAuth } = require('../scripts/configure-google-oauth.js');

function fixture() {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'votify-google-config-'));
  const sourcePath = path.join(directory, 'desktop-client.json');
  const configPath = path.join(directory, 'firebase-config.json');
  fs.writeFileSync(
    sourcePath,
    JSON.stringify({
      installed: {
        client_id: '123456-example.apps.googleusercontent.com',
        client_secret: 'desktop-client-key',
        project_id: 'votify-test',
        redirect_uris: ['http://localhost'],
      },
    })
  );
  fs.writeFileSync(
    configPath,
    JSON.stringify({
      apiKey: 'public-api-key',
      authDomain: 'votify-test.firebaseapp.com',
      projectId: 'votify-test',
      appId: 'public-app-id',
    })
  );
  return { directory, sourcePath, configPath };
}

test('imports a Desktop OAuth download into Firebase Web Config', t => {
  const files = fixture();
  t.after(() => fs.rmSync(files.directory, { recursive: true, force: true }));

  const result = configureGoogleOAuth(files);
  const merged = JSON.parse(fs.readFileSync(files.configPath, 'utf8'));
  assert.equal(result.projectId, 'votify-test');
  assert.equal(merged.googleDesktopClientId, '123456-example.apps.googleusercontent.com');
  assert.equal(merged.googleDesktopClientSecret, 'desktop-client-key');
  if (process.platform !== 'win32') {
    assert.equal(fs.statSync(files.configPath).mode & 0o777, 0o600);
  }
});

test('refuses an OAuth client from another Google Cloud project', t => {
  const files = fixture();
  t.after(() => fs.rmSync(files.directory, { recursive: true, force: true }));
  const config = JSON.parse(fs.readFileSync(files.configPath, 'utf8'));
  config.projectId = 'different-project';
  fs.writeFileSync(files.configPath, JSON.stringify(config));

  assert.throws(() => configureGoogleOAuth(files), /OAuth-клиент относится к проекту/);
});
