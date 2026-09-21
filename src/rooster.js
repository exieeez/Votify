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
    { id: 'demo-01', title: 'Неоновый дождь', artist: 'Votify Demo' },
    { id: 'demo-02', title: 'Полночный экспресс', artist: 'Votify Demo' },
    { id: 'demo-03', title: 'Моя волна', artist: 'Votify Demo' },
    { id: 'demo-04', title: 'Хрустальное утро', artist: 'Votify Demo' },
    { id: 'demo-05', title: 'Городские огни', artist: 'Votify Demo' },
    { id: 'demo-06', title: 'Тёплый шум', artist: 'Votify Demo' },
    { id: 'demo-07', title: 'Пыль на виниле', artist: 'Votify Demo' },
    { id: 'demo-08', title: 'Последний троллейбус', artist: 'Votify Demo' },
  ];
  var DEMO_COVERS = {
    'demo-01': 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=500&h=500&fit=crop',
    'demo-02': 'https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?w=500&h=500&fit=crop',
    'demo-03': 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&h=500&fit=crop',
    'demo-04': 'https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=500&h=500&fit=crop',
    'demo-05': 'https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=500&h=500&fit=crop',
    'demo-06': 'https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=500&h=500&fit=crop',
    'demo-07': 'https://images.unsplash.com/photo-1511379938547-c1f69419868d?w=500&h=500&fit=crop',
    'demo-08': 'https://images.unsplash.com/photo-1446776811953-b23d57bd21aa?w=500&h=500&fit=crop',
  };

  function demoTrack(i) {
    var e = DEMO[i % DEMO.length];
    return {
      id: e.id,
      title: e.title,
      artist: e.artist,
      cover: DEMO_COVERS[e.id] || ('/demo/cover/' + e.id + '.svg'),
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
      gearBtn.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"></circle><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"></path></svg>';
      gearBtn.title = 'Настройки';
    }
    var bell = byId('tb-bell-btn');
    if (bell) bell.innerHTML = SVG_BELL;
    var existingBrowse = byId('tb-browse-btn');
    if (existingBrowse) existingBrowse.remove();

    if (homeBtn && navHome) homeBtn.addEventListener('click', () => navHome.click());
    if (gearBtn) {
      gearBtn.addEventListener('click', () => {
        if (typeof window.openSettings === 'function') window.openSettings();
        else if (navSettings) navSettings.click();
      });
    }

    if (tbSearch && navSearch && searchInput) {
      /* страница поиска не открывается сама: вводишь сверху, Enter — на страницу треков и сразу ищет */
      tbSearch.addEventListener('keydown', function (e) {
        if (e.key !== 'Enter') return;
        e.preventDefault();
        navSearch.click();
        searchInput.value = tbSearch.value;
        if (typeof window.doSearch === 'function') {
          window.doSearch();
        } else {
          searchInput.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
        }
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
    if (avatar) {
      avatar.addEventListener('click', function () {
        if (window.VotifyCloud && typeof window.VotifyCloud.openProfile === 'function') {
          window.VotifyCloud.openProfile();
        } else if (typeof window.openProfile === 'function') {
          window.openProfile();
        } else {
          var overlay = byId('profile-overlay');
          if (overlay) overlay.style.display = 'flex';
        }
      });
    }

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
    if (window.navigationHistory && typeof window.navigationHistory.updateButtons === 'function') {
      window.navigationHistory.updateButtons();
    }
  }
  function wireHistory() {
    // Delegated to primary navigationHistory in main.js
  }

  /* ---------- Сайдбар как в новом макете ---------- */
  function rebuildSidebar() {
    // Sidebar center buttons disabled as requested
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
  var SVG_MUSIC_NOTE = '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/></svg>';
  var DEFAULT_PLAYLIST_COVERS = [
    'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=300&h=300&fit=crop',
    'https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?w=300&h=300&fit=crop',
    'https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=300&h=300&fit=crop',
    'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=300&h=300&fit=crop',
    'https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=300&h=300&fit=crop'
  ];
  var lastPinsKey = null;
  function renderPins(force) {
    if (force) lastPinsKey = null;
    var wrap = byId('sidebar-pins');
    if (!wrap) return;
    var playlists = {};
    try {
      playlists = JSON.parse(localStorage.getItem('votify-playlists') || '{}') || {};
    } catch (e) {
      /* ignore */
    }
    if (window.playlists && typeof window.playlists === 'object') {
      playlists = Object.assign({}, window.playlists, playlists);
    }
    var keys = Object.keys(playlists);
    var sig = 'pl:' + keys.join('|');
    if (sig === lastPinsKey) return;
    lastPinsKey = sig;

    var html =
      '<div class="sidebar-pin pin-liked" data-pl="Избранное" title="Любимые треки">' +
      SVG_HEART +
      '</div>';
    
    var idx = 0;
    keys.forEach(function (key) {
      if (key === 'Избранное' || key === 'Любимые треки') return;
      var rawList = playlists[key] || [];
      var list = Array.isArray(rawList) ? rawList : (rawList.tracks || rawList.items || []);
      var cover = (list.length && list[0] && list[0].cover) ? list[0].cover : DEFAULT_PLAYLIST_COVERS[idx % DEFAULT_PLAYLIST_COVERS.length];
      idx++;
      var safeName = key.replace(/"/g, '&quot;');
      html += '<div class="sidebar-pin" data-pl="' + safeName + '" title="' + safeName + '"><img src="' + cover + '" alt="" /></div>';
    });

    html += '<div class="sidebar-pin pin-add-playlist" id="sidebar-add-pin-btn" title="Создать плейлист"><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg></div>';

    wrap.innerHTML = html;
    wrap.querySelectorAll('.sidebar-pin').forEach(function (pin) {
      if (pin.id === 'sidebar-add-pin-btn' || pin.classList.contains('pin-add-playlist')) {
        pin.addEventListener('click', function () {
          if (typeof window.createPlaylist === 'function') {
            window.createPlaylist();
          } else if (typeof createPlaylist === 'function') {
            createPlaylist();
          }
        });
        return;
      }
      pin.addEventListener('click', function () {
        var name = pin.getAttribute('data-pl');
        if (!name) return;
        var targetName = (name === 'Избранное' || name === 'Любимые треки') ? 'Избранное' : name;
        if (typeof window.openPlaylist === 'function') {
          window.openPlaylist(targetName);
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
      '<div class="rz-quick-card" data-demo="0"><div class="rz-quick-cover"><img src="https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=300&h=300&fit=crop" style="width:100%;height:100%;object-fit:cover;border-radius:4px;"/></div><span class="rz-quick-name">exieeez</span><span class="rz-quick-play">' +
      SVG_PLAY +
      '</span></div>';
    html +=
      '<div class="rz-quick-card" data-demo="2"><div class="rz-quick-cover"><img src="https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?w=300&h=300&fit=crop" style="width:100%;height:100%;object-fit:cover;border-radius:4px;"/></div><span class="rz-quick-name">sexyswag</span><span class="rz-quick-play">' +
      SVG_PLAY +
      '</span></div>';
    html += '</div>';

    html += '<section class="rz-section"><div class="rz-sec-head">';
    html +=
      '<div class="rz-sec-left"><div class="rz-sec-avatar" style="overflow:hidden;"><img src="https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=100&h=100&fit=crop" style="width:100%;height:100%;object-fit:cover;border-radius:50%;"/></div><div><div class="rz-sec-kicker">Похоже на:</div><div class="rz-sec-title">madk1d</div></div></div>';
    html += showAllBtn();
    html += '</div><div class="rz-grid">';
    html +=
      '<div class="rz-card" data-demo="0"><div class="rz-card-cover"><img src="https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=400&h=400&fit=crop" style="width:100%;height:100%;object-fit:cover;border-radius:8px;"/></div><div class="rz-card-title">тёмный принц, паранойя, greyrock …</div><div class="rz-card-sub">В эфире: madk1d, greyrock, trankvilizer и другие</div></div>';
    html +=
      '<div class="rz-card" data-demo="1"><div class="rz-card-cover"><img src="https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?w=400&h=400&fit=crop" style="width:100%;height:100%;object-fit:cover;border-radius:8px;"/></div><div class="rz-card-title">отвратительный король</div><div class="rz-card-sub">тёмный принц</div></div>';
    html +=
      '<div class="rz-card" data-demo="2"><div class="rz-card-cover"><img src="https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=400&h=400&fit=crop" style="width:100%;height:100%;object-fit:cover;border-radius:8px;"/></div><div class="rz-card-title">Найпопулярніші українські треки в…</div><div class="rz-card-sub">Оновлюється щоп’ятниці.</div></div>';
    html +=
      '<div class="rz-card" data-demo="3"><div class="rz-card-cover"><img src="https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=400&h=400&fit=crop" style="width:100%;height:100%;object-fit:cover;border-radius:8px;"/></div><div class="rz-card-title">50 найгарячіших пісень в Україні.…</div><div class="rz-card-sub">Головні хіти просто зараз.</div></div>';
    html +=
      '<div class="rz-card" data-demo="4"><div class="rz-card-cover"><img src="https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=400&h=400&fit=crop" style="width:100%;height:100%;object-fit:cover;border-radius:8px;"/></div><div class="rz-card-title">ПАПА</div><div class="rz-card-sub">тёмный пр… FORTUNA</div></div>';
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
      '<div class="rz-card" data-demo="5"><div class="rz-card-cover" style="border-radius:50%;overflow:hidden;"><img src="https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=400&h=400&fit=crop" style="width:100%;height:100%;object-fit:cover;"/></div><div class="rz-card-title">shadowraze</div><div class="rz-card-sub">Исполнитель</div></div>';
    html +=
      '<div class="rz-card" data-demo="6"><div class="rz-card-cover"><img src="https://images.unsplash.com/photo-1511379938547-c1f69419868d?w=400&h=400&fit=crop" style="width:100%;height:100%;object-fit:cover;border-radius:8px;"/></div><div class="rz-card-title">ASTRAL STEP</div><div class="rz-card-sub">shadowraze</div></div>';
    html += '</div></section>';
    return html;
  }
  function fillHome() {
    var home = byId('home-screen');
    if (!home) return;
    var existingNew = byId('rz-home-new');
    if (existingNew) existingNew.remove();
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
      var isMusic = chip.dataset.chip === 'music';
      home.classList.toggle('rz-music', isMusic);
    });
  }

  /* ---------- Правая панель «Активность друзей» ---------- */
  window.addFriend = function(targetUid, targetName) {
    if (window.VotifyCloud && typeof window.VotifyCloud.addFriend === 'function') {
      window.VotifyCloud.addFriend(targetUid, targetName).then(function() {
        renderFriendsList();
      });
    }
  };

  function renderFriendsList() {
    var container = byId('rz-friends-list');
    if (!container) return;
    var getFriendsFn = window.VotifyCloud && window.VotifyCloud.getFriends;
    if (typeof getFriendsFn !== 'function') {
      if (window.VotifyCloud && typeof window.VotifyCloud.whenReady === 'function') {
        window.VotifyCloud.whenReady().then(renderFriendsList);
        return;
      }
      container.innerHTML = '<div style="font-size:12px; color:#737373; padding:20px 12px; text-align:center;">Загрузка данных...</div>';
      return;
    }
    getFriendsFn().then(function(friends) {
      if (!friends || friends.length === 0) {
        container.innerHTML = '<div style="font-size:12px; color:#737373; padding:20px 12px; text-align:center;">У вас пока нет друзей.<br/>Нажмите «Найти друзей» ниже, чтобы добавить.</div>';
        return;
      }
      var html = '';
      var defaultAva = "data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='128' height='128' viewBox='0 0 128 128'><rect width='128' height='128' rx='64' fill='%23262626'/><path d='M64 28a20 20 0 1 0 0 40 20 20 0 0 0 0-40zm0 48c-22.1 0-40 13.4-40 30v4h80v-4c0-16.6-17.9-30-40-30z' fill='%23888888'/></svg>";
      friends.forEach(function (f) {
        var subText = (f.track && f.track !== 'В сети' && f.track !== 'Не в сети')
          ? ('♫ ' + f.track)
          : (f.about || f.handle || '');
        html +=
          '<div class="rz-friend" style="cursor:pointer; display:flex; align-items:center; gap:10px; padding:8px 10px; border-radius:10px; transition:background 0.2s;" onclick="if(window.openUserProfile){window.openUserProfile(\'' + f.uid + '\');}">' +
          '<div class="rz-friend-ava" style="width:38px; height:38px; min-width:38px; background:#262626; overflow:hidden; border-radius:50%; position:relative;">' +
          '<img src="' + (f.avatar || defaultAva) + '" style="width:100%;height:100%;object-fit:cover;" onerror="this.src=\'' + defaultAva + '\'" />' +
          '</div>' +
          '<div class="rz-friend-info" style="flex:1; min-width:0;">' +
          '<div class="rz-friend-name" style="font-weight:700; color:#fff; font-size:13px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">' + f.name + ' <span style="font-weight:400;font-size:11px;color:#a3a3a3;">' + (f.handle || '') + '</span></div>' +
          (subText ? '<div class="rz-friend-track" style="font-size:11px; color:#a3a3a3; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; margin-top:2px;">' + subText + '</div>' : '') +
          '</div></div>';
      });
      container.innerHTML = html;
    }).catch(function() {
      container.innerHTML = '<div style="font-size:12px; color:#737373; padding:20px 12px; text-align:center;">У вас пока нет друзей</div>';
    });
  }

  function buildAside() {
    var aside = byId('rz-right-aside');
    if (!aside) return;
    var html = '<div class="rz-friends-head"><span style="font-weight:800;font-size:16px;">Активность друзей</span>';
    html +=
      '<button class="rz-aside-x" id="rz-aside-close" title="Закрыть" aria-label="Закрыть">✕</button></div>';
    html += '<div class="rz-friends-list" id="rz-friends-list"></div>';
    html += '<button class="rz-find-friends" id="rz-find-friends" style="margin-top:12px;width:100%;background:#ffffff;color:#000;border:none;border-radius:9999px;padding:10px;font-weight:700;cursor:pointer;">Найти друзей</button>';
    aside.innerHTML = html;
    renderFriendsList();

    var close = byId('rz-aside-close');
    if (close)
      close.addEventListener('click', function () {
        setAsideVisible(false);
      });
    var find = byId('rz-find-friends');
    if (find)
      find.addEventListener('click', function () {
        var profile = byId('nav-profile-btn');
        if (profile) profile.click();
        var searchInput = byId('friend-search-input');
        if (searchInput) searchInput.focus();
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

  /* ---------- Клики по обложке и заголовку: открытие полноэкранного плеера ---------- */
  function wirePlayerLinks() {
    document.addEventListener('click', function (e) {
      var t = e.target.closest
        ? e.target.closest('#fi-title, #fi-artist, #fi-info, #player-track-title, .player-bar-info, #fi-cover-wrap, #fi-cover, #fi-cover-expand-btn')
        : null;
      if (t) {
        if (typeof window.openFullscreenPlayer === 'function') {
          window.openFullscreenPlayer();
        } else {
          var nav = byId('nav-player-btn');
          if (nav) nav.click();
        }
      }
    });
  }

  /* ---------- Друзья в профиле ---------- */
  function fillProfile() {
    var card = document.querySelector('#profile-overlay .profile-card');
    if (!card || byId('rz-profile-friends')) return;
    var box = document.createElement('div');
    box.className = 'rz-profile-friends';
    box.id = 'rz-profile-friends';
    box.innerHTML = '<h4>Друзья</h4><div id="rz-profile-friends-inner" style="font-size:12px; color:#737373; padding:8px 0;">У вас пока нет друзей</div>';
    card.appendChild(box);
    if (window.VotifyCloud && typeof window.VotifyCloud.getFriends === 'function') {
      window.VotifyCloud.getFriends().then(function(friends) {
        var inner = byId('rz-profile-friends-inner');
        if (!inner) return;
        if (!friends || friends.length === 0) {
          inner.innerHTML = 'У вас пока нет друзей';
        } else {
          inner.innerHTML = friends.map(function(f) {
            return '<div class="rz-friend" style="display:flex;align-items:center;gap:10px;padding:6px 0;"><img src="' + (f.avatar || 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=100&h=100&fit=crop') + '" style="width:28px;height:28px;border-radius:50%;object-fit:cover;" /><div style="font-weight:700;color:#fff;">' + f.name + '</div></div>';
          }).join('');
        }
      }).catch(function() {});
    }
  }

  function init() {
    wireTopbar();
    fillProfile();
    wirePlayerLinks();
    wireHistory();
    rebuildSidebar();
    buildAside();
    wireChips();
    wireAside();
    wireDemoClicks();
    window.renderPins = renderPins;
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
