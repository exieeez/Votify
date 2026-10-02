(() => {
  const DEFAULT_AVATAR = "data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='128' height='128' viewBox='0 0 128 128'><rect width='128' height='128' rx='64' fill='%23262626'/><path d='M64 28a20 20 0 1 0 0 40 20 20 0 0 0 0-40zm0 48c-22.1 0-40 13.4-40 30v4h80v-4c0-16.6-17.9-30-40-30z' fill='%23888888'/></svg>";

  const state = {
    initialized: false,
    available: false,
    error: null,
    auth: null,
    db: null,
    user: null,
    profile: null,
  };
  const authListeners = new Set();
  let initialAuthStateHandled = false;

  const ready = initialize();

  function dispatchAuthState() {
    const detail = { user: publicUser(state.user), profile: state.profile };
    authListeners.forEach(listener => listener(detail));
    window.dispatchEvent(new CustomEvent('votify:auth-changed', { detail }));
    updateAccountUi();
  }

  function publicUser(user) {
    if (!user) return null;
    return {
      uid: user.uid,
      email: user.email || '',
      displayName: user.displayName || '',
      isAnonymous: !!user.isAnonymous,
      emailVerified: !!user.emailVerified,
    };
  }

  const PROFILE_FRAMES = ['none', 'cyberpunk', 'neon', 'sakura', 'fire', 'galaxy', 'pixel', 'magic', 'heart', 'rainbow', 'glow', 'double'];

  function cleanProfile(profile = {}) {
    const bannerRaw = String(profile.banner || '').trim();
    const cursorRaw = String(profile.cursor || '').trim();
    const showcaseImgRaw = String(profile.showcaseImage || '').trim();
    const handleRaw = profile.handle !== undefined
      ? String(profile.handle || '').trim().replace(/^@/, '').toLowerCase().slice(0, 30)
      : undefined;

    const favTrackRaw = profile.favTrack && typeof profile.favTrack === 'object' && profile.favTrack.title ? {
      id: String(profile.favTrack.id || '').slice(0, 100),
      title: String(profile.favTrack.title || '').slice(0, 120),
      artist: String(profile.favTrack.artist || '').slice(0, 120),
      cover: String(profile.favTrack.cover || '').slice(0, 150000),
      duration: Number(profile.favTrack.duration) || 0,
    } : (profile.favTrack === null ? null : undefined);

    return {
      displayName: String(profile.displayName || '')
        .trim()
        .slice(0, 40),
      ...(handleRaw !== undefined ? { handle: handleRaw } : {}),
      avatar: String(profile.avatar || '').startsWith('data:image/') || String(profile.avatar || '').startsWith('https://')
        ? String(profile.avatar).slice(0, 300000)
        : '',
      about: String(profile.about || '').trim().slice(0, 300),
      banner: bannerRaw.startsWith('data:image/')
        ? bannerRaw.slice(0, 500000)
        : (bannerRaw.startsWith('http://') || bannerRaw.startsWith('https://'))
          ? bannerRaw.slice(0, 1000)
          : oneOf(bannerRaw, ['grad-1','grad-2','grad-3','grad-4','grad-5','grad-6','grad-7','grad-8','grad-9',''], ''),
      frame: oneOf(profile.frame, PROFILE_FRAMES, 'none'),
      cursor: cursorRaw.startsWith('data:image/') ? cursorRaw.slice(0, 80000) : '',
      showcaseImage: showcaseImgRaw.startsWith('data:image/')
        ? showcaseImgRaw.slice(0, 200000)
        : (showcaseImgRaw.startsWith('http://') || showcaseImgRaw.startsWith('https://'))
          ? showcaseImgRaw.slice(0, 1000)
          : '',
      showcaseTitle: String(profile.showcaseTitle || '').trim().slice(0, 60),
      showcaseText: String(profile.showcaseText || '').trim().slice(0, 300),
      ...(favTrackRaw !== undefined ? { favTrack: favTrackRaw } : {}),
    };
  }

  function oneOf(value, allowed, fallback) {
    return allowed.includes(value) ? value : fallback;
  }

  function color(value, fallback) {
    const normalized = String(value || '').trim();
    return /^#[0-9a-f]{6}$/i.test(normalized) ? normalized.toUpperCase() : fallback;
  }

  function integer(value, minimum, maximum, fallback) {
    const number = Number(value);
    return Number.isFinite(number)
      ? Math.max(minimum, Math.min(maximum, Math.round(number)))
      : fallback;
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

  function cleanCoreWorkshopTheme(theme = {}) {
    return {
      primary: color(theme.primary, '#1DB954'),
      background: color(theme.background, '#121212'),
      text: color(theme.text, '#FFFFFF'),
      cards: color(theme.cards, '#181818'),
      borders: color(theme.borders, '#2A2A2A'),
      focus: color(theme.focus, '#1DB954'),
      mode: oneOf(theme.mode, ['dark', 'light', 'system', 'contrast', 'midnight'], 'dark'),
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
        theme.particles || theme.bgParticles,
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
          'jakarta',
          'default',
        ],
        'inter'
      ),
    };
  }

  function cleanWorkshopTheme(theme = {}) {
    const boolean = (value, fallback = true) => (typeof value === 'boolean' ? value : (value !== undefined ? !!value : fallback));
    const str = (value, fallback = '') => (typeof value === 'string' && value.trim() ? value.trim() : fallback);

    return {
      primary: color(theme.primary, '#1DB954'),
      background: color(theme.background, '#121212'),
      text: color(theme.text, '#FFFFFF'),
      cards: color(theme.cards, '#181818'),
      borders: color(theme.borders, '#2A2A2A'),
      focus: color(theme.focus, '#1DB954'),
      mode: oneOf(theme.mode, ['dark', 'light', 'system', 'contrast', 'midnight'], 'dark'),
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
      uiScale: integer(theme.uiScale, 50, 150, 100),
      backgroundBlur: integer(theme.backgroundBlur, 0, 60, 0),
      dynamicPlayerBg: boolean(theme.dynamicPlayerBg, true),
      accentGlow: boolean(theme.accentGlow, true),
      cursorGlow: boolean(theme.cursorGlow, false),
      animations: boolean(theme.animations, true),
      particles: oneOf(
        theme.particles || theme.bgParticles,
        ['none', 'snow', 'rain', 'stars', 'dots', 'hearts', 'fireflies', 'sakura', 'network'],
        'none'
      ),
      particleCount: integer(theme.particleCount, 10, 200, 50),
      particleSpeed: integer(theme.particleSpeed, 5, 50, 15),
      particleSize: integer(theme.particleSize, 1, 10, 3),
      particleParallax: boolean(theme.particleParallax, true),
      perfParticles: boolean(theme.perfParticles, true),
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
          'jakarta',
          'default',
        ],
        'inter'
      ),
      fontSize: str(theme.fontSize, '16px'),
      cursorPreset: oneOf(
        theme.cursorPreset,
        ['none', 'glow', 'trail', 'fire', 'matrix', 'rainbow', 'heart', 'star', 'cat', 'custom'],
        'none'
      ),
      playerStyle: oneOf(
        theme.playerStyle,
        ['standard', 'compact', 'vinyl', 'minimal', 'cards', 'full', 'modern', 'glass', 'large', 'neon'],
        'standard'
      ),
      playerTitleAlign: oneOf(theme.playerTitleAlign, ['left', 'center', 'right'], 'center'),
      playerSliderType: oneOf(theme.playerSliderType, ['normal', 'thin', 'wave', 'ios'], 'normal'),
      playerCoverShape: str(theme.playerCoverShape, 'Закруглённый квадрат'),
      coverAnimation: str(theme.coverAnimation, 'none'),
      coverEffects: str(theme.coverEffects, 'none'),
      dynamicAccentColor: boolean(theme.dynamicAccentColor, true),
      coverInPlayer: boolean(theme.coverInPlayer, true),
      miniBg: oneOf(theme.miniBg, ['theme', 'cover', 'transparent', 'accent', 'dark', 'cover-color'], 'theme'),
      miniProgress: oneOf(theme.miniProgress, ['line', 'circle', 'hidden', 'wave', 'dots', 'bg', 'cover'], 'line'),
      miniCover: oneOf(theme.miniCover, ['default', 'hidden', 'circle', 'rounded', 'round', 'square'], 'default'),
      miniBorder: oneOf(theme.miniBorder, ['default', 'none', 'accent', 'glow', 'capsule'], 'default'),
    };
  }

  async function initialize() {
    try {
      if (!window.firebase) throw new Error('Firebase SDK не загружен');
      let config = null;
      try {
        const response = await fetch('/api/firebase/config', { cache: 'no-store' });
        if (response.ok) {
          const payload = await response.json().catch(() => ({}));
          config = payload.config;
        }
      } catch (e) {
        console.warn('[Firebase] fetch config error:', e);
      }

      if (!config) {
        config = {
          apiKey: 'AIzaSyBSRslJWfFlpgX3Sm3_RbXEILtj2PL0A-k',
          authDomain: 'votify-f461a.firebaseapp.com',
          projectId: 'votify-f461a',
          storageBucket: 'votify-f461a.firebasestorage.app',
          messagingSenderId: '283427548411',
          appId: '1:283427548411:web:dc7c5cfa55240448d6d45e',
        };
      }

      const app = window.firebase.apps.length
        ? window.firebase.app()
        : window.firebase.initializeApp(config);
      state.auth = window.firebase.auth(app);
      state.db = window.firebase.firestore(app);
      await state.auth.setPersistence(window.firebase.auth.Auth.Persistence.LOCAL);
      state.available = true;

      let profileUnsub = null;
      let usersUnsub = null;
      let libraryUnsub = null;

      state.auth.onAuthStateChanged(async user => {
        if (profileUnsub) { profileUnsub(); profileUnsub = null; }
        if (usersUnsub) { usersUnsub(); usersUnsub = null; }
        if (libraryUnsub) { libraryUnsub(); libraryUnsub = null; }

        state.user = user || null;
        state.profile = user ? await ensureProfile(user).catch(() => null) : null;
        dispatchAuthState();
        updateAccountUi();

        if (user && !user.isAnonymous) {
          const onDocUpdate = (snap) => {
            if (!snap || !snap.exists) return;
            const d = snap.data() || {};
            const cur = state.profile || {};
            const effDisplayName = d.displayName || d.name || cur.displayName || user.displayName || 'Пользователь';
            const effHandle = d.handle || d.username || cur.handle || 'user';
            const effAvatar = d.avatar || d.photoUrl || cur.avatar || '';
            const effAbout = d.about || d.bio || cur.about || '';
            const effBanner = d.banner !== undefined ? d.banner : (cur.banner || '');
            const effFavTrack = d.favTrack !== undefined ? d.favTrack : (cur.favTrack || null);

            state.profile = {
              ...cur,
              displayName: effDisplayName,
              name: effDisplayName,
              handle: String(effHandle).trim().replace(/^@/, '').toLowerCase(),
              avatar: effAvatar,
              photoUrl: effAvatar,
              about: effAbout,
              bio: effAbout,
              banner: effBanner,
              favTrack: effFavTrack,
            };
            try {
              localStorage.setItem('votifyLocalProfile', JSON.stringify(state.profile));
            } catch (e) {}
            dispatchAuthState();
            updateAccountUi();
          };

          try {
            profileUnsub = profileRef(user.uid).onSnapshot(onDocUpdate, () => {});
            usersUnsub = firestoreProfileRef(user.uid).onSnapshot(onDocUpdate, () => {});
            libraryUnsub = syncRef(user.uid, 'library').onSnapshot(snap => {
              if (!snap || !snap.exists) return;
              const d = snap.data() || {};
              window.dispatchEvent(new CustomEvent('votify:cloud-library-updated', {
                detail: { playlists: d.playlists, updatedAt: d.updatedAt }
              }));
            }, () => {});
          } catch (e) {}
        }

        if (!initialAuthStateHandled) {
          initialAuthStateHandled = true;
          if (!user) window.setTimeout(() => openAuth('auth-login'), 0);
        }
      });
    } catch (error) {
      state.error = error;
      console.warn('[Firebase]', error.message || error);
      updateAccountUi();
      // Браузерное превью (например, песочница Arena): firebase-конфига нет,
      // но окно входа всё равно показываем, чтобы UI первого запуска был виден.
      const isElectron = /electron/i.test(navigator.userAgent || '');
      const isPreviewHost = /(^|\.)e2b\.app$/.test(location.hostname);
      if (!isElectron && isPreviewHost) {
        window.setTimeout(() => openAuth('auth-login'), 200);
      }
    } finally {
      state.initialized = true;
    }
    return state.available;
  }

  async function requireCloud() {
    await ready;
    if (!state.available || !state.auth || !state.db) {
      throw new Error(state.error?.message || 'Облачная синхронизация не настроена');
    }
  }

  async function requireUser() {
    await requireCloud();
    const user = state.auth.currentUser;
    if (!user) throw new Error('Сначала войдите в аккаунт');
    return user;
  }

  async function requirePermanentUser() {
    const user = await requireUser();
    if (user.isAnonymous) throw new Error('Для публикации зарегистрируйте постоянный аккаунт');
    return user;
  }

  function profileRef(uid) {
    return state.db.collection('users').doc(uid);
  }

  function firestoreProfileRef(uid) {
    return state.db.collection('profiles').doc(uid);
  }

  function usernameRef(handleLower) {
    return state.db.collection('usernames').doc(handleLower);
  }

  function syncRef(uid, name) {
    return profileRef(uid).collection('sync').doc(name);
  }

  async function reserveUsername(handle, uid) {
    const handleLower = String(handle || '').toLowerCase().trim().replace(/^@/, '');
    if (!handleLower) return '';
    if (!/^[a-z0-9_]{3,20}$/.test(handleLower)) {
      throw new Error('Юзернейм должен состоять из 3-20 символов (латинские буквы, цифры, _)');
    }
    const ref = usernameRef(handleLower);
    try {
      const snap = await ref.get();
      if (snap && snap.exists) {
        const data = snap.data() || {};
        if (data.uid && data.uid !== uid) {
          const err = new Error('Этот юзернейм уже занят');
          err.code = 'ALREADY_EXISTS';
          throw err;
        }
      }
    } catch (err) {
      if (err?.code === 'ALREADY_EXISTS' || err?.message?.includes('уже занят')) {
        throw err;
      }
      console.warn('reserveUsername read check failed (skipping check):', err);
    }

    try {
      await ref.set({
        uid,
        createdAt: window.firebase.firestore.FieldValue.serverTimestamp(),
      }, { merge: true });
    } catch (err) {
      console.warn('reserveUsername write failed (proceeding with local handle):', err);
    }
    return handleLower;
  }

  async function getUserProfile(uid) {
    if (!uid || !state.db) return null;
    try {
      const [profSnap, userSnap, libSnap] = await Promise.all([
        firestoreProfileRef(uid).get().catch(() => null),
        profileRef(uid).get().catch(() => null),
        syncRef(uid, 'library').get().catch(() => null),
      ]);
      let d = {};
      if (userSnap && userSnap.exists) d = { ...d, ...(userSnap.data() || {}) };
      if (profSnap && profSnap.exists) d = { ...d, ...(profSnap.data() || {}) };

      let playlists = d.playlists || [];
      if ((!playlists || playlists.length === 0) && libSnap && libSnap.exists) {
        const rawPl = libSnap.data()?.playlists;
        if (rawPl && typeof rawPl === 'object') {
          playlists = Object.keys(rawPl).map(k => {
            const tracks = Array.isArray(rawPl[k]) ? rawPl[k] : (rawPl[k]?.tracks || []);
            const cover = rawPl[k]?.cover || (tracks[0] && tracks[0].cover) || '';
            return {
              name: k,
              count: tracks.length,
              cover,
            };
          });
        }
      }

      const name = d.displayName || d.username || (d.email ? d.email.split('@')[0] : 'Пользователь');
      const rawHandle = d.handle || d.username || d.displayName || (d.email ? d.email.split('@')[0] : uid.slice(0, 8));
      const cleanHandle = String(rawHandle).trim().replace(/^@/, '').toLowerCase();
      const ava = (d.avatar && !d.avatar.includes('unsplash.com')) ? d.avatar : ((d.photoUrl && !d.photoUrl.includes('unsplash.com')) ? d.photoUrl : getAvatarUrl(name));

      return {
        uid,
        name,
        handle: '@' + (cleanHandle || uid.slice(0, 8)),
        avatar: ava,
        about: d.about || d.bio || '',
        banner: d.banner || '',
        frame: d.frame || 'none',
        showcaseImage: d.showcaseImage || '',
        showcaseTitle: d.showcaseTitle || '',
        showcaseText: d.showcaseText || '',
        favTrack: d.favTrack || null,
        playlists,
      };
    } catch {
      return null;
    }
  }

  async function ensureProfile(user) {
    if (!user) return null;
    const reference = profileRef(user.uid);
    const fsProfRef = firestoreProfileRef(user.uid);
    
    let data = {};
    const [userSnap, profSnap] = await Promise.all([
      reference.get().catch(() => null),
      fsProfRef.get().catch(() => null),
    ]);

    if (userSnap && userSnap.exists) data = { ...data, ...(userSnap.data() || {}) };
    if (profSnap && profSnap.exists) data = { ...data, ...(profSnap.data() || {}) };

    const effectiveDisplayName = data.displayName || data.name || user.displayName || (user.email ? user.email.split('@')[0] : 'Гость');
    const effectiveHandle = data.handle || data.username || (user.email ? user.email.split('@')[0] : 'guest');

    const profile = {
      displayName: effectiveDisplayName,
      name: effectiveDisplayName,
      handle: String(effectiveHandle).trim().replace(/^@/, '').toLowerCase(),
      email: user.email || '',
      isAnonymous: !!user.isAnonymous,
      avatar: data.avatar || data.photoUrl || '',
      photoUrl: data.avatar || data.photoUrl || '',
      about: data.about || data.bio || '',
      bio: data.about || data.bio || '',
      banner: data.banner || '',
      frame: data.frame || 'none',
      cursor: data.cursor || '',
      showcaseImage: data.showcaseImage || '',
      showcaseTitle: data.showcaseTitle || '',
      showcaseText: data.showcaseText || '',
      favTrack: data.favTrack || null,
      createdAt: data.createdAt || window.firebase.firestore.FieldValue.serverTimestamp(),
      updatedAt: window.firebase.firestore.FieldValue.serverTimestamp(),
    };

    if (!userSnap?.exists || !profSnap?.exists) {
      await Promise.all([
        reference.set(profile, { merge: true }).catch(() => {}),
        fsProfRef.set(profile, { merge: true }).catch(() => {}),
      ]);
    }

    try {
      localStorage.setItem('votifyLocalProfile', JSON.stringify(profile));
    } catch (e) {}

    return profile;
  }

  async function register(email, password, displayName) {
    await requireCloud();
    const handleCandidate = String(displayName || email.split('@')[0]).trim();
    const handleLower = handleCandidate.toLowerCase().replace(/^@/, '');

    if (handleLower) {
      const checkSnap = await usernameRef(handleLower).get().catch(() => null);
      if (checkSnap && checkSnap.exists) {
        const err = new Error('Этот юзернейм уже занят');
        err.code = 'ALREADY_EXISTS';
        throw err;
      }
    }

    const current = state.auth.currentUser;
    let credential;
    if (current?.isAnonymous) {
      const emailCredential = window.firebase.auth.EmailAuthProvider.credential(email, password);
      credential = await current.linkWithCredential(emailCredential);
    } else {
      credential = await state.auth.createUserWithEmailAndPassword(email, password);
    }
    const name = String(displayName || email.split('@')[0])
      .trim()
      .slice(0, 40);
    await credential.user.updateProfile({ displayName: name });
    const uid = credential.user.uid;

    let reservedHandle = '';
    if (handleLower) {
      try {
        reservedHandle = await reserveUsername(handleLower, uid);
      } catch (e) {
        if (e.code === 'ALREADY_EXISTS') throw e;
      }
    }

    const profileData = {
      displayName: name,
      handle: reservedHandle || handleLower || uid.slice(0, 8),
      email: credential.user.email || email,
      isAnonymous: false,
      updatedAt: window.firebase.firestore.FieldValue.serverTimestamp(),
    };
    await profileRef(uid).set(profileData, { merge: true });
    await firestoreProfileRef(uid).set(profileData, { merge: true }).catch(() => {});
    state.profile = await ensureProfile(credential.user);
    dispatchAuthState();
    return publicUser(credential.user);
  }

  async function signIn(email, password) {
    await requireCloud();
    const credential = await state.auth.signInWithEmailAndPassword(email, password);
    return publicUser(credential.user);
  }

  async function signInWithGoogle() {
    await requireCloud();
    if (!window.electronAPI?.signInWithGoogle) {
      throw new Error('Вход через Google доступен только в приложении Votify');
    }
    const oauthResult = await window.electronAPI.signInWithGoogle();
    if (oauthResult?.error) throw new Error(oauthResult.error);
    if (!oauthResult?.tokens?.idToken) throw new Error('Google не вернул данные аккаунта');

    const googleCredential = window.firebase.auth.GoogleAuthProvider.credential(
      oauthResult.tokens.idToken,
      oauthResult.tokens.accessToken || null
    );
    const current = state.auth.currentUser;
    const credential = current?.isAnonymous
      ? await current.linkWithCredential(googleCredential)
      : await state.auth.signInWithCredential(googleCredential);
    const user = credential.user;
    const displayName = String(user.displayName || user.email?.split('@')[0] || 'Пользователь')
      .trim()
      .slice(0, 40);
    await profileRef(user.uid).set(
      {
        displayName,
        email: user.email || '',
        isAnonymous: false,
        updatedAt: window.firebase.firestore.FieldValue.serverTimestamp(),
      },
      { merge: true }
    );
    state.user = user;
    state.profile = await ensureProfile(user);
    dispatchAuthState();
    return publicUser(user);
  }

  async function signInAsGuest() {
    await requireCloud();
    const current = state.auth.currentUser;
    if (current) return publicUser(current);
    const credential = await state.auth.signInAnonymously();
    return publicUser(credential.user);
  }

  async function sendPasswordReset(email) {
    await requireCloud();
    await state.auth.sendPasswordResetEmail(email);
  }

  async function signOut() {
    try {
      if (state.auth && typeof state.auth.signOut === 'function') {
        await state.auth.signOut();
      }
    } catch (err) {
      console.warn('signOut Firebase error (proceeding with local cleanup):', err);
    }
    state.user = null;
    state.profile = null;
    try {
      localStorage.removeItem('votify-user-profile');
    } catch (e) {}
    notifyAuth();
    updateAccountUi();
  }

  async function saveProfile(profile) {
    const existing = state.profile || {};
    const merged = {
      displayName: profile.displayName !== undefined ? profile.displayName : (existing.displayName || existing.name || ''),
      handle: profile.handle !== undefined ? profile.handle : existing.handle,
      avatar: profile.avatar !== undefined ? profile.avatar : (existing.avatar || existing.photoUrl || ''),
      about: profile.about !== undefined ? profile.about : (existing.about || existing.bio || ''),
      banner: profile.banner !== undefined ? profile.banner : (existing.banner || ''),
      frame: profile.frame !== undefined ? profile.frame : (existing.frame || 'none'),
      favTrack: profile.favTrack !== undefined ? profile.favTrack : (existing.favTrack || null),
      cursor: profile.cursor !== undefined ? profile.cursor : (existing.cursor || ''),
      showcaseImage: profile.showcaseImage !== undefined ? profile.showcaseImage : (existing.showcaseImage || ''),
      showcaseTitle: profile.showcaseTitle !== undefined ? profile.showcaseTitle : (existing.showcaseTitle || ''),
      showcaseText: profile.showcaseText !== undefined ? profile.showcaseText : (existing.showcaseText || ''),
    };
    const safe = cleanProfile(merged);
    const user = state.auth?.currentUser;
    if (user) {
      const uid = user.uid;
      const existingHandle = state.profile?.handle || '';
      let handleToSave = safe.handle !== undefined ? safe.handle : existingHandle;
      handleToSave = String(handleToSave).toLowerCase().trim().replace(/^@/, '');
      if (handleToSave && /^[a-z0-9_]{3,20}$/.test(handleToSave)) {
        if (handleToSave !== existingHandle) {
          await reserveUsername(handleToSave, uid);
          if (existingHandle) {
            try {
              const oldRef = usernameRef(existingHandle);
              const oldSnap = await oldRef.get();
              if (oldSnap.exists && oldSnap.data()?.uid === uid) {
                await oldRef.delete();
              }
            } catch (e) {}
          }
        }
        safe.handle = handleToSave;
      } else if (existingHandle) {
        safe.handle = String(existingHandle).toLowerCase().trim().replace(/^@/, '');
      }
      if (safe.displayName && safe.displayName !== user.displayName) {
        await user.updateProfile({ displayName: safe.displayName }).catch(() => {});
      }
      const payload = {
        ...safe,
        name: safe.displayName,
        displayName: safe.displayName,
        avatar: safe.avatar,
        photoUrl: safe.avatar,
        about: safe.about,
        bio: safe.about,
        email: user.email || '',
        isAnonymous: !!user.isAnonymous,
        updatedAt: window.firebase.firestore.FieldValue.serverTimestamp(),
      };
      await Promise.all([
        profileRef(uid).set(payload, { merge: true }).catch(() => {}),
        firestoreProfileRef(uid).set(payload, { merge: true }).catch(() => {}),
      ]).catch(() => {});
    }
    const currentLocal = JSON.parse(localStorage.getItem('votifyLocalProfile') || '{}');
    const updatedLocal = { ...currentLocal, ...safe, name: safe.displayName };
    try {
      localStorage.setItem('votifyLocalProfile', JSON.stringify(updatedLocal));
    } catch (e) {}
    state.profile = { ...(state.profile || {}), ...updatedLocal };
    dispatchAuthState();
    updateAccountUi();
    return state.profile;
  }

  async function searchUsers(query) {
    const q = String(query || '').trim().toLowerCase().replace(/^@/, '');
    if (!q) return [];

    const resultsMap = new Map();

    // 1. Search local friends
    try {
      const localFriends = JSON.parse(localStorage.getItem('votifyLocalFriends') || '[]');
      localFriends.forEach(f => {
        const name = String(f.name || '').toLowerCase();
        const handle = String(f.handle || '').toLowerCase();
        if (name.includes(q) || handle.includes(q)) {
          resultsMap.set(f.uid, f);
        }
      });
    } catch (e) {}

    // 2. Search Firebase if connected
    if (state.available && state.db) {
      try {
        // 2a. Search profiles collection
        const profSnap = await state.db.collection('profiles').limit(40).get().catch(() => null);
        if (profSnap) {
          profSnap.forEach(doc => {
            const d = doc.data() || {};
            const name = String(d.displayName || d.username || '').toLowerCase();
            const handle = String(d.handle || d.username || '').toLowerCase();
            if (name.includes(q) || handle.includes(q)) {
              const h = d.handle || d.username || d.displayName || (d.email ? d.email.split('@')[0] : doc.id.slice(0, 8));
              const dispName = d.displayName || d.username || h;
              const ava = (d.avatar && !d.avatar.includes('unsplash.com')) ? d.avatar : ((d.photoUrl && !d.photoUrl.includes('unsplash.com')) ? d.photoUrl : getAvatarUrl(dispName));
              resultsMap.set(doc.id, {
                uid: doc.id,
                name: dispName,
                handle: '@' + String(h).trim().replace(/^@/, '').toLowerCase(),
                avatar: ava,
                about: d.about || d.bio || '',
              });
            }
          });
        }

        // 2b. Search users collection
        const usersSnap = await state.db.collection('users').limit(40).get().catch(() => null);
        if (usersSnap) {
          usersSnap.forEach(doc => {
            if (resultsMap.has(doc.id)) return;
            const d = doc.data() || {};
            const name = String(d.displayName || d.username || '').toLowerCase();
            const email = String(d.email || '').toLowerCase();
            const handle = String(d.handle || d.username || '').toLowerCase();
            if (name.includes(q) || handle.includes(q) || email.includes(q)) {
              const h = d.handle || d.username || d.displayName || (d.email ? d.email.split('@')[0] : doc.id.slice(0, 8));
              const dispName = d.displayName || d.username || h;
              const ava = (d.avatar && !d.avatar.includes('unsplash.com')) ? d.avatar : ((d.photoUrl && !d.photoUrl.includes('unsplash.com')) ? d.photoUrl : getAvatarUrl(dispName));
              resultsMap.set(doc.id, {
                uid: doc.id,
                name: dispName,
                handle: '@' + String(h).trim().replace(/^@/, '').toLowerCase(),
                avatar: ava,
                about: d.about || d.bio || '',
              });
            }
          });
        }

        // 2c. Search usernames collection
        const uSnap = await usernameRef(q).get().catch(() => null);
        if (uSnap && uSnap.exists) {
          const uData = uSnap.data() || {};
          if (uData.uid && !resultsMap.has(uData.uid)) {
            const p = await getUserProfile(uData.uid);
            if (p) resultsMap.set(uData.uid, p);
          }
        }
      } catch (e) {
        console.warn('[searchUsers]', e);
      }
    }

    const currentUser = getCurrentUser();
    if (currentUser) {
      resultsMap.delete(currentUser.uid);
    }

    return Array.from(resultsMap.values());
  }

  function getAvatarUrl(name, customAvatar) {
    if (customAvatar && !customAvatar.includes('unsplash.com')) return customAvatar;
    const svg = `<svg xmlns='http://www.w3.org/2000/svg' width='128' height='128' viewBox='0 0 128 128'><rect width='128' height='128' rx='64' fill='%23242730'/><path d='M64 30a18 18 0 1 0 0 36 18 18 0 0 0 0-36zm0 46c-20 0-36 12-36 28v4h72v-4c0-16-16-28-36-28z' fill='%237d8494'/></svg>`;
    return 'data:image/svg+xml;utf8,' + svg;
  }

  async function addFriend(targetUid, targetName, customAvatar) {
    if (!targetUid) return;
    
    // Save to local storage friends list
    const localFriends = JSON.parse(localStorage.getItem('votifyLocalFriends') || '[]');
    const avatarUrl = getAvatarUrl(targetName, customAvatar);
    if (!localFriends.find(f => f.uid === targetUid || f.name === targetName)) {
      localFriends.push({
        uid: targetUid || ('user_' + Date.now()),
        name: targetName || 'Пользователь',
        handle: '@' + String(targetName || 'user').toLowerCase().replace(/\s+/g, ''),
        avatar: avatarUrl,
        addedAt: Date.now()
      });
      localStorage.setItem('votifyLocalFriends', JSON.stringify(localFriends));
    }

    // Sync to Firestore if logged in
    if (state.available && state.db && state.auth?.currentUser && !state.auth.currentUser.isAnonymous) {
      try {
        const user = state.auth.currentUser;
        const docId = [user.uid, targetUid].sort().join('_');
        await state.db.collection('friendships').doc(docId).set({
          users: [user.uid, targetUid],
          fromUid: user.uid,
          toUid: targetUid,
          createdAt: window.firebase.firestore.FieldValue.serverTimestamp(),
        }, { merge: true });

        await state.db.collection('users').doc(user.uid).collection('friends').doc(targetUid).set({
          uid: targetUid,
          name: targetName,
          addedAt: window.firebase.firestore.FieldValue.serverTimestamp(),
        }, { merge: true }).catch(() => {});
      } catch (e) {
        console.warn('[addFriend firestore sync]', e);
      }
    }

    if (typeof window.showToast === 'function') {
      window.showToast('Добавлен в друзья: ' + (targetName || 'Пользователь'));
    }
    updateAccountUi();
    if (typeof window.renderFriendsList === 'function') {
      window.renderFriendsList();
    }
  }

  // Helper: Adaptive Cover Background for Favorite Track Card
  function applyFavTrackCoverBackground(rowEl, coverUrl) {
    if (!rowEl) return;
    if (!coverUrl || coverUrl.startsWith('data:image/svg')) {
      rowEl.style.background = '#181818';
      rowEl.style.boxShadow = '0 8px 24px rgba(0, 0, 0, 0.4)';
      return;
    }
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
        let r = 0, g = 0, b = 0, count = 0;
        for (let i = 0; i < data.length; i += 4) {
          const pr = data[i], pg = data[i + 1], pb = data[i + 2];
          const brightness = (pr * 299 + pg * 587 + pb * 114) / 1000;
          if (brightness > 18 && brightness < 235) {
            r += pr;
            g += pg;
            b += pb;
            count++;
          }
        }
        if (!count) {
          for (let i = 0; i < data.length; i += 4) {
            r += data[i];
            g += data[i + 1];
            b += data[i + 2];
            count++;
          }
        }
        r = Math.round(r / count);
        g = Math.round(g / count);
        b = Math.round(b / count);

        const r1 = Math.round(r * 0.55);
        const g1 = Math.round(g * 0.55);
        const b1 = Math.round(b * 0.55);
        const r2 = Math.round(r * 0.22);
        const g2 = Math.round(g * 0.22);
        const b2 = Math.round(b * 0.22);

        rowEl.style.background = `linear-gradient(135deg, rgba(${r1}, ${g1}, ${b1}, 0.72) 0%, rgba(${r2}, ${g2}, ${b2}, 0.95) 75%, #121216 100%)`;
        rowEl.style.boxShadow = `0 10px 30px rgba(${r2}, ${g2}, ${b2}, 0.6), 0 0 24px rgba(${r1}, ${g1}, ${b1}, 0.2)`;
      } catch {
        rowEl.style.background = '#181818';
        rowEl.style.boxShadow = '0 8px 24px rgba(0, 0, 0, 0.4)';
      }
    };
    img.onerror = () => {
      rowEl.style.background = '#181818';
      rowEl.style.boxShadow = '0 8px 24px rgba(0, 0, 0, 0.4)';
    };
    img.src = coverUrl;
  }

  async function getFriends() {
    try {
      const friendUids = new Set();
      const localMap = new Map();

      // 1. Load local friends
      const localFriends = JSON.parse(localStorage.getItem('votifyLocalFriends') || '[]');
      localFriends.forEach(f => {
        if (f.uid) {
          friendUids.add(f.uid);
          localMap.set(f.uid, f);
        }
      });

      // 2. Load Firestore friends if user is logged in
      if (state.available && state.db && state.auth?.currentUser && !state.auth.currentUser.isAnonymous) {
        const user = state.auth.currentUser;
        const snap = await state.db.collection('users').doc(user.uid).collection('friends').get().catch(() => null);
        if (snap) {
          snap.forEach(doc => {
            friendUids.add(doc.id);
            if (!localMap.has(doc.id)) {
              localMap.set(doc.id, { uid: doc.id, ...doc.data() });
            }
          });
        }
      }

      // 3. If no friends added yet, return empty list
      if (friendUids.size === 0) {
        return [];
      }

      // 4. Resolve profile data for each friend UID
      const profiles = await Promise.all(
        Array.from(friendUids).map(async fUid => {
          const remote = await getUserProfile(fUid).catch(() => null);
          const local = localMap.get(fUid) || {};
          const merged = { ...local, ...(remote || {}) };
          if (!merged.name && !merged.uid) return null;
          return {
            uid: merged.uid || fUid,
            name: merged.name || merged.displayName || 'Пользователь',
            handle: merged.handle || ('@' + String(merged.name || 'user').toLowerCase().replace(/\s+/g, '')),
            avatar: merged.avatar || getAvatarUrl(merged.name, merged.avatar),
            about: merged.about || 'Любитель музыки',
            track: merged.track || '',
            playlists: merged.playlists || [],
          };
        })
      );

      return profiles.filter(Boolean);
    } catch (e) {
      console.warn('[getFriends]', e);
      return [];
    }
  }

  async function pullState() {
    const user = await requireUser();
    const [settingsDoc, libraryDoc, historyDoc, userDoc] = await Promise.all([
      syncRef(user.uid, 'settings').get().catch(() => null),
      syncRef(user.uid, 'library').get().catch(() => null),
      syncRef(user.uid, 'history').get().catch(() => null),
      profileRef(user.uid).get().catch(() => null),
    ]);
    let playlists = libraryDoc?.exists ? libraryDoc.data()?.playlists || null : null;
    // Fallback to Android SyncBlob in users/{uid}.sync if libraryDoc is absent/empty
    if ((!playlists || Object.keys(playlists).length === 0) && userDoc?.exists) {
      const syncStr = userDoc.data()?.sync;
      if (syncStr) {
        try {
          const blob = JSON.parse(syncStr);
          const restored = { 'Избранное': [] };
          if (Array.isArray(blob.favorites)) {
            restored['Избранное'] = blob.favorites.map(f => ({
              id: f.track?.id,
              title: f.track?.t,
              artist: f.track?.a,
              cover: f.track?.c,
              duration: f.track?.d,
            }));
          }
          if (Array.isArray(blob.playlists)) {
            blob.playlists.forEach(p => {
              if (p && p.name) {
                restored[p.name] = (p.tracks || []).map(t => ({
                  id: t.id,
                  title: t.t,
                  artist: t.a,
                  cover: t.c,
                  duration: t.d,
                }));
              }
            });
          }
          playlists = restored;
        } catch (e) {}
      }
    }
    return {
      settings: settingsDoc?.exists ? settingsDoc.data()?.value || null : null,
      playlists,
      history: historyDoc?.exists ? historyDoc.data()?.history || null : null,
      exists: (settingsDoc && settingsDoc.exists) || (libraryDoc && libraryDoc.exists) || (historyDoc && historyDoc.exists) || !!playlists,
    };
  }

  async function pushState({ settings, playlists, history }) {
    const user = await requireUser();
    const updatedAt = window.firebase.firestore.FieldValue.serverTimestamp();
    const batch = state.db.batch();
    batch.set(syncRef(user.uid, 'settings'), { value: settings || {}, updatedAt }, { merge: true });
    batch.set(
      syncRef(user.uid, 'library'),
      { playlists: playlists || { Избранное: [] }, updatedAt },
      { merge: true }
    );
    batch.set(syncRef(user.uid, 'history'), { history: history || [], updatedAt }, { merge: true });

    // Also mirror public playlist summaries into profile documents
    const playlistSummaries = Object.keys(playlists || {}).map(name => {
      const tracks = Array.isArray(playlists[name]) ? playlists[name] : (playlists[name]?.tracks || []);
      const cover = playlists[name]?.cover || (tracks[0] && tracks[0].cover) || '';
      return {
        name,
        count: tracks.length,
        cover,
      };
    });
    batch.set(firestoreProfileRef(user.uid), { playlists: playlistSummaries, updatedAt }, { merge: true });

    // Also serialize Android-compatible syncBlob string so Android syncs seamlessly
    try {
      const favList = Array.isArray(playlists?.['Избранное'])
        ? playlists['Избранное']
        : (playlists?.['Избранное']?.tracks || []);
      const androidFavorites = favList.map(t => ({
        addedAt: Date.now(),
        track: {
          id: String(t.id || ''),
          t: String(t.title || t.name || t.t || 'Трек'),
          a: String(t.artist || t.artistName || t.a || 'Неизвестный исполнитель'),
          c: String(t.cover || t.thumbnail || t.c || ''),
          d: Number(t.duration || t.d || 180),
        },
      }));
      const androidPlaylists = Object.keys(playlists || {})
        .filter(k => k !== 'Избранное' && k !== 'Любимые треки')
        .map(name => {
          const list = Array.isArray(playlists[name]) ? playlists[name] : (playlists[name]?.tracks || []);
          return {
            name,
            createdAt: Date.now(),
            tracks: list.map(t => ({
              id: String(t.id || ''),
              t: String(t.title || t.name || t.t || 'Трек'),
              a: String(t.artist || t.artistName || t.a || 'Неизвестный исполнитель'),
              c: String(t.cover || t.thumbnail || t.c || ''),
              d: Number(t.duration || t.d || 180),
            })),
          };
        });
      const androidSyncBlob = JSON.stringify({
        v: 1,
        theme: settings?.theme || '',
        customTheme: settings?.accent || '',
        backgroundUrl: settings?.bgUrl || '',
        favorites: androidFavorites,
        playlists: androidPlaylists,
      });
      batch.set(profileRef(user.uid), {
        playlists: playlistSummaries,
        sync: androidSyncBlob,
        updatedAt,
      }, { merge: true });
    } catch (e) {
      batch.set(profileRef(user.uid), { playlists: playlistSummaries, updatedAt }, { merge: true });
    }

    await batch.commit();
  }

  async function listWorkshopThemes() {
    await requireCloud();
    const snapshot = await state.db
      .collection('workshopThemes')
      .orderBy('createdAt', 'desc')
      .limit(100)
      .get();
    return snapshot.docs.map(document => {
      const data = document.data() || {};
      const rawAuthor = data.authorName || 'user';
      const cleanAuthor = String(rawAuthor).trim().replace(/^@/, '');
      const handle = cleanAuthor ? (cleanAuthor.toLowerCase() === 'пользователь' ? 'user' : cleanAuthor.toLowerCase()) : 'user';
      return {
        id: document.id,
        title: String(data.title || '').slice(0, 60),
        description: String(data.description || '').slice(0, 240),
        ownerId: String(data.ownerId || ''),
        authorName: '@' + handle,
        theme: cleanWorkshopTheme(data.theme),
        createdAt: data.createdAt?.toMillis?.() || 0,
      };
    });
  }

  async function publishWorkshopTheme({ title, description, theme }) {
    const user = await requirePermanentUser();
    const safeTitle = String(title || '')
      .trim()
      .slice(0, 60);
    const safeDescription = String(description || '')
      .trim()
      .slice(0, 240);
    if (safeTitle.length < 3) throw new Error('Название должно содержать минимум 3 символа');
    const now = window.firebase.firestore.FieldValue.serverTimestamp();
    const reference = state.db.collection('workshopThemes').doc();

    const rawHandle = state.profile?.handle || user.displayName || (user.email ? user.email.split('@')[0] : 'user');
    const cleanHandle = String(rawHandle).trim().replace(/^@/, '').toLowerCase() || 'user';
    const authorHandle = ('@' + cleanHandle).slice(0, 40);

    const fullPayload = {
      title: safeTitle,
      description: safeDescription,
      ownerId: user.uid,
      authorName: authorHandle,
      theme: cleanWorkshopTheme(theme),
      schemaVersion: 1,
      createdAt: now,
      updatedAt: now,
    };
    try {
      await reference.set(fullPayload);
    } catch (err) {
      const msg = String(err?.message || '').toLowerCase();
      const code = String(err?.code || '').toLowerCase();
      if (code === 'permission-denied' || msg.includes('permission') || msg.includes('доступ')) {
        const corePayload = {
          ...fullPayload,
          theme: cleanCoreWorkshopTheme(theme),
        };
        await reference.set(corePayload);
      } else {
        throw err;
      }
    }
    return reference.id;
  }

  async function deleteWorkshopTheme(themeId) {
    const user = await requirePermanentUser();
    const id = String(themeId || '').trim();
    if (!/^[a-z0-9]{10,40}$/i.test(id)) throw new Error('Некорректный ID темы');
    const reference = state.db.collection('workshopThemes').doc(id);
    const snapshot = await reference.get();
    if (!snapshot.exists || snapshot.data()?.ownerId !== user.uid) {
      throw new Error('Удалять можно только собственные темы');
    }
    await reference.delete();
  }

  function onAuthChanged(listener) {
    authListeners.add(listener);
    if (state.initialized) listener({ user: publicUser(state.user), profile: state.profile });
    return () => authListeners.delete(listener);
  }

  function getCurrentUser() {
    return publicUser(state.auth?.currentUser || state.user);
  }

  function getProfile() {
    const localProfile = JSON.parse(localStorage.getItem('votifyLocalProfile') || '{}');
    return state.profile ? { ...localProfile, ...state.profile } : (Object.keys(localProfile).length ? localProfile : null);
  }

  function friendlyError(error) {
    const code = String(error?.code || '');
    const messages = {
      'auth/email-already-in-use': 'Этот email уже зарегистрирован',
      'auth/invalid-email': 'Некорректный email',
      'auth/invalid-credential': 'Неверный email или пароль',
      'auth/user-not-found': 'Аккаунт не найден',
      'auth/wrong-password': 'Неверный email или пароль',
      'auth/weak-password': 'Пароль должен содержать не менее 6 символов',
      'auth/too-many-requests': 'Слишком много попыток. Попробуйте позже',
      'auth/network-request-failed': 'Нет соединения с Firebase',
      'auth/operation-not-allowed': 'Этот способ входа не включён в Firebase',
      'auth/credential-already-in-use': 'Этот Google-аккаунт уже связан с другим профилем',
      'auth/account-exists-with-different-credential':
        'Аккаунт с этим email уже использует другой способ входа',
      'auth/popup-closed-by-user': 'Вход через Google отменён',
      'auth/requires-recent-login': 'Войдите в аккаунт повторно',
      'permission-denied':
        'Нет доступа Firestore. Опубликуйте актуальные правила из firestore.rules',
      'firestore/permission-denied':
        'Нет доступа Firestore. Опубликуйте актуальные правила из firestore.rules',
      unavailable: 'Firestore временно недоступен. Проверьте подключение к интернету',
    };
    return messages[code] || error?.message || 'Неизвестная ошибка';
  }

  function showAuthForm(formId) {
    ['auth-login', 'auth-register', 'auth-forgot'].forEach(id => {
      const element = document.getElementById(id);
      if (element) element.style.display = id === formId ? 'block' : 'none';
    });
  }

  function setMessage(id, message = '') {
    const element = document.getElementById(id);
    if (element) element.textContent = message;
  }

  function setBusy(button, busy) {
    if (!button) return;
    button.disabled = busy;
    button.classList.toggle('busy', busy);
  }

  function openAuth(formId = 'auth-login') {
    showAuthForm(formId);
    const overlay = document.getElementById('auth-overlay');
    if (overlay) overlay.style.display = 'flex';
    window.setTimeout(() => {
      const form = document.getElementById(formId);
      const input = form && form.querySelector('input:not([type="hidden"])');
      if (input) input.focus({ preventScroll: true });
    }, 60);
  }

  function closeAuth() {
    const overlay = document.getElementById('auth-overlay');
    if (overlay) overlay.style.display = 'none';
  }

  let lastScreenBeforeProfile = 'home-screen';

  function openProfile() {
    updateAccountUi();
    const curScreen = window.currentScreen || 'home-screen';
    if (curScreen !== 'profile-screen') {
      lastScreenBeforeProfile = curScreen;
    }
    const overlay = document.getElementById('profile-overlay');
    if (overlay) {
      overlay.style.display = 'flex';
      overlay.style.zIndex = '99999';
    }
  }

  function closeProfile() {
    const overlay = document.getElementById('profile-overlay');
    if (overlay) overlay.style.display = 'none';
  }

  function openFriendsModal() {
    const overlay = document.getElementById('friends-overlay');
    if (overlay) {
      overlay.style.display = 'flex';
      overlay.style.zIndex = '100000';
      const input = document.getElementById('friend-search-input');
      if (input) {
        input.value = '';
        setTimeout(() => input.focus(), 60);
      }
      const results = document.getElementById('friend-search-results');
      if (results) results.innerHTML = '';
      getFriends().then(friends => {
        ['pc-friends-count', 'page-friends-count'].forEach(id => {
          const countEl = document.getElementById(id);
          if (countEl) countEl.textContent = friends.length + ' ' + (friends.length === 1 ? 'друг' : friends.length >= 2 && friends.length <= 4 ? 'друга' : 'друзей');
        });
        ['pc-friends-list', 'page-friends-list'].forEach(id => {
          const listEl = document.getElementById(id);
          if (listEl) {
            if (friends.length === 0) {
              listEl.innerHTML = '<div style="font-size:12px; color:#737373; padding:8px 0;">У вас пока нет друзей</div>';
            } else {
              listEl.innerHTML = friends.map(f => `
                <div class="pc-friend-chip" style="cursor:pointer;" onclick="if(window.openUserProfile){window.openUserProfile('${f.uid}');}">
                  <div class="pc-friend-ava-shell">
                    <img src="${(f.avatar && !f.avatar.includes('unsplash.com')) ? f.avatar : getAvatarUrl(f.name)}" alt="${f.name}" />
                    <span class="pc-friend-dot"></span>
                  </div>
                  <span class="pc-friend-chip-name">${f.name}</span>
                </div>
              `).join('');
            }
          }
        });
      }).catch(() => {});
    }
  }

  function closeFriendsModal() {
    const overlay = document.getElementById('friends-overlay');
    if (overlay) overlay.style.display = 'none';
  }

  function updateAccountUi() {
    const user = getCurrentUser();
    const profile = getProfile() || {};
    const button = document.getElementById('nav-profile-btn');
    const avatarImage = document.getElementById('profile-avatar-image');
    const avatarFallback = document.getElementById('profile-avatar-fallback');
    const displayNameInput = document.getElementById('profile-display-name');
    const email = document.getElementById('profile-email');
    const kind = document.getElementById('profile-account-kind');
    const cloudStatus = document.getElementById('profile-cloud-status');
    const profileLoginButton = document.getElementById('profile-login-btn');
    const pageProfileLoginButton = document.getElementById('page-profile-login-btn');
    const isLoggedIn = !!user && !user.isAnonymous;
    const isGuest = !!user && user.isAnonymous;
    const heroName = document.getElementById('profile-display-name-text');
    const heroHandle = document.getElementById('profile-handle-text');
    const heroRole = document.getElementById('profile-role-text');

    const defaultName = isGuest ? 'Гость' : (user ? (user.displayName || (user.email ? user.email.split('@')[0] : 'Пользователь')) : 'Гость');
    const nameText = profile.displayName || defaultName;
    const defaultHandle = isGuest ? 'guest' : (user ? (user.email ? user.email.split('@')[0] : (user.displayName ? user.displayName.toLowerCase().replace(/\s+/g, '') : 'user')) : 'guest');
    const handleText = '@' + (profile.handle || defaultHandle);
    const roleText = isLoggedIn ? 'Пользователь' : (isGuest ? 'Гость (не привязан)' : 'Не авторизован');

    if (heroName) heroName.textContent = nameText;
    if (heroHandle) heroHandle.textContent = handleText;
    if (heroRole) heroRole.textContent = roleText;

    if (button) {
      button.classList.toggle('signed-in', isLoggedIn);
      button.title = isLoggedIn ? 'Профиль' : 'Войти в аккаунт';
      const icon = button.querySelector('.material-icons');
      if (icon) icon.textContent = isLoggedIn ? 'account_circle' : 'person_outline';
    }
    if (displayNameInput) displayNameInput.value = profile.displayName || (isLoggedIn ? nameText : '');
    const handleInput = document.getElementById('profile-handle');
    if (handleInput) handleInput.value = profile.handle || (user ? defaultHandle : '');
    const aboutInput = document.getElementById('profile-about');
    if (aboutInput) aboutInput.value = profile.about || '';
    if (email)
      email.textContent =
        user?.email || (isGuest ? 'Гостевой аккаунт' : 'Не авторизован (оффлайн)');
    if (kind)
      kind.textContent = isGuest
        ? 'Гость'
        : isLoggedIn
          ? 'Аккаунт Firebase'
          : 'Локальный режим';
    if (cloudStatus) {
      cloudStatus.textContent = state.available
        ? isLoggedIn
          ? 'Синхронизация включена'
          : 'Войдите для синхронизации с облаком'
        : state.error?.message || 'Firebase не настроен';
    }
    if (profileLoginButton) {
      profileLoginButton.style.display = isLoggedIn ? 'none' : 'block';
      profileLoginButton.textContent = isGuest
        ? 'Привязать аккаунт (Google / Email)'
        : 'Войти / Зарегистрироваться';
    }
    if (avatarImage) {
      if (profile.avatar) {
        avatarImage.src = profile.avatar;
        avatarImage.style.display = 'block';
        if (avatarFallback) avatarFallback.style.display = 'none';
      } else {
        avatarImage.src = DEFAULT_AVATAR;
      }
    }

    const pageHeroName = document.getElementById('page-profile-display-name-text');
    const pageHeroHandle = document.getElementById('page-profile-handle-text');
    const pageHeroRole = document.getElementById('page-profile-role-text');
    if (pageHeroName) pageHeroName.textContent = nameText;
    if (pageHeroHandle) pageHeroHandle.textContent = handleText;
    if (pageHeroRole) pageHeroRole.textContent = roleText;

    const pageAvatar = document.getElementById('page-profile-avatar-image');
    if (pageAvatar) {
      pageAvatar.src = profile.avatar || DEFAULT_AVATAR;
    }

    // 1. Banner Rendering (smooth bottom fade overlay)
    const bannerBg = document.getElementById('profile-banner-bg');
    const pageBannerBg = document.getElementById('page-profile-banner-bg');
    const bannerVal = profile.banner || '';
    const bannerGradients = {
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
    const applyBanner = (el, val) => {
      if (!el) return;
      if (val && (val.startsWith('http') || val.startsWith('data:image/'))) {
        el.style.backgroundImage = `url("${val.replace(/"/g, '\\"')}")`;
      } else if (val && bannerGradients[val]) {
        el.style.backgroundImage = bannerGradients[val];
      } else {
        el.style.backgroundImage = 'none';
        el.style.backgroundColor = '#14161f';
      }
    };
    applyBanner(bannerBg, bannerVal);
    applyBanner(pageBannerBg, bannerVal);

    const bannerInput = document.getElementById('profile-banner-url');
    const pageBannerInput = document.getElementById('page-profile-banner-url');
    if (bannerInput && !bannerInput.matches(':focus')) bannerInput.value = (bannerVal.startsWith('http') ? bannerVal : '');
    if (pageBannerInput && !pageBannerInput.matches(':focus')) pageBannerInput.value = (bannerVal.startsWith('http') ? bannerVal : '');

    // 2. Clean Avatar (no frames)
    const avatarWrapper = document.getElementById('profile-avatar-wrapper');
    const pageAvatarWrapper = document.getElementById('page-profile-avatar-wrapper');
    if (avatarWrapper) avatarWrapper.dataset.frame = 'none';
    if (pageAvatarWrapper) pageAvatarWrapper.dataset.frame = 'none';

    // 3. Favorite Track Section (Telegram style)
    const renderFavTrack = (sectionId, coverId, titleId, artistId, albumId, playBtnId, track) => {
      const sec = document.getElementById(sectionId);
      const coverEl = document.getElementById(coverId);
      const titleEl = document.getElementById(titleId);
      const artistEl = document.getElementById(artistId);
      const albumEl = document.getElementById(albumId);
      const playBtn = document.getElementById(playBtnId);
      if (!sec) return;
      if (track && track.title) {
        sec.style.display = 'block';
        if (coverEl) coverEl.src = track.cover || 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=300&h=300&fit=crop';
        
        const rawArtist = (track.artist && track.artist !== 'Unknown' && track.artist !== 'Неизвестный исполнитель') ? track.artist : '';
        const hasArtistInTitle = rawArtist && track.title.toLowerCase().includes(rawArtist.toLowerCase());
        const fullTitle = (rawArtist && !hasArtistInTitle) ? `${rawArtist} - ${track.title}` : track.title;
        
        if (titleEl) titleEl.textContent = fullTitle;
        if (artistEl) artistEl.textContent = rawArtist ? `<${rawArtist}>` : '<unknown>';
        if (albumEl) albumEl.textContent = (track.album || track.title || 'SINGLE').toUpperCase();
        
        const playAction = (e) => {
          if (e) e.stopPropagation();
          if (typeof window.playTrack === 'function') {
            window.playTrack(track);
            if (typeof window.showToast === 'function') window.showToast('Воспроизведение: ' + track.title);
          }
        };
        if (playBtn) playBtn.onclick = playAction;
        const rowEl = sec.querySelector('.pc-fav-track-row');
        if (rowEl) {
          rowEl.onclick = playAction;
          applyFavTrackCoverBackground(rowEl, track.cover);
        }
      } else {
        sec.style.display = 'none';
      }
    };
    renderFavTrack('profile-fav-track-section', 'profile-fav-track-cover', 'profile-fav-track-title', 'profile-fav-track-artist', 'profile-fav-track-album', 'profile-fav-track-play-btn', profile.favTrack);
    renderFavTrack('page-profile-fav-track-section', 'page-profile-fav-track-cover', 'page-profile-fav-track-title', 'page-profile-fav-track-artist', 'page-profile-fav-track-album', 'page-profile-fav-track-play-btn', profile.favTrack);

    const prevFav = document.getElementById('profile-fav-track-selected-preview');
    const pagePrevFav = document.getElementById('page-profile-fav-track-selected-preview');
    const favText = profile.favTrack ? `✓ ${profile.favTrack.artist ? profile.favTrack.artist + ' - ' : ''}${profile.favTrack.title}` : '';
    if (prevFav) prevFav.textContent = favText;
    if (pagePrevFav) pagePrevFav.textContent = favText;

    const pageNameInput = document.getElementById('page-profile-display-name');
    const pageHandleInput = document.getElementById('page-profile-handle');
    const pageAboutInput = document.getElementById('page-profile-about');
    if (pageNameInput) pageNameInput.value = profile.displayName || user?.displayName || '';
    if (pageHandleInput) pageHandleInput.value = profile.handle || (user ? defaultHandle : '');
    if (pageAboutInput) pageAboutInput.value = profile.about || '';

    // Render real user playlists
    const plsGrid = document.getElementById('pc-user-playlists-grid');
    const pagePlsGrid = document.getElementById('page-user-playlists-grid');
    const plObj = (window.playlists && typeof window.playlists === 'object' && !Array.isArray(window.playlists))
      ? window.playlists
      : (JSON.parse(localStorage.getItem('votify-playlists') || '{}') || {});
    const plNames = Object.keys(plObj);
    const renderPlCards = (container) => {
      if (!container) return;
      if (plNames.length === 0) {
        container.innerHTML = '<div style="font-size:12px; color:#737373; padding:12px 0;">Нет созданных плейлистов</div>';
        return;
      }
      container.innerHTML = plNames.map(name => {
        const tracks = Array.isArray(plObj[name]) ? plObj[name] : [];
        const count = tracks.length;
        const countStr = count + ' ' + (count === 1 ? 'трек' : (count >= 2 && count <= 4) ? 'трека' : 'треков');
        const firstCover = tracks.find(t => t && t.cover)?.cover || 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=300&h=300&fit=crop';
        return `
          <div class="pc-playlist-card" data-pl="${name.replace(/"/g, '&quot;')}" style="cursor:pointer;">
            <div class="pc-pl-cover-wrap">
              <img src="${firstCover}" alt="${name.replace(/"/g, '&quot;')}" />
              <button class="pc-pl-play-btn" title="Слушать"><i class="material-icons">play_arrow</i></button>
            </div>
            <div class="pc-pl-name">${name}</div>
            <div class="pc-pl-count">${countStr}</div>
          </div>
        `;
      }).join('');

      container.querySelectorAll('.pc-playlist-card').forEach(card => {
        card.addEventListener('click', () => {
          const plName = card.getAttribute('data-pl');
          if (plName && typeof window.openPlaylist === 'function') {
            closeProfile();
            window.openPlaylist(plName);
          }
        });
      });
    };
    renderPlCards(plsGrid);
    renderPlCards(pagePlsGrid);

    // Render real friends
    getFriends().then(friends => {
      ['pc-friends-count', 'page-friends-count'].forEach(id => {
        const countEl = document.getElementById(id);
        if (countEl) countEl.textContent = friends.length + ' ' + (friends.length === 1 ? 'друг' : friends.length >= 2 && friends.length <= 4 ? 'друга' : 'друзей');
      });
      ['pc-friends-list', 'page-friends-list'].forEach(id => {
        const listEl = document.getElementById(id);
        if (listEl) {
          if (friends.length === 0) {
            listEl.innerHTML = '<div style="font-size:12px; color:#737373; padding:8px 0;">У вас пока нет друзей</div>';
          } else {
            listEl.innerHTML = friends.map(f => `
              <div class="pc-friend-chip" style="cursor:pointer;" onclick="if(window.openUserProfile){window.openUserProfile('${f.uid}');}">
                <div class="pc-friend-ava-shell">
                  <img src="${(f.avatar && !f.avatar.includes('unsplash.com')) ? f.avatar : getAvatarUrl(f.name)}" alt="${f.name}" />
                  <span class="pc-friend-dot"></span>
                </div>
                <span class="pc-friend-chip-name">${f.name}</span>
              </div>
            `).join('');
          }
        }
      });
    }).catch(() => {});
  }

  function fileToImage(file, maxW = 900, maxH = 600, quality = 0.8) {
    return new Promise((resolve, reject) => {
      if (!file?.type?.startsWith('image/')) return reject(new Error('Выберите изображение'));
      const image = new Image();
      const reader = new FileReader();
      reader.onerror = () => reject(new Error('Не удалось прочитать изображение'));
      reader.onload = event => {
        image.onload = () => {
          let w = image.width;
          let h = image.height;
          if (w > maxW || h > maxH) {
            const ratio = Math.min(maxW / w, maxH / h);
            w = Math.round(w * ratio);
            h = Math.round(h * ratio);
          }
          const canvas = document.createElement('canvas');
          canvas.width = w;
          canvas.height = h;
          const ctx = canvas.getContext('2d');
          ctx.drawImage(image, 0, 0, w, h);
          resolve(canvas.toDataURL('image/webp', quality));
        };
        image.onerror = () => reject(new Error('Некорректное изображение'));
        image.src = event.target.result;
      };
      reader.readAsDataURL(file);
    });
  }

  function fileToAvatar(file) {
    return new Promise((resolve, reject) => {
      if (!file?.type?.startsWith('image/')) return reject(new Error('Выберите изображение'));
      const image = new Image();
      const reader = new FileReader();
      reader.onerror = () => reject(new Error('Не удалось прочитать изображение'));
      reader.onload = event => {
        image.onload = () => {
          const canvas = document.createElement('canvas');
          const size = 128;
          canvas.width = size;
          canvas.height = size;
          const context = canvas.getContext('2d');
          const scale = Math.max(size / image.width, size / image.height);
          const width = image.width * scale;
          const height = image.height * scale;
          context.drawImage(image, (size - width) / 2, (size - height) / 2, width, height);
          resolve(canvas.toDataURL('image/webp', 0.78));
        };
        image.onerror = () => reject(new Error('Некорректное изображение'));
        image.src = event.target.result;
      };
      reader.readAsDataURL(file);
    });
  }
  let pendingProfileBanner = undefined;
  let pendingProfileShowcase = undefined;
  let pendingProfileFavTrack = undefined;

  window.toggleProfileEditForm = function(formId) {
    const form = document.getElementById(formId);
    if (!form) return;
    const isHidden = (form.style.display === 'none' || !form.style.display);
    form.style.display = isHidden ? 'block' : 'none';
    if (isHidden) {
      setTimeout(() => {
        form.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      }, 30);
    }
  };

  function wireUi() {
    const handleProfileTrigger = () => {
      const user = getCurrentUser();
      if (!user || user.isAnonymous) {
        openAuth('auth-login');
      } else {
        openProfile();
      }
    };
    document.getElementById('nav-profile-btn')?.addEventListener('click', handleProfileTrigger);
    document.getElementById('tb-avatar-btn')?.addEventListener('click', handleProfileTrigger);
    document.getElementById('auth-close-btn')?.addEventListener('click', closeAuth);
    document.getElementById('profile-close-btn')?.addEventListener('click', closeProfile);

    // Profile Edit toggle
    const editBtn = document.getElementById('profile-edit-toggle-btn');
    if (editBtn) {
      editBtn.onclick = (e) => {
        e.preventDefault();
        e.stopPropagation();
        window.toggleProfileEditForm('profile-edit-form');
      };
    }
    const pageEditBtn = document.getElementById('page-profile-edit-toggle-btn');
    if (pageEditBtn) {
      pageEditBtn.onclick = (e) => {
        e.preventDefault();
        e.stopPropagation();
        window.toggleProfileEditForm('page-profile-edit-form');
      };
    }

    // Frame selection live preview
    const handleFrameSelectChange = (e) => {
      const frame = e.target.value;
      const avatarWrap = document.getElementById('profile-avatar-wrapper');
      const pageAvatarWrap = document.getElementById('page-profile-avatar-wrapper');
      if (avatarWrap) avatarWrap.dataset.frame = frame;
      if (pageAvatarWrap) pageAvatarWrap.dataset.frame = frame;
      const select1 = document.getElementById('profile-frame-select');
      const select2 = document.getElementById('page-profile-frame-select');
      if (select1 && select1 !== e.target) select1.value = frame;
      if (select2 && select2 !== e.target) select2.value = frame;
    };
    document.getElementById('profile-frame-select')?.addEventListener('change', handleFrameSelectChange);
    document.getElementById('page-profile-frame-select')?.addEventListener('change', handleFrameSelectChange);

    // Banner file upload handlers
    const handleBannerFileChange = async (e) => {
      try {
        const dataUrl = await fileToImage(e.target.files?.[0], 1200, 500, 0.82);
        pendingProfileBanner = dataUrl;
        const b1 = document.getElementById('profile-banner-bg');
        const b2 = document.getElementById('page-profile-banner-bg');
        if (b1) b1.style.backgroundImage = `url("${dataUrl}")`;
        if (b2) b2.style.backgroundImage = `url("${dataUrl}")`;
        if (typeof window.showToast === 'function') window.showToast('Баннер загружен');
      } catch (err) {
        if (typeof window.showToast === 'function') window.showToast(err.message);
      } finally {
        e.target.value = '';
      }
    };
    document.getElementById('profile-banner-file-input')?.addEventListener('change', handleBannerFileChange);
    document.getElementById('page-profile-banner-file-input')?.addEventListener('change', handleBannerFileChange);

    // Banner clear handlers
    const handleBannerClear = () => {
      pendingProfileBanner = '';
      const in1 = document.getElementById('profile-banner-url');
      const in2 = document.getElementById('page-profile-banner-url');
      if (in1) in1.value = '';
      if (in2) in2.value = '';
      const b1 = document.getElementById('profile-banner-bg');
      const b2 = document.getElementById('page-profile-banner-bg');
      if (b1) { b1.style.backgroundImage = 'none'; b1.style.backgroundColor = '#14161f'; }
      if (b2) { b2.style.backgroundImage = 'none'; b2.style.backgroundColor = '#14161f'; }
      if (typeof window.showToast === 'function') window.showToast('Баннер сброшен');
    };
    document.getElementById('profile-banner-clear-btn')?.addEventListener('click', handleBannerClear);
    document.getElementById('page-profile-banner-clear-btn')?.addEventListener('click', handleBannerClear);

    // Banner URL live preview handlers (typing / paste)
    const handleBannerUrlInput = (e) => {
      const url = e.target.value.trim();
      pendingProfileBanner = url;
      const b1 = document.getElementById('profile-banner-bg');
      const b2 = document.getElementById('page-profile-banner-bg');
      const in1 = document.getElementById('profile-banner-url');
      const in2 = document.getElementById('page-profile-banner-url');
      if (e.target !== in1 && in1) in1.value = url;
      if (e.target !== in2 && in2) in2.value = url;
      if (url && (url.startsWith('http') || url.startsWith('data:image/'))) {
        if (b1) b1.style.backgroundImage = `url("${url.replace(/"/g, '\\"')}")`;
        if (b2) b2.style.backgroundImage = `url("${url.replace(/"/g, '\\"')}")`;
      } else if (url && url.startsWith('grad-')) {
        if (b1) b1.style.backgroundImage = 'var(--' + url + ', linear-gradient(135deg, #1e1e24 0%, #2a2b36 100%))';
        if (b2) b2.style.backgroundImage = 'var(--' + url + ', linear-gradient(135deg, #1e1e24 0%, #2a2b36 100%))';
      } else if (!url) {
        if (b1) b1.style.backgroundImage = 'linear-gradient(135deg, #1e1e24 0%, #2a2b36 100%)';
        if (b2) b2.style.backgroundImage = 'linear-gradient(135deg, #1e1e24 0%, #2a2b36 100%)';
      }
    };
    document.getElementById('profile-banner-url')?.addEventListener('input', handleBannerUrlInput);
    document.getElementById('profile-banner-url')?.addEventListener('change', handleBannerUrlInput);
    document.getElementById('page-profile-banner-url')?.addEventListener('input', handleBannerUrlInput);
    document.getElementById('page-profile-banner-url')?.addEventListener('change', handleBannerUrlInput);

    // Favorite Track: Set current track
    const handleSetCurrentFavTrack = () => {
      let cur = null;
      if (window.currentTrack && window.currentTrack.title) {
        cur = window.currentTrack;
      } else if (typeof window.getCurrentPlayingTrack === 'function') {
        cur = window.getCurrentPlayingTrack();
      }

      if (!cur || !cur.title) {
        const fiTitle = document.getElementById('fi-title')?.innerText?.trim();
        const fiArtist = document.getElementById('fi-artist')?.innerText?.trim();
        const fiCover = document.getElementById('fi-cover')?.src;
        const playerTitle = document.getElementById('player-track-title')?.innerText?.trim();
        const playerArtist = document.getElementById('player-track-artist')?.innerText?.trim();
        const playerCover = document.getElementById('player-bar-cover')?.src;

        const title = (fiTitle && fiTitle !== 'Music' && fiTitle !== '—') 
          ? fiTitle 
          : ((playerTitle && playerTitle !== 'Music' && playerTitle !== '—') ? playerTitle : '');
        const artist = (fiArtist && fiArtist !== 'Выберите трек' && fiArtist !== '—') 
          ? fiArtist 
          : ((playerArtist && playerArtist !== 'Выберите трек' && playerArtist !== '—') ? playerArtist : '');
        let cover = fiCover || playerCover || '';
        if (cover.startsWith('data:image/svg+xml') || cover.includes('music_note')) cover = '';

        if (title) {
          cur = {
            id: 'track_' + Date.now(),
            title: title,
            artist: artist || '',
            cover: cover || '',
            duration: 0
          };
        }
      }

      if (cur && cur.title) {
        pendingProfileFavTrack = {
          id: cur.id || ('track_' + Date.now()),
          title: cur.title,
          artist: cur.artist || '',
          cover: cur.cover || '',
          duration: Number(cur.duration) || 0,
        };
        const text = `✓ ${cur.artist ? cur.artist + ' - ' : ''}${cur.title}`;
        const p1 = document.getElementById('profile-fav-track-selected-preview');
        const p2 = document.getElementById('page-profile-fav-track-selected-preview');
        if (p1) p1.textContent = text;
        if (p2) p2.textContent = text;
        if (typeof window.showToast === 'function') window.showToast('Любимый трек выбран: ' + cur.title);
      } else {
        if (typeof window.showToast === 'function') window.showToast('Сейчас ничего не играет. Запустите трек и нажмите снова');
      }
    };
    document.getElementById('profile-fav-track-set-current-btn')?.addEventListener('click', handleSetCurrentFavTrack);
    document.getElementById('page-profile-fav-track-set-current-btn')?.addEventListener('click', handleSetCurrentFavTrack);

    // Favorite Track: Clear
    const handleClearFavTrack = () => {
      pendingProfileFavTrack = null;
      const p1 = document.getElementById('profile-fav-track-selected-preview');
      const p2 = document.getElementById('page-profile-fav-track-selected-preview');
      if (p1) p1.textContent = 'Трек сброшен';
      if (p2) p2.textContent = 'Трек сброшен';
      if (typeof window.showToast === 'function') window.showToast('Любимый трек очищен');
    };
    document.getElementById('profile-fav-track-clear-btn')?.addEventListener('click', handleClearFavTrack);
    document.getElementById('page-profile-fav-track-clear-btn')?.addEventListener('click', handleClearFavTrack);

    // Save profile handlers
    document.getElementById('profile-save-btn')?.addEventListener('click', async event => {
      const button = event.currentTarget;
      setBusy(button, true);
      try {
        const newName = document.getElementById('profile-display-name')?.value?.trim();
        const newHandle = document.getElementById('profile-handle')?.value?.trim();
        const newAbout = document.getElementById('profile-about')?.value?.trim();
        const bannerUrlVal = document.getElementById('profile-banner-url')?.value?.trim() || '';
        const newBanner = pendingProfileBanner !== undefined ? pendingProfileBanner : (bannerUrlVal || state.profile?.banner || '');
        const newFavTrack = pendingProfileFavTrack !== undefined ? pendingProfileFavTrack : (state.profile?.favTrack || null);

        await saveProfile({
          displayName: newName,
          ...(newHandle !== undefined ? { handle: newHandle } : {}),
          avatar: state.profile?.avatar || '',
          about: newAbout,
          banner: newBanner,
          frame: 'none',
          favTrack: newFavTrack,
        });
        pendingProfileBanner = undefined;
        pendingProfileFavTrack = undefined;
        const form = document.getElementById('profile-edit-form');
        if (form) form.style.display = 'none';
        if (typeof window.showToast === 'function') window.showToast('Профиль сохранён');
      } catch (error) {
        if (typeof window.showToast === 'function') window.showToast(friendlyError(error));
      } finally {
        setBusy(button, false);
      }
    });

    document.getElementById('page-profile-save-btn')?.addEventListener('click', async event => {
      const button = event.currentTarget;
      setBusy(button, true);
      try {
        const newName = document.getElementById('page-profile-display-name')?.value?.trim();
        const newHandle = document.getElementById('page-profile-handle')?.value?.trim();
        const newAbout = document.getElementById('page-profile-about')?.value?.trim();
        const bannerUrlVal = document.getElementById('page-profile-banner-url')?.value?.trim() || '';
        const newBanner = pendingProfileBanner !== undefined ? pendingProfileBanner : (bannerUrlVal || state.profile?.banner || '');
        const newFavTrack = pendingProfileFavTrack !== undefined ? pendingProfileFavTrack : (state.profile?.favTrack || null);

        await saveProfile({
          displayName: newName,
          ...(newHandle !== undefined ? { handle: newHandle } : {}),
          avatar: state.profile?.avatar || '',
          about: newAbout,
          banner: newBanner,
          frame: 'none',
          favTrack: newFavTrack,
        });
        pendingProfileBanner = undefined;
        pendingProfileFavTrack = undefined;
        const form = document.getElementById('page-profile-edit-form');
        if (form) form.style.display = 'none';
        if (typeof window.showToast === 'function') window.showToast('Профиль сохранён');
      } catch (error) {
        if (typeof window.showToast === 'function') window.showToast(friendlyError(error));
      } finally {
        setBusy(button, false);
      }
    });

    // Avatar upload handlers
    const handleAvatarChange = async event => {
      try {
        const avatar = await fileToAvatar(event.target.files?.[0]);
        const currentHandle = document.getElementById('profile-handle')?.value?.trim() || document.getElementById('page-profile-handle')?.value?.trim() || state.profile?.handle || '';
        await saveProfile({
          displayName: document.getElementById('profile-display-name')?.value?.trim() || document.getElementById('page-profile-display-name')?.value?.trim() || state.profile?.displayName || '',
          handle: currentHandle,
          avatar,
          about: document.getElementById('profile-about')?.value?.trim() || document.getElementById('page-profile-about')?.value?.trim() || state.profile?.about || '',
        });
        if (typeof window.showToast === 'function') window.showToast('Аватар обновлён');
      } catch (error) {
        if (typeof window.showToast === 'function') window.showToast(friendlyError(error));
      } finally {
        event.target.value = '';
      }
    };
    document.getElementById('profile-avatar-input')?.addEventListener('change', handleAvatarChange);
    document.getElementById('page-profile-avatar-input')?.addEventListener('change', handleAvatarChange);

    // Profile App Bar actions
    document.getElementById('profile-friends-btn')?.addEventListener('click', () => {
      openFriendsModal();
    });

    document.getElementById('friends-modal-close-btn')?.addEventListener('click', () => {
      closeFriendsModal();
    });

    document.getElementById('friends-overlay')?.addEventListener('click', (e) => {
      if (e.target.id === 'friends-overlay') {
        closeFriendsModal();
      }
    });

    document.getElementById('profile-copy-id-btn')?.addEventListener('click', () => {
      const handle = document.getElementById('profile-handle-text')?.textContent || '@user';
      navigator.clipboard.writeText(handle).then(() => {
        if (typeof window.showToast === 'function') window.showToast('Юзернейм ' + handle + ' скопирован!');
      }).catch(() => {
        if (typeof window.showToast === 'function') window.showToast('Юзернейм: ' + handle);
      });
    });

    document.getElementById('profile-settings-btn')?.addEventListener('click', () => {
      closeProfile();
      if (typeof window.openSettings === 'function') {
        window.openSettings();
      } else if (typeof window.toggleSettingsOverlay === 'function') {
        window.toggleSettingsOverlay();
      } else {
        document.getElementById('nav-settings-btn')?.click();
      }
    });

    document.getElementById('profile-logout-btn')?.addEventListener('click', async () => {
      await signOut().catch(() => {});
      closeProfile();
      openAuth('auth-login');
      if (typeof window.showToast === 'function') window.showToast('Вы вышли из аккаунта');
    });

    // Publish Playlists buttons
    const handlePublishPlaylists = async () => {
      ['pc-playlists-sync-sub', 'page-playlists-sync-sub'].forEach(id => {
        const sub = document.getElementById(id);
        if (sub) sub.textContent = 'Синхронизация...';
      });
      try {
        if (state.user && !state.user.isAnonymous) {
          await pushState().catch(() => {});
        }
        ['pc-playlists-sync-sub', 'page-playlists-sync-sub'].forEach(id => {
          const sub = document.getElementById(id);
          if (sub) sub.textContent = 'Все изменения опубликованы';
        });
        if (typeof window.showToast === 'function') window.showToast('Плейлисты успешно опубликованы!');
      } catch (err) {
        ['pc-playlists-sync-sub', 'page-playlists-sync-sub'].forEach(id => {
          const sub = document.getElementById(id);
          if (sub) sub.textContent = 'Ошибка синхронизации';
        });
        if (typeof window.showToast === 'function') window.showToast('Ошибка публикации: ' + friendlyError(err));
      }
    };
    document.getElementById('pc-publish-btn')?.addEventListener('click', handlePublishPlaylists);
    document.getElementById('page-publish-btn')?.addEventListener('click', handlePublishPlaylists);

    // Overlays background click
    document.getElementById('profile-overlay')?.addEventListener('click', e => {
      if (e.target.id === 'profile-overlay') closeProfile();
    });
    document.getElementById('other-profile-overlay')?.addEventListener('click', e => {
      if (e.target.id === 'other-profile-overlay') closeUserProfile();
    });
    document.getElementById('auth-overlay')?.addEventListener('click', e => {
      if (e.target.id === 'auth-overlay') closeAuth();
    });

    // Auth screen transitions
    document.getElementById('show-register')?.addEventListener('click', event => {
      event.preventDefault();
      showAuthForm('auth-register');
    });
    document.getElementById('show-forgot')?.addEventListener('click', event => {
      event.preventDefault();
      showAuthForm('auth-forgot');
    });
    document.getElementById('show-login-from-register')?.addEventListener('click', event => {
      event.preventDefault();
      showAuthForm('auth-login');
    });
    document.getElementById('show-login-from-forgot')?.addEventListener('click', event => {
      event.preventDefault();
      showAuthForm('auth-login');
    });

    document.querySelectorAll('.google-login-btn').forEach(button => {
      button.addEventListener('click', async event => {
        const target = event.currentTarget;
        const errorTarget = target.dataset.errorTarget || 'login-error';
        setMessage('login-error');
        setMessage('register-error');
        setBusy(target, true);
        try {
          await signInWithGoogle();
          closeAuth();
        } catch (error) {
          setMessage(errorTarget, friendlyError(error));
        } finally {
          setBusy(target, false);
        }
      });
    });

    document.getElementById('login-btn')?.addEventListener('click', async event => {
      const button = event.currentTarget;
      setMessage('login-error');
      setBusy(button, true);
      try {
        await signIn(
          document.getElementById('login-email')?.value.trim(),
          document.getElementById('login-password')?.value
        );
        closeAuth();
      } catch (error) {
        setMessage('login-error', friendlyError(error));
      } finally {
        setBusy(button, false);
      }
    });

    document.getElementById('register-btn')?.addEventListener('click', async event => {
      const button = event.currentTarget;
      const password = document.getElementById('register-password')?.value || '';
      const confirmation = document.getElementById('register-password2')?.value || '';
      setMessage('register-error');
      if (password !== confirmation) {
        setMessage('register-error', 'Пароли не совпадают');
        return;
      }
      setBusy(button, true);
      try {
        await register(
          document.getElementById('register-email')?.value.trim(),
          password,
          document.getElementById('register-username')?.value.trim()
        );
        closeAuth();
      } catch (error) {
        setMessage('register-error', friendlyError(error));
      } finally {
        setBusy(button, false);
      }
    });

    document.getElementById('forgot-btn')?.addEventListener('click', async event => {
      const button = event.currentTarget;
      const email = document.getElementById('forgot-email')?.value.trim();
      setMessage('forgot-error');
      setMessage('forgot-success');
      setBusy(button, true);
      try {
        await sendPasswordReset(email);
        setMessage('forgot-success', 'Ссылка для сброса пароля отправлена на почту');
      } catch (error) {
        setMessage('forgot-error', friendlyError(error));
      } finally {
        setBusy(button, false);
      }
    });

    document.querySelectorAll('.guest-login-btn').forEach(button => {
      button.addEventListener('click', async event => {
        const target = event.currentTarget;
        const errorTarget = target.closest('#auth-register') ? 'register-error' : 'login-error';
        setMessage(errorTarget);
        setBusy(target, true);
        try {
          await signInAsGuest();
          closeAuth();
        } catch (error) {
          setMessage(errorTarget, friendlyError(error));
        } finally {
          setBusy(target, false);
        }
      });
    });

    document.getElementById('other-profile-close-btn')?.addEventListener('click', closeUserProfile);

    // Friend search input
    const handleFriendSearch = async (e, containerId) => {
      const query = e.target.value.trim();
      const resContainer = document.getElementById(containerId);
      if (!resContainer) return;
      if (!query) {
        resContainer.innerHTML = '';
        return;
      }
      try {
        const matched = await searchUsers(query);
        if (matched.length === 0) {
          resContainer.innerHTML = '<div style="font-size:12px; color:#737373; padding:8px;">Пользователь не найден</div>';
        } else {
          resContainer.innerHTML = matched.map(u => `
            <div class="pc-search-result-item" style="display:flex; align-items:center; justify-content:space-between; padding:10px 14px; background:rgba(255,255,255,0.05); border:1px solid rgba(255,255,255,0.05); border-radius:14px; margin-bottom:8px; cursor:pointer; transition:all 0.15s ease;" onclick="if(window.VotifyCloud&&window.VotifyCloud.openUserProfile){window.VotifyCloud.openUserProfile('${u.uid}');}">
              <div style="display:flex; align-items:center; gap:12px;">
                <img src="${(u.avatar && !u.avatar.includes('unsplash.com')) ? u.avatar : getAvatarUrl(u.name || u.handle)}" style="width:38px; height:38px; border-radius:50%; object-fit:cover; border:1px solid rgba(255,255,255,0.1);" />
                <div>
                  <div style="font-size:14px; font-weight:700; color:#ffffff;">${u.name}</div>
                  <div style="font-size:12px; color:#a3a3a3; font-weight:500;">${u.handle}</div>
                </div>
              </div>
              <button class="pc-btn-publish" style="font-size:12px; padding:6px 14px; font-weight:700; background:rgba(34,197,94,0.15); color:#22c55e; border:1px solid rgba(34,197,94,0.3); border-radius:9999px; cursor:pointer; transition:all 0.15s ease;" onclick="event.stopPropagation(); if(window.VotifyCloud&&window.VotifyCloud.addFriend){window.VotifyCloud.addFriend('${u.uid}','${u.name.replace(/'/g, "\\'")}');}">+ Добавить</button>
            </div>
          `).join('');
        }
      } catch (err) {
        resContainer.innerHTML = '<div style="font-size:12px; color:#ef4444; padding:8px;">' + (err.message || 'Ошибка поиска') + '</div>';
      }
    };
    document.getElementById('friend-search-input')?.addEventListener('input', e => handleFriendSearch(e, 'friend-search-results'));
    document.getElementById('page-friend-search-input')?.addEventListener('input', e => handleFriendSearch(e, 'page-friend-search-results'));

    document.getElementById('profile-login-btn')?.addEventListener('click', async () => {
      const user = getCurrentUser();
      if (!user?.isAnonymous) await signOut().catch(() => {});
      closeProfile();
      openAuth(user?.isAnonymous ? 'auth-register' : 'auth-login');
    });

    updateAccountUi();
  }

  async function openUserProfile(userObjOrUid) {
    const overlay = document.getElementById('other-profile-overlay');
    if (overlay) {
      overlay.style.display = 'flex';
      overlay.style.zIndex = '100005';
      overlay.classList.remove('hidden');
    }

    closeFriendsModal();
    closeProfile();

    let user = null;
    const localFriends = JSON.parse(localStorage.getItem('votifyLocalFriends') || '[]');

    if (typeof userObjOrUid === 'object' && userObjOrUid !== null) {
      user = { ...userObjOrUid };
      if (user.uid && state.available && state.db && !String(user.uid).startsWith('local_') && !String(user.uid).startsWith('user_')) {
        try {
          const remote = await getUserProfile(user.uid);
          if (remote) user = { ...user, ...remote };
        } catch (e) {}
      }
    } else if (typeof userObjOrUid === 'string' && userObjOrUid.trim()) {
      const targetId = userObjOrUid.trim();
      const localMatch = localFriends.find(f => 
        (f.uid && f.uid === targetId) || 
        (f.name && f.name.toLowerCase() === targetId.toLowerCase()) || 
        (f.handle && f.handle.toLowerCase() === targetId.toLowerCase())
      );

      if (localMatch) {
        user = {
          uid: localMatch.uid || targetId,
          name: localMatch.name || 'Пользователь',
          handle: localMatch.handle || ('@' + String(localMatch.name || 'user').toLowerCase().replace(/\s+/g, '')),
          avatar: (localMatch.avatar && !localMatch.avatar.includes('unsplash.com')) ? localMatch.avatar : getAvatarUrl(localMatch.name),
          about: localMatch.about || 'Любитель хорошей музыки',
          banner: localMatch.banner || '',
          favTrack: localMatch.favTrack || null,
          playlists: localMatch.playlists || []
        };
      }

      if (state.available && state.db && !targetId.startsWith('local_') && !targetId.startsWith('user_')) {
        try {
          const remote = await getUserProfile(targetId);
          if (remote) user = { ...(user || {}), ...remote };
        } catch (e) {}
      }

      if (!user) {
        const cleanName = targetId.replace(/^@/, '');
        user = {
          uid: targetId,
          name: cleanName,
          handle: targetId.startsWith('@') ? targetId : '@' + cleanName.toLowerCase().replace(/\s+/g, ''),
          avatar: getAvatarUrl(cleanName),
          about: 'Любитель хорошей музыки',
          banner: '',
          playlists: []
        };
      }
    }

    if (!user) return;

    try {
      const avatar = document.getElementById('other-profile-avatar');
      const name = document.getElementById('other-profile-name');
      const handle = document.getElementById('other-profile-handle');
      const about = document.getElementById('other-profile-about');
      const addBtn = document.getElementById('other-profile-add-btn');

      const effectiveAvatar = (user.avatar && !user.avatar.includes('unsplash.com')) ? user.avatar : getAvatarUrl(user.name || user.handle);
      if (avatar) avatar.src = effectiveAvatar;
      if (name) name.textContent = user.name || 'Пользователь';
      if (handle) handle.textContent = user.handle || ('@' + String(user.name || 'user').toLowerCase());
      if (about) about.textContent = user.about || 'Любитель хорошей музыки';

      // Other user banner
      const otherBannerBg = document.getElementById('other-profile-banner-bg');
      if (otherBannerBg) {
        const bannerVal = user.banner || '';
        if (bannerVal && (bannerVal.startsWith('http') || bannerVal.startsWith('data:image/'))) {
          otherBannerBg.style.backgroundImage = `url("${bannerVal.replace(/"/g, '\\"')}")`;
        } else {
          otherBannerBg.style.backgroundImage = 'linear-gradient(135deg, #1e1e24 0%, #2a2b36 100%)';
        }
      }

      // Other user favorite track (Telegram style)
      const otherFavSec = document.getElementById('other-profile-fav-track-section');
      const otherFavCover = document.getElementById('other-profile-fav-track-cover');
      const otherFavTitle = document.getElementById('other-profile-fav-track-title');
      const otherFavArtist = document.getElementById('other-profile-fav-track-artist');
      const otherFavAlbum = document.getElementById('other-profile-fav-track-album');
      const otherFavPlayBtn = document.getElementById('other-profile-fav-track-play-btn');
      if (otherFavSec) {
        if (user.favTrack && user.favTrack.title) {
          otherFavSec.style.display = 'block';
          if (otherFavCover) otherFavCover.src = user.favTrack.cover || 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=300&h=300&fit=crop';
          
          const rawArtist = (user.favTrack.artist && user.favTrack.artist !== 'Unknown' && user.favTrack.artist !== 'Неизвестный исполнитель') ? user.favTrack.artist : '';
          const hasArtistInTitle = rawArtist && user.favTrack.title.toLowerCase().includes(rawArtist.toLowerCase());
          const fullTitle = (rawArtist && !hasArtistInTitle) ? `${rawArtist} - ${user.favTrack.title}` : user.favTrack.title;
          
          if (otherFavTitle) otherFavTitle.textContent = fullTitle;
          if (otherFavArtist) otherFavArtist.textContent = rawArtist ? `<${rawArtist}>` : '<unknown>';
          if (otherFavAlbum) otherFavAlbum.textContent = (user.favTrack.album || user.favTrack.title || 'SINGLE').toUpperCase();
          
          const playAction = (e) => {
            if (e) e.stopPropagation();
            if (typeof window.playTrack === 'function') {
              window.playTrack(user.favTrack);
              if (typeof window.showToast === 'function') window.showToast('Воспроизведение: ' + user.favTrack.title);
            }
          };
          if (otherFavPlayBtn) otherFavPlayBtn.onclick = playAction;
          const rowEl = otherFavSec.querySelector('.pc-fav-track-row');
          if (rowEl) {
            rowEl.onclick = playAction;
            applyFavTrackCoverBackground(rowEl, user.favTrack.cover);
          }
        } else {
          otherFavSec.style.display = 'none';
        }
      }

      if (addBtn) {
        const isAlreadyFriend = localFriends.some(f => f.uid === user.uid || f.name === user.name);
        if (isAlreadyFriend) {
          addBtn.textContent = '✓ В друзьях';
          addBtn.disabled = true;
          addBtn.style.opacity = '0.6';
        } else {
          addBtn.disabled = false;
          addBtn.style.opacity = '1';
          addBtn.textContent = '+ Добавить в друзья';
        }
        addBtn.onclick = async () => {
          try {
            await addFriend(user.uid || ('user_' + Date.now()), user.name, user.avatar);
            addBtn.textContent = '✓ В друзьях';
            addBtn.disabled = true;
            addBtn.style.opacity = '0.6';
          } catch (e) {
            if (typeof window.showToast === 'function') window.showToast('Добавлен в друзья');
          }
        };
      }

      // Populate user playlists grid (real playlists only)
      const playlistsGrid = document.getElementById('other-profile-playlists-grid');
      const playlistsCount = document.getElementById('other-profile-playlists-count');
      
      let userPlaylists = user.playlists || [];
      if (!userPlaylists || userPlaylists.length === 0) {
        if (state.available && state.db && user.uid) {
          try {
            const libDoc = await syncRef(user.uid, 'library').get().catch(() => null);
            if (libDoc && libDoc.exists && libDoc.data()?.playlists) {
              const rawPl = libDoc.data().playlists;
              if (typeof rawPl === 'object') {
                userPlaylists = Object.keys(rawPl).map(k => {
                  const tracks = Array.isArray(rawPl[k]) ? rawPl[k] : (rawPl[k]?.tracks || []);
                  return {
                    name: k,
                    count: tracks.length,
                    cover: rawPl[k]?.cover || (tracks[0] && tracks[0].cover) || ''
                  };
                });
              }
            }
          } catch (e) {}
        }
      }

      if (playlistsCount) {
        if (!userPlaylists || userPlaylists.length === 0) {
          playlistsCount.textContent = '0 плейлистов';
        } else {
          playlistsCount.textContent = userPlaylists.length + ' ' + (userPlaylists.length === 1 ? 'плейлист' : userPlaylists.length >= 2 && userPlaylists.length <= 4 ? 'плейлиста' : 'плейлистов');
        }
      }

      if (playlistsGrid) {
        if (!userPlaylists || userPlaylists.length === 0) {
          playlistsGrid.innerHTML = '<div style="grid-column: 1/-1; text-align:center; padding: 28px 12px; color: #737373; font-size: 13px;">У пользователя пока нет плейлистов</div>';
        } else {
          const defaultCover = 'data:image/svg+xml;utf8,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="128" height="128" viewBox="0 0 128 128"><rect width="128" height="128" fill="#262626"/><text x="50%" y="54%" dominant-baseline="middle" text-anchor="middle" fill="#666" font-size="40">♫</text></svg>');
          playlistsGrid.innerHTML = userPlaylists.map(pl => `
            <div class="pc-playlist-card" data-pl="${pl.name}" style="background:#181818; border-radius:12px; padding:10px; cursor:pointer; text-align:left;">
              <div style="width:100%; aspect-ratio:1; border-radius:8px; overflow:hidden; margin-bottom:8px; position:relative; background:#262626;">
                <img src="${pl.cover || defaultCover}" style="width:100%; height:100%; object-fit:cover;" onerror="this.src='${defaultCover}'" />
              </div>
              <div style="font-size:12px; font-weight:700; color:#fff; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${pl.name}</div>
              <div style="font-size:11px; color:#a3a3a3;">${pl.count || 0} треков</div>
            </div>
          `).join('');

          playlistsGrid.querySelectorAll('.pc-playlist-card').forEach(card => {
            card.addEventListener('click', () => {
              const plName = card.getAttribute('data-pl');
              if (plName && typeof window.openPlaylist === 'function') {
                closeUserProfile();
                window.openPlaylist(plName);
              }
            });
          });
        }
      }
    } catch (err) {
      console.error('[openUserProfile render error]', err);
    }
  }

  function closeUserProfile() {
    const overlay = document.getElementById('other-profile-overlay');
    if (overlay) overlay.style.display = 'none';
    if (window.navigationHistory && typeof window.navigationHistory.updateButtons === 'function') {
      window.navigationHistory.updateButtons();
    }
  }

  window.openUserProfile = openUserProfile;
  window.closeUserProfile = closeUserProfile;
  window.getUserProfile = getUserProfile;
  window.getAvatarUrl = getAvatarUrl;
  window.openFriendsModal = openFriendsModal;
  window.closeFriendsModal = closeFriendsModal;
  window.openSettings = function() {
    const overlay = document.getElementById('settings-overlay');
    if (overlay) {
      overlay.style.display = 'flex';
      overlay.classList.remove('hidden');
    }
    if (typeof window.syncSettingsModalUI === 'function') {
      try { window.syncSettingsModalUI(); } catch (e) {}
    }
    if (window.navigationHistory && typeof window.navigationHistory.updateButtons === 'function') {
      window.navigationHistory.updateButtons();
    }
  };

  window.VotifyCloud = {
    whenReady: () => ready,
    isAvailable: () => state.available,
    getCurrentUser,
    getProfile,
    getUserProfile,
    getAvatarUrl,
    onAuthChanged,
    register,
    signIn,
    signInWithGoogle,
    signInAsGuest,
    sendPasswordReset,
    signOut,
    saveProfile,
    pullState,
    pushState,
    listWorkshopThemes,
    publishWorkshopTheme,
    deleteWorkshopTheme,
    openAuth,
    openProfile,
    openFriendsModal,
    closeFriendsModal,
    openUserProfile,
    closeUserProfile,
    friendlyError,
    reserveUsername,
    searchUsers,
    addFriend,
    getFriends,
  };

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', wireUi);
  else wireUi();

  // Режим проверки: открой окно входа сразу, если в URL есть ?auth=1
  if (new URLSearchParams(location.search).has('auth')) {
    const open = () => openAuth('auth-login');
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', open);
    else open();
  }
})();
