/* Votify Color Picker — HeroUI-style component.
   ============================================================
   Триггер-свотч с HEX-подписью открывает попап: поле
   «насыщенность/яркость», вертикальный слайдер оттенка, HEX-ввод
   и быстрые пресеты. Попап крепится к <body> с фиксированным
   позиционированием и всегда вписывается в экран (переворот
   вверх/вниз + горизонтальное ограничение).

   Компонент — обёртка над существующим <input type="color"
   data-vcp>: он синхронизирует его value и шлёт события
   input/change, поэтому вся логика приложения (сохранение схем,
   фон и т.д.) продолжает работать без изменений.
   ============================================================ */
(function () {
  'use strict';

  if (window.VotifyColorPicker) return;

  // Упорядоченная палитра: спектр → белый → серые → чёрный.
  const PRESETS = [
    '#EF4444', '#F97316', '#F59E0B', '#EAB308', '#84CC16', '#22C55E',
    '#10B981', '#14B8A6', '#06B6D4', '#0EA5E9', '#3B82F6', '#6366F1',
    '#8B5CF6', '#A855F7', '#D946EF', '#EC4899', '#F43F5E', '#FFFFFF',
    '#E5E7EB', '#9CA3AF', '#6B7280', '#374151', '#111827', '#000000',
  ];

  const clamp = (v, min, max) => Math.min(max, Math.max(min, v));

  function hexToRgb(hex) {
    const m = /^#?([0-9a-f]{6})$/i.exec(String(hex).trim());
    if (!m) return null;
    const n = parseInt(m[1], 16);
    return { r: (n >> 16) & 255, g: (n >> 8) & 255, b: n & 255 };
  }

  function rgbToHsv(rgb) {
    const r = rgb.r / 255;
    const g = rgb.g / 255;
    const b = rgb.b / 255;
    const max = Math.max(r, g, b);
    const min = Math.min(r, g, b);
    const d = max - min;
    let h = 0;
    if (d !== 0) {
      if (max === r) h = ((g - b) / d) % 6;
      else if (max === g) h = (b - r) / d + 2;
      else h = (r - g) / d + 4;
      h *= 60;
      if (h < 0) h += 360;
    }
    return { h, s: max === 0 ? 0 : d / max, v: max };
  }

  function hsvToRgb(h, s, v) {
    const c = v * s;
    const hp = h / 60;
    const x = c * (1 - Math.abs((hp % 2) - 1));
    let rgb;
    if (hp < 1) rgb = [c, x, 0];
    else if (hp < 2) rgb = [x, c, 0];
    else if (hp < 3) rgb = [0, c, x];
    else if (hp < 4) rgb = [0, x, c];
    else if (hp < 5) rgb = [x, 0, c];
    else rgb = [c, 0, x];
    const m = v - c;
    return {
      r: Math.round((rgb[0] + m) * 255),
      g: Math.round((rgb[1] + m) * 255),
      b: Math.round((rgb[2] + m) * 255),
    };
  }

  const rgbToHex = rgb =>
    '#' + [rgb.r, rgb.g, rgb.b].map(n => n.toString(16).padStart(2, '0')).join('');

  const fmtHex = hex => String(hex || '').toUpperCase();

  const openPops = [];

  function closeAllPops(except) {
    openPops.slice().forEach(p => {
      if (p !== except) p.close();
    });
  }

  function mount(input) {
    if (!input || input.type !== 'color' || input._vcp) return;
    input._vcp = true;
    const labelMode = input.dataset.vcpLabel || '';

    const anchor = document.createElement('span');
    anchor.className = 'vcp-anchor';
    input.insertAdjacentElement('afterend', anchor);
    input.classList.add('vcp-src');

    anchor.innerHTML =
      '<button type="button" class="vcp-trigger" aria-haspopup="dialog" aria-expanded="false">' +
      '<span class="vcp-swatch"></span>' +
      '<span class="vcp-text"></span>' +
      '<span class="material-icons vcp-caret" aria-hidden="true">expand_more</span>' +
      '</button>';

    // Popup lives on <body>: fixed positioning, always fits the viewport.
    const pop = document.createElement('div');
    pop.className = 'vcp-pop';
    pop.hidden = true;
    pop.setAttribute('role', 'dialog');
    pop.setAttribute('aria-label', 'Выбор цвета');
    pop.innerHTML =
      '<div class="vcp-main">' +
      '<div class="vcp-area" title="Насыщенность / яркость"><div class="vcp-handle vcp-area-handle"></div></div>' +
      '<div class="vcp-rail" title="Оттенок"><div class="vcp-handle vcp-rail-handle"></div></div>' +
      '</div>' +
      '<div class="vcp-hex-row">' +
      '<span class="vcp-hex-label">HEX</span>' +
      '<div class="vcp-hex-field"><span aria-hidden="true">#</span>' +
      '<input class="vcp-hex-input" spellcheck="false" autocomplete="off" maxlength="6" aria-label="Шестнадцатеричный код цвета" />' +
      '</div>' +
      '</div>' +
      '<div class="vcp-presets" role="listbox" aria-label="Быстрые цвета"></div>';
    document.body.appendChild(pop);

    const trigger = anchor.querySelector('.vcp-trigger');
    const swatch = anchor.querySelector('.vcp-swatch');
    const text = anchor.querySelector('.vcp-text');
    const area = pop.querySelector('.vcp-area');
    const rail = pop.querySelector('.vcp-rail');
    const areaHandle = pop.querySelector('.vcp-area-handle');
    const railHandle = pop.querySelector('.vcp-rail-handle');
    const hexInput = pop.querySelector('.vcp-hex-input');
    const presetsEl = pop.querySelector('.vcp-presets');

    const state = { hex: '#000000', h: 0, s: 0, v: 1 };

    // Пресеты
    PRESETS.forEach(hex => {
      const b = document.createElement('button');
      b.type = 'button';
      b.className = 'vcp-preset';
      b.title = fmtHex(hex);
      b.style.background = hex;
      b.setAttribute('role', 'option');
      b.dataset.hex = hex;
      b.addEventListener('click', () => {
        setColor(hex);
        commit();
        close();
      });
      presetsEl.appendChild(b);
    });

    function setFromHex(hex) {
      const rgb = hexToRgb(hex);
      if (!rgb) return false;
      const hsv = rgbToHsv(rgb);
      state.hex = rgbToHex(rgb);
      state.h = hsv.h;
      state.s = hsv.s;
      state.v = hsv.v;
      return true;
    }

    function render() {
      const { h, s, v } = state;
      swatch.style.background = state.hex;
      text.textContent = labelMode || fmtHex(state.hex);
      area.style.background =
        'linear-gradient(to top, #000 0%, rgba(0,0,0,0) 100%),' +
        'linear-gradient(to right, #fff 0%, hsl(' + h + ',100%,50%) 100%)';
      areaHandle.style.left = s * 100 + '%';
      areaHandle.style.top = (1 - v) * 100 + '%';
      railHandle.style.top = (h / 360) * 100 + '%';
      hexInput.value = state.hex.slice(1).toUpperCase();
    }

    function syncInput(live) {
      input.value = state.hex;
      input.dispatchEvent(new Event(live ? 'input' : 'change', { bubbles: true }));
    }

    function setColor(hex, liveOnly) {
      if (!setFromHex(hex)) return;
      render();
      syncInput(liveOnly === true);
    }

    function commit() {
      input.value = state.hex;
      input.dispatchEvent(new Event('change', { bubbles: true }));
    }

    // --- Positioning: fixed, fits viewport, flips up when tight ---
    function reposition() {
      const r = trigger.getBoundingClientRect();
      const popW = Math.min(pop.offsetWidth || 248, window.innerWidth - 12);
      const popH = pop.offsetHeight || 300;
      const gap = 8;
      const margin = 10;

      let top = r.bottom + gap;
      if (top + popH > window.innerHeight - margin && r.top - gap - popH > margin) {
        top = r.top - gap - popH; // вверх, если снизу нет места
      } else if (top + popH > window.innerHeight - margin) {
        top = Math.max(margin, window.innerHeight - popH - margin);
      }
      const left = clamp(r.left, margin, Math.max(margin, window.innerWidth - popW - margin));
      pop.style.left = Math.round(left) + 'px';
      pop.style.top = Math.round(top) + 'px';
    }

    // --- S/V area + rail drag ---
    function attachDrag(el, horizontal, vertical, update) {
      let dragging = false;
      const move = e => {
        if (!dragging) return;
        const r = el.getBoundingClientRect();
        const x = clamp((e.clientX - r.left) / r.width, 0, 1);
        const y = clamp((e.clientY - r.top) / r.height, 0, 1);
        update(horizontal ? x : 0, vertical ? y : 0);
      };
      const end = e => {
        if (!dragging) return;
        dragging = false;
        el.removeEventListener('pointermove', move);
        try { el.releasePointerCapture(e.pointerId); } catch (err) { /* noop */ }
        move(e);
        commit();
      };
      el.addEventListener('pointerdown', e => {
        if (e.button !== 0) return;
        e.preventDefault();
        dragging = true;
        try { el.setPointerCapture(e.pointerId); } catch (err) { /* noop */ }
        el.addEventListener('pointermove', move);
        el.addEventListener('pointerup', end, { once: true });
        el.addEventListener('pointercancel', end, { once: true });
        move(e);
      });
    }

    attachDrag(area, true, true, (s, v) => {
      state.s = s;
      state.v = 1 - v;
      const rgb = hsvToRgb(state.h, state.s, state.v);
      state.hex = rgbToHex(rgb);
      render();
      syncInput(true);
    });

    attachDrag(rail, false, true, (s, y) => {
      state.h = y * 360;
      const rgb = hsvToRgb(state.h, state.s, state.v);
      state.hex = rgbToHex(rgb);
      render();
      syncInput(true);
    });

    // --- HEX field ---
    hexInput.addEventListener('input', () => {
      hexInput.value = hexInput.value.replace(/[^0-9a-fA-F]/g, '').slice(0, 6);
      if (hexInput.value.length === 6) setColor('#' + hexInput.value);
    });
    hexInput.addEventListener('keydown', e => {
      if (e.key === 'Enter') {
        e.preventDefault();
        if (hexInput.value.length === 6) {
          setColor('#' + hexInput.value);
          commit();
        }
        hexInput.blur();
      }
    });
    hexInput.addEventListener('blur', () => {
      if (hexInput.value.length === 6) {
        setColor('#' + hexInput.value);
        commit();
      } else {
        hexInput.value = state.hex.slice(1).toUpperCase();
        render();
      }
    });

    // --- Open / close ---
    const onDocDown = e => {
      if (!anchor.contains(e.target) && !pop.contains(e.target)) close();
    };
    const onKey = e => {
      if (e.key === 'Escape') {
        close();
        trigger.focus();
      }
    };
    const onScroll = () => reposition();
    const onResize = () => { if (!pop.hidden) reposition(); };

    function close() {
      if (pop.hidden) return;
      pop.hidden = true;
      trigger.classList.remove('open');
      trigger.setAttribute('aria-expanded', 'false');
      const i = openPops.indexOf(api);
      if (i !== -1) openPops.splice(i, 1);
      document.removeEventListener('pointerdown', onDocDown, true);
      document.removeEventListener('keydown', onKey);
      document.removeEventListener('scroll', onScroll, true);
      window.removeEventListener('resize', onResize);
    }

    function open() {
      closeAllPops(api);
      pop.hidden = false;
      render();
      reposition();
      // Второй проход: после фактической отрисовки размеры могут уточниться.
      requestAnimationFrame(() => { if (!pop.hidden) reposition(); });
      trigger.classList.add('open');
      trigger.setAttribute('aria-expanded', 'true');
      openPops.push(api);
      document.addEventListener('pointerdown', onDocDown, true);
      document.addEventListener('keydown', onKey);
      document.addEventListener('scroll', onScroll, true);
      window.addEventListener('resize', onResize);
    }

    trigger.addEventListener('click', () => {
      if (pop.hidden) open();
      else close();
    });

    // Начальное значение — из input (учитывает уже применённые настройки).
    setFromHex(input.value || '#000000');
    render();

    const api = {
      close,
      // Внешнее изменение (пресет/схема/загрузка настроек) — обновить UI без событий.
      setExternal(hex) {
        if (!setFromHex(hex)) return;
        render();
      },
    };
    input._vcpApi = api;
  }

  function refresh(input) {
    if (!input || !input._vcpApi) return;
    input._vcpApi.setExternal(input.value);
  }

  function init(scope) {
    (scope || document).querySelectorAll('input[type="color"][data-vcp]').forEach(mount);
  }

  window.VotifyColorPicker = { mount, refresh, init };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => init());
  } else {
    init();
  }
})();
