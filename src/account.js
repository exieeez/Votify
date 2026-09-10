/* Votify — Account page controller (profile + friends by @username).
 * Classic script (no bundler): relies on globals from main.js
 * (switchScreen, escapeHtml, showToast, formatTrackCount, openPlaylist,
 * translations, appSettings, playlists) and window.VotifyCloud.
 */
/* global window, document, navigator, localStorage, CSS, escapeHtml, showToast,
   switchScreen, formatTrackCount, openPlaylist, translations, appSettings, playlists */
(() => {
  'use strict';

  // ---------- tiny helpers ----------

  const $ = id => document.getElementById(id);

  function esc(value) {
    if (typeof escapeHtml === 'function') return escapeHtml(value);
    return String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function toast(message) {
    if (typeof showToast === 'function') showToast(message);
    else console.log('[Votify]', message);
  }

  function lang() {
    try {
      return (typeof appSettings !== 'undefined' && appSettings.lang) || 'ru';
    } catch {
      return 'ru';
    }
  }

  function t(key) {
    try {
      if (typeof translations !== 'undefined') {
        const table = translations[lang()] || translations.ru || {};
        if (table[key]) return table[key];
        if (translations.ru && translations.ru[key]) return translations.ru[key];
      }
    } catch {
      /* ignore */
    }
    return key;
  }

  function plural(count, one, few, many) {
    if (lang() !== 'ru') return count === 1 ? one : many;
    const mod10 = count % 10;
    const mod100 = count % 100;
    if (mod10 === 1 && mod100 !== 11) return one;
    if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return few;
    return many;
  }

  function trackCountText(count) {
    if (typeof formatTrackCount === 'function') return formatTrackCount(count);
    return `${count}`;
  }

  function cloud() {
    return window.VotifyCloud || null;
  }

  function validators() {
    return window.VotifySocialValidate || null;
  }

  function friendly(error) {
    try {
      if (cloud()?.friendlyError) return cloud().friendlyError(error);
    } catch {
      /* ignore */
    }
    return error?.message || String(error || 'Error');
  }

  function go(screenId, btnId) {
    if (typeof switchScreen === 'function') switchScreen(screenId, btnId);
  }

  function localPlaylists() {
    try {
      if (typeof playlists !== 'undefined' && playlists && typeof playlists === 'object') {
        return playlists;
      }
    } catch {
      /* ignore */
    }
    try {
      return JSON.parse(localStorage.getItem('votify-playlists') || '{}') || {};
    } catch {
      return {};
    }
  }

  function openExternal(url) {
    if (!url) return;
    if (window.electronAPI?.openExternal) window.electronAPI.openExternal(url);
    else window.open(url, '_blank', 'noopener');
  }

  function copyText(text, doneMessage) {
    const finish = () => toast(doneMessage || t('account-copied'));
    if (navigator.clipboard?.writeText) {
      navigator.clipboard.writeText(text).then(finish, () => toast(text));
    } else {
      const area = document.createElement('textarea');
      area.value = text;
      document.body.appendChild(area);
      area.select();
      try {
        document.execCommand('copy');
      } catch {
        /* ignore */
      }
      area.remove();
      finish();
    }
  }

  function avatarLetter(name) {
    const letter = String(name || 'V').trim()[0] || 'V';
    return esc(letter.toUpperCase());
  }

  // ---------- state ----------

  const S = {
    viewingUid: null, // null = own profile
    viewingUsername: null,
    renderToken: 0,
    searchTimer: null,
    searchToken: 0,
    usernameTimer: null,
    usernameToken: 0,
    editAvatar: undefined, // undefined = unchanged, '' = removed, dataUrl = new
    editPrivate: false,
    editBusy: false,
    modal: null, // { kind, uid, title }
  };

  // ---------- shared row builders ----------

  function avatarHtml(user, cls = 'acc-user-avatar') {
    const name = esc(user.displayName || user.username || 'V');
    if (user.avatar) {
      return `<div class="${cls}"><img src="${esc(user.avatar)}" alt="${name}" loading="lazy" /></div>`;
    }
    return `<div class="${cls}">${avatarLetter(user.displayName || user.username)}</div>`;
  }

  function followButtonHtml(state, uid) {
    if (state === 'self') return '';
    if (state === 'following') {
      return `<button class="acc-mini-btn subscribed" data-acc-unfollow="${esc(uid)}">${esc(t('account-following'))}</button>`;
    }
    if (state === 'requested') {
      return `<button class="acc-mini-btn" data-acc-unfollow="${esc(uid)}">${esc(t('account-requested'))}</button>`;
    }
    return `<button class="acc-mini-btn solid" data-acc-follow="${esc(uid)}">${esc(t('account-follow'))}</button>`;
  }

  function userRowHtml(user, actionHtml = '') {
    const sub = user.username ? `@${esc(user.username)}` : esc(t('account-no-username'));
    const lock = user.isPrivate ? '<i class="material-icons">lock</i>' : '';
    return `
      <div class="acc-user-row" data-acc-open-user="${esc(user.uid)}" role="button" tabindex="0">
        ${avatarHtml(user)}
        <div class="acc-user-meta">
          <div class="acc-user-name">${esc(user.displayName || '—')} ${lock}</div>
          <div class="acc-user-sub">${sub}</div>
        </div>
        ${actionHtml}
      </div>`;
  }

  function playlistCardHtml(name, count, cover, ownerView) {
    const coverHtml = cover
      ? `<img class="card-cover" src="${esc(cover)}" alt="${esc(name)}" loading="lazy" />`
      : `<div class="card-cover-placeholder"><i class="material-icons">queue_music</i></div>`;
    const playBtn = ownerView
      ? `<button class="card-play-btn" data-acc-play-playlist="${esc(name)}" title="${esc(t('account-play'))}"><i class="material-icons">play_arrow</i></button>`
      : '';
    return `
      <div class="playlist-card" data-acc-playlist="${esc(name)}" data-acc-owner="${ownerView ? '1' : ''}">
        <div class="card-cover-wrap">${coverHtml}${playBtn}</div>
        <div class="card-info">
          <div class="card-title">${esc(name)}</div>
          <div class="card-sub">${esc(trackCountText(count))}</div>
        </div>
      </div>`;
  }

  function loadingHtml() {
    return `<div class="acc-loading"><i class="material-icons">sync</i><span>${esc(t('account-loading'))}</span></div>`;
  }

  // ---------- own public snapshot ----------

  async function loadOwnContext(user) {
    const api = cloud();
    const result = {
      publicProfile: null,
      friends: [],
      incoming: [],
      outgoing: [],
      socialOk: true,
      socialError: '',
    };
    if (!api?.isAvailable?.()) {
      result.socialOk = false;
      return result;
    }
    if (user.isAnonymous) return result;
    try {
      const [pub, friends, incoming, outgoing] = await Promise.all([
        api.fetchPublicProfile(user.uid).catch(() => null),
        api.listFriends(user.uid, 12).catch(() => []),
        api.listIncomingRequests(30).catch(() => []),
        api.listOutgoingRequests(30).catch(() => []),
      ]);
      result.publicProfile = pub;
      result.friends = friends || [];
      result.incoming = incoming || [];
      result.outgoing = outgoing || [];
    } catch (error) {
      result.socialOk = false;
      result.socialError = friendly(error);
    }
    return result;
  }

  // ---------- render: own profile ----------

  function headerHtml({ profile, user, foreign, followState }) {
    const isGuest = !!user?.isAnonymous;
    const name = profile.displayName || user?.displayName || t('account-guest-name');
    const username = profile.username || '';
    const avatarImg = profile.avatar
      ? `<img src="${esc(profile.avatar)}" alt="${esc(name)}" />`
      : '';
    const avatarBody = profile.avatar
      ? avatarImg
      : `<div class="acc-avatar-fallback acc-avatar" style="width:100%;height:100%">${avatarLetter(name)}</div>`;
    const badge = !foreign
      ? `<button class="acc-avatar-badge" data-acc-settings title="${esc(t('account-settings'))}"><i class="material-icons">settings</i></button>`
      : '';
    const side = foreign
      ? `<button class="acc-icon-btn" data-acc-share="${esc(username)}" title="${esc(t('account-share'))}"><i class="material-icons">share</i></button>`
      : `
        ${username ? `<button class="acc-icon-btn" data-acc-share="${esc(username)}" title="${esc(t('account-share'))}"><i class="material-icons">share</i></button>` : ''}
        <button class="acc-icon-btn danger" data-acc-logout title="${esc(t('account-logout'))}"><i class="material-icons">logout</i></button>`;
    const usernameLine = username
      ? `<div class="acc-username">@${esc(username)}
           <button data-acc-copy-username="${esc(username)}" title="${esc(t('account-copy'))}"><i class="material-icons">content_copy</i></button>
         </div>`
      : `<div class="acc-username">${esc(isGuest ? t('account-guest-handle') : t('account-no-username-set'))}</div>`;
    const privateChip =
      profile.isPrivate && !foreign
        ? `<div style="margin-bottom:12px"><span class="acc-private-chip"><i class="material-icons">lock</i>${esc(t('account-private'))}</span></div>`
        : '';
    const bio = profile.about ? `<p class="acc-bio">${esc(profile.about)}</p>` : '';
    const links = linksHtml(profile.links);
    const followBtn = foreign ? followActionHtml(followState) : '';
    const editBtn =
      !foreign && !isGuest && username
        ? `<button class="acc-primary-btn" data-acc-edit>${esc(t('account-edit-profile'))}</button>`
        : '';
    const claimBtn =
      !foreign && !isGuest && !username
        ? `<button class="acc-primary-btn" data-acc-edit>${esc(t('account-claim-username'))}</button>`
        : '';
    const backBtn = foreign
      ? `<button class="acc-icon-btn" data-acc-back title="${esc(t('account-back'))}" style="margin-bottom:16px"><i class="material-icons">arrow_back</i></button>`
      : '';
    return `
      ${backBtn}
      <div class="acc-header">
        <div class="acc-avatar-row">
          <div class="acc-avatar-wrap">
            <div class="acc-avatar">${avatarBody}</div>
            ${badge}
          </div>
          <div class="acc-header-side">${side}</div>
        </div>
        <h1 class="acc-name">${esc(name)}</h1>
        ${usernameLine}
        ${privateChip}
        ${bio}
        ${links}
        <div class="acc-actions">${followBtn}${editBtn}${claimBtn}</div>
      </div>`;
  }

  function followActionHtml(state) {
    if (state === 'following') {
      return `<button class="acc-primary-btn is-following" data-acc-unfollow-user>${esc(t('account-following'))}</button>`;
    }
    if (state === 'requested') {
      return `<button class="acc-primary-btn is-following" data-acc-unfollow-user>${esc(t('account-requested'))}</button>`;
    }
    return `<button class="acc-primary-btn" data-acc-follow-user>${esc(t('account-follow-btn'))}</button>`;
  }

  function linksHtml(links = {}) {
    const api = validators();
    const items = [];
    const defs = [
      ['telegram', 'Telegram', 'send'],
      ['soundcloud', 'SoundCloud', 'graphic_eq'],
      ['vk', 'ВКонтакте', 'link'],
    ];
    for (const [kind, label, icon] of defs) {
      const handle = links[kind];
      if (!handle) continue;
      const url = api?.linkUrl ? api.linkUrl(kind, handle) : '';
      items.push(
        `<button class="acc-link-chip" data-acc-link="${esc(url)}"><i class="material-icons">${icon}</i>${esc(label)}</button>`
      );
    }
    if (!items.length) return '';
    return `<div class="acc-links">${items.join('')}</div>`;
  }

  function statsHtml(counts, locked) {
    if (locked) {
      return `
        <div class="acc-stats">
          <span class="acc-stat" style="cursor:default"><b>–</b><span>${esc(t('account-followers'))}</span></span>
          <span class="acc-stat" style="cursor:default"><b>–</b><span>${esc(t('account-following-label'))}</span></span>
        </div>`;
    }
    return `
      <div class="acc-stats">
        <button class="acc-stat" data-acc-list="followers"><b>${counts.followers}</b><span>${esc(plural(counts.followers, t('account-follower-1'), t('account-follower-2'), t('account-follower-5')))}</span></button>
        <button class="acc-stat" data-acc-list="following"><b>${counts.following}</b><span>${esc(plural(counts.following, t('account-sub-1'), t('account-sub-2'), t('account-sub-5')))}</span></button>
      </div>`;
  }

  function searchCardHtml() {
    return `
      <div class="acc-section">
        <div class="acc-section-head static"><h2>${esc(t('account-find-friends'))}</h2></div>
        <div class="acc-card">
          <div class="acc-search-row">
            <div class="acc-search-field">
              <i class="material-icons">search</i>
              <span class="at">@</span>
              <input type="text" id="acc-search-input" placeholder="${esc(t('account-search-ph'))}" autocomplete="off" autocapitalize="off" spellcheck="false" maxlength="32" />
            </div>
          </div>
          <div class="acc-search-results" id="acc-search-results">
            <div class="acc-search-hint">${esc(t('account-search-hint'))}</div>
          </div>
        </div>
      </div>`;
  }

  function requestsHtml(incoming, outgoing) {
    if (!incoming.length && !outgoing.length) return '';
    const incomingRows = incoming
      .map(
        user => `
        <div class="acc-user-row" data-acc-open-user="${esc(user.uid)}" role="button" tabindex="0">
          ${avatarHtml(user)}
          <div class="acc-user-meta">
            <div class="acc-user-name">${esc(user.displayName || '—')}</div>
            <div class="acc-user-sub">${user.username ? `@${esc(user.username)}` : ''}</div>
          </div>
          <div class="acc-row-actions">
            <button class="acc-accept-btn" data-acc-accept="${esc(user.uid)}" title="${esc(t('account-accept'))}"><i class="material-icons">check</i></button>
            <button class="acc-decline-btn" data-acc-decline="${esc(user.uid)}" title="${esc(t('account-decline'))}"><i class="material-icons">close</i></button>
          </div>
        </div>`
      )
      .join('');
    const outgoingRows = outgoing
      .map(
        user => `
        <div class="acc-user-row" data-acc-open-user="${esc(user.uid)}" role="button" tabindex="0">
          ${avatarHtml(user)}
          <div class="acc-user-meta">
            <div class="acc-user-name">${esc(user.displayName || '—')}</div>
            <div class="acc-user-sub">@${esc(user.username || '')} · ${esc(t('account-requested'))}</div>
          </div>
          <div class="acc-row-actions">
            <button class="acc-decline-btn" data-acc-cancel-request="${esc(user.uid)}" title="${esc(t('account-cancel-request'))}"><i class="material-icons">close</i></button>
          </div>
        </div>`
      )
      .join('');
    return `
      <div class="acc-section">
        <div class="acc-section-head static">
          <h2>${esc(t('account-requests'))}</h2>
          <span class="acc-section-count">${incoming.length + outgoing.length}</span>
        </div>
        <div class="acc-card" style="padding:8px 12px">
          ${incomingRows}${outgoingRows}
        </div>
      </div>`;
  }

  function friendsHtml(friends, isOwn) {
    if (!friends.length) {
      if (!isOwn) return '';
      return `
        <div class="acc-section">
          <div class="acc-section-head static"><h2>${esc(t('account-friends'))}</h2></div>
          <div class="acc-empty">${esc(t('account-friends-empty'))}</div>
        </div>`;
    }
    const rows = friends
      .slice(0, 8)
      .map(user => userRowHtml(user, followButtonHtml('following', user.uid)))
      .join('');
    return `
      <div class="acc-section">
        <button class="acc-section-head" data-acc-list="friends">
          <h2>${esc(t('account-friends'))}</h2>
          <i class="material-icons">chevron_right</i>
        </button>
        <div class="acc-card" style="padding:8px 12px">${rows}</div>
      </div>`;
  }

  function playlistsHtml(title, cards) {
    const body = cards.length
      ? `<div class="acc-grid">${cards.join('')}</div>`
      : `<div class="acc-empty">${esc(t('account-no-playlists'))}</div>`;
    return `
      <div class="acc-section">
        <div class="acc-section-head static"><h2>${esc(title)}</h2></div>
        ${body}
      </div>`;
  }

  function lockHtml() {
    return `<div class="acc-private-lock"><i class="material-icons">lock</i><p>${esc(t('account-private-lock'))}</p></div>`;
  }

  function ownPlaylistCards() {
    const all = localPlaylists();
    return Object.keys(all)
      .filter(name => name && name !== 'Избранное')
      .sort((a, b) => a.localeCompare(b, 'ru'))
      .map(name => {
        const list = Array.isArray(all[name]) ? all[name] : [];
        const withCover = list.find(track => track && track.cover);
        return playlistCardHtml(name, list.length, withCover?.cover || '', true);
      });
  }

  // ---------- render entry points ----------

  async function render() {
    const wrap = $('acc-wrap');
    if (!wrap) return;
    const token = ++S.renderToken;
    const api = cloud();
    const user = api?.getCurrentUser?.() || null;

    if (S.viewingUid && (!user || S.viewingUid !== user.uid)) {
      await renderForeign(wrap, token, user, S.viewingUid, S.viewingUsername);
      return;
    }
    S.viewingUid = null;
    S.viewingUsername = null;

    if (!user) {
      wrap.innerHTML = `
        <div class="acc-header" style="align-items:stretch">
          <h1 class="acc-name">${esc(t('account-title'))}</h1>
          <div class="acc-notice">
            <i class="material-icons">account_circle</i>
            <div>
              <b>${esc(t('account-login-title'))}</b><br />
              ${esc(t('account-login-desc'))}<br />
              <button class="acc-mini-btn solid" data-acc-login>${esc(t('account-login-btn'))}</button>
            </div>
          </div>
        </div>
        ${playlistsHtml(t('account-my-playlists'), ownPlaylistCards())}`;
      wireStatic(wrap);
      return;
    }

    wrap.innerHTML = loadingHtml();
    const profile = { ...(api.getProfile?.() || {}) };
    const context = await loadOwnContext(user);
    if (token !== S.renderToken) return;
    // First visit: publish the public snapshot so friends can find this user.
    if (!context.publicProfile && !user.isAnonymous && api?.isAvailable?.()) {
      try {
        await api.ensurePublicSnapshot();
        context.publicProfile = await api.fetchPublicProfile(user.uid).catch(() => null);
      } catch {
        /* offline or outdated rules: stay local, banner below explains */
      }
      if (token !== S.renderToken) return;
    }

    const pub = context.publicProfile || {};
    const merged = {
      displayName: profile.displayName || user.displayName || '',
      username: profile.username || pub.username || '',
      avatar: profile.avatar || pub.avatar || '',
      about: profile.about || pub.about || '',
      links: profile.links || pub.links || {},
      isPrivate: !!profile.isPrivate,
    };
    const counts = {
      followers: pub.followersCount || 0,
      following: pub.followingCount || 0,
    };
    const isGuest = !!user.isAnonymous;

    let banner = '';
    if (isGuest) {
      banner = `
        <div class="acc-notice">
          <i class="material-icons">person_outline</i>
          <div>
            <b>${esc(t('account-guest-title'))}</b><br />
            ${esc(t('account-guest-desc'))}<br />
            <button class="acc-mini-btn solid" data-acc-register>${esc(t('account-create-btn'))}</button>
          </div>
        </div>`;
    } else if (!context.socialOk && api?.isAvailable?.()) {
      banner = `
        <div class="acc-notice">
          <i class="material-icons">cloud_off</i>
          <div><b>${esc(t('account-social-off'))}</b><br />${esc(context.socialError || t('account-social-off-desc'))}</div>
        </div>`;
    } else if (!api?.isAvailable?.()) {
      banner = `
        <div class="acc-notice">
          <i class="material-icons">cloud_off</i>
          <div><b>${esc(t('account-offline'))}</b><br />${esc(t('account-offline-desc'))}</div>
        </div>`;
    }

    wrap.innerHTML =
      headerHtml({ profile: merged, user, foreign: false }) +
      (isGuest ? '' : statsHtml(counts, false)) +
      banner +
      (isGuest ? '' : searchCardHtml()) +
      (isGuest ? '' : requestsHtml(context.incoming, context.outgoing)) +
      (isGuest ? '' : friendsHtml(context.friends, true)) +
      playlistsHtml(t('account-my-playlists'), ownPlaylistCards());
    wireStatic(wrap);
    wireSearch(wrap);
  }

  async function renderForeign(wrap, token, user, uid, usernameHint) {
    const api = cloud();
    wrap.innerHTML = loadingHtml();
    let profile = null;
    let error = '';
    try {
      if (!api?.isAvailable?.()) throw new Error(t('account-offline-desc'));
      if (!user) {
        api.openAuth?.();
        S.viewingUid = null;
        await render();
        return;
      }
      profile =
        (uid ? await api.fetchPublicProfile(uid).catch(() => null) : null) ||
        (usernameHint ? await api.fetchProfileByUsername(usernameHint).catch(() => null) : null);
      if (!profile) throw new Error(t('account-user-not-found'));
      if (profile.uid === user.uid) {
        S.viewingUid = null;
        S.viewingUsername = null;
        await render();
        return;
      }
    } catch (err) {
      error = friendly(err);
    }
    if (token !== S.renderToken) return;
    if (!profile) {
      wrap.innerHTML = `
        <button class="acc-icon-btn" data-acc-back style="margin-bottom:16px"><i class="material-icons">arrow_back</i></button>
        <div class="acc-notice"><i class="material-icons">person_search</i><div>${esc(error)}</div></div>`;
      wireStatic(wrap);
      return;
    }
    S.viewingUid = profile.uid;
    S.viewingUsername = profile.username || null;

    let followState = 'none';
    try {
      followState = (await api.getFollowState(profile.uid))?.state || 'none';
    } catch {
      /* offline: keep default */
    }
    if (token !== S.renderToken) return;

    const canSee = !profile.isPrivate || followState === 'following';
    const counts = {
      followers: profile.followersCount || 0,
      following: profile.followingCount || 0,
    };
    const cards = canSee
      ? (profile.showcase || []).map(item =>
          playlistCardHtml(item.name, item.count, item.cover, false)
        )
      : [];
    wrap.innerHTML =
      headerHtml({ profile, user, foreign: true, followState }) +
      statsHtml(counts, !canSee) +
      `<div class="acc-section">
         <div class="acc-section-head static"><h2>${esc(t('account-public-playlists'))}</h2></div>
         ${canSee ? (cards.length ? `<div class="acc-grid">${cards.join('')}</div>` : `<div class="acc-empty">${esc(t('account-no-playlists'))}</div>`) : lockHtml()}
       </div>`;
    wireStatic(wrap);
    const listButtons = wrap.querySelectorAll('[data-acc-list]');
    listButtons.forEach(btn => {
      if (!canSee) {
        btn.onclick = event => {
          event.stopPropagation();
          toast(t('account-private-lock-short'));
        };
      }
    });
  }

  // ---------- search ----------

  function wireSearch(wrap) {
    const input = wrap.querySelector('#acc-search-input');
    const results = wrap.querySelector('#acc-search-results');
    if (!input || !results) return;
    input.addEventListener('input', () => {
      if (S.searchTimer) clearTimeout(S.searchTimer);
      const query = input.value;
      const api = validators();
      const normalized = api ? api.normalizeUsername(query) : String(query || '').trim();
      if (normalized.replace(/[^a-z0-9_]/g, '').length < 2) {
        results.innerHTML = `<div class="acc-search-hint">${esc(t('account-search-hint'))}</div>`;
        return;
      }
      results.innerHTML = `<div class="acc-search-hint">${esc(t('account-searching'))}</div>`;
      S.searchTimer = setTimeout(() => runSearch(normalized, results), 350);
    });
    input.addEventListener('keydown', event => {
      if (event.key === 'Enter') {
        event.preventDefault();
        if (S.searchTimer) clearTimeout(S.searchTimer);
        runSearch(input.value, results);
      }
    });
  }

  async function runSearch(query, resultsEl) {
    const token = ++S.searchToken;
    const api = cloud();
    try {
      if (!api?.isAvailable?.()) throw new Error(t('account-offline-desc'));
      const users = await api.searchUsers(query, 8);
      if (token !== S.searchToken) return;
      if (!users.length) {
        resultsEl.innerHTML = `<div class="acc-search-hint">${esc(t('account-search-empty'))}</div>`;
        return;
      }
      const me = api.getCurrentUser?.();
      resultsEl.innerHTML = users
        .map(user => {
          const state = me && user.uid === me.uid ? 'self' : 'unknown';
          const action =
            state === 'self'
              ? `<span class="acc-user-sub">${esc(t('account-it-is-you'))}</span>`
              : `<span data-acc-row-action="${esc(user.uid)}"><button class="acc-mini-btn" disabled>…</button></span>`;
          return userRowHtml(user, action);
        })
        .join('');
      wireDynamic(resultsEl);
      // Resolve follow states in the background without blocking the list.
      const states = await Promise.all(
        users.map(user =>
          me && user.uid === me.uid
            ? Promise.resolve({ state: 'self' })
            : api.getFollowState(user.uid).catch(() => ({ state: 'none' }))
        )
      );
      if (token !== S.searchToken) return;
      states.forEach((result, index) => {
        const holder = resultsEl.querySelector(
          `[data-acc-row-action="${CSS.escape(users[index].uid)}"]`
        );
        if (holder && result.state !== 'self') {
          holder.outerHTML = followButtonHtml(result.state, users[index].uid);
        }
      });
      wireDynamic(resultsEl);
    } catch (error) {
      if (token !== S.searchToken) return;
      resultsEl.innerHTML = `<div class="acc-search-hint">${esc(friendly(error))}</div>`;
    }
  }

  // ---------- followers modal ----------

  async function openListModal(kind) {
    const api = cloud();
    const overlay = $('acc-list-overlay');
    const body = $('acc-list-body');
    const title = $('acc-list-title');
    if (!overlay || !body || !title) return;
    const user = api?.getCurrentUser?.();
    const targetUid = S.viewingUid || user?.uid;
    if (!targetUid) return;
    const titles = {
      followers: t('account-followers-title'),
      following: t('account-following-title'),
      friends: t('account-friends'),
    };
    S.modal = { kind, uid: targetUid };
    title.textContent = titles[kind] || '';
    body.innerHTML = loadingHtml();
    overlay.style.display = 'flex';
    try {
      let users = [];
      if (kind === 'followers') users = await api.listFollowers(targetUid, 100);
      else if (kind === 'following') users = await api.listFollowing(targetUid, 100);
      else users = await api.listFriends(targetUid, 100);
      renderModalList(body, users, kind, targetUid, user?.uid);
    } catch (error) {
      body.innerHTML = `<div class="acc-empty">${esc(friendly(error))}</div>`;
    }
  }

  async function renderModalList(body, users, kind, targetUid, myUid) {
    const api = cloud();
    if (!users.length) {
      body.innerHTML = `<div class="acc-empty">${esc(t('account-list-empty'))}</div>`;
      return;
    }
    const ownList = targetUid === myUid;
    body.innerHTML = users
      .map(user => {
        let action = '';
        if (user.uid === myUid) {
          action = `<span class="acc-user-sub">${esc(t('account-it-is-you'))}</span>`;
        } else if (ownList && kind === 'followers') {
          action = `<button class="acc-mini-btn" data-acc-remove-follower="${esc(user.uid)}">${esc(t('account-remove'))}</button>`;
        } else {
          action = `<span data-acc-row-action="${esc(user.uid)}"><button class="acc-mini-btn" disabled>…</button></span>`;
        }
        return userRowHtml(user, action);
      })
      .join('');
    wireDynamic(body);
    const pending = users.filter(user => user.uid !== myUid && !(ownList && kind === 'followers'));
    if (!pending.length || !api) return;
    const states = await Promise.all(
      pending.map(user => api.getFollowState(user.uid).catch(() => ({ state: 'none' })))
    );
    states.forEach((result, index) => {
      const holder = body.querySelector(
        `[data-acc-row-action="${CSS.escape(pending[index].uid)}"]`
      );
      if (holder) holder.outerHTML = followButtonHtml(result.state, pending[index].uid);
    });
    wireDynamic(body);
  }

  function closeListModal() {
    const overlay = $('acc-list-overlay');
    if (overlay) overlay.style.display = 'none';
    S.modal = null;
  }

  // ---------- actions ----------

  async function withBusy(button, job) {
    if (!button || button.disabled) return;
    const original = button.innerHTML;
    button.disabled = true;
    button.innerHTML = `<span style="opacity:.6">${esc(t('account-wait'))}</span>`;
    try {
      await job();
    } finally {
      if (document.contains(button)) {
        button.disabled = false;
        button.innerHTML = original;
      }
    }
  }

  async function refreshAfterSocialChange() {
    if (S.modal) {
      const { kind } = S.modal;
      await openListModal(kind);
    }
    await render();
  }

  function wireFollowButtons(root) {
    root.querySelectorAll('[data-acc-follow]').forEach(btn => {
      btn.onclick = event => {
        event.stopPropagation();
        withBusy(btn, async () => {
          try {
            await cloud().followUid(btn.getAttribute('data-acc-follow'));
            toast(t('account-followed'));
          } catch (error) {
            toast(friendly(error));
            return;
          }
          await refreshAfterSocialChange();
        });
      };
    });
    root.querySelectorAll('[data-acc-unfollow]').forEach(btn => {
      btn.onclick = event => {
        event.stopPropagation();
        withBusy(btn, async () => {
          try {
            await cloud().unfollowUid(btn.getAttribute('data-acc-unfollow'));
          } catch (error) {
            toast(friendly(error));
            return;
          }
          await refreshAfterSocialChange();
        });
      };
    });
  }

  function wireUserRows(root) {
    root.querySelectorAll('[data-acc-open-user]').forEach(row => {
      const open = () => openUser(row.getAttribute('data-acc-open-user'));
      row.onclick = event => {
        if (event.target.closest('button')) return;
        open();
      };
      row.onkeydown = event => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          open();
        }
      };
    });
  }

  function wireDynamic(root) {
    wireFollowButtons(root);
    wireUserRows(root);
    root.querySelectorAll('[data-acc-accept]').forEach(btn => {
      btn.onclick = event => {
        event.stopPropagation();
        withBusy(btn, async () => {
          try {
            await cloud().acceptRequest(btn.getAttribute('data-acc-accept'));
            toast(t('account-request-accepted'));
          } catch (error) {
            toast(friendly(error));
            return;
          }
          await refreshAfterSocialChange();
        });
      };
    });
    root.querySelectorAll('[data-acc-decline]').forEach(btn => {
      btn.onclick = event => {
        event.stopPropagation();
        withBusy(btn, async () => {
          try {
            await cloud().declineRequest(btn.getAttribute('data-acc-decline'));
          } catch (error) {
            toast(friendly(error));
            return;
          }
          await refreshAfterSocialChange();
        });
      };
    });
    root.querySelectorAll('[data-acc-cancel-request]').forEach(btn => {
      btn.onclick = event => {
        event.stopPropagation();
        withBusy(btn, async () => {
          try {
            await cloud().cancelRequest(btn.getAttribute('data-acc-cancel-request'));
          } catch (error) {
            toast(friendly(error));
            return;
          }
          await refreshAfterSocialChange();
        });
      };
    });
    root.querySelectorAll('[data-acc-remove-follower]').forEach(btn => {
      btn.onclick = event => {
        event.stopPropagation();
        withBusy(btn, async () => {
          try {
            await cloud().removeFollower(btn.getAttribute('data-acc-remove-follower'));
            toast(t('account-follower-removed'));
          } catch (error) {
            toast(friendly(error));
            return;
          }
          await refreshAfterSocialChange();
        });
      };
    });
  }

  function wireStatic(root) {
    wireDynamic(root);
    const api = cloud();

    root.querySelectorAll('[data-acc-edit]').forEach(btn => {
      btn.onclick = () => openEdit();
    });
    root.querySelectorAll('[data-acc-back]').forEach(btn => {
      btn.onclick = () => {
        S.viewingUid = null;
        S.viewingUsername = null;
        render();
      };
    });
    root.querySelectorAll('[data-acc-list]').forEach(btn => {
      if (btn.onclick) return;
      btn.onclick = () => openListModal(btn.getAttribute('data-acc-list'));
    });
    root.querySelectorAll('[data-acc-settings]').forEach(btn => {
      btn.onclick = () => {
        const settingsBtn = $('nav-settings-btn');
        if (settingsBtn) settingsBtn.click();
      };
    });
    root.querySelectorAll('[data-acc-login]').forEach(btn => {
      btn.onclick = () => api?.openAuth?.('auth-login');
    });
    root.querySelectorAll('[data-acc-register]').forEach(btn => {
      btn.onclick = () => api?.openAuth?.('auth-register');
    });
    root.querySelectorAll('[data-acc-logout]').forEach(btn => {
      btn.onclick = async () => {
        try {
          await api?.signOut?.();
        } catch {
          /* ignore */
        }
        S.viewingUid = null;
        api?.openAuth?.('auth-login');
        render();
      };
    });
    root.querySelectorAll('[data-acc-share]').forEach(btn => {
      btn.onclick = () => {
        const username = btn.getAttribute('data-acc-share');
        copyText(username ? `@${username}` : 'Votify', t('account-username-copied'));
      };
    });
    root.querySelectorAll('[data-acc-copy-username]').forEach(btn => {
      btn.onclick = event => {
        event.stopPropagation();
        copyText(`@${btn.getAttribute('data-acc-copy-username')}`, t('account-username-copied'));
      };
    });
    root.querySelectorAll('[data-acc-link]').forEach(btn => {
      btn.onclick = () => openExternal(btn.getAttribute('data-acc-link'));
    });
    root.querySelectorAll('[data-acc-follow-user]').forEach(btn => {
      btn.onclick = () => {
        withBusy(btn, async () => {
          try {
            const result = await api.followUid(S.viewingUid);
            toast(result.state === 'requested' ? t('account-request-sent') : t('account-followed'));
          } catch (error) {
            toast(friendly(error));
            return;
          }
          await render();
        });
      };
    });
    root.querySelectorAll('[data-acc-unfollow-user]').forEach(btn => {
      btn.onclick = () => {
        withBusy(btn, async () => {
          try {
            await api.unfollowUid(S.viewingUid);
            toast(t('account-unfollowed'));
          } catch (error) {
            toast(friendly(error));
            return;
          }
          await render();
        });
      };
    });
    root.querySelectorAll('[data-acc-playlist]').forEach(card => {
      card.onclick = event => {
        if (event.target.closest('[data-acc-play-playlist]')) return;
        openPlaylistCard(card);
      };
    });
    root.querySelectorAll('[data-acc-play-playlist]').forEach(btn => {
      btn.onclick = event => {
        event.stopPropagation();
        playPlaylist(btn.getAttribute('data-acc-play-playlist'));
      };
    });
  }

  function openPlaylistCard(card) {
    const name = card.getAttribute('data-acc-playlist');
    const isOwner = card.getAttribute('data-acc-owner') === '1';
    if (!name) return;
    if (!isOwner) {
      toast(t('account-friend-playlist'));
      return;
    }
    go('folders-screen', 'nav-folders-btn');
    try {
      if (typeof openPlaylist === 'function') openPlaylist(name);
    } catch {
      /* ignore */
    }
  }

  function playPlaylist(name) {
    if (!name) return;
    go('folders-screen', 'nav-folders-btn');
    try {
      if (typeof openPlaylist === 'function') openPlaylist(name);
    } catch {
      /* ignore */
    }
    window.setTimeout(() => {
      const playAll = $('lib-play-all-btn');
      if (playAll) playAll.click();
      else toast(t('account-opened-playlist'));
    }, 120);
  }

  // ---------- navigation ----------

  function openUser(usernameOrUid) {
    const api = cloud();
    const user = api?.getCurrentUser?.();
    const raw = String(usernameOrUid || '').trim();
    if (!raw) return;
    const clean = raw.replace(/^@+/, '');
    if (user && (clean === user.uid || `@${clean}` === `@${api.getProfile?.()?.username || ''}`)) {
      S.viewingUid = null;
      S.viewingUsername = null;
    } else {
      // Ambiguous input is resolved in order: uid first, then @username
      // (see renderForeign), so uids that look like handles still open.
      S.viewingUid = clean;
      S.viewingUsername =
        validators()?.validateUsername(clean).ok === true
          ? validators().normalizeUsername(clean)
          : null;
    }
    closeListModal();
    go('account-screen', 'nav-profile-btn');
    render();
  }

  // ---------- edit profile ----------

  function openEdit() {
    const api = cloud();
    const user = api?.getCurrentUser?.();
    if (!user) {
      api?.openAuth?.('auth-login');
      return;
    }
    go('account-edit-screen', 'nav-profile-btn');
    renderEdit();
  }

  function setEditAvatarPreview(value, name) {
    const preview = $('acc-edit-avatar-preview');
    if (!preview) return;
    if (value) {
      preview.innerHTML = `<img src="${esc(value)}" alt="${esc(name || '')}" />`;
    } else {
      preview.textContent =
        String(name || 'V')
          .trim()[0]
          ?.toUpperCase() || 'V';
    }
  }

  function updateBioCount() {
    const bio = $('acc-edit-bio');
    const count = $('acc-bio-count');
    if (bio && count) count.textContent = `${bio.value.length}/150`;
  }

  function setUsernameStatus(status, hintText, isError) {
    const badge = $('acc-username-status');
    const hint = $('acc-username-hint');
    if (badge) {
      badge.className = `acc-username-status ${status}`;
      const icons = { ok: 'check_circle', bad: 'error', checking: 'sync', '': '' };
      badge.innerHTML = icons[status] ? `<i class="material-icons">${icons[status]}</i>` : '';
    }
    if (hint) {
      hint.textContent = hintText || '';
      hint.classList.toggle('error', !!isError);
    }
  }

  async function checkEditUsername() {
    const token = ++S.usernameToken;
    const input = $('acc-edit-username');
    const api = cloud();
    const SV = validators();
    if (!input || !SV) return;
    const user = api?.getCurrentUser?.();
    const current = api?.getProfile?.()?.username || '';
    const raw = input.value;
    if (!raw.trim()) {
      setUsernameStatus('', current ? `votify.app/${current}` : t('account-username-empty'), false);
      return;
    }
    const { ok, value, error } = SV.validateUsername(raw);
    if (!ok) {
      setUsernameStatus('bad', SV.usernameErrorText(error, lang()), true);
      return;
    }
    if (value === current) {
      setUsernameStatus('ok', `votify.app/${value}`, false);
      return;
    }
    if (user?.isAnonymous) {
      setUsernameStatus('bad', t('account-username-guest'), true);
      return;
    }
    setUsernameStatus('checking', t('account-username-checking'), false);
    try {
      const result = await api.checkUsername(value);
      if (token !== S.usernameToken) return;
      if (result.available) {
        setUsernameStatus('ok', `votify.app/${value} · ${t('account-username-free')}`, false);
      } else {
        setUsernameStatus('bad', SV.usernameErrorText('taken', lang()), true);
      }
    } catch (error) {
      if (token !== S.usernameToken) return;
      setUsernameStatus('', friendly(error), true);
    }
  }

  function scheduleUsernameCheck() {
    if (S.usernameTimer) clearTimeout(S.usernameTimer);
    S.usernameTimer = setTimeout(checkEditUsername, 450);
  }

  function renderEdit() {
    const api = cloud();
    const user = api?.getCurrentUser?.();
    if (!user) {
      api?.openAuth?.('auth-login');
      go('account-screen', 'nav-profile-btn');
      return;
    }
    const profile = api.getProfile?.() || {};
    S.editAvatar = undefined;
    S.editPrivate = !!profile.isPrivate;

    const nameInput = $('acc-edit-name');
    const usernameInput = $('acc-edit-username');
    const bioInput = $('acc-edit-bio');
    if (nameInput) nameInput.value = profile.displayName || user.displayName || '';
    if (usernameInput) {
      usernameInput.value = profile.username || '';
      usernameInput.disabled = !!user.isAnonymous;
    }
    if (bioInput) bioInput.value = (profile.about || '').slice(0, 150);
    const links = profile.links || {};
    if ($('acc-edit-link-telegram')) $('acc-edit-link-telegram').value = links.telegram || '';
    if ($('acc-edit-link-soundcloud')) $('acc-edit-link-soundcloud').value = links.soundcloud || '';
    if ($('acc-edit-link-vk')) $('acc-edit-link-vk').value = links.vk || '';
    const toggle = $('acc-edit-privacy-toggle');
    if (toggle) toggle.setAttribute('aria-checked', S.editPrivate ? 'true' : 'false');

    setEditAvatarPreview(profile.avatar || '', profile.displayName || user.displayName);
    updateBioCount();
    setUsernameStatus(
      '',
      profile.username
        ? `votify.app/${profile.username}`
        : user.isAnonymous
          ? t('account-username-guest')
          : t('account-username-empty'),
      false
    );
  }

  async function saveEdit() {
    const api = cloud();
    const user = api?.getCurrentUser?.();
    if (!user || S.editBusy) return;
    const saveBtn = $('acc-edit-save');
    S.editBusy = true;
    if (saveBtn) saveBtn.disabled = true;
    try {
      const SV = validators();
      const payload = {
        displayName: $('acc-edit-name')?.value || '',
        about: $('acc-edit-bio')?.value || '',
        links: {
          telegram: $('acc-edit-link-telegram')?.value || '',
          soundcloud: $('acc-edit-link-soundcloud')?.value || '',
          vk: $('acc-edit-link-vk')?.value || '',
        },
        isPrivate: S.editPrivate,
      };
      if (!user.isAnonymous) {
        payload.username = $('acc-edit-username')?.value || '';
      }
      if (S.editAvatar !== undefined) payload.avatar = S.editAvatar;
      if (SV?.buildShowcase) payload.showcase = SV.buildShowcase(localPlaylists());
      await api.saveAccountProfile(payload);
      toast(t('account-saved'));
      S.viewingUid = null;
      S.viewingUsername = null;
      go('account-screen', 'nav-profile-btn');
      await render();
    } catch (error) {
      toast(friendly(error));
    } finally {
      S.editBusy = false;
      if (saveBtn) saveBtn.disabled = false;
    }
  }

  function wireEditScreen() {
    $('acc-edit-cancel')?.addEventListener('click', () => {
      S.viewingUid = null;
      go('account-screen', 'nav-profile-btn');
      render();
    });
    $('acc-edit-save')?.addEventListener('click', saveEdit);
    $('acc-edit-bio')?.addEventListener('input', updateBioCount);
    $('acc-edit-username')?.addEventListener('input', scheduleUsernameCheck);
    $('acc-edit-name-clear')?.addEventListener('click', () => {
      const input = $('acc-edit-name');
      if (input) {
        input.value = '';
        input.focus();
      }
    });
    const toggle = $('acc-edit-privacy-toggle');
    toggle?.addEventListener('click', () => {
      S.editPrivate = !S.editPrivate;
      toggle.setAttribute('aria-checked', S.editPrivate ? 'true' : 'false');
    });
    const fileInput = $('acc-edit-avatar-input');
    const pickAvatar = () => fileInput?.click();
    $('acc-edit-avatar-btn')?.addEventListener('click', pickAvatar);
    $('acc-edit-change-photo')?.addEventListener('click', pickAvatar);
    $('acc-edit-remove-photo')?.addEventListener('click', () => {
      S.editAvatar = '';
      const name = $('acc-edit-name')?.value || 'V';
      setEditAvatarPreview('', name);
    });
    fileInput?.addEventListener('change', async event => {
      const file = event.target.files?.[0];
      event.target.value = '';
      if (!file) return;
      try {
        if (!cloud()?.avatarFromFile) throw new Error(t('account-avatar-unsupported'));
        const dataUrl = await cloud().avatarFromFile(file);
        S.editAvatar = dataUrl;
        const name = $('acc-edit-name')?.value || 'V';
        setEditAvatarPreview(dataUrl, name);
      } catch (error) {
        toast(friendly(error));
      }
    });
    // Enter in name/username saves, Escape cancels.
    for (const id of ['acc-edit-name', 'acc-edit-username']) {
      $(id)?.addEventListener('keydown', event => {
        if (event.key === 'Enter') {
          event.preventDefault();
          saveEdit();
        }
        if (event.key === 'Escape') $('acc-edit-cancel')?.click();
      });
    }
  }

  // ---------- boot ----------

  function boot() {
    if (S.booted) return;
    S.booted = true;
    wireEditScreen();
    $('acc-list-close')?.addEventListener('click', closeListModal);
    $('acc-list-overlay')?.addEventListener('click', event => {
      if (event.target.id === 'acc-list-overlay') closeListModal();
    });
    document.addEventListener('keydown', event => {
      if (event.key === 'Escape' && $('acc-list-overlay')?.style.display !== 'none') {
        closeListModal();
      }
    });
    // Re-render the account page on sign in/out without a manual refresh.
    window.addEventListener('votify:auth-changed', () => {
      const screen = $('account-screen');
      if (screen && screen.style.display !== 'none') render();
    });
  }

  window.VotifyAccount = {
    render,
    renderEdit,
    openUser,
    openEdit,
    openListModal,
  };

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();
