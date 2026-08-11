const { Client } = require('@xhayper/discord-rpc');

const LISTENING_ACTIVITY_TYPE = 2;
const DETAILS_STATUS_DISPLAY_TYPE = 2;
const MAX_ACTIVITY_TEXT_LENGTH = 128;
const DEFAULT_RECONNECT_DELAY = 15000;

function normalizeText(value, fallback) {
  const normalized = String(value || '')
    .replace(/\s+/g, ' ')
    .trim();
  const text = normalized || fallback;
  return [...text].slice(0, MAX_ACTIVITY_TEXT_LENGTH).join('');
}

function normalizeNumber(value, fallback = 0) {
  const number = Number(value);
  return Number.isFinite(number) ? number : fallback;
}

function isExternalImageUrl(value) {
  if (typeof value !== 'string') return false;
  try {
    const url = new URL(value);
    return url.protocol === 'https:' || url.protocol === 'http:';
  } catch {
    return false;
  }
}

function buildListeningActivity(playback, options = {}, now = Date.now()) {
  const title = normalizeText(playback?.title, 'Неизвестный трек');
  const artist = normalizeText(playback?.artist, 'Неизвестный исполнитель');
  const isPlaying = playback?.isPlaying === true;
  const duration = Math.max(0, normalizeNumber(playback?.duration));
  const position = Math.max(0, Math.min(duration || Infinity, normalizeNumber(playback?.position)));
  const playbackRate = Math.max(0.25, Math.min(4, normalizeNumber(playback?.playbackRate, 1)));
  const activity = {
    name: normalizeText(options.applicationName, 'Votify'),
    type: LISTENING_ACTIVITY_TYPE,
    details: title,
    state: isPlaying ? artist : normalizeText(`${artist} • На паузе`, artist),
    statusDisplayType: DETAILS_STATUS_DISPLAY_TYPE,
    instance: false,
  };

  const cover = isExternalImageUrl(playback?.cover)
    ? playback.cover
    : String(options.fallbackImageKey || '').trim();
  if (cover) {
    activity.largeImageKey = cover;
    activity.largeImageText = normalizeText(`${title} — ${artist}`, title);
  }

  if (isPlaying && duration > 0 && position < duration) {
    const nowMs = normalizeNumber(now, Date.now());
    activity.startTimestamp = Math.round(nowMs - (position / playbackRate) * 1000);
    activity.endTimestamp = Math.round(nowMs + ((duration - position) / playbackRate) * 1000);
  }

  return activity;
}

class DiscordPresence {
  constructor({
    clientId,
    applicationName = 'Votify',
    fallbackImageKey = '',
    reconnectDelay = DEFAULT_RECONNECT_DELAY,
    ClientClass = Client,
    logger = console,
  } = {}) {
    this.clientId = String(clientId || '').trim();
    this.applicationName = applicationName;
    this.fallbackImageKey = fallbackImageKey;
    this.reconnectDelay = reconnectDelay;
    this.ClientClass = ClientClass;
    this.logger = logger;
    this.client = null;
    this.ready = false;
    this.connecting = false;
    this.stopped = true;
    this.reconnectTimer = null;
    this.desiredActivity = null;
    this.hasDesiredActivity = false;
    this.desiredVersion = 0;
    this.appliedVersion = -1;
    this.applying = false;
    this.hasLoggedConnectionError = false;
  }

  get enabled() {
    return /^\d{16,22}$/.test(this.clientId);
  }

  start() {
    if (!this.enabled) return false;
    this.stopped = false;
    this.connect();
    return true;
  }

  update(playback) {
    if (!this.enabled) return;
    this.desiredActivity = buildListeningActivity(playback, {
      applicationName: this.applicationName,
      fallbackImageKey: this.fallbackImageKey,
    });
    this.hasDesiredActivity = true;
    this.desiredVersion += 1;
    if (this.stopped) this.start();
    this.flush();
  }

  clear() {
    if (!this.enabled) return;
    this.desiredActivity = null;
    this.hasDesiredActivity = true;
    this.desiredVersion += 1;
    this.flush();
  }

  connect() {
    if (this.stopped || this.connecting || this.ready || !this.enabled) return;
    this.connecting = true;

    const client = new this.ClientClass({ clientId: this.clientId });
    this.client = client;

    client.once('ready', () => {
      if (this.client !== client || this.stopped) return;
      this.connecting = false;
      this.ready = true;
      this.appliedVersion = -1;
      this.hasLoggedConnectionError = false;
      this.logger.log('[discord] Rich Presence connected');
      this.flush();
    });

    client.once('disconnected', () => {
      if (this.client !== client) return;
      this.connecting = false;
      this.ready = false;
      this.client = null;
      if (!this.stopped) {
        this.logger.warn('[discord] Discord disconnected; reconnecting in the background');
        this.scheduleReconnect();
      }
    });

    Promise.resolve(client.login()).catch(error => {
      if (this.client !== client) return;
      this.connecting = false;
      this.ready = false;
      this.client = null;
      if (!this.hasLoggedConnectionError) {
        this.logger.warn(
          `[discord] Rich Presence is waiting for Discord: ${error?.message || String(error)}`
        );
        this.hasLoggedConnectionError = true;
      }
      Promise.resolve(client.destroy()).catch(() => {});
      this.scheduleReconnect();
    });
  }

  scheduleReconnect() {
    if (this.stopped || this.reconnectTimer || !this.enabled) return;
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.connect();
    }, this.reconnectDelay);
    this.reconnectTimer.unref?.();
  }

  async flush() {
    if (
      this.applying ||
      !this.ready ||
      !this.client?.user ||
      !this.hasDesiredActivity ||
      this.appliedVersion === this.desiredVersion
    ) {
      return;
    }

    this.applying = true;
    try {
      while (
        this.ready &&
        this.client?.user &&
        this.hasDesiredActivity &&
        this.appliedVersion !== this.desiredVersion
      ) {
        const version = this.desiredVersion;
        const activity = this.desiredActivity;
        if (activity) await this.client.user.setActivity(activity);
        else await this.client.user.clearActivity();
        this.appliedVersion = version;
      }
    } catch (error) {
      this.logger.warn(
        `[discord] Failed to update Rich Presence: ${error?.message || String(error)}`
      );
    } finally {
      this.applying = false;
    }
  }

  async stop() {
    this.stopped = true;
    this.ready = false;
    this.connecting = false;
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }

    const client = this.client;
    this.client = null;
    if (!client) return;
    try {
      if (client.user) await client.user.clearActivity();
    } catch {
      // Discord also removes the activity when the RPC connection closes.
    }
    try {
      await client.destroy();
    } catch {
      // The Discord process may already be closed.
    }
  }
}

module.exports = {
  DiscordPresence,
  buildListeningActivity,
  isExternalImageUrl,
  LISTENING_ACTIVITY_TYPE,
};
