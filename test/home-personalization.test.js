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
  const css = read('src/home-personalization.css');
  const main = read('src/main.js');

  assert.match(html, /id="home-screen"[\s\S]*id="home-for-you-section"/);
  assert.match(html, /id="home-for-you-section"[\s\S]*id="for-you-results"/);
  assert.match(html, /class="rec-grid for-you-carousel"/);
  assert.match(html, /id="for-you-prev"/);
  assert.match(html, /id="for-you-next"/);
  assert.match(html, /id="for-you-refresh"/);
  assert.doesNotMatch(html, /id="nav-for-you-btn"/);
  assert.doesNotMatch(html, /id="for-you-screen"/);
  assert.doesNotMatch(main, /safeClick\('nav-for-you-btn'/);
  assert.match(css, /\.rec-grid\.for-you-carousel[\s\S]*display:\s*flex/);
  assert.match(css, /\.rec-grid\.for-you-carousel[\s\S]*overflow-x:\s*auto/);
  assert.match(
    main,
    /function gatherWaveSeeds\(\)[\s\S]*Object\.entries\(playlists\)[\s\S]*listeningHistory/
  );
  assert.match(main, /async function loadForYouContent\(forceReload = false\)/);
  assert.match(main, /loadForYouContent[\s\S]*fetchWaveTracks\(seeds, 30\)/);
  assert.match(main, /renderRecTiles\(results, forYouTracks\)/);
  assert.match(main, /function scrollForYou\(direction\)/);
  assert.match(main, /function loadHomeContent\(\)[\s\S]*loadForYouContent\(\)/);
});

test('player buttons isolate spinner when is-loading class is active', () => {
  const css = read('src/styles.css');
  const main = read('src/main.js');

  assert.match(css, /\.is-loading > :not\(\.votify-spinner-wrap\):not\(\.votify-spinner-svg\)/);
  assert.match(css, /\.is-loading \.icon-play-svg/);
  assert.match(css, /\.is-loading \.icon-pause-svg/);
  assert.match(css, /\.is-loading \.votify-spinner-wrap/);
  assert.match(main, /btn\.classList\.toggle\('is-loading', isLoading\)/);
});

test('lyrics parser and playback support offsets and timing compensation', () => {
  const main = read('src/main.js');

  assert.match(main, /function getLyricsPlaybackTime\(time\)/);
  assert.match(main, /const offsetSec = \(appSettings\.lyricsOffset \|\| 0\) \/ 1000/);
  assert.match(main, /parseLrcTimings\(lrc/);
  assert.match(main, /offsetMatch/);
  assert.match(main, /updateLyricsOffsetUI/);
});

test('workshop publishing has automatic fallback to core theme schema for live rules', () => {
  const fb = read('src/firebase-client.js');

  assert.match(fb, /function cleanCoreWorkshopTheme/);
  assert.match(fb, /permission-denied/);
  assert.match(fb, /theme: cleanCoreWorkshopTheme\(theme\)/);
});

