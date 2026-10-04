const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const rules = fs.readFileSync(path.join(__dirname, '..', 'firestore.rules'), 'utf8');

test('publicPlaylists rules enforce public read, authenticated create/update, and owner delete', () => {
  assert.match(rules, /match \/publicPlaylists\/\{playlistId\}/);
  assert.match(rules, /match \/publicPlaylists\/\{playlistId\}[\s\S]*?allow read: if true;/);
  assert.match(
    rules,
    /match \/publicPlaylists\/\{playlistId\}[\s\S]*?allow create, update: if isSignedIn\(\);/
  );
  assert.match(
    rules,
    /match \/publicPlaylists\/\{playlistId\}[\s\S]*?allow delete: if isSignedIn\(\) && resource\.data\.ownerId == request\.auth\.uid;/
  );
});

test('existing collections rules remain intact', () => {
  assert.match(rules, /match \/users\/\{userId\}/);
  assert.match(rules, /match \/usernames\/\{handleLower\}/);
  assert.match(rules, /match \/profiles\/\{uid\}/);
  assert.match(rules, /match \/friendships\/\{friendshipId\}/);
  assert.match(rules, /match \/workshopThemes\/\{themeId\}/);
});
