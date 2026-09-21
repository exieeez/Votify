/**
 * Real Firebase Friend Activity & Presence Sync Manager for Votify
 */

window.SPOTIFY_FAKE_FRIENDS = [
  {
    id: 'friend-1',
    name: 'Алексей Смирнов',
    handle: '@alex_smirnov',
    avatar: 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150&h=150&fit=crop',
    status: 'online',
    statusText: 'Слушает сейчас',
    track: 'Blinding Lights',
    artist: 'The Weeknd',
    album: 'After Hours',
    cover: 'https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?w=150&h=150&fit=crop',
    timeAgo: 'Сейчас',
    isFollowing: true,
  },
  {
    id: 'friend-2',
    name: 'София Вершинина',
    handle: '@sofia_v',
    avatar: 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150&h=150&fit=crop',
    status: 'online',
    statusText: 'Слушает сейчас',
    track: 'Birds of a Feather',
    artist: 'Billie Eilish',
    album: 'HIT ME HARD AND SOFT',
    cover: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=150&h=150&fit=crop',
    timeAgo: '3 мин назад',
    isFollowing: true,
  },
  {
    id: 'friend-3',
    name: 'Михаил Ковалев',
    handle: '@misha_k',
    avatar: 'https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=150&h=150&fit=crop',
    status: 'idle',
    statusText: 'Был(а) 12 мин назад',
    track: 'Группа крови',
    artist: 'Кино',
    album: 'Группа крови',
    cover: 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=150&h=150&fit=crop',
    timeAgo: '12 мин назад',
    isFollowing: true,
  },
  {
    id: 'friend-4',
    name: 'Анна Раймер',
    handle: '@reimer_anna',
    avatar: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150&h=150&fit=crop',
    status: 'online',
    statusText: 'Слушает сейчас',
    track: 'Flowers',
    artist: 'Miley Cyrus',
    album: 'Endless Summer Vacation',
    cover: 'https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=150&h=150&fit=crop',
    timeAgo: 'Сейчас',
    isFollowing: true,
  },
  {
    id: 'friend-5',
    name: 'Денис Соколов',
    handle: '@den_sokol',
    avatar: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150&h=150&fit=crop',
    status: 'offline',
    statusText: 'Был(а) 42 мин назад',
    track: 'Где прошла ты',
    artist: 'MACAN',
    album: 'I AM',
    cover: 'https://images.unsplash.com/photo-1511379938547-c1f69419868d?w=150&h=150&fit=crop',
    timeAgo: '42 мин назад',
    isFollowing: false,
  },
  {
    id: 'friend-6',
    name: 'Елена Волкова',
    handle: '@elena_volk',
    avatar: 'https://images.unsplash.com/photo-1517841905240-472988babdf9?w=150&h=150&fit=crop',
    status: 'offline',
    statusText: 'Была 1 ч назад',
    track: 'As It Was',
    artist: 'Harry Styles',
    album: "Harry's House",
    cover: 'https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=150&h=150&fit=crop',
    timeAgo: '1 ч назад',
    isFollowing: true,
  },
];

// Firebase Firestore Live Friend Activity Sync
window.syncUserPresenceToFirebase = function(track) {
  if (!track) return;
  try {
    const user = window.VotifyCloud && window.VotifyCloud.getCurrentUser();
    const profile = window.VotifyCloud && window.VotifyCloud.getProfile();
    const db = window.firebase && window.firebase.firestore && window.firebase.firestore();
    
    if (user && db) {
      const uid = user.uid;
      const presenceRef = db.collection('users').doc(uid).collection('presence').doc('live');
      presenceRef.set({
        uid: uid,
        displayName: profile?.displayName || user.displayName || 'Пользователь Votify',
        avatar: profile?.avatar || 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150&h=150&fit=crop',
        trackTitle: track.title || 'Песня',
        artistName: track.artist || 'Исполнитель',
        albumTitle: track.album || 'Альбом',
        coverUrl: track.cover || '',
        status: 'online',
        updatedAt: window.firebase.firestore.FieldValue.serverTimestamp()
      }, { merge: true }).catch(err => console.log('Firestore presence sync notice:', err.message));
    }
  } catch (e) {
    console.warn('Presence sync:', e.message);
  }
};

