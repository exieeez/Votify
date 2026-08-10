/**
 * dom.js — DOM utilities
 * Tiny, zero-deps helpers
 */

// Selectors
export const $ = (sel, ctx = document) => ctx.querySelector(sel);
export const $$ = (sel, ctx = document) => Array.from(ctx.querySelectorAll(sel));

// Create element
export function create(tag, attrs = {}, children = []) {
  const el = document.createElement(tag);
  Object.entries(attrs).forEach(([key, val]) => {
    if (key === 'class') el.className = val;
    else if (key === 'style' && typeof val === 'object') Object.assign(el.style, val);
    else if (key.startsWith('on') && typeof val === 'function')
      el.addEventListener(key.slice(2).toLowerCase(), val);
    else if (key === 'dataset') Object.assign(el.dataset, val);
    else el.setAttribute(key, val);
  });
  children.forEach(child => {
    if (typeof child === 'string') el.appendChild(document.createTextNode(child));
    else if (child instanceof Node) el.appendChild(child);
    else if (Array.isArray(child)) child.forEach(c => el.append(c));
  });
  return el;
}

// HTML template
export function html(strings, ...values) {
  const template = document.createElement('template');
  template.innerHTML = strings.reduce((acc, str, i) => acc + str + (values[i] ?? ''), '').trim();
  return template.content.firstElementChild;
}

// Event delegation
export function on(parent, selector, event, handler) {
  parent.addEventListener(event, e => {
    const target = e.target.closest(selector);
    if (target && parent.contains(target)) handler.call(target, e);
  });
}

// Debounce
export function debounce(fn, ms = 250) {
  let timer;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), ms);
  };
}

// Throttle
export function throttle(fn, ms = 100) {
  let last = 0;
  return (...args) => {
    const now = Date.now();
    if (now - last >= ms) {
      last = now;
      fn(...args);
    }
  };
}

// Class toggles
export function toggleClass(el, className, force) {
  return el.classList.toggle(className, force);
}

export function addClass(el, ...classes) {
  el.classList.add(...classes);
}

export function removeClass(el, ...classes) {
  el.classList.remove(...classes);
}

// Attributes
export function attr(el, name, value) {
  if (value === undefined) return el.getAttribute(name);
  if (value === null) el.removeAttribute(name);
  else el.setAttribute(name, value);
}

// Data attributes
export function data(el, key, value) {
  if (value === undefined) return el.dataset[key];
  el.dataset[key] = value;
}

// Style
export function style(el, styles) {
  Object.assign(el.style, styles);
}

// Dimensions
export function rect(el) {
  return el.getBoundingClientRect();
}
export function offset(el) {
  const r = el.getBoundingClientRect();
  return { top: r.top + window.scrollY, left: r.left + window.scrollX };
}

// Scroll
export function scrollTo(el, options = {}) {
  el.scrollIntoView({ behavior: 'smooth', block: 'nearest', ...options });
}

// Visibility
export function isVisible(el) {
  const r = el.getBoundingClientRect();
  return (
    r.width > 0 &&
    r.height > 0 &&
    r.top < window.innerHeight &&
    r.bottom > 0 &&
    r.left < window.innerWidth &&
    r.right > 0
  );
}

// Focus management
export function trapFocus(el) {
  const focusable = el.querySelectorAll(
    'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
  );
  const first = focusable[0];
  const last = focusable[focusable.length - 1];

  function handleTab(e) {
    if (e.key !== 'Tab') return;
    if (e.shiftKey) {
      if (document.activeElement === first) {
        e.preventDefault();
        last.focus();
      }
    } else {
      if (document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    }
  }

  el.addEventListener('keydown', handleTab);
  return () => el.removeEventListener('keydown', handleTab);
}

// Animation frame
export function raf(fn) {
  return requestAnimationFrame(fn);
}
export function caf(id) {
  cancelAnimationFrame(id);
}

// Ready
export function ready(fn) {
  if (document.readyState !== 'loading') fn();
  else document.addEventListener('DOMContentLoaded', fn);
}

export default {
  $,
  $$,
  create,
  html,
  on,
  debounce,
  throttle,
  toggleClass,
  addClass,
  removeClass,
  attr,
  data,
  style,
  rect,
  offset,
  scrollTo,
  isVisible,
  trapFocus,
  raf,
  caf,
  ready,
};
