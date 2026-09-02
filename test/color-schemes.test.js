const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const schemes = require('../src/color-schemes.js');

const {
  COLOR_SCHEME_LIMIT,
  COLOR_SCHEME_NAME_MAX,
  COLOR_SCHEME_FIELDS,
  COLOR_SCHEME_DEFAULTS,
  SETTINGS_COLOR_KEYS,
  normalizeHexColor,
  sanitizeSchemeName,
  sanitizeColorScheme,
  sanitizeSavedColorSchemes,
  createColorScheme,
  colorSchemesEqual,
  nextColorSchemeName,
  saveColorScheme,
  applyColorScheme,
  deleteColorScheme,
  colorSchemeToSettings,
  readColorSchemeFromSettings,
} = schemes;

function sampleColors(overrides = {}) {
  return {
    accent: '#1DB954',
    background: '#121212',
    text: '#FFFFFF',
    cards: '#181818',
    borders: '#2A2A2A',
    focus: '#1DB954',
    ...overrides,
  };
}

test('normalizes 6-digit, 3-digit and rgb colors to uppercase hex', () => {
  assert.equal(normalizeHexColor('#1db954'), '#1DB954');
  assert.equal(normalizeHexColor('#abc'), '#AABBCC');
  assert.equal(normalizeHexColor('rgb(29, 185, 84)'), '#1DB954');
  assert.equal(normalizeHexColor('rgba(10, 20, 30, 0.4)'), '#0A141E');
  assert.equal(normalizeHexColor('not-a-color', '#112233'), '#112233');
});

test('sanitizes scheme names and color payloads', () => {
  assert.equal(sanitizeSchemeName('  Моя   схема  '), 'Моя схема');
  assert.equal(sanitizeSchemeName('x'.repeat(80)).length, COLOR_SCHEME_NAME_MAX);
  assert.equal(sanitizeSchemeName('   '), 'Схема');

  const cleaned = sanitizeColorScheme({
    id: ' scheme-1 ',
    name: ' Neon ',
    createdAt: '1700000000000',
    colors: {
      accent: '#1db',
      background: 'rgb(18,18,18)',
      text: 'nope',
      cards: '#222',
      borders: '#333333',
      focus: '#0f0',
    },
  });

  assert.equal(cleaned.id, 'scheme-1');
  assert.equal(cleaned.name, 'Neon');
  assert.equal(cleaned.createdAt, 1700000000000);
  assert.equal(cleaned.colors.accent, '#11DDBB');
  assert.equal(cleaned.colors.background, '#121212');
  assert.equal(cleaned.colors.text, COLOR_SCHEME_DEFAULTS.text);
  assert.equal(cleaned.colors.cards, '#222222');
  assert.equal(cleaned.colors.focus, '#00FF00');
  assert.deepEqual(Object.keys(cleaned.colors), COLOR_SCHEME_FIELDS);
});

test('sanitizeSavedColorSchemes drops invalid items, duplicates and extras over 12', () => {
  const list = [
    { id: '', name: 'empty' },
    { id: 'a', name: 'One', colors: sampleColors() },
    { id: 'a', name: 'Dup', colors: sampleColors({ accent: '#FF0000' }) },
    ...Array.from({ length: 14 }, (_, index) => ({
      id: `extra-${index}`,
      name: `Extra ${index}`,
      colors: sampleColors({ accent: `#${String(index + 10).padStart(6, '0')}` }),
    })),
  ];
  const sanitized = sanitizeSavedColorSchemes(list);
  assert.equal(sanitized.length, COLOR_SCHEME_LIMIT);
  assert.equal(sanitized[0].id, 'a');
  assert.equal(new Set(sanitized.map(item => item.id)).size, COLOR_SCHEME_LIMIT);
});