window.listenToFirebaseFriendActivity = function() {
  try {
    const db = window.firebase && window.firebase.firestore && window.firebase.firestore();
    if (db) {
      db.collectionGroup('presence').onSnapshot(snapshot => {
        const liveFriends = [];
        snapshot.forEach(doc => {
          const data = doc.data();
          if (data && data.trackTitle) {
            liveFriends.push({
              id: data.uid || doc.id,
              name: data.displayName || 'Пользователь',
              handle: `@${(data.displayName || 'user').toLowerCase().replace(/\s+/g, '_')}`,
              avatar: data.avatar || 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150&h=150&fit=crop',
              status: data.status || 'online',
              statusText: 'Слушает в Firebase',
              track: data.trackTitle,
              artist: data.artistName,
              album: data.albumTitle || 'Альбом',
              cover: data.coverUrl || '',
              timeAgo: 'Сейчас',
              isFollowing: true,
            });
          }
        });
        if (liveFriends.length > 0) {
          // Merge Firebase live friends with sample friend list
          window.SPOTIFY_FAKE_FRIENDS = [...liveFriends, ...window.SPOTIFY_FAKE_FRIENDS.slice(liveFriends.length)];
          window.renderFriendActivitySidebar('friends-activity-list');
          window.renderMobileFriendsList('mobile-friends-list');
        }
      }, err => console.log('Firestore friend activity listener notice:', err.message));
    }
  } catch (e) {
    console.warn('Firebase activity listener:', e.message);
  }
};

window.renderFriendActivitySidebar = function (containerId = 'friends-activity-list') {
  const container = document.getElementById(containerId);
  if (!container) return;

  const friends = window.SPOTIFY_FAKE_FRIENDS || [];
  container.innerHTML = friends
    .map(
      f => `
    <div class="spotify-friend-card" data-friend-id="${f.id}">
      <div class="friend-avatar-wrap ${f.status}">
        <img class="friend-avatar-img" src="${f.avatar}" alt="${f.name}" />
        <span class="friend-status-dot ${f.status}"></span>
      </div>
      <div class="friend-info-col">
        <div class="friend-header-row">
          <span class="friend-name-text">${f.name}</span>
          <span class="friend-time-text">${f.timeAgo}</span>
        </div>
        <div class="friend-track-row">
          <span class="friend-track-title">${f.track}</span>
          <span class="friend-dot-sep">•</span>
          <span class="friend-artist-name">${f.artist}</span>
        </div>
        <div class="friend-album-row">
          <svg viewBox="0 0 24 24" class="friend-album-ic"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 14.5c-2.49 0-4.5-2.01-4.5-4.5S9.51 7.5 12 7.5s4.5 2.01 4.5 4.5-2.01 4.5-4.5 4.5z"/></svg>
          <span>${f.album}</span>
          ${
            f.status === 'online'
              ? `
            <div class="spotify-eq-bars">
              <span></span><span></span><span></span>
            </div>
          `
              : ''
          }
        </div>
      </div>
    </div>
  `
    )
    .join('');
};

window.renderMobileFriendsList = function (containerId = 'mobile-friends-list') {
  const container = document.getElementById(containerId);
  if (!container) return;

  const friends = window.SPOTIFY_FAKE_FRIENDS || [];
  container.innerHTML = friends
    .map(
      f => `
    <div class="mobile-friend-row">
      <div class="mobile-friend-left">
        <div class="mobile-friend-avatar-shell">
          <img src="${f.avatar}" alt="${f.name}" />
          <span class="mobile-status-badge ${f.status}"></span>
        </div>
        <div class="mobile-friend-meta">
          <div class="mobile-friend-name">${f.name}</div>
          <div class="mobile-friend-listening">
            <svg viewBox="0 0 24 24" width="12" height="12" fill="#1DB954"><path d="M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z"/></svg>
            <span>${f.track} — ${f.artist}</span>
          </div>
        </div>
      </div>
      <button class="mobile-friend-follow-btn ${f.isFollowing ? 'following' : ''}" data-friend-id="${f.id}">
        ${f.isFollowing ? 'Подписан' : 'Подписаться'}
      </button>
    </div>
  `
    )
    .join('');
};

// Initialize Firebase Presence listening on load
document.addEventListener('DOMContentLoaded', () => {
  if (typeof window.listenToFirebaseFriendActivity === 'function') {
    window.listenToFirebaseFriendActivity();
  }
});
