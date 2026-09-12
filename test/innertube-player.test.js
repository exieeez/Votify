const test = require('node:test');
const assert = require('node:assert/strict');

const { pickAudioFormat } = require('../routes/utils.js');

// Mirrors an InnerTube ANDROID adaptiveFormats list (bitrate in bps).
const FORMATS = [
  { itag: 249, mimeType: 'audio/webm; codecs="opus"', bitrate: 50000, url: 'https://a/249' },
  { itag: 250, mimeType: 'audio/webm; codecs="opus"', bitrate: 70000, url: 'https://a/250' },
  { itag: 140, mimeType: 'audio/mp4; codecs="mp4a.40.2"', bitrate: 128000, url: 'https://a/140' },
  { itag: 251, mimeType: 'audio/webm; codecs="opus"', bitrate: 160000, url: 'https://a/251' },
  // Must be ignored: video track.
  { itag: 22, mimeType: 'video/mp4; codecs="avc1"', bitrate: 2000000, url: 'https://a/22' },
  // Must be ignored: no direct url (needs deciphering).
  { itag: 141, mimeType: 'audio/mp4; codecs="mp4a.40.2"', bitrate: 256000, signatureCipher: 'x' },
];

test('high picks the max-bitrate audio with a direct url', () => {
  const f = pickAudioFormat(FORMATS, 'high');
  assert.equal(f.itag, 251);
  assert.equal(f.url, 'https://a/251');
});

test('medium picks the first audio >= 96k', () => {
  const f = pickAudioFormat(FORMATS, 'medium');
  assert.equal(f.itag, 140);
});

test('low picks the first audio >= 48k', () => {
  const f = pickAudioFormat(FORMATS, 'low');
  assert.equal(f.itag, 249);
});

test('medium falls back to max when nothing reaches 96k', () => {
  const tiny = FORMATS.filter(f => (f.bitrate || 0) < 96000);
  const f = pickAudioFormat(tiny, 'medium');
  assert.equal(f.itag, 250);
});

test('low falls back to min when nothing reaches 48k', () => {
  const tiny = [{ itag: 249, mimeType: 'audio/webm', bitrate: 24000, url: 'https://a/249' }];
  const f = pickAudioFormat(tiny, 'low');
  assert.equal(f.itag, 249);
});

test('returns null when no playable audio exists', () => {
  assert.equal(pickAudioFormat([], 'high'), null);
  assert.equal(pickAudioFormat(null, 'high'), null);
  assert.equal(pickAudioFormat([{ itag: 22, mimeType: 'video/mp4', url: 'https://a/22' }], 'high'), null);
  assert.equal(
    pickAudioFormat(
      [{ itag: 141, mimeType: 'audio/mp4', bitrate: 256000, signatureCipher: 'x' }],
      'high'
    ),
    null
  );
});
