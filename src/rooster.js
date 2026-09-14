/* ============================================================
   VOTIFY — ROOSTER THEME · обвязка редизайна 1-в-1 с макетом
   Верхняя панель, сайдбар как в макете (медиатека + «плюс»,
   пины с обложками), главная с карточками-обложками и ссылками
   «Показать все», чипсы, правая панель «История прослушивания».
   ============================================================ */
(function () {
  'use strict';

  function byId(id) {
    return document.getElementById(id);
  }

  /* Демо-каталог (зеркало routes/demo.js) — обложки/аудио для пустых состояний */
  var DEMO = [
    { id: 'demo-01', title: 'Неоновый дождь', artist: 'Стеклянный Оркестр' },
    { id: 'demo-02', title: 'Полночный экспресс', artist: 'Стеклянный Оркестр' },
    { id: 'demo-03', title: 'Моя волна', artist: 'Votify Demo' },
    { id: 'demo-04', title: 'Хрустальное утро', artist: 'Votify Demo' },
    { id: 'demo-05', title: 'Городские огни', artist: 'Ночной Рейс' },
    { id: 'demo-06', title: 'Тёплый шум', artist: 'Ночной Рейс' },
    { id: 'demo-07', title: 'Пыль на виниле', artist: 'Кассетный Дом' },
    { id: 'demo-08', title: 'Последний троллейбус', artist: 'Кассетный Дом' },
  ];
  function demoTrack(i) {
    var e = DEMO[i % DEMO.length];
    return {
      id: e.id,
      title: e.title,
      artist: e.artist,
      cover: '/demo/cover/' + e.id + '.svg',
      url: '/api/audio?id=' + e.id,
      duration: 26,
      demo: true,
    };
  }

  /* ---------- Верхняя панель ---------- */
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
        if (typeof window.showToast === 'function') window.showToast('Нет новых уведомлений');
      });
    }

    var avatar = byId('tb-avatar-btn');
    var navProfile = byId('nav-profile-btn');
    if (avatar && navProfile) avatar.addEventListener('click', () => navProfile.click());

    var friends = byId('tb-friends-btn');
    if (friends) friends.addEventListener('click', () => setAsideVisible(!asideIsVisible()));
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
    var btn = navId ? byId(navId) : null;
    if (!btn) btn = byId('nav-home-btn');
    suppressHistory = true;
    btn.click();
    window.setTimeout(function () {
      suppressHistory = false;
    }, 300);
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
        navigateTo(prev);
        currentScreen = prev;
        updateNavButtons();
      });
    }
    if (fwd) {
      fwd.addEventListener('click', function () {
        if (!fwdStack.length) return;
        var next = fwdStack.pop();
        if (currentScreen) backStack.push(currentScreen);
        navigateTo(next);
        currentScreen = next;
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

  /* ---------- Сайдбар как в макете ---------- */
  function rebuildSidebar() {
    var top = document.querySelector('.sidebar-top');
    var bottom = document.querySelector('.sidebar-bottom');
    if (top && !byId('rz-lib-btn')) {
      var lib = document.createElement('button');
      lib.className = 'nav-btn';
      lib.id = 'rz-lib-btn';
      lib.title = 'Моя медиатека';
      lib.innerHTML = '<i class="material-icons">library_music</i>';
      lib.addEventListener('click', function () {
        var b = byId('nav-folders-btn');
        if (b) b.click();
      });
      var plus = document.createElement('button');
      plus.className = 'nav-btn rz-plus-btn';
      plus.id = 'rz-plus-btn';
      plus.title = 'Создать плейлист';
      plus.innerHTML = '<i class="material-icons">add</i>';
      plus.addEventListener('click', function () {
        var b = byId('nav-folders-btn');
        if (b) b.click();
        window.setTimeout(function () {
          var add = byId('lib-add-playlist-btn');
          if (add) add.click();
        }, 350);
      });
      top.appendChild(lib);
      top.appendChild(plus);
    }
    /* оставшуюся навигацию — вниз, чтобы верх сайдбара был как в макете */
    if (bottom) {
      ['nav-player-btn', 'nav-search-btn', 'nav-workshop-btn'].forEach(function (id) {
        var b = byId(id);
        if (b && b.parentElement !== bottom) bottom.insertBefore(b, bottom.firstChild);
      });
    }
  }

  /* ---------- Пины с обложками ---------- */
  var lastPinsKey = null;
  function renderPins() {
    var wrap = byId('sidebar-pins');
    if (!wrap) return;
    var playlists = {};
    try {
      playlists = JSON.parse(localStorage.getItem('votify-playlists') || '{}') || {};
    } catch (e) {
      /* игнор */
    }
    var keys = Object.keys(playlists).slice(0, 8);
    var sig = 'pl:' + keys.join('|');
    if (sig === lastPinsKey) return;
    lastPinsKey = sig;

    var html =
      '<div class="sidebar-pin pin-liked" title="Любимые треки"><i class="material-icons">music_note</i></div>';
    if (keys.length) {
      keys.forEach(function (key) {
        var list = playlists[key] || [];
        var cover = list.length && list[0] && list[0].cover ? list[0].cover : '';
        html += cover
          ? '<div class="sidebar-pin" title="' +
            key.replace(/"/g, '&quot;') +
            '"><img src="' +
            cover +
            '" alt="" /></div>'
          : '<div class="sidebar-pin pin-empty" title="' +
            key.replace(/"/g, '&quot;') +
            '"><i class="material-icons">queue_music</i></div>';
      });
    } else {
      /* нет плейлистов — пины демо-обложками, как в макете */
      DEMO.forEach(function (e, i) {
        html +=
          '<div class="sidebar-pin pin-demo" data-demo="' +
          i +
          '" title="' +
          e.title +
          '"><img src="/demo/cover/' +
          e.id +
          '.svg" alt="" /></div>';
      });
    }
    wrap.innerHTML = html;
    wrap.querySelectorAll('.sidebar-pin').forEach(function (pin) {
      pin.addEventListener('click', function () {
        if (pin.dataset.demo !== undefined) {
          if (typeof window.playTrack === 'function')
            window.playTrack(demoTrack(Number(pin.dataset.demo)));
        } else {
          var b = byId('nav-folders-btn');
          if (b) b.click();
        }
      });
    });
  }

  /* ---------- Главная: карточки как в макете ---------- */
  function tileHtml(t, i) {
    return (
      '<div class="rec-tile" data-demo="' +
      i +
      '">' +
      '<img class="rec-tile-cover" src="' +
      t.cover +
      '" alt="" />' +
      '<div class="rec-tile-title">' +
      t.title +
      '</div>' +
      '<div class="rec-tile-artist">' +
      t.artist +
      '</div>' +
      '<div class="rec-tile-play"><i class="material-icons">play_arrow</i></div>' +
      '</div>'
    );
  }
  function isEmpty(el) {
    if (!el) return true;
    if (el.querySelector('.rec-tile')) return false;
    if (el.querySelector('.track-item')) return false;
    return true;
  }
  var filledOnce = {};
  function fillHome() {
    var forYou = byId('for-you-results');
    if (forYou && isEmpty(forYou) && !filledOnce.forYou) {
      filledOnce.forYou = true;
      forYou.innerHTML = DEMO.map(function (_, i) {
        return tileHtml(demoTrack(i), i);
      }).join('');
    }
    var cont = byId('home-continue');
    if (cont && isEmpty(cont) && !filledOnce.cont) {
      filledOnce.cont = true;
      cont.classList.add('rz-demo-grid');
      cont.innerHTML = DEMO.map(function (_, i) {
        return tileHtml(demoTrack((i + 3) % DEMO.length), (i + 3) % DEMO.length);
      }).join('');
    }
    /* ссылки «Показать все» в заголовках секций, как в макете */
    document
      .querySelectorAll('.home-section-header, .home-section > .home-heading')
      .forEach(function (hdr) {
        var section = hdr.classList.contains('home-section') ? hdr.parentElement : hdr;
        if (section && !section.querySelector('.rz-show-all')) {
          var link = document.createElement('button');
          link.className = 'rz-show-all';
          link.type = 'button';
          link.textContent = 'Показать все';
          link.addEventListener('click', function () {
            if (typeof window.showToast === 'function')
              window.showToast('Все карточки уже на экране');
          });
          if (hdr.classList.contains('home-section-header')) hdr.appendChild(link);
          else hdr.parentElement.appendChild(link);
        }
      });
  }
  function wireDemoClicks() {
    document.addEventListener('click', function (e) {
      var tile = e.target.closest ? e.target.closest('.rec-tile[data-demo]') : null;
      if (tile && typeof window.playTrack === 'function') {
        window.playTrack(demoTrack(Number(tile.dataset.demo)));
      }
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
        home.classList.toggle('rz-music', chip.dataset.chip === 'music');
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
    } catch (e) {
      /* игнор */
    }
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
    } catch (e) {
      /* игнор */
    }
    setAsideVisible(stored !== 'closed');
  }

  function init() {
    wireTopbar();
    wireHistory();
    rebuildSidebar();
    wireChips();
    wireAside();
    wireDemoClicks();
    renderPins();
    fillHome();
    window.setInterval(renderPins, 2000);
    window.setInterval(fillHome, 1500);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
