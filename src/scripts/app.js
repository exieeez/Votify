/**
 * app.js — Votify Main Entry Point
 * Bootstraps: Store, Router, Sidebar, Player, Keyboard, Theme
 * Version: 2.0 Night Studio
 */

// TODO: For now we inline core modules since ESM imports need a bundler
// In prod these will be bundled by Vite/Webpack, in dev they work as ESM

// ==========================================
// SIMPLE INLINE CORE (no bundler needed)
// ==========================================

// --- Mini Store ---
const state = new Proxy({
  currentTrack: null, queue: [], volume: 0.8, isPlaying: false, currentTime: 0, duration: 0,
  sidebarCollapsed: true, activeRoute: '/', searchQuery: '', searchResults: [], searchLoading: false,
  waveTracks: [], chartsRegion: [], user: { playlists: {}, liked: [], history: [] }
}, {
  set(t, p, v) { const old = t[p]; t[p] = v; if (old !== v) dispatch('state:' + p, v, old); return true; }
});

const listeners = {};
function on(evt, fn) { (listeners[evt] = listeners[evt] || []).push(fn); return () => { listeners[evt] = listeners[evt].filter(f => f !== fn); }; }
function dispatch(evt, ...args) { (listeners[evt] || []).forEach(fn => { try { fn(...args); } catch(e) { console.error(evt, e); } }); }

// Audio
let audio = new Audio();
audio.preload = 'auto';
audio.volume = state.volume;

audio.addEventListener('loadedmetadata', () => { state.duration = audio.duration; });
audio.addEventListener('timeupdate', () => { state.currentTime = audio.currentTime; });
audio.addEventListener('ended', () => { onNext(); });
audio.addEventListener('error', (e) => { console.error('Audio error:', e); dispatch('toast', { text: 'Ошибка воспроизведения', type: 'error' }); });
audio.addEventListener('play', () => { state.isPlaying = true; });
audio.addEventListener('pause', () => { state.isPlaying = false; });

// ==========================================
// API HELPERS
// ==========================================
async function api(path) {
  try {
    const res = await fetch(path);
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const data = await res.json();
    return data;
  } catch (e) {
    console.log('API error:', e.message);
    return null;
  }
}

