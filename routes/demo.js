/* ==========================================================================
   VOTIFY — OFFLINE DEMO CATALOG
   Когда YouTube/SoundCloud недоступны (нет сети, блокировки), поиск и
   рекомендации возвращают небольшой локальный каталог, а аудио синтезируется
   на лету в памяти (WAV 22.05 kHz mono). Никаких файлов на диске.

   Отключается переменной окружения VOTIFY_DEMO=0.
   ========================================================================== */

const CATALOG = [
  { id: 'demo-01', title: 'Неоновый дождь', artist: 'Стеклянный Оркестр', root: 220.0, bpm: 92, mode: [0, 3, 7, 10], seed: 7, dur: 26 },
  { id: 'demo-02', title: 'Полночный экспресс', artist: 'Стеклянный Оркестр', root: 196.0, bpm: 120, mode: [0, 4, 7, 11], seed: 21, dur: 24 },
  { id: 'demo-03', title: 'Моя волна', artist: 'Votify Demo', root: 246.94, bpm: 100, mode: [0, 2, 7, 9], seed: 3, dur: 28 },
  { id: 'demo-04', title: 'Хрустальное утро', artist: 'Votify Demo', root: 261.63, bpm: 84, mode: [0, 4, 7, 9], seed: 11, dur: 26 },
  { id: 'demo-05', title: 'Городские огни', artist: 'Ночной Рейс', root: 174.61, bpm: 110, mode: [0, 3, 7, 14], seed: 42, dur: 25 },
  { id: 'demo-06', title: 'Тёплый шум', artist: 'Ночной Рейс', root: 146.83, bpm: 76, mode: [0, 5, 7, 12], seed: 5, dur: 27 },
  { id: 'demo-07', title: 'Пыль на виниле', artist: 'Кассетный Дом', root: 207.65, bpm: 96, mode: [0, 2, 5, 7], seed: 17, dur: 24 },
  { id: 'demo-08', title: 'Последний троллейбус', artist: 'Кассетный Дом', root: 233.08, bpm: 104, mode: [0, 3, 8, 10], seed: 29, dur: 26 },
];

const SR = 22050;
const wavCache = new Map();

function demoEnabled() {
  return process.env.VOTIFY_DEMO !== '0';
}

function isDemoId(id) {
  return String(id || '').startsWith('demo-');
}

function getDemoEntry(id) {
  return CATALOG.find(e => e.id === id) || null;
}

function shape(e) {
  return {
    id: e.id,
    title: e.title,
    artist: e.artist,
    url: '/api/audio?id=' + encodeURIComponent(e.id),
    cover: '/demo/cover/' + e.id + '.svg',
    duration: e.dur,
    demo: true,
  };
}

