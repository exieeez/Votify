const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

test('Search filters UI in index.html contains exactly 2 filters: Музыка and Плейлисты', () => {
  const htmlPath = path.join(__dirname, '..', 'src', 'index.html');
  const html = fs.readFileSync(htmlPath, 'utf8');

  // Find the #search-filters container
  const filterSectionMatch = html.match(/<div class="search-filters" id="search-filters">([\s\S]*?)<\/div>/);
  assert.ok(filterSectionMatch, 'search-filters container exists');

  const filterSection = filterSectionMatch[1];
  const pills = [...filterSection.matchAll(/<button class="filter-pill[^"]*" data-filter="([^"]+)">([^<]+)<\/button>/g)];

  assert.equal(pills.length, 2, 'There must be exactly 2 search filters');

  assert.equal(pills[0][1], 'music', 'First filter must have data-filter="music"');
  assert.equal(pills[0][2].trim(), 'Музыка', 'First filter label must be "Музыка"');

  assert.equal(pills[1][1], 'playlists', 'Second filter must have data-filter="playlists"');
  assert.equal(pills[1][2].trim(), 'Плейлисты', 'Second filter label must be "Плейлисты"');

  // Verify removed legacy pills are not inside #search-filters
  assert.ok(!filterSection.includes('data-filter="all"'), 'Filter "all" should not be in search-filters');
  assert.ok(!filterSection.includes('data-filter="albums"'), 'Filter "albums" should not be in search-filters');
  assert.ok(!filterSection.includes('data-filter="art"'), 'Filter "art" should not be in search-filters');
});

test('Search filters behavior in main.js defaults to music and handles instant switching', () => {
  const mainPath = path.join(__dirname, '..', 'src', 'main.js');
  const main = fs.readFileSync(mainPath, 'utf8');

  // Default activeSearchFilter is 'music'
  assert.match(main, /let activeSearchFilter = 'music';/);

  // Filter pill click listener caches and triggers renderSearchResults
  assert.match(main, /activeSearchFilter = pill\.getAttribute\('data-filter'\) \|\| 'music';/);
  assert.match(main, /renderSearchResults\(lastSearchTracks,\s*searchCurrentQuery,\s*lastSearchFoundPlaylists\)/);

  // renderSearchResults handles both filters cleanly
  assert.match(main, /const isPlaylistsFilter = activeSearchFilter === 'playlists';/);
  assert.match(main, /const isMusicFilter = activeSearchFilter === 'music' \|\| activeSearchFilter === 'tracks';/);
  assert.match(main, /Музыка не найдена/);
  assert.match(main, /Плейлистов не найдено/);
});