async function apiPost(path, body) {
  try {
    const res = await fetch(path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
    if (!res.ok) throw new Error('HTTP ' + res.status);
    return await res.json();
  } catch (e) { return null; }
}

// ==========================================
// PLAYER CONTROLS
// ==========================================
function playTrack(track) {
  if (!track || !track.id) return;
  state.currentTrack = track;
  state.queue = [track];
  loadAndPlay(track);
}

function loadAndPlay(track) {
  const streamUrl = `/api/stream?id=${encodeURIComponent(track.id)}`;
  fetch(streamUrl)
    .then(r => {
      if (!r.ok) throw new Error('Stream error ' + r.status);
      return r.blob();
    })
    .then(blob => {
      const objectUrl = URL.createObjectURL(blob);
      if (audio.src && audio.src.startsWith('blob:')) URL.revokeObjectURL(audio.src);
      audio.src = objectUrl;
      audio.play().catch(e => dispatch('toast', { text: 'Не удалось воспроизвести', type: 'error' }));
    })
    .catch(e => dispatch('toast', { text: 'Ошибка загрузки трека', type: 'error' }));
}

function togglePlay() {
  if (!audio.src) return;
  if (state.isPlaying) audio.pause();
  else audio.play().catch(e => dispatch('toast', { text: 'Не удалось воспроизвести', type: 'error' }));
}

function onPrev() {
  if (!state.queue.length) return;
  const idx = state.queue.findIndex(t => t.id === state.currentTrack?.id);
  if (idx <= 0) return;
  const prev = state.queue[idx - 1];
  state.currentTrack = prev;
  loadAndPlay(prev);
}

function onNext() {
  if (!state.queue.length) return;
  const idx = state.queue.findIndex(t => t.id === state.currentTrack?.id);
  if (idx < 0 || idx >= state.queue.length - 1) return;
  const next = state.queue[idx + 1];
  state.currentTrack = next;
  loadAndPlay(next);
}

function seek(time) { audio.currentTime = time; }

// ==========================================
// UI HELPERS
// ==========================================
const $ = (s, ctx = document) => ctx.querySelector(s);
const $$ = (s, ctx = document) => Array.from(ctx.querySelectorAll(s));

function toggleClass(el, cls, force) { el.classList.toggle(cls, force); }
function addClass(el, ...cls) { el.classList.add(...cls); }
function removeClass(el, ...cls) { el.classList.remove(...cls); }

function h(tag, attrs = {}, children = []) {
  const el = document.createElement(tag);
  Object.entries(attrs).forEach(([k, v]) => {
    if (k === 'class' || k === 'className') el.className = v;
    else if (k.startsWith('on') && typeof v === 'function') el.addEventListener(k.slice(2).toLowerCase(), v);
    else if (k === 'dataset') Object.assign(el.dataset, v);
    else el.setAttribute(k, String(v));
  });
  children.forEach(c => {
    if (typeof c === 'string') el.appendChild(document.createTextNode(c));
    else if (c instanceof Node) el.appendChild(c);
    else if (Array.isArray(c)) c.forEach(cc => el.appendChild(cc));
  });
  return el;
}

function icon(name) {
  return h('svg', { class: 'icon', viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2', 'stroke-linecap': 'round', 'stroke-linejoin': 'round' });
}

// ==========================================
// TRACK CARD RENDERER
// ==========================================
function renderTrackCard(track, variant = 'default') {
  const cover = track.cover || `https://img.youtube.com/vi/${track.id}/hqdefault.jpg`;
  const title = track.title || 'Unknown';
  const artist = track.artist || 'Unknown';

  const card = h('div', { class: `track-card track-card--${variant}`, dataset: { trackId: track.id, artist: artist } });

  const coverEl = h('img', { class: 'track-card__cover', src: cover, alt: title, loading: 'lazy' });
  coverEl.onerror = () => { coverEl.src = `https://img.youtube.com/vi/${track.id}/mqdefault.jpg`; };

  card.innerHTML = (variant === 'chart')
    ? `<div class="track-card__rank${track._rank <= 3 ? ' is-top3' : ''}">${track._rank || ''}</div>
       <img class="track-card__cover" src="${cover}" alt="${title}" loading="lazy">
       <div class="track-card__content">
         <div class="track-card__title">${title}</div>
         <div class="track-card__artist">${artist}</div>
       </div>
       <div class="track-card__actions">
         <button class="track-card__play" aria-label="Play ${title}">
           <svg class="icon" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>
         </button>
         <button class="track-card__action" aria-label="Add to queue">
           <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line></svg>
         </button>
       </div>`
    : (variant === 'compact')
    ? `<img class="track-card__cover" src="${cover}" alt="${title}" loading="lazy">
       <div class="track-card__content">
         <div class="track-card__title">${title}</div>
         <div class="track-card__artist">${artist}</div>
       </div>
       <div class="track-card__actions">
         <button class="track-card__play" aria-label="Play ${title}">
           <svg class="icon" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>
         </button>
       </div>`
    : `<img class="track-card__cover" src="${cover}" alt="${title}" loading="lazy">
       <div class="track-card__content">
         <div class="track-card__title">${title}</div>
         <div class="track-card__artist">${artist}</div>
       </div>
       <div class="track-card__actions">
         <button class="track-card__play" aria-label="Play ${title}">
           <svg class="icon" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>
         </button>
         <button class="track-card__action" aria-label="Add to queue">
           <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line></svg>
         </button>
         <button class="track-card__action" aria-label="Like">
           <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"></path></svg>
         </button>
       </div>`;

  card.querySelector('.track-card__play')?.addEventListener('click', (e) => {
    e.stopPropagation();
    playTrack(track);
  });
  
  card.addEventListener('click', () => playTrack(track));
  return card;
}

// ==========================================
// SEARCH VIEW
// ==========================================
function renderSearchView() {
  const query = state.searchQuery;
  if (query) return fetchAndRenderSearch(query);
  
  // Show history and trending
  return `<div class="search-view">
    <div class="search-input-wrapper">
      <input type="text" class="search-input" id="searchInput" placeholder="Введите название трека или артиста..." autofocus>
      <svg class="search-input__icon icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>
      <button class="search-input__clear icon-btn" id="searchClear" aria-label="Очистить">×</button>
    </div>
    ${renderSearchHistory()}
    <div id="searchResults"></div>
  </div>`;
}

function renderSearchHistory() {
  const history = JSON.parse(localStorage.getItem('searchHistory') || '[]').slice(0, 10);
  if (!history.length) return '';
  return `<div class="search-history">
    <div class="search-history__header">
      <h3 class="search-history__title">Недавние</h3>
      <button class="search-history__clear" id="clearHistory">Очистить</button>
    </div>
    <div class="search-history__list">
      ${history.map(q => `<button class="search-history__item" data-query="${q}">
        <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="1 4 1 10 7 10"></polyline><path d="M3.51 15a9 9 0 0011zA 9</path></svg>
        ${q}
      </button>`).join('')}
    </div>
  </div>`;
}

async function fetchAndRenderSearch(query) {
  const container = $('searchResults');
  if (!container) return;
  container.innerHTML = '<div class="search-loading"><div class="spinner"></div>Ищем...</div>';
  
  const data = await api(`/api/search?q=${encodeURIComponent(query)}&limit=12`);
  if (!data || !data.tracks) {
    container.innerHTML = '<div class="search-empty">Ничего не найдено</div>';
    return;
  }

  var html = `<div class="search-results">
    <div class="search-results__header">
      <div><h2 class="search-results__title">Результаты</h2>
      <span class="search-results__count">Найдено: ${data.tracks.length}</span></div>
    </div>
    <div class="search-results__grid">`;
  data.tracks.forEach(t => {
    html += `<div class="search-result-item" data-id="${t.id}">
      <img class="search-result-item__cover" src="${t.cover || 'https://img.youtube.com/vi/'+t.id+'/hqdefault.jpg'}" alt="${t.title}" loading="lazy">
      <div class="search-result-item__info">
        <div class="search-result-item__title">${t.title}</div>
        <div class="search-result-item__meta">
          <span>${t.artist}</span>
          <span class="search-result-item__type">Трек</span>
        </div>
      </div>
      <div class="search-result-item__actions">
        <button class="search-result-item__play" data-id="${t.id}">
          <svg class="icon" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>
        </button>
      </div>
    </div>`;
  });
  html += '</div></div>';
  container.innerHTML = html;

  // Bind click events
  container.querySelectorAll('.search-result-item__play').forEach(btn => {
    btn.addEventListener('click', () => {
      const track = data.tracks.find(t => t.id === btn.dataset.id);
      if (track) playTrack(track);
    });
  });

  container.querySelectorAll('.search-result-item').forEach(item => {
    item.addEventListener('click', () => {
      const track = data.tracks.find(t => t.id === item.dataset.id);
      if (track) playTrack(track);
    });
  });

  // Save search history
  const history = JSON.parse(localStorage.getItem('searchHistory') || '[]');
  history.unshift(query);
  localStorage.setItem('searchHistory', JSON.stringify([...new Set(history)].slice(0, 20)));
}

// ==========================================
// CHARTS VIEW
// ==========================================
const CHART_TABS = [
  { id: 'region', label: 'Регион', icon: '🏠' },
  { id: 'neighbors', label: 'Соседи', icon: '🌍' },
  { id: 'niche', label: 'Нишевое', icon: '🎭' },
  { id: 'underground', label: 'Подземелье', icon: '🕳' }
];

let currentChartTab = 'region';

function renderChartsView() {
  return `<div class="charts-tabs">
    ${CHART_TABS.map(t => `<button class="charts-tab${t.id === currentChartTab ? ' is-active' : ''}" data-tab="${t.id}"><span>${t.icon}</span> ${t.label}</button>`).join('')}
  </div>
  <div class="charts-content" id="chartsContent">
    <div class="charts-panel is-active" data-panel="region">
      <div class="chart-list stagger" id="chartList">
        <div class="chart-item">Загрузка чартов...</div>
      </div>
    </div>
  </div>`;
}

async function loadCharts(region = 'RU') {
  try {
    // Try Invidious trending by region
    const instance = 'https://yewtu.be';
    const popData = await api(`${instance}/api/v1/trending?type=music&region=${region}`);
    
    if (popData && Array.isArray(popData)) {
      const list = document.getElementById('chartList');
      if (!list) return;
      list.innerHTML = '';
      list.classList.add('stagger');
      popData.slice(0, 16).forEach((v, i) => {
        const div = document.createElement('div');
        div.className = 'chart-item';
        div.innerHTML = `
          <span class="chart-item__rank${i < 3 ? ' is-top'+(i+1) : ''}">${i + 1}</span>
          <img class="chart-item__cover" src="${v.videoThumbnails?.[1]?.url || v.videoThumbnails?.[0]?.url || ''}" alt="${v.title}" loading="lazy">
          <div class="chart-item__info">
            <div class="chart-item__title">${v.title}</div>
            <div class="chart-item__artist">${v.author}</div>
          </div>
          <div class="chart-item__actions">
            <button class="chart-item__play" data-id="${v.videoId}">
              <svg class="icon" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>
            </button>
          </div>`;
        div.addEventListener('click', () => {
          const track = { id: v.videoId, title: v.title, artist: v.author };
          playTrack(track);
        });
        list.appendChild(div);
      });
    } else {
      throw new Error('No data');
    }
  } catch (e) {
    // Fallback to search-based charts
    const list = document.getElementById('chartList');
    if (!list) return;
    list.innerHTML = '<div class="chart-item">Не удалось загрузить региональные чарты. Используй поиск.</div>';
  }
}

// ==========================================
// ROUTING & PAGE RENDERER
// ==========================================
const ROUTES = {
  '/': renderHomeView,
  '/search': renderSearchView,
  '/charts': renderChartsView,
  '/library': renderLibraryView,
};

function renderHomeView() {
  return `<div class="wave-section">
    <div class="wave-section__header">
      <div>
        <h2 class="wave-section__title">Твоя волна</h2>
        <p class="wave-section__subtitle">На основе твоих плейлистов, лайков и истории</p>
      </div>
      <button class="wave-section__action" id="refreshWave">Обновить волну</button>
    </div>
    <div id="waveContent">
      <div class="wave-skeleton">
        ${Array(6).fill('<div class="wave-skeleton__item"><div class="wave-skeleton__cover"></div><div class="wave-skeleton__text"></div><div class="wave-skeleton__text"></div></div>').join('')}
      </div>
    </div>
  </div>
  <div class="wave-section">
    <div class="wave-section__header">
      <div>
        <h2 class="wave-section__title">Региональные чарты</h2>
        <p class="wave-section__subtitle">Популярное в твоем регионе</p>
      </div>
      <button class="wave-section__action" data-route="/charts">Все чарты →</button>
    </div>
    <div id="homeCharts" class="wave-grid">
      Загрузка...</div>
  </div>`;
}

function renderLibraryView() {
  return `<div class="library-view">
    <div class="library-header">
      <h2 class="library-title">Моя библиотека</h2>
    </div>
    <div class="library-empty">
      <div class="library-empty__icon">📚</div>
      <h3>Здесь будут плейлисты, лайки и история прослушивания</h3>
      <p>Начни с поиска музыки и добавления в плейлисты</p>
    </div>
  </div>`;
}

function navigate(route) {
  state.activeRoute = route;
  window.location.hash = route;
  render();
}

function render() {
  const hash = window.location.hash.slice(1) || '/';
  const routeFn = ROUTES[hash] || ROUTES['/'];
  state.activeRoute = hash;

  // Update sidebar active
  $$('.sidebar__item').forEach(item => {
    item.classList.toggle('is-active', item.dataset.route === (hash === '/' ? '/' : hash));
  });

  // Update page title
  const titles = { '/': 'Моя волна', '/search': 'Поиск', '/charts': 'Чарты', '/library': 'Библиотека' };
  const titleEl = $('pageTitle');
  if (titleEl) titleEl.textContent = titles[hash] || 'Все';

  // Render content
  const content = $('mainContent');
  if (!content) return;
  content.innerHTML = routeFn();

  // Post-render init
  setTimeout(() => {
    if (hash === '/') initHomeView();
    if (hash === '/search') initSearchView();
    if (hash === '/charts') initChartTabs();
    if (hash === '/library') { /* static view */ }
  }, 10);
}

// ==========================================
// INIT VIEWS
// ==========================================
async function initHomeView() {
  // Load wave tracks from recommendations
  const refreshBtn = document.getElementById('refreshWave');
  if (refreshBtn) {
    refreshBtn.addEventListener('click', async () => {
      refreshBtn.textContent = 'Обновляем...';
      refreshBtn.disabled = true;
      await loadNewWave();
      refreshBtn.textContent = 'Обновить волну';
      refreshBtn.disabled = false;
    });
  }

  await loadNewWave();
  await loadHomeCharts();
}

async function loadNewWave() {
  const container = document.getElementById('waveContent');
  if (!container) return;
  try {
    const data = await api('/api/recommendations?limit=8');
    if (data && data.tracks) {
      container.innerHTML = '<div class="wave-scroll">' +
        data.tracks.map(t => `<div class="track-card track-card--default stagger__item" data-id="${t.id}">
          <img class="track-card__cover" src="${t.cover || 'https://img.youtube.com/vi/'+t.id+'/hqdefault.jpg'}" alt="${t.title}" loading="lazy">
          <div class="track-card__content">
            <div class="track-card__title">${t.title}</div>
            <div class="track-card__artist">${t.artist}</div>
            <div class="track-card__actions">
              <button class="track-card__play track-card__play-btn" data-id="${t.id}">
                <svg class="icon" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>
              </button>
            </div>
          </div>
        </div>`).join('') +
        '</div>';
      // Bind
      container.querySelectorAll('.track-card__play-btn').forEach(btn => {
        btn.addEventListener('click', e => {
          const track = data.tracks.find(t => t.id === btn.dataset.id);
          if (track) playTrack(track);
          e.stopPropagation();
        });
      });
      container.querySelectorAll('.track-card').forEach(card => {
        card.addEventListener('click', () => {
          const track = data.tracks.find(t => t.id === card.dataset.id);
          if (track) playTrack(track);
        });
      });
    }
  } catch (e) {
    container.innerHTML = '<div class="wave-empty">Не удалось загрузить волну. Попробуй поиск.</div>';
  }
}

async function loadHomeCharts() {
  const container = document.getElementById('homeCharts');
  if (!container) return;
  try {
    const data = await api('/api/recommendations?limit=6');
    if (data && data.tracks) {
      container.innerHTML = data.tracks.map((t, i) => `
        <div class="track-card track-card--default stagger__item" data-id="${t.id}">
          <img class="track-card__cover" src="${t.cover || 'https://img.youtube.com/vi/'+t.id+'/hqdefault.jpg'}" alt="${t.title}" loading="lazy">
          <div class="track-card__content">
            <div class="track-card__title">${t.title}</div>
            <div class="track-card__artist">${t.artist}</div>
          </div>
        </div>
      `).join('');
      container.querySelectorAll('.track-card').forEach(card => {
        card.addEventListener('click', () => {
          const track = data.tracks.find(t => t.id === card.dataset.id);
          if (track) playTrack(track);
        });
      });
    }
  } catch (e) {}
}

function initSearchView() {
  const input = document.getElementById('searchInput');
  const clearBtn = document.getElementById('searchClear');
  const results = document.getElementById('searchResults');
  const clearHistoryBtn = document.getElementById('clearHistory');

  if (input) {
    input.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') {
        const q = input.value.trim();
        if (!q) return;
        state.searchQuery = q;
        fetchAndRenderSearch(q);
      }
    });
    input.focus();
    input.addEventListener('input', () => {
      toggleClass(clearBtn, 'visible', input.value.length > 0);
    });
  }

  if (clearBtn) {
    clearBtn.addEventListener('click', () => {
      if (input) input.value = '';
      state.searchQuery = '';
      toggleClass(clearBtn, 'visible', false);
      if (results) results.innerHTML = '';
    });
  }

  if (clearHistoryBtn) {
    clearHistoryBtn.addEventListener('click', () => {
      localStorage.removeItem('searchHistory');
      const list = clearHistoryBtn.closest('.search-history');
      if (list) list.remove();
    });
  }

  // History clicks
  $$('.search-history__item').forEach(el => {
    el.addEventListener('click', () => {
      const q = el.dataset.q;
      state.searchQuery = q;
      if (input) input.value = q;
      fetchAndRenderSearch(q);
    });
  });

  // "Всеет чарты" button
  $$('[data-route]').forEach(el => {
    el.addEventListener('click', () => navigate(el.dataset.route));
  });
}

