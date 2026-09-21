/**
 * Spotify 1:1 Mock Data & Audio Synthesizer Engine for Votify
 */

window.SPOTIFY_MOCK_DATA = {
  quickGrid: [
    {
      id: 'quick-1',
      title: 'Любимые треки',
      type: 'playlist',
      coverGradient: 'linear-gradient(135deg, #450af5, #c4efd9)',
      icon: 'favorite',
      trackCount: 48,
    },
    {
      id: 'quick-2',
      title: 'Открытия недели',
      type: 'playlist',
      coverGradient: 'linear-gradient(135deg, #1e3264, #a0cbe8)',
      icon: 'auto_awesome',
      trackCount: 30,
    },
    {
      id: 'quick-3',
      title: 'Микс дня 1',
      type: 'playlist',
      coverGradient: 'linear-gradient(135deg, #8400e7, #e8115b)',
      icon: 'album',
      trackCount: 25,
    },
    {
      id: 'quick-4',
      title: 'Топ-50 (Россия)',
      type: 'playlist',
      coverGradient: 'linear-gradient(135deg, #006450, #27856a)',
      icon: 'bar_chart',
      trackCount: 50,
    },
    {
      id: 'quick-5',
      title: 'Chill Hits',
      type: 'playlist',
      coverGradient: 'linear-gradient(135deg, #bc5900, #e91429)',
      icon: 'bedtime',
      trackCount: 40,
    },
    {
      id: 'quick-6',
      title: 'Rock Classics',
      type: 'playlist',
      coverGradient: 'linear-gradient(135deg, #535353, #181818)',
      icon: 'electric_bolt',
      trackCount: 60,
    },
  ],

  topTracks: [
    {
      id: 'mock-tr-1',
      title: 'boost it',
      artist: 'Kai Angel',
      album: 'GOD SPEED',
      duration: 180,
      durationStr: '3:00',
      cover: 'https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?w=300&h=300&fit=crop',
      coverBg: '#1a0826',
      genre: 'Hip-Hop / Electronic',
      synthFreq: 440,
    },
    {
      id: 'mock-tr-2',
      title: 'Blinding Lights',
      artist: 'The Weeknd',
      album: 'After Hours',
      duration: 200,
      durationStr: '3:20',
      cover: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=300&h=300&fit=crop',
      coverBg: '#092336',
      genre: 'Pop / Synthwave',
      synthFreq: 523.25,
    },
    {
      id: 'mock-tr-3',
      title: 'Birds of a Feather',
      artist: 'Billie Eilish',
      album: 'HIT ME HARD AND SOFT',
      duration: 198,
      durationStr: '3:18',
      cover: 'https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=300&h=300&fit=crop',
      coverBg: '#362a09',
      genre: 'Indie Pop',
      synthFreq: 392,
    },
    {
      id: 'mock-tr-4',
      title: 'Группа крови',
      artist: 'Кино',
      album: 'Группа крови',
      duration: 285,
      durationStr: '4:45',
      cover: 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=300&h=300&fit=crop',
      coverBg: '#222222',
      genre: 'Post-Punk / Rock',
      synthFreq: 329.63,
    },
    {
      id: 'mock-tr-5',
      title: 'Starboy',
      artist: 'The Weeknd, Daft Punk',
      album: 'Starboy',
      duration: 230,
      durationStr: '3:50',
      cover: 'https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=300&h=300&fit=crop',
      coverBg: '#3d071c',
      genre: 'R&B / Electronic',
      synthFreq: 587.33,
    },
    {
      id: 'mock-tr-6',
      title: 'As It Was',
      artist: 'Harry Styles',
      album: "Harry's House",
      duration: 167,
      durationStr: '2:47',
      cover: 'https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=300&h=300&fit=crop',
      coverBg: '#093630',
      genre: 'Indie Pop',
      synthFreq: 659.25,
    },
    {
      id: 'mock-tr-7',
      title: 'Где прошла ты',
      artist: 'MACAN',
      album: 'I AM',
      duration: 184,
      durationStr: '3:04',
      cover: 'https://images.unsplash.com/photo-1511379938547-c1f69419868d?w=300&h=300&fit=crop',
      coverBg: '#1f1508',
      genre: 'Hip-Hop',
      synthFreq: 293.66,
    },
    {
      id: 'mock-tr-8',
      title: 'Комета',
      artist: 'JONY',
      album: 'Список твоих мыслей',
      duration: 160,
      durationStr: '2:40',
      cover: 'https://images.unsplash.com/photo-1446776811953-b23d57bd21aa?w=300&h=300&fit=crop',
      coverBg: '#0a1d36',
      genre: 'Pop / R&B',
      synthFreq: 349.23,
    },
  ],

  artists: [
    {
      id: 'art-1',
      name: 'The Weeknd',
      followers: '112 млн',
      image: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=300&h=300&fit=crop',
      genre: 'R&B / Synthpop',
    },
    {
      id: 'art-2',
      name: 'Billie Eilish',
      followers: '98 млн',
      image: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300&h=300&fit=crop',
      genre: 'Alternative',
    },
    {
      id: 'art-3',
      name: 'Кино',
      followers: '4.2 млн',
      image: 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=300&h=300&fit=crop',
      genre: 'Post-Punk',
    },
    {
      id: 'art-4',
      name: 'JONY',
      followers: '3.8 млн',
      image: 'https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=300&h=300&fit=crop',
      genre: 'Pop / R&B',
    },
    {
      id: 'art-5',
      name: 'MACAN',
      followers: '5.1 млн',
      image: 'https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=300&h=300&fit=crop',
      genre: 'Hip-Hop',
    },
    {
      id: 'art-6',
      name: 'Taylor Swift',
      followers: '105 млн',
      image: 'https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=300&h=300&fit=crop',
      genre: 'Pop',
    },
  ],

  madeForYou: [
    {
      id: 'mfy-1',
      title: 'Daily Mix 1',
      subtitle: 'The Weeknd, Daft Punk, Lana Del Rey и другие',
      cover: 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=300&h=300&fit=crop',
      badge: 'Mix',
    },
    {
      id: 'mfy-2',
      title: 'Радар новинок',
      subtitle: 'Свежие релизы от ваших любимых исполнителей',
      cover: 'https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=300&h=300&fit=crop',
      badge: 'Radar',
    },
    {
      id: 'mfy-3',
      title: 'Топ-50 Главное',
      subtitle: 'Самые популярные треки прямо сейчас',
      cover: 'https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?w=300&h=300&fit=crop',
      badge: 'Charts',
    },
    {
      id: 'mfy-4',
      title: 'Вечерний Релакс',
      subtitle: 'Спокойная музыка для отдыха и работы',
      cover: 'https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=300&h=300&fit=crop',
      badge: 'Chill',
    },
  ],
};

