/**
 * Storage.js — Unified localStorage + IndexedDB wrapper
 * Graceful degradation, typed, async-first
 */

const DB_NAME = 'VotifyDB';
const DB_VERSION = 1;
const STORES = ['cache', 'wave', 'charts', 'images', 'offline'];

let dbPromise = null;

function openDB() {
  if (dbPromise) return dbPromise;
  
  dbPromise = new Promise((resolve, reject) => {
    if (!window.indexedDB) {
      reject(new Error('IndexedDB not supported'));
      return;
    }
    
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    
    request.onerror = () => reject(request.error);
    request.onsuccess = () => resolve(request.result);
    
    request.onupgradeneeded = (e) => {
      const db = e.target.result;
      STORES.forEach(storeName => {
        if (!db.objectStoreNames.contains(storeName)) {
          db.createObjectStore(storeName, { keyPath: 'key' });
        }
      });
    };
  });
  
  return dbPromise;
}

async function withStore(storeName, mode, callback) {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(storeName, mode);
    const store = tx.objectStore(storeName);
    const result = callback(store);
    
    tx.oncomplete = () => resolve(result);
    tx.onerror = () => reject(tx.error);
  });
}

// High-level API
export const Storage = {
  // localStorage sync
  local: {
    get(key, defaultValue = null) {
      try {
        const item = localStorage.getItem(key);
        return item ? JSON.parse(item) : defaultValue;
      } catch { return defaultValue; }
    },
    set(key, value) {
      try { localStorage.setItem(key, JSON.stringify(value)); return true; }
      catch { return false; }
    },
    remove(key) {
      try { localStorage.removeItem(key); return true; }
      catch { return false; }
    },
    clear() {
      try { localStorage.clear(); return true; }
      catch { return false; }
    },
    keys() {
      try { return Object.keys(localStorage); }
      catch { return []; }
    }
  },

  // IndexedDB async
  async get(storeName, key) {
    return withStore(storeName, 'readonly', store => 
      new Promise((resolve, reject) => {
        const req = store.get(key);
        req.onsuccess = () => resolve(req.result?.value);
        req.onerror = () => reject(req.error);
      })
    );
  },

  async set(storeName, key, value, ttl = null) {
    return withStore(storeName, 'readwrite', store => 
      new Promise((resolve, reject) => {
        const data = { key, value, ts: Date.now(), ttl };
        const req = store.put(data);
        req.onsuccess = () => resolve(true);
        req.onerror = () => reject(req.error);
      })
    );
  },

  async remove(storeName, key) {
    return withStore(storeName, 'readwrite', store => 
      new Promise((resolve, reject) => {
        const req = store.delete(key);
        req.onsuccess = () => resolve(true);
        req.onerror = () => reject(req.error);
      })
    );
  },

  async clear(storeName) {
    return withStore(storeName, 'readwrite', store => 
      new Promise((resolve, reject) => {
        const req = store.clear();
        req.onsuccess = () => resolve(true);
        req.onerror = () => reject(req.error);
      })
    );
  },

  async keys(storeName) {
    return withStore(storeName, 'readonly', store => 
      new Promise((resolve, reject) => {
        const req = store.getAllKeys();
        req.onsuccess = () => resolve(req.result);
        req.onerror = () => reject(req.error);
      })
    );
  },

  // Cache with TTL
  async getCached(storeName, key, maxAge = 30 * 60 * 1000) {
    const entry = await this.get(storeName, key);
    if (!entry) return null;
    if (entry.ttl && Date.now() - entry.ts > entry.ttl) {
      await this.remove(storeName, key);
      return null;
    }
    if (maxAge && Date.now() - entry.ts > maxAge) {
      return null;
    }
    return entry.value;
  },

  async setCached(storeName, key, value, ttl = null) {
    return this.set(storeName, key, value, ttl);
  },

  // Migration helpers
  async migrateFromLocalStorage(prefix) {
    const keys = this.local.keys().filter(k => k.startsWith(prefix));
    for (const key of keys) {
      const value = this.local.get(key);
      const storeKey = key.slice(prefix.length);
      await this.set('cache', storeKey, value);
    }
    return keys.length;
  }
};

// Specialized stores
export const Cache = {
  get: (key) => Storage.getCached('cache', key),
  set: (key, value, ttl) => Storage.setCached('cache', key, value, ttl),
  remove: (key) => Storage.remove('cache', key)
};

export const WaveCache = {
  get: (key) => Storage.getCached('wave', key),
  set: (key, value) => Storage.setCached('wave', key, value, 24 * 60 * 60 * 1000),
  remove: (key) => Storage.remove('wave', key)
};

export const ChartCache = {
  get: (key) => Storage.getCached('charts', key),
  set: (key, value) => Storage.setCached('charts', key, value, 30 * 60 * 1000),
  remove: (key) => Storage.remove('charts', key)
};

export const ImageCache = {
  get: (url) => Storage.getCached('images', url, 7 * 24 * 60 * 60 * 1000),
  set: (url, blob) => Storage.setCached('images', url, blob, 7 * 24 * 60 * 60 * 1000)
};

export const OfflineQueue = {
  add: (action) => Storage.set('offline', `action_${Date.now()}`, action),
  getAll: async () => {
    const keys = await Storage.keys('offline');
    const actions = [];
    for (const key of keys) {
      const action = await Storage.get('offline', key);
      actions.push({ key, ...action });
    }
    return actions.sort((a, b) => a.ts - b.ts);
  },
  remove: (key) => Storage.remove('offline', key)
};

export default Storage;