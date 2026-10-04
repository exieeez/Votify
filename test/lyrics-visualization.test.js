const test = require('node:test');
const assert = require('node:assert/strict');

// Replicate lyrics parsing logic from src/main.js
function parseLrcTimings(lrc) {
  if (!lrc) return { synced: false, lines: [] };
  const rawLines = lrc.split('\n').map(l => l.trim()).filter(Boolean);

  let lrcOffsetMs = 0;
  for (const line of rawLines) {
    const offsetMatch = line.match(/^\[offset:\s*([+-]?\d+)\]/i);
    if (offsetMatch) {
      lrcOffsetMs = parseInt(offsetMatch[1], 10) || 0;
      break;
    }
  }

  const timestampRegex = /\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?\]/g;
  const synced = [];

  for (const line of rawLines) {
    if (/^\[(ti|ar|al|by|re|ve|length|offset|id|encoding):/i.test(line)) {
      continue;
    }

    const matches = [...line.matchAll(timestampRegex)];
    if (!matches.length) continue;

    const text = line.replace(timestampRegex, '').trim();

    for (const m of matches) {
      const min = parseInt(m[1], 10) || 0;
      const sec = parseInt(m[2], 10) || 0;
      const msStr = m[3] || '0';
      let ms = 0;
      if (msStr.length === 1) ms = parseInt(msStr, 10) * 100;
      else if (msStr.length === 2) ms = parseInt(msStr, 10) * 10;
      else ms = parseInt(msStr.slice(0, 3), 10);

      const baseSec = min * 60 + sec + ms / 1000;
      const finalSec = Math.max(0, baseSec + lrcOffsetMs / 1000);

      synced.push({
        time: Math.round(finalSec * 1000) / 1000,
        text: text || '♪',
      });
    }
  }

  if (synced.length > 0) {
    synced.sort((a, b) => a.time - b.time);
    return { synced: true, lines: synced };
  }

  const validLines = rawLines
    .filter(l => !l.startsWith('[') && !l.startsWith('#'))
    .map(text => ({ time: 0, text: text || '♪' }));

  return { synced: false, lines: validLines };
}

function findActiveLyricIndex(lyrics, time) {
  if (!lyrics || !lyrics.synced || !lyrics.lines || !lyrics.lines.length) return -1;
  const lines = lyrics.lines;
  let lo = 0;
  let hi = lines.length - 1;
  let ans = -1;
  while (lo <= hi) {
    const mid = (lo + hi) >> 1;
    if (lines[mid].time <= time) {
      ans = mid;
      lo = mid + 1;
    } else {
      hi = mid - 1;
    }
  }
  return ans;
}

function renderLyricsLinesHtml(lines, isFullOverlay = false) {
  if (!lines || !lines.length) return '<div class="lyrics-placeholder">Нет текста</div>';
  const extraClass = isFullOverlay ? ' full-lyrics-line' : '';
  return lines.map((l, i) => {
    const text = l.text || '♪';
    return `<div class="lyrics-line${extraClass}" data-idx="${i}" data-time="${l.time}">${text}</div>`;
  }).join('');
}

test('lyrics parser produces synced lines and handles musical notes placeholder', () => {
  const lrc = `
[00:10.50] First verse line
[00:15.00] 
[00:20.25] Second verse line
`;
  const parsed = parseLrcTimings(lrc);
  assert.equal(parsed.synced, true);
  assert.equal(parsed.lines.length, 3);
  assert.equal(parsed.lines[0].text, 'First verse line');
  assert.equal(parsed.lines[0].time, 10.5);
  assert.equal(parsed.lines[1].text, '♪');
  assert.equal(parsed.lines[1].time, 15);
  assert.equal(parsed.lines[2].text, 'Second verse line');
  assert.equal(parsed.lines[2].time, 20.25);
});

