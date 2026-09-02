/* Кастомизация профиля: баннер, рамка аватара, описание «О себе».
   Значения уходят в облачный профиль через window.VotifyCloud.saveProfile
   (кнопка «Сохранить профиль» в карточке профиля). */
(function () {
  'use strict';

  const GRADS = {
    'grad-1': 'linear-gradient(135deg,#7928ca,#ff0080)',
    'grad-2': 'linear-gradient(135deg,#00f2fe,#4facfe)',
    'grad-3': 'linear-gradient(135deg,#833ab4,#fd1d1d,#fcb045)',
    'grad-4': 'linear-gradient(135deg,#10b981,#059669,#022c22)',
    'grad-5': 'linear-gradient(135deg,#ff416c,#ff4b2b)',
    'grad-6': 'linear-gradient(135deg,#00c6ff,#0072ff)',
    'grad-7': 'linear-gradient(135deg,#a855f7,#6366f1)',
    'grad-8': 'linear-gradient(135deg,#f43f5e,#fb7185)',
    'grad-9': 'linear-gradient(135deg,#18181b,#09090b)',
  };

  const cloud = () => window.VotifyCloud;

  function bannerCss(banner) {
    const raw = String(banner || '');
    if (!raw) return '';
    if (raw.startsWith('data:image/') || raw.startsWith('https://')) {
      return `background-image:url("${raw.replace(/"/g, '%22')}");background-size:cover;background-position:center;`;
    }
    if (GRADS[raw]) return `background-image:${GRADS[raw]};`;
    return '';
  }

  function applyOwnLook() {
    const api = cloud();
    const profile = (api && api.getProfile && api.getProfile()) || {};
    const banner = document.getElementById('profile-banner-value')?.value || profile.banner || '';
    const frame = document.getElementById('profile-frame-value')?.value || profile.frame || 'none';
    const header = document.querySelector('#profile-overlay .profile-card-header');
    if (header) header.style.cssText = bannerCss(banner);
    const avatar = document.querySelector('#profile-overlay .profile-avatar-large');
    if (avatar) avatar.className = 'profile-avatar-large frame-' + frame;
  }

  function wire() {
    document.getElementById('profile-frame-chips')?.addEventListener('click', e => {
      const chip = e.target.closest('.frame-chip');
      if (!chip) return;
      const hidden = document.getElementById('profile-frame-value');
      if (hidden) hidden.value = chip.dataset.frame;
      document
        .querySelectorAll('#profile-frame-chips .frame-chip')
        .forEach(c => c.classList.toggle('active', c === chip));
      applyOwnLook();
    });

    document.getElementById('profile-banner-presets')?.addEventListener('click', e => {
      const chip = e.target.closest('.banner-chip');
      if (!chip) return;
      const hidden = document.getElementById('profile-banner-value');
      if (hidden) hidden.value = chip.dataset.banner;
      document
        .querySelectorAll('#profile-banner-presets .banner-chip')
        .forEach(c => c.classList.toggle('active', c === chip));
      applyOwnLook();
    });

    document.getElementById('profile-banner-url')?.addEventListener('change', e => {
      const value = e.target.value.trim();
      if (/^https:\/\/[^\s]{1,300}$/.test(value)) {
        const hidden = document.getElementById('profile-banner-value');
        if (hidden) hidden.value = value;
        applyOwnLook();
      }
    });

    document.getElementById('profile-banner-input')?.addEventListener('change', e => {
      const file = e.target.files && e.target.files[0];
      e.target.value = '';
      if (!file) return;
      if (file.size > 200000) return;
      const reader = new FileReader();
      reader.onload = () => {
        const hidden = document.getElementById('profile-banner-value');
        if (hidden) hidden.value = String(reader.result || '');
        applyOwnLook();
      };
      reader.readAsDataURL(file);
    });

    window.addEventListener('votify:auth-changed', applyOwnLook);
    applyOwnLook();
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', wire);
  else wire();
})();