function hashStr(s) {
  let h = 2166136261;
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

function mulberry32(a) {
  return function () {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/* Поиск по демо-каталогу: совпадения в названии/исполнителе, иначе ротация */
function searchDemo(query, limit) {
  if (!demoEnabled()) return [];
  const q = String(query || '').toLowerCase().trim();
  let hits = [];
  if (q) {
    hits = CATALOG.filter(e => {
      const hay = (e.title + ' ' + e.artist).toLowerCase();
      return hay.includes(q) || q.includes(e.artist.toLowerCase());
    });
  }
  if (!hits.length && q) {
    const off = hashStr(q) % CATALOG.length;
    hits = CATALOG.slice(off).concat(CATALOG.slice(0, off)).slice(0, Math.max(limit, 3));
  }
  return hits.slice(0, limit || 10).map(shape);
}

function searchDemoByArtist(name, limit) {
  if (!demoEnabled()) return [];
  const n = String(name || '').toLowerCase().trim();
  const hits = CATALOG.filter(e => e.artist.toLowerCase().includes(n) || n.includes(e.artist.toLowerCase()));
  return (hits.length ? hits : CATALOG).slice(0, limit || 10).map(shape);
}

function demoAll(limit) {
  if (!demoEnabled()) return [];
  return CATALOG.slice(0, limit || 12).map(shape);
}

/* ------------------------------------------------------------- WAV synth */
function synthWav(entry) {
  const cached = wavCache.get(entry.id);
  if (cached) return cached;
  const rnd = mulberry32(entry.seed * 7919 + 13);
  const N = Math.floor(SR * entry.dur);
  const samples = new Float32Array(N);
  const beat = 60 / entry.bpm;
  const chordLen = entry.dur / 4;
  const semi = s => Math.pow(2, s / 12);

  for (let c = 0; c < 4; c++) {
    const start = Math.floor(c * chordLen * SR);
    const end = Math.min(N, Math.floor((c + 1) * chordLen * SR));
    const chord = entry.mode.map(m => m + (c === 3 ? 12 : 0));
    // Пэд: три голоса с медленной атакой
    for (let i = start; i < end; i++) {
      const t = (i - start) / SR;
      const env = Math.min(1, t / 1.2) * Math.min(1, (end - i) / SR / 0.8);
      let v = 0;
      for (let k = 0; k < 3; k++) {
        const f = entry.root * semi(chord[k % chord.length]) * (k === 2 ? 2 : 1);
        v += Math.sin(2 * Math.PI * f * t + k * 0.7) * (0.12 / (k + 1));
      }
      samples[i] += v * env;
    }
    // Бас: четвертные
    for (let b = 0; b * beat < chordLen; b++) {
      const bs = start + Math.floor(b * beat * SR);
      const bl = Math.floor(beat * 0.9 * SR);
      const f = (entry.root / 2) * semi(chord[0]);
      for (let i = 0; i < bl && bs + i < N; i++) {
        const t = i / SR;
        samples[bs + i] += Math.sin(2 * Math.PI * f * t) * 0.16 * Math.exp(-t * 2.2);
      }
    }
    // Арпеджио: восьмые сверху
    for (let a = 0; a * beat * 0.5 < chordLen; a++) {
      const as = start + Math.floor(a * beat * 0.5 * SR);
      const al = Math.floor(beat * 0.5 * SR);
      const note = chord[(a + c) % chord.length];
      const f = entry.root * 2 * semi(note);
      for (let i = 0; i < al && as + i < N; i++) {
        const t = i / SR;
        samples[as + i] += Math.sin(2 * Math.PI * f * t) * 0.14 * Math.exp(-t * 6);
      }
    }
    // Хэт: шум на слабые доли
    for (let h8 = 0; h8 * beat * 0.5 < chordLen; h8++) {
      if (h8 % 2 === 0) continue;
      const hs = start + Math.floor(h8 * beat * 0.5 * SR);
      const hl = Math.floor(0.04 * SR);
      for (let i = 0; i < hl && hs + i < N; i++) {
        samples[hs + i] += (rnd() * 2 - 1) * 0.05 * Math.exp(-(i / SR) * 90);
      }
    }
  }
  // Мастер: soft clip + фейды
  const fade = Math.floor(0.5 * SR);
  const buf = Buffer.alloc(44 + N * 2);
  buf.write('RIFF', 0);
  buf.writeUInt32LE(36 + N * 2, 4);
  buf.write('WAVE', 8);
  buf.write('fmt ', 12);
  buf.writeUInt32LE(16, 16);
  buf.writeUInt16LE(1, 20);
  buf.writeUInt16LE(1, 22);
  buf.writeUInt32LE(SR, 24);
  buf.writeUInt32LE(SR * 2, 28);
  buf.writeUInt16LE(2, 32);
  buf.writeUInt16LE(16, 34);
  buf.write('data', 36);
  buf.writeUInt32LE(N * 2, 40);
  for (let i = 0; i < N; i++) {
    let v = Math.tanh(samples[i] * 1.4);
    if (i < fade) v *= i / fade;
    if (i > N - fade) v *= (N - i) / fade;
    buf.writeInt16LE(Math.max(-1, Math.min(1, v)) * 32767, 44 + i * 2);
  }
  wavCache.set(entry.id, buf);
  return buf;
}

function serveDemoAudio(id, res) {
  const entry = getDemoEntry(id);
  if (!entry) return false;
  const buf = synthWav(entry);
  res.writeHead(200, {
    'Content-Type': 'audio/wav',
    'Content-Length': buf.length,
    'Cache-Control': 'public, max-age=3600',
  });
  res.end(buf);
  return true;
}

/* SVG-обложка: градиент из hash + название */
function serveDemoCover(id, res) {
  const entry = getDemoEntry(id);
  if (!entry) return false;
  const h = hashStr(entry.id);
  const hue = h % 360;
  const hue2 = (hue + 60 + (h >> 3) % 120) % 360;
  const esc = s => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" width="300" height="300" viewBox="0 0 300 300">` +
    `<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">` +
    `<stop offset="0" stop-color="hsl(${hue},62%,46%)"/>` +
    `<stop offset="1" stop-color="hsl(${hue2},70%,26%)"/></linearGradient></defs>` +
    `<rect width="300" height="300" fill="url(#g)"/>` +
    `<circle cx="${80 + (h >> 5) % 140}" cy="${70 + (h >> 9) % 120}" r="${60 + (h >> 11) % 50}" fill="rgba(255,255,255,0.14)"/>` +
    `<text x="24" y="252" font-family="sans-serif" font-size="30" font-weight="700" fill="rgba(255,255,255,0.92)">${esc(entry.title)}</text>` +
    `<text x="24" y="280" font-family="sans-serif" font-size="18" fill="rgba(255,255,255,0.66)">${esc(entry.artist)}</text>` +
    `</svg>`;
  res.writeHead(200, {
    'Content-Type': 'image/svg+xml; charset=utf-8',
    'Cache-Control': 'public, max-age=3600',
  });
  res.end(svg);
  return true;
}

function handleDemoRoutes(req, res, u) {
  if (!demoEnabled()) return false;
  if (u.pathname.startsWith('/demo/cover/')) {
    return serveDemoCover(u.pathname.slice('/demo/cover/'.length).replace(/\.svg$/, ''), res);
  }
  if (u.pathname.startsWith('/demo/audio/')) {
    return serveDemoAudio(u.pathname.slice('/demo/audio/'.length).replace(/\.wav$/, ''), res);
  }
  return false;
}

module.exports = {
  demoEnabled,
  isDemoId,
  getDemoEntry,
  searchDemo,
  searchDemoByArtist,
  demoAll,
  serveDemoAudio,
  serveDemoCover,
  handleDemoRoutes,
};
