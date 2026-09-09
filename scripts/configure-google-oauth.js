const fs = require('fs');
const os = require('os');
const path = require('path');

const CLIENT_ID_PATTERN = /^\d+-[a-z0-9_-]+\.apps\.googleusercontent\.com$/i;

function expandHome(filePath) {
  if (filePath === '~') return os.homedir();
  if (filePath.startsWith(`~${path.sep}`)) return path.join(os.homedir(), filePath.slice(2));
  return filePath;
}

function readJson(filePath, label) {
  try {
    return JSON.parse(fs.readFileSync(filePath, 'utf8'));
  } catch (error) {
    throw new Error(`Не удалось прочитать ${label}: ${error.message}`);
  }
}

function configureGoogleOAuth({ sourcePath, configPath }) {
  const source = path.resolve(expandHome(sourcePath));
  const destination = path.resolve(configPath);
  const downloaded = readJson(source, 'Google OAuth JSON');
  if (!downloaded.installed || downloaded.web) {
    throw new Error('Нужен OAuth Client ID типа Desktop app (раздел installed)');
  }

  const clientId = String(downloaded.installed.client_id || '').trim();
  const clientSecret = String(downloaded.installed.client_secret || '').trim();
  if (!CLIENT_ID_PATTERN.test(clientId) || !clientSecret) {
    throw new Error('В Google OAuth JSON отсутствует корректный Client ID или Client secret');
  }

  const config = readJson(destination, 'firebase-config.json');
  if (config.private_key || config.type === 'service_account') {
    throw new Error('firebase-config.json не должен содержать Firebase Service Account');
  }
  if (!config.apiKey || !config.projectId || !config.appId) {
    throw new Error('Сначала добавьте Firebase Web Config в firebase-config.json');
  }
  const oauthProjectId = String(downloaded.installed.project_id || '').trim();
  if (oauthProjectId && oauthProjectId !== String(config.projectId).trim()) {
    throw new Error(
      `OAuth-клиент относится к проекту ${oauthProjectId}, а Firebase — к ${config.projectId}`
    );
  }

  config.googleDesktopClientId = clientId;
  config.googleDesktopClientSecret = clientSecret;
  const temporaryPath = `${destination}.${process.pid}.tmp`;
  fs.writeFileSync(temporaryPath, `${JSON.stringify(config, null, 2)}\n`, { mode: 0o600 });
  fs.renameSync(temporaryPath, destination);
  try {
    fs.chmodSync(destination, 0o600);
  } catch (error) {
    if (process.platform !== 'win32') throw error;
  }
  return { projectId: config.projectId };
}

if (require.main === module) {
  const sourcePath = process.argv[2];
  if (!sourcePath) {
    console.error('Использование: npm run configure:google -- ~/Downloads/client_secret_....json');
    process.exitCode = 1;
  } else {
    try {
      const result = configureGoogleOAuth({
        sourcePath,
        configPath: path.join(__dirname, '..', 'firebase-config.json'),
      });
      console.log(`[google-auth] Desktop OAuth добавлен для проекта ${result.projectId}`);
      console.log('[google-auth] Значения не выведены и firebase-config.json остаётся вне Git');
    } catch (error) {
      console.error(`[google-auth] ${error.message}`);
      process.exitCode = 1;
    }
  }
}

module.exports = { configureGoogleOAuth };