function initChartTabs() {
  // Bind tab switches
  $$('.charts-tab').forEach(tab => {
    tab.addEventListener('click', () => {
      $$('.charts-tab').forEach(t => t.classList.remove('is-active'));
      tab.classList.add('is-active');
      currentChartTab = tab.dataset.tab;
      loadCurrentTabChart();
    });
  });
  loadCurrentTabChart();
}

async function loadCurrentTabChart() {
  const list = document.getElementById('chartList');
  if (!list) return;
  list.innerHTML = '<div class="chart-item">Информация о чартах недоступна в офлайн режиме</div>';
  
  // Try loading from cached API
  try {
    const data = await api(`/api/search?q=music&limit=10`);
    if (data && data.tracks) {
      list.innerHTML = '';
      data.tracks.forEach((t, i) => {
        const div = document.createElement('div');
        div.className = 'chart-item';
        div.innerHTML = `
          <span class="chart-item__rank${i < 3 ? ' is-top'+(i+1) : ''}">${i + 1}</span>
          <img class="chart-item__cover" src="${t.cover || 'https://img.youtube.com/vi/'+t.id+'/hqdefault.jpg'}" alt="${t.title}" loading="lazy">
          <div class="chart-item__info">
            <div class="chart-item__title">${t.title}</div>
            <div class="chart-item__artist">${t.artist}</div>
          </div>
          <div class="chart-item__actions">
            <button class="chart-item__play" data-id="${t.id}">
              <svg class="icon" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>
            </button>
          </div>`;
        div.addEventListener('click', () => playTrack(t));
        list.appendChild(div);
      });
    }
  } catch (e) {}
}

