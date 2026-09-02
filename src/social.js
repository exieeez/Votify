/* Votify Social: страница друзей в стиле Discord (вкладки, заявки, добавление
   по никнейму, живая активность «кто что слушает») + кастомизация профиля
   (баннер / рамка / описание). Работает поверх window.VotifyCloud. */
(function () {
  'use strict';

  const GRADS = {
    'grad-1': 'linear-gradient(135deg,#7928ca,#ff0080)',
    'grad-2': 'linear-gradient(135deg,#00f2fe,#4facfe)',
    'grad-3': 'linear-gradient(135deg,#833ab4,#fd1d1d,#fcb045)',
    'grad-4': 'linear-gradient(135deg,#10b981,#059669,#022c22)',
    'grad-5': 'linear-gradient(135deg,#ff416c,#ff4b2b)',
    'grad-6': 'linear-gradient(135deg,#00c6ff,#0072ff)',
    'grad-7': 'linear-gradient(135deg,#a855f7,#6366f1)',
    'grad-8': 'linear-gradient(135deg,#f43f5e,#fb7185)',
    'grad-9': 'linear-gradient(135deg,#18181b,#09090b)',
  };

  const cloud = () => window.VotifyCloud;
  let unsubFriends = null;
  const unsubProfiles = new Map();
  let refreshScheduled = false;
  let lastFriends = [];
  let lastProfiles = {};
  let lastRequests = [];

  function esc(value) {
    return String(value == null ? '' : value).replace(/[&<>"']/g, c => ({
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;',
    }[c]));
  }

  function bannerCss(banner) {
    const raw = String(banner || '');
    if (!raw) return '';
    if (raw.startsWith('data:image/') || raw.startsWith('https://')) {
      return `background-image:url("${raw.replace(/"/g, '%22')}");background-size:cover;background-position:center;`;
    }
    if (GRADS[raw]) return `background-image:${GRADS[raw]};`;
    return '';
  }

  function avatarInner(profile) {
    if (profile && profile.avatar) return `<img src="${profile.avatar}" alt="" />`;
    const letter = (profile && profile.displayName ? profile.displayName[0] : 'V').toUpperCase();
    return `<span>${esc(letter)}</span>`;
  }

  function setMessage(text) {
    const el = document.getElementById('friends-message');
    if (el) el.textContent = text || '';
  }

  function scheduleRefresh() {
    if (refreshScheduled) return;
    refreshScheduled = true;
    setTimeout(() => {
      refreshScheduled = false;
      refreshFriends();
    }, 250);
  }

  async function loadCode() {
    const api = cloud();
    if (!api || !api.getCurrentUser || !api.getCurrentUser()) return;
    try {
      const code = await api.getMyCode();
      const a = document.getElementById('friends-my-code');
      if (a) a.textContent = code;
      const b = document.getElementById('profile-friend-code');
      if (b) b.textContent = code;
    } catch (e) {
      /* облако недоступно */
    }
  }

  function activityRow(profile) {
    const act = profile && profile.activity;
    if (!act || !act.title) {
      return '<div class="fp-status offline"><i class="material-icons">circle</i>Не в сети</div>';
    }
    if (act.playing) {
      return `<div class="fp-status listening"><span class="fp-eq"><i></i><i></i><i></i></span>${
        act.cover ? `<img src="${esc(act.cover)}" alt="" />` : ''
      }<span class="fp-status-text">Слушает: <b>${esc(act.title)}</b> — ${esc(act.artist || '')}</span></div>`;
    }
    return `<div class="fp-status idle"><i class="material-icons">music_note</i><span class="fp-status-text">Слушал(а): ${esc(act.title)}</span></div>`;
  }

  function friendRow(uid, profile, withActions) {
    const p = profile || {};
    return `<div class="fp-friend-row" data-uid="${uid}">
      <div class="friend-avatar frame-${esc(p.frame || 'none')}">${avatarInner(p)}</div>
      <div class="fp-friend-main">
        <div class="fp-friend-name">${esc(p.displayName || 'Меломан')}</div>
        ${p.about ? `<div class="fp-friend-about">${esc(p.about)}</div>` : ''}
        ${activityRow(p)}
      </div>
      ${
        withActions
          ? `<div class="friend-actions">
              <button class="btn-icon-sm" data-accept="${uid}" title="Принять"><i class="material-icons">check</i></button>
              <button class="btn-icon-sm" data-decline="${uid}" title="Отклонить"><i class="material-icons">close</i></button>
            </div>`
          : ''
      }
    </div>`;
  }

  function renderPanes() {
    const filter = (document.getElementById('fp-filter-input')?.value || '').trim().toLowerCase();
    const friends = filter
      ? lastFriends.filter(f => ((lastProfiles[f.friendUid] || {}).displayName || '').toLowerCase().includes(filter))
      : lastFriends;
    const listBox = document.getElementById('friends-list');
    if (listBox)
      listBox.innerHTML = friends.length
        ? friends.map(f => friendRow(f.friendUid, lastProfiles[f.friendUid], false)).join('')
        : '<div class="friends-empty">Никого не нашлось</div>';

    const listening = lastFriends.filter(f => {
      const act = (lastProfiles[f.friendUid] || {}).activity;
      return act && act.playing && act.title;
    });
    const listenBox = document.getElementById('friends-listening');
    if (listenBox)
      listenBox.innerHTML = listening.length
        ? listening.map(f => friendRow(f.friendUid, lastProfiles[f.friendUid], false)).join('')
        : '<div class="friends-empty">Никто не слушает прямо сейчас</div>';

    const reqBox = document.getElementById('friends-requests');
    if (reqBox)
      reqBox.innerHTML = lastRequests.length
        ? lastRequests.map(r => friendRow(r.friendUid, lastProfiles[r.friendUid], true)).join('')
        : '<div class="friends-empty">Нет заявок</div>';

    const setBadge = (id, value) => {
      const el = document.getElementById(id);
      if (el) el.textContent = value ? String(value) : '';
    };
    setBadge('fp-count-all', lastFriends.length);
    setBadge('fp-count-listening', listening.length);
    setBadge('fp-count-requests', lastRequests.length);
  }

  async function refreshFriends() {
    const api = cloud();
    if (!api || !api.getCurrentUser || !api.getCurrentUser()) return;
    const me = api.getCurrentUser();
    let entries = [];
    try {
      entries = await api.listFriendEntries();
    } catch (e) {
      return;
    }
    lastRequests = entries.filter(en => en.status === 'pending' && en.requestedBy !== me.uid);
    lastFriends = entries.filter(en => en.status === 'accepted');
    const uids = [...lastRequests.map(r => r.friendUid), ...lastFriends.map(f => f.friendUid)];
    try {
      lastProfiles = await api.fetchProfiles(uids);
    } catch (e) {
      lastProfiles = {};
    }
    renderPanes();
    uids.forEach(uid => {
      if (unsubProfiles.has(uid) || !api.onProfileChange) return;
      unsubProfiles.set(uid, api.onProfileChange(uid, () => scheduleRefresh()));
    });
  }

  function switchTab(name) {
    document.querySelectorAll('.fp-tab').forEach(t => t.classList.toggle('active', t.dataset.ftab === name));
    document.querySelectorAll('.fp-pane').forEach(p => p.classList.toggle('active', p.dataset.fpane === name));
  }

  async function addByNickname() {
    const api = cloud();
    const input = document.getElementById('friends-name-input');
    const results = document.getElementById('friends-search-results');
    const name = (input?.value || '').trim();
    if (!api || !name) return;
    setMessage('');
    let found = [];
    try {
      found = await api.searchFriendsByName(name);
    } catch (e) {
      setMessage(api.friendlyError ? api.friendlyError(e) : String(e.message || e));
      return;
    }
    if (!found.length) {
      results.innerHTML = '';
      setMessage(`Пользователь с ником «${name}» не найден`);
      return;
    }
    if (found.length === 1) {
      try {
        await api.addFriendByUid(found[0].uid);
        setMessage(`Заявка отправлена: ${found[0].displayName || name}`);
        results.innerHTML = '';
        input.value = '';
        refreshFriends();
      } catch (e) {
        setMessage(api.friendlyError ? api.friendlyError(e) : String(e.message || e));
      }
      return;
    }
    results.innerHTML =
      '<div class="fp-add-hint">Нашлось несколько — выберите нужного:</div>' +
      found
        .map(
          u => `<div class="fp-friend-row">
            <div class="friend-avatar frame-${esc(u.frame || 'none')}">${avatarInner(u)}</div>
            <div class="fp-friend-main"><div class="fp-friend-name">${esc(u.displayName || '—')}</div></div>
            <div class="friend-actions"><button class="auth-btn" data-adduid="${u.uid}">Добавить</button></div>
          </div>`
        )
        .join('');
  }

  function openFriends() {
    const page = document.getElementById('friends-overlay');
    if (!page) return;
    page.style.display = 'flex';
    const api = cloud();
    if (!api || !api.getCurrentUser || !api.getCurrentUser()) {
      document.getElementById('friends-my-code').textContent = '—';
      document.getElementById('friends-list').innerHTML =
        '<div class="friends-empty"><button class="auth-btn" id="friends-login-hint">Войти в аккаунт</button></div>';
      document.getElementById('friends-login-hint')?.addEventListener('click', () => {
        if (api && api.openAuth) api.openAuth();
      });
      return;
    }
    loadCode();
    refreshFriends();
    if (!unsubFriends && api.onFriendsChange) {
      unsubFriends = api.onFriendsChange(() => scheduleRefresh());
    }
  }

  function closeFriends() {
    const page = document.getElementById('friends-overlay');
    if (page) page.style.display = 'none';
  }

  function copyCode() {
    const code = (
      document.getElementById('friends-my-code')?.textContent ||
      document.getElementById('profile-friend-code')?.textContent ||
      ''
    ).trim();
    if (!code || code === '—') return;
    if (navigator.clipboard) navigator.clipboard.writeText(code).catch(() => {});
    setMessage('Код скопирован');
  }

  function applyOwnLook() {
    const api = cloud();
    const profile = (api && api.getProfile && api.getProfile()) || {};
    const banner = document.getElementById('profile-banner-value')?.value || profile.banner || '';
    const frame = document.getElementById('profile-frame-value')?.value || profile.frame || 'none';
    const header = document.querySelector('#profile-overlay .profile-card-header');
    if (header) header.style.cssText = bannerCss(banner);
    const avatar = document.querySelector('#profile-overlay .profile-avatar-large');
    if (avatar) avatar.className = 'profile-avatar-large frame-' + frame;
  }

  function wire() {
    document.getElementById('nav-friends-btn')?.addEventListener('click', openFriends);
    document.getElementById('friends-close-btn')?.addEventListener('click', closeFriends);
    document.getElementById('friends-copy-code')?.addEventListener('click', copyCode);
    document.getElementById('profile-copy-code')?.addEventListener('click', copyCode);

    document.querySelectorAll('.fp-tab').forEach(tab => {
      tab.addEventListener('click', () => switchTab(tab.dataset.ftab));
    });
    document.getElementById('fp-filter-input')?.addEventListener('input', renderPanes);

    document.getElementById('friends-add-btn')?.addEventListener('click', addByNickname);
    document.getElementById('friends-name-input')?.addEventListener('keydown', e => {
      if (e.key === 'Enter') addByNickname();
    });
    document.getElementById('friends-add-code-btn')?.addEventListener('click', async () => {
      const api = cloud();
      const input = document.getElementById('friends-code-input');
      if (!api || !input) return;
      try {
        await api.addFriendByCode(input.value);
        setMessage('Заявка отправлена');
        input.value = '';
        refreshFriends();
      } catch (e) {
        setMessage(api.friendlyError ? api.friendlyError(e) : String(e.message || e));
      }
    });
    document.getElementById('friends-search-results')?.addEventListener('click', async e => {
      const btn = e.target.closest('[data-adduid]');
      if (!btn) return;
      const api = cloud();
      try {
        await api.addFriendByUid(btn.dataset.adduid);
        setMessage('Заявка отправлена');
        btn.closest('.fp-friend-row')?.remove();
        refreshFriends();
      } catch (err) {
        setMessage(api.friendlyError ? api.friendlyError(err) : String(err.message || err));
      }
    });

    document.getElementById('friends-requests')?.addEventListener('click', async e => {
      const api = cloud();
      if (!api) return;
      const accept = e.target.closest('[data-accept]');
      const decline = e.target.closest('[data-decline]');
      if (accept) {
        await api.respondFriend(accept.dataset.accept, true).catch(() => {});
        refreshFriends();
      }
      if (decline) {
        await api.respondFriend(decline.dataset.decline, false).catch(() => {});
        refreshFriends();
      }
    });

    document.getElementById('profile-frame-chips')?.addEventListener('click', e => {
      const chip = e.target.closest('.frame-chip');
      if (!chip) return;
      const hidden = document.getElementById('profile-frame-value');
      if (hidden) hidden.value = chip.dataset.frame;
      document
        .querySelectorAll('#profile-frame-chips .frame-chip')
        .forEach(c => c.classList.toggle('active', c === chip));
      applyOwnLook();
    });

    document.getElementById('profile-banner-presets')?.addEventListener('click', e => {
      const chip = e.target.closest('.banner-chip');
      if (!chip) return;
      const hidden = document.getElementById('profile-banner-value');
      if (hidden) hidden.value = chip.dataset.banner;
      document
        .querySelectorAll('#profile-banner-presets .banner-chip')
        .forEach(c => c.classList.toggle('active', c === chip));
      applyOwnLook();
    });

    document.getElementById('profile-banner-url')?.addEventListener('change', e => {
      const value = e.target.value.trim();
      if (/^https:\/\/[^\s]{1,300}$/.test(value)) {
        const hidden = document.getElementById('profile-banner-value');
        if (hidden) hidden.value = value;
        applyOwnLook();
      }
    });

    document.getElementById('profile-banner-input')?.addEventListener('change', e => {
      const file = e.target.files && e.target.files[0];
      e.target.value = '';
      if (!file) return;
      if (file.size > 200000) {
        setMessage('Картинка баннера — до 200 КБ');
        return;
      }
      const reader = new FileReader();
      reader.onload = () => {
        const hidden = document.getElementById('profile-banner-value');
        if (hidden) hidden.value = String(reader.result || '');
        applyOwnLook();
      };
      reader.readAsDataURL(file);
    });

    window.addEventListener('votify:auth-changed', () => {
      loadCode();
      applyOwnLook();
    });
    applyOwnLook();
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', wire);
  else wire();
})();
