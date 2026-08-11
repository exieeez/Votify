const test = require('node:test');
const assert = require('node:assert/strict');
const { EventEmitter } = require('node:events');
const {
  DiscordPresence,
  buildListeningActivity,
  isExternalImageUrl,
  LISTENING_ACTIVITY_TYPE,
} = require('../discord-presence.js');

test('builds a listening activity with a playback timeline and cover', () => {
  const now = 1_800_000_000_000;
  const activity = buildListeningActivity(
    {
      title: 'Track title',
      artist: 'Artist',
      cover: 'https://example.com/cover.jpg',
      position: 30,
      duration: 210,
      playbackRate: 1,
      isPlaying: true,
    },
    { applicationName: 'Votify' },
    now
  );

  assert.equal(activity.name, 'Votify');
  assert.equal(activity.type, LISTENING_ACTIVITY_TYPE);
  assert.equal(activity.details, 'Track title');
  assert.equal(activity.state, 'Artist');
  assert.equal(activity.largeImageKey, 'https://example.com/cover.jpg');
  assert.equal(activity.startTimestamp, now - 30_000);
  assert.equal(activity.endTimestamp, now + 180_000);
});

test('freezes a paused activity by omitting advancing timestamps', () => {
  const activity = buildListeningActivity({
    title: 'Paused track',
    artist: 'Artist',
    position: 40,
    duration: 120,
    isPlaying: false,
  });

  assert.equal(activity.state, 'Artist • На паузе');
  assert.equal(activity.startTimestamp, undefined);
  assert.equal(activity.endTimestamp, undefined);
});

test('accounts for playback speed in the Discord timeline', () => {
  const now = 1_800_000_000_000;
  const activity = buildListeningActivity(
    {
      title: 'Fast track',
      artist: 'Artist',
      position: 20,
      duration: 100,
      playbackRate: 2,
      isPlaying: true,
    },
    {},
    now
  );

  assert.equal(activity.startTimestamp, now - 10_000);
  assert.equal(activity.endTimestamp, now + 40_000);
});

test('uses a configured fallback asset for local cover data', () => {
  const activity = buildListeningActivity(
    {
      title: 'Local track',
      artist: 'Local artist',
      cover: 'data:image/png;base64,abc',
      isPlaying: true,
    },
    { fallbackImageKey: 'votify' }
  );

  assert.equal(activity.largeImageKey, 'votify');
  assert.equal(isExternalImageUrl('https://example.com/image.webp'), true);
  assert.equal(isExternalImageUrl('data:image/png;base64,abc'), false);
});

test('normalizes and truncates untrusted metadata', () => {
  const activity = buildListeningActivity({
    title: `  ${'a'.repeat(150)}  `,
    artist: '   ',
    duration: Number.NaN,
    position: -50,
    isPlaying: true,
  });

  assert.equal([...activity.details].length, 128);
  assert.equal(activity.state, 'Неизвестный исполнитель');
  assert.equal(activity.startTimestamp, undefined);
});

test('clears stale sample game presence when Discord connects', async () => {
  const calls = [];
  class FakeClient extends EventEmitter {
    constructor() {
      super();
      this.user = {
        setActivity: async activity => calls.push(activity),
        clearActivity: async () => calls.push(null),
      };
    }

    async login() {
      queueMicrotask(() => this.emit('ready'));
    }

    async destroy() {}
  }

  const presence = new DiscordPresence({
    clientId: '123456789012345678',
    ClientClass: FakeClient,
    logger: { log() {}, warn() {} },
  });
  presence.start();
  await new Promise(resolve => setImmediate(resolve));

  assert.deepEqual(calls, [null]);
  await presence.stop();
});

test('queues renderer updates until Discord is connected', async () => {
  const calls = [];
  class FakeClient extends EventEmitter {
    constructor() {
      super();
      this.user = {
        setActivity: async activity => calls.push(activity),
        clearActivity: async () => calls.push(null),
      };
    }

    async login() {
      queueMicrotask(() => this.emit('ready'));
    }

    async destroy() {}
  }

  const presence = new DiscordPresence({
    clientId: '123456789012345678',
    ClientClass: FakeClient,
    logger: { log() {}, warn() {} },
  });
  presence.update({
    title: 'Queued track',
    artist: 'Artist',
    duration: 60,
    isPlaying: true,
  });
  await new Promise(resolve => setImmediate(resolve));

  assert.equal(calls.length, 1);
  assert.equal(calls[0].details, 'Queued track');
  presence.clear();
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(calls.at(-1), null);
  await presence.stop();
});
