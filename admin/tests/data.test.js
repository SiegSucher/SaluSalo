// Run: node admin/tests/data.test.js
// Loads js/data.js with a stand-in Supabase client.
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const src = fs.readFileSync(path.join(__dirname, '..', 'js', 'data.js'), 'utf8');

function load(answers) {
  const window = { SaluTimeline: { build: () => [] } };
  vm.runInNewContext(src, { window, console });
  const calls = [];
  const sb = {
    rpc: async (name, args) => { calls.push(name); return answers[name](args); },
    storage: { from: () => ({ createSignedUrls: async () => ({ data: [], error: null }) }) }
  };
  return { data: window.SaluData, sb, calls };
}

(async () => {
  // An action that worked is reported as done even when reloading the counts afterwards fails.
  {
    let countsFail = false;
    const t = load({
      admin_whoami: () => ({ data: { me: { id: 'a1', name: 'Ada', email: 'ada@example.com' }, admins: [] }, error: null }),
      admin_counts: () => (countsFail ? { data: null, error: { code: 'XX000', message: 'network' } } : { data: { openReports: 2 }, error: null }),
      admin_remove_listing: () => ({ data: { cancelled: 1, closedReports: 1 }, error: null }),
      admin_dismiss_listing_reports: () => ({ data: { dismissed: 1 }, error: null }),
      admin_dismiss_report: () => ({ data: { dismissed: 1 }, error: null }),
      admin_suspend_user: (args) => ({ data: { removedListings: 1, cancelledReservations: 1, closedReports: 2, args }, error: null }),
      admin_restore_user: (args) => ({ data: { restored: true, args }, error: null })
    });
    await t.data.start(t.sb, () => {});
    assert.strictEqual(t.data.counts().openReports, 2);
    countsFail = true;
    assert.deepStrictEqual(await t.data.removeListing('l1', 'Spoiled'), { cancelled: 1, closedReports: 1 });
    assert.deepStrictEqual(await t.data.dismissListingReports('l1', null), { dismissed: 1 });
    assert.deepStrictEqual(await t.data.dismissReport('r1', null), { dismissed: 1 });
    // Suspend and restore send exactly these arguments and also survive a failed count reload.
    const sus = await t.data.suspendUser('u1', 'NO_SHOWS', 'Test note');
    assert.deepStrictEqual(JSON.parse(JSON.stringify(sus.args)), { p_user: 'u1', p_reason: 'NO_SHOWS', p_note: 'Test note' }); // built inside the sandbox
    assert.strictEqual(sus.removedListings, 1);
    const res = await t.data.restoreUser('u1', '');
    assert.deepStrictEqual(JSON.parse(JSON.stringify(res.args)), { p_user: 'u1', p_note: null });
    assert.strictEqual(t.data.text.suspension.OTHER, 'Breaking the community rules');
    assert.strictEqual(t.data.text.actions.USER_SUSPENDED, 'Account suspended');
  }

  // A refused action still fails, with the server's own short text.
  {
    const t = load({
      admin_whoami: () => ({ data: { me: { id: 'a1', name: 'Ada', email: 'ada@example.com' }, admins: [] }, error: null }),
      admin_counts: () => ({ data: { openReports: 0 }, error: null }),
      admin_remove_listing: () => ({ data: null, error: { code: 'P0001', message: 'This listing was already removed.' } }),
      admin_suspend_user: () => ({ data: null, error: { code: 'P0001', message: 'Admins can’t be suspended.' } })
    });
    await t.data.start(t.sb, () => {});
    await assert.rejects(t.data.removeListing('l1', 'Again'), (e) => e instanceof t.data.DataError && e.message === 'This listing was already removed.');
    await assert.rejects(t.data.suspendUser('u2', 'SPAM', 'x'), (e) => e instanceof t.data.DataError && e.code === 'P0001');
  }

  // Losing admin access on any call signs the page out.
  {
    let lost = 0;
    const t = load({
      admin_whoami: () => ({ data: { me: { id: 'a1', name: 'Ada', email: 'ada@example.com' }, admins: [] }, error: null }),
      admin_counts: () => ({ data: { openReports: 0 }, error: null }),
      admin_overview: () => ({ data: null, error: { code: '42501', message: 'Admin access required' } })
    });
    await t.data.start(t.sb, () => { lost++; });
    await assert.rejects(t.data.overview(), (e) => e instanceof t.data.AccessError);
    assert.strictEqual(lost, 1);
  }

  // Analytics asks the server for the chosen number of weeks and hands its answer to the page.
  {
    const answer = { weekStarts: [1, 2, 3, 4], kpis: { listings: 3 }, previous: null, series: {}, statusCounts: {} };
    let asked = null;
    const t = load({
      admin_whoami: () => ({ data: { me: { id: 'a1', name: 'Ada', email: 'ada@example.com' }, admins: [] }, error: null }),
      admin_counts: () => ({ data: { openReports: 0 }, error: null }),
      admin_analytics: (args) => { asked = args; return { data: answer, error: null }; }
    });
    await t.data.start(t.sb, () => {});
    assert.deepStrictEqual(await t.data.analytics({ weeks: 4 }), answer);
    assert.deepStrictEqual(JSON.parse(JSON.stringify(asked)), { p_weeks: 4 }); // built inside the sandbox
  }

  // Announcements: sending passes the form's fields; the history and one announcement mark the admin's own rows.
  {
    let asked = null;
    const t = load({
      admin_whoami: () => ({ data: { me: { id: 'a1', name: 'Ada', email: 'ada@example.com' }, admins: [] }, error: null }),
      admin_counts: () => ({ data: { openReports: 0 }, error: null }),
      admin_audience_sizes: () => ({ data: { ALL: 3, DONORS: 1, RECIPIENTS: 1 }, error: null }),
      admin_send_announcement: (args) => { asked = args; return { data: { id: 'n1', reach: 1 }, error: null }; },
      admin_announcements: () => ({ data: { rows: [{ id: 'n1', admin: { id: 'a1', name: 'Ada' } }], total: 1, page: 1, pageSize: 10, pages: 1 }, error: null }),
      admin_announcement: () => ({ data: { id: 'n1', admin: { id: 'b2', name: 'Ben' } }, error: null })
    });
    await t.data.start(t.sb, () => {});
    assert.deepStrictEqual(await t.data.audienceSizes(), { ALL: 3, DONORS: 1, RECIPIENTS: 1 });
    assert.deepStrictEqual(await t.data.sendAnnouncement({ title: 'Hi', message: 'Hello', audience: 'DONORS' }), { id: 'n1', reach: 1 });
    assert.deepStrictEqual(JSON.parse(JSON.stringify(asked)), { p_title: 'Hi', p_message: 'Hello', p_audience: 'DONORS' });
    assert.strictEqual((await t.data.announcements({ page: 1, pageSize: 10 })).rows[0].admin.isYou, true);
    assert.strictEqual((await t.data.announcement('n1')).admin.isYou, undefined);
    assert.strictEqual(t.data.text.audience.RECIPIENTS, 'Recipients');
    assert.strictEqual(t.data.text.actions.ANNOUNCEMENT_SENT, 'Sent announcement');
  }

  // A refusal keeps the server's code, so the page can show the server's own text (the rate limit).
  {
    const limit = 'You can send up to 3 announcements every 10 minutes. Please wait a few minutes.';
    const t = load({
      admin_whoami: () => ({ data: { me: { id: 'a1', name: 'Ada', email: 'ada@example.com' }, admins: [] }, error: null }),
      admin_counts: () => ({ data: { openReports: 0 }, error: null }),
      admin_send_announcement: () => ({ data: null, error: { code: 'P0001', message: limit } })
    });
    await t.data.start(t.sb, () => {});
    await assert.rejects(t.data.sendAnnouncement({ title: 'Hi', message: 'Hello', audience: 'ALL' }),
      (e) => e instanceof t.data.DataError && e.code === 'P0001' && e.message === limit);
  }

  console.log('data: all checks passed');
})().catch((e) => { console.error(e); process.exit(1); });
