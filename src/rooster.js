/* ============================================================
   VOTIFY — ROOSTER THEME · обвязка редизайна
   Верхняя панель (домой, назад/вперёд, поиск-пилюля, шестерёнка,
   колокольчик, друзья, аватар), пины плейлистов в сайдбаре,
   чипсы «Все/Музыка» на главной и правая панель
   «История прослушивания» — всё как в макете Rooster.
   ============================================================ */
(function () {
  'use strict';

  function byId(id) {
    return document.getElementById(id);
  }

  /* ---------- Верхняя панель: делегирование ---------- */
  function wireTopbar() {
    var homeBtn = byId('tb-home-btn');
    var gearBtn = byId('tb-gear-btn');
    var tbSearch = byId('tb-search-input');
    var navHome = byId('nav-home-btn');
    var navSearch = byId('nav-search-btn');
    var navSettings = byId('nav-settings-btn');
    var searchInput = byId('search-input');

    if (homeBtn && navHome) homeBtn.addEventListener('click', () => navHome.click());
    if (gearBtn && navSettings) gearBtn.addEventListener('click', () => navSettings.click());

    if (tbSearch && navSearch && searchInput) {
      tbSearch.addEventListener('focus', function () {
        navSearch.click();
        window.setTimeout(function () {
          searchInput.focus();
          if (tbSearch.value && !searchInput.value) {
            searchInput.value = tbSearch.value;
            searchInput.dispatchEvent(new Event('input', { bubbles: true }));
          }
        }, 0);
      });
      tbSearch.addEventListener('input', function () {
        navSearch.click();
        searchInput.value = tbSearch.value;
        searchInput.dispatchEvent(new Event('input', { bubbles: true }));
        searchInput.focus();
      });
      searchInput.addEventListener('input', function () {
        if (document.activeElement !== tbSearch && tbSearch.value !== searchInput.value) {
          tbSearch.value = searchInput.value;
        }
      });
    }

    var bell = byId('tb-bell-btn');
    if (bell) {
      bell.addEventListener('click', function () {
        if (typeof window.showToast === 'function') {
          window.showToast('Нет новых уведомлений');
        }
      });
    }

    var avatar = byId('tb-avatar-btn');
    var navProfile = byId('nav-profile-btn');
    if (avatar && navProfile) avatar.addEventListener('click', () => navProfile.click());

    var friends = byId('tb-friends-btn');
    if (friends) {
      friends.addEventListener('click', function () {
        setAsideVisible(!asideIsVisible());
      });
    }
  }

  /* ---------- Назад / вперёд ---------- */
  var SCREEN_NAV = {
    'home-screen': 'nav-home-btn',
    'player-screen': 'nav-player-btn',
    'search-screen': 'nav-search-btn',
    'folders-screen': 'nav-folders-btn',
    'workshop-screen': 'nav-workshop-btn',
  };
  var backStack = [];
  var fwdStack = [];
  var currentScreen = null;
  var suppressHistory = false;

  function visibleScreenId() {
    var screens = document.querySelectorAll('.screen');
    for (var i = 0; i < screens.length; i++) {
      var el = screens[i];
      if (el.style.display !== 'none' && !el.classList.contains('hidden')) return el.id;
    }
    return null;
  }

  function navigateTo(id) {
    var navId = SCREEN_NAV[id];
    if (navId) {
      var btn = byId(navId);
      if (btn) {
        suppressHistory = true;
        btn.click();
        window.setTimeout(function () {
          suppressHistory = false;
        }, 300);
        return;
      }
    }
    if (id === 'artist-screen') {
      // попасть обратно в артиста можно только кликом по артисту —
      // история просто открывает главную
      var home = byId('nav-home-btn');
      if (home) home.click();
      return;
    }
    if (id === 'album-screen') {
      var home2 = byId('nav-home-btn');
      if (home2) home2.click();
      return;
    }
    var fallback = byId('nav-home-btn');
    if (fallback) fallback.click();
  }

  function updateNavButtons() {
    var back = byId('tb-back-btn');
    var fwd = byId('tb-fwd-btn');
    if (back) back.disabled = backStack.length === 0;
    if (fwd) fwd.disabled = fwdStack.length === 0;
  }

  function wireHistory() {
    var back = byId('tb-back-btn');
    var fwd = byId('tb-fwd-btn');
    if (back) {
      back.addEventListener('click', function () {
        if (!backStack.length) return;
        var prev = backStack.pop();
        if (currentScreen) fwdStack.push(currentScreen);
        suppressHistory = true;
        navigateTo(prev);
        currentScreen = prev;
        window.setTimeout(function () {
          suppressHistory = false;
        }, 300);
        updateNavButtons();
      });
    }
    if (fwd) {
      fwd.addEventListener('click', function () {
        if (!fwdStack.length) return;
        var next = fwdStack.pop();
        if (currentScreen) backStack.push(currentScreen);
        suppressHistory = true;
        navigateTo(next);
        currentScreen = next;
        window.setTimeout(function () {
          suppressHistory = false;
        }, 300);
        updateNavButtons();
      });
    }
    window.setInterval(function () {
      var now = visibleScreenId();
      if (now === currentScreen) return;
      if (!suppressHistory && currentScreen && now) {
        backStack.push(currentScreen);
        fwdStack.length = 0;
      }
      currentScreen = now;
      updateNavButtons();
    }, 400);
    currentScreen = visibleScreenId();
    updateNavButtons();
  }

  /* ---------- Пины плейлистов в сайдбаре ---------- */
  var lastPinsKey = null;
  function renderPins() {
    var wrap = byId('sidebar-pins');
    if (!wrap) return;
    var playlists = {};
    try {
      playlists = JSON.parse(localStorage.getItem('votify-playlists') || '{}') || {};
    } catch (e) {
      playlists = {};
    }
    var keys = Object.keys(playlists).slice(0, 8);
    var sig = keys.join('|');
    if (sig === lastPinsKey) return;
    lastPinsKey = sig;

    var html =
      '<div class="sidebar-pin pin-liked" data-pin="liked" title="Любимые треки">' +
      '<i class="material-icons">music_note</i></div>';
    keys.forEach(function (key) {
      var list = playlists[key] || [];
      var cover = list.length && list[0] && list[0].cover ? list[0].cover : '';
      html += cover
        ? '<div class="sidebar-pin" data-pin="pl" title="' +
          key.replace(/"/g, '&quot;') +
          '"><img src="' +
          cover +
          '" alt="" /></div>'
        : '<div class="sidebar-pin pin-empty" data-pin="pl" title="' +
          key.replace(/"/g, '&quot;') +
          '"><i class="material-icons">queue_music</i></div>';
    });
    wrap.innerHTML = html;
    wrap.querySelectorAll('.sidebar-pin').forEach(function (pin) {
      pin.addEventListener('click', function () {
        var btn = byId('nav-folders-btn');
        if (btn) btn.click();
      });
    });
  }

  /* ---------- Чипсы «Все / Музыка» ---------- */
  function wireChips() {
    var chips = document.querySelectorAll('#rz-home-chips .rz-chip');
    chips.forEach(function (chip) {
      chip.addEventListener('click', function () {
        chips.forEach(function (c) {
          c.classList.remove('active');
        });
        chip.classList.add('active');
        var home = byId('home-screen');
        if (!home) return;
        if (chip.dataset.chip === 'music') home.classList.add('rz-music');
        else home.classList.remove('rz-music');
      });
    });
  }

  /* ---------- Правая панель «История прослушивания» ---------- */
  function asideIsVisible() {
    var aside = byId('rz-right-aside');
    return !!aside && !aside.classList.contains('closed');
  }
  function setAsideVisible(show) {
    var aside = byId('rz-right-aside');
    var main = document.querySelector('.main-content');
    if (!aside) return;
    aside.classList.toggle('closed', !show);
    if (main) main.classList.toggle('rz-right-open', show);
    try {
      localStorage.setItem('rooster-aside', show ? 'open' : 'closed');
    } catch (e) {}
  }
  function wireAside() {
    var close = byId('rz-aside-close');
    if (close) close.addEventListener('click', () => setAsideVisible(false));
    var settings = byId('rz-aside-settings');
    var navSettings = byId('nav-settings-btn');
    if (settings && navSettings) settings.addEventListener('click', () => navSettings.click());
    var stored = null;
    try {
      stored = localStorage.getItem('rooster-aside');
    } catch (e) {}
    setAsideVisible(stored !== 'closed');
  }

  function init() {
    wireTopbar();
    wireHistory();
    wireChips();
    wireAside();
    renderPins();
    window.setInterval(renderPins, 2000);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
