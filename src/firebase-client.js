(() => {
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

  function cleanProfile(profile = {}) {
    return {
      displayName: String(profile.displayName || '')
        .trim()
        .slice(0, 40),
      avatar: String(profile.avatar || '').startsWith('data:image/')
        ? String(profile.avatar).slice(0, 150000)
        : '',
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
      const response = await fetch('/api/firebase/config', { cache: 'no-store' });
      const payload = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(payload.error || 'Firebase не настроен');

      const app = window.firebase.apps.length
        ? window.firebase.app()
        : window.firebase.initializeApp(payload.config);
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
          if (!user) window.setTimeout(() => openAuth('auth-register'), 0);
        }
      });
    } catch (error) {
      state.error = error;
      console.warn('[Firebase]', error.message || error);
      updateAccountUi();
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

  function syncRef(uid, name) {
    return profileRef(uid).collection('sync').doc(name);
  }

  async function ensureProfile(user) {
    const reference = profileRef(user.uid);
    const snapshot = await reference.get();
    if (!snapshot.exists) {
      const profile = {
        displayName: user.displayName || (user.email ? user.email.split('@')[0] : 'Гость'),
        email: user.email || '',
        isAnonymous: !!user.isAnonymous,
        avatar: '',
        createdAt: window.firebase.firestore.FieldValue.serverTimestamp(),
        updatedAt: window.firebase.firestore.FieldValue.serverTimestamp(),
      };
      await reference.set(profile, { merge: true });
      return profile;
    }
    return snapshot.data() || {};
  }

  async function register(email, password, displayName) {
    await requireCloud();
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
    await profileRef(credential.user.uid).set(
      {
        displayName: name,
        email: credential.user.email || email,
        isAnonymous: false,
        updatedAt: window.firebase.firestore.FieldValue.serverTimestamp(),
      },
      { merge: true }
    );
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
    const user = await requireUser();
    const safe = cleanProfile(profile);
    if (safe.displayName && safe.displayName !== user.displayName) {
      await user.updateProfile({ displayName: safe.displayName });
    }
    await profileRef(user.uid).set(
      {
        ...safe,
        email: user.email || '',
        isAnonymous: !!user.isAnonymous,
        updatedAt: window.firebase.firestore.FieldValue.serverTimestamp(),
      },
      { merge: true }
    );
    state.profile = { ...(state.profile || {}), ...safe };
    dispatchAuthState();
    return state.profile;
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
    return state.profile ? { ...state.profile } : null;
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
  }

  function closeAuth() {
    const overlay = document.getElementById('auth-overlay');
    if (overlay) overlay.style.display = 'none';
  }

  function openProfile() {
    const user = getCurrentUser();
    if (!user) {
      openAuth();
      return;
    }
    updateAccountUi();
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

    if (button) {
      button.classList.toggle('signed-in', !!user);
      button.title = user ? 'Профиль' : 'Войти в аккаунт';
      const icon = button.querySelector('.material-icons');
      if (icon) icon.textContent = user ? 'account_circle' : 'person_outline';
    }
    if (displayNameInput) displayNameInput.value = profile.displayName || user?.displayName || '';
    if (email)
      email.textContent =
        user?.email || (user?.isAnonymous ? 'Гостевой аккаунт' : 'Не выполнен вход');
    if (kind)
      kind.textContent = user?.isAnonymous
        ? 'Гость'
        : user
          ? 'Аккаунт Firebase'
          : 'Локальный режим';
    if (cloudStatus) {
      cloudStatus.textContent = state.available
        ? user
          ? 'Синхронизация включена'
          : 'Войдите для синхронизации'
        : state.error?.message || 'Firebase не настроен';
    }
    if (profileLoginButton) {
      profileLoginButton.textContent = user?.isAnonymous
        ? 'Привязать постоянный аккаунт'
        : 'Войти в другой аккаунт';
    }
    if (avatarImage && avatarFallback) {
      if (profile.avatar) {
        avatarImage.src = profile.avatar;
        avatarImage.style.display = 'block';
        avatarFallback.style.display = 'none';
      } else {
        avatarImage.removeAttribute('src');
        avatarImage.style.display = 'none';
        avatarFallback.style.display = 'grid';
        avatarFallback.textContent =
          (profile.displayName || user?.email || 'V').trim()[0]?.toUpperCase() || 'V';
      }
    }
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
    document.getElementById('auth-close-btn')?.addEventListener('click', closeAuth);
    document.getElementById('profile-close-btn')?.addEventListener('click', closeProfile);
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
        });
        setMessage('profile-message', 'Аватар сохранён');
      } catch (error) {
        setMessage('profile-message', friendlyError(error));
      } finally {
        event.target.value = '';
      }
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
    friendlyError,
  };

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', wireUi);
  else wireUi();
})();
