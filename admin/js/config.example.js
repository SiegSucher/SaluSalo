/*
 * Copy to config.js (git-ignored) and fill in. These values are public in any web
 * app; security comes from the admin check in every Supabase function. See README.md.
 */
window.SaluConfig = {
  supabaseUrl: 'https://YOUR-PROJECT.supabase.co',
  supabaseKey: 'YOUR-PUBLISHABLE-KEY',
  firebase: {
    apiKey: 'FROM-FIREBASE-CONSOLE',
    authDomain: 'salusalo.firebaseapp.com',
    projectId: 'salusalo',
    appId: 'FROM-FIREBASE-CONSOLE'
  }
};
