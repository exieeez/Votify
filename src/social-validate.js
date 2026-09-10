/* Votify — Social validators (usernames, links, follows).
 * UMD: works as <script> (window.VotifySocialValidate) and in Node (require).
 * Username rules are Telegram-like: lowercase latin, digits, underscores,
 * must start with a letter, 3–32 chars, globally unique (first come, first served).
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  } else {
    root.VotifySocialValidate = factory();
  }
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  const USERNAME_MIN = 3;
  const USERNAME_MAX = 32;
  const USERNAME_PATTERN = /^[a-z][a-z0-9_]{2,31}$/;
  const DISPLAY_NAME_MAX = 40;
  const BIO_MAX = 150;
  const LINK_MAX = 120;
  const SHOWCASE_LIMIT = 20;

  // Reserved handles that can never be claimed (services, impersonation, routes).
  const RESERVED_USERNAMES = [
    'admin',
    'administrator',
    'api',
    'app',
    'account',
    'album',
    'albums',
    'android',
    'apple',
    'artist',
    'artists',
    'blog',
    'bot',
    'bots',
    'chart',
    'charts',
    'client',
    'desktop',
    'dev',
    'developer',
    'donate',
    'email',
    'favorites',
    'favourites',
    'gift',
    'gifts',
    'guest',
    'help',
    'history',
    'home',
    'invite',
    'ios',
    'library',
    'liked',
    'linux',
    'login',
    'macos',
    'mail',
    'mobile',
    'mod',
    'moderator',
    'music',
    'news',
    'now',
    'official',
    'player',
    'playing',
    'playlist',
    'playlists',
    'plus',
    'premium',
    'pro',
    'profile',
    'promo',
    'root',
    'search',
    'server',
    'settings',
    'shop',
    'soundcloud',
    'spotify',
    'staff',
    'status',
    'store',
    'support',
    'system',
    'telegram',
    'test',
    'track',
    'tracks',
    'update',
    'updates',
    'user',
    'users',
    'verified',
    'vk',
    'votify',
    'web',
    'windows',
    'youtube',
  ];

  const LINK_KINDS = ['telegram', 'soundcloud', 'vk'];
  const LINK_PATTERNS = {
    telegram: /^[A-Za-z0-9_]{3,32}$/,
    vk: /^[A-Za-z0-9_.]{3,64}$/,
    soundcloud: /^[A-Za-z0-9_.-]{3,64}$/,
  };
  const LINK_URLS = {
    telegram: handle => `https://t.me/${handle}`,
    vk: handle => `https://vk.com/${handle}`,
    soundcloud: handle => `https://soundcloud.com/${handle}`,
  };

  /** "  @ExIeEez " -> "exieeez" */
  function normalizeUsername(raw) {
    return String(raw || '')
      .trim()
      .replace(/^@+/, '')
      .toLowerCase();
  }

  function validateUsername(raw) {
    const value = normalizeUsername(raw);
    if (!value) return { ok: false, value: '', error: 'empty' };
    if (value.length < USERNAME_MIN) return { ok: false, value, error: 'too-short' };
    if (value.length > USERNAME_MAX) return { ok: false, value, error: 'too-long' };
    if (!USERNAME_PATTERN.test(value)) return { ok: false, value, error: 'bad-chars' };
    if (RESERVED_USERNAMES.includes(value)) return { ok: false, value, error: 'reserved' };
    return { ok: true, value, error: '' };
  }

  function usernameErrorText(error, lang = 'ru') {
    const en = lang !== 'ru';
    switch (error) {
      case 'empty':
        return en ? 'Enter a username' : 'Введите юзернейм';
      case 'too-short':
        return en ? `Minimum ${USERNAME_MIN} characters` : `Минимум ${USERNAME_MIN} символа`;
      case 'too-long':
        return en ? `Maximum ${USERNAME_MAX} characters` : `Максимум ${USERNAME_MAX} символов`;
      case 'bad-chars':
        return en
          ? 'Latin letters, digits and _; must start with a letter'
          : 'Латиница, цифры и _; начинается с буквы';
      case 'reserved':
        return en ? 'This username is reserved' : 'Этот юзернейм зарезервирован';
      case 'taken':
        return en ? 'This username is already taken' : 'Этот юзернейм уже занят';
      default:
        return en ? 'Invalid username' : 'Некорректный юзернейм';
    }
  }

  function sanitizeDisplayName(raw) {
    return String(raw || '')
      .replace(/\s+/g, ' ')
      .trim()
      .slice(0, DISPLAY_NAME_MAX);
  }

  function sanitizeBio(raw) {
    return String(raw || '')
      .replace(/\r/g, '')
      .trim()
      .slice(0, BIO_MAX);
  }

  /** Accepts a handle ("durov"), "@durov" or a full link; returns a clean handle or ''. */
  function normalizeLink(kind, raw) {
    if (!LINK_KINDS.includes(kind)) return '';
    let value = String(raw || '').trim();
    if (!value) return '';
    // Strip protocol / domain / leading @ so "https://t.me/durov" -> "durov".
    value = value
      .replace(/^https?:\/\//i, '')
      .replace(/^(www\.)?/i, '')
      .replace(/^(t\.me|telegram\.me|vk\.com|soundcloud\.com)\//i, '')
      .replace(/^@+/, '')
      .split(/[?#]/)[0]
      .replace(/\/+$/, '');
    // soundcloud links may include locale prefix, keep the last path segment
    if (value.includes('/')) value = value.split('/').pop();
    value = value.trim().slice(0, LINK_MAX);
    if (!value) return '';
    const pattern = LINK_PATTERNS[kind];
    if (pattern && !pattern.test(value)) return '';
    return value;
  }

  function linkUrl(kind, handle) {
    if (!handle || !LINK_URLS[kind]) return '';
    return LINK_URLS[kind](handle);
  }

  function sanitizeLinks(raw = {}) {
    return {
      telegram: normalizeLink('telegram', raw.telegram),
      soundcloud: normalizeLink('soundcloud', raw.soundcloud),
      vk: normalizeLink('vk', raw.vk),
    };
  }

  /** Firestore doc id for a follow edge / request: "followerUid_followingUid". */
  function followDocId(followerUid, followingUid) {
    return `${String(followerUid || '')}_${String(followingUid || '')}`;
  }

  function isSafeHttpUrl(value, maxLength = 2048) {
    const input = String(value || '').trim();
    if (!input || input.length > maxLength) return false;
    if (!/^https:\/\/[^/\s@]+[^@\s]*$/.test(input)) return false;
    return true;
  }

  function isSafeAvatar(value, maxLength = 153600) {
    const input = String(value || '');
    if (!input) return true;
    if (input.length > maxLength) return false;
    return input.startsWith('data:image/');
  }

  /** Builds the public playlist showcase stored in profiles/{uid}. */
  function buildShowcase(playlists) {
    if (!playlists || typeof playlists !== 'object') return [];
    return Object.keys(playlists)
      .filter(name => name && name !== 'Избранное')
      .slice(0, SHOWCASE_LIMIT)
      .map(name => {
        const list = Array.isArray(playlists[name]) ? playlists[name] : [];
        const firstWithCover = list.find(t => t && isSafeHttpUrl(t.cover));
        return {
          name: String(name).slice(0, 60),
          count: list.length,
          cover: firstWithCover ? String(firstWithCover.cover).slice(0, 2048) : '',
        };
      });
  }

  return {
    USERNAME_MIN,
    USERNAME_MAX,
    USERNAME_PATTERN,
    DISPLAY_NAME_MAX,
    BIO_MAX,
    LINK_MAX,
    SHOWCASE_LIMIT,
    RESERVED_USERNAMES,
    LINK_KINDS,
    normalizeUsername,
    validateUsername,
    usernameErrorText,
    sanitizeDisplayName,
    sanitizeBio,
    normalizeLink,
    linkUrl,
    sanitizeLinks,
    followDocId,
    isSafeHttpUrl,
    isSafeAvatar,
    buildShowcase,
  };
});
