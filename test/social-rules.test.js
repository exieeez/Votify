const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const rules = fs.readFileSync(path.join(__dirname, '..', 'firestore.rules'), 'utf8');

test('social rules define username, profile, follow and request collections', () => {
  assert.match(rules, /match \/usernames\/\{name\}/);
  assert.match(rules, /match \/profiles\/\{uid\}/);
  assert.match(rules, /match \/follows\/\{edgeId\}/);
  assert.match(rules, /match \/followRequests\/\{edgeId\}/);
});

test('username format and reserved list are enforced', () => {
  assert.match(rules, /\^\[a-z\]\[a-z0-9_\]\{2,31\}\$/);
  assert.match(rules, /function isReservedUsername\(name\)/);
  assert.match(rules, /!isReservedUsername\(name\)/);
});

test('public profiles are owner-writable with counter nudges for follows', () => {
  assert.match(rules, /function isValidPublicProfile\(data\)/);
  assert.match(rules, /function isCounterNudge\(\)/);
  assert.match(rules, /affectedKeys\(\)/);
  assert.match(rules, /followersCount == resource\.data\.followersCount \+ 1/);
  assert.match(rules, /data\.avatar\.size\(\) <= 153600/);
  assert.match(rules, /data\.showcase is list && data\.showcase\.size\(\) <= 20/);
  assert.match(rules, /'frame'\n        \]\)/);
  assert.match(rules, /\('frame' in data\.keys\(\)\)/);
});

test('follow edges are self-only creates with deterministic ids', () => {
  assert.match(rules, /function isValidFollowEdge\(edgeId\)/);
  assert.match(rules, /edgeId == request\.auth\.uid \+ '_' \+ request\.resource\.data\.following/);
  assert.match(rules, /request\.resource\.data\.follower == request\.auth\.uid/);
  assert.match(rules, /request\.resource\.data\.following != request\.auth\.uid/);
});

test('accepting a request can create the edge from the recipient side', () => {
  assert.match(rules, /function isValidAcceptEdge\(edgeId\)/);
  assert.match(rules, /isValidFollowEdge\(edgeId\) \|\| isValidAcceptEdge\(edgeId\)/);
  assert.match(
    rules,
    /exists\(\/databases\/\$\(database\)\/documents\/followRequests\/\$\(edgeId\)\)/
  );
});
