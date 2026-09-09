(function (root, factory) {
  if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  } else {
    root.VotifyColorSchemes = factory();
  }
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  const COLOR_SCHEME_LIMIT = 12;
  const COLOR_SCHEME_NAME_MAX = 40;
  const COLOR_SCHEME_FIELDS = ['accent', 'background', 'text', 'cards', 'borders', 'focus'];
  const COLOR_SCHEME_DEFAULTS = {
    accent: '#1DB954',
    background: '#121212',
    text: '#FFFFFF',
    cards: '#181818',
    borders: '#2A2A2A',
    focus: '#1DB954',
  };
  const SETTINGS_COLOR_KEYS = {
    accent: 'customColorPrimary',
    background: 'customColorBg',
    text: 'customColorText',
    cards: 'customColorCards',
    borders: 'customColorBorders',
    focus: 'customColorFocus',
  };

  function normalizeHexColor(value, fallback = COLOR_SCHEME_DEFAULTS.accent) {
    const raw = String(value || '').trim();
    if (/^#[0-9a-f]{6}$/i.test(raw)) return raw.toUpperCase();
    if (/^#[0-9a-f]{3}$/i.test(raw)) {
      return `#${raw
        .slice(1)
        .split('')
        .map(character => character.repeat(2))
        .join('')}`.toUpperCase();
    }
    const rgb = raw.match(/^rgba?\(\s*(\d+)\D+(\d+)\D+(\d+)/i);
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
    return String(fallback || COLOR_SCHEME_DEFAULTS.accent).toUpperCase();
  }

  function sanitizeSchemeName(value, fallback = 'Схема') {
    const name = String(value || '')
      .replace(/\s+/g, ' ')
      .trim()
      .slice(0, COLOR_SCHEME_NAME_MAX);
    return name || fallback;
  }

  function sanitizeColorScheme(input = {}, fallbacks = COLOR_SCHEME_DEFAULTS) {
    const source = input && typeof input === 'object' ? input : {};
    const colors = source.colors && typeof source.colors === 'object' ? source.colors : source;
    const scheme = {
      id: String(source.id || '')
        .trim()
        .slice(0, 64),
      name: sanitizeSchemeName(source.name),
      createdAt: Number.isFinite(Number(source.createdAt)) ? Number(source.createdAt) : 0,
      colors: {},
    };
    COLOR_SCHEME_FIELDS.forEach(field => {
      scheme.colors[field] = normalizeHexColor(
        colors[field],
        fallbacks[field] || COLOR_SCHEME_DEFAULTS[field]
      );
    });
    return scheme;
  }

  function createColorSchemeId(now = Date.now()) {
    return `scheme-${Number(now).toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
  }

  function createColorScheme({
    name,
    colors,
    id,
    createdAt,
    now = Date.now(),
    fallbacks = COLOR_SCHEME_DEFAULTS,
  } = {}) {
    const scheme = sanitizeColorScheme(
      {
        id: id || createColorSchemeId(now),
        name,
        createdAt: createdAt || now,
        colors,
      },
      fallbacks
    );
    if (!scheme.id) scheme.id = createColorSchemeId(now);
    if (!scheme.createdAt) scheme.createdAt = now;
    return scheme;
  }

  function sanitizeSavedColorSchemes(list, fallbacks = COLOR_SCHEME_DEFAULTS) {
    if (!Array.isArray(list)) return [];
    const seen = new Set();
    return list
      .map(item => sanitizeColorScheme(item, fallbacks))
      .filter(scheme => {
        if (!scheme.id || seen.has(scheme.id)) return false;
        seen.add(scheme.id);
        return true;
      })
      .slice(0, COLOR_SCHEME_LIMIT);
  }

  function colorSchemesEqual(left, right) {
    if (!left || !right) return false;
    return COLOR_SCHEME_FIELDS.every(field => left.colors?.[field] === right.colors?.[field]);
  }

  function nextColorSchemeName(list, lang = 'ru') {
    const schemes = sanitizeSavedColorSchemes(list);
    const prefix = lang === 'en' ? 'Scheme' : 'Схема';
    let index = schemes.length + 1;
    const existing = new Set(schemes.map(scheme => scheme.name.toLowerCase()));
    while (existing.has(`${prefix} ${index}`.toLowerCase())) index += 1;
    return `${prefix} ${index}`;
  }

  function saveColorScheme(list, incoming, options = {}) {
    const schemes = sanitizeSavedColorSchemes(list);
    const scheme = createColorScheme({
      ...incoming,
      now: options.now,
      fallbacks: options.fallbacks,
    });
    const existingIndex = schemes.findIndex(item => item.id === scheme.id);
    if (existingIndex === -1) {
      const duplicate = schemes.find(item => colorSchemesEqual(item, scheme));
      if (duplicate) {
        return { ok: false, reason: 'duplicate', schemes, scheme: duplicate };
      }
      if (schemes.length >= COLOR_SCHEME_LIMIT) {
        return { ok: false, reason: 'limit', schemes, scheme };
      }
      schemes.push(scheme);
    } else {
      schemes[existingIndex] = { ...schemes[existingIndex], ...scheme };
    }
    return { ok: true, schemes, scheme };
  }

  function applyColorScheme(list, id) {
    const schemes = sanitizeSavedColorSchemes(list);
    return schemes.find(scheme => scheme.id === id) || null;
  }

  function deleteColorScheme(list, id) {
    const schemes = sanitizeSavedColorSchemes(list);
    return schemes.filter(scheme => scheme.id !== id);
  }

  function colorSchemeToSettings(scheme) {
    const safe = sanitizeColorScheme(scheme);
    const settings = {
      accent: safe.colors.accent,
      activeColorSchemeId: safe.id,
    };
    COLOR_SCHEME_FIELDS.forEach(field => {
      settings[SETTINGS_COLOR_KEYS[field]] = safe.colors[field];
    });
    return settings;
  }

  function readColorSchemeFromSettings(settings = {}, fallbacks = COLOR_SCHEME_DEFAULTS) {
    const source = settings && typeof settings === 'object' ? settings : {};
    return {
      accent: normalizeHexColor(source.customColorPrimary || source.accent, fallbacks.accent),
      background: normalizeHexColor(source.customColorBg, fallbacks.background),
      text: normalizeHexColor(source.customColorText, fallbacks.text),
      cards: normalizeHexColor(source.customColorCards, fallbacks.cards),
      borders: normalizeHexColor(source.customColorBorders, fallbacks.borders),
      focus: normalizeHexColor(
        source.customColorFocus || source.customColorPrimary || source.accent,
        fallbacks.focus
      ),
    };
  }

  return {
    COLOR_SCHEME_LIMIT,
    COLOR_SCHEME_NAME_MAX,
    COLOR_SCHEME_FIELDS,
    COLOR_SCHEME_DEFAULTS,
    SETTINGS_COLOR_KEYS,
    normalizeHexColor,
    sanitizeSchemeName,
    sanitizeColorScheme,
    sanitizeSavedColorSchemes,
    createColorSchemeId,
    createColorScheme,
    colorSchemesEqual,
    nextColorSchemeName,
    saveColorScheme,
    applyColorScheme,
    deleteColorScheme,
    colorSchemeToSettings,
    readColorSchemeFromSettings,
  };
});
