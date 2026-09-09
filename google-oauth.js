const crypto = require('crypto');
const http = require('http');

const CALLBACK_PATH = '/oauth/google/callback';
const GOOGLE_AUTH_ENDPOINT = 'https://accounts.google.com/o/oauth2/v2/auth';
const GOOGLE_TOKEN_ENDPOINT = 'https://oauth2.googleapis.com/token';

function base64Url(buffer) {
  return buffer.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}

function isGoogleClientId(value) {
  return /^\d+-[a-z0-9_-]+\.apps\.googleusercontent\.com$/i.test(String(value || '').trim());
}

function createPkcePair() {
  const verifier = base64Url(crypto.randomBytes(64));
  const challenge = base64Url(crypto.createHash('sha256').update(verifier).digest());
  return { verifier, challenge };
}

function buildAuthorizationUrl({ clientId, redirectUri, state, challenge }) {
  const url = new URL(GOOGLE_AUTH_ENDPOINT);
  url.search = new URLSearchParams({
    client_id: clientId,
    redirect_uri: redirectUri,
    response_type: 'code',
    scope: 'openid email profile',
    state,
    code_challenge: challenge,
    code_challenge_method: 'S256',
    prompt: 'select_account',
  }).toString();
  return url.toString();
}

function callbackPage(success, message) {
  const title = success ? 'Вход выполнен' : 'Не удалось войти';
  const color = success ? '#1ed760' : '#ff6b6b';
  const safeMessage = String(message || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
  return `<!doctype html><html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${title} — Votify</title><style>body{margin:0;min-height:100vh;display:grid;place-items:center;background:#101114;color:#fff;font:16px system-ui,sans-serif}.card{width:min(420px,calc(100% - 48px));padding:32px;border:1px solid #303238;border-radius:24px;background:#1b1d22;text-align:center;box-shadow:0 24px 80px #0008}.mark{width:56px;height:56px;margin:0 auto 18px;display:grid;place-items:center;border-radius:50%;background:${color};color:#101114;font-size:30px;font-weight:800}h1{margin:0 0 10px;font-size:24px}p{margin:0;color:#b8bbc4;line-height:1.5}</style></head><body><main class="card"><div class="mark">${success ? '✓' : '!'}</div><h1>${title}</h1><p>${safeMessage}</p></main></body></html>`;
}

async function exchangeCode({ clientId, clientSecret, redirectUri, code, verifier, fetchImpl }) {
  const body = new URLSearchParams({
    client_id: clientId,
    code,
    code_verifier: verifier,
    redirect_uri: redirectUri,
    grant_type: 'authorization_code',
  });
  if (clientSecret) body.set('client_secret', clientSecret);
  const response = await fetchImpl(GOOGLE_TOKEN_ENDPOINT, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok || !payload.id_token) {
    throw new Error(payload.error_description || payload.error || 'Google не вернул ID token');
  }
  return {
    idToken: payload.id_token,
    accessToken: payload.access_token || '',
  };
}

function startGoogleOAuth({
  clientId,
  clientSecret = '',
  openExternal,
  fetchImpl = global.fetch,
  timeoutMs = 300000,
}) {
  if (!isGoogleClientId(clientId)) {
    return Promise.reject(new Error('Google Desktop OAuth Client ID не настроен'));
  }
  if (typeof openExternal !== 'function' || typeof fetchImpl !== 'function') {
    return Promise.reject(new Error('Системный вход через Google недоступен'));
  }

  const { verifier, challenge } = createPkcePair();
  const state = base64Url(crypto.randomBytes(32));

  return new Promise((resolve, reject) => {
    let finished = false;
    let timer = null;
    let redirectUri = '';

    const server = http.createServer(async (request, response) => {
      const url = new URL(request.url, 'http://127.0.0.1');
      if (url.pathname !== CALLBACK_PATH) {
        response.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
        response.end('Not found');
        return;
      }

      const returnedState = url.searchParams.get('state') || '';
      const oauthError = url.searchParams.get('error');
      const code = url.searchParams.get('code');
      if (returnedState !== state) {
        response.writeHead(400, { 'Content-Type': 'text/html; charset=utf-8' });
        response.end(callbackPage(false, 'Защитный код запроса не совпал. Вернитесь в Votify.'));
        finish(new Error('Некорректный state в ответе Google'));
        return;
      }
      if (oauthError || !code) {
        const message =
          oauthError === 'access_denied'
            ? 'Вход был отменён. Можно закрыть эту вкладку.'
            : `Google вернул ошибку: ${oauthError || 'authorization_failed'}`;
        response.writeHead(400, { 'Content-Type': 'text/html; charset=utf-8' });
        response.end(callbackPage(false, message));
        finish(new Error(message));
        return;
      }

      try {
        const tokens = await exchangeCode({
          clientId,
          clientSecret,
          redirectUri,
          code,
          verifier,
          fetchImpl,
        });
        response.writeHead(200, {
          'Content-Type': 'text/html; charset=utf-8',
          'Cache-Control': 'no-store',
        });
        response.end(callbackPage(true, 'Вернитесь в приложение — аккаунт уже подключён.'));
        finish(null, tokens);
      } catch (error) {
        response.writeHead(500, { 'Content-Type': 'text/html; charset=utf-8' });
        response.end(callbackPage(false, 'Вернитесь в Votify и повторите попытку.'));
        finish(error);
      }
    });

    function finish(error, result) {
      if (finished) return;
      finished = true;
      if (timer) clearTimeout(timer);
      server.close();
      if (error) reject(error);
      else resolve(result);
    }

    server.once('error', error => finish(error));
    server.listen(0, '127.0.0.1', async () => {
      const address = server.address();
      redirectUri = `http://127.0.0.1:${address.port}${CALLBACK_PATH}`;
      const authorizationUrl = buildAuthorizationUrl({
        clientId,
        redirectUri,
        state,
        challenge,
      });
      try {
        await openExternal(authorizationUrl);
      } catch (error) {
        finish(error);
      }
    });

    timer = setTimeout(
      () => finish(new Error('Время ожидания входа через Google истекло')),
      timeoutMs
    );
  });
}

module.exports = {
  GOOGLE_AUTH_ENDPOINT,
  GOOGLE_TOKEN_ENDPOINT,
  buildAuthorizationUrl,
  createPkcePair,
  isGoogleClientId,
  startGoogleOAuth,
};
