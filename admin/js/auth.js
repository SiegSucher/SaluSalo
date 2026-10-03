/* Sign-in for admins: Firebase Auth (the app's project), then the admin check on Supabase. */
(function () {
  'use strict';
  const cfg = window.SaluConfig;
  const box = document.getElementById('signin');
  const form = document.getElementById('signin-form');
  const msg = document.getElementById('signin-msg');
  const submit = document.getElementById('signin-submit');
  const google = document.getElementById('signin-google');
  const appEl = document.getElementById('app');
  const LOST = 'salusalo-admin:signin-message';

  function setBusy(on) {
    submit.disabled = on;
    google.disabled = on;
    submit.textContent = on ? 'Signing in…' : 'Sign in';
  }
  function show(message) {
    appEl.hidden = true;
    box.hidden = false;
    msg.textContent = message || '';
    msg.hidden = !message;
    setBusy(false);
  }
  function remember(message) { try { sessionStorage.setItem(LOST, message); } catch (e) { /* storage blocked */ } }
  function recall() {
    try { const m = sessionStorage.getItem(LOST); sessionStorage.removeItem(LOST); return m; } catch (e) { return null; }
  }

  if (!cfg || !cfg.supabaseUrl || !cfg.supabaseKey || !cfg.firebase || /YOUR-|FROM-/.test(JSON.stringify(cfg))) {
    show('This copy of the dashboard has no settings yet (js/config.js). See admin/README.md.');
    form.querySelectorAll('input, button').forEach((el) => { el.disabled = true; });
    return;
  }

  firebase.initializeApp(cfg.firebase);
  const auth = firebase.auth();
  const sb = window.supabase.createClient(cfg.supabaseUrl, cfg.supabaseKey, {
    accessToken: async () => (auth.currentUser ? auth.currentUser.getIdToken() : null)
  });
  let started = false;

  window.SaluAuth = { signOut: () => auth.signOut().then(() => { location.hash = ''; location.reload(); }) };

  function authError(e) {
    const c = e && e.code;
    if (c === 'auth/popup-closed-by-user' || c === 'auth/cancelled-popup-request') return '';
    if (c === 'auth/invalid-credential' || c === 'auth/wrong-password' || c === 'auth/user-not-found' || c === 'auth/invalid-email') return 'Wrong email or password.';
    if (c === 'auth/too-many-requests') return 'Too many attempts. Try again in a few minutes.';
    if (c === 'auth/network-request-failed') return 'Couldn’t reach the server. Check your connection and try again.';
    if (c === 'auth/user-disabled') return 'This account is disabled.';
    return 'Couldn’t sign in. Please try again.';
  }

  form.addEventListener('submit', async (ev) => {
    ev.preventDefault();
    const email = document.getElementById('signin-email').value.trim();
    const password = document.getElementById('signin-password').value;
    if (!email || !password) { show('Enter your email and password.'); return; }
    setBusy(true);
    try { await auth.signInWithEmailAndPassword(email, password); } catch (e) { show(authError(e)); }
  });
  google.addEventListener('click', async () => {
    setBusy(true);
    const provider = new firebase.auth.GoogleAuthProvider();
    try {
      await auth.signInWithPopup(provider);
    } catch (e) {
      if (e && e.code === 'auth/popup-blocked') { await auth.signInWithRedirect(provider); return; }
      show(authError(e));
    }
  });
  auth.getRedirectResult().catch((e) => show(authError(e)));

  auth.onAuthStateChanged(async (user) => {
    if (!user) { show(recall()); return; }
    if (started) return;
    setBusy(true);
    try {
      await window.SaluData.start(sb, () => {
        remember('Your admin access ended. Please sign in again.');
        window.SaluAuth.signOut();
      });
    } catch (e) {
      if (e instanceof window.SaluData.AccessError) {
        remember('This account doesn’t have admin access.');
        await auth.signOut(); // onAuthStateChanged(null) shows the message
        return;
      }
      show('Couldn’t reach Salu-Salo. Check your connection, then reload the page.');
      return;
    }
    started = true;
    box.hidden = true;
    appEl.hidden = false;
    window.App.start();
  });
})();
