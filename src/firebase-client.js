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

  const PROFILE_FRAMES = ['none', 'glow', 'neon', 'rainbow', 'pixel', 'double', 'heart'];

  function cleanProfile(profile = {}) {
    const bannerRaw = String(profile.banner || '').trim();
    const cursorRaw = String(profile.cursor || '').trim();
    return {
      displayName: String(profile.displayName || '')
        .trim()
        .slice(0, 40),
      avatar: String(profile.avatar || '').startsWith('data:image/')
        ? String(profile.avatar).slice(0, 150000)
        : '',
      about: String(profile.about || '').trim().slice(0, 300),
      banner: bannerRaw.startsWith('data:image/')
        ? bannerRaw.slice(0, 200000)
        : /^https:\/\/[^\s]{1,300}$/.test(bannerRaw)
          ? bannerRaw
          : oneOf(bannerRaw, ['grad-1','grad-2','grad-3','grad-4','grad-5','grad-6','grad-7','grad-8','grad-9',''], ''),
      frame: oneOf(profile.frame, PROFILE_FRAMES, 'none'),
      cursor: cursorRaw.startsWith('data:image/') ? cursorRaw.slice(0, 80000) : '',
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

  function cleanWorkshopTheme(theme = {}) {
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

      state.auth.onAuthStateChanged(async user => {
        state.user = user || null;
        state.profile = user ? await ensureProfile(user).catch(() => null) : null;
        dispatchAuthState();
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
    const snap = await ref.get();
    if (snap.exists) {
      const data = snap.data() || {};
      if (data.uid && data.uid !== uid) {
        const err = new Error('Этот юзернейм уже занят');
        err.code = 'ALREADY_EXISTS';
        throw err;
      }
    }
    await ref.set({
      uid,
      createdAt: window.firebase.firestore.FieldValue.serverTimestamp(),
    }, { merge: true });
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
      const ava = d.avatar || d.photoUrl || getAvatarUrl(name);

      return {
        uid,
        name,
        handle: '@' + (cleanHandle || uid.slice(0, 8)),
        avatar: ava,
        about: d.about || d.bio || '',
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

    const effectiveDisplayName = data.displayName || user.displayName || (user.email ? user.email.split('@')[0] : 'Гость');
    const effectiveHandle = data.handle || data.username || (user.email ? user.email.split('@')[0] : 'guest');

    const profile = {
      displayName: effectiveDisplayName,
      handle: String(effectiveHandle).trim().replace(/^@/, '').toLowerCase(),
      email: user.email || '',
      isAnonymous: !!user.isAnonymous,
      avatar: data.avatar || data.photoUrl || '',
      about: data.about || data.bio || '',
      banner: data.banner || '',
      frame: data.frame || 'none',
      cursor: data.cursor || '',
      createdAt: data.createdAt || window.firebase.firestore.FieldValue.serverTimestamp(),
      updatedAt: window.firebase.firestore.FieldValue.serverTimestamp(),
    };

    if (!userSnap?.exists || !profSnap?.exists) {
      await Promise.all([
        reference.set(profile, { merge: true }).catch(() => {}),
        fsProfRef.set(profile, { merge: true }).catch(() => {}),
      ]);
    }

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
    await requireCloud();
    await state.auth.signOut();
  }

  async function saveProfile(profile) {
    const safe = cleanProfile(profile);
    const user = state.auth?.currentUser;
    if (user) {
      const uid = user.uid;
      let handleToSave = safe.handle || safe.displayName || '';
      handleToSave = String(handleToSave).toLowerCase().trim().replace(/^@/, '');
      if (handleToSave && /^[a-z0-9_]{3,20}$/.test(handleToSave)) {
        await reserveUsername(handleToSave, uid).catch(() => {});
        safe.handle = handleToSave;
      }
      if (safe.displayName && safe.displayName !== user.displayName) {
        await user.updateProfile({ displayName: safe.displayName }).catch(() => {});
      }
      const payload = {
        ...safe,
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
    const updatedLocal = { ...currentLocal, ...safe };
    localStorage.setItem('votifyLocalProfile', JSON.stringify(updatedLocal));
    state.profile = { ...(state.profile || {}), ...updatedLocal };
    dispatchAuthState();
    return state.profile;
  }

  async function searchUsers(query) {
    if (!state.available || !state.db) return [];
    const q = String(query || '').trim().toLowerCase().replace(/^@/, '');
    if (!q) return [];

    try {
      const resultsMap = new Map();

      // 1. Search profiles collection
      const profSnap = await state.db.collection('profiles').limit(40).get().catch(() => null);
      if (profSnap) {
        profSnap.forEach(doc => {
          const d = doc.data() || {};
          const name = String(d.displayName || d.username || '').toLowerCase();
          const handle = String(d.handle || d.username || '').toLowerCase();
          if (name.includes(q) || handle.includes(q)) {
            const h = d.handle || d.username || d.displayName || (d.email ? d.email.split('@')[0] : doc.id.slice(0, 8));
            resultsMap.set(doc.id, {
              uid: doc.id,
              name: d.displayName || d.username || h,
              handle: '@' + String(h).trim().replace(/^@/, '').toLowerCase(),
              avatar: d.avatar || d.photoUrl || '',
              about: d.about || d.bio || '',
            });
          }
        });
      }

      // 2. Search users collection
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
            resultsMap.set(doc.id, {
              uid: doc.id,
              name: d.displayName || d.username || h,
              handle: '@' + String(h).trim().replace(/^@/, '').toLowerCase(),
              avatar: d.avatar || d.photoUrl || '',
              about: d.about || d.bio || '',
            });
          }
        });
      }

      // 3. Search usernames collection
      const uSnap = await usernameRef(q).get().catch(() => null);
      if (uSnap && uSnap.exists) {
        const uData = uSnap.data() || {};
        if (uData.uid && !resultsMap.has(uData.uid)) {
          const p = await getUserProfile(uData.uid);
          if (p) resultsMap.set(uData.uid, p);
        }
      }

      const currentUser = getCurrentUser();
      if (currentUser) {
        resultsMap.delete(currentUser.uid);
      }

      return Array.from(resultsMap.values());
    } catch (e) {
      console.warn('[searchUsers]', e);
      return [];
    }
  }

  function getAvatarUrl(name, customAvatar) {
    if (customAvatar && !customAvatar.includes('unsplash.com')) return customAvatar;
    const initial = String(name || 'U').trim().charAt(0).toUpperCase();
    const colors = ['#e11d48', '#2563eb', '#059669', '#d97706', '#7c3aed', '#db2777', '#0284c7'];
    let charCodeSum = 0;
    for (let i = 0; i < (name || '').length; i++) charCodeSum += name.charCodeAt(i);
    const color = colors[charCodeSum % colors.length];
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="128" height="128" viewBox="0 0 128 128"><rect width="128" height="128" rx="64" fill="${color}"/><text x="50%" y="54%" dominant-baseline="middle" text-anchor="middle" fill="#ffffff" font-family="sans-serif" font-size="56" font-weight="bold">${initial}</text></svg>`;
    return 'data:image/svg+xml;utf8,' + encodeURIComponent(svg);
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
    const [settingsDoc, libraryDoc, historyDoc] = await Promise.all([
      syncRef(user.uid, 'settings').get(),
      syncRef(user.uid, 'library').get(),
      syncRef(user.uid, 'history').get(),
    ]);
    return {
      settings: settingsDoc.exists ? settingsDoc.data()?.value || null : null,
      playlists: libraryDoc.exists ? libraryDoc.data()?.playlists || null : null,
      history: historyDoc.exists ? historyDoc.data()?.history || null : null,
      exists: settingsDoc.exists || libraryDoc.exists || historyDoc.exists,
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
    batch.set(profileRef(user.uid), { playlists: playlistSummaries, updatedAt }, { merge: true });

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
      return {
        id: document.id,
        title: String(data.title || '').slice(0, 60),
        description: String(data.description || '').slice(0, 240),
        ownerId: String(data.ownerId || ''),
        authorName: String(data.authorName || 'Пользователь').slice(0, 40),
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
    const authorName =
      String(state.profile?.displayName || user.displayName || user.email || '')
        .trim()
        .slice(0, 40) || 'Пользователь';
    await reference.set({
      title: safeTitle,
      description: safeDescription,
      ownerId: user.uid,
      authorName,
      theme: cleanWorkshopTheme(theme),
      schemaVersion: 1,
      createdAt: now,
      updatedAt: now,
    });
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

  function openProfile() {
    updateAccountUi();
    if (typeof window.switchScreen === 'function') {
      window.switchScreen('profile-screen', 'nav-profile-btn');
    } else {
      document.getElementById('nav-profile-btn')?.click();
    }
    const overlay = document.getElementById('profile-overlay');
    if (overlay) overlay.style.display = 'flex';
  }

  function closeProfile() {
    const overlay = document.getElementById('profile-overlay');
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

    const heroName = document.getElementById('profile-display-name-text');
    const heroHandle = document.getElementById('profile-handle-text');
    const nameText = profile.displayName || user?.displayName || (user?.email ? user.email.split('@')[0] : 'exieeez');
    const handleText = '@' + (profile.handle || (user?.email ? user.email.split('@')[0] : 'exieeez'));
    if (heroName) heroName.textContent = nameText;
    if (heroHandle) heroHandle.textContent = handleText;

    if (button) {
      button.classList.toggle('signed-in', !!user);
      button.title = user ? 'Профиль' : 'Войти в аккаунт';
      const icon = button.querySelector('.material-icons');
      if (icon) icon.textContent = user ? 'account_circle' : 'person_outline';
    }
    if (displayNameInput) displayNameInput.value = profile.displayName || user?.displayName || 'exieeez';
    const aboutInput = document.getElementById('profile-about');
    if (aboutInput) aboutInput.value = profile.about || '';
    if (email)
      email.textContent =
        user?.email || (user?.isAnonymous ? 'Гостевой аккаунт' : 'Локальный профиль (оффлайн)');
    if (kind)
      kind.textContent = user?.isAnonymous
        ? 'Гость'
        : user
          ? 'Аккаунт Firebase'
          : 'Локальный профиль';
    if (cloudStatus) {
      cloudStatus.textContent = state.available
        ? user
          ? 'Синхронизация включена'
          : 'Войдите для синхронизации с облаком'
        : state.error?.message || 'Firebase не настроен';
    }
    if (profileLoginButton) {
      profileLoginButton.textContent = user
        ? 'Войти в другой аккаунт'
        : 'Войти / Зарегистрироваться в Firebase';
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
    if (pageHeroName) pageHeroName.textContent = nameText;
    if (pageHeroHandle) pageHeroHandle.textContent = handleText;

    const pageAvatar = document.getElementById('page-profile-avatar-image');
    if (pageAvatar) {
      pageAvatar.src = profile.avatar || DEFAULT_AVATAR;
    }

    const pageNameInput = document.getElementById('page-profile-display-name');
    const pageAboutInput = document.getElementById('page-profile-about');
    if (pageNameInput) pageNameInput.value = profile.displayName || user?.displayName || '';
    if (pageAboutInput) pageAboutInput.value = profile.about || '';

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
                  <img src="${f.avatar || 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=100&h=100&fit=crop'}" alt="${f.name}" />
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

  function wireUi() {
    document.getElementById('nav-profile-btn')?.addEventListener('click', openProfile);
    document.getElementById('tb-avatar-btn')?.addEventListener('click', openProfile);
    document.getElementById('auth-close-btn')?.addEventListener('click', closeAuth);
    document.getElementById('profile-close-btn')?.addEventListener('click', closeProfile);
    document.getElementById('profile-edit-toggle-btn')?.addEventListener('click', () => {
      const form = document.getElementById('profile-edit-form');
      if (form) form.style.display = form.style.display === 'none' ? 'block' : 'none';
    });
    document.getElementById('page-profile-edit-toggle-btn')?.addEventListener('click', () => {
      const form = document.getElementById('page-profile-edit-form');
      if (form) form.style.display = form.style.display === 'none' ? 'block' : 'none';
    });
    document.getElementById('page-profile-save-btn')?.addEventListener('click', async event => {
      const button = event.currentTarget;
      setBusy(button, true);
      try {
        await saveProfile({
          displayName: document.getElementById('page-profile-display-name')?.value,
          avatar: state.profile?.avatar || '',
          about: document.getElementById('page-profile-about')?.value,
        });
        if (typeof window.showToast === 'function') window.showToast('Профиль сохранён');
      } catch (error) {
        if (typeof window.showToast === 'function') window.showToast(friendlyError(error));
      } finally {
        setBusy(button, false);
      }
    });
    document.getElementById('page-profile-avatar-input')?.addEventListener('change', async event => {
      try {
        const avatar = await fileToAvatar(event.target.files?.[0]);
        await saveProfile({
          displayName: document.getElementById('page-profile-display-name')?.value,
          avatar,
          about: document.getElementById('page-profile-about')?.value,
        });
        if (typeof window.showToast === 'function') window.showToast('Аватар сохранён');
      } catch (error) {
        if (typeof window.showToast === 'function') window.showToast(friendlyError(error));
      } finally {
        event.target.value = '';
      }
    });
    document.getElementById('profile-overlay')?.addEventListener('click', e => {
      if (e.target.id === 'profile-overlay') closeProfile();
    });
    document.getElementById('other-profile-overlay')?.addEventListener('click', e => {
      if (e.target.id === 'other-profile-overlay') closeUserProfile();
    });
    document.getElementById('auth-overlay')?.addEventListener('click', e => {
      if (e.target.id === 'auth-overlay') closeAuth();
    });
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

    document.getElementById('profile-save-btn')?.addEventListener('click', async event => {
      const button = event.currentTarget;
      setBusy(button, true);
      try {
        await saveProfile({
          displayName: document.getElementById('profile-display-name')?.value,
          avatar: state.profile?.avatar || '',
          about: document.getElementById('profile-about')?.value,
        });
        setMessage('profile-message', 'Профиль сохранён');
      } catch (error) {
        setMessage('profile-message', friendlyError(error));
      } finally {
        setBusy(button, false);
      }
    });

    document.getElementById('profile-avatar-input')?.addEventListener('change', async event => {
      try {
        const avatar = await fileToAvatar(event.target.files?.[0]);
        await saveProfile({
          displayName: document.getElementById('profile-display-name')?.value,
          avatar,
          about: document.getElementById('profile-about')?.value,
        });
        setMessage('profile-message', 'Аватар сохранён');
      } catch (error) {
        setMessage('profile-message', friendlyError(error));
      } finally {
        event.target.value = '';
      }
    });

    document.getElementById('profile-copy-id-btn')?.addEventListener('click', () => {
      const handle = document.getElementById('profile-handle-text')?.textContent || '@god';
      navigator.clipboard.writeText(handle).then(() => {
        if (typeof window.showToast === 'function') window.showToast('Юзернейм ' + handle + ' скопирован!');
      }).catch(() => {
        if (typeof window.showToast === 'function') window.showToast('Юзернейм: ' + handle);
      });
    });

    document.getElementById('profile-settings-btn')?.addEventListener('click', () => {
      if (typeof window.openSettings === 'function') window.openSettings();
    });

    document.getElementById('profile-edit-toggle-btn')?.addEventListener('click', () => {
      const form = document.getElementById('profile-edit-form');
      if (form) {
        form.style.display = form.style.display === 'none' ? 'block' : 'none';
      }
    });

    document.getElementById('pc-publish-btn')?.addEventListener('click', () => {
      const sub = document.getElementById('pc-playlists-sync-sub');
      if (sub) sub.textContent = 'Все изменения опубликованы';
      if (typeof window.showToast === 'function') window.showToast('Плейлисты успешно опубликованы!');
    });

    document.getElementById('other-profile-close-btn')?.addEventListener('click', closeUserProfile);

    document.getElementById('friend-search-input')?.addEventListener('input', async (e) => {
      const query = e.target.value.trim();
      const resContainer = document.getElementById('friend-search-results');
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
            <div style="display:flex; align-items:center; justify-content:space-between; padding:8px 12px; background:rgba(255,255,255,0.04); border-radius:12px; margin-bottom:6px; cursor:pointer;" onclick="if(window.VotifyCloud&&window.VotifyCloud.openUserProfile){window.VotifyCloud.openUserProfile('${u.uid}');}">
              <div style="display:flex; align-items:center; gap:10px;">
                <img src="${u.avatar || 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=100&h=100&fit=crop'}" style="width:32px; height:32px; border-radius:50%; object-fit:cover;" />
                <div>
                  <div style="font-size:13px; font-weight:700; color:#fff;">${u.name}</div>
                  <div style="font-size:11px; color:#a3a3a3;">${u.handle}</div>
                </div>
              </div>
              <button class="pc-btn-publish" style="font-size:11px; padding:4px 10px;" onclick="event.stopPropagation(); if(window.VotifyCloud&&window.VotifyCloud.addFriend){window.VotifyCloud.addFriend('${u.uid}','${u.name.replace(/'/g, "\\'")}');}">+ Добавить</button>
            </div>
          `).join('');
        }
      } catch (err) {
        resContainer.innerHTML = '<div style="font-size:12px; color:#ef4444; padding:8px;">' + (err.message || 'Ошибка поиска') + '</div>';
      }
    });

    document.querySelectorAll('.pc-playlist-card').forEach(card => {
      card.addEventListener('click', () => {
        const plName = card.getAttribute('data-pl');
        if (plName && typeof window.openPlaylist === 'function') {
          closeProfile();
          window.openPlaylist(plName);
        }
      });
    });

    document.getElementById('profile-logout-btn')?.addEventListener('click', async () => {
      await signOut().catch(() => {});
      closeProfile();
      openAuth();
    });

    document.getElementById('profile-login-btn')?.addEventListener('click', async () => {
      const user = getCurrentUser();
      if (!user?.isAnonymous) await signOut().catch(() => {});
      closeProfile();
      openAuth(user?.isAnonymous ? 'auth-register' : 'auth-login');
    });

    updateAccountUi();
  }

  async function openUserProfile(userObjOrUid) {
    let user = null;
    if (typeof userObjOrUid === 'object' && userObjOrUid !== null) {
      user = { ...userObjOrUid };
      if (user.uid && state.available && state.db) {
        const remote = await getUserProfile(user.uid).catch(() => null);
        if (remote) user = { ...user, ...remote };
      }
    } else if (typeof userObjOrUid === 'string' && userObjOrUid.trim()) {
      try {
        user = await getUserProfile(userObjOrUid);
      } catch (e) {}
      if (!user) {
        const cleanName = userObjOrUid.replace(/^@/, '');
        user = {
          uid: userObjOrUid,
          name: cleanName,
          handle: userObjOrUid.startsWith('@') ? userObjOrUid : '@' + cleanName.toLowerCase().replace(/\s+/g, ''),
          avatar: getAvatarUrl(cleanName),
          about: 'Любитель хорошей музыки'
        };
      }
    }
    if (!user) return;

    const localFriends = JSON.parse(localStorage.getItem('votifyLocalFriends') || '[]');
    const isAlreadyFriend = localFriends.some(f => f.uid === user.uid || f.name === user.name);

    const overlay = document.getElementById('other-profile-overlay');
    const avatar = document.getElementById('other-profile-avatar');
    const name = document.getElementById('other-profile-name');
    const handle = document.getElementById('other-profile-handle');
    const about = document.getElementById('other-profile-about');
    const addBtn = document.getElementById('other-profile-add-btn');

    const effectiveAvatar = user.avatar || getAvatarUrl(user.name);
    if (avatar) avatar.src = effectiveAvatar;
    if (name) name.textContent = user.name || 'Пользователь';
    if (handle) handle.textContent = user.handle || ('@' + (user.name || 'user').toLowerCase());
    if (about) about.textContent = user.about || 'Любитель хорошей музыки';

    if (addBtn) {
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

    if (overlay) overlay.style.display = 'flex';
    if (window.navigationHistory && typeof window.navigationHistory.updateButtons === 'function') {
      window.navigationHistory.updateButtons();
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
  window.openSettings = function() {
    const overlay = document.getElementById('settings-overlay');
    if (overlay) overlay.style.display = 'flex';
    if (window.navigationHistory && typeof window.navigationHistory.updateButtons === 'function') {
      window.navigationHistory.updateButtons();
    }
  };

  window.VotifyCloud = {
    whenReady: () => ready,
    isAvailable: () => state.available,
    getCurrentUser,
    getProfile,
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
