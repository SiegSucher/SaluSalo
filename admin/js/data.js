/*
 * The dashboard's only way to its data.
 *
 * Today every function answers from the SAMPLE records in sample-data.js, after a short
 * delay like a network call, and changes last only until the page is reloaded. Nothing
 * is read from or sent to Supabase or Firebase.
 *
 * Connecting it later (Phase 6) means replacing the bodies below with calls to admin-only
 * server functions; the pages call these functions and would not change. Those server
 * functions don't exist yet, and this dashboard adds none.
 */
window.SaluData = (function () {
  'use strict';

  const { MIN, HOUR, DAY } = window.SaluSample;
  const WEEK = 7 * DAY;

  /* Labels shared with the app (model/Review.kt, model/FoodPost.kt, PostScreen.kt). */
  const text = {
    reasons: {
      LISTING: { UNSAFE_FOOD: 'Unsafe or spoiled food', MISLEADING: 'Wrong or misleading details', SPAM_FAKE: 'Spam or fake listing', OTHER: 'Other' },
      USER: { ABUSIVE: 'Rude or abusive', NO_SHOW: 'Didn’t show up', SCAM: 'Scam or fraud', SPAM: 'Spam', OTHER: 'Other' }
    },
    sources: { LISTING: 'From the listing page', DONOR_PAGE: 'From the donor’s page', CHAT: 'From a chat' },
    handover: { MEET_AT_PICKUP_LOCATION: 'Meet at pickup location', COLLECT_FROM_PROVIDER: 'Collect from provider' },
    audience: { ALL: 'Everyone', DONORS: 'Donors', RECIPIENTS: 'Recipients' },
    roles: { DONOR: 'Donor', RECIPIENT: 'Recipient', ADMIN: 'Admin' },
    categories: ['Bread & pastries', 'Vegetables', 'Fruit', 'Cooked meals', 'Packaged food', 'Other']
  };

  let db;
  let byId;
  let failMode = 'none';
  const listeners = new Set();
  try { failMode = sessionStorage.getItem('salusalo-admin:fail') || 'none'; } catch (e) { /* storage blocked */ }

  function boot() {
    db = window.SaluSample.create();
    byId = {
      users: new Map(db.users.map((u) => [u.id, u])),
      listings: new Map(db.listings.map((l) => [l.id, l])),
      reservations: new Map(db.reservations.map((r) => [r.id, r])),
      reports: new Map(db.reports.map((r) => [r.id, r])),
      announcements: new Map(db.announcements.map((a) => [a.id, a]))
    };
  }
  boot();

  const now = () => Date.now();
  const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
  const clone = (v) => JSON.parse(JSON.stringify(v));
  const emit = () => listeners.forEach((fn) => { try { fn(); } catch (e) { console.error(e); } });

  class DataError extends Error {}
  async function load(fn) {
    await wait(240 + Math.random() * 360);
    if (failMode === 'loads') throw new DataError('Sample load failure');
    return clone(fn());
  }
  async function act(fn) {
    await wait(520 + Math.random() * 380);
    if (failMode === 'actions') throw new DataError('Sample action failure');
    const out = fn();
    emit();
    return clone(out === undefined ? null : out);
  }

  /* ---------- Lookups and row shapes ---------- */
  const user = (id) => byId.users.get(id);
  const listing = (id) => byId.listings.get(id);
  const who = (u) => (u ? { id: u.id, name: u.name, org: u.org, role: u.role, isYou: !!u.isYou, kind: u.kind } : { id: null, name: 'Deleted account', org: false });
  const openReportsOn = (type, id) => db.reports.filter((r) => r.status === 'OPEN' && r.targetType === type && (type === 'LISTING' ? r.listingId === id : r.userId === id));
  const isActive = (l) => l.status === 'AVAILABLE' || l.status === 'RESERVED';

  function userRow(u) {
    return {
      id: u.id, name: u.name, email: u.email, role: u.role, org: u.org, kind: u.kind, isYou: !!u.isYou,
      city: u.city, emailVerified: u.emailVerified, verification: u.verification,
      accountStatus: u.accountStatus, joinedAt: u.joinedAt, lastActiveAt: u.lastActiveAt,
      openReports: openReportsOn('USER', u.id).length,
      requestType: u.request ? u.request.type : null,
      submittedAt: u.request ? u.request.submittedAt : null
    };
  }
  function listingRow(l) {
    const d = user(l.donorId);
    return {
      id: l.id, name: l.name, category: l.category, unit: l.unit, price: l.price,
      quantity: l.quantity, initialQuantity: l.initialQuantity,
      pickupStart: l.pickupStart, pickupEnd: l.pickupEnd, status: l.status,
      createdAt: l.createdAt, photos: l.photos, donor: who(d),
      openReports: openReportsOn('LISTING', l.id).length,
      openReservations: db.reservations.filter((r) => r.listingId === l.id && r.status === 'RESERVED').length
    };
  }
  function reservationRow(r) {
    const l = listing(r.listingId);
    return {
      id: r.id, quantity: r.quantity, unitPrice: r.unitPrice, total: r.quantity * r.unitPrice,
      status: r.status, cancelReason: r.cancelReason, createdAt: r.createdAt, updatedAt: r.updatedAt,
      listing: { id: l.id, name: l.name, category: l.category, unit: l.unit, status: l.status },
      donor: who(user(r.donorId)), recipient: who(user(r.recipientId))
    };
  }
  function reportTarget(r) {
    if (r.targetType === 'LISTING') {
      const l = listing(r.listingId);
      return l ? { type: 'LISTING', id: l.id, name: l.name, category: l.category, status: l.status, donor: who(user(l.donorId)) }
        : { type: 'LISTING', id: null, name: r.listingName || 'Deleted listing' };
    }
    const u = user(r.userId);
    return { type: 'USER', id: u.id, name: u.name, org: u.org, role: u.role, accountStatus: u.accountStatus };
  }
  function reportRow(r) {
    return {
      id: r.id, status: r.status, reason: r.reason, targetType: r.targetType,
      reasonLabel: text.reasons[r.targetType][r.reason], source: r.source,
      createdAt: r.createdAt, reporter: who(user(r.reporterId)), target: reportTarget(r),
      resolution: r.resolution ? Object.assign({}, r.resolution, { byName: who(user(r.resolution.by)).name }) : null
    };
  }

  function paginate(rows, page, pageSize) {
    const size = pageSize || 10;
    const pages = Math.max(1, Math.ceil(rows.length / size));
    const p = Math.min(Math.max(1, page || 1), pages);
    return { rows: rows.slice((p - 1) * size, p * size), total: rows.length, page: p, pageSize: size, pages };
  }
  const has = (value, q) => String(value || '').toLowerCase().includes(q);
  function sinceFor(range) {
    const t = now();
    return { TODAY: t - (t - new Date(t).setHours(0, 0, 0, 0)), '7D': t - 7 * DAY, '30D': t - 30 * DAY }[range] || 0;
  }

  /* ---------- Activity (what people did in the app) ---------- */
  function activityEvents(since) {
    const events = [];
    for (const u of db.users) {
      if (u.joinedAt >= since && u.role !== 'ADMIN') {
        events.push({ type: 'USER_JOINED', at: u.joinedAt, parts: [{ b: u.name }, ' registered'], meta: text.roles[u.role], link: '#/users/' + u.id });
      }
      if (u.request && u.request.submittedAt >= since && u.verification === 'PENDING') {
        events.push({ type: 'VERIFICATION', at: u.request.submittedAt, parts: [{ b: u.name }, ' asked to be verified'], meta: u.request.type, link: '#/users/' + u.id + '/verify' });
      }
    }
    for (const l of db.listings) {
      if (l.createdAt < since) break;
      const d = user(l.donorId);
      events.push({ type: 'LISTING', at: l.createdAt, parts: [{ b: d.name }, ' posted ', { b: l.name }], meta: l.initialQuantity + ' ' + l.unit + ' · ' + (l.price ? '₱' + l.price.toFixed(2) + ' each' : 'Free'), link: '#/listings/' + l.id });
    }
    for (const r of db.reservations) {
      const l = listing(r.listingId);
      const rec = user(r.recipientId);
      if (r.createdAt >= since) {
        events.push({ type: 'RESERVED', at: r.createdAt, parts: [{ b: rec.name }, ' reserved ' + r.quantity + ' ' + unitWord(l.unit, r.quantity)], meta: l.name + ' · from ' + user(l.donorId).name, link: '#/reservations/' + r.id });
      }
      if (r.status === 'CLAIMED' && r.updatedAt >= since) {
        events.push({ type: 'CLAIMED', at: r.updatedAt, parts: ['Reservation completed: ', { b: l.name }], meta: 'Picked up by ' + rec.name, link: '#/reservations/' + r.id });
      }
    }
    for (const rep of db.reports) {
      if (rep.createdAt >= since && rep.status === 'OPEN') {
        const t = reportTarget(rep);
        events.push({ type: 'REPORT', at: rep.createdAt, parts: [{ b: t.name }, rep.targetType === 'LISTING' ? ' was reported' : ' was reported'], meta: text.reasons[rep.targetType][rep.reason], link: '#/reports/' + rep.id });
      }
    }
    return events.sort((a, b) => b.at - a.at);
  }
  function unitWord(unit, n) {
    if (n !== 1) return unit;
    return { pcs: 'pc', packs: 'pack', meals: 'meal', boxes: 'box' }[unit] || unit;
  }

  /* ---------- Weekly series for analytics and the overview ---------- */
  function weeks(count) {
    const end = now();
    const out = [];
    for (let i = count - 1; i >= 0; i--) out.push({ start: end - (i + 1) * WEEK, end: end - i * WEEK });
    return out;
  }
  function bucket(list, weeksList, timeOf, valueOf) {
    const values = weeksList.map(() => 0);
    const first = weeksList[0].start;
    for (const item of list) {
      const t = timeOf(item);
      if (t == null || t < first || t >= weeksList[weeksList.length - 1].end) continue;
      const i = Math.floor((t - first) / WEEK);
      if (i >= 0 && i < values.length) values[i] += valueOf ? valueOf(item) : 1;
    }
    return values;
  }

  /* ---------- Audit ---------- */
  function audit(action, targetType, targetId, targetName, details) {
    db.audit.unshift({ id: 'AU-' + (8000 + db.audit.length), at: now(), adminId: db.youId, action, targetType, targetId, targetName, details, thisSession: true });
  }

  const api = {
    text,
    DataError,

    subscribe(fn) { listeners.add(fn); return () => listeners.delete(fn); },
    getFailMode: () => failMode,
    setFailMode(mode) {
      failMode = mode;
      try { sessionStorage.setItem('salusalo-admin:fail', mode); } catch (e) { /* storage blocked */ }
    },
    reset() { boot(); emit(); },

    /** Counts for the navigation badges; answers at once. */
    counts() {
      return {
        openReports: db.reports.filter((r) => r.status === 'OPEN').length,
        pendingVerifications: db.users.filter((u) => u.verification === 'PENDING').length,
        unreadAlerts: db.alerts.filter((a) => !a.read).length
      };
    },
    me() { return clone(userRow(user(db.youId))); },

    overview() {
      return load(() => {
        const t = now();
        const today = sinceFor('TODAY');
        const claimed30 = db.reservations.filter((r) => r.status === 'CLAIMED' && r.updatedAt >= t - 30 * DAY);
        const cancelled30 = db.reservations.filter((r) => r.status === 'CANCELLED' && r.updatedAt >= t - 30 * DAY);
        const w = weeks(12);
        return {
          counts: {
            users: db.users.length,
            usersThisWeek: db.users.filter((u) => u.joinedAt >= t - 7 * DAY).length,
            activeListings: db.listings.filter(isActive).length,
            listingsToday: db.listings.filter((l) => l.createdAt >= today).length,
            openReservations: db.reservations.filter((r) => r.status === 'RESERVED').length,
            reservationsToday: db.reservations.filter((r) => r.createdAt >= today).length,
            pendingReports: db.reports.filter((r) => r.status === 'OPEN').length,
            reportsToday: db.reports.filter((r) => r.status === 'OPEN' && r.createdAt >= today).length,
            pendingVerifications: db.users.filter((u) => u.verification === 'PENDING').length
          },
          activity: activityEvents(t - 3 * DAY).slice(0, 8),
          verification: db.users.filter((u) => u.verification === 'PENDING')
            .sort((a, b) => b.request.submittedAt - a.request.submittedAt).map(userRow),
          reports: db.reports.filter((r) => r.status === 'OPEN').slice(0, 3).map(reportRow),
          reservations: db.reservations.slice(0, 6).map(reservationRow),
          rescue: {
            itemsRescued30: claimed30.reduce((s, r) => s + r.quantity, 0),
            completed30: claimed30.length,
            cancelled30: cancelled30.length,
            activeDonors30: new Set(db.listings.filter((l) => l.createdAt >= t - 30 * DAY).map((l) => l.donorId)).size,
            weeklyItems: bucket(db.reservations.filter((r) => r.status === 'CLAIMED'), w, (r) => r.updatedAt, (r) => r.quantity),
            weekStarts: w.map((x) => x.start)
          }
        };
      });
    },

    /* ---------- Users ---------- */
    users({ q = '', role = 'ALL', verification = 'ALL', account = 'ALL', page = 1, pageSize = 10 } = {}) {
      return load(() => {
        const s = q.trim().toLowerCase();
        const rows = db.users.filter((u) =>
          (!s || has(u.name, s) || has(u.email, s)) &&
          (role === 'ALL' || u.role === role) &&
          (verification === 'ALL' || u.verification === verification) &&
          (account === 'ALL' || u.accountStatus === account))
          .sort((a, b) => b.joinedAt - a.joinedAt);
        return paginate(rows.map(userRow), page, pageSize);
      });
    },
    verificationQueue() {
      return load(() => db.users.filter((u) => u.verification === 'PENDING')
        .sort((a, b) => a.request.submittedAt - b.request.submittedAt)
        .map((u) => Object.assign(userRow(u), { listings: db.listings.filter((l) => l.donorId === u.id).length })));
    },
    user(id) {
      return load(() => {
        const u = user(id);
        if (!u) return null;
        const posted = db.listings.filter((l) => l.donorId === u.id);
        const made = db.reservations.filter((r) => r.recipientId === u.id);
        const handedOver = db.reservations.filter((r) => r.donorId === u.id && r.status === 'CLAIMED');
        const events = [];
        posted.slice(0, 6).forEach((l) => events.push({ at: l.createdAt, icon: 'lunchDining', text: 'Posted ' + l.name, meta: l.initialQuantity + ' ' + l.unit, link: '#/listings/' + l.id }));
        made.slice(0, 6).forEach((r) => {
          const l = listing(r.listingId);
          events.push({ at: r.createdAt, icon: 'bookmark', text: 'Reserved ' + r.quantity + ' ' + unitWord(l.unit, r.quantity) + ' of ' + l.name, meta: 'From ' + user(l.donorId).name, link: '#/reservations/' + r.id });
          if (r.status === 'CLAIMED') events.push({ at: r.updatedAt, icon: 'taskAlt', text: 'Picked up ' + l.name, meta: 'From ' + user(l.donorId).name, link: '#/reservations/' + r.id });
        });
        if (u.request) events.push({ at: u.request.submittedAt, icon: 'verified', text: 'Asked to be verified', meta: u.request.type, link: '#/users/' + u.id + '/verify' });
        events.push({ at: u.joinedAt, icon: 'personAdd', text: 'Joined Salu-Salo', meta: u.city });
        events.sort((a, b) => b.at - a.at);
        const request = u.request ? Object.assign({}, u.request, { decidedByName: u.request.decidedBy ? who(user(u.request.decidedBy)).name : null }) : null;
        return Object.assign(userRow(u), {
          barangay: u.barangay,
          request,
          suspension: u.suspension ? Object.assign({}, u.suspension, { byName: who(user(u.suspension.by)).name }) : null,
          stats: {
            listings: posted.length,
            activeListings: posted.filter(isActive).length,
            reservations: made.length,
            completed: made.filter((r) => r.status === 'CLAIMED').length,
            handedOver: handedOver.length,
            reportsAgainst: db.reports.filter((r) => r.targetType === 'USER' && r.userId === u.id).length,
            reportsOnListings: db.reports.filter((r) => r.targetType === 'LISTING' && posted.some((l) => l.id === r.listingId)).length
          },
          openReports: db.reports.filter((r) => r.status === 'OPEN' && ((r.targetType === 'USER' && r.userId === u.id) || (r.targetType === 'LISTING' && posted.some((l) => l.id === r.listingId)))).map(reportRow),
          events: events.slice(0, 6)
        });
      });
    },
    approveVerification(id, note) {
      return act(() => {
        const u = user(id);
        u.verification = 'VERIFIED';
        Object.assign(u.request, { decidedAt: now(), decidedBy: db.youId, decisionNote: note || null });
        db.alerts.forEach((a) => { if (a.link === '#/users/' + id + '/verify') a.read = true; });
        audit('Approved verification', 'USER', u.id, u.name, (note || u.request.type + ': documents match the account.'));
      });
    },
    rejectVerification(id, reason) {
      return act(() => {
        const u = user(id);
        u.verification = 'REJECTED';
        Object.assign(u.request, { decidedAt: now(), decidedBy: db.youId, decisionNote: reason });
        db.alerts.forEach((a) => { if (a.link === '#/users/' + id + '/verify') a.read = true; });
        audit('Rejected verification', 'USER', u.id, u.name, reason);
      });
    },
    verifyWithoutRequest(id, note) {
      return act(() => {
        const u = user(id);
        u.verification = 'VERIFIED';
        u.request = { submittedAt: now(), type: u.org ? 'Food business' : 'Household donor', documents: [], note: null, address: u.barangay + ', ' + u.city, decidedAt: now(), decidedBy: db.youId, decisionNote: note };
        audit('Verified account', 'USER', u.id, u.name, note);
      });
    },
    suspendUser(id, reason, reportId) {
      return act(() => {
        const u = user(id);
        u.accountStatus = 'SUSPENDED';
        u.suspension = { reason, at: now(), by: db.youId, reportId: reportId || null };
        let closedReports = 0;
        db.reports.forEach((r) => {
          if (r.status === 'OPEN' && r.targetType === 'USER' && r.userId === id) {
            r.status = 'RESOLVED';
            r.resolution = { action: 'USER_SUSPENDED', by: db.youId, at: now(), note: reason };
            closedReports++;
          }
        });
        if (reportId) {
          const r = byId.reports.get(reportId);
          if (r && r.status === 'OPEN') { r.status = 'RESOLVED'; r.resolution = { action: 'USER_SUSPENDED', by: db.youId, at: now(), note: reason }; closedReports++; }
        }
        audit('Suspended account', 'USER', u.id, u.name, reason + (reportId ? ' (' + reportId + ')' : ''));
        return { closedReports };
      });
    },
    restoreUser(id, note) {
      return act(() => {
        const u = user(id);
        u.accountStatus = 'ACTIVE';
        u.suspension = null;
        audit('Restored account', 'USER', u.id, u.name, note || 'Account restored.');
      });
    },

    /* ---------- Listings ---------- */
    listings({ q = '', status = 'ALL', posted = 'ANY', donorId = null, page = 1, pageSize = 10 } = {}) {
      return load(() => {
        const s = q.trim().toLowerCase();
        const since = sinceFor(posted);
        const rows = db.listings.filter((l) => {
          if (donorId && l.donorId !== donorId) return false;
          if (since && l.createdAt < since) return false;
          if (status === 'ACTIVE' && !isActive(l)) return false;
          if (status === 'REPORTED' && !openReportsOn('LISTING', l.id).length) return false;
          if (status !== 'ALL' && status !== 'ACTIVE' && status !== 'REPORTED' && l.status !== status) return false;
          return !s || has(l.name, s) || has(user(l.donorId).name, s) || has(l.id, s);
        });
        const out = paginate(rows, page, pageSize);
        out.rows = out.rows.map(listingRow);
        out.donorName = donorId ? (user(donorId) || {}).name : null;
        return out;
      });
    },
    listing(id) {
      return load(() => {
        const l = listing(id);
        if (!l) return null;
        return Object.assign(listingRow(l), {
          description: l.description, allergens: l.allergens, storageNotes: l.storageNotes,
          pickupLocation: l.pickupLocation, handover: l.handover, expiresAt: l.expiresAt, updatedAt: l.updatedAt,
          donor: Object.assign(who(user(l.donorId)), { verification: user(l.donorId).verification, email: user(l.donorId).email }),
          reviewedAt: l.reviewedAt, reviewedByName: l.reviewedBy ? who(user(l.reviewedBy)).name : null,
          removedByName: l.removedBy ? (l.removedBy === l.donorId ? 'the donor' : who(user(l.removedBy)).name) : null,
          removedReason: l.removedReason,
          reservations: db.reservations.filter((r) => r.listingId === l.id).map(reservationRow),
          reports: db.reports.filter((r) => r.targetType === 'LISTING' && r.listingId === l.id).map(reportRow)
        });
      });
    },
    removeListing(id, reason, reportId) {
      return act(() => {
        const l = listing(id);
        l.status = 'REMOVED';
        l.removedBy = db.youId;
        l.removedReason = reason;
        l.updatedAt = now();
        let cancelled = 0;
        db.reservations.forEach((r) => {
          if (r.listingId === id && r.status === 'RESERVED') {
            r.status = 'CANCELLED'; r.cancelReason = 'LISTING_REMOVED'; r.cancelledBy = 'LISTING_REMOVED'; r.updatedAt = now();
            cancelled++;
          }
        });
        let closedReports = 0;
        db.reports.forEach((r) => {
          if (r.status === 'OPEN' && r.targetType === 'LISTING' && r.listingId === id) {
            r.status = 'RESOLVED';
            r.resolution = { action: 'LISTING_REMOVED', by: db.youId, at: now(), note: reason };
            closedReports++;
          }
        });
        audit('Removed listing', 'LISTING', l.id, l.name, reason + (reportId ? ' (' + reportId + ')' : '') + (cancelled ? ' · ' + cancelled + ' reservation' + (cancelled === 1 ? '' : 's') + ' cancelled' : ''));
        return { cancelled, closedReports };
      });
    },
    dismissListingReports(id, note) {
      return act(() => {
        const l = listing(id);
        let dismissed = 0;
        db.reports.forEach((r) => {
          if (r.status === 'OPEN' && r.targetType === 'LISTING' && r.listingId === id) {
            r.status = 'DISMISSED';
            r.resolution = { action: 'DISMISSED', by: db.youId, at: now(), note: note || 'No problem found.' };
            dismissed++;
          }
        });
        audit('Dismissed report', 'LISTING', l.id, l.name, (note || 'No problem found.') + ' · ' + dismissed + ' report' + (dismissed === 1 ? '' : 's'));
        return { dismissed };
      });
    },
    markListingReviewed(id, note) {
      return act(() => {
        const l = listing(id);
        l.reviewedAt = now();
        l.reviewedBy = db.youId;
        audit('Reviewed listing', 'LISTING', l.id, l.name, note || 'Checked; no change needed.');
      });
    },

    /* ---------- Reservations (read-only for admins) ---------- */
    reservations({ q = '', status = 'ALL', date = 'ANY', page = 1, pageSize = 10 } = {}) {
      return load(() => {
        const s = q.trim().toLowerCase();
        const since = sinceFor(date);
        const rows = db.reservations.filter((r) => {
          if (status !== 'ALL' && r.status !== status) return false;
          if (since && r.createdAt < since) return false;
          if (!s) return true;
          const l = listing(r.listingId);
          return has(l.name, s) || has(user(r.donorId).name, s) || has(user(r.recipientId).name, s) || has(r.id, s);
        });
        const out = paginate(rows, page, pageSize);
        out.rows = out.rows.map(reservationRow);
        return out;
      });
    },
    reservation(id) {
      return load(() => {
        const r = byId.reservations.get(id);
        if (!r) return null;
        const l = listing(r.listingId);
        const row = reservationRow(r);
        const unit = (n) => n + ' ' + unitWord(l.unit, n);
        const steps = [
          { title: 'Reservation created', at: r.createdAt, meta: row.recipient.name + ' reserved ' + unit(r.quantity) + '.', state: 'done', icon: 'bookmark' },
          { title: 'Quantity reduced', at: r.createdAt, meta: 'The listing went from ' + unit(r.quantityBefore) + ' to ' + unit(Math.max(0, r.quantityBefore - r.quantity)) + ' available.', state: 'done', icon: 'inventory2' }
        ];
        if (r.status === 'RESERVED') {
          steps.push({ title: 'Waiting for donor confirmation', at: null, meta: row.donor.name + ' marks it picked up at the handover. Pickup ' + (l.pickupEnd > now() ? 'is open until ' + new Date(l.pickupEnd).toLocaleString('en-US', { weekday: 'short', hour: 'numeric', minute: '2-digit' }) + '.' : 'window has ended.'), state: 'current', icon: 'hourglassBottom' });
          steps.push({ title: 'Claimed', at: null, meta: 'Not yet.', state: 'pending', icon: 'taskAlt' });
        } else if (r.status === 'CLAIMED') {
          steps.push({ title: 'Waiting for donor confirmation', at: null, meta: 'The recipient came for the food.', state: 'done', icon: 'hourglassBottom' });
          steps.push({ title: 'Claimed', at: r.updatedAt, meta: 'Marked as picked up by ' + row.donor.name + '.', state: 'done', icon: 'taskAlt' });
        } else {
          steps.push({ title: 'Cancelled', at: r.updatedAt, meta: r.cancelReason === 'LISTING_REMOVED' ? 'Cancelled because the listing was removed; the recipient was alerted.' : 'Cancelled by ' + row.recipient.name + ' in the app. The quantity went back to the listing.', state: 'bad', icon: 'highlightOff' });
        }
        return Object.assign(row, {
          pickupStart: l.pickupStart, pickupEnd: l.pickupEnd, pickupLocation: l.pickupLocation, handover: l.handover,
          listingStatus: l.status, timeline: steps
        });
      });
    },

    /* ---------- Reports ---------- */
    reports({ q = '', status = 'OPEN', target = 'ALL', reason = 'ALL', page = 1, pageSize = 10 } = {}) {
      return load(() => {
        const s = q.trim().toLowerCase();
        const rows = db.reports.filter((r) => {
          if (status !== 'ALL' && r.status !== status) return false;
          if (target !== 'ALL' && r.targetType !== target) return false;
          if (reason !== 'ALL' && r.reason !== reason) return false;
          if (!s) return true;
          const t = reportTarget(r);
          return has(r.id, s) || has(t.name, s) || has(user(r.reporterId).name, s);
        });
        const out = paginate(rows, page, pageSize);
        out.rows = out.rows.map(reportRow);
        return out;
      });
    },
    report(id) {
      return load(() => {
        const r = byId.reports.get(id);
        if (!r) return null;
        const row = reportRow(r);
        const reporter = user(r.reporterId);
        const target = reportTarget(r);
        let targetDetail = null;
        if (r.targetType === 'LISTING' && target.id) {
          const l = listing(target.id);
          targetDetail = Object.assign(listingRow(l), { donorAccountStatus: user(l.donorId).accountStatus });
        } else if (r.targetType === 'USER') {
          targetDetail = userRow(user(r.userId));
        }
        const sameTarget = db.reports.filter((x) => x.id !== r.id && x.targetType === r.targetType && (r.targetType === 'LISTING' ? x.listingId === r.listingId : x.userId === r.userId));
        return Object.assign(row, {
          details: r.details,
          listingName: r.listingName,
          notes: r.notes.map((n) => ({ at: n.at, text: n.text, by: who(user(n.by)).name })),
          reporterDetail: Object.assign(userRow(reporter), { reportsFiled: db.reports.filter((x) => x.reporterId === reporter.id).length }),
          targetDetail,
          otherReports: sameTarget.map(reportRow)
        });
      });
    },
    dismissReport(id, note) {
      return act(() => {
        const r = byId.reports.get(id);
        r.status = 'DISMISSED';
        r.resolution = { action: 'DISMISSED', by: db.youId, at: now(), note: note || 'No problem found.' };
        audit('Dismissed report', 'REPORT', r.id, r.id, note || 'No problem found.');
      });
    },
    addReportNote(id, noteText) {
      return act(() => {
        const r = byId.reports.get(id);
        r.notes.push({ by: db.youId, at: now(), text: noteText });
      });
    },

    /* ---------- Notifications ---------- */
    announcements({ status = 'ALL' } = {}) {
      return load(() => db.announcements
        .filter((a) => status === 'ALL' || a.status === status)
        .sort((a, b) => b.at - a.at)
        .map((a) => Object.assign({}, a, { byName: who(user(a.by)).name })));
    },
    announcement(id) {
      return load(() => {
        const a = byId.announcements.get(id);
        return a ? Object.assign({}, a, { byName: who(user(a.by)).name }) : null;
      });
    },
    audienceSizes() {
      return {
        ALL: db.users.length,
        DONORS: db.users.filter((u) => u.role === 'DONOR').length,
        RECIPIENTS: db.users.filter((u) => u.role === 'RECIPIENT').length
      };
    },
    createAnnouncement({ title, message, audience, when, at, draft, replaceId }) {
      return act(() => {
        if (replaceId) {
          const i = db.announcements.findIndex((a) => a.id === replaceId);
          if (i >= 0) { byId.announcements.delete(replaceId); db.announcements.splice(i, 1); }
        }
        const sizes = api.audienceSizes();
        const status = draft ? 'DRAFT' : when === 'LATER' ? 'SCHEDULED' : 'SENT';
        const a = {
          id: 'N-' + (400 + db.announcements.length + Math.floor(Math.random() * 90)),
          title, message, audience, status,
          at: status === 'SCHEDULED' ? at : now(),
          by: db.youId,
          createdAt: now(),
          reach: status === 'SENT' ? sizes[audience] : 0
        };
        db.announcements.unshift(a);
        byId.announcements.set(a.id, a);
        if (status === 'SENT') audit('Sent notification', 'NOTIFICATION', a.id, a.title, 'To ' + a.reach + ' people (' + text.audience[audience].toLowerCase() + ').');
        if (status === 'SCHEDULED') audit('Scheduled notification', 'NOTIFICATION', a.id, a.title, 'For ' + new Date(at).toLocaleString('en-US', { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' }) + ' to ' + text.audience[audience].toLowerCase() + '.');
        return a;
      });
    },
    cancelScheduled(id) {
      return act(() => {
        const a = byId.announcements.get(id);
        a.status = 'CANCELLED';
        audit('Cancelled notification', 'NOTIFICATION', a.id, a.title, 'The scheduled notification won’t be sent.');
      });
    },
    deleteDraft(id) {
      return act(() => {
        const i = db.announcements.findIndex((a) => a.id === id);
        if (i >= 0) db.announcements.splice(i, 1);
        byId.announcements.delete(id);
      });
    },
    alerts() {
      return load(() => db.alerts.slice().sort((a, b) => b.at - a.at));
    },
    markAlertsRead() {
      return act(() => { db.alerts.forEach((a) => { a.read = true; }); });
    },
    markAlertRead(id) {
      const a = db.alerts.find((x) => x.id === id);
      if (a && !a.read) { a.read = true; emit(); }
    },

    /* ---------- Audit log ---------- */
    audit({ q = '', adminId = 'ALL', action = 'ALL', range = 'ANY', page = 1, pageSize = 12 } = {}) {
      return load(() => {
        const s = q.trim().toLowerCase();
        const since = sinceFor(range);
        const rows = db.audit.filter((a) =>
          (adminId === 'ALL' || a.adminId === adminId) &&
          (action === 'ALL' || a.action === action) &&
          (!since || a.at >= since) &&
          (!s || has(a.targetName, s) || has(a.details, s) || has(a.targetId, s)));
        const out = paginate(rows, page, pageSize);
        out.rows = out.rows.map((a) => Object.assign({}, a, { admin: who(user(a.adminId)) }));
        return out;
      });
    },
    auditFilters() {
      return {
        admins: db.users.filter((u) => u.role === 'ADMIN').map((u) => ({ id: u.id, name: u.name, isYou: !!u.isYou })),
        actions: Array.from(new Set(db.audit.map((a) => a.action))).sort()
      };
    },

    /* ---------- Analytics ---------- */
    analytics({ weeks: count = 12 } = {}) {
      return load(() => {
        const w = weeks(count);
        const from = w[0].start;
        const inRange = (t) => t >= from;
        const listingsIn = db.listings.filter((l) => inRange(l.createdAt));
        const resIn = db.reservations.filter((r) => inRange(r.createdAt));
        const claimed = db.reservations.filter((r) => r.status === 'CLAIMED');
        const cancelled = db.reservations.filter((r) => r.status === 'CANCELLED');
        const statusCounts = { AVAILABLE: 0, RESERVED: 0, CLAIMED: 0, EXPIRED: 0, REMOVED: 0 };
        listingsIn.forEach((l) => { statusCounts[l.status]++; });
        const claimedIn = claimed.filter((r) => inRange(r.updatedAt));
        const kpis = {
          listings: listingsIn.length,
          reservations: resIn.length,
          completed: claimedIn.length,
          itemsRescued: claimedIn.reduce((s, r) => s + r.quantity, 0),
          expired: db.listings.filter((l) => l.status === 'EXPIRED' && inRange(l.pickupEnd)).length,
          activeDonors: new Set(listingsIn.map((l) => l.donorId)).size,
          activeRecipients: new Set(resIn.map((r) => r.recipientId)).size
        };
        // Compare with the period before, when the sample reaches back that far (13 weeks).
        let previous = null;
        if (count * 2 <= 13) {
          const pFrom = from - count * WEEK;
          const inPrev = (t) => t >= pFrom && t < from;
          const lp = db.listings.filter((l) => inPrev(l.createdAt));
          const rp = db.reservations.filter((r) => inPrev(r.createdAt));
          const cp = claimed.filter((r) => inPrev(r.updatedAt));
          previous = {
            listings: lp.length, reservations: rp.length, completed: cp.length,
            itemsRescued: cp.reduce((s, r) => s + r.quantity, 0),
            expired: db.listings.filter((l) => l.status === 'EXPIRED' && inPrev(l.pickupEnd)).length,
            activeDonors: new Set(lp.map((l) => l.donorId)).size,
            activeRecipients: new Set(rp.map((r) => r.recipientId)).size
          };
        }
        return {
          weekStarts: w.map((x) => x.start),
          kpis, previous,
          series: {
            listings: bucket(db.listings, w, (l) => l.createdAt),
            reservations: bucket(db.reservations, w, (r) => r.createdAt),
            completed: bucket(claimed, w, (r) => r.updatedAt),
            cancelled: bucket(cancelled, w, (r) => r.updatedAt),
            itemsRescued: bucket(claimed, w, (r) => r.updatedAt, (r) => r.quantity)
          },
          statusCounts
        };
      });
    },

    /* ---------- Settings (sample: saved in this page only) ---------- */
    settings() { return load(() => db.settings); },
    saveSettings(section, values, describe) {
      return act(() => {
        const before = db.settings[section];
        const changes = [];
        for (const key of Object.keys(values)) {
          if (before[key] !== values[key]) changes.push(describe ? describe(key, before[key], values[key]) : key + ': ' + before[key] + ' → ' + values[key]);
        }
        db.settings[section] = Object.assign({}, before, values);
        if (changes.length) audit('Changed setting', 'SETTING', section, section.charAt(0).toUpperCase() + section.slice(1), changes.join('; '));
        return { changed: changes.length };
      });
    }
  };
  return api;
})();
