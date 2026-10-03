// Run: node admin/tests/auth.test.js
// Loads js/auth.js with stand-ins for the page, Firebase and SaluData, and checks the
// messages Sign In shows after the dashboard signs someone out.
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const src = fs.readFileSync(path.join(__dirname, '..', 'js', 'auth.js'), 'utf8');
const ticks = async () => { for (let i = 0; i < 20; i++) await Promise.resolve(); };

function storage() {
  const m = new Map();
  return { getItem: (k) => (m.has(k) ? m.get(k) : null), setItem: (k, v) => m.set(k, String(v)), removeItem: (k) => m.delete(k) };
}

/** One page load. Like Firebase, signOut() tells the observer before its promise resolves. */
function load(store, user, start) {
  const els = {};
  const get = (id) => (els[id] = els[id] || { hidden: false, textContent: '', disabled: false, value: '', addEventListener() {}, querySelectorAll: () => [] });
  const page = { navigated: false, appStarted: false, startCalls: 0 };
  let observer = null;
  const auth = {
    currentUser: user,
    onAuthStateChanged(cb) { observer = cb; Promise.resolve().then(() => cb(auth.currentUser)); },
    getRedirectResult: () => Promise.resolve(null),
    signOut() { auth.currentUser = null; Promise.resolve().then(() => observer(null)); return Promise.resolve(); }
  };
  class AccessError extends Error {}
  const window = {
    SaluConfig: { supabaseUrl: 'https://x.supabase.co', supabaseKey: 'k', firebase: { apiKey: 'a', authDomain: 'salusalo.firebaseapp.com', projectId: 'salusalo', appId: 'i' } },
    supabase: { createClient: () => ({}) },
    SaluData: { AccessError, start: (sb, onLost) => { page.startCalls++; return start(onLost, AccessError); } },
    App: { start() { page.appStarted = true; } }
  };
  const firebase = { initializeApp() {}, auth: Object.assign(() => auth, { GoogleAuthProvider: class {} }) };
  const location = { pathname: '/', search: '', hash: '#/settings', reload() { page.navigated = true; }, replace() { page.navigated = true; } };
  vm.runInNewContext(src, { window, document: { getElementById: get }, sessionStorage: store, location, firebase });
  page.msg = () => get('signin-msg');
  return page;
}

(async () => {
  // A non-admin is refused on sign-in and told why.
  {
    const store = storage();
    const p = load(store, { uid: 'u' }, (onLost, AccessError) => Promise.reject(new AccessError('Admin access required')));
    await ticks();
    assert.strictEqual(p.appStarted, false);
    assert.strictEqual(p.msg().textContent, 'This account doesn’t have admin access.');
  }

  // An admin whose access is taken back while the page is open sees why after the reload.
  {
    const store = storage();
    let lost = null;
    const p = load(store, { uid: 'u' }, (onLost) => { lost = onLost; return Promise.resolve(); });
    await ticks();
    assert.strictEqual(p.appStarted, true);
    lost(new Error('Admin access required'));
    await ticks();
    assert.strictEqual(p.navigated, true, 'the page reloads after signing out');
    const after = load(store, null, () => Promise.resolve());
    await ticks();
    assert.strictEqual(after.msg().textContent, 'Your admin access ended. Please sign in again.');
    assert.strictEqual(after.msg().hidden, false);
  }

  console.log('auth: all checks passed');
})().catch((e) => { console.error(e); process.exit(1); });
