(() => {
  const CACHE_KEY = 'votify-workshop-themes-v1';
  const BUILTIN_THEMES = [];

  const state = {
    themes: mergeWithBuiltins(readCache()),
    query: '',
    filter: 'all',
    loading: false,
    loadedAt: 0,
    publishTheme: null,
  };

  function escapeHtml(value) {
    return String(value ?? '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#39;');
  }

  function color(value, fallback) {
    const normalized = String(value || '').trim();
    return /^#[0-9a-f]{6}$/i.test(normalized) ? normalized.toUpperCase() : fallback;
  }

  function httpsUrl(value) {
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

  function cssUrl(value) {
    return httpsUrl(value).replace(/[()'"\\\s;]/g, character => encodeURIComponent(character));
  }

  function cleanTheme(theme = {}) {
    const oneOf = (value, allowed, fallback) => (allowed.includes(value) ? value : fallback);
    const integer = (value, minimum, maximum, fallback) => {
      const number = Number(value);
      return Number.isFinite(number)
        ? Math.max(minimum, Math.min(maximum, Math.round(number)))
        : fallback;
    };
    return {
      primary: color(theme.primary, '#1DB954'),
      background: color(theme.background, '#121212'),
      text: color(theme.text, '#FFFFFF'),
      cards: color(theme.cards, '#181818'),
      borders: color(theme.borders, '#2A2A2A'),
      focus: color(theme.focus, '#1DB954'),
      mode: oneOf(theme.mode, ['dark', 'light', 'system'], 'dark'),
      backgroundPreset: oneOf(
        theme.backgroundPreset,
        [
          'default',
          'grad-1',
          'grad-2',
          'grad-3',
          'grad-4',
          'grad-5',
          'grad-6',
          'grad-7',
          'grad-8',
          'grad-9',
        ],
        'default'
      ),
      backgroundUrl: httpsUrl(theme.backgroundUrl),
      cornerRadius: integer(theme.cornerRadius, 0, 24, 8),
      uiTransparency: integer(theme.uiTransparency, 10, 100, 45),
      backgroundBlur: integer(theme.backgroundBlur, 0, 60, 0),
      particles: oneOf(
        theme.particles,
        ['none', 'snow', 'rain', 'stars', 'dots', 'hearts', 'fireflies', 'sakura', 'network'],
        'none'
      ),
      fontFamily: oneOf(
        theme.fontFamily,
        [
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
        ],
        'inter'
      ),
    };
  }

  function cleanThemeDocument(item) {
    return {
      id: String(item?.id || '').slice(0, 40),
      title: String(item?.title || '').slice(0, 60),
      description: String(item?.description || '').slice(0, 240),
      ownerId: String(item?.ownerId || '').slice(0, 128),
      authorName: String(item?.authorName || 'Пользователь').slice(0, 40),
      downloads: String(item?.downloads != null ? item.downloads : '0'),
      category: String(item?.category || 'themes'),
      theme: cleanTheme(item?.theme),
      createdAt: Number(item?.createdAt) || 0,
      builtIn: item?.builtIn === true,
    };
  }

  function mergeWithBuiltins(themes) {
    const communityThemes = themes.filter(theme => !String(theme.id).startsWith('builtin-'));
    return [...BUILTIN_THEMES, ...communityThemes];
  }

  function readCache() {
    try {
      const cached = JSON.parse(localStorage.getItem(CACHE_KEY) || '[]');
      return Array.isArray(cached) ? cached.map(cleanThemeDocument).slice(0, 100) : [];
    } catch {
      return [];
    }
  }

  function writeCache() {
    try {
      const communityThemes = state.themes.filter(theme => !theme.builtIn).slice(0, 100);
      localStorage.setItem(CACHE_KEY, JSON.stringify(communityThemes));
    } catch {
      // The workshop still works online when local storage is full or unavailable.
    }
  }

  function setStatus(message, kind = '') {
    const element = document.getElementById('workshop-status');
    if (!element) return;
    element.textContent = message;
    element.dataset.kind = kind;
  }

  function installedThemeId() {
    try {
      return JSON.parse(localStorage.getItem('votify-settings') || '{}').workshopThemeId || '';
    } catch {
      return '';
    }
  }

  function previewMarkup(theme, compact = false) {
    const safe = cleanTheme(theme);
    const remoteBackground = cssUrl(safe.backgroundUrl);
    const backgroundStyle = remoteBackground ? `--preview-image:url(${remoteBackground});` : '';
    return `
      <div class="workshop-theme-preview workshop-preview-bg-${safe.backgroundPreset} ${remoteBackground ? 'workshop-preview-has-url' : ''} ${compact ? 'compact' : ''}" style="${backgroundStyle}--preview-bg:${safe.background};--preview-card:${safe.cards};--preview-accent:${safe.primary};--preview-text:${safe.text};--preview-border:${safe.borders};--preview-focus:${safe.focus};--preview-radius:${safe.cornerRadius}px">
        <div class="workshop-preview-sidebar"><span></span><span></span><span></span></div>
        <div class="workshop-preview-content">
          <div class="workshop-preview-topline">
            <div class="workshop-preview-heading"></div>
            <div class="workshop-preview-palette" title="Палитра темы">
              <i style="background:${safe.primary}"></i><i style="background:${safe.background}"></i><i style="background:${safe.cards}"></i><i style="background:${safe.text}"></i><i style="background:${safe.borders}"></i><i style="background:${safe.focus}"></i>
            </div>
          </div>
          <div class="workshop-preview-cards"><span></span><span></span><span></span></div>
          <div class="workshop-preview-player"><i></i><b></b><em></em></div>
        </div>
      </div>`;
  }

  function formatDate(timestamp) {
    if (!timestamp) return 'только что';
    try {
      return new Intl.DateTimeFormat('ru-RU', {
        day: 'numeric',
        month: 'short',
        year: 'numeric',
      }).format(new Date(timestamp));
    } catch {
      return '';
    }
  }

  function renderThemes() {
    const grid = document.getElementById('workshop-grid');
    if (!grid) return;
    const query = state.query.trim().toLocaleLowerCase('ru');
    const currentUser = window.VotifyCloud?.getCurrentUser?.();
    const installedId = installedThemeId();

    let filtered = state.themes.filter(theme => {
      // Category filter
      if (state.filter === 'presets' && theme.category !== 'presets') return false;
      if (state.filter === 'themes' && theme.category !== 'themes' && theme.builtIn) return false;
      if (state.filter === 'backgrounds' && !theme.theme?.backgroundUrl) return false;
      if (state.filter === 'equalizer' && theme.category !== 'equalizer') return false;

      if (!query) return true;
      return `${theme.title} ${theme.description} ${theme.authorName}`
        .toLocaleLowerCase('ru')
        .includes(query);
    });

    if (state.filter === 'popular') {
      filtered = [...filtered].sort((a, b) => parseFloat(b.downloads || 0) - parseFloat(a.downloads || 0));
    }

    if (!filtered.length && query) {
      grid.innerHTML = `
        <div class="workshop-empty">
          <i class="material-icons">search_off</i>
          <h3>Ничего не найдено</h3>
          <p>Попробуйте изменить поисковый запрос или выбрать другую категорию.</p>
        </div>`;
      return;
    }

    if (!filtered.length && !query) {
      grid.innerHTML = `
        <div class="workshop-empty">
          <i class="material-icons">palette</i>
          <h3>В этой категории пока нет тем</h3>
          <p>Будьте первым автором — опубликуйте своё новое оформление!</p>
        </div>`;
      return;
    }

    grid.innerHTML = filtered
      .map(theme => {
        const own = !!currentUser && !currentUser.isAnonymous && theme.ownerId === currentUser.uid;
        const installed = installedId === theme.id;
        const bgUrl = theme.theme?.backgroundUrl;
        const authorHandle = theme.authorName.startsWith('@') ? theme.authorName : `@${theme.authorName}`;
        const showDots = theme.title === 'Состояние' || theme.hasDots;

        return `
          <article class="workshop-card" data-theme-id="${escapeHtml(theme.id)}">
            <div class="workshop-card-preview">
              ${
                bgUrl
                  ? `<img class="workshop-card-img" src="${escapeHtml(bgUrl)}" alt="${escapeHtml(theme.title)}" />`
                  : previewMarkup(theme.theme)
              }
              ${
                showDots
                  ? `<div class="workshop-card-dots">
                      <span class="workshop-card-dot active"></span>
                      <span class="workshop-card-dot"></span>
                      <span class="workshop-card-dot"></span>
                     </div>`
                  : ''
              }
            </div>
            <div class="workshop-card-body">
              <div class="workshop-card-row-top">
                <div class="workshop-card-title-wrap">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="workshop-cube-icon">
                    <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path>
                    <polyline points="3.27 6.96 12 12.01 20.73 6.96"></polyline>
                    <line x1="12" y1="22.08" x2="12" y2="12"></line>
                  </svg>
                  <h3 class="workshop-card-title">${escapeHtml(theme.title)}</h3>
                </div>
                <div class="workshop-card-downloads">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="opacity:0.6;">
                    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                    <polyline points="7 10 12 15 17 10"></polyline>
                    <line x1="12" y1="15" x2="12" y2="3"></line>
                  </svg>
                  <span>${escapeHtml(String(theme.downloads != null ? theme.downloads : '0'))}</span>
                </div>
              </div>
              <div class="workshop-card-author">${escapeHtml(authorHandle)}</div>
              ${theme.description ? `<div class="workshop-card-desc">${escapeHtml(theme.description)}</div>` : ''}
              <div class="workshop-card-actions">
                <button class="workshop-install-pill-btn ${installed ? 'installed' : ''}" data-action="install">
                  <span>${installed ? 'Установлено' : 'Скачать'}</span>
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                    <polyline points="7 10 12 15 17 10"></polyline>
                    <line x1="12" y1="15" x2="12" y2="3"></line>
                  </svg>
                </button>
                <button class="workshop-preview-eye-btn" data-action="preview" title="Предпросмотр темы">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
                    <circle cx="12" cy="12" r="3"></circle>
                  </svg>
                </button>
                ${own ? '<button class="workshop-delete-btn" data-action="delete" title="Удалить"><i class="material-icons">delete_outline</i></button>' : ''}
              </div>
            </div>
          </article>`;
      })
      .join('');
  }

  async function loadThemes(force = false) {
    if (state.loading) return;
    if (!force && state.loadedAt && Date.now() - state.loadedAt < 30000) {
      renderThemes();
      return;
    }
    const cloud = window.VotifyCloud;
    // Always render builtins immediately — even without Firebase
    if (!state.themes.length) {
      state.themes = mergeWithBuiltins(readCache());
    }
    renderThemes();

    if (!cloud) {
      setStatus('Офлайн режим — показаны встроенные темы (4) + локальные', 'success');
      return;
    }
    state.loading = true;
    document.getElementById('workshop-refresh-btn')?.classList.add('loading');
    setStatus(state.themes.length ? 'Обновляем каталог…' : 'Загружаем темы…');
    try {
      await cloud.whenReady();
      if (!cloud.isAvailable || !cloud.isAvailable()) {
        // offline: keep builtins + local cache
        const cached = readCache();
        state.themes = mergeWithBuiltins(cached);
        renderThemes();
        setStatus(`Офлайн режим — встроенных тем: ${BUILTIN_THEMES.length}, локальных: ${cached.length}. Добавьте firebase-config.json для облачных тем.`, 'success');
        return;
      }
      const themes = await cloud.listWorkshopThemes();
      // themes may be empty when offline — keep builtins
      const merged = mergeWithBuiltins((themes || []).map(cleanThemeDocument));
      // If cloud returned nothing, keep at least builtins + cached
      if (!themes || !themes.length) {
        const cached = readCache();
        state.themes = mergeWithBuiltins(cached);
        renderThemes();
        setStatus(`Тем в мастерской: ${state.themes.length} (встроенные + локальные)`, 'success');
      } else {
        state.themes = merged;
        state.loadedAt = Date.now();
        writeCache();
        renderThemes();
        setStatus(`Тем в мастерской: ${state.themes.length}`, 'success');
      }
    } catch (error) {
      console.error('[Workshop] Load error:', error);
      const cached = readCache();
      state.themes = mergeWithBuiltins(cached);
      renderThemes();
      const isOffline = /облачн|синхронизация|не настроена|offline/i.test(error.message || '');
      setStatus(
        isOffline
          ? `Офлайн режим — встроенных: ${BUILTIN_THEMES.length}, локальных: ${cached.length}`
          : (state.themes.length
            ? 'Не удалось обновить каталог — показана сохранённая копия'
            : window.VotifyCloud?.friendlyError?.(error) || 'Не удалось загрузить мастерскую'),
        isOffline ? 'success' : 'error'
      );
    } finally {
      state.loading = false;
      document.getElementById('workshop-refresh-btn')?.classList.remove('loading');
    }
  }

  function updatePublishAccess() {
    const button = document.getElementById('workshop-publish-btn');
    if (!button) return;
    const cloud = window.VotifyCloud;
    const user = cloud?.getCurrentUser?.();
    const isOffline = cloud && typeof cloud.isAvailable === 'function' && !cloud.isAvailable();
    if (isOffline) {
      button.title = user ? 'Опубликовать тему локально (офлайн)' : 'Войдите как гость чтобы публиковать локально';
      button.disabled = !user;
      return;
    }
    button.disabled = false;
    button.title = !user
      ? 'Сначала войдите в аккаунт'
      : user.isAnonymous
        ? 'Привяжите постоянный аккаунт для публикации в облако'
        : 'Опубликовать текущую тему';
  }

  function closePublishModal() {
    const overlay = document.getElementById('workshop-publish-overlay');
    if (overlay) overlay.style.display = 'none';
    const error = document.getElementById('workshop-publish-error');
    if (error) error.textContent = '';
    state.publishTheme = null;
  }

  function openPublishModal() {
    const cloud = window.VotifyCloud;
    const user = cloud?.getCurrentUser?.();
    const isOffline = cloud && typeof cloud.isAvailable === 'function' && !cloud.isAvailable();
    if (!user) {
      cloud?.openAuth?.('auth-register');
      setStatus(isOffline ? 'Войдите как гость чтобы публиковать локально' : 'Зарегистрируйтесь, чтобы публиковать темы', 'error');
      return;
    }
    if (!isOffline && user.isAnonymous) {
      cloud?.openProfile?.();
      setStatus('Привяжите гостевой профиль к Email или Google для публикации в облако', 'error');
      return;
    }
    const theme = window.VotifyThemeWorkshop?.getCurrentTheme?.();
    if (!theme) {
      setStatus('Не удалось получить текущую тему', 'error');
      return;
    }
    state.publishTheme = cleanTheme(theme);
    const preview = document.getElementById('workshop-publish-preview');
    if (preview) preview.innerHTML = previewMarkup(state.publishTheme, true);
    const overlay = document.getElementById('workshop-publish-overlay');
    if (overlay) overlay.style.display = 'flex';
    window.setTimeout(() => document.getElementById('workshop-theme-title')?.focus(), 0);
  }

  async function publishTheme() {
    const button = document.getElementById('workshop-publish-confirm');
    const errorElement = document.getElementById('workshop-publish-error');
    const title = document.getElementById('workshop-theme-title')?.value.trim() || '';
    const description = document.getElementById('workshop-theme-description')?.value.trim() || '';
    if (errorElement) errorElement.textContent = '';
    if (title.length < 3) {
      if (errorElement) errorElement.textContent = 'Введите название минимум из 3 символов';
      return;
    }
    if (!state.publishTheme) return;
    button.disabled = true;
    button.classList.add('busy');
    try {
      await window.VotifyCloud.publishWorkshopTheme({
        title,
        description,
        theme: state.publishTheme,
      });
      closePublishModal();
      document.getElementById('workshop-theme-title').value = '';
      document.getElementById('workshop-theme-description').value = '';
      setStatus('Тема опубликована', 'success');
      await loadThemes(true);
    } catch (error) {
      if (errorElement) {
        errorElement.textContent =
          window.VotifyCloud?.friendlyError?.(error) || error.message || 'Ошибка публикации';
      }
    } finally {
      button.disabled = false;
      button.classList.remove('busy');
    }
  }

  async function handleCardAction(event) {
    const actionButton = event.target.closest('[data-action]');
    const card = event.target.closest('.workshop-card');
    if (!actionButton || !card) return;

    if (actionButton.dataset.action === 'install-default') {
      resetToDefaultTheme();
      return;
    }

    const theme = state.themes.find(item => item.id === card.dataset.themeId);
    if (!theme) return;

    if (actionButton.dataset.action === 'install') {
      window.VotifyThemeWorkshop?.applyTheme?.(theme.theme, {
        id: theme.id,
        title: theme.title,
      });
      renderThemes();
      setStatus(`Тема «${theme.title}» установлена и сохранена`, 'success');
      return;
    }

    if (actionButton.dataset.action === 'preview') {
      window.VotifyThemeWorkshop?.applyTheme?.(theme.theme, {
        id: theme.id,
        title: theme.title,
      });
      setStatus(`Предпросмотр темы «${theme.title}»`, 'success');
      return;
    }

    if (actionButton.dataset.action === 'delete') {
      if (!window.confirm(`Удалить тему «${theme.title}» из мастерской?`)) return;
      actionButton.disabled = true;
      try {
        await window.VotifyCloud.deleteWorkshopTheme(theme.id);
        state.themes = state.themes.filter(item => item.id !== theme.id);
        writeCache();
        renderThemes();
        setStatus('Тема удалена', 'success');
      } catch (error) {
        actionButton.disabled = false;
        setStatus(
          window.VotifyCloud?.friendlyError?.(error) || error.message || 'Не удалось удалить тему',
          'error'
        );
      }
    }
  }

  function resetToDefaultTheme() {
    // Clear workshop theme and restore default black & white
    try {
      const api = window.VotifyColorSchemes;
      if (api && window.VotifyThemeWorkshop) {
        // Reset to default black & white mono theme
        const defaultTheme = {
          primary: '#FFFFFF',
          background: '#121212',
          text: '#FFFFFF',
          cards: '#181818',
          borders: '#282828',
          focus: '#FFFFFF',
          mode: 'contrast',
          backgroundPreset: 'default',
          backgroundUrl: '',
          cornerRadius: 8,
          uiTransparency: 100,
          backgroundBlur: 0,
          particles: 'none',
          fontFamily: 'system'
      };
        window.VotifyThemeWorkshop.applyTheme(defaultTheme, { id: '', title: 'Стандартная тема' });
        const settingsStr = localStorage.getItem('votify-settings');
        if (settingsStr) {
          const settings = JSON.parse(settingsStr);
          settings.activeColorSchemeId = '';
          settings.workshopThemeId = '';
          settings.workshopThemeTitle = '';
          localStorage.setItem('votify-settings', JSON.stringify(settings));
        }
        setStatus('Возвращена стандартная чёрно-белая тема', 'success');
        renderThemes();
      }
    } catch (e) {
      console.error('Failed to reset to default theme', e);
      setStatus('Не удалось сбросить тему', 'error');
    }
  }

  function wireUi() {
    document.getElementById('workshop-search-input')?.addEventListener('input', event => {
      state.query = event.target.value || '';
      renderThemes();
    });

    document.querySelectorAll('.workshop-cat-pill').forEach(pill => {
      pill.addEventListener('click', () => {
        document.querySelectorAll('.workshop-cat-pill').forEach(p => p.classList.remove('active'));
        pill.classList.add('active');
        state.filter = pill.dataset.filter || 'all';
        renderThemes();
      });
    });

    document
      .getElementById('workshop-refresh-btn')
      ?.addEventListener('click', () => loadThemes(true));
    document.getElementById('workshop-default-btn')?.addEventListener('click', resetToDefaultTheme);
    document.getElementById('workshop-publish-btn')?.addEventListener('click', openPublishModal);
    document.getElementById('workshop-grid')?.addEventListener('click', handleCardAction);
    document.getElementById('workshop-publish-close')?.addEventListener('click', closePublishModal);
    document
      .getElementById('workshop-publish-cancel')
      ?.addEventListener('click', closePublishModal);
    document.getElementById('workshop-publish-confirm')?.addEventListener('click', publishTheme);
    document.getElementById('workshop-publish-overlay')?.addEventListener('click', event => {
      if (event.target === event.currentTarget) closePublishModal();
    });
    document.addEventListener('keydown', event => {
      if (event.key === 'Escape') closePublishModal();
    });
    window.addEventListener('votify:workshop-open', () => loadThemes());
    window.addEventListener('votify:auth-changed', () => {
      updatePublishAccess();
      renderThemes();
    });
    updatePublishAccess();
    renderThemes();
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', wireUi);
  else wireUi();
})();