test('findActiveLyricIndex tracks active line accurately across time intervals matching mobile', () => {
  const lyrics = {
    synced: true,
    lines: [
      { time: 5.0, text: 'Intro' },
      { time: 10.0, text: 'Verse 1' },
      { time: 20.0, text: 'Chorus' }
    ]
  };

  assert.equal(findActiveLyricIndex(lyrics, 0.0), -1);
  assert.equal(findActiveLyricIndex(lyrics, 4.9), -1);
  assert.equal(findActiveLyricIndex(lyrics, 5.0), 0);
  assert.equal(findActiveLyricIndex(lyrics, 9.99), 0);
  assert.equal(findActiveLyricIndex(lyrics, 10.0), 1);
  assert.equal(findActiveLyricIndex(lyrics, 19.99), 1);
  assert.equal(findActiveLyricIndex(lyrics, 20.0), 2);
  assert.equal(findActiveLyricIndex(lyrics, 50.0), 2);
});

test('renderLyricsLinesHtml generates both lyrics-line and full-lyrics-line for full overlay sync', () => {
  const lines = [
    { time: 12.3, text: 'Line 1' },
    { time: 18.5, text: 'Line 2' }
  ];
  const html = renderLyricsLinesHtml(lines, true);
  assert.ok(html.includes('class="lyrics-line full-lyrics-line"'));
  assert.ok(html.includes('data-idx="0"'));
  assert.ok(html.includes('data-time="12.3"'));
  assert.ok(html.includes('data-idx="1"'));
  assert.ok(html.includes('data-time="18.5"'));
});

test('plain unsynced lyrics return synced: false with non-empty lines', () => {
  const rawText = `Line one\nLine two\nLine three`;
  const parsed = parseLrcTimings(rawText);
  assert.equal(parsed.synced, false);
  assert.equal(parsed.lines.length, 3);
  assert.equal(parsed.lines[0].text, 'Line one');
  assert.equal(findActiveLyricIndex(parsed, 10.0), -1);
});

test('calculateLyricScrollTop centers the active element at ~38% viewport', () => {
  function calculateLyricScrollTop(containerHeight, currentScroll, eTop, cTop, eHeight) {
    const elementTopInContainer = (eTop - cTop) + currentScroll;
    return Math.max(0, elementTopInContainer - (containerHeight * 0.38) + (eHeight / 2));
  }

  // Case 1: container height 600, active line at 400px inside content
  const target = calculateLyricScrollTop(600, 0, 400, 0, 40);
  // target = 400 - (600 * 0.38) + 20 = 400 - 228 + 20 = 192
  assert.equal(target, 192);

  // Case 2: active element near top does not scroll below 0
  const topTarget = calculateLyricScrollTop(600, 0, 50, 0, 40);
  // target = 50 - 228 + 20 = -158 -> coerced to 0
  assert.equal(topTarget, 0);
});

test('cleans and extracts primary artist and title from YouTube style names', () => {
  const stripJunk = s => (s || '')
    .replace(/\((?:official\s*(?:video|audio|music\s*video|lyric\s*video|clip|hd|hq)|audio|remix|official|video|clip|lyrics|visualizer|slowed|reverb)\)/gi, '')
    .replace(/\[(?:official\s*(?:video|audio|music\s*video|lyric\s*video|clip|hd|hq)|audio|remix|official|video|clip|lyrics|visualizer|slowed|reverb)\]/gi, '')
    .replace(/\s*--\s*|\s*—\s*|\s*–\s*/g, ' - ')
    .replace(/\s+/g, ' ')
    .trim();

  const title = 'Betsy, Мария Янковская - Sigma Boy (Official audio)';
  const cleaned = stripJunk(title);
  const parts = cleaned.split(' - ');
  const rawArtist = parts[0].trim();
  const rawTitle = parts[1].trim();
  const primaryArtist = rawArtist.split(/,|&|\bfeat\.?|\bft\.?/i)[0].trim();

  assert.equal(primaryArtist, 'Betsy');
  assert.equal(rawTitle, 'Sigma Boy');
});
