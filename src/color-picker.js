/* Votify Color Picker — HeroUI-style component.
   ============================================================
   Триггер-свотч с HEX-подписью открывает попап: поле
   «насыщенность/яркость», вертикальный слайдер оттенка, HEX-ввод
   и быстрые пресеты. Компонент — обёртка над существующим
   <input type="color" data-vcp>: он синхронизирует его value и
   шлёт события input/change, поэтому вся логика приложения
   (сохранение схем, фон и т.д.) продолжает работать без изменений.
   ============================================================ */
(function () {
  'use strict';

  if (window.VotifyColorPicker) return;

  const PRESETS = [
    '#FFFFFF', '#F0F0F0', '#C9C9C9', '#7A7A7A', '#4A4A4A', '#1F1F1F',
    '#0A0A0A', '#000000', '#1DB954', '#DC263F', '#38BDF8', '#F59E0B',
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
      '</button>' +
      '<div class="vcp-pop" hidden role="dialog" aria-label="Выбор цвета">' +
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
      '<div class="vcp-presets" role="listbox" aria-label="Быстрые цвета"></div>' +
      '</div>';

    const trigger = anchor.querySelector('.vcp-trigger');
    const pop = anchor.querySelector('.vcp-pop');
    const swatch = anchor.querySelector('.vcp-swatch');
    const text = anchor.querySelector('.vcp-text');
    const area = anchor.querySelector('.vcp-area');
    const rail = anchor.querySelector('.vcp-rail');
    const areaHandle = anchor.querySelector('.vcp-area-handle');
    const railHandle = anchor.querySelector('.vcp-rail-handle');
    const hexInput = anchor.querySelector('.vcp-hex-input');
    const presetsEl = anchor.querySelector('.vcp-presets');

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

    // --- S/V area ---
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
    function position() {
      pop.hidden = false;
      const ar = anchor.getBoundingClientRect();
      const below = window.innerHeight - ar.bottom - 16;
      const above = ar.top - 16;
      const needUp = pop.offsetHeight + 12 > below && above > below;
      pop.classList.toggle('vcp-up', needUp);
    }

    function close() {
      if (pop.hidden) return;
      pop.hidden = true;
      trigger.classList.remove('open');
      trigger.setAttribute('aria-expanded', 'false');
      document.removeEventListener('pointerdown', onDocDown);
      document.removeEventListener('keydown', onKey);
    }

    function open() {
      pop.hidden = false;
      position();
      render();
      trigger.classList.add('open');
      trigger.setAttribute('aria-expanded', 'true');
      document.addEventListener('pointerdown', onDocDown, true);
      document.addEventListener('keydown', onKey);
    }

    function onDocDown(e) {
      if (!anchor.contains(e.target)) close();
    }

    function onKey(e) {
      if (e.key === 'Escape') {
        close();
        trigger.focus();
      }
    }

    trigger.addEventListener('click', () => {
      if (pop.hidden) open();
      else close();
    });

    // Начальное значение — из input (учитывает уже применённые настройки).
    setFromHex(input.value || '#000000');
    render();
  }

  function refresh(input) {
    if (!input || !input._vcp) return;
    const anchor = input.nextElementSibling;
    if (!anchor || !anchor.classList.contains('vcp-anchor')) return;
    // Перечитать значение и перерисовать триггер без события.
    const rgb = hexToRgb(input.value);
    if (!rgb) return;
    const hsv = rgbToHsv(rgb);
    const swatch = anchor.querySelector('.vcp-swatch');
    const text = anchor.querySelector('.vcp-text');
    const labelMode = input.dataset.vcpLabel || '';
    if (swatch) swatch.style.background = input.value;
    if (text) text.textContent = labelMode || fmtHex(input.value);
    anchor.querySelector('.vcp-area-handle').style.left = hsv.s * 100 + '%';
    anchor.querySelector('.vcp-area-handle').style.top = (1 - hsv.v) * 100 + '%';
    anchor.querySelector('.vcp-rail-handle').style.top = (hsv.h / 360) * 100 + '%';
    const hexField = anchor.querySelector('.vcp-hex-input');
    if (hexField) hexField.value = input.value.replace('#', '').toUpperCase();
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
