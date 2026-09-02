/* Votify Social: друзья, их активность и кастомизация профиля
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

  function friendCard(entry, profile) {
    const p = profile || {};
    const act = p.activity;
    const activity =
      act && act.title
        ? `<div class="friend-activity${act.playing ? ' playing' : ''}">
            ${act.cover ? `<img src="${esc(act.cover)}" alt="" />` : '<i class="material-icons">music_note</i>'}
            <span>${act.playing ? 'Сейчас слушает: ' : 'Слушал(а): '}<b>${esc(act.title)}</b> — ${esc(act.artist || '')}</span>
          </div>`
        : '<div class="friend-activity muted"><i class="material-icons">music_off</i><span>Сейчас ничего не слушает</span></div>';
    return `<div class="friend-card">
      <div class="friend-banner${p.banner ? '' : ' empty'}" style="${bannerCss(p.banner)}"></div>
      <div class="friend-card-body">
        <div class="friend-avatar frame-${esc(p.frame || 'none')}">${avatarInner(p)}</div>
        <div class="friend-info">
          <strong>${esc(p.displayName || 'Меломан')}</strong>
          ${p.about ? `<span class="friend-about">${esc(p.about)}</span>` : ''}
          ${activity}
        </div>
      </div>
    </div>`;
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
    const requests = entries.filter(en => en.status === 'pending' && en.requestedBy !== me.uid);
    const friends = entries.filter(en => en.status === 'accepted');
    const uids = [...requests.map(r => r.friendUid), ...friends.map(f => f.friendUid)];
    let profiles = {};
    try {
      profiles = await api.fetchProfiles(uids);
    } catch (e) {
      profiles = {};
    }

    const reqBox = document.getElementById('friends-requests');
    if (reqBox)
      reqBox.innerHTML = requests.length
        ? requests
            .map(r => {
              const p = profiles[r.friendUid] || {};
              return `<div class="friend-row">
                <div class="friend-avatar frame-${esc(p.frame || 'none')}">${avatarInner(p)}</div>
                <div class="friend-info"><strong>${esc(p.displayName || 'Меломан')}</strong><span>Хочет добавить вас в друзья</span></div>
                <div class="friend-actions">
                  <button class="btn-icon-sm" data-accept="${r.friendUid}" title="Принять"><i class="material-icons">check</i></button>
                  <button class="btn-icon-sm" data-decline="${r.friendUid}" title="Отклонить"><i class="material-icons">close</i></button>
                </div>
              </div>`;
            })
            .join('')
        : '<div class="friends-empty">Нет заявок</div>';

    const listBox = document.getElementById('friends-list');
    if (listBox)
      listBox.innerHTML = friends.length
        ? friends.map(f => friendCard(f, profiles[f.friendUid])).join('')
        : '<div class="friends-empty">Пока никого нет — поделитесь кодом с друзьями</div>';

    uids.forEach(uid => {
      if (unsubProfiles.has(uid) || !api.onProfileChange) return;
      unsubProfiles.set(uid, api.onProfileChange(uid, () => scheduleRefresh()));
    });
  }

  function openFriends() {
    const overlay = document.getElementById('friends-overlay');
    if (!overlay) return;
    overlay.style.display = 'flex';
    const api = cloud();
    if (!api || !api.getCurrentUser || !api.getCurrentUser()) {
      const my = document.getElementById('friends-my-code');
      if (my) my.textContent = '—';
      const req = document.getElementById('friends-requests');
      if (req) req.innerHTML = '<div class="friends-empty">Войдите в аккаунт, чтобы добавлять друзей</div>';
      const list = document.getElementById('friends-list');
      if (list)
        list.innerHTML =
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
    const overlay = document.getElementById('friends-overlay');
    if (overlay) overlay.style.display = 'none';
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
    const banner =
      document.getElementById('profile-banner-value')?.value || profile.banner || '';
    const frame = document.getElementById('profile-frame-value')?.value || profile.frame || 'none';
    const header = document.querySelector('#profile-overlay .profile-card-header');
    if (header) header.style.cssText = bannerCss(banner);
    const avatar = document.querySelector('#profile-overlay .profile-avatar-large');
    if (avatar) avatar.className = 'profile-avatar-large frame-' + frame;
  }

  function wire() {
    document.getElementById('nav-friends-btn')?.addEventListener('click', openFriends);
    document.getElementById('friends-close-btn')?.addEventListener('click', closeFriends);
    document.getElementById('friends-overlay')?.addEventListener('click', e => {
      if (e.target.id === 'friends-overlay') closeFriends();
    });
    document.getElementById('friends-copy-code')?.addEventListener('click', copyCode);
    document.getElementById('profile-copy-code')?.addEventListener('click', copyCode);

    document.getElementById('friends-add-btn')?.addEventListener('click', async () => {
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
