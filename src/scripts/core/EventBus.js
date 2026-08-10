/**
 * EventBus.js — Pub/Sub для декомпозиции компонентов
 * Tiny, zero-deps, supports wildcards
 */

export class EventBus {
  constructor() {
    this._events = new Map();
    this._wildcards = new Map();
  }

  on(event, callback) {
    if (!this._events.has(event)) this._events.set(event, new Set());
    this._events.get(event).add(callback);
    return () => this.off(event, callback);
  }

  off(event, callback) {
    this._events.get(event)?.delete(callback);
  }

  emit(event, ...args) {
    // Exact match
    this._events.get(event)?.forEach(cb => {
      try { cb(...args); } catch (e) { console.error(`EventBus error [${event}]:`, e); }
    });
    
    // Wildcard listeners
    this._wildcards.forEach((callbacks, pattern) => {
      if (this._match(pattern, event)) {
        callbacks.forEach(cb => {
          try { cb(event, ...args); } catch (e) { console.error(`EventBus wildcard error [${pattern}]:`, e); }
        });
      }
    });
  }

  onAny(pattern, callback) {
    if (!this._wildcards.has(pattern)) this._wildcards.set(pattern, new Set());
    this._wildcards.get(pattern).add(callback);
    return () => this._wildcards.get(pattern)?.delete(callback);
  }

  offAny(pattern, callback) {
    this._wildcards.get(pattern)?.delete(callback);
  }

  _match(pattern, event) {
    if (pattern === '*') return true;
    const regex = new RegExp('^' + pattern.replace(/\*/g, '.*') + '$');
    return regex.test(event);
  }

  once(event, callback) {
    const wrapper = (...args) => {
      this.off(event, wrapper);
      callback(...args);
    };
    return this.on(event, wrapper);
  }

  clear() {
    this._events.clear();
    this._wildcards.clear();
  }
}

// Singleton
export const eventBus = new EventBus();

// Event names constants
export const EVENTS = {
  // Player
  PLAYER_PLAY: 'player:play',
  PLAYER_PAUSE: 'player:pause',
  PLAYER_STOP: 'player:stop',
  PLAYER_NEXT: 'player:next',
  PLAYER_PREV: 'player:prev',
  PLAYER_SEEK: 'player:seek',
  PLAYER_VOLUME: 'player:volume',
  PLAYER_REPEAT: 'player:repeat',
  PLAYER_SHUFFLE: 'player:shuffle',
  PLAYER_TRACK_CHANGE: 'player:track-change',
  PLAYER_PROGRESS: 'player:progress',
  PLAYER_TIME_UPDATE: 'player:time-update',
  PLAYER_END: 'player:end',
  PLAYER_ERROR: 'player:error',
  
  // UI
  SIDEBAR_TOGGLE: 'sidebar:toggle',
  SIDEBAR_MOBILE_OPEN: 'sidebar:mobile-open',
  SIDEBAR_MOBILE_CLOSE: 'sidebar:mobile-close',
  THEME_CHANGE: 'theme:change',
  ROUTE_CHANGE: 'route:change',
  ROUTE_NAVIGATE: 'route:navigate',
  
  // Wave
  WAVE_GENERATE: 'wave:generate',
  WAVE_UPDATE: 'wave:update',
  WAVE_TRACK_ADD: 'wave:track-add',
  
  // Charts
  CHARTS_LOAD: 'charts:load',
  CHARTS_UPDATE: 'charts:update',
  CHARTS_TAB_CHANGE: 'charts:tab-change',
  
  // Search
  SEARCH_QUERY: 'search:query',
  SEARCH_RESULTS: 'search:results',
  SEARCH_HISTORY_ADD: 'search:history-add',
  SEARCH_HISTORY_CLEAR: 'search:history-clear',
  SEARCH_FILTER: 'search:filter',
  
  // Library
  LIBRARY_PLAYLIST_CREATE: 'library:playlist-create',
  LIBRARY_PLAYLIST_UPDATE: 'library:playlist-update',
  LIBRARY_PLAYLIST_DELETE: 'library:playlist-delete',
  LIBRARY_TRACK_LIKE: 'library:track-like',
  LIBRARY_TRACK_UNLIKE: 'library:track-unlike',
  
  // Fullscreen Player
  FULLSCREEN_OPEN: 'fullscreen:open',
  FULLSCREEN_CLOSE: 'fullscreen:close',
  
  // Toast/Notifications
  TOAST_SHOW: 'toast:show',
  TOAST_HIDE: 'toast:hide',
  
  // Settings
  SETTINGS_CHANGE: 'settings:change',
  
  // Keyboard
  KEY_SHORTCUT: 'key:shortcut'
};

export default eventBus;