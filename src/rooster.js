/* ============================================================
   VOTIFY — ROOSTER THEME v2 · обвязка редизайна 1-в-1
   с новым макетом (stitch_): сайдбар без нижних кнопок,
   иконка медиатеки «архив», текстовые пины, чипсы с «Подкасты»,
   главная: быстрые плитки, «Похоже на», миксы, «Недавние»,
   правая панель «Активность друзей», плеер-бар как в макете.
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

  /* ---------- SVG как в макете ---------- */
  var SVG_ARCHIVE =
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10"/></svg>';
  var SVG_PLUS =
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 4v16m8-8H4"/></svg>';
  var SVG_HEART =
    '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg>';
  var SVG_BOOKMARK =
    '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M17 3H7c-1.1 0-2 .9-2 2v16l7-3 7 3V5c0-1.1-.9-2-2-2z"/></svg>';
  var SVG_BELL =
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"/></svg>';
  var SVG_DOTS =
    '<svg viewBox="0 0 24 24" fill="currentColor"><circle cx="5" cy="12" r="2"/><circle cx="12" cy="12" r="2"/><circle cx="19" cy="12" r="2"/></svg>';
  var SVG_PLAY =
    '<svg viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"/></svg>';

  /* ---------- Верхняя панель ---------- */
  function wireTopbar() {
    var homeBtn = byId('tb-home-btn');
    var gearBtn = byId('tb-gear-btn');
    var tbSearch = byId('tb-search-input');
    var navHome = byId('nav-home-btn');
    var navSearch = byId('nav-search-btn');
    var navSettings = byId('nav-settings-btn');
    var searchInput = byId('search-input');

    /* иконки как в новом макете: «…» слева, outline-колокольчик, archive в поиске */
    if (gearBtn) {
      gearBtn.innerHTML = SVG_DOTS;
      gearBtn.title = 'Ещё';
    }
    var bell = byId('tb-bell-btn');
    if (bell) bell.innerHTML = SVG_BELL;
    var searchWrap = document.querySelector('.tb-search');
    if (searchWrap && !byId('tb-browse-btn')) {
      var browse = document.createElement('button');
      browse.className = 'tb-browse-btn';
      browse.id = 'tb-browse-btn';
      browse.title = 'Обзор';
      browse.setAttribute('aria-label', 'Обзор');
      browse.innerHTML = SVG_ARCHIVE;
      browse.addEventListener('click', function () {
        var b = byId('nav-folders-btn');
        if (b) b.click();
      });
      searchWrap.appendChild(browse);
    }

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

  /* ---------- Сайдбар как в новом макете ---------- */
  function rebuildSidebar() {
    var top = document.querySelector('.sidebar-top');
    if (top && !byId('rz-lib-btn')) {
      var lib = document.createElement('button');
      lib.className = 'rz-lib-btn';
      lib.id = 'rz-lib-btn';
      lib.title = 'Моя медиатека';
      lib.setAttribute('aria-label', 'Моя медиатека');
      lib.innerHTML = SVG_ARCHIVE;
      lib.addEventListener('click', function () {
        var b = byId('nav-folders-btn');
        if (b) b.click();
      });
      var plus = document.createElement('button');
      plus.className = 'rz-plus-btn';
      plus.id = 'rz-plus-btn';
      plus.title = 'Создать плейлист';
      plus.setAttribute('aria-label', 'Создать плейлист');
      plus.innerHTML = SVG_PLUS;
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
  }

  /* ---------- Пины: текстовые плитки как в макете ---------- */
  var PIN_STYLES = [
    'ps-emerald',
    'ps-dirt',
    'ps-bookmark',
    'ps-mono',
    'ps-circle',
    'ps-gray',
    'ps-cyan',
    'ps-light',
  ];
  function pinInitials(name) {
    var words = String(name || '')
      .trim()
      .split(/\s+/);
    var base = words[0] || 'PL';
    var s = base.length <= 5 ? base : base.slice(0, 4);
    return s.toUpperCase();
  }
  var lastPinsKey = null;
  function renderPins() {
    var wrap = byId('sidebar-pins');
    if (!wrap) return;
    var playlists = {};
    try {
      playlists = JSON.parse(localStorage.getItem('votify-playlists') || '{}') || {};
    } catch (e) {
      /* ignore */
    }
    var keys = Object.keys(playlists).slice(0, 8);
    var sig = 'pl:' + keys.join('|');
    if (sig === lastPinsKey) return;
    lastPinsKey = sig;

    var html = '<div class="sidebar-pin pin-liked" title="Любимые треки">' + SVG_HEART + '</div>';
    if (keys.length) {
      keys.forEach(function (key, i) {
        var style = PIN_STYLES[i % PIN_STYLES.length];
        html +=
          '<div class="sidebar-pin ' +
          style +
          '" title="' +
          key.replace(/"/g, '&quot;') +
          '"><span>' +
          pinInitials(key) +
          '</span></div>';
      });
    } else {
      /* нет плейлистов — демо-пины плитками, как в макете */
      var demoPins = [
        { t: 'CC', style: 'ps-emerald' },
        { t: 'DIRT', style: 'ps-dirt' },
        { t: SVG_BOOKMARK, style: 'ps-bookmark' },
        { t: '264', style: 'ps-mono' },
        { t: '', style: 'ps-circle' },
        { t: 'SCOOT', style: 'ps-gray' },
        { t: 'CITY', style: 'ps-cyan' },
        { t: 'MANGA', style: 'ps-light' },
      ];
      demoPins.forEach(function (p, i) {
        html +=
          '<div class="sidebar-pin ' +
          p.style +
          '" data-demo="' +
          i +
          '" title="' +
          DEMO[i % DEMO.length].title.replace(/"/g, '&quot;') +
          '">' +
          (p.t ? '<span>' + p.t + '</span>' : '') +
          '</div>';
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

  /* ---------- Главная как в новом макете ---------- */
  function showAllBtn() {
    return '<button class="rz-show-all" type="button">Показать все</button>';
  }
  function mixCard(m, i) {
    return (
      '<div class="rz-card" data-demo="' +
      i +
      '">' +
      '<div class="rz-card-cover rz-mix ' +
      m.cls +
      '"><span class="rz-mix-dot"></span><span class="rz-mix-ring"></span>' +
      '<span class="rz-mix-badge" style="background:' +
      m.badge +
      '">' +
      m.name +
      '</span></div>' +
      '<div class="rz-card-title">' +
      m.name +
      '</div>' +
      '<div class="rz-card-sub">' +
      m.sub +
      '</div>' +
      '</div>'
    );
  }
  function homeNewHtml() {
    var mixes = [
      {
        cls: 'mix-energy',
        badge: '#ff4db8',
        name: 'Energy Mix',
        sub: 'Energy music for you. Also try rap, dance,…',
      },
      {
        cls: 'mix-break',
        badge: '#f8d02e',
        name: 'Breakcore Mix',
        sub: 'Breakcore music for you. Also try drum a…',
      },
      {
        cls: 'mix-bpm',
        badge: '#ffc83b',
        name: '160 BPM Mix',
        sub: '160 BPM music for you. Also try pop,…',
      },
      {
        cls: 'mix-chill',
        badge: '#5c68ff',
        name: 'Chill Workout Mix',
        sub: 'Chill Workout music for you. Also try…',
      },
      {
        cls: 'mix-emo',
        badge: '#486581',
        name: 'Emo Rap',
        sub: 'Emo Rap music for you. Also try…',
      },
    ];
    var html = '';
    html += '<div class="rz-quick">';
    html +=
      '<div class="rz-quick-card" data-demo="0"><div class="rz-quick-cover qcc">CC</div><span class="rz-quick-name">exieeez</span><span class="rz-quick-play">' +
      SVG_PLAY +
      '</span></div>';
    html +=
      '<div class="rz-quick-card" data-demo="2"><div class="rz-quick-cover qmad">Mad</div><span class="rz-quick-name">sexyswag</span><span class="rz-quick-play">' +
      SVG_PLAY +
      '</span></div>';
    html += '</div>';

    html += '<section class="rz-section"><div class="rz-sec-head">';
    html +=
      '<div class="rz-sec-left"><div class="rz-sec-avatar">M1D</div><div><div class="rz-sec-kicker">Похоже на:</div><div class="rz-sec-title">madk1d</div></div></div>';
    html += showAllBtn();
    html += '</div><div class="rz-grid">';
    html +=
      '<div class="rz-card" data-demo="0"><div class="rz-card-cover rz-cv-radio"><span class="rz-cv-top">РАДИО</span><span class="rz-cv-name">madk1d</span></div><div class="rz-card-title">тёмный принц, паранойя, greyrock …</div><div class="rz-card-sub">В эфире: madk1d, greyrock, trankvilizer и другие</div></div>';
    html +=
      '<div class="rz-card" data-demo="1"><div class="rz-card-cover rz-cv-collage"><span>ПРИНЦ</span><span>MASK</span><span>DARK</span><span>KING</span></div><div class="rz-card-title">отвратительный король</div><div class="rz-card-sub">тёмный принц</div></div>';
    html +=
      '<div class="rz-card" data-demo="2"><div class="rz-card-cover rz-cv-ukr"><span class="rz-mix-dot"></span><span class="rz-cv-plate">Топ українських треків 2025</span></div><div class="rz-card-title">Найпопулярніші українські треки в…</div><div class="rz-card-sub">Оновлюється щоп’ятниці.</div></div>';
    html +=
      '<div class="rz-card" data-demo="3"><div class="rz-card-cover rz-cv-hot"><span class="rz-cv-vert">HOT HITS</span><span class="rz-cv-corner">УКРАЇНА</span><span class="rz-cv-artist">ARTIST</span></div><div class="rz-card-title">50 найгарячіших пісень в Україні.…</div><div class="rz-card-sub">Головні хіти просто зараз.</div></div>';
    html +=
      '<div class="rz-card" data-demo="4"><div class="rz-card-cover rz-cv-papa"><span class="rz-cv-papa-t">ПАПА</span><span class="rz-cv-papa-s">FORTUNA</span></div><div class="rz-card-title">ПАПА</div><div class="rz-card-sub">тёмный пр… FORTUNA</div></div>';
    html += '</div></section>';

    html += '<section class="rz-section"><div class="rz-sec-head">';
    html += '<div class="rz-sec-title rz-sec-plain">Soundtrack your Monday morning</div>';
    html += showAllBtn();
    html += '</div><div class="rz-grid">';
    html += mixes
      .map(function (m, i) {
        return mixCard(m, i + 2);
      })
      .join('');
    html += '</div></section>';

    html += '<section class="rz-section"><div class="rz-sec-head">';
    html += '<div class="rz-sec-title rz-sec-plain">Недавние</div>';
    html += showAllBtn();
    html += '</div><div class="rz-grid">';
    html +=
      '<div class="rz-card" data-demo="5"><div class="rz-card-cover rz-cv-round">ARTIST</div><div class="rz-card-title">shadowraze</div><div class="rz-card-sub">Исполнитель</div></div>';
    html +=
      '<div class="rz-card" data-demo="6"><div class="rz-card-cover rz-cv-album">ALBUM</div><div class="rz-card-title">ASTRAL STEP</div><div class="rz-card-sub">shadowraze</div></div>';
    html += '</div></section>';
    return html;
  }
  function fillHome() {
    var home = byId('home-screen');
    if (!home) return;
    if (!byId('rz-home-new')) {
      var chips = byId('rz-home-chips');
      var box = document.createElement('div');
      box.id = 'rz-home-new';
      box.innerHTML = homeNewHtml();
      if (chips && chips.nextSibling) home.insertBefore(box, chips.nextSibling);
      else home.appendChild(box);
    }
    /* третий чипс «Подкасты» */
    var chipsWrap = byId('rz-home-chips');
    if (chipsWrap && !byId('rz-chip-podcasts')) {
      var pod = document.createElement('button');
      pod.className = 'rz-chip';
      pod.id = 'rz-chip-podcasts';
      pod.dataset.chip = 'podcasts';
      pod.textContent = 'Подкасты';
      chipsWrap.appendChild(pod);
    }
  }
  function wireDemoClicks() {
    document.addEventListener('click', function (e) {
      var tile = e.target.closest ? e.target.closest('[data-demo]') : null;
      if (tile && typeof window.playTrack === 'function') {
        window.playTrack(demoTrack(Number(tile.dataset.demo)));
      }
      var showAll = e.target.closest ? e.target.closest('.rz-show-all') : null;
      if (showAll && typeof window.showToast === 'function') {
        window.showToast('Все карточки уже на экране');
      }
    });
  }

  /* ---------- Чипсы ---------- */
  function wireChips() {
    var chipsWrap = byId('rz-home-chips');
    if (!chipsWrap) return;
    chipsWrap.addEventListener('click', function (e) {
      var chip = e.target.closest ? e.target.closest('.rz-chip') : null;
      if (!chip) return;
      chipsWrap.querySelectorAll('.rz-chip').forEach(function (c) {
        c.classList.remove('active');
      });
      chip.classList.add('active');
      var home = byId('home-screen');
      if (!home) return;
      home.classList.toggle('rz-music', chip.dataset.chip === 'music');
      home.classList.toggle('rz-podcasts', chip.dataset.chip === 'podcasts');
    });
  }

  /* ---------- Правая панель «Активность друзей» ---------- */
  var FRIENDS_DEMO = [
    {
      ini: 'NK',
      color: '#7b3fe4',
      name: 'Nikita',
      track: 'FEIN (feat. Playboi Carti)',
      meta: 'Travis Scott • UTOPIA',
      online: true,
    },
    {
      ini: 'AL',
      color: '#e07b12',
      name: 'Alex',
      track: 'Starlight',
      meta: 'Muse • Black Holes',
      online: true,
    },
    {
      ini: 'VL',
      color: '#0e6b4a',
      name: 'Vladislav',
      track: 'ASTRAL STEP',
      meta: '3 ч. назад',
      online: false,
    },
  ];
  function buildAside() {
    var aside = byId('rz-right-aside');
    if (!aside || byId('rz-friends-list')) return;
    var html = '<div class="rz-friends-head"><span>Активность друзей</span>';
    html +=
      '<button class="rz-aside-x" id="rz-aside-close" title="Закрыть" aria-label="Закрыть">✕</button></div>';
    html += '<div class="rz-friends-list" id="rz-friends-list">';
    FRIENDS_DEMO.forEach(function (f) {
      html +=
        '<div class="rz-friend"><div class="rz-friend-ava" style="background:' +
        f.color +
        '">' +
        f.ini +
        (f.online ? '<span class="rz-friend-dot"></span>' : '') +
        '</div><div class="rz-friend-info"><div class="rz-friend-name">' +
        f.name +
        '</div><div class="rz-friend-track">' +
        f.track +
        '</div><div class="rz-friend-meta">♫ ' +
        f.meta +
        '</div></div></div>';
    });
    html += '</div>';
    html += '<button class="rz-find-friends" id="rz-find-friends">Найти друзей</button>';
    aside.innerHTML = html;
    var close = byId('rz-aside-close');
    if (close)
      close.addEventListener('click', function () {
        setAsideVisible(false);
      });
    var find = byId('rz-find-friends');
    if (find)
      find.addEventListener('click', function () {
        if (typeof window.showToast === 'function')
          window.showToast('Друзья появятся, когда вы войдёте в аккаунт');
      });
  }
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
      /* ignore */
    }
  }
  function wireAside() {
    var stored = null;
    try {
      stored = localStorage.getItem('rooster-aside');
    } catch (e) {
      /* ignore */
    }
    setAsideVisible(stored === 'open');
  }

  function init() {
    wireTopbar();
    wireHistory();
    rebuildSidebar();
    buildAside();
    wireChips();
    wireAside();
    wireDemoClicks();
    renderPins();
    fillHome();
    window.setInterval(renderPins, 2000);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