test('saveColorScheme stores up to 12 unique schemes and rejects duplicates', () => {
  let list = [];
  for (let index = 0; index < COLOR_SCHEME_LIMIT; index += 1) {
    const result = saveColorScheme(list, {
      name: `Схема ${index + 1}`,
      colors: sampleColors({ accent: `#${String(index + 1).padStart(6, 'A')}` }),
      now: 1_700_000_000_000 + index,
    });
    assert.equal(result.ok, true);
    list = result.schemes;
  }
  assert.equal(list.length, 12);

  const overflow = saveColorScheme(list, {
    name: 'Схема 13',
    colors: sampleColors({ accent: '#ABCDEF' }),
  });
  assert.equal(overflow.ok, false);
  assert.equal(overflow.reason, 'limit');
  assert.equal(overflow.schemes.length, 12);

  const duplicate = saveColorScheme(list, {
    name: 'Копия',
    colors: list[0].colors,
  });
  assert.equal(duplicate.ok, false);
  assert.equal(duplicate.reason, 'duplicate');
  assert.equal(duplicate.scheme.id, list[0].id);
});

test('apply and delete operate on sanitized saved schemes', () => {
  const first = createColorScheme({
    id: 'scheme-a',
    name: 'Лес',
    colors: sampleColors({ accent: '#228B22' }),
    now: 10,
  });
  const second = createColorScheme({
    id: 'scheme-b',
    name: 'Ночь',
    colors: sampleColors({ background: '#000000' }),
    now: 20,
  });
  const list = [first, second];

  assert.equal(applyColorScheme(list, 'scheme-b').name, 'Ночь');
  assert.equal(applyColorScheme(list, 'missing'), null);

  const remaining = deleteColorScheme(list, 'scheme-a');
  assert.deepEqual(
    remaining.map(item => item.id),
    ['scheme-b']
  );
  assert.equal(colorSchemesEqual(first, second), false);
});

test('maps settings keys for Accent, Background, Text, Cards, Borders and Focus', () => {
  assert.deepEqual(SETTINGS_COLOR_KEYS, {
    accent: 'customColorPrimary',
    background: 'customColorBg',
    text: 'customColorText',
    cards: 'customColorCards',
    borders: 'customColorBorders',
    focus: 'customColorFocus',
  });

  const scheme = createColorScheme({
    id: 'mapped',
    name: 'Карта',
    colors: {
      accent: '#ff0000',
      background: '#010101',
      text: '#eeeeee',
      cards: '#111111',
      borders: '#333333',
      focus: '#00ffaa',
    },
  });
  const settings = colorSchemeToSettings(scheme);
  assert.equal(settings.accent, '#FF0000');
  assert.equal(settings.customColorPrimary, '#FF0000');
  assert.equal(settings.customColorBg, '#010101');
  assert.equal(settings.customColorText, '#EEEEEE');
  assert.equal(settings.customColorCards, '#111111');
  assert.equal(settings.customColorBorders, '#333333');
  assert.equal(settings.customColorFocus, '#00FFAA');
  assert.equal(settings.activeColorSchemeId, 'mapped');

  const readBack = readColorSchemeFromSettings(settings);
  assert.deepEqual(readBack, scheme.colors);
});

test('nextColorSchemeName increments past existing names', () => {
  assert.equal(nextColorSchemeName([], 'ru'), 'Схема 1');
  assert.equal(
    nextColorSchemeName([{ id: '1', name: 'Схема 1', colors: sampleColors() }], 'ru'),
    'Схема 2'
  );
  assert.equal(nextColorSchemeName([], 'en'), 'Scheme 1');
});

test('settings UI wires the save button, name field and scheme list', () => {
  const html = fs.readFileSync(path.join(__dirname, '..', 'src', 'index.html'), 'utf8');
  assert.match(html, /Применить свою тему/);
  assert.match(html, /id="btn-custom-theme-apply"/);
  assert.match(html, /id="btn-custom-theme-save"/);
  assert.match(html, /Сохранить схему/);
  assert.match(html, /id="color-scheme-name-input"/);
  assert.match(html, /maxlength="40"/);
  assert.match(html, /id="saved-color-schemes"/);
  assert.match(html, /id="saved-color-schemes-count"/);
  assert.match(html, /<script src="color-schemes\.js(\?[^"]*)?"><\/script>/);
  assert.match(html, /<link rel="stylesheet" href="custom-theme.css"\s*\/?>/);
  assert.ok(html.indexOf('styles.css') < html.indexOf('custom-theme.css'));
  assert.ok(html.indexOf('color-schemes.js') < html.indexOf('src="main.js"'));
  assert.match(html, /id="picker-color-primary"/);
  assert.match(html, /id="picker-color-bg"/);
  assert.match(html, /id="picker-color-text"/);
  assert.match(html, /id="picker-color-cards"/);
  assert.match(html, /id="picker-color-borders"/);
  assert.match(html, /id="picker-color-focus"/);
  [
    'crossfade-duration',
    'background-blur-slider',
    'font-size-slider',
    'corner-radius-slider',
    'slider-ui-scale',
    'bg-brightness-slider',
    'bg-blur-slider',
    'ui-transparency-slider',
    'slider-particle-count',
    'slider-particle-speed',
    'slider-particle-size',
  ].forEach(id => {
    assert.match(
      html,
      new RegExp(`class="settings-range"[^>]*id="${id}"|id="${id}"[^>]*class="settings-range"`)
    );
  });
});

test('Wave/iOS/thin player slider styles do not apply to settings-range', () => {
  const css = fs.readFileSync(path.join(__dirname, '..', 'src', 'styles.css'), 'utf8');
  assert.match(
    css,
    /body\[data-player-slider-type="thin"\] input\[type="range"\]:not\(\.settings-range\)::-webkit-slider-thumb/
  );
  assert.match(
    css,
    /body\[data-player-slider-type="ios"\] input\[type="range"\]:not\(\.settings-range\)::-webkit-slider-thumb/
  );
  assert.match(
    css,
    /body\[data-player-slider-type="wave"\] #page-player-progress::-webkit-slider-runnable-track/
  );
  assert.match(
    css,
    /body\[data-player-slider-type="wave"\] #page-player-progress::-webkit-slider-thumb/
  );
  assert.match(css, /M0 12 C10 2 20 2 30 12 S50 22 60 12 S70 2 80 12/);
  assert.match(
    css,
    /#page-player-progress::-webkit-slider-runnable-track[\s\S]{0,700}var\(--accent\)/
  );
  assert.doesNotMatch(css, /fill='%231DB954'/);
  assert.doesNotMatch(
    css,
    /body\[data-player-slider-type="ios"\] input\[type="range"\]::-webkit-slider-thumb \{/
  );
});

test('cover shape setting applies to every player cover', () => {
  const html = fs.readFileSync(path.join(__dirname, '..', 'src', 'index.html'), 'utf8');
  const css = fs.readFileSync(path.join(__dirname, '..', 'src', 'styles.css'), 'utf8');
  const main = fs.readFileSync(path.join(__dirname, '..', 'src', 'main.js'), 'utf8');
  assert.match(html, /id="setting-cover-shape"/);
  assert.match(html, /Для всех плееров/);
  assert.match(main, /document\.body\.dataset\.playerCoverShape = shapeKey/);
  assert.match(
    main,
    /\.pp-cover-wrap, \.player-bar-cover-wrap, \.right-player-cover-shell, \.fs-cover-container/
  );
  assert.match(css, /data-player-cover-shape="circle"[\s\S]{0,700}img\.right-player-cover/);
  assert.match(css, /data-player-cover-shape="circle"[\s\S]{0,900}img\.fs-cover/);
  assert.match(css, /data-player-cover-shape="square"[\s\S]{0,700}border-radius:\s*0\s*!important/);
});

test('cover animation None overrides vinyl and fullscreen cover animation', () => {
  const css = fs.readFileSync(path.join(__dirname, '..', 'src', 'styles.css'), 'utf8');
  const main = fs.readFileSync(path.join(__dirname, '..', 'src', 'main.js'), 'utf8');
  assert.match(
    css,
    /body\[data-cover-animation="none"\] img\.fs-cover,[\s\S]{0,1500}animation:\s*none\s*!important/
  );
  assert.match(
    css,
    /body\[data-cover-animation="none"\] \.fs-player-cover-wrap,[\s\S]{0,800}animation:\s*none\s*!important/
  );
  assert.match(
    main,
    /if \(anim === 'none'\) \{[\s\S]{0,120}setProperty\('animation', 'none', 'important'\)/
  );
});

test('custom theme paint layer overrides hard-coded glass and settings colors', () => {
  const css = fs.readFileSync(path.join(__dirname, '..', 'src', 'custom-theme.css'), 'utf8');
  assert.match(css, /\.settings-modal,[\s\S]*background:\s*var\(--bg-surface\)\s*!important/);
  assert.match(css, /\.bg-overlay[\s\S]*var\(--bg-base\)/);
  assert.match(css, /#ui-theme-presets button\.active[\s\S]*var\(--accent\)/);
  assert.match(css, /\.home-quick-card,[\s\S]*var\(--bg-surface\)/);
  assert.doesNotMatch(css, /background:\s*#12141d\s*!important/);
});

test('settings sliders are a regular line of the current var(--accent)', () => {
  const css = fs.readFileSync(path.join(__dirname, '..', 'src', 'styles.css'), 'utf8');
  assert.match(css, /\.settings-overlay input\[type="range"\]/);
  assert.match(css, /input\.settings-range/);
  assert.match(
    css,
    /input\.settings-range::-webkit-slider-runnable-track[\s\S]{0,500}var\(--accent\)/
  );
  assert.match(css, /background-image:\s*none\s*!important/);
});

test('main.js stores schemes in existing settings sync and wires apply/delete', () => {
  const main = fs.readFileSync(path.join(__dirname, '..', 'src', 'main.js'), 'utf8');
  assert.match(main, /btn-custom-theme-apply/);
  assert.match(main, /savedColorSchemes/);
  assert.match(main, /sanitizeSavedColorSchemes/);
  assert.match(main, /function getCloudSafeSettings\([\s\S]*savedColorSchemes/);
  assert.match(main, /settings-range/);
  assert.match(main, /applySavedColorSchemeById/);
  assert.match(main, /deleteSavedColorSchemeById/);
  assert.match(main, /saveCurrentColorScheme/);
  assert.match(main, /function bindCustomColorPickers\(\)/);
  assert.match(main, /function bindThemeColorPresets\(\)/);
  assert.match(main, /const THEME_COLOR_PRESETS =/);
  assert.match(main, /customColorBg: background/);
  assert.match(main, /function applyCurrentCustomColors/);
  assert.match(main, /function buildPlayerWavePath\(width, height, endX = width\)/);
  assert.match(main, /drawWaveTimelinePaths\(rightTlBg, rightTlActive/);
  assert.match(main, /drawWaveTimelinePaths\(barTlBg, barTlActive/);
  assert.doesNotMatch(main, /picker\.addEventListener\('input', commitColor\)/);
  assert.match(main, /picker\.addEventListener\('change', commitColor\)/);
  assert.match(main, /_committedColorValue/);
  assert.match(
    main,
    /function saveCurrentColorScheme\([\s\S]*applyAccentColor\(result\.scheme\.colors\.accent\);[\s\S]*applyCustomColors\(\)/
  );
  assert.match(main, /initSavedColorSchemes\(\);/);
});

test('firebase client still syncs personal settings without a new collection', () => {
  const client = fs.readFileSync(path.join(__dirname, '..', 'src', 'firebase-client.js'), 'utf8');
  assert.match(client, /profileRef\(uid\)\.collection\('sync'\)\.doc\(name\)/);
  assert.match(client, /async function pushState\(\{ settings, playlists, history \}\)/);
  assert.match(client, /async function pullState\(/);
  assert.doesNotMatch(client, /collection\('savedColorSchemes'\)/);
  assert.doesNotMatch(client, /collection\('colorSchemes'\)/);
});
