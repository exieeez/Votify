/* ============================================================
   VOTIFY — ROOSTER THEME · обвязка новой верхней панели
   Круглая кнопка «домой», поиск-пилюля и шестерёнка настроек
   в тайтлбаре делегируют действия существующим элементам UI.
   ============================================================ */
(function () {
  'use strict';

  function byId(id) {
    return document.getElementById(id);
  }

  function init() {
    var homeBtn = byId('tb-home-btn');
    var gearBtn = byId('tb-gear-btn');
    var tbSearch = byId('tb-search-input');
    var navHome = byId('nav-home-btn');
    var navSearch = byId('nav-search-btn');
    var navSettings = byId('nav-settings-btn');
    var searchInput = byId('search-input');

    if (homeBtn && navHome) {
      homeBtn.addEventListener('click', function () {
        navHome.click();
      });
    }

    if (gearBtn && navSettings) {
      gearBtn.addEventListener('click', function () {
        navSettings.click();
      });
    }

    if (tbSearch && navSearch && searchInput) {
      // Фокус в поиске тайтлбара открывает экран поиска
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

      // Печатаем в пилюле — запрос уходит в настоящий поиск
      tbSearch.addEventListener('input', function () {
        navSearch.click();
        searchInput.value = tbSearch.value;
        searchInput.dispatchEvent(new Event('input', { bubbles: true }));
        searchInput.focus();
      });

      // Если основной поиск очистили/изменили извне — синхронизируем пилюлю
      searchInput.addEventListener('input', function () {
        if (document.activeElement !== tbSearch && tbSearch.value !== searchInput.value) {
          tbSearch.value = searchInput.value;
        }
      });
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
