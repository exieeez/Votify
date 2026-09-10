const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const test = require('node:test');

// Isolated static root so the test never touches the real ./src.
const root = fs.mkdtempSync(path.join(os.tmpdir(), 'votify-static-'));
fs.writeFileSync(path.join(root, 'index.html'), '<h1>hi</h1>');
fs.writeFileSync(path.join(root, 'app.js'), 'console.log(1)');
fs.writeFileSync(path.join(root, 'style.css'), 'body{}');
process.env.VOTIFY_SRC_DIR = root;

const { serveStatic } = require('../routes/utils.js');

function mockRes() {
  return {
    status: null,
    headers: null,
    body: null,
    writeHead(status, headers) {
      this.status = status;
      this.headers = headers;
    },
    end(body) {
      this.body = body;
    },
  };
}

test('serveStatic is exported', () => {
  assert.equal(typeof serveStatic, 'function');
});

test('serves index for / with html mime and no-store', async () => {
  const res = mockRes();
  await serveStatic('/', res);
  assert.equal(res.status, 200);
  assert.equal(res.headers['Content-Type'], 'text/html; charset=utf-8');
  assert.match(res.headers['Cache-Control'], /no-store/);
  assert.equal(String(res.body), '<h1>hi</h1>');
});

test('serves js and css with correct mime types', async () => {
  const js = mockRes();
  await serveStatic('/app.js', js);
  assert.equal(js.status, 200);
  assert.equal(js.headers['Content-Type'], 'application/javascript; charset=utf-8');

  const css = mockRes();
  await serveStatic('/style.css', css);
  assert.equal(css.status, 200);
  assert.equal(css.headers['Content-Type'], 'text/css; charset=utf-8');
});

test('returns 404 for missing files', async () => {
  const res = mockRes();
  await serveStatic('/nope.js', res);
  assert.equal(res.status, 404);
});

test('blocks directory traversal', async () => {
  const res = mockRes();
  await serveStatic('/..%2F..%2Fetc%2Fpasswd', res);
  assert.ok([403, 404].includes(res.status));

  const res2 = mockRes();
  await serveStatic('/%2e%2e/%2e%2e/package.json', res2);
  assert.ok([403, 404].includes(res2.status));
});