// ==========================================
// KEYBOARD SHORTCUTS
// ==========================================
document.addEventListener('keydown', (e) => {
  // Don't trigger in inputs
  if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA' || e.target.isContentEditable) {
    if (e.key === 'Escape' && e.target.tagName === 'INPUT') e.target.blur();
    return;
  }

  switch (e.key) {
    case ' ': e.preventDefault(); togglePlay(); break;
    case 'ArrowLeft': e.preventDefault(); audio.currentTime -= 5; break;
    case 'ArrowRight': e.preventDefault(); audio.currentTime += 5; break;
    case 'ArrowUp': e.preventDefault(); state.volume = Math.min(1, state.volume + 0.05); audio.volume = state.volume; break;
    case 'ArrowDown': e.preventDefault(); state.volume = Math.max(0, state.volume - 0.05); audio.volume = state.volume; break;
    case 'n': case 'N': e.preventDefault(); onNext(); break;
    case 'p': case 'P': e.preventDefault(); onPrev(); break;
    case 'm': case 'M': e.preventDefault(); state.volume = state.volume > 0 ? 0 : 0.8; audio.volume = state.volume; break;
    case '/': e.preventDefault(); navigate('/search'); break;
  }
});

// ==========================================
// PLAYER BAR BINDINGS
// ==========================================
function initPlayerBar() {
  // Play/Pause
  const playBtns = $$('.player-bar__play');
  playBtns.forEach(btn => btn.addEventListener('click', togglePlay));

  // Next/Prev
  $('.player-bar__next')?.addEventListener('click', onNext);
  $('.player-bar__prev')?.addEventListener('click', onPrev);

  // Shuffle/Repeat
  $('.player-bar__shuffle')?.addEventListener('click', function() { 
    this.classList.toggle('is-active'); 
  });
  $('.player-bar__repeat')?.addEventListener('click', function() { 
    this.classList.toggle('is-active');
  });

  // Volume
  const volumeSlider = $('.player-bar__volume-slider');
  const muteBtn = $('.player-bar__mute');
  volumeSlider?.addEventListener('input', e => {
    state.volume = parseFloat(e.target.value);
    audio.volume = state.volume;
  });
  muteBtn?.addEventListener('click', () => {
    state.volume = state.volume > 0 ? 0 : 0.8;
    audio.volume = state.volume;
    if (volumeSlider) volumeSlider.value = state.volume;
  });

  // Progress
  const progressEl = $('.player-bar__progress');
  progressEl?.addEventListener('click', e => {
    const rect = progressEl.getBoundingClientRect();
    const pct = (e.clientX - rect.left) / rect.width;
    audio.currentTime = pct * audio.duration;
  });

  // reactive updates
  on('state:currentTime', time => {
    const fill = $('.player-bar__progress-fill');
    if (fill && state.duration > 0) fill.style.width = (time / state.duration * 100) + '%';
    const currentEl = $('.player-bar__current');
    if (currentEl) currentEl.textContent = `${Math.floor(time/60)}:${Math.floor(time%60).toString().padStart(2, '0')}`;
  });

  on('state:duration', dur => {
    const durEl = $('.player-bar__duration');
    if (durEl) durEl.textContent = `${Math.floor(dur/60)}:${Math.floor(dur%60).toString().padStart(2, '0')}`;
  });

  on('state:currentTrack', track => {
    const coverEl = $('.player-bar__cover');
    const titleEl = $('.player-bar__title');
    const artistEl = $('.player-bar__artist');
    if (track) {
      if (coverEl) coverEl.src = track.cover || `https://img.youtube.com/vi/${track.id}/hqdefault.jpg`;
      if (titleEl) titleEl.textContent = track.title || 'Неизвестен';
      if (artistEl) artistEl.textContent = track.artist || '';
    }
  });

  on('state:isPlaying', playing => {
    const playBtns = $$('.player-bar__play--large');
    playBtns.forEach(b => b.classList.toggle('is-playing', playing));
    const equalizer = $('.player-bar__equalizer');
    if (equalizer) equalizer.classList.toggle('playing', playing);
  });

  on('state:volume', vol => {
    if (volumeSlider) volumeSlider.value = vol;
    const volumeIcon = muteBtn?.querySelector('.icon-volume-high');
    const muteIcon = muteBtn?.querySelector('.icon-volume-mute');
    if (volumeIcon) volumeIcon.style.display = vol > 0.5 ? 'block' : 'none';
    if (muteIcon) muteIcon.style.display = vol === 0 ? 'block' : 'none';
  });
}

