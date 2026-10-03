# Salu-Salo Admin (web)

A responsive admin dashboard for Salu-Salo: Overview, Users, Listings, Reservations, Reports, Analytics, Notifications, Audit log and Settings. It is a separate website for administrators, not part of the Android app.

**It shows the live Salu-Salo data** to admins only. Every request goes to an admin-only Supabase function (`supabase/migrations/20261012_admin_dashboard.sql`) that checks the caller's Firebase token and `users.role = 'ADMIN'`; the page holds only the publishable key. What works: Overview, Users, Listings (with photos), Reservations, Reports (dismiss, notes, remove the listing), Analytics (`supabase/migrations/20261013_admin_analytics.sql`), Notifications (announcements, `supabase/migrations/20261014_admin_announcements.sql`; the app build from PROJECT_STATUS.md §27 must be on every phone first), Audit log, Settings (Log out). Not connected yet: verification requests, suspending accounts.

## One-time setup

1. Run `supabase/migrations/20261012_admin_dashboard.sql` in the Supabase SQL Editor, then `supabase/tests/admin_dashboard_check.sql` and `supabase/tests/admin_dashboard_live_safety_check.sql` (see `supabase/README.md`).
2. Make your account an admin (it must have signed into the app once): `update public.users set role = 'ADMIN' where email = 'you@example.com';`
3. Firebase console → Project settings → Your apps → Add app → Web (no Firebase Hosting setup needed there). Copy `apiKey` and `appId`.
4. Copy `admin/js/config.example.js` to `admin/js/config.js` (git-ignored) and fill in the Supabase URL and publishable key (the same values as `local.properties`) and the Firebase values. Keep `authDomain: 'salusalo.firebaseapp.com'` (Google sign-in's registered return address; `salusalo.web.app` would need an extra redirect URI in Google Cloud).
5. Authentication → Settings → Authorized domains: `salusalo.web.app` and `localhost` are there by default; keep them.

## Try it on this computer

`npx http-server admin -p 8080 -c-1`, then open http://localhost:8080 and sign in.

## Put it online (open it on your phone)

`npx firebase-tools login` once, then `npx firebase-tools deploy --only hosting` from the repo root. Open https://salusalo.web.app on the phone; in the browser menu choose "Add to Home screen" to open it like an app. Deploy again after every change. Undo a deploy in the Firebase console → Hosting → release history.

## Admins

Give: `update public.users set role = 'ADMIN' where email = '…';` Take back: `… set role = 'RECIPIENT' …`. A signed-in dashboard notices on its next request and signs out. Every admin action is in the Audit log; nobody can edit or delete an entry.

## What's where

| Path | What it holds |
|---|---|
| `index.html` | The page: sidebar, top bar, and the places for panels, dialogs and toasts |
| `css/admin.css` | Design tokens (the app's own colours from `SaluTheme.kt`), components and the three layouts |
| `js/icons.js` | Material Symbols Outlined line icons (Apache 2.0), the family the app's icons come from |
| `js/config.example.js` | The shape of `js/config.js` (git-ignored): Supabase URL and publishable key, Firebase web config |
| `js/auth.js` | The Sign In screen: Firebase Auth, the Supabase client, the admin check, Log out |
| `js/timeline.js` | A reservation's timeline steps |
| `tests/*.test.js` | Node checks for the timeline, the sign-in messages and the data layer: `for t in admin/tests/*.test.js; do node $t; done` |
| `js/data.js` | The only way pages get data: one admin function per call, photos signed for 1 hour |
| `js/ui.js`, `js/list-view.js`, `js/charts.js` | Shared pieces: badges, tables that become cards on phones, filters, pagination, the details panel, confirmation dialogs, toasts, skeletons, empty and error states, SVG charts with a table view |
| `js/pages/*.js` | One file per page |
| `js/app.js` | Navigation and the address bar (`#/listings/<id>`) |
| `assets/salusalo-logo-2.webp` | The app's logo, a copy of `app/src/main/res/drawable-nodpi/salu_logo.webp` (renamed from `salusalo-logo.webp` with the new logo, so browsers don't keep the old one) |

## Screen sizes

- **Desktop (1200 px and wider):** a fixed sidebar that can collapse to icons (the choice is remembered on that browser).
- **Tablet (768–1199 px):** an icon rail that opens over the page; tables scroll sideways when needed, and less important columns are hidden.
- **Phone (under 768 px):** a top bar with a menu button that opens a drawer; tables become cards; filters stack; details open full screen; confirmation dialogs slide up from the bottom.
