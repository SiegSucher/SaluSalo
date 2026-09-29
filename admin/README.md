# Salu-Salo Admin (web)

A responsive admin dashboard for Salu-Salo: Overview, Users (with verification requests), Listings, Reservations, Reports, Analytics, Notifications, Audit log and Settings. It is a separate website for administrators, not part of the Android app.

**It shows sample data.** Nothing here reads from or writes to Supabase or Firebase. The people, businesses, listings, reservations, reports and numbers are generated examples (`js/sample-data.js`, every email on `example.com`). Their fields and statuses match the real tables. What you do in the dashboard (approve, suspend, remove, send) changes only the open page, and a reload undoes it. The dashboard says so in its top bar ("Sample data"), on Overview, and on the pages where it matters.

## Open it

No build step and no dependencies. Either:

- open `admin/index.html` in a browser, or
- serve the folder, for example `python3 -m http.server 8080` inside `admin/`, then open http://localhost:8080.

Roboto comes from Google Fonts; without a connection the system font is used.

## What's where

| Path | What it holds |
|---|---|
| `index.html` | The page: sidebar, top bar, and the places for panels, dialogs and toasts |
| `css/admin.css` | Design tokens (the app's own colours from `SaluTheme.kt`), components and the three layouts |
| `js/icons.js` | Material Symbols Outlined line icons (Apache 2.0), the family the app's icons come from |
| `js/sample-data.js` | The generated sample (fixed seed, times relative to now) |
| `js/data.js` | The only way pages get data. Each function would become one server call |
| `js/ui.js`, `js/list-view.js`, `js/charts.js` | Shared pieces: badges, tables that become cards on phones, filters, pagination, the details panel, confirmation dialogs, toasts, skeletons, empty and error states, SVG charts with a table view |
| `js/pages/*.js` | One file per page |
| `js/app.js` | Navigation and the address bar (`#/users/U-1001`) |
| `assets/salusalo-logo.webp` | The launcher artwork, copied unchanged from `app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.webp` |

## Screen sizes

- **Desktop (1200 px and wider):** a fixed sidebar that can collapse to icons (the choice is remembered on that browser).
- **Tablet (768–1199 px):** an icon rail that opens over the page; tables scroll sideways when needed, and less important columns are hidden.
- **Phone (under 768 px):** a top bar with a menu button that opens a drawer; tables become cards; filters stack; details open full screen; confirmation dialogs slide up from the bottom.

## Why it can't show real data yet

The app's security rules let an account read only its own rows, and there are no admin-only server functions. The `users.role` column does have an `ADMIN` value, but no policy or function uses it. Showing real data needs new, reviewed server work, which this dashboard deliberately doesn't add. Connecting it later (Phase 6) would mean, at least:

1. **Sign-in with the same Firebase project**, and a check that the caller's own `users` row has `role = 'ADMIN'`, set by hand in SQL; the app never sets roles.
2. **Admin-only `SECURITY DEFINER` functions** that take the caller from the token, refuse anyone who isn't an admin, and return exactly what each page shows. Examples: a paged user list, listings, reservations, reports with their reporter and target, and counts for Overview and Analytics. Never a service-role key in the browser, and never wider RLS on `users`.
3. **New tables or columns** for what doesn't exist yet: document verification requests, account suspension, report statuses beyond `OPEN` and admin notes, announcements (the app has no announcement alert type), and an append-only audit log.
4. **Moderation through functions** that reuse the existing rules. Removing a listing should behave like `remove_my_food_post`: it cancels open reservations and alerts their recipients.

Each function in `js/data.js` names the page data it would return, so the pages shouldn't need to change.

## Notes for the sample

- **Roles:** every account the app creates has `role = RECIPIENT`, and "donor" just means someone who has posted. The sample marks donors as `DONOR` so the role filter can be shown.
- **Report reasons** are the app's own (`model/Review.kt`): unsafe or spoiled food, wrong or misleading details, spam or fake listing, rude or abusive, didn't show up, scam or fraud, spam, other.
- **Items rescued** add up each listing's own unit (pieces, packs, meals, boxes or kilograms), like the app's History totals.
- **Settings → Sample data** can make actions or page loads fail on purpose, to show the error messages, and can reset the sample.
