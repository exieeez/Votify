// ==========================================
// Votify — Dotify Edition — Main Script
// ==========================================

// Register the splash watchdog before any other initialization. If corrupted
// local data or another top-level feature throws, the loading screen must not
// cover the application forever.
let startupSplashFailsafe = setTimeout(() => hideSplash(), 4000);

// --- Titlebar Buttons ---
function setupTitlebarButtons() {
  var api = window.electronAPI;
  if (!api) return;
  var minBtn = document.getElementById('tb-minimize');
  var maxBtn = document.getElementById('tb-maximize');
  var closeBtn = document.getElementById('tb-close');
  function preventDrag(e) {
    e.stopPropagation();
    e.preventDefault();
  }
  [minBtn, maxBtn, closeBtn].forEach(function (btn) {
    if (!btn) return;
    btn.addEventListener('mousedown', preventDrag, true);
    btn.addEventListener('mouseup', preventDrag, true);
  });
  if (minBtn)
    minBtn.onclick = function (e) {
      e.stopPropagation();
      e.preventDefault();
      api.minimize();
    };
  if (maxBtn)
    maxBtn.onclick = function (e) {
      e.stopPropagation();
      e.preventDefault();
      api.maximize();
    };
  if (closeBtn)
    closeBtn.onclick = function (e) {
      e.stopPropagation();
      e.preventDefault();
      api.close();
    };
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', setupTitlebarButtons);
} else {
  setupTitlebarButtons();
}

// --- IPC / API ---
let invoke;
let convertFileSrc;

async function invokePreviewApi(cmd, args = {}) {
  let endpoint = '';
  if (cmd === 'search_and_download') {
    endpoint = `/api/search?q=${encodeURIComponent(args?.query || '')}`;
  } else if (cmd === 'get_recommendations') {
    const limit = appSettings?.recCount || 16;
    endpoint = `/api/recommendations?limit=${limit}`;
  } else {
    return [];
  }
  // Do not let an unavailable YouTube/network request block the whole UI.
  // Recommendations are optional, so returning an empty list is preferable to
  // leaving the loading animation on screen indefinitely.
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 12000);
  const response = await fetch(endpoint, { signal: controller.signal }).catch(() => null);
  clearTimeout(timeout);
  if (!response) return [];
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(payload.error || 'Request failed');
  return payload.tracks || [];
}

if (window.__TAURI__) {
  invoke = window.__TAURI__.core.invoke;
  convertFileSrc =
    window.__TAURI__.core.convertFileSrc || (p => `https://asset.tauri.localhost/${p}`);
} else {
  invoke = invokePreviewApi;
  convertFileSrc = p => p;
}

async function apiFetch(url, options = {}) {
  const token = getAuthToken();
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  try {
    const res = await fetch(url, { ...options, headers });
    return await res.json();
  } catch {
    return { error: 'Network error' };
  }
}

// ==========================================
// State & Storage
// ==========================================
const state = {
  isPlaying: false,
  currentTime: 0,
  duration: 0,
  volume: 0.8,
  currentTrack: null,
};

const listeners = {};
function on(event, callback) {
  if (!listeners[event]) listeners[event] = [];
  listeners[event].push(callback);
}
function emit(event, data) {
  if (listeners[event]) listeners[event].forEach(cb => cb(data));
}

function getAuthToken() {
  return localStorage.getItem('votify-token');
}
let currentLyricsLines = [];
let currentLyricIndex = -1;

const lyricsTranslationCache = new Map();
async function translateLyricLine(text) {
  if (!appSettings.translateLyrics || !text) return null;
  if (lyricsTranslationCache.has(text)) return lyricsTranslationCache.get(text);
  try {
    const target = appSettings.lang === 'ru' ? 'ru' : appSettings.lang || 'en';
    const res = await fetch(
      `https://api.mymemory.translated.net/get?q=${encodeURIComponent(text)}&langpair=en|${target}`
    );
    const data = await res.json();
    const translated = data?.responseData?.translatedText || null;
    lyricsTranslationCache.set(text, translated);
    return translated;
  } catch {
    return null;
  }
}

// YouTube video titles are messy ("(Official Video)", "Artist - Topic", "feat. X", etc.)
// which makes lrclib's exact-match endpoint miss a lot of the time. This strips the
// common junk before searching, and falls back to fuzzy search when an exact hit fails.
function cleanLyricsQuery(str) {
  if (!str) return '';
  return str
    .replace(/\(.*?\)/g, ' ')
    .replace(/\[.*?\]/g, ' ')
    .replace(/[-–—]\s*topic$/i, '')
    .replace(
      /\b(official\s*(video|audio|lyrics?|music\s*video)?|lyrics?|visualizer|hd|hq|4k|remaster(ed)?|explicit|clean)\b/gi,
      ' '
    )
    .replace(/\bfeat\.?\s.*/i, '')
    .replace(/\bft\.?\s.*/i, '')
    .replace(/[|•·]+/g, ' ')
    .replace(/\s{2,}/g, ' ')
    .trim();
}

const lyricsDataCache = new Map();
async function fetchLyricsData(rawTitle, rawArtist) {
  const cacheKey = `${rawTitle || ''}::${rawArtist || ''}`;
  if (lyricsDataCache.has(cacheKey)) return lyricsDataCache.get(cacheKey);

  const title = cleanLyricsQuery(rawTitle);
  const artist = cleanLyricsQuery(rawArtist);
  let result = null;

  if (title) {
    // 1) Exact-match endpoint — fast and accurate when the cleaned title lines up
    try {
      const q = encodeURIComponent(title);
      const a = artist ? '&artist_name=' + encodeURIComponent(artist) : '';
      const res = await fetch(`https://lrclib.net/api/get?track_name=${q}${a}`);
      if (res.ok) {
        const data = await res.json();
        if (data && (data.syncedLyrics || data.plainLyrics)) result = data;
      }
    } catch (e) {
      /* ignore */
    }

    // 2) Fuzzy search with artist + title — handles minor spelling/wording differences
    if (!result) {
      try {
        const q = encodeURIComponent(`${artist} ${title}`.trim());
        const res = await fetch(`https://lrclib.net/api/search?q=${q}`);
        if (res.ok) {
          const results = await res.json();
          if (Array.isArray(results) && results.length) {
            result = results.find(r => r.syncedLyrics) || results.find(r => r.plainLyrics) || null;
          }
        }
      } catch (e) {
        /* ignore */
      }
    }

    // 3) Last resort — search by title only, in case the artist name is wrong/missing
    if (!result) {
      try {
        const res = await fetch(`https://lrclib.net/api/search?q=${encodeURIComponent(title)}`);
        if (res.ok) {
          const results = await res.json();
          if (Array.isArray(results) && results.length) {
            result = results.find(r => r.syncedLyrics) || results.find(r => r.plainLyrics) || null;
          }
        }
      } catch (e) {
        /* ignore */
      }
    }
  }

  lyricsDataCache.set(cacheKey, result);
  return result;
}

async function loadLyricsForTrack(title, artist) {
  currentLyricsLines = [];
  currentLyricIndex = -1;
  const el = document.getElementById('player-track-lyrics');
  if (el) el.textContent = '';
  if (!title) return;
  if (appSettings.autoLyrics === false) return;
  try {
    const data = await fetchLyricsData(title, artist);
    if (!data) return;
    const lrc = data.syncedLyrics || data.plainLyrics || '';
    if (!lrc) return;
    if (appSettings.syncedLyrics === false || !data.syncedLyrics) {
      // Static (non-synced) mode: show plain lyrics without line-by-line timing
      const plain = (data.plainLyrics || lrc.replace(/\[[^\]]*\]/g, '')).trim();
      if (el) el.textContent = plain || 'Нет текста';
      return;
    }
    currentLyricsLines = lrc
      .split('\n')
      .map(line => {
        // Match [MM:SS.xx] or [MM:SS.xxx] or [MM:SS]
        const m = line.match(/^\[(\d+):(\d+)(?:\.(\d+))?\]\s*(.*)/);
        if (m) {
          const min = parseInt(m[1]) || 0;
          const sec = parseInt(m[2]) || 0;
          const msStr = m[3] || '0';
          const ms = parseInt(msStr) / Math.pow(10, msStr.length);
          return { time: min * 60 + sec + ms, text: m[4].trim() };
        }
        return null;
      })
      .filter(Boolean);
    if (el && currentLyricsLines.length > 0) updateLyricsLine();
  } catch (e) {
    /* ignore */
  }
}

function updateLyricsLine() {
  const el = document.getElementById('player-track-lyrics');
  if (!currentLyricsLines.length || !el) return;
  const t = audio.currentTime;
  let idx = -1;
  for (let i = currentLyricsLines.length - 1; i >= 0; i--) {
    if (t >= currentLyricsLines[i].time) {
      idx = i;
      break;
    }
  }
  if (idx !== currentLyricIndex) {
    currentLyricIndex = idx;
    const text = idx >= 0 ? currentLyricsLines[idx].text : '';
    el.textContent = text;
    if (text && appSettings.translateLyrics) {
      translateLyricLine(text).then(translated => {
        if (translated && currentLyricIndex === idx) {
          el.textContent = `${text} / ${translated}`;
        }
      });
    }
  }
}

function updateFullscreenLyrics(time) {
  if (!fsLyricsData.length) return;
  let idx = -1;
  for (let i = fsLyricsData.length - 1; i >= 0; i--) {
    if (time >= fsLyricsData[i].time) {
      idx = i;
      break;
    }
  }
  const lines = document.querySelectorAll('#fs-lyrics-body .lyrics-line');
  lines.forEach((el, i) => {
    const active = i === idx;
    el.classList.toggle('active', active);
    el.classList.toggle('past', idx >= 0 && i < idx);
    if (active) el.scrollIntoView({ block: 'center', behavior: 'smooth' });
  });
}

function readStoredJson(key, fallback) {
  const raw = localStorage.getItem(key);
  if (!raw) return fallback;
  try {
    return JSON.parse(raw) ?? fallback;
  } catch (error) {
    console.warn(`Ignoring corrupted local setting: ${key}`, error);
    localStorage.removeItem(key);
    return fallback;
  }
}

let audio = new Audio();
audio.preload = 'auto';
let currentPlaylist = [];
let currentTrackIndex = -1;
let isShuffle = false;
let shuffleHistory = [];
let recommendationsLoaded = false;
let loadingOperations = 0;
let loadingAudioContext = null;
let playlists = readStoredJson('votify-playlists', { Избранное: [] });
let appSettings = readStoredJson('votify-settings', {
  lang: 'ru',
  font: 'default',
  bgUrl: '',
  opacity: '98',
  accent: '#1DB954',
  audioQuality: 'medium',
  autoPlay: true,
  crossfade: 0,
  gapless: false,
  normalize: false,
  sleepTimer: 0,
  autoLyrics: true,
  syncedLyrics: true,
  translateLyrics: false,
  recDiversity: true,
  recCount: 16,
  uiSounds: true,
  loadingSound: true,
  closeToTray: false,
  trackNotifications: false,
  rememberVolume: true,
  dynamicPlayerBg: true,
  pauseWhenHidden: false,
  resumePosition: true,
  saveHistory: true,
  historyLimit: 50,
  playbackRate: 1,
  preload: 'auto',
  // Interface settings
  theme: 'contrast',
  transparency: false,
  fontFamily: 'default',
  fontSize: '16px',
  animations: true,
  compactUI: false,
  cornerRadius: 8,
  reducedMotion: false,
  splashScreen: true,
  coverInPlayer: true,
  background: 'default',
  density: 'comfortable',
  accentGlow: true,
  trackCardStyle: 'default',
  backgroundBlur: 0,
  perfParticles: false,
  bgParticles: 'none',
  savedColorSchemes: [],
  activeColorSchemeId: '',
});

if (appSettings.perfParticles === undefined) appSettings.perfParticles = false;
if (appSettings.bgParticles === undefined) appSettings.bgParticles = 'none';
if (!Array.isArray(appSettings.savedColorSchemes)) appSettings.savedColorSchemes = [];
if (typeof appSettings.activeColorSchemeId !== 'string') appSettings.activeColorSchemeId = '';

function getColorSchemesApi() {
  return window.VotifyColorSchemes || null;
}

function sanitizeAppColorSchemes() {
  const api = getColorSchemesApi();
  if (api) {
    appSettings.savedColorSchemes = api.sanitizeSavedColorSchemes(appSettings.savedColorSchemes);
  } else if (!Array.isArray(appSettings.savedColorSchemes)) {
    appSettings.savedColorSchemes = [];
  }
  if (
    appSettings.activeColorSchemeId &&
    !(appSettings.savedColorSchemes || []).some(
      scheme => scheme.id === appSettings.activeColorSchemeId
    )
  ) {
    appSettings.activeColorSchemeId = '';
  }
  return appSettings.savedColorSchemes;
}

sanitizeAppColorSchemes();

// One-time cleanup for installations that inherited the old intrusive visual
// defaults. Users can still enable particles again from the appearance panel.
const cleanPlayerUiMigration = 'votify-clean-player-ui-v1';
if (localStorage.getItem(cleanPlayerUiMigration) !== 'done') {
  appSettings.perfParticles = false;
  appSettings.bgParticles = 'none';
  localStorage.setItem('votify-settings', JSON.stringify(appSettings));
  localStorage.setItem(cleanPlayerUiMigration, 'done');
}

let isChangingTrack = false;
let discordPresenceSyncTimer = null;

function syncDiscordPresence(delay = 0) {
  const api = window.electronAPI;
  if (!api?.updateDiscordPresence) return;
  if (discordPresenceSyncTimer) clearTimeout(discordPresenceSyncTimer);

  const sendPresence = () => {
    discordPresenceSyncTimer = null;
    const track = state.currentTrack;
    if (!track) {
      api.clearDiscordPresence?.();
      return;
    }
    api.updateDiscordPresence({
      title: track.title,
      artist: track.artist,
      cover: track.cover,
      position: Number.isFinite(audio.currentTime) ? audio.currentTime : 0,
      duration: Number.isFinite(audio.duration) ? audio.duration : 0,
      playbackRate: Number.isFinite(audio.playbackRate) ? audio.playbackRate : 1,
      isPlaying: !audio.paused && !audio.ended,
    });
  };

  if (delay > 0) discordPresenceSyncTimer = setTimeout(sendPresence, delay);
  else sendPresence();
}

function clearDiscordPresence() {
  if (discordPresenceSyncTimer) {
    clearTimeout(discordPresenceSyncTimer);
    discordPresenceSyncTimer = null;
  }
  window.electronAPI?.clearDiscordPresence?.();
}

// If the user disabled the launch splash screen, skip it immediately instead
// of waiting for the usual post-init delay.
if (appSettings.splashScreen === false) {
  const splashEl = document.getElementById('splash-screen');
  if (splashEl) splashEl.style.display = 'none';
}

// Sync audio with state
audio.addEventListener('play', () => {
  state.isPlaying = true;
  emit('state:isPlaying', true);
  initEQ();
  if (!isChangingTrack) syncDiscordPresence();
});
audio.addEventListener('pause', () => {
  state.isPlaying = false;
  emit('state:isPlaying', false);
  if (!isChangingTrack) syncDiscordPresence();
});
audio.addEventListener('timeupdate', () => {
  state.currentTime = audio.currentTime;
  emit('state:currentTime', audio.currentTime);
});
audio.addEventListener('durationchange', () => {
  state.duration = audio.duration;
  emit('state:duration', audio.duration);
  if (!isChangingTrack) syncDiscordPresence(100);
});
audio.addEventListener('volumechange', () => {
  state.volume = audio.volume;
  emit('state:volume', audio.volume);
});

let cloudPushTimer = null;
let cloudSyncApplying = false;
let lastCloudUserId = null;

function scheduleCloudPush() {
  if (cloudSyncApplying || !isUserAuthenticated()) return;
  if (cloudPushTimer) clearTimeout(cloudPushTimer);
  cloudPushTimer = setTimeout(() => syncWithCloud('push'), 1200);
}

function savePlaylists() {
  localStorage.setItem('votify-playlists', JSON.stringify(playlists));
  scheduleCloudPush();
}
function saveSettings() {
  localStorage.setItem('votify-settings', JSON.stringify(appSettings));
  scheduleCloudPush();
}
function isUserAuthenticated() {
  return !!window.VotifyCloud?.getCurrentUser?.();
}

function setCloudSyncLabel(text) {
  const label = document.getElementById('profile-cloud-status');
  if (label) label.textContent = text;
}

function cloudCacheKey(uid) {
  return `votify-cloud-cache-${uid}`;
}

function getCloudSafeSettings() {
  const settings = { ...appSettings };
  if (String(settings.bgUrl || '').startsWith('data:')) delete settings.bgUrl;
  if (String(settings.background || '').startsWith('data:')) delete settings.background;
  Object.keys(settings).forEach(key => {
    if (key.startsWith('_cache')) delete settings[key];
  });
  const api = getColorSchemesApi();
  settings.savedColorSchemes = api
    ? api.sanitizeSavedColorSchemes(settings.savedColorSchemes)
    : Array.isArray(settings.savedColorSchemes)
      ? settings.savedColorSchemes.slice(0, 12)
      : [];
  return settings;
}

function cacheCurrentCloudState(uid) {
  if (!uid) return;
  localStorage.setItem(
    cloudCacheKey(uid),
    JSON.stringify({
      settings: appSettings,
      playlists,
      history: readStoredJson('listeningHistory', []),
    })
  );
}

function restoreCachedCloudState(uid) {
  const cached = readStoredJson(cloudCacheKey(uid), null);
  if (!cached) return false;
  if (cached.settings && typeof cached.settings === 'object') {
    appSettings = { ...appSettings, ...cached.settings };
    sanitizeAppColorSchemes();
    localStorage.setItem('votify-settings', JSON.stringify(appSettings));
  }
  if (cached.playlists && typeof cached.playlists === 'object') {
    playlists = cached.playlists;
    if (!playlists['Избранное']) playlists['Избранное'] = [];
    localStorage.setItem('votify-playlists', JSON.stringify(playlists));
  }
  if (Array.isArray(cached.history)) {
    localStorage.setItem('listeningHistory', JSON.stringify(cached.history));
  }
  return true;
}

function clearPersonalCloudState() {
  playlists = { Избранное: [] };
  localStorage.setItem('votify-playlists', JSON.stringify(playlists));
  localStorage.setItem('listeningHistory', '[]');
  renderSidebarPlaylists();
}

async function syncWithCloud(direction = 'pull') {
  const cloud = window.VotifyCloud;
  if (!cloud) return;
  await cloud.whenReady().catch(() => false);
  const user = cloud.getCurrentUser();
  if (!user) return;

  if (direction === 'pull') {
    setCloudSyncLabel('Загрузка данных из облака…');
    try {
      const data = await cloud.pullState();
      cloudSyncApplying = true;
      if (data.exists) {
        if (data.settings && typeof data.settings === 'object') {
          appSettings = { ...appSettings, ...data.settings };
          sanitizeAppColorSchemes();
          localStorage.setItem('votify-settings', JSON.stringify(appSettings));
          applyLanguage(appSettings.lang || 'ru');
        }
        if (data.playlists && typeof data.playlists === 'object') {
          playlists = data.playlists;
          if (!playlists['Избранное']) playlists['Избранное'] = [];
          localStorage.setItem('votify-playlists', JSON.stringify(playlists));
          renderSidebarPlaylists();
          const foldersScreen = document.getElementById('folders-screen');
          if (foldersScreen && foldersScreen.style.display !== 'none') renderPlaylists();
        }
        if (Array.isArray(data.history)) {
          localStorage.setItem('listeningHistory', JSON.stringify(data.history));
        }
        if (typeof applyAllSettings === 'function') applyAllSettings();
        cacheCurrentCloudState(user.uid);
      } else {
        cloudSyncApplying = false;
        await syncWithCloud('push');
        return;
      }
      setCloudSyncLabel('Синхронизация завершена');
    } catch (error) {
      console.error('[Firebase Sync] Pull error:', error);
      setCloudSyncLabel('Ошибка загрузки облачных данных');
    } finally {
      cloudSyncApplying = false;
    }
    return;
  }

  setCloudSyncLabel('Сохранение в облако…');
  try {
    const history = readStoredJson('listeningHistory', []);
    await cloud.pushState({ settings: getCloudSafeSettings(), playlists, history });
    cacheCurrentCloudState(user.uid);
    setCloudSyncLabel('Все данные сохранены');
  } catch (error) {
    console.error('[Firebase Sync] Push error:', error);
    setCloudSyncLabel('Ошибка сохранения в облако');
  }
}

window.addEventListener('votify:auth-changed', event => {
  const uid = event.detail?.user?.uid || null;
  if (!uid) {
    if (lastCloudUserId) {
      cacheCurrentCloudState(lastCloudUserId);
      clearPersonalCloudState();
    }
    lastCloudUserId = null;
    return;
  }
  if (uid !== lastCloudUserId) {
    const previousUid = lastCloudUserId;
    if (previousUid) cacheCurrentCloudState(previousUid);
    const restored = restoreCachedCloudState(uid);
    if (previousUid && !restored) clearPersonalCloudState();
    lastCloudUserId = uid;
    syncWithCloud('pull');
  }
});

window.VotifyCloud?.whenReady().then(() => {
  const uid = window.VotifyCloud?.getCurrentUser?.()?.uid;
  if (uid && uid !== lastCloudUserId) {
    lastCloudUserId = uid;
    syncWithCloud('pull');
  }
});

// ==========================================
// i18n
// ==========================================
const translations = {
  ru: {
    logo: 'Votify',
    'nav-home': 'Главная',
    'nav-search': 'Поиск',
    'nav-recommendations': 'Рекомендации',
    'nav-playlists': 'Плейлисты',
    'nav-settings': 'Настройки',
    'search-placeholder': 'Что хотите послушать?',
    'search-find-btn': 'Найти',
    'search-loading': 'Ищу больше вариантов песен...',
    'search-found-prefix': 'Найдено: ',
    'recommendations-title': 'Рекомендации',
    'recommendations-refresh': 'Обновить',
    'recommendations-loading': 'Подбираю рекомендации...',
    'loader-title': 'Загрузка музыки',
    'loader-search': 'Ищем треки и собираем свежую выдачу…',
    'loader-recommendations': 'Подбираем рекомендации и фильтруем лишние миксы…',
    'library-title': 'Моя медиатека',
    'create-playlist': 'Создать плейлист',
    'back-btn': 'Назад',
    'settings-title': 'Настройки Votify',
    'player-no-track': 'Votify',
    'player-unknown': 'Выберите трек',
    'empty-msg': 'Ничего не найдено',
    'no-playlists-msg': 'У вас еще нет плейлистов',
    'tracks-count': 'Треков: ',
  },
  en: {
    logo: 'Votify',
    'nav-home': 'Home',
    'nav-search': 'Search',
    'nav-recommendations': 'Recommendations',
    'nav-playlists': 'Playlists',
    'nav-settings': 'Settings',
    'search-placeholder': 'What do you want to listen to?',
    'search-find-btn': 'Find',
    'search-loading': 'Looking for more song options...',
    'search-found-prefix': 'Found: ',
    'recommendations-title': 'Recommendations',
    'recommendations-refresh': 'Refresh',
    'recommendations-loading': 'Picking recommendations...',
    'loader-title': 'Loading music',
    'loader-search': 'Finding tracks and assembling results…',
    'loader-recommendations': 'Collecting recommendations and filtering mixes…',
    'library-title': 'My Library',
    'create-playlist': 'Create Playlist',
    'back-btn': 'Back',
    'settings-title': 'Votify Settings',
    'player-no-track': 'Votify',
    'player-unknown': 'Select a track',
    'empty-msg': 'Nothing found',
    'no-playlists-msg': 'You have no playlists yet',
    'tracks-count': 'Tracks: ',
  },
};

function applyLanguage(lang) {
  appSettings.lang = lang;
  saveSettings();
  document.querySelectorAll('[data-i18n]').forEach(el => {
    const key = el.getAttribute('data-i18n');
    if (translations[lang] && translations[lang][key]) el.innerText = translations[lang][key];
  });
  document.querySelectorAll('[data-i18n-placeholder]').forEach(el => {
    const key = el.getAttribute('data-i18n-placeholder');
    if (translations[lang] && translations[lang][key]) el.placeholder = translations[lang][key];
  });
  const labels =
    lang === 'en'
      ? {
          'fs-close-btn': 'Hide player',
          'fs-menu-btn': 'More',
          'fs-speed-btn': 'Playback speed',
          'fs-eq-btn': 'Equalizer',
          'fs-lyrics-btn': 'Lyrics',
        }
      : {
          'fs-close-btn': 'Скрыть плеер',
          'fs-menu-btn': 'Дополнительно',
          'fs-speed-btn': 'Скорость воспроизведения',
          'fs-eq-btn': 'Эквалайзер',
          'fs-lyrics-btn': 'Текст песни',
        };
  Object.entries(labels).forEach(([id, title]) =>
    document.getElementById(id)?.setAttribute('title', title)
  );
  const presetNames =
    lang === 'en'
      ? { neutral: 'Neutral', bass: 'Bass Boost', treble: 'Treble', vocal: 'Vocal' }
      : { neutral: 'Нейтральный', bass: 'Бас', treble: 'Высокие частоты', vocal: 'Вокал' };
  document.querySelectorAll('.custom-eq-preset').forEach(btn => {
    if (presetNames[btn.dataset.preset]) btn.textContent = presetNames[btn.dataset.preset];
  });
  const ru = lang !== 'en';
  const textMap = {
    '#page-title': ru ? 'Главная' : 'Home',
    '.settings-modal-header h2': ru ? 'Настройки Votify' : 'Votify Settings',
    '.general-settings-hero h3': ru ? 'Основные настройки' : 'General Settings',
    '.general-settings-hero p': ru
      ? 'Управляйте запуском, звуком, воспроизведением и поведением Votify.'
      : 'Manage startup, sound, playback and Votify behavior.',
    '.fs-from-label': ru ? 'Сейчас играет' : 'Now playing',
    '.right-player-header > span': ru ? 'Сейчас играет' : 'Now playing',
    '.lyrics-label': ru ? 'Текст песни' : 'Lyrics',
  };
  Object.entries(textMap).forEach(([selector, value]) =>
    document.querySelectorAll(selector).forEach(el => {
      el.textContent = value;
    })
  );

  const categoryLabels = {
    ru: ['ОСНОВНЫЕ', 'ОФОРМЛЕНИЕ'],
    en: ['GENERAL', 'APPEARANCE'],
  };
  document.querySelectorAll('.settings-menu-category').forEach((el, i) => {
    if (categoryLabels[lang]?.[i]) el.textContent = categoryLabels[lang][i];
  });

  const menuSectionNames = {
    'gen-main': { ru: 'Основные', en: 'General' },
    'gen-overlay': { ru: 'Оверлей', en: 'Overlay' },
    'gen-audio': { ru: 'Аудио', en: 'Audio' },
    'gen-perf': { ru: 'Эффективность', en: 'Performance' },
    'gen-hotkeys': { ru: 'Горячие клавиши', en: 'Hotkeys' },
    'gen-storage': { ru: 'Хранилище', en: 'Storage' },
    'app-player': { ru: 'Плеер', en: 'Player' },
    'app-cover': { ru: 'Обложка', en: 'Cover' },
    'app-ui': { ru: 'Интерфейс', en: 'Interface' },
    'app-tabs': { ru: 'Вкладки', en: 'Tabs' },
    'app-bg': { ru: 'Фон', en: 'Background' },
    'app-custom': { ru: 'Кастомизация', en: 'Customization' },
  };

  document.querySelectorAll('.settings-menu-item').forEach(el => {
    const sec = el.getAttribute('data-settings-section');
    if (sec && menuSectionNames[sec]) {
      const icon = el.querySelector('.material-icons')?.outerHTML || '';
      const text = menuSectionNames[sec][lang] || menuSectionNames[sec].ru;
      el.innerHTML = `${icon} ${text}`;
    }
  });

  document.documentElement.lang = lang;
}

// ==========================================
// Utilities
// ==========================================
function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function formatTime(secs) {
  if (!secs || isNaN(secs)) return '0:00';
  const m = Math.floor(secs / 60);
  const s = Math.floor(secs % 60)
    .toString()
    .padStart(2, '0');
  return `${m}:${s}`;
}

// --- Premium cover placeholder helper ---
function setCoverState(imgId, fallbackId, coverUrl, wrapperClass) {
  const img = document.getElementById(imgId);
  const fallback = fallbackId ? document.getElementById(fallbackId) : null;
  let wrapper = null;
  if (wrapperClass) wrapper = document.querySelector(wrapperClass);
  else if (img) wrapper = img.parentElement;
  const hasCover = !!coverUrl && String(coverUrl).trim() !== '';
  if (img) {
    if (hasCover) {
      img.src = coverUrl;
      img.style.display = '';
      img.style.opacity = '';
      img.style.visibility = '';
    } else {
      img.removeAttribute('src');
      img.src = '';
      img.style.display = 'none';
    }
  }
  if (fallback) fallback.style.display = hasCover ? 'none' : 'flex';
  if (wrapper) {
    wrapper.classList.toggle('has-cover', hasCover);
    wrapper.classList.toggle('is-empty', !hasCover);
  }
}
function applyAllCoverPlaceholders(trackOrCover) {
  const cover = typeof trackOrCover === 'string' ? trackOrCover : trackOrCover?.cover || '';
  setCoverState('fi-cover', 'fi-cover-fallback', cover, '.fi-cover-wrap');
  setCoverState('player-bar-cover', 'player-bar-cover-fallback', cover, '.player-bar-cover-wrap');
  setCoverState('right-player-cover', 'right-player-cover-fallback', cover, '.right-player-cover-shell');
  setCoverState('page-player-cover', 'page-player-cover-fallback', cover, '.pp-cover-wrap');
  setCoverState('fs-cover', 'fs-cover-fallback', cover, '.fs-cover-container');
  setCoverState('album-screen-cover', 'album-screen-cover-fallback', cover, '.album-screen-cover-wrap');
  // lib detail uses separate logic but we ensure fallback class
  const libWrap = document.querySelector('.lib-detail-cover');
  const libImg = document.getElementById('lib-detail-cover-img');
  const libFallback = document.getElementById('lib-detail-cover-fallback');
  const libFallbackIcon = document.getElementById('lib-detail-cover-icon');
  if (libWrap) {
    const has = !!cover;
    libWrap.classList.toggle('has-cover', has);
    libWrap.classList.toggle('is-empty', !has);
    if (libImg) {
      if (has) { libImg.src = cover; libImg.style.display = 'block'; }
      else { libImg.style.display = 'none'; libImg.removeAttribute('src'); }
    }
    if (libFallback) libFallback.style.display = has ? 'none' : 'flex';
    if (libFallbackIcon) {
      libFallbackIcon.style.display = 'flex';
      // icon visibility handled by wrapper; keep text updated elsewhere
    }
  }
}

function preloadTrackStreams(tracks) {
  if (!tracks || !tracks.length) return;
  const ids = tracks
    .slice(0, 5)
    .map(t => t.id)
    .filter(Boolean);
  if (ids.length) fetch('/api/preload?ids=' + ids.join(',')).catch(() => {});
}

function hexToRgb(hex) {
  const bigint = parseInt(hex.replace('#', ''), 16);
  return { r: (bigint >> 16) & 255, g: (bigint >> 8) & 255, b: bigint & 255 };
}

function estimateLocalStorageSize() {
  let total = 0;
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i);
    total += (key.length + (localStorage.getItem(key) || '').length) * 2;
  }
  return Math.round(total / 1024);
}

function safeClick(id, callback) {
  const el = document.getElementById(id);
  if (el) el.onclick = callback;
}

function showToast(msg) {
  const toast = document.createElement('div');
  toast.className = 'toast-msg';
  toast.innerText = msg;
  toast.style.cssText =
    'position:fixed;bottom:100px;left:50%;transform:translateX(-50%);background:var(--bg-elevated);color:var(--text-primary);padding:12px 24px;border-radius:24px;font-size:13px;z-index:99999;opacity:0;transition:opacity 0.3s;box-shadow:0 4px 12px rgba(0,0,0,0.5);';
  document.body.appendChild(toast);
  requestAnimationFrame(() => {
    toast.style.opacity = '1';
  });
  setTimeout(() => {
    toast.style.opacity = '0';
    setTimeout(() => toast.remove(), 300);
  }, 2500);
}

function notifyTrackChange(track) {
  if (!appSettings.trackNotifications || !track) return;
  try {
    if (typeof Notification === 'undefined' || Notification.permission !== 'granted') return;
    const n = new Notification(track.title || 'Votify', {
      body: track.artist || '',
      icon: track.cover || 'icon.png',
      silent: true,
    });
    setTimeout(() => n.close(), 4000);
  } catch (e) {
    /* notifications unsupported */
  }
}

// ==========================================
// Modal System
// ==========================================
const modalOverlay = document.getElementById('votify-modal-overlay');
const modalHeader = document.getElementById('votify-modal-header');
const modalBody = document.getElementById('votify-modal-body');
const modalOk = document.getElementById('votify-modal-ok');
const modalCancel = document.getElementById('votify-modal-cancel');

function showModal({ title, bodyHtml, okText = 'OK', cancelText = 'Отмена' }) {
  return new Promise(resolve => {
    if (!modalOverlay || !modalHeader || !modalBody || !modalOk || !modalCancel) {
      resolve(null);
      return;
    }
    modalHeader.innerText = title;
    modalBody.innerHTML = bodyHtml;
    modalOk.innerText = okText;
    modalCancel.innerText = cancelText;
    modalOverlay.classList.remove('hidden');
    const cleanup = () => {
      modalOverlay.classList.add('hidden');
      modalOk.onclick = null;
      modalCancel.onclick = null;
      modalOverlay.onclick = null;
    };
    modalOk.onclick = () => {
      const input = modalBody.querySelector('input');
      cleanup();
      resolve(input ? input.value : true);
    };
    modalCancel.onclick = () => {
      cleanup();
      resolve(null);
    };
    modalOverlay.onclick = e => {
      if (e.target === modalOverlay) {
        cleanup();
        resolve(null);
      }
    };
    setTimeout(() => {
      const input = modalBody.querySelector('input');
      if (input) input.focus();
    }, 100);
  });
}

async function promptModal(title, placeholder = '') {
  return await showModal({
    title,
    bodyHtml: `<input type="text" placeholder="${escapeHtml(placeholder)}" style="width:100%;padding:10px 14px;background:var(--bg-surface);border:1px solid var(--bg-highlight);border-radius:8px;color:var(--text-primary);font-size:14px;outline:none;">`,
    okText: 'OK',
    cancelText: translations[appSettings.lang]['back-btn'] || 'Отмена',
  });
}

async function confirmModal(title, message) {
  const result = await showModal({
    title,
    bodyHtml: `<p style="color:var(--text-secondary);line-height:1.5;">${escapeHtml(message)}</p>`,
    okText: 'OK',
    cancelText: translations[appSettings.lang]['back-btn'] || 'Отмена',
  });
  return result !== null;
}

async function playlistPickerModal(trackTitle) {
  const availablePlaylists = Object.keys(playlists);
  if (availablePlaylists.length === 0) return null;
  const chipsHtml = availablePlaylists
    .map(
      name =>
        `<button class="modal-playlist-chip" data-playlist="${escapeHtml(name)}" style="padding:8px 16px;background:var(--bg-elevated);border:1px solid var(--bg-highlight);border-radius:20px;color:var(--text-primary);cursor:pointer;font-size:13px;transition:all 0.2s;">${escapeHtml(name)}</button>`
    )
    .join('');
  return new Promise(resolve => {
    if (!modalOverlay || !modalHeader || !modalBody || !modalOk || !modalCancel) {
      resolve(null);
      return;
    }
    modalHeader.innerText = 'Добавить в плейлист';
    modalBody.innerHTML = `<p style="margin-bottom:12px;color:var(--text-secondary);">${escapeHtml(trackTitle)}</p><div style="display:flex;flex-wrap:wrap;gap:8px;">${chipsHtml}</div>`;
    modalOk.style.display = 'none';
    modalCancel.innerText = translations[appSettings.lang]['back-btn'] || 'Отмена';
    modalOverlay.classList.remove('hidden');
    const cleanup = () => {
      modalOverlay.classList.add('hidden');
      modalOk.style.display = '';
      modalOk.onclick = null;
      modalCancel.onclick = null;
      modalOverlay.onclick = null;
      modalBody.querySelectorAll('.modal-playlist-chip').forEach(c => {
        c.onclick = null;
      });
    };
    modalBody.querySelectorAll('.modal-playlist-chip').forEach(chip => {
      chip.onmouseenter = () => {
        chip.style.background = 'var(--accent)';
        chip.style.borderColor = 'var(--accent)';
      };
      chip.onmouseleave = () => {
        chip.style.background = 'var(--bg-elevated)';
        chip.style.borderColor = 'var(--bg-highlight)';
      };
      chip.onclick = () => {
        const picked = chip.getAttribute('data-playlist');
        cleanup();
        resolve(picked);
      };
    });
    modalCancel.onclick = () => {
      cleanup();
      resolve(null);
    };
    modalOverlay.onclick = e => {
      if (e.target === modalOverlay) {
        cleanup();
        resolve(null);
      }
    };
  });
}

// ==========================================
// Track Rendering
// ==========================================
function isTrackFavorite(track) {
  const fav = playlists['Избранное'] || [];
  return fav.some(t => t.id === track.id && t.title === track.title);
}

function toggleFavorite(track) {
  if (!playlists['Избранное']) playlists['Избранное'] = [];
  const fav = playlists['Избранное'];
  const idx = fav.findIndex(t => t.id === track.id && t.title === track.title);
  if (idx !== -1) {
    fav.splice(idx, 1);
  } else {
    fav.push({ ...track });
  }
  savePlaylists();
  renderSidebarPlaylists();
  return idx === -1;
}

function renderTrackRows(container, tracks, options = {}) {
  if (!container) return;
  const {
    showAddButton = false,
    showDeleteButton = false,
    showFavoriteButton = true,
    playButtonClass = 'play-track-btn',
    addButtonClass = 'add-to-playlist-btn',
    playlistName = '',
  } = options;
  if (!tracks || tracks.length === 0) {
    container.innerHTML = `<p class="empty-msg">${translations[appSettings.lang]['empty-msg']}</p>`;
    return;
  }
  container.innerHTML = tracks
    .map((track, idx) => {
      const title = escapeHtml(track.title || 'Track');
      const artist = escapeHtml(track.artist || 'Unknown');
      const isFav = isTrackFavorite(track);
      const coverMarkup = track.cover
        ? `<img class="track-cover" src="${escapeHtml(track.cover)}" alt="${title}">`
        : `<div class="track-cover-fallback"><i class="material-icons">music_note</i></div>`;
      return `
      <div class="track-item" data-track-id="${escapeHtml(track.id || '')}">
        <div class="track-main">
          ${coverMarkup}
          <div class="track-info">
            <span class="track-title">${title}</span>
            <span class="track-artist clickable-artist">${artist}</span>
          </div>
        </div>
        <div class="track-actions">
          <button class="m3-icon-btn ${playButtonClass}" data-index="${idx}"><i class="material-icons">play_arrow</i></button>
          ${showFavoriteButton ? `<button class="m3-icon-btn fav-btn ${isFav ? 'is-fav' : ''}" data-index="${idx}" title="В избранное"><i class="material-icons">${isFav ? 'favorite' : 'favorite_border'}</i></button>` : ''}
          ${showAddButton ? `<button class="m3-icon-btn ${addButtonClass}" data-index="${idx}"><i class="material-icons">playlist_add</i></button>` : ''}
          ${showDeleteButton ? `<button class="m3-icon-btn delete-track-btn" data-index="${idx}" title="Удалить из плейлиста"><i class="material-icons">close</i></button>` : ''}
        </div>
      </div>`;
    })
    .join('');

  container.querySelectorAll(`.${playButtonClass}`).forEach(btn => {
    btn.onclick = () => {
      const idx = Number(btn.getAttribute('data-index'));
      currentPlaylist = tracks;
      currentTrackIndex = idx;
      playTrack(tracks[idx]);
    };
  });
  container.querySelectorAll('.clickable-artist').forEach((artistEl, idx) => {
    artistEl.addEventListener('click', e => {
      e.stopPropagation();
      const artistName = tracks[idx]?.artist || artistEl.textContent;
      if (artistName) openArtistPage(artistName);
    });
  });
  container.querySelectorAll('.track-item').forEach((row, idx) => {
    row.addEventListener('click', e => {
      if (e.target.closest('button')) return;
      currentPlaylist = tracks;
      currentTrackIndex = idx;
      playTrack(tracks[idx]);
    });
  });
  container.querySelectorAll('.fav-btn').forEach(btn => {
    btn.onclick = () => {
      const idx = Number(btn.getAttribute('data-index'));
      const track = tracks[idx];
      const added = toggleFavorite(track);
      btn.classList.toggle('is-fav', added);
      btn.querySelector('.material-icons').textContent = added ? 'favorite' : 'favorite_border';
      showToast(added ? 'Добавлено в избранное' : 'Удалено из избранного');
    };
  });
  if (showAddButton) {
    container.querySelectorAll(`.${addButtonClass}`).forEach(btn => {
      btn.onclick = async () => {
        const idx = Number(btn.getAttribute('data-index'));
        const track = tracks[idx];
        const targetPlaylist = await playlistPickerModal(track.title || 'Track');
        if (targetPlaylist && playlists[targetPlaylist]) {
          playlists[targetPlaylist].push(track);
          savePlaylists();
          renderSidebarPlaylists();
        }
      };
    });
  }
  if (showDeleteButton && playlistName) {
    container.querySelectorAll('.delete-track-btn').forEach(btn => {
      btn.onclick = async () => {
        const idx = Number(btn.getAttribute('data-index'));
        const track = tracks[idx];
        const confirmed = await confirmModal(
          'Удалить трек',
          `Удалить «${track.title}» из плейлиста?`
        );
        if (!confirmed) return;
        const pl = playlists[playlistName];
        if (!pl) return;
        const ti = pl.findIndex(t => t.id === track.id && t.title === track.title);
        if (ti !== -1) pl.splice(ti, 1);
        savePlaylists();
        openPlaylist(playlistName);
      };
    });
  }
}

function appendTrackRows(container, tracks, options = {}) {
  if (!container || !tracks || tracks.length === 0) return;
  const {
    showAddButton = false,
    playButtonClass = 'play-track-btn',
    addButtonClass = 'add-to-playlist-btn',
  } = options;
  const existingCount = container.querySelectorAll('.track-item').length;
  const sentinel = document.getElementById('load-more-sentinel');

  tracks.forEach((track, i) => {
    const idx = existingCount + i;
    const title = escapeHtml(track.title || 'Track');
    const artist = escapeHtml(track.artist || 'Unknown');
    const coverMarkup = track.cover
      ? `<img class="track-cover" src="${escapeHtml(track.cover)}" alt="${title}">`
      : `<div class="track-cover-fallback"><i class="material-icons">music_note</i></div>`;
    const div = document.createElement('div');
    div.className = 'track-item';
    div.setAttribute('data-track-id', escapeHtml(track.id || ''));
    div.innerHTML = `
      <div class="track-main">${coverMarkup}<div class="track-info"><span class="track-title">${title}</span><span class="track-artist clickable-artist">${artist}</span></div></div>
      <div class="track-actions">
        <button class="m3-icon-btn ${playButtonClass}" data-index="${idx}"><i class="material-icons">play_arrow</i></button>
        ${showAddButton ? `<button class="m3-icon-btn ${addButtonClass}" data-index="${idx}"><i class="material-icons">playlist_add</i></button>` : ''}
      </div>`;
    const playBtn = div.querySelector(`.${playButtonClass}`);
    if (playBtn) {
      playBtn.onclick = () => {
        currentPlaylist = searchAllTracks.length ? searchAllTracks : tracks;
        currentTrackIndex = searchAllTracks.length ? searchAllTracks.indexOf(track) : idx;
        if (currentTrackIndex < 0) currentTrackIndex = idx;
        playTrack(track);
      };
    }
    const artistEl = div.querySelector('.clickable-artist');
    if (artistEl) {
      artistEl.addEventListener('click', e => {
        e.stopPropagation();
        if (track.artist) openArtistPage(track.artist);
      });
    }
    div.addEventListener('click', e => {
      if (e.target.closest('button')) return;
      currentPlaylist = searchAllTracks.length ? searchAllTracks : tracks;
      currentTrackIndex = searchAllTracks.length ? searchAllTracks.indexOf(track) : idx;
      if (currentTrackIndex < 0) currentTrackIndex = idx;
      playTrack(track);
    });
    if (showAddButton) {
      const addBtn = div.querySelector(`.${addButtonClass}`);
      if (addBtn) {
        addBtn.onclick = async () => {
          const targetPlaylist = await playlistPickerModal(track.title || 'Track');
          if (targetPlaylist && playlists[targetPlaylist]) {
            playlists[targetPlaylist].push(track);
            savePlaylists();
            renderSidebarPlaylists();
          }
        };
      }
    }
    if (sentinel) container.insertBefore(div, sentinel);
    else container.appendChild(div);
  });
}

// ==========================================
// Loading & Sounds
// ==========================================
const loadingOverlay = document.getElementById('loading-overlay');
const loadingTitle = document.getElementById('loading-title');
const loadingSubtitle = document.getElementById('loading-subtitle');

function getLoadingAudioContext() {
  if (!window.AudioContext && !window.webkitAudioContext) return null;
  if (!loadingAudioContext) {
    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    loadingAudioContext = new AudioContextClass();
  }
  return loadingAudioContext;
}

async function playLoadingSound() {
  if (appSettings.loadingSound === false) return;
  const ctx = getLoadingAudioContext();
  if (!ctx) return;
  if (ctx.state === 'suspended') {
    try {
      await ctx.resume();
    } catch {
      return;
    }
  }
  const now = ctx.currentTime;
  const masterGain = ctx.createGain();
  masterGain.gain.setValueAtTime(0.0001, now);
  masterGain.gain.exponentialRampToValueAtTime(0.06, now + 0.02);
  masterGain.gain.exponentialRampToValueAtTime(0.0001, now + 0.45);
  masterGain.connect(ctx.destination);
  const melody = [
    { freq: 523.25, start: 0.0, duration: 0.09 },
    { freq: 659.25, start: 0.09, duration: 0.1 },
    { freq: 783.99, start: 0.2, duration: 0.14 },
  ];
  melody.forEach(note => {
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = 'triangle';
    osc.frequency.setValueAtTime(note.freq, now + note.start);
    gain.gain.setValueAtTime(0.0001, now + note.start);
    gain.gain.exponentialRampToValueAtTime(0.22, now + note.start + 0.02);
    gain.gain.exponentialRampToValueAtTime(0.0001, now + note.start + note.duration);
    osc.connect(gain);
    gain.connect(masterGain);
    osc.start(now + note.start);
    osc.stop(now + note.start + note.duration + 0.03);
  });
  const bass = ctx.createOscillator();
  const bassGain = ctx.createGain();
  bass.type = 'sine';
  bass.frequency.setValueAtTime(130.81, now);
  bassGain.gain.setValueAtTime(0.0001, now);
  bassGain.gain.exponentialRampToValueAtTime(0.12, now + 0.03);
  bassGain.gain.exponentialRampToValueAtTime(0.0001, now + 0.32);
  bass.connect(bassGain);
  bassGain.connect(masterGain);
  bass.start(now);
  bass.stop(now + 0.35);
}

function playClickSound() {
  try {
    const ctx = getLoadingAudioContext();
    if (!ctx) return;
    if (ctx.state === 'suspended') ctx.resume().catch(() => {});
    const now = ctx.currentTime;
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = 'sine';
    osc.frequency.setValueAtTime(800, now);
    osc.frequency.exponentialRampToValueAtTime(400, now + 0.06);
    gain.gain.setValueAtTime(0.08, now);
    gain.gain.exponentialRampToValueAtTime(0.0001, now + 0.08);
    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.start(now);
    osc.stop(now + 0.1);
  } catch (e) {
    /* ignore audio context failure */
  }
}

document.addEventListener('click', e => {
  if (appSettings.uiSounds === false) return;
  const btn = e.target.closest(
    "button, .btn, .nav-btn, .rec-tile, .suggestion-item, .play-track-btn, .sidebar-playlist-item, .home-tile, .filter-pill, .theme-card, .bg-card, .modal-playlist-chip, [role='button']"
  );
  if (btn) playClickSound();
});

let loadingTimeout = null;

// Failsafe: every loading state must have a short, finite lifetime.
setTimeout(() => {
  if (loadingOverlay) {
    loadingOverlay.classList.add('hidden');
    document.body.classList.remove('loading-active');
  }
  loadingOperations = 0;
}, 12000);

function setLoadingState(active, subtitleKey = 'loader-search') {
  if (!loadingOverlay || !loadingTitle || !loadingSubtitle) return;
  if (active) {
    const shouldPlaySound = loadingOperations === 0;
    loadingOperations += 1;
    loadingTitle.innerText = translations[appSettings.lang]['loader-title'];
    loadingSubtitle.innerText =
      translations[appSettings.lang][subtitleKey] ||
      translations[appSettings.lang]['loader-search'];
    loadingOverlay.classList.remove('hidden');
    document.body.classList.add('loading-active');
    if (shouldPlaySound) playLoadingSound().catch(() => {});
    clearTimeout(loadingTimeout);
    loadingTimeout = setTimeout(() => {
      loadingOperations = 0;
      loadingOverlay.classList.add('hidden');
      document.body.classList.remove('loading-active');
    }, 12000);
    return;
  }
  loadingOperations = Math.max(0, loadingOperations - 1);
  if (loadingOperations === 0) {
    clearTimeout(loadingTimeout);
    loadingOverlay.classList.add('hidden');
    document.body.classList.remove('loading-active');
  }
}

// ==========================================
// Playlists UI
// ==========================================
// ==========================================
// Playlists UI (Master-Detail Layout)
// ==========================================
let currentActiveLibItem = 'Избранное';

function renderPlaylists() {
  // Update counts for system items
  const favCount = (playlists['Избранное'] || []).length;
  const offlineCount = (state.offlineTracks || []).length;

  const favCountEl = document.getElementById('lib-fav-count');
  if (favCountEl) favCountEl.textContent = favCount > 0 ? `${favCount} треков` : 'Нет треков';

  const offlineCountEl = document.getElementById('lib-offline-count');
  if (offlineCountEl)
    offlineCountEl.textContent = offlineCount > 0 ? `${offlineCount} треков` : 'Нет треков';

  // Render user playlists list in left sidebar / grid
  const container = document.getElementById('lib-playlists-list');
  if (container) {
    const keys = Object.keys(playlists).filter(k => k !== 'Избранное');
    if (keys.length === 0) {
      container.innerHTML =
        '<div style="font-size:13px;color:rgba(255,255,255,0.4);padding:18px 0;grid-column:1/-1;">У вас пока нет созданных плейлистов. Нажмите «Создать плейлист», чтобы добавить первый.</div>';
    } else {
      container.innerHTML = keys
        .map(key => {
          const list = playlists[key] || [];
          const cover = list.length > 0 && list[0].cover ? list[0].cover : '';
          return `
        <div class="playlist-card" data-playlist="${escapeHtml(key)}">
          <div class="card-cover-wrap">
            ${
              cover
                ? `<img class="card-cover" src="${escapeHtml(cover)}" alt="${escapeHtml(key)}" />`
                : `<div class="card-cover-placeholder"><i class="material-icons">queue_music</i></div>`
            }
            <button class="card-play-btn" title="Воспроизвести"><i class="material-icons">play_arrow</i></button>
          </div>
          <div class="card-info">
            <div class="card-title">${escapeHtml(key)}</div>
            <div class="card-sub">${list.length > 0 ? list.length + ' треков' : 'Нет треков'}</div>
          </div>
        </div>
      `;
        })
        .join('');
    }

    // Attach click listeners for playlists
    container.querySelectorAll('.playlist-card').forEach(item => {
      item.onclick = () => {
        const plName = item.getAttribute('data-playlist');
        openPlaylist(plName);
      };
    });
  }

  // Attach system item click listeners
  const favItem = document.getElementById('lib-item-favorites');
  if (favItem) {
    favItem.onclick = () => openPlaylist('Избранное');
  }

  const offlineItem = document.getElementById('lib-item-offline');
  if (offlineItem) {
    offlineItem.onclick = () => openPlaylist('__OFFLINE__');
  }

  // Filter Tabs Event Listeners
  const filterTabs = document.getElementById('library-filter-tabs');
  if (filterTabs) {
    filterTabs.querySelectorAll('.lib-tab-btn').forEach(btn => {
      btn.onclick = () => {
        filterTabs.querySelectorAll('.lib-tab-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        const tab = btn.dataset.tab;
        const playlistsSection = document.getElementById('lib-playlists-section');
        const detailPane = document.getElementById('lib-detail-pane');

        if (tab === 'favorites') {
          if (playlistsSection) playlistsSection.style.display = 'none';
          openPlaylist('Избранное');
        } else if (tab === 'playlists') {
          if (playlistsSection) playlistsSection.style.display = 'block';
          if (detailPane) detailPane.style.display = 'none';
        } else {
          // All
          if (playlistsSection) playlistsSection.style.display = 'block';
          if (detailPane) detailPane.style.display = 'none';
        }
      };
    });
  }

  // Action buttons
  safeClick('lib-add-playlist-btn', createPlaylist);
  safeClick('lib-refresh-btn', () => {
    renderPlaylists();
    showToast('Медиатека обновлена');
  });

  // Do NOT auto-open any playlist — let user choose via tabs or card clicks
  const detailPane = document.getElementById('lib-detail-pane');
  if (detailPane) detailPane.style.display = 'none';
}

function renderSidebarPlaylists() {
  const container = document.getElementById('sidebar-playlists-list');
  if (!container) return;
  const names = Object.keys(playlists);
  if (names.length === 0) {
    container.innerHTML =
      '<div class="sidebar-playlist-item" style="opacity:0.3;cursor:default;font-size:11px;">No playlists</div>';
    return;
  }
  container.innerHTML = names
    .map(
      name => `
    <div class="sidebar-playlist-item" data-name="${escapeHtml(name)}">
      <i class="material-icons">queue_music</i>
      <span>${escapeHtml(name)}</span>
      <span class="sp-count">${playlists[name].length}</span>
    </div>
  `
    )
    .join('');
  container.querySelectorAll('.sidebar-playlist-item').forEach(item => {
    item.onclick = () => {
      switchScreen('folders-screen', 'nav-folders-btn');
      openPlaylist(item.getAttribute('data-name'));
    };
  });
}

async function createPlaylist() {
  const name = (await promptModal('Новый плейлист', 'Например: В дорогу'))?.trim();
  if (!name) return;
  if (playlists[name]) {
    showToast('Плейлист с таким названием уже есть');
    return;
  }
  playlists[name] = [];
  savePlaylists();
  renderSidebarPlaylists();
  renderPlaylists();
  showToast(`Плейлист «${name}» создан`);
}

function openPlaylist(name) {
  currentActiveLibItem = name;

  const detailPane = document.getElementById('lib-detail-pane');
  if (detailPane) {
    detailPane.style.display = 'block';
  }

  // Close button listener
  safeClick('lib-close-detail-btn', () => {
    if (detailPane) detailPane.style.display = 'none';
  });

  // Highlight active item in sidebar/grid
  document.querySelectorAll('.lib-item').forEach(item => {
    const isFav = name === 'Избранное' && item.dataset.system === 'favorites';
    const isPl = item.dataset.playlist === name;
    item.classList.toggle('active', isFav || isPl);
  });

  let tracks = [];
  let title = name;
  let subtitle = 'Создан Votify';

  if (name === 'Избранное') {
    tracks = playlists['Избранное'] || [];
    title = 'Любимые треки';
    subtitle = `${tracks.length} треков в вашей коллекции`;
  } else {
    tracks = playlists[name] || [];
    title = name;
    subtitle = `${tracks.length} треков`;
  }

  // Update header card
  const titleEl = document.getElementById('lib-detail-title');
  const subEl = document.getElementById('lib-detail-sub');
  if (titleEl) titleEl.textContent = title;
  if (subEl) subEl.textContent = subtitle;

  // Use premium placeholder helper for lib detail
  const libCoverUrl = tracks.length > 0 ? tracks[0].cover || '' : '';
  const libWrap2 = document.querySelector('.lib-detail-cover');
  const coverImg = document.getElementById('lib-detail-cover-img');
  const coverFallback = document.getElementById('lib-detail-cover-fallback');
  const coverIcon = document.getElementById('lib-detail-cover-icon');
  if (libWrap2) {
    const has = !!libCoverUrl;
    libWrap2.classList.toggle('has-cover', has);
    libWrap2.classList.toggle('is-empty', !has);
    if (coverImg) {
      if (has) { coverImg.src = libCoverUrl; coverImg.style.display = 'block'; }
      else { coverImg.style.display = 'none'; coverImg.removeAttribute('src'); }
    }
    if (coverFallback) coverFallback.style.display = has ? 'none' : 'flex';
    if (coverIcon) {
      coverIcon.textContent = name === 'Избранное' ? 'favorite' : 'queue_music';
    }
  }

  // Play button
  safeClick('lib-play-all-btn', () => {
    if (!tracks.length) {
      showToast('Нет треков для воспроизведения');
      return;
    }
    currentPlaylist = [...tracks];
    currentTrackIndex = 0;
    playTrack(tracks[0]);
  });

  // Shuffle button
  safeClick('lib-shuffle-all-btn', () => {
    if (!tracks.length) {
      showToast('Нет треков');
      return;
    }
    currentPlaylist = [...tracks];
    isShuffle = true;
    currentTrackIndex = Math.floor(Math.random() * tracks.length);
    playTrack(tracks[currentTrackIndex]);
  });

  // Delete button
  safeClick('lib-delete-active-btn', async () => {
    if (name === 'Избранное') {
      showToast('Системную подборку нельзя удалить');
      return;
    }
    const confirmed = await confirmModal('Удалить плейлист', `Удалить плейлист «${name}»?`);
    if (confirmed) {
      delete playlists[name];
      savePlaylists();
      renderPlaylists();
      if (detailPane) detailPane.style.display = 'none';
    }
  });

  // Content body (Empty state or Tracklist)
  const emptyState = document.getElementById('lib-empty-state');
  const tracklistWrap = document.getElementById('lib-tracklist-wrap');

  if (tracks.length === 0) {
    if (emptyState) emptyState.style.display = 'flex';
    if (tracklistWrap) {
      tracklistWrap.style.display = 'none';
      tracklistWrap.innerHTML = '';
    }
  } else {
    if (emptyState) emptyState.style.display = 'none';
    if (tracklistWrap) {
      tracklistWrap.style.display = 'block';
      renderTrackRows(tracklistWrap, tracks, { showAddButton: true });
    }
  }
}

safeClick('create-playlist-btn', async () => {
  const name = (await promptModal('Новый плейлист', 'Например: В дорогу'))?.trim();
  if (!name) return;
  if (playlists[name]) {
    showToast('Плейлист с таким названием уже есть');
    return;
  }
  playlists[name] = [];
  savePlaylists();
  renderSidebarPlaylists();
  renderPlaylists();
  showToast(`Плейлист «${name}» создан`);
});

safeClick('import-playlist-btn', async () => {
  const url = (
    await promptModal('Импорт плейлиста', 'Ссылка на YouTube, Spotify или SoundCloud')
  )?.trim();
  if (!url) return;
  if (!/^https?:\/\//i.test(url)) {
    showToast('Введите корректную ссылку');
    return;
  }

  const hostname = new URL(url).hostname.toLowerCase();
  const isSoundCloud = hostname.includes('soundcloud.com');

  setLoadingState(true, 'loader-search');
  try {
    const endpoint = isSoundCloud ? `/api/soundcloud/import?url=` : `/api/playlist?url=`;
    const res = await fetch(`${endpoint}${encodeURIComponent(url)}`);
    const data = await res.json();
    if (!res.ok || !Array.isArray(data.tracks))
      throw new Error(data.error || 'Не удалось импортировать плейлист');
    const suggested = isSoundCloud
      ? data.name || 'SoundCloud импорт'
      : hostname.includes('spotify')
        ? 'Spotify импорт'
        : 'YouTube импорт';
    const name = (await promptModal('Название нового плейлиста', suggested))?.trim();
    if (!name) return;
    playlists[name] = data.tracks;
    savePlaylists();
    renderSidebarPlaylists();
    renderPlaylists();
    showToast(`Импортировано треков: ${data.tracks.length}`);
  } catch (error) {
    showToast(error.message || 'Ошибка импорта');
  } finally {
    setLoadingState(false);
  }
});

// ==========================================
// Navigation
// ==========================================
let previousScreenId = 'home-screen';
let previousActiveBtnId = 'nav-home-btn';
let currentScreenId = 'home-screen';
let isBooting = true;

const screenPageTitles = {
  'home-screen': 'Главная',
  'player-screen': 'Плеер',
  'search-screen': 'Поиск',
  'folders-screen': 'Моя медиатека',
  'workshop-screen': 'Мастерская тем',
  'artist-screen': 'Артист',
};

let artistRequestId = 0;

function formatTrackCount(count) {
  if (appSettings.lang !== 'ru') return `${count} ${count === 1 ? 'track' : 'tracks'}`;
  const mod10 = count % 10;
  const mod100 = count % 100;
  const word =
    mod10 === 1 && mod100 !== 11
      ? 'трек'
      : mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)
        ? 'трека'
        : 'треков';
  return `${count} ${word}`;
}

// Helper to generate monthly listeners count realistically
function getArtistMonthlyListeners(name, totalViews = 0) {
  if (totalViews > 0) {
    const computed = Math.round(totalViews * 0.42);
    return computed.toLocaleString('ru-RU');
  }
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = (hash << 5) - hash + name.charCodeAt(i);
    hash |= 0;
  }
  const abs = Math.abs(hash);
  // Realistic listener count algorithm
  const base = 480000 + (abs % 4200000);
  return base.toLocaleString('ru-RU');
}

// Recent Artists Management
function recordRecentArtist(track) {
  if (!track || !track.artist || track.artist === '—' || track.artist === 'Неизвестный исполнитель')
    return;
  const name = track.artist.trim();
  let recent = JSON.parse(localStorage.getItem('votify-recent-artists') || '[]');

  recent = recent.filter(a => a.name.toLowerCase() !== name.toLowerCase());
  recent.unshift({
    name: name,
    cover: track.cover || '',
    updatedAt: Date.now(),
  });

  recent = recent.slice(0, 12);
  localStorage.setItem('votify-recent-artists', JSON.stringify(recent));
  renderRecentArtists();
}

function renderRecentArtists() {
  const container = document.getElementById('home-recent-artists');
  if (!container) return;

  let recent = JSON.parse(localStorage.getItem('votify-recent-artists') || '[]');

  // If no explicit recent artists, generate from history
  if (!recent.length) {
    const history = JSON.parse(
      localStorage.getItem('listeningHistory') || localStorage.getItem('votify-history') || '[]'
    );
    const seen = new Set();
    for (const t of history) {
      if (t.artist && t.artist !== '—' && !seen.has(t.artist.toLowerCase())) {
        seen.add(t.artist.toLowerCase());
        recent.push({ name: t.artist, cover: t.cover || '' });
        if (recent.length >= 10) break;
      }
    }
  }

  const section = document.getElementById('home-recent-artists-section');
  if (!recent.length) {
    if (section) section.style.display = 'none';
    return;
  }

  if (section) section.style.display = 'block';

  container.innerHTML = recent
    .map(
      a => `
    <div class="artist-card" data-artist="${escapeHtml(a.name)}">
      <div class="artist-card-avatar">
        ${a.cover ? `<img src="${escapeHtml(a.cover)}" alt="${escapeHtml(a.name)}"/>` : `<i class="material-icons">person</i>`}
      </div>
      <div class="artist-card-name">${escapeHtml(a.name)}</div>
      <div class="artist-card-sub">Исполнитель</div>
    </div>
  `
    )
    .join('');

  container.querySelectorAll('.artist-card').forEach(card => {
    card.onclick = () => {
      const name = card.getAttribute('data-artist');
      if (name) openArtistPage(name);
    };
  });
}

function scrollRecentArtists(direction) {
  const container = document.getElementById('home-recent-artists');
  if (!container) return;
  const distance = Math.max(280, Math.round(container.clientWidth * 0.82));
  container.scrollBy({ left: distance * direction, behavior: 'smooth' });
}

safeClick('recent-artists-prev', () => scrollRecentArtists(-1));
safeClick('recent-artists-next', () => scrollRecentArtists(1));

async function openArtistPage(artistName) {
  const name = String(artistName || '').trim();
  if (!name || name === 'Unknown' || name === '—') return;

  if (currentScreenId !== 'artist-screen') {
    previousScreenId = currentScreenId;
    const activeNav = document.querySelector('.nav-btn.active');
    previousActiveBtnId = activeNav?.id || previousActiveBtnId || 'nav-home-btn';
  }

  switchScreen('artist-screen', previousActiveBtnId);
  const requestId = ++artistRequestId;
  const nameEl = document.getElementById('artist-name');
  const countEl = document.getElementById('artist-track-count');
  const listenersCountEl = document.getElementById('artist-listeners-count');
  const statusEl = document.getElementById('artist-status');
  const tracksEl = document.getElementById('artist-tracks');
  const albumsEl = document.getElementById('artist-albums');
  const avatarEl = document.getElementById('artist-avatar');
  const pageTitle = document.getElementById('page-title');

  if (nameEl) nameEl.textContent = name;
  if (pageTitle) pageTitle.textContent = name;
  if (listenersCountEl) listenersCountEl.textContent = getArtistMonthlyListeners(name);
  if (countEl) countEl.textContent = 'Загрузка треков…';
  if (statusEl) statusEl.textContent = '';
  if (tracksEl)
    tracksEl.innerHTML =
      '<div class="artist-loading"><div class="spinner"></div><span>Загружаем треки исполнителя…</span></div>';
  if (albumsEl) albumsEl.innerHTML = '';
  if (avatarEl) avatarEl.innerHTML = '<i class="material-icons">person</i>';

  try {
    const response = await fetch(`/api/artist?name=${encodeURIComponent(name)}&limit=50`);
    const data = await response.json().catch(() => ({}));
    if (requestId !== artistRequestId) return;
    if (!response.ok) throw new Error(data.error || 'Не удалось загрузить исполнителя');

    const tracks = Array.isArray(data.tracks) ? data.tracks : [];
    if (countEl) countEl.textContent = formatTrackCount(tracks.length);

    const saveAllBtn = document.getElementById('artist-save-all-playlist-btn');
    if (saveAllBtn) {
      saveAllBtn.onclick = async () => {
        if (!tracks.length) {
          if (typeof showToast === 'function') showToast('Нет треков для сохранения');
          return;
        }
        const targetPlaylist = await playlistPickerModal(name);
        if (targetPlaylist && playlists[targetPlaylist]) {
          playlists[targetPlaylist].push(...tracks);
          savePlaylists();
          renderSidebarPlaylists();
          if (typeof showToast === 'function')
            showToast(`Сохранено ${tracks.length} треков в «${targetPlaylist}»`);
        }
      };
    }

    if (tracks.length && avatarEl) {
      const cover = tracks.find(track => track.cover)?.cover;
      if (cover) avatarEl.innerHTML = `<img src="${escapeHtml(cover)}" alt="${escapeHtml(name)}">`;
    }

    // Render Top Hits (Top 8 tracks)
    const topHits = tracks.slice(0, 8);
    renderTrackRows(tracksEl, topHits, {
      showAddButton: true,
      playButtonClass: 'play-artist-track-btn',
    });

    // Group tracks into Albums & Singles
    if (albumsEl) {
      const albumsMap = new Map();
      const singlesList = [];

      // 1. Check if explicit album metadata exists
      const tracksWithAlbum = tracks.filter(
        t => t.album && t.album.toLowerCase() !== t.title.toLowerCase()
      );

      if (tracksWithAlbum.length >= 2) {
        tracks.forEach(track => {
          const hasAlbum = track.album && track.album.toLowerCase() !== track.title.toLowerCase();
          if (hasAlbum) {
            const albumKey = track.album.toLowerCase();
            if (!albumsMap.has(albumKey)) {
              albumsMap.set(albumKey, {
                title: track.album,
                cover: track.cover || '',
                tracks: [track],
                type: 'Альбом',
              });
            } else {
              albumsMap.get(albumKey).tracks.push(track);
            }
          } else {
            singlesList.push(track);
          }
        });
      } else {
        // Automatically structure artist tracks into Albums (collections) and Singles
        if (tracks.length >= 4) {
          const alb1Size = Math.min(6, Math.floor(tracks.length * 0.55));
          const alb1Tracks = tracks.slice(0, alb1Size);
          albumsMap.set('album-greatest-hits', {
            title: `${name} — Greatest Hits`,
            cover: alb1Tracks[0]?.cover || '',
            tracks: alb1Tracks,
            type: 'Альбом',
          });

          const remaining = tracks.slice(alb1Size);
          if (remaining.length >= 3) {
            const alb2Size = Math.min(6, Math.floor(remaining.length * 0.6));
            const alb2Tracks = remaining.slice(0, alb2Size);
            albumsMap.set('album-collection', {
              title: `${name} — Album Collection`,
              cover: alb2Tracks[0]?.cover || '',
              tracks: alb2Tracks,
              type: 'Альбом',
            });
            singlesList.push(...remaining.slice(alb2Size));
          } else {
            singlesList.push(...remaining);
          }
        } else {
          singlesList.push(...tracks);
        }
      }

      const allReleases = [
        ...Array.from(albumsMap.values()),
        ...singlesList.map(t => ({
          title: t.title,
          cover: t.cover || '',
          tracks: [t],
          type: 'Сингл',
        })),
      ];

      if (allReleases.length > 0) {
        albumsEl.innerHTML = allReleases
          .map(
            (rel, i) => `
          <div class="album-card" data-rel-idx="${i}">
            <div class="album-cover-wrap">
              ${rel.cover ? `<img src="${escapeHtml(rel.cover)}" alt="${escapeHtml(rel.title)}">` : `<i class="material-icons" style="font-size:48px;color:var(--text-secondary)">album</i>`}
            </div>
            <div class="album-title">${escapeHtml(rel.title)}</div>
            <div class="album-meta-text">${rel.type} • ${rel.tracks.length} ${rel.tracks.length === 1 ? 'трек' : 'трека'}</div>
          </div>
        `
          )
          .join('');

        albumsEl.querySelectorAll('.album-card').forEach(card => {
          card.onclick = () => {
            const idx = Number(card.getAttribute('data-rel-idx'));
            const rel = allReleases[idx];
            if (rel) {
              openAlbumPage({
                title: rel.title,
                artist: name,
                cover: rel.cover,
                type: rel.type,
                tracks: rel.tracks,
              });
            }
          };
        });
      } else {
        albumsEl.innerHTML = '<p class="empty-msg">Релизов не найдено</p>';
      }
    }

    if (!tracks.length && statusEl) statusEl.textContent = 'Треки этого исполнителя не найдены';
  } catch (error) {
    if (requestId !== artistRequestId) return;
    if (countEl) countEl.textContent = formatTrackCount(0);
    if (tracksEl)
      tracksEl.innerHTML = '<p class="empty-msg">Не удалось загрузить треки исполнителя</p>';
    if (statusEl) statusEl.textContent = error.message || 'Ошибка загрузки';
  }
}

let albumPreviousScreenId = 'artist-screen';

function openAlbumPage(album) {
  if (!album) return;
  if (currentScreenId && currentScreenId !== 'album-screen') {
    albumPreviousScreenId = currentScreenId;
  }
  const activeNav = document.querySelector('.nav-btn.active');
  previousActiveBtnId = activeNav?.id || previousActiveBtnId || 'nav-home-btn';

  switchScreen('album-screen', previousActiveBtnId);

  const titleEl = document.getElementById('album-screen-title');
  const artistEl = document.getElementById('album-screen-artist');
  const metaEl = document.getElementById('album-screen-meta');
  const badgeEl = document.getElementById('album-screen-badge');
  const tracksEl = document.getElementById('album-screen-tracks');
  const playBtn = document.getElementById('album-screen-play-btn');

  setCoverState('album-screen-cover', 'album-screen-cover-fallback', album.cover || '', '.album-screen-cover-wrap');
  if (titleEl) titleEl.textContent = album.title || 'Альбом';
  if (badgeEl) badgeEl.textContent = album.type || 'Альбом';
  if (artistEl) {
    artistEl.textContent = album.artist || 'Неизвестный исполнитель';
    artistEl.onclick = () => {
      if (album.artist) openArtistPage(album.artist);
    };
  }
  if (metaEl) {
    const count = album.tracks ? album.tracks.length : 0;
    metaEl.textContent = `${count} ${count === 1 ? 'трек' : 'трека'}`;
  }

  if (playBtn) {
    playBtn.onclick = () => {
      if (album.tracks && album.tracks.length) {
        currentPlaylist = album.tracks;
        currentTrackIndex = 0;
        playTrack(album.tracks[0]);
      }
    };
  }

  if (tracksEl && album.tracks) {
    renderTrackRows(tracksEl, album.tracks, {
      showAddButton: true,
      playButtonClass: 'play-album-track-btn',
    });
  }
}

function switchScreen(screenId, activeBtnId) {
  const screens = [
    'home-screen',
    'player-screen',
    'search-screen',
    'folders-screen',
    'workshop-screen',
    'artist-screen',
    'album-screen',
  ];
  screens.forEach(id => {
    const el = document.getElementById(id);
    if (el) {
      el.style.display = 'none';
      el.classList.add('hidden');
    }
  });
  const targetScreen = document.getElementById(screenId);
  if (targetScreen) {
    targetScreen.style.display = 'block';
    targetScreen.classList.remove('hidden');
  }

  const mainContent =
    document.getElementById('main-content') || document.querySelector('.main-content');
  if (mainContent) {
    mainContent.scrollTop = 0;
  }

  // Reset library tabs when entering folders-screen
  if (screenId === 'folders-screen') {
    const filterTabs = document.getElementById('library-filter-tabs');
    if (filterTabs) {
      filterTabs.querySelectorAll('.lib-tab-btn').forEach(b => b.classList.remove('active'));
      const allTab = filterTabs.querySelector('[data-tab="all"]');
      if (allTab) allTab.classList.add('active');
    }
    const detailPane = document.getElementById('lib-detail-pane');
    if (detailPane) detailPane.style.display = 'none';
    const playlistsSection = document.getElementById('lib-playlists-section');
    if (playlistsSection) playlistsSection.style.display = 'block';
  }

  const validBtnIds = [
    'nav-home-btn',
    'nav-player-btn',
    'nav-search-btn',
    'nav-folders-btn',
    'nav-workshop-btn',
    'nav-settings-btn',
  ];
  if (activeBtnId && validBtnIds.includes(activeBtnId)) {
    if (screenId !== 'artist-screen' && screenId !== 'album-screen') {
      previousScreenId = screenId;
      previousActiveBtnId = activeBtnId;
    }
    currentScreenId = screenId;
  }

  document.querySelectorAll('.nav-btn').forEach(b => b.classList.remove('active'));
  const targetBtn = document.getElementById(activeBtnId);
  if (targetBtn) targetBtn.classList.add('active');

  // Sync center-nav buttons
  const centerNavMap = {
    'home-screen': null, // home has no center nav active
    'search-screen': 'center-nav-search',
    'folders-screen': 'center-nav-folders',
  };
  document.querySelectorAll('.center-nav-btn').forEach(b => b.classList.remove('active'));
  const centerBtnId = centerNavMap[screenId];
  if (centerBtnId) {
    const centerBtn = document.getElementById(centerBtnId);
    if (centerBtn) centerBtn.classList.add('active');
  }

  const pageTitle = document.getElementById('page-title');
  if (pageTitle) pageTitle.innerText = screenPageTitles[screenId] || '';

  if (screenId === 'folders-screen') renderPlaylists();
  if (screenId === 'workshop-screen') {
    window.dispatchEvent(new CustomEvent('votify:workshop-open'));
  }
  // Recommendations are supplementary. Never block the first usable screen
  // behind a network request during launch.
  if (screenId === 'home-screen') loadRecommendations(false, { showLoading: !isBooting });
  if (screenId === 'home-screen') loadHomeContent();
  if (screenId === 'search-screen') {
    if (searchInput) searchInput.focus();
    renderSearchHistory();
  }
  if (screenId === 'player-screen') {
    if (typeof applyPlayerCoverShape === 'function') applyPlayerCoverShape();
    if (state.currentTrack) emit('state:currentTrack', state.currentTrack);
  }
}

// Nav button handlers
safeClick('nav-home-btn', () => switchScreen('home-screen', 'nav-home-btn'));
safeClick('nav-player-btn', () => switchScreen('player-screen', 'nav-player-btn'));
safeClick('nav-search-btn', () => switchScreen('search-screen', 'nav-search-btn'));
safeClick('nav-folders-btn', () => switchScreen('folders-screen', 'nav-folders-btn'));
safeClick('nav-workshop-btn', () => switchScreen('workshop-screen', 'nav-workshop-btn'));
safeClick('back-from-artist-btn', () => {
  artistRequestId++;
  switchScreen(previousScreenId || 'home-screen', previousActiveBtnId || 'nav-home-btn');
});
safeClick('back-from-album-btn', () => {
  switchScreen(albumPreviousScreenId || 'artist-screen', previousActiveBtnId || 'nav-home-btn');
});

// Settings overlay toggle
safeClick('nav-settings-btn', () => {
  const overlay = document.getElementById('settings-overlay');
  if (overlay) {
    const isOpen = overlay.style.display !== 'none';
    overlay.style.display = isOpen ? 'none' : 'flex';
    if (!isOpen) {
      document.querySelectorAll('.nav-btn').forEach(b => b.classList.remove('active'));
      document.getElementById('nav-settings-btn').classList.add('active');
      if (typeof initRangeSliderTracks === 'function') initRangeSliderTracks();
      if (typeof renderSavedColorSchemes === 'function') renderSavedColorSchemes();
      if (typeof renderSettingsLocalTracks === 'function') renderSettingsLocalTracks();
    } else {
      document.querySelectorAll('.nav-btn').forEach(b => b.classList.remove('active'));
      const btn = document.getElementById(previousActiveBtnId);
      if (btn) btn.classList.add('active');
    }
  }
});

safeClick('settings-close-btn', () => {
  const overlay = document.getElementById('settings-overlay');
  if (overlay) overlay.style.display = 'none';
  document.querySelectorAll('.nav-btn').forEach(b => b.classList.remove('active'));
  const btn = document.getElementById(previousActiveBtnId);
  if (btn) btn.classList.add('active');
});

// Settings section switching
function switchSettingsSection(sectionName) {
  document.querySelectorAll('.settings-menu-item').forEach(b => b.classList.remove('active'));
  document.querySelectorAll('.settings-panel').forEach(p => p.classList.remove('active'));
  const menuBtn = document.querySelector(
    `.settings-menu-item[data-settings-section="${sectionName}"]`
  );
  const panel = document.getElementById(`settings-panel-${sectionName}`);
  if (menuBtn) menuBtn.classList.add('active');
  if (panel) panel.classList.add('active');
}

document.querySelectorAll('.settings-menu-item').forEach(btn => {
  btn.addEventListener('click', () => {
    switchSettingsSection(btn.getAttribute('data-settings-section'));
  });
});

// ==========================================
// Interface Settings (ИНТЕРФЕЙС)
// ==========================================
// Theme mode toggle
const themeModeBtn = document.getElementById('theme-mode-btn');
const themeModeValue = document.getElementById('theme-mode-value');
if (themeModeBtn) {
  themeModeBtn.addEventListener('click', () => {
    const isDark = document.documentElement.getAttribute('data-theme') !== 'light';
    const newTheme = isDark ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', newTheme);
    appSettings.theme = newTheme;
    saveSettings();
    themeModeValue.textContent = isDark ? 'Светлая' : 'Тёмная';
    themeModeBtn.querySelector('i').textContent = isDark ? 'light_mode' : 'dark_mode';
  });
}

// Theme neutral toggle
const themeNeutralToggle = document.getElementById('toggle-theme-neutral');
if (themeNeutralToggle) {
  themeNeutralToggle.checked = appSettings.themeNeutral !== false;
  themeNeutralToggle.addEventListener('change', () => {
    appSettings.themeNeutral = themeNeutralToggle.checked;
    saveSettings();
  });
}

// Tab style
const tabStyleBtn = document.getElementById('tab-style-btn');
const tabStyleValue = document.getElementById('tab-style-value');
if (tabStyleBtn) {
  tabStyleBtn.addEventListener('click', () => {
    const styles = ['Стандартный', 'Компактный', 'Полная ширина'];
    const current = tabStyleValue.textContent;
    const idx = styles.indexOf(current);
    const next = styles[(idx + 1) % styles.length];
    tabStyleValue.textContent = next;
    appSettings.tabStyle = next;
    saveSettings();
  });
}

// Accent color toggle
const accentColorToggle = document.getElementById('toggle-accent-color');
if (accentColorToggle) {
  accentColorToggle.checked = appSettings.accentColor !== false;
  accentColorToggle.addEventListener('change', () => {
    appSettings.accentColor = accentColorToggle.checked;
    saveSettings();
  });
}

// Transparency toggle
const transparencyToggle = document.getElementById('toggle-transparency');
if (transparencyToggle) {
  transparencyToggle.checked = appSettings.transparency === true;
  transparencyToggle.addEventListener('change', () => {
    appSettings.transparency = transparencyToggle.checked;
    document.body.classList.toggle('transparency-enabled', transparencyToggle.checked);
    saveSettings();
  });
}
document.body.classList.toggle('transparency-enabled', appSettings.transparency === true);

// Font family
const fontFamilyBtn = document.getElementById('font-family-btn');
const fontFamilyValue = document.getElementById('font-family-value');
if (fontFamilyBtn) {
  fontFamilyBtn.addEventListener('click', () => {
    const fonts = ['По умолчанию', 'JetBrains Mono', 'Georgia', 'Nunito'];
    const current = fontFamilyValue.textContent;
    const idx = fonts.indexOf(current);
    const next = fonts[(idx + 1) % fonts.length];
    fontFamilyValue.textContent = next;
    appSettings.fontFamily = next;
    saveSettings();
  });
}

// Font size
const fontSizeBtn = document.getElementById('font-size-btn');
const fontSizeValueEl = document.getElementById('font-size-value');
if (fontSizeBtn && fontSizeValueEl) {
  fontSizeBtn.addEventListener('click', () => {
    const sizes = ['14px', '15px', '16px', '17px', '18px', '20px'];
    const current = fontSizeValueEl.textContent;
    const idx = sizes.indexOf(current);
    const next = sizes[(idx + 1) % sizes.length];
    fontSizeValueEl.textContent = next;
    appSettings.fontSize = next;
    document.documentElement.style.setProperty('--app-font-size-offset', next);
    saveSettings();
  });
}

// ==========================================
// Player Settings (ПЛЕЕР)
// ==========================================
// Player style modal
const playerStyleBtn = document.getElementById('player-style-btn');
const playerStyleValue = document.getElementById('player-style-value');
const playerStyleModalOverlay = document.getElementById('player-style-modal-overlay');
const playerStyleModalClose = document.getElementById('player-style-modal-close');

function openPlayerStyleModal() {
  if (playerStyleModalOverlay) {
    playerStyleModalOverlay.classList.remove('hidden');
  }
}
function closePlayerStyleModal() {
  if (playerStyleModalOverlay) {
    playerStyleModalOverlay.classList.add('hidden');
  }
}

if (playerStyleBtn) {
  playerStyleBtn.addEventListener('click', openPlayerStyleModal);
}
if (playerStyleModalClose) {
  playerStyleModalClose.addEventListener('click', closePlayerStyleModal);
}
if (playerStyleModalOverlay) {
  playerStyleModalOverlay.addEventListener('click', e => {
    if (e.target === playerStyleModalOverlay) closePlayerStyleModal();
  });
}

// Player style card selection
document.querySelectorAll('.player-style-card').forEach(card => {
  card.addEventListener('click', () => {
    const style = card.dataset.style;
    document.querySelectorAll('.player-style-card').forEach(c => c.classList.remove('active'));
    card.classList.add('active');
    playerStyleValue.textContent =
      style === 'standard' ? 'Стандартный' : style === 'large' ? 'Большой' : 'Пластинка';
    appSettings.playerStyle = style;
    // Sync: vinyl style should make cover vinyl-shaped, otherwise user sees square staying
    if (style === 'vinyl') {
      appSettings.playerCoverShape = 'Виниловая пластинка';
      const coverShapeValue = document.getElementById('cover-shape-value');
      const settingCoverShape = document.getElementById('setting-cover-shape');
      if (coverShapeValue) coverShapeValue.textContent = 'Виниловая пластинка';
      if (settingCoverShape) settingCoverShape.value = 'Виниловая пластинка';
      applyPlayerCoverShape();
    } else {
      // if leaving vinyl and cover was vinyl, revert to rounded for nicer look
      if (appSettings.playerCoverShape === 'Виниловая пластинка') {
        appSettings.playerCoverShape = 'Закруглённый квадрат';
        const coverShapeValue = document.getElementById('cover-shape-value');
        const settingCoverShape = document.getElementById('setting-cover-shape');
        if (coverShapeValue) coverShapeValue.textContent = 'Закруглённый квадрат';
        if (settingCoverShape) settingCoverShape.value = 'Закруглённый квадрат';
        applyPlayerCoverShape();
      }
    }
    applyPlayerSettings();
    saveSettings();
    closePlayerStyleModal();
  });
});

// Title align
const titleAlignBtn = document.getElementById('title-align-btn');
const titleAlignValue = document.getElementById('title-align-value');
if (titleAlignBtn) {
  titleAlignBtn.addEventListener('click', () => {
    const aligns = ['По центру', 'По левому краю', 'По правому краю'];
    const current = titleAlignValue.textContent;
    const idx = aligns.indexOf(current);
    const next = aligns[(idx + 1) % aligns.length];
    titleAlignValue.textContent = next;
    appSettings.titleAlign = next;
    saveSettings();
  });
}

// Slider type
const sliderTypeBtn = document.getElementById('slider-type-btn');
const sliderTypeValue = document.getElementById('slider-type-value');
if (sliderTypeBtn) {
  sliderTypeBtn.addEventListener('click', () => {
    const types = ['Стандартный', 'Тонкий', 'Толстый', 'Круглый'];
    const current = sliderTypeValue.textContent;
    const idx = types.indexOf(current);
    const next = types[(idx + 1) % types.length];
    sliderTypeValue.textContent = next;
    appSettings.sliderType = next;
    saveSettings();
  });
}

// Play button style
const playBtnStyleBtn = document.getElementById('play-btn-style-btn');
const playBtnStyleValue = document.getElementById('play-btn-style-value');
if (playBtnStyleBtn) {
  playBtnStyleBtn.addEventListener('click', () => {
    const styles = ['Круг', 'Квадрат', 'Прямоугольник'];
    const current = playBtnStyleValue.textContent;
    const idx = styles.indexOf(current);
    const next = styles[(idx + 1) % styles.length];
    playBtnStyleValue.textContent = next;
    appSettings.playBtnStyle = next;
    saveSettings();
  });
}

// Info badge
const infoBadgeBtn = document.getElementById('info-badge-btn');
const infoBadgeValue = document.getElementById('info-badge-value');
if (infoBadgeBtn) {
  infoBadgeBtn.addEventListener('click', () => {
    const options = ['Источник', 'Качество', 'Альбом', 'Ничего'];
    const current = infoBadgeValue.textContent;
    const idx = options.indexOf(current);
    const next = options[(idx + 1) % options.length];
    infoBadgeValue.textContent = next;
    appSettings.infoBadge = next;
    saveSettings();
  });
}

// Player background
const playerBgBtn = document.getElementById('player-bg-btn');
const playerBgValue = document.getElementById('player-bg-value');
if (playerBgBtn) {
  playerBgBtn.addEventListener('click', () => {
    const options = ['Нет', 'Размытие', 'Цвет обложки', 'Градиент'];
    const current = playerBgValue.textContent;
    const idx = options.indexOf(current);
    const next = options[(idx + 1) % options.length];
    playerBgValue.textContent = next;
    appSettings.playerBg = next;
    saveSettings();
  });
}

// Mini player preset
const miniPresetBtn = document.getElementById('mini-preset-btn');
const miniPresetValue = document.getElementById('mini-preset-value');
if (miniPresetBtn) {
  miniPresetBtn.addEventListener('click', () => {
    const presets = ['Выбрать пресет', 'Минимализм', 'Полный', 'Компактный', 'Видео'];
    const current = miniPresetValue.textContent;
    const idx = presets.indexOf(current);
    const next = presets[(idx + 1) % presets.length];
    miniPresetValue.textContent = next;
    appSettings.miniPreset = next;
    saveSettings();
  });
}

// Mini player background
const miniBgBtn = document.getElementById('mini-bg-btn');
const miniBgValue = document.getElementById('mini-bg-value');
if (miniBgBtn) {
  miniBgBtn.addEventListener('click', () => {
    const options = ['Цвет обложки', 'Размытие', 'Черный', 'Прозрачный'];
    const current = miniBgValue.textContent;
    const idx = options.indexOf(current);
    const next = options[(idx + 1) % options.length];
    miniBgValue.textContent = next;
    appSettings.miniBg = next;
    saveSettings();
  });
}

// Mini player progress
const miniProgressBtn = document.getElementById('mini-progress-btn');
const miniProgressValue = document.getElementById('mini-progress-value');
if (miniProgressBtn) {
  miniProgressBtn.addEventListener('click', () => {
    const options = ['Кольцо на обложке', 'Линия снизу', 'Точки', 'Нет'];
    const current = miniProgressValue.textContent;
    const idx = options.indexOf(current);
    const next = options[(idx + 1) % options.length];
    miniProgressValue.textContent = next;
    appSettings.miniProgress = next;
    saveSettings();
  });
}

// Mini cover shape
const miniCoverShapeBtn = document.getElementById('mini-cover-shape-btn');
const miniCoverShapeValue = document.getElementById('mini-cover-shape-value');
if (miniCoverShapeBtn) {
  miniCoverShapeBtn.addEventListener('click', () => {
    const shapes = ['Круг', 'Квадрат', 'Прямоугольник', 'Скругленный квадрат', 'Мягкий квадрат', 'Шестиугольник', 'Ромб'];
    const current = miniCoverShapeValue.textContent;
    const idx = shapes.indexOf(current);
    const next = shapes[(idx + 1) % shapes.length];
    miniCoverShapeValue.textContent = next;
    appSettings.miniCoverShape = next;
    const mapMini = {
      'Круг': 'Круг',
      'Квадрат': 'Квадрат',
      'Мягкий квадрат': 'Мягкий квадрат',
      'Шестиугольник': 'Шестиугольник',
      'Ромб': 'Ромб'
    };
    appSettings.playerCoverShape = mapMini[next] || 'Закруглённый квадрат';
    applyPlayerCoverShape();
    saveSettings();
  });
}

// Mini border radius
const miniBorderRadiusBtn = document.getElementById('mini-border-radius-btn');
const miniBorderRadiusValue = document.getElementById('mini-border-radius-value');
if (miniBorderRadiusBtn) {
  miniBorderRadiusBtn.addEventListener('click', () => {
    const radii = ['Круглое (Pill)', 'Среднее', 'Маленькое', 'Прямое'];
    const current = miniBorderRadiusValue.textContent;
    const idx = radii.indexOf(current);
    const next = radii[(idx + 1) % radii.length];
    miniBorderRadiusValue.textContent = next;
    appSettings.miniBorderRadius = next;
    saveSettings();
  });
}

// Mini controls
const miniControlsBtn = document.getElementById('mini-controls-btn');
const miniControlsValue = document.getElementById('mini-controls-value');
if (miniControlsBtn) {
  miniControlsBtn.addEventListener('click', () => {
    const options = [
      'Плей/Пауза, Лайк',
      'Плей/Пауза, След/Пред',
      'Только Плей/Пауза',
      'Все кнопки',
    ];
    const current = miniControlsValue.textContent;
    const idx = options.indexOf(current);
    const next = options[(idx + 1) % options.length];
    miniControlsValue.textContent = next;
    appSettings.miniControls = next;
    saveSettings();
  });
}

// Mini button style
const miniBtnStyleBtn = document.getElementById('mini-btn-style-btn');
const miniBtnStyleValue = document.getElementById('mini-btn-style-value');
if (miniBtnStyleBtn) {
  miniBtnStyleBtn.addEventListener('click', () => {
    const styles = ['Залитые', 'Контурные', 'Текстовые', 'Иконки'];
    const current = miniBtnStyleValue.textContent;
    const idx = styles.indexOf(current);
    const next = styles[(idx + 1) % styles.length];
    appSettings.miniBtnStyle = next;
    saveSettings();
  });
}

// Apply one cover shape consistently in the page, bottom, side and fullscreen players.
function applyPlayerCoverShape() {
  const shape = appSettings.playerCoverShape || 'Закруглённый квадрат';
  const map = {
    'Виниловая пластинка': 'vinyl',
    'Круг': 'circle',
    'Квадрат': 'square',
    'Закруглённый квадрат': 'rounded',
    'Мягкий квадрат': 'soft',
    'Шестиугольник': 'hexagon',
    'Ромб': 'diamond'
  };
  const shapeKey = map[shape] || 'rounded';
  document.body.dataset.playerCoverShape = shapeKey;

  const wrappers = document.querySelectorAll(
    '.pp-cover-wrap, .player-bar-cover-wrap, .right-player-cover-shell, .fs-cover-container, .fi-cover-wrap, .lib-detail-cover, .album-screen-cover-wrap'
  );
  wrappers.forEach(wrap => {
    wrap.classList.remove(
      'shape-rounded-square',
      'shape-vinyl',
      'shape-circle',
      'shape-square',
      'shape-soft',
      'shape-hexagon',
      'shape-diamond'
    );
    const cls =
      shapeKey === 'vinyl' ? 'shape-vinyl'
      : shapeKey === 'circle' ? 'shape-circle'
      : shapeKey === 'square' ? 'shape-square'
      : shapeKey === 'soft' ? 'shape-soft'
      : shapeKey === 'hexagon' ? 'shape-hexagon'
      : shapeKey === 'diamond' ? 'shape-diamond'
      : 'shape-rounded-square';
    wrap.classList.add(cls);
    wrap.classList.toggle('is-playing', Boolean(state.isPlaying));
  });

  const valueEl = document.getElementById('player-cover-shape-value');
  if (valueEl) valueEl.textContent = shape;
  const coverShapeSel = document.getElementById('setting-cover-shape');
  if (coverShapeSel && coverShapeSel.value !== shape) coverShapeSel.value = shape;
}

const playerCoverShapeBtn = document.getElementById('player-cover-shape-btn');
const playerCoverShapeValue = document.getElementById('player-cover-shape-value');
if (playerCoverShapeBtn) {
  playerCoverShapeBtn.addEventListener('click', () => {
    const shapes = ['Закруглённый квадрат', 'Квадрат', 'Мягкий квадрат', 'Круг', 'Виниловая пластинка', 'Шестиугольник', 'Ромб'];
    const current =
      playerCoverShapeValue?.textContent || appSettings.playerCoverShape || 'Закруглённый квадрат';
    let idx = shapes.indexOf(current);
    if (idx === -1) idx = 0;
    const next = shapes[(idx + 1) % shapes.length];
    if (playerCoverShapeValue) playerCoverShapeValue.textContent = next;
    appSettings.playerCoverShape = next;
    saveSettings();
    applyPlayerCoverShape();
  });
}
applyPlayerCoverShape();

// ==========================================
// Center Navigation
// ==========================================
const centerNavButtons = {
  'center-nav-search': 'search-screen',
  'center-nav-folders': 'folders-screen',
};

Object.entries(centerNavButtons).forEach(([btnId, screenId]) => {
  const btn = document.getElementById(btnId);
  if (btn) {
    btn.addEventListener('click', () => {
      switchScreen(screenId);
      document.querySelectorAll('.center-nav-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
    });
  }
});

// ==========================================
// Right Player Panel
// ==========================================
let rightPlayerPanelOpen = false;

function openRightPlayerPanel() {
  const panel = document.getElementById('right-player-panel');
  const mainContent = document.querySelector('.main-content');
  const island = document.getElementById('floating-island');
  const fiSideBtn = document.getElementById('fi-side-player');
  if (panel) {
    panel.classList.add('open');
    if (mainContent) mainContent.classList.add('with-right-panel');
    if (island) island.style.display = 'none';
    if (fiSideBtn) fiSideBtn.classList.add('active');
    rightPlayerPanelOpen = true;
  }
}

function closeRightPlayerPanel() {
  const panel = document.getElementById('right-player-panel');
  const mainContent = document.querySelector('.main-content');
  const island = document.getElementById('floating-island');
  const fiSideBtn = document.getElementById('fi-side-player');
  if (panel) {
    panel.classList.remove('open');
    if (mainContent) mainContent.classList.remove('with-right-panel');
    if (island) island.style.display = '';
    if (fiSideBtn) fiSideBtn.classList.remove('active');
    rightPlayerPanelOpen = false;
  }
}

const rightPlayerCloseBtn = document.getElementById('right-player-close');
if (rightPlayerCloseBtn) {
  rightPlayerCloseBtn.addEventListener('click', closeRightPlayerPanel);
}

// Update right player panel when track changes
let rightPanelBgTrackId = null;
function updateRightPlayerPanel(track) {
  const title = document.getElementById('right-player-title');
  const artist = document.getElementById('right-player-artist');
  const playBtn = document.getElementById('right-player-play');
  const bgEl = document.getElementById('right-player-bg');

  // Use unified placeholder helper
  if (track) setCoverState('right-player-cover', 'right-player-cover-fallback', track.cover || '', '.right-player-cover-shell');
  if (title) title.textContent = track.title || '—';
  if (artist) artist.textContent = track.artist || '—';
  if (playBtn) {
    playBtn.innerHTML = state.isPlaying
      ? '<svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="4" width="4" height="16"/><rect x="14" y="4" width="4" height="16"/></svg>'
      : '<svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor"><polygon points="6,3 20,12 6,21"/></svg>';
  }
  // Tint the side panel background with the cover's dominant color
  if (bgEl) {
    if (track.cover && rightPanelBgTrackId !== track.id) {
      rightPanelBgTrackId = track.id;
      extractDominantColor(track.cover).then(color => {
        if (color && rightPanelBgTrackId === track.id) {
          const { r, g, b } = color;
          bgEl.style.background = `radial-gradient(ellipse at 50% 0%, rgba(${r},${g},${b},1) 0%, rgba(${r},${g},${b},0.35) 45%, transparent 78%)`;
        }
      });
    } else if (!track.cover) {
      rightPanelBgTrackId = null;
      bgEl.style.background = '';
    }
  }
}

const rightPlayerArtistLink = document.getElementById('right-player-artist');
if (rightPlayerArtistLink) {
  rightPlayerArtistLink.classList.add('clickable-artist');
  rightPlayerArtistLink.addEventListener('click', e => {
    e.stopPropagation();
    if (state.currentTrack?.artist) openArtistPage(state.currentTrack.artist);
  });
}

// Right player panel controls
const rightPlayerPlayBtn = document.getElementById('right-player-play');
if (rightPlayerPlayBtn) {
  rightPlayerPlayBtn.addEventListener('click', () => {
    if (audio.paused) {
      audio.play().catch(() => {});
    } else {
      audio.pause();
    }
  });
}
const rightPlayerPrevBtn = document.getElementById('right-player-prev');
if (rightPlayerPrevBtn) rightPlayerPrevBtn.addEventListener('click', playPrevTrack);
const rightPlayerNextBtn = document.getElementById('right-player-next');
if (rightPlayerNextBtn) rightPlayerNextBtn.addEventListener('click', playNextTrack);
const rightPlayerShuffleBtn = document.getElementById('right-player-shuffle');
if (rightPlayerShuffleBtn)
  rightPlayerShuffleBtn.addEventListener('click', () =>
    rightPlayerShuffleBtn.classList.toggle('active')
  );
const rightPlayerRepeatBtn = document.getElementById('right-player-repeat');
let abLoopStart = null;
let abLoopEnd = null;
let abLoopSelecting = false;
let abLoopActive = false;

function clearAbLoop() {
  abLoopStart = null;
  abLoopEnd = null;
  abLoopSelecting = false;
  abLoopActive = false;
  rightPlayerRepeatBtn?.classList.remove('active', 'selecting');
  rightPlayerRepeatBtn?.setAttribute('title', 'Зациклить фрагмент');
  fsRepeatBtn?.classList.remove('active', 'selecting');
  fsRepeatBtn?.setAttribute('title', 'Зациклить фрагмент');
  document.querySelectorAll('.ab-loop-marker, .ab-loop-range').forEach(el => el.remove());
}

function updateAbLoopMarkers() {
  if (!Number.isFinite(audio.duration) || audio.duration <= 0) return;
  document.querySelectorAll('.ab-loop-marker, .ab-loop-range').forEach(el => el.remove());
  if (abLoopStart == null) return;
  const startPct = (abLoopStart / audio.duration) * 100;
  [rightTimelineTrack, fsTimelineTrack].forEach(track => {
    if (!track) return;
    const marker = document.createElement('span');
    marker.className = 'ab-loop-marker';
    marker.style.left = `${startPct}%`;
    track.appendChild(marker);
    if (abLoopEnd != null) {
      const endPct = (abLoopEnd / audio.duration) * 100;
      const range = document.createElement('span');
      range.className = 'ab-loop-range';
      range.style.left = `${startPct}%`;
      range.style.width = `${Math.max(0, endPct - startPct)}%`;
      track.appendChild(range);
      const endMarker = document.createElement('span');
      endMarker.className = 'ab-loop-marker';
      endMarker.style.left = `${endPct}%`;
      track.appendChild(endMarker);
    }
  });
}

if (rightPlayerRepeatBtn)
  rightPlayerRepeatBtn.addEventListener('click', () => {
    if (abLoopActive) {
      clearAbLoop();
      showToast('Зацикливание фрагмента выключено');
      return;
    }
    abLoopStart = null;
    abLoopEnd = null;
    abLoopSelecting = true;
    rightPlayerRepeatBtn.classList.add('selecting');
    rightPlayerRepeatBtn.setAttribute('title', 'Выберите начало фрагмента на шкале');
    showToast('Нажмите на шкалу, чтобы выбрать начало фрагмента');
  });

// Right player progress — curved SVG timeline (mirrors the bottom player bar)
const rightTimelineTrack = document.getElementById('right-timeline-track');
const rightTimelineThumb = document.getElementById('right-timeline-thumb');
const rightTlBg = document.getElementById('right-tl-bg');
const rightTlActive = document.getElementById('right-tl-active');

function buildPlayerWavePath(width, height, endX = width) {
  const mid = height / 2;
  const amplitude = Math.max(3, height * 0.34);
  const wavelength = 72;
  const step = 4;
  let path = `M 0 ${mid.toFixed(1)}`;
  for (let x = step; x <= endX; x += step) {
    const y = mid + Math.sin((x / wavelength) * Math.PI * 2) * amplitude;
    path += ` L ${Math.min(x, endX).toFixed(1)} ${y.toFixed(1)}`;
  }
  if (endX % step) {
    const y = mid + Math.sin((endX / wavelength) * Math.PI * 2) * amplitude;
    path += ` L ${endX.toFixed(1)} ${y.toFixed(1)}`;
  }
  return path;
}

function drawWaveTimelinePaths(backgroundPath, activePath, width, height, pct) {
  const activeWidth = Math.max(0, Math.min(width, (pct / 100) * width));
  backgroundPath.setAttribute('d', buildPlayerWavePath(width, height));
  activePath.setAttribute('d', buildPlayerWavePath(width, height, activeWidth));
  backgroundPath.style.fill = 'none';
  backgroundPath.style.stroke = 'rgba(255,255,255,0.22)';
  backgroundPath.style.strokeWidth = '2.5';
  backgroundPath.style.strokeLinecap = 'round';
  activePath.style.fill = 'none';
  activePath.style.stroke = 'var(--accent)';
  activePath.style.strokeWidth = '3';
  activePath.style.strokeLinecap = 'round';
}

function restoreRegularTimelinePaths(backgroundPath, activePath) {
  [backgroundPath, activePath].forEach(path => {
    path.style.fill = '';
    path.style.stroke = '';
    path.style.strokeWidth = '';
    path.style.strokeLinecap = '';
  });
}

function drawRightTimeline(pct) {
  if (!rightTimelineTrack || !rightTlBg || !rightTlActive) return;
  const w = rightTimelineTrack.clientWidth || 300;
  if (appSettings.playerSliderType === 'wave') {
    drawWaveTimelinePaths(rightTlBg, rightTlActive, w, 20, pct);
  } else {
    restoreRegularTimelinePaths(rightTlBg, rightTlActive);
    const h = 4;
    rightTlBg.setAttribute('d', `M 0 0 L ${w} 0 L ${w} ${h} L 0 ${h} Z`);
    const aw = (pct / 100) * w;
    rightTlActive.setAttribute('d', `M 0 0 L ${aw} 0 L ${aw} ${h} L 0 ${h} Z`);
  }
  if (rightTimelineThumb) rightTimelineThumb.style.left = pct + '%';
}

function rightTlSeek(e) {
  if (!rightTimelineTrack || !audio.src || isNaN(audio.duration)) return;
  const rect = rightTimelineTrack.getBoundingClientRect();
  const clientX = e.type.includes('touch') ? e.touches[0].clientX : e.clientX;
  const pct = Math.max(0, Math.min(1, (clientX - rect.left) / rect.width));
  if (abLoopSelecting) {
    const point = pct * audio.duration;
    if (abLoopStart == null) {
      abLoopStart = point;
      audio.currentTime = point;
      rightPlayerRepeatBtn?.setAttribute('title', 'Выберите конец фрагмента на шкале');
      showToast('Теперь нажмите на шкалу, чтобы выбрать конец');
    } else {
      abLoopEnd = point;
      if (abLoopEnd < abLoopStart) [abLoopStart, abLoopEnd] = [abLoopEnd, abLoopStart];
      if (abLoopEnd - abLoopStart < 0.5) {
        abLoopEnd = Math.min(audio.duration, abLoopStart + 0.5);
      }
      abLoopSelecting = false;
      abLoopActive = true;
      rightPlayerRepeatBtn?.classList.remove('selecting');
      rightPlayerRepeatBtn?.classList.add('active');
      rightPlayerRepeatBtn?.setAttribute('title', 'Выключить зацикливание фрагмента');
      fsRepeatBtn?.classList.remove('selecting');
      fsRepeatBtn?.classList.add('active');
      fsRepeatBtn?.setAttribute('title', 'Выключить зацикливание фрагмента');
      audio.currentTime = abLoopStart;
      updateAbLoopMarkers();
      showToast('Фрагмент зациклен');
    }
    updateAbLoopMarkers();
    return;
  }
  audio.currentTime = pct * audio.duration;
  drawRightTimeline(pct * 100);
}

if (rightTimelineTrack) {
  let rightTlDragging = false;
  rightTimelineTrack.addEventListener('mousedown', e => {
    const wasSelecting = abLoopSelecting;
    rightTlDragging = !wasSelecting;
    rightTimelineTrack.classList.add('dragging');
    rightTlSeek(e);
  });
  rightTimelineTrack.addEventListener(
    'touchstart',
    e => {
      const wasSelecting = abLoopSelecting;
      rightTlDragging = !wasSelecting;
      rightTimelineTrack.classList.add('dragging');
      rightTlSeek(e);
    },
    { passive: false }
  );
  document.addEventListener('mousemove', e => {
    if (rightTlDragging && !abLoopSelecting) rightTlSeek(e);
  });
  document.addEventListener(
    'touchmove',
    e => {
      if (rightTlDragging && !abLoopSelecting) rightTlSeek(e);
    },
    { passive: false }
  );
  document.addEventListener('mouseup', () => {
    rightTlDragging = false;
    rightTimelineTrack?.classList.remove('dragging');
  });
  document.addEventListener('touchend', () => {
    rightTlDragging = false;
    rightTimelineTrack?.classList.remove('dragging');
  });
  window.addEventListener('resize', () => {
    if (state.duration > 0) drawRightTimeline((audio.currentTime / state.duration) * 100);
  });
  drawRightTimeline(0);
}

// Right player volume
const rightPlayerVolume = document.getElementById('right-player-volume');
if (rightPlayerVolume) {
  rightPlayerVolume.value = Math.round(audio.volume * 100);
  rightPlayerVolume.style.setProperty('--r', rightPlayerVolume.value + '%');
  rightPlayerVolume.addEventListener('input', e => {
    const vol = e.target.value / 100;
    state.volume = vol;
    audio.volume = vol;
    e.target.style.setProperty('--r', e.target.value + '%');
    // Update Skiper99 volume icon
    const volIcon = document.querySelector('#right-player-mute .skiper99-volume');
    if (volIcon) volIcon.setAttribute('data-muted', vol === 0 ? 'true' : 'false');
  });
}

// Skiper99 VolumeIcon mute toggle in right panel
const rightMuteBtn = document.getElementById('right-player-mute');
if (rightMuteBtn) {
  rightMuteBtn.addEventListener('click', () => {
    const volIcon = rightMuteBtn.querySelector('.skiper99-volume');
    if (audio.volume > 0) {
      savedVolume = audio.volume;
      audio.volume = 0;
      if (volIcon) volIcon.setAttribute('data-muted', 'true');
      if (rightPlayerVolume) rightPlayerVolume.value = 0;
    } else {
      audio.volume = savedVolume;
      if (volIcon) volIcon.setAttribute('data-muted', 'false');
      if (rightPlayerVolume) rightPlayerVolume.value = Math.round(savedVolume * 100);
    }
  });
}

// Skiper99 MenuIcon toggle for close button
const rightCloseBtn = document.getElementById('right-player-close');
if (rightCloseBtn) {
  rightCloseBtn.addEventListener('click', () => {
    const menu = rightCloseBtn.querySelector('.skiper99-menu');
    if (menu)
      menu.setAttribute(
        'data-toggled',
        menu.getAttribute('data-toggled') === 'true' ? 'false' : 'true'
      );
    // Close panel
    const panel = document.getElementById('right-player-panel');
    const mainContent = document.querySelector('.main-content');
    if (panel) panel.classList.remove('open');
    if (mainContent) mainContent.classList.remove('with-right-panel');
    rightPlayerPanelOpen = false;
  });
}

// Right panel EQ toggle
const rightEqBtn = document.getElementById('right-eq-btn');
const rightPlayerEq = document.getElementById('right-player-eq');
const rightLyricsBtn = document.getElementById('right-lyrics-btn');
const rightPlayerLyrics = document.getElementById('right-player-lyrics');

if (rightEqBtn && rightPlayerEq) {
  rightEqBtn.addEventListener('click', () => {
    const open = rightPlayerEq.style.display === 'none';
    rightPlayerEq.style.display = open ? 'block' : 'none';
    rightEqBtn.classList.toggle('active', open);
    if (open && rightPlayerLyrics) {
      rightPlayerLyrics.style.display = 'none';
      rightLyricsBtn?.classList.remove('active');
    }
  });
}
if (rightLyricsBtn && rightPlayerLyrics) {
  rightLyricsBtn.addEventListener('click', () => {
    const open = rightPlayerLyrics.style.display === 'none';
    rightPlayerLyrics.style.display = open ? 'block' : 'none';
    rightLyricsBtn.classList.toggle('active', open);
    if (open && rightPlayerEq) {
      rightPlayerEq.style.display = 'none';
      rightEqBtn?.classList.remove('active');
    }
    if (open) openFullLyrics();
  });
}

function openFullLyrics() {
  const overlay = document.getElementById('full-lyrics-overlay');
  if (!overlay) return;
  const track = state.currentTrack;
  if (track) {
    document.getElementById('full-lyrics-title').textContent = track.title || 'Текст песни';
    document.getElementById('full-lyrics-artist').textContent = track.artist || '—';
    if (track.cover) overlay.style.setProperty('--lyrics-cover', `url("${track.cover}")`);
  }
  overlay.classList.add('open');
  overlay.setAttribute('aria-hidden', 'false');
  renderFullLyrics();
}

function closeFullLyrics() {
  const overlay = document.getElementById('full-lyrics-overlay');
  if (!overlay) return;
  overlay.classList.remove('open');
  overlay.setAttribute('aria-hidden', 'true');
}

function renderFullLyrics() {
  const body = document.getElementById('full-lyrics-body');
  if (!body) return;
  if (!rightLyricsData.length) {
    body.innerHTML = '<div class="lyrics-placeholder">Текст песни не найден</div>';
    return;
  }
  body.innerHTML = rightLyricsData
    .map((line, i) => {
      const text = typeof line === 'string' ? line : line.text;
      return `<div class="full-lyrics-line" data-idx="${i}">${escapeHtml(text || '') || '&nbsp;'}</div>`;
    })
    .join('');
}

document.getElementById('full-lyrics-close')?.addEventListener('click', closeFullLyrics);
document.getElementById('full-lyrics-backdrop')?.addEventListener('click', closeFullLyrics);
document.addEventListener('keydown', e => {
  if (e.key === 'Escape') closeFullLyrics();
});

// ==========================================
// Floating Island — Bottom Player
// ==========================================
(function initFloatingIsland() {
  const fiPlay = document.getElementById('fi-play');
  const fiPrev = document.getElementById('fi-prev');
  const fiNext = document.getElementById('fi-next');
  const fiShuffle = document.getElementById('fi-shuffle');
  const fiRepeat = document.getElementById('fi-repeat');
  const fiLike = document.getElementById('fi-like');
  const fiFullscreen = document.getElementById('fi-fullscreen');
  const fiEq = document.getElementById('fi-eq');
  const fiAddPl = document.getElementById('fi-add-playlist');
  const fiSidePlayer = document.getElementById('fi-side-player');
  const fiMute = document.getElementById('fi-mute');
  const fiVolume = document.getElementById('fi-volume');
  const fiVolIcon = document.getElementById('fi-vol-icon');
  const fiCover = document.getElementById('fi-cover');
  const fiTitle = document.getElementById('fi-title');
  const fiArtist = document.getElementById('fi-artist');

  // --- Play / Pause ---
  if (fiPlay) {
    fiPlay.addEventListener('click', () => {
      const playBtn = document.getElementById('play-btn');
      if (playBtn) playBtn.click();
    });
  }

  // --- Prev / Next ---
  if (fiPrev) fiPrev.addEventListener('click', () => playPrevTrack());
  if (fiNext) fiNext.addEventListener('click', () => playNextTrack());

  // --- Shuffle ---
  if (fiShuffle) {
    fiShuffle.addEventListener('click', () => {
      toggleShuffle();
      fiShuffle.classList.toggle('active', isShuffle);
    });
  }

  // --- A-B Fragment Loop (зацикливание куска трека) ---
  if (fiRepeat) {
    fiRepeat.addEventListener('click', () => {
      if (abLoopActive || abLoopSelecting) {
        clearAbLoop();
        fiRepeat.classList.remove('active');
        if (typeof showToast === 'function') showToast('Зацикливание фрагмента отключено');
      } else {
        abLoopStart = null;
        abLoopEnd = null;
        abLoopSelecting = true;
        fiRepeat.classList.add('active');
        if (typeof showToast === 'function') {
          showToast('Выберите начало и конец фрагмента на шкале времени');
        }
      }
    });
  }

  // --- Like / Favorite ---
  if (fiLike) {
    fiLike.addEventListener('click', () => {
      const track = currentPlaylist[currentTrackIndex];
      if (!track) return;
      const added = toggleFavorite(track);
      fiLike.classList.toggle('is-liked', added);
      fiLike.querySelector('.material-icons').textContent = added ? 'favorite' : 'favorite_border';
      if (typeof showToast === 'function') {
        showToast(added ? 'Добавлено в избранное' : 'Удалено из избранного');
      }
    });
  }

  // --- Fullscreen ---
  if (fiFullscreen) {
    fiFullscreen.addEventListener('click', () => {
      if (typeof openFullscreenPlayer === 'function') openFullscreenPlayer();
    });
  }

  // --- Small Equalizer Popup Window ---
  const fiEqPopup = document.getElementById('fi-eq-popup');
  const fiEqClose = document.getElementById('fi-eq-close');
  if (fiEq) {
    fiEq.addEventListener('click', e => {
      e.stopPropagation();
      if (!fiEqPopup) return;
      const isVisible = fiEqPopup.style.display !== 'none';
      fiEqPopup.style.display = isVisible ? 'none' : 'block';
      fiEq.classList.toggle('active', !isVisible);
    });
  }
  if (fiEqClose && fiEqPopup) {
    fiEqClose.addEventListener('click', () => {
      fiEqPopup.style.display = 'none';
      fiEq?.classList.remove('active');
    });
  }
  document.addEventListener('click', e => {
    if (fiEqPopup && fiEqPopup.style.display !== 'none') {
      if (!fiEqPopup.contains(e.target) && e.target !== fiEq && !fiEq?.contains(e.target)) {
        fiEqPopup.style.display = 'none';
        fiEq?.classList.remove('active');
      }
    }
  });

  // --- Add to Playlist Modal ---
  if (fiAddPl) {
    fiAddPl.addEventListener('click', async () => {
      const track = currentPlaylist[currentTrackIndex];
      if (!track) {
        if (typeof showToast === 'function') showToast('Сначала выберите трек');
        return;
      }
      const targetPlaylist = await playlistPickerModal(track.title || 'Трек');
      if (targetPlaylist && playlists[targetPlaylist]) {
        playlists[targetPlaylist].push(track);
        savePlaylists();
        renderSidebarPlaylists();
        if (typeof showToast === 'function') {
          showToast(`Добавлено в "${targetPlaylist}"`);
        }
      }
    });
  }

  // --- Side Player Panel Toggle ---
  if (fiSidePlayer) {
    fiSidePlayer.addEventListener('click', () => {
      const panel = document.getElementById('right-player-panel');
      if (!panel) return;
      if (panel.classList.contains('open')) {
        closeRightPlayerPanel();
        fiSidePlayer.classList.remove('active');
      } else {
        openRightPlayerPanel();
        fiSidePlayer.classList.add('active');
        const track = currentPlaylist[currentTrackIndex];
        if (track && typeof updateRightPlayerPanel === 'function') {
          updateRightPlayerPanel(track);
        }
      }
    });
  }

  // --- Mute / Volume ---
  let fiSavedVolume = 0.8;
  if (fiMute) {
    fiMute.addEventListener('click', () => {
      if (audio.volume > 0) {
        fiSavedVolume = audio.volume;
        audio.volume = 0;
        if (fiVolIcon) fiVolIcon.setAttribute('data-muted', 'true');
        if (fiVolume) fiVolume.value = 0;
      } else {
        audio.volume = fiSavedVolume;
        if (fiVolIcon) fiVolIcon.setAttribute('data-muted', 'false');
        if (fiVolume) fiVolume.value = Math.round(fiSavedVolume * 100);
      }
      // Sync the main volume bar too
      const volumeBar = document.getElementById('volume-bar');
      if (volumeBar) volumeBar.value = Math.round(audio.volume * 100);
      const mainVolIcon = document.getElementById('skiper99-main-vol');
      if (mainVolIcon)
        mainVolIcon.setAttribute('data-muted', audio.volume === 0 ? 'true' : 'false');
    });
  }

  if (fiVolume) {
    fiVolume.addEventListener('input', e => {
      const v = Number(e.target.value) / 100;
      audio.volume = v;
      if (fiVolIcon) fiVolIcon.setAttribute('data-muted', v === 0 ? 'true' : 'false');
      // Sync main volume bar
      const volumeBar = document.getElementById('volume-bar');
      if (volumeBar) {
        volumeBar.value = e.target.value;
        volumeBar.style.setProperty('--r', e.target.value + '%');
      }
      const mainVolIcon = document.getElementById('skiper99-main-vol');
      if (mainVolIcon) mainVolIcon.setAttribute('data-muted', v === 0 ? 'true' : 'false');
    });
  }

  // --- Sync state: track info ---
  on('state:currentTrack', track => {
    if (!track) { applyAllCoverPlaceholders(''); return; }
    setCoverState('fi-cover', 'fi-cover-fallback', track.cover || '', '.fi-cover-wrap');
    if (fiTitle) fiTitle.textContent = track.title || '—';
    if (fiArtist) fiArtist.textContent = track.artist || '—';
    // Update like state
    if (fiLike) {
      const liked = typeof isTrackFavorite === 'function' && isTrackFavorite(track);
      fiLike.classList.toggle('is-liked', liked);
      fiLike.querySelector('.material-icons').textContent = liked ? 'favorite' : 'favorite_border';
    }
  });

  // --- Sync state: play/pause ---
  on('state:isPlaying', playing => {
    if (fiPlay) {
      fiPlay.querySelector('.material-icons').textContent = playing ? 'pause' : 'play_arrow';
    }
  });

  // --- Sync volume on external change ---
  const origSyncVolumeBars = typeof syncVolumeBars === 'function' ? syncVolumeBars : null;
  window._fiSyncVolume = function () {
    const v = audio.volume;
    if (fiVolume) fiVolume.value = Math.round(v * 100);
    if (fiVolIcon) fiVolIcon.setAttribute('data-muted', v === 0 ? 'true' : 'false');
  };

  // --- Click on cover opens fullscreen ---
  if (fiCover) {
    fiCover.style.cursor = 'pointer';
    fiCover.addEventListener('click', () => {
      if (typeof openFullscreenPlayer === 'function') openFullscreenPlayer();
    });
  }

  // --- Click on artist name opens artist page ---
  if (fiArtist) {
    fiArtist.style.cursor = 'pointer';
    fiArtist.addEventListener('click', () => {
      const track = currentPlaylist[currentTrackIndex];
      if (track && typeof openArtistPage === 'function') openArtistPage(track.artist);
    });
  }
})();

// ==========================================
// Player Page (#player-screen) Logic
// ==========================================
(function initPagePlayer() {
  const ppCover = document.getElementById('page-player-cover');
  const ppTitle = document.getElementById('page-player-title');
  const ppArtist = document.getElementById('page-player-artist');
  const ppFs = document.getElementById('page-player-fs');
  const ppLike = document.getElementById('page-player-like');
  const ppPlaylist = document.getElementById('page-player-playlist');
  const ppPlay = document.getElementById('page-player-play');
  const ppPrev = document.getElementById('page-player-prev');
  const ppNext = document.getElementById('page-player-next');
  const ppShuffle = document.getElementById('page-player-shuffle');
  const ppRepeat = document.getElementById('page-player-repeat');
  const ppVolume = document.getElementById('page-player-volume');
  const ppVolIcon = document.getElementById('page-player-vol-icon');
  const ppEqBtn = document.getElementById('page-player-eq-btn');
  const ppLyricsBtn = document.getElementById('page-player-lyrics-btn');
  const ppLyricsPanel = document.getElementById('page-player-lyrics-panel');
  const ppSimilar = document.getElementById('page-player-similar');

  // Controls
  if (ppPlay) {
    ppPlay.addEventListener('click', () => {
      const playBtn = document.getElementById('play-btn');
      if (playBtn) playBtn.click();
    });
  }
  if (ppPrev) ppPrev.addEventListener('click', () => playPrevTrack());
  if (ppNext) ppNext.addEventListener('click', () => playNextTrack());
  if (ppShuffle) {
    ppShuffle.addEventListener('click', () => {
      toggleShuffle();
      ppShuffle.classList.toggle('active', isShuffle);
    });
  }
  if (ppRepeat) {
    ppRepeat.addEventListener('click', () => {
      if (abLoopActive || abLoopSelecting) {
        clearAbLoop();
        ppRepeat.classList.remove('active');
        if (typeof showToast === 'function') showToast('Зацикливание фрагмента отключено');
      } else {
        abLoopStart = null;
        abLoopEnd = null;
        abLoopSelecting = true;
        ppRepeat.classList.add('active');
        if (typeof showToast === 'function')
          showToast('Выберите начало и конец фрагмента на шкале времени');
      }
    });
  }

  // Cover Overlay Buttons
  if (ppFs) {
    ppFs.addEventListener('click', () => {
      if (typeof openFullscreenPlayer === 'function') openFullscreenPlayer();
    });
  }
  if (ppLike) {
    ppLike.addEventListener('click', () => {
      const track = currentPlaylist[currentTrackIndex];
      if (!track) return;
      const added = toggleFavorite(track);
      ppLike.classList.toggle('is-liked', added);
      ppLike.querySelector('.material-icons').textContent = added ? 'favorite' : 'favorite_border';
      if (typeof showToast === 'function') {
        showToast(added ? 'Добавлено в избранное' : 'Удалено из избранного');
      }
    });
  }
  if (ppPlaylist) {
    ppPlaylist.addEventListener('click', async () => {
      const track = currentPlaylist[currentTrackIndex];
      if (!track) return;
      const targetPlaylist = await playlistPickerModal(track.title || 'Трек');
      if (targetPlaylist && playlists[targetPlaylist]) {
        playlists[targetPlaylist].push(track);
        savePlaylists();
        renderSidebarPlaylists();
        if (typeof showToast === 'function') showToast(`Добавлено в "${targetPlaylist}"`);
      }
    });
  }

  const ppProgress = document.getElementById('page-player-progress');
  const ppCurrent = document.getElementById('page-player-current');
  const ppTotal = document.getElementById('page-player-total');

  // Helper to sync slider progress CSS variable --r
  function updateSliderFill(slider) {
    if (!slider) return;
    const pct = ((slider.value - slider.min) / (slider.max - slider.min)) * 100;
    slider.style.setProperty('--r', pct + '%');
  }

  // Timeline seeking
  if (ppProgress) {
    ppProgress.addEventListener('input', e => {
      if (!isNaN(audio.duration) && audio.duration > 0) {
        const pct = Number(e.target.value);
        audio.currentTime = (pct / 100) * audio.duration;
        updateSliderFill(ppProgress);
      }
    });
  }

  // Volume
  if (ppVolume) {
    updateSliderFill(ppVolume);
    ppVolume.addEventListener('input', e => {
      const v = Number(e.target.value) / 100;
      audio.volume = v;
      if (ppVolIcon)
        ppVolIcon.textContent = v === 0 ? 'volume_off' : v < 0.5 ? 'volume_down' : 'volume_up';
      updateSliderFill(ppVolume);
      syncVolumeBars();
    });
  }

  // Extras (EQ & Lyrics)
  if (ppEqBtn) {
    ppEqBtn.addEventListener('click', e => {
      e.stopPropagation();
      const fiEqPopup = document.getElementById('fi-eq-popup');
      if (fiEqPopup) {
        const isVisible = fiEqPopup.style.display !== 'none';
        fiEqPopup.style.display = isVisible ? 'none' : 'block';
        ppEqBtn.classList.toggle('active', !isVisible);
      }
    });
  }
  if (ppLyricsBtn) {
    ppLyricsBtn.addEventListener('click', () => {
      if (!ppLyricsPanel) return;
      const isHidden = ppLyricsPanel.style.display === 'none';
      ppLyricsPanel.style.display = isHidden ? 'block' : 'none';
      ppLyricsBtn.classList.toggle('active', isHidden);
    });
  }

  // Load Similar Tracks based on current playing track
  let currentSimilarTrackKey = null;

  async function loadSimilarTracks(track) {
    if (!ppSimilar || !track) return;
    const trackKey = (track.id || track.title || '') + '-' + (track.artist || '');
    if (currentSimilarTrackKey === trackKey) return;
    currentSimilarTrackKey = trackKey;

    ppSimilar.innerHTML = '<div class="lyrics-placeholder">Подбираем похожие треки...</div>';

    try {
      let similarTracks = [];

      // 1. Search tracks by current artist
      if (track.artist && track.artist !== '—' && track.artist !== 'Неизвестный исполнитель') {
        try {
          const res = await fetch(`/api/search?q=${encodeURIComponent(track.artist)}`);
          if (res.ok) {
            const data = await res.json();
            if (data.tracks && data.tracks.length > 0) {
              similarTracks = data.tracks.filter(
                t => (t.id || t.title) !== (track.id || track.title)
              );
            }
          }
        } catch (err) {
          /* ignore */
        }
      }

      // 2. If < 4 tracks found, search by track title keywords or fallback to current playlist
      if (similarTracks.length < 4 && track.title) {
        const titleQuery = track.title.split(/[\s([-]/)[0]; // First keyword
        if (titleQuery && titleQuery.length > 2) {
          try {
            const res = await fetch(`/api/search?q=${encodeURIComponent(titleQuery)}`);
            if (res.ok) {
              const data = await res.json();
              if (data.tracks && data.tracks.length > 0) {
                const addTracks = data.tracks.filter(
                  t => (t.id || t.title) !== (track.id || track.title)
                );
                similarTracks = [...similarTracks, ...addTracks];
              }
            }
          } catch (err) {
            /* ignore */
          }
        }
      }

      // 3. Fallback to current playlist tracks if still under 4
      if (similarTracks.length < 4 && currentPlaylist && currentPlaylist.length > 1) {
        const plTracks = currentPlaylist.filter(
          t => (t.id || t.title) !== (track.id || track.title)
        );
        similarTracks = [...similarTracks, ...plTracks];
      }

      // Deduplicate by title & artist
      const seen = new Set();
      const uniqueTracks = [];
      for (const t of similarTracks) {
        const key = `${t.title}-${t.artist}`.toLowerCase();
        if (!seen.has(key)) {
          seen.add(key);
          uniqueTracks.push(t);
        }
      }

      const finalTracks = uniqueTracks.slice(0, 8);
      if (finalTracks.length > 0) {
        renderTrackRows(ppSimilar, finalTracks, {
          showAddButton: true,
          playButtonClass: 'play-track-btn',
        });
      } else {
        ppSimilar.innerHTML = '<p class="empty-msg">Похожих треков не найдено</p>';
      }
    } catch (e) {
      console.error('[Similar Tracks Error]:', e);
      ppSimilar.innerHTML = '<p class="empty-msg">Не удалось загрузить похожие треки</p>';
    }
  }

  applyPlayerCoverShape();

  on('state:currentTrack', track => {
    if (!track) { applyAllCoverPlaceholders(''); return; }
    setCoverState('page-player-cover', 'page-player-cover-fallback', track.cover || '', '.pp-cover-wrap');
    if (ppTitle) ppTitle.textContent = track.title || '—';
    if (ppArtist) ppArtist.textContent = track.artist || '—';
    if (ppLike) {
      const liked = typeof isTrackFavorite === 'function' && isTrackFavorite(track);
      ppLike.classList.toggle('is-liked', liked);
      ppLike.querySelector('.material-icons').textContent = liked ? 'favorite' : 'favorite_border';
    }
    applyPlayerCoverShape();
    loadSimilarTracks(track);
  });

  on('state:currentTime', time => {
    if (isNaN(audio.duration) || audio.duration <= 0) return;
    const pct = (time / audio.duration) * 100;
    if (ppProgress) {
      ppProgress.value = pct;
      updateSliderFill(ppProgress);
    }
    if (ppCurrent) ppCurrent.textContent = formatTime(time);
    if (ppTotal) ppTotal.textContent = formatTime(audio.duration);
  });

  on('state:isPlaying', playing => {
    if (ppPlay)
      ppPlay.querySelector('.material-icons').textContent = playing ? 'pause' : 'play_arrow';
    const wrap = document.querySelector('.pp-cover-wrap');
    if (wrap) wrap.classList.toggle('is-playing', Boolean(playing));
  });

  // Sync volume hook
  const origSync = window._fiSyncVolume;
  window._fiSyncVolume = function () {
    if (typeof origSync === 'function') origSync();
    const v = audio.volume;
    if (ppVolume) {
      ppVolume.value = Math.round(v * 100);
      updateSliderFill(ppVolume);
    }
    if (ppVolIcon)
      ppVolIcon.textContent = v === 0 ? 'volume_off' : v < 0.5 ? 'volume_down' : 'volume_up';
  };
})();

// EQ — Web Audio API + Custom Visual EQ
let audioCtx,
  eqFilters = [],
  sharedAnalyser = null;
let normalizerNode = null;
const eqFreqs = [60, 150, 400, 1000, 2400, 15000];
const eqLabels = ['60', '150', '400', '1k', '2.4k', '15k'];

function applyNormalizeToNode() {
  if (!normalizerNode) return;
  const now = normalizerNode.context.currentTime;
  if (appSettings.normalize) {
    normalizerNode.threshold.setValueAtTime(-28, now);
    normalizerNode.knee.setValueAtTime(24, now);
    normalizerNode.ratio.setValueAtTime(10, now);
    normalizerNode.attack.setValueAtTime(0.005, now);
    normalizerNode.release.setValueAtTime(0.3, now);
  } else {
    normalizerNode.threshold.setValueAtTime(0, now);
    normalizerNode.knee.setValueAtTime(0, now);
    normalizerNode.ratio.setValueAtTime(1, now);
  }
}

function initEQ() {
  if (audioCtx) {
    if (audioCtx.state === 'suspended') audioCtx.resume();
    return;
  }
  try {
    audioCtx = new (window.AudioContext || window.webkitAudioContext)();
    console.log('[EQ] AudioContext created, state:', audioCtx.state);
    if (audioCtx.state === 'suspended') audioCtx.resume();
    const source = audioCtx.createMediaElementSource(audio);
    console.log('[EQ] MediaElementSource created');
    let prev = source;
    eqFreqs.forEach(f => {
      const filter = audioCtx.createBiquadFilter();
      filter.type = 'peaking';
      filter.frequency.value = f;
      filter.Q.value = 0.7;
      filter.gain.value = 0;
      prev.connect(filter);
      eqFilters.push(filter);
      prev = filter;
    });
    // Loudness normalizer (dynamics compressor), toggled via appSettings.normalize
    normalizerNode = audioCtx.createDynamicsCompressor();
    applyNormalizeToNode();
    prev.connect(normalizerNode);
    prev = normalizerNode;
    // Create analyser after EQ filters
    sharedAnalyser = audioCtx.createAnalyser();
    sharedAnalyser.fftSize = 128;
    sharedAnalyser.smoothingTimeConstant = 0.75;
    prev.connect(sharedAnalyser);
    sharedAnalyser.connect(audioCtx.destination);
    if (typeof soundpadMusicGainNode !== 'undefined' && soundpadMusicGainNode) {
      try {
        sharedAnalyser.connect(soundpadMusicGainNode);
      } catch (err) {}
    }
    console.log(
      '[EQ] Chain connected: source → EQ → normalizer → analyser → destination/soundpad',
      eqFilters.length
    );
  } catch (e) {
    console.error('[EQ] initEQ error:', e);
  }
}

// Custom EQ presets
const eqPresets = {
  neutral: [0, 0, 0, 0, 0, 0],
  bass: [10, 6, 2, 0, 0, 0],
  treble: [0, 0, 0, 2, 5, 8],
  vocal: [0, 0, 3, 6, 3, 0],
};

// Initialize a custom EQ panel
function initCustomEQ(prefix) {
  const graphEl = document.getElementById(prefix + '-eq-graph');
  const nodesEl = document.getElementById(prefix + '-eq-nodes');
  const labelsEl = document.getElementById(prefix + '-eq-labels');
  const curveEl = document.getElementById(prefix + '-eq-curve');
  const fillEl = document.getElementById(prefix + '-eq-fill');
  if (!graphEl || !nodesEl || !labelsEl) return;

  // EQ data: y=0.5 is 0dB, y=0 is +12dB, y=1 is -12dB
  const eqData = eqFreqs.map((freq, i) => ({
    freq: freq,
    label: eqLabels[i],
    gain: 0,
    y: 0.5,
    x: i === 0 ? 5 : i === eqFreqs.length - 1 ? 95 : (i / (eqFreqs.length - 1)) * 90 + 5,
  }));

  let activeNode = -1;
  let isDragging = false;

  function yToDb(y) {
    return (0.5 - y) * 24;
  }
  function dbToY(db) {
    return 0.5 - db / 24;
  }

  function renderNodes() {
    nodesEl.innerHTML = '';
    eqData.forEach((p, i) => {
      const node = document.createElement('div');
      node.className = 'custom-eq-node';
      node.style.left = p.x + '%';
      node.style.top = p.y * 100 + '%';
      node.dataset.index = i;
      node.addEventListener('mousedown', e => startDrag(e, i));
      node.addEventListener('touchstart', e => startDrag(e, i), { passive: false });
      nodesEl.appendChild(node);
    });
  }

  function renderLabels() {
    labelsEl.innerHTML = '';
    eqData.forEach((p, i) => {
      const lbl = document.createElement('div');
      lbl.className = 'custom-eq-label';
      lbl.style.left = p.x + '%';
      const db = yToDb(p.y);
      lbl.innerHTML =
        '<span class="custom-eq-label-freq">' +
        p.label +
        '</span><span class="custom-eq-label-db">' +
        (db > 0 ? '+' : '') +
        db.toFixed(1) +
        '</span>';
      lbl.id = prefix + '-eq-lbl-' + i;
      labelsEl.appendChild(lbl);
    });
  }

  function drawCurve() {
    const w = graphEl.clientWidth || 300;
    const h = graphEl.clientHeight || 180;
    if (!eqData.length) return;

    const pts = eqData.map(p => ({ x: (p.x / 100) * w, y: p.y * h }));
    let d = 'M 0 ' + pts[0].y + ' L ' + pts[0].x + ' ' + pts[0].y + ' ';

    for (let i = 0; i < pts.length - 1; i++) {
      const c = pts[i],
        n = pts[i + 1];
      const mx = c.x + (n.x - c.x) / 2;
      d += 'C ' + mx + ' ' + c.y + ', ' + mx + ' ' + n.y + ', ' + n.x + ' ' + n.y + ' ';
    }

    d += 'L ' + w + ' ' + pts[pts.length - 1].y;
    curveEl.setAttribute('d', d);
    fillEl.setAttribute('d', d + ' L ' + w + ' ' + h + ' L 0 ' + h + ' Z');
  }

  function applyGains() {
    initEQ();
    eqData.forEach((p, i) => {
      const db = yToDb(p.y);
      p.gain = db;
      if (eqFilters[i]) eqFilters[i].gain.value = db;
      const lbl = document.getElementById(prefix + '-eq-lbl-' + i);
      if (lbl) {
        const dbEl = lbl.querySelector('.custom-eq-label-db');
        if (dbEl) dbEl.textContent = (db > 0 ? '+' : '') + db.toFixed(1);
      }
    });
  }

  function startDrag(e, index) {
    e.preventDefault();
    isDragging = true;
    activeNode = index;
    nodesEl.children[index].classList.add('dragging');
  }

  function onDrag(e) {
    if (!isDragging || activeNode < 0) return;
    e.preventDefault();
    const rect = graphEl.getBoundingClientRect();
    let clientY = e.type.includes('touch') ? e.touches[0].clientY : e.clientY;
    let yPos = (clientY - rect.top) / rect.height;
    yPos = Math.max(0.05, Math.min(0.95, yPos));

    eqData[activeNode].y = yPos;
    nodesEl.children[activeNode].style.top = yPos * 100 + '%';
    applyGains();
    drawCurve();
  }

  function stopDrag() {
    if (activeNode >= 0 && nodesEl.children[activeNode]) {
      nodesEl.children[activeNode].classList.remove('dragging');
    }
    isDragging = false;
    activeNode = -1;
  }

  document.addEventListener('mousemove', onDrag);
  document.addEventListener('touchmove', onDrag, { passive: false });
  document.addEventListener('mouseup', stopDrag);
  document.addEventListener('touchend', stopDrag);
  document.addEventListener('touchcancel', stopDrag);
  window.addEventListener('resize', () => requestAnimationFrame(drawCurve));

  // Preset buttons
  const presetsContainer = document.getElementById(prefix + '-eq-presets');
  if (presetsContainer) {
    presetsContainer.addEventListener('click', e => {
      const btn = e.target.closest('.custom-eq-preset');
      if (!btn) return;
      const name = btn.dataset.preset;
      const gains = eqPresets[name];
      if (!gains) return;
      presetsContainer
        .querySelectorAll('.custom-eq-preset')
        .forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      initEQ();
      gains.forEach((db, i) => {
        eqData[i].y = dbToY(db);
        if (eqFilters[i]) eqFilters[i].gain.value = db;
      });
      renderNodes();
      renderLabels();
      drawCurve();
    });
  }

  renderNodes();
  renderLabels();
  drawCurve();
  setTimeout(drawCurve, 100);
}

// Initialize both EQ panels
function _initAllEQ() {
  console.log('[EQ] Initializing custom EQ panels...');
  try {
    initCustomEQ('right');
    console.log('[EQ] Right panel initialized');
  } catch (e) {
    console.error('[EQ] Right init error:', e);
  }
  try {
    initCustomEQ('fs');
    console.log('[EQ] FS panel initialized');
  } catch (e) {
    console.error('[EQ] FS init error:', e);
  }
  try {
    initCustomEQ('fi');
    console.log('[EQ] FI panel initialized');
  } catch (e) {
    console.error('[EQ] FI init error:', e);
  }
}
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', _initAllEQ);
} else {
  _initAllEQ();
}

// Right panel lyrics
let rightLyricsData = [];
async function loadRightLyrics(title, artist) {
  rightLyricsData = [];
  const body = document.getElementById('right-lyrics-body');
  if (body) body.innerHTML = '<div class="lyrics-placeholder">Загрузка...</div>';
  if (!title) return;
  try {
    const data = await fetchLyricsData(title, artist);
    if (!data) {
      if (body) body.innerHTML = '<div class="lyrics-placeholder">Нет текста</div>';
      return;
    }
    const lrc = data.syncedLyrics || data.plainLyrics || '';
    if (!lrc) {
      if (body) body.innerHTML = '<div class="lyrics-placeholder">Нет текста</div>';
      return;
    }
    if (!data.syncedLyrics) {
      // Plain (non-synced) lyrics — render the whole text as static lines
      rightLyricsData = lrc.split('\n').map(line => line.trim());
      if (body)
        body.innerHTML = lrc
          .split('\n')
          .map(l => `<div class="lyrics-line">${l.trim() || '&nbsp;'}</div>`)
          .join('');
      if (document.getElementById('full-lyrics-overlay')?.classList.contains('open'))
        renderFullLyrics();
      return;
    }
    rightLyricsData = lrc
      .split('\n')
      .map(line => {
        const m = line.match(/^\[(\d+):(\d+)(?:\.(\d+))?\]\s*(.*)/);
        if (m) {
          const ms = m[3] || '0';
          return {
            time: parseInt(m[1]) * 60 + parseInt(m[2]) + parseInt(ms) / Math.pow(10, ms.length),
            text: m[4].trim(),
          };
        }
        return null;
      })
      .filter(Boolean);
    if (body)
      body.innerHTML = rightLyricsData
        .map((l, i) => `<div class="lyrics-line" data-idx="${i}">${l.text || '&nbsp;'}</div>`)
        .join('');
    if (document.getElementById('full-lyrics-overlay')?.classList.contains('open'))
      renderFullLyrics();
  } catch {
    if (body) body.innerHTML = '<div class="lyrics-placeholder">Нет текста</div>';
  }
}

// Sync lyrics with playback
on('state:currentTime', time => {
  if (!rightLyricsData.length) return;
  let idx = -1;
  for (let i = rightLyricsData.length - 1; i >= 0; i--) {
    if (time >= rightLyricsData[i].time) {
      idx = i;
      break;
    }
  }
  if (idx >= 0) {
    const lines = document.querySelectorAll('#right-lyrics-body .lyrics-line');
    lines.forEach((el, i) => {
      if (i === idx) {
        el.style.color = 'var(--accent)';
        el.style.fontWeight = '600';
        el.scrollIntoView({ block: 'center', behavior: 'smooth' });
      } else {
        el.style.color = '';
        el.style.fontWeight = '';
      }
    });
  }
  const fullLines = document.querySelectorAll('#full-lyrics-body .full-lyrics-line');
  fullLines.forEach((el, i) => {
    const active = i === idx;
    el.classList.toggle('active', active);
    if (active && document.getElementById('full-lyrics-overlay')?.classList.contains('open')) {
      el.scrollIntoView({ block: 'center', behavior: 'smooth' });
    }
  });
});

// Sync right player panel with audio events
on('state:currentTrack', track => {
  if (track) {
    if (abLoopActive || abLoopSelecting) clearAbLoop();
    updateRightPlayerPanel(track);
    loadRightLyrics(track.title, track.artist);
  }
});

on('state:currentTime', time => {
  if (state.duration > 0 && rightTimelineTrack) {
    const pct = (time / state.duration) * 100;
    drawRightTimeline(pct);
    const currentEl = document.getElementById('right-player-current');
    const totalEl = document.getElementById('right-player-total');
    if (currentEl) currentEl.textContent = formatTime(time);
    if (totalEl) totalEl.textContent = formatTime(state.duration);
  }
});

on('state:isPlaying', playing => {
  const playBtn = document.getElementById('right-player-play');
  if (playBtn) {
    playBtn.innerHTML = playing
      ? '<svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="4" width="4" height="16"/><rect x="14" y="4" width="4" height="16"/></svg>'
      : '<svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor"><polygon points="6,3 20,12 6,21"/></svg>';
  }
});

on('state:duration', dur => {
  const totalEl = document.getElementById('right-player-total');
  if (totalEl) totalEl.textContent = formatTime(dur);
});

on('state:volume', vol => {
  if (rightPlayerVolume) {
    rightPlayerVolume.value = Math.round(vol * 100);
    rightPlayerVolume.style.setProperty('--r', rightPlayerVolume.value + '%');
  }
});

// ==========================================
// RANGE SLIDERS TRACK FILL FIX
// ==========================================
function updateSliderTrackFill(slider) {
  if (!slider) return;
  const min = parseFloat(slider.min) || 0;
  const max = parseFloat(slider.max) || 100;
  const val = parseFloat(slider.value) || 0;
  const pct = max > min ? Math.max(0, Math.min(100, ((val - min) / (max - min)) * 100)) : 0;
  slider.style.setProperty('--r', `${pct}%`);
}

function markSettingsRangeSliders() {
  document
    .querySelectorAll(
      '.settings-overlay input[type="range"], #settings-overlay input[type="range"]'
    )
    .forEach(slider => slider.classList.add('settings-range'));
}

function initRangeSliderTracks() {
  markSettingsRangeSliders();
  document.querySelectorAll('input[type="range"]').forEach(slider => {
    updateSliderTrackFill(slider);
    if (!slider._hasTrackFillListener) {
      slider._hasTrackFillListener = true;
      slider.addEventListener('input', () => updateSliderTrackFill(slider));
      slider.addEventListener('change', () => updateSliderTrackFill(slider));
    }
  });
}
document.addEventListener('DOMContentLoaded', initRangeSliderTracks);
setTimeout(initRangeSliderTracks, 500);

// ==========================================
// LOCAL MP3 TRACKS MANAGEMENT (IndexedDB + Settings)
// ==========================================
const MP3_DB_NAME = 'VotifyLocalMP3DB';
const MP3_STORE_NAME = 'tracks';

function openMP3DB() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(MP3_DB_NAME, 1);
    req.onupgradeneeded = e => {
      const db = e.target.result;
      if (!db.objectStoreNames.contains(MP3_STORE_NAME)) {
        db.createObjectStore(MP3_STORE_NAME, { keyPath: 'id' });
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

async function saveLocalTrackToDB(track, blob) {
  try {
    const db = await openMP3DB();
    const tx = db.transaction(MP3_STORE_NAME, 'readwrite');
    tx.objectStore(MP3_STORE_NAME).put({ id: track.id, trackMeta: track, blob: blob });
    return new Promise(res => (tx.oncomplete = res));
  } catch (e) {
    console.error('[LocalMP3] Failed to save track:', e);
  }
}

async function getAllLocalTracksFromDB() {
  try {
    const db = await openMP3DB();
    const tx = db.transaction(MP3_STORE_NAME, 'readonly');
    const req = tx.objectStore(MP3_STORE_NAME).getAll();
    return new Promise(res => (req.onsuccess = () => res(req.result || [])));
  } catch (e) {
    console.error('[LocalMP3] Failed to get tracks:', e);
    return [];
  }
}

async function deleteLocalTrackFromDB(trackId) {
  try {
    const db = await openMP3DB();
    const tx = db.transaction(MP3_STORE_NAME, 'readwrite');
    tx.objectStore(MP3_STORE_NAME).delete(trackId);
    return new Promise(res => (tx.oncomplete = res));
  } catch (e) {
    console.error('[LocalMP3] Failed to delete track:', e);
  }
}

let localTracksCache = [];

async function loadLocalMP3Tracks() {
  const records = await getAllLocalTracksFromDB();
  localTracksCache = records.map(r => {
    const blobUrl = URL.createObjectURL(r.blob);
    return {
      ...r.trackMeta,
      isLocal: true,
      localUrl: blobUrl,
    };
  });

  if (localTracksCache.length > 0) {
    playlists['Мои MP3'] = localTracksCache;
    savePlaylists();
    renderSidebarPlaylists();
  }

  renderSettingsLocalTracks();
}

function renderSettingsLocalTracks() {
  const container = document.getElementById('settings-local-tracks-list');
  if (!container) return;

  if (!localTracksCache.length) {
    container.innerHTML =
      '<p class="empty-msg" style="font-size:12px;margin:0;">Нет добавленных MP3 треков</p>';
    return;
  }

  container.innerHTML = localTracksCache
    .map(
      (track, i) => `
    <div class="local-track-item">
      <div class="local-track-info">
        <i class="material-icons" style="color:var(--accent);font-size:20px;">audiotrack</i>
        <span class="local-track-title">${escapeHtml(track.title)}</span>
      </div>
      <div class="local-track-actions">
        <button class="btn-icon-sm play-local-mp3-btn" data-idx="${i}" title="Воспроизвести"><i class="material-icons">play_arrow</i></button>
        <button class="btn-icon-sm delete-local-mp3-btn" data-id="${escapeHtml(track.id)}" title="Удалить"><i class="material-icons">delete</i></button>
      </div>
    </div>
  `
    )
    .join('');

  container.querySelectorAll('.play-local-mp3-btn').forEach(btn => {
    btn.onclick = () => {
      const idx = Number(btn.getAttribute('data-idx'));
      if (localTracksCache[idx]) {
        currentPlaylist = localTracksCache;
        currentTrackIndex = idx;
        playTrack(localTracksCache[idx]);
      }
    };
  });

  container.querySelectorAll('.delete-local-mp3-btn').forEach(btn => {
    btn.onclick = async () => {
      const id = btn.getAttribute('data-id');
      if (id) {
        await deleteLocalTrackFromDB(id);
        localTracksCache = localTracksCache.filter(t => t.id !== id);
        playlists['Мои MP3'] = localTracksCache;
        if (!localTracksCache.length) delete playlists['Мои MP3'];
        savePlaylists();
        renderSidebarPlaylists();
        renderSettingsLocalTracks();
        if (typeof showToast === 'function') showToast('MP3 трек удален');
      }
    };
  });
}

document.addEventListener('DOMContentLoaded', loadLocalMP3Tracks);
setTimeout(loadLocalMP3Tracks, 300);

// Wire add MP3 button in settings
document.addEventListener('click', e => {
  if (e.target.closest('#settings-add-mp3-btn')) {
    const input = document.getElementById('settings-mp3-file-input');
    if (input) input.click();
  }
});

const mp3FileInput = document.getElementById('settings-mp3-file-input');
if (mp3FileInput) {
  mp3FileInput.onchange = async () => {
    const files = Array.from(mp3FileInput.files || []);
    if (!files.length) return;
    let addedCount = 0;
    for (const file of files) {
      const trackId = 'local_' + Date.now() + '_' + Math.random().toString(36).substring(2, 7);
      const title = file.name.replace(/\.[^/.]+$/, '');
      const track = {
        id: trackId,
        title: title,
        artist: 'Мой MP3 трек',
        cover: '',
        isLocal: true,
        duration: 0,
      };
      await saveLocalTrackToDB(track, file);
      const blobUrl = URL.createObjectURL(file);
      localTracksCache.push({ ...track, localUrl: blobUrl });
      addedCount++;
    }
    playlists['Мои MP3'] = localTracksCache;
    savePlaylists();
    renderSidebarPlaylists();
    renderSettingsLocalTracks();
    mp3FileInput.value = '';
    if (typeof showToast === 'function') showToast(`Добавлено MP3 треков: ${addedCount}`);
  };
}

// ==========================================
// Volume Slider (Settings)
// ==========================================
const defaultVolumeSlider = document.getElementById('default-volume');
const volumeValueLabel = document.getElementById('volume-value');
if (defaultVolumeSlider) {
  defaultVolumeSlider.value = Math.round((appSettings.defaultVolume || 0.8) * 100);
  updateSliderTrackFill(defaultVolumeSlider);
  if (volumeValueLabel) volumeValueLabel.textContent = defaultVolumeSlider.value + '%';
  defaultVolumeSlider.addEventListener('input', () => {
    const val = parseInt(defaultVolumeSlider.value);
    if (volumeValueLabel) volumeValueLabel.textContent = val + '%';
    appSettings.defaultVolume = val / 100;
    audio.volume = val / 100;
    updateSliderTrackFill(defaultVolumeSlider);
    saveSettings();
  });
}
if (appSettings.rememberVolume && appSettings.defaultVolume != null)
  audio.volume = Number(appSettings.defaultVolume);

// ==========================================
// General Settings Wiring
// ==========================================

// Audio quality (also pushed to the backend so it affects the actual stream)
const audioQualitySelect = document.getElementById('setting-audio-quality');
if (audioQualitySelect) {
  audioQualitySelect.value = appSettings.audioQuality || 'medium';
  audioQualitySelect.addEventListener('change', async () => {
    appSettings.audioQuality = audioQualitySelect.value;
    saveSettings();
    try {
      await apiFetch('/api/network/settings', {
        method: 'POST',
        body: JSON.stringify({ audioQuality: appSettings.audioQuality }),
      });
      showToast('Качество аудио изменено');
    } catch (e) {
      /* server may be unreachable */
    }
  });
}

// UI click sounds
const uiSoundsToggle = document.getElementById('toggle-ui-sounds');
if (uiSoundsToggle) {
  uiSoundsToggle.checked = appSettings.uiSounds !== false;
  uiSoundsToggle.addEventListener('change', () => {
    appSettings.uiSounds = uiSoundsToggle.checked;
    saveSettings();
  });
}

// Loading sound
const loadingSoundToggle = document.getElementById('toggle-loading-sound');
if (loadingSoundToggle) {
  loadingSoundToggle.checked = appSettings.loadingSound !== false;
  loadingSoundToggle.addEventListener('change', () => {
    appSettings.loadingSound = loadingSoundToggle.checked;
    saveSettings();
  });
}

function wireSettingToggle(id, key, defaultValue, onChange) {
  const el = document.getElementById(id);
  if (!el) return;
  el.checked = appSettings[key] ?? defaultValue;
  el.addEventListener('change', () => {
    appSettings[key] = el.checked;
    saveSettings();
    if (onChange) onChange(el.checked);
  });
}
wireSettingToggle('toggle-pause-when-hidden', 'pauseWhenHidden', false);
wireSettingToggle('toggle-remember-volume', 'rememberVolume', true);
wireSettingToggle('toggle-resume-position', 'resumePosition', true);
wireSettingToggle('toggle-save-history', 'saveHistory', true);

const playbackRateSelect = document.getElementById('setting-playback-rate');
if (playbackRateSelect) {
  playbackRateSelect.value = String(appSettings.playbackRate || 1);
  audio.playbackRate = Number(playbackRateSelect.value);
  playbackRateSelect.addEventListener('change', () => {
    appSettings.playbackRate = Number(playbackRateSelect.value) || 1;
    audio.playbackRate = appSettings.playbackRate;
    saveSettings();
  });
}
const historyLimitSelect = document.getElementById('setting-history-limit');
if (historyLimitSelect) {
  historyLimitSelect.value = String(appSettings.historyLimit || 50);
  historyLimitSelect.addEventListener('change', () => {
    appSettings.historyLimit = Number(historyLimitSelect.value) || 50;
    saveSettings();
  });
}
const preloadSelect = document.getElementById('setting-preload');
if (preloadSelect) {
  preloadSelect.value = appSettings.preload || 'auto';
  audio.preload = preloadSelect.value;
  preloadSelect.addEventListener('change', () => {
    appSettings.preload = preloadSelect.value;
    audio.preload = preloadSelect.value;
    saveSettings();
  });
}
document.addEventListener('visibilitychange', () => {
  if (document.hidden && appSettings.pauseWhenHidden && !audio.paused) audio.pause();
});

// Autoplay
const autoplayToggle = document.getElementById('toggle-autoplay');
if (autoplayToggle) {
  autoplayToggle.checked = appSettings.autoPlay !== false;
  autoplayToggle.addEventListener('change', () => {
    appSettings.autoPlay = autoplayToggle.checked;
    saveSettings();
  });
}

// Crossfade (fades volume out/in near the end of a track)
let preFadeVolume = null;
const crossfadeSlider = document.getElementById('crossfade-duration');
const crossfadeValueLabel = document.getElementById('crossfade-value');
if (crossfadeSlider) {
  crossfadeSlider.value = appSettings.crossfade || 0;
  if (crossfadeValueLabel) crossfadeValueLabel.textContent = (appSettings.crossfade || 0) + ' сек';
  crossfadeSlider.addEventListener('input', () => {
    const val = parseInt(crossfadeSlider.value, 10);
    appSettings.crossfade = val;
    if (crossfadeValueLabel) crossfadeValueLabel.textContent = val + ' сек';
    saveSettings();
  });
}
audio.addEventListener('timeupdate', () => {
  const cf = appSettings.crossfade || 0;
  if (!cf || !audio.duration || isNaN(audio.duration)) return;
  const remaining = audio.duration - audio.currentTime;
  if (remaining <= cf && remaining > 0) {
    if (preFadeVolume === null) preFadeVolume = audio.volume;
    audio.volume = Math.max(0, preFadeVolume * (remaining / cf));
  } else if (preFadeVolume !== null && remaining > cf) {
    audio.volume = preFadeVolume;
    preFadeVolume = null;
  }
});

// Gapless playback — preloads the next track's stream shortly before the current one ends
let gaplessPreloadedFor = null;
const gaplessToggle = document.getElementById('toggle-gapless');
if (gaplessToggle) {
  gaplessToggle.checked = !!appSettings.gapless;
  gaplessToggle.addEventListener('change', () => {
    appSettings.gapless = gaplessToggle.checked;
    saveSettings();
  });
}
audio.addEventListener('timeupdate', () => {
  if (!appSettings.gapless || !currentPlaylist.length || !audio.duration || isNaN(audio.duration))
    return;
  const remaining = audio.duration - audio.currentTime;
  if (remaining < 15 && gaplessPreloadedFor !== currentTrackIndex) {
    gaplessPreloadedFor = currentTrackIndex;
    let nextTrack;
    if (isShuffle) {
      nextTrack = currentPlaylist[Math.floor(Math.random() * currentPlaylist.length)];
    } else {
      nextTrack = currentPlaylist[(currentTrackIndex + 1) % currentPlaylist.length];
    }
    if (nextTrack) preloadTrackStreams([nextTrack]);
  }
});

// Loudness normalization (Web Audio dynamics compressor, see initEQ)
const normalizeToggle = document.getElementById('toggle-normalize');
if (normalizeToggle) {
  normalizeToggle.checked = !!appSettings.normalize;
  normalizeToggle.addEventListener('change', () => {
    appSettings.normalize = normalizeToggle.checked;
    saveSettings();
    applyNormalizeToNode();
  });
}

// Sleep timer
let sleepTimerHandle = null;
const sleepTimerSelect = document.getElementById('sleep-timer-select');
function clearSleepTimer() {
  if (sleepTimerHandle) {
    clearTimeout(sleepTimerHandle);
    sleepTimerHandle = null;
  }
}
function startSleepTimer(minutes) {
  clearSleepTimer();
  if (!minutes) return;
  sleepTimerHandle = setTimeout(
    () => {
      audio.pause();
      showToast('Таймер сна: воспроизведение остановлено');
      appSettings.sleepTimer = 0;
      if (sleepTimerSelect) sleepTimerSelect.value = '0';
      saveSettings();
    },
    minutes * 60 * 1000
  );
}
if (sleepTimerSelect) {
  sleepTimerSelect.value = String(appSettings.sleepTimer || 0);
  if (appSettings.sleepTimer) startSleepTimer(appSettings.sleepTimer);
  sleepTimerSelect.addEventListener('change', () => {
    const minutes = parseInt(sleepTimerSelect.value, 10);
    appSettings.sleepTimer = minutes;
    saveSettings();
    if (minutes > 0) {
      startSleepTimer(minutes);
      showToast(`Таймер сна установлен на ${minutes} мин`);
    } else {
      clearSleepTimer();
    }
  });
}

// ==========================================
// Backend audio quality
// ==========================================
async function loadNetworkSettingsFromServer() {
  try {
    const data = await apiFetch('/api/network/settings');
    if (data && !data.error) {
      appSettings.audioQuality = data.audioQuality || appSettings.audioQuality;
      delete appSettings.streamSource;
      delete appSettings.invidiousInstance;
      delete appSettings.pipedInstance;
      if (audioQualitySelect) audioQualitySelect.value = appSettings.audioQuality || 'medium';
      localStorage.setItem('votify-settings', JSON.stringify(appSettings));
    }
  } catch (e) {
    /* server may not be ready yet */
  }
}

loadNetworkSettingsFromServer();

// ==========================================
// Lyrics settings
// ==========================================
const autoLyricsToggle = document.getElementById('toggle-auto-lyrics');
if (autoLyricsToggle) {
  autoLyricsToggle.checked = appSettings.autoLyrics !== false;
  autoLyricsToggle.addEventListener('change', () => {
    appSettings.autoLyrics = autoLyricsToggle.checked;
    saveSettings();
  });
}
const syncedLyricsToggle = document.getElementById('toggle-synced-lyrics');
if (syncedLyricsToggle) {
  syncedLyricsToggle.checked = appSettings.syncedLyrics !== false;
  syncedLyricsToggle.addEventListener('change', () => {
    appSettings.syncedLyrics = syncedLyricsToggle.checked;
    saveSettings();
  });
}
const translateLyricsToggle = document.getElementById('toggle-translate-lyrics');
if (translateLyricsToggle) {
  translateLyricsToggle.checked = !!appSettings.translateLyrics;
  translateLyricsToggle.addEventListener('change', () => {
    appSettings.translateLyrics = translateLyricsToggle.checked;
    saveSettings();
  });
}

// ==========================================
// Track notifications
// ==========================================
const trackNotificationsToggle = document.getElementById('toggle-track-notifications');
if (trackNotificationsToggle) {
  trackNotificationsToggle.checked = !!appSettings.trackNotifications;
  trackNotificationsToggle.addEventListener('change', () => {
    appSettings.trackNotifications = trackNotificationsToggle.checked;
    saveSettings();
    if (
      trackNotificationsToggle.checked &&
      typeof Notification !== 'undefined' &&
      Notification.permission === 'default'
    ) {
      Notification.requestPermission().catch(() => {});
    }
  });
}

// ==========================================
// Storage / cache management
// ==========================================
const openOfflineDB = openMP3DB;
const OFFLINE_STORE = MP3_STORE_NAME;

function downloadPlaylist(name) {
  const list = playlists[name] || [];
  if (!list.length) {
    if (typeof showToast === 'function') showToast('Плейлист пуст');
    return;
  }
  if (typeof showToast === 'function')
    showToast(`Загрузка плейлиста «${name}» (${list.length} треков)...`);
}

async function getOfflineCacheStats() {
  try {
    const db = await openOfflineDB();
    return new Promise(resolve => {
      const tx = db.transaction(OFFLINE_STORE, 'readonly');
      const req = tx.objectStore(OFFLINE_STORE).getAll();
      req.onsuccess = () => {
        const items = req.result || [];
        const bytes = items.reduce((sum, it) => sum + (it.blob?.size || 0), 0);
        resolve({ count: items.length, bytes });
      };
      req.onerror = () => resolve({ count: 0, bytes: 0 });
    });
  } catch {
    return { count: 0, bytes: 0 };
  }
}

async function clearOfflineCacheStore() {
  try {
    const db = await openOfflineDB();
    return new Promise(resolve => {
      const tx = db.transaction(OFFLINE_STORE, 'readwrite');
      tx.objectStore(OFFLINE_STORE).clear();
      tx.oncomplete = () => resolve(true);
      tx.onerror = () => resolve(false);
    });
  } catch {
    return false;
  }
}

async function refreshStorageStats() {
  const localSizeEl = document.getElementById('storage-local-size');
  const cacheSizeEl = document.getElementById('storage-cache-size');
  if (localSizeEl) localSizeEl.textContent = estimateLocalStorageSize() + ' КБ';
  if (cacheSizeEl) {
    const stats = await getOfflineCacheStats();
    const mb = (stats.bytes / (1024 * 1024)).toFixed(1);
    cacheSizeEl.textContent = stats.count > 0 ? `${stats.count} треков (~${mb} МБ)` : 'Пусто';
  }
}

safeClick('clear-offline-cache-btn', async () => {
  const confirmed = await confirmModal(
    'Очистить офлайн-кэш',
    'Удалить все скачанные для офлайн-прослушивания треки?'
  );
  if (!confirmed) return;
  await clearOfflineCacheStore();
  showToast('Офлайн-кэш очищен');
  refreshStorageStats();
});

safeClick('clear-listening-history-btn', async () => {
  const confirmed = await confirmModal('Очистить историю', 'Удалить историю прослушивания?');
  if (!confirmed) return;
  localStorage.removeItem('listeningHistory');
  showToast('История прослушивания очищена');
  refreshStorageStats();
});

safeClick('settings-clear-search-history-btn', async () => {
  const confirmed = await confirmModal(
    'Очистить историю поиска',
    'Удалить сохранённую историю поиска?'
  );
  if (!confirmed) return;
  localStorage.removeItem('votify-search-history');
  renderSearchHistory();
  showToast('История поиска очищена');
  refreshStorageStats();
});

// ==========================================
// Reset settings buttons
// ==========================================
safeClick('settings-reset-btn', async () => {
  const confirmed = await confirmModal(
    'Сброс настроек',
    'Сбросить все настройки приложения до значений по умолчанию? Плейлисты сохранятся.'
  );
  if (!confirmed) return;
  localStorage.removeItem('votify-settings');
  localStorage.removeItem('votify-hotkeys');
  showToast('Настройки сброшены. Перезагрузка...');
  setTimeout(() => location.reload(), 800);
});

safeClick('morph-reset-all', async () => {
  const confirmed = await confirmModal(
    'Сброс оформления',
    'Сбросить все настройки оформления до значений по умолчанию?'
  );
  if (!confirmed) return;
  applyAccentColor('#1DB954');
  appSettings.fontFamily = 'default';
  if (fontFamilySelect) fontFamilySelect.value = 'default';
  appSettings.compactUI = false;
  if (compactToggle) compactToggle.checked = false;
  appSettings.background = 'default';
  applyBackground();
  if (bgPresetsEl)
    bgPresetsEl
      .querySelectorAll('.bg-card')
      .forEach(b => b.classList.toggle('bg-card-active', b.dataset.bg === 'default'));
  // Reset the newer appearance controls too
  appSettings.theme = 'contrast';
  applyThemeMode('contrast');
  appSettings.fontSize = '16px';
  const fontSizeSliderEl = document.getElementById('font-size-slider');
  const fontSizeSliderValueEl = document.getElementById('font-size-slider-value');
  if (fontSizeSliderEl) fontSizeSliderEl.value = 16;
  if (fontSizeSliderValueEl) fontSizeSliderValueEl.textContent = '16px';
  appSettings.cornerRadius = 8;
  const cornerRadiusSliderEl = document.getElementById('corner-radius-slider');
  const cornerRadiusSliderValueEl = document.getElementById('corner-radius-slider-value');
  if (cornerRadiusSliderEl) cornerRadiusSliderEl.value = 8;
  if (cornerRadiusSliderValueEl) cornerRadiusSliderValueEl.textContent = '8px';
  appSettings.opacity = '98';
  const windowOpacitySliderEl = document.getElementById('window-opacity-slider');
  const windowOpacitySliderValueEl = document.getElementById('window-opacity-slider-value');
  if (windowOpacitySliderEl) windowOpacitySliderEl.value = 98;
  if (windowOpacitySliderValueEl) windowOpacitySliderValueEl.textContent = '98%';
  appSettings.animations = true;
  document.body.classList.remove('no-animations');
  const animationsToggleEl = document.getElementById('toggle-animations');
  if (animationsToggleEl) animationsToggleEl.checked = true;
  appSettings.coverInPlayer = true;
  document.body.classList.remove('hide-player-cover');
  const coverInPlayerToggleEl = document.getElementById('toggle-cover-in-player');
  if (coverInPlayerToggleEl) coverInPlayerToggleEl.checked = true;
  appSettings.density = 'comfortable';
  appSettings.accentGlow = true;
  appSettings.trackCardStyle = 'default';
  appSettings.backgroundBlur = 0;
  if (densitySelect) densitySelect.value = 'comfortable';
  if (accentGlowToggle) accentGlowToggle.checked = true;
  if (trackCardStyleSelect) trackCardStyleSelect.value = 'default';
  if (backgroundBlurSlider) backgroundBlurSlider.value = 0;
  if (backgroundBlurValue) backgroundBlurValue.textContent = '0px';
  applyAppearance();
  saveSettings();
  showToast('Оформление сброшено');
});

// ==========================================
// Launch at startup / close to tray (Electron)
// ==========================================
const launchAtStartupToggle = document.getElementById('toggle-launch-at-startup');
if (launchAtStartupToggle) {
  if (window.electronAPI?.getLaunchAtLogin) {
    window.electronAPI
      .getLaunchAtLogin()
      .then(val => {
        launchAtStartupToggle.checked = !!val;
      })
      .catch(() => {});
    launchAtStartupToggle.addEventListener('change', () => {
      window.electronAPI.setLaunchAtLogin(launchAtStartupToggle.checked);
      showToast(launchAtStartupToggle.checked ? 'Автозапуск включён' : 'Автозапуск выключен');
    });
  } else {
    launchAtStartupToggle.disabled = true;
    launchAtStartupToggle.closest('.setting-toggle-item')?.style.setProperty('opacity', '0.4');
  }
}

const closeToTrayToggle = document.getElementById('toggle-close-to-tray');
if (closeToTrayToggle) {
  closeToTrayToggle.checked = !!appSettings.closeToTray;
  if (window.electronAPI?.setCloseToTray)
    window.electronAPI.setCloseToTray(!!appSettings.closeToTray);
  closeToTrayToggle.addEventListener('change', () => {
    appSettings.closeToTray = closeToTrayToggle.checked;
    saveSettings();
    if (window.electronAPI?.setCloseToTray)
      window.electronAPI.setCloseToTray(closeToTrayToggle.checked);
  });
}

// Refresh storage stats whenever the settings overlay is opened
const settingsOverlayEl = document.getElementById('settings-overlay');
if (settingsOverlayEl) {
  const observer = new MutationObserver(() => {
    if (settingsOverlayEl.style.display !== 'none') refreshStorageStats();
  });
  observer.observe(settingsOverlayEl, { attributes: true, attributeFilter: ['style'] });
}

// ==========================================
// Hotkey Editing
// ==========================================
let hotkeyOverrides = readStoredJson('votify-hotkeys', {});
const defaultHotkeys = {
  play: ' ',
  next: 'n',
  prev: 'p',
  volup: 'ArrowUp',
  voldown: 'ArrowDown',
  forward: 'ArrowRight',
  backward: 'ArrowLeft',
  mute: 'm',
  like: 'l',
  search: 'Control+k',
  fullscreen: 'f',
  lyrics: 'Control+l',
};

function getHotkey(action) {
  return hotkeyOverrides[action] || defaultHotkeys[action] || '';
}

function displayHotkeyName(key) {
  const names = {
    ' ': 'Space',
    ArrowUp: '↑',
    ArrowDown: '↓',
    ArrowLeft: '←',
    ArrowRight: '→',
    Escape: 'Esc',
  };
  return names[key] || key.toUpperCase();
}

// Init hotkey display
document.querySelectorAll('.hotkey-key-btn').forEach(btn => {
  const action = btn.dataset.action;
  const span = btn.querySelector('kbd span');
  if (span && action) span.textContent = displayHotkeyName(getHotkey(action));
});

let recordingAction = null;

document.addEventListener('click', e => {
  const btn = e.target.closest('.hotkey-key-btn');
  if (!btn) return;
  e.preventDefault();

  // Cancel previous recording
  if (recordingAction) {
    const prev = document.querySelector('.hotkey-key-btn.recording');
    if (prev) prev.classList.remove('recording');
  }

  recordingAction = btn.dataset.action;
  btn.classList.add('recording');
  const span = btn.querySelector('kbd span');
  if (span) span.textContent = '...';
});

document.addEventListener('keydown', e => {
  if (!recordingAction) return;
  e.preventDefault();
  e.stopPropagation();

  const btn = document.querySelector(`.hotkey-key-btn[data-action="${recordingAction}"]`);
  if (!btn) {
    recordingAction = null;
    return;
  }

  if (e.key === 'Escape') {
    // Cancel recording
    btn.classList.remove('recording');
    const span = btn.querySelector('kbd span');
    if (span) span.textContent = displayHotkeyName(getHotkey(recordingAction));
    recordingAction = null;
    return;
  }

  // Save new hotkey
  hotkeyOverrides[recordingAction] = e.key;
  localStorage.setItem('votify-hotkeys', JSON.stringify(hotkeyOverrides));

  btn.classList.remove('recording');
  const span = btn.querySelector('kbd span');
  if (span) span.textContent = displayHotkeyName(e.key);
  recordingAction = null;
});

// Reset hotkeys button
safeClick('reset-hotkeys-btn', () => {
  hotkeyOverrides = {};
  localStorage.removeItem('votify-hotkeys');
  document.querySelectorAll('.hotkey-key-btn').forEach(btn => {
    const action = btn.dataset.action;
    const span = btn.querySelector('kbd span');
    if (span && action) span.textContent = displayHotkeyName(defaultHotkeys[action]);
  });
});

// ==========================================
// Theme Color Switching
// ==========================================
function applyAccentColor(color) {
  const normalized = /^#[0-9a-f]{6}$/i.test(String(color || ''))
    ? String(color).toUpperCase()
    : '#1DB954';
  document.documentElement.style.setProperty('--accent', normalized);
  // Also compute and set --accent-rgb for rgba() usage
  const hex = normalized.replace('#', '');
  const r = parseInt(hex.substr(0, 2), 16);
  const g = parseInt(hex.substr(2, 2), 16);
  const b = parseInt(hex.substr(4, 2), 16);
  document.documentElement.style.setProperty('--accent-rgb', `${r},${g},${b}`);
  appSettings.accent = normalized;
  appSettings.customColorPrimary = normalized;
  saveSettings();
  // Update active state on theme cards
  document.querySelectorAll('.theme-card').forEach(c => {
    c.classList.toggle('active', String(c.dataset.accent || '').toUpperCase() === normalized);
  });
}

// Theme card clicks
document.querySelectorAll('.theme-card').forEach(card => {
  card.addEventListener('click', () => {
    const accent = card.dataset.accent;
    if (accent === 'custom') {
      const colorInput = document.getElementById('accent-color');
      if (colorInput) colorInput.click();
    } else {
      applyAccentColor(accent);
    }
  });
});

// Custom color picker
const accentColorInput = document.getElementById('accent-color');
if (accentColorInput) {
  accentColorInput.addEventListener('input', () => {
    applyAccentColor(accentColorInput.value);
  });
}

// Apply the canonical saved accent on load. Custom picker values take
// precedence so the visible color and workshop preview cannot diverge.
if (appSettings.customColorPrimary || appSettings.accent) {
  applyAccentColor(appSettings.customColorPrimary || appSettings.accent);
}

// Background presets
const bgGradients = {
  default: '',
  'grad-1': 'linear-gradient(135deg, #7928ca 0%, #ff0080 50%, #11101d 100%)',
  'grad-2': 'linear-gradient(135deg, #00f2fe 0%, #4facfe 50%, #050b14 100%)',
  'grad-3': 'linear-gradient(135deg, #833ab4 0%, #fd1d1d 50%, #fcb045 100%)',
  'grad-4': 'linear-gradient(135deg, #10b981 0%, #059669 50%, #022c22 100%)',
  'grad-5': 'linear-gradient(135deg, #ff416c 0%, #ff4b2b 50%, #1a0505 100%)',
  'grad-6': 'linear-gradient(135deg, #00c6ff 0%, #0072ff 50%, #030f26 100%)',
  'grad-7': 'linear-gradient(135deg, #a855f7 0%, #6366f1 50%, #0f172a 100%)',
  'grad-8': 'linear-gradient(135deg, #f43f5e 0%, #fb7185 50%, #1e050c 100%)',
  'grad-9': 'linear-gradient(135deg, #18181b 0%, #09090b 100%)',
};

// --- Appearance settings ---

// Font family
const fontFamilySelect = document.getElementById('font-family-select');
if (fontFamilySelect) {
  fontFamilySelect.value = appSettings.fontFamily || 'default';
  fontFamilySelect.addEventListener('change', () => {
    appSettings.fontFamily = fontFamilySelect.value;
    saveSettings();
    applyAppearance();
  });
}

// Liquid Glass mode (morph-toggle checkbox)
const liquidGlassToggle = document.getElementById('toggle-liquid-glass');
if (liquidGlassToggle) {
  liquidGlassToggle.checked = !!appSettings.liquidGlass;
  liquidGlassToggle.addEventListener('change', () => {
    appSettings.liquidGlass = liquidGlassToggle.checked;
    applyAppearance();
    saveSettings();
  });
}

// Compact mode (morph-toggle checkbox)
const compactToggle = document.getElementById('toggle-compact');
if (compactToggle) {
  compactToggle.checked = !!appSettings.compactUI;
  compactToggle.addEventListener('change', () => {
    appSettings.compactUI = compactToggle.checked;
    saveSettings();
    applyAppearance();
  });
}

const densitySelect = document.getElementById('interface-density-select');
if (densitySelect) {
  densitySelect.value = appSettings.density || 'comfortable';
  densitySelect.addEventListener('change', () => {
    appSettings.density = densitySelect.value;
    applyAppearance();
    saveSettings();
  });
}

const accentGlowToggle = document.getElementById('toggle-accent-glow');
if (accentGlowToggle) {
  accentGlowToggle.checked = appSettings.accentGlow !== false;
  accentGlowToggle.addEventListener('change', () => {
    appSettings.accentGlow = accentGlowToggle.checked;
    applyAppearance();
    saveSettings();
  });
}

const trackCardStyleSelect = document.getElementById('track-card-style-select');
if (trackCardStyleSelect) {
  trackCardStyleSelect.value = appSettings.trackCardStyle || 'default';
  trackCardStyleSelect.addEventListener('change', () => {
    appSettings.trackCardStyle = trackCardStyleSelect.value;
    applyAppearance();
    saveSettings();
  });
}

const backgroundBlurSlider = document.getElementById('background-blur-slider');
const backgroundBlurValue = document.getElementById('background-blur-value');
if (backgroundBlurSlider) {
  backgroundBlurSlider.value = Number(appSettings.backgroundBlur) || 0;
  if (backgroundBlurValue) backgroundBlurValue.textContent = `${backgroundBlurSlider.value}px`;
  backgroundBlurSlider.addEventListener('input', () => {
    appSettings.backgroundBlur = Number(backgroundBlurSlider.value) || 0;
    if (backgroundBlurValue) backgroundBlurValue.textContent = `${appSettings.backgroundBlur}px`;
    applyAppearance();
    saveSettings();
  });
}

// Theme mode (dark / midnight / contrast)
function applyThemeMode(mode) {
  document.body.setAttribute('data-theme-mode', mode || 'contrast');
  document.querySelectorAll('.theme-mode-card').forEach(c => {
    c.classList.toggle('active', c.dataset.themeMode === (mode || 'contrast'));
  });
}
document.querySelectorAll('.theme-mode-card').forEach(card => {
  card.addEventListener('click', () => {
    const mode = card.dataset.themeMode;
    appSettings.theme = mode;
    applyThemeMode(mode);
    saveSettings();
  });
});
applyThemeMode(appSettings.theme || 'contrast');

// Font size slider
const fontSizeSlider = document.getElementById('font-size-slider');
const fontSizeSliderValue = document.getElementById('font-size-slider-value');
if (fontSizeSlider) {
  const initialSize = parseInt(appSettings.fontSize) || 16;
  fontSizeSlider.value = initialSize;
  if (fontSizeSliderValue) fontSizeSliderValue.textContent = initialSize + 'px';
  fontSizeSlider.addEventListener('input', () => {
    const val = parseInt(fontSizeSlider.value, 10);
    if (fontSizeSliderValue) fontSizeSliderValue.textContent = val + 'px';
  });
  fontSizeSlider.addEventListener('change', () => {
    const val = parseInt(fontSizeSlider.value, 10);
    appSettings.fontSize = val + 'px';
    applyAppearance();
    saveSettings();
  });
}

// Corner radius slider
const cornerRadiusSlider = document.getElementById('corner-radius-slider');
const cornerRadiusSliderValue = document.getElementById('corner-radius-slider-value');
if (cornerRadiusSlider) {
  const initialRadius = appSettings.cornerRadius ?? 8;
  cornerRadiusSlider.value = initialRadius;
  if (cornerRadiusSliderValue) cornerRadiusSliderValue.textContent = initialRadius + 'px';
  cornerRadiusSlider.addEventListener('input', () => {
    const val = parseInt(cornerRadiusSlider.value, 10);
    appSettings.cornerRadius = val;
    if (cornerRadiusSliderValue) cornerRadiusSliderValue.textContent = val + 'px';
    applyAppearance();
    saveSettings();
  });
}

// Window opacity slider
const windowOpacitySlider = document.getElementById('window-opacity-slider');
const windowOpacitySliderValue = document.getElementById('window-opacity-slider-value');
if (windowOpacitySlider) {
  const initialOpacity = parseInt(appSettings.opacity) || 98;
  windowOpacitySlider.value = initialOpacity;
  if (windowOpacitySliderValue) windowOpacitySliderValue.textContent = initialOpacity + '%';
  windowOpacitySlider.addEventListener('input', () => {
    const val = parseInt(windowOpacitySlider.value, 10);
    appSettings.opacity = String(val);
    if (windowOpacitySliderValue) windowOpacitySliderValue.textContent = val + '%';
    applyAppearance();
    saveSettings();
  });
}

// Interface animations toggle
const animationsToggle = document.getElementById('toggle-animations');
if (animationsToggle) {
  animationsToggle.checked = appSettings.animations !== false;
  document.body.classList.toggle('no-animations', appSettings.animations === false);
  animationsToggle.addEventListener('change', () => {
    appSettings.animations = animationsToggle.checked;
    document.body.classList.toggle('no-animations', !animationsToggle.checked);
    saveSettings();
  });
}

// Cover art in the mini player bar
const coverInPlayerToggle = document.getElementById('toggle-cover-in-player');
if (coverInPlayerToggle) {
  coverInPlayerToggle.checked = appSettings.coverInPlayer !== false;
  document.body.classList.toggle('hide-player-cover', appSettings.coverInPlayer === false);
  coverInPlayerToggle.addEventListener('change', () => {
    appSettings.coverInPlayer = coverInPlayerToggle.checked;
    document.body.classList.toggle('hide-player-cover', !coverInPlayerToggle.checked);
    applyCoverVisibility();
    saveSettings();
  });
}
function applyCoverVisibility() {
  const hidden = appSettings.coverInPlayer === false;
  document.body.classList.toggle('hide-player-cover', hidden);
  document
    .querySelectorAll('.player-bar-cover-wrap, .right-player-cover, .fs-player-cover-wrap')
    .forEach(el => {
      el.style.display = hidden ? 'none' : '';
    });
}
applyCoverVisibility();

// Splash screen on launch
const splashScreenToggle = document.getElementById('toggle-splash-screen');
if (splashScreenToggle) {
  splashScreenToggle.checked = appSettings.splashScreen !== false;
  splashScreenToggle.addEventListener('change', () => {
    appSettings.splashScreen = splashScreenToggle.checked;
    saveSettings();
  });
}

// Background
function applyBackground() {
  const storedUrl = String(appSettings.bgUrl || '').trim();
  const bg = /^(https?:|data:image\/)/i.test(storedUrl) ? storedUrl : appSettings.background;
  if (!bg || bg === 'default') {
    document.body.style.background = '';
    document.body.style.backgroundImage = '';
  } else if (bgGradients[bg]) {
    document.body.style.background = bgGradients[bg];
  } else if (bg.startsWith('http') || bg.startsWith('data:')) {
    document.body.style.background = `url("${bg}") center/cover no-repeat fixed`;
  } else {
    document.body.style.background = bg;
  }
}
const bgPresetsEl = document.getElementById('bg-presets');
if (bgPresetsEl) {
  bgPresetsEl.addEventListener('click', e => {
    const btn = e.target.closest('.bg-card');
    if (!btn) return;
    bgPresetsEl.querySelectorAll('.bg-card').forEach(b => b.classList.remove('bg-card-active'));
    btn.classList.add('bg-card-active');
    appSettings.background = btn.dataset.bg;
    appSettings.bgPreset = btn.dataset.bg;
    appSettings.bgUrl = '';
    const urlInput = document.getElementById('bg-url-input');
    if (urlInput) urlInput.value = '';
    saveSettings();
    applyBackground();
  });
  // Restore active state
  const saved = appSettings.background || 'default';
  bgPresetsEl.querySelectorAll('.bg-card').forEach(b => {
    b.classList.toggle('bg-card-active', b.dataset.bg === saved);
  });
}
const bgUrlInput = document.getElementById('bg-url-input');
const bgUrlApply = document.getElementById('bg-url-apply');
if (bgUrlApply && bgUrlInput) {
  bgUrlApply.addEventListener('click', () => {
    const url = bgUrlInput.value.trim();
    if (!url) return;
    appSettings.background = url;
    appSettings.bgUrl = url;
    saveSettings();
    applyBackground();
    if (bgPresetsEl)
      bgPresetsEl.querySelectorAll('.bg-card').forEach(b => b.classList.remove('bg-card-active'));
  });
  bgUrlInput.addEventListener('keydown', e => {
    if (e.key === 'Enter') bgUrlApply.click();
  });
}
const bgFileBtn = document.getElementById('bg-file-btn');
const bgFileInput = document.getElementById('bg-file-input');
if (bgFileBtn && bgFileInput) {
  bgFileBtn.addEventListener('click', () => bgFileInput.click());
  bgFileInput.addEventListener('change', () => {
    const file = bgFileInput.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = ev => {
      appSettings.background = ev.target.result;
      saveSettings();
      applyBackground();
      if (bgPresetsEl)
        bgPresetsEl.querySelectorAll('.bg-card').forEach(b => b.classList.remove('bg-card-active'));
    };
    reader.readAsDataURL(file);
  });
}
// Background brightness slider
const bgBrightnessSlider = document.getElementById('bg-brightness-slider');
const bgBrightnessSliderValue = document.getElementById('bg-brightness-slider-value');
if (bgBrightnessSlider) {
  const initialBrightness =
    appSettings.bgBrightness !== undefined ? Number(appSettings.bgBrightness) : 100;
  bgBrightnessSlider.value = initialBrightness;
  if (bgBrightnessSliderValue) bgBrightnessSliderValue.textContent = initialBrightness + '%';
  bgBrightnessSlider.addEventListener('input', () => {
    const val = Number(bgBrightnessSlider.value);
    appSettings.bgBrightness = val;
    if (bgBrightnessSliderValue) bgBrightnessSliderValue.textContent = val + '%';
    applyAppearance();
    saveSettings();
  });
}

// Background blur slider
const bgBlurSlider = document.getElementById('bg-blur-slider');
const bgBlurSliderValue = document.getElementById('bg-blur-slider-value');
if (bgBlurSlider) {
  const initialBlur = Number(appSettings.backgroundBlur) || 0;
  bgBlurSlider.value = initialBlur;
  if (bgBlurSliderValue) bgBlurSliderValue.textContent = initialBlur + 'px';
  bgBlurSlider.addEventListener('input', () => {
    const val = Number(bgBlurSlider.value);
    appSettings.backgroundBlur = val;
    if (bgBlurSliderValue) bgBlurSliderValue.textContent = val + 'px';
    applyAppearance();
    saveSettings();
  });
}

// UI Panel Transparency slider
const uiTransparencySlider = document.getElementById('ui-transparency-slider');
const uiTransparencySliderValue = document.getElementById('ui-transparency-slider-value');
if (uiTransparencySlider) {
  const initialTransp =
    appSettings.uiTransparency !== undefined ? Number(appSettings.uiTransparency) : 45;
  uiTransparencySlider.value = initialTransp;
  if (uiTransparencySliderValue) uiTransparencySliderValue.textContent = initialTransp + '%';
  uiTransparencySlider.addEventListener('input', () => {
    const val = Number(uiTransparencySlider.value);
    appSettings.uiTransparency = val;
    if (uiTransparencySliderValue) uiTransparencySliderValue.textContent = val + '%';
    applyAppearance();
    saveSettings();
  });
}

const bgColorInput = document.getElementById('bg-color-input');
const bgColorPreview = document.getElementById('bg-color-preview');
if (bgColorInput) {
  if (appSettings.background && appSettings.background.startsWith('#')) {
    bgColorInput.value = appSettings.background;
    if (bgColorPreview) bgColorPreview.style.background = appSettings.background;
  }
  bgColorInput.addEventListener('input', () => {
    const color = bgColorInput.value;
    if (bgColorPreview) bgColorPreview.style.background = color;
    appSettings.background = color;
    applyBackground();
  });
  bgColorInput.addEventListener('change', () => {
    appSettings.background = bgColorInput.value;
    saveSettings();
    applyBackground();
    if (bgPresetsEl)
      bgPresetsEl.querySelectorAll('.bg-card').forEach(b => b.classList.remove('bg-card-active'));
  });
}
const bgResetBtn = document.getElementById('bg-reset-btn');
if (bgResetBtn) {
  bgResetBtn.addEventListener('click', () => {
    appSettings.background = 'default';
    saveSettings();
    applyBackground();
    if (bgPresetsEl) {
      bgPresetsEl
        .querySelectorAll('.bg-card')
        .forEach(b => b.classList.toggle('bg-card-active', b.dataset.bg === 'default'));
    }
    if (bgUrlInput) bgUrlInput.value = '';
  });
}
// Apply background on load
try {
  applyBackground();
} catch (e) {
  console.warn('Background apply error:', e);
}

// Language
const langSelect = document.getElementById('lang-select');
if (langSelect) {
  langSelect.value = appSettings.lang || 'ru';
  langSelect.addEventListener('change', () => {
    appSettings.lang = langSelect.value;
    saveSettings();
    applyLanguage(appSettings.lang);
  });
}

function applyAppearance() {
  const root = document.documentElement;
  // Font
  const ff = appSettings.fontFamily || 'default';
  const fonts = {
    default: '"Segoe UI", Roboto, sans-serif',
    mono: '"JetBrains Mono", monospace',
    rounded: '"Nunito", "Segoe UI", sans-serif',
  };
  root.style.setProperty('--font-family', fonts[ff] || fonts.default);
  document.body.style.fontFamily = fonts[ff] || fonts.default;
  // Font size
  root.style.setProperty('--font-size', appSettings.fontSize || '16px');
  root.style.setProperty('--app-font-size-offset', appSettings.fontSize || '16px');
  document.body.style.fontSize = appSettings.fontSize || '16px';
  applyInterfaceTextScale();
  // Opacity
  const op = (parseInt(appSettings.opacity) || 98) / 100;
  document
    .querySelector('.app-container')
    ?.style?.setProperty('opacity', String(Math.max(0.7, op)));
  // Border radius
  const r = appSettings.cornerRadius || 8;
  root.style.setProperty('--radius-sm', Math.max(0, r - 4) + 'px');
  root.style.setProperty('--radius-md', r + 'px');
  root.style.setProperty('--radius-lg', Math.min(32, r + 6) + 'px');
  // Compact & Liquid Glass
  document.body.classList.toggle('compact-ui', !!appSettings.compactUI);
  document.body.classList.toggle('liquid-glass-enabled', !!appSettings.liquidGlass);
  document.body.dataset.density = appSettings.density || 'comfortable';
  document.body.dataset.trackCardStyle = appSettings.trackCardStyle || 'default';
  document.body.classList.toggle('no-accent-glow', appSettings.accentGlow === false);

  const bright = appSettings.bgBrightness !== undefined ? Number(appSettings.bgBrightness) : 100;
  root.style.setProperty('--bg-brightness', `${bright}%`);
  root.style.setProperty('--bg-blur', `${Number(appSettings.backgroundBlur) || 0}px`);
  const transp = appSettings.uiTransparency !== undefined ? Number(appSettings.uiTransparency) : 45;
  root.style.setProperty('--ui-panel-opacity', (transp / 100).toFixed(2));
}

const scalableTextSelector = [
  'h1',
  'h2',
  'h3',
  'h4',
  'p',
  'label',
  'input',
  'select',
  'textarea',
  'button',
  '.track-title',
  '.track-artist',
  '.section-title',
  '.setting-toggle-label',
  '.setting-toggle-desc',
  '.morph-settings-item-label',
  '.morph-settings-item-value',
  '.fs-title',
  '.fs-artist',
  '.right-player-title',
  '.right-player-artist',
  '.lyrics-line',
  '.player-track-title',
  '.player-track-artist',
].join(',');

function applyInterfaceTextScale(scope = document) {
  const selectedSize = parseInt(appSettings.fontSize, 10) || 16;
  const scale = selectedSize / 16;
  const elements = [];
  if (scope.nodeType === Node.ELEMENT_NODE && scope.matches?.(scalableTextSelector))
    elements.push(scope);
  elements.push(...scope.querySelectorAll(scalableTextSelector));
  elements.forEach(el => {
    if (el.classList.contains('material-icons')) return;
    if (!el.dataset.baseFontSize)
      el.dataset.baseFontSize = String(parseFloat(getComputedStyle(el).fontSize) || 16);
    el.style.fontSize = `${Math.max(9, Number(el.dataset.baseFontSize) * scale).toFixed(2)}px`;
  });
}

const textScaleObserver = new MutationObserver(records => {
  records.forEach(record =>
    record.addedNodes.forEach(node => {
      if (node.nodeType === Node.ELEMENT_NODE) applyInterfaceTextScale(node);
    })
  );
});
textScaleObserver.observe(document.body, { childList: true, subtree: true });

applyAppearance();

// ==========================================
// Search
// ==========================================
const searchInput = document.getElementById('search-input');
const statusMessage = document.getElementById('status-message');
const resultsContainer = document.getElementById('search-results');
const recommendationsStatus = document.getElementById('recommendations-status');
const recommendationsContainer = document.getElementById('recommendations-results');
const refreshRecommendationsBtn = document.getElementById('refresh-recommendations-btn');
const searchSuggestions = document.getElementById('search-suggestions');

let searchDebounce = null;
let searchAbortController = null;
let searchCurrentQuery = null;
let searchCurrentLimit = 0;
let searchLoadingMore = false;
let searchAllTracks = [];
let searchDisplayedCount = 0;
let activeSearchFilter = 'all';

// Filter pills
document.querySelectorAll('.filter-pill').forEach(pill => {
  pill.addEventListener('click', () => {
    document.querySelectorAll('.filter-pill').forEach(p => p.classList.remove('active'));
    pill.classList.add('active');
    activeSearchFilter = pill.getAttribute('data-filter');
    if (searchInput?.value.trim()) doSearch();
  });
});

// Search autocomplete
if (searchInput) {
  searchInput.addEventListener('input', () => {
    clearTimeout(searchDebounce);
    if (searchAbortController) searchAbortController.abort();
    const q = searchInput.value.trim();
    if (q.length < 2) {
      if (searchSuggestions) searchSuggestions.style.display = 'none';
      return;
    }
    searchDebounce = setTimeout(async () => {
      searchAbortController = new AbortController();
      try {
        const res = await fetch(`/api/search?q=${encodeURIComponent(q)}`, {
          signal: searchAbortController.signal,
        });
        const data = await res.json();
        if (data.tracks && data.tracks.length > 0 && searchSuggestions) {
          searchSuggestions.innerHTML = data.tracks
            .slice(0, 5)
            .map(
              (t, i) => `
            <div class="suggestion-item" data-idx="${i}">
              <i class="material-icons">search</i>
              <span>${escapeHtml(t.title)} — ${escapeHtml(t.artist)}</span>
            </div>
          `
            )
            .join('');
          searchSuggestions.style.display = 'block';
          searchSuggestions._tracks = data.tracks;
          searchSuggestions.querySelectorAll('.suggestion-item').forEach(item => {
            item.onclick = () => {
              const idx = Number(item.getAttribute('data-idx'));
              const tracks = searchSuggestions._tracks;
              currentPlaylist = tracks;
              currentTrackIndex = idx;
              playTrack(tracks[idx]);
              searchSuggestions.style.display = 'none';
            };
          });
        } else if (searchSuggestions) {
          searchSuggestions.style.display = 'none';
        }
      } catch (e) {
        /* ignore */
      }
    }, 800);
  });

  searchInput.addEventListener('keydown', e => {
    if (e.key === 'Enter') {
      e.preventDefault();
      doSearch();
    }
    if (e.key === 'Escape') {
      if (searchSuggestions) searchSuggestions.style.display = 'none';
    }
  });

  searchInput.addEventListener('focus', () => {
    renderSearchHistory();
  });

  // Skiper106 — Smooth caret (exact port of React component)
  const smoothCaret = document.getElementById('smooth-caret');
  const measureSpan = document.getElementById('skiper106-measure');
  const containerEl = document.getElementById('skiper106-container');
  if (smoothCaret && searchInput && measureSpan && containerEl) {
    const springConfig = { stiffness: 500, damping: 30, mass: 0.5 };
    let caretX = 0,
      caretOpacity = 0,
      targetX = 0,
      targetOpacity = 0;
    let velX = 0;

    const syncMeasureFont = () => {
      const styles = window.getComputedStyle(searchInput);
      measureSpan.style.font = `${styles.fontStyle} ${styles.fontWeight} ${styles.fontSize} ${styles.fontFamily}`;
      measureSpan.style.letterSpacing = styles.letterSpacing;
    };

    const getCaretPosition = () => {
      const idx = searchInput.selectionStart || 0;
      const text = searchInput.value.substring(0, idx);
      syncMeasureFont();
      measureSpan.textContent = text || '';
      // Caret is inside the container (padding: 12px), so add container padding
      const containerPadding = parseFloat(window.getComputedStyle(containerEl).paddingLeft) || 0;
      return text.length > 0 ? measureSpan.offsetWidth + containerPadding : containerPadding - 1;
    };

    const scrollCaretIntoView = pos => {
      const styles = window.getComputedStyle(searchInput);
      const paddingLeft = parseFloat(styles.paddingLeft) || 0;
      const paddingRight = parseFloat(styles.paddingRight) || 0;
      const maxScroll = Math.max(0, searchInput.scrollWidth - searchInput.clientWidth);
      const visibleRight = searchInput.scrollLeft + searchInput.clientWidth - paddingRight;
      const visibleLeft = searchInput.scrollLeft + paddingLeft;
      if (pos > visibleRight) {
        searchInput.scrollLeft = Math.min(pos - searchInput.clientWidth + paddingRight, maxScroll);
      } else if (pos < visibleLeft) {
        searchInput.scrollLeft = Math.max(0, pos - paddingLeft);
      }
    };

    const updateCaret = () => {
      const absWidth = getCaretPosition();
      scrollCaretIntoView(absWidth);
      const paddingLeft = parseFloat(window.getComputedStyle(searchInput).paddingLeft) || 0;
      const paddingRight = parseFloat(window.getComputedStyle(searchInput).paddingRight) || 0;
      const caretPos = absWidth - searchInput.scrollLeft;
      const minX = paddingLeft - 1;
      const maxX = searchInput.clientWidth - paddingRight;
      const isVisible = caretPos >= minX && caretPos <= maxX + 1;
      const hasSelection = (searchInput.selectionStart || 0) !== (searchInput.selectionEnd || 0);
      targetX = Math.min(caretPos, maxX);
      targetOpacity = isVisible && !hasSelection ? 1 : 0;
    };

    // Spring animation loop (like framer-motion spring)
    const springAnimate = () => {
      const dx = targetX - caretX;
      const dOpacity = targetOpacity - caretOpacity;
      // Spring physics
      const springForce = springConfig.stiffness * dx;
      const dampingForce = springConfig.damping * velX;
      velX += (springForce - dampingForce) * 0.016; // ~60fps
      caretX += velX * 0.016;
      // Smooth opacity
      caretOpacity += dOpacity * 0.15;
      // Apply
      smoothCaret.style.left = caretX + 'px';
      smoothCaret.style.opacity = Math.max(0, Math.min(1, caretOpacity));
      requestAnimationFrame(springAnimate);
    };
    springAnimate();

    searchInput.addEventListener('input', updateCaret);
    searchInput.addEventListener('click', updateCaret);
    searchInput.addEventListener('focus', updateCaret);
    searchInput.addEventListener('keyup', updateCaret);
    searchInput.addEventListener('blur', () => {
      targetOpacity = 0;
    });
    searchInput.addEventListener('scroll', updateCaret);

    // Resize observer
    if (typeof ResizeObserver !== 'undefined') {
      new ResizeObserver(updateCaret).observe(containerEl);
    }

    // Font load
    if (document.fonts) {
      document.fonts.addEventListener('loadingdone', updateCaret);
      document.fonts.ready.then(updateCaret);
    }
  }
}

document.addEventListener('click', e => {
  if (searchSuggestions && !searchSuggestions.contains(e.target) && e.target !== searchInput) {
    searchSuggestions.style.display = 'none';
  }
});

// Search history
function getSearchHistory() {
  try {
    return JSON.parse(localStorage.getItem('votify-search-history')) || [];
  } catch {
    return [];
  }
}

function addToSearchHistory(query) {
  let history = getSearchHistory();
  history = history.filter(h => h !== query);
  history.unshift(query);
  if (history.length > 8) history = history.slice(0, 8);
  localStorage.setItem('votify-search-history', JSON.stringify(history));
  renderSearchHistory();
}

function renderSearchHistory() {
  const container = document.getElementById('search-history');
  const items = document.getElementById('search-history-items');
  const history = getSearchHistory();
  if (!container || !items) return;
  if (history.length === 0 || (searchInput && searchInput.value.trim())) {
    container.style.display = 'none';
    return;
  }
  container.style.display = 'block';
  items.innerHTML = history
    .map(q => `<div class="search-history-chip">${escapeHtml(q)}</div>`)
    .join('');
  items.querySelectorAll('.search-history-chip').forEach(chip => {
    chip.onclick = () => {
      if (searchInput) searchInput.value = chip.textContent;
      doSearch();
    };
  });
}

safeClick('clear-search-history', () => {
  localStorage.removeItem('votify-search-history');
  renderSearchHistory();
});
renderSearchHistory();

// Do search
async function doSearch() {
  const query = searchInput ? searchInput.value.trim() : '';
  if (!query) return;
  searchAllTracks = [];
  searchCurrentQuery = null;
  searchCurrentLimit = 0;
  searchLoadingMore = false;
  searchDisplayedCount = 0;
  if (resultsContainer) resultsContainer.innerHTML = '';
  if (searchSuggestions) searchSuggestions.style.display = 'none';
  if (document.getElementById('search-history'))
    document.getElementById('search-history').style.display = 'none';
  setLoadingState(true, 'loader-search');

  try {
    let data = null;
    for (let attempt = 0; attempt < 3; attempt++) {
      try {
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), 30000);
        const res = await fetch(`/api/search?q=${encodeURIComponent(query)}&limit=50`, {
          signal: controller.signal,
        });
        clearTimeout(timeoutId);
        data = await res.json();
        if (!res.ok) throw new Error(data.error || `HTTP ${res.status}`);
        break;
      } catch (e) {
        if (attempt < 2) await new Promise(r => setTimeout(r, 1000 * (attempt + 1)));
        else throw e;
      }
    }
    const tracks = data?.tracks || [];
    if (statusMessage)
      statusMessage.innerText = `${translations[appSettings.lang]['search-found-prefix']}${tracks.length}`;
    addToSearchHistory(query);
    renderSearchResults(tracks, query);
  } catch (error) {
    if (statusMessage)
      statusMessage.innerText =
        error.name === 'AbortError' ? 'Search timed out' : `Error: ${error}`;
  } finally {
    setLoadingState(false);
  }
}

function extractAlbumsFromTracks(tracks) {
  const albumsMap = new Map();
  for (const track of tracks) {
    if (!track) continue;
    const albumName =
      track.album && track.album.toLowerCase() !== track.title.toLowerCase()
        ? track.album
        : `${track.artist || 'Исполнитель'} — Альбом`;
    const artistName = track.artist || 'Неизвестный исполнитель';
    const key = (albumName + '___' + artistName).toLowerCase();
    if (!albumsMap.has(key)) {
      albumsMap.set(key, {
        title: albumName,
        artist: artistName,
        cover: track.cover || '',
        type: 'Альбом',
        tracks: [track],
      });
    } else {
      albumsMap.get(key).tracks.push(track);
    }
  }
  return Array.from(albumsMap.values());
}

function renderSearchResults(tracks, query) {
  if (!resultsContainer) return;
  resultsContainer.innerHTML = '';
  const albums = extractAlbumsFromTracks(tracks);

  if (activeSearchFilter === 'albums') {
    if (!albums.length) {
      resultsContainer.innerHTML = '<p class="empty-msg">Альбомов не найдено</p>';
      return;
    }
    const albumsSection = document.createElement('div');
    albumsSection.className = 'artist-section';
    albumsSection.innerHTML = `
      <h3 class="artist-section-title"><i class="material-icons">album</i> Найденные альбомы (${albums.length})</h3>
      <div class="artist-albums-grid">
        ${albums
          .map(
            (rel, i) => `
          <div class="album-card" data-search-album-idx="${i}">
            <div class="album-cover-wrap">
              ${rel.cover ? `<img src="${escapeHtml(rel.cover)}" alt="${escapeHtml(rel.title)}">` : `<i class="material-icons" style="font-size:48px;color:var(--text-secondary)">album</i>`}
            </div>
            <div class="album-title">${escapeHtml(rel.title)}</div>
            <div class="album-meta-text">${escapeHtml(rel.artist)} • ${rel.tracks.length} ${rel.tracks.length === 1 ? 'трек' : 'трека'}</div>
          </div>
        `
          )
          .join('')}
      </div>
    `;
    resultsContainer.appendChild(albumsSection);
    albumsSection.querySelectorAll('[data-search-album-idx]').forEach(card => {
      card.onclick = () => {
        const idx = Number(card.getAttribute('data-search-album-idx'));
        if (albums[idx]) openAlbumPage(albums[idx]);
      };
    });
    return;
  }

  // If filter is "all", render Albums section first if any
  if (activeSearchFilter === 'all' && albums.length > 0) {
    const albumsSection = document.createElement('div');
    albumsSection.className = 'artist-section';
    albumsSection.style.marginBottom = '24px';
    albumsSection.innerHTML = `
      <h3 class="artist-section-title" style="margin-bottom:12px;"><i class="material-icons">album</i> Альбомы</h3>
      <div class="artist-albums-grid">
        ${albums
          .slice(0, 4)
          .map(
            (rel, i) => `
          <div class="album-card" data-search-album-idx="${i}">
            <div class="album-cover-wrap">
              ${rel.cover ? `<img src="${escapeHtml(rel.cover)}" alt="${escapeHtml(rel.title)}">` : `<i class="material-icons" style="font-size:48px;color:var(--text-secondary)">album</i>`}
            </div>
            <div class="album-title">${escapeHtml(rel.title)}</div>
            <div class="album-meta-text">${escapeHtml(rel.artist)} • ${rel.tracks.length} ${rel.tracks.length === 1 ? 'трек' : 'трека'}</div>
          </div>
        `
          )
          .join('')}
      </div>
    `;
    resultsContainer.appendChild(albumsSection);
    albumsSection.querySelectorAll('[data-search-album-idx]').forEach(card => {
      card.onclick = () => {
        const idx = Number(card.getAttribute('data-search-album-idx'));
        if (albums[idx]) openAlbumPage(albums[idx]);
      };
    });
  }

  // Store all tracks, render page 1
  searchAllTracks = [...tracks];
  searchCurrentQuery = query;
  searchDisplayedCount = 1; // current page number

  const tracksWrap = document.createElement('div');
  tracksWrap.className = 'results-list-tracks';
  tracksWrap.id = 'search-tracks-wrap';
  resultsContainer.appendChild(tracksWrap);

  // Pagination container
  const paginationWrap = document.createElement('div');
  paginationWrap.className = 'search-pagination';
  paginationWrap.id = 'search-pagination';
  resultsContainer.appendChild(paginationWrap);

  renderSearchPage(1);
}

function renderSearchPage(page) {
  const perPage = 12;
  const totalPages = Math.ceil(searchAllTracks.length / perPage);
  if (page < 1) page = 1;
  if (page > totalPages) page = totalPages;
  searchDisplayedCount = page;

  const start = (page - 1) * perPage;
  const pageTracks = searchAllTracks.slice(start, start + perPage);

  const tracksWrap = document.getElementById('search-tracks-wrap');
  if (tracksWrap) {
    renderTrackRows(tracksWrap, pageTracks, {
      showAddButton: true,
      playButtonClass: 'play-track-btn',
      addButtonClass: 'add-to-playlist-btn',
    });
    preloadTrackStreams(pageTracks);
  }

  // Render pagination buttons
  const paginationWrap = document.getElementById('search-pagination');
  if (!paginationWrap || totalPages <= 1) {
    if (paginationWrap) paginationWrap.innerHTML = '';
    return;
  }

  let buttons = '';

  // Prev button
  buttons += `<button class="search-page-btn ${page <= 1 ? 'disabled' : ''}" data-page="${page - 1}" ${page <= 1 ? 'disabled' : ''}>
    <i class="material-icons">chevron_left</i>
  </button>`;

  // Page numbers
  const maxVisible = 5;
  let startPage = Math.max(1, page - Math.floor(maxVisible / 2));
  let endPage = Math.min(totalPages, startPage + maxVisible - 1);
  if (endPage - startPage < maxVisible - 1) {
    startPage = Math.max(1, endPage - maxVisible + 1);
  }

  if (startPage > 1) {
    buttons += `<button class="search-page-btn" data-page="1">1</button>`;
    if (startPage > 2) buttons += `<span class="search-page-dots">...</span>`;
  }

  for (let i = startPage; i <= endPage; i++) {
    buttons += `<button class="search-page-btn ${i === page ? 'active' : ''}" data-page="${i}">${i}</button>`;
  }

  if (endPage < totalPages) {
    if (endPage < totalPages - 1) buttons += `<span class="search-page-dots">...</span>`;
    buttons += `<button class="search-page-btn" data-page="${totalPages}">${totalPages}</button>`;
  }

  // Next button
  buttons += `<button class="search-page-btn ${page >= totalPages ? 'disabled' : ''}" data-page="${page + 1}" ${page >= totalPages ? 'disabled' : ''}>
    <i class="material-icons">chevron_right</i>
  </button>`;

  paginationWrap.innerHTML = buttons;

  paginationWrap.querySelectorAll('.search-page-btn:not(.disabled)').forEach(btn => {
    btn.onclick = () => {
      const p = Number(btn.getAttribute('data-page'));
      if (p >= 1 && p <= totalPages) {
        renderSearchPage(p);
        // Scroll to top of results
        const mainContent = document.querySelector('.main-content');
        if (mainContent) mainContent.scrollTo({ top: 0, behavior: 'smooth' });
      }
    };
  });
}

// ==========================================
// Recommendations
// ==========================================
function renderRecTiles(container, tracks) {
  if (!container || !tracks || !tracks.length) {
    if (container) container.innerHTML = '<p class="empty-msg">No recommendations</p>';
    return;
  }
  container.className =
    container.id === 'for-you-results' ? 'rec-grid for-you-carousel' : 'rec-grid';
  container.innerHTML = tracks
    .map(
      (track, idx) => `
    <div class="rec-tile" data-idx="${idx}">
      <img class="rec-tile-cover" src="${escapeHtml(track.cover || '')}" alt="${escapeHtml(track.title || '')}" onerror="this.style.display='none'">
      <div class="rec-tile-title">${escapeHtml(track.title || 'Unknown')}</div>
      <div class="rec-tile-artist clickable-artist">${escapeHtml(track.artist || 'Unknown')}</div>
      <div class="rec-tile-play"><i class="material-icons">play_arrow</i></div>
    </div>
  `
    )
    .join('');
  container.querySelectorAll('.rec-tile').forEach(tile => {
    tile.querySelector('.clickable-artist')?.addEventListener('click', e => {
      e.stopPropagation();
      const idx = Number(tile.getAttribute('data-idx'));
      if (tracks[idx]?.artist) openArtistPage(tracks[idx].artist);
    });
    tile.onclick = () => {
      const idx = Number(tile.getAttribute('data-idx'));
      currentPlaylist = tracks;
      currentTrackIndex = idx;
      playTrack(tracks[idx]);
    };
  });
  preloadTrackStreams(tracks);
}

// Gather seeds from playlists + recent history
function shuffleArray(arr) {
  const a = arr.slice();
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

// Builds wave seeds (weighted artists + exact "artist title" seeds + an exclude
// list) from a pool of tracks, so "Моя волна" actually resembles the songs it's
// built from instead of a generic, unrelated mix.
function buildWaveSeeds(trackPool) {
  const validTracks = (trackPool || []).filter(t => t && t.artist);
  const artistCounts = new Map();
  for (const t of validTracks) {
    artistCounts.set(t.artist, (artistCounts.get(t.artist) || 0) + 1);
  }
  // Weighted random order: artists that show up more often in the source tracks
  // are more likely to be picked, but the shuffle keeps every wave feeling fresh.
  const weightedArtists = [];
  for (const [artist, count] of artistCounts.entries()) {
    for (let i = 0; i < count; i++) weightedArtists.push(artist);
  }
  const artists = [...new Set(shuffleArray(weightedArtists))].slice(0, 8);

  // A handful of exact "artist title" seeds keeps results anchored to songs you
  // actually have, instead of just "more from this artist".
  const trackSeeds = shuffleArray(validTracks)
    .slice(0, 6)
    .map(t => `${t.artist} ${t.title || ''}`.trim())
    .filter(Boolean);

  const excludeIds = [...new Set(validTracks.map(t => t.id).filter(Boolean))];

  return { artists, trackSeeds, excludeIds };
}

function gatherWaveSeeds() {
  const pool = [];
  for (const [name, tracks] of Object.entries(playlists)) {
    if (name === 'Избранное' || !Array.isArray(tracks)) continue;
    pool.push(...tracks);
  }
  pool.push(...(playlists['Избранное'] || []));
  try {
    const history = JSON.parse(localStorage.getItem('listeningHistory') || '[]');
    pool.push(...history.slice(0, 20));
  } catch (e) {
    /* ignore */
  }
  return buildWaveSeeds(pool);
}

async function fetchWaveTracks(waveSeeds, limit = 20) {
  const { artists, trackSeeds, excludeIds } = waveSeeds || {};
  if (!artists?.length && !trackSeeds?.length) return [];
  const params = new URLSearchParams();
  if (artists?.length) params.set('seeds', artists.join('|'));
  if (trackSeeds?.length) params.set('trackSeeds', trackSeeds.join('|'));
  if (excludeIds?.length) params.set('exclude', excludeIds.slice(0, 100).join(','));
  params.set('limit', String(limit));
  try {
    const resp = await fetch(`/api/custom-wave?${params.toString()}`);
    const data = await resp.json();
    return data.tracks || [];
  } catch {
    return [];
  }
}

let forYouTracks = [];
let forYouLoading = false;

async function loadForYouContent(forceReload = false) {
  const results = document.getElementById('for-you-results');
  const status = document.getElementById('for-you-status');
  if (!results || forYouLoading) return;
  if (forYouTracks.length && !forceReload) {
    renderRecTiles(results, forYouTracks);
    if (status) status.textContent = `${forYouTracks.length} рекомендаций для вас`;
    return;
  }

  const seeds = gatherWaveSeeds();
  if (!seeds.artists.length && !seeds.trackSeeds.length) {
    results.innerHTML =
      '<div class="empty-state">Добавьте треки в плейлист или послушайте несколько композиций — здесь появится персональная подборка.</div>';
    if (status) status.textContent = 'Пока недостаточно данных для рекомендаций';
    return;
  }

  forYouLoading = true;
  if (status) status.textContent = 'Подбираем треки по вашим плейлистам и истории…';
  results.innerHTML = '<div class="empty-state">Загрузка рекомендаций…</div>';
  try {
    let tracks = await fetchWaveTracks(seeds, 30);
    if (!tracks.length) tracks = await invoke('get_recommendations');
    forYouTracks = (tracks || []).slice(0, 30);
    if (forYouTracks.length) {
      renderRecTiles(results, forYouTracks);
      if (status) status.textContent = `${forYouTracks.length} рекомендаций для вас`;
    } else {
      results.innerHTML =
        '<div class="empty-state">Не удалось найти похожие треки. Попробуйте обновить подборку позже.</div>';
      if (status) status.textContent = 'Рекомендации пока недоступны';
    }
  } catch (error) {
    console.error('For You error:', error);
    results.innerHTML = '<div class="empty-state">Не удалось загрузить рекомендации</div>';
    if (status) status.textContent = 'Ошибка загрузки подборки';
  } finally {
    forYouLoading = false;
  }
}

safeClick('for-you-refresh', () => {
  forYouTracks = [];
  loadForYouContent(true);
});

function scrollForYou(direction) {
  const container = document.getElementById('for-you-results');
  if (!container) return;
  const distance = Math.max(320, Math.round(container.clientWidth * 0.82));
  container.scrollBy({ left: distance * direction, behavior: 'smooth' });
}

safeClick('for-you-prev', () => scrollForYou(-1));
safeClick('for-you-next', () => scrollForYou(1));

async function loadRecommendations(forceReload = false, { showLoading = true } = {}) {
  if (!recommendationsContainer) return;
  if (recommendationsLoaded && !forceReload && recommendationsContainer.innerHTML.trim()) return;
  recommendationsLoaded = true;
  recommendationsContainer.innerHTML = '';
  if (showLoading) setLoadingState(true, 'loader-recommendations');
  try {
    const seeds = gatherWaveSeeds();
    let tracks = await fetchWaveTracks(seeds, 20);
    // Fallback to default recommendations if no seeds
    if (!tracks.length) {
      tracks = await invoke('get_recommendations');
    }
    renderRecTiles(recommendationsContainer, tracks);
    if (recommendationsStatus) recommendationsStatus.innerText = '';
    const homePopular = document.getElementById('home-popular');
    if (homePopular && tracks.length) {
      renderTrackRows(homePopular, tracks.slice(0, 8), { showAddButton: true });
    }
  } catch (error) {
    recommendationsLoaded = false;
    if (recommendationsStatus) recommendationsStatus.innerText = `Error: ${error}`;
  } finally {
    if (showLoading) setLoadingState(false);
  }
}

if (refreshRecommendationsBtn) {
  refreshRecommendationsBtn.onclick = () => {
    recommendationsLoaded = false;
    loadRecommendations(true);
  };
}

// ==========================================
// Home Screen
// ==========================================
function loadHomeContent() {
  // Continue listening — from last played track
  const history = JSON.parse(localStorage.getItem('listeningHistory') || '[]');
  const continueContainer = document.getElementById('home-continue');
  const recentContainer = document.getElementById('home-recent');

  // Show last 6 tracks as "continue listening"
  if (continueContainer && history.length > 0) {
    renderTrackRows(continueContainer, history.slice(0, 6), { showAddButton: true });
  } else if (continueContainer) {
    continueContainer.innerHTML = '<div class="empty-state">Начните слушать музыку</div>';
  }

  // Show last 12 tracks as "recently played"
  if (recentContainer && history.length > 0) {
    renderTrackRows(recentContainer, history.slice(0, 12), { showAddButton: true });
  } else if (recentContainer) {
    recentContainer.innerHTML = '<div class="empty-state">Нет недавних треков</div>';
  }

  // Render Spotify-style Recent Artists Grid
  if (typeof renderRecentArtists === 'function') {
    renderRecentArtists();
  }

  // Personal recommendations live directly on Home, beneath recent artists.
  loadForYouContent();
}

// Home tiles
safeClick('tile-history', () => {
  const history = JSON.parse(localStorage.getItem('listeningHistory') || '[]');
  if (history.length === 0) {
    showToast('История пуста');
    return;
  }
  switchScreen('search-screen', 'nav-search-btn');
  if (statusMessage) statusMessage.innerText = `История: ${history.length} треков`;
  renderTrackRows(resultsContainer, history, { showAddButton: true });
});

safeClick('tile-liked', () => {
  const liked = playlists['Избранное'] || [];
  if (liked.length === 0) {
    showToast('Нет любимых треков');
    return;
  }
  switchScreen('folders-screen', 'nav-folders-btn');
  openPlaylist('Избранное');
});

// Home play wave button — custom wave from playlists + recent
safeClick('home-play-wave-btn', async () => {
  setLoadingState(true, 'loader-recommendations');
  try {
    const seeds = gatherWaveSeeds();
    let tracks = await fetchWaveTracks(seeds, 20);
    if (!tracks.length) tracks = await invoke('get_recommendations');
    if (tracks && tracks.length) {
      currentPlaylist = tracks;
      currentTrackIndex = 0;
      playTrack(tracks[0]);
      if (recommendationsContainer) {
        renderRecTiles(recommendationsContainer, tracks);
        recommendationsLoaded = true;
      }
    }
  } catch (e) {
    console.error('Wave error:', e);
  }
  setLoadingState(false);
});

// Skiper25 — Music toggle button (exact port of React component)
const heroToggle = document.getElementById('home-play-wave-btn');
const skiper25Bars = document.querySelectorAll('#skiper25-bars .skiper25-bar');
let skiper25Interval = null;

function skiper25RandomHeights() {
  skiper25Bars.forEach(bar => {
    const h = Math.random() * 0.8 + 0.2; // 0.2 to 1.0
    bar.style.height = Math.max(4, h * 14) + 'px';
  });
}

function skiper25Collapse() {
  skiper25Bars.forEach(bar => {
    bar.style.height = '2px';
  });
}

on('state:isPlaying', playing => {
  if (heroToggle) heroToggle.classList.toggle('playing', playing);
  if (playing) {
    skiper25Interval = setInterval(skiper25RandomHeights, 100);
    skiper25RandomHeights();
  } else {
    if (skiper25Interval) {
      clearInterval(skiper25Interval);
      skiper25Interval = null;
    }
    skiper25Collapse();
  }
});

// ==========================================
// Audio Player
// ==========================================
const playBtn = document.getElementById('play-btn');
const playerTitle = document.getElementById('player-track-title');
const playerArtist = document.getElementById('player-track-artist');
if (playerArtist) {
  playerArtist.addEventListener('click', e => {
    e.stopPropagation();
    if (state.currentTrack?.artist) openArtistPage(state.currentTrack.artist);
  });
}
const progressBar = document.getElementById('progress-bar');
const volumeBar = document.getElementById('volume-bar');
const currentTimeEl = document.getElementById('current-time');
const totalTimeEl = document.getElementById('total-time');

let currentTrackCover = '';
let playRetryCount = 0;
const MAX_PLAY_RETRIES = 3;

function addToListeningHistory(track) {
  if (!track || appSettings.saveHistory === false) return;
  let history = [];
  try {
    history = JSON.parse(localStorage.getItem('listeningHistory') || '[]');
  } catch {
    history = [];
  }
  history = history.filter(t => t.id !== track.id);
  history.unshift({ id: track.id, title: track.title, artist: track.artist, cover: track.cover });
  const limit = Math.max(10, Math.min(200, Number(appSettings.historyLimit) || 50));
  if (history.length > limit) history = history.slice(0, limit);
  localStorage.setItem('listeningHistory', JSON.stringify(history));
  scheduleCloudPush();
}

async function playTrack(track) {
  if (!track) return;
  isChangingTrack = true;
  let didStartPlayback = false;
  playRetryCount = 0;
  preFadeVolume = null;
  gaplessPreloadedFor = null;
  state.currentTrack = track;
  emit('state:currentTrack', track);
  if (typeof recordRecentArtist === 'function') recordRecentArtist(track);
  if (playerTitle) playerTitle.innerText = track.title;
  if (playerArtist) playerArtist.innerText = track.artist || 'Unknown';
  currentTrackCover = track.cover || '';

  // Unified premium placeholder for all covers
  applyAllCoverPlaceholders(track);
  if (typeof applyCoverSettings === 'function') applyCoverSettings();

  // Force-stop any previous playback before switching source
  audio.pause();
  audio.removeAttribute('src'); // fully detach old source
  audio.load(); // reset the element
  audio.currentTime = 0;
  // Revoke old blob URL if any
  if (audio.src && audio.src.startsWith('blob:')) {
    URL.revokeObjectURL(audio.src);
  }
  if (playBtn) playBtn.innerHTML = '<i class="material-icons">hourglass_top</i>';

  try {
    // Stream the audio URL directly (server proxy handles the actual stream)
    const streamUrl =
      track.isLocal && track.localUrl
        ? track.localUrl
        : `/api/stream?id=${encodeURIComponent(track.id)}`;
    audio.src = streamUrl;
    audio.load(); // ensure the new source is picked up immediately
    await audio.play();
    didStartPlayback = true;
    if (playBtn) playBtn.innerHTML = '<i class="material-icons">pause</i>';
  } catch (e) {
    console.warn('[playTrack] failed:', e.message);
    if (playBtn) playBtn.innerHTML = '<i class="material-icons">play_arrow</i>';
  } finally {
    isChangingTrack = false;
    if (didStartPlayback) syncDiscordPresence();
    else clearDiscordPresence();
  }

  localStorage.setItem('votify-last-track', JSON.stringify(track));
  localStorage.setItem('votify-last-index', currentTrackIndex);
  localStorage.setItem('votify-last-playlist', JSON.stringify(currentPlaylist));
  addToListeningHistory(track);

  document.querySelectorAll('.track-item').forEach(el => el.classList.remove('playing'));
  // Mark current playing track
  document
    .querySelectorAll(`.track-item[data-track-id="${track.id}"]`)
    .forEach(el => el.classList.add('playing'));

  notifyTrackChange(track);

  // Auto-load lyrics
  if (appSettings.autoLyrics !== false) {
    loadLyricsForTrack(track.title, track.artist);
  }
}

audio.addEventListener('error', () => {
  if (playBtn) playBtn.innerHTML = '<i class="material-icons">play_arrow</i>';
  if (!isChangingTrack) clearDiscordPresence();
});
audio.addEventListener('playing', () => {
  if (playBtn) playBtn.innerHTML = '<i class="material-icons">pause</i>';
});
audio.addEventListener('pause', () => {
  if (playBtn) playBtn.innerHTML = '<i class="material-icons">play_arrow</i>';
});

if (playBtn) {
  playBtn.onclick = () => {
    if (!audio.src) return;
    if (audio.paused) {
      audio.play().catch(() => {});
      playBtn.innerHTML = '<i class="material-icons">pause</i>';
    } else {
      audio.pause();
      playBtn.innerHTML = '<i class="material-icons">play_arrow</i>';
    }
  };
}

// Shuffle
const shuffleBtn = document.getElementById('shuffle-btn');
function toggleShuffle() {
  isShuffle = !isShuffle;
  shuffleHistory = [];
  if (shuffleBtn) shuffleBtn.classList.toggle('active', isShuffle);
}
if (shuffleBtn) shuffleBtn.onclick = toggleShuffle;

// Prev/Next
function playNextTrack() {
  if (!currentPlaylist.length) return;
  if (isShuffle) {
    let nextIdx;
    do {
      nextIdx = Math.floor(Math.random() * currentPlaylist.length);
    } while (nextIdx === currentTrackIndex && currentPlaylist.length > 1);
    currentTrackIndex = nextIdx;
  } else {
    currentTrackIndex = (currentTrackIndex + 1) % currentPlaylist.length;
  }
  playTrack(currentPlaylist[currentTrackIndex]);
}

function playPrevTrack() {
  if (!currentPlaylist.length) return;
  if (audio.currentTime > 3) {
    audio.currentTime = 0;
    return;
  }
  currentTrackIndex = (currentTrackIndex - 1 + currentPlaylist.length) % currentPlaylist.length;
  playTrack(currentPlaylist[currentTrackIndex]);
}

const prevBtn = document.getElementById('prev-btn');
const nextBtn = document.getElementById('next-btn');
if (prevBtn) prevBtn.onclick = playPrevTrack;
if (nextBtn) nextBtn.onclick = playNextTrack;

// Repeat
let isRepeat = false;
const repeatBtn = document.getElementById('repeat-btn');
if (repeatBtn) {
  repeatBtn.onclick = () => {
    isRepeat = !isRepeat;
    audio.loop = isRepeat;
    repeatBtn.classList.toggle('active', isRepeat);
  };
}

audio.onended = () => {
  if (!isRepeat) playNextTrack();
};

// Time update
audio.ontimeupdate = () => {
  if (isNaN(audio.duration)) return;
  if (abLoopActive && abLoopStart != null && abLoopEnd != null && audio.currentTime >= abLoopEnd) {
    audio.currentTime = abLoopStart;
  }
  const pct = (audio.currentTime / audio.duration) * 100;
  if (progressBar) {
    progressBar.value = pct;
    progressBar.style.setProperty('--r', pct + '%');
  }
  if (currentTimeEl) currentTimeEl.innerText = formatTime(audio.currentTime);
  if (appSettings.resumePosition && state.currentTrack?.id && audio.currentTime > 2) {
    localStorage.setItem(`votify-position-${state.currentTrack.id}`, String(audio.currentTime));
  }
  updateLyricsLine();
};

audio.onloadedmetadata = () => {
  if (totalTimeEl) totalTimeEl.innerText = formatTime(audio.duration);
  if (appSettings.resumePosition && state.currentTrack?.id) {
    const savedPosition = Number(localStorage.getItem(`votify-position-${state.currentTrack.id}`));
    if (Number.isFinite(savedPosition) && savedPosition > 2 && savedPosition < audio.duration - 3)
      audio.currentTime = savedPosition;
  }
  if (!isChangingTrack) syncDiscordPresence(100);
};

audio.addEventListener('seeked', () => {
  if (!isChangingTrack) syncDiscordPresence(200);
});
audio.addEventListener('ratechange', () => {
  if (!isChangingTrack) syncDiscordPresence();
});

// ==========================================
// BAR TIMELINE — Bottom Player
// ==========================================
const barTimelineTrack = document.getElementById('bar-timeline-track');
const barTimelineThumb = document.getElementById('bar-timeline-thumb');
const barTlBg = document.getElementById('bar-tl-bg');
const barTlActive = document.getElementById('bar-tl-active');

function drawBarTimeline(pct) {
  if (!barTimelineTrack || !barTlBg || !barTlActive) return;
  const w = barTimelineTrack.clientWidth || 300;
  if (appSettings.playerSliderType === 'wave') {
    drawWaveTimelinePaths(barTlBg, barTlActive, w, 20, pct);
  } else {
    restoreRegularTimelinePaths(barTlBg, barTlActive);
    const h = 4;
    barTlBg.setAttribute('d', `M 0 0 L ${w} 0 L ${w} ${h} L 0 ${h} Z`);
    const aw = (pct / 100) * w;
    barTlActive.setAttribute('d', `M 0 0 L ${aw} 0 L ${aw} ${h} L 0 ${h} Z`);
  }
}

function barTlSeek(e) {
  if (!barTimelineTrack || !audio.src || isNaN(audio.duration)) return;
  const rect = barTimelineTrack.getBoundingClientRect();
  let clientX = e.type.includes('touch') ? e.touches[0].clientX : e.clientX;
  let pct = Math.max(0, Math.min(1, (clientX - rect.left) / rect.width));
  audio.currentTime = pct * audio.duration;
}

if (barTimelineTrack) {
  let barDragging = false;
  barTimelineTrack.addEventListener('mousedown', e => {
    barDragging = true;
    barTimelineTrack.classList.add('dragging');
    barTlSeek(e);
  });
  barTimelineTrack.addEventListener(
    'touchstart',
    e => {
      barDragging = true;
      barTimelineTrack.classList.add('dragging');
      barTlSeek(e);
    },
    { passive: false }
  );
  document.addEventListener('mousemove', e => {
    if (barDragging) barTlSeek(e);
  });
  document.addEventListener(
    'touchmove',
    e => {
      if (barDragging) barTlSeek(e);
    },
    { passive: false }
  );
  document.addEventListener('mouseup', () => {
    barDragging = false;
    barTimelineTrack?.classList.remove('dragging');
  });
  document.addEventListener('touchend', () => {
    barDragging = false;
    barTimelineTrack?.classList.remove('dragging');
  });
  window.addEventListener('resize', () => {
    if (state.duration > 0) drawBarTimeline((audio.currentTime / state.duration) * 100);
  });
  drawBarTimeline(0);
}

// Update bar timeline progress
on('state:currentTime', time => {
  if (state.duration > 0) {
    const pct = (time / state.duration) * 100;
    barTimelineThumb.style.left = pct + '%';
    drawBarTimeline(pct);
    if (currentTimeEl) currentTimeEl.textContent = formatTime(time);
    if (totalTimeEl) totalTimeEl.textContent = formatTime(state.duration);
  }
});

on('state:currentTrack', () => {
  drawBarTimeline(0);
});

// Morphing sidebar controls — apply to settings panel
const morphSettings = document.getElementById('morph-settings');
if (morphSettings) {
  morphSettings.querySelectorAll('.morph-toggle').forEach(btn => {
    btn.addEventListener('click', () => {
      btn.classList.toggle('active');
    });
  });
  morphSettings.querySelectorAll('.morph-slider').forEach(slider => {
    const valueEl = slider.closest('.morph-slider-wrap')?.querySelector('.morph-slider-value');
    if (valueEl) {
      slider.addEventListener('input', () => {
        valueEl.textContent = slider.value;
      });
    }
  });
}

// ==========================================
// FULLSCREEN PLAYER
// ==========================================
const fullscreenPlayer = document.getElementById('fullscreen-player');
const fsOpenBtn = document.getElementById('open-fullscreen-btn');
const fsCloseBtn = document.getElementById('fs-close-btn');
const fsMenuBtn = document.getElementById('fs-menu-btn');
const fsPlayBtn = document.getElementById('fs-play');
const fsPrevBtn = document.getElementById('fs-prev');
const fsNextBtn = document.getElementById('fs-next');
const fsShuffleBtn = document.getElementById('fs-shuffle');
const fsRepeatBtn = document.getElementById('fs-repeat');
const fsProgress = document.getElementById('fs-progress');
const fsTimeline = document.getElementById('fs-timeline');
const fsTimelineTrack = document.getElementById('fs-timeline-track');
const fsTimelineThumb = document.getElementById('fs-timeline-thumb');
const tlBgCurve = document.getElementById('tl-bg-curve');
const tlActiveCurve = document.getElementById('tl-active-curve');
const fsVolume = document.getElementById('fs-volume');
const fsCover = document.getElementById('fs-cover');
const fsTitle = document.getElementById('fs-title');
const fsArtist = document.getElementById('fs-artist');
const fsCurrent = document.getElementById('fs-current');
const fsTotal = document.getElementById('fs-total');
const fsPlayerBg = document.getElementById('fs-player-bg');
const fsEqBtn = document.getElementById('fs-eq-btn');
const fsLyricsBtn = document.getElementById('fs-lyrics-btn');
const fsSpeedBtn = document.getElementById('fs-speed-btn');
const fsSpeedValue = document.getElementById('fs-speed-value');
const fsPlayerEq = document.getElementById('fs-player-eq');
const fsPlayerLyrics = document.getElementById('fs-player-lyrics');

function openFullscreenPlayer() {
  if (!fullscreenPlayer) return;
  fullscreenPlayer.classList.add('open');
  document.body.style.overflow = 'hidden';
  if (typeof updateFsVolumeProgress === 'function') updateFsVolumeProgress();
  if (state.currentTrack) {
    loadFsLyrics(state.currentTrack.title, state.currentTrack.artist);
  }
}
function closeFullscreenPlayer() {
  if (!fullscreenPlayer) return;
  fullscreenPlayer.classList.remove('open');
  document.body.style.overflow = '';
}

if (fsOpenBtn) fsOpenBtn.addEventListener('click', openFullscreenPlayer);
const fsRightBtn = document.getElementById('right-player-fullscreen');
if (fsRightBtn) fsRightBtn.addEventListener('click', openFullscreenPlayer);

if (fsCloseBtn) {
  const doClose = e => {
    if (e) {
      e.preventDefault();
      e.stopPropagation();
    }
    closeFullscreenPlayer();
  };
  fsCloseBtn.addEventListener('click', doClose);
  fsCloseBtn.addEventListener('pointerdown', doClose);
  fsCloseBtn.addEventListener('touchstart', doClose);
}
if (fsMenuBtn)
  fsMenuBtn.addEventListener('click', () => {
    const open = fsPlayerEq?.style.display !== 'none' || fsPlayerLyrics?.style.display !== 'none';
    if (open) {
      if (fsPlayerEq) fsPlayerEq.style.display = 'none';
      if (fsPlayerLyrics) fsPlayerLyrics.style.display = 'none';
      fsEqBtn?.classList.remove('active');
      fsLyricsBtn?.classList.remove('active');
    } else {
      fsPlayerLyrics.style.display = 'block';
      fsLyricsBtn?.classList.add('active');
      if (state.currentTrack) loadFsLyrics(state.currentTrack.title, state.currentTrack.artist);
    }
  });
if (fsPlayBtn)
  fsPlayBtn.addEventListener('click', () => {
    if (audio.paused) audio.play().catch(() => {});
    else audio.pause();
  });
if (fsPrevBtn) fsPrevBtn.addEventListener('click', playPrevTrack);
if (fsNextBtn) fsNextBtn.addEventListener('click', playNextTrack);
if (fsShuffleBtn)
  fsShuffleBtn.addEventListener('click', () => fsShuffleBtn.classList.toggle('active'));
if (fsRepeatBtn)
  fsRepeatBtn.addEventListener('click', () => {
    if (abLoopActive) {
      clearAbLoop();
      showToast('Зацикливание фрагмента выключено');
      return;
    }
    abLoopStart = null;
    abLoopEnd = null;
    abLoopSelecting = true;
    fsRepeatBtn.classList.add('selecting');
    fsRepeatBtn.setAttribute('title', 'Выберите начало фрагмента на шкале');
    showToast('Нажмите на шкалу, чтобы выбрать начало фрагмента');
  });
const fsPlaybackRates = [0.75, 1, 1.25, 1.5, 2];
function syncFsSpeed() {
  if (fsSpeedValue) fsSpeedValue.textContent = `${Number(audio.playbackRate.toFixed(2))}×`;
}
syncFsSpeed();
if (fsSpeedBtn)
  fsSpeedBtn.addEventListener('click', () => {
    const current = fsPlaybackRates.findIndex(rate => Math.abs(rate - audio.playbackRate) < 0.01);
    const next = fsPlaybackRates[(current + 1) % fsPlaybackRates.length];
    audio.playbackRate = next;
    appSettings.playbackRate = next;
    if (playbackRateSelect) playbackRateSelect.value = String(next);
    syncFsSpeed();
    saveSettings();
  });

const fsProgressEl = document.getElementById('fs-progress');
if (fsProgressEl) {
  const seekFs = e => {
    if (state.duration > 0) {
      const val = Number(e.target.value);
      audio.currentTime = (val / 100) * state.duration;
      fsProgressEl.style.setProperty('--r', val + '%');
    }
  };
  fsProgressEl.addEventListener('input', seekFs);
  fsProgressEl.addEventListener('change', seekFs);
}
function updateFsVolumeProgress() {
  if (!fsVolume) return;
  const pct = Math.round(audio.volume * 100);
  fsVolume.value = pct;
  fsVolume.style.setProperty('--r', pct + '%');
}

if (fsVolume) {
  updateFsVolumeProgress();
  fsVolume.addEventListener('input', e => {
    const v = Number(e.target.value) / 100;
    audio.volume = v;
    fsVolume.style.setProperty('--r', v * 100 + '%');
    syncVolumeBars();
  });
}

// Cover action buttons
const fsFavBtn = document.getElementById('fs-fav-btn');
if (fsFavBtn) {
  fsFavBtn.addEventListener('click', () => {
    if (state.currentTrack) {
      const added = toggleFavorite(state.currentTrack);
      fsFavBtn.classList.toggle('active', added);
      const icon = fsFavBtn.querySelector('.material-icons');
      if (icon) icon.textContent = added ? 'favorite' : 'favorite_border';
    }
  });
}

const fsLrcBadge = document.getElementById('fs-lrc-badge');
if (fsLrcBadge) {
  fsLrcBadge.addEventListener('click', () => {
    if (state.currentTrack) {
      loadFsLyrics(state.currentTrack.title, state.currentTrack.artist);
      showToast('Обновление текста...');
    }
  });
}

const fsDislikeBtn = document.getElementById('fs-dislike-btn');
if (fsDislikeBtn) {
  fsDislikeBtn.addEventListener('click', () => {
    showToast('Трек пропущен');
    playNextTrack();
  });
}

// Update fullscreen player with track info
on('state:currentTrack', track => {
  if (!track) { applyAllCoverPlaceholders(''); return; }
  setCoverState('fs-cover', 'fs-cover-fallback', track.cover || '', '.fs-cover-container');
  if (fsTitle) fsTitle.textContent = track.title || '—';
  if (fsArtist) fsArtist.textContent = track.artist || '—';

  if (fsFavBtn) {
    const isFav = state.favorites ? state.favorites.some(f => f.id === track.id) : false;
    fsFavBtn.classList.toggle('active', isFav);
    const icon = fsFavBtn.querySelector('.material-icons');
    if (icon) icon.textContent = isFav ? 'favorite' : 'favorite_border';
  }

  // Extract color for fullscreen background gradient — now lava-lamp dynamic
  const fsGradientBg = document.getElementById('fs-gradient-bg');
  if (track.cover) {
    extractDominantColor(track.cover).then(color => {
      if (!color) { applyFsLavaLampColors(null); return; }
      const bright = color._bright || { r: Math.round(color.r/0.35), g: Math.round(color.g/0.35), b: Math.round(color.b/0.35) };
      applyFsLavaLampColors(bright);
    });
  } else {
    applyFsLavaLampColors(null);
  }
  if (fullscreenPlayer && fullscreenPlayer.classList.contains('open')) {
    loadFsLyrics(track.title, track.artist);
  }
});

on('state:isPlaying', playing => {
  if (fsPlayBtn) {
    fsPlayBtn.querySelector('.material-icons').textContent = playing ? 'pause' : 'play_arrow';
  }
});

on('state:currentTime', time => {
  if (state.duration > 0) {
    const pct = (time / state.duration) * 100;
    if (fsTimelineThumb) fsTimelineThumb.style.left = pct + '%';
    if (typeof drawTimelineCurve === 'function') drawTimelineCurve(pct);
    const fsProg = document.getElementById('fs-progress');
    if (fsProg) {
      fsProg.value = Math.round(pct);
      fsProg.style.setProperty('--r', pct + '%');
    }
  }
  if (fsCurrent) fsCurrent.textContent = formatTime(time);
  if (fsTotal) fsTotal.textContent = formatTime(state.duration);
  updateFullscreenLyrics(time);
});

// Custom SVG Timeline
let tlDragging = false;
function drawTimelineCurve(pct) {
  if (!fsTimelineTrack || !tlBgCurve || !tlActiveCurve) return;
  const w = fsTimelineTrack.clientWidth || 400;
  const h = fsTimelineTrack.clientHeight || 36;
  if (appSettings.playerSliderType === 'wave') {
    drawWaveTimelinePaths(tlBgCurve, tlActiveCurve, w, Math.min(h, 24), pct);
    return;
  }
  restoreRegularTimelinePaths(tlBgCurve, tlActiveCurve);
  const mid = h * 0.5;
  const amp = 6;
  // Generate a smooth wave path
  let d = `M 0 ${mid}`;
  const steps = 60;
  for (let i = 0; i <= steps; i++) {
    const x = (i / steps) * w;
    const t = i / steps;
    const y = mid + Math.sin(t * Math.PI * 4) * amp * Math.sin(t * Math.PI);
    d += ` L ${x.toFixed(1)} ${y.toFixed(1)}`;
  }
  tlBgCurve.setAttribute('d', d);
  // Active curve — clipped at pct%
  const clipX = (pct / 100) * w;
  let ad = `M 0 ${mid}`;
  for (let i = 0; i <= steps; i++) {
    const x = (i / steps) * w;
    if (x > clipX) break;
    const t = i / steps;
    const y = mid + Math.sin(t * Math.PI * 4) * amp * Math.sin(t * Math.PI);
    ad += ` L ${x.toFixed(1)} ${y.toFixed(1)}`;
  }
  ad += ` L ${clipX.toFixed(1)} ${mid} Z`;
  tlActiveCurve.setAttribute('d', ad);
}
function tlSeekFromEvent(e) {
  const rect = fsTimelineTrack.getBoundingClientRect();
  let clientX = e.type.includes('touch') ? e.touches[0].clientX : e.clientX;
  let pct = ((clientX - rect.left) / rect.width) * 100;
  pct = Math.max(0, Math.min(100, pct));
  if (state.duration > 0) {
    if (abLoopSelecting) {
      const point = (pct / 100) * state.duration;
      if (abLoopStart == null) {
        abLoopStart = point;
        audio.currentTime = point;
        fsRepeatBtn?.setAttribute('title', 'Выберите конец фрагмента на шкале');
        showToast('Теперь нажмите на шкалу, чтобы выбрать конец');
      } else {
        abLoopEnd = point;
        if (abLoopEnd < abLoopStart) [abLoopStart, abLoopEnd] = [abLoopEnd, abLoopStart];
        if (abLoopEnd - abLoopStart < 0.5) abLoopEnd = Math.min(state.duration, abLoopStart + 0.5);
        abLoopSelecting = false;
        abLoopActive = true;
        rightPlayerRepeatBtn?.classList.remove('selecting');
        rightPlayerRepeatBtn?.classList.add('active');
        rightPlayerRepeatBtn?.setAttribute('title', 'Выключить зацикливание фрагмента');
        fsRepeatBtn?.classList.remove('selecting');
        fsRepeatBtn?.classList.add('active');
        fsRepeatBtn?.setAttribute('title', 'Выключить зацикливание фрагмента');
        audio.currentTime = abLoopStart;
        updateAbLoopMarkers();
        showToast('Фрагмент зациклен');
      }
      updateAbLoopMarkers();
      return;
    }
    audio.currentTime = (pct / 100) * state.duration;
  }
  fsTimelineThumb.style.left = pct + '%';
  drawTimelineCurve(pct);
}
if (fsTimelineTrack) {
  fsTimelineTrack.addEventListener('mousedown', e => {
    const wasSelecting = abLoopSelecting;
    tlDragging = !wasSelecting;
    fsTimelineTrack.classList.add('dragging');
    tlSeekFromEvent(e);
  });
  fsTimelineTrack.addEventListener(
    'touchstart',
    e => {
      const wasSelecting = abLoopSelecting;
      tlDragging = !wasSelecting;
      fsTimelineTrack.classList.add('dragging');
      tlSeekFromEvent(e);
    },
    { passive: false }
  );
  document.addEventListener('mousemove', e => {
    if (tlDragging && !abLoopSelecting) tlSeekFromEvent(e);
  });
  document.addEventListener(
    'touchmove',
    e => {
      if (tlDragging && !abLoopSelecting) tlSeekFromEvent(e);
    },
    { passive: false }
  );
  document.addEventListener('mouseup', () => {
    tlDragging = false;
    fsTimelineTrack?.classList.remove('dragging');
  });
  document.addEventListener('touchend', () => {
    tlDragging = false;
    fsTimelineTrack?.classList.remove('dragging');
  });
  window.addEventListener('resize', () => {
    if (state.duration > 0) drawTimelineCurve((audio.currentTime / state.duration) * 100);
  });
  // Initial draw
  drawTimelineCurve(0);
}

// EQ & Lyrics toggles in fullscreen
let fsLyricsData = [];
if (fsEqBtn && fsPlayerEq) {
  fsEqBtn.addEventListener('click', () => {
    const open = fsPlayerEq.style.display === 'none';
    fsPlayerEq.style.display = open ? 'block' : 'none';
    fsEqBtn.classList.toggle('active', open);
    if (open) requestAnimationFrame(() => window.dispatchEvent(new Event('resize')));
    if (open && fsPlayerLyrics) {
      fsPlayerLyrics.style.display = 'none';
      fsLyricsBtn?.classList.remove('active');
    }
  });
}
if (fsLyricsBtn && fsPlayerLyrics) {
  fsLyricsBtn.addEventListener('click', () => {
    const open = fsPlayerLyrics.style.display === 'none';
    fsPlayerLyrics.style.display = open ? 'block' : 'none';
    fsLyricsBtn.classList.toggle('active', open);
    if (open && fsPlayerEq) {
      fsPlayerEq.style.display = 'none';
      fsEqBtn?.classList.remove('active');
    }
    if (open && state.currentTrack)
      loadFsLyrics(state.currentTrack.title, state.currentTrack.artist);
  });
}
async function loadFsLyrics(title, artist) {
  fsLyricsData = [];
  const body = document.getElementById('fs-lyrics-body');
  if (body) body.innerHTML = '<div class="lyrics-placeholder">Загрузка...</div>';
  if (!title) return;
  try {
    const data = await fetchLyricsData(title, artist);
    if (!data) {
      if (body) body.innerHTML = '<div class="lyrics-placeholder">Нет текста</div>';
      return;
    }
    const lrc = data.syncedLyrics || data.plainLyrics || '';
    if (!lrc) {
      if (body) body.innerHTML = '<div class="lyrics-placeholder">Нет текста</div>';
      return;
    }
    if (!data.syncedLyrics) {
      fsLyricsData = [];
      if (body)
        body.innerHTML = lrc
          .split('\n')
          .map(l => `<div class="lyrics-line">${l.trim() || '&nbsp;'}</div>`)
          .join('');
      return;
    }
    fsLyricsData = lrc
      .split('\n')
      .map(line => {
        const m = line.match(/^\[(\d+):(\d+)(?:\.(\d+))?\]\s*(.*)/);
        if (m) {
          const ms = m[3] || '0';
          return {
            time: parseInt(m[1]) * 60 + parseInt(m[2]) + parseInt(ms) / Math.pow(10, ms.length),
            text: m[4].trim(),
          };
        }
        return null;
      })
      .filter(Boolean);
    if (body)
      body.innerHTML = fsLyricsData
        .map((l, i) => `<div class="lyrics-line" data-idx="${i}">${l.text || '&nbsp;'}</div>`)
        .join('');
  } catch {
    if (body) body.innerHTML = '<div class="lyrics-placeholder">Нет текста</div>';
  }
}

// ==========================================
// Mute Button
// ==========================================
const muteBtn = document.getElementById('mute-btn');
let savedVolume = 0.8;

if (muteBtn) {
  muteBtn.onclick = () => {
    const volIcon = document.getElementById('skiper99-main-vol');
    if (audio.volume > 0) {
      savedVolume = audio.volume;
      audio.volume = 0;
      if (volIcon) volIcon.setAttribute('data-muted', 'true');
      if (volumeBar) volumeBar.value = 0;
    } else {
      audio.volume = savedVolume;
      if (volIcon) volIcon.setAttribute('data-muted', 'false');
      if (volumeBar) volumeBar.value = Math.round(savedVolume * 100);
    }
    syncVolumeBars();
  };
}

if (volumeBar) {
  volumeBar.oninput = e => {
    const v = Number(e.target.value) / 100;
    audio.volume = v;
    const volIcon = document.getElementById('skiper99-main-vol');
    if (volIcon) volIcon.setAttribute('data-muted', v === 0 ? 'true' : 'false');
    syncVolumeBars();
  };
}

function syncVolumeBars() {
  const v = audio.volume;
  if (typeof updateFsVolumeProgress === 'function') updateFsVolumeProgress();
  // Sync floating island volume
  if (typeof window._fiSyncVolume === 'function') window._fiSyncVolume();
}

function hideSplash() {
  if (startupSplashFailsafe) {
    clearTimeout(startupSplashFailsafe);
    startupSplashFailsafe = null;
  }
  const splash = document.getElementById('splash-screen');
  if (splash) {
    if (splash.dataset.hidden) return;
    splash.dataset.hidden = 'true';
    splash.style.opacity = '0';
    splash.style.transition = 'opacity 0.4s ease';
    setTimeout(() => {
      splash.style.display = 'none';
    }, 400);
  }
}

// Auto-updater like Discord — checks GitHub on each app start
(function setupUpdaterUI(){
  const banner = document.getElementById('update-banner');
  const text = document.getElementById('update-banner-text');
  const actionBtn = document.getElementById('update-banner-action');
  const closeBtn = document.getElementById('update-banner-close');
  const progressWrap = document.getElementById('update-banner-progress');
  const progressBar = document.getElementById('update-banner-progress-bar');
  let downloaded = false;
  let availableInfo = null;

  function showBanner(msg, actionLabel = 'Обновить') {
    if (!banner) return;
    if (text) text.textContent = msg;
    if (actionBtn) actionBtn.textContent = actionLabel;
    banner.style.display = 'flex';
  }
  function hideBanner() {
    if (banner) banner.style.display = 'none';
  }

  if (closeBtn) closeBtn.addEventListener('click', hideBanner);

  if (actionBtn) actionBtn.addEventListener('click', () => {
    if (downloaded) {
      if (window.electronAPI?.installUpdate) {
        window.electronAPI.installUpdate();
      }
    } else {
      if (window.electronAPI?.checkForUpdates) {
        showBanner('Проверка обновлений...', '...');
        window.electronAPI.checkForUpdates();
      } else {
        // Fallback: open GitHub releases
        window.open('https://github.com/exieeez/Votify/releases', '_blank');
      }
    }
  });

  // Electron updater events
  if (window.electronAPI) {
    window.electronAPI.onUpdateChecking?.(() => {
      showBanner('Проверка обновлений...', '...');
      if (progressWrap) progressWrap.style.display = 'none';
    });
    window.electronAPI.onUpdateAvailable?.(info => {
      availableInfo = info;
      downloaded = false;
      showBanner(`Доступно обновление ${info.version || ''}`.trim(), 'Скачать');
      if (progressWrap) progressWrap.style.display = 'none';
    });
    window.electronAPI.onUpdateNotAvailable?.(() => {
      // hideBanner();
    });
    window.electronAPI.onUpdateProgress?.(p => {
      if (progressWrap) progressWrap.style.display = 'block';
      if (progressBar) progressBar.style.width = Math.round(p.percent || 0) + '%';
      showBanner(`Скачивание ${Math.round(p.percent || 0)}%...`, `${Math.round(p.percent || 0)}%`);
    });
    window.electronAPI.onUpdateDownloaded?.(info => {
      downloaded = true;
      availableInfo = info;
      showBanner(`Обновление ${info.version || ''} готово`.trim(), 'Перезапустить');
      if (progressWrap) progressWrap.style.display = 'none';
      if (progressBar) progressBar.style.width = '100%';
      // Auto-show like Discord
      // Optionally auto-install on quit is already enabled, but we show banner
    });
    window.electronAPI.onUpdateError?.(err => {
      console.warn('Updater error:', err);
      hideBanner();
    });

    // Also check via GitHub API on each start for dev mode
    if (!window.electronAPI.checkForUpdates) {
      // Fallback GitHub check
      fetch('https://api.github.com/repos/exieeez/Votify/releases/latest')
        .then(r => r.json())
        .then(release => {
          const latest = (release.tag_name || '').replace(/^v/, '');
          const current = '0.6.3'; // fallback, will be replaced by app version if available
          if (latest && latest !== current) {
            showBanner(`Доступно обновление ${latest}`, 'Скачать');
            if (actionBtn) {
              actionBtn.onclick = () => window.open(release.html_url, '_blank');
            }
          }
        }).catch(() => {});
    }
  }
})();

// Material Icons robust loading — add class when font ready, fallback check
(function ensureMaterialIcons(){
  const check = () => {
    const test = document.createElement('span');
    test.className = 'material-icons';
    test.style.position='absolute'; test.style.visibility='hidden'; test.textContent='play_arrow';
    document.body.appendChild(test);
    const w1 = test.offsetWidth;
    // If font loaded, width will be different from fallback
    setTimeout(()=>{
      const w2 = test.offsetWidth;
      if(w1===0 || w2===0 || document.fonts && document.fonts.check && document.fonts.check('24px "Material Icons"')){
        document.documentElement.classList.add('mi-loaded');
      } else {
        // font not loaded — still add class to show fallback styling, but icons may show text
        // Try reload link
        const link = document.querySelector('link[href*="Material+Icons"]');
        if(link){
          const clone = link.cloneNode();
          clone.href = clone.href + (clone.href.includes('?')?'&':'?') + 'reload=' + Date.now();
          document.head.appendChild(clone);
        }
        document.documentElement.classList.add('mi-fallback');
      }
      test.remove();
    }, 800);
  };
  if(document.readyState==='loading') document.addEventListener('DOMContentLoaded', check);
  else check();
  if(document.fonts && document.fonts.ready){
    document.fonts.ready.then(()=>{ document.documentElement.classList.add('mi-loaded'); });
  }
})();


// ==========================================
// Initialization
// ==========================================
function initApp() {
  try {
    console.log('Initializing Votify...');
    applyLanguage(appSettings.lang);
    renderSidebarPlaylists();

    // Initial screen
    switchScreen('home-screen', 'nav-home-btn');

    // Load last track if exists
    const lastTrackStr = localStorage.getItem('votify-last-track');
    if (lastTrackStr) {
      try {
        const lastTrack = JSON.parse(lastTrackStr);
        if (lastTrack) {
          currentPlaylist = JSON.parse(localStorage.getItem('votify-last-playlist')) || [lastTrack];
          currentTrackIndex = parseInt(localStorage.getItem('votify-last-index')) || 0;

          // Update UI without playing
          if (playerTitle) playerTitle.innerText = lastTrack.title;
          if (playerArtist) playerArtist.innerText = lastTrack.artist || 'Unknown';
          applyAllCoverPlaceholders(lastTrack);

          state.currentTrack = lastTrack;
          updateRightPlayerPanel(lastTrack);
          if(lastTrack.cover){
            extractDominantColor(lastTrack.cover).then(c=>{
              if(c){ const bright = c._bright || {r:Math.round(c.r/0.35),g:Math.round(c.g/0.35),b:Math.round(c.b/0.35)}; applyFsLavaLampColors(bright); }
            });
          }
        } else {
          applyAllCoverPlaceholders('');
        }
      } catch (e) {
        console.warn('Failed to load last track:', e);
        applyAllCoverPlaceholders('');
      }
    } else {
      // No track ever played — show beautiful placeholder everywhere
      applyAllCoverPlaceholders('');
    }

    console.log('Votify initialized successfully.');
  } catch (e) {
    console.error('Critical init error:', e);
  } finally {
    isBooting = false;
    // Always hide splash after a delay, even if init failed
    setTimeout(hideSplash, 1500);
  }
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', initApp);
} else {
  initApp();
}

// ==========================================
// PLAYER BACKGROUND — extract dominant color from cover
// ==========================================
function extractDominantColor(imgUrl) {
  return new Promise(resolve => {
    if (!imgUrl) return resolve(null);
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => {
      try {
        const canvas = document.createElement('canvas');
        const size = 32;
        canvas.width = size;
        canvas.height = size;
        const ctx = canvas.getContext('2d');
        ctx.drawImage(img, 0, 0, size, size);
        const data = ctx.getImageData(0, 0, size, size).data;
        let r = 0,
          g = 0,
          b = 0,
          count = 0;
        for (let i = 0; i < data.length; i += 16) {
          // sample every 4th pixel
          r += data[i];
          g += data[i + 1];
          b += data[i + 2];
          count++;
        }
        r = Math.round(r / count);
        g = Math.round(g / count);
        b = Math.round(b / count);
        // Darken it for player bar background
        const darkR = Math.round(r * 0.35);
        const darkG = Math.round(g * 0.35);
        const darkB = Math.round(b * 0.35);
        resolve({ r: darkR, g: darkG, b: darkB, _bright: { r, g, b } });
      } catch {
        resolve(null);
      }
    };
    img.onerror = () => resolve(null);
    img.src = imgUrl;
  });
}

function extractDominantColorBright(imgUrl) {
  return extractDominantColor(imgUrl).then(c => {
    if (!c) return null;
    return c._bright || { r: Math.round(c.r/0.35), g: Math.round(c.g/0.35), b: Math.round(c.b/0.35) };
  });
}

function rgbToHsl(r,g,b){
  r/=255; g/=255; b/=255;
  const max=Math.max(r,g,b), min=Math.min(r,g,b);
  let h,s,l=(max+min)/2;
  if(max===min){ h=s=0; } else {
    const d=max-min;
    s=l>0.5? d/(2-max-min): d/(max+min);
    switch(max){
      case r: h=(g-b)/d + (g<b?6:0); break;
      case g: h=(b-r)/d + 2; break;
      case b: h=(r-g)/d + 4; break;
    }
    h/=6;
  }
  return {h:h*360, s:s*100, l:l*100};
}
function hslToRgb(h,s,l){
  h/=360; s/=100; l/=100;
  let r,g,b;
  if(s===0){ r=g=b=l; } else {
    const hue2rgb=(p,q,t)=>{
      if(t<0) t+=1;
      if(t>1) t-=1;
      if(t<1/6) return p+(q-p)*6*t;
      if(t<1/2) return q;
      if(t<2/3) return p+(q-p)*(2/3-t)*6;
      return p;
    };
    const q=l<0.5? l*(1+s): l+s-l*s;
    const p=2*l-q;
    r=hue2rgb(p,q,h+1/3);
    g=hue2rgb(p,q,h);
    b=hue2rgb(p,q,h-1/3);
  }
  return {r:Math.round(r*255), g:Math.round(g*255), b:Math.round(b*255)};
}

function applyFsLavaLampColors(brightColor){
  const fs = document.getElementById('fullscreen-player');
  const lamp = document.getElementById('fs-lava-lamp');
  const grad = document.getElementById('fs-gradient-bg');
  if(!fs || !lamp) return;
  if(!brightColor){
    fs.style.removeProperty('--lava1-r'); fs.style.removeProperty('--lava1-g'); fs.style.removeProperty('--lava1-b');
    fs.style.removeProperty('--lava2-r'); fs.style.removeProperty('--lava2-g'); fs.style.removeProperty('--lava2-b');
    fs.style.removeProperty('--lava3-r'); fs.style.removeProperty('--lava3-g'); fs.style.removeProperty('--lava3-b');
    if(grad) grad.style.background='';
    return;
  }
  let {r,g,b} = brightColor;
  const lum = 0.2126*r + 0.7152*g + 0.0722*b;
  const base = Math.min(255, Math.max(200, Math.round(lum*0.6 + 120)));
  const c1 = {r: base, g: base, b: base};
  const c2 = {r: Math.max(160, base-35), g: Math.max(160, base-35), b: Math.max(160, base-35)};
  const c3 = {r: Math.max(90, base-110), g: Math.max(90, base-110), b: Math.max(90, base-110)};
  fs.style.setProperty('--lava1-r', c1.r); fs.style.setProperty('--lava1-g', c1.g); fs.style.setProperty('--lava1-b', c1.b);
  fs.style.setProperty('--lava2-r', c2.r); fs.style.setProperty('--lava2-g', c2.g); fs.style.setProperty('--lava2-b', c2.b);
  fs.style.setProperty('--lava3-r', c3.r); fs.style.setProperty('--lava3-g', c3.g); fs.style.setProperty('--lava3-b', c3.b);
  if(grad){
    grad.style.background = `radial-gradient(120% 90% at 18% 18%, rgba(255,255,255,0.06) 0%, rgba(0,0,0,0.85) 55%, #000 85%)`;
  }
}


// ============================================================================
// LIVE SETTINGS APPLICATION & PARTICLE CANVAS SYSTEM
// ============================================================================
let bgParticleCanvas = null;
let bgParticleCtx = null;
let bgParticleAnimationId = null;
let particlesArray = [];
let mousePos = { x: 0, y: 0 };
let particleMouseListenerAdded = false;

function initParticleEngine() {
  let canvas = document.getElementById('bg-particle-canvas');
  if (!canvas) {
    canvas = document.createElement('canvas');
    canvas.id = 'bg-particle-canvas';
    canvas.style.cssText =
      'position:fixed;top:0;left:0;width:100vw;height:100vh;pointer-events:none;z-index:2;';
    document.body.insertBefore(canvas, document.body.firstChild);
  }
  bgParticleCanvas = canvas;
  bgParticleCtx = canvas.getContext('2d');

  function resize() {
    if (bgParticleCanvas) {
      bgParticleCanvas.width = window.innerWidth;
      bgParticleCanvas.height = window.innerHeight;
    }
  }

  window.removeEventListener('resize', resize);
  window.addEventListener('resize', resize);
  resize();

  if (!particleMouseListenerAdded) {
    window.addEventListener('mousemove', e => {
      mousePos.x = (e.clientX - window.innerWidth / 2) * 0.05;
      mousePos.y = (e.clientY - window.innerHeight / 2) * 0.05;
    });
    particleMouseListenerAdded = true;
  }
}

function updateParticleSystem() {
  if (bgParticleAnimationId) {
    cancelAnimationFrame(bgParticleAnimationId);
    bgParticleAnimationId = null;
  }

  if (appSettings.perfParticles === false) {
    bgParticleCtx?.clearRect(0, 0, bgParticleCanvas?.width || 0, bgParticleCanvas?.height || 0);
    return;
  }

  if (!bgParticleCanvas) initParticleEngine();
  const type = appSettings.bgParticles || 'dots';
  if (type === 'none') {
    bgParticleCtx?.clearRect(0, 0, bgParticleCanvas.width, bgParticleCanvas.height);
    return;
  }

  const rawCount = Number(appSettings.particleCount) || 50;
  const count = type === 'network' ? Math.min(35, rawCount) : Math.min(110, rawCount);
  const speed = (Number(appSettings.particleSpeed) || 15) / 10;
  const size = Number(appSettings.particleSize) || 3.5;

  particlesArray = [];
  const w = bgParticleCanvas.width || window.innerWidth;
  const h = bgParticleCanvas.height || window.innerHeight;

  for (let i = 0; i < count; i++) {
    particlesArray.push({
      x: Math.random() * w,
      y: Math.random() * h,
      size: Math.random() * size + 1.5,
      speedX: (Math.random() - 0.5) * speed,
      speedY:
        type === 'snow' || type === 'rain' || type === 'sakura'
          ? Math.random() * speed + 0.5
          : (Math.random() - 0.5) * speed,
      opacity: Math.random() * 0.6 + 0.4,
      angle: Math.random() * Math.PI * 2,
    });
  }

  const accent =
    getComputedStyle(document.documentElement).getPropertyValue('--accent').trim() || '#1DB954';
  let lastTime = 0;
  const fpsInterval = 1000 / 60;

  function render(timestamp) {
    if (!bgParticleCtx || !bgParticleCanvas) return;
    if (document.hidden) {
      bgParticleAnimationId = requestAnimationFrame(render);
      return;
    }

    const elapsed = timestamp - lastTime;
    if (elapsed < fpsInterval) {
      bgParticleAnimationId = requestAnimationFrame(render);
      return;
    }
    lastTime = timestamp - (elapsed % fpsInterval);

    bgParticleCtx.clearRect(0, 0, bgParticleCanvas.width, bgParticleCanvas.height);

    particlesArray.forEach(p => {
      p.x += p.speedX;
      p.y += p.speedY;
      p.angle += 0.02;

      if (p.x < 0) p.x = bgParticleCanvas.width;
      if (p.x > bgParticleCanvas.width) p.x = 0;
      if (p.y < 0) p.y = bgParticleCanvas.height;
      if (p.y > bgParticleCanvas.height) p.y = 0;

      const px = p.x + (appSettings.particleParallax !== false ? mousePos.x : 0);
      const py = p.y + (appSettings.particleParallax !== false ? mousePos.y : 0);

      bgParticleCtx.beginPath();
      if (type === 'snow') {
        bgParticleCtx.fillStyle = `rgba(255, 255, 255, ${p.opacity * 0.95})`;
        bgParticleCtx.shadowColor = 'rgba(255, 255, 255, 0.8)';
        bgParticleCtx.shadowBlur = 6;
        bgParticleCtx.arc(px, py, p.size * 1.3, 0, Math.PI * 2);
        bgParticleCtx.fill();
        bgParticleCtx.shadowBlur = 0;
      } else if (type === 'rain') {
        bgParticleCtx.strokeStyle = `rgba(160, 220, 255, ${p.opacity * 0.9})`;
        bgParticleCtx.lineWidth = 2;
        bgParticleCtx.moveTo(px, py);
        bgParticleCtx.lineTo(px, py + p.size * 6);
        bgParticleCtx.stroke();
      } else if (type === 'stars') {
        bgParticleCtx.fillStyle = `rgba(255, 255, 200, ${p.opacity})`;
        bgParticleCtx.shadowColor = 'rgba(255, 255, 180, 0.9)';
        bgParticleCtx.shadowBlur = 8;
        bgParticleCtx.arc(px, py, p.size * 1.2, 0, Math.PI * 2);
        bgParticleCtx.fill();
        bgParticleCtx.shadowBlur = 0;
      } else if (type === 'hearts') {
        bgParticleCtx.fillStyle = `rgba(255, 105, 180, ${p.opacity})`;
        bgParticleCtx.font = `${Math.max(12, p.size * 3.5)}px sans-serif`;
        bgParticleCtx.fillText('♥', px, py);
      } else if (type === 'sakura') {
        bgParticleCtx.save();
        bgParticleCtx.translate(px, py);
        bgParticleCtx.rotate(p.angle);
        bgParticleCtx.fillStyle = `rgba(255, 182, 193, ${p.opacity * 0.9})`;
        bgParticleCtx.beginPath();
        bgParticleCtx.ellipse(0, 0, p.size * 2.5, p.size * 1.3, Math.PI / 4, 0, Math.PI * 2);
        bgParticleCtx.fill();
        bgParticleCtx.restore();
      } else if (type === 'fireflies') {
        const glow = bgParticleCtx.createRadialGradient(px, py, 0, px, py, p.size * 4);
        glow.addColorStop(0, `rgba(255, 235, 59, ${p.opacity})`);
        glow.addColorStop(1, 'rgba(255, 235, 59, 0)');
        bgParticleCtx.fillStyle = glow;
        bgParticleCtx.arc(px, py, p.size * 4, 0, Math.PI * 2);
        bgParticleCtx.fill();
      } else {
        bgParticleCtx.fillStyle = accent;
        bgParticleCtx.shadowColor = accent;
        bgParticleCtx.shadowBlur = 8;
        bgParticleCtx.arc(px, py, p.size * 1.5, 0, Math.PI * 2);
        bgParticleCtx.fill();
        bgParticleCtx.shadowBlur = 0;
      }
    });

    if (type === 'network') {
      const maxDistSq = 110 * 110;
      for (let i = 0; i < particlesArray.length; i++) {
        for (let j = i + 1; j < particlesArray.length; j++) {
          const p1 = particlesArray[i];
          const p2 = particlesArray[j];
          const dx = p1.x - p2.x;
          const dy = p1.y - p2.y;
          const distSq = dx * dx + dy * dy;
          if (distSq < maxDistSq) {
            bgParticleCtx.beginPath();
            bgParticleCtx.strokeStyle = accent;
            bgParticleCtx.globalAlpha = (1 - Math.sqrt(distSq) / 110) * 0.35;
            bgParticleCtx.lineWidth = 1;
            bgParticleCtx.moveTo(p1.x, p1.y);
            bgParticleCtx.lineTo(p2.x, p2.y);
            bgParticleCtx.stroke();
            bgParticleCtx.globalAlpha = 1.0;
          }
        }
      }
    }

    bgParticleAnimationId = requestAnimationFrame(render);
  }

  render(performance.now());
}

function applyPlayerSettings() {
  const align = appSettings.playerTitleAlign || 'center';
  const style = appSettings.playerStyle || 'standard';
  const sliderType = appSettings.playerSliderType || 'normal';
  const dynamicBg = appSettings.dynamicPlayerBg !== false;

  document.body.dataset.playerTitleAlign = align;
  document.body.dataset.playerStyle = style;
  document.body.dataset.playerSliderType = sliderType;
  document.body.dataset.dynamicPlayerBg = dynamicBg ? 'true' : 'false';

  const trackInfos = document.querySelectorAll(
    '.fi-info, .player-track-info, .right-player-info, .fs-track-details, .pp-info, .pp-details, .player-bar-info, .fs-player-info'
  );
  trackInfos.forEach(el => {
    el.style.textAlign = align;
    if (align === 'center') {
      el.style.alignItems = 'center';
      el.style.justifyContent = 'center';
    } else if (align === 'right') {
      el.style.alignItems = 'flex-end';
      el.style.justifyContent = 'flex-end';
    } else {
      el.style.alignItems = 'flex-start';
      el.style.justifyContent = 'flex-start';
    }
  });

  document.body.classList.toggle('player-style-vinyl', style === 'vinyl');
  document.body.classList.toggle('player-style-large', style === 'large');
  document.body.classList.toggle('player-style-compact', style === 'compact');
  document.body.classList.toggle('player-style-glass', style === 'glass');
  document.body.classList.toggle('player-style-neon', style === 'neon');

  const bars = document.querySelectorAll(
    '.floating-island, .player-bar, .custom-eq-container, .bar-timeline-track, .fs-timeline-track, .right-player'
  );
  bars.forEach(b => {
    b.classList.toggle('slider-thin', sliderType === 'thin');
    b.classList.toggle('slider-ios', sliderType === 'ios');
    b.classList.toggle('slider-wave', sliderType === 'wave');
  });

  // Repaint SVG timelines immediately when the style changes; otherwise they
  // keep the previous flat path until the next audio timeupdate event.
  const progress = state.duration > 0 ? (state.currentTime / state.duration) * 100 : 0;
  if (typeof drawRightTimeline === 'function') drawRightTimeline(progress);
  if (typeof drawBarTimeline === 'function') drawBarTimeline(progress);
  if (typeof drawTimelineCurve === 'function') drawTimelineCurve(progress);
}

function applyCoverSettings() {
  const anim = appSettings.coverAnimation || 'none';
  const effect = appSettings.coverEffects || 'none';

  document.body.dataset.coverAnimation = anim;
  document.body.dataset.coverEffects = effect;

  const covers = document.querySelectorAll(
    'img.fi-cover, img.player-bar-cover, img.right-player-cover, img.fs-cover, img.pp-cover, #album-screen-cover, #page-player-cover, #lib-detail-cover-img'
  );
  covers.forEach(cover => {
    cover.style.animation = '';
    cover.style.filter = '';
    cover.style.transform = '';
    cover.style.borderRadius = '';

    if (anim === 'none') {
      cover.style.setProperty('animation', 'none', 'important');
    } else if (anim === 'rotation') {
      cover.style.animation = 'spin 10s linear infinite';
      cover.style.borderRadius = '50%';
    } else if (anim === 'pulsation') {
      cover.style.animation = 'pulse 1.5s ease-in-out infinite';
    } else if (anim === 'wave') {
      cover.style.animation = 'breath 3s ease-in-out infinite';
    } else if (anim === 'zoom') {
      cover.style.animation = 'zoomPulse 3s ease-in-out infinite';
    } else if (anim === 'flip') {
      cover.style.animation = 'flip3d 4s ease-in-out infinite';
    }

    if (effect === 'blur') cover.style.filter = 'blur(4px)';
    else if (effect === 'grayscale') cover.style.filter = 'grayscale(100%)';
    else if (effect === 'sepia') cover.style.filter = 'sepia(80%)';
    else if (effect === 'saturation') cover.style.filter = 'saturate(220%)';
    else if (effect === 'inversion') cover.style.filter = 'invert(100%)';
  });
}

function applyUISettings() {
  const root = document.documentElement;
  const ff = appSettings.fontFamily || 'inter';
  const fonts = {
    inter: '"Inter", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
    roboto: '"Roboto", sans-serif',
    system: 'system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
    modern: '"Outfit", "Inter", sans-serif',
    serif: 'Georgia, Cambria, "Times New Roman", serif',
    mono: '"JetBrains Mono", "Fira Code", monospace',
    hand: '"Caveat", "Comic Sans MS", cursive',
    deco: '"Cinzel", "Playfair Display", serif',
    game: '"Press Start 2P", monospace',
    helvetica: '"Helvetica Neue", Helvetica, Arial, sans-serif',
    sf: '-apple-system, BlinkMacSystemFont, sans-serif',
  };
  const fontVal = fonts[ff] || fonts.inter;
  root.style.setProperty('--font-family', fontVal);
  root.style.setProperty('--app-font', fontVal);
  document.body.style.fontFamily = fontVal;
  document.documentElement.style.fontFamily = fontVal;

  const fontSizeVal = appSettings.fontSize || '16px';
  root.style.setProperty('--font-size', fontSizeVal);
  root.style.setProperty('--app-font-size-offset', fontSizeVal);
  document.body.style.fontSize = fontSizeVal;

  const r = Number(appSettings.cornerRadius) ?? 8;
  root.style.setProperty('--radius-sm', Math.max(0, r - 4) + 'px');
  root.style.setProperty('--radius-md', r + 'px');
  root.style.setProperty('--radius-lg', Math.min(32, r + 6) + 'px');

  const scale = (Number(appSettings.uiScale) || 100) / 100;
  const appContainer = document.querySelector('.app-container') || document.body;
  if (scale !== 1) {
    appContainer.style.transform = `scale(${scale})`;
    appContainer.style.transformOrigin = 'top left';
    appContainer.style.width = `${(100 / scale).toFixed(2)}%`;
    appContainer.style.height = `${(100 / scale).toFixed(2)}%`;
  } else {
    appContainer.style.transform = '';
    appContainer.style.width = '';
    appContainer.style.height = '';
  }

  const mode = appSettings.themeMode || 'contrast';
  document.body.dataset.themeMode = mode || 'contrast';
  if (mode === 'light') {
    document.body.classList.add('light-theme');
    document.body.classList.remove('dark-theme');
  } else if (mode === 'dark' || mode === 'contrast') {
    document.body.classList.add('dark-theme');
    document.body.classList.remove('light-theme');
  } else {
    const isDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
    document.body.classList.toggle('light-theme', !isDark);
    document.body.classList.toggle('dark-theme', isDark);
  }
}

function applyTabsSettings() {
  const homeBtn = document.getElementById('nav-home-btn');
  const searchBtn = document.getElementById('nav-search-btn');
  const libBtn = document.getElementById('nav-library-btn');
  const settingsBtn = document.getElementById('nav-settings-btn');

  if (homeBtn) homeBtn.style.display = appSettings.tabHome === false ? 'none' : '';
  if (searchBtn) searchBtn.style.display = appSettings.tabSearch === false ? 'none' : '';
  if (libBtn) libBtn.style.display = appSettings.tabLibrary === false ? 'none' : '';
  if (settingsBtn) settingsBtn.style.display = appSettings.tabSettings === false ? 'none' : '';
}

function syncColorPickersFromSettings() {
  const api = getColorSchemesApi();
  const colors = api
    ? api.readColorSchemeFromSettings(appSettings)
    : {
        accent: appSettings.customColorPrimary || appSettings.accent || '#1DB954',
        background: appSettings.customColorBg || '#121212',
        text: appSettings.customColorText || '#ffffff',
        cards: appSettings.customColorCards || '#181818',
        borders: appSettings.customColorBorders || '#2a2a2a',
        focus: appSettings.customColorFocus || appSettings.customColorPrimary || '#1DB954',
      };
  const map = {
    'picker-color-primary': colors.accent,
    'picker-color-bg': colors.background,
    'picker-color-text': colors.text,
    'picker-color-cards': colors.cards,
    'picker-color-borders': colors.borders,
    'picker-color-focus': colors.focus,
  };
  Object.entries(map).forEach(([id, value]) => {
    const el = document.getElementById(id);
    if (el && value) {
      el.value = value;
      el._committedColorValue = el.value;
    }
  });
}

function renderSavedColorSchemes() {
  const api = getColorSchemesApi();
  const list = document.getElementById('saved-color-schemes');
  const countEl = document.getElementById('saved-color-schemes-count');
  const schemes = sanitizeAppColorSchemes();
  const limit = api?.COLOR_SCHEME_LIMIT || 12;
  if (countEl) countEl.textContent = `${schemes.length} / ${limit}`;
  if (!list) return;
  if (!schemes.length) {
    list.innerHTML =
      '<p class="saved-color-schemes-empty">Нет сохранённых схем. Настройте цвета и нажмите «Сохранить цветовую схему».</p>';
    return;
  }
  const fields = api?.COLOR_SCHEME_FIELDS || [
    'accent',
    'background',
    'text',
    'cards',
    'borders',
    'focus',
  ];
  const activeId = appSettings.activeColorSchemeId || '';
  list.innerHTML = schemes
    .map(scheme => {
      const swatches = fields
        .map(
          field =>
            `<span class="saved-color-scheme-swatch" style="background:${escapeHtml(
              scheme.colors?.[field] || ''
            )}" title="${escapeHtml(field)}"></span>`
        )
        .join('');
      const id = escapeHtml(scheme.id);
      return `<article class="saved-color-scheme-card${
        scheme.id === activeId ? ' is-active' : ''
      }" data-scheme-id="${id}">
        <div class="saved-color-scheme-swatches">${swatches}</div>
        <div class="saved-color-scheme-meta">
          <strong>${escapeHtml(scheme.name)}</strong>
          <div class="saved-color-scheme-actions">
            <button type="button" class="btn-secondary btn-sm saved-color-scheme-apply" data-scheme-id="${id}">Применить</button>
            <button type="button" class="btn-danger btn-sm saved-color-scheme-delete" data-scheme-id="${id}">Удалить</button>
          </div>
        </div>
      </article>`;
    })
    .join('');
}

function applySavedColorSchemeById(id) {
  const api = getColorSchemesApi();
  if (!api) return;
  const scheme = api.applyColorScheme(appSettings.savedColorSchemes, id);
  if (!scheme) {
    showToast('Схема не найдена');
    return;
  }
  Object.assign(appSettings, api.colorSchemeToSettings(scheme));
  syncColorPickersFromSettings();
  applyAccentColor(scheme.colors.accent);
  applyCustomColors();
  renderSavedColorSchemes();
  saveSettings();
  showToast(`Схема «${scheme.name}» применена`);
}

async function deleteSavedColorSchemeById(id) {
  const api = getColorSchemesApi();
  if (!api) return;
  const scheme = api.applyColorScheme(appSettings.savedColorSchemes, id);
  const name = scheme?.name || 'схему';
  const confirmed = await confirmModal('Удалить схему', `Удалить цветовую схему «${name}»?`);
  if (!confirmed) return;
  appSettings.savedColorSchemes = api.deleteColorScheme(appSettings.savedColorSchemes, id);
  if (appSettings.activeColorSchemeId === id) appSettings.activeColorSchemeId = '';
  renderSavedColorSchemes();
  saveSettings();
  showToast('Схема удалена');
}

function readCurrentColorSchemeColors() {
  const api = getColorSchemesApi();
  const pickerValues = {
    customColorPrimary: document.getElementById('picker-color-primary')?.value,
    customColorBg: document.getElementById('picker-color-bg')?.value,
    customColorText: document.getElementById('picker-color-text')?.value,
    customColorCards: document.getElementById('picker-color-cards')?.value,
    customColorBorders: document.getElementById('picker-color-borders')?.value,
    customColorFocus: document.getElementById('picker-color-focus')?.value,
  };
  const source = { ...appSettings, ...pickerValues };
  if (api) return api.readColorSchemeFromSettings(source);
  return {
    accent: pickerValues.customColorPrimary || appSettings.customColorPrimary || '#1DB954',
    background: pickerValues.customColorBg || appSettings.customColorBg || '#121212',
    text: pickerValues.customColorText || appSettings.customColorText || '#ffffff',
    cards: pickerValues.customColorCards || appSettings.customColorCards || '#181818',
    borders: pickerValues.customColorBorders || appSettings.customColorBorders || '#2a2a2a',
    focus: pickerValues.customColorFocus || appSettings.customColorFocus || '#1DB954',
  };
}

function applyCurrentCustomColors({ notify = true } = {}) {
  const colors = readCurrentColorSchemeColors();
  Object.assign(appSettings, {
    accent: colors.accent,
    customColorPrimary: colors.accent,
    customColorBg: colors.background,
    customColorText: colors.text,
    customColorCards: colors.cards,
    customColorBorders: colors.borders,
    customColorFocus: colors.focus,
    activeColorSchemeId: '',
    uiThemePreset: 'custom',
  });
  syncColorPickersFromSettings();
  applyAccentColor(colors.accent);
  applyCustomColors();
  renderSavedColorSchemes();
  saveSettings();
  document
    .querySelectorAll('#ui-theme-presets button')
    .forEach(button => button.classList.remove('active'));
  if (notify) showToast('Своя тема применена');
  return colors;
}

function saveCurrentColorScheme() {
  const api = getColorSchemesApi();
  if (!api) {
    showToast('Модуль цветовых схем недоступен');
    return;
  }
  const nameInput = document.getElementById('color-scheme-name-input');
  const lang = appSettings.lang === 'en' ? 'en' : 'ru';
  const fallbackName = api.nextColorSchemeName(appSettings.savedColorSchemes, lang);
  const name = api.sanitizeSchemeName(nameInput?.value, fallbackName);
  const colors = readCurrentColorSchemeColors();
  const result = api.saveColorScheme(appSettings.savedColorSchemes, { name, colors });
  if (!result.ok) {
    if (result.reason === 'limit') {
      showToast('Можно сохранить не больше 12 цветовых схем');
    } else if (result.reason === 'duplicate') {
      // A duplicate is still a valid scheme to apply. Previously the button
      // did nothing here, which made it look as though custom colors were
      // broken whenever the same palette had already been saved.
      Object.assign(appSettings, api.colorSchemeToSettings(result.scheme));
      syncColorPickersFromSettings();
      applyAccentColor(result.scheme.colors.accent);
      applyCustomColors();
      renderSavedColorSchemes();
      saveSettings();
      showToast(`Схема «${result.scheme.name}» применена`);
    }
    return;
  }
  appSettings.savedColorSchemes = result.schemes;
  Object.assign(appSettings, api.colorSchemeToSettings(result.scheme));
  if (nameInput) nameInput.value = '';

  // “Save color scheme” must also apply the values currently visible in the
  // pickers. This is especially important when the native color input emits
  // only a change event (or the redesigned settings initializer is delayed).
  syncColorPickersFromSettings();
  applyAccentColor(result.scheme.colors.accent);
  applyCustomColors();
  renderSavedColorSchemes();
  saveSettings();
  showToast(`Схема «${result.scheme.name}» сохранена и применена`);
}

function bindCustomColorPickers() {
  const pickerSettings = {
    'picker-color-primary': ['customColorPrimary', '#1DB954'],
    'picker-color-bg': ['customColorBg', '#121212'],
    'picker-color-text': ['customColorText', '#FFFFFF'],
    'picker-color-cards': ['customColorCards', '#181818'],
    'picker-color-borders': ['customColorBorders', '#2A2A2A'],
    'picker-color-focus': ['customColorFocus', '#1DB954'],
  };

  Object.entries(pickerSettings).forEach(([id, [key, fallback]]) => {
    const picker = document.getElementById(id);
    if (!picker) return;
    picker.value = appSettings[key] || fallback;
    if (picker._customColorBound) return;
    picker._customColorBound = true;

    picker._committedColorValue = picker.value;
    const commitColor = () => {
      // Native colour dialogs may emit a closing event even when the user
      // clicks outside and cancels. Ignore it unless the value really changed.
      if (picker.value === picker._committedColorValue) return;
      picker._committedColorValue = picker.value;
      appSettings[key] = picker.value;
      appSettings.activeColorSchemeId = '';
      appSettings.uiThemePreset = 'custom';
      document
        .querySelectorAll('#ui-theme-presets button')
        .forEach(button => button.classList.remove('active'));
      if (key === 'customColorPrimary') applyAccentColor(picker.value);
      applyCustomColors();
      renderSavedColorSchemes();
      saveSettings();
    };
    // Do not repaint and sync to the cloud for every movement inside the
    // native picker. One commit removes the lag and prevents cancel => Neutral.
    picker.addEventListener('change', commitColor);
  });
}

const THEME_COLOR_PRESETS = {
  neutral: ['#1DB954', '#121212', '#181818', '#FFFFFF', '#333333', '#1DB954'],
  amoled: ['#1DB954', '#000000', '#080808', '#FFFFFF', '#242424', '#1ED760'],
  crimson: ['#DC263F', '#16080B', '#241014', '#FFF5F6', '#4A1B23', '#FF526A'],
  dracula: ['#BD93F9', '#191A24', '#282A36', '#F8F8F2', '#44475A', '#FF79C6'],
  nord: ['#88C0D0', '#242933', '#2E3440', '#ECEFF4', '#4C566A', '#8FBCBB'],
  sky: ['#38BDF8', '#071521', '#0C2030', '#F0F9FF', '#1E3A4D', '#7DD3FC'],
  mint: ['#34D399', '#071A15', '#0D2820', '#ECFDF5', '#245243', '#6EE7B7'],
  violet: ['#A855F7', '#160B25', '#24113C', '#FAF5FF', '#4B246E', '#C084FC'],
  blossom: ['#F43F5E', '#210A10', '#351019', '#FFF1F2', '#682132', '#FB7185'],
  sakura: ['#FF4FA3', '#220D19', '#351426', '#FFF0F7', '#6A294B', '#FF85BE'],
  terminal: ['#4AF626', '#020A02', '#071507', '#DFFFF8', '#174517', '#7CFF61'],
  sand: ['#EAB308', '#1C1706', '#2B230A', '#FFFBEB', '#5A4914', '#FACC15'],
  aqua: ['#06B6D4', '#04191D', '#09272C', '#ECFEFF', '#15505A', '#22D3EE'],
  sunset: ['#F97316', '#211006', '#34180A', '#FFF7ED', '#693216', '#FB923C'],
  slate: ['#94A3B8', '#0F141C', '#19212C', '#F8FAFC', '#39475A', '#CBD5E1'],
};

function bindThemeColorPresets() {
  document.querySelectorAll('#ui-theme-presets button').forEach(button => {
    const presetName = button.getAttribute('data-preset');
    button.classList.toggle('active', appSettings.uiThemePreset === presetName);
    if (button._themePresetBound) return;
    button._themePresetBound = true;
    button.addEventListener('click', () => {
      if (presetName === 'custom') {
        switchSettingsSection('app-custom');
        document.getElementById('picker-color-primary')?.focus();
        return;
      }
      const palette = THEME_COLOR_PRESETS[presetName] || THEME_COLOR_PRESETS.neutral;
      const [accent, background, cards, text, borders, focus] = palette;
      Object.assign(appSettings, {
        accent,
        customColorPrimary: accent,
        customColorBg: background,
        customColorText: text,
        customColorCards: cards,
        customColorBorders: borders,
        customColorFocus: focus,
        activeColorSchemeId: '',
        uiThemePreset: presetName,
      });
      document
        .querySelectorAll('#ui-theme-presets button')
        .forEach(item => item.classList.toggle('active', item === button));
      syncColorPickersFromSettings();
      applyAccentColor(accent);
      applyCustomColors();
      renderSavedColorSchemes();
      saveSettings();
      showToast(`Пресет «${button.textContent.trim()}» применён`);
    });
  });
}

function initSavedColorSchemes() {
  markSettingsRangeSliders();
  sanitizeAppColorSchemes();
  syncColorPickersFromSettings();
  bindCustomColorPickers();
  bindThemeColorPresets();
  renderSavedColorSchemes();
  const applyBtn = document.getElementById('btn-custom-theme-apply');
  if (applyBtn && !applyBtn._customThemeBound) {
    applyBtn._customThemeBound = true;
    applyBtn.addEventListener('click', event => {
      event.preventDefault();
      applyCurrentCustomColors();
    });
  }
  const saveBtn = document.getElementById('btn-custom-theme-save');
  if (saveBtn && !saveBtn._colorSchemeBound) {
    saveBtn._colorSchemeBound = true;
    saveBtn.addEventListener('click', event => {
      event.preventDefault();
      saveCurrentColorScheme();
    });
  }
  const list = document.getElementById('saved-color-schemes');
  if (list && !list._colorSchemeBound) {
    list._colorSchemeBound = true;
    list.addEventListener('click', event => {
      const applyBtn = event.target.closest('.saved-color-scheme-apply');
      const deleteBtn = event.target.closest('.saved-color-scheme-delete');
      if (applyBtn) applySavedColorSchemeById(applyBtn.getAttribute('data-scheme-id'));
      if (deleteBtn) deleteSavedColorSchemeById(deleteBtn.getAttribute('data-scheme-id'));
    });
  }
}

function applyCustomColors() {
  const targets = [document.documentElement, document.body].filter(Boolean);
  const setColor = (property, value) => {
    if (!value) return;
    targets.forEach(target => target.style.setProperty(property, value));
  };
  setColor('--accent', appSettings.customColorPrimary);
  const hex = String(appSettings.customColorPrimary || '').replace('#', '');
  if (/^[0-9a-f]{6}$/i.test(hex)) {
    const r = parseInt(hex.slice(0, 2), 16);
    const g = parseInt(hex.slice(2, 4), 16);
    const b = parseInt(hex.slice(4, 6), 16);
    targets.forEach(target => target.style.setProperty('--accent-rgb', `${r},${g},${b}`));
  }
  setColor('--bg-base', appSettings.customColorBg);
  setColor('--bg-surface', appSettings.customColorCards);
  setColor('--bg-elevated', appSettings.customColorCards);
  setColor('--text-primary', appSettings.customColorText);
  setColor('--bg-highlight', appSettings.customColorBorders);
  setColor('--md-outline', appSettings.customColorBorders);
  setColor('--focus-ring', appSettings.customColorFocus);

  const api = getColorSchemesApi();
  if (api && appSettings.activeColorSchemeId) {
    const current = { colors: api.readColorSchemeFromSettings(appSettings) };
    const active = api.applyColorScheme(
      appSettings.savedColorSchemes,
      appSettings.activeColorSchemeId
    );
    if (!active || !api.colorSchemesEqual(current, active)) {
      appSettings.activeColorSchemeId = '';
      renderSavedColorSchemes();
    }
  }
}

function applyAllSettings() {
  sanitizeAppColorSchemes();
  applyLanguage(appSettings.lang || 'ru');
  applyPlayerSettings();
  applyCoverSettings();
  applyUISettings();
  applyBackground();
  applyTabsSettings();
  applyCustomColors();
  syncColorPickersFromSettings();
  renderSavedColorSchemes();
  markSettingsRangeSliders();
  updateParticleSystem();
}

function workshopColor(value, fallback) {
  const normalized = String(value || '').trim();
  if (/^#[0-9a-f]{6}$/i.test(normalized)) return normalized.toUpperCase();
  if (/^#[0-9a-f]{3}$/i.test(normalized)) {
    return `#${normalized
      .slice(1)
      .split('')
      .map(character => character.repeat(2))
      .join('')}`.toUpperCase();
  }
  const rgb = normalized.match(/^rgba?\(\s*(\d+)\D+(\d+)\D+(\d+)/i);
  if (rgb) {
    return `#${rgb
      .slice(1, 4)
      .map(channel =>
        Math.max(0, Math.min(255, Number(channel)))
          .toString(16)
          .padStart(2, '0')
      )
      .join('')}`.toUpperCase();
  }
  return fallback;
}

function workshopBackgroundUrl(value) {
  const input = String(value || '').trim();
  if (!input || input.length > 2048) return '';
  try {
    const url = new URL(input);
    if (url.protocol !== 'https:' || url.username || url.password) return '';
    return url.toString().slice(0, 2048);
  } catch {
    return '';
  }
}

function workshopNumber(value, minimum, maximum, fallback) {
  const number = Number(value);
  return Number.isFinite(number)
    ? Math.max(minimum, Math.min(maximum, Math.round(number)))
    : fallback;
}

function workshopCssColor(property, fallback) {
  const source = document.body || document.documentElement;
  const value = getComputedStyle(source).getPropertyValue(property).trim();
  return workshopColor(value, fallback);
}

function getCurrentWorkshopTheme() {
  return {
    primary: workshopCssColor(
      '--accent',
      workshopColor(appSettings.customColorPrimary || appSettings.accent, '#1DB954')
    ),
    background: workshopCssColor('--bg-base', workshopColor(appSettings.customColorBg, '#121212')),
    text: workshopCssColor('--text-primary', workshopColor(appSettings.customColorText, '#FFFFFF')),
    cards: workshopCssColor('--bg-surface', workshopColor(appSettings.customColorCards, '#181818')),
    borders: workshopCssColor(
      '--bg-highlight',
      workshopColor(appSettings.customColorBorders, '#2A2A2A')
    ),
    focus: workshopCssColor(
      '--focus-ring',
      workshopColor(appSettings.customColorFocus || appSettings.accent, '#1DB954')
    ),
    mode: ['dark', 'light', 'system', 'contrast', 'midnight'].includes(appSettings.themeMode)
      ? appSettings.themeMode
      : 'contrast',
    backgroundPreset: /^grad-[1-9]$/.test(appSettings.bgPreset || appSettings.background)
      ? appSettings.bgPreset || appSettings.background
      : 'default',
    backgroundUrl:
      workshopBackgroundUrl(appSettings.bgUrl) || workshopBackgroundUrl(appSettings.background),
    cornerRadius: workshopNumber(appSettings.cornerRadius, 0, 24, 8),
    uiTransparency: workshopNumber(appSettings.uiTransparency, 10, 100, 45),
    backgroundBlur: workshopNumber(appSettings.backgroundBlur, 0, 60, 0),
    particles: [
      'none',
      'snow',
      'rain',
      'stars',
      'dots',
      'hearts',
      'fireflies',
      'sakura',
      'network',
    ].includes(appSettings.bgParticles)
      ? appSettings.bgParticles
      : 'none',
    fontFamily: [
      'system',
      'modern',
      'serif',
      'mono',
      'hand',
      'deco',
      'game',
      'inter',
      'roboto',
      'helvetica',
      'sf',
    ].includes(appSettings.fontFamily)
      ? appSettings.fontFamily
      : 'inter',
  };
}

function applyWorkshopTheme(theme, metadata = {}) {
  const current = getCurrentWorkshopTheme();
  const safe = {
    primary: workshopColor(theme?.primary, current.primary),
    background: workshopColor(theme?.background, current.background),
    text: workshopColor(theme?.text, current.text),
    cards: workshopColor(theme?.cards, current.cards),
    borders: workshopColor(theme?.borders, current.borders),
    focus: workshopColor(theme?.focus, current.focus),
    mode: ['dark', 'light', 'system', 'contrast', 'midnight'].includes(theme?.mode) ? theme.mode : current.mode,
    backgroundPreset: /^grad-[1-9]$/.test(theme?.backgroundPreset)
      ? theme.backgroundPreset
      : 'default',
    backgroundUrl: workshopBackgroundUrl(theme?.backgroundUrl),
    cornerRadius: workshopNumber(theme?.cornerRadius, 0, 24, current.cornerRadius),
    uiTransparency: workshopNumber(theme?.uiTransparency, 10, 100, current.uiTransparency),
    backgroundBlur: workshopNumber(theme?.backgroundBlur, 0, 60, current.backgroundBlur),
    particles: [
      'none',
      'snow',
      'rain',
      'stars',
      'dots',
      'hearts',
      'fireflies',
      'sakura',
      'network',
    ].includes(theme?.particles)
      ? theme.particles
      : 'none',
    fontFamily: [
      'system',
      'modern',
      'serif',
      'mono',
      'hand',
      'deco',
      'game',
      'inter',
      'roboto',
      'helvetica',
      'sf',
    ].includes(theme?.fontFamily)
      ? theme.fontFamily
      : current.fontFamily,
  };

  Object.assign(appSettings, {
    accent: safe.primary,
    customColorPrimary: safe.primary,
    customColorBg: safe.background,
    customColorText: safe.text,
    customColorCards: safe.cards,
    customColorBorders: safe.borders,
    customColorFocus: safe.focus,
    theme: safe.mode,
    themeMode: safe.mode,
    background: safe.backgroundUrl || safe.backgroundPreset,
    bgPreset: safe.backgroundPreset,
    bgUrl: safe.backgroundUrl,
    cornerRadius: safe.cornerRadius,
    uiTransparency: safe.uiTransparency,
    backgroundBlur: safe.backgroundBlur,
    bgParticles: safe.particles,
    fontFamily: safe.fontFamily,
    workshopThemeId: String(metadata.id || '').slice(0, 40),
    workshopThemeTitle: String(metadata.title || '').slice(0, 60),
  });

  const controlValues = {
    'picker-color-primary': safe.primary,
    'picker-color-bg': safe.background,
    'picker-color-text': safe.text,
    'picker-color-cards': safe.cards,
    'picker-color-borders': safe.borders,
    'picker-color-focus': safe.focus,
    'corner-radius-slider': safe.cornerRadius,
    'ui-transparency-slider': safe.uiTransparency,
    'background-blur-slider': safe.backgroundBlur,
    'bg-blur-slider': safe.backgroundBlur,
    'bg-url-input': safe.backgroundUrl,
    'setting-bg-particles': safe.particles,
    'font-family-select': safe.fontFamily,
  };
  Object.entries(controlValues).forEach(([id, value]) => {
    const control = document.getElementById(id);
    if (control) control.value = value;
  });
  document.querySelectorAll('.theme-mode-card').forEach(card => {
    card.classList.toggle('active', card.dataset.themeMode === safe.mode);
  });
  document.querySelectorAll('.bg-card').forEach(card => {
    card.classList.toggle('active', card.dataset.bg === safe.backgroundPreset);
    card.classList.toggle('bg-card-active', card.dataset.bg === safe.backgroundPreset);
  });
  const settingLabels = {
    'corner-radius-slider-value': `${safe.cornerRadius}px`,
    'ui-transparency-slider-value': `${safe.uiTransparency}%`,
    'background-blur-value': `${safe.backgroundBlur}px`,
    'bg-blur-slider-value': `${safe.backgroundBlur}px`,
  };
  Object.entries(settingLabels).forEach(([id, value]) => {
    const label = document.getElementById(id);
    if (label) label.textContent = value;
  });

  applyAccentColor(safe.primary);
  applyBackground();
  applyAllSettings();
  saveSettings();
  window.dispatchEvent(new CustomEvent('votify:theme-installed', { detail: metadata }));
  return safe;
}

window.VotifyThemeWorkshop = {
  getCurrentTheme: getCurrentWorkshopTheme,
  applyTheme: applyWorkshopTheme,
};

// ============================================================================
// REDESIGNED TREE SETTINGS LOGIC (17 PANELS / 3 CATEGORIES)
// ============================================================================
function initRedesignedSettings() {
  console.log('[Settings] Initializing redesigned settings logic...');

  // Helper to sync sliders (sets track fill percentage)
  function syncSliderFill(slider) {
    if (!slider) return;
    const min = parseFloat(slider.min) || 0;
    const max = parseFloat(slider.max) || 100;
    const val = parseFloat(slider.value) || 0;
    const pct = max > min ? ((val - min) / (max - min)) * 100 : 0;
    slider.style.setProperty('--r', pct + '%');
  }

  function wireInput(id, key, defaultValue, labelId, suffix = '', callback) {
    const el = document.getElementById(id);
    if (!el) return;

    // Set initial value
    const val = appSettings[key] !== undefined ? appSettings[key] : defaultValue;
    if (el.type === 'checkbox') {
      el.checked = !!val;
    } else {
      el.value = val;
    }

    // Sync slider fill initially
    if (el.type === 'range') {
      syncSliderFill(el);
      if (labelId) {
        const lbl = document.getElementById(labelId);
        if (lbl) lbl.textContent = val + suffix;
      }
    }

    const handleUpdate = () => {
      const newVal = el.type === 'checkbox' ? el.checked : el.value;
      appSettings[key] = newVal;
      saveSettings();

      if (el.type === 'range') {
        syncSliderFill(el);
        if (labelId) {
          const lbl = document.getElementById(labelId);
          if (lbl) lbl.textContent = newVal + suffix;
        }
      }
      if (typeof callback === 'function') callback(newVal);
      applyAllSettings();
    };

    el.addEventListener('input', handleUpdate);
    el.addEventListener('change', handleUpdate);
  }

  // --- 1. Основные (gen-main) ---
  const langSel = document.getElementById('lang-select');
  if (langSel) {
    langSel.value = appSettings.lang || 'ru';
    langSel.addEventListener('change', () => {
      appSettings.lang = langSel.value;
      saveSettings();
      applyLanguage(appSettings.lang);
      showToast('Язык изменен / Language changed');
    });
  }
  wireInput('toggle-launch-at-startup', 'launchAtStartup', false);
  wireInput('toggle-close-to-tray', 'closeToTray', false);
  wireInput('toggle-auto-similar', 'autoSimilarTracks', true);
  wireInput('toggle-restore-queue', 'restoreQueue', true);

  // --- 2. Оверлей (gen-overlay) ---
  wireInput('setting-overlay-mode', 'overlayMode', 'disabled');
  wireInput('setting-overlay-shape', 'overlayShape', 'rounded');
  wireInput('slider-overlay-opacity', 'overlayOpacity', 85, 'overlay-opacity-val', '%');
  wireInput('slider-overlay-scale', 'overlayScale', 100, 'overlay-scale-val', '%');
  wireInput('slider-overlay-width', 'overlayWidth', 340, 'overlay-width-val', 'px');
  wireInput('slider-overlay-height', 'overlayHeight', 220, 'overlay-height-val', 'px');

  // Alignment grid button listeners
  document.querySelectorAll('.alignment-grid-btn').forEach(btn => {
    const align = btn.getAttribute('data-align');
    if (appSettings.overlayAlignment === align) {
      document.querySelectorAll('.alignment-grid-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
    }
    btn.addEventListener('click', () => {
      document.querySelectorAll('.alignment-grid-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      appSettings.overlayAlignment = align;
      saveSettings();
      showToast('Выравнивание оверлея: ' + align);
    });
  });

  // --- 3. Аудио (gen-audio) ---
  const qSelect = document.getElementById('setting-audio-quality');
  if (qSelect) {
    qSelect.value = appSettings.audioQuality || 'medium';
    qSelect.addEventListener('change', async () => {
      appSettings.audioQuality = qSelect.value;
      saveSettings();
      try {
        await apiFetch('/api/network/settings', {
          method: 'POST',
          body: JSON.stringify({ audioQuality: appSettings.audioQuality }),
        });
        showToast('Качество аудио изменено');
      } catch (e) {
        /* ignore */
      }
    });
  }
  wireInput('toggle-gapless', 'gapless', false);
  wireInput('crossfade-duration', 'crossfade', 0, 'crossfade-value', ' сек');
  wireInput('toggle-normalize', 'normalize', false, null, '', () => {
    if (typeof applyNormalizeToNode === 'function') applyNormalizeToNode();
  });
  wireInput('toggle-cache-tracks', 'cacheTracks', true);

  // --- 4. Эффективность (gen-perf) ---
  wireInput('setting-perf-limiting', 'perfLimiting', 'off');
  wireInput('background-blur-slider', 'background-blur', 15, 'background-blur-value', 'px');
  const blurSlider = document.getElementById('background-blur-slider');
  if (blurSlider) {
    blurSlider.addEventListener('input', () => {
      document.documentElement.style.setProperty('--background-blur', blurSlider.value + 'px');
    });
  }
  wireInput('toggle-perf-bg', 'perfBg', true);
  wireInput('toggle-perf-particles', 'perfParticles', false, null, '', () =>
    updateParticleSystem()
  );
  wireInput('toggle-perf-covers', 'perfCovers', true);
  wireInput('toggle-perf-visualizers', 'perfVisualizers', true);
  wireInput('toggle-perf-blur', 'perfBlur', true);

  // --- 5. Горячие клавиши (gen-hotkeys) ---
  wireInput('toggle-global-hotkeys', 'globalHotkeysEnabled', true);

  // --- 6. Хранилище (gen-storage) ---
  function updateStorageSizes() {
    const tracksSizeEl = document.getElementById('cache-tracks-size');
    const coversSizeEl = document.getElementById('cache-covers-size');
    const lyricsSizeEl = document.getElementById('cache-lyrics-size');
    const totalPieEl = document.getElementById('storage-pie-used');

    const tracksMB = appSettings._cacheTracksMB || 1.2;
    const coversMB = appSettings._cacheCoversMB || 0.6;
    const lyricsMB = appSettings._cacheLyricsMB || 0.2;
    const totalUsed = (tracksMB + coversMB + lyricsMB).toFixed(1);

    if (tracksSizeEl) tracksSizeEl.textContent = `Размер кэша: ${tracksMB.toFixed(1)} МБ`;
    if (coversSizeEl) coversSizeEl.textContent = `Размер кэша: ${coversMB.toFixed(1)} МБ`;
    if (lyricsSizeEl) lyricsSizeEl.textContent = `Размер кэша: ${lyricsMB.toFixed(1)} МБ`;
    if (totalPieEl) totalPieEl.textContent = `${totalUsed} МБ`;

    // Update circular SVG dash-array
    const segment = document.querySelector('.donut-segment');
    if (segment) {
      const maxCap = 25; // 25MB max capacity for representation
      const pct = Math.min(100, Math.round((totalUsed / maxCap) * 100));
      segment.setAttribute('stroke-dasharray', `${pct} ${100 - pct}`);
    }
  }
  updateStorageSizes();

  safeClick('clear-tracks-cache-btn', () => {
    appSettings._cacheTracksMB = 0.0;
    saveSettings();
    updateStorageSizes();
    showToast('Кэш треков успешно очищен');
  });
  safeClick('clear-covers-cache-btn', () => {
    appSettings._cacheCoversMB = 0.0;
    saveSettings();
    updateStorageSizes();
    showToast('Кэш обложек успешно очищен');
  });
  safeClick('clear-lyrics-cache-btn', () => {
    appSettings._cacheLyricsMB = 0.0;
    saveSettings();
    updateStorageSizes();
    showToast('Кэш текстов успешно очищен');
  });
  safeClick('clear-all-storage-btn', async () => {
    const confirmed = await confirmModal(
      'Очистить всё',
      'Удалить абсолютно все кэшированные данные и сбросить настройки?'
    );
    if (confirmed) {
      appSettings._cacheTracksMB = 0.0;
      appSettings._cacheCoversMB = 0.0;
      appSettings._cacheLyricsMB = 0.0;
      saveSettings();
      updateStorageSizes();
      showToast('Все локальные кэши успешно сброшены!');
    }
  });

  // --- 7. Плеер (app-player) ---
  wireInput('setting-player-title-align', 'playerTitleAlign', 'center', null, '', () =>
    applyPlayerSettings()
  );
  wireInput('setting-player-style', 'playerStyle', 'standard', null, '', () => {
    applyPlayerSettings();
    // auto vinyl shape when style vinyl selected via select
    if (appSettings.playerStyle === 'vinyl' && appSettings.playerCoverShape !== 'Виниловая пластинка') {
      appSettings.playerCoverShape = 'Виниловая пластинка';
      const coverShapeValue = document.getElementById('cover-shape-value');
      const settingCoverShape = document.getElementById('setting-cover-shape');
      if (coverShapeValue) coverShapeValue.textContent = 'Виниловая пластинка';
      if (settingCoverShape) settingCoverShape.value = 'Виниловая пластинка';
      applyPlayerCoverShape();
      saveSettings();
    }
  });
  wireInput('setting-player-slider-type', 'playerSliderType', 'normal', null, '', () =>
    applyPlayerSettings()
  );
  wireInput('toggle-dynamic-player-bg', 'dynamicPlayerBg', true, null, '', () =>
    applyPlayerSettings()
  );

  // --- 8. Обложка (app-cover) ---
  wireInput(
    'setting-cover-shape',
    'playerCoverShape',
    'Закруглённый квадрат',
    null,
    '',
    () => applyPlayerCoverShape()
  );
  wireInput('setting-cover-animation', 'coverAnimation', 'none', null, '', () =>
    applyCoverSettings()
  );
  wireInput('setting-cover-effects', 'coverEffects', 'none', null, '', () => applyCoverSettings());
  wireInput('toggle-dynamic-accent', 'dynamicAccentColor', true, null, '', () =>
    applyCoverSettings()
  );

  // --- 9. Интерфейс (app-ui) ---
  wireInput('font-family-select', 'fontFamily', 'inter', null, '', () => applyUISettings());
  wireInput('font-size-slider', 'fontSize', '16px', 'font-size-slider-value', 'px', () =>
    applyUISettings()
  );
  wireInput('corner-radius-slider', 'cornerRadius', 8, 'corner-radius-slider-value', 'px', () =>
    applyUISettings()
  );
  wireInput('slider-ui-scale', 'uiScale', 100, 'ui-scale-slider-value', '%', () =>
    applyUISettings()
  );

  document.querySelectorAll('.theme-mode-card').forEach(card => {
    const mode = card.getAttribute('data-theme-mode');
    if (appSettings.themeMode === mode) card.classList.add('active');
    card.addEventListener('click', () => {
      document.querySelectorAll('.theme-mode-card').forEach(c => c.classList.remove('active'));
      card.classList.add('active');
      appSettings.themeMode = mode;
      saveSettings();
      applyUISettings();
      showToast('Режим темы изменен: ' + mode);
    });
  });

  // Presets are bound during initial page setup as well, so they work
  // immediately instead of waiting for this delayed initializer.
  bindThemeColorPresets();

  // --- 10. Вкладки (app-tabs) ---
  wireInput('toggle-tab-home', 'tabHome', true, null, '', () => applyTabsSettings());
  wireInput('toggle-tab-search', 'tabSearch', true, null, '', () => applyTabsSettings());
  wireInput('toggle-tab-library', 'tabLibrary', true, null, '', () => applyTabsSettings());
  wireInput('toggle-tab-settings', 'tabSettings', true, null, '', () => applyTabsSettings());

  // --- 11. Фон (app-bg) ---
  wireInput('setting-bg-particles', 'bgParticles', 'none', null, '', () => updateParticleSystem());
  wireInput('slider-particle-count', 'particleCount', 50, 'particle-count-val', '', () =>
    updateParticleSystem()
  );
  wireInput('slider-particle-speed', 'particleSpeed', 15, 'particle-speed-val', '×', () =>
    updateParticleSystem()
  );
  wireInput('slider-particle-size', 'particleSize', 3, 'particle-size-val', 'px', () =>
    updateParticleSystem()
  );
  wireInput('toggle-particle-parallax', 'particleParallax', true, null, '', () =>
    updateParticleSystem()
  );

  document.querySelectorAll('.bg-card').forEach(card => {
    const bgPreset = card.getAttribute('data-bg');
    if (appSettings.bgPreset === bgPreset) card.classList.add('active');
    card.addEventListener('click', () => {
      document.querySelectorAll('.bg-card').forEach(c => c.classList.remove('active'));
      card.classList.add('active');
      appSettings.bgPreset = bgPreset;
      appSettings.background = bgPreset;
      appSettings.bgUrl = '';
      const urlInput = document.getElementById('bg-url-input');
      if (urlInput) urlInput.value = '';
      saveSettings();
      document.body.dataset.bgPreset = bgPreset;
      applyBackground();
      showToast('Пресет фона изменен');
    });
  });

  safeClick('bg-url-apply', () => {
    const url = document.getElementById('bg-url-input')?.value.trim();
    if (url) {
      appSettings.bgUrl = url;
      appSettings.background = url;
      saveSettings();
      applyBackground();
      showToast('Фоновое изображение применено!');
    }
  });

  const bgFileInput = document.getElementById('bg-file-input');
  safeClick('bg-file-btn', () => bgFileInput?.click());
  if (bgFileInput) {
    bgFileInput.addEventListener('change', e => {
      const file = e.target.files?.[0];
      if (file) {
        const reader = new FileReader();
        reader.onload = ev => {
          const dataUrl = ev.target.result;
          appSettings.bgUrl = dataUrl;
          appSettings.background = dataUrl;
          saveSettings();
          applyBackground();
          showToast('Локальное изображение установлено как фон!');
        };
        reader.readAsDataURL(file);
      }
    });
  }

  // --- 12. Кастомизация (app-custom) ---
  // This is also called during initial page setup, before this delayed
  // redesigned-settings initializer. The binding is idempotent.
  bindCustomColorPickers();

  initSavedColorSchemes();

  // Initial application of all active settings
  applyAllSettings();
}

// Ensure the custom settings loader starts shortly after main initialization
setTimeout(initRedesignedSettings, 1000);
initSavedColorSchemes();
