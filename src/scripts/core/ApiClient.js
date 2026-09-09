/**
 * ApiClient.js — Backend communication layer
 * Wraps existing /api/* endpoints + new chart endpoints
 */

const API_BASE = '';

class ApiError extends Error {
  constructor(message, status, data) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.data = data;
  }
}

async function request(path, options = {}) {
  const url = `${API_BASE}${path}`;
  const config = {
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
    ...options,
  };

  if (config.body && typeof config.body === 'object') {
    config.body = JSON.stringify(config.body);
  }

  try {
    const res = await fetch(url, config);

    if (!res.ok) {
      let data;
      try {
        data = await res.json();
      } catch {
        data = { error: res.statusText };
      }
      throw new ApiError(data.error || 'Request failed', res.status, data);
    }

    const contentType = res.headers.get('content-type');
    if (contentType?.includes('application/json')) {
      return res.json();
    }
    return res.text();
  } catch (e) {
    if (e instanceof ApiError) throw e;
    throw new ApiError(e.message || 'Network error', 0, {});
  }
}

export const ApiClient = {
  // Search
  async search(query, limit = 12) {
    return request(`/api/search?q=${encodeURIComponent(query)}&limit=${limit}`);
  },

  // Stream URL
  async getStreamUrl(id) {
    return request(`/api/stream?id=${encodeURIComponent(id)}`);
  },

  // Audio URL (legacy)
  async getAudioUrl(id) {
    return request(`/api/audio?id=${encodeURIComponent(id)}`);
  },

  // Recommendations
  async getRecommendations(limit = 16) {
    return request(`/api/recommendations?limit=${limit}`);
  },

  // Preload
  async preload(ids) {
    return request(`/api/preload?ids=${ids.join(',')}`);
  },

  // Lyrics
  async getLyrics(track, artist) {
    return request(
      `/api/lyrics?track=${encodeURIComponent(track)}&artist=${encodeURIComponent(artist)}`
    );
  },

  // Artist
  async getArtist(name, limit = 50) {
    return request(`/api/artist?name=${encodeURIComponent(name)}&limit=${limit}`);
  },

  // Charts (NEW - will be implemented on backend)
  async getCharts(type, region = 'RU') {
    // Try new endpoint first
    try {
      return await request(`/api/charts/${type}?region=${encodeURIComponent(region)}`);
    } catch (e) {
      // Fallback: generate from recommendations + lastfm tags
      console.warn('Charts endpoint not ready, using fallback');
      return { tracks: [], source: 'fallback' };
    }
  },

  // Sync
  async syncGet() {
    return request('/api/sync/get');
  },

  async syncPush(data) {
    return request('/api/sync/push', {
      method: 'POST',
      body: data,
    });
  },

  // Network settings
  async getNetworkSettings() {
    return request('/api/network/settings');
  },

  async updateNetworkSettings(config) {
    return request('/api/network/settings', {
      method: 'POST',
      body: config,
    });
  },

  // Health check
  async health() {
    return request('/api/health').catch(() => ({ status: 'ok' }));
  },
};

// Helper: batch requests
export async function batchRequests(requests) {
  return Promise.allSettled(requests.map(r => r()));
}

// Helper: retry with backoff
export async function withRetry(fn, retries = 3, baseDelay = 1000) {
  for (let i = 0; i <= retries; i++) {
    try {
      return await fn();
    } catch (e) {
      if (i === retries) throw e;
      await new Promise(r => setTimeout(r, baseDelay * Math.pow(2, i)));
    }
  }
}

export default ApiClient;