// Web Audio API Synthesizer Fallback for Playing Mock Tracks
class MockAudioSynthesizer {
  constructor() {
    this.ctx = null;
    this.osc = null;
    this.gainNode = null;
    this.isPlaying = false;
    this.currentTrack = null;
    this.playbackTime = 0;
    this.timer = null;
    this.onTimeUpdate = null;
    this.onEnded = null;
  }

  init() {
    if (!this.ctx) {
      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      if (AudioCtx) {
        this.ctx = new AudioCtx();
      }
    }
  }

  play(track, onTimeUpdate, onEnded) {
    this.init();
    this.stop();

    this.currentTrack = track;
    this.onTimeUpdate = onTimeUpdate;
    this.onEnded = onEnded;
    this.playbackTime = 0;
    this.isPlaying = true;

    if (this.ctx) {
      if (this.ctx.state === 'suspended') {
        this.ctx.resume();
      }
      this.startMelody(track.synthFreq || 440);
    }

    this.timer = setInterval(() => {
      if (!this.isPlaying) return;
      this.playbackTime += 1;
      const duration = track.duration || 180;
      if (typeof this.onTimeUpdate === 'function') {
        this.onTimeUpdate(this.playbackTime, duration);
      }
      if (this.playbackTime >= duration) {
        this.stop();
        if (typeof this.onEnded === 'function') this.onEnded();
      }
    }, 1000);
  }

  startMelody(baseFreq) {
    if (this.osc) {
      try {
        this.osc.stop();
        this.osc.disconnect();
      } catch (e) {}
      this.osc = null;
    }
  }

  pause() {
    this.isPlaying = false;
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
    if (this.gainNode && this.ctx) {
      try { this.gainNode.gain.setValueAtTime(0, this.ctx.currentTime); } catch (e) {}
    }
  }

  resume() {
    if (!this.currentTrack) return;
    this.isPlaying = true;
    if (this.timer) clearInterval(this.timer);
    this.timer = setInterval(() => {
      if (!this.isPlaying) return;
      this.playbackTime += 1;
      const duration = this.currentTrack.duration || 180;
      if (typeof this.onTimeUpdate === 'function') {
        this.onTimeUpdate(this.playbackTime, duration);
      }
      if (this.playbackTime >= duration) {
        this.stop();
        if (typeof this.onEnded === 'function') this.onEnded();
      }
    }, 1000);
  }

  stop() {
    this.isPlaying = false;
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
    if (this.osc) {
      try {
        this.osc.stop();
        this.osc.disconnect();
      } catch (e) {}
      this.osc = null;
    }
  }

  seek(seconds) {
    this.playbackTime = seconds;
    if (typeof this.onTimeUpdate === 'function' && this.currentTrack) {
      this.onTimeUpdate(this.playbackTime, this.currentTrack.duration || 180);
    }
  }
}

window.spotifyAudioSynth = new MockAudioSynthesizer();