// ===========================================
// TOAST NOTIFICATIONS
// ===========================================
on('toast', ({ text, type = 'info' }) => {
  const container = $('toastContainer');
  if (!container) return;
  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;
  toast.textContent = text;
  toast.style.cssText = `
    background: var(--bg-elevated); border: 1px solid var(--border);
    color: var(--fg-primary); padding: var(--space-3) var(--space-5);
    border-radius: var(--radius-md); font-size: var(--text-sm);
    box-shadow: var(--shadow-lg); animation: slideLeft 200ms var(--ease-out);
    pointer-events: auto;
    `;
  container.appendChild(toast);
  setTimeout(() => { toast.remove(); }, 3000);
});

// ==========================================
// BOOTING
// ==========================================
document.addEventListener('DOMContentLoaded', () => {
  // Migrate old data to new format
  migrateOldData();

  // Init sidebar
  const sidebar = $('sidebar');
  const toggleBtn = $('sidebarToggle');
  toggleBtn?.addEventListener('click', () => {
    const isExpanded = sidebar.classList.toggle('is-expanded');
    sidebar.setAttribute('aria-expanded', String(isExpanded));
    localStorage.setItem('sidebarExpanded', String(isExpanded));
    state.sidebarCollapsed = !isExpanded;
  });
  
  // Restore sidebar state
  const savedExpanded = localStorage.getItem('sidebarExpanded') !== 'false';
  if (savedExpanded) { sidebar?.classList.add('is-expanded'); }
  else { sidebar?.classList.remove('is-expanded'); }
  state.sidebarCollapsed = !savedExpanded;

  // Sidebar navigation
  $$('.sidebar__item').forEach(item => {
    item.addEventListener('click', () => navigate(item.dataset.route || '/'));
  });

  // Route links on the page
  $$('.wave-section__action, [data-route]').forEach(el => {
    el.addEventListener('click', () => navigate(el.dataset.route));
  });

  // Theme toggle
  let themeIdx = 0;
  const themes = ['dark', 'oled', 'sepia', 'high-contrast'];
  $('themeToggle')?.addEventListener('click', () => {
    themeIdx = (themeIdx + 1) % themes.length;
    document.documentElement.setAttribute('data-theme', themes[themeIdx]);
    state.theme = themes[themeIdx];
    localStorage.setItem('theme', themes[themeIdx]);
  });
  // Load saved theme
  const savedTheme = localStorage.getItem('theme') || 'dark';
  document.documentElement.setAttribute('data-theme', savedTheme);
  state.theme = savedTheme;

  // Init player
  initPlayerBar();

  // Init router
  window.addEventListener('hashchange', render);
  render();

  // Keyboard: / to focus search
  document.addEventListener('keydown', (e) => {
    if (e.key === '/' && e.target.tagName !== 'INPUT' && e.target.tagName !== 'TEXTAREA') {
      e.preventDefault();
      navigate('/search');
      setTimeout(() => document.getElementById('searchInput')?.focus(), 50);
    }
  });

  // Global click handler for dynamic elements
  $('mainContent')?.addEventListener('click', (e) => {
    const playBtn = e.target.closest('.track-card__play, .search-result-item__play, .chart-item__play');
    if (playBtn) {
      e.stopPropagation();
      const card = playBtn.closest('[data-id], [data-track-id]');
      const id = card?.dataset.id || card?.dataset.trackId;
      const title = card?.querySelector('.track-card__title, .search-result-item__title')?.textContent;
      const artist = card?.querySelector('.track-card__artist, .search-result-item__artist')?.textContent;
      if (id) {
        const cover = card?.querySelector('img')?.src;
        playTrack({ id, title, artist: artist || '', cover });
      }
    }
  });

  // Periodic refresh
  $('refreshWave')?.addEventListener('click', loadNewWave);

  console.log('Votify Night Studio booted');
});

function migrateOldData() {
  const migrationDone = localStorage.getItem('v2_migrated');
  if (migrationDone) return;
  
  // Migrate playlists
  const oldPlaylists = JSON.parse(localStorage.getItem('votify-playlists') || '{}');
  localStorage.setItem('playlists', JSON.stringify(oldPlaylists));
  
  // Migrate settings
  const oldSettings = JSON.parse(localStorage.getItem('votify-settings') || '{}');
  if (oldSettings.theme) localStorage.setItem('theme', oldSettings.theme);
  
  // Migrate auth (skip)
  
  localStorage.setItem('v2_migrated', Date.now().toString());
}