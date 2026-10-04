const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const root = path.join(__dirname, '..');
const read = file => fs.readFileSync(path.join(root, file), 'utf8');

test('PC titlebar logo is removed from HTML and hidden in CSS', () => {
  const html = read('src/index.html');
  const css = read('src/styles.css');

  // Titlebar logo span element removed from header
  assert.doesNotMatch(html, /<span class="titlebar-logo" id="titlebar-logo"/);
  // CSS hides .titlebar-logo
  assert.match(css, /\.titlebar-logo\s*\{[\s\S]*display:\s*none\s*!important/);
});

test('No fake tracks or fake artists are injected into recent sections when history is empty', () => {
  const main = read('src/main.js');

  const homeContentMatch = main.match(/async function loadHomeContent\(\)\s*\{([\s\S]*?)\n\}/);
  assert.ok(homeContentMatch, 'loadHomeContent function found');
  const homeContentBody = homeContentMatch[1];

  // loadHomeContent does not fetch search or insert mock tracks when history is empty
  assert.doesNotMatch(homeContentBody, /fetch\('\/api\/search\?q='/);
  assert.doesNotMatch(homeContentBody, /SPOTIFY_MOCK_DATA/);
  assert.doesNotMatch(homeContentBody, /The Weeknd/);

  // In renderRecentArtists, empty recent artists hide section and do not inject fake artists
  const recentArtistsMatch = main.match(/function renderRecentArtists\(\)\s*\{([\s\S]*?)\n\}/);
  assert.ok(recentArtistsMatch, 'renderRecentArtists function found');
  const recentArtistsBody = recentArtistsMatch[1];
  assert.doesNotMatch(recentArtistsBody, /Стеклянный Оркестр/);
  assert.doesNotMatch(recentArtistsBody, /Кассетный Дом/);
  assert.doesNotMatch(recentArtistsBody, /Ночной Рейс/);
});

test('Bottom floating island player is hidden when empty and slides up on play', () => {
  const html = read('src/index.html');
  const css = read('src/styles.css');
  const main = read('src/main.js');

  // HTML initializes floating island as hidden
  assert.match(html, /id="floating-island"[^>]*player-island-hidden|class="[^"]*player-island-hidden[^"]*"[^>]*id="floating-island"/);

  // CSS specifies slide transition and offscreen translation when hidden
  assert.match(css, /\.floating-island\.player-island-hidden[\s\S]*translate:\s*-50%\s*calc\(100%\s*\+\s*48px\)/);
  assert.match(css, /\.floating-island\.player-island-visible[\s\S]*translate:\s*-50%\s*0/);

  // JS has showFloatingPlayer / hideFloatingPlayer and calls showFloatingPlayer in playTrack
  assert.match(main, /function showFloatingPlayer\(\)/);
  assert.match(main, /function hideFloatingPlayer\(\)/);
  assert.match(main, /async function playTrack\([\s\S]*showFloatingPlayer\(\)/);
});

test('Desktop settings modal is redesigned with mobile-style Stitch cards and typography', () => {
  const themeCss = read('src/styles/dotify-23-theme.css');

  // Clean dark background without green color-mix wash
  assert.match(themeCss, /\.dotify-23-settings-modal[\s\S]*background:\s*#111114/);

  // Settings sections styled as rounded dark cards
  assert.match(themeCss, /\.dotify-section\s*\{[\s\S]*background:\s*#18181b[\s\S]*border-radius:\s*18px/);

  // Section titles styled as uppercase secondary labels
  assert.match(themeCss, /\.dotify-section-title\s*\{[\s\S]*text-transform:\s*uppercase[\s\S]*color:\s*#8E8E93/);

  // Navigation pill tab active state is crisp white/contrast
  assert.match(themeCss, /\.dotify-pill-tab\.active[\s\S]*background:\s*#ffffff[\s\S]*color:\s*#000000/);

  // Toggle switch has iOS-style clean green active slider
  assert.match(themeCss, /\.dotify-switch input:checked \+ \.dotify-switch-slider\s*\{[\s\S]*background-color:\s*#34C759/);
});

test('Favorites card on Home is redesigned with custom Votify hero styling, not Spotify', () => {
  const html = read('src/index.html');
  const themeCss = read('src/styles/dotify-23-theme.css');
  const main = read('src/main.js');

  // Spotify purple-to-mint gradient icon is completely removed
  assert.doesNotMatch(html, /linear-gradient\(135deg,\s*#450af5,\s*#c4efd9\)/);

  // Votify hero card structure is present with heart svg and quick play button
  assert.match(html, /class="votify-fav-hero-card"[^>]*id="tile-liked"/);
  assert.match(html, /class="fav-hero-heart-svg"/);
  assert.match(html, /id="tile-liked-play-btn"/);

  // CSS defines sleek rounded hero card styles
  assert.match(themeCss, /\.votify-fav-hero-card\s*\{[\s\S]*border-radius:\s*18px/);
  assert.match(themeCss, /\.fav-hero-icon-wrap\s*\{[\s\S]*border-radius:\s*14px/);

  // main.js wires quick play button and updates subtitle count
  assert.match(main, /safeClick\('tile-liked-play-btn'/);
  assert.match(main, /document\.getElementById\('fav-subtitle'\)/);
});

