// Run: node admin/tests/timeline.test.js
const assert = require('node:assert');
const T = require('../js/timeline.js');

const now = Date.UTC(2026, 9, 3, 6, 0);
const base = { quantity: 2, listing: { unit: 'packs' }, recipient: { name: 'Ana' }, donor: { name: 'Ben' }, createdAt: now - 7200e3, updatedAt: now - 60e3, pickupDeadline: null, pickupEnd: null };

let s = T.build(Object.assign({}, base, { status: 'RESERVED', pickupDeadline: now + 3600e3 }), now);
assert.strictEqual(s.length, 3);
assert.strictEqual(s[0].meta, 'Ana reserved 2 packs.');
assert.strictEqual(s[1].state, 'current');
assert.match(s[1].meta, /Pick up by /);
assert.strictEqual(s[2].state, 'pending');

s = T.build(Object.assign({}, base, { status: 'RESERVED', pickupEnd: now - 1 }), now);
assert.match(s[1].meta, /The pickup time has passed\./);

for (const [reason, title, re] of [
  ['LISTING_REMOVED', 'Cancelled: listing removed', /listing was removed/],
  ['DONOR_RELEASED', 'Released by the donor', /Released by Ben/],
  ['PICKUP_EXPIRED', 'Expired', /Not picked up in time/],
  [null, 'Cancelled', /Cancelled by Ana in the app/]
]) {
  s = T.build(Object.assign({}, base, { status: 'CANCELLED', cancelReason: reason }), now);
  assert.strictEqual(s.length, 2);
  assert.strictEqual(s[1].state, 'bad');
  assert.strictEqual(s[1].title, title);
  assert.match(s[1].meta, re);
  assert.strictEqual(s[1].at, base.updatedAt);
}

s = T.build(Object.assign({}, base, { quantity: 1, status: 'CLAIMED' }), now);
assert.strictEqual(s[0].meta, 'Ana reserved 1 pack.');
assert.strictEqual(s[1].title, 'Claimed');
assert.strictEqual(s[1].meta, 'Marked as picked up by Ben.');

console.log('timeline: all checks passed');
