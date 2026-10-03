/*
 * The dashboard's only way to its data: admin-only Supabase functions
 * (supabase/migrations/20261012_admin_dashboard.sql, 20261013 (Analytics) and 20261014 (announcements)). Each one refuses anyone who
 * isn't an admin; this page holds only the publishable key, so it can't get around
 * that. Times are epoch milliseconds. Photos are signed here for 1 hour and never logged.
 */
window.SaluData = (function () {
  'use strict';

  /* Labels shared with the app (model/Review.kt, model/FoodPost.kt, PostScreen.kt). */
  const text = {
    reasons: {
      LISTING: { UNSAFE_FOOD: 'Unsafe or spoiled food', MISLEADING: 'Wrong or misleading details', SPAM_FAKE: 'Spam or fake listing', OTHER: 'Other' },
      USER: { ABUSIVE: 'Rude or abusive', NO_SHOW: 'Didn’t show up', SCAM: 'Scam or fraud', SPAM: 'Spam', OTHER: 'Other' }
    },
    sources: { LISTING: 'From the listing page', DONOR_PAGE: 'From the donor’s page', CHAT: 'From a chat' },
    handover: { MEET_AT_PICKUP_LOCATION: 'Meet at pickup location', COLLECT_FROM_PROVIDER: 'Collect from provider' },
    roles: { DONOR: 'Donor', RECIPIENT: 'Recipient', ADMIN: 'Admin' },
    actions: { LISTING_REMOVED: 'Removed listing', REPORTS_DISMISSED: 'Dismissed report', LISTING_REVIEWED: 'Reviewed listing', REPORT_NOTE_ADDED: 'Added note', ANNOUNCEMENT_SENT: 'Sent announcement' },
    audience: { ALL: 'Everyone', DONORS: 'Donors', RECIPIENTS: 'Recipients' }
  };

  class DataError extends Error {}
  class AccessError extends DataError {}

  let sb = null;
  let me = { id: null, name: '', email: '' };
  let admins = [];
  let openReports = 0;
  let onLost = () => {};
  const listeners = new Set();
  const emit = () => listeners.forEach((fn) => { try { fn(); } catch (e) { console.error(e); } });

  async function rpc(name, args) {
    const { data, error } = await sb.rpc(name, args || {});
    if (error) {
      // 42501/28000: not (or no longer) an admin; PGRST30x: the token was refused.
      if (error.code === '42501' || error.code === '28000' || /^PGRST30/.test(error.code || '')) {
        const e = new AccessError('Admin access required');
        onLost(e);
        throw e;
      }
      // Our own short texts; never details or hints. The code tells a refusal (P0001) from a failure.
      throw Object.assign(new DataError(error.message || 'Request failed'), { code: error.code || null });
    }
    return data;
  }

  const labelReport = (r) => { if (r) r.reasonLabel = (text.reasons[r.targetType] || {})[r.reason] || r.reason; return r; };
  const markYou = (p) => { if (p && p.id === me.id) p.isYou = true; return p; };
  const paging = (q) => ({ p_page: q.page || 1, p_page_size: q.pageSize || 10 });

  async function signPaths(paths) {
    const list = Array.from(new Set(paths.filter(Boolean)));
    if (!list.length) return {};
    try {
      const { data, error } = await sb.storage.from('food-images').createSignedUrls(list, 3600);
      if (error || !data) return {};
      const out = {};
      data.forEach((d) => { if (d && d.signedUrl && !d.error) out[d.path] = d.signedUrl; });
      return out;
    } catch (e) {
      return {}; // photos are optional; the tile shows instead
    }
  }
  async function withCovers(items, pick) {
    const urls = await signPaths(items.map((x) => pick(x) && pick(x).coverPath));
    items.forEach((x) => { const t = pick(x); if (t) t.photoUrl = urls[t.coverPath] || null; });
    return items;
  }
  async function refreshCounts() {
    const c = await rpc('admin_counts');
    openReports = c.openReports;
    emit();
  }
  /** After an action that worked: a failed count reload must not report the action as failed. */
  const refreshCountsAfterAction = () => refreshCounts().catch(() => {});

  return {
    text, DataError, AccessError,

    subscribe(fn) { listeners.add(fn); return () => listeners.delete(fn); },
    /** Checks the admin and loads the counts; onLostAccess runs if a later call is refused. */
    async start(client, onLostAccess) {
      sb = client;
      const w = await rpc('admin_whoami');
      me = w.me;
      admins = w.admins || [];
      await refreshCounts();
      onLost = onLostAccess;
    },
    counts: () => ({ openReports, pendingVerifications: 0, unreadAlerts: 0 }),
    me: () => Object.assign({ isYou: true }, me),

    async overview() {
      const o = await rpc('admin_overview');
      o.reports.forEach(labelReport);
      await withCovers(o.reservations, (r) => r.listing);
      return o;
    },

    async users(q) {
      const res = await rpc('admin_users', Object.assign({ p_q: q.q || '', p_filter: q.role || 'ALL' }, paging(q)));
      res.rows.forEach(markYou);
      return res;
    },
    async user(id) {
      const u = await rpc('admin_user', { p_id: id });
      if (u) { markYou(u); u.openReports.forEach(labelReport); }
      return u;
    },

    async listings(q) {
      const res = await rpc('admin_listings', Object.assign({
        p_q: q.q || '', p_status: q.status || 'ALL', p_posted: q.posted || 'ANY', p_donor: q.donorId || null
      }, paging(q)));
      await withCovers(res.rows, (l) => l);
      return res;
    },
    async listing(id) {
      const l = await rpc('admin_listing', { p_id: id });
      if (!l) return null;
      l.reports.forEach(labelReport);
      const urls = await signPaths(l.photoPaths);
      l.photoUrls = l.photoPaths.map((p) => urls[p]).filter(Boolean);
      return l;
    },
    async removeListing(id, reason) {
      const r = await rpc('admin_remove_listing', { p_post: id, p_reason: reason });
      await refreshCountsAfterAction();
      return r;
    },
    async dismissListingReports(id, note) {
      const r = await rpc('admin_dismiss_listing_reports', { p_post: id, p_note: note || null });
      await refreshCountsAfterAction();
      return r;
    },
    markListingReviewed(id, note) { return rpc('admin_mark_listing_reviewed', { p_post: id, p_note: note || null }); },

    async reservations(q) {
      const res = await rpc('admin_reservations', Object.assign({ p_q: q.q || '', p_status: q.status || 'ALL', p_date: q.date || 'ANY' }, paging(q)));
      await withCovers(res.rows, (r) => r.listing);
      return res;
    },
    async reservation(id) {
      const r = await rpc('admin_reservation', { p_id: id });
      if (!r) return null;
      await withCovers([r], (x) => x.listing);
      r.timeline = window.SaluTimeline.build(r);
      return r;
    },

    async reports(q) {
      const res = await rpc('admin_reports', Object.assign({
        p_q: q.q || '', p_status: q.status || 'OPEN', p_target: q.target || 'ALL', p_reason: q.reason || 'ALL'
      }, paging(q)));
      res.rows.forEach(labelReport);
      return res;
    },
    async report(id) {
      const r = await rpc('admin_report', { p_id: id });
      if (!r) return null;
      labelReport(r);
      r.otherReports.forEach(labelReport);
      if (r.targetType === 'LISTING' && r.targetDetail) await withCovers([r.targetDetail], (x) => x);
      return r;
    },
    async dismissReport(id, note) {
      const r = await rpc('admin_dismiss_report', { p_report: id, p_note: note || null });
      await refreshCountsAfterAction();
      return r;
    },
    addReportNote(id, note) { return rpc('admin_add_report_note', { p_report: id, p_note: note }); },

    async audit(q) {
      const code = Object.keys(text.actions).find((k) => text.actions[k] === q.action) || 'ALL';
      const res = await rpc('admin_audit', Object.assign({
        p_q: q.q || '', p_admin: q.adminId && q.adminId !== 'ALL' ? q.adminId : null, p_action: code, p_range: q.range || 'ANY'
      }, paging(q)));
      res.rows.forEach((a) => { a.action = text.actions[a.action] || a.action; markYou(a.admin); });
      return res;
    },
    /** Analytics for the last 4, 8 or 12 weeks (admin_analytics, 20261013). */
    analytics({ weeks }) { return rpc('admin_analytics', { p_weeks: weeks }); },

    /** Announcements (20261014): the form's audience sizes, sending one, the history, one by ID. */
    audienceSizes() { return rpc('admin_audience_sizes'); },
    sendAnnouncement({ title, message, audience }) {
      return rpc('admin_send_announcement', { p_title: title, p_message: message, p_audience: audience });
    },
    async announcements(q) {
      const res = await rpc('admin_announcements', paging(q));
      res.rows.forEach((a) => markYou(a.admin));
      return res;
    },
    async announcement(id) {
      const a = await rpc('admin_announcement', { p_id: id });
      if (a) markYou(a.admin);
      return a;
    },

    auditFilters() {
      return { admins: admins.map((a) => Object.assign({ isYou: a.id === me.id }, a)), actions: Object.values(text.actions) };
    }
  };
})();
