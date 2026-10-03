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
      admin_dismiss_report: () => ({ data: { dismissed: 1 }, error: null })
    });
    await t.data.start(t.sb, () => {});
    assert.strictEqual(t.data.counts().openReports, 2);
    countsFail = true;
    assert.deepStrictEqual(await t.data.removeListing('l1', 'Spoiled'), { cancelled: 1, closedReports: 1 });
    assert.deepStrictEqual(await t.data.dismissListingReports('l1', null), { dismissed: 1 });
    assert.deepStrictEqual(await t.data.dismissReport('r1', null), { dismissed: 1 });
  }

  // A refused action still fails, with the server's own short text.
  {
    const t = load({
      admin_whoami: () => ({ data: { me: { id: 'a1', name: 'Ada', email: 'ada@example.com' }, admins: [] }, error: null }),
      admin_counts: () => ({ data: { openReports: 0 }, error: null }),
      admin_remove_listing: () => ({ data: null, error: { code: 'P0001', message: 'This listing was already removed.' } })
    });
    await t.data.start(t.sb, () => {});
    await assert.rejects(t.data.removeListing('l1', 'Again'), (e) => e instanceof t.data.DataError && e.message === 'This listing was already removed.');
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

  console.log('data: all checks passed');
})().catch((e) => { console.error(e); process.exit(1); });
