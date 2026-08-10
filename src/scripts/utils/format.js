/**
 * format.js — Formatting utilities
 * Time, numbers, strings
 */

// Time formatting
export function formatTime(seconds) {
  if (!seconds || isNaN(seconds) || seconds < 0) return '0:00';
  const mins = Math.floor(seconds / 60);
  const secs = Math.floor(seconds % 60);
  return `${mins}:${secs.toString().padStart(2, '0')}`;
}

export function formatDuration(ms) {
  if (!ms || ms < 0) return '0:00';
  const totalSecs = Math.floor(ms / 1000);
  const hours = Math.floor(totalSecs / 3600);
  const mins = Math.floor((totalSecs % 3600) / 60);
  const secs = totalSecs % 60;
  if (hours > 0) return `${hours}:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  return `${mins}:${secs.toString().padStart(2, '0')}`;
}

export function formatRelativeTime(ms) {
  const diff = Date.now() - ms;
  const secs = Math.floor(diff / 1000);
  const mins = Math.floor(secs / 60);
  const hours = Math.floor(mins / 60);
  const days = Math.floor(hours / 24);
  const weeks = Math.floor(days / 7);
  const months = Math.floor(days / 30);
  const years = Math.floor(days / 365);

  if (secs < 60) return 'только что';
  if (mins < 60) return `${mins} мин. назад`;
  if (hours < 24) return `${hours} ч. назад`;
  if (days < 7) return `${days} дн. назад`;
  if (weeks < 4) return `${weeks} нед. назад`;
  if (months < 12) return `${months} мес. назад`;
  return `${years} г. назад`;
}

// Number formatting
export function formatNumber(num, decimals = 0) {
  if (num === null || num === undefined || isNaN(num)) return '0';
  if (num >= 1e9) return (num / 1e9).toFixed(decimals) + 'B';
  if (num >= 1e6) return (num / 1e6).toFixed(decimals) + 'M';
  if (num >= 1e3) return (num / 1e3).toFixed(decimals) + 'K';
  return num.toLocaleString('ru-RU');
}

export function formatCount(count) {
  if (count >= 1e6) return (count / 1e6).toFixed(1) + 'M';
  if (count >= 1e3) return (count / 1e3).toFixed(1) + 'K';
  return count.toString();
}

// String formatting
export function truncate(str, max = 50, suffix = '…') {
  if (!str || str.length <= max) return str;
  return str.slice(0, max - suffix.length) + suffix;
}

export function capitalize(str) {
  if (!str) return '';
  return str.charAt(0).toUpperCase() + str.slice(1).toLowerCase();
}

export function titleCase(str) {
  if (!str) return '';
  return str.toLowerCase().split(' ').map(w => capitalize(w)).join(' ');
}

export function slugify(str) {
  return str
    .toLowerCase()
    .trim()
    .replace(/[^\w\s-]/g, '')
    .replace(/[\s_-]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

export function highlight(text, query, className = 'highlight') {
  if (!query) return text;
  const regex = new RegExp(`(${escapeRegExp(query)})`, 'gi');
  return text.replace(regex, `<span class="${className}">$1</span>`);
}

function escapeRegExp(str) {
  return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

// File size
export function formatFileSize(bytes) {
  if (!bytes || bytes < 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(1024));
  return (bytes / Math.pow(1024, i)).toFixed(i === 0 ? 0 : 1) + ' ' + units[i];
}

// Duration from milliseconds to human readable
export function formatMs(ms) {
  if (ms < 1000) return `${ms}ms`;
  return formatDuration(ms);
}

// Genre/mood formatting
export function formatTags(tags, max = 3) {
  if (!tags || !tags.length) return '';
  const visible = tags.slice(0, max);
  const hidden = tags.length - max;
  let str = visible.map(t => `#${t}`).join(' ');
  if (hidden > 0) str += ` +${hidden}`;
  return str;
}

export default { formatTime, formatDuration, formatRelativeTime, formatNumber, formatCount, truncate, capitalize, titleCase, slugify, highlight, formatFileSize, formatMs, formatTags };