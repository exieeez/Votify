const assert = require('node:assert/strict');
const test = require('node:test');

const {
  USERNAME_MIN,
  USERNAME_MAX,
  RESERVED_USERNAMES,
  normalizeUsername,
  validateUsername,
  usernameErrorText,
  sanitizeDisplayName,
  sanitizeBio,
  normalizeLink,
  linkUrl,
  sanitizeLinks,
  followDocId,
  isSafeHttpUrl,
  isSafeAvatar,
  buildShowcase,
} = require('../src/social-validate.js');

test('normalizes usernames like Telegram (@, spaces, case)', () => {
  assert.equal(normalizeUsername('  @ExIeEez '), 'exieeez');
  assert.equal(normalizeUsername('@@durov'), 'durov');
  assert.equal(normalizeUsername(''), '');
  assert.equal(normalizeUsername(null), '');
});

test('validates username shape', () => {
  assert.deepEqual(validateUsername('exieeez'), { ok: true, value: 'exieeez', error: '' });
  assert.deepEqual(validateUsername('@Abc_123'), {
    ok: true,
    value: 'abc_123',
    error: '',
  });
  assert.equal(validateUsername('ab').error, 'too-short');
  assert.equal(validateUsername('a'.repeat(USERNAME_MAX + 1)).error, 'too-long');
  assert.equal(validateUsername('1abc').error, 'bad-chars');
  assert.equal(validateUsername('_abc').error, 'bad-chars');
  assert.equal(validateUsername('мама').error, 'bad-chars');
  assert.equal(validateUsername('a b').error, 'bad-chars');
  assert.equal(validateUsername('').error, 'empty');
  assert.equal(USERNAME_MIN, 3);
  assert.equal(USERNAME_MAX, 32);
});

test('rejects reserved usernames', () => {
  assert.ok(RESERVED_USERNAMES.includes('votify'));
  assert.ok(RESERVED_USERNAMES.includes('admin'));
  assert.ok(RESERVED_USERNAMES.includes('support'));
  assert.equal(validateUsername('votify').error, 'reserved');
  assert.equal(validateUsername('@Admin').error, 'reserved');
});

test('explains username errors in ru and en', () => {
  assert.match(usernameErrorText('taken', 'ru'), /занят/);
  assert.match(usernameErrorText('taken', 'en'), /taken/);
  assert.match(usernameErrorText('reserved'), /зарезервирован/);
  assert.match(usernameErrorText('bad-chars'), /Латиница/);
});

test('sanitizes display names and bios', () => {
  assert.equal(sanitizeDisplayName('  Exi   eeez  '), 'Exi eeez');
  assert.equal(sanitizeDisplayName('x'.repeat(100)).length, 40);
  assert.equal(sanitizeBio('night drives • ambient').length > 0, true);
  assert.equal(sanitizeBio('x'.repeat(500)).length, 150);
});

test('normalizes social links from handles and urls', () => {
  assert.equal(normalizeLink('telegram', '@durov'), 'durov');
  assert.equal(normalizeLink('telegram', 'https://t.me/durov'), 'durov');
  assert.equal(normalizeLink('telegram', 't.me/durov?x=1'), 'durov');
  assert.equal(normalizeLink('vk', 'https://vk.com/exieeez'), 'exieeez');
  assert.equal(normalizeLink('soundcloud', 'https://soundcloud.com/artist-name'), 'artist-name');
  assert.equal(normalizeLink('telegram', 'not a handle!'), '');
  assert.equal(normalizeLink('telegram', ''), '');
  assert.equal(normalizeLink('unknown', 'x'), '');
  assert.equal(linkUrl('telegram', 'durov'), 'https://t.me/durov');
  assert.deepEqual(sanitizeLinks({ telegram: '@a_b_c', vk: '', soundcloud: null }), {
    telegram: 'a_b_c',
    soundcloud: '',
    vk: '',
  });
});

test('builds deterministic follow doc ids', () => {
  assert.equal(followDocId('aaa', 'bbb'), 'aaa_bbb');
});

test('checks safe urls and avatars', () => {
  assert.equal(isSafeHttpUrl('https://example.com/cover.jpg'), true);
  assert.equal(isSafeHttpUrl('http://example.com/x.jpg'), false);
  assert.equal(isSafeHttpUrl('https://evil@x.com/'), false);
  assert.equal(isSafeHttpUrl(''), false);
  assert.equal(isSafeAvatar(''), true);
  assert.equal(isSafeAvatar('data:image/webp;base64,AAA'), true);
  assert.equal(isSafeAvatar('https://example.com/x.png'), false);
});

test('builds a bounded public playlist showcase', () => {
  const showcase = buildShowcase({
    Избранное: [{ id: '1' }],
    main: [
      { id: '2', cover: 'https://example.com/a.jpg' },
      { id: '3', cover: '' },
    ],
    empty: [],
  });
  assert.equal(showcase.length, 2);
  assert.deepEqual(showcase[0], {
    name: 'main',
    count: 2,
    cover: 'https://example.com/a.jpg',
    tracks: [
      { id: '2', title: '', artist: '', cover: 'https://example.com/a.jpg', duration: 0 },
      { id: '3', title: '', artist: '', cover: '', duration: 0 },
    ],
  });
  assert.deepEqual(showcase[1], { name: 'empty', count: 0, cover: '', tracks: [] });

  const long = { big: Array.from({ length: 15 }, (_, i) => ({ id: `t${i}` })) };
  assert.equal(buildShowcase(long)[0].tracks.length, 10);

  const many = {};
  for (let i = 0; i < 50; i++) many[`pl-${i}`] = [];
  assert.equal(buildShowcase(many).length, 20);
  assert.deepEqual(buildShowcase(null), []);
});
