const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const root = path.join(__dirname, '..');
const read = file => fs.readFileSync(path.join(root, file), 'utf8');

test('home keeps recent artists in one horizontally navigable row', () => {
  const html = read('src/index.html');
  const css = read('src/home-personalization.css');
  const main = read('src/main.js');

  assert.match(
    html,
    /id="home-recent-artists"[^>]*artists-carousel|class="artists-grid artists-carousel"[^>]*id="home-recent-artists"/
  );
  assert.match(html, /id="recent-artists-prev"/);
  assert.match(html, /id="recent-artists-next"/);
  assert.match(css, /\.artists-grid\.artists-carousel[\s\S]*display:\s*flex/);
  assert.match(css, /\.artists-grid\.artists-carousel[\s\S]*overflow-x:\s*auto/);
  assert.match(css, /\.artists-carousel \.artist-card[\s\S]*flex:\s*0 0/);
  assert.match(main, /function scrollRecentArtists\(direction\)/);
  assert.match(main, /scrollBy\(\{ left: distance \* direction, behavior: 'smooth' \}\)/);
});

test('For You section on Home uses playlist and listening-history wave seeds', () => {
  const html = read('src/index.html');
  const main = read('src/main.js');

  assert.match(html, /id="home-screen"[\s\S]*id="home-for-you-section"/);
  assert.match(html, /id="home-for-you-section"[\s\S]*id="for-you-results"/);
  assert.match(html, /id="for-you-refresh"/);
  assert.doesNotMatch(html, /id="nav-for-you-btn"/);
  assert.doesNotMatch(html, /id="for-you-screen"/);
  assert.doesNotMatch(main, /safeClick\('nav-for-you-btn'/);
  assert.match(
    main,
    /function gatherWaveSeeds\(\)[\s\S]*Object\.entries\(playlists\)[\s\S]*listeningHistory/
  );
  assert.match(main, /async function loadForYouContent\(forceReload = false\)/);
  assert.match(main, /loadForYouContent[\s\S]*fetchWaveTracks\(seeds, 30\)/);
  assert.match(main, /function loadHomeContent\(\)[\s\S]*loadForYouContent\(\)/);
});
