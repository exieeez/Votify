/**
 * State.js — Reactive Proxy-based Store
 * Minimal, typed, dependency-free state management
 */

export class Store {
  constructor(initial = {}) {
    this._state = new Proxy(initial, {
      set: (target, prop, value) => {
        const old = target[prop];
        if (old !== value) {
          target[prop] = value;
          this._emit(prop, value, old);
        }
        return true;
      },
      get: (target, prop) => {
        if (prop === 'subscribe') return this.subscribe.bind(this);
        if (prop === 'getState') return () => ({ ...target });
        return target[prop];
      }
    });
    this._listeners = new Map();
  }

  subscribe(key, callback) {
    if (!this._listeners.has(key)) this._listeners.set(key, new Set());
    this._listeners.get(key).add(callback);
    return () => this._listeners.get(key)?.delete(callback);
  }

  _emit(key, value, old) {
    this._listeners.get(key)?.forEach(cb => cb(value, old));
    this._listeners.get('*')?.forEach(cb => cb(key, value, old));
  }

  getState() { return { ...this._state }; }
  setState(partial) { Object.assign(this._state, partial); }
}

// Create singleton store with initial state
export const store = new Store({
  // Player state
  currentTrack: null,
  queue: [],
  volume: 0.8,
  isPlaying: false,
  currentTime: 0,
  duration: 0,
  repeatMode: 'off', // off | all | one
  shuffle: false,
  
  // UI state
  sidebarCollapsed: true,
  sidebarMobileOpen: false,
  theme: 'dark', // dark | oled | sepia | high-contrast
  activeRoute: '/',
  
  // Data
  waveTracks: [],
  waveSections: [],
  charts: { region: [], neighbors: [], niche: [], underground: [] },
  searchResults: { tracks: [], artists: [], albums: [], playlists: [] },
  searchHistory: [],
  searchQuery: '',
  searchFilter: 'all',
  
  // User data
  user: { playlists: {}, liked: [], history: [] },
  
  // Loading states
  loading: { wave: false, charts: false, search: false },
  
  // Errors
  errors: {}
});

// Helper: typed accessors
export const select = {
  player: () => ({
    currentTrack: store.currentTrack,
    queue: store.queue,
    volume: store.volume,
    isPlaying: store.isPlaying,
    currentTime: store.currentTime,
    duration: store.duration,
    repeatMode: store.repeatMode,
    shuffle: store.shuffle
  }),
  ui: () => ({
    sidebarCollapsed: store.sidebarCollapsed,
    sidebarMobileOpen: store.sidebarMobileOpen,
    theme: store.theme,
    activeRoute: store.activeRoute
  }),
  wave: () => ({
    tracks: store.waveTracks,
    sections: store.waveSections
  }),
  charts: () => store.charts,
  search: () => ({
    results: store.searchResults,
    history: store.searchHistory,
    query: store.searchQuery,
    filter: store.searchFilter
  }),
  user: () => store.user,
  loading: () => store.loading,
  errors: () => store.errors
};

// Actions - centralized mutations
export const actions = {
  // Player
  setTrack(track) { store.currentTrack = track; },
  setQueue(queue) { store.queue = queue; },
  addToQueue(track) { store.queue = [...store.queue, track]; },
  removeFromQueue(index) { store.queue = store.queue.filter((_, i) => i !== index); },
  clearQueue() { store.queue = []; },
  setVolume(v) { store.volume = Math.max(0, Math.min(1, v)); },
  setPlaying(p) { store.isPlaying = p; },
  setCurrentTime(t) { store.currentTime = t; },
  setDuration(d) { store.duration = d; },
  setRepeatMode(m) { store.repeatMode = m; },
  setShuffle(s) { store.shuffle = s; },
  nextTrack() {
    const { queue, currentTrack, repeatMode, shuffle } = store;
    if (!currentTrack || queue.length === 0) return;
    const idx = queue.findIndex(t => t.id === currentTrack.id);
    let nextIdx = idx + 1;
    if (nextIdx >= queue.length) {
      if (repeatMode === 'all') nextIdx = 0;
      else return;
    }
    store.currentTrack = queue[nextIdx];
  },
  prevTrack() {
    const { queue, currentTrack, repeatMode } = store;
    if (!currentTrack || queue.length === 0) return;
    const idx = queue.findIndex(t => t.id === currentTrack.id);
    let prevIdx = idx - 1;
    if (prevIdx < 0) {
      if (repeatMode === 'all') prevIdx = queue.length - 1;
      else return;
    }
    store.currentTrack = queue[prevIdx];
  },
  
  // UI
  toggleSidebar() { store.sidebarCollapsed = !store.sidebarCollapsed; },
  setSidebarCollapsed(c) { store.sidebarCollapsed = c; },
  setSidebarMobileOpen(o) { store.sidebarMobileOpen = o; },
  setTheme(t) { 
    store.theme = t; 
    document.documentElement.setAttribute('data-theme', t);
    localStorage.setItem('theme', t);
  },
  setRoute(route) { store.activeRoute = route; },
  
  // Wave
  setWaveTracks(tracks) { store.waveTracks = tracks; },
  setWaveSections(sections) { store.waveSections = sections; },
  
  // Charts
  setCharts(type, tracks) { store.charts = { ...store.charts, [type]: tracks }; },
  
  // Search
  setSearchResults(results) { store.searchResults = results; },
  setSearchHistory(history) { store.searchHistory = history; },
  addSearchHistory(query) { 
    const h = store.searchHistory.filter(q => q !== query);
    h.unshift(query);
    store.searchHistory = h.slice(0, 20);
  },
  setSearchQuery(q) { store.searchQuery = q; },
  setSearchFilter(f) { store.searchFilter = f; },
  
  // User
  setUser(user) { store.user = { ...store.user, ...user }; },
  addToHistory(track) {
    const h = store.user.history.filter(t => t.id !== track.id);
    h.unshift({ ...track, playedAt: Date.now() });
    store.user = { ...store.user, history: h.slice(0, 500) };
  },
  toggleLike(trackId) {
    const liked = store.user.liked;
    const idx = liked.findIndex(t => t.id === trackId);
    if (idx >= 0) liked.splice(idx, 1);
    else liked.unshift(store.currentTrack);
    store.user = { ...store.user, liked: [...liked] };
  },
  
  // Loading
  setLoading(key, v) { store.loading = { ...store.loading, [key]: v }; },
  
  // Errors
  setError(key, err) { store.errors = { ...store.errors, [key]: err }; },
  clearError(key) { const e = { ...store.errors }; delete e[key]; store.errors = e; }
};

export default store;