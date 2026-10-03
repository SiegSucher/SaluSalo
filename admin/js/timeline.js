/* A reservation's steps, from what the database stores. Pure; tested by admin/tests/timeline.test.js. */
(function (root) {
  'use strict';

  const SINGULAR = { pcs: 'pc', packs: 'pack', meals: 'meal', boxes: 'box' };
  const qty = (n, unit) => n + ' ' + (n === 1 ? SINGULAR[unit] || unit : unit);
  const when = (t) => new Date(t).toLocaleString('en-US', { weekday: 'short', hour: 'numeric', minute: '2-digit' });

  const CANCEL_TITLE = { LISTING_REMOVED: 'Cancelled: listing removed', DONOR_RELEASED: 'Released by the donor', PICKUP_EXPIRED: 'Expired' };
  function cancelMeta(r) {
    if (r.cancelReason === 'LISTING_REMOVED') return 'Cancelled because the listing was removed; the recipient was alerted.';
    if (r.cancelReason === 'DONOR_RELEASED') return 'Released by ' + r.donor.name + '; the quantity went back to the listing.';
    if (r.cancelReason === 'PICKUP_EXPIRED') return 'Not picked up in time, so it expired by itself; the quantity went back to the listing.';
    return 'Cancelled by ' + r.recipient.name + ' in the app; the quantity went back to the listing.';
  }

  function build(r, now) {
    const t = now || Date.now();
    const steps = [{ title: 'Reservation created', at: r.createdAt, meta: r.recipient.name + ' reserved ' + qty(r.quantity, r.listing.unit) + '.', state: 'done', icon: 'bookmark' }];
    if (r.status === 'RESERVED') {
      const until = r.pickupDeadline || r.pickupEnd;
      const due = until ? (until > t ? ' Pick up by ' + when(until) + '.' : ' The pickup time has passed.') : '';
      steps.push({ title: 'Waiting for pickup', at: null, meta: r.donor.name + ' marks it picked up at the handover.' + due, state: 'current', icon: 'hourglassBottom' });
      steps.push({ title: 'Claimed', at: null, meta: 'Not yet.', state: 'pending', icon: 'taskAlt' });
    } else if (r.status === 'CLAIMED') {
      steps.push({ title: 'Claimed', at: r.updatedAt, meta: 'Marked as picked up by ' + r.donor.name + '.', state: 'done', icon: 'taskAlt' });
    } else {
      steps.push({ title: CANCEL_TITLE[r.cancelReason] || 'Cancelled', at: r.updatedAt, meta: cancelMeta(r), state: 'bad', icon: 'highlightOff' });
    }
    return steps;
  }

  const api = { build };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.SaluTimeline = api;
})(this);
