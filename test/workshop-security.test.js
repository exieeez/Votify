const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const rules = fs.readFileSync(path.join(__dirname, '..', 'firestore.rules'), 'utf8');

test('workshop rules allow public reads but restrict writes to permanent owners', () => {
  assert.match(rules, /match \/workshopThemes\/\{themeId\}/);
  assert.match(rules, /allow read: if true;/);
  assert.match(rules, /allow create: if isPermanentAccount\(\) && isValidWorkshopDocument\(\);/);
  assert.match(rules, /allow update: if false;/);
  assert.match(rules, /resource\.data\.ownerId == request\.auth\.uid/);
  assert.match(rules, /request\.auth\.token\.email is string/);
});

test('workshop rules reject arbitrary theme fields and unsafe values', () => {
  assert.match(rules, /theme\.keys\(\)\.hasOnly/);
  assert.match(rules, /theme\.backgroundUrl\.size\(\) <= 2048/);
  assert.match(rules, /theme\.backgroundUrl\.matches\('\^https:\/\/.\+\$'\)/);
  assert.ok(rules.includes("!theme.backgroundUrl.matches('^https://[^/]*@.*$')"));
  assert.match(rules, /value\.matches\('\^#\[0-9a-fA-F\]\{6\}\$'\)/);
  assert.match(rules, /request\.resource\.data\.description\.size\(\) <= 240/);
  assert.match(rules, /request\.resource\.data\.createdAt == request\.time/);
});
