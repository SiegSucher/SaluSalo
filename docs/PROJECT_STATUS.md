# Salu-Salo — Project Status

_Last updated: 2026-10-07. Phases 1–4 are complete and both parts of Phase 5 passed on two phones; all of them are merged into `master`, together with the demo fixes (§9–§13), the Profile redesign (§16), the clean-ups after the second audit (§17), the automatic Supabase role (§18, PR #13), the UI/UX refresh (§19, PRs #14 and #15) and a web admin dashboard with sample data (§20, PR #15). The bug fix pass after the full audit of 2026-09-29 (§21) passed a one-phone test and was merged into `master` through PR #17 on 2026-09-30 (`8ed8eef`); its three migrations are applied to the live project. A second round (§22: the rest of the 34 findings) passed a one-phone test and was merged into `master` through PR #18 on 2026-09-30 (`65d5f6a`); its three migrations are applied too (live checks 5/5, 5/5, 3/3). A third round (§23: what the verification audit of 2026-09-30 found still open; no SQL) passed a one-phone test and two-phone checks and was merged through PR #19 on 2026-10-03 (`10270e7`). Reservation limits and pickup deadlines (§24, Mika's idea from PR #16, rebuilt) passed a two-phone test and were merged through PR #20 on 2026-10-03 (`a3dcd98`); its migration is applied and its Cron job runs. Phase 6 has started: its first slice, the admin dashboard on real data (§25), was built on branch `claude/admin-real`, its migration applied to the live project (safety check 20/20), deployed to https://salusalo.web.app and tested on the PC and the phone on 2026-10-03, and merged into `master` through PR #23 (merge commit `a60b315`). Analytics (§26) applied, deployed, tested on the phone and merged through PR #24 (`c240f48`, 2026-10-03). Announcements (§27) on branch `claude/admin-announcements`: migration applied (live safety check 14/14), deployed, and the one-phone test passed (2026-10-04); merged into `master` through PR #25 (`f959f0e`). Mika's chat wallpaper (§28, PR #22) was merged on 2026-10-04 (`6f1223c`). The new logo and the loading screen on every app open (§29) passed a one-phone test and were merged through PR #26 on 2026-10-04 (`7369654`). A second full bug audit on 2026-10-04 found nothing critical or high; a fourth bug fix round (§30, #36–#42) was built on branch `claude/bugfix-pass-4`, its migration applied to the live project and its one-phone test passed (2026-10-04); it was merged into `master` through PR #27 (merge commit `a5f9a2c`). On-device recommendations (§31: "Recommended for you" on Home, a small logistic-regression model trained on the phone from the account's own reservations and saves) were built with AGP (unit tests 86/86, lint 0 errors), passed a one-phone test and were merged into `master` through PR #29 on 2026-10-04 (merge commit `b15cb2a`). My allergies (§32, a friend's suggestion: donors pick allergens from a list, and Home hides listings whose donor picked one of the user's allergies, with Show) is on branch `claude/allergy-filter`: built with AGP (unit tests 91/91, lint 0 errors) and passed its one-phone test on 2026-10-06; merged into `master` through PR #30 (`ed07da9`). Keyboard and map fixes (§33, the friend's other two suggestions: the bottom bar hides while the keyboard is open, "Use my location" fills in an empty pickup location, a full-screen map on Post and Edit, plus a "saved" snackbar on the Profile pages) passed a one-phone test on 2026-10-06 and were merged through PR #31 (`9568264`). The app is portrait only (§34, branch `claude/portrait-only`): built; not tested on a phone yet. Push notifications and staying signed in (§35, branch `claude/push-notifications`, which also carries §34): every alert also pops up as a phone notification, even with the app closed, and an account stays signed in until Log out ("Keep me signed in" removed); built and checked locally (unit tests 104/104, lint 0 errors, Deno 33/33, SQL check 10/10 on a local PostgreSQL); the migration, the `send-push` setup and the phone test are pending. [Current state](#current-state-2026-10-05) summarizes the architecture and [Current known issues](#current-known-issues) what is still open; the numbered sections are the history of each phase, with later changes marked. Manual test results are in the [results log](#results-log)._

This is the handoff status document. It was first written from static inspection of the Kotlin code and the Supabase SQL at `d4f65fe`, plus build checks (see [Build verification](#build-verification)). Manual results are recorded only as reported by the tester. Keep it up to date, and never mark an item fixed or tested unless it actually was.

**Legend**
- **Implemented (code):** present in the code at `d4f65fe`, confirmed by reading it.
- **Manually tested:** a recorded run on a device or emulator, listed in the [results log](#results-log). The first results were reported on 2026-09-25; anything not listed there is still unverified.
- **Not yet verified:** not confirmed by a build, a manual run, or both.

Unless stated otherwise, Kotlin paths are relative to `app/src/main/java/com/example/salusalo/`. Line numbers (`L…`) refer to `d4f65fe` and will drift as code changes.

---

## 1. Project status

| Phase | Scope | Status |
|---|---|---|
| Phase 1: Backend Foundation | Firebase email/password auth, Supabase client using Firebase tokens, `users` profiles, private Storage buckets | **Complete.** Do not redo. Authentication/profile regression passed on 2026-09-25 ([results log](#results-log)). |
| Phase 2: Food Listings | `food_posts`, create listing with image upload to `food-images`, feed, My Listings, delete | **Complete.** Do not redo. Regression passed on 2026-09-25 for both accounts: Home/feed, create listing, delete listing ([results log](#results-log)). |
| Phase 3: Reservations | Reserve, cancel and claim with atomic quantity; recipient and donor views | **Complete.** Signed off by the developer on 2026-09-25. The A/B reservation test, the missing-image fallback/feed check, and the cancel, claim and Recommended for You tests passed; the remaining checks without an individual test record are listed under the [results log](#results-log). The changes since `d4f65fe` were committed on 2026-09-25 (§6). The known-issue fixes made after sign-off (§3.5) were built and manually tested on 2026-09-25. |
| Phase 4: Messaging and Notifications | Chat per listing (recipient ↔ donor), in-app alerts for messages and reservation changes, live updates through Supabase Realtime, notification preferences stored in Supabase (§8) | **Complete.** Signed off by the developer on 2026-09-25. Migration applied to the live project. At `266325b` the one-phone A/B test and regression passed, except saving a preference switched back on. At `c7323b8` the two preference fixes and all the live two-phone checks passed, including live alerts after more than 1 hour, and a new account signed up. The live safety check (negative tests) passed 21 of 21. Not tested: the password-reset email, whose code Phase 4 did not change (§8). |
| Activity: Purchases, Claims, listing prices (between Phases 4 and 5; not part of Phase 5) | Stored listing prices, the price locked into each reservation, real Purchases and Claims tabs, real Activity totals (§9) | **Merged into `master` through PR #3 on 2026-09-26, together with the Phase 1 demo fixes, before its own device test. Built with AGP and migration applied to the live project on 2026-09-26. The §9.4 two-phone test passed on 2026-09-26, except the "Reserved" dialog after Reserve, which never appears: a Phase 3 bug present since `d4f65fe`, left for the Phase 3 demo fixes (§9.6).** |
| Phase 1 demo fixes: accounts, profile, security (fixes to finished Phase 1 features; not part of Phase 5) | Google sign-in, a working "Keep me signed in", honest sign-in texts, profile name and photo saved to Supabase, real password change, real Member since; location, Privacy and Signed-in devices removed (§10) | **Complete.** Merged into `master` through PR #3 on 2026-09-26; built with AGP (`googleid` 1.2.0, new `google-services.json`) and migration applied the same day. The developer ran the Phase 1 checks on two phones on 2026-09-26 and reported that they passed ([results log](#results-log)). "Keep me signed in" starting unchecked was changed after PR #3; the tested build included it (§10). |
| Phase 2 demo fixes: listings (fixes to finished Phase 2 features; not part of Phase 5) | Real donor names, no ratings or distances, newest-first feed, the listing's own pickup address, pickup window, handover, allergens and storage notes saved and shown, up to 4 photos, Edit listing, real Saved Listings, "Expired" from the expiry date (§11) | **Passed.** Built with AGP on branch `claude/phase2-demo-fixes` at `4b6aac6` (PR #5), and migration `20260929_phase2_listing_details.sql` applied to the live project on 2026-09-26. The §11.4 two-phone test (steps 1–25) and its regression round (steps 26–39) passed the same day ([results log](#results-log), §11.6). New small issues are listed in §11.5. |
| Phase 3 demo fixes: reservations, Activity, Profile (fixes to finished Phase 3 features; not part of Phase 5) | Reserve review page and receipt, pickup questions and notes through the chat, deleting reserved listings by removing them, real Activity records, real Profile claims and donations, Phase 2 leftovers (§12) | **Passed.** Built with AGP on branch `claude/phase3-demo-fixes` at `24fe9f2`. Migration `20260930_phase3_demo_fixes.sql` applied to the live project on 2026-09-26; its read-only check passed 13 of 13 and the live safety check 14 of 14. The two-phone test (§12.4, 32 steps) passed on 2026-09-27 ([results log](#results-log), §12.6). Merged into `master` through PR #7 on 2026-09-27 (merge commit `c184d9b`). |
| Phase 4 demo fixes: messages, alerts, Profile (fixes to finished Phase 4 features; not part of Phase 5) | The Profile bell opens Alerts and the gear opens Settings, the unused Food recommendations and Pickup reminders switches and Compact preview removed, a real notification sound, a real response rate, a live Messages inbox and a chat badge that counts unread messages (§13) | **Passed.** Written on branch `claude/phase4-demo-fixes` (from `master` at `c184d9b`) on 2026-09-27 and built with AGP at `3288d02`. Migration `20261001_phase4_demo_fixes.sql` applied to the live project on 2026-09-27; its read-only check passed 9 of 9. A short two-phone test (§13.4, 13 steps) passed the same day ([results log](#results-log), §13.6); the checks it left out are listed in §13.6. Merged into `master` through PR #8 on 2026-09-27 (merge commit `39b57b5`). |
| Phase 5: Maps, Reviews, Reports | Split by the developer into part 1 (ratings and reviews, reports, and an "Email verified" badge moved here from Phase 6; §14) and part 2 (Maps) | **Part 1: passed, and merged into `master` through PR #9 on 2026-09-28 (merge commit `711cc5e`).** Written on branch `claude/phase5-reviews-reports-v6ahqn` (from `master` at `39b57b5`) on 2026-09-27 and built with AGP at `3bd038c`. Migration `20261002_phase5_reviews_reports.sql` applied to the live project on 2026-09-27; its read-only check passed 18 of 18 and the live safety check 27 of 27. The two-phone test (§14.4, 15 steps) passed on 2026-09-28 ([results log](#results-log), §14.6). **Part 2 (Maps): passed, and merged into `master` through PR #11 on 2026-09-28 (merge commit `255b0f8`).** Written on branch `claude/phase5-maps` (from `master` at `711cc5e`) on 2026-09-28 and built with AGP at `47ed894`. Migration `20261003_phase5_maps.sql` applied to the live project on 2026-09-28; its read-only check passed 11 of 11 and the live safety check 22 of 22. The two-phone test (§15.4, 12 steps) passed on 2026-09-28 ([results log](#results-log), §15.6). |
| Fixes after the second demo audit (not part of the six phases) | A neutral photo tile, "Check your listing", "My posts" times, filter help, Read more, Learn more, welcome texts; Mika's Start now, required allergens, profile circle, full-screen photos, summary columns and keyboard fix; dead code, unused images and icons; one signing request per list, reservations by ID, WebP; welcome pages only on the first start and pull to refresh (§17) | **Passed, and merged into `master` through PR #12 on 2026-09-28 (merge commit `12e931c`).** Written on branch `claude/cleanup-after-audit` (from `master` at `49ac319`) on 2026-09-28 and built with AGP at `e0f2d59` (unit tests 26 of 26, lint 0 errors). No SQL. The 9-step two-phone test (§17.4) passed on 2026-09-28 ([results log](#results-log), §17.6). |
| Automatic Supabase role for new accounts (not part of the six phases) | The `set-firebase-role` Edge Function gives a new account the `role: authenticated` claim, called by the app when the token lacks it; replaces running the developer's script after each sign-up (§18) | **Passed, and merged into `master` through PR #13 on 2026-09-28 (merge commit `b8aa7f9`).** Written on branch `claude/auto-role-claim` (from `master` at `12e931c`) on 2026-09-28 and built with AGP at `9126799` (unit tests 34 of 34). The function was deployed and set up on 2026-09-28 (§18.2), and the 7-step two-phone test (§18.5) passed the same day ([results log](#results-log), §18.7). No SQL. |
| UI/UX refresh (not part of the six phases) | One design system (`SaluTheme.kt`) with Salu-Salo's original colours, type scale and shared components; a loading screen with the original logo at start; the bottom bar in its original order Home → Alerts → Post → Activity → Profile (Post in the middle), with slide transitions and swipes between tabs; restyled Welcome, Sign In, Home, food details, Post and Edit, Alerts, Messages and chat, Profile and Settings, with skeletons and empty states; Activity rebuilt from the developer's mockups as three tabs (My Listings, My Reservations, History) with Manage reservations, reservation and pickup pages. No SQL and no change to repositories, ViewModels or server logic (§19) | **Merged into `master` through PR #14 (`f27ee47`) and PR #15 (`fa95fff`) on 2026-09-29.** No separate test record; its code is part of the §21 build, which was built with AGP and passed a one-phone test on 2026-09-30. Branch `claude/focused-mayer-0aevp8` (from `master` at `b8aa7f9`), 2026-09-28. A kotlinc type check (0 errors) and the JVM tests (43 of 43) ran in the session (§19.2). |
| Admin dashboard, web (not part of the six phases; a first look at Phase 6's admin screen) | A separate responsive website in `admin/`: Overview, Users and verification requests, Listings, Reservations, Reports, Analytics, Notifications, Audit log and Settings, in the app's colours and with its logo. Sample data only (§20) | **Merged into `master` through PR #15 on 2026-09-29; sample data, not connected.** Replaced by §25 (real data) on branch `claude/admin-real`, once that is merged. Branch `claude/focused-mayer-0aevp8`, 2026-09-29. Checked in headless Chromium at phone, tablet and desktop sizes (§20.3). No change to the app, SQL, Supabase or Firebase. |
| Bug fix pass after the full bug audit (not part of the six phases) | Groups A–E of the 2026-09-29 audit: #1 cancel/claim without a profile, #10 photo reads, #11 listing checks, #2–#6 Home refresh, failure states, photo URLs and post retries, #8 price, #9 release, #23 pickup end, #7 rotation, #14 alerts on resume, #15 re-created accounts, #21 ended sessions, #17–#20 and #22 (§21) | **Passed on one phone (2026-09-30); committed as `5429e6e`, merged into `master` through PR #17 on 2026-09-30 (`8ed8eef`).** Built and unit-tested (51 of 51), migrations applied. Branch `claude/bugfix-pass-1` (from `master` at `fa95fff`), 2026-09-29. Three new migrations (`20261005`–`20261007`), dry-run on a local PostgreSQL 16 (§21.3) and applied to the live project on 2026-09-30; live safety checks 22 of 23 (the one failure was the check itself, now corrected, §21), 15 of 15, 9 of 9. |
| Bug fix pass, round 2 (not part of the six phases) | The rest of the 34 findings: #12 one rating per reviewer, #13 friendly errors, #16 photo cache keys, #24 bucket limits and an orphan list, #25 rate limits, #26 bookmark race, #27 photo sampling, #29 fewer requests, #30 backup rules, #31 release config, #32 `anon` grants, #34 clean-ups; #28 deferred (§22) | **Passed on one phone (2026-09-30); committed as `a2412eb` (docs `2562de6`), merged into `master` through PR #18 on 2026-09-30 (`65d5f6a`).** Built and unit-tested (54 of 54), migrations applied. Branch `claude/bugfix-pass-2` (from `claude/bugfix-pass-1` at `5429e6e`), 2026-09-30. Three new migrations (`20261008`–`20261010`), dry-run on a local PostgreSQL 16 (§22.4) and applied to the live project on 2026-09-30; live checks 5 of 5, 5 of 5, 3 of 3. |
| Bug fix pass, round 3 (not part of the six phases) | What the verification audit of 2026-09-30 found still open: #35 sign-in lost to a rotation, #17 listing pages when reservations fail, #22 details after a listing leaves the feed, #7 pages closed by rotation, #4 Post's My posts, #18 feed reload after cancel, #14 Realtime reconnects, #2 feed every minute, R1 alert-settings errors, #29 and #34 leftovers, #33 docs (§23) | **Passed on one phone and two-phone checks (2026-10-03); merged into `master` through PR #19 (`10270e7`).** Built and unit-tested (57 of 57). Branch `claude/bugfix-pass-3` (from `master` at `65d5f6a`). No SQL. |
| Reservation limits and pickup deadlines (Mika's idea from PR #16, rebuilt; not part of the six phases) | A per-person limit set on Post; a pickup time chosen when reserving (15 min to 2 h); reservations not picked up in time expire by themselves through a Supabase Cron job, with alerts; countdowns and "Expired" in Activity (§24) | **Built and unit-tested (68 of 68), lint 0 errors, on branch `claude/reservation-limits` (from `master` at `10270e7`). Migration `20261011_reservation_limits_and_expiry.sql` applied on 2026-10-03 (check 9/9, live safety check 20/20, Cron job running); the two-phone test passed on 2026-10-03; committed as `cc235a1`, merged into `master` through PR #20 on 2026-10-03 (`a3dcd98`).** |
| Bug fix pass, round 4 (not part of the six phases) | The second full audit of 2026-10-04: #36 a reported or chatted-about listing kept as `REMOVED` instead of deleted, #37 Log out no longer redraws Home without an account, #38 the Review reservation page keeps its price and amount, #39 a chat loads its newest messages, #40 one error when closing a chat offline, #41 listings whose pickup ended are Closed, #42 these docs, and four small ones (§30) | **Built and checked locally on branch `claude/bugfix-pass-4` (from `master` at `7369654`): unit tests 73/73, lint 0 errors, SQL dry run 9/9. Migration `20261015` applied (live safety check 9/9) to the live project and the one-phone test (§30.4) passed on 2026-10-04; merged into `master` through PR #27 (merge commit `a5f9a2c`).** |
| On-device recommendations (not part of the six phases) | "Recommended for you" at the top of Home: a small machine-learning model (logistic regression) trained on the phone from the account's own reservations and saved listings ranks the listings that match the saved Food preferences, and each card says why it is there (§31) | **Written on branch `ccr-01f52d20-ihgnaw`, renamed `claude/ml-recommendations` (from `master` at `a5f9a2c`, the PR #27 merge), on 2026-10-04. No SQL. Built with AGP on the developer's PC (unit tests 86/86, lint 0 errors; §31.4) and the 10-step one-phone test passed on 2026-10-04 (§31.5); merged into `master` through PR #29 on 2026-10-04 (merge commit `b15cb2a`).** |
| My allergies (a friend's suggestion; not part of the six phases) | Donors pick allergens from chips on Post and Edit instead of typing them; Profile → My allergies (saved on the phone, per account); Home and All listings leave out listings whose donor picked one of them, with a Show link, the row at the top never shows them, and the details page warns (§32) | **Written on branch `claude/allergy-filter` (from `master` at `b15cb2a`, the PR #29 merge) on 2026-10-05. No SQL. Built with AGP on the developer's PC (unit tests 91/91, lint 0 errors; §32.4); the 10-step one-phone test (§32.5) passed on 2026-10-06; merged into `master` through PR #30 on 2026-10-06 (merge commit `ed07da9`).** |
| Keyboard and map fixes (the friend's other suggestions; not part of the six phases) | The bottom bar hides while the keyboard is open; "Use my location" on Post and Edit fills in an empty pickup location (street, barangay, town, province; no house number); a full-screen map for the pickup pin; "saved" messages on the Profile pages as a snackbar (§33) | **Written on branch `claude/keyboard-and-map` (from `claude/allergy-filter` at `aa76d51`) on 2026-10-06. No SQL. Built with AGP on the developer's PC (unit tests 95/95, lint 0 errors; §33.3); the 9-step one-phone test (§33.4) passed on 2026-10-06; merged into `master` through PR #31 on 2026-10-06 (merge commit `9568264`).** |
| Portrait only (not part of the six phases) | The app stays upright whatever the phone's auto-rotate setting (§34) | **Written on branch `claude/portrait-only` (from `master` at `9568264`) on 2026-10-06. No SQL. Built with AGP on the developer's PC (unit tests 95/95, lint 0 errors; §34.2). Not tested on a phone yet (§34.3).** |
| Push notifications and staying signed in (not part of the six phases) | Every alert (message, reservation update, announcement) also pops up as a phone notification while the app is closed, in the background or open (not for the open chat); a tap opens the alert's chat, reservation or announcement; the permission asked once; "Keep me signed in" removed, so an account stays signed in until Log out (§35) | **Written on branch `claude/push-notifications` (from `claude/portrait-only` at `387e0bb`) on 2026-10-07. Migration `20261016_push_notifications.sql` and the Edge Function `send-push`. Built with AGP (unit tests 104/104, lint 0 errors), Deno 33/33, SQL check 10/10 on a local PostgreSQL 16 (§35.3). Migration, setup and phone test not done yet (§35.4).** |
| Phase 6: Admin and Final Release | ID verification and the admin screen (the Email verified badge moved to Phase 5 part 1) | **Started: first slice (§25), the web admin dashboard on real data, on branch `claude/admin-real`: migration applied (live safety check 20/20), deployed to https://salusalo.web.app, and the manual test passed on the PC and the phone (2026-10-03); merged into `master` through PR #23 (`a60b315`).** Analytics (§26) applied, deployed, tested on the phone and merged through PR #24 (`c240f48`, 2026-10-03). Announcements (§27) on branch `claude/admin-announcements`: migration applied (live safety check 14/14), deployed, and the one-phone test passed (2026-10-04); merged into `master` through PR #25 (`f959f0e`). ID verification and suspending accounts are later slices. |

- The roadmap has six phases (confirmed by the developer on 2026-09-25); earlier versions of this document went only up to Phase 4. Phases 1–3 are described in §1–§3 and Phase 4 in §8.

### Current state (2026-10-05)

This summarizes the app as it is on `master` at `9568264` (2026-10-06), plus portrait only (§34) on branch `claude/portrait-only` and push notifications and staying signed in (§35) on branch `claude/push-notifications`. `master` has the §21 and §22 bug fix passes (PRs #17 and #18), round 3 (§23, PR #19), reservation limits and pickup deadlines (§24, PR #20), the admin dashboard (§25–§27, PRs #23–#25), the chat wallpaper (§28, PR #22) and the new logo and loading screen (§29, PR #26), round 4 (§30, PR #27) the on-device recommendations (§31, PR #29), My allergies (§32, PR #30) and the keyboard and map fixes (§33, PR #31). The numbered sections below are the history of each phase; where a later change replaced something, it is marked there.

- **Bugs fixed:** the full bug audit of 2026-09-29 had 34 findings. §21 fixed #1–#11, #14, #15 and #17–#23; round 2 (§22) fixed #12, #13, #25, #26, #27, #30, #32 and #33, and partly #16, #24, #29, #31 and #34; #28 is deferred. The per-finding table is §22.1. A verification audit on 2026-09-30 found 17 of them only partly fixed, one regression (R1) and one new bug (#35); round 3 (§23) fixes what can be fixed in the app, and §23.1 lists every finding's state after it. A second full audit on 2026-10-04 found nothing critical or high and seven new findings (#36–#42) plus four small ones; round 4 (§30) fixes all of them (§30.1).
- **Database migrations:** 25 files in `supabase/migrations/`, applied by hand in filename order (`supabase/README.md`).
  - Not applied yet (§35, branch `claude/push-notifications`): `20261016_push_notifications.sql` (push tokens and the trigger that sends each new alert to `send-push`).
  - Applied to the live project on 2026-10-03 and 2026-10-04: `20261011_reservation_limits_and_expiry.sql` (§24), `20261012_admin_dashboard.sql` (§25), `20261013_admin_analytics.sql` (§26) and `20261014_admin_announcements.sql` (§27).
  - Applied to the live project on 2026-10-04 (round 4, §30): `20261015_bugfix_keep_reported_listings.sql` (a reported or chatted-about listing is kept as `REMOVED` instead of deleted).
  - Applied to the live project on 2026-09-30: `20261005_bugfix_security_integrity.sql` (the profile check in cancel and claim, the listing photo read rule, the listing checks trigger), `20261006_bugfix_reservations.sql` (`create_reservation_at_price`, `release_reservation`, the pickup-end check) and `20261007_bugfix_profile_relink.sql` (`relink_my_profile`).
  - Applied to the live project on 2026-09-30 (round 2): `20261008_bugfix_one_rating_per_reviewer.sql` (ratings count each reviewer's latest review), `20261009_bugfix_storage_and_rate_limits.sql` (bucket limits, message and reservation rate limits) and `20261010_bugfix_revoke_anon_execute.sql` (no function executable without a login).
- **Authentication:**
  - Firebase Authentication: email/password and Google through Credential Manager.
  - Since §35 an account stays signed in until Log out ("Keep me signed in" removed; before, unchecked, a fresh start signed out).
  - The Firebase ID token is sent as the Supabase access token (`SupabaseClientProvider`), and Supabase accepts it through Third-Party Auth when it carries `role: authenticated`. The app gets that claim itself through the `set-firebase-role` Edge Function (§18).
  - After each sign-in, `ensureSupabaseRole()` and then `loadOrCreateCurrentProfile()` run. A failure keeps the Firebase session and offers Try again. When the email already belongs to an earlier profile, the app offers "Restore your profile?" instead: `relink_my_profile`, only after the user confirms, and only with a verified email in the token (§21).
  - The signed-in profile is saved with the screen state, so rotation, dark mode, font size and process recreation keep Home.
  - When Firebase reports the account disabled or deleted, or its sign-in revoked or expired, the app signs out and returns to Sign In with "Your session has ended. Please sign in again." A network failure never signs out. Only Profile's Log out signs out; Back on Home closes the app.
  - Round 2: sign-in errors show short texts (a network problem, too many attempts), never technical details. Android backup and phone-to-phone transfer leave out Firebase's saved sign-in, so a restored or new phone asks to sign in again.
  - Round 3: a sign-in or sign-up that finishes after a rotation (or a dark-mode or font change) still opens Home: the app follows Firebase's user as Compose state, and runs one account setup at a time.
- **Reservations:**
  - Every change goes through SECURITY DEFINER RPCs that take the caller from the token and refuse a token without a profile. Clients have SELECT only on `reservations`.
  - The current app reserves through `create_reservation_with_hold` (§24), which calls `create_reservation_at_price` (refused when the price changed since the user saw it), which calls `create_reservation`. That function locks the listing, refuses your own, removed, expired and pickup-ended listings and asks for more than is left, and changes quantity and status in the same transaction. Older builds call `create_reservation` directly.
  - The recorded `unit_price` always comes from the listing, through a trigger.
  - `cancel_reservation` is for the recipient, and `claim_reservation` and `release_reservation` for the listing's donor, all only while `RESERVED`. Cancel and release give the quantity back.
  - `remove_my_food_post` cancels the open reservations of a removed listing. Since round 4 (§30) it deletes a listing only when nobody reserved it, reported it or started a chat about it; otherwise it keeps it as `REMOVED`.
  - The `reservations_notify` trigger sends the alerts: a release alerts the recipient.
  - Donors see recipient names only, through `get_donor_reservations()`, which also returns `cancel_reason`.
  - Since §24 a listing may limit how much one person reserves, the app reserves with a pickup time (15 min to 2 h), and the Cron job `salusalo-expire-reservations` cancels a reservation not marked as picked up by its deadline (`PICKUP_EXPIRED`), giving the quantity back. The donor can still release a no-show earlier.
  - Round 2 (`20261009`): at most 10 reservations per person per 10 minutes and 20 chat messages per person per minute, enforced in `create_reservation` and `send_message`.
  - Round 2 (`20261008`): a donor's rating counts each reviewer's latest review only.
- **Home feed refresh:**
  - The feed contains `AVAILABLE`, unexpired listings whose pickup time has not ended, newest first.
  - It loads when Home starts, and reloads when Home or All listings is opened and when the app comes back, at most every 30 seconds (`refreshFeedIfStale`). Since round 3 it also reloads every minute while Home or All listings is on screen and the app is in front. It also reloads on pull to refresh (the spinner shows only for a pull), after a post, edit, delete, reservation, cancel, release or review, and after a refused reservation. Since round 3 the reload after a cancel or release no longer depends on Activity staying open (`ReservationViewModel.quantityReturned`).
  - A failed reload keeps the listings with "Couldn't refresh the listings. These may be out of date." and Try again. A failed first load shows "Could not load listings". The empty states appear only after a successful load.
  - Food details (round 3): the open listing is held by `FoodViewModel` (`openedListing`), so rotation keeps the page and its Review reservation page open; a listing the feed listed when the page opened but no longer lists shows "Unavailable" and "This listing is no longer available to reserve." instead of Reserve; the page re-checks by itself when the expiry or the end of pickup passes.
  - Only chats and alerts use Realtime. Since §35 (once its migration and setup are done) every alert is also pushed to the account's phones as a notification, also while the app is closed. Since round 3, when Realtime reconnects after a lost connection, the alerts, the inbox and the open chat reload what they missed.
- **Home recommendations (§31; passed a one-phone test on 2026-10-04, merged through PR #29):**
  - The row at the top of Home becomes "Recommended for you" once the account has reserved or saved a listing and the feed has listings that share something with what it chose; otherwise it is "Picked for you" (with saved Food preferences) or "Just posted", as before.
  - A logistic regression is trained on the phone (`model/ListingRecommender.kt`) from the account's own reservations and saved listings, with the feed's other listings as the negatives. Each card says why it is there ("Like the cooked meals you reserved", "From a donor you reserved from"). Nothing is sent to Supabase or stored.
  - The account's reservations now load once, quietly, when Home starts (`ReservationViewModel.start()`), so the model can use them before Activity is opened.
- **My allergies (§32; passed a one-phone test on 2026-10-06, merged through PR #30):**
  - Post and Edit have allergen chips (Milk, Eggs, Fish, Shellfish, Peanuts, Tree nuts, Wheat/gluten, Soy, Sesame, Other, None) instead of a text box; they store readable text in `food_posts.allergens` ("Milk, Shellfish, Other: crab paste"), so no SQL.
  - Profile → My allergies is saved on the phone, per account. Home and All listings leave out listings whose donor picked one of them ("1 listing hidden because of your allergies (Milk)." with Show); the row at the top never shows them; the details page shows a red warning, or an amber note when the allergens can't be checked (typed before the chips, or under Other).
- **Keyboard and map fixes (§33; passed a one-phone test on 2026-10-06, merged through PR #31):** the bottom bar hides while the keyboard is open; "Use my location" on Post and Edit fills in an empty pickup location through Android's Geocoder (which sends the pin's position to its provider, Google on most phones); a button opens the pickup map full screen; "saved" messages on the Profile pages are a snackbar.
- **Portrait only (§34, branch `claude/portrait-only`; built 2026-10-06, not tested on a phone yet):** `MainActivity` is locked to portrait, whatever the auto-rotate setting. Android 16+ ignores the lock on large screens (tablets, unfolded foldables).
- **Push notifications (§35, branch `claude/push-notifications`; built 2026-10-07, migration, setup and phone test pending):** the trigger `notifications_push` sends each new alert, with the account's Firebase UID and its phones' FCM tokens, to the Edge Function `send-push`, which sends FCM data messages; the app shows them (`PushNotifications.kt`), except a message of the chat open on screen, and a tap opens the alert's place. The alert text passes through Google's FCM.
- **Images and Storage:**
  - Private buckets `food-images` (`<Firebase UID>/<listing ID>/image.jpg`, `photo-2.jpg`…`photo-4.jpg`) and `profile-images` (owner only).
  - Photos are shown through 1-hour signed URLs, one signing request per list.
  - Since §21 a listing photo is readable only by its uploader, or by someone who may see the listing, saved it or chats about it (`can_read_food_image`).
  - A feed photo that fails when the feed is 50+ minutes old reloads the feed once for fresh URLs. Activity, Messages and Saved Listings sign their photos each time they load.
  - Posting uploads with upsert under the form's listing ID and deletes photos only when the listing certainly wasn't saved. Deleting a never-reserved listing removes its photos, best effort.
  - Round 2: photos are cached on the phone by their Storage path (`signedImageRequest`), so a new signed URL doesn't download them again; photos are sampled on the longer side before decoding (`photoSampleSize`); `20261009` limits both buckets to JPEG, PNG and WebP up to 5 MB.
- **Activity (§19, §21):** three tabs.
  - **My Listings** (Active and Closed): Manage reservations with Mark as picked up and Release reservation, View, Edit and Delete listing.
  - **My Reservations**: open reservations, filtered All, Free food or Purchases, with the reservation and pickup pages, Message and Cancel.
  - **History**: picked-up and cancelled reservations from both sides, with the Completed and Items rescued totals.
  - Results of cancel, claim and release show in a dialog. Reservation lists reload when Activity opens and when a tab is tapped (tab taps at most every 10 seconds since round 2).
  - A failed first load shows "Could not load your listings" or "Could not load your reservations" with Try again, never an empty state. A failed reload keeps the lists with a note. Totals and reservation counts show only after the reservations have loaded; since round 3 that includes the "Your listing" and Manage reservations pages, which show the load error with Try again instead of "No reservations yet" or "No active reservations".
  - Round 3: the Edit Listing and Rate pages stay open through rotation (Activity and Post). Post's "My posts" shows its loading and failure states instead of "(0)" and "You have no food posts yet.".
- **Automated tests (2026-10-04):**
  - `testDebugUnitTest` 73 of 73 (round 4): `ExampleUnitTest` 1, `GeoTest` 11, `PhotoSigningTest` 8, `PickupStartTest` 6, `SupabaseRoleTest` 8, `UiTextTest` 9, `PublishOnceTest` 6, `ReserveBlockerTest` 5, `PhotoSampleTest` 3, `RealtimeRejoinTest` 2, `ReservationLimitsTest` 11, `NotificationTypeTest` 3.
  - `assembleDebug` passed and `lintDebug` found 0 errors on 2026-10-04 (round 4); `assembleRelease` (unsigned) passed in round 2.
  - The admin dashboard's Node checks (`admin/tests/`, 3) pass (2026-10-04).
  - §31 (merged through PR #29): the same tests plus `ListingRecommenderTest` (12) and one more in `UiTextTest` (10): `testDebugUnitTest` 86 of 86, `lintDebug` 0 errors (§31.4).
  - §32 (branch `claude/allergy-filter`): plus `AllergensTest` (5): `testDebugUnitTest` 91 of 91, `lintDebug` 0 errors (2026-10-05, §32.4).
  - §33 (merged through PR #31): plus 4 in `GeoTest` (15): `testDebugUnitTest` 95 of 95, `lintDebug` 0 errors (2026-10-06, §33.3). §34 (branch `claude/portrait-only`): the same 95 of 95, `lintDebug` 0 errors and 26 warnings (2026-10-06, §34.2).
  - §35 (branch `claude/push-notifications`): plus `PushRulesTest` (9): `testDebugUnitTest` 104 of 104, `lintDebug` 0 errors and 25 warnings (2026-10-07, §35.3).
  - The Edge Functions' Deno tests: 33 (21 for `set-firebase-role`, 12 for `send-push`), all passing on 2026-10-07 (§35.3).
  - SQL live safety checks after the §21 migrations (2026-09-30): 22 of 23 (the one failure was the check itself, corrected), 15 of 15, 9 of 9. After the round 2 migrations (2026-09-30): 5 of 5, 5 of 5, 3 of 3 (§22.4). Round 3 has no SQL. Round 4's check (`bugfix_keep_reported_listings_live_safety_check.sql`) passed 9 of 9 on a local PostgreSQL 16 (§30.3) and 9 of 9 on the live project (2026-10-04, §30.4a).
- **Manual testing completed:** every phase and fix set up to §18 passed on two phones (results log). §21 passed a test on one phone, switching accounts, on 2026-09-30 (§21.4). It covered posting, filters after posting, reserving through the price-checked function, cancel, release and its alert, mark as picked up, offline refresh and Activity notes, rotation and dark mode offline, posting offline and retrying, the Home dialog after leaving Post, the pickup-end note and feed, and delete. §22 passed its one-phone test on 2026-09-30 (§22.5). Round 3 (§23) passed its one-phone test (§23.4, with regression steps 15–19) on 2026-10-03. §24 passed its two-phone test on 2026-10-03, and §25–§27 and §29 their tests (§25.4, §26.4, §27.4, §29.2). Round 4 (§30) passed its one-phone test on 2026-10-04 (§30.4a).

### Current known issues

Still unresolved on 2026-10-04:

- **Not tested on a phone:**
  - The optional checks for #5 (photo links older than an hour), #21 (a disabled or deleted account) and #15 (restoring a re-created account's profile).
  - The password-reset email (no test record).
- **Round 3 (§23)** passed on one phone and its two-phone checks (#2, #8, #14) on 2026-10-03; merged through PR #19.
- **Round 4 (§30)** on branch `claude/bugfix-pass-4`: built and checked locally; migration `20261015` applied to the live project and the one-phone test (§30.4) passed on 2026-10-04; merged through PR #27 (`a5f9a2c`).
- **On-device recommendations (§31)**: the one-phone test (§31.5) passed on 2026-10-04; merged through PR #29 (`b15cb2a`).
- **My allergies (§32)** on branch `claude/allergy-filter`: built with AGP (91/91, lint 0 errors) on 2026-10-05; the one-phone test (§32.5) passed on 2026-10-06; merged through PR #30 (`ed07da9`).
- **Keyboard and map fixes (§33)**: the 9-step one-phone test (§33.4) passed on 2026-10-06; merged through PR #31 (`9568264`).
- **Portrait only (§34)** on branch `claude/portrait-only`: built with AGP (95/95, lint 0 errors) on 2026-10-06; **not tested on a phone yet** (§34.3).
- **Push notifications and staying signed in (§35)** on branch `claude/push-notifications`: built and checked locally on 2026-10-07; **the migration, the `send-push` setup (`supabase/README.md`, "Push notifications") and the phone test (§35.4) are not done yet**. Known limits in §35.5.
- **Audit findings still open after round 3** (details in §23.1; each needs a decision or work outside the app code):
  - #9: reservations never expire by themselves, and one person can reserve a listing's whole quantity (the app's design says "part or all"). **Addressed in §24** (per-person limit, pickup deadlines, automatic expiry): applied and passed its two-phone test on 2026-10-03.
  - #10: donors' Firebase UIDs are the first folder of listing photo paths, so anyone who may see a listing can read its donor's UID.
  - #11: the database accepts any category or unit text (up to 80/30 characters) and any future expiry on a new listing; the app sends only its own lists.
  - #12: a friendly second account still adds one rating (each reviewer now counts once).
  - #16: cards download the stored 1600 px photo once per phone (no thumbnails; Supabase image transformations need a paid plan).
  - #24: orphaned Storage files are only listed (`storage_orphans_query.sql`), never deleted automatically.
  - #28: no feed pagination (PostgREST would cap the feed at 1000 rows); the chat Realtime channel is unfiltered and checked by RLS per row. Since round 4 a chat loads its newest 1000 messages (#39), so a longer chat leaves out its oldest.
  - #29: every Activity open still reloads its lists (kept on purpose: an alert opening Activity needs fresh lists).
  - #31: not ready for Play: R8 off, no real keystore, `applicationId` `com.example.salusalo` (Play refuses `com.example.*`), no in-app account deletion or web deletion link, Play Console forms not done; Google sign-in works only with registered SHA-1s; `googleid` pinned to 1.2.0.
  - #34: `users.role` always `RECIPIENT` (kept for Phase 6); an unknown server enum value breaks a whole list (alert types only: fixed by the §27 build, which skips unknown types); a discarded draft's photo-picker grants are left to Android to prune; Post's "My posts" duplicates Activity.
- **Design limits:**
  - Only chats and alerts are live; the feed reloads every minute while it is on screen (round 3). Since §35 alerts are also pushed as phone notifications (§35.5 for their limits).
  - A published listing photo is never replaced by the app, but the Storage policy lets a donor overwrite their own file through the API; other phones would keep showing their cached copy (§23.1, #16).
  - Reservations expire about a minute after their pickup deadline (§24.6); those made by builds older than §24 have no deadline and never expire.
  - Since round 4 a listing someone reported or started a chat about can't be fully deleted by its donor; it is only removed (hidden), with its photos (§30.5).
  - Rate limits (round 2): a person reserving more than 10 times in 10 minutes, or sending more than 20 messages a minute, waits.
  - A recipient keeps read access to a listing after cancelling (`is_reservation_recipient` ignores status).
  - #21 is noticed at the next token refresh (up to an hour).
  - Older builds reserve without the price check and can't release.
  - Google sign-in with the email of an unverified email/password account makes that account Google-only (§10.5).
  - The automatic role's limits (§18.6): the claim write is not atomic and the key doesn't expire.
  - OpenStreetMap's free tile servers suit testing and a small user base only (§15.5).
- **Phase 6** (admin and final release): first slice (§25) on branch `claude/admin-real`: the `admin/` dashboard on real data through admin-only functions, Firebase Hosting. Migration applied, deployed, tested on the PC and the phone and merged through PR #23 (2026-10-03). Analytics (§26) applied, deployed, tested on the phone and merged through PR #24 (`c240f48`, 2026-10-03). Announcements (§27) on branch `claude/admin-announcements`: migration applied (live safety check 14/14), deployed, and the one-phone test passed (2026-10-04); merged into `master` through PR #25 (`f959f0e`).

### Build verification

- **Result:** `BUILD SUCCESSFUL`.
  - Run on 2026-09-25 01:37 (+08:00), during this documentation pass.
  - Command: `gradlew.bat :app:assembleDebug --offline`, with `JAVA_HOME` set to the Gradle-provisioned JDK 21.
  - `compileDebugKotlin` was already up to date, which means the current sources had already compiled in an earlier IDE build. Manifest, resources, dex and packaging re-ran.
  - No unit tests, instrumented tests or lint were run.
- **After the §3.2 and §3.3 image changes:** `BUILD SUCCESSFUL in 47s` on 2026-09-25, same command.
  - `compileDebugKotlin` re-ran, so the changed sources compiled.
  - The only compiler warning, a deprecated `Icons.Outlined.ArrowBack` in `MainActivity.kt`, was already present at `d4f65fe`.
  - No tests or lint were run, and the app was not installed or run.
- **After the §3.4 load-failure fixes:** `BUILD SUCCESSFUL in 16s` on 2026-09-25, same command.
  - `compileDebugKotlin` re-ran. Its only warning, a deprecated `Icons.Outlined.FormatListBulleted` in `ActivityScreen.kt`, was already present at `d4f65fe`; it appears because that file was recompiled.
  - No tests or lint were run, and the app was not installed or run.
- **After the §3.5 known-issue fixes (commit `85c9263`):** `BUILD SUCCESSFUL in 29s` on 2026-09-25, same command.
  - `compileDebugKotlin` re-ran. Its only warnings, the deprecated `Icons.Outlined.FormatListBulleted` (`ActivityScreen.kt`) and `Icons.Outlined.ArrowBack` (`MainActivity.kt`), were already present.
  - No tests or lint were run. The developer then installed this code from Android Studio and ran the §3.5 checks ([results log](#results-log)).
- **Phase 4 (commit `266325b`):** `gradlew :app:assembleDebug` passed locally on 2026-09-25. The only warnings were the deprecations already present (`FormatListBulleted`, `ArrowBack`, `Logout`).
- **After the §8.7 preference fixes (commit `c7323b8`, which includes `5c1e89c`):** `BUILD SUCCESSFUL in 29s` on 2026-09-25 21:21 (+08:00), `gradlew.bat :app:assembleDebug`.
  - Its only warning, the deprecated `Icons.Outlined.ArrowBack` (`MainActivity.kt`), was already present.
  - No tests or lint were run. The developer then installed this code from Android Studio and ran the retest ([results log](#results-log)).
- **Purchases/Claims and Phase 1 demo fixes (branch `claude/phase1-demo-fixes` at `8908548`, merged as PR #3):** `assembleDebug` BUILD SUCCESSFUL on 2026-09-26 after pinning `googleid` 1.2.0 (§10.6), and again with the "starts unchecked" change. The developer then installed it from Android Studio and ran the Phase 1 checks ([results log](#results-log)).
- **Phase 3 demo fixes (branch `claude/phase3-demo-fixes` at `24fe9f2`):** `gradlew.bat :app:assembleDebug --offline` BUILD SUCCESSFUL in 3m 23s on 2026-09-26, with only the three old deprecation warnings (`FormatListBulleted`, `ArrowBack`, `Logout`). No tests or lint were run. The developer then installed it from Android Studio on both phones and ran the §12.4 test ([results log](#results-log)).
- **Phase 4 demo fixes (branch `claude/phase4-demo-fixes` at `3288d02`, §13):** `gradlew.bat :app:assembleDebug --offline` BUILD SUCCESSFUL in 43s on 2026-09-27, with only old deprecation warnings (`ArrowBack`, `Logout`). No tests or lint were run. Before that, only a kotlinc 2.2.10 type check against desktop Compose jars and hand-written Android stubs had been run (§13.3). The developer then installed it from Android Studio on both phones and ran the §13.4 test ([results log](#results-log)).
- **Phase 5 part 1 (branch `claude/phase5-reviews-reports-v6ahqn` at `3bd038c`, §14):** `gradlew.bat :app:assembleDebug --offline` BUILD SUCCESSFUL in 57s on 2026-09-27, with only the three old deprecation warnings (`FormatListBulleted`, `ArrowBack`, `Logout`). No tests or lint were run. Before that, only a kotlinc 2.2.10 type check against desktop Compose jars and hand-written Android stubs had been run (§14.3). The developer then installed it from Android Studio on both phones and ran the §14.4 test ([results log](#results-log)).
- **Phase 5 part 2 (branch `claude/phase5-maps` at `47ed894`, §15):** `gradlew.bat :app:assembleDebug :app:testDebugUnitTest` BUILD SUCCESSFUL in 3m 43s on 2026-09-28, run online once to download osmdroid 6.1.20, with only the three old deprecation warnings (`FormatListBulleted`, `ArrowBack`, `Logout`). `testDebugUnitTest`: `GeoTest` 11 of 11 and `ExampleUnitTest` passed. No lint was run. The developer then installed it from Android Studio on both phones and ran the §15.4 test ([results log](#results-log)).
- **Mika's PR #10 (branch `mika/navigation-activity-settings` at `b2b04ca`, §16):** `gradlew.bat :app:assembleDebug :app:testDebugUnitTest --offline` BUILD SUCCESSFUL in 36s on 2026-09-28, with only icon deprecation warnings; `GeoTest` 11 of 11. The developer then installed it from Android Studio on both phones and ran the §16 test ([results log](#results-log)).
- **Fixes after the second demo audit (branch `claude/cleanup-after-audit`, §17):** `gradlew.bat :app:assembleDebug :app:testDebugUnitTest --offline` at `7feb5b5`: BUILD SUCCESSFUL in 55s on 2026-09-28 with no warnings, unit tests 26 of 26; `lintDebug`: 0 errors. At `e0f2d59` (Back on Home): `assembleDebug --offline` BUILD SUCCESSFUL. The developer then installed it from Android Studio on both phones and ran the §17.4 test ([results log](#results-log)).
- **UI/UX refresh (branch `claude/focused-mayer-0aevp8`, §19):** not built with AGP: Google's Maven repository (`dl.google.com`) is blocked from the session. Only a kotlinc 2.2.10 type check (0 errors, 0 warnings) and the JVM tests (43 of 43) ran (§19.2). It was merged through PRs #14 and #15 and first built here with AGP as part of the §21 build.
- **Bug fix pass (branch `claude/bugfix-pass-1`, §21):** `gradlew.bat :app:assembleDebug :app:testDebugUnitTest --offline` BUILD SUCCESSFUL after each of the five groups on 2026-09-29; the last run passed 51 of 51 tests; a forced `compileDebugKotlin` showed no warnings; `lintDebug` 0 errors (38 warnings, none on a changed line). Committed as `5429e6e`. The developer then installed it from Android Studio on one phone and ran the §21.4 test ([results log](#results-log)).
- **Bug fix pass, round 2 (branch `claude/bugfix-pass-2`, §22):** `gradlew.bat :app:assembleDebug :app:testDebugUnitTest --offline` BUILD SUCCESSFUL after each group on 2026-09-30 (groups 2 and 3 built together, because the shell was unavailable after group 2); the last run, forced with `--rerun`, passed 54 of 54 tests; a forced `compileDebugKotlin` showed no warnings.
- **Bug fix pass, round 3 (branch `claude/bugfix-pass-3`, §23):** `gradlew.bat :app:assembleDebug :app:testDebugUnitTest --offline` BUILD SUCCESSFUL on 2026-09-30 after the fixes and again after the last change; 57 of 57 tests; a forced `compileDebugKotlin --rerun` showed no warnings; `lintDebug` 0 errors (39 warnings, one more than round 2's 38: the new `Uri.parse` line in PostScreen gets the same `UseKtx` warning as the two `Uri.parse` lines already there; 7 hints). Not installed on a phone yet.
- **Automated tests (2026-09-30, round 3):** 57 JVM tests, all run by `testDebugUnitTest` and all passing: the template `ExampleUnitTest` (1), `GeoTest` (11, §15), `PhotoSigningTest` (8) and `PickupStartTest` (6, §17), `SupabaseRoleTest` (8, §18), `UiTextTest` (9, §19), `PublishOnceTest` (6) and `ReserveBlockerTest` (3, §21 and §23), `PhotoSampleTest` (3, §22), and `RealtimeRejoinTest` (2, §23). Also the instrumented template `ExampleInstrumentedTest` (not run) and the Edge Function's Deno tests (21, §18).
- **On-device recommendations (branch `claude/ml-recommendations`, written as `ccr-01f52d20-ihgnaw`, §31):** the writing session could not build with AGP (`dl.google.com` is blocked there) and ran a kotlinc 2.2.10 type check (0 errors, 0 warnings) and the JVM tests (86 of 86). On the developer's PC on 2026-10-04: `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --offline` BUILD SUCCESSFUL, 86 of 86 tests, lint 0 errors (§31.4). The developer then installed it from Android Studio and ran the §31.5 test ([results log](#results-log)).
- **My allergies (branch `claude/allergy-filter`, §32):** on the developer's PC on 2026-10-05, `gradlew.bat :app:assembleDebug :app:testDebugUnitTest --offline` BUILD SUCCESSFUL, 91 of 91 tests, and `:app:lintDebug` 0 errors (§32.4). The developer then installed it from Android Studio and ran the §32.5 test ([results log](#results-log)).
- **Keyboard and map fixes (branch `claude/keyboard-and-map`, §33):** on the developer's PC on 2026-10-06, `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --offline` BUILD SUCCESSFUL, 95 of 95 tests, lint 0 errors (§33.3). The developer then installed it from Android Studio and ran the §33.4 test ([results log](#results-log)).
- **Portrait only (branch `claude/portrait-only`, §34):** on the developer's PC on 2026-10-06, `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --offline` BUILD SUCCESSFUL, 95 of 95 tests, lint 0 errors and 26 warnings (§34.2). Not yet installed for a phone test.
- **Push notifications (branch `claude/push-notifications`, §35):** on the developer's PC on 2026-10-07, `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --offline` BUILD SUCCESSFUL, 104 of 104 tests, lint 0 errors and 25 warnings; Deno 2.9.6 33 of 33; the migration and its safety check on a local PostgreSQL 16.4 (§35.3). Not yet installed for a phone test.

### Phase 1: what exists (summary)

- **Auth flows.** Sign-up (with display name), sign-in, password-reset email and sign-out through Firebase, in `SaluSaloApp()` (`MainActivity.kt` L113–325).
  - An existing session is restored on launch (`LaunchedEffect(firebaseAuth.currentUser?.uid)`, L156–160).
  - System Back on the Home page signed the user out (L162–171) until §17; since then it closes the app, and only Profile's Log out signs out.
- **Profile creation.** After sign-up the ID token is force-refreshed so it includes the `role: authenticated` claim, which, since §18, `UserRepository.ensureSupabaseRole()` gets from the `set-firebase-role` Edge Function (before §18 the developer's Admin SDK script, outside this repo, added it by hand). `UserRepository.loadOrCreateCurrentProfile()` then finds or creates the `public.users` row.
  - New rows are always `RECIPIENT`: the DB default plus the RLS insert check.
- **Supabase tokens.** `SupabaseClientProvider` sends the Firebase ID token to Supabase, and RLS checks it via `is_salusalo_firebase_jwt()`.
- **Storage.** The private buckets `food-images` and `profile-images` are created by `20260921_phase1_foundation.sql`.
- **Not part of the implemented scope** (current placeholders):
  - The Google, Apple and Facebook buttons only show a message. **Since §10 (in code, not tested): real Google sign-in; Apple and Facebook removed.**
  - "Keep me signed in" has no effect. **Since §10 (in code, not tested): it works.**
  - Profile name/location edits, the profile photo and settings are saved only to local SharedPreferences. Reviews are sample data. **Since §10 (in code, not tested): the name and photo are saved to Supabase and the location is removed; other settings are still local, reviews still sample data.**
  - Only the displayed profile **name** comes from Supabase.
  - `StorageRepository.profileImagePath()` is unused. **Since §10 it is used for profile photos.**

### Phase 2: what exists (summary)

- **Create listing.** `FoodRepository.createFoodPost()` uploads the image to `food-images/<firebaseUid>/<postId>/image.<ext>` with `upsert = false`, then inserts the `food_posts` row with `image_url = <storage path>`. If the insert fails, the uploaded image is deleted.
- **Feed.** `getAvailableFoodPosts()` returns posts with `status = AVAILABLE` and `expiration_date > now`.
  - Each post gets a 1-hour signed URL in `displayImageUrl`.
  - The Home list (`AvailableFoodCard`) shows it with `AsyncImage`.
- **Own posts.** `getUserFoodPosts()` feeds PostScreen → "My posts" and Activity → My Listings, both with Delete. Delete removes the row, then the image.
- **Not part of the implemented scope:**
  - **No edit UI.** `FoodRepository.updateFoodPost()` exists but is unused. **Since §11: Edit listing through `update_my_food_post()`, and `updateFoodPost()` is removed (passed on 2026-09-26).**
  - **Only the first selected photo is uploaded**, even though up to four can be picked. **Since §11 all of them, up to 4 (passed on 2026-09-26).**
  - **Form fields that are collected but not saved:** price ("I want to sell"; saved since §9, passed on 2026-09-26), pickup start/end, distribution mode, partial claims and maximum per recipient, allergens, storage notes. **Since §11 the pickup window, handover (the former distribution mode), allergens and storage notes are saved, and partial claims and maximum per recipient are removed (passed on 2026-09-26).**
  - **Hard-coded display values:** every listing shows "Free", provider "Food donor", rating "New", and uses a fixed distance of 0 for the "Nearby" filter. **The price is real since §9 and the donor name since §11; the rating and the Nearby filter were removed in §11 (passed on 2026-09-26).**

---

## 2. Phase 3 — Reservations: actual implementation

Code involved:
- `supabase/migrations/20260924_phase3_reservations.sql`
- `supabase/migrations/20260924_phase3_donor_recipient_names.sql`
- `data/ReservationRepository.kt`
- `model/Reservation.kt`
- `viewmodel/ReservationViewModel.kt`
- UI: `FoodDetailsScreen` in `MainActivity.kt`, and `ActivityScreen.kt`

**Later changes.** This section describes Phase 3 as built on 2026-09-25. Since then: listings change only through RPCs (§11, §12); the Activity tabs were rebuilt (§19); cancel and claim refuse a caller without a profile, donors can release a reservation, reserving checks the price shown and the pickup end, and listing photos have a stricter read rule (§21). The markers below point to them; [Current state](#current-state-2026-10-05) has the summary.

### 2.1 Data model

- **`public.reservations` table.**
  - `id` (uuid).
  - `food_post_id` → `food_posts.id`, **on delete cascade**.
  - `recipient_id` → `users.id`, on delete restrict.
  - `quantity`, which must be > 0.
  - `status`: `RESERVED`, `CLAIMED` or `CANCELLED` (default `RESERVED`).
  - `created_at` and `updated_at` (kept current by a trigger).
  - Indexes on `recipient_id` and `food_post_id`.
- **Kotlin (`model/Reservation.kt`).**
  - `Reservation` and `ReservationStatus` mirror the table.
  - `ReservationDisplay(reservation, foodPost, recipientName?)` is the row the UI renders.
  - `DonorReservationRow` holds the `get_donor_reservations()` result, including `recipient_name`.

### 2.2 Creating reservations

- **UI.** `FoodDetailsScreen` (`MainActivity.kt` L2228) opens from any feed or recommendation card.
  - **Reserve** (L2700–2714) calls `reservationViewModel.reserve(food.id, quantity)`.
  - On the user's own post (`isOwnFood`, L1958) it shows "You cannot reserve your own food post." instead.
- **Call chain.** `ReservationViewModel.reserve()` → `ReservationRepository.createReservation()` → RPC `create_reservation(p_food_post_id, p_quantity)`. Only the post ID and quantity are sent; the recipient is derived from the JWT.
- **On success.**
  - The message "Reservation created. Please coordinate pickup with the donor." is set.
  - `myReservations` is reloaded.
  - `FoodDetailsScreen` calls `onReserved()`, which reloads the feed and closes the dialog (L1960–1963, L2279–2287). This also closes the "Reserved" notice set just before, so it is never seen (found on 2026-09-26, §9.6).
- **On failure.** The raw exception message is shown in a "Reservation" dialog.
- **Not a reservation.** "Claim Now" / "Buy Now" on the same screen opens `FoodActionsDialog` (`FoodActions.kt`). That flow is **preview-only**; it states "No request was sent, no stock was held, and no payment was taken."

### 2.3 Quantity handling

- **Client.**
  - The stepper runs from 1 to `available` (L2642–2668).
  - `available` is parsed from the display string `"<quantity> <unit>"` (L2235–2238) and falls back to 1 if parsing fails.
  - `ReservationRepository.createReservation()` also `require`s quantity > 0.
  - Client values come from the last feed load and may be stale. The server is authoritative.
- **Server (`create_reservation`) rejects:**
  - a quantity ≤ 0;
  - a missing or expired post ("Food post is no longer available");
  - a caller with no profile ("Profile not found");
  - the caller's own post;
  - `status <> 'AVAILABLE'` or `quantity < requested` ("Not enough food available. Only N remains.").
- **Constraint change.** Phase 3 relaxes `food_posts_quantity_check` from `quantity > 0` to `quantity >= 0`, so a fully reserved listing can reach 0.

### 2.4 Atomic quantity protection

- **`create_reservation`** runs everything in one function call, which is one transaction.
  - It row-locks the post (`SELECT … FOR UPDATE`), validates, inserts the reservation, and decrements `food_posts.quantity`.
  - The post's `status` becomes `RESERVED` when the quantity reaches 0, otherwise `AVAILABLE`.
  - Concurrent reservations on the same post are serialized by the lock. The later one reads the reduced quantity and fails the check.
- **`cancel_reservation`** locks the reservation row and restores quantity with a single `UPDATE … SET quantity = quantity + n`.
- **`claim_reservation`** locks both the reservation row and the post row.
- **Client permissions.** Clients have **no** INSERT/UPDATE/DELETE grants on `reservations`, only SELECT. Every write goes through the SECURITY DEFINER RPCs.
- **Not yet verified.** No concurrency test (two recipients reserving the last units at the same moment) has been run.

### 2.5 Cancellation

- **UI.** Activity → **My Reservations** → open a record → **Cancel**. **Since §19:** Activity → My Reservations → the reservation → Cancel reservation.
  - It is shown only for non-donor records whose status is `Reserved` (`ActivityScreen.kt` L698–699).
- **RPC `cancel_reservation(p_reservation_id)`.**
  - Only the reservation's recipient may cancel.
  - `CLAIMED` or already `CANCELLED` reservations are rejected.
  - It sets the reservation to `CANCELLED`, adds its quantity back to the post, and sets the post's `status = 'AVAILABLE'` unconditionally.
- **After success.** The ViewModel reloads both reservation lists. It does **not** reload the Home feed. **Changed in §3.5:** Activity then reloads the feed; since §21 it reacts to a typed result (`ReservationAction.Cancelled`), not to the message text.

### 2.6 Claiming (the donor marks a reservation as claimed)

- **UI.** Activity → **My Donations** → open a record → **Mark claimed**. **Since §19:** My Listings → Manage reservations → Mark as picked up. **Since §21** the donor can also release an open reservation there (`release_reservation`).
  - It is shown only for donor records whose status is `Reserved` (`ActivityScreen.kt` L696–697).
- **RPC `claim_reservation(p_reservation_id)`.**
  - Only the donor of the reservation's food post may claim, and only a `RESERVED` reservation.
  - It sets the reservation to `CLAIMED`.
  - **It does not change the food post's quantity or status.**
- **Not related:** the Activity **"Claims"** tab is demo data and does not show claimed reservations. **Outdated:** Claims became real in §9 and moved into History in §19.

### 2.7 Recipient reservations ("My Reservations")

- **Query.** `ReservationRepository.getMyReservations()` selects from `reservations` where `recipient_id` equals the caller's profile id.
  - This explicit filter matters: RLS alone would also return reservations on the caller's own posts.
- **Joining posts.** Each reservation is matched in memory with its `FoodPost` by `toDisplays()`. It fetches **every** `food_posts` row visible to the caller. Since the 2026-09-25 change (§3.3) it keeps only the posts in `ids` and signs each one's image; before that, `ids` was computed but unused.
- **Visibility.** The recipient can still read a post they reserved after it becomes `RESERVED` with quantity 0. This works through the Phase 3 `food_posts` SELECT policy that uses `is_reservation_recipient()`.
- **Order.** The client does not sort the list.

### 2.8 Donor reservation management ("My Donations")

- **Query.** `ReservationRepository.getReservationsForMyFoodPosts()` calls `get_donor_reservations()`.
  - That SECURITY DEFINER function returns reservations on the caller's own posts, newest first, plus `recipient_name` (`users.name`) only.
  - The same function is defined in `20260924_phase3_donor_recipient_names.sql` and also appended to the end of `20260924_phase3_reservations.sql`. Both use `create or replace`, so running both is harmless.
- **Joining posts.** Rows are matched in memory with the donor's visible `food_posts`; every visible row is fetched. Since the 2026-09-25 change (§3.3), only the posts the rows reference are kept, and each one's image is signed.
- **Display.** The UI shows "Recipient: <name>" on the card and in the details dialog.
  - **Recipient information is limited to the display name.** No email or phone is exposed.

### 2.9 Ownership / security (as implemented)

- **Reservation visibility.** The `reservations` RLS SELECT policy allows the caller when they are the recipient **or** the donor of the reservation's post. There are no write policies or write grants.
- **All four RPCs:**
  - are `SECURITY DEFINER` with `search_path = public`;
  - have `EXECUTE` revoked from `public` and granted to `authenticated`;
  - derive the caller via `current_salusalo_profile_id()` from the JWT.
- **Token check.** `create_reservation`, `cancel_reservation` and `claim_reservation` also check `is_salusalo_firebase_jwt()`. `get_donor_reservations()` and `is_reservation_recipient()` do not; they only filter by the caller's profile.
- **UI rules** mirror the server: no Reserve on your own post, Cancel only on the recipient's `Reserved` records, Mark claimed only on the donor's `Reserved` records. The server is authoritative.
- **Profiles.** `users` RLS is unchanged (users can select only their own row). Recipient names reach donors only through `get_donor_reservations()`.
- **Storage.** `food-images` SELECT is allowed for any authenticated Salu-Salo user (Phase 2 policy). **Changed in §21:** only the uploader, and people who may see, saved or chat about the listing, can read its photos (`can_read_food_image`). A recipient can therefore create signed URLs for a donor's listing image; the §3.3 change relies on this.
- **Hardening notes** (from reading the SQL; not tested):
  - **Null-unsafe ownership checks. Fixed in §21** (`20261005_bugfix_security_integrity.sql`, applied and live-checked on 2026-09-30).
    - `cancel_reservation` compares `recipient_id <> v_profile`, and `claim_reservation` compares `v_donor <> current_salusalo_profile_id()`.
    - If the caller has a valid Salu-Salo token but **no** `users` row, that comparison yields NULL, so the ownership check does not raise.
    - `create_reservation` checks `v_profile is null` explicitly; these two do not.
    - Exploiting this requires knowing a reservation UUID, which RLS does not expose to such a caller, so the risk is low.
    - A future migration could use `IS DISTINCT FROM` or an explicit null check.
  - **Donor can edit quantity and status directly. Fixed in §11:** clients have no UPDATE on `food_posts`; edits go through `update_my_food_post`.
    - The `food_posts` UPDATE policy lets a donor change **any** column of their own post, including `quantity` and `status`.
    - So the rule that only the RPCs change quantity is not enforced against the owner.
    - No UI does this today (`updateFoodPost()` is unused), but a future edit-listing feature must account for it.
  - **Silent cascade delete. Changed in §12:** clients can't delete listings; `remove_my_food_post` keeps a reserved listing as `REMOVED`, cancels its open reservations and alerts their recipients. Deleting a listing cascade-deletes its reservations, including active ones. The recipient gets no notice.

### 2.10 Food listing quantity / status

- **Quantity.** `food_posts.quantity` is the **remaining** quantity. The originally posted quantity is not stored.
- **Status values.** `AVAILABLE`, `RESERVED`, `CLAIMED`, `EXPIRED` (DB check), mirrored by the `FoodPostStatus` enum.
- **Transitions that actually happen:**

| Event | `quantity` | `status` |
|---|---|---|
| Create listing | As entered. The form requires > 0; the DB allows ≥ 0 after Phase 3. | `AVAILABLE` (required by the insert policy) |
| `create_reservation(n)` | − n | `RESERVED` if it reaches 0, otherwise `AVAILABLE` |
| `cancel_reservation` | + n | `AVAILABLE` (always) |
| `claim_reservation` | unchanged | unchanged |

- **Statuses never set.** `CLAIMED` and `EXPIRED` are **not set by any code path**. Expiry is enforced only by `expiration_date > now()` filters in the feed query and in RLS.
- **Visibility.**
  - The feed shows only `AVAILABLE`, unexpired posts.
  - The donor always sees their own posts in My Listings. After a full reservation that looks like `0 <unit>` with status "Reserved".
  - Other users cannot see a `RESERVED` post unless they hold a reservation on it.

### 2.11 Status matrix

| Item | Implemented (code) | Manually tested | Not yet verified |
|---|---|---|---|
| Create reservation (RPC + Reserve button) | Yes | Passed 2026-09-25 (§4 A/B test) | — |
| Quantity stepper + server quantity checks | Yes | Reserving the full quantity passed 2026-09-25 (§4 A/B test) | Over-quantity / stale-quantity error path |
| Atomic quantity protection (row lock) | Yes (SQL) | None recorded | Concurrency test |
| Cancellation | Yes | Passed 2026-09-25 (cancel test: Activity message, status Cancelled). Restored quantity on Home after a partial cancel, and a fully reserved listing's return to Home after its cancel, 2026-09-25 (§3.5 check 4) | — |
| Claiming | Yes | Passed 2026-09-25 (claim test: Activity message, both accounts see Claimed, B has no Cancel) | — |
| My Reservations (recipient's own only) | Yes (separate tab) | Passed 2026-09-25 (§4 A/B test) | — |
| My Donations (donor's posts only, recipient name) | Yes (separate tab) | Passed 2026-09-25 (§4 A/B test; the record showing the recipient's name was used in the claim test) | — |
| My Listings (own posts) | Yes | Create and delete listing passed for both accounts, 2026-09-25 (Phase 2 regression) | — |
| Server-side ownership rules | Yes (SQL) | None recorded | Negative tests (acting on another user's reservation) |
| Listing quantity/status transitions | Yes (SQL) | Seen on Home, 2026-09-25 (§3.5 check 4): the quantity after a partial reserve and after its cancel; a fully reserved listing left Home and came back with its full quantity after the cancel | The fully reserved listing in the donor's My Listings (quantity 0, "Reserved") |
| Reservation images | Yes, in the 2026-09-25 change (§3.3); builds | Passed 2026-09-25 (§4 A/B test: image in both reservation views) | — |
| Recommended for You real image | Yes, in the 2026-09-25 change (§3.2); builds | Passed 2026-09-25 (Recommended for You test) | — |
| Load-failure fixes (per-post image signing, queued loads, independent reservation lists, Activity messages, tab reload) | Yes, in the 2026-09-25 change (§3.4); builds | Missing-image fallback/feed check passed 2026-09-25; the §4 A/B test also passed on this build; Activity messages passed 2026-09-25 (cancel and claim tests) | Tab reload (§4, §3.4 check 5) |
| Known-issue fixes (food details data, reservation dialog text, feed reload after cancel) | Yes, in commit `85c9263` (§3.5); builds | Passed 2026-09-25 (§3.5 checks 1–4, including the "Image unavailable" header) | — |
| Migrations applied to live Supabase | Partly confirmed: the §3.4 SQL checks (2026-09-25) show `food_posts`, `reservations` and `get_donor_reservations()` | n/a | The full list of applied migrations |

---

## 3. Phase 3 work items (complete)

Phase 3 was signed off on 2026-09-25. These sections are kept as the record of that work; follow-ups carried forward are listed in §6.

### 3.1 Activity UI separation

**Desired structure**

- **My Reservations**
  - only reservations created by the signed-in recipient
  - Cancel action
- **My Donations / Manage Reservations**
  - only reservations made on the signed-in donor's food posts
  - recipient information
  - Mark Claimed action
- **My Listings**
  - the donor's own food listings

**Handoff description vs. actual code.** The handoff says `myReservations` and `donorReservations` are combined in the same Reservations UI. **The code at `d4f65fe` already keeps them separate.** That commit replaced the old single sample-data "Reservations" tab with separate tabs:

| Desired | Actual code in `ActivityScreen.kt` |
|---|---|
| My Reservations: own reservations only, Cancel | Tab `"My Reservations"`. Data flows `getMyReservations()` (filtered by `recipient_id`) → `ReservationViewModel.myReservations` → `myReservationRecords` (L277–290). Cancel is in the details dialog (L698–699). |
| My Donations / Manage Reservations: reservations on own posts, recipient info, Mark Claimed | Tab `"My Donations"`. Data flows `get_donor_reservations()` → `donorReservations` → `donorReservationRecords` (L293–307). The card shows "Recipient: <name>" (L790–797); Mark claimed is at L696–697. |
| My Listings: own listings | Tab `"My Listings"`. Data flows `getUserFoodPosts()` → `FoodViewModel.myPosts` → `myListingRecords` (L249–274). Delete action. |

- The tab list is at L505–511.
- Sample `"Reservations"` and `"My Listings"` records are filtered out (L310–312). The "Purchases" and "Claims" tabs showed demo data until §9 (in code, not yet tested).

**What remains for this item**

1. **Verified on 2026-09-25.** The §4 A/B test passed: B sees the reservation under My Reservations, A does not, and A sees it under My Donations.
2. **Decide the tab label.** It is "My Donations" in code; it could become "Manage Reservations" or "My Donations / Manage Reservations". This is a label-only change.
3. **Related issues seen in the same screen** (not fixed; fix only if in scope):
   - `ActivityScreen` never displays `reservationViewModel.message`, so success **and error** messages from Cancel and Mark claimed are not shown there. The message is not cleared either, so it pops up as a "Reservation" notice the next time any food details dialog opens (`MainActivity.kt` L2279–2287). **Changed in code on 2026-09-25 (§3.4): the Activity screen now shows and clears it. Verified on a device on 2026-09-25 (cancel and claim tests).**
   - The details dialog for reservation records ends with "This is your live Supabase listing." (L686). That text was meant for listings. **Changed in code on 2026-09-25 (§3.5). Verified on a device on 2026-09-25.**
   - The reservation lists have no loading or error state.

### 3.2 Recommended for You image

**Status (2026-09-25): verified on a device. The Recommended for You test passed: each card showed its listing's uploaded photo.**
- `RecommendationCard` now takes `imageUrl` (the call site passes `food.imageUrl`). It shows the image with `AsyncImage` and uses the demo drawable only when `imageUrl` is null. If loading fails it shows an "Image unavailable" box, as `AvailableFoodCard` does. The card's size, layout and badge are unchanged.
- `:app:assembleDebug` passes.

**Issue at `d4f65fe` (confirmed in code)**
- `MainActivity.kt` already has the real image URL: `FoodPost.toHomeFood()` (L1970–1988) sets `imageUrl = displayImageUrl` (the signed URL) and `photo = R.drawable.food_produce` (demo).
- The "Recommended for you" row (L1654–1668) passes only `photo = food.photo` to `RecommendationCard`.
- `RecommendationCard` (L2006–2082) draws `Image(painterResource(photo))` (L2027–2034), so it always shows the demo `food_produce` drawable.
- Only the two cards in the horizontal row on the Home page are affected. The "See all" recommendations page uses `AvailableFoodCard`, which already shows real images.

**Desired**
- Display the actual uploaded image: pass `food.imageUrl` into `RecommendationCard` and render it with `AsyncImage`. `coil3.compose.AsyncImage` is already imported in `MainActivity.kt`.
- Use the demo image only as a genuine fallback (no URL).
- Follow the existing `AvailableFoodCard` pattern (L2105–2126): `AsyncImage` with `onError`, the drawable when `imageUrl == null`, and an "Image unavailable" box when loading fails.

### 3.3 Reservation image

**Status (2026-09-25): verified on a device. The §4 A/B test passed, including the uploaded image in both reservation views.**
- `ReservationRepository` now has a private `withSignedImageUrl()`, a copy of `FoodRepository`'s: the same `food-images` bucket and 1-hour signed URL. Both list methods keep only the posts their reservations reference and sign those. `ActivityScreen.kt` is unchanged.
- Since the §3.4 change, both repositories use one shared `withSignedImageUrl(bucket)`, and a failed signature leaves only that post without an image instead of failing the whole list load.
- **Regression found on 2026-09-25 (§3.4):** before that fix, this signing made Account A's My Reservations load fail (A had reservations on two listings whose images are missing), and `refresh()` then skipped My Donations.
- `:app:assembleDebug` passes.

**Issue at `d4f65fe` (confirmed in code)**
- `ReservationRepository.kt` loads the related `FoodPost` rows directly: `getReservationsForMyFoodPosts()` at L40 and `toDisplays()` at L55. It never generates a signed Storage URL for the image.
- `FoodPost.displayImageUrl` is `@Transient` and defaults to null, so reservation records get `uri = null` (`ActivityScreen.kt` L287, L303).
- `RecordPhoto` (L901–935) then draws an **empty** box, with neither an image nor a placeholder. This affects both My Reservations and My Donations.
- `FoodRepository.kt` already has the correct approach in `withSignedImageUrl()` (L102–105): `bucket.createSignedUrl(path, 1.hours)` on the `food-images` bucket, then `copy(displayImageUrl = …)`.

**Desired**
- Use the existing `food-images` bucket and follow the `FoodRepository` signed-URL implementation, keeping the 1-hour expiry.
- Populate `FoodPost.displayImageUrl` before building each `ReservationDisplay`, for both the recipient and donor lists. The existing `RecordPhoto` then shows the actual uploaded image, with its error fallback.
- **Implementation notes:**
  - `withSignedImageUrl()` is `private` in `FoodRepository`. Reuse it via a small shared helper or an equivalent short extension in `ReservationRepository`.
  - Both repository methods currently fetch every visible `food_posts` row. Sign only the posts the reservations actually reference (the unused `ids` set in `toDisplays()`), so there is no Storage call for every listing in the feed.
  - No migration should be needed, because `food-images` read access for authenticated users already exists (Phase 2 policy). This comes from reading the policy; it has not been verified at runtime.

### 3.4 Listings missing from Home / My Listings, and donor reservations not showing

**Status (2026-09-25): code changes made and committed; `assembleDebug` passes. Manually verified on 2026-09-25: the missing-image fallback/feed check passed, and the §4 A/B test passed on this build, and the Activity messages passed in the cancel and claim tests. Not reported: tab reload (§4, §3.4 check 5). The live-database checks below were run by the user on 2026-09-25; the results confirm both causes (see "SQL results").**

**Reported in live tests (2026-09-25)**
- A listing created by Account A appeared in A's My Listings but not on Home, for either account.
- A listing created by Account B appeared neither on Home nor in B's My Listings.
- After B reserved all 8 of A's listing (B's record showed correctly, with its image), A's My Donations was empty.

**Diagnosis**
- Confirmed in code:
  - Each list load signed every post's image, one request at a time, with no per-post error handling. `createSignedUrl` throws when the object is missing or the request times out (supabase-kt 3.5.0, 10 s request timeout), so one bad image failed the whole feed, My Listings or reservation list.
  - On failure the ViewModels kept the previous list. Home showed an error only when its list was empty; Activity showed none.
  - `createFoodPost()` deleted the uploaded image when anything after the upload failed, including signing the row it had already inserted. That can leave a listing whose image no longer exists. While it is `AVAILABLE` and unexpired, it fails every account's feed, and it always fails its owner's My Listings.
  - `createPost()` reloaded the feed before My Listings, so a failed feed reload skipped My Listings. Loads requested while another `FoodViewModel` request ran were silently dropped.
  - `ReservationViewModel.refresh()` loaded My Reservations first; if that failed, My Donations was never requested. Errors went only to `message`, which Activity did not show. The UI does not filter donor records.

**SQL results (run by the user on 2026-09-25; screenshots, not copied here)**
- Check 1: six listings. Two of Account B's listings (both named "lemon dou", quantity 1, created 2026-09-24 06:55 and 07:22 UTC) are `AVAILABLE`, unexpired, and have `image_exists = false`. Every other listing's image exists.
- Check 2: `get_donor_reservations()` exists. The visible part of its result type matches the repo; the rest was cut off in the screenshot.
- Check 3: Account B holds three `RESERVED` reservations on Account A's "glasses" listing (1 + 1 + 8, so it started at 10 and is now `RESERVED` with quantity 0). Account A holds four `CANCELLED` reservations on Account B's "lemon dou" listings.

**Confirmed causes (SQL results plus the code before §3.4)**
- Listings: the two "lemon dou" listings are in every feed and their images cannot be signed, so every feed load failed for both accounts. They are also in B's My Listings, so B's list failed; all of A's listings have images, so A's list loaded.
- Donor reservations: `refresh()` loaded A's My Reservations first. That list includes the four cancelled "lemon dou" reservations, and the §3.3 change signs those listings' images, so the load threw and My Donations was never requested. The donor RPC was not the cause.
- Not confirmed: how the two images disappeared. The app's create cleanup does not fit the first listing (A reserved it about 20 minutes after it was created, which required its image to sign then). The app's delete flow does not fit either, because both rows and their reservations still exist.

**Code changes** (paths relative to `app/src/main/java/com/example/salusalo/`)
- `data/FoodRepository.kt`
  - `withSignedImageUrl(bucket)` is now a shared top-level function. A failed signature leaves only that post's `displayImageUrl` null; coroutine cancellation is still rethrown. The existing null-URL fallback then applies: the demo drawable on Home cards and an empty photo box in Activity.
  - `createFoodPost()` deletes the uploaded image only when the insert itself fails. Decoding and signing run after the try/catch.
- `data/ReservationRepository.kt`: uses the shared `withSignedImageUrl(bucket)`; its private copy is removed.
- `viewmodel/FoodViewModel.kt`
  - `loadAvailablePosts()` and `loadMyPosts()` requested while a request is running are queued and run right after it, one at a time, My Listings first. Each queued load succeeds or fails on its own; `error` keeps the first failure.
  - `createPost()` reloads My Listings, then the feed, as queued loads.
  - `createPost()` and `deletePost()` are still ignored while any request is running (unchanged).
- `viewmodel/ReservationViewModel.kt`: `refresh()`, and the reload after cancel or claim, load My Reservations and My Donations independently and report the first failure through `message`.
- `ActivityScreen.kt`
  - Shows `reservationViewModel.message` in a "Reservation" dialog and clears it, so it no longer appears later in the food details dialog.
  - Tapping the My Reservations or My Donations tab reloads the reservation lists.
- No SQL, RLS, RPC, authentication or storage-policy changes.

### 3.5 Known-issue fixes (after Phase 3 sign-off)

**Status (2026-09-25): verified on a device.** The change was made in a Claude Code web session that had no Android SDK (commit `85c9263`, branch `claude/focused-dirac-oy16ck`). `assembleDebug` then passed locally, and the developer ran the §4 "Checks for the §3.5 fixes" on the phone: all four passed. No listing with a missing image was left on Home, so check 2 and the "Image unavailable" header were run with a staged test listing. Merged into `master` through pull request #1 on 2026-09-25.

- **Reservation dialog text** (`ActivityScreen.kt`): the details dialog's closing line now depends on the record. My Listings keeps "This is your live Supabase listing."; My Donations shows "This is a live reservation on your listing."; My Reservations shows "This is your live reservation."
- **Food details** (`MainActivity.kt`, `FoodDetailsScreen`):
  - The header shows the listing's signed image, with the `AvailableFoodCard` fallback: the demo drawable when there is no URL, an "Image unavailable" box when loading fails.
  - The description is the post's own `description` (`HomeFood` now carries it), or "No description provided."
  - "Pickup Address" shows the post's pickup location instead of "Del Rosarrio, Balilihan".
  - Unchanged: allergens ("Not provided — ask the provider", since allergens are not saved), Storage Note, Pickup Window, and the preview-only `FoodActionsDialog`, whose map view still has its own demo address (`FoodActions.kt`).
- **Home feed after cancel** (`ActivityScreen.kt`): when the Activity screen receives the "Reservation cancelled" message, it also calls `foodViewModel.loadAvailablePosts()`, so Home shows the restored quantity. Claim changes neither quantity nor status, so it does not reload the feed. There is still no realtime update across devices.
- **Home greeting** (`MainActivity.kt`, `HomeScreen`): "Good evening, Mika 👋" is replaced by "Good morning / Good afternoon / Good evening, <first name> 👋". The first name is the first word of the Supabase profile name (no name if it is blank). The period uses the device's local time (morning 05:00–11:59, afternoon 12:00–17:59, evening 18:00–04:59) and is re-read every minute while Home is open. The other demo "Mika" values (Profile screen default, preview-only "Requesting as") are unchanged. This change came after pull request #1 (commit `851e8cb`), so it was not part of the §3.5 test. It was built at `266325b`, and the Phase 4 test on 2026-09-25 showed "Good evening, <first name> 👋". The morning and afternoon greetings were not checked.
- No SQL, RLS, RPC, authentication or storage-policy changes.

**Live-database checks** (read-only; run in the Supabase SQL Editor)

```sql
-- 1. Listings whose image object is missing
select p.created_at, u.name as donor, p.name as listing, p.status, p.quantity,
       p.expiration_date > now() as unexpired, p.image_url, o.id is not null as image_exists
from public.food_posts p
join public.users u on u.id = p.donor_id
left join storage.objects o on o.bucket_id = 'food-images' and o.name = p.image_url
order by p.created_at desc;

-- 2. Does the donor RPC exist, and what does it return?
select p.oid::regprocedure as function, pg_get_function_result(p.oid) as returns
from pg_proc p join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public' and p.proname = 'get_donor_reservations';

-- 3. Reservations with their donor and recipient
select r.created_at, r.status, r.quantity, fp.name as listing, d.name as donor, u.name as recipient
from public.reservations r
join public.food_posts fp on fp.id = r.food_post_id
join public.users d on d.id = fp.donor_id
join public.users u on u.id = r.recipient_id
order by r.created_at desc;
```

- **Check 1:** a row with `image_exists = false` is a listing that now loads without its image. If it is `AVAILABLE` and unexpired, it explains the feed failure. Its owner can delete it from My Listings.
- **Check 2:** no row, or a result other than `TABLE(id uuid, food_post_id uuid, recipient_id uuid, quantity integer, status text, created_at timestamp with time zone, updated_at timestamp with time zone, recipient_name text)`, means the existing `supabase/migrations/20260924_phase3_donor_recipient_names.sql` must be run manually in the SQL Editor.
- **Check 3:** B's reservation should list A as the donor.

---

## 4. Manual test: Account A / Account B

### Preconditions (not verifiable from the repo)

- All four migrations are applied to the live Supabase project in order:
  1. `20260921_phase1_foundation.sql`
  2. `20260923_phase2_food_posts.sql`
  3. `20260924_phase3_reservations.sql`
  4. `20260924_phase3_donor_recipient_names.sql`
- Supabase Third-Party Auth is configured for Firebase project `salusalo`, and both accounts receive the `role: authenticated` custom claim.
- A current debug build is installed, with a valid `local.properties` (`SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`).
- There are two accounts with different emails. Use two devices/emulators, or sign out between steps (Profile → Log out).
- There is no pull-to-refresh:
  - **Feed:** the Home feed reloads on sign-in, after a reservation, and when leaving Post with its on-screen **Back** button.
  - **Activity:** the Activity tab reloads each time it is opened.

### The test

**Account A:**
- creates a food listing (Post → fill in the required fields → Post Listing)
- quantity = 8
- uploads an image

**Account B:**
- logs in
- reserves all 8 (Home → open A's listing → tap "+" until 8 → Reserve)

**Expected:**
- B sees it under **My Reservations**.
- A does **NOT** see B's reservation under **My Reservations**.
- A sees B's reservation under **My Donations / Manage Reservations** (the tab label in code is "My Donations").
- The uploaded image appears correctly in both relevant views.

**Prediction from the code, not a test result:**
- The first three expectations should pass.
- With the §3.3 change, both reservation cards should show the uploaded image. At `d4f65fe` they rendered none.
- For §3.2, check the Home "Recommended for you" row **before** B reserves all 8. The row shows only the first two posts of the AVAILABLE feed, in no fixed order, and a fully reserved post leaves that feed. Each card in the row should show its listing's uploaded photo, not the demo produce image.

### Optional follow-up checks (for Phase 3 sign-off)

- A → My Listings shows the listing with quantity 0 and status "Reserved", and it is gone from B's feed.
- B cancels: B's record shows "Cancelled", and the listing returns to the feed with 8 available.
- B reserves again, then A taps Mark claimed: both see "Claimed", and B no longer gets a Cancel action.
- With a stale details screen, B requests more than remains: "Not enough food available. Only N remains."
- A opens their own listing: the Reserve button shows "Your listing / You cannot reserve it".

### Checks for the §3.4 fixes

Run the §3.4 SQL checks first, then install a build that contains §3.4.

0. **Existing data (from the 2026-09-25 SQL results):** Home shows five listings for both accounts, and the two "lemon dou" listings use the demo produce photo. A's My Donations shows three `Reserved` records on "glasses" (8, 1, 1) with "Recipient: <B's name>" and the photo. A's My Reservations shows four `Cancelled` "lemon dou" records with no photo. B's My Listings shows all four of B's listings.
1. **Feed with a bad image** (only if check 1 found an `AVAILABLE`, unexpired listing with `image_exists = false`): Home loads for both accounts without "Could not load listings. Try again."; that listing shows the demo produce photo; its owner's My Listings loads.
2. **A's new listing:** A creates a listing with a photo, then taps "Back to home": it is on Home with its photo, and Activity → My Listings shows it. After switching to B, B's Home shows it.
3. **B's new listing:** step 2 with the accounts swapped. Also, right after "Listing published", go straight to Activity with the bottom bar: My Listings shows it.
4. **Donor view:** B reserves all 8 of A's listing. A → Activity → My Donations shows it with "Recipient: <B's name>" and the photo. If a "Reservation" dialog with an error appears instead, record its exact text (see check 2).
5. **Tab reload (two devices):** A keeps Activity open while B reserves, then taps My Donations: the new reservation appears.
6. **Messages:** A taps Mark claimed: a "Reservation" dialog on Activity says "Reservation marked as claimed." After OK, opening any listing on Home shows no leftover notice. Same for B → Cancel ("Reservation cancelled. The quantity is available again.").

### Checks for the §3.5 fixes

1. **Food details:** open a listing that has a photo and a description. The header shows the uploaded photo, the text is the listing's description, and "Pickup Address" is its pickup location. A listing without a description shows "No description provided."
2. **Missing image:** open one of the "lemon dou" listings (image missing from Storage). The header shows the demo produce photo. If no such listing is on Home, stage one; this also covers the "Image unavailable" header:
   1. Account A posts a listing named `IMAGE TEST` with any photo.
   2. Upload a text file named `broken.jpg` to the top level of `food-images` (dashboard → Storage). Then run `update public.food_posts set image_url = 'broken.jpg' where name = 'IMAGE TEST' returning id, name, image_url;`, which should return 1 row.
   3. Reload Home (Post → **Back**). The card and the details header show "Image unavailable".
   4. Delete `broken.jpg` in Storage and reload Home again. The card and the details header show the demo produce photo.
   5. Clean up in this order: run the SQL below to point the listing back at its photo, open Activity (which reloads My Listings), then delete `IMAGE TEST` there. Deleting it also removes its photo.

   ```sql
   update public.food_posts p
   set image_url = o.name
   from storage.objects o
   where p.name = 'IMAGE TEST'
     and o.bucket_id = 'food-images'
     and o.name like ('%/' || p.id::text || '/%')
   returning p.id, p.name, p.image_url;
   ```
3. **Dialog text:** in Activity, open one record per tab. My Listings ends with "This is your live Supabase listing.", My Reservations with "This is your live reservation.", My Donations with "This is a live reservation on your listing."
4. **Feed after cancel:** B reserves part of A's listing, cancels it in Activity → My Reservations, then goes to Home with the bottom bar. The listing shows the restored quantity (and is back on the feed if B had reserved all of it).
5. **Greeting:** sign in as A: Home says "Good morning/afternoon/evening, <A's first name> 👋" matching the device time. Sign in as B: it shows B's first name.

### Phase 4 test: messaging and alerts (Account A / Account B)

Preconditions: `20260926_phase4_messaging_notifications.sql` has been run in the SQL Editor, `messages` and `notifications` appear under Database → Publications → `supabase_realtime`, and a build with Phase 4 is installed. Two devices make the live parts visible; with one device, sign out between steps and check the result after signing in.

1. **Start a chat:** B opens A's listing → "Message Donor" → sends "Is this still available?". A (Home open) sees the chat icon badge and the bell badge increase without refreshing; Home → chat icon lists the thread with B's name, the listing and an unread count.
2. **Live reply:** A opens the thread and replies. B, with the chat open, sees the reply appear without refreshing. A's thread shows no unread count after opening.
3. **Prefilled questions:** B taps the Allergens row on A's listing: the chat opens with "Hi! What allergens does <listing> contain?" in the input.
4. **Own listing:** A opens their own listing: the button reads "Your listing", and tapping it (or Allergens/Storage) shows "You cannot message yourself…".
5. **Reservation alerts:** B reserves part of A's listing → A gets "New reservation" in Alerts. B cancels → A gets "Reservation cancelled". B reserves again and A marks it claimed → B gets "Reservation claimed". Tapping a reservation alert opens Activity on My Donations (A). Since §9 (not yet tested), B's "Reservation claimed" alert opens that reservation in Purchases or Claims instead of My Reservations.
6. **Chat from Activity:** A → Activity → My Donations → record → "Message recipient", and B → My Reservations → record → "Message donor": both land in the same thread as step 1.
7. **Preferences:** A → Profile → Notifications (Profile → Preferences → Notifications before §13) → turns off "New messages" → Save ("Preferences saved to your account."). B sends a message: it arrives in A's chat, but A gets no new MESSAGE alert. Turn it back on and Save, leave the page and reopen it: the switch is still on (§8.7 bug 1). While the page loads, it shows "Loading your alert settings…" rather than switches (§8.7 bug 2). Repeat both checks in Alerts → settings icon.
8. **Mark read:** Alerts → "Mark shown as read": the unread count and the bell badge go to 0 and stay 0 after reopening the app.
9. **Token refresh:** leave the app open on Home for more than an hour, then have the other account send a message: it still arrives live. (Realtime is re-authenticated every 5 minutes; §8.3.)
10. **Regression:** because the Ktor engine changed to OkHttp, repeat the Phase 1/2 regression and the A/B reservation test above.

### Results log

| Date | Build / commit | Tester | Result | Notes |
|---|---|---|---|---|
| 2026-09-25 | `d4f65fe` + uncommitted §3.2–§3.4 changes | Developer | Passed | Phase 1: authentication/profile regression (reported as one result). |
| 2026-09-25 | same | Developer | Passed | Phase 2, Account A: Home/feed, create listing, delete listing. |
| 2026-09-25 | same | Developer | Passed | Phase 2, Account B: Home/feed, create listing, delete listing. |
| 2026-09-25 | same | Developer | Passed | Phase 3: the A/B reservation test ("The test" above). |
| 2026-09-25 | same | Developer | Passed | Phase 3: missing-image fallback/feed (check 1 in "Checks for the §3.4 fixes"). |
| 2026-09-25 | same (no code changes since 10:04) | Developer | Passed | Phase 3: cancel (Account B). A "Reservation" dialog on Activity showed "Reservation cancelled. The quantity is available again."; the record then showed Cancelled with no Cancel button; no leftover popup on Home. |
| 2026-09-25 | same | Developer | Passed | Phase 3: mark claimed (Account A). A "Reservation" dialog on Activity showed "Reservation marked as claimed."; the record then showed Claimed with no Mark claimed button; B saw Claimed with no Cancel button; no leftover popup on Home. |
| 2026-09-25 | same | Developer | Passed | Phase 3: Recommended for You image. Each card showed its listing's uploaded photo, matching the "Available near you" list. |
| 2026-09-25 | `85c9263` (branch `claude/focused-dirac-oy16ck`) | Developer | Passed | §3.5 checks 1, 3 and 4. Food details showed the listing's own photo, description and pickup location, and "No description provided." for a listing without one. The Activity dialogs ended with the new lines in My Reservations and My Donations; My Listings kept its line. After B cancelled a partial reservation, Home showed the full quantity again without a new sign-in. After B reserved a whole listing it left Home, and after the cancel it came back with its full quantity. |
| 2026-09-25 | same | Developer | Passed | Regression on this build: switching accounts (sign-out, sign-in); create a listing with an image (shown on Home); delete a listing (gone from Home and My Listings); own listing shows "Your listing / You cannot reserve it"; reserve (Home quantity dropped); the cancel and Mark claimed messages. |
| 2026-09-25 | same | Developer | Passed | §3.5 check 2 with a staged listing (`IMAGE TEST`, steps under check 2). While its photo path pointed at a file that is not an image, the Home card and the details header showed "Image unavailable". After that file was deleted, both showed the demo produce photo. |
| 2026-09-25 | `266325b` (branch `claude/focused-dirac-oy16ck`), migration applied | Developer | Passed, with one failure | Phase 4 test on one phone, signing out between accounts. Passed: greeting "Good evening, <first name> 👋"; the chat icon and the Messages inbox; "Message Donor" chat; the prefilled Allergens question; "Your listing" on your own listing; "Message donor"/"Message recipient" from Activity; the New reservation, Reservation cancelled and Reservation claimed alerts, each opening the right Activity tab; turning "New messages" off suppressed message alerts; "Mark shown as read"; deleting the listing removed its thread. **Failed:** turning "New messages" back on did not save (§8.7 bug 1). |
| 2026-09-25 | same | Developer | Passed | Regression on the Phase 4 build (OkHttp engine): feed, create a listing with a photo, reserve, cancel, claim, delete. |
| 2026-09-25 | same | Developer | Partly checked | Phase 4 live test on two phones. Realtime connected ("Connected to realtime websocket!"). `realtime.subscription` held one `notifications` subscription per phone, with `claims_role` = authenticated and issuer `https://securetoken.google.com/salusalo`. Replaying the RLS check as account A passed: with `request.jwt.claims` set from the subscription and role authenticated, `is_salusalo_firebase_jwt()` was true, `current_salusalo_profile_id()` returned A's profile, and A's alerts were visible. The API logs show the sender's open chat receiving its own message live (`send_message`, then `mark_conversation_read` about 1 s later). The "live alert" step failed only because A's `message_alerts` was still false (bug 1). **Not yet confirmed:** live chat between the two phones, the live reservation alert, and the token refresh after more than 1 hour. |
| 2026-09-25 | `c7323b8` (branch `claude/focused-dirac-oy16ck`; includes the §8.7 fixes, `5c1e89c`) | Developer | Passed | §8.7 retest, Account A. Profile → Preferences → Notifications first showed "Loading your alert settings…" with Save preferences greyed out, then the saved switches. "New messages" turned on, saved ("Preferences saved to your account.") and reopened was still on; turned off and on again, each state was kept after reopening. Alerts → settings icon: "Loading your alert settings…", then "New messages" on; "Reservation updates" turned off and back on, each state kept after reopening. |
| 2026-09-25 | same | Developer | Passed | Phase 4 live test on two phones, both on Home. B sent a message from one of A's listings: A's chat icon and bell badges went up without anyone touching A's phone. With the chat open on both phones, each side's messages appeared on the other phone by themselves. With A back on Home, B reserved 1: A's bell badge went up by itself and Alerts showed "New reservation". |
| 2026-09-25 | same | Developer | Passed | Token refresh (Phase 4 test step 9). A's app stayed open on Home for more than 65 minutes (the steps asked for the screen to stay on, with the phone charging and unused). B then sent a message, and A's chat icon and bell badges went up live. |
| 2026-09-25 | same | Developer | Passed | Sign-up with the OkHttp engine, on the second phone: a new account was created, the role-claim script (outside this repo) was run, and the account signed in again. Reported as done. The password-reset email was not reported. |
| 2026-09-25 | live project (SQL Editor); script `supabase/tests/phase4_live_safety_check.sql` | Developer | Passed | Live safety check (negative tests): 21 passed, 0 failed, 0 skipped. It used the most recent chat, with its donor and recipient (the two test accounts), and the newest other account as the outsider; the three sanity checks confirmed the simulated sign-ins. Refused as expected: the outsider reading the chat (directly or through `get_my_conversations`), sending in it, marking it read, opening a chat from someone else's reservation, and seeing or changing other people's alerts and alert settings; a chat member inserting or editing messages directly, creating alerts directly, calling `notify_user`, starting a chat about their own listing, or reading the other person's `users` row; a token from another Firebase project; no login (all four tables, and `send_message`). The script ends with an error on purpose, which rolled everything back. |
| 2026-09-26 | `8908548` (branch `claude/phase1-demo-fixes`, merged as PR #3) plus the uncommitted "starts unchecked" change; both migrations applied | Developer | Passed | Phase 1 demo fixes (§10) on two phones, from a summary checklist of §10.4, reported as one result ("phase 1 demo fixes passed"). The checklist covered: the Welcome and sign-in texts with only "Continue with Google"; Keep me signed in (ticked: still signed in after closing the app; unticked: signed out); Profile with no location and a real Member since; the name change with its errors, then saved, with the Home greeting updating at once; a photo kept after signing out and in, then replaced by a second photo; the password change (a wrong current password and a too-short new one refused, then changed; afterwards the old password failed and the new one worked); Settings without Privacy or the devices list; B seeing A's new name in Messages and My Donations and sending a chat message; the message arriving live; reserving and cancelling one of B's listings; renaming A back; a new Google account (closing the picker shows no error; the first sign-in fails until the role script runs, then works; the Gmail address in Account Settings; no password to change). The checklist's regression row (email sign-up, the password-reset email, create and delete a listing, the feed, reserving, chat and alerts) was not reported separately. |
| 2026-09-26 | same build | Developer | Passed, with one known issue | Purchases, Claims and listing prices: the §9.4 two-phone test, given in parts (§9.6). Every step passed except the "Reserved" dialog after Reserve (steps 8, 9, 19), which never appears: a Phase 3 bug present since `d4f65fe`, left for the Phase 3 demo fixes. Step 4 passed on a redo (the first try typed the price into Quantity). A0/A1/B0/B1 = 2/2/2/2; the totals then matched (4 and 7). The new price showed on Home after a reload. |
| 2026-09-26 | `4b6aac6` (branch `claude/phase2-demo-fixes`, PR #5); migration `20260929_phase2_listing_details.sql` applied | Developer | Passed | Phase 2 demo fixes (§11): the §11.4 two-phone test, steps 1–25, given in parts in the order §11.4 now shows. Every step passed. Observed: B's saved `EXPIRY TEST`, once expired, showed only "No longer available" and "Remove", with no name or photo (step 21; §11.5); after the edit, My Donations showed the new name with the old price, "2 packs • ₱40.00" (step 17), while B's new reservation of 4 used the new price, "4 packs • ₱100.00" (step 22); deleting `GALLERY TEST 2` also deleted B's two reservations on it (step 23; the known cascade, §9.6); the "Reserved" dialog never appeared after Reserve (the known §9.6 bug). |
| 2026-09-26 | same | Developer | Passed | Regression round on the same build (§11.4 steps 26–39): a one-photo donation posted and later a one-photo listing deleted; A's renamed display name shown as the donor name on B's Home after a reload, then renamed back; the Donation, For Sale, Expiring Soon and All filters; reserve, cancel ("Reservation cancelled. The quantity is available again."), reserve again and Mark claimed ("Reservation marked as claimed."), with the New reservation (twice), Reservation cancelled and Reservation claimed alerts; a chat message both ways, the reply arriving live in B's open chat, with the message alerts; the claimed free reservation in Claims and gone from My Reservations. Not re-run: email sign-up, Keep me signed in, Google sign-in, the profile photo, the password change, a priced claim in Purchases, and the password-reset email (never tested). |
| 2026-09-26 | live project (SQL Editor), after the test | Developer | Passed | Read-only check of the Phase 2 migration: all 8 rows true. The 6 new `food_posts` columns, `update_my_food_post`, `get_listing_donor_names` and `saved_listings` exist; `authenticated` has no UPDATE privilege on `food_posts` and no UPDATE policy remains, and still has INSERT; `authenticated` can execute both functions, `anon` cannot (§11.6). |
| 2026-09-26 | live project (SQL Editor): `20260930_phase3_demo_fixes.sql`, then `supabase/tests/phase3_demo_fixes_check.sql` in the same run | Developer | Passed | Phase 3 demo fix migration applied; the read-only check returned 13 of 13 PASS: exactly one check constraint each on `food_posts.status` (allows `REMOVED`) and `notifications.type` (allows `RESERVATION_REMOVED`), `reservations.cancel_reason` with its check, no DELETE or UPDATE policy or privilege on `food_posts`, both new functions SECURITY DEFINER and `authenticated`-only, the `REMOVED` checks in `create_reservation`, `update_my_food_post`, `notify_reservation_change` and the `saved_listings` insert policy, `notify_user` and `notify_reservation_change` not executable by clients, no UPDATE on `saved_listings` (§12.6). |
| 2026-09-26 | live project (SQL Editor); script `supabase/tests/phase3_live_safety_check.sql` | Developer | Passed | 14 passed, 0 failed, 0 skipped. Listing checked: `OLD TEST` (account A), outsider: the newest other account. The three sanity checks confirmed both simulated sign-ins and that A's own `remove_my_food_post` would have deleted `OLD TEST` (never reserved; undone at once). Refused as expected: A's direct DELETE ("permission denied for table food_posts"), the outsider's removal ("You can delete only your own listing."), removal and `get_my_saved_listing_names` without a login (permission denied), a token from another Firebase project ("Not authenticated"). The outsider's saved names were their own only. With the listing marked `REMOVED`: A's edit ("This listing was removed and can no longer be edited."), the outsider's reservation ("The donor removed this listing."), new chat ("This listing is no longer available"), bookmark (row-level security) and reading the listing or its donor name (0 rows) were refused. The closing error rolled everything back. |
| 2026-09-27 | `24fe9f2` (branch `claude/phase3-demo-fixes`); migration applied | Developer | Passed | Phase 3 demo fixes (§12): the §12.4 two-phone test, 32 steps, given in parts. Steps 1–15 were reported in a first session and step 16 (stale quantity, B signed in on both phones) and steps 17–32 in a second session, both on 2026-09-27. Every step passed, including: no Buy Now / Claim Now; the review page and the "Reserved" receipt with the total and "Your message was sent to <A>." (the missing "Reserved" dialog of §9.6 is gone); the Pickup Window row opening the chat; the real photo on the Pickup Address page; the stale reserve showing "Not enough food available. Only 0 remains." on the review page; "Listing deleted." for a never-reserved listing; "Listing removed. 2 open reservations were cancelled…" and "…1 open reservation was cancelled…"; one "Listing removed" alert per listing on B's phone, opening the cancelled reservation in My Reservations; Purchases, Claims, the Activity totals and the Profile counts keeping the claimed reservations of removed listings; no "Reservation cancelled" alert for A; the chat about a removed listing still working; the name and photo on an expired saved listing's card; the short Post Listing and reserve errors in flight mode. Observed: "Listing: Removed by you / by the donor" appears in a record's details dialog, not on its card (as coded). Not re-run: email sign-up, Google sign-in, Keep me signed in, the profile photo, the password change, the photo gallery, the Delete, Cancel and Mark claimed error texts, dates on a phone set to another language, and the password-reset email (never tested). |
| 2026-09-27 | live project (SQL Editor): `20261001_phase4_demo_fixes.sql`, then `supabase/tests/phase4_demo_fixes_check.sql` in the same run | Developer | Passed | Phase 4 demo fix migration applied; the read-only check returned 9 of 9 PASS: `get_my_response_rate()` with its result columns, SECURITY INVOKER, STABLE and `search_path=public`, executable by `authenticated` only; the two helpers it calls executable by `authenticated`; no INSERT, UPDATE or DELETE for clients on `conversations`, `messages` and `notifications`, which have SELECT policies only; `notify_user` not executable by clients; `messages` and `notifications` in `supabase_realtime`; no UPDATE on `users` and no UPDATE or DELETE on `food_posts` for clients (§13.6). |
| 2026-09-27 | `3288d02` (branch `claude/phase4-demo-fixes`); migration applied | Developer | Passed | Phase 4 demo fixes (§13): the short §13.4 two-phone test, 13 steps, sent all at once. The developer asked for fewer steps than the 30-step draft, and reported one result ("test passed"). Covered: a new free listing `P4 FREE`; the Profile header and caption texts; Response Rate as a real value, and its page; the gear opening Settings; Notifications with only "New messages", "Reservation updates" and "Notification sound". Also covered, the bell and badges: the Profile bell opening Alerts, its dot gone after "Mark shown as read" and back after new alerts; no Refresh button in Messages. Live, without touching the phone: with Messages open, B's new chat appearing at the top and an older chat moving up, both with an unread count of 1, and the notification sound playing. The chat badge counted the unread messages and went back down once the chats were opened. No sound for a message in the open chat. Both Response Rate numbers were 1 higher after B started a chat and A replied. "Notification sound" turned off stayed off after signing out and in again; the next message then made no sound, but the badge still went up live. In the background: no sound. After reopening from Recents, the badge and the inbox included the message sent meanwhile: the note of a reservation B made through the review page, with the "Reserved" receipt. A also had the "New reservation" alert. Not covered: see §13.6. |
| 2026-09-27 | live project (SQL Editor): `20261002_phase5_reviews_reports.sql`, then `supabase/tests/phase5_reviews_reports_check.sql` in the same run | Developer | Passed | Phase 5 part 1 migration applied; the read-only check returned 18 of 18 PASS (§14.6). |
| 2026-09-27 | live project (SQL Editor); script `supabase/tests/phase5_live_safety_check.sql` | Developer | Passed | 27 passed, 0 failed, 0 skipped. Reservation checked: `P3 FREE` (claimed), with the two test accounts as recipient and donor and the newest other account as the outsider. The sanity checks confirmed the simulated sign-ins, a review and its change (still one row), and a listing report. Refused as expected: reviews by the outsider and by the donor, ratings 0 and 6, a 501-character comment, a RESERVED or CANCELLED reservation, no login and another project's token; reading or writing `reviews` and `reports` directly; a second report of the same listing, reporting your own listing or yourself, a chat you are not in, a wrong or unknown reason, "Other" without details, 1001 characters of details and an 11th report in 24 hours. After the listing was marked `REMOVED`, the outsider got no donor details or reviews and could not report it, while the recipient still got them. `sync_my_email_verified` set only the caller's own value from the token (true, then false) and changed nothing else; a direct UPDATE of `users` was refused; a new profile inserted as verified was stored unverified; `get_listing_donor_names` still worked. The closing error rolled everything back. |
| 2026-09-28 | `3bd038c` (branch `claude/phase5-reviews-reports-v6ahqn`); migration applied | Developer | Passed | Phase 5 part 1 (§14): the §14.4 two-phone test, 15 steps, sent in two parts and reported as passed (steps 1–9 at 00:10 +08:00, then steps 10–15). Covered: A's grey "Email not verified" chip and the real Verification page (not confirmed yet, the verification email sent, then confirmed after its link, with the chip turning green); B's Google account showing "Email verified"; no demo verification or rating texts left; `P5 FREE` posted and reserved twice through the review page; no Rate button on a Reserved record; "Report this listing" with its validation texts, then "You already reported this."; the alerts and Mark claimed; A reporting B from the chat ("Didn't show up"); B rating A from the "Reservation claimed" alert (4 stars, then changed to 5), with "★ 4.0 (1)" and then "★ 5.0 (1)" on the card, the details and the Donor row after the feed reloads, and the Donor page's badge, rating and review; B reporting A from the chat, then a refused Donor-page report; A's Profile rating, panel and Ratings & Reviews page; A's own card after signing out and in, with no Report buttons on A's own listing; the reports query showing the three reports. During the test A's account was deleted in the Firebase console and re-created, and its profile was re-linked by hand (§14.6). Not covered: the "Left out" list in §14.4. |
| 2026-09-28 | live project (SQL Editor): `20261003_phase5_maps.sql`, then `supabase/tests/phase5_maps_check.sql` in the same run | Developer | Passed | Phase 5 part 2 migration applied; the read-only check returned 11 of 11 PASS (§15.6). |
| 2026-09-28 | live project (SQL Editor); script `supabase/tests/phase5_maps_live_safety_check.sql` | Developer | Passed | 22 passed, 0 failed, 0 skipped. The donor of the newest listing and the newest other account (the two test accounts) were the donor and the outsider. The sanity checks confirmed the simulated sign-ins, a listing posted through RLS with a pin sent with 7 decimals and stored as 9.647812, 123.854013, a listing without a pin, moving a pin together with a name change in one call, and adding a pin to a listing without one. Refused as expected: on insert, a half pin, latitude 91 and longitude 181 (the check constraint); on edit, one coordinate, 90.5, −180.5, NaN and a blank name sent with a new pin, each leaving the pin unchanged; the outsider, an expired listing, a `REMOVED` listing, a missing listing, no login and another project's token; a direct UPDATE by the donor. An edit with both pin arguments NULL and an edit through the old `update_my_food_post` both kept the pin. The outsider saw the pin while the listing was available and nothing once it had expired, while the donor still did. The closing error rolled everything back. |
| 2026-09-28 | `47ed894` (branch `claude/phase5-maps`); migration applied | Developer | Passed | Phase 5 part 2 (§15): the §15.4 two-phone test, 12 steps, sent in two parts (steps 1–7, then 8–12) and reported as passed. Covered: the pin picker on Post (the note, the map starting at Tagbilaran with the attribution, "Place the pickup pin on the map.", placing the pin by tapping, and "Use my location" with Android's prompt and Precise); B's Nearby with the prompt, "Food within 5 km" and the filter help; distances on the cards, Nearest (listings without a pin last) and Name; the details' distance line; the Pickup location page's map, attribution link, Directions (Google Maps), new pickup instructions and "Ask for exact location"; a listing without a pin (no distance or map, "Search area in Maps"); Reserve through the review page and "Directions" in My Reservations; A's alerts and chat reply; moving a pin on Edit, and the "no pickup pin yet" text on an older listing; B seeing the moved pin after a reload; location access removed on B (no distances, the Nearby and Nearest line, the map and Directions still working); location turned off on A (the "Use my location" and Nearby texts, then back on); Mark claimed, with no Directions on the Claims record. Not covered: the "Left out" list in §15.4. |
| 2026-09-28 | live project (SQL Editor): `20261004_profile_bio.sql`, then `supabase/tests/profile_bio_check.sql` in the same run | Developer | Passed | Profile bio migration (Mika's PR #10) applied; the read-only check returned 6 of 6 PASS (§16.3). |
| 2026-09-28 | `b2b04ca` (branch `mika/navigation-activity-settings`); migration applied | Developer | Passed | Mika's Profile redesign and settings (§16): a 6-step two-phone test, reported as passed. Covered: A's new Profile layout; Edit Profile saving a new name and a bio; the Settings hub; Food preferences turning Home's row into "For your preferences" and back; the new "About distance" note; Alerts in the bottom bar on Activity; Nearby distances; B seeing A's new name on the cards and the Donor row; B's new Profile, Log out and signing in again. Not covered: the list under "Not tested" in §16.3. |
| 2026-09-28 | `7feb5b5`, then `e0f2d59` for step 9 (branch `claude/cleanup-after-audit`); no migration | Developer | Passed | Fixes after the second demo audit (§17): the §17.4 two-phone test, 9 steps, sent in two parts. Covered: the Home profile circle and a changed photo; About the filters; Learn more → How Salu-Salo works; Check your listing (quantity, pickup window, photos, missing allergens); the Start now errors and button; required allergens on Post and on an older listing's Edit; My posts in local time; the Edit Listing field above the keyboard; the welcome pages after Clear storage, their Sign in link and Sign In on the next start; pull to refresh; Read more; the aligned summary columns; the full-screen photos; the Review reservation note and the chat box above the keyboard; photos in Messages, My Reservations, Saved Listings and My Donations; Mark claimed; Back on Home closing the app (with "Keep me signed in" unticked the next open showed Sign In, as designed), Log out and signing in again. Not covered: the list under "Not tested" in §17.6. |
| 2026-09-28 | live project: Google Cloud project `salusalo` and the Supabase dashboard | Developer, checks by Claude | Passed | §18.2 setup: the custom role `salusaloRoleSetter` (`firebaseauth.users.get`, `firebaseauth.users.update`), the service account `salusalo-role-setter` and its key as the secret `FIREBASE_AUTH_ADMIN_KEY`; `set-firebase-role` deployed from the dashboard editor with JWT verification off. Claude's no-token checks: POST 401 `{"error":"Invalid or missing Firebase ID token."}`, GET 405. Fixed on the way: a random function address (`quick-api`, 404) and a key from another Google Cloud project (500 "secret missing or invalid") (§18.7). |
| 2026-09-28 | `9126799` (branch `claude/auto-role-claim`); function deployed | Developer | Passed | Automatic Supabase role (§18): the §18.5 two-phone test, 7 steps in two parts, each checked in the Supabase logs. Covered: a new email account reaching Home without the script and reserving and cancelling; one `role set` log line per new account with 6 characters of the UID only; A and B signing in with no function call; a spare Google account; the setup-failure dialog with Try again while gateway JWT verification was on, then Home after turning it off. Not covered: the "Left out" list in §18.5. |
| 2026-09-28 | `49ac319` (`master`) | Developer | Merged | Mika's Profile redesign and settings (§16) merged into `master` through PR #10 on 2026-09-28 (merge commit `49ac319`). |
| 2026-09-30 | `claude/bugfix-pass-1` (the code committed as `5429e6e`); migrations `20261005`–`20261007` applied | Developer | Passed | Bug fix pass (§21) on one phone, switching accounts: live safety checks 22/23 (check 12 was stricter than the rule and was corrected), 15/15, 9/9; test Part 1 (10 steps) and Part 2 (the replacement step 11 and steps 12–17). Details and what was left out: §21.4. |
| 2026-09-30 | `claude/bugfix-pass-2` (uncommitted, before the offline Google sign-in fix); migrations `20261008`–`20261010` applied | Developer | Passed | Bug fix pass round 2 (§22) on one phone, switching accounts: live checks 5/5, 5/5, 3/3; test Part 1 (steps 1–13, step 7 with email/password) and Part 2 (steps 14–19). Offline Google sign-in showed nothing; fixed afterwards and its 3-step check passed. Details: §22.5. |
| 2026-10-04 | `claude/admin-announcements` (then committed as `817cf19`); migration `20261014` applied | Developer | Passed | Admin announcements (§27): live safety check 14/14, then the one-phone test switching accounts A and B with the dashboard on the PC (§27.4). |
| 2026-10-04 | `f959f0e` (`master`) | Developer | Merged | Admin announcements (§27) merged into `master` through PR #25. |
| 2026-10-04 | `6f1223c` (`master`) | Developer | Merged | Mika's chat wallpaper (§28) merged through PR #22, after Claude built it merged with `master` (unit tests 71/71, lint 0 errors). Not checked on a phone before the merge. |
| 2026-10-04 | `claude/new-logo-startup` (uncommitted); dashboard deployed | Developer | Passed | New logo and the loading screen when the app opens (§29): the 9-step one-phone test (§29.2), including Mika's chat wallpaper (§28) and the dashboard logo. |
| 2026-10-04 | `59237ff` (branch `ccr-01f52d20-ihgnaw`, renamed `claude/ml-recommendations`); no SQL | Developer | Passed | On-device recommendations (§31): the 10-step one-phone test (§31.5) with accounts A and a new C. The first reservation expired on its 30-minute pickup time before step 9 (§24 as designed); step 9 passed with a second one. |
| 2026-10-04 | `ba394b8` (branch `claude/ml-recommendations`); no SQL | Developer | Passed | §31 follow-up: up to 4 cards in the row at the top of Home; the 2-step check (§31.5 steps 11–12). |
| 2026-10-04 | `b15cb2a` (`master`) | Developer | Merged | On-device recommendations (§31) merged into `master` through PR #29 at 17:51 (+08:00). |
| 2026-10-06 | `claude/allergy-filter` (uncommitted); no SQL | Developer | Passed | My allergies (§32): the 10-step one-phone test (§32.5), switching accounts A and B; every step passed. |
| 2026-10-06 | `claude/keyboard-and-map` (uncommitted); no SQL | Developer | Passed | Keyboard and map fixes (§33): the 7-step one-phone test (§33.4); every step passed. The developer then asked for a Close button on the full-screen map, for older users (§33.4 step 8). |
| 2026-10-06 | `ed07da9` (`master`) | Developer | Merged | My allergies (§32) merged into `master` through PR #30 at 22:46 (+08:00). |
| 2026-10-06 | `9568264` (`master`) | Developer | Merged | Keyboard and map fixes (§33) merged into `master` through PR #31 at 22:47 (+08:00). |
| 2026-10-06 | `da71998` (the PR #31 head) | Developer | Passed | §33.4 steps 8–9 (the ✕ on the full-screen map, the "saved" snackbar); reported right after the merge. |

- **Build:** the test phone's app code was deployed on 2026-09-25 at 10:37 (+08:00), after the last source change (10:04), so it matches the working tree above. That same code was committed on 2026-09-25, with no source changes after 10:04. The build on any second device was not checked.
- **Phase 4 retest build:** `c7323b8`, built at 21:21 (+08:00) with no local source changes and installed from Android Studio. A's phone showed the new "Loading your alert settings…" text, so it ran this build; which build the second phone ran was not checked.
- **Phase 1 demo fix test build:** installed from Android Studio on 2026-09-26 from `claude/phase1-demo-fixes` at `8908548` plus the uncommitted "starts unchecked" change, which was committed afterwards on branch `claude/phase1-demo-fixes-results`. Which build each phone ran was not checked.
- **Phase 2 demo fix test build:** `assembleDebug` of `claude/phase2-demo-fixes` at `4b6aac6` on the developer's PC (2026-09-26, BUILD SUCCESSFUL in 1m 34s, only the three old deprecation warnings), installed on both phones after the migration. Both phones showed screens that exist only on this branch (A the 4-photo Create Listing, B the gallery and Saved Listings), so both ran a build of it; the exact build on each phone was not checked.
- **Phase 3 demo fix test build:** `assembleDebug` of `claude/phase3-demo-fixes` at `24fe9f2` on the developer's PC (2026-09-26), installed from Android Studio on both phones right after the migration. Both phones showed screens that exist only on this branch (the two-button details page and the review page and receipt; the new delete dialogs), so both ran a build of it; the exact build on each phone was not checked.
- **Phase 4 demo fix test build:** `assembleDebug` of `claude/phase4-demo-fixes` at `3288d02` on the developer's PC (2026-09-27), installed from Android Studio on both phones. A's phone showed screens that exist only on this branch: the gear opening Settings, Notifications with three switches, and the real Response Rate page. So it ran a build of it; which build B's phone ran was not checked.
- **Phase 5 part 1 test build:** `assembleDebug` of `claude/phase5-reviews-reports-v6ahqn` at `3bd038c` on the developer's PC (2026-09-27), installed from Android Studio on both phones. Both phones showed screens that exist only on this branch (A the Email Verification page, B the Rate page), so both ran a build of it; the exact build on each phone was not checked.
- **§3.5 test build:** the developer installed commit `85c9263` from Android Studio, with no local source changes, and followed a step-by-step script covering §3.5 checks 1, 3 and 4 (including a full reservation in check 4) plus the regression steps above. §3.5 check 2 could not be run as written: the "lemon dou" listings were no longer on Home; only the listings created during this test were. The reason was not checked; the feed hides expired and fully reserved listings. Check 2 was then run with a staged listing (row above).
- **Phase 3 sign-off:** on 2026-09-25 the developer declared Phases 1–3 complete and manually verified. The cancel, claim and Recommended for You tests were then run and passed (rows above). Two optional follow-up checks passed later, in the §3.5 test: reserving your own listing ("Your listing / You cannot reserve it") and a cancelled listing's return to the feed. These checks still have no individual test record:
  - from the optional follow-up checks above: the fully reserved listing in My Listings, and over-quantity;
  - §3.4 check 5 (tab reload) and the bottom-bar step in check 3;
  - the concurrency and negative ownership tests (§2.11).

---

## 5. Relevant files

| Area | Path | Notes |
|---|---|---|
| Main activity, auth, navigation, Home, food details | `app/src/main/java/com/example/salusalo/MainActivity.kt` | `MainActivity`, `SaluSaloApp()` (auth + top-level `screen`), `WelcomeScreen`, `SignInScreen`, `HomeScreen()` (`page` + bottom bar), `RecommendationCard`, `AvailableFoodCard`, `FoodDetailsScreen` |
| Activity tabs | `app/src/main/java/com/example/salusalo/ActivityScreen.kt` | Since §19 three tabs: My Listings (Active and Closed; Manage reservations with Mark as picked up and, since §21, Release reservation), My Reservations (open ones; All, Free food, Purchases) and History (picked up and cancelled, both sides, with the totals); full-screen listing, reservation and pickup pages; Rate for claimed pickups (§14). Since §21 failed loads are told apart from empty lists, and results are typed (`ReservationAction`). |
| Food post model | `app/src/main/java/com/example/salusalo/model/FoodPost.kt` | `FoodPost` (with `@Transient displayImageUrl` and, since §11, `@Transient donorName`), `NewFoodPost`, `FoodPostDraft`, `FoodPostEdit`, `ListingHandover`, `FoodPostStatus`, `isExpired()` |
| Saved listings (§11) | `model/SavedListing.kt`, `data/SavedListingRepository.kt`, `viewmodel/SavedListingViewModel.kt` | Bookmarks in `saved_listings`; loaded once when Home starts, reloaded when Saved Listings opens |
| Reservation model | `app/src/main/java/com/example/salusalo/model/Reservation.kt` | `Reservation`, `ReservationStatus`, `ReservationDisplay`, `DonorReservationRow` |
| Food repository | `app/src/main/java/com/example/salusalo/data/FoodRepository.kt` | Feed (newest first, with donor names since §11), own posts, create with up to 4 photos, `updateMyFoodPost()`, delete, `withSignedImageUrl()`, `signedExtraPhotoUrls()`; since §17 `withSignedImageUrls()` and `signedUrls()` (one signing request per list, per-photo fallback) |
| Reservation repository | `app/src/main/java/com/example/salusalo/data/ReservationRepository.kt` | RPC calls, `getMyReservations()`, `getReservationsForMyFoodPosts()` |
| Reservation ViewModel | `app/src/main/java/com/example/salusalo/viewmodel/ReservationViewModel.kt` | `myReservations`, `donorReservations`, `message`, `reserve` / `cancel` / `claim` / `refresh`; since §31 `start()` (a quiet load when Home starts) |
| Recommendations (§31) | `app/src/main/java/com/example/salusalo/model/ListingRecommender.kt`, `UiText.kt` (`recommendationLabel`) | The on-device model behind "Recommended for you" on Home; tested by `ListingRecommenderTest` |
| Food ViewModel | `app/src/main/java/com/example/salusalo/viewmodel/FoodViewModel.kt` | `availablePosts`, `myPosts`, `createPost`, `updatePost`, `deletePost`; since §11 writes wait for the running request instead of being dropped |
| Auth / profile | `app/src/main/java/com/example/salusalo/data/UserRepository.kt` | Find-or-create profile; `ProfileException`; since §10 `uploadProfilePhoto`, `profilePhotoUrl`, `changePassword`, `supabaseErrorText`; since §16 `updateMyProfile` (`updateMyName` was removed in §17) |
| Auth / profile | `app/src/main/java/com/example/salusalo/data/GoogleSignIn.kt`, `data/ProfilePhoto.kt` | Google ID token through Credential Manager; profile photo shrinking (§10) |
| Auth / profile | `app/src/main/java/com/example/salusalo/model/UserProfile.kt` | `UserProfile`, `UserRole`, `NewUserProfile` |
| Auth / profile | `app/src/main/java/com/example/salusalo/data/SupabaseClientProvider.kt` | Supabase client using the Firebase ID token |
| Auth / profile | `app/src/main/java/com/example/salusalo/ProfileScreen.kt`, `ProfileSections.kt` | Name, photo, member since and password change are real (§10, passed on 2026-09-26), as are the notification preferences (§8) and saved listings (§11, passed on 2026-09-26); since §13 (passed on two phones on 2026-09-27) the bell opens Alerts, the gear opens Settings and the response rate is real; the rating, reviews, verification and report are still demo. **Since §14 (passed on two phones on 2026-09-28):** the rating, reviews and Email verified badge are real, and Report a concern explains where the Report buttons are |
| Storage paths | `app/src/main/java/com/example/salusalo/data/StorageRepository.kt` | `foodImagePath()`, `profileImagePath()` (`<uid>/profile-<millis>.jpg` since §10) |
| Create listing | `app/src/main/java/com/example/salusalo/PostScreen.kt` | Create Listing form, "My posts", `EditListingDialog` (§11), `PostPhoto` |
| Messaging (Phase 4) | `ChatScreen.kt`, `data/MessageRepository.kt`, `data/RealtimeInserts.kt`, `model/Conversation.kt`, `viewmodel/MessageViewModel.kt` | Chat dialog and Messages inbox; RPC calls; Realtime insert stream. Since §13 (passed on two phones on 2026-09-27): the inbox is live for the whole session (`MessageViewModel.start`), the chat badge is the sum of unread counts, and `getMyResponseRate()` / `ResponseRate` |
| Alerts (Phase 4) | `AlertScreen.kt`, `data/NotificationRepository.kt`, `model/AppNotification.kt`, `viewmodel/NotificationViewModel.kt` | Real alerts, unread counts, preferences; since §13 `newAlerts` (alerts that arrive live) |
| Alert sound (§13) | `AlertSound.kt` | `AlertSoundPlayer` (the phone's default notification sound) and the phone-level `notification_sound` setting in `salusalo_session` |
| Demo screens | `app/src/main/java/com/example/salusalo/FoodActions.kt` | Preview-only pickup and checkout dialogs; its demo chat is now only a fallback |
| Listing photos (§17) | `app/src/main/java/com/example/salusalo/ListingPhotos.kt` | `ListingPhoto` (a signed photo with the plain and "Image unavailable" tiles) and `FullScreenPhotos`. The unused template theme `ui/theme/` was removed in §17; `MainActivity` builds its own `MaterialTheme` |
| Reviews and reports (§14) | `ReviewsAndReports.kt`, `model/Review.kt`, `data/ReviewRepository.kt`, `data/ReportRepository.kt` | The Rate page, the Report page and review rows; ratings and reviews through names-only functions; `submit_report` (passed on two phones on 2026-09-28) |
| Database / Storage SQL | `supabase/migrations/` (16 files), `supabase/README.md`, `supabase/tests/` | Applied manually in the Supabase SQL Editor; `supabase/tests/` holds read-only checks, live safety checks and, since §14, `phase5_reports_query.sql` |
| Build / config | `app/build.gradle.kts`, `gradle/libs.versions.toml`, `app/google-services.json`, `local.properties` (git-ignored) | Supabase URL and publishable key are read from `local.properties` into `BuildConfig` |

- **Navigation files:** there are none; there is no Navigation-Compose `NavHost`. Navigation is string state in `MainActivity.kt`:
  - `SaluSaloApp` → `screen` (L114, L208–307)
  - `HomeScreen` → `page` (L1257, L1324–1358), with the bottom bar at L1808–1932
- **Authentication:** Firebase calls live in `SaluSaloApp()` (L113–325), `SignInScreen` (L593) and `authErrorMessage` (L327) in `MainActivity.kt`. Profile creation is in `UserRepository.kt`.

---

## 6. Development order

### Phase 3 (complete, signed off on 2026-09-25)

1. **Separate My Reservations and My Donations / Manage Reservations.** The code at `d4f65fe` already separates them (§3.1). **Verified on 2026-09-25** (§4 A/B test). The tab label decision is still open.
2. **Fix the Recommended for You image** (§3.2). Code changed on 2026-09-25. **Verified on 2026-09-25** (Recommended for You test).
3. **Fix the Reservation image** (§3.3). Code changed on 2026-09-25. **Verified on 2026-09-25** (§4 A/B test).
4. **Build the project** (`assembleDebug`). Passed on 2026-09-25 after steps 2 and 3, and again after the §3.4 load-failure fixes.
5. **Manually test with two accounts** (§4), including the §3.4 checks, and record the outcome in the results log. The §3.4 SQL checks were run on 2026-09-25. **On 2026-09-25** the A/B reservation test and the missing-image fallback/feed check passed. Not reported: the items listed under the results log.
6. **Regression-test Phase 1/2:**
   - sign-up (creates the profile), sign-in, sign-out, password-reset email;
   - create a listing with an image and see it in the feed and in My Listings;
   - delete a listing, which also deletes its image.
   - **Passed on 2026-09-25, as reported:** authentication/profile regression; Home/feed, create listing and delete listing for both accounts.
7. **Phase 3 signed off as complete by the developer on 2026-09-25.** Carried forward, not blocking:
   - The checks without an individual test record (listed under the results log, §4).
   - The tab label decision (§3.1).
   - How the two "lemon dou" images went missing (§3.4).
8. **Commit the work since `d4f65fe`.** Done on 2026-09-25: the §3.2–§3.4 code changes, `docs/` and `CLAUDE.md`.
9. **Known-issue fixes (§3.5).** Made after sign-off in a Claude Code web session (commit `85c9263`). Built and manually tested on 2026-09-25 (results log), then merged into `master` through pull request #1 on 2026-09-25 (merge commit `85e2bf4`).

### Phase 4 — Messaging and Notifications (complete, signed off on 2026-09-25)

Started on 2026-09-25 at the developer's request; scope and status are in §8. Remaining steps:

1. Apply `supabase/migrations/20260926_phase4_messaging_notifications.sql` in the Supabase SQL Editor and check the Realtime publication. **Done on 2026-09-25.**
2. Build with `assembleDebug`. **Passed at `266325b` on 2026-09-25.**
3. Run the Phase 4 test in §4 and the regression, and record the results in the results log. **Done on 2026-09-25 on one phone**, with one failure (§8.7 bug 1).
4. Fix the two preference bugs (§8.7). **Fixed on 2026-09-25 (`5c1e89c`).**
5. Build again, retest the preference switches on both screens, and finish the live checks: chat between the two phones, a live reservation alert, and the token refresh after more than 1 hour. **Done on 2026-09-25 at `c7323b8`; all passed.** Sign-up of a new account also passed.
6. Run the negative tests on the live project. **Passed on 2026-09-25** (21 of 21, `supabase/tests/phase4_live_safety_check.sql`). Not yet done: the password-reset email (part of the step 10 regression in §4).
7. Sign off Phase 4. **Signed off by the developer on 2026-09-25.** The password-reset email was not tested; Phase 4 did not change its code.

Phase 5 (Maps, Reviews, Reports) and Phase 6 (Admin and Final Release) follow. Phase 5 part 1 (reviews, reports, Email verified) passed on two phones on 2026-09-28 and was merged into `master` through PR #9 on 2026-09-28 (merge commit `711cc5e`) (§14). Phase 5 part 2 (Maps) passed on two phones on 2026-09-28 and was merged into `master` through PR #11 on 2026-09-28 (merge commit `255b0f8`) (§15). Mika's Profile redesign and settings (PR #10) passed a short test on two phones on 2026-09-28 and was merged into `master` through PR #10 on 2026-09-28 (merge commit `49ac319`) (§16). Phase 6 has not started. The fixes, clean-up and optimization after the second demo audit (§17) passed a 9-step test on two phones on 2026-09-28 on branch `claude/cleanup-after-audit` and were merged into `master` through PR #12 on 2026-09-28 (merge commit `12e931c`). The automatic Supabase role for new accounts (§18) was deployed, passed a 7-step test on two phones on 2026-09-28 and was merged into `master` through PR #13 (merge commit `b8aa7f9`). The UI/UX refresh (§19) and the admin dashboard sample (§20) were merged through PRs #14 and #15 on 2026-09-29. The bug fix pass (§21) passed a one-phone test on 2026-09-30 and is in PR #17.

---

## 7. Other observations (not scheduled; verify before acting)

- **Food details screen.** Since §3.5 (verified on 2026-09-25) the header shows the uploaded image, and the description and "Pickup Address" come from the post. **Changed in §11 (passed on two phones on 2026-09-26, §11.6):** allergens, storage notes, pickup window and handover are saved and shown, the Maps search and Donor page use the listing, and "Distance" became "Expires".
- **Dropped requests.** Since §3.4, feed and My Listings loads requested during another `FoodViewModel` request are queued, not dropped. **Since §11** `FoodViewModel` writes (create, edit, delete) wait for the running request instead; only a second write before the first has finished is ignored, and the buttons stay disabled until then. In the §11.4 test on 2026-09-26 every Post Listing, Save changes and Delete ended with its result; a write started during a running load was not tested on purpose. `ReservationViewModel` also queues a refresh asked for during a request; only a second write before the first has finished is ignored.
- **No realtime updates outside Phase 4.** Since §3.5 (verified on 2026-09-25) the Home feed reloads after a cancel. Claim does not change the feed. Apart from Phase 4's open chat and alerts (§8), nothing updates in real time across devices. **Changed in §21:** the feed also reloads when Home or All listings is opened and when the app comes back (at most every 30 seconds), and alerts reload when the app comes back; still nothing else is live.
- **Placeholder or hard-coded UI values:**
  - the greeting "Good evening, Mika". **Changed on 2026-09-25 (§3.5, commit `851e8cb`): it now uses the signed-in profile's first name and the time of day. Built; the evening greeting was seen in the Phase 4 test on 2026-09-25.**
  - the location "Del Rosarrio, Balilihan". **The profile location was removed on 2026-09-26 (§10; passed on two phones).** The Home and Activity location pickers still show sample places.
  - "Browse all 10 food listings". **Removed in §11 with the Recommended page; the Home checks of §11.4 step 1 passed on 2026-09-26.**
  - Activity metrics 48 / 128. **Replaced by real totals (§9); passed on two phones on 2026-09-26 (§9.6).**
  - sample Purchases, Claims and reviews (sample Alerts were replaced by real alerts in Phase 4). **The sample Purchases and Claims were replaced (§9); passed on two phones on 2026-09-26 (§9.6).** Sample reviews remain. **Replaced by real reviews in §14 (passed on two phones on 2026-09-28).**
  - The "Nearby" filter relies on a fixed distance. **Removed in §11, with the Nearest sort, distances, ratings and the Home location row (passed on two phones on 2026-09-26, §11.4 steps 1 and 7–9).** The "For Sale" filter matched nothing, because every live listing was "Free"; since §9 it matches listings with a price (passed on 2026-09-26, §9.4 step 5).
- **Read access after cancel.** `is_reservation_recipient()` ignores reservation status, so a recipient keeps read access to a post after cancelling.
- **Signed URL expiry.** Signed image URLs expire after 1 hour and are not refreshed while a screen stays open. **Changed in §21:** a Home photo that fails when the feed is 50+ minutes old reloads the feed once for fresh URLs; Activity, Messages and Saved Listings sign again whenever they load.
- **Raw error messages.** User-facing errors are raw exception messages from supabase-kt and Firebase. How readable the RPC error text is has not been checked. **Changed since §11/§12:** most screens show only the server's short text (`shortErrorText`); Chat and Alerts still show raw text (audit #13, open).

---

## 8. Phase 4 — Messaging and Notifications

**Status: complete. Signed off by the developer on 2026-09-25.**
- `assembleDebug` passed at `266325b`, and again at `c7323b8` with the §8.7 fixes. The migration was applied to the live project, and `messages` and `notifications` are in the `supabase_realtime` publication.
- The one-phone Phase 4 test and the regression passed at `266325b`, except saving a preference switched back on.
- At `c7323b8`, on two phones: the §8.7 retest passed, and so did the live checks (message alerts, chat between the phones, a reservation alert, and alerts after more than 1 hour on Home). A new account signed up ([results log](#results-log)).
- The live safety check (`supabase/tests/phase4_live_safety_check.sql`, §8.6) passed 21 of 21 on 2026-09-25.
- Not tested: the password-reset email (§8.5). Phase 4 did not change its code.
 Scope chosen by the developer: chat per listing; in-app alerts only (no push); live updates with Supabase Realtime; notification preferences stored in Supabase.

### 8.1 Database (`supabase/migrations/20260926_phase4_messaging_notifications.sql`)

- **`conversations`**: one thread per `(food_post_id, recipient_id)`; `donor_id` is copied from the listing by the RPCs. Per-side read times (`recipient_last_read_at`, `donor_last_read_at`) and `last_message_at`. Deleted with the listing (cascade).
- **`messages`**: `conversation_id`, `sender_id`, `body` (1–2000 characters after trimming), `created_at`.
- **`notifications`**: per-user alerts of type `MESSAGE`, `RESERVATION_CREATED`, `RESERVATION_CANCELLED` or `RESERVATION_CLAIMED`, with optional links to the listing, conversation and reservation, and `read_at`.
- **`notification_preferences`**: one row per user (`message_alerts`, `reservation_alerts`, `recommendations`, `pickup_reminders`). A missing row means everything is on. Only `message_alerts` and `reservation_alerts` affect alerts today. **Since §13** the app no longer shows `recommendations` and `pickup_reminders` (checked on a phone on 2026-09-27). A save writes back their stored values (as coded; not checked in the database), and the columns stay.
- **Access:** clients may only SELECT conversations and messages they take part in, and their own alerts and preferences. `notification_preferences` also allows INSERT/UPDATE of the caller's own row. Every other write goes through SECURITY DEFINER functions that take the caller from the JWT:
  - `start_listing_conversation(p_food_post_id)`: get or create the caller's thread with the donor. Rejects the caller's own listing, and listings that are not available unless the caller has a reservation on them.
  - `open_reservation_conversation(p_reservation_id)`: get or create the thread of a reservation; either its recipient or the listing's donor may call it.
  - `send_message(p_conversation_id, p_body)`: participants only; also updates the thread and alerts the other participant.
  - `mark_conversation_read(p_conversation_id)`: also marks that thread's message alerts read.
  - `mark_notifications_read(p_ids)`: the caller's alerts; `null` marks all.
  - `get_my_conversations(p_conversation_id)`: the caller's threads with the listing name and image path, the other participant's **display name only**, the last message and the unread count.
- **Alerts** are created only by the database: `notify_user()` (not executable by clients) respects the preferences, and a trigger on `reservations` alerts the donor when a reservation is created or cancelled and the recipient when it is claimed. The Phase 3 RPCs are unchanged.
- **Realtime:** `messages` and `notifications` are added to the `supabase_realtime` publication; Realtime applies the SELECT policies to what each client receives.

### 8.2 Privacy change

Before Phase 4, recipients never saw the donor's name. In a conversation, **both participants see each other's display name** (through `get_my_conversations()`), never email or phone. `users` RLS is unchanged.

### 8.3 App

- **Dependencies:** `realtime-kt` added; the Ktor engine changed from `ktor-client-android` to `ktor-client-okhttp` (the Android engine has no WebSocket support) and Ktor aligned to 3.4.2, the version supabase-kt 3.5.0 uses.
- **Realtime auth:** Realtime uses the same Firebase ID token as PostgREST (the client's `accessToken` lambda), read when a channel joins. Without the Supabase Auth plugin it is never refreshed automatically, so `NotificationViewModel` calls `realtime.setAuth()` every 5 minutes (confirmed by reading the realtime-kt 3.5.0 sources). On 2026-09-25 alerts still arrived live after the app had been open on Home for more than 65 minutes ([results log](#results-log)).
- **Data layer:** `MessageRepository`, `NotificationRepository`, `realtimeInserts()` (a channel per stream, removed when collection stops), `MessageViewModel` and `NotificationViewModel` (created per profile like the Phase 3 ViewModels and cleared on sign-out). `signedUrlOrNull()` is shared with `withSignedImageUrl()`.
- **UI:**
  - Home header: new chat icon (badge = unread message alerts; **since §13, passed on two phones on 2026-09-27:** the sum of the threads' unread counts) → Messages inbox (`MessagesScreen`); bell badge = all unread alerts.
  - `ChatScreen`: full-screen conversation over any screen, live updates, marks the thread read.
  - Food details: "Message Donor", the Allergens and Storage rows, and the details dialog's "Ask for exact location"/"Message provider" links open the real chat (prefilled where the demo chat was). On your own listing the button reads "Your listing".
  - Activity: "Message donor"/"Message recipient" in reservation details; the bell dot only shows with unread alerts.
  - Alerts: real alerts, filters All/Messages/Reservations, Today/Earlier, mark read, tap to open the chat or the Activity tab, settings dialog saved to Supabase. The sample alerts and the "Personalized for you" card were removed.
  - Profile → Preferences → Notifications: "New messages", "Reservation updates", "Food recommendations" and "Pickup reminders" are saved to Supabase; "Notification sound" stays on the device. **Changed in §13 (passed on two phones on 2026-09-27):** the page opens from Profile → Notifications and has only "New messages" and "Reservation updates" (saved to Supabase) and "Notification sound" (saved on the phone, and now plays a sound).

### 8.4 Known limits

- No push notifications (the app must be open for live updates; alerts are still stored and shown later). **Since §35 (built, not tested yet): alerts are also pushed as phone notifications.**
- No blocking or reporting of users (Phase 5), and no editing or deleting of messages. **Since §14 (passed on two phones on 2026-09-28):** people can be reported from a chat; there is still no blocking.
- No rate limit on messages beyond the 2000-character limit.
- Deleting a listing deletes its conversations, messages and related alerts.
- The chat icon badge counts unread message **alerts**, so it stays empty when message alerts are turned off; the inbox still shows unread counts. **Fixed in §13:** the badge sums the threads' unread counts (passed on two phones on 2026-09-27). With message alerts turned off it was not tested.
- The Profile screen's bell still opens settings and always shows its dot (unchanged demo UI). **Fixed in §13 (passed on two phones on 2026-09-27).**

### 8.5 Status matrix

| Item | Implemented (code) | Verified | Not yet verified |
|---|---|---|---|
| Migration applies and re-runs | Yes | Local Postgres 16 with Supabase-like stubs; applied to the live project (2026-09-25) | — |
| RPCs, RLS denials, alerts, preferences | Yes (SQL) | Local three-account SQL test (§8.6); the one-phone Phase 4 test and the two-phone retest; the live safety check, 21 of 21 (2026-09-25) | — |
| Realtime with Firebase Third-Party Auth | Yes | Connects; the subscriptions carry the Firebase claims; the RLS replay as account A passes; the sender's own message arrives live; on two phones, live message alerts, live chat, a live reservation alert, and live alerts after more than 1 hour on Home (2026-09-25) | — |
| Kotlin data layer and ViewModels | Yes | AGP build at `266325b` and `c7323b8`; device runs (2026-09-25) | — |
| Chat, inbox, alerts, preferences UI | Yes | The one-phone Phase 4 test and the two-phone retest (2026-09-25) | — |
| Preference fixes (§8.7) | Yes (2026-09-25) | Type-checked; JSON encoding checked; AGP build at `c7323b8`; device retest on both screens (2026-09-25) | — |
| Ktor engine change (OkHttp) | Yes | The regression on the Phase 4 build; sign-up of a new account (2026-09-25) | The password-reset email was not reported |

### 8.6 How the Phase 4 code was checked (2026-09-25)

The cloud session that wrote Phase 4 has no Android SDK, and Google's Maven repository is blocked there, so no AGP build was possible.
- **SQL:** all five migrations were run in order on a local PostgreSQL 16 with stand-ins for Supabase's `auth.jwt()`, roles, Storage tables and the `supabase_realtime` publication; the Phase 4 file was then re-run without errors. A test with three accounts (A donor, B recipient, C outsider) confirmed: starting a thread and messaging; unread counts and read marking; MESSAGE and reservation alerts (created, cancelled, claimed); the same thread from the listing and from the reservation; the preference switch suppressing message alerts; the preferences upsert; and the denials (messaging your own listing, direct inserts into `messages`/`notifications`, calling `notify_user`, a non-participant reading, sending or opening a thread, writing another user's preferences, calls without a valid token).
- **Kotlin:** every app source file was compiled with kotlinc 2.2.10 plus the Compose and serialization compiler plugins, against the real supabase-kt 3.5.0 JVM jars and Compose Multiplatform desktop jars, with small stand-ins for Android, AndroidX Activity/Lifecycle and Firebase APIs. It compiled with no errors and no warnings in the new code. This checks types and API usage only; it is not an Android build.
- **Live safety check** (`supabase/tests/phase4_live_safety_check.sql`, written 2026-09-25): a single `do` block for the SQL Editor. It simulates the Firebase token claims of the most recent chat's two participants and of the newest other account, tries the forbidden actions, and always ends with an error on purpose, so everything it did is rolled back. The report is the error text.
  - Before it ran on the live project, it was dry-run on an in-memory Postgres (PGlite 0.3) built from the five migrations plus stand-ins for `auth.jwt()`, the Supabase roles, Storage and the Realtime publication: 21 of 21 passed and the data was unchanged.
  - With one rule deliberately broken at a time, the matching checks reported FAIL: direct message inserts allowed, `notify_user` executable, chats readable by any user, token issuer not checked, messages readable without a login, alert settings open to anyone, all profile rows readable.
  - Result on the live project: 21 of 21 passed (results log).

### 8.7 Bugs found in the device test (2026-09-25)

**Status: fixed on 2026-09-25 (commit `5c1e89c`), then built at `c7323b8` and retested on a device the same day: passed ([results log](#results-log)).** No SQL or migration change.

1. **A preference switched back on is never saved.** In Profile → Preferences → Notifications (and in Alerts → settings), turning "New messages" off saved, but turning it back on reported "Preferences saved to your account." while `message_alerts` stayed false.
   - **Cause.** supabase-kt 3.5.0's default serializer is `Json { ignoreUnknownKeys = true }`, without `encodeDefaults`. kotlinx.serialization therefore leaves out properties equal to their default. `NotificationPreferences` defaults all four booleans to `true`, so a `true` value was missing from the upsert body in `NotificationRepository.savePreferences()`. PostgREST's merge-duplicates updates only the columns it receives.
   - This affected all four switches on both screens.
   - **Fix** (`model/AppNotification.kt`): `@EncodeDefault` on `messageAlerts`, `reservationAlerts`, `recommendations` and `pickupReminders`. The defaults stay, for the all-on fallback in `getPreferences()`.
   - Checked here: encoding `NotificationPreferences(userId = "x")` with that `Json` now gives all four keys.
   - `encodeDefaults` was not turned on globally, because that would change the inserts of other models.
2. **The switches show on before the saved settings load.**
   - **Fix.** The Alerts settings dialog (`AlertScreen.kt`) and the Notifications page (`ProfileSections.kt`) show "Loading your alert settings…" instead of the account switches until the settings have loaded. Save stays disabled until then. "Notification sound" (on the device) is still shown.
   - `SectionButton` gained an optional `enabled` parameter.
   - If loading fails, the page keeps showing "Loading…" until it is reopened; the error is not shown there.

**Not fixed (noted only).** The unused `FoodRepository.updateFoodPost()` sends a whole `FoodPost` through the same serializer. Any field equal to its default would be left out of the update. Fix it before anything calls it.

---

## 9. Activity: Purchases, Claims and listing prices

**Status (2026-09-26): written on branch `claude/activity-purchases-claims` (from `master` at `5eaee72`) and merged into `master` through PR #3 on 2026-09-26, together with the Phase 1 demo fixes (§10), before its own device test. Built with AGP on 2026-09-26 as part of the §10 build (§10.6), and the migration was applied to the live project the same day. The §9.4 two-phone test passed on 2026-09-26 (reported by the developer), except the "Reserved" dialog after Reserve, which never appears (§9.6).** This is not Phase 5: no reviews, ratings, maps, reports or online payments. Payment is always in person at pickup.

### 9.1 What the tabs show (the developer chose "split by stage")

- **My Reservations:** the recipient's reservations that are still `RESERVED`, and `CANCELLED` ones. **Changed from Phase 3**, where it also listed `CLAIMED` ones.
- **Purchases:** the recipient's `CLAIMED` reservations with a price (`unit_price > 0`). Card: "3 packs • ₱135.30". Details: "Price: ₱45.10 per unit", "Total: ₱135.30, paid at pickup.", closing line "This is your live purchase, paid at pickup."
- **Claims:** the recipient's `CLAIMED` free reservations. Card: "2 pcs • Free". Details: "Free food you picked up.", closing line "This is free food you picked up."
- A reservation moves from My Reservations to Purchases or Claims when the donor marks it claimed. Each reservation appears in exactly one recipient tab.
- **My Donations:** unchanged, plus the total ("• ₱135.30" or "• Free") and, for priced reservations, "Price: … per unit" and "Collect … at pickup." in the details.
- Tapping Purchases or Claims reloads the reservation lists, like My Reservations and My Donations.
- **"Reservation claimed" alert:** opens Activity on Claims, and once the reloaded list shows the reservation as claimed, switches to Purchases if it has a price and opens its details. If it cannot be found, Activity stays on Claims.
- The six sample Purchases and Claims records were removed. The other demo UI is unchanged.
- **Completed / Items Rescued** are computed from data Activity already loads: the number of `CLAIMED` reservations where the user is the recipient plus those on the user's listings, and the sum of their quantities (each unit — pack, piece, kg — counts as one item). The notices explain this.

### 9.2 Prices (`supabase/migrations/20260927_activity_prices.sql`)

- `food_posts.price numeric(10,2) not null default 0`, check 0–99,999.99. 0 = free donation; existing listings become free.
- `reservations.unit_price numeric(10,2) not null default 0`, same check. Set only by the BEFORE INSERT trigger `reservations_set_unit_price` (function `set_reservation_unit_price()`, SECURITY DEFINER, not executable by clients), which always overwrites it with the listing's price. `create_reservation` already holds `FOR UPDATE` on the listing, so the price is the one in effect when the quantity was checked. A later price change does not change existing reservations.
- `get_donor_reservations()` dropped and recreated with `unit_price` (still `stable security definer set search_path = public`, recipient names only, executable by `authenticated` only).
- Unchanged: the Phase 3 RPCs (still the only way to write reservations), the Phase 4 trigger and RPCs, and all RLS. The migration is safe to run again. Running the Phase 3 files again after it fails (`supabase/README.md`).
- The app never sends a reservation price. Totals come from the reservation's `unit_price × quantity`, computed with `BigDecimal.valueOf(unitPrice).multiply(BigDecimal(quantity))` and shown with two decimals (`formatPeso()`, `reservationTotal()` in `model/FoodPost.kt`).

### 9.3 App changes

- `model/FoodPost.kt`: `FoodPost.price` (default 0.0 when reading); `NewFoodPost.price` has no default, so 0.0 is always sent; `FoodPostDraft.price`; the money helpers.
- `model/Reservation.kt`: `unitPrice` on `Reservation` and `DonorReservationRow`.
- `PostScreen.kt`: Donation saves 0, For Sale saves the validated price; new error "Enter a price of at most ₱99,999.99."
- `MainActivity.kt`: Home cards show "₱45.10" or "Free", so the "For Sale" and "Donation" chips now match live listings. Food details show the price, and the note under Reserve reads "Pay ₱<unit price × chosen quantity> at pickup. The app takes no payments." for a priced listing (free: unchanged).
- `viewmodel/ReservationViewModel.kt`: after reserving a priced listing the message is "Reservation created. Pay ₱<total> at pickup.", from the `unit_price` the database returned (free: "Reservation created. Please coordinate pickup with the donor.").
- `ActivityScreen.kt`, `AlertScreen.kt`: §9.1.
- **Order:** run the migration before installing this build. Until the `price` column exists, Create Listing fails.

### 9.4 Two-phone test (Account A = donor on phone 1, Account B = recipient on phone 2)

Preconditions: run `20260927_activity_prices.sql` in the SQL Editor (check: `select table_name, column_name from information_schema.columns where column_name in ('price','unit_price') and table_schema = 'public';` returns `food_posts.price` and `reservations.unit_price`; run on 2026-09-26). Build `master` (PR #3 or later) and install it on both phones.

**A, part 1**
1. [Regression] Sign in as A. Home shows "Good <morning/afternoon/evening>, <A's first name> 👋".
2. [New] Post → "I want to sell", name `PRICE TEST`, a photo, any category, quantity `5`, unit `packs`, price `45.10`, future dates → Post Listing. Back on Home, its card shows "₱45.10".
3. [New] Post → "I want to donate", name `FREE TEST`, a photo, quantity `4`, unit `pcs` → Post Listing. Its card shows "Free".
4. [New] Post → "I want to sell", filled in as in step 2 but with `100000` in "Price per unit (₱)" (not in Quantity) → Post Listing shows "Enter a price of at most ₱99,999.99." Leave without posting.
5. [New] Home chips: "For Sale" lists `PRICE TEST` and not `FREE TEST`; "Donation" lists `FREE TEST` and not `PRICE TEST`.
6. [New] Activity: write down Completed and Items Rescued (A0, A1). Tap Completed: "<A0> completed: your reservations that donors marked claimed, plus claimed reservations on your listings." The Purchases and Claims tabs show no sample records.

**B, part 1**
7. [New] Sign in as B. Activity: write down Completed and Items Rescued (B0, B1).
8. [New] Home → `PRICE TEST`: the price box shows "₱45.10" / "For sale". Set quantity 3: the small grey line under the three buttons reads "Pay ₱135.30 at pickup. The app takes no payments." Reserve → dialog "Reserved": "Reservation created. Pay ₱135.30 at pickup." (**Known issue:** this dialog never appears; the details close and Home reloads, §9.6.)
9. [Regression] Home → `FREE TEST`: "This is a free donation. Please be respectful and pick up on time." Quantity 2 → Reserve → "Reservation created. Please coordinate pickup with the donor." (never appears, §9.6).
10. [New] Activity → My Reservations: "`PRICE TEST` 3 packs • ₱135.30", status Reserved; details "Your reservation.", "Price: ₱45.10 per unit", "Pay ₱135.30 at pickup.", closing "This is your live reservation.", buttons Cancel and "Message donor". `FREE TEST`: "2 pcs • Free". Purchases and Claims do not list either one (Claims can show older claimed reservations as "Free").

**A, part 2**
11. [New] Activity → My Donations: `PRICE TEST` "3 packs • ₱135.30"; details "Recipient: <B's name>", "Reservation for your listing.", "Price: ₱45.10 per unit", "Collect ₱135.30 at pickup.". `FREE TEST` "2 pcs • Free" with no price lines. [Regression] Alerts has two "New reservation" alerts.
12. [New] Price lock: in the SQL Editor run `update public.food_posts set price = 99 where id = (select id from public.food_posts where name = 'PRICE TEST' order by created_at desc limit 1) returning name, price, created_at;` (only the newest `PRICE TEST`) → 1 row, `99.00`. Tap My Donations again: still "₱135.30". Home shows the new price only after it reloads (step 19).
13. [Regression] Open `PRICE TEST` in My Donations → "Mark claimed" → "Reservation" dialog "Reservation marked as claimed.". Same for `FREE TEST`.
14. [New] Completed = A0 + 2, Items Rescued = A1 + 5.

**B, part 2**
15. [Regression] Alerts shows two "Reservation claimed" alerts ("The donor marked your reservation of PRICE TEST as claimed." and the same for FREE TEST).
16. [New] Tap the `PRICE TEST` alert: Activity opens on **Purchases** with its details open: "3 packs • ₱135.30", "Status: Claimed", "Picked up: <time>", "Price: ₱45.10 per unit", "Total: ₱135.30, paid at pickup.", "This is your live purchase, paid at pickup." Only Close and "Message donor" (no Cancel). The total is not ₱297.00, so the reserved price was kept.
17. [New] Back to Alerts, tap the `FREE TEST` alert: Activity opens on **Claims** with "2 pcs • Free", "Free food you picked up.", "This is free food you picked up."
18. [New] My Reservations no longer lists these two. Completed = B0 + 2, Items Rescued = B1 + 5.
19. [New] Home (open Post, then Back, to reload): `PRICE TEST` shows "₱99.00". Reserve 1 → "Reservation created. Pay ₱99.00 at pickup." (never appears, §9.6). [Regression] Activity → My Reservations → it → Cancel → "Reservation cancelled. The quantity is available again."; it stays in My Reservations as Cancelled, "1 packs • ₱99.00".
20. [Regression] Purchases → `PRICE TEST` → "Message donor" → send "Thanks!". On A's phone, the chat badge goes up without refreshing; A opens the thread and replies; B sees the reply live.

**A, part 3**
21. [Regression] Alerts has "Reservation cancelled" for `PRICE TEST`; tapping it opens My Donations.
22. [Regression] Delete `PRICE TEST` and `FREE TEST` in Activity → My Listings; they leave Home and My Listings. Sign out, sign in again. Deleting a listing also deletes its reservations, chat and alerts (§9.6), so B's Purchases and Claims lose these two and both totals drop back.

### 9.5 How this was checked (2026-09-25)

- **SQL:** all migrations run in order on a local PostgreSQL 16 with stand-ins for Supabase's roles and `auth.jwt()`, then `20260927_activity_prices.sql` run twice more without errors. A two-account test confirmed: the price check rejects −1 and 100000; a donor's own insert saves a price; `create_reservation` returns `unit_price` 45.10 for a priced listing and 0.00 for a free one; clients still cannot insert reservations; after the donor changed the price to 99 the earlier reservation kept 45.10 and a new one got 99.00; a directly supplied `unit_price` is overwritten; `get_donor_reservations()` returns names and `unit_price`; claim and cancel still work and still create the Phase 4 alerts; clients cannot execute the trigger function; `anon` cannot execute `get_donor_reservations()`. Running `20260924_phase3_donor_recipient_names.sql` afterwards fails with "cannot change return type of existing function", as documented.
- **Kotlin:** all app sources compiled with kotlinc 2.2.10 and the Compose and serialization plugins against the supabase-kt 3.5.0 JVM jars and Compose desktop jars, with small stand-ins for Android and Firebase APIs: no errors; the only warning is the deprecated `FormatListBulleted` that was already there. A small JVM check: `formatPeso(45.1)` = ₱45.10, 0.1 + 0.2 = ₱0.30, `reservationTotal(45.1, 3)` = ₱135.30, `NewFoodPost` encodes `"price":0.0`, and `unit_price: 45.10` decodes. This is not an Android build.
- **Since then (2026-09-26):** built with AGP as part of the §10 build (§10.6); the developer ran the migration on the live project; merged into `master` through PR #3; device test in §9.6.

### 9.6 Device test (2026-09-26) and known issues

- **Result:** the developer ran §9.4 on two phones (A = donor on phone 1, B = recipient on phone 2) with the §10 build, given in parts, and reported that every step passed except the "Reserved" dialog below. Recorded values: A0 = 2, A1 = 2, B0 = 2, B1 = 2; steps 14 and 18 then expected Completed 4 and Items Rescued 7, and were reported as passed.
  - Step 4: the first try put `100000` into Quantity, leaving the price empty, and correctly got "Enter a price greater than zero, with up to two decimal places."; the redo with the price field showed "Enter a price of at most ₱99,999.99.".
  - Step 12: A had two `PRICE TEST` listings (another account had reserved the first one); the newest-only update changed the tested one and returned 1 row, `99.00`. Home showed "₱99.00" only after a reload (Post → Back), since the feed does not update live (§7).
- **Known issue, fixed in §12 (the Reserve review page and receipt; passed on two phones on 2026-09-27): the "Reserved" dialog never appears.** After a successful Reserve, `FoodDetailsScreen` sets its "Reserved" notice ("Reservation created. Pay ₱… at pickup." or "…Please coordinate pickup with the donor.") and at once calls `onReserved()`. `HomeScreen`'s `onReserved` reloads the feed and sets `selectedFood = null`, which closes the details dialog and the notice inside it before it is drawn. Present since `d4f65fe`, so not caused by §9 or §10. It affects §9.4 steps 8, 9 and 19 and §10.4 step 17. The reservation itself is created correctly; the amount still shows in My Reservations and in the line under the buttons. The developer chose on 2026-09-26 to fix it with the Phase 3 demo fixes, together with Buy Now / Claim Now. Proposed fix: keep the details open until the dialog's OK, then close them; the feed can still reload at once.
- **Observations for later (not bugs in §9):**
  - The "Pay ₱… at pickup" line under the buttons is 10 sp grey text and was missed at first.
  - The Buy Now / Claim Now demo checkout looks like a receipt and was taken for the real flow.
  - Deleting a listing deletes its reservations (`on delete cascade`), with their chats and alerts, so a buyer's Purchases and Claims history disappears when the donor deletes the listing.

---

## 10. Phase 1 demo fixes: accounts, profile, security

**Status (2026-09-26): complete.** Written on branch `claude/phase1-demo-fixes` (from `claude/activity-purchases-claims` at `32915d1`) and merged into `master` through PR #3 on 2026-09-26, which also brought in §9. Built with AGP (§10.6); the migration was applied to the live project, and the developer ran the Phase 1 checks on two phones and reported that they passed (§10.6, [results log](#results-log)). "Keep me signed in" starting unchecked was changed after PR #3, before the test, and reached `master` in a later pull request. The new `google-services.json` is committed (§10.2). Fixes to finished Phase 1 features, found in the developer's audit; not part of Phase 5.

### 10.1 What changed

- **Google sign-in** (`data/GoogleSignIn.kt`, `MainActivity.kt`). One "Continue with Google" button on the Sign In and Create Account tabs; Apple and Facebook are removed. Credential Manager (`androidx.credentials` 1.6.0, `googleid` 1.2.0; 1.2.1 is built with Kotlin 2.4 metadata, which this project's Kotlin 2.2.10 cannot read) returns a Google ID token for the server client ID `R.string.default_web_client_id`, which the Google Services plugin generates from `google-services.json` (never hard-coded). Firebase signs in with `GoogleAuthProvider.getCredential(idToken, null)`, then the app opens Home as after sign-up (`openAuthenticatedHome(refreshFirebaseToken = true)`), creating the profile with the Google name and email.
  - Cancelling the picker shows nothing. No Google account: "No Google account was found on this phone. Add one in your phone's Settings, then try again." Firebase errors use the existing texts (for example "An account already exists for this email address."). Anything else: "Google sign-in failed. Please try again." The no-account and failure messages add a second line "Details: <message>" so setup errors (wrong SHA-1 or client ID) can be read without logcat.
  - A new Google account without the role claim gets "We could not load your profile. Check your internet connection and try again." and stays signed in to Firebase, as after an email sign-up. After the role script runs, tapping Google again works.
  - Logging out also clears the Credential Manager state (best effort).
- **Keep me signed in.** **Removed in §35: an account stays signed in until Log out.** The checkbox starts **unchecked** (the developer's choice, 2026-09-26). Stored on the device (`salusalo_session`, `keep_signed_in`; a device that never saved it keeps its session). Sign In tab (email or Google): the checkbox value; sign-up and Google from the Create Account tab: kept. When unchecked, a fresh start of the app (for example after swiping it away from Recents) signs out and shows the Welcome screen. Rotation and restores by Android keep the session.
- **Honest texts.** Welcome page 1 left card: "Find surplus food" / "Browse food shared in your community."; page 2: "Add a photo, quantity, and pickup location."; page 3 body: "Hold food for pickup, message the provider, and collect it at the agreed place.", right card "Message before pickup" / "Chat with the donor to arrange pickup."; sign-in footer: "Agree on pickup details in the chat and check the food before you take it." The illustrations are unchanged.
- **Display name** saved through `update_my_profile_name`. Account Details shows the sign-in email read-only ("Email (used to sign in)"). Texts: "Enter your name.", "Your name must be 120 characters or fewer.", "Saved to your account."; errors show the database's short text, or "Your name could not be saved. Check your internet connection and try again." for network errors. The new profile goes up to the app, so the Home greeting and Profile header change at once. The "Mika" default and the device-only name are gone.
- **Profile location removed:** the header line, the Location field, the Preferences → Privacy page and its switch.
- **Profile photo.** Shrunk to at most 512 px (upright, using EXIF), JPEG, off the main thread; uploaded to the private `profile-images` bucket at a new path each time (`<uid>/profile-<millis>.jpg`, no upsert, because overwriting one path can leave the CDN serving the old picture); saved through `set_my_profile_image`; the previous file is then deleted (best effort), and a failed save deletes the new upload. Shown with a 1-hour signed URL, falling back to the initial. Texts: "Profile photo updated.", "This photo could not be read. Please choose another photo.", "Your photo could not be uploaded. Check your internet connection and try again." (or the database's short error).
- **Account Security.** The false "not connected to authentication" text and the Signed-in devices page are removed. Change password: "Current password", "New password", "Confirm new password", button "Change password". Checks: "Enter your current password.", "Your password must be at least 6 characters.", "Your passwords do not match.". Reauthentication failure: "Your current password is incorrect."; weak new password: "Choose a stronger new password." (mapped separately, so it is never reported as a wrong current password); "Too many attempts. Please wait a few minutes and try again."; otherwise "Your password could not be changed. Check your internet connection and try again."; success: "Your password was changed.". A Google-only account sees "This account signs in with Google, so it has no password to change."
- **Member since** from `users.created_at`: the stat shows e.g. "Sep 2026", the Membership page "Member since September 2026". The "sample value" text is gone.
- **Settings captions:** "Edit your display name", "Change your password", "Notifications and app settings"; Profile menu "Notification and app preferences".
- **Unchanged (later tasks):** Buy Now / Claim Now, Home and listing demos, the other Profile stats, Ratings & Reviews, the Identity Verified badge and verification pages, Saved Listings, Report a concern, notification sound and app preferences, Activity.

### 10.2 Setup (developer)

1. **Firebase console:** Authentication → Sign-in method → enable **Google** (choose a support email). Project settings → Your apps → the Android app `com.example.salusalo` → **Add fingerprint** with the debug SHA-1 (`./gradlew signingReport`, or `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`). Download the new `google-services.json` and commit it to this branch as `app/google-services.json`. It must now contain an OAuth client; without it `R.string.default_web_client_id` does not exist and the build fails. **Done on 2026-09-26:** Google enabled, the debug SHA-1 added, and the new `google-services.json` (Android and web OAuth clients) committed to this branch.
2. **Supabase:** run `supabase/migrations/20260927_activity_prices.sql` (if not yet run), then `supabase/migrations/20260928_profile_updates.sql`, by hand in the SQL Editor. Check: `select proname from pg_proc where proname in ('update_my_profile_name','set_my_profile_image');` returns 2 rows. **Both run on 2026-09-26.**
3. Build and install on both phones.

### 10.3 Database (`supabase/migrations/20260928_profile_updates.sql`)

`update_my_profile_name(p_name)` and `set_my_profile_image(p_path)`: SECURITY DEFINER, `set search_path = public`, executable by `authenticated` only. Both find the caller's row from `auth.jwt() ->> 'sub'` after `is_salusalo_firebase_jwt()`, never from an argument, and change only `name` or `profile_image_url` (never `role`, `email` or `firebase_uid`). The name is trimmed and must be 1–120 characters. The photo path must start with `<sub>/`, contain no `..`, be at most 512 characters and exist in `profile-images`. `users` still has no UPDATE grant or policy for clients. Safe to run again.

### 10.4 Two-phone test

**Preconditions:** §10.2 done. A (phone 1) and B (phone 2) are existing email/password accounts. A has a reservation on one of B's listings (so B's My Donations shows "Recipient: <A's name>") and a chat with B. A is signed in only on phone 1. G is a Google account on phone 1 (phone Settings → Accounts) that has **no** Salu-Salo account yet and no role claim. Write down A's current name and password. Do not use a Google account whose email already has an email/password Salu-Salo account (§10.5).

**A (phone 1)**
1. [New] Signed out, swipe through Welcome: page 1 left card "Find surplus food" / "Browse food shared in your community."; page 2 left card "Add a photo, quantity, and pickup location."; page 3 "Hold food for pickup, message the provider, and collect it at the agreed place." with the right card "Message before pickup" / "Chat with the donor to arrange pickup.".
2. [New] Sign In tab: under "or continue with" there is one button, "Continue with Google" (no Apple or Facebook); the same on Create Account. Footer: "Your safety matters" / "Agree on pickup details in the chat and check the food before you take it."
3. [New] Sign In tab: "Keep me signed in" starts **unchecked**. Tick it and sign in as A. Swipe the app away from Recents and open it again: Home opens.
4. [New] Profile → Log out. Sign in as A leaving it **unchecked**. Rotate the phone: still Home. Swipe the app away from Recents and open it again: the Welcome screen shows.
5. [Regression] Sign in as A with it ticked.
6. [New] Profile: no location line under the name. The Member Since stat shows the month A signed up, e.g. "Sep 2026"; tapping it shows "Member since September 2026" and "Account name: <A's name>", with no "sample value" text.
7. [New] Profile → Account Settings: "Display name" shows A's name; "Email (used to sign in)" shows A's email, greyed out and not editable. Clear the name → Save account details → "Enter your name.". Type "  Ana Test  " → Save → "Saved to your account.". Back: the Profile header shows "Ana Test"; Home says "Good <morning/afternoon/evening>, Ana 👋".
8. [New] Profile → camera button → choose photo 1: a spinner, then "Profile photo updated."; the circle shows photo 1. Log out and sign in again (checked): photo 1 still shows.
9. [New] Camera button → choose a different photo 2: "Profile photo updated."; the circle shows photo 2, not photo 1.
10. [New] Account Settings → Account security → Change password:
    - a wrong current password, new "abc123" twice → "Your current password is incorrect.";
    - the right current password, new "abc12" twice → "Your password must be at least 6 characters.";
    - the right current password, two different new passwords → "Your passwords do not match.";
    - the right current password, a new password P2 twice → "Your password was changed." and the fields are empty.
11. [New] Log out. Sign in with the old password → "The email address or password is incorrect.". Sign in with P2 → Home.
12. [New] Profile → bell icon at the top (opens Settings): "Account details / Edit your display name", "Security / Change your password", "Preferences / Notifications and app settings". Preferences has no Privacy; Security has only "Change password"; there is no Signed-in devices page and no "The app is not connected to authentication yet" text.

**B (phone 2)**
13. [New] Home → chat icon (Messages): the thread with A shows "Ana Test"; opening it shows "Ana Test".
14. [New] Activity → My Donations → A's reservation → "Recipient: Ana Test".
15. [Regression] In the chat with A, send "Name check".

**A (phone 1)**
16. [Regression] The chat badge and a message alert appear without refreshing. Open the chat and reply "Got it"; B sees it live.
17. [Regression] Open one of B's listings → Reserve 1 → "Reservation created…" (this dialog never appears, §9.6). Activity → My Reservations → it → Cancel → "Reservation cancelled. The quantity is available again."
18. [New] Account Settings → set the name back to A's original name → "Saved to your account.". Optionally change the password back (current P2, new = the old password). Log out.

**G (phone 1, signed out)**
19. [New] Sign In → Continue with Google → close the account picker: no dialog, still on Sign In.
20. [New] Continue with Google → choose G → "We could not load your profile. Check your internet connection and try again." (no role claim yet). A "Details:" line instead means a setup problem (§10.2).
21. [New] Run the role script for G (outside this repo). Continue with Google → G → Home greets G's Google first name. Profile → Account Settings: the email field shows G's Gmail address.
22. [New] Account Settings → Account security → Change password: "This account signs in with Google, so it has no password to change."
23. [New] Log out → Continue with Google → the account picker appears again → G → Home. Log out.

**Regression (Phases 1–4, and §9 if not yet tested):** sign-up of a new email account (with the role script); the password-reset email; create and delete a listing with a photo; the Home feed; the §4 A/B reservation test (reserve, cancel, claim); the §4 Phase 4 chat and alerts test; the §9.4 test.

### 10.5 Known limits

- **Google sign-in can take over an unverified email account.** Signing in with Google using the email of an existing email/password account whose email was never verified makes Firebase drop that account's password: it keeps its UID and profile but becomes Google-only. The test uses a Google account with no Salu-Salo account for this reason.
- After a password change, Firebase ends that account's session on any other phone within about an hour. The app does not notice, so that phone shows load errors until someone signs in again.
- The profile photo is visible only to its owner (the Phase 1 `profile-images` policy allows only the owner's folder). There is no way to remove a photo, only to replace it. Deleting the previous file is best effort, so an old file can remain.
- Other people see a new name on their next load, not live.
- "Keep me signed in" is per device and applies only to a fresh start.
- If Firebase rejects a Google sign-in as an invalid credential, the message is the email/password text "The email address or password is incorrect.", because `authErrorMessage()` in `MainActivity.kt` is shared with email sign-in. Found by reading the code; not seen on a device.

### 10.6 How this was checked (2026-09-26)

- **SQL:** all migrations run in order on a local PostgreSQL 16 with stand-ins for Supabase's roles, `auth.jwt()` and `storage.objects`, then `20260928_profile_updates.sql` twice more without errors. Confirmed: a user renames themselves and the name is saved trimmed ("  Ana Test \n" → "Ana Test"); blank, null and 121-character names are refused ("Enter your name.", "Your name must be 120 characters or fewer."), 120 characters are accepted; photo paths in another user's folder, with `..`, with a lookalike prefix (`uidAX/…`), null, or for a missing object are refused; an own existing path is saved; `role`, `email` and `firebase_uid` do not change and other users are untouched; a direct `update public.users` is still permission denied; a token from another issuer gets "Not authenticated"; `anon` gets permission denied; `get_donor_reservations()` and `get_my_conversations()` show the new name; `supabase/tests/phase4_live_safety_check.sql` passed 21 of 21 on the local data.
- **Kotlin:** all sources compiled with kotlinc 2.2.10 and the Compose and serialization plugins, against the supabase-kt 3.5.0 JVM jars and Compose desktop jars. Google's Maven repository is blocked in this session, so `androidx.credentials` 1.6.0, `googleid` 1.2.1, the new Firebase calls (`GoogleAuthProvider`, `EmailAuthProvider`, `reauthenticate`, `updatePassword`, `providerData`, `FirebaseAuthWeakPasswordException`) and the Android graphics and `ExifInterface` APIs were checked only against hand-written stubs of their signatures. No errors; only the three deprecation warnings that were already there. This is not an Android build.
- **AGP build (2026-09-26, the developer's PC):** with `googleid` 1.2.1, `compileDebugKotlin` failed: "Module was compiled with an incompatible version of Kotlin. The binary version of its metadata is 2.4.0, expected version is 2.2.0." `googleid` 1.2.0 (metadata 2.1.0) builds: `assembleDebug` BUILD SUCCESSFUL, with only the three old deprecation warnings. `gradle/libs.versions.toml` now pins 1.2.0.
- **Live migration and device test (2026-09-26):** the developer ran `20260927_activity_prices.sql` and `20260928_profile_updates.sql` in the SQL Editor, installed the build from Android Studio (this branch at `8908548` plus the uncommitted "starts unchecked" change, which also built with AGP), and ran the Phase 1 checks on two phones from a summary of §10.4. Reported result: passed ([results log](#results-log)).
- **Not reported:** the regression list at the end of §10.4 (including the password-reset email, never tested) and the §9.4 test.

---

## 11. Phase 2 demo fixes: listings

**Status (2026-09-26): passed on two phones.** Written on branch `claude/phase2-demo-fixes` (from `master` at `8cc18e3`, which contains PR #4; PR #5). **Built with AGP at `4b6aac6`; migration `20260929_phase2_listing_details.sql` applied to the live project; the §11.4 two-phone test and its regression round passed on 2026-09-26 (§11.6).** Fixes to finished Phase 2 features, found in the developer's audit; not part of Phase 5.

### 11.1 What changed

- **Real donor names** on the Home cards, the details (donor chip and Donor/Seller row), the Donor page, Saved Listings and the Home search ("Search food or donors"). They come from `get_listing_donor_names(uuid[])` (§11.2), looked up by post IDs after the feed loads. If that lookup fails, the listings still load and show "Donor name unavailable". The donor line uses a plain person icon instead of the verified-user icon, since nothing verifies donors.
- **No ratings or distances.** Removed: "★ New" on cards and details, "★ … — sample rating", "This profile uses the selected listing's sample information." and the address on the Donor page; the Home location row ("Del Rosarrio, Balilihan"); the Nearby chip and its line in the filter help; "Within 1.5 km"; the Nearest sort; the Nearby/Popular badge. Sorts are "Newest" (default, by `created_at`) and "Name". "Available near you" is now "Available food".
- **Newest listings.** The feed loads newest first. The "Recommended for you" row is now "Newest listings" (the two newest); its See all opens the normal all-listings page sorted by Newest. The separate Recommended page and "Browse all 10 food listings" are gone. The Home list is keyed by listing ID.
- **Pickup Address and Maps** use the listing's `pickup_location`. "The sample listing has no exact address or map pin yet." is removed. "Search area in Maps" searches the location as entered, adding ", Philippines" when the text doesn't already contain "Philippines"; "No maps app is available on this device." stays. The Buy/Claim checkout's "Pickup area" line uses the same value, so it now shows the listing's location.
- **Donor page:** title "Donor" or "Seller", the name, "Food donor"/"Food seller", button "Message donor"/"Message seller", and the listing's real cover photo (with the same fallbacks as the cards).
- **Create Listing** saves the pickup window, the handover choice (the field is now called "Handover": "Meet at pickup location" / "Collect from provider"), allergens and storage notes (blank = not provided). Removed: the "Smart options" panel (partial claims, maximum per recipient, whole batch) and its check, its lines in Recommendation insights, and "Recipient distances cannot be calculated from this form yet."; the pickup location starts empty. New checks: "The food name must be 160 characters or fewer.", "The pickup location must be 300 characters or fewer.", "Allergens must be 300 characters or fewer.", "Storage notes must be 300 characters or fewer." The published panel shows "Photos: N".
- **Up to 4 photos.** Each is shrunk to at most 1600 px (upright), re-encoded as JPEG off the main thread, and uploaded to `<firebaseUid>/<postId>/image.jpg` (the cover, `image_url`) and `photo-2.jpg`–`photo-4.jpg` (`extra_image_paths`). If an upload or the insert fails, the photos already uploaded are deleted (best effort). A photo that can't be read: "One of the photos could not be read. Please choose another photo." Deleting a listing deletes all its photos (best effort); the delete dialogs say "This permanently removes this listing and its photos."
- **Details:** a swipeable gallery (`HorizontalPager`) with a "1 / 4" counter when there is more than one photo; photos 2–4 get their own 1-hour signed URLs when the details open, and each photo has its own "Image unavailable" fallback. The summary's third column is the expiry, e.g. "Sep 27, 6:00 PM", captioned "Expires"; the last caption is "Status". Rows: Allergens (the saved text or "Not provided — ask the donor"), Pickup Window (e.g. "Sep 27, 3:00 PM – 5:00 PM", or "Not set — ask the donor"; it still opens the demo picker), **Handover** (new; the label or "Not set — ask the donor"; tapping it opens the chat with "Hi! How will the handover for <name> work?"), Pickup Address, Storage Note (the saved text or "Not provided — ask the donor"), Donor/Seller. The allergen guess from the demo picture is gone. Blank allergens never read as allergen-free.
- **Edit listing.** "Edit listing" in the Activity → My Listings dialog and "Edit" in Post → My posts, only for the signed-in user's own, unexpired listings. The full-screen "Edit Listing" page has the donate/sell choice and price, name, category, description, expiration, pickup location, pickup start and end (with "Clear pickup times"), handover, allergens and storage notes, prefilled, and "Quantity, unit and photos can't be changed." Buttons "Save changes" ("Saving…" while it runs) and "Cancel". Checks match the server's: the pickup window may already have started, and both times may be empty, but not just one ("Choose both pickup times, or neither."). Result: "Listing updated." (My Listings and the feed reload), or the server's short error (for example "Pickup must end on or before expiration.", "You can edit only your own listing.", "This listing no longer exists.", "This listing has expired and can no longer be edited."), or "Your listing could not be updated. Check your internet connection and try again." The unused `FoodRepository.updateFoodPost()` is removed.
- **No dropped saves.** `FoodViewModel` runs its requests one at a time: Post Listing, Save changes and Delete wait for a running feed or My Listings load instead of being ignored, so "Posting…" / "Saving…" always ends in a result or an error. A second write before the first has finished (a double tap) is ignored and its button is disabled.
- **Saved Listings.** The bookmark icons on the cards and the details save or remove at once and show the saved state; they are hidden on your own listings. If the server refuses, the icon switches back and "Your bookmark could not be saved. Check your internet connection and try again." appears. Bookmarks load once when Home starts (per profile, like the alerts). Profile menu: "Saved Listings" / "Listings you bookmarked". The page: "Listings you bookmarked, newest first."; each card has the cover, name, donor name, "quantity • price", "View listing" (opens the details by the listing's ID, from the row just loaded) and "Remove"; a listing that is gone, fully reserved or expired shows "No longer available" and only "Remove". Empty: "You have no saved listings yet. Tap the bookmark on a listing to save it here."; loading: "Loading your saved listings…"; error: "Your saved listings could not be loaded. Check your internet connection and try again." The sample items, "Restore examples", the "Example saved…" texts and the sample food page are gone.
- **Expired.** My Listings and Post → My posts show "Expired" once `expiration_date` has passed, with "Expired on" instead of "Expires". "Active Listings" and its filter count only unexpired, available listings.
- **Unchanged (later tasks or not in scope):** Buy Now / Claim Now and the checkout, the pickup-time picker, the "Reserved" dialog (§9.6), Delete erasing Purchases and Claims (§9.6), the Activity location picker and footer card, the Profile stats, Reviews, the Identity Verified badge, Report, notification settings and the Profile bell, the Home filter help (minus the Nearby line), the "Help reduce food waste" card.

### 11.2 Database (`supabase/migrations/20260929_phase2_listing_details.sql`)

Run it by hand in the SQL Editor after `20260928_profile_updates.sql` and **before** installing a build of this branch: Create Listing sends the new columns and fails without them. Older builds keep working after it (supabase-kt ignores unknown columns). Safe to run again. Check: `select column_name from information_schema.columns where table_schema = 'public' and table_name = 'food_posts' and column_name in ('pickup_start','pickup_end','handover','allergens','storage_notes','extra_image_paths');` returns 6 rows, and `select proname from pg_proc where proname in ('update_my_food_post','get_listing_donor_names');` returns 2.

- **New `food_posts` columns**, nullable or with a default: `pickup_start`, `pickup_end` (both or neither; end after start; end ≤ `expiration_date`), `handover` (`MEET_AT_PICKUP_LOCATION` or `COLLECT_FROM_PROVIDER`), `allergens` and `storage_notes` (≤ 300 characters), `extra_image_paths text[] not null default '{}'` (≤ 3 paths, no nulls).
- **Insert policy** recreated with the same rules plus: `image_url` and every extra path must be inside `<token sub>/<post id>/` and contain no `..`.
- **Direct updates closed:** the Phase 2 policy "Users may update their own food posts" is dropped and UPDATE on `food_posts` is revoked from `authenticated`. Listings now change only by insert, the reservation functions and `update_my_food_post`. **Do not run `20260923_phase2_food_posts.sql` again:** it would restore both.
- **`update_my_food_post(p_food_post_id, p_name, p_description, p_category, p_price, p_pickup_location, p_expiration_date, p_pickup_start, p_pickup_end, p_handover, p_allergens, p_storage_notes)`**: SECURITY DEFINER, `search_path = public`, caller from the token only. It locks the row, refuses a missing listing ("This listing no longer exists."), someone else's ("You can edit only your own listing.") and an expired one, checks the same limits as the table and Create Listing, and changes only those fields (never quantity, unit, status, donor, photos or `created_at`; `updated_at` comes from the existing trigger). Existing reservations keep their `unit_price`.
- **`get_listing_donor_names(p_food_post_ids uuid[])`**: SECURITY DEFINER; returns `(food_post_id, donor_name)` only, and only for listings the caller may see under the `food_posts` SELECT policy (available and unexpired, their own, or one they reserved). `users` RLS is not widened.
- **`saved_listings`** (`user_id`, `food_post_id`, `created_at`; primary key (user, post); both foreign keys cascade on delete): RLS select/insert/delete only your own rows; insert only for a listing you can see that is not your own; no UPDATE; revoked from `anon`.
- Both functions are revoked from `public` and `anon` and executable by `authenticated`. `create_reservation`, `cancel_reservation`, `claim_reservation`, the Phase 4 triggers and the price trigger are unchanged.

### 11.3 How this was checked (2026-09-26)

- **SQL:** all migrations run in order on a local PostgreSQL 16 with stand-ins for Supabase's roles and `auth.jwt()`, a listing inserted before the new file ("old listing"), then `20260929_phase2_listing_details.sql` run three times without errors. Confirmed with three accounts:
  - the old listing stays readable, with null new fields and an empty photo list;
  - a listing with all new fields and 3 extra photos saves; one-time-only, end-before-start and end-after-expiry windows, a bad handover code, 301-character allergens or storage notes and 4 extra photos are refused;
  - photo paths in another user's folder, for another post ID, with `..` or with a lookalike folder (`uidBX/…`) are refused; a listing without photos saves;
  - a direct UPDATE of `food_posts` is refused for the owner too (`permission denied`);
  - `get_listing_donor_names` returns only the name, for available listings, the caller's own (also expired) and ones they reserved (also fully reserved); nothing for an outsider on a fully reserved or expired listing, a foreign token or a fake ID; `anon` is refused;
  - `update_my_food_post`: the owner's edit changes name, price, window, handover and notes (blank allergens become null), trims the name, sets `updated_at`, and leaves quantity, unit, status, donor and photos unchanged; a window that already started is accepted; all 15 validation errors appear with their texts; a missing listing, another user's listing, an expired listing, `anon` and a foreign token are refused; the old listing is edited with both times empty;
  - after the price changed from 45.10 to 50.00, the existing reservation kept `unit_price` 45.10; reserve, cancel and claim still work and create their Phase 4 alerts (`RESERVATION_CREATED`, `RESERVATION_CANCELLED`, `RESERVATION_CLAIMED`, `MESSAGE`);
  - `saved_listings`: saving works; a duplicate with `on conflict do nothing` is ignored; saving for another user, an invisible (expired) listing, an outsider's view of a fully reserved listing and your own listing are refused; UPDATE is refused; each user sees and deletes only their own rows; rows cascade when the listing is deleted;
  - `supabase/tests/phase4_live_safety_check.sql` on this data: 21 passed, 0 failed, 0 skipped (with one saved alert-settings row added, so check 11 does not skip).
- **Kotlin:** all sources compiled with kotlinc 2.2.10 and the Compose and serialization plugins against the supabase-kt 3.5.0 JVM jars and the Compose desktop jars (foundation 1.9.1 has `HorizontalPager`), with small stand-ins for Android and Firebase APIs: no errors, only the three old deprecation warnings. A JVM check: `NewFoodPost` encodes `"pickup_start":null, … "extra_image_paths":[]` (every new field is sent); a `food_posts` row with `extra_image_paths` and `handover` decodes; supabase-kt's default `Json` ignores unknown keys and keeps nulls. **This is not an Android build**; the AGP build is the real check.
- **No new libraries.**

### 11.4 Two-phone test (A on phone 1, B on phone 2)

**Preconditions:** run `20260929_phase2_listing_details.sql` and its check (§11.2). Build `claude/phase2-demo-fixes` and install it on both phones. A has at least one **unexpired listing posted before the migration** (the "old listing") that stays unexpired for the whole test; B has not reserved it. Have 4 photos on phone 1. Write down the old listing's name. The steps are in the order they were run on 2026-09-26: `EXPIRY TEST` expires 20 minutes after it is posted, so its edit (step 5) and B's bookmark (step 6) come first.

**A, part 1 (phone 1)**
1. [New] Home: no location row under the greeting. Chips: All, Donation, For Sale, Expiring Soon (no Nearby). The filter button's text has no "Nearby" line. The row is "Newest listings", with no Nearby/Popular badge on its cards. The list title is "Available food" and "Sort: Newest"; the sort menu has Newest and Name. No "★" anywhere on the cards. Your own listings have no bookmark icon.
2. [New] Post: the pickup location field is empty; there is no "Smart options" panel; the handover field is called "Handover". "I want to sell", name `GALLERY TEST`, **4 photos**, any category, quantity `6`, unit `packs`, price `20.00`, expiration tomorrow 6:00 PM, pickup location `Balilihan Town Plaza, Bohol`, pickup starts tomorrow 3:00 PM, ends tomorrow 5:00 PM, Handover "Collect from provider", allergens `Contains peanuts`, storage notes `Keep chilled` → Post Listing. "Listing published" shows "Photos: 4".
3. [New] Supabase dashboard → Storage → `food-images` → A's UID folder → the new post's folder has `image.jpg`, `photo-2.jpg`, `photo-3.jpg`, `photo-4.jpg`. Write down the folder name (the post ID).
4. [New] Post: `EXPIRY TEST`, "I want to donate", 1 photo, quantity `1`, any unit, pickup location `Plaza`, expiration **20 minutes from now**, pickup starts 4 minutes from now, ends 15 minutes from now → Post Listing.
5. [New] After `EXPIRY TEST`'s pickup has started (4 minutes after posting) and before it expires: Post → My posts → `EXPIRY TEST` → "Edit". The Edit Listing page shows its values and "Quantity, unit and photos can't be changed." Change the description → Save changes → "Listing updated.". Then go straight to phone 2.

**B, part 1 (phone 2)**
6. [New] First, before `EXPIRY TEST` expires: bookmark it on its Home card; the icon fills.
7. [New] Home: `GALLERY TEST`'s card shows A's real name next to a person icon (not "Food donor"), and "Available  •  Balilihan Town Plaza, Bohol". Typing A's first name in the search box shows A's listings.
8. [New] Open `GALLERY TEST`: the header shows "1 / 4"; swipe through the 4 photos. The donor chip shows A's name; there is no "⭐" chip. The four columns: "6 packs" / Available, "₱20.00" / For sale, "<tomorrow>, 6:00 PM" / Expires, "Available" / Status. Rows: Allergens "Contains peanuts"; Pickup Window "<tomorrow>, 3:00 PM – 5:00 PM"; Handover "Collect from provider"; Pickup Address "Balilihan Town Plaza, Bohol"; Storage Note "Keep chilled"; Seller "<A's name>".
9. [New] Tap Pickup Address: the page shows "Balilihan Town Plaza, Bohol" and no "sample listing" sentence. "Search area in Maps" opens Maps searching "Balilihan Town Plaza, Bohol, Philippines". Back. Tap Seller: the title is "Seller", with A's name, "Food seller", no address, no rating, no "sample information" text, the button "Message seller", and `GALLERY TEST`'s real cover under "Listing from this provider". Close.
10. [New] Open A's old listing: one photo and no counter; Allergens "Not provided — ask the donor", Pickup Window "Not set — ask the donor", Handover "Not set — ask the donor", Storage Note "Not provided — ask the donor". Tap Handover: the chat with A opens with "Hi! How will the handover for <name> work?" ready to send. Close the chat without sending.
11. [New] Bookmark `GALLERY TEST` on its Home card: the icon fills. Open it: the bookmark at the top right is filled too. Also bookmark the old listing.
12. [New] Profile: "Saved Listings" / "Listings you bookmarked". Open it: "Listings you bookmarked, newest first.", with the three listings, newest saved first; each shows its cover, name, A's name, and quantity • price (e.g. "6 packs • ₱20.00"). No example items, no "Restore examples". If `EXPIRY TEST` has expired by now, it shows only "No longer available" and "Remove"; leave it. Tap "View listing" on `GALLERY TEST`: its details open.
13. [New] Log out and sign in as B again. On Home, the bookmark icons of `GALLERY TEST` and the old listing are already filled, without opening Saved Listings.
14. [Regression] Reserve 2 of `GALLERY TEST` (the "Reserved" dialog does not appear, §9.6). Activity → My Reservations: "2 packs • ₱40.00".

**A, part 2 (phone 1)**
15. [New] Activity → My Listings → `GALLERY TEST` → "Edit listing". Set the pickup end after the expiration (tomorrow 7:00 PM) → Save changes → "Pickup must end on or before expiration.". Tap "Clear pickup times", then set only Pickup starts → "Choose both pickup times, or neither.".
16. [New] Now set the name `GALLERY TEST 2`, price `25.00`, pickup starts tomorrow 3:00 PM, ends tomorrow 5:30 PM → Save changes → an "Edit Listing" dialog: "Listing updated.". My Listings shows `GALLERY TEST 2`.
17. [New] Post → My posts → the old listing → "Edit": Pickup starts and Pickup ends show their "e.g." examples (empty), Handover shows its example. Change only the description → Save changes → "Listing updated.".
18. [Regression] Activity → My Donations: B's reservation, now named `GALLERY TEST 2`, still shows "2 packs • ₱40.00"; its details say "Price: ₱20.00 per unit" and "Collect ₱40.00 at pickup."
19. [New] After `EXPIRY TEST`'s expiration: Activity → My Listings shows it as "Expired"; its dialog shows "Expired on: …" and no "Edit listing" button. The Active Listings number does not count it, and tapping Active Listings does not list it. Post → My posts: "Status: Expired" and no Edit.

**B, part 2 (phone 2)**
20. [New] Reload Home (Post → Back): the card shows `GALLERY TEST 2` and "4 packs  •  ₱25.00"; its Pickup Window reads "<tomorrow>, 3:00 PM – 5:30 PM". Activity → My Reservations still shows "2 packs • ₱40.00".
21. [New] Saved Listings: `EXPIRY TEST`'s card shows only "No longer available" and "Remove", with no photo or name (B can no longer see an expired listing it did not reserve). Tap Remove: it disappears.
22. [New] Reserve the remaining 4 of `GALLERY TEST 2` ("Pay ₱100.00 at pickup"). It leaves Home, and My Reservations shows "2 packs • ₱40.00" and "4 packs • ₱100.00" (the new price). Saved Listings: its card shows its cover, name, A's name and "0 packs • ₱25.00", then "No longer available" and only "Remove". Leave it saved.

**A, part 3 (phone 1)**
23. [New] Activity → My Listings → `GALLERY TEST 2` → Delete → "This permanently removes this listing and its photos." → Delete. This also deletes B's two reservations on it (§9.6). Supabase Storage → `food-images` → A's UID folder: the folder from step 3 is gone.

**B, part 3 (phone 2)**
24. [New] Saved Listings: `GALLERY TEST 2` is gone; the old listing is still there. Remove it: the list shows "You have no saved listings yet. Tap the bookmark on a listing to save it here."
25. [New] Home: the old listing's bookmark icon is empty again. Activity → My Reservations no longer shows the two `GALLERY TEST 2` reservations.

**Regression (Phases 1–4 and §9), A (phone 1)**
26. Post `REGRESSION TEST`: "I want to donate", 1 photo, quantity `3`, unit `packs`, pickup location `Plaza`, expiration 2 hours from now, pickup from 30 minutes to 1 hour 30 minutes from now → "Listing published".
27. Profile → Account Details: add " Test" to your display name → "Save account details" → "Saved to your account."

**Regression, B (phone 2)**
28. Reload Home: `REGRESSION TEST` shows A's new name, "3 packs  •  Free" and "Expires soon  •  Plaza". Donation shows it and no priced listings; For Sale does not show it; Expiring Soon shows it; All shows everything.
29. Reserve 1: My Reservations shows "1 packs • Free". Cancel it: "Reservation cancelled. The quantity is available again."
30. Reserve 1 again.
31. In its details tap Handover: the chat opens with "Hi! How will the handover for REGRESSION TEST work?" → Send. Leave the chat open.

**Regression, A (phone 1)**
32. Alerts: "New message from <B's name>" ("REGRESSION TEST: Hi! How will the handover for REGRESSION TEST work?"), "New reservation" twice ("<B's name> reserved 1 packs of REGRESSION TEST."), and "Reservation cancelled" ("<B's name> cancelled their reservation of REGRESSION TEST. The quantity is available again.").
33. Messages → the chat with B → reply `Meet at the plaza gate.`
34. Activity → My Donations → B's reservation ("1 packs • Free") → Mark claimed → "Reservation marked as claimed."
35. Profile → Account Details: remove " Test" → "Saved to your account."
36. Activity → My Listings → `EXPIRY TEST` → Delete → Delete: it disappears.

**Regression, B (phone 2)**
37. The open chat shows "Meet at the plaza gate." without being reopened.
38. Alerts: "Reservation claimed" ("The donor marked your reservation of REGRESSION TEST as claimed.") and "New message from <A's name>" ("REGRESSION TEST: Meet at the plaza gate.").
39. Activity → Claims: `REGRESSION TEST`, "1 packs • Free"; My Reservations no longer lists it. Reload Home: A's name no longer ends with "Test".

Not covered by this round (use §4, §9.4 and §10.4 when those areas change): email sign-up, "Keep me signed in", Google sign-in, the profile photo, the password change, a priced claim in Purchases, and the password-reset email.

### 11.5 Known limits

- Recipients are not told when a listing they reserved is edited, and nothing updates live: others see an edit after their next reload.
- Photos can't be edited; to change them, post a new listing. Expired listings can't be edited either.
- Saved Listings doesn't update live; it reloads when opened, and the bookmark icons load once per session.
- Listings made before the migration have no pickup window or handover until they are edited.
- Nothing on the server sets the `EXPIRED` status; the app works it out from `expiration_date`.
- The Maps button is a text search for the pickup location, not a map pin. Maps is Phase 5.
- Deleting photos (after a failed post, or with a listing) is best effort; a failure can leave files in Storage.
- The pickup-time picker in the details (the "Pickup Window" row) is still the demo picker and does not reserve anything.
- `get_listing_donor_names` repeats the `food_posts` SELECT rule in SQL; if that policy changes, the function must change with it. (The `saved_listings` insert policy reads `food_posts` under its RLS, so it follows automatically.)
- **Blank Saved Listings card.** A saved listing the user can no longer see (expired, and not reserved by them) is hidden by the `food_posts` SELECT policy, so its card shows only "No longer available" and "Remove", without its name or photo. Seen in the §11.4 test (step 21). **Fixed in §12 (`get_my_saved_listing_names`); passed on two phones on 2026-09-27.**
- **Dates follow the phone's language.** The details times (`listingTimeFormat` and `listingClockFormat` in `MainActivity.kt`), the time buttons of Create and Edit Listing (`postTime()` in `PostScreen.kt`), Activity, Alerts and Chat format dates with `Locale.getDefault()`, while "Member since" uses English; on a phone set to another language the month names and AM/PM change. Found in review; not tested. **Changed in §12 (every date uses `Locale.ENGLISH`); built, but not checked on a phone set to another language.**
- **Long error text on Post Listing; no error on Delete.** `FoodViewModel.errorText()` shows the whole exception message on Post Listing, which for a Supabase error includes the request URL (headers masked); Edit uses the short `supabaseErrorText()`. _Correction (2026-09-26):_ Delete errors were never shown at all; they went to the feed's error state, which no dialog displays and the next load clears. Found in review; not tested. Both are addressed in §12: the short Post Listing error passed on a phone on 2026-09-27 (flight mode), and the Delete result dialog passed for "Listing deleted." and "Listing removed…"; a Delete error was not triggered.

### 11.6 Results (2026-09-26)

- **Build:** `assembleDebug` of `4b6aac6` on the developer's PC: BUILD SUCCESSFUL in 1m 34s, only the three old deprecation warnings. Installed on both phones after the migration.
- **Migration:** the developer ran `20260929_phase2_listing_details.sql` in the SQL Editor before installing, and the test used every new column, both new functions and `saved_listings`. After the test, a read-only check query on the live project returned true for all 8 of its checks: the 6 new columns, both functions and `saved_listings` exist; `authenticated` has no UPDATE privilege on `food_posts` and no UPDATE policy is left on it, while INSERT is still granted; `authenticated` can execute both functions and `anon` cannot.
- **Two-phone test:** §11.4 steps 1–25 passed, given in parts in the order §11.4 now shows. The first draft had `EXPIRY TEST` expire 15 minutes after posting, with its edit in A's part 2; it was changed so that the edit and B's bookmark happen before it expires.
- **Regression round:** §11.4 steps 26–39 passed. What it did not cover is listed after step 39.
- **Expected, not new:** the "Reserved" dialog never appeared after Reserve, and deleting `GALLERY TEST 2` also deleted B's two reservations on it (both §9.6). A reservation kept its price after the listing's price changed, and a new one used the new price (steps 18 and 22).
- **New small issue:** the blank Saved Listings card (§11.5).
- **Not checked on the live project:** the other negative checks of §11.3 (the photo-path rules, the saved-listing refusals, who can see donor names, the refusals of `update_my_food_post`) ran only on the local PostgreSQL copy. The missing UPDATE privilege and policy were confirmed live (above), but no direct UPDATE was attempted there.


## 12. Phase 3 demo fixes: reservations, Activity, Profile

**Status (2026-09-27): passed on two phones, and merged into `master` through PR #7 on 2026-09-27 (merge commit `c184d9b`).** Written on branch `claude/phase3-demo-fixes` (from `master` at `aa3e823`). Built with AGP at `24fe9f2`. Migration `20260930_phase3_demo_fixes.sql` applied to the live project on 2026-09-26; its read-only check passed 13 of 13 and the live safety check 14 of 14. The §12.4 two-phone test passed on 2026-09-27 (§12.6). The SQL was also tested on a local PostgreSQL 16 copy (§12.3). These are fixes to finished Phase 3 features, not part of Phase 5.

### 12.1 What changed

- **Reserve review page and receipt** (`FoodActions.kt` `ReservationReviewDialog`, `MainActivity.kt` `FoodDetailsScreen`).
  - Buy Now, Claim Now and the fake checkout and reserve pages are gone. The details page keeps Message and Reserve; on your own listing it shows "Your listing / You cannot reserve it". Near Reserve it shows the amount: "You'll pay ₱X at pickup for N <unit>." / "The app takes no payments.", or "Free — nothing to pay." / "Please be respectful and pick up on time."
  - Reserve opens the full-screen "Review reservation" page: cover, name, "From <donor>", Reserving as, quantity and unit, pickup location, pickup window, handover ("Not set — ask the donor"), expiry, the price (per unit, total, "Paid at pickup. The app takes no payments.") or "Free", and an optional message to the donor. "Confirm reservation" shows "Reserving…" and is disabled while it runs.
  - On success the page becomes the "Reserved" receipt ("You reserved N <unit> of <name>.", the amount, location, pickup window, whether the message was sent, "You'll find this reservation in Activity → My Reservations.", and Message donor/seller and Done). Done and Back close the receipt and the details and reload the feed. This fixes the missing "Reserved" dialog of §9.6.
  - On failure the page stays open with the server's short error text, or "Your reservation could not be made. Check your internet connection and try again."
  - When the donor name is unavailable, no sentence uses it: "the donor" (or "the seller" for a priced listing) is used instead and the "From" line is left out.
  - The receipt's button reads "Message seller" for a paid reservation and "Message donor" otherwise.
- **Pickup times and the note go through the chat.** The demo pickup picker (`PickupOptions`) and the demo chat in `FoodActions.kt` are removed. The Pickup Window row opens the chat with "Hi! When can I pick up <name>?". The review page's note is sent after the reservation succeeds (`open_reservation_conversation`, then `send_message`); if sending fails, the reservation stands and the receipt says so.
- **Delete through `remove_my_food_post`.** Clients can no longer delete `food_posts` rows directly. A listing nobody ever reserved is deleted with its photos; otherwise it becomes `REMOVED`: hidden from Home, My Listings, saving and reserving; its open reservations are cancelled with `cancel_reason = 'LISTING_REMOVED'`, each recipient gets one "Listing removed" alert (it follows the "Reservation updates" switch and opens that reservation in My Reservations); past reservations, photos and chats are kept, and its saved-listing rows are deleted. Both delete dialogs explain this, and a "Delete listing" dialog shows the result.
- **Activity.** The sample records, the location picker, the "Demonstration record" and closing lines are removed; the footer says "Share surplus food by creating a listing." with Post Food. A record without a photo shows a plain tile. Reservations on a removed listing say "Listing: Removed by the donor" / "Removed by you" with "Cancelled because the donor removed this listing.", "The donor has since removed this listing." or "You removed this listing." Cancel and Mark claimed are disabled while a request runs, and a failed load shows "Your reservations could not be loaded. Check your internet connection and try again."
- **`ReservationViewModel`** runs requests behind a `Mutex`, queues a refresh instead of dropping it, and exposes `saving`, `reserveResult` and `loadFailed`.
- **Profile.** Successful Claims (own reservations marked claimed) and Successful Donations (claimed reservations on own listings) are real, and together equal Activity's Completed. Their pages list the records newest first ("3 packs • ₱135.30", "Picked up Sep 26, 2026 • 3:00 PM", "Recipient: <name>"), with loading, error and empty states. A record page shows "View listing" only while the listing is available, and opens it by ID. The demo totals (38/24), "Demo total…", "DEMO-104", the by-title food lookup and `SectionFoodPhoto` are removed. A caption says "Verified badge, rating and response rate are demo values".
- **Phase 2 leftovers (§11.5).** Post Listing and Delete errors show the short text ("Your listing could not be published. Check your internet connection and try again." / "Your listing could not be deleted. …"). All dates use `Locale.ENGLISH`. A saved listing the user can no longer see shows its name and photo from `get_my_saved_listing_names()`.

**Left as they are:** the Profile bell, the Food recommendations and Pickup reminders switches, sound, compact preview, Response Rate, Ratings & Reviews, Report a concern, the Identity Verified badge, the Home filter help and "Help reduce food waste" card, and a donor cancelling a single reservation.

### 12.2 Migration `20260930_phase3_demo_fixes.sql`

Run it by hand in the SQL Editor after `20260929_phase2_listing_details.sql` (see `supabase/README.md`). It is safe to run again. It adds `REMOVED` to the `food_posts.status` check, `reservations.cancel_reason`, and `RESERVATION_REMOVED` to the `notifications.type` check; drops the `food_posts` DELETE policy and revokes DELETE; refuses removed listings in `create_reservation`, `update_my_food_post` and the `saved_listings` insert policy; makes `notify_reservation_change` skip listing-removal cancellations; and adds `remove_my_food_post(uuid)` and `get_my_saved_listing_names()` (SECURITY DEFINER, `authenticated` only).

Afterwards, run `supabase/tests/phase3_demo_fixes_check.sql` (read-only, 13 checks, expect all PASS) and `supabase/tests/phase3_live_safety_check.sql` (always ends with the error "SAFETY CHECK FINISHED", which rolls everything back; expect every check PASS or SKIP).

### 12.3 Local results (PostgreSQL 16, not the live project)

- All migrations in order, then the new one twice more: no errors, exactly one check constraint each on `food_posts.status` and `notifications.type`, and `authenticated` has only SELECT and INSERT on `food_posts`.
- Functional tests: outsider, `anon` and foreign-token calls refused; a reserved listing with two open reservations by one recipient and one claimed by another → `REMOVED`, 2 cancelled, quantity unchanged, one alert linked to the newest reservation, no alert for the donor or the claimed recipient, saved rows deleted, chats kept; removing again → `REMOVED`, 0; reserving, saving and editing a removed listing refused; a never-reserved listing → `DELETED`; single-reservation wording; no alert with the preference off; a recipient's own cancel still alerts the donor; bad `cancel_reason` values rejected; saved names are the caller's own only.
- Concurrency (two sessions, 2 s hold): cancel/claim/create racing a remove in either order all end consistently, and the log shows no deadlock.
- `phase4_live_safety_check.sql`: 21/21 PASS. `phase3_demo_fixes_check.sql`: 13/13 PASS. `phase3_live_safety_check.sql`: 14/14 PASS and fully rolled back; the SKIP path was checked too.

### 12.4 Manual test (A → B → A; phone 1 = A, phone 2 = B)

The steps in the order they were run on 2026-09-27 (§12.6). **Preconditions:** the migration and both checks in §12.2 have been run, and this branch is installed on both phones right after the migration. A (phone 1) and B (phone 2) are signed in. Each step is tagged [New] or [Regression].

**A, part 1 (phone 1)**
1. [New] Home → one of A's own listings: two buttons, "Your listing / Questions go to Messages" and "Your listing / You cannot reserve it". There is no Buy Now or Claim Now.
2. [Regression] Post `P3 PAID`:
   - "I want to sell", 1 photo, quantity `5`, unit `packs`, price `45.10`;
   - expiration a day or more ahead, and any pickup location;
   - pickup from 3:00 PM to 5:00 PM before that, and handover "Collect from provider".

   Expect "Listing published".
3. [Regression] Post `P3 FREE`: "I want to donate", 1 photo, quantity `3`, unit `pcs`, no pickup times and no handover.
4. [New, setup] Post `P3 EXPIRY`: "I want to donate", 1 photo, quantity `1`, expiration 15 minutes from now.
5. [New] Activity:
   - There is no "Quezon City, Metro Manila" row under "My Activity".
   - The "Thanks for making a difference!" card opens "Share surplus food by creating a listing." with Close and Post Food.
   - A My Listings record has no "This is your live Supabase listing." line.
6. [New] Note Activity's Completed (A0), and Profile's Successful Claims (A1) and Successful Donations (A2). A1 + A2 = A0. Under the stats: "Verified badge, rating and response rate are demo values".

**B, part 1 (phone 2)**

7. [New] Before it expires, bookmark `P3 EXPIRY`. Note B's Completed (B0) and Successful Claims (B1).
8. [New] Open `P3 PAID`:
   - two buttons, "Message Seller" and a green "Reserve";
   - under them, "You'll pay ₱45.10 at pickup for 1 packs." and "The app takes no payments.";
   - at quantity 3, "You'll pay ₱135.30 at pickup for 3 packs."
9. [New] The Pickup Window row opens the chat with A, with "Hi! When can I pick up P3 PAID?" ready to send. Close it without sending.
10. [New] Pickup Address: the card at the top shows P3 PAID's photo. Before, it showed the demo produce picture.
11. [New] Reserve (quantity 3). "Review reservation" shows:
    - the photo, "From <A>" and "Reserving as <B>";
    - Quantity "3 packs", the pickup location, and Pickup window, for example "Sep 27, 3:00 PM – 5:00 PM";
    - Handover "Collect from provider", and Expires;
    - Price per unit "₱45.10", Total "₱135.30", and "Paid at pickup. The app takes no payments."

    Type `I'll come at 5.` and tap **Confirm reservation**; it briefly reads "Reserving…".
12. [New] The "Reserved" receipt shows:
    - "You reserved 3 packs of P3 PAID." and "Pay ₱135.30 at pickup.";
    - the location and window;
    - "Your message was sent to <A>." and "You'll find this reservation in Activity → My Reservations."

    **Message seller** opens the chat showing "I'll come at 5.". **Done** closes the details, and Home shows 2 packs.
13. [New] Open `P3 FREE`. It shows "Free — nothing to pay." and "Please be respectful and pick up on time."
    - Reserve 1. The review page shows Pickup window and Handover as "Not set — ask the donor", and Price "Free".
    - Confirm with no message. The receipt shows "You reserved 1 pcs of P3 FREE." and "Free — nothing to pay.", with no message line.
14. [Regression] Reserve 1 more of `P3 PAID`. Then My Reservations → that reservation → Cancel. Expect "Reservation cancelled. The quantity is available again."
15. [New] Reserve 1 of `P3 PAID` twice more. B now holds 3 + 1 + 1 open, and P3 PAID leaves Home because it is fully reserved.

**Stale quantity (B on both phones)**

16. [New] Sign in as B on phone 1 too.
    - On both phones: open `P3 FREE` ("Up to 2 pcs"), set quantity 2, tap Reserve, and stay on the review page.
    - Phone 1: Confirm. The receipt appears.
    - Phone 2: Confirm. The page stays open with "Not enough food available. Only 0 remains.", and there is no receipt.
    - Sign phone 1 back in as A.

**A, part 2 (phone 1)**

17. [Regression] Alerts shows:
    - "New reservation" for P3 PAID and for P3 FREE;
    - one "Reservation cancelled": "<B> cancelled their reservation of P3 PAID. The quantity is available again.";
    - "New message from <B>": "P3 PAID: I'll come at 5."
18. [Regression] My Donations → P3 PAID "3 packs • ₱135.30". The details show "Recipient: <B>", "Reservation for your listing.", "Price: ₱45.10 per unit" and "Collect ₱135.30 at pickup.", with no closing "live" line. Mark claimed → "Reservation marked as claimed."
19. [Regression] P3 FREE "1 pcs • Free" → Mark claimed → "Reservation marked as claimed."
20. [New] Completed = A0 + 2, Successful Donations = A2 + 2, and Successful Claims = A1.
    - The Successful donations page shows "Reservations on your listings that you marked as claimed.", then P3 FREE ("1 pcs • Free", "Picked up …", "Recipient: <B>") and P3 PAID ("3 packs • ₱135.30" …).
    - The P3 PAID record shows its photo, "Claimed" and "Pickup location: …". It has no "View listing", because the listing is fully reserved.
21. [Regression] `OLD TEST` → Edit listing → change the description → "Listing updated."
22. [New] Post `P3 NEVER` (donation, 1 photo, quantity 1). My Listings → Delete. The dialog reads "If nobody has ever reserved this listing, it is deleted with its photos. If it has reservations, …". Delete → "Listing deleted."
23. [New] Delete `P3 PAID` → "Listing removed. 2 open reservations were cancelled, and their recipients were told. Its past reservations and chats are kept." It is gone from My Listings and from Post → My posts.
24. [New] Delete `P3 FREE` → "Listing removed. 1 open reservation was cancelled, and its recipient was told. Its past reservations and chats are kept."
25. [New] My Donations:
    - P3 PAID's two "1 packs" reservations are Cancelled, and their details end with "Listing: Removed by you" and "You removed this listing.";
    - the claimed ones are still Claimed;
    - Completed and Successful Donations are unchanged (A0 + 2 and A2 + 2);
    - there is no new "Reservation cancelled" alert.

**B, part 2 (phone 2)**

26. [New] Two "Listing removed" alerts, with a ✕ icon:
    - "<A> removed P3 PAID, so your 2 reservations were cancelled."
    - "<A> removed P3 FREE, so your reservation was cancelled."

    [Regression] There are also two "Reservation claimed" alerts.
27. [New] The P3 PAID alert opens My Reservations with a "1 packs • ₱45.10" reservation: "Status: Cancelled", "Listing: Removed by the donor" and "Cancelled because the donor removed this listing.", with no Cancel button.
28. [New] Purchases: P3 PAID "3 packs • ₱135.30", with its photo and "The donor has since removed this listing.". Claims: P3 FREE "1 pcs • Free", the same.
29. [New] Successful Claims = B1 + 2 (P3 FREE and P3 PAID, with no "Recipient" line). Completed = B0 + 2.
30. [New] Purchases → P3 PAID → Message donor: the chat with "I'll come at 5." opens. Send `Thanks anyway!`; it reaches A, whose chat badge goes up on phone 1.
31. [New] Home shows neither P3 PAID nor P3 FREE. Saved Listings: P3 EXPIRY (expired) shows its name and photo, "No longer available" and Remove. Before, this was a blank card. Remove it.
32. [New] With flight mode on:
    - Post Listing → "Your listing could not be published. Check your internet connection and try again."
    - On a listing still on Home, Reserve → Confirm → "Your reservation could not be made. Check your internet connection and try again."

    Turn flight mode off.

**Not covered by this test.** Use §4, §9.4, §10.4 and §11.4 when those areas change. The gaps are: email sign-up, Google sign-in, Keep me signed in, the profile photo, the password change, the photo gallery, the Delete, Cancel and Mark claimed error texts, dates on a phone set to another language, and the password-reset email.

### 12.5 Known limits

- A user who already had a chat about a listing keeps it after the listing is removed and can still open it; nobody else can start one.
- A removed listing cannot be restored.
- Nothing updates live apart from chat and alerts: other phones see a removal after their next reload.
- Deleting photos of a never-reserved listing is best effort.
- **Older app builds break against this migration:** they can't delete listings (no DELETE privilege), and their `Json` fails on the new `REMOVED` status and `RESERVATION_REMOVED` alert type. Install the new build on every phone after running the migration.
- "Listing: Removed by you / by the donor" is shown in a record's details dialog, not on its card in the list.

### 12.6 Results (2026-09-26 to 2026-09-27)

- **Build:** `assembleDebug` of `24fe9f2` on the developer's PC (2026-09-26): BUILD SUCCESSFUL in 3m 23s, with only the three old deprecation warnings. Installed on both phones right after the migration.
- **Migration and live checks (2026-09-26):** `20260930_phase3_demo_fixes.sql` was applied together with `phase3_demo_fixes_check.sql` in the same run: 13 of 13 PASS. `phase3_live_safety_check.sql`: 14 passed, 0 failed, 0 skipped, all rolled back ([results log](#results-log)).
- **Two-phone test:** §12.4 steps 1–32 passed. Steps 1–15 and steps 16–32 were reported in two sessions, both on 2026-09-27.
- **Merged:** into `master` through PR #7 on 2026-09-27 (merge commit `c184d9b`).
- **Observed:** "Listing: Removed by you / by the donor" appears in the record's details dialog, not on its card, as coded (§12.5).
- **Not checked:** the list at the end of §12.4. The concurrency results in §12.3 come from the local PostgreSQL copy only.

---

## 13. Phase 4 demo fixes: messages, alerts, Profile

**Status (2026-09-27): passed on two phones, and merged into `master` through PR #8 on 2026-09-27 (merge commit `39b57b5`).** Written on branch `claude/phase4-demo-fixes` (from `master` at `c184d9b`, the PR #7 merge) and built with AGP at `3288d02`. The migration `20261001_phase4_demo_fixes.sql` was applied to the live project on 2026-09-27, and its read-only check passed 9 of 9. A short two-phone test (§13.4, 13 steps) passed the same day (§13.6). The SQL was also tested on a local PostgreSQL 16 copy (§13.3). These are fixes to finished Phase 4 features, not part of Phase 5.

### 13.1 What changed

- **Profile bell and gear** (`ProfileScreen.kt`). The bell opens Alerts, and its orange dot shows only while there are unread alerts, like the Home and Activity bells. The gear opens the Settings page (content description "Settings"), which was reachable before only through the bell. The "Account Settings" menu row still opens Account details. The header line reads "Demo verification & rating".
- **Notification preferences** (`ProfileSections.kt`, `ProfileScreen.kt`).
  - The "Food recommendations" and "Pickup reminders" switches are removed; no alert ever used them. `NotificationPreferences` keeps both fields (`@EncodeDefault`), and Save writes back the values loaded from the account, so the columns and older builds are unaffected. No database change.
  - "App preferences" → "Compact preview" (a page of sample foods) and the "Preferences" hub are removed. The Profile menu row and the Settings link are now "Notifications / Message and reservation alerts, sound" and open "Notification Preferences" directly.
  - The page shows "New messages", "Reservation updates" and "Notification sound", with the note: "New messages and Reservation updates are saved to your account and decide which alerts you get on the Alerts page. Notification sound is saved on this phone: it plays the phone's notification sound when a new alert arrives while the app is open, and stays quiet in silent and vibrate mode. Phone notifications while the app is closed are not available yet."
- **Notification sound** (new `AlertSound.kt`, `NotificationViewModel.newAlerts`, `HomeScreen`).
  - When an alert arrives live through Realtime (never the alerts loaded when Home starts), `HomeScreen` plays the phone's default notification sound (`RingtoneManager.TYPE_NOTIFICATION`, notification audio usage) if all of these hold: the app is on screen (resumed); "Notification sound" is on; the alert is not a message of the chat that is open; the ringer is on normal (silent and vibrate stay quiet). A new sound stops the previous one, and leaving the screen stops it.
  - The switch is stored on the phone in `salusalo_session` → `notification_sound` (default on), next to "Keep me signed in"; sign-out does not clear it. The old `salusalo_profile` → `notificationSound` value is ignored.
  - No Android notification, no permission and no new library.
- **Response rate** (migration `20261001_phase4_demo_fixes.sql`, `MessageRepository.getMyResponseRate()`, `ResponseRate` in `model/Conversation.kt`, `ProfileScreen.kt`, `ProfileSections.kt`).
  - Over the last 30 days: **received** = the caller's chats whose first message the other participant sent in that period; **replied** = those in which the caller sent a message after that first message; the rate is replied ÷ received × 100, rounded. With nothing received there is no rate: the stat shows "—".
  - The stat loads when Profile opens and again when its page opens. The page shows the percentage or "—", "Response rate", "Last 30 days", then one of: "Loading your response rate…"; "Your response rate could not be loaded. Check your internet connection and try again." with "Try again"; "No one has started a chat with you in the last 30 days, so there is no rate yet."; or a card "Your last 30 days" with "Chats started by others: N", "Chats you replied to: R", "Chats without a reply: N−R" and "R ÷ N × 100 = P%" ("≈" when not exact). "How it works" reads: "Counts chats that someone else started with you in the last 30 days. A chat counts as replied once you send a message after their first message. Chats you started are not counted. A note sent with a reservation starts a chat too. Only you can see your response rate."
  - The demo "92%", "Demo reporting period", the example summary (50 / 46 / 4) and "Your actual messages are not being tracked." are removed. The caption under the stats reads "Verified badge and rating are demo values".
- **Live Messages inbox and chat badge** (`MessageViewModel.start()`, `MessageRepository.allMessageInserts()`, `realtimeInserts()`, `HomeScreen`, `ChatScreen.kt`).
  - The inbox loads when Home starts and then follows new messages in all of the caller's threads through one Realtime channel on `messages` with no filter (`realtimeInserts()`'s filter is now optional). Realtime applies the `messages` SELECT policy, so only the caller's own threads arrive.
  - A message in a thread already listed updates its last message, time, "mine" flag and order in memory; the unread count goes up only for someone else's message outside the open chat. No cover image is re-signed. A thread not listed yet (for example a new chat) is fetched once with `get_my_conversations(p_conversation_id)`. A reload keeps any thread that Realtime has already moved past the loaded copy.
  - The Home chat badge is the sum of the threads' unread counts, instead of the unread MESSAGE alerts, so it also counts while "New messages" alerts are off. The open chat is marked read, so it never counts.
  - When the app returns from the background (lifecycle ON_START after a stop, not the first start), the inbox reloads once, because Realtime misses messages while the app is stopped. The initial load and this reload show no error dialog; opening Messages still reloads the list and shows errors.
  - The Messages page's Refresh button is removed. The Realtime token refresh stays in `NotificationViewModel` only; it covers the whole client.
- **Docs.** The Phase 3 test dates are corrected to 2026-09-27 (both sessions were on that day), and Phase 3's merge through PR #7 (`c184d9b`) is recorded.

**Left as they are:** phone notifications while the app is closed (push; a later phase), Ratings & Reviews and Report a concern (Phase 5), the Identity Verified badge and the verification pages (Phase 6), the Home page, message alerts, the Home and Activity bells and the Alerts page.

### 13.2 Migration `20261001_phase4_demo_fixes.sql`

Run it by hand in the SQL Editor after `20260930_phase3_demo_fixes.sql` (see `supabase/README.md`). It is safe to run again. It adds one function, `get_my_response_rate()`, and changes no table, column, policy, table grant, trigger or existing function.

- **SECURITY INVOKER, STABLE, `search_path = public`, no arguments.** It checks `is_salusalo_firebase_jwt()` first ("Not authenticated"), then takes the caller from `current_salusalo_profile_id()` ("Profile not found" when null). It reads only rows the caller may already select, so it needs no extra rights; it also filters explicitly on the caller being the chat's recipient or donor, and the `conversations` and `messages` RLS still applies on top.
- The first message of a chat is the earliest by `created_at`, ties broken by `id`; "after" uses the same order.
- EXECUTE is revoked from `public` and `anon` and granted to `authenticated`.
- **Order:** it can run before or after the new build is installed. Older builds never call it. Until it runs, the new build shows "—" for Response Rate and "Your response rate could not be loaded…" on its page.

Afterwards, run `supabase/tests/phase4_demo_fixes_check.sql` (read-only, 9 checks, expect all PASS): the function's result type; INVOKER, STABLE and `search_path=public`; EXECUTE for `authenticated` only; the helpers executable by `authenticated`; no INSERT, UPDATE or DELETE for clients on `conversations`, `messages` and `notifications`; SELECT-only policies on them; `notify_user` not executable by clients; `messages` and `notifications` in `supabase_realtime`; no UPDATE on `users` and no UPDATE or DELETE on `food_posts` for clients.

### 13.3 Local results (PostgreSQL 16 and kotlinc, not the live project or a phone)

- **Migrations:** all in order, with `20261001_phase4_demo_fixes.sql` applied twice at the end: no errors.
- **Counts on sample data.** Caller A got received 5, replied 4. Counted and replied: a chat B started that A answered; a chat where B wrote twice before A replied (counted once); a chat whose first two messages share one `created_at`, where B's has the lower `id`; a chat made through the real RPCs (`start_listing_conversation`, `send_message`). Counted, not replied: a chat C started that A never answered. Not counted: a chat A started; a chat whose first message is 31 days old; a same-`created_at` tie where A's message has the lower `id`. B got 2 / 1, C 1 / 1, and a profile with no chats 0 / 0; each caller got only their own counts. With RLS bypassed (table owner), A still got 5 / 4, so the explicit participant filter works on its own.
- **Refusals:** `anon` → "permission denied for function get_my_response_rate"; a token from another Firebase project → "Not authenticated"; no token → "Not authenticated"; a valid token without a profile → "Profile not found"; calling it with an argument → no such function; a direct insert into `messages` as `authenticated` → permission denied.
- **Existing checks on the local copy:** `phase4_live_safety_check.sql` 21 passed, 0 failed, 0 skipped (after seeding a reservation and alert settings); `phase3_live_safety_check.sql` 14/14; `phase3_demo_fixes_check.sql` 13/13; the new `phase4_demo_fixes_check.sql` 9/9 PASS. In a rolled-back negative run (EXECUTE granted to `anon` and PUBLIC, SECURITY DEFINER, INSERT on `messages` granted, an UPDATE policy on `notifications`), checks 2, 3, 5 and 6 reported FAIL, as they should.
- **Kotlin:** a kotlinc 2.2.10 type check of all sources after each commit: 0 errors, and only the three old deprecation warnings. It uses desktop Compose jars and hand-written stubs of the Android classes (including `AudioManager`, `RingtoneManager`, `Ringtone`, `AudioAttributes` and the lifecycle owner classes), because Google Maven is blocked here. **It is not an AGP build**, and nothing ran on a phone: the sound, the ringer-mode check, the lifecycle events, the unfiltered Realtime channel and the badge are untested. (The AGP build and the phone test came afterwards; see §13.6. The ringer-mode check is still untested on a phone.)

### 13.4 Manual test (A → B → A; phone 1 = A, phone 2 = B)

The steps as run on 2026-09-27 (§13.6). The first draft of this test had 30 steps. At the developer's request, this short version of 13 steps was run instead. The checks it left out are listed after the steps, and they are not tested.

**Preconditions:**
- The migration and `phase4_demo_fixes_check.sql` (9 of 9 PASS) have been run, and this branch is installed on both phones.
- A (phone 1) and B (phone 2) are signed in, and already have at least one older chat with each other.
- Phone 1's ringer is on normal, with the notification volume up.

Each step is tagged [New] or [Regression].

**A**

1. [Regression] Post a new listing named `P4 FREE`: a donation, quantity 3, expiring a few days later, with one photo. It shows on Home.
2. [New] Profile:
   - "Demo verification & rating" is near the top, and "Verified badge and rating are demo values" is under the stats.
   - Response Rate shows a percentage or "—", never "92%". Its page: write down "Chats started by others" and "Chats you replied to" (both 0 if it shows "No one has started a chat with you in the last 30 days, so there is no rate yet.").
   - The gear (top right) opens "Settings". Its "Notifications" opens "Notification Preferences" with only "New messages", "Reservation updates" and "Notification sound", all on. There is no "Food recommendations", "Pickup reminders" or "Compact preview".
3. [New] The Profile bell opens Alerts.
   - Tap "Mark shown as read": back on Profile, the bell has no orange dot.
   - On Home, write down the number on the chat icon (none = 0).
   - Tap the chat icon: "Messages" has no Refresh button. Stay on this page.

**B**

4. [New] In Messages, open an older chat with A (not the top one) and send "Hello again". Then Home → `P4 FREE` → "Message Donor" → send "Is P4 still available?". Stay in this chat.

**A (without touching the phone)**

5. [New] Without anyone touching phone 1:
   - The notification sound plays.
   - The `P4 FREE` chat (B's name, "Your listing: P4 FREE") appears at the top with "Is P4 still available?" and an unread count of 1.
   - The older chat is right below it, with "Hello again" and 1.
   - On Home, the chat badge is 2 more than the number written down, and on Profile the bell has the orange dot.
6. [New] Open the `P4 FREE` chat and reply "Yes, it is." Stay in it.

**B**

7. [Regression] "Yes, it is." appears by itself. Reply "On my way".

**A**

8. [New] Three checks:
   - "On my way" appears in the open chat with no sound.
   - Go back, open the older chat too, then go to Home: the chat badge is back to the number written down.
   - Profile → Response Rate: both numbers are 1 more than in step 2.
9. [New] Profile → "Notifications" → turn "Notification sound" off → "Save preferences" ("Preferences saved to your account.").
   - Log out, then sign in again as A.
   - Profile → Notifications: "Notification sound" is still off.
   - Stay on Home.

**B**

10. [New] In the `P4 FREE` chat, send "Test 1".

**A**

11. [New] No sound, but the chat badge goes up by 1 by itself. Turn "Notification sound" back on and save. Press the phone's Home button, so Salu-Salo goes to the background without being swiped away.

**B**

12. [Regression] Home → `P4 FREE` → "Reserve" → "Review reservation":
    - quantity 1;
    - "Pickup at 5 PM?" in "Message to <A> (optional)";
    - "Confirm reservation" → the "Reserved" receipt.

**A**

13. [New] No sound played while Salu-Salo was in the background. Reopened from Recents:
    - the chat badge is 2 more than the number written down;
    - Messages shows the `P4 FREE` chat at the top with "Pickup at 5 PM?";
    - Alerts has "New reservation" ("<B> reserved 1 <unit> of P4 FREE.").

**Left out of the short test (not tested):**
- Sound settings:
  - silent and vibrate mode (the ringer-mode check), and Do Not Disturb;
  - the sound for a reservation alert (the one in step 13 arrived while the app was in the background);
  - "New messages" turned off: no alert and no sound, while the chat badge still counts.
- Settings pages:
  - turning "Reservation updates" off and on again (the §8.7 regression; Save itself was used in steps 9 and 11);
  - the other Settings pages opened from the gear (only "Notifications" was opened).
- Response rate:
  - B's own rate (a chat B started does not count for B; tested only on the local copy, §13.3);
  - the Response Rate error text (it needs the phone offline while the page loads).
- A Realtime reconnect while the app stays open.
- The regressions in the draft's last step:
  - removing a reserved listing and its "Listing removed" alert;
  - Mark claimed, with Purchases or Claims and the Activity totals;
  - email sign-up, editing a listing, and saving a listing.
- Also not run: Google sign-in, the profile photo, the password change, and the password-reset email (never tested).

### 13.5 Known limits

- No sound while the app is in the background or closed, and no phone notifications. Push notifications are a later phase.
- No sound for a message while "New messages" is off, because no alert is created. The chat badge still counts it.
- Every new alert plays the sound, message and reservation alerts alike.
- The sound switch belongs to the phone, not the account: every account signed in on that phone shares it.
- Do Not Disturb was not checked. The app checks only the ringer mode, not Do Not Disturb. Depending on the phone and its Do Not Disturb settings, the sound may still play while the app is open. The push-notification phase should add a Do Not Disturb check (for example `NotificationManager.currentInterruptionFilter`) and test it.
- A message missed during a Realtime reconnect while the app stays open appears the next time Messages opens, a chat is closed, or the app returns from the background.
- The response rate counts a reply however late it came, and the period is fixed at 30 days. A note sent with a reservation starts a chat, so it counts. Chats of a never-reserved listing that is deleted disappear with it (existing cascade) and drop out of the rate.
- Older builds still show "Food recommendations" and "Pickup reminders". Their stored values stay, and the new build writes them back unchanged.

### 13.6 Results

- **Build:** `gradlew.bat :app:assembleDebug --offline` at `3288d02`: BUILD SUCCESSFUL in 43s on 2026-09-27, with only old deprecation warnings (`ArrowBack`, `Logout`). The developer installed it from Android Studio on both phones.
- **Migration:** `20261001_phase4_demo_fixes.sql` applied to the live project on 2026-09-27 in the SQL Editor, with `supabase/tests/phase4_demo_fixes_check.sql` in the same run: 9 of 9 PASS ([results log](#results-log)).
- **Two-phone test:** the short §13.4 test, 13 steps, passed on 2026-09-27, reported as one result ("test passed").
- **Not tested:** the checks listed under "Left out of the short test" in §13.4, including silent and vibrate mode, Do Not Disturb, the sound for a reservation alert, and "New messages" turned off. The developer chose to test them in the later push-notification phase (§35), which reworks the same alert and sound code.
- **Merged:** into `master` through PR #8 on 2026-09-27 (merge commit `39b57b5`).

---

## 14. Phase 5 part 1: reviews, reports, Email verified

**Status (2026-09-28): passed on two phones, and merged into `master` through PR #9 on 2026-09-28 (merge commit `711cc5e`).** Written on branch `claude/phase5-reviews-reports-v6ahqn` (from `master` at `39b57b5`, the PR #8 merge) and built with AGP at `3bd038c`. The migration `20261002_phase5_reviews_reports.sql` was applied to the live project on 2026-09-27; its read-only check passed 18 of 18 and `phase5_live_safety_check.sql` 27 of 27. The two-phone test (§14.4, 15 steps) passed on 2026-09-28 (§14.6). The SQL was also tested on a local PostgreSQL 16 copy (§14.3). The developer split Phase 5 into part 1 (this: ratings and reviews, reports, and an "Email verified" badge, which moved here from Phase 6) and part 2 (Maps, not started).

### 14.1 What changed

- **Ratings and reviews** (new `ReviewsAndReports.kt`, `model/Review.kt`, `data/ReviewRepository.kt`; `ActivityScreen.kt`, `FoodActions.kt`, `MainActivity.kt`, `ProfileScreen.kt`, `ProfileSections.kt`, `viewmodel/ReservationViewModel.kt`).
  - After a reservation is **CLAIMED**, its recipient can rate the listing's donor or seller: 1–5 stars and an optional comment of up to 500 characters. There is one review per reservation; sending again changes it, and there is no delete. Donors don't review recipients. A claimed reservation of a `REMOVED` listing can still be rated, and its review counts.
  - **Where to rate:** Activity → Purchases or Claims → the record's details → "Rate <name>" ("Rate the donor"/"Rate the seller" without a name), or "Change your review" once rated. The "Reservation claimed" alert already opens that record. The page shows the listing, "3 packs • ₱135.30", five stars ("Tap a star to rate." / "4 of 5 stars"), "Comment (optional)" with a counter, and "Your name, stars and comment are shown to other users."; its button is "Send review", or "Save review" when changing, with "Sending…" while it runs. Results: "Thanks! Your review was sent." or "Your review was updated.", with Done; errors: "Choose from 1 to 5 stars.", the server's short text, or "Your review could not be sent. Check your internet connection and try again." A sent or changed review reloads the Home feed, so the new rating shows when the reviewer goes back to Home.
  - **Sending** runs in `ReservationViewModel` behind the same Mutex and `saving` flag as reserve, cancel and claim: a second tap while one runs is ignored, one that has started is never dropped (Back waits), and the result goes only to the Rate page (`reviewResult`). The account's own reviews load with the reservation lists (`get_my_given_reviews`); if that load fails, nothing is shown and the last ones are kept.
  - **Where ratings show:** "★ 4.7 (12)" next to the donor's name on the Home cards (not on the "Newest listings" cards, which show no donor name), in the details header, and on the Donor/Seller row ("<name> • ★ 4.7 (12)"); nothing when there are no reviews. The Donor/Seller page loads the donor's details and latest 20 reviews fresh when it opens: "Email verified" (when true), "★ 4.7 • 12 reviews" or "No reviews yet", and under "Reviews" each reviewer's name, stars, comment and date ("Sep 27, 2026"), with "Loading reviews…", "Reviews could not be loaded. Check your internet connection and try again." and "Showing the latest 20 reviews.".
  - **Own Profile:** the Overall Rating stat ("4.7" or "—"); the Ratings & Reviews panel (the average, "Overall Rating", rounded stars, "(12 reviews)"/"(1 review)" or "No reviews yet", and the three latest reviews, or "Donors and sellers are rated after each pickup; people who only receive food have no rating."); the reviews page ("★ 4.7" or "—", "12 reviews"/"1 review"/"No reviews yet", "Reviews from people who picked up your food.", the latest 50 reviews, loading and error texts with "Try again"). The sample reviews, `ProfileReview`, "56 reviews • demo summary" and "Example review" are gone.
  - Donor names, ratings and the badge for lists of listings come from one call, `get_listing_donor_profiles`, which the new build uses everywhere instead of `get_listing_donor_names` (Home, details, Saved Listings, and the recipient's own reservations for "Rate <name>").
- **Reports** (`ReviewsAndReports.kt` `ReportPage`, `data/ReportRepository.kt`, `model/Review.kt`).
  - Three places: "Report this listing" at the bottom of a listing's details (not your own); "Report <name>" at the bottom of the Donor/Seller page (not on your own listing); and a flag at the top right of a chat ("Report <name>"; this is how a donor reports a recipient, for example one who didn't show up).
  - Reasons for a listing: "Unsafe or spoiled food", "Wrong or misleading details", "Spam or fake listing", "Other". For a person: "Rude or abusive", "Didn't show up", "Scam or fraud", "Spam", "Other". "Other" needs details; details are optional otherwise, up to 1000 characters.
  - The page: "Report listing" / "Report <name>", "What's wrong with <listing>?" / "Why are you reporting <name>?", the reasons, "Details (optional)" or "Details (required)" with a counter, "Reports go to the Salu-Salo team. The other person is not told.", "Send report" ("Sending…"). Client checks: "Choose a reason.", "Describe the problem in a few words.". Result: "Report sent. The Salu-Salo team will review it." with Done, or the server's short text, or "Your report could not be sent. Check your internet connection and try again.". The reported person is not told.
  - Settings → Trust & safety → "Report a concern" is no longer a preview: it explains where the three Report buttons are and says "Reports go to the Salu-Salo team. The person you report is not told. You can send up to 10 reports a day." There is no report without a target.
  - The developer reads reports with `supabase/tests/phase5_reports_query.sql` until the Phase 6 admin screen.
- **Email verified** (`UserRepository`, `MainActivity.kt`, `ProfileScreen.kt`, `ProfileSections.kt`, `model/UserProfile.kt`).
  - "Identity Verified" becomes a badge that shows only once Firebase has confirmed the account's email: Google accounts automatically; email/password accounts after they open the link in a verification email the app sends (`sendEmailVerification()`).
  - The server stores it in `users.email_verified`, set only by `sync_my_email_verified()` from the token. The app calls it in the background after the profile loads: it reloads the Firebase user, refreshes the ID token when Firebase says the email is confirmed but the cached token doesn't, and calls the function. It never blocks and never signs anyone out; on any failure the stored value stays and nothing is shown.
  - Own Profile: a chip under the name, green "Email verified" or grey "Email not verified", both opening Verification. The Verification panel reads "Your email address is confirmed." or "Confirm your email address to show the Email verified badge.", with a row "Email verified"/"Email not verified", "Confirms the email address, not your identity" and "Verified"/"Not verified". "Demo verification & rating" and "Verified badge and rating are demo values" are gone. The footer reads "Rate the people you get food from, and report anything that doesn't feel right."
  - Settings → "Verification / Confirm your email address" → "Email Verification": "Email verified" or "Email not verified", the email address, and "The badge only means that this email address was confirmed. It is not an identity check." When not verified: "We'll send a link to <email>. Open it, then come back and tap “I've confirmed my email”.", "Send verification email" ("Sending…") and "I've confirmed my email" ("Checking…", which reloads the user, refreshes the token and syncs). Results: "Verification email sent to <email>. If you don't see it, check your spam folder.", "Too many attempts. Please wait a few minutes and try again.", "The verification email could not be sent. Check your internet connection and try again.", "Your email is confirmed. Your profile now shows the Email verified badge.", "Your email is not confirmed yet. Open the link in the email we sent, then try again.", "Your email could not be checked. Check your internet connection and try again."
  - For other people the badge shows only on the Donor/Seller page (not on the details screen).
  - The demo "verificationSteps" and "verificationStatus" pages, the demo request and `demoVerificationRequested` are removed. Trust & safety now has "Coordinate clearly", "What the Email verified badge means", "Ratings and reviews", "Email verification / Confirm your email address" and "Report a concern / Where to find the Report buttons"; "Understand verification" and "How verification works" are gone.
- **Docs:** the Phase 4 demo fixes' merge through PR #8 (`39b57b5`) is recorded here, in `CLAUDE.md` and in `README.md`.

**Left as they are:** Maps (Phase 5 part 2: coordinates, a map picker, distance, Nearby, "Search area in Maps", "View on map"); phone notifications and the checks left for that phase (§13.4, §13.5); Phase 6 (ID verification, the admin screen); the Home page apart from the ratings; no alert for a new review.

### 14.2 Migration `20261002_phase5_reviews_reports.sql`

Run it by hand in the SQL Editor after `20261001_phase4_demo_fixes.sql`, **before** installing this build: the new build loads donor names through `get_listing_donor_profiles`, so without the migration it shows "Donor name unavailable" and its review, report and verification calls fail. **Older builds are unaffected:** supabase-kt 3.5.0 ignores unknown keys, so `UserProfile` still decodes with the new column (checked locally, §14.3); they never call the new functions; and `get_listing_donor_names` is unchanged. It is safe to run again. Details are in `supabase/README.md`.

- **Every new function the app calls** is SECURITY DEFINER with `search_path = public`, checks `is_salusalo_firebase_jwt()` first ("Not authenticated"), takes the caller only from `current_salusalo_profile_id()` ("Profile not found"), and is executable by `authenticated` only. The helper `caller_can_view_food_post` and the trigger function `users_start_unverified` are executable by no client. The trigger function has no token check: it takes no input, returns nothing but the row and only sets its `email_verified` to false; the insert policy already checks the token.
- **`users.email_verified`** (boolean, default false). A BEFORE INSERT trigger always stores false; the users insert policy is unchanged, and clients still have no UPDATE on `users`. **`sync_my_email_verified()`** sets it to true only when the token's `email_verified` is JSON `true` and its `email` equals the profile's email (trimmed, case-insensitive), changes nothing else (`updated_at` only when the value changes), and returns the stored value.
- **`caller_can_view_food_post(uuid)`** (internal) is exactly the `food_posts` SELECT policy, like `get_listing_donor_names`. If that policy changes, change both.
- **`reviews`** (one per reservation; rating 1–5; comment 1–500 characters or null; reviewer ≠ reviewee): RLS on, no privileges at all for clients. **`submit_review`**, **`get_my_given_reviews`**, **`get_listing_donor_profiles`** (for listings the caller may see: name, `email_verified`, average to one decimal and count), **`get_listing_donor_reviews`** (at most 50), **`get_my_rating_summary`**, **`get_my_received_reviews`** (at most 100). The read functions return only the reviewer's current name, the stars, the comment and the dates.
- **`reports`** (reporter, reported person, `target_type` LISTING/USER, `source` LISTING/DONOR_PAGE/CHAT, `food_post_id` and `conversation_id` set to NULL on delete, the `listing_name` snapshot, `reason`, `details`, `status` only `OPEN`, `created_at`): RLS on, no privileges at all for clients. A check constraint allows each reason only for its target and requires details for `OTHER`. **`submit_report(p_target, p_target_id, p_reason, p_details)`**: `LISTING` or `DONOR` with a listing the caller may see that is not their own, or `CHAT` with one of the caller's own chats; the reported person (the listing's donor or the chat's other participant) is found on the server and is never the caller. One open report per reporter and listing, and per reporter and person: a pre-check plus partial unique indexes, and a `unique_violation` from a race gives the same "You already reported this.". At most 10 reports per reporter in any 24 hours, counted after taking a per-reporter advisory lock.
- **Server error texts** (the app shows them as they are, adding a full stop where missing): "Not authenticated", "Profile not found"; `submit_review`: "Choose from 1 to 5 stars.", "Your comment must be 500 characters or fewer.", "You can rate only your own pickups.", "You can rate only after the pickup is marked as claimed.", "You cannot review yourself."; `submit_report`: "You can send up to 10 reports a day. Please try again later.", "Unknown report type.", "Choose a reason from the list.", "Describe the problem in a few words.", "Details must be 1000 characters or fewer.", "This listing is no longer available.", "You cannot report your own listing.", "You cannot report yourself.", "You can report only people in your own chats.", "You already reported this."
- **Nothing else changes:** no Phase 3 or Phase 4 RPC, trigger or policy, and no RLS is widened.

Afterwards run, in the same way as the earlier checks:
- `supabase/tests/phase5_reviews_reports_check.sql` (read-only, 18 checks, expect all PASS);
- `supabase/tests/phase5_live_safety_check.sql` (always ends with "SAFETY CHECK FINISHED", which rolls everything back; expect every check PASS or SKIP; it needs at least one claimed reservation, and a chat between that reservation's recipient and donor for check 16).

### 14.3 Local results (PostgreSQL 16 and kotlinc, not the live project or a phone)

- **Migrations:** all eleven in order on a local PostgreSQL 16 with stand-ins for Supabase's roles, `auth.jwt()`, Storage and the Realtime publication, then the new file twice more: no errors.
- **The helper matches RLS.** Callers: the donor A, a recipient R who reserved, an outsider O. Listings of A: available; expired; fully reserved by R; `REMOVED` after R's reservation was cancelled; available with an open reservation by R; expired with an open reservation by R; `REMOVED` with a claimed reservation by R. For all 21 pairs the helper's answer equalled whether that caller could SELECT the row under RLS, and also what `get_listing_donor_names` and `get_listing_donor_profiles` returned:

  | Listing | A (donor) | R (reserved) | O (outsider) |
  |---|---|---|---|
  | available | yes | yes | yes |
  | expired | yes | no | no |
  | fully reserved by R | yes | yes | no |
  | `REMOVED` (R's reservation cancelled) | yes | yes | no |
  | available, reserved by R | yes | yes | yes |
  | expired, reserved by R | yes | yes | no |
  | `REMOVED` (R claimed one) | yes | yes | no |

- **Reviews:** the recipient of a claimed reservation rated it ("created" true; the comment was trimmed); a second send changed the same row ("created" false; a blank comment became NULL) and one row remained; a claimed reservation of a `REMOVED` listing could be rated. Refused: the outsider, the donor and another recipient ("You can rate only your own pickups."), a RESERVED and a CANCELLED reservation ("…marked as claimed."), a missing reservation, ratings 0, 6 and null, a 501-character comment (500 accepted, also with surrounding spaces), a token from another project and no token ("Not authenticated"), a valid token without a profile ("Profile not found"), `anon` (permission denied on every new function), and direct SELECT, INSERT, UPDATE and DELETE on `reviews`. Averages: 5, 4, 4, 4 → 4.3 (4); after one change to 5 → 4.5; after another → 4.8. The outsider got summaries only for visible listings, a donor with no reviews got null/0, `p_limit` was kept between 1 and 50, and the recipient still got the donor's reviews after the listing was removed.
- **Reports:** a listing report, a chat report by the recipient and a chat report by the donor were saved with the right `target_type`, `source`, reported person and `listing_name`; a claimed `REMOVED` listing could be reported by its recipient. Refused: a second report of the same listing, and a Donor-page report of a person already reported from a chat ("You already reported this."); your own listing; yourself (DONOR on your own listing); a chat that isn't yours and a missing chat; a listing you can't see (fully reserved, expired, missing); an unknown or null reason, `NO_SHOW` for a listing and `UNSAFE_FOOD` for a person ("Choose a reason from the list."); an unknown target; `OTHER` without details; 1001 characters of details (1000 accepted); the 11th report within 24 hours (accepted again once the earlier ones were more than 24 hours old); direct SELECT, INSERT and DELETE on `reports`, also by `anon`. **Race:** two sessions sent the same person report at the same moment: one "OK", one "You already reported this.", one row. With the pre-check removed (in a rolled-back copy of the function), a duplicate still returned "You already reported this." through the unique index and the `unique_violation` handler. Deleting a never-reserved listing that had a listing report and a chat report still worked; both reports stayed, with `food_post_id` and `conversation_id` NULL and `listing_name` kept.
- **Email verified:** a verified token whose email differed only in case and surrounding spaces set true, and no other column changed; a second sync with the same value left `updated_at` unchanged; a false claim, a missing claim, the string `"true"`, another email and a missing email claim all stored false; the outsider's sync with a token carrying the recipient's email stored false for the outsider and left the recipient's row alone; a token from another project and a token without a profile were refused; a direct UPDATE of `users` was refused; a new profile inserted with `email_verified = true` was stored false.
- **Checks on the local copy:** `phase5_reviews_reports_check.sql` 18 of 18 PASS; `phase5_live_safety_check.sql` 27 passed, 0 failed, 0 skipped, with the data unchanged afterwards; `phase4_live_safety_check.sql` 21/21; `phase3_live_safety_check.sql` 14/14; `phase3_demo_fixes_check.sql` 13/13; `phase4_demo_fixes_check.sql` 9/9. **Negative runs** (rolled back): with SELECT granted on `reviews`, a policy on `reports`, the helper executable by `authenticated`, `sync_my_email_verified` as SECURITY INVOKER, `submit_review` executable by `anon`, another `search_path`, the insert trigger dropped, the reason-per-target constraint and a unique index dropped, and UPDATE granted on `users`, checks 2, 3, 5, 6, 7, 8, 9, 15 and 18 of the read-only check reported FAIL. With the recipient check removed from `submit_review`, the email comparison removed from `sync_my_email_verified`, the daily limit raised, SELECT granted on `reports` and the trigger dropped, checks 5, 6, 12, 19, 23 and 25 of the live safety check reported FAIL.
- **Kotlin:** a kotlinc 2.2.10 type check (with the Compose and serialization plugins) of every source after each Kotlin commit: 0 errors, and only the three old deprecation warnings (`FormatListBulleted`, `ArrowBack`, `Logout`). It used the supabase-kt 3.5.0 JVM jars and **Compose Multiplatform 1.7.3** desktop jars (1.9 needs `androidx` artifacts that are only on Google's Maven repository, which is blocked here), plus hand-written stubs of the Android, AndroidX Activity/Lifecycle, Credential Manager and Firebase APIs. A small JVM check: a `users` row with and without `email_verified` decodes (also into a model without the field, as older builds have), `get_listing_donor_profiles`, `get_my_rating_summary`, `submit_review` and `get_listing_donor_reviews` rows decode, the scalar `true` of `sync_my_email_verified` decodes, `ratingLabel` gives "★ 4.0 (1)", "★ 4.8 (3)" and nothing without reviews, stars round to whole stars (4.3 → ★★★★☆, 4.5 → ★★★★★), and a blank comment is sent as JSON null. **It is not an AGP build**, and nothing ran on a phone. (The AGP build and the phone test came afterwards; see §14.6.)

### 14.4 Manual test (A → B → A; phone 1 = A, phone 2 = B)

**Preconditions:**
- The migration has been run, then `phase5_reviews_reports_check.sql` (18 of 18 PASS) and `phase5_live_safety_check.sql` (every check PASS or SKIP).
- This branch is built with AGP and installed on both phones **after** the migration.
- A (phone 1) is an **email/password** account whose inbox the tester can open, with its email not confirmed yet and no reviews yet. A newly created email/password account needs the role-claim script (outside this repo) before it can sign in, like any sign-up.
- B (phone 2) is a **Google** account that already has a Salu-Salo profile and the role claim (for example G from §10.4). Do not sign in with Google using A's email address (§10.5).
- A and B have not reported each other before (reports can't be withdrawn until Phase 6).

Each step is tagged [New] or [Regression]. Where Home must show new data, the step names the action that reloads it.

**A (phone 1)**

1. [New] Profile (bottom bar):
   - under the name, a grey chip "Email not verified"; no "Identity Verified", no "Demo verification & rating", and no "Verified badge and rating are demo values" under the stats;
   - Overall Rating "—"; Ratings & Reviews shows "—", "Overall Rating", "No reviews yet", and under "Recent reviews" "Donors and sellers are rated after each pickup; people who only receive food have no rating.";
   - Verification Status: "Confirm your email address to show the Email verified badge.", and the row "Email not verified" / "Confirms the email address, not your identity" / "Not verified";
   - the footer: "Rate the people you get food from, and report anything that doesn't feel right."
2. [New] Tap the chip: "Email Verification" shows "Email not verified", A's email and "The badge only means that this email address was confirmed. It is not an identity check."
   - "I've confirmed my email" → "Your email is not confirmed yet. Open the link in the email we sent, then try again."
   - "Send verification email" → "Verification email sent to <A's email>. If you don't see it, check your spam folder." Don't open the email yet.
   - Back, then the gear (Settings): "Verification / Confirm your email address". Trust & safety: "Coordinate clearly", "What the Email verified badge means", "Ratings and reviews", "Email verification / Confirm your email address" and "Report a concern / Where to find the Report buttons"; no "How verification works". Report a concern: the three "Report …" lines and "Reports go to the Salu-Salo team. The person you report is not told. You can send up to 10 reports a day.", with no text box and no "Preview report".
3. [Regression] Post `P5 FREE`: "I want to donate", 1 photo, quantity 3, unit pcs, expiring 2 days later → "Listing published". Post's Back button (this reloads Home): its card shows A's name with no "★".

**B (phone 2)**

4. [New] Profile: a green chip "Email verified". Tap it: "Email verified", B's Gmail address and the sentence, with no "Send verification email" and no "I've confirmed my email".
5. [New, Regression] Open Post and tap its Back button (this reloads Home), then open `P5 FREE`:
   - the donor chip and the "Donor" row show A's name with no "★"; at the bottom, "Report this listing";
   - the Donor row opens "Donor": A's name, "Food donor", no "Email verified", "No reviews yet", "Message donor", and at the bottom "Report <A>". Back.
   - [Regression] Reserve 1 → "Review reservation" → message `P5 test` → "Confirm reservation" → the "Reserved" receipt with "Your message was sent to <A>." → Done. Open `P5 FREE` again → Reserve 1 → Confirm with no message → Done.
6. [New] Activity → My Reservations → one of the two `P5 FREE` reservations: "Status: Reserved", with Cancel and "Message donor" and **no** "Rate" button. Close.
   - Home → `P5 FREE` → "Report this listing": "Report listing", "What's wrong with P5 FREE?" and the four listing reasons. "Send report" with no reason → "Choose a reason."; choose "Other" and send → "Describe the problem in a few words."; choose "Wrong or misleading details", details `Test report` → "Send report" → "Report sent. The Salu-Salo team will review it." → Done.
   - "Report this listing" again → "Spam or fake listing" → "Send report" → "You already reported this." Back.

**A (phone 1)**

7. [Regression] Alerts: two "New reservation" ("<B> reserved 1 pcs of P5 FREE.") and "New message from <B>" ("P5 FREE: P5 test"). Activity → My Donations → one of the `P5 FREE` reservations → "Mark claimed" → "Reservation marked as claimed." Leave the other one Reserved.
8. [New] In A's inbox, open the Firebase verification email and tap its link (the browser page says the email was verified). Back in Salu-Salo: Profile → the chip → "I've confirmed my email" → "Your email is confirmed. Your profile now shows the Email verified badge.", and the page shows "Email verified" with no buttons. Back: the chip is green "Email verified"; Verification Status reads "Your email address is confirmed." with "Email verified" and "Verified".
9. [New] Home → chat icon → the `P5 FREE` chat with B → the flag at the top right → "Report <B>", "Why are you reporting <B>?" with "Rude or abusive", "Didn't show up", "Scam or fraud", "Spam" and "Other" → "Didn't show up" → "Send report" → "Report sent. The Salu-Salo team will review it." → Done.

**B (phone 2)**

10. [New, Regression] Alerts → "Reservation claimed" → Activity opens on Claims with the `P5 FREE` record: "Status: Claimed" and a "Rate <A>" button.
    - Tap it: "Rate <A>", "P5 FREE", "1 pcs • Free", "Tap a star to rate.", "Comment (optional)", and "Your name, stars and comment are shown to other users."
    - "Send review" with no stars → "Choose from 1 to 5 stars."
    - Tap the 4th star ("4 of 5 stars"), comment `Friendly and on time` → "Send review" → "Thanks! Your review was sent." → Done.
    - Open the record again: the button reads "Change your review".
11. [New] Home (bottom bar; the feed was reloaded when the review was sent): the `P5 FREE` card shows "<A>  ★ 4.0 (1)". Open it: the donor chip shows "★ 4.0 (1)" after A's name, and the Donor row reads "<A> • ★ 4.0 (1)". The Donor row opens "Donor": "Email verified", "★ 4.0 • 1 review", and under "Reviews": <B>, "★★★★☆", "Friendly and on time" and today's date.
12. [New] Activity → Claims → `P5 FREE` → "Change your review": the page is titled "Change your review", with 4 stars and the comment filled in. Tap the 5th star, comment `Great, thank you!` → "Save review" → "Your review was updated." → Done. Home (bottom bar; the feed was reloaded again): the card shows "★ 5.0 (1)", still one review.
13. [New] Chat icon → the `P5 FREE` chat with A → flag → "Report <A>" → "Scam or fraud" → "Send report" → "Report sent. The Salu-Salo team will review it." → Done. Then Home → `P5 FREE` → Donor row → "Report <A>" → "Rude or abusive" → "Send report" → "You already reported this."

**A (phone 1)**

14. [New] Profile (bottom bar; the rating loads each time Profile opens): Overall Rating "5.0"; Ratings & Reviews "5.0", "★★★★★", "(1 review)", and under "Recent reviews" <B>, "★★★★★", "Great, thank you!" and today's date. "View all" → "Ratings & Reviews": "★ 5.0", "1 review", "Reviews from people who picked up your food.", and B's review.
    - Profile → Log out, then sign in as A again (Home loads fresh): A's own `P5 FREE` card shows "★ 5.0 (1)". Open it: no "Report this listing"; its Donor row opens "Donor" with "Email verified" and no Report button.

**Developer (Supabase SQL Editor)**

15. [New] Run `supabase/tests/phase5_reports_query.sql`. The three newest rows, all `OPEN`, newest first:
    - <B> → <A>, `USER`, `CHAT`, `SCAM`, listing `P5 FREE`;
    - <A> → <B>, `USER`, `CHAT`, `NO_SHOW`, listing `P5 FREE`;
    - <B> → <A>, `LISTING`, `LISTING`, `MISLEADING`, details `Test report`, listing `P5 FREE`.

    There is no row for B's refused second listing report or the refused Donor-page report.

**Left out of the short test (not tested unless run separately):**
- Reviews: a priced record in Purchases ("Rate the seller" when the name is unknown); rating a claimed reservation of a `REMOVED` listing; the 500-character limit; more than 3 reviews on Profile or 20 on the Donor page; the review error texts (offline).
- Reports: the daily limit, reporting a listing you can't see, and a chat that isn't yours (local and live SQL checks only); the 1000-character limit; the report error text (offline).
- Email verified: a second phone signed in to A with an older token; "Too many attempts. …"; an unverified Google account; Google sign-in with an unverified email account's address (§10.5); the badge on other screens (it shows only on the Donor/Seller page).
- The regressions not in the steps: Cancel, removing a listing, editing a listing, Saved Listings (which now also load donor ratings), notification settings and sound, the response rate, the profile photo and name, the password change, email sign-up and the password-reset email.

### 14.5 Known limits

- No alert for a new review; the donor sees it the next time Profile opens.
- Nothing updates live: other phones see a new rating after their feed reloads (sign-in, Post → Back, a reservation, or a review sent from that phone).
- One review per pickup: someone who picked up several times from the same donor can rate each pickup, so frequent recipients weigh more in the average.
- No deleting a review, no reply from the donor and no moderation of review text; donors don't review recipients.
- Reviews show the reviewer's current name; a rename changes old reviews.
- Reports: only `OPEN`; reporters can't see or withdraw them; nobody is told; one open report per listing and per person per reporter until Phase 6 can close them; at most 10 per 24 hours. The developer reads them only in the SQL Editor.
- The badge means only that the email was confirmed. For other people it shows only on the Donor/Seller page. It is refreshed when the profile loads and after "I've confirmed my email"; another phone with an older token picks up the change at its next start (the app refreshes the token when Firebase already says the email is confirmed).
- Signing in with Google using the email of an unverified email/password account makes that account Google-only and verified (§10.5).
- The verification email is Firebase's default template and may land in spam.
- Older builds still show the demo ratings and the demo "Identity Verified" badge; they are otherwise unaffected.
- `caller_can_view_food_post` and `get_listing_donor_names` both repeat the `food_posts` SELECT rule; if that policy changes, change both.
- Deleting an account in the Firebase console leaves its Supabase profile, whose email is unique. Re-creating the account with the same email then cannot sign in ("We could not load your profile…") until the profile is re-linked to the new Firebase UID in the SQL Editor; this happened during the §14.4 test (§14.6). There is no in-app account deletion.

### 14.6 Results

- **Build:** `gradlew.bat :app:assembleDebug --offline` at `3bd038c`: BUILD SUCCESSFUL in 57s on 2026-09-27, with only the three old deprecation warnings. The developer installed it from Android Studio on both phones.
- **Migration:** `20261002_phase5_reviews_reports.sql` applied to the live project on 2026-09-27 in the SQL Editor, with `supabase/tests/phase5_reviews_reports_check.sql` in the same run: 18 of 18 PASS ([results log](#results-log)).
- **Live safety check:** `supabase/tests/phase5_live_safety_check.sql` on 2026-09-27: 27 passed, 0 failed, 0 skipped, rolled back by its closing error. Reservation checked: `P3 FREE` (claimed), with the two test accounts as recipient and donor and the newest other account as the outsider.
- **Two-phone test:** §14.4 steps 1–15 passed, sent in two parts. Steps 1–9 were reported at 00:10 (+08:00) on 2026-09-28, after being run late on 2026-09-27 and around midnight; steps 10–15 were reported shortly after, on 2026-09-28. A was an email/password account and B a Google account.
- **During the test:** after step 3 the developer deleted A's account in the Firebase console and re-created it with the same email. Sign-in then failed with "We could not load your profile…", because the Supabase profile (whose email is unique) was still linked to the deleted account's UID. After a read-only check of the profile that owned `P5 FREE`, the developer re-linked it to the new UID in the SQL Editor (one row changed) and ran the role script. A then signed in with `P5 FREE` unchanged (quantity 3), sent the verification email again, and the test went on (§14.5).
- **Not tested:** the list under "Left out of the short test" in §14.4.
- **Merged** into `master` through PR #9 on 2026-09-28 (merge commit `711cc5e`).

---

## 15. Phase 5 part 2: Maps

**Status (2026-09-28): built with AGP at `47ed894`, migration applied to the live project (read-only check 11 of 11, live safety check 22 of 22), and the 12-step two-phone test (§15.4) passed on 2026-09-28; merged into `master` through PR #11 on 2026-09-28 (merge commit `255b0f8`).** Written on branch `claude/phase5-maps` (from `master` at `711cc5e`, the PR #9 merge). Before that, the SQL was tested on a local PostgreSQL 16 copy, and the Kotlin passed a kotlinc type check and a JVM unit test (§15.3). Results: §15.6.

### 15.1 What changed

- **Pickup pin on Post and Edit** (new `PickupMap.kt`; `PostScreen.kt`, `model/FoodPost.kt`, `data/FoodRepository.kt`).
  - Under "Pickup location / exact meeting point" (and, on Post, "Enter an address or landmark recipients can recognize.") there is "Pickup pin", the note "Everyone who can see this listing can see this pin. Put it where you'll hand over the food. A public spot like a gate, store or plaza is fine; it doesn't have to be your home.", a 220 dp map, a status line and "Use my location". The address text stays as it was.
  - **Tapping the map** places or moves the pin; a tap while zoomed out (below zoom 15) also zooms in to 16. Pinch zooms, and a drag on the map pans it instead of scrolling the page.
  - **"Use my location"** ("Finding your location…" while it runs) asks for location access if needed and places the pin at the phone's position, from a fresh fix (or one at most 2 minutes old if none comes within 20 seconds). If the phone shares only its approximate location, or the fix is less precise than 100 m, the map centres there at zoom 14 and no pin is placed.
  - **Where the map starts:** the listing's pin at zoom 16 (Edit); else the phone's last known position (at most a day old) at zoom 16, only if location access is already allowed (no prompt); else Tagbilaran City (9.6500 N, 123.8540 E) at zoom 12.
  - **Status line:** Post: "Tap the map to place the pin, or use your location."; Edit of an older listing: "This listing has no pickup pin yet. Tap the map to add one. Without a pin, others see no map or distance."; Edit with a pin: "Tap the map to move the pin."; after placing: "Pin placed. Tap the map again to move it."
  - **"Use my location" messages:** "Location access is off for Salu-Salo. Tap the map to place the pin, or allow location in Settings → Apps → Salu-Salo → Permissions → Location."; "Your phone's location is turned off. Turn it on, or tap the map to place the pin."; "Your location could not be found. Try again, or tap the map to place the pin."; "Your phone shared only its approximate location, so the pin wasn't placed. Zoom in and tap the exact spot."
  - **Rules:** a new listing needs a pin ("Place the pickup pin on the map.", checked after the pickup location). On Edit an older listing can get a pin, and a pin can be moved but not removed; an older listing can still be saved without one. The pin is stored with 6 decimals.
- **Pickup location page** ("View on map" or the Pickup Address row; `FoodActions.kt`).
  - With a pin: under the address, "About 1.2 km from you in a straight line; the route may be longer." (when the phone's position is known); a 260 dp map centred on the pin with a marker (zoom and pan allowed); "Directions" instead of "Search area in Maps"; and under "Pickup instructions": "The pin shows where the provider hands over the food. If you can't find it, ask them in the chat." "Ask for exact location" stays.
  - **Directions** opens `https://www.google.com/maps/dir/?api=1&destination=<lat>,<lng>` (6 decimals, always with a dot), which the Google Maps app handles; if nothing opens it, a `geo:<lat>,<lng>?q=<lat>,<lng>` link; else "No maps app is available on this device."
  - Without a pin the page is unchanged: "Search area in Maps" and "Ask the provider for the exact meeting point and collection instructions."
- **Distance, Nearby, Nearest** (`MainActivity.kt`, `model/Geo.kt`).
  - Straight-line (haversine) distance from the phone to the pin: "350 m" under 1 km (steps of 10 m, at least 10 m), "1.2 km" under 10 km, "18 km" above.
  - Home cards: "Available  •  1.2 km away  •  <address>". The details' Pickup Address row: the address, then "About 1.2 km away (straight line)". Nothing without a pin or a phone position. The "Newest listings" cards and Saved Listings show no distance.
  - **Nearby** chip (after All): listings with a pin within 5 km; title "Food within 5 km"; empty: "No listings with a map pin within 5 km.". The filter help starts with "Nearby: listings with a map pin within 5 km of you".
  - **Nearest** in the sort menu (Newest, Nearest, Name): nearest first, listings without a pin last (newest first); until the phone's position is known the order stays Newest.
  - **One line under the chips** while Nearby or Nearest is chosen: "Finding your location…"; "Allow location to use Nearby and Nearest: Settings → Apps → Salu-Salo → Permissions → Location."; "Your phone's location is turned off. Turn it on to use Nearby and Nearest."; "Your location could not be found. Tap Nearby or Nearest again to try again."; "Distances use your phone's approximate location."
  - **The phone's position** is kept only in memory on Home. It is refreshed when Home opens and when the app returns to the foreground (only if access is already allowed; a position at most 10 minutes old is reused, else a fresh fix within 20 seconds), and each time Nearby or Nearest is chosen (asking for access if needed). It is gone after sign-out or when the app closes.
- **Directions for a reservation** (`ActivityScreen.kt`): Activity → My Reservations → a reservation of yours with status Reserved → "Directions" (same link and fallbacks) when its listing has a pin. "Message donor" and Cancel stay.
- **Location permission** (`AndroidManifest.xml`, `data/PhoneLocation.kt`): `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`, foreground only, asked for together (Android 12+ then offers Precise or Approximate) and only when "Use my location" is tapped or Nearby or Nearest is chosen. Precise makes the pin and short distances meaningful; Approximate still gives distances, with the note. Refused: everything else works, tapping the map still places the pin, no distances are shown, and Nearby and Nearest show the one-line message. No location library: the platform LocationManager through `androidx.core`'s `LocationManagerCompat` (fused provider on Android 12+, then network, then GPS).
- **The phone's location** is used on the phone for distances, and is never sent to Supabase or stored. The one exception is "Use my location" on Post and Edit, which puts the pin at the phone's position; the donor then saves that pin with the listing, and the note says who sees it. (Since §33 "Use my location" also sends the pin to Android's address lookup, Google on most phones, to fill in an empty pickup location.)
- **Map library and tiles:** osmdroid 6.1.20 (`org.osmdroid:osmdroid-android`, Maven Central; no API key) with OpenStreetMap's tiles from `https://tile.openstreetmap.org/`. The tile source is one constant, `MAP_TILES` in `PickupMap.kt` (URL, name, attribution), so another provider can replace it later without touching the screens. Following the tile usage policy: the User-Agent `SaluSalo/<versionName> (Android; com.example.salusalo)`; "© OpenStreetMap contributors" on every map (tapping it opens openstreetmap.org/copyright); tiles cached in the app's own cache folder (at most 50 MB, trimmed to 40 MB), following the servers' cache headers; at most 2 downloads at a time; no bulk, preventive or offline downloading; no storage permission. Without a connection the map is blank and the rest of the page works.
- **Docs:** the Phase 5 part 1 merge through PR #9 (`711cc5e`) is recorded here, in `CLAUDE.md` and in `README.md`.

**Left as they are:** a map of all listings; a Home location, a Profile location and the Activity location picker; phone notifications; Phase 6; the pickup "now" option and the "(optional)" labels on Post; "View insights"; the four summary boxes on the details screen; the Home leaf icon; a full-screen photo view; the chat box and the keyboard.

### 15.2 Migration `20261003_phase5_maps.sql`

Run it by hand in the SQL Editor after `20261002_phase5_reviews_reports.sql`, **before** installing this build: the new build sends the pin with every new listing and saves edits through `update_my_food_post_with_pin`, so without the migration its Post Listing and Save changes fail. It is safe to run again. **Older builds are unaffected:** supabase-kt ignores the two new columns, their new listings get no pin, and they still edit through `update_my_food_post`, which is unchanged and never touches the pin.

- **`food_posts.pickup_latitude numeric(8,6)` and `pickup_longitude numeric(9,6)`**, nullable; existing listings get NULL. The check `food_posts_pickup_pin_check`: both set or both NULL, latitude −90 to 90, longitude −180 to 180 (NaN fails too).
- **Who sees the pin:** exactly who may see the listing under the existing `food_posts` SELECT policy. No policy or grant changes; clients still have SELECT and INSERT only on `food_posts`, and the insert policy is unchanged.
- **`update_my_food_post_with_pin(...)`**: the 12 arguments of `update_my_food_post` plus `p_pickup_latitude` and `p_pickup_longitude`; SECURITY DEFINER, `search_path = public`, executable by `authenticated` only. It takes the caller from the token, locks the listing, and refuses someone else's, a `REMOVED` and an expired listing with the same texts as `update_my_food_post`; then "Send both pickup pin coordinates, or neither." and "The pickup pin is not a valid map position."; then it calls `update_my_food_post` for every other field and its checks, and stores the pin rounded to 6 decimals. One transaction: when anything is refused, nothing changes. Both pin arguments NULL keep the stored pin (or its absence).
- **Nothing else changes:** no Phase 3, 4 or 5 function, trigger or policy, and no RLS is widened. The server does not require a pin, so older builds can still post.

Afterwards run, in the same way as the earlier checks:
- `supabase/tests/phase5_maps_check.sql` (read-only, 11 checks, expect all PASS);
- `supabase/tests/phase5_maps_live_safety_check.sql` (always ends with "SAFETY CHECK FINISHED", which rolls everything back; 22 checks, expect all PASS; with a single account the outsider checks report SKIP).

### 15.3 Local results (PostgreSQL 16, kotlinc and the JVM; not the live project or a phone)

- **Migrations:** all eleven earlier files in order on a local PostgreSQL 16 with stand-ins for Supabase's roles, `auth.jwt()`, Storage and the Realtime publication, a local seed (accounts, listings, reservations, reviews and chats made through the real functions), then the new file twice: no errors.
- **`phase5_maps_check.sql`:** 11 of 11 PASS. **`phase5_maps_live_safety_check.sql`:** 22 passed, 0 failed, 0 skipped, and afterwards the listings were unchanged (no test listing, no pin). Its checks: the donor's and the outsider's sign-ins; a listing posted with a pin through RLS stored 9.647812, 123.854013 (sent with 7 decimals); a listing without a pin; moving a pin and changing the name in one call; adding a pin to a listing without one; refused on insert: latitude only, longitude only, latitude 91, longitude 181; refused on edit with nothing changed: one coordinate, 90.5, −180.5, NaN, and a blank name sent with a new pin ("Enter the food name."); both pin arguments NULL and an edit through the old `update_my_food_post` both keep the pin; refused: the outsider ("You can edit only your own listing."), an expired listing, a `REMOVED` listing, a listing that doesn't exist, no login (`anon`: permission denied) and another Firebase project's token ("Not authenticated"); a direct UPDATE by the donor ("permission denied for table food_posts"); the outsider sees the pin while the listing is available and nothing once it has expired, while the donor still does.
- **Earlier checks on the same copy after the migration:** `phase3_demo_fixes_check.sql` 13/13, `phase3_live_safety_check.sql` 14/14, `phase4_demo_fixes_check.sql` 9/9, `phase4_live_safety_check.sql` 21/21, `phase5_reviews_reports_check.sql` 18/18, `phase5_live_safety_check.sql` 27/27 (its check 16 needs a chat between the claimed reservation's recipient and donor, which the local seed first lacked: 26 passed and 1 skipped, the same before and after this migration).
- **Negative runs** (each rolled back): dropping the pin constraint and storing a half pin made read-only checks 3 and 11 FAIL and live checks 7 and 8 FAIL; EXECUTE for `anon` → check 4 and live check 18 FAIL; a column UPDATE grant on the pin → check 7 and live check 20 FAIL; an UPDATE policy with the grant → checks 7 and 8 and live checks 20 and 21 FAIL; a SELECT policy of `true` → check 10 and live check 22 FAIL; `update_my_food_post` clearing the pin → check 6 and live checks 12, 13, 21 and 22 FAIL; the new function clearing the pin on NULL → live checks 12, 13, 21 and 22 FAIL; both functions without the owner check → live checks 14 and 21 FAIL; the new function without its pin checks → live checks 9 and 10 FAIL (the constraint still refused the values); both functions without the expiry and `REMOVED` checks → live checks 15 and 16 FAIL; the new function as SECURITY INVOKER → check 4 and 10 live checks FAIL. A first version of the live check reported SKIP instead of FAIL when a pin had been wrongly cleared; it was fixed before the commit.
- **Kotlin:** a kotlinc 2.2.10 type check (with the Compose and serialization plugins) of every app source: 0 errors, and only the three old deprecation warnings. This time it used Robolectric's `android-all` 16 jar (`16-robolectric-13921718`, from Maven Central) for the Android classes instead of hand-written stand-ins, the real osmdroid 6.1.20 classes, the supabase-kt 3.5.0 JVM jars and Compose Multiplatform 1.7.3 desktop jars. AndroidX and Firebase still had stand-ins (Google's Maven repository is not reachable from the session): `LocationManagerCompat` (both `getCurrentLocation` overloads, copied from the androidx source), `ContextCompat`, `Consumer`, `AndroidView`, the activity and lifecycle Compose functions, `BuildConfig`.
- **`GeoTest`** (`app/src/test/.../GeoTest.kt`, JUnit 4.13.2, run directly on the JVM, not through Gradle): 11 of 11 passed: distances (1° of latitude ≈ 111.195 km), the Nearby boundary, the labels ("990 m", "1.0 km", "9.9 km", "10 km"), a dot with a German default locale, rounding to 6 decimals, valid positions, and the pin fields' JSON with and without a pin (a row without the new columns decodes too).
- **Not done in that session:** the AGP build, lint, `testDebugUnitTest` through Gradle, the migration on the live project, and anything on a phone. All but lint were done afterwards (§15.6).

### 15.4 Manual test (A → B → A; phone 1 = A, phone 2 = B)

**Preconditions:**
- The migration has been run, then `phase5_maps_check.sql` (11 of 11 PASS) and `phase5_maps_live_safety_check.sql` (22 passed, or SKIPs only for the outsider checks).
- This branch is built with AGP and installed on both phones **after** the migration.
- Both phones have location turned on, the Google Maps app installed, and Salu-Salo's location access **not allowed yet** (a fresh install, or Settings → Apps → Salu-Salo → Permissions → Location → "Don't allow").
- A and B are together (within a few hundred metres of each other).
- A already has an older listing without a pin, available and unexpired (for example `P5 FREE` from §14.4, or post one with the old build before installing this one); below it is called `OLD`.

Each check is tagged [New] or [Regression]. Where a phone must show new data, the step names the action that reloads it.

**A (phone 1)**

1. [New, Regression] Home → Post. Fill in `MAP TAP`: "I want to donate", 1 photo, quantity 2, unit pcs, a pickup location text, expiring tomorrow, pickup window from later today.
   - [New] Under "Enter an address or landmark recipients can recognize.": "Pickup pin", the note "Everyone who can see this listing can see this pin. Put it where you'll hand over the food. A public spot like a gate, store or plaza is fine; it doesn't have to be your home.", a map of Tagbilaran with "© OpenStreetMap contributors" in its bottom-right corner, "Tap the map to place the pin, or use your location." and "Use my location". No location prompt appears.
   - [New] "Post Listing" → "Place the pickup pin on the map."
   - [New] Pan and zoom the map to a spot **more than 5 km** from both phones (another town) and tap it: the marker appears there, the map zooms in if it was zoomed out, and the line reads "Pin placed. Tap the map again to move it." Dragging on the map moves the map, not the page.
   - [Regression] "Post Listing" → "Listing published".
2. [New, Regression] Post's Back button, then Post again. Fill in `MAP HERE` the same way (quantity 2).
   - [New] "Use my location" → Android asks for location access: choose **"Precise"** and "While using the app". The button reads "Finding your location…", then the marker appears where the phone is, the map centres there, and the line reads "Pin placed. Tap the map again to move it."
   - [New] If "Approximate" was chosen by mistake: no pin, the map centres near the phone and "Your phone shared only its approximate location, so the pin wasn't placed. Zoom in and tap the exact spot." Then turn on Settings → Apps → Salu-Salo → Permissions → Location → "Use precise location", go back, and tap "Use my location" again.
   - [Regression] "Post Listing" → "Listing published". Post's Back button.

**B (phone 2)**

3. [New] Home, signed in as B. Tap Post, then its Back button (this reloads Home).
   - No location prompt on Home, and no card shows a distance yet.
   - Tap "Nearby" → Android asks for location access → "Precise" and "While using the app" ("Approximate" also works for B; the line under the chips then reads "Distances use your phone's approximate location."). While it looks: "Finding your location…".
   - The title reads "Food within 5 km"; `MAP HERE` is listed with "Available  •  <distance> away  •  <address>" (for example "10 m away"); `MAP TAP` and `OLD` are not listed.
   - The filter button next to the search box → the first line is "Nearby: listings with a map pin within 5 km of you". OK.
4. [New, Regression] Tap "All".
   - `MAP TAP`'s card shows a distance of more than 5 km (for example "7.4 km away"), `MAP HERE` shows its short distance, and `OLD`'s card shows no distance ("Available  •  <address>").
   - "Sort: Newest" → the menu lists "Newest", "Nearest", "Name" → "Nearest": `MAP HERE` first, then `MAP TAP`, then the other pinned listings by distance, and `OLD` after every pinned listing.
   - [Regression] "Name" sorts by name. Set it back to "Newest".
5. [New, Regression] Open `MAP TAP`.
   - The Pickup Address row: the address, then "About 7.4 km away (straight line)" (the same distance as the card).
   - "View on map" → "Pickup location": under the address "About 7.4 km from you in a straight line; the route may be longer."; a map with the marker at A's pin and "© OpenStreetMap contributors" visible; pinch zooms, and a drag pans the map without scrolling the page. Tapping "© OpenStreetMap contributors" opens openstreetmap.org/copyright; come back.
   - "Directions" opens Google Maps with a route to the pin. Come back to Salu-Salo.
   - Under "Pickup instructions": "The pin shows where the provider hands over the food. If you can't find it, ask them in the chat." [Regression] "Ask for exact location" opens the chat with A with "Hi! Where exactly can I pick up MAP TAP?" filled in; close it without sending.
6. [Regression] Back, open `OLD`: the Pickup Address row has no distance line. "View on map" → no map, "Search area in Maps" (opens Google Maps with a search for the address) and "Ask the provider for the exact meeting point and collection instructions." Come back.
7. [Regression, New] Open `MAP HERE` → Reserve 1 → "Review reservation" → message `Map test` → "Confirm reservation" → the "Reserved" receipt with "Your message was sent to <A>." → Done.
   - [New] Activity → My Reservations → `MAP HERE`: "Status: Reserved", "Message donor", "Directions" and Cancel. "Directions" opens Google Maps with a route to `MAP HERE`'s pin. Come back and Close.

**A (phone 1)**

8. [Regression, New] Alerts: "New reservation" ("<B> reserved 1 pcs of MAP HERE.") and "New message from <B>" ("MAP HERE: Map test"). Open the chat and reply `OK`.
   - [New] Activity → My Listings → `MAP TAP` → "Edit listing": the map starts at the pin, zoomed in, with "Tap the map to move the pin." Tap a spot **1–3 km** from the phones → "Pin placed. Tap the map again to move it." → "Save changes" → "Listing updated." [Regression]
   - [New] Activity → My Listings → `OLD` → "Edit listing": "This listing has no pickup pin yet. Tap the map to add one. Without a pin, others see no map or distance." Cancel without tapping the map (`OLD` keeps no pin).

**B (phone 2)**

9. [New, Regression] Tap Post, then its Back button (this reloads Home), then "Nearby": `MAP TAP` is now listed with its new distance (1–3 km), together with `MAP HERE`. Open `MAP TAP` → "View on map": the marker is at the new spot. [Regression] The chat icon → the `MAP HERE` chat shows A's `OK`.
10. [New] Android Settings → Apps → Salu-Salo → Permissions → Location → "Don't allow". Android closes Salu-Salo when a permission is removed: reopen it, and sign in again if asked.
    - Home: no distances on any card, and no prompt.
    - Tap "Nearby" (if Android asks again, choose "Don't allow"): "Allow location to use Nearby and Nearest: Settings → Apps → Salu-Salo → Permissions → Location." and no cards.
    - "All", then "Sort: Newest" → "Nearest" ("Don't allow" again if asked): the same line, and the order stays newest first.
    - Open `MAP TAP` → "View on map": the map, the marker and "Directions" still work; there is no distance line.

**A (phone 1)**

11. [New] Turn off location in the phone's quick settings.
    - Home → Post → "Use my location" → "Your phone's location is turned off. Turn it on, or tap the map to place the pin." Tap the map → "Pin placed. Tap the map again to move it." Post's Back button without posting.
    - Home → "Nearby" → "Your phone's location is turned off. Turn it on to use Nearby and Nearest."
    - Turn location back on and tap "Nearby" again: the line disappears and the listings within 5 km show their distances.
12. [Regression, New] Activity → My Donations → B's `MAP HERE` reservation → "Mark claimed" → "Reservation marked as claimed." On B (phone 2): the "Reservation claimed" alert; Activity → Claims shows `MAP HERE`, and its record has no "Directions" [New]; My Reservations no longer lists it.

**Left out of the short test (not tested unless run separately):**
- Android 11 and older (no Precise/Approximate choice); a phone without Google Maps (the `geo:` fallback) or without any maps app or browser ("No maps app is available on this device.").
- "Your location could not be found. …" (needs a phone that finds no position within 20 seconds); a fix less precise than 100 m with Precise allowed.
- The map offline (blank map, the rest of the page works), the 50 MB tile cache, and Android clearing the cache.
- Rotating the phone while placing a pin; TalkBack ("Map for the pickup pin. Tap to place the pin.", "Map of the pickup pin for <listing>"); dark mode; a small screen.
- An older build after the migration (it posts without a pin, and its edits keep the pin) and the function's refusals on a phone: covered only by the SQL checks.
- Adding a pin to an older listing on Edit (step 8 only opens Edit); moving a pin by "Use my location" on Edit.
- Home returning from the background with a new position; sign-out clearing the position.
- The regressions not in the steps: Cancel, removing a listing, editing other fields, Saved Listings, reviews and reports, notification settings and sound, the response rate, the profile, the password change, email sign-up and the password-reset email.

### 15.5 Known limits

- **Tiles:** the free `tile.openstreetmap.org` servers are fine for testing and a small user base. Before a large public release, consider a tile provider meant for apps; such providers usually have a free tier and a key, which would stay out of the repository like the Supabase keys (change `MAP_TILES` in `PickupMap.kt`). The OpenStreetMap Foundation may block traffic that degrades its service without notice.
- Distances are straight lines; the route may be longer. The cards say "1.2 km away" without "about" for space.
- The phone's position is refreshed only when Home opens, when the app returns, and when Nearby or Nearest is chosen; it doesn't follow the phone while Home stays open.
- Only listings with a pin have a map, a distance or a place in Nearby; older listings get one only when their donor edits them. A pin can be moved but not removed.
- Everyone who can see the listing sees the exact pin, by design; the note on Post and Edit says so.
- "Use my location" places the pin only from a precise fix (100 m or better); otherwise the donor taps the map.
- Nothing is live: others see a new or moved pin only after their Home reloads (sign-in, Post → Back, a reservation).
- Without a connection the map is blank. Tiles are cached in the app's cache folder, which Android may clear.
- Directions need Google Maps, another maps app or a browser. Android stops showing the permission prompt after the user refuses twice; then only Settings can allow it.
- osmdroid 6.1.20 (August 2024) is the latest release; the project is maintained slowly.
- The migration must run before the new build is installed: without it, Post Listing and Save changes fail with the server's short error.

### 15.6 Results

- **Build:** `gradlew.bat :app:assembleDebug :app:testDebugUnitTest` at `47ed894`: BUILD SUCCESSFUL in 3m 43s on 2026-09-28 (online once, to download osmdroid 6.1.20), with only the three old deprecation warnings; `GeoTest` 11 of 11 passed through Gradle. The developer installed it from Android Studio on both phones.
- **osmdroid checks (javap on the 6.1.20 classes):** without `Configuration.load()` osmdroid has no "normalized" User-Agent, so its tile downloader falls back to the app's own value (`SaluSalo/<versionName> (Android; com.example.salusalo)`), which the tile policy accepts. osmdroid adds no permission to the merged manifest, only optional `uses-feature` entries.
- **Migration:** `20261003_phase5_maps.sql` applied to the live project on 2026-09-28 in the SQL Editor, with `supabase/tests/phase5_maps_check.sql` in the same run: 11 of 11 PASS ([results log](#results-log)).
- **Live safety check:** `supabase/tests/phase5_maps_live_safety_check.sql` on 2026-09-28: 22 passed, 0 failed, 0 skipped, rolled back by its closing error. The donor was the donor of the newest listing and the outsider the newest other account (the two test accounts).
- **Two-phone test:** §15.4 steps 1–12 passed on 2026-09-28, sent in two parts (steps 1–7, then 8–12).
- **Not tested:** the list under "Left out of the short test" in §15.4. Lint was not run.
- **Merged** into `master` through PR #11 on 2026-09-28 (merge commit `255b0f8`).

---

## 16. Profile redesign and settings (Mika's PR #10)

**Status (2026-09-28): the 6-step two-phone test passed and the migration is applied (check 6 of 6); merged into `master` through PR #10 on 2026-09-28 (merge commit `49ac319`).** Written by Mika S on branch `mika/navigation-activity-settings` (commit `7888309`, from `master` at `711cc5e`). Before the merge, Claude merged `master` (Maps) into her branch and fixed its one conflict (`308a499`), then changed one sentence and the migration (`b2b04ca`).

### 16.1 What changed

- **Profile** (`ProfileScreen.kt`):
  - a larger photo with the camera button, and the name with a pencil that opens Edit Profile;
  - the bio under the name, or "Add a little about yourself using the pencil." when it is empty;
  - Claims, Donations and Rating; Verification Status; Ratings & Reviews; Saved Listings;
  - "Response: …%" and "Member since …" at the bottom, and a Log out button.
  - The bell is gone: Alerts is always in the bottom bar.
- **Edit Profile** (`ProfileSettingsUi.kt`): the display name and "About me" (at most 160 characters), saved with `update_my_profile_details` ("Profile saved to your account."). The photo can also be changed there.
- **Settings** (the gear): Notifications, Food preferences, Location & distance, Account & security, Help & food safety and About Salu-Salo, with the "How Salu-Salo works" and "Reservations & pickups" help pages. The old account-name editor is gone.
- **Food preferences and pickup area** (`DiscoveryPreferences.kt`):
  - food categories, Both/Donations/For sale, and a text that must appear in the pickup location;
  - saved per account, on this phone only;
  - while any is set, Home's "Newest listings" row becomes "For your preferences"; the full feed below is unchanged.
- **Bottom bar:** the second button is always Alerts (on Activity it used to be Browse).
- **Changed by Claude before the merge:**
  - the "About distance" note on Location & distance now points to the map distances and Nearby; it used to say that distances were not available yet;
  - the migration was renamed from `20261002_profile_bio.sql` to `20261004_profile_bio.sql`, so it sorts after the Maps file, and it got `users_bio_length_check`.

### 16.2 Migration `20261004_profile_bio.sql`

- `users.bio text` (nullable) and the check `users_bio_length_check` (at most 160 characters), which also covers a profile's first insert.
- SECURITY DEFINER `update_my_profile_details(p_name, p_bio)`:
  - takes the caller from the token and changes only their own row;
  - the name must have 1–120 characters and the bio at most 160 (blank is stored as NULL);
  - executable by `authenticated` only;
  - never changes `role`, `email` or `firebase_uid`.
- `update_my_profile_name` is unchanged, so older builds keep working.
- Only the owner sees the bio, because `users` rows can be read only by their owner.
- It must run before the new build is installed; without it, Save changes on Edit Profile fails.
- Read-only check: `supabase/tests/profile_bio_check.sql` (6 checks).

### 16.3 Results

- **Build:** `gradlew.bat :app:assembleDebug :app:testDebugUnitTest --offline` at `b2b04ca`: BUILD SUCCESSFUL in 36s on 2026-09-28, with only icon deprecation warnings; `GeoTest` 11 of 11. The developer installed it from Android Studio on both phones.
- **Migration:** applied to the live project on 2026-09-28, with `supabase/tests/profile_bio_check.sql` in the same run: 6 of 6 PASS.
- **Two-phone test (6 steps, 2026-09-28): passed.** Covered:
  - A: the new Profile layout;
  - A: Edit Profile, saving a new name and a bio ("Profile saved to your account.");
  - A: the Settings hub, and Food preferences turning Home's row into "For your preferences" and back;
  - A: the new "About distance" note;
  - A: Alerts in the bottom bar on Activity, and Nearby distances;
  - B: A's new name on the cards and the Donor row;
  - B: the new Profile, Log out and signing in again.
- **Not tested:**
  - the photo change on Edit Profile;
  - the pickup-area filter itself;
  - the 160-character limit;
  - Account & security, Help & food safety and About;
  - email verification from the new layout;
  - a Google account's password page.

### 16.4 Known limits

- Food preferences and the pickup area are saved on the phone, per account; they don't follow the account to another phone.
- The pickup-area filter matches the typed text in pickup locations, not a distance; Nearby does that.
- Only the owner sees the bio; other people's pages don't show it.

---

## 17. Fixes, clean-up and optimization after the second demo audit

**Status (2026-09-28): built with AGP at `e0f2d59` (unit tests 26 of 26 through Gradle, lint 0 errors), and the 9-step two-phone test (§17.4) passed on 2026-09-28; merged into `master` through PR #12 on 2026-09-28 (merge commit `12e931c`).** Written on branch `claude/cleanup-after-audit` (from `master` at `49ac319`, the PR #10 merge). No SQL, no database change and no new library. Results: §17.6.

Commits: `2404989` (the PR #10 merge in the docs), `9281f61` (photo tile, unused images), `5a1b90d` (signing and reservations), `2f4d424` (Post), `2a05a22` (Home and details), `8dbac5e` (keyboard), `e10894d` (welcome pages), `0bfca3f` (clean-up and icons), `5034262` (WebP), then this document.

### 17.1 The audit

| # | Item | Outcome |
|---|---|---|
| 1 | Stock photo `food_produce.png` shown for listings | Replaced by a neutral tile (`ListingPhoto`): plain for no photo, "Image unavailable" when a stored photo can't be shown. The `photo: Int` fields and parameters are gone. |
| 2 | "Recommendation insights" claimed matching that doesn't exist | Now "Check your listing", a full page of form checks (§17.2). |
| 3 | "My posts" showed the expiry in UTC | Shown in the phone's time zone. |
| 4 | Filter button looked like a filter; Expiring Soon line vague | An info icon, "About the filters"; "Expiring Soon: listings that expire within 3 hours". |
| 5 | Details "Read more" added a generic paragraph | The description shows 4 lines; Read more only when it is longer; the paragraph is removed. |
| 6 | Home "Learn more" showed one sentence | Opens "How Salu-Salo works". |
| 7 | Welcome pages 2–3: placeholder descriptions and TODOs | Real descriptions; TODOs removed. |
| 8 | Mika: a quick way to start pickup now | "Start now" on Post and Edit, with 10 minutes of slack on Post. |
| 9 | Mika: allergens required | Required on Post and Edit. |
| 10 | Mika: Home leaf icon | The profile photo or initial; opens Profile. |
| 11 | Mika: full-screen photos | Tap a details photo to see all photos full screen (no pinch zoom). |
| 12 | Mika: summary boxes misaligned | Four equal columns with aligned captions. |
| 13 | Mika: chat box behind the keyboard | Fixed, and the same fix on the other full-screen pages with text boxes (§17.2). |
| 14 | Unused images | The five `food_*.png` files (13.8 MB) deleted. |
| 15 | Dead code | Removed (§17.2). |
| 16 | Deprecated icons | `Icons.AutoMirrored.Outlined.*` in the 7 places. |
| 17 | One signing request per photo | One request per list, with a per-photo fallback. |
| 18 | Reservations loaded every visible listing | Only the reserved listings, by ID. |
| 19 | Large PNG illustrations | WebP: 892 KB → 78 KB and 535 KB → 49 KB. |
| 20 | Welcome pages on every start | Only until this phone has seen them. |
| 21 | No way to reload Home | Pull to refresh. |

### 17.2 What changed

- **Photos** (new `ListingPhotos.kt`): `ListingPhoto(imageUrl, hasPhoto, …)` is used by the Home cards, the details pager, the Pickup location and Donor pages, the Review reservation page, the chat header, Saved Listings and Activity records. No stored photo: a plain pale tile. A stored photo that can't be signed or loaded: "Image unavailable" (an icon with that description on tiles narrower than 60 dp). Activity records now carry `hasPhoto`, so they show "Image unavailable" too. Saved Listings' card for a listing you can no longer see still shows a plain tile.
- **Signing** (`data/FoodRepository.kt`): `List<FoodPost>.withSignedImageUrls(bucket)` and `BucketApi.signedUrls(paths)` sign a whole list in one `createSignedUrls` request (1-hour URLs), matched by path. supabase-kt 3.5.0 can't read the answer when any path can't be signed (Storage returns `"signedURL": null`), so then each photo is signed on its own as before. The exception is never shown or logged, because it can contain signed URLs. Used by the feed, My Listings, photos 2–4, both reservation lists, Saved Listings (visible covers in one batch, covers of listings you can no longer see in another) and the Messages inbox. Single items (create, edit, one conversation, the profile photo) keep one request each.
- **Reservations** (`data/ReservationRepository.kt`): both lists load only their listings, with `id=in.(…)` in chunks of 100; RLS decides exactly as before.
- **Post** (`PostScreen.kt`, `model/FoodPost.kt`):
  - "Check your listing" panel ("See the price, pickup window and missing details before you post.", button "Check listing") opens a full page "Check your listing": "Based on the details you've entered so far. Nothing is posted until you tap Post Listing.", then Price, Quantity, Pickup window, Expiration, Location and Details to review, and "These are form checks and calculations, not AI predictions or a food-safety assessment." The category section and the matching claims are gone.
  - "Start now" beside "Pickup starts" (Post and Edit) sets the current minute. Post accepts a start up to 10 minutes in the past through one pure rule, `isPickupStartAllowed()`, which the check page also uses. Errors: "Choose when pickup starts, or tap Start now." and "The pickup start is in the past. Tap Start now, or choose a later time."
  - Allergens: label "Allergens", error `Enter the allergens, or write "None".` on Post and Edit (so an older listing without allergens must get them to be saved), hint `List allergens such as wheat, milk, eggs or nuts, or write "None" if there are none.` Storage notes stay optional; the details page still shows "Not provided — ask the donor" when allergens are blank.
  - "My posts": "Expires: Sep 29, 2026 • 6:00 PM" in the phone's time zone, or "Expires: —".
- **Home and details** (`MainActivity.kt`):
  - The info icon ("About the filters") opens a dialog titled "About the filters": "Nearby: listings with a map pin within 5 km of you", "Donation: free food", "For Sale: paid food", "Expiring Soon: listings that expire within 3 hours", then "Tap a category below to filter the listings."
  - "Learn more" opens "How Salu-Salo works" (the Settings help text).
  - The header circle shows the profile photo (its signed URL keyed on the stored path, so a new photo shows as soon as it is saved on Profile), else the name's initial, else a person icon; tapping it opens Profile ("Your profile" for TalkBack).
  - Pull down on Home or "All food listings" reloads the feed and, only if location access is already allowed, the phone's position. It never asks for access. The spinner also shows while Home first loads.
  - Details: the description shows 4 lines, with "Read more" / "Read less" only when it is longer; the four summary columns are equal, as tall as the tallest, with the value at the top (up to 2 lines) and the caption at the bottom; tapping a photo opens a black full-screen view (whole photos, swipe, "1 / 2", "Close photos"), which returns to the photo you were viewing.
- **Keyboard** (`FoodActions.kt`: `EdgeToEdgeDialog`, `DarkSystemBarIcons()`): full-screen dialogs used the default `decorFitsSystemWindows`, so their window never got the keyboard's size and `imePadding()` did nothing. The four full-screen pages with text boxes now draw edge to edge, with dark status-bar and navigation-bar icons, the header clear of the status bar and the bottom clear of the navigation bar:
  - the chat (`ChatScreen.kt`), which also keeps the newest message in view while the keyboard opens;
  - `ActionPage` (the Reserve note on Review reservation, Report, Rate);
  - Edit Listing (`EditListingDialog`);
  - the Profile pages dialog (`ProfileSections.kt`: Edit Profile, Change Password, the pickup area).
- **Welcome** (`MainActivity.kt`): the app opens on the welcome pages only until this phone has seen them (`welcome_seen` in `salusalo_session`), set by Get Started, the new "Already have an account? Sign in" link under the button, or any account reaching Home; a saved "Keep me signed in" choice (an older build was used) counts as seen. Otherwise it opens on Sign In, where Back closes the app; Back on Create Account and Forgot password goes to Sign In. **Back on Home closes the app instead of signing out** (the developer's choice during the §17.4 test; it had signed out since the first Firebase commit, `c3c6749`); only Profile's Log out signs out. The three images are described as "People sharing a box of vegetables and bread".
- **Removed:** the five `food_*.png` images; `clearLocalAccountData()`; the Activity statuses Pending, Confirmed and Saved locally; `ProfileToggle` and `ProfileStat`; `ui/theme/` (`Color.kt`, `Theme.kt`, `Type.kt`) and `res/values/colors.xml` (`themes.xml` keeps `Theme.SaluSalo`); `UserProfile.phoneNumber`; `UserRepository.updateMyName()` (the SQL function stays for older builds); `FoodViewModel.clearError()`; an unused local on Review reservation; Home's "Salu-Salo" message dialog and the unreachable bottom-bar branch.
- **Icons:** ArrowBack, Logout, FormatListBulleted, HelpOutline and MenuBook use `Icons.AutoMirrored.Outlined`.
- **Illustrations:** `welcome_reference` and `signin_reference` are lossy WebP at quality 90 (both PNGs were fully opaque); a side-by-side check at 1:1 and 3× zoom showed no visible difference.

### 17.3 Local checks (not a build or a phone)

- **Kotlin:** the kotlinc 2.2.10 type check of every app source (as in §15.3): 0 errors, and no deprecation warnings left. Compose was checked against Compose Multiplatform 1.7.3 desktop jars (material3 1.3), not the project's Compose BOM 2026.02.01 (material3 1.4.0): Google's Maven repository is not reachable from the session. New stand-ins for Android-only APIs: `DialogProperties(usePlatformDefaultWidth, decorFitsSystemWindows)`, `DialogWindowProvider`, `LocalView`, `WindowCompat.getInsetsController` and `WindowInsetsControllerCompat` (`core-ktx` is already a dependency).
- **JVM tests** (JUnit 4.13.2 run directly, not through Gradle): 26 of 26 passed: `PhotoSigningTest` 8 (one request per list, matching by path, a refused path losing only its own picture, the per-photo fallback, cancellation, nothing to sign, each listing's own URL, and a decode test showing that supabase-kt 3.5.0 can't read a batch answer with a null URL), the new `PickupStartTest` 6 (the 10-minute rule), `GeoTest` 11, `ExampleUnitTest` 1.
- **Other:** a search that no removed name is still referenced; a secret scan of the diff.
- **Not done in that session:** the AGP build, lint, `testDebugUnitTest` through Gradle, and anything on a phone. All were done afterwards (§17.6).

### 17.4 Manual test (A → B → A; phone 1 = A, phone 2 = B)

**Preconditions:**
- This branch built with AGP (`assembleDebug testDebugUnitTest`) and installed on both phones. There is no migration.
- A has an older listing, available and unexpired, whose details page shows "Not provided — ask the donor" under Allergens (for example `OLD` from §15.4); below it is called `OLD`.
- A has a second photo ready to set as the profile photo.

Each check is tagged [New] or [Regression]. Home reloads by pulling the list down.

**A (phone 1)**

1. [New] Home, signed in as A.
   - The circle at the top left shows A's profile photo (or A's initial). Tap it → Profile. Change the photo with the camera button, then tap Home in the bottom bar: the circle shows the new photo.
   - The info icon next to the search box → "About the filters" with the five lines of §17.2 → OK.
   - "Learn more" → "How Salu-Salo works" with "Share food", "Find and reserve" and "Coordinate and collect" → Back.
2. [New, Regression] Post. Fill in `AUDIT`: "I want to donate", **2 photos**, a description of about 400 characters (8 lines or more), quantity 3, unit pcs, a pickup location, the pin, expiring tomorrow at 6:00 PM, pickup ends later today, **Pickup starts and Allergens left empty**.
   - The panel reads "Check your listing" and "See the price, pickup window and missing details before you post." "Check listing" → the page "Check your listing": "Based on the details you've entered so far. Nothing is posted until you tap Post Listing.", Price "Free donation. Recipients will not pay for the food.", Quantity "Available: 3 pcs.", Pickup window "Select both pickup start and end times.", Details to review "• 2 photo(s) selected." and `• Allergens are missing. Write "None" if there are none.` → Back.
   - "Post Listing" → "Choose when pickup starts, or tap Start now."
   - Set Pickup starts to today, more than 10 minutes ago → "Post Listing" → "The pickup start is in the past. Tap Start now, or choose a later time." "Check listing" shows the same sentence under Pickup window → Back.
   - "Start now" → the button shows the current time. "Post Listing" → `Enter the allergens, or write "None".` The hint under the fields reads `List allergens such as wheat, milk, eggs or nuts, or write "None" if there are none.`
   - [Regression] Allergens `Wheat` → "Post Listing" → "Listing published".
   - "My posts" → `AUDIT`: "Expires: <tomorrow's date> • 6:00 PM" (the phone's time, not UTC). Post's Back button.
3. [New, Regression] Activity → My Listings: `AUDIT` and `OLD` show their photos. `OLD` → "Edit listing": the field is labelled "Allergens" and is empty, and "Start now" is beside "Pickup starts".
   - "Save changes" → `Enter the allergens, or write "None".`
   - Tap the Allergens field: the keyboard opens, the field scrolls into view above it, and the "Edit Listing" header stays below the status bar. Type `None` → "Save changes" → "Listing updated."

**B (phone 2)**

4. [New] Settings → Apps → Salu-Salo → Storage → Clear storage (this signs B out), then open Salu-Salo.
   - The first welcome page, with "Already have an account? Sign in" under "Next". Tap it → Sign In. Press Back: the app closes.
   - Open it again: Sign In at once, with no welcome pages. [Regression] Sign in as B.
5. [New, Regression] Home: the circle shows B's photo or initial. Pull the list down: a spinner, then `AUDIT` appears with its photo.
   - Open `AUDIT`: the description shows 4 lines ending in "…" and "Read more" → the whole text and "Read less", with no extra paragraph.
   - The four summary columns (quantity, Free, expiry, status) have equal widths, and "Available", "Donation", "Expires" and "Status" sit on one line.
   - Tap the photo: black full screen, the photo whole, "1 / 2". Swipe → "2 / 2". "Close photos" → the details page shows the second photo.
   - [Regression] Bookmark `AUDIT`.
6. [New, Regression] Reserve 1 → "Review reservation" → tap the message box: it stays above the keyboard. Type `Audit test` → "Confirm reservation" → the "Reserved" receipt with "Your message was sent to <A>." → Done.
   - The chat icon → Messages: the `AUDIT` thread shows the listing's photo. Open it and tap the message box: the box and `Audit test` stay above the keyboard, and the header stays below the status bar. Send `Hello`.
7. [New, Regression] Activity → My Reservations → the `AUDIT` record shows its photo. Profile → Saved Listings → `AUDIT` shows its photo.

**A (phone 1)**

8. [Regression, New] Alerts: "New reservation" ("<B> reserved 1 pcs of AUDIT.") and "New message from <B>". Activity → My Donations → the `AUDIT` record shows its photo → "Mark claimed" → "Reservation marked as claimed."
9. [New, Regression] Home → system Back: the app closes; it no longer signs out. Open it again: with "Keep me signed in" ticked at sign-in, Home, still signed in; without it (the default), Sign In, because the next start begins signed out. Profile → Log out → Sign In (no welcome pages). Back: the app closes. Open it and sign in as A: Home.

**Left out of the short test (not tested unless run separately):**
- "Image unavailable" (needs a photo missing from Storage or one that can't be signed), the per-photo fallback that follows, and a plain tile for a listing without a photo (new listings always have one).
- A start between 1 and 10 minutes in the past; "Start now" on Edit; the Price, Expiration and short-window lines of the check page.
- A description of 4 lines or fewer (no Read more); rotating the phone in the full-screen view; TalkBack ("Your profile", "About the filters", "Close photos", "Image unavailable").
- The keyboard on Report, Rate, Edit Profile, Change Password and the pickup area; three-button navigation; the dark bar icons on each page.
- Pull to refresh with location allowed (distances refresh); "All food listings".
- Welcome pages on a phone that used an older build (`keep_signed_in` saved) and after restoring a backup; Back on Create Account and Forgot password.
- More than 100 reservations (a second chunk); right-to-left languages (the mirrored icons).
- The regressions not in the steps: email sign-up, Google sign-in, Cancel, removing a listing, editing other fields, reviews and reports, notification settings and sound, the password change, the maps pages and the password-reset email.

### 17.5 Known limits

- Allergens are required only in this build. The server and older builds still accept blank allergens, and the details page shows "Not provided — ask the donor" for them.
- The 10-minute slack for the pickup start is checked on the phone only; the server doesn't check the start at all.
- A list whose photos include one that can't be signed costs one request per photo, as before this change (supabase-kt 3.5.0 can't read the batch answer). If a later supabase-kt reads it, the fallback can go.
- The welcome "seen" flag is per phone. It survives sign-out and is restored with the app's Android backup; clearing the app's storage shows the welcome pages again.
- Pull to refresh reloads only the feed and the phone's position, not alerts, messages or saved listings.
- The full-screen photo view has no pinch zoom.
- Phones that ran older builds keep the unused `salusalo_profile` and `salusalo_local_posts` files; nothing reads them.
- The keyboard fix depends on edge-to-edge dialogs, which were only type-checked here.

### 17.6 Results

- **Build:** `gradlew.bat :app:assembleDebug :app:testDebugUnitTest --offline` at `7feb5b5`: BUILD SUCCESSFUL in 55s on 2026-09-28, with **no warnings** (the deprecated-icon warnings are gone). Unit tests through Gradle: 26 of 26 (`PhotoSigningTest` 8, `PickupStartTest` 6, `GeoTest` 11, `ExampleUnitTest` 1).
- **Lint:** `gradlew.bat :app:lintDebug`: 0 errors, 38 warnings and 7 hints, none caused by this batch:
  - version notices, `UseKtx` and `AutoboxingStateCreation`;
  - the WebP illustrations in the densityless `drawable` folder;
  - the default launcher foreground reported as unused (it stays until the real icon, Phase 6);
  - `android.media.ExifInterface` in `data/ProfilePhoto.kt`, which lint suggests replacing with androidx.exifinterface because of security bugs on old Android versions (a later task).
- **Changed during the test:**
  - the developer asked why Back on Home signs out; it had done so since the first Firebase commit (`c3c6749`);
  - Claude changed it so that Back on Home closes the app and only Profile's Log out signs out (`e0f2d59`; `assembleDebug --offline` BUILD SUCCESSFUL);
  - step 9 was updated to match.
- **Two-phone test (§17.4, 9 steps, 2026-09-28): passed.**
  - It was sent in two parts: steps 1–3, then 4–9.
  - Steps 1–3 ran on the `7feb5b5` build; phone A got the `e0f2d59` build before step 9.
  - Step 9 ran with "Keep me signed in" unticked, so after Back the app opened on Sign In, as designed.
- **Not tested:**
  - the list under "Left out of the short test" in §17.4;
  - Back on Home with "Keep me signed in" ticked (the app should still be signed in when opened again).
- **Merged** into `master` through PR #12 on 2026-09-28 (merge commit `12e931c`).

---

## 18. Automatic Supabase role for new accounts

**Status (2026-09-28): built with AGP at `9126799` (unit tests 34 of 34), the function deployed and set up, and the 7-step two-phone test (§18.5) passed on 2026-09-28; merged into `master` through PR #13 on 2026-09-28 (merge commit `b8aa7f9`).** Written on branch `claude/auto-role-claim` (from `master` at `12e931c`, the PR #12 merge). No SQL and no database change. Until now the developer ran a local Firebase Admin SDK script after each sign-up to give the account the custom claim `role: "authenticated"` that Supabase Third-Party Auth and `is_salusalo_firebase_jwt()` require. The script is not in the repo and stays as a backup.

### 18.1 What changed

- **Edge Function `set-firebase-role`** (`supabase/functions/set-firebase-role/index.ts`, no dependencies: Web Crypto and `fetch` only). It gives the calling Firebase account the claim and nothing else.
  - It verifies the Firebase ID token from `Authorization: Bearer <token>` itself: RS256 signature against Google's `securetoken@system.gserviceaccount.com` keys (cached for their `Cache-Control` max-age; an unknown key ID fetches them again at most once a minute); issuer `https://securetoken.google.com/salusalo` and audience `salusalo`; not expired and not issued or signed in in the future (60 s of clock skew); a non-empty subject. Anything else: 401 `{"error":"Invalid or missing Firebase ID token."}`. The request body is never read. Methods other than POST: 405 `{"error":"Use POST."}`.
  - Anonymous accounts (`firebase.sign_in_provider` = `anonymous`): 403 `{"error":"Anonymous accounts can't use Salu-Salo."}`, nothing changed.
  - A token that already has the role: 200 `{"role":"authenticated","changed":false}` without calling Google.
  - Otherwise it gets a Google access token for the service account in the secret `FIREBASE_AUTH_ADMIN_KEY` (scope `https://www.googleapis.com/auth/cloud-platform`, the only one Google lists for these two methods), reads the account with the Identity Toolkit REST API (`v1/projects/salusalo/accounts:lookup`), refuses a deleted account (401), a token issued before the account's sessions were revoked (401) and a disabled account (403 `{"error":"This account is disabled."}`), and writes the custom claims with `role` added and every other claim kept (`accounts:update`), only when the role is missing. 200 `{"role":"authenticated","changed":true}` (or `false`).
  - A missing or unusable secret: 500 `{"error":"Server setup incomplete: the FIREBASE_AUTH_ADMIN_KEY secret is missing or invalid."}`. A Google error: 502 `{"error":"Could not update the account. Try again later."}`, with only the step and HTTP status in the log.
  - Logs: one line per call, never the token, the key, the access token, Google's error body or a full UID; at most the UID's first 6 characters (`role set (uid abc123…)`), and a refusal only its reason code (`refused (token_expired)`).
  - Entry point `export default { fetch }`, which Supabase's docs describe as equivalent to `Deno.serve`; the fallback is in the setup's last check.
- **App.**
  - `UserRepository.ensureSupabaseRole()` runs in `openAuthenticatedHome()` before the first Supabase call of a session, so it covers email sign-up, sign-in, Google sign-in and a session restored at start-up. It reads the cached ID token's claims; when `role` isn't exactly `authenticated` it refreshes the token once (the backup script may have set it); only when that token also lacks it does it call the function (`supabase.functions.invoke("set-firebase-role")`) and refresh again. An account that already has the role makes no extra call. A process-wide `Mutex` stops the overlapping sign-in checks (the sign-in callback and the check when the signed-in user changes) from calling it twice.
  - The call uses supabase-kt's Functions plugin (`functions-kt` from the existing 3.5.0 BOM, `install(Functions)`), which sends the same Firebase token and publishable key as the other plugins. Any failure becomes `ProfileException.RoleSetupFailed`; the library's exception (its text can include the URL) is never shown or logged.
  - On any failure of the setup or of loading the profile, the app goes to Sign In, keeps the user signed in to Firebase, and shows "Salu-Salo" / "Your account is signed in, but Salu-Salo couldn't finish setting it up. Check your internet connection, then tap Try again." (for the role) or the existing "We could not load your profile. Check your internet connection and try again." with **Close** and **Try again**. Try again repeats the setup and keeps the name typed on Create Account. A failure of a second, overlapping attempt after the first reached Home shows nothing.
  - The `refreshFirebaseToken` parameter and the "Cloud Function" comments are gone.
- **Repo.** `.gitignore` ignores `salusalo-*.json` (Google's default name for the downloaded key) and `*service-account*.json`; neither matches `app/google-services.json` or any tracked file.

Commits: `710f643` (the PR #12 merge in the docs), `9ded1ca` (the function and its Deno tests), `3f16242` (the app), then this document with `supabase/README.md`, `CLAUDE.md`, `README.md` and `.gitignore`.

### 18.2 One-time setup (the developer)

The steps, with the log lines and what to do on a 500 or 502, are in [`supabase/README.md`](../supabase/README.md) under "Edge Function `set-firebase-role`". In short:

1. Google Cloud console, project **salusalo** (check the project box at the top left before creating anything: on 2026-09-28 the first try went into another project): ☰ → **IAM & Admin** → **Roles** → **+ Create role**: title `Salu-Salo role setter`, ID `salusaloRoleSetter`, stage General Availability → **+ Add permissions** → **Filter permissions by role** → **Firebase Authentication Admin** → tick `firebaseauth.users.get` and `firebaseauth.users.update` (typing the names didn't find them) → **Add** → **Create**.
2. **IAM & Admin** → **Service Accounts** → **+ Create service account**: `salusalo-role-setter` → **Create and continue** → role **Custom** → **Salu-Salo role setter** → **Continue** → **Done**. Its email must end with `@salusalo.iam.gserviceaccount.com`.
3. The account → **Keys** → **Add key** → **Create new key** → **JSON** → **Create** (downloads `salusalo-<12 characters>.json`).
4. **APIs & Services** → **Enabled APIs & services**: **Identity Toolkit API** is listed (enable it if not). No other API.
5. Supabase → **Edge Functions** → **Secrets**: `FIREBASE_AUTH_ADMIN_KEY` = the whole JSON file → **Save**.
6. **Edge Functions** → **Deploy a new function** → **Via Editor** → name `set-firebase-role` **before the first deploy** (renaming later only changes the label; the list's URL column must end with `/functions/v1/set-firebase-role`) → paste the whole `supabase/functions/set-firebase-role/index.ts` → **Deploy function**.
7. On the function's settings, turn **off** JWT verification and save (the CLI equivalent, not used here: `[functions.set-firebase-role]` / `verify_jwt = false` in `supabase/config.toml`).
8. Delete the downloaded key file (and empty the trash), or keep it only outside every repository.
9. The function's **Test** → HTTP 401 `{"error":"Invalid or missing Firebase ID token."}` and one `refused (...)` log line. A boot error instead: switch the last block to `Deno.serve` as `supabase/README.md` shows, deploy again.

`firebaseauth.users.get` is the permission Google's Identity Platform access-control page lists for GetAccountInfo (`accounts:lookup`), and `firebaseauth.users.update` the one for SetAccountInfo (`accounts:update`). These were read through web-search summaries of Google's pages (the pages themselves could not be opened from the session). If a 403 stays with both, `supabase/README.md` names which log line points at which permission; "Firebase Authentication Admin" is the last resort.

### 18.3 Security

- **Identity only from the verified token.** The function checks the signature, issuer, audience and times itself (the Supabase gateway can't verify Firebase tokens, so its JWT check is off) and uses only the token's `sub`. The request body is ignored, so a caller can't name another account or another claim.
- **One claim only.** The only write is `role: "authenticated"` merged into the caller's own claims; every other claim is kept. That is exactly what the script gives every account, so a caller gains nothing beyond what any signed-up account gets.
- **Least privilege.** The service account's only role is a custom one with `firebaseauth.users.get` and `firebaseauth.users.update`: a leaked key could read and change accounts, but not create or delete them. The key is only a Supabase Edge Function secret: not in the app, the repo, the docs or a log. The app has no Admin credentials, service-role key or secret key; it calls the function with the Firebase token and the publishable key it already uses.
- **Abuse.** Calls need a valid token of project `salusalo`. A token that already has the role costs no Google call; a bad token costs one signature check against cached keys; an unknown key ID refetches Google's keys at most once a minute. Anonymous, disabled, deleted and revoked accounts are refused. There are no CORS headers (the app is not a browser). Supabase's invocation limits and Firebase's sign-up limits apply.
- **Logs.** No token, key, access token, Google error body, email or full UID.
- **A change in who gets in.** Before, nobody reached Supabase until the developer ran the script, which acted as a manual gate. Now every new account of the Firebase project gets the role on its first sign-in, as intended: anyone can already sign up in the app, and email sign-up doesn't require a verified email (unchanged). All RLS and server-function rules still apply.

### 18.4 Local checks (not a build, a deploy or a phone)

- **Deno 2.9.6** (installed from npm into the session's scratch folder, not the repo): `deno check` and `deno lint` clean; `deno test supabase/functions/tests/set-firebase-role-test.ts`: 21 of 21 passed, with locally generated RSA keys and a fake Google (no network): a good token; wrong audience; wrong issuer; expired (and accepted within the skew); issued or signed in in the future; a forged signature and a changed body; an unknown key ID, HS256, `none`, an empty subject and a non-token; no token, `Basic` and GET; refusals logging only their reason code; the anonymous refusal with no Google call; a request body naming another account ignored; the role in the token answered with no Google call; the role added with other claims (including nested ones) kept, the OAuth scope, and no token, access token, key or full UID in the logs or the answer; the access token reused on a second call; a stored role not written again; an account without claims; disabled, deleted and revoked accounts refused and unchanged; a missing, empty, non-JSON or wrong-project secret giving the setup message; Google's error body neither returned nor logged; the claim merge idempotent; Google's keys cached for max-age.
- A mutation check: removing the audience, issuer, signature, expiry or anonymous check, dropping the other claims, or logging the full UID each made the tests fail.
- The function served with `deno serve`, called with curl: POST with no token → 401 with the exact body, `Content-Type: application/json` and `Cache-Control: no-store`; GET → 405; a non-Firebase key as the token → 401. The `Deno.serve` fallback from the setup also type-checked and answered 401.
- **Kotlin:** the kotlinc 2.2.10 type check of every app source (as in §17.3) with the real `functions-kt-jvm-3.5.0.jar` added: 0 errors. **JVM tests** (JUnit 4.13.2 run directly): 34 of 34, of them the new `SupabaseRoleTest` 8 (a cached token with the role: no refresh and no call; a refreshed token with the role: no call; a new account: one call, then a refresh; the role still missing afterwards; a function error, with its text not kept; a token error; cancellation passed on; only exactly `authenticated` counts).
- **Not done:** the AGP build, lint and `testDebugUnitTest` through Gradle; the Google Cloud and Supabase setup; the deploy; anything on a phone.

### 18.5 Manual test (phone 1 = A, phone 2 = B)

**Preconditions:**
- The setup in §18.2 done, including its last check (401).
- This branch built with AGP (`assembleDebug testDebugUnitTest`) and installed on both phones. Android Studio's Run installs only on the phone picked in the device list, so run it once per phone. There is no migration.
- A Gmail address the developer controls, for "+" aliases (`<name>+role1@gmail.com`, `<name>+role2@gmail.com`). **Don't run the backup script** for the new accounts.
- A has an available listing. The Supabase dashboard is open at **Edge Functions** → `set-firebase-role` (its **Logs** and **Invocations**).
- Optional: a spare Google account never used with Salu-Salo.

1. [New] Phone 2: Profile → Log out. Create Account: name `Role Test`, email `<name>+role1@gmail.com`, a password → Home with "Good <morning/afternoon/evening>, Role 👋" and no error dialog.
2. [New, Regression] Still as Role Test: open A's listing → Reserve 1 → "Confirm reservation" → the "Reserved" receipt. Then Activity → My Reservations → cancel it, so A's quantity comes back.
3. [New] Dashboard: **Invocations** shows one POST with status 200 for step 1, and **Logs** exactly one `set-firebase-role: role set (uid ……)`; its 6 characters match the start of Role Test's User UID in the Firebase console (Authentication → Users). No line has a full UID, an email or a token (`eyJ…`).
4. [Regression] Phone 2: Log out → sign in as B → Home. Phone 1: Log out → sign in as A → Home, as before. Dashboard: no new invocation (A and B already have the role).
5. [New] Only with a spare Google account: phone 2: Log out → Continue with Google → the spare account → Home with its Google name; dashboard: one more `role set`. Log out and sign in as B again. Without one, skip this step and say so.
6. [New] Safe failure. Dashboard: the function's settings → turn JWT verification **on** → save. Phone 2: Log out → Create Account `Retry Test`, `<name>+role2@gmail.com` → Sign In with the dialog "Salu-Salo" / "Your account is signed in, but Salu-Salo couldn't finish setting it up. Check your internet connection, then tap Try again." and the buttons "Close" and "Try again".
   - "Try again" → the same dialog. "Close" → Sign In. Swipe Salu-Salo away from Recents and open it: the same dialog again (the account is still signed in).
   - Dashboard: turn JWT verification **off** → save. "Try again" → Home with "…, Retry 👋"; Profile shows `Retry Test`.
7. [New] Dashboard after step 6: **Invocations** shows the 401s while verification was on and then one 200; **Logs** one more `role set` (the 401s were refused by the gateway before the function ran, so they have no function log line). Phone 2: Log out → sign in as B → Home.

**Left out of the short test:** offline sign-up (the same dialog), a disabled or deleted account, the 500 and 502 answers (a wrong secret or missing permission), an older app build, "Keep me signed in" unticked during a failure, and the regressions not in the steps (reviews, reports, maps, chat and alerts). The two test accounts can stay; if one is deleted in Firebase, its Supabase profile stays (§14.6).

### 18.6 Known limits

- **Older app builds** (before this branch) never call the function: accounts made with them still need the backup script.
- **Not atomic.** The function reads the claims, then writes them. If another tool (the script, or a future admin tool) changes the same account's claims at the same moment, one change can be lost. Identity Toolkit has no compare-and-set for claims.
- **Size.** Firebase allows at most 1,000 characters of custom claims; if other claims ever fill that, the write fails (502).
- **The key doesn't expire.** If it ever leaks, delete it in Google Cloud (☰ → IAM & Admin → Service Accounts → `salusalo-role-setter` → Keys → delete), create a new one and put it in the secret (`supabase/README.md`, "The key"). The old key stops working at once: it can't get new Google access tokens (an access token already issued with it expires within 1 hour at the latest). Rotating it changes nothing for users.
- **No version history.** The dashboard editor keeps none; the repo file is the source, and a change means pasting it and deploying again.
- **Once set, the role stays** (as with the script). A disabled or revoked account is refused only when it calls the function; Firebase itself stops a disabled account from signing in.
- **Anonymous accounts** are refused, unlike the script, which covers every user. The app has no anonymous sign-in.
- **Every new account now gets in** without the developer (§18.3); before, the script was a manual gate.
- The exact label of the dashboard's JWT verification switch was not confirmed (supabase.com could not be opened from the session); the steps describe it by what it does.
- Each new account costs up to 1 invocation (2 with overlapping attempts only when the first fails).

### 18.7 Results

- **Build:** `gradlew.bat :app:assembleDebug :app:testDebugUnitTest` at `9126799`, run online once to download `functions-kt` 3.5.0: BUILD SUCCESSFUL in 1m 48s on 2026-09-28, with no warnings. Unit tests: 34 of 34, including the 8 new `SupabaseRoleTest` tests. No lint was run. The developer installed the build from Android Studio on both phones.
- **Setup (2026-09-28):** three problems were found and fixed on the way.
  - **The function's address.** The dashboard editor deployed it at the random address `quick-api`, and renaming it later only changed its label, so the app's call got 404 "Requested function was not found". It was deleted and deployed again as `set-firebase-role`.
  - **The old build.** Phone 2 still ran the previous build, which never calls the function: Android Studio's Run installs only on the selected phone. Supabase refused its profile loads with 401 until the new build was installed.
  - **The wrong project.** The first custom role, service account and key were created in another Google Cloud project (`gmailpracticeapi`), so the function answered 500 "the FIREBASE_AUTH_ADMIN_KEY secret is missing or invalid". They were made again in project `salusalo` and the secret was replaced; the ones in the other project were deleted.
  - **Claude's checks** (a POST and a GET with no token, and the logs read through the Supabase connector): 401 `{"error":"Invalid or missing Firebase ID token."}` and 405 `{"error":"Use POST."}`. So the function starts on the Edge Runtime with `export default { fetch }`, and the gateway's JWT verification is off.
- **Two-phone test (§18.5, 7 steps, 2026-09-28): passed**, sent in two parts: steps 1–4, then 5–7. Each result was checked in the Supabase logs.
  1. The new email account `Role Test` reached Home without the script at 17:32 +08:00: one `role set`, and its profile was created.
  2. It reserved one of A's listings and cancelled the reservation; both calls answered 200.
  3. The log shows exactly one `role set (uid …)` for it, with 6 characters of the UID and no email or token.
  4. A and B signed in with no function call.
  5. A spare Google account reached Home with one `role set`.
  6. With the gateway's JWT verification on, `Retry Test` got the setup-failure dialog with Try again: three 401s at the gateway and no function log line. With verification off again, Try again reached Home with one `role set`.
  7. B signed in with no function call. Afterwards Claude confirmed that JWT verification is off.
- **An account left without the role:** one created while phone 2 still had the old build (UID `ShBi…`) has no role until it signs in with the new build.
- **Not tested:** the list under "Left out of the short test" in §18.5.

---

## 19. UI/UX refresh

**Status (2026-09-29): written on branch `claude/focused-mayer-0aevp8` (from `master` at `b8aa7f9`, the PR #13 merge). A kotlinc type check (0 errors) and the JVM tests (43 of 43) ran in the session; not built with AGP, not run on a phone or emulator.** **Merged into `master` through PR #14 (`f27ee47`) and PR #15 (`fa95fff`) on 2026-09-29, with no separate test record; its code is part of the §21 build, which was built with AGP and passed a one-phone test on 2026-09-30.** On 2026-09-29, at the developer's request, the first version's new colours (yellow, pink, a warm near-black) and its new logo were replaced by Salu-Salo's original palette and logo (below); layout, type, components and motion stayed. The same day, also at the developer's request, Post went back to the middle of the bottom bar, where the original had it, and Activity was rebuilt from the developer's mockups (three tabs and full-screen reservation pages; below). No SQL, no database change and no new library. Repositories, ViewModels, models, Supabase, Firebase, reservations, messaging, notifications and the map itself are unchanged; the UI calls the same functions with the same arguments.

### 19.1 What changed

- **Design system (`SaluTheme.kt`, new).** Salu-Salo's original colours, with the exact values the app used on `master` (`MainActivity`'s palette): brand green `#47782E` (main actions with white text, selection, links, icons), dark green `#263C29` (titles and text), cream `#FCFBF6` (background), pale green `#F3F6E8` (soft surfaces and tags), icon green `#E4EDC9` (icon circles, avatars, the selected tab), grey `#666873` (supporting text), line `#E3E4E7`, the page-dot grey `#DDDEDA` and the "not verified" grey `#EDEEF0`; the accents orange `#E85B13` (expiring soon), orange `#EF7900` (unread dots, reservations held), gold `#FFA125` (the spark before recommendations), star gold `#FFAD08` and rating amber `#B26A00`, and Material's error red `#B3261E`, which the original theme used for errors, Delete, Log out and the Home badges. The only additions are see-through tints of these. Screens that had their own near-identical shade (for example Profile's `#347523` or Activity's `#39792B`) now use the main one. Orange is too light for small text, so expiring items show an orange hourglass and tint with dark green words. A type scale (screen title, section, food title, body, supporting, metadata, status, price), moderate corner radii (pills only for filters, tags and statuses) and short motion timings. Shared pieces: green primary buttons (white text) and outlined secondary buttons (green text) with press feedback, cards, section headers, status badges (icon and word), price tag, the original filter chips (green outline and label, green when selected), empty states, one-line notices, count badges, avatar, the logo and name, top bar, page dots and skeleton blocks. `MaterialTheme` now comes from `SaluTheme`, with `primary` the brand green as before. The per-file colour palettes are gone or point at these tokens.
- **Logo.** The original Salu-Salo logo, taken unchanged from the launcher icon (`R.mipmap.ic_launcher_foreground`, its sharpest file) and shown round like the round launcher icon (`SaluLogo`): on the loading screen, at the top of the welcome pages and Sign In, and on About. No logo file was added or redrawn, and the launcher icon is unchanged. (Replaced by the new logo in §29.)
- **`UiText.kt` (new).** "Expires in 45 min / 5 h / tomorrow / Sep 30", "Posted 3 h ago", the reason a listing is in "Picked for you" (from the saved Food preferences and pickup area only), the singular unit for prices ("pack") and initials. Tested by `UiTextTest` (9 tests on 2026-09-29).
- **Start and sign-in.** While a saved session or a just-signed-in account is set up (`ensureSupabaseRole()` and the profile load, unchanged), a loading screen (the original logo, which fades in and then breathes gently, the name, "Good food. Less waste. Stronger community.", a thin green progress bar and "Getting things ready…") replaces the Sign In form; Home fades in. If setup fails, Sign In with the same Try again dialog as before. Welcome pages and Sign In / Create Account / Reset password restyled: each illustration on its own rounded card, a two-part Sign in | Create account switch, labelled text fields, the spinner inside the main button, "Keep me signed in" as a 48 dp row.
- **Navigation.** The bottom bar keeps the original order Home → Alerts → Post → Activity → Profile, with Post in the middle (the first version of this refresh had moved Post second), and the original white bar with rounded top corners and a shadow and the green Post button with a white plus; the other tabs have a pale green selection pill, a filled icon for the current tab and the unread-alert count on Alerts. Tab changes slide a tenth of the width with a fade (320 ms). A sideways swipe on one of the five main pages moves to the neighbouring tab once the finger lifts (not on All listings or Messages); chip rows, the recommendation row, the Activity tabs, photo rows and the map keep their own drags. Post keeps its draft while other tabs are open; system Back from Post and "Back to home" after posting still clear it, as before. Tapping Home while on Home scrolls to the top. The "Post Food" button on Home was removed (Post is in the bar). Activity's tab is now reset to My Listings when Activity is opened from the bar or a swipe (alerts still choose their tab), instead of when Activity is left, so the page fading out doesn't change.
- **Home.** Header with the profile photo, the greeting and Messages and Alerts with counts; search with a clear button; filter chips with icons (Donation and For Sale now read "Free" and "For sale"; the help dialog lists each filter). Location notes appear as a one-line notice. "Picked for you" (when Food preferences or a pickup area are set) or "Just posted" sits on a tinted band, each card saying why it is there ("Because you like vegetables", "Posted 3 h ago"); which listings appear is unchanged (the saved preferences, newest first, the first 2). New food cards: photo, name, donor and rating, price or Free with its unit, amount available, expiry, distance or pickup place, and an animated bookmark. Skeleton cards until the first feed load has finished; empty states for no food, no search match, no filter match, Nearby without a position and a failed load (with Try again). The feed is now a `LazyColumn` (it was one scrolling `Column` of every card).
- **Food details.** A 300 dp photo pager with dots; status and category; the title; a donor card (rating or "No reviews yet", Email verified when the donor has it, Message); three key facts (available, price, expiry); sections About this food, Pickup (window, handover, location with View on map) and Food safety (allergens, storage). Tapping a detail still opens the chat with the same prefilled question. Report this listing at the end. A bottom bar always shows what the chosen quantity costs, the quantity stepper and Reserve; an own listing shows a notice there instead.
- **Post and Edit.** A Donate / Sell switch; sections Food (photos with the cover marked and a remove button on each, name, category and unit as chips, quantity, the price with a ₱ prefix only when selling, description), Pickup (location, the pin picker, start with Start now, end, handover chips) and Food information (allergens, storage, expiration). Required fields are marked *. Errors show as a notice by the Post button; "Post another listing" after posting starts an empty form; My posts is folded under the form, with status badges. The same fields, checks, texts and `FoodViewModel` calls.
- **Activity** (rebuilt on 2026-09-29 from the developer's mockups). "Activity" / "Your food-sharing journey", the bell as before, and three tabs in one segmented control instead of five:
  - **My Listings** ("Food you posted", Donor / seller): Active (can still be reserved, or has open reservations to hand over) and Closed. Each card shows "Donation · Free" or "For sale · ₱20.00 / pc", what is left, "2 active reservations" / "No reservations yet" and a status badge when it can't simply be reserved; Manage reservations and View listing, and a ⋮ menu with View, Edit and Delete listing. "Create listing" at the end opens Post.
  - **My Reservations** ("Food you reserved", Recipient / buyer): open reservations only, filtered All, Free food or Purchases, each with "From <donor>", the amount and total, "Pay at pickup" when priced, the pickup window ("Pickup today, 4:00 PM – 6:00 PM", else the place), View reservation and a ⋮ menu (Message provider, Cancel reservation).
  - **History** ("Your past transactions"): picked-up and cancelled reservations from both sides, newest first, filtered All, Completed or Cancelled, each saying what it was ("Donation to Mika", "Purchase from Lina · ₱60.00") and when. The Completed and Items rescued totals moved here from the old summary card.
  - **Pages** (full screen): Manage reservations (per listing: what's available and reserved, Active / Completed / Cancelled, each recipient by name with an initial avatar, Message and Mark as picked up); Reservation details (quantity, cost, pickup window or when it was picked up or cancelled, a note on where things stand, View pickup details, Message provider, Cancel reservation, or Rate / Change your review once picked up); Pickup details (quantity, total, payment, the window, place and handover, the pin on the map with Open directions, Message provider); Your listing (availability, reservations, price, expiry, pickup, the food's details, Manage reservations, Edit, Delete).
  - **Same data and calls.** The lists come from the same view models, and every action calls what it did before (`claim`, `cancel`, `submitReview`, `openForReservation`, `deletePost`, Edit Listing). New: Mark as picked up (was Mark claimed) and Cancel reservation ask for confirmation first; claimed reservations read "Completed", and the claim's result reads "Marked as picked up". Alerts open the new places: a new or cancelled reservation on your listing opens that listing's Manage reservations; a claimed or removed one opens the reservation in History. Removed: the five pill tabs, "See all", the Active Listings total (the Active filter replaces it) and the "Thanks for making a difference!" card (Create listing replaces its Post Food). Donors see recipients' names only, so the avatars are initials, not photos.
- **Alerts.** Today and Earlier as grouped lists; each kind with its own icon, colour and label (message, new reservation, cancelled, claimed, listing removed); unread rows tinted pale green, with a bold title, an orange dot and "Unread" for screen readers; "All caught up" when nothing is unread.
- **Messages and chat.** Inbox rows with an unread tint and count, skeleton rows and an empty state. The chat has day headings, green (yours, white text, as before) and white bubbles with times, a round send button, and "Report <name>" in a ⋮ menu (it was a flag icon).
- **Profile and settings.** Photo, name, the Email verified chip, bio and Member since come first; then a stats row (Rating, Response, Claims, Donations) with skeletons while loading, a reviews preview, "Your account" (Edit profile, Saved Listings, Email verification) and "Settings" (Notifications, Food preferences, Location & distance, Trust & Safety, All settings), and Log out. The Settings hub is grouped and gained a Trust & Safety row (an existing page). Sub-pages use the shared top bar, skeletons, empty states and notices. The same pages and saves.
- **Shared pages** (`ActionPage`: Pickup location, Donor/Seller, Review reservation and the receipt, Rate, Report, Check your listing, How it works) use a back-arrow top bar. Listing photos fade in over cream (Coil's crossfade) instead of popping in.
- **Accessibility.** New controls have 48 dp touch targets; headings are marked; counts are spoken with their control ("Open alerts, 3 unread"); statuses are never shown by colour alone; text sizes are in sp with no fixed-height text boxes.

### 19.2 Local checks (not a build or a phone)

- **Kotlin:** a kotlinc 2.2.10 type check (with the Compose and serialization plugins) of every app source: 0 errors, 0 warnings. As before (§17.3), Google's Maven repository is not reachable from the session, so it used Compose Multiplatform desktop jars (foundation and ui 1.8.2, material3 1.7.3, which matches Jetpack material3 1.3, older than the project's 1.4.0), Robolectric's `android-all` 17 jar, osmdroid 6.1.20, the supabase-kt 3.5.0 and Coil 3.3.0 JVM jars, and hand-written stand-ins for AndroidX Activity, Lifecycle and Core, Credential Manager and Firebase. **It is not an AGP build.**
- **JVM tests** (JUnit 4.13.2 run directly): 43 of 43, of them the new `UiTextTest` 9 (4 for Activity's pickup windows and dates).
- **Not done:** the AGP build, lint and `testDebugUnitTest` through Gradle; anything on a phone or emulator. No screenshot of the real app exists; the design canvas made in the session shows mockups of the direction.

### 19.3 Manual test (one phone for most steps; phone 1 = A, phone 2 = B for reservations)

**Preconditions:** this branch built with AGP (`assembleDebug testDebugUnitTest lintDebug`) and installed on both phones; A has an available listing; B has Food preferences set. There is no migration.

1. [New] With "Keep me signed in" on, swipe Salu-Salo away and open it: the loading screen with the original logo (round, sharp, not cut off), then Home fades in, with no Sign In form in between. Log out: Sign In; a wrong password shows the error dialog; Create Account and Forgot password switch in place.
2. [New] Home: skeleton cards on the first load; greeting, photo and counts (the red badges on Messages and Alerts show the whole number, two digits too, with nothing cut off); search and clear; each filter chip; Nearby without location access (a notice and an empty state); the sort menu; "Picked for you" reasons match B's Food preferences ("Just posted" without preferences); a bookmark animates, and stays after reopening.
3. [New] The bottom bar reads Home, Alerts, Post, Activity, Profile, with the green Post button in the middle. Swipe Home → Alerts → Post → Activity → Profile and back. The chip row, the "Picked for you" row, the Activity tabs, the photo row on Post and the map scroll without changing tab, and scrolling up and down never changes tab. Tap each tab; tap Home while on Home.
4. [New] Post: fill half the form, open Alerts, come back: the draft is still there. Back: Home, and Post opens empty. Post a listing (photos, chips, pin, times): "Listing published" → "Post another listing" shows an empty form.
5. [New, Regression] B opens A's listing: photos and dots, full screen; the donor card → Donor page; tap Allergens → the chat with the prefilled question; View on map → map and Directions; the quantity stepper and the pay line; Reserve → Review reservation → the Reserved receipt.
6. [New, Regression] Activity (A and B): B reserves one free and one priced listing of A's. B → My Reservations: both cards (From <A>, totals, Pay at pickup on the priced one, the pickup window); Free food and Purchases each show one. View reservation → View pickup details: the map and Open directions; Message provider opens the chat over the page. A → My Listings → Active: "1 active reservation" on each; Manage reservations → Message, then Mark as picked up → the confirmation → "Marked as picked up…"; the reservation moves to Completed. B → History: "Donation from <A>" or "Purchase from <A> · ₱…", Completed; open it → Rate <A> → the review page. B cancels the other reservation (with the confirmation) → it shows under History → Cancelled for both. A: View listing, the ⋮ menu's Edit and Delete, Closed after expiry, and Create listing. Alerts: A's "new reservation" alert opens that listing's Manage reservations; B's "claimed" alert opens the reservation in History.
7. [Regression] Alerts: each kind looks different, unread rows stand out, "Mark shown as read", the settings dialog; each alert opens the same place as before.
8. [Regression] Messages: unread counts in the inbox and on Home; open a chat; day headings; send; ⋮ → Report <name>.
9. [Regression] Profile: the stats open their pages; Edit profile; the Settings hub rows; Log out.
10. [New] Accessibility: the largest font size and display size: no cut-off text on Home cards, details, Post, Activity and Profile; TalkBack reads the bottom bar ("Alerts, 3 unread"), cards and status badges.

### 19.4 Known limits

- Not built with AGP or run on a phone in this session. It was type-checked against older Compose Multiplatform material3 (Jetpack 1.3) than the project's 1.4.0; only APIs in both were used, but the AGP build is the real check.
- The swipe changes tab after the finger lifts (with the slide), rather than a pager that follows the finger, so no page is composed or loaded just because it was dragged past. Material's own guidance discourages swiping between bottom-bar tabs, so it is limited to the five main pages.
- Post's draft is kept across tabs only while the app runs (and through rotation).
- Light theme only; the system dark theme is not followed (as before).
- At the largest font sizes some one-line texts (a card's pickup place, chat previews) end with "…" rather than wrapping.
- The illustrations keep their own light background, shown as a rounded card.
- The logo is the launcher artwork enlarged from its 432 px file; on a very dense screen the 104 dp logo on the loading screen may look slightly soft.
- The recommendation choice, all data and all server rules are unchanged; nothing shown is invented (the reasons come from the saved preferences or the listing's time).
- Activity: a donor's open reservations are grouped by listing (each listing's Manage reservations), not in one list as the old My Donations tab had them. History dates each reservation by its last change (`updated_at`: when it was picked up or cancelled). "Marked as picked up…" replaces the view model's "Reservation marked as claimed." on screen only; the view model is unchanged.

### 19.5 Results

- 2026-09-29, reported by the developer: on Home, the red count badges on Messages and Alerts were cut off along a curve, hiding part of the number. Cause: both sat in Material's `IconButton`, which clips its content to a 40 dp circle, and the badge reached past it. Fixed afterwards: `HeaderIconButton` (`MainActivity.kt`) is now a plain 48 dp button with the same round ripple that doesn't clip. Not re-tested. Nothing else was reported, so the §19.3 test is still to be run.

## 20. Admin dashboard (web, sample data)

**Status (2026-09-29): written on branch `claude/focused-mayer-0aevp8` at the developer's request, in `admin/`. It is a static website (HTML, CSS and JavaScript, no build step, no dependencies) with generated sample data. It is not connected to Supabase or Firebase, and nothing outside `admin/` and this document changed: no Kotlin, no SQL, no migration, no Firebase or Supabase setting.** **Merged into `master` through PR #15 on 2026-09-29.** How to open it and how it is organised: `admin/README.md`.

### 20.1 What it has

- **Look.** The app's own colours from `SaluTheme.kt` (green `#47782E`, dark green `#263C29`, cream `#FCFBF6`, pale green, icon green, orange for held and pending things as icons only, error red `#B3261E` for destructive actions), Roboto, Material Symbols line icons (the family of the app's icons), moderate corners, status badges with an icon and a word matching the app's `StatusBadge` (Available, Reserved, Claimed, Cancelled, Expired, Removed), and the original logo: the launcher artwork copied unchanged. The developer's brief listed yellow `#F8C163`, cream `#FFF8ED`, `#F6EBD7`, green `#338C28` and pink `#D62F6B` as the existing palette; those are the first UI refresh's colours, which the developer had replaced with the original ones (§19). The dashboard uses the original ones, so it matches the app; all colours are tokens at the top of `admin/css/admin.css`.
- **Layout per screen size.** Desktop: a sidebar (logo, nine pages with icons, counts for verification requests and open reports, a dot for unread alerts, the admin, collapse to icons). Tablet: an icon rail that opens over the page. Phone: a top bar with a menu button and a drawer; tables become cards, filters stack, details open full screen, dialogs slide up from the bottom. No sideways page scrolling at any size.
- **Pages.** Overview (four headline numbers, recent activity, verification requests, reports to review, reservation activity, food rescued in 30 days with a pickup rate); Users (search, role, verification and account filters, pagination, user details with activity, verify, suspend and restore; a verification-requests tab and a review page showing who, what they sent, supporting numbers and Approve or Reject); Listings (search, status including Reported, date posted, listing details with reservations and reports; Mark as reviewed, Dismiss report, Remove listing; no editing of a donor's food); Reservations (monitoring only: food, donor, recipient, quantity, date, status, and a timeline from Reservation created to Claimed or Cancelled); Reports (Open, Resolved, Dismissed and All tabs, filters, a review with the report, reporter, target, other reports, admin notes; Dismiss report, Remove listing, Suspend user or donor); Analytics (4, 8 or 12 weeks: seven numbers, listings and reservations over time, completed vs cancelled, food rescued over time, listing status distribution, each with a table view); Notifications (announcement history with Sent, Scheduled and Draft, Create notification with audience, send now or schedule, a preview drawn like the app's Alerts row; the admin's own alerts); Audit log (who did what and when, filters; actions taken in the page are added); Settings (account, change password form, alert switches, moderation and system settings, and the sample-data tools).
- **States.** Skeletons while loading; lists keep their rows, dimmed, while reloading; an empty state for each list and search; a "Couldn't load … Try again" state; success toasts ("User successfully verified.", "Listing removed successfully.", "Notification sent successfully."); errors in the dialog that stays open ("Unable to remove listing. Please try again."). Suspend user, Remove listing and Reject ask for confirmation with a required reason; Restore, Dismiss, Send and Reset ask first too; buttons show a spinner and are disabled while an action runs.
- **Accessibility.** A skip link, one heading per page, labelled controls, visible focus, keyboard use of menus, tabs, panels, dialogs and charts (arrow keys), focus kept inside open panels, dialogs and the drawer and returned after, Escape to close, statuses as words and icons, counts read with their link ("Reports, 7 open reports"), charts with a text summary and a table, reduced motion respected, 44 px targets on phones, text contrast checked (for example grey on white 5.5:1, green on white 5.3:1).

### 20.2 The sample data

`admin/js/sample-data.js` generates, from a fixed seed and relative to the current time: 248 accounts (3 admins, 22 food businesses, 48 household donors, 175 recipients; 5 verification requests, 3 suspended), about 1,600 listings over 13 weeks (126 up now: 104 available, 22 fully reserved), about 3,300 reservations (54 waiting for pickup), 37 reports (7 open), 7 announcements, 6 admin alerts and an audit log. The brief's examples are in it (Maria Santos registered 2 minutes ago; Aling Nena's Kitchen posted bread 8 minutes ago; Juan Dela Cruz reserved 3 items 15 minutes ago). The records use the real tables' values: listing and reservation statuses, `cancel_reason`, handover, units, the app's food categories and report reasons, notification types. Every number on every page is computed from the same records, so the tables, counts and charts agree. People and businesses are invented; emails are on `example.com`.

### 20.3 Checks run (local only)

- `node --check` on every script.
- Headless Chromium (Playwright) at 1440×900, 1280×800, 1024×768, 768×1024 and 390×844, on 20 routes (every page, every kind of details panel): no script errors, no sideways page scrolling, no skeleton left after loading, the panel open on details routes. Bugs found this way and fixed: wide tables and the tab row stretched the page instead of scrolling inside their box; a hidden table label escaped its scroll box; the phone's menu button also showed on desktops; toasts covered the details panel's buttons; long names were cut off in tables; and in the sample, the newest listings had too little history, so two charts dipped in the latest week.
- An interaction run: the phone drawer (opens, takes focus, Escape closes and returns focus, links close it), the tablet overlay, collapsing the desktop sidebar, search and the empty state, Clear filters, suspending a user from the row menu (reason required, spinner, toast, row updated), approving a verification request (count 5 → 4), the Reported filter (4 listings), removing a listing from a report (open reports 7 → 6), failed actions and failed page loads with Try again, Create notification (validation, confirmation, toast, in the list), saving a setting, and all of these in the Audit log; the skip link, keyboard chart tooltips and the table view. All passed after the fixes.
- Screenshots were reviewed at each size. **Not done:** other browsers (Safari, Firefox), a real phone, screen-reader runs (NVDA, VoiceOver, TalkBack).

### 20.4 Test on a computer and a phone

1. Open `admin/index.html` (or serve the folder) on a computer. Overview shows 248 users, 126 active listings, 54 open reservations and 7 pending reports, and the Sample data chip explains the sample.
2. Collapse the sidebar; hover the icons for their names; narrow the window to tablet width (rail, menu over the page) and to phone width (menu button, drawer, cards).
3. Users: search, filter, open a user, suspend (a reason is required) and restore; Verification requests → Review → Approve or Reject.
4. Listings: filter Reported, open one, Dismiss report or Remove listing. Reservations: open one and read the timeline. Reports: review, add a note, dismiss or act.
5. Analytics: switch 4, 8 and 12 weeks; hover a chart, use the arrow keys on it, show its table.
6. Notifications: Create notification, schedule one, save a draft, cancel the scheduled one. Audit log: your actions are at the top. Settings → Sample data: make actions fail and try one; turn it off; reset.
7. On a phone: the same pages; nothing is cut off or scrolls sideways.

### 20.5 Known limits and what connecting it needs

- Sample data only; a reload undoes every change. Nothing reaches the app, the database or a phone.
- No admin sign-in yet, because there is nothing real behind it to protect. When connected, the dashboard should sign in with the same Firebase project and check the caller's own `users.role = 'ADMIN'`.
- Showing real data needs server work that doesn't exist and that this change doesn't add (`admin/README.md` lists it): admin-only `SECURITY DEFINER` functions that check the caller's role from the token, and tables or columns for document verification, suspension, report statuses beyond `OPEN`, admin notes, announcements and an append-only audit log. Never a service-role key in the browser and never wider RLS. Any of it is a Phase 6 decision for the developer, and each migration must be run by hand as usual.
- Every account the app creates has `role = RECIPIENT`; the sample shows donors as `DONOR` so the role filter can be shown. When connected, "donor" should come from posting activity.
- Items rescued add up different units (pieces, packs, meals, boxes, kilograms), as the app's History totals do.
- Listing photos are not shown (the sample has no images; the real ones are private and need signed URLs); a tile with the category icon stands in, as in the app.

## 21. Bug fix pass after the full bug audit (2026-09-29)

**Status (2026-09-29): written on branch `claude/bugfix-pass-1` (from `master` at `fa95fff`, the PR #15 merge); committed as `5429e6e`, pushed, opened as PR #17 into `master` and merged on 2026-09-30 (merge commit `8ed8eef`). Built with AGP (`assembleDebug`) and unit-tested (`testDebugUnitTest`) after each group, last 51 of 51; `lintDebug` 0 errors (none of its 38 warnings is on a changed line). The three migrations passed a local PostgreSQL 16 dry run, and the developer applied all three to the live project on 2026-09-30 (each "Success. No rows returned"). Live safety checks on 2026-09-30: `bugfix_reservations_live_safety_check.sql` 15 of 15 and `bugfix_profile_relink_live_safety_check.sql` 9 of 9 passed; `bugfix_security_live_safety_check.sql` 22 of 23, all security checks passed. Its check 12 failed on one photo: the removed §14 test listing "P5 FREE", whose account was re-linked to a new Firebase UID by hand (§14.6), keeps its photo in the old UID's folder. A read-only query showed the path still has the listing's ID as second folder and the file exists, so the rule still finds it (its donor can read it; checked locally too). The check was stricter than the rule; since 2026-09-30 it fails only for a path without its listing's ID and reports old-folder photos as a note. The phone test (one phone, switching accounts, §21.4) passed on 2026-09-30, except the optional slow checks for #5, #21 and #15, which were not run.** It fixes the approved groups of the full bug audit of 2026-09-29: A (#1, #10, #11), B (#2–#6), C (#8, #9, #23), D (#7, #14, #15, #21) and E (#17–#20, #22). #12, #13, #16 and #24–#34 were not part of this pass.

### 21.1 What changed

- **#1 (critical) cancel and claim without a profile.** `cancel_reservation` and `claim_reservation` refuse a token with no `users` row ("Profile not found") and compare owners with `IS DISTINCT FROM`. Before, a valid Firebase token without a profile passed both owner checks (`x <> NULL` is NULL, which plpgsql's IF treats as false); the local dry run reproduced such an account cancelling someone's reservation before the fix.
- **#10 listing photos.** The `food-images` read policy no longer lets any signed-in token read every object. A photo is readable by its uploader, and otherwise only when it is a photo of a listing the caller may see, saved or chats about (`can_read_food_image`). Signed URLs, Saved Listings covers and Messages thumbnails keep working.
- **#11 server checks on new listings.** A trigger stores the server's time in `created_at`/`updated_at` and refuses a quantity outside 1–10,000, a past expiry or pickup end and a description over 2,000 characters (on edits: a changed description only). Post and Edit show the same limits. The audit was partly wrong here: name, category, unit and location lengths were already constrained.
- **#2 Home refresh.** The feed reloads when Home or All listings is opened and when the app comes back, at most every 30 seconds (`FoodViewModel.refreshFeedIfStale`), besides pull to refresh; the pull spinner shows only for a pull. Search and filters are unchanged.
- **#3/#4 failed loads.** The feed and My Listings each have their own loaded and error state. A failed reload keeps the list and shows "Couldn't refresh … These may be out of date." with Try again; a failed first load shows "Could not load …" with Try again; "No active listings" and the feed's empty states appear only after a successful load.
- **#5 expired photo URLs.** A feed photo that fails to load reloads the feed once for fresh 1-hour URLs, only when the feed is 50+ minutes old (and not within 30 seconds of the last load), so it never loops. The details page shows the feed's latest copy of its listing.
- **#6 posting retries.** Each Post form keeps its listing ID; a retry first looks for a listing already saved with it, uploads with upsert to the same paths, and after a failed insert deletes the photos only when the database refused it or a check finds no listing. When that check fails too, the photos stay and the form says "We couldn't confirm whether your listing was published… it will not be posted twice." The logic is `publishOnce` (`PublishOnceTest`, 6 tests). The profile photo upload got the same look-before-deleting check.
- **#8 price.** Reserve sends the price shown to `create_reservation_at_price`, which refuses a changed price ("The price changed to ₱30.00 per unit. Check the details before reserving.") and otherwise calls `create_reservation`; the feed reloads after a refused reservation, so the review page shows the new price. The recorded price still comes only from the listing.
- **#9 release.** The donor can release an open reservation (Manage reservations or the reservation page → Release reservation → confirm). `release_reservation` gives the quantity back, marks it `DONOR_RELEASED` and alerts the recipient ("Reservation released"); claimed, cancelled and other people's reservations are refused. Nothing expires reservations automatically.
- **#23 pickup end.** `create_reservation` refuses a listing whose pickup time has ended, and the feed leaves those listings out.
- **#7 rotation and recreation.** The signed-in profile is saved with the screen state, so rotation, dark mode, font size and process recreation keep Home instead of repeating the setup (which ended on Sign In when offline). Rotation no longer empties and reloads the ViewModels.
- **#14 alerts on resume.** Alerts reload quietly when the app comes back, merged with any that arrived live (no duplicates, no error dialog).
- **#15 re-created account.** A profile-creation failure on the unique email offers "Restore your profile?"; `relink_my_profile` moves the earlier profile to the new Firebase UID only with a verified email in Firebase's token (an unverified account is sent the verification email first), and only after the user taps Restore profile.
- **#21 session ended.** When Firebase says the account is disabled or deleted or its sign-in revoked (`FirebaseAuthInvalidUserException` while getting the token), the app signs out and returns to Sign In with "Your session has ended. Please sign in again." A network failure never signs out.
- **#17 reservation lists.** A failed first load shows "Could not load your reservations" with Try again instead of "No reservations"; History totals and the listings' reservation counts appear only after the reservations loaded; until then a fully reserved listing counts as active.
- **#18 typed results.** Cancel, claim and release report `ReservationAction` values instead of message strings; Activity no longer matches "Reservation cancelled…".
- **#19 posting result.** The result stays in `FoodViewModel`, tagged with the form's ID: the form shows its own result even after tab switches or rotation, Home shows "Listing published" / "Listing not published" when Post isn't open, and a new form never shows an old result.
- **#20 filters after posting.** "Back to home" after posting clears Home's search and filter, as Back already did.
- **#22 details.** The details page shows Removed, Expired or Reserved and a note ("The pickup time for this listing has ended.", "Fully reserved: nothing is left to reserve." …) instead of Reserve when a reservation would be refused (`reserveBlocker`, `ReserveBlockerTest`, 2 tests).

### 21.2 Database (run by hand, in this order, before installing the new build)

1. `supabase/migrations/20261005_bugfix_security_integrity.sql`, then `supabase/tests/bugfix_security_live_safety_check.sql` (expect 23 passed).
2. `supabase/migrations/20261006_bugfix_reservations.sql`, then `supabase/tests/bugfix_reservations_live_safety_check.sql` (expect 15 passed).
3. `supabase/migrations/20261007_bugfix_profile_relink.sql`, then `supabase/tests/bugfix_profile_relink_live_safety_check.sql` (expect 9 passed).

Details are in `supabase/README.md`. Each file is safe to run again, and older app builds keep working.

### 21.3 Checks run (local only)

- A local PostgreSQL 16.4 (portable binaries in the session's scratch folder, with stand-ins for Supabase's roles, `auth.jwt()` and the storage schema): the 13 earlier migrations applied, then the three new ones, each twice. Before its migration each new check failed as expected (group A: 4 passed, 19 failed); after it, 23/23, 15/15 and 9/9. The earlier checks still pass after all three: `phase3_live_safety_check` 14/14, `phase5_maps_live_safety_check` 22/22, `phase3_demo_fixes_check` 13/13, `phase4_demo_fixes_check` 9/9, `phase5_reviews_reports_check` 18/18, `phase5_maps_check` 11/11, `profile_bio_check` 6/6. Not run locally: the Phase 4 and Phase 5 part 1 live checks (they need chats and claimed reservations the sample lacked).
- `assembleDebug` and `testDebugUnitTest` after each group: 43 of 43, then 49 of 49 (with `PublishOnceTest`), then 51 of 51 (with `ReserveBlockerTest`). `lintDebug`: 0 errors.

### 21.4 Phone test

Run on one phone, switching accounts (the developer had one phone), with the migrations applied.

**Part 1, 2026-09-30: all 10 steps passed.** A: filters cleared after posting ("Test rice", ₱25, 3 packs) and the listing with its photo on Home and in My Listings; a normal Log out showed no "session ended" message. B: reserved through `create_reservation_at_price` (receipt at ₱25.00, 2 left), cancelled ("Reservation cancelled. The quantity is available again.", 3 left), reserved again. A: "New reservation" and "Reservation cancelled" alerts; released B's reservation ("Reservation released. The quantity is available again, and the recipient was told."). B: the "Reservation released" alert and its note, 3 packs back; reserved again. A: Mark as picked up. B: History → Completed.

Not testable on one phone: Home refreshing by itself when another account posts (#2), and the app's "price changed" message (#8); the price refusal passed in the live safety check.

**Part 2, 2026-09-30: reported as "all passed".** The first step 11 (open a fully reserved listing from Saved Listings) could not be done: Saved Listings opens only listings that can still be reserved and shows "No longer available" with Remove for the others, as since §11/§12; not a bug. It was replaced by: A posts "Test pickup" with pickup ending a few minutes later; B stays on Home past the end, then the card opens with an Expired badge and "The pickup time for this listing has ended." instead of Reserve; after pulling down the listing is gone from Home; Saved Listings → View listing shows the same note. Then 12 returning to the app; 13 rotation and dark mode in airplane mode (stays on Home); 14 a failed refresh keeps the lists with "Couldn't refresh …" notes, Try again clears them; 15 posting in airplane mode fails, posting again online publishes once; 16 leaving Post while posting shows "Listing published" on Home, once in My Listings; 17 Delete ("Listing deleted.") and removing Test rice with an open reservation.

Not tested (optional, slow): photo links older than an hour (#5), a session ended by disabling the account (#21), restoring a re-created account's profile (#15).

### 21.5 Known limits

- Nothing is live across phones except chats and alerts: the feed updates when Home opens, on resume (30-second minimum) and on pull.
- A post whose answer was lost keeps its photos until a retry from the same form; a form discarded after such a failure can leave photos in Storage (readable only by the uploader).
- After a profile is restored (#15), photos stay in the old Firebase UID's folders: listing photos still show, the old profile photo does not (upload a new one), and deleting a never-reserved old listing leaves its photos in Storage.
- The price check (#8) and Release (#9) need the new build; older builds still reserve through `create_reservation` without the price check.
- #21 is noticed when the Firebase token is next refreshed (up to an hour after the account is disabled or deleted).

## 22. Bug fix pass, round 2 (2026-09-30)

**Status (2026-09-30): written on branch `claude/bugfix-pass-2` (from `claude/bugfix-pass-1` at `5429e6e`, §21, merged through PR #17); committed as `a2412eb`, pushed, and opened as PR #18 into `master` on 2026-09-30 (not merged yet). `assembleDebug` passed and `testDebugUnitTest` passed 54 of 54 (forced to run fresh); a forced `compileDebugKotlin` showed no warnings; `lintDebug` 0 errors (38 warnings, none on a changed line); `assembleRelease` built an unsigned release APK (with no `keystore.properties`; it needed one online run to download a release-only dependency). The three new migrations passed a local PostgreSQL 16 dry run, including all 18 migrations applied in order to an empty database. On 2026-09-30 the developer applied all three to the live project (each "Success. No rows returned"), their live checks passed (§22.4), and the one-phone test passed (§22.5).** Every finding of the 2026-09-29 audit was reviewed again against the code before changing anything.

### 22.1 The 34 findings

| Bug | Status after round 2 | What happened |
|---|---|---|
| #1 | FIXED (§21) | Cancel/claim refuse a caller without a profile; live check passed. |
| #2 | FIXED (§21); two-phone check open | Home reloads on open and resume (30 s minimum). Seeing another account's post appear by itself needs two phones. |
| #3 | FIXED (§21) | A failed reload keeps the list with a note and Try again. |
| #4 | FIXED (§21) | Loading, failed, empty and loaded are separate states. |
| #5 | FIXED (§21); slow check open | Expired photo links are renewed once; the 1-hour check wasn't run. Round 2 adds stable cache keys (#16). |
| #6 | FIXED (§21) | Posting is idempotent; `PublishOnceTest`. |
| #7 | FIXED (§21) | The profile survives recreation. |
| #8 | FIXED (§21); app message needs two phones | The server refuses a changed price (live check). |
| #9 | FIXED (§21); limit documented | Donor release, refused for claimed/cancelled/others' reservations, quantity back atomically, recipient alerted. Nothing expires reservations automatically: the project has no scheduler (pg_cron is not set up), and a phone-side timer would be unreliable. An expired listing isn't revived by a release or cancel (the feed and SELECT policy go by the expiry date). A claim after expiry stays allowed on purpose: a handover before expiry is often recorded later. |
| #10 | FIXED (§21) | Photos readable only by the uploader or people who may see, saved or chat about the listing; bucket private; signed URLs unchanged. Left: the uploader's Firebase UID is in the path of photos others may see. |
| #11 | FIXED (§21); reviewed again | Server time for `created_at`/`updated_at`, quantity 1–10,000, future expiry and pickup end, description ≤ 2,000; status forced to `AVAILABLE` by the insert policy; name, category, unit, location, price, pickup window, handover, allergens, storage and photo paths by constraints and the policy. Not added: allow-lists for category and unit (older builds and old rows use other values; they are length-limited display text) and a lower bound for `pickup_start` (a past start only widens the window). |
| #12 | FIXED (round 2) | Ratings and review lists count each reviewer's latest review of a donor (`20261008`). Limit: a friendly second account still adds one rating. |
| #13 | FIXED (round 2) | Chat and Alerts use `shortErrorText` (the server's short text, never the URL or headers); Google sign-in no longer shows "Details: …" (it goes to logcat); Firebase network and too-many-requests errors have their own texts. |
| #14 | FIXED (§21) | Alerts reload quietly on resume, merged with live ones. |
| #15 | FIXED (§21); optional check open | Confirmed re-link with a verified email only; live check 9/9. |
| #16 | PARTLY FIXED (round 2) | Photos are cached by their Storage path, not the whole signed URL, so a new token no longer downloads them again. Cards still download the stored 1600 px file once (Coil decodes it at card size): thumbnails need Supabase image transformations (a paid plan) or a second upload per photo. |
| #17 | FIXED (§21) | A failed reservation load is never "0 reservations" or "Closed". |
| #18 | FIXED (§21) | Typed `ReservationAction`; no string matching. |
| #19 | FIXED (§21) | Post results are kept per form (`draftId`). |
| #20 | FIXED (§21) | "Back to home" after posting clears search and filter. |
| #21 | FIXED (§21); optional check open | Invalid account → Sign In with a message; network failure → no sign-out. |
| #22 | FIXED (§21) | The details page shows why a listing can't be reserved. |
| #23 | FIXED (§21) | Server refuses after `pickup_end`; the feed hides such listings. |
| #24 | PARTLY FIXED (round 2) | Buckets accept only JPEG, PNG and WebP up to 5 MB (`20261009`). Orphaned files are listed by the read-only `supabase/tests/storage_orphans_query.sql`, not deleted automatically (a wrong automatic delete would lose real photos). |
| #25 | FIXED (round 2) | 20 messages per minute, 10 reservations per 10 minutes, per person, counted under an advisory lock (`20261009`). Chats are already one per listing and person; cancels are bounded by reservations. |
| #26 | FIXED (round 2) | Bookmark changes made while the first load runs win over its older answer (`SavedListingViewModel`). |
| #27 | FIXED (round 2) | Photos are sampled on the longer side before decoding (4032×3024 decodes at 2016×1512, not 12 MP); `PhotoSampleTest`. Final quality unchanged (still 1600 px / 512 px). |
| #28 | DEFERRED (documented) | No pagination: at the current scale a feed of all available listings is small; PostgREST would cap it at 1000 rows (dropping the oldest). The unfiltered `messages` Realtime channel is checked by RLS per row; narrowing it needs a per-user channel design. Neither is a current problem for a thesis-sized user base. |
| #29 | PARTLY FIXED (round 2) | The profile ID is looked up once per account (`UserRepository.getCurrentProfileId()`), saving a `users` request on most loads; Activity tab taps reload at most every 10 s. Not changed: Home re-derives its filtered list on recomposition (memoizing it would risk stale expiry labels for little gain). |
| #30 | FIXED (round 2) | Backup rules exclude only Firebase Auth's saved sign-in (its exact preferences file); the app's own preferences are still backed up. |
| #31 | PARTLY FIXED (round 2) | The missing `proguard-rules.pro` reference is gone (keep rules live in `src/main/keepRules/`); optional release signing from a git-ignored `keystore.properties`; no key created or committed. Still needed for Play: R8 with keep rules and a tested release build, a real keystore, an `applicationId` other than `com.example.*` (Play refuses it; changing it means a new Firebase Android app, `google-services.json` and SHA-1s), in-app account deletion plus a web deletion link (Play policy for apps with accounts), the Data safety form and privacy policy in Play Console. |
| #32 | FIXED (round 2) | No function in `public` is executable by `anon`; signed-in access unchanged (`20261010`, read-only check 3/3). |
| #33 | FIXED | CLAUDE.md, README.md and this document describe the current code. |
| #34 | PARTLY FIXED (round 2) | Free/For sale filters and `HomeFood.free` use the price, not the text "Free"; the details page uses the listing's quantity and unit instead of parsing "3 packs"; Activity's `listingStatus` reuses Post's. §21 already typed the reservation results and kept `cancel_reason`. Left: `users.role` always `RECIPIENT` (a Phase 6 decision), an unknown server enum value breaks a whole list in older builds (lenient decoding would need a decoder change in every model), persisted photo-picker grants aren't released, Post's "My posts" duplicates Activity. |

### 22.2 What changed (code)

- `viewmodel/MessageViewModel.kt`, `viewmodel/NotificationViewModel.kt`: errors through `shortErrorText` (#13).
- `MainActivity.kt`: Google sign-in errors without technical details, Firebase network/too-many-requests texts (#13); after the phone test, a Google sign-in that ends as "cancelled" with no working internet (`hasInternet()`, `ACCESS_NETWORK_STATE` now declared in the manifest) shows the same "Could not reach the server…" text instead of nothing (#13); Free/For sale from the price, details quantity and unit from the listing (#34).
- `ListingPhotos.kt`: `signedImageRequest()` caches by Storage path; used by `ListingPhoto`, the `Avatar` in `SaluTheme.kt` and Saved Listings' fallback cover in `ProfileSections.kt` (#16).
- `viewmodel/SavedListingViewModel.kt`: `localChanges` wins over an older load (#26).
- `data/ProfilePhoto.kt`: `photoSampleSize()` on the longer side, the larger decode freed early (#27); `PhotoSampleTest` (3 tests).
- `data/UserRepository.kt`: `getCurrentProfileId()`; used by `FoodRepository`, `ReservationRepository`, `SavedListingRepository`, `NotificationRepository` (#29).
- `viewmodel/ReservationViewModel.kt`, `ActivityScreen.kt`: `refreshIfStale()` for tab taps (#29); Activity's `listingStatus` (#34).
- `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml` (#30).
- `app/build.gradle.kts`, `.gitignore` (#31).

### 22.3 Database (run by hand, in this order; any build keeps working)

1. `supabase/migrations/20261008_bugfix_one_rating_per_reviewer.sql`, then `supabase/tests/bugfix_ratings_live_safety_check.sql` (expect 5 passed; needs three accounts).
2. `supabase/migrations/20261009_bugfix_storage_and_rate_limits.sql`, then `supabase/tests/bugfix_limits_live_safety_check.sql` (expect 5 passed).
3. `supabase/migrations/20261010_bugfix_revoke_anon_execute.sql`, then `supabase/tests/bugfix_anon_grants_check.sql` (read-only; 3 rows, all PASS). Running the earlier live checks again afterwards is a good regression test.

Optional: `supabase/tests/storage_orphans_query.sql` (read-only) lists unused Storage files to review.

### 22.4 Checks run

- Live project, 2026-09-30, after the three migrations: `bugfix_ratings_live_safety_check.sql` 5 passed, 0 failed, 0 skipped (4.0 stars from 2 reviewers; both of the recipient's reviews kept; `anon` refused); `bugfix_limits_live_safety_check.sql` 5 passed, 0 failed, 0 skipped (both buckets set; the 21st message and the 11th reservation refused with the new texts; the donor could still reply); `bugfix_anon_grants_check.sql` 3 of 3 PASS (34 functions checked, none missing, none open).
- Local PostgreSQL 16.4 with Supabase stand-ins: each new check failed before its migration (ratings: 3.0 stars from 3 reviews instead of 4.0 from 2; limits: no bucket limits, a 21st message and an 11th reservation allowed; grants: `anon` could run `set_updated_at`, `is_salusalo_firebase_jwt`, `is_reservation_recipient`, the Phase 4 RPCs and others) and passed after it (5/5, 5/5, 3/3). Each migration ran twice. After all three, every earlier check still passed: Phase 3 live 12 passed, 2 skipped (the sample's outsider already had a reservation and chat on the listing it picks); Phase 4 live 20 passed, 1 skipped; Phase 5 live 27/27 (with a sample picked-up reservation); Maps live 22/22; the §21 checks 23/23, 15/15, 9/9; read-only checks 13/13, 9/9, 18/18, 11/11, 6/6. All 18 migrations also applied in order to an empty database, with the same results.
- `assembleDebug` after each group; `testDebugUnitTest` 54 of 54 (51 plus `PhotoSampleTest`), the last run forced with `--rerun`; a forced `compileDebugKotlin` had no warnings.

### 22.5 Phone test

**Passed on 2026-09-30** on one phone, switching between Account A (PASTOR, donor) and Account B (Mika Suarez), after the three migrations:

- Part 1 (steps 1–13): sign-in and Home with photos; a pull to refresh keeps the photos shown without fading in again (#16); a listing posted with a camera photo and a new profile photo (#24, #27); in airplane mode a chat message shows "Could not complete the message request. Check your connection and try again." and Alerts "Could not load alerts. Check your connection and try again." (#13); email/password sign-in in airplane mode shows "Could not reach the server. Check your internet connection and try again." (#13); a bookmark tapped as soon as Home shows stays and appears in Saved Listings (#26); Reserve, a chat message, quick Activity tab taps without errors (#29), Cancel, Reserve again.
- Part 2 (steps 14–19): the alerts and the chat on A, a reply, Mark as picked up, B's 5-star review, the review shown once on the donor page (#12), the password-reset request text, A's Ratings & Reviews, log out and in.
- Also seen: Google sign-in with a Google account whose Firebase account had been deleted showed "Restore your profile?" (#15; what happened after the dialog was not reported).
- Found during step 7: Google sign-in in airplane mode showed nothing (the picker's offline failure arrives as a cancelled request, which the app ignored; older than round 2). Fixed afterwards in `MainActivity.kt` (§22.2); built and unit-tested (54 of 54), and a 3-step phone check passed on 2026-09-30: Google sign-in in airplane mode shows "Could not reach the server. Check your internet connection and try again."; closing the picker with Back while online shows nothing; Google sign-in online opens Home.

Not covered on the phone: the rate-limit messages and one person counting once (covered by the live checks, §22.4); the optional sign-up of a new account (not reported separately); after a backup restore or phone transfer the app asks to sign in again (needs a second device or a reset); release signing and the Play checks (need Play Console and a real keystore).

### 22.6 Known limits of round 2

- #12: a second account still counts once. #16: cards download the full stored photo once per phone. #24: orphaned files are only listed. #28: no pagination, unfiltered chat channel. #29: Home recomputes its list on recomposition. #31: not Play-ready (R8, keystore, package name, account deletion, Play Console forms). #34: `users.role`, lenient enum decoding, photo-picker grants, Post's "My posts".
- The rate limits count real recent activity, so a person reserving 10 times in 10 minutes (for example buying for a group) waits a few minutes for the 11th.
- The backup rule names Firebase's file for this Firebase Android app; if the app ID in `google-services.json` changes, update both XML files.

## 23. Bug fix pass, round 3 (2026-09-30)

**Status (2026-09-30): written on branch `claude/bugfix-pass-3` (from `master` at `65d5f6a`, which has §21 and §22). `assembleDebug` passed, `testDebugUnitTest` passed 57 of 57 (54 before, plus 3 new), a forced `compileDebugKotlin` showed no warnings and `lintDebug` found 0 errors. No SQL: nothing needs to be run on Supabase. The one-phone test (§23.4) passed on 2026-10-03; committed on the branch for its pull request.** It follows the read-only verification audit of 2026-09-30, which checked each of the 34 findings again against `65d5f6a`: 16 fully fixed, 17 partly fixed, #28 not fixed, one regression caused by a fix (R1) and one new bug (#35). Round 3 fixes what can be fixed in the app without changing working parts; the rest needs a decision or work outside the app code (§23.3).

### 23.1 Every finding after round 3

| Bug | Status after round 3 | What happened |
|---|---|---|
| #1 | FIXED (§21) | Unchanged. |
| #2 | FIXED (round 3); two-phone check open | Besides Home opening, the app coming back and a pull, the feed now reloads every minute while Home or All listings is on screen and the app is in front (through `refreshFeedIfStale`, so never more than once per 30 seconds). |
| #3 | FIXED (§21) | Unchanged. |
| #4 | FIXED (round 3) | Post's "My posts" had no load states: it said "My posts (0)" and "You have no food posts yet." while loading or after a failed load. It now shows "Loading your posts…", or the failure with Try again, and the count only after a load. |
| #5 | FIXED (§21); 1-hour check open | Unchanged. |
| #6 | FIXED (§21) | Unchanged. |
| #7 | FIXED (round 3) | The open food details page (and its Review reservation page) closed on rotation, dark mode or a font change: it was held in `remember`. It is now held by `FoodViewModel.openedListing`. Activity's Edit Listing and Rate pages and Post's Edit Listing now use `rememberSaveable` (an ID, or the Rate page as JSON). The small confirm dialogs (cancel, release, mark as picked up, delete) still close; one tap reopens them. |
| #8 | FIXED (§21); two-phone message check open | Unchanged. |
| #9 | PARTLY (release, §21) | No automatic expiry and no per-person cap: both need decisions (§23.3). |
| #10 | PARTLY (reads, §21) | Photo paths still start with the donor's Firebase UID (§23.3). |
| #11 | PARTLY (§21) | Any category or unit text and any future expiry are still accepted on insert (§23.3). |
| #12 | PARTLY (§22) | A friendly second account still adds one rating (Phase 6). |
| #13 | FIXED (§22) | Its regression R1 is fixed in round 3. |
| #14 | FIXED (round 3); two-phone check open | Alerts inserted while Realtime was reconnecting (app open, connection lost for a moment) never arrived. supabase-kt rejoins its channels after a reconnect but doesn't replay rows (checked in its 3.5.0 sources), so `realtimeInserts()` now reports each rejoin, and the alerts, the Messages inbox and the open chat reload quietly then (`RealtimeRejoinTest`). |
| #15 | FIXED (§21); end-to-end restore not reported | Unchanged. |
| #16 | PARTLY (§22) | Photos are cached by path; no thumbnails. The claim that listing photos "can't be replaced" was corrected: the app never replaces one, but the Storage policy would let a donor overwrite their own file through the API. |
| #17 | FIXED (round 3) | "Your listing" said "No reservations yet" and "Reserved 0 …" and hid Manage reservations when the reservations had not loaded; Manage reservations said "No active reservations". Both now show the load failure with Try again (or "Loading reservations…") and no count until the reservations have loaded. |
| #18 | FIXED (round 3) | The feed reload after a cancel or release ran in Activity's own effect, so leaving Activity before the result skipped it. `ReservationViewModel` now signals `quantityReturned`, and Home reloads the feed whichever page is open. |
| #19, #20 | FIXED (§21) | Unchanged. |
| #21 | FIXED (§21); disabled-account check open | Unchanged. |
| #22 | FIXED (round 3) | The details page fell back to the copy it opened with when the listing left the feed (fully reserved, removed or ended since), so Reserve stayed. It now shows "Unavailable" and "This listing is no longer available to reserve.". It also re-checks by itself when the expiry or the end of pickup passes while it is open, and when the app comes back (`nextReserveBlockerChange()`, `ReserveBlockerTest`). |
| #23 | FIXED (§21) | Unchanged. |
| #24 | PARTLY (§22) | Bucket limits live; orphaned files are only listed (§23.3). |
| #25–#27 | FIXED (§22) | Unchanged. |
| #28 | DEFERRED | Unchanged (§23.3). |
| #29 | MOSTLY FIXED (round 3) | Home's derived lists are `remember`ed with every value they read as a key, so an alert, a message or a badge no longer rebuilds them; Profile reloads the reservations at most every 10 seconds (`refreshIfStale`). Activity still reloads its lists every time it opens, on purpose: an alert that opens Activity needs fresh lists. |
| #30 | FIXED (§22); restore check open | Unchanged. |
| #31 | PARTLY (§22) | Needs the developer's keystore, package name, Firebase release SHA-1 and Play Console work, and an account deletion feature (§23.3). |
| #32 | FIXED (§22) | Unchanged. |
| #33 | FIXED (round 3) | The docs recorded PR #18 as not merged, and made the photo-replacement claim (#16); both corrected. |
| #34 | PARTLY (round 3) | Photo-picker grants are now released when a photo is removed from the draft (not while posting) and after the listing is published. Left: strict enum decoding (alerts lenient since §27), `users.role`, Post's "My posts" list (§23.3). |
| #35 | FIXED (round 3) | New in the verification audit: rotating right after tapping Sign in or Create account (or during Google sign-in) left the user on Sign In while signed in to Firebase, because the sign-in finished in the discarded screen and the new screen's `LaunchedEffect` read Firebase's user, which isn't Compose state. `SaluSaloApp` now keeps the user's UID as state (set by its `AuthStateListener`), the typed name is saved before Firebase answers, and `openAuthenticatedHome` runs one setup at a time, so the callback and the effect can't start two. |
| R1 | FIXED (round 3) | Since §22 a failure loading or saving the alert settings said "Could not load alerts…". It now says "Could not load your alert settings…" or "Could not save your alert settings…". |

### 23.2 What changed (code)

- `MainActivity.kt`: `authUid` state, the setup guard and the name saved at tap time (#35); the details page from `FoodViewModel.openedListing`, with `noLongerListed` and a re-check at the next expiry or pickup end (#7, #22); the feed reload every minute while Home or All listings is shown (#2); the feed reload on `quantityReturned` (#18); `remember`ed Home lists (#29).
- `viewmodel/FoodViewModel.kt`: `OpenedListing`, `openedListing`, `openListing()`, `closeListing()`; cleared with the session.
- `viewmodel/ReservationViewModel.kt`: `quantityReturned` (a conflated channel, so a signal waits through a rotation) after a cancel or release.
- `ActivityScreen.kt`: the feed reload removed from the result effect (#18); the Edit Listing and Rate pages saved (#7); `ListingDetailsPage` and `ManageReservationsPage` take whether the reservations are known and failed (#17).
- `PostScreen.kt`: "My posts" load states (#4); Edit Listing saved by ID (#7); photo-picker grants released (#34).
- `ProfileScreen.kt`: `refreshIfStale()` (#29).
- `viewmodel/NotificationViewModel.kt`: the alert-settings error texts (R1); a quiet reload after a Realtime rejoin (#14).
- `viewmodel/MessageViewModel.kt`, `data/MessageRepository.kt`, `data/NotificationRepository.kt`, `data/RealtimeInserts.kt`: the rejoin reloads (#14).
- `model/FoodPost.kt`: `nextReserveBlockerChange()` (#22).
- Tests: `RealtimeRejoinTest` (2), one more `ReserveBlockerTest`.
- The `ListingPhotos.kt` comment, `CLAUDE.md`, `README.md` and this file (#33).

### 23.3 Not changed, and why

- **#9:** automatic expiry needs a server scheduler (for example `pg_cron`) and a rule for what an "expired" reservation means (the food may still be picked up late); a per-person cap contradicts "reserve part or all of the quantity". Both are product decisions.
- **#10:** paths without the Firebase UID would change the upload paths, the Storage insert, update and delete rules, the `food_posts` insert policy and `can_read_food_image`, and old listings would keep their old paths. A Firebase UID is an identifier, not a credential.
- **#11:** a database allow-list for category and unit would also refuse the test listings that 5 live check scripts insert ('Donation', 'Food', 'pack'), and would tie every new category to a migration; a free-text category can't do anything the free-text name and description can't already do. The app itself allows any future expiry (dry and canned food can last years). Needs the developer's rules first.
- **#12:** needs identity checks (Phase 6).
- **#16:** thumbnails need Supabase image transformations (a paid plan) or a second upload per photo, which changes the posting flow fixed in §21 (#6).
- **#24:** deleting orphans needs the Storage API with a service key (never in the app), or a sweep in the app that could delete a draft's photos while it is being posted.
- **#28:** paging would break Nearby, Nearest and the filters, which work on the whole list; a filtered chat channel would miss new conversations. No problem at the current size.
- **#31:** needs the developer's keystore, a new package name registered in Firebase (a new `google-services.json`), the release SHA-1 and Play Console; account deletion is a feature of its own.
- **#34:** lenient enum decoding needs a decision about how an unknown status or alert type should look; `users.role` is kept for Phase 6's admin role; removing Post's "My posts" would change the UI.

### 23.4 Phone test (one phone; A = PASTOR, the donor; B = Mika Suarez)

Auto-rotate on. "Rotate" means turn to landscape and back. Nothing needs to be run on Supabase first.

**Account A**
1. (Regression) Open the app (saved session). The loading screen, then Home with "Good …, <A's first name> 👋".
2. (New, #7) Open any listing and rotate. The details page stays open on the same listing.
3. (New, #7) Activity → My Listings → ⋮ on a listing → Edit listing. Change the description (don't save) and rotate. Edit Listing stays open with your change. Leave with the back arrow.
4. (New, #4) Turn on airplane mode. Open Post and tap "My posts". Your posts are listed with "Couldn't refresh your posts. These may be out of date." and Try again. Turn airplane mode off and tap Try again: the note goes away.
5. (Regression, #34) Post a free listing "Round 3 test": 5 packs, two photos (then remove one with its ×), pickup start "Start now", pickup end about 6 minutes from now, expiry later today. "Listing published". Note the time the pickup ends.
6. (New, #35) Profile → Log out. On Sign In enter B's email and password, tap Sign in and rotate at once. The loading screen, then Home as B. (Before: it stayed on Sign In.)

**Account B**
7. (New, #22 and #2) Open "Round 3 test"; Reserve is shown. Keep the page open, with the screen on, until the pickup end passes. Without tapping anything, Reserve changes to "The pickup time for this listing has ended." and the badge to Expired; within about a minute more the badge says "Unavailable" and the text "This listing is no longer available to reserve.". Close it: "Round 3 test" is no longer on Home, without a pull.
8. (New, #18) Reserve 1 of another listing of A's, and note its quantity on Home. Activity → My Reservations → open it → Cancel reservation → confirm, then tap Home at once. Within a few seconds, without a pull, the listing shows the quantity it had before you reserved. Back on Activity: "Reservation cancelled. The quantity is available again.". Then reserve 1 of it again and keep that reservation.
9. (New, #7) Activity → History → a completed pickup → "Change your review". Tap a different number of stars and rotate. The Rate page stays open with your stars. Leave without sending.
10. (New, R1) Profile → Notifications; wait until the switches show. Turn on airplane mode, change a switch and tap Save: "Could not save your alert settings. Check your connection and try again.". Turn airplane mode off and Save again: saved. Put the switch back and save.
11. Profile → Log out.

**Account A**
12. (Regression) Sign in as A normally (no rotation): Home opens.
13. (New, #17) Open Post once (it loads your listings), then turn on airplane mode and open Activity. My Listings shows your listings with "Couldn't refresh your listings. These may be out of date." and "Your reservations could not be loaded. Check your internet connection and try again.", each with Try again. Tap View listing on the listing B reserved in step 8. The page shows the same note with Try again, "Reserved —", no reservations chip and no Manage reservations button (before: "No reservations yet" and "Reserved 0 …"). Turn airplane mode off and tap Try again on the page: "1 active reservation", "Reserved 1 …" and Manage reservations appear.
14. (Regression, #18) Manage reservations → B's reservation → Release reservation → confirm, then tap Home at once: the listing's quantity is back without a pull. Back on Activity: "Reservation released. The quantity is available again, and the recipient was told.".
15. (Regression) Pull to refresh on Home; open a chat and send a message; open Alerts. Log out and sign in again.

Optional: create a new test account and rotate right after tapping Create account. Home opens with the typed name in the greeting (#35).

Not covered on one phone (both need two phones): #14, an alert sent by the other account while this phone's connection drops for a moment appears when the connection comes back, without leaving the screen; and #2 with a new listing posted from another phone.

### 23.4a Result (2026-10-03)

The developer ran §23.4 steps 1–15 on one phone, plus five regression steps added on 2026-10-02: deleting "Round 3 test" ("Listing deleted."); B seeing A's "Round 3 check" message with the unread count and reserving 1; A's "New reservation" alert and Mark as picked up ("Marked as picked up. You'll find it under Completed."); B's "Reservation claimed" alert and the reservation under History → Completed. **All passed.** Before the test, on 2026-10-02, `testDebugUnitTest` (forced re-run) passed 57 of 57 and `assembleDebug` passed on the same working tree. Not covered: the optional new-account step (not reported).

**Two-phone checks (2026-10-03), all passed:** #2, a listing posted on phone 2 appeared on phone 1's Home within about a minute without a pull; #14, a message alert sent while phone 1 was in airplane mode appeared on its Alerts page within about 30 seconds of reconnecting, without leaving the page; #8, a price changed from ₱20.00 to ₱25.00 while phone 1 was on Review reservation was refused with "The price changed to ₱25.00 per unit. Check the details before reserving." and nothing was reserved. Both test listings were deleted.

### 23.5 Known limits of round 3

- #7: after Android ends the app in the background (not a rotation) the details page closes; the confirm dialogs close on rotation.
- #22: a listing opened from Saved Listings that the feed did not list at that moment keeps the saved copy's status until Saved Listings reloads.
- #2: the feed reloads every minute only while it is on screen and the app is in front; each reload is one listing request, one donor-profile request and one signing request.
- #14: a rejoin is noticed from the channel's status; if a reconnect were too fast for the app to see the status change, the next return to the app still reloads.
- #35: if a sign-in fails while the screen is being rebuilt, its error message is lost (the form shows no message); signing in again works.

## 24. Reservation limits and pickup deadlines (2026-10-03)

### 24.0 Status

**Built on branch `claude/reservation-limits` (from `master` at `10270e7`, the PR #19 merge) on 2026-10-03: `assembleDebug` passed, `testDebugUnitTest` passed 68 of 68 (57 before, plus 11 in `ReservationLimitsTest`; `GeoTest` also checks that a new listing always sends `max_per_recipient`), `lintDebug` 0 errors (39 warnings, as before). On 2026-10-03 the developer applied `20261011_reservation_limits_and_expiry.sql` to the live project (without the optional trial run): `cron.schedule` returned job 1; `reservation_limits_check.sql` 9 of 9 true; the job `salusalo-expire-reservations` succeeded at 06:40 and 06:41 UTC; `reservation_limits_live_safety_check.sql` passed 20 of 20 (donor PASTOR, recipient Mika Suarez). The 13-step two-phone test (§24.5) passed on 2026-10-03. Committed as `cc235a1` and merged into `master` through PR #20 on 2026-10-03 (merge commit `a3dcd98`).** Design: `docs/superpowers/specs/2026-10-02-reservation-limits-design.md`; plan: `docs/superpowers/plans/2026-10-03-reservation-limits.md`.

It rebuilds the idea of Mika's PR #16, which could not be merged (based on `f27ee47`, 7 conflicts, its expiry migration in no branch, its `create_reservation` without the rate limit and pickup-end check). A read-only check on 2026-10-02 found none of PR #16's SQL on the live project. It addresses audit finding #9.

### 24.1 What it does

| | |
|---|---|
| Per-person limit | Optional "Maximum per person" on Post (1 to the quantity; blank = no limit). The person's open and picked-up quantity on the listing counts; cancelled and expired don't. It can't be changed later; Edit Listing shows it. Older listings have none. |
| Pickup time | On Review reservation the recipient picks 15 min, 30 min (first), 1 hour or 2 hours. The clock starts at the later of now and the pickup start, and the deadline never passes the end of pickup (or the expiry, for listings without a pickup window). |
| Expiry | A Supabase Cron job runs every minute: an open reservation past its deadline becomes `CANCELLED` with reason `PICKUP_EXPIRED`, its quantity goes back on the listing (a removed listing stays removed), and both people get a "Reservation expired" alert. Mark as picked up is refused after the deadline. |
| Activity | Open reservations show "Pick up by 5:30 PM · 12 min left" (red under 10 minutes; "pickup starts …" before it starts; "Time's up" after), updated once a minute; Activity reloads once about 70 seconds after a deadline it shows. Expired reservations have an "Expired" badge and their own note. |

### 24.2 Database (`20261011_reservation_limits_and_expiry.sql`)

- `food_posts.max_per_recipient` (check: NULL or 1–10,000; at most the quantity, on insert only, in `food_posts_check_values()`).
- `reservations.pickup_deadline`, a partial index for the job, and `PICKUP_EXPIRED` added to `reservations_cancel_reason_check`.
- `create_reservation`: the 20261009 version plus the limit check (before the rate limit). `create_reservation_at_price` unchanged.
- `create_reservation_with_hold(listing, quantity, price, minutes)`: authenticated only; the price check and every reservation check, then the deadline.
- `claim_reservation`: refuses after the deadline ("The pickup time for this reservation has passed.").
- `expire_overdue_reservations()`: no client may run it; up to 500 per run, `skip locked`.
- `notify_reservation_change()`: the expiry alerts (type `RESERVATION_CANCELLED`).
- `get_donor_reservations()`: also returns `pickup_deadline`.
- `pg_cron` turned on and the job `salusalo-expire-reservations` (`* * * * *`) scheduled.
- Checks: `supabase/tests/reservation_limits_live_safety_check.sql` (20 checks, always rolls back) and `supabase/tests/reservation_limits_check.sql` (read-only).

Older builds keep working: they reserve without a deadline, the limit applies to them, and an expired reservation looks to them like an ordinary cancelled one. Existing open reservations never expire.

### 24.3 App changes

- `model/FoodPost.kt`: `maxPerRecipient` on `FoodPost`, `NewFoodPost` (always sent) and `FoodPostDraft`; `HOLD_MINUTES`, `DEFAULT_HOLD_MINUTES`, `pickupDeadlineEstimate()`, `reservableNow()`.
- `model/Reservation.kt`: `pickupDeadline`, `CANCEL_REASON_PICKUP_EXPIRED`, `expiredUnpicked`.
- `UiText.kt`: `pickUpByTime()`, `holdEstimateText()`, `deadlineLine()`, `deadlineUrgent()`.
- `data/ReservationRepository.kt`: Reserve through `create_reservation_with_hold`; `heldQuantity()`. `data/FoodRepository.kt`: sends the limit.
- `viewmodel/ReservationViewModel.kt`: `reserve(…, holdMinutes)`, `heldQuantity()`.
- `PostScreen.kt`: the field, its check, "Check your listing", the Edit Listing note.
- `MainActivity.kt`: the details page's limit line, the stepper capped by the person's allowance, "You've reserved the most this listing allows per person (N)."
- `FoodActions.kt`: "Pick up within" with the estimate; the receipt's "Pick up by".
- `ActivityScreen.kt`: deadline lines, the minute ticker, the one reload, "Expired" badges and notes, "The pickup time has passed." instead of Mark as picked up.

### 24.4 Database steps (the developer, in the SQL Editor)

Run **before** installing the build that reserves with a pickup time (`create_reservation_with_hold`). Older builds keep working.

1. **Trial run (nothing is kept):** paste this migration and, below it, `tests/reservation_limits_live_safety_check.sql` into one SQL Editor query and run it. It ends with "SAFETY CHECK FINISHED: …" on purpose. Then run `tests/reservation_limits_check.sql`: every row should be false (nothing stayed). If rows are true, the editor committed the migration; that is harmless and counts as step 2.
2. **Apply:** run this file alone. It also turns on Supabase Cron (`pg_cron`) and schedules the job `salusalo-expire-reservations` (every minute). If the extension is refused, switch on Integrations → Cron in the dashboard and run the file again.
3. **Check:** run `tests/reservation_limits_check.sql` (every row true), then the two commented queries at its end (the job is active and has succeeded at least once), then `tests/reservation_limits_live_safety_check.sql` alone (all PASS).

Never grant EXECUTE on `expire_overdue_reservations()` to any client role; keep the limit check and the rate limit in `create_reservation`.

### 24.5 Two-phone test

Phone 1 = A (donor), phone 2 = B. Preconditions: migration applied, read-only check all true, the Cron job has run; new build on both phones.

1. [New] A: Post "Limit test": free, quantity 5, Maximum per person 2, pickup start "Start now", pickup end **6 minutes from now**, expiry later today. "Check your listing" shows "Maximum per person: 2.". Post: "Listing published".
2. [New] A: Post "Bad limit": quantity 3, Maximum per person 4 → "Enter a maximum per person from 1 to the quantity, or leave it blank." Don't post it.
3. [New] B: open "Limit test": "Limit: 2 per person"; the stepper stops at 2. Reserve 1: Review reservation shows "Pick up within" with 30 min selected and "Pick up by <end time>, when pickup ends." Choose 15 min (same text). Confirm: the receipt shows "Pick up by <end time>".
4. [New] B: open "Limit test" again: the stepper stops at 1. Reserve 1 more → receipt. Open it again: "You've reserved the most this listing allows per person (2)." instead of Reserve.
5. [New] B: Activity → My Reservations: both show "Pick up by <end time> · N min left", red under 10 minutes.
6. [New] A: Activity → "Limit test" → Manage reservations: B's two reservations with the same line.
7. [New] Wait until the end time passes, both apps on Activity. Within about 2 minutes, without touching anything: B's reservations move to History with the "Expired" badge; A's Manage reservations shows them as Expired. Both phones get "Reservation expired" alerts (B: "Your reservation of Limit test expired because it wasn't picked up in time."; A: "<B>'s reservation of Limit test expired because it wasn't picked up in time. The quantity is available again.").
8. [New] B: open an expired reservation: "You didn't pick up in time, so the reservation expired and the food went back on the listing." A: "<B> didn't pick up in time, so the reservation expired."
9. [New] A: Edit "Limit test": the notice ends "Maximum per person: 2 (can't be changed)."
10. [New] A: post "Hold test": free, 3 packs, no limit, pickup start "Start now", pickup end in 2 hours. B: reserve 1 with 15 min: "Pick up by <now + 15 min>." Wait 15 minutes (do steps 11–13 meanwhile). Before it expires, A sees "Mark as picked up"; after the time passes A sees "The pickup time has passed." instead (if still shown before the reload); then it expires as in step 7.
11. [Regression] B: reserve 1 of an unlimited listing with 1 hour; A marks it picked up: "Marked as picked up. You'll find it under Completed."; B gets "Reservation claimed".
12. [Regression] B: reserve and cancel: "Reservation cancelled. The quantity is available again."; A releases another B reservation: "Reservation released. The quantity is available again, and the recipient was told."
13. [Regression] Chat message both ways; Home pull to refresh; Post a listing with a photo and delete it ("Listing deleted.").

### 24.5a Result (2026-10-03)

The developer ran the 13 steps on two phones: **all passed.** During step 7 "Limit test" left Home once its pickup ended while staying in A's My Listings; that is the existing rule (Home lists only listings that can still be reserved), not a defect, and the expired quantity went back onto that ended listing as §24.6 describes.

### 24.6 Known limits

- Expiry happens up to about a minute after the deadline; longer if more than 500 are overdue at once or a listing is busy at that moment.
- If Cron is turned off, nothing expires (late claims are still refused).
- A donor who hands food over but doesn't tap Mark as picked up before the deadline sees it expire and the quantity come back; Edit can't change the quantity.
- Deadlines are fixed when reserving; editing the pickup window later doesn't move them.
- The limit is per account, and can't be changed after posting.
- The details page's allowance is read when it opens; the server decides at Reserve.

## 25. Admin dashboard with real data (Phase 6, first slice)

### 25.0 Status

**Written on branch `claude/admin-real` (from `master` at `e24c8d7`) on 2026-10-03. Migration applied to the live project (live safety check 20/20), deployed to https://salusalo.web.app with `npx firebase-tools deploy --only hosting`, and the manual test passed on the PC and on the phone (2026-10-03). Merged into `master` through PR #23 on 2026-10-03 (merge commit `a60b315`).** Design: `docs/superpowers/specs/2026-10-03-admin-real-data-design.md`; plan: `docs/superpowers/plans/2026-10-03-admin-real-data.md`.

### 25.1 What changed

- **Database** (`20261012_admin_dashboard.sql`): `is_salusalo_admin()` (caller's own `users.role = 'ADMIN'`, set by hand); admin-only SECURITY DEFINER read functions for every page and actions `admin_remove_listing` (never deletes: `REMOVED`, open reservations cancelled `LISTING_REMOVED`, a "Listing removed" alert per recipient and "Your listing was removed" for the donor, both of the existing type `RESERVATION_REMOVED`, saved rows removed, open reports resolved), `admin_dismiss_listing_reports`, `admin_dismiss_report`, `admin_add_report_note`, `admin_mark_listing_reviewed`; report statuses `RESOLVED`/`DISMISSED` with who, when, action and note; `report_notes`; the insert-only `admin_audit_log`; `can_read_food_image` also for admins. No client table privileges, nothing for `anon`, no RLS widening.
- **Web dashboard** (`admin/`): a Sign In screen (email and password, or Google) with Firebase Auth of project `salusalo`; supabase-js sends the Firebase ID token like the app; a non-admin is signed out with "This account doesn't have admin access."; `js/data.js` calls the admin functions; listing photos through 1-hour signed URLs; the sample data and its tools are gone. Overview, Users (read-only), Listings, Reservations (with the real endings: cancelled, released, listing removed, expired), Reports (dismiss, notes, remove the listing), Audit log and Settings (Log out) are real; Analytics (until §26) and Notifications say "Not connected yet"; verification requests and Suspend/Restore were removed until their slices.
- **Hosting**: `firebase.json` (`admin/`, a Content-Security-Policy, no caching of HTML/JS/CSS) and `.firebaserc` (`salusalo`); `admin/js/config.js` (Supabase URL and key, Firebase web config) is git-ignored. Deploy: `npx firebase-tools deploy --only hosting` → https://salusalo.web.app.
- No Android change.

### 25.2 Checks run (local only, 2026-10-03)

- A local PostgreSQL 16.4 with stand-ins for Supabase's roles, `auth.jwt()`, Storage and Cron: all 21 migrations applied (the new one twice). `admin_dashboard_live_safety_check.sql` failed first (1 passed, 14 failed, 5 skipped) and passes **20 of 20** after the migration; `admin_dashboard_check.sql` rows 1–6 true (the "an admin exists" row false, as no admin was set); `bugfix_anon_grants_check.sql` 3 of 3. Earlier checks still pass: `phase3_live_safety_check` 14/14, `bugfix_security_live_safety_check` 21 passed 2 skipped, `reservation_limits_live_safety_check` 20/20, `phase5_maps_live_safety_check` 22/22, `bugfix_reservations_live_safety_check` 15/15, `phase5_live_safety_check` 3 passed 24 skipped (the seed has no claimed reservations).
- Local read checks: search text with `%` or `_` matches literally; a page past the end gives the last page; page size is capped at 50; a non-admin is refused.
- The Node checks in `admin/tests/` passed (timeline; sign-in messages, including the "Your admin access ended" message surviving the reload; the data layer, including an action reported as done when only the count reload fails); `node --check` on every script.
- A fresh-context review of the whole branch found no critical problem; its two important findings (that message, and Google sign-in needing `authDomain: salusalo.firebaseapp.com`) and two re-graded ones (an action reported as failed when only the count reload failed; two CLAUDE.md rules contradicting the admin functions) were fixed.
- **Not done:** the dashboard has not run in a browser against Supabase (no local PostgREST); that is the developer's manual test (§25.4).

### 25.3 Database (run by hand)

**Applied to the live project on 2026-10-03** (developer, SQL Editor): the migration ("Success. No rows returned"); `admin_dashboard_check.sql` rows 1–6 true, then true for every row after the developer's account was set to `ADMIN`; `admin_dashboard_live_safety_check.sql` **20 passed, 0 failed, 0 skipped** (donor, recipient and admin were three existing accounts; 12 users, 5 open reports at the time).


See `supabase/README.md` → 20261012: run the migration, `tests/admin_dashboard_check.sql`, set the admin role, then `tests/admin_dashboard_live_safety_check.sql` (expect 20 passed).

### 25.4 Manual test

**Part 1, 2026-10-03 (PC, http://localhost:8080 with `npx http-server`): passed** (reported as "went perfectly good"): the Sign In screen and the wrong-password message, a non-admin refused with "This account doesn't have admin access.", the admin signed in to Overview with real numbers and names and no sample data. **Part 2, 2026-10-03: passed** (reported as "everything passed on the phone too"): a listing posted by a normal account, reserved and reported by the admin in the app; in the dashboard the report with the listing's photo, a note, the listing's photos, reservation and report; Remove listing with the two counts, Removed by the admin, the report under Resolved and the Audit log entries; the "Listing removed" and "Your listing was removed" alerts in the app and the listing gone from Home; the admin role taken back while signed in ("Your admin access ended. Please sign in again.") and restored; `firebase-tools login` (in Android Studio's terminal; the remote login from Claude Code's shell failed) and the deploy ("Deploy complete!", https://salusalo.web.app); on the phone's browser, sign-in, Overview, Reports, photos and the menu drawer. **Not done:** Add to Home screen (the developer couldn't find the menu item); Safari/iOS, screen readers and two admins at once were left out.


The plan's Task 9: M1 sign-in screen and wrong password; M2 a non-admin refused; M3 Overview numbers; M4–M6 a reported test listing reviewed, noted and removed, with both alerts in the app and the Audit log entries; M7 admin role taken back while signed in; M8 on the phone at https://salusalo.web.app.

### 25.5 Known limits

- Every admin can do everything; no per-admin permissions.
- The donor's removal alert follows their "Reservation updates" switch, like every reservation alert.
- Nothing is live: pages reload when opened and on Try again.
- The publishable key and Firebase web config are visible to anyone who opens the site, as in any web app; security rests on the token and the admin check in each function.
- Suspend, ID verification, announcements and analytics are later slices.

## 26. Admin dashboard: Analytics (Phase 6, second slice)

### 26.0 Status

**Written on branch `claude/admin-analytics` (from `master` at `a60b315`, the PR #23 merge) on 2026-10-03. Migration applied to the live project (safety check 10/10), deployed to https://salusalo.web.app and tested on the phone on 2026-10-03. Merged into `master` through PR #24 on 2026-10-03 (merge commit `c240f48`).**

### 26.1 What changed

- **Database** (`20261013_admin_analytics.sql`): admin-only SECURITY DEFINER `admin_analytics(p_weeks)` (4, 8 or 12 only; "Choose 4, 8 or 12 weeks." otherwise) and the internal `admin_analytics_kpis(from, to)` (no client may run it). It returns counts only: seven totals (listings posted, reservations made, completed pickups, items rescued, expired listings, active donors, active recipients), the same totals for the period before (only when the first account is older than that period, otherwise `null`), weekly series (listings, reservations, picked up, cancelled, items rescued) and how the listings posted in the period ended (available, reserved, claimed, expired, removed). A pickup counts when it was marked picked up; "expired" is a still-available listing past its expiry or pickup end, as elsewhere in the dashboard (nothing on the server sets `EXPIRED`). Periods end at the real current time (`clock_timestamp()`).
- **Web**: the Analytics page from the sample version (§20), unchanged, on `SaluData.analytics()`; Overview's Food rescue card links to it again.
- No app change.

### 26.2 Checks run (local only, 2026-10-03)

- Local PostgreSQL 16: `admin_analytics_live_safety_check.sql` failed before the migration (1 passed, 6 failed, 2 skipped) and passes **10 of 10** after it: the admin reads 12 weeks; after a test listing, a reservation of 2 and its pickup every total and the latest week move by exactly that much; for 4, 8 and 12 weeks the weekly series add up to the totals and the statuses to the listings; 5 weeks, a non-admin and `anon` are refused; the privileges; no names or emails in the answer; the previous period appears only when the first account is older than it (run with new accounts and again with a 10-week-old account). The first run found that periods ended at `now()` (the start of the transaction), which left out rows written in the same transaction; they end at `clock_timestamp()` now. The migration ran twice; `admin_dashboard_live_safety_check` 20/20 and `bugfix_anon_grants_check` 3/3 still pass.
- The Node checks in `admin/tests/` pass (`data.test.js` now also checks `analytics()`); `node --check` on every script.

### 26.3 Database (run by hand)

Run `supabase/migrations/20261013_admin_analytics.sql`, then `supabase/tests/admin_analytics_live_safety_check.sql` (expect 10 passed).

**Applied to the live project on 2026-10-03** (developer, SQL Editor); `admin_analytics_live_safety_check.sql` **10 passed, 0 failed, 0 skipped** (12 weeks before the test: 31 listings, 45 reservations, 13 pickups, 15 items rescued, 8 expired, 6 active donors, 7 active recipients; each total moved by exactly the test's listing, reservation and pickup; first account 2026-09-21).

### 26.4 Manual test

**2026-10-03: passed** (reported as "test done"): deployed with `npx firebase-tools deploy --only hosting`; on the phone, Analytics with its seven numbers and five charts, the 4/8/12-week switch, a chart's table view, and Overview → Food rescue → Analytics.


Deploy, then on the phone: Analytics shows seven numbers and five charts for 12 weeks; 4 and 8 weeks change them; a chart's table view; Overview → Food rescue → Analytics.

### 26.5 Known limits

- Items rescued add up different units (pieces, packs, meals, boxes, kilograms), like the app's History totals.
- A pickup counts in the week it was marked picked up, a cancellation in the week it was cancelled.
- With only a few weeks of data, the comparison with the previous period is hidden for the longer ranges.

## 27. Admin dashboard: announcements (Phase 6, third slice)

### 27.0 Status

**Written on branch `claude/admin-announcements` (from `master` at `c240f48`, the PR #24 merge) on 2026-10-04. Migration applied to the live project on 2026-10-04 (live safety check 14/14: 12 accounts, 6 donors, 6 recipients). Deployed, and the manual test (27.4) passed on one phone on 2026-10-04. Merged into `master` through PR #25 on 2026-10-04 (merge commit `f959f0e`).** Spec: `docs/superpowers/specs/2026-10-04-admin-announcements-design.md`; plan: `docs/superpowers/plans/2026-10-04-admin-announcements.md`.

### 27.1 What changed

- **App** (must be on every phone before the migration): `NotificationType` gains `ANNOUNCEMENT`. Alerts of a type the build doesn't know decode as `UNKNOWN` (`NotificationTypeSerializer`) and are dropped by `NotificationRepository` (the list and the live alerts), so a type added later never breaks Alerts again; before this build one unknown type made the whole list fail ("Could not load alerts"). Alerts shows announcements with a megaphone, the label "Announcement" and an "Announcements" filter chip; tapping one opens its whole text with "From Salu-Salo · <time>" (kept through rotation). They play the alert sound like other live alerts. The header reads "Messages, reservation updates and announcements".
- **Database** (`20261014_admin_announcements.sql`): the `ANNOUNCEMENT` alert type; table `announcements` (title 1–80, message 1–500, audience, reach, admin and time; RLS on, no client privileges); audit action `ANNOUNCEMENT_SENT` (target type `ANNOUNCEMENT`); admin-only `admin_audience_sizes()`, `admin_send_announcement(title, message, audience)`, `admin_announcements(page, size)`, `admin_announcement(id)` and the internal `admin_announcement_json`. Sending trims spaces, tabs and line breaks around the title and message (so blank-looking text counts as empty), then inserts one alert per account in the audience directly, not through `notify_user()`: it ignores the alert switches and keeps the whole text. It refuses an empty audience ("No one is in this audience yet.") and allows at most 3 announcements per admin per 10 minutes, under a per-admin advisory lock; the alerts, the history row and the audit row ("To 12 people (everyone).") are written in one transaction. Audiences are the Users page's filters: Everyone (every account, admins included), Donors (has posted), Recipients (never posted, not an admin).
- **Web**: the Notifications page is real: the history (paged) with each announcement's details, and "New announcement" with title and message counters, the audience with live counts, a preview drawn like the app's row (line breaks kept, cut after 3 lines) and "Send to N people?"; the details keep the message's line breaks. A refusal shows the server's own text (`DataError.code` P0001; `confirm()` takes the error text as a function too); any other failure may still have sent it (a lost answer), so it says "Couldn’t confirm it was sent. Close this form and check the list before sending it again." and reloads the list instead of inviting a retry. The Audit log's "Sent announcement" links to the announcement.

### 27.2 Checks run (local only, 2026-10-04)

- JVM unit tests 71 of 71 (`NotificationTypeTest`, 3 new, failed to compile before the change); `assembleDebug` passed; `lintDebug` 0 errors.
- Local PostgreSQL 16: `admin_announcements_live_safety_check.sql` before the migration 0 passed, 12 failed, 2 skipped; after it (the migration run twice) **14 of 14**: the audience sizes; an empty audience refused; Everyone reaches every account once with the title trimmed of spaces, tabs and line breaks and the whole 500-character text, including an account with both alert switches off; Donors and Recipients reach exactly their accounts; seven wrong inputs refused with their texts (including a tab-only title and a message of line breaks, which a spaces-only trim let through: checks 3 and 6 failed with it); the fourth send in 10 minutes refused; the history, the announcement by ID and the audit row; the recipient reads its own announcement; a non-admin and `anon` refused by all four functions; no client privileges on `announcements`; the privileges; the earlier alert types still accepted and an unknown one refused. Still passing: `admin_dashboard_live_safety_check` 20/20, `admin_analytics_live_safety_check` 10/10, `bugfix_anon_grants_check` 3/3, `admin_dashboard_check` (every row true except "an admin exists"; the local seed has none).
- Node checks in `admin/tests/` pass (`data.test.js` also checks the four announcement calls and that a refusal keeps its code); `node --check` on every script.
- Headless Edge with a stand-in Supabase client (not committed): the page lists, opens the form with live counts, refuses an empty title, updates counters and preview (line breaks kept, cut after 3 lines), asks "Send to 5 people?", sends the trimmed fields to the chosen audience, shows "Announcement sent to 5 people.", shows the rate-limit text on a refusal, answers a lost reply with "Couldn’t confirm it was sent…" and a list reload, shows details (line breaks kept; HTML in a message stays text) and "Announcement not found"; no script errors.

### 27.3 Order (by hand)

1. Install the new app build on every phone that uses the live project.
2. Run `supabase/migrations/20261014_admin_announcements.sql`, then `supabase/tests/admin_announcements_live_safety_check.sql` (expect 14 passed).
3. Deploy: `npx firebase-tools deploy --only hosting`.
4. The manual test (27.4).

### 27.4 Manual test (passed 2026-10-04, one phone)

**2026-10-04: passed** (reported as "all test passed"). Run on one phone, switching between accounts A and B, with the dashboard on the PC:
- the new build installed, and Alerts showing its new header and chips;
- the migration, the live safety check (14/14) and the deploy;
- with A signed in: an announcement to Everyone (a message longer than 3 lines) arriving live with the sound; the full text, kept through rotation; the read state and the Announcements chip; the history, the details and the Audit log link; Donors and Recipients sends; the rate-limit text;
- signed in as B: B's announcements in Alerts although B wasn't signed in when they were sent; B messages A and reserves 1 of A's listings;
- back as A: the message alert opens the chat, the "New reservation" alert opens the reservations; A releases the reservation; the Reservation updates switch saves off and on.

Not tested: two phones getting the same announcement live at once (the second account was checked by loading Alerts after signing in). The original two-phone checklist follows.


Phone A and phone B have different accounts; the dashboard runs on the PC or a phone's browser with the admin account.

Part 1, before the migration:
1. (New) Install the new build on both phones. On A open Alerts: the header says "Messages, reservation updates and announcements", the chips are All, Messages, Reservations, Announcements, and the existing alerts load. The Announcements chip shows "No announcements".
2. Run the migration ("Success. No rows returned"), then the safety check ("SAFETY CHECK FINISHED: 14 passed, 0 failed, 0 skipped."), then deploy.

Part 2, sending:
3. (New) Both phones on Home with the app open. Dashboard → Notifications: "No announcements yet". New announcement: the three audiences show their counts.
4. (New) Title "Test announcement", a message longer than 3 lines, Everyone, Send → "Send to N people?" → Send → "Announcement sent to N people."
5. (New) Both phones: the alert arrives live with the alert sound; in Alerts it has the megaphone and "Announcement", the text cut after 3 lines.
6. (New) A: tap it → the whole text and "From Salu-Salo · <time>"; rotate → still open; OK. The row is read; the Announcements chip shows it, the Messages chip doesn't.
7. (New) Dashboard: the history row (Everyone, N people, You); open it → the details; Audit log → "Sent announcement" → its link opens the same announcement.
8. (New) Send one to Donors, then one to Recipients: each reaches the count shown (or, for an audience of 0 people, "No one is in this audience yet."). Then send another to Everyone until the dialog shows "You can send up to 3 announcements every 10 minutes. Please wait a few minutes."; Cancel.

Part 3, regression:
9. (Regression) B messages A about a listing: A's message alert opens the chat.
10. (Regression) B reserves 1 of A's listings: A's "New reservation" alert opens the reservations; B cancels.
11. (Regression) A: Alerts → settings, turn Reservation updates off, Save, reopen (off), turn it back on, Save.

### 27.5 Known limits

- In-app only: no push while the app is closed; it shows when the app opens or Alerts reloads.
- No scheduling, drafts, unsend or per-person opt-out.
- The reach counts accounts, not phones, at the moment of sending; accounts created later don't get it.
- A phone on a build older than §27 shows "Could not load alerts" once it has an announcement, until it is updated.

## 28. Chat wallpaper (Mika's PR #22)

**Written by Mika S (`f357f7f`, from `master` at `e24c8d7`). Merged into `master` through PR #22 on 2026-10-04 (merge commit `6f1223c`), at the developer's request, after Claude built it merged with `master` at `f959f0e` (`assembleDebug`, unit tests 71/71, `lintDebug` 0 errors, no lint warning in `ChatScreen.kt`). Not checked on a phone before the merge; checked afterwards in step 8 of the §29 test (passed 2026-10-04).**

- `ChatScreen.kt` only: `Modifier.foodDoodleWallpaper()` draws a white background with food doodles (eight vector outlines, a soft green-to-yellow gradient, in a staggered 60 dp grid) behind the messages, the loading spinner and the empty state. The paths are built once (`drawWithCache`); the doodles are decorative (no touch handling, nothing for TalkBack). The header, the bubbles and the typing area are unchanged.
- No SQL, no new library or image file.

## 29. New logo and a loading screen when the app opens

**Written on branch `claude/new-logo-startup` (from `master` at `6f1223c`) on 2026-10-04, at the developer's request. Built: `assembleDebug`, unit tests 71/71, `lintDebug` 0 errors (39 warnings, from 47; none new). The dashboard logo deployed on 2026-10-04, and the one-phone test (29.2) passed on 2026-10-04. Committed as `399a6da` and merged into `master` through PR #26 on 2026-10-04 (merge commit `7369654`).**

### 29.1 What changed

- **The new logo** (the developer's image: a green and gold ring around a bowl of greens, an apple and bread held by a hand, on white), everywhere the app shows its logo:
  - `SaluLogo` (the loading screen, the welcome pages, Sign In, About) draws `res/drawable-nodpi/salu_logo.webp` (512 px, the emblem cropped square) on a white disc. The old way (the launcher artwork enlarged until its green square filled the circle) is gone.
  - The launcher icon: the adaptive icon's foreground is the emblem inside the safe zone on white, its background plain white (it was Android Studio's template green grid), and a new silhouette (`ic_launcher_monochrome`) for themed icons on Android 13+. The legacy icon (Android 7) is a white disc with the emblem. The template drawables and the separate round icon (identical now, which lint flagged) were removed, with the manifest's `roundIcon`.
  - The admin dashboard: `admin/assets/salusalo-logo-2.webp` (the sidebar, the sign-in card and the browser tab icon) is the same 512 px file, under a new name: with the old name (`salusalo-logo.webp`), browsers kept showing the old logo after the deploy, because Firebase lets them keep images for an hour (`max-age=3600`). The page itself had the same problem: `firebase.json`'s `no-cache` rule covered only paths ending in `.html`, `.js` or `.css`, so the dashboard's address `/` was kept for an hour too (`/index.html` was `no-cache`), and browsers kept opening the page that asked for the old file. A new rule gives `/` `no-cache` as well; without it any deploy could show an hour-old page with new scripts. `.logo img` no longer enlarges it 1.65× (that cropped the old artwork). Checked in headless Edge (the sign-in card shows it on its white disc) and with the Node checks; deployed on 2026-10-04: the live site sends `no-cache` for `/` (checked with curl), and the developer sees the new logo on the dashboard.
  - The icons were made from the image with a Pillow script (not committed): its near-white background lifted to pure white so it meets the white icon background without a seam.
- **The loading screen when the app opens**: before, it showed only while a signed-in account was set up (after signing in, or opening the app with "Keep me signed in"); opening the app signed out went straight to the welcome pages or Sign In. Now `SaluSaloApp` shows it for at least 1.2 s every time the app opens (`STARTUP_INTRO_MS`; `introDone`, saved, so rotation and Android restoring the app don't show it again), then Sign In, the welcome pages or Home as before. A setup that takes longer keeps it up as before.
- **The window background is the app's cream** (`Theme.SaluSalo`, `@color/salu_cream`), so the starting window and Android 12+'s system splash (the launcher icon on that background) flow into the loading screen instead of flashing white.

### 29.2 Manual test (passed 2026-10-04, one phone)

**2026-10-04: passed** (reported as "all test passed"), after Run in Android Studio:
1. (New) Opening the app: cream, then the loading screen with the new logo, the name, the tagline, the green bar and "Getting things ready…", then Sign In or Home.
2. (New) The new launcher icon on the phone.
3. (New) The new logo on Sign In; turning the phone sideways doesn't show the loading screen again.
4. (New) Swiped away in Recents and opened again: the loading screen again, then Sign In.
5. (Regression) Signing in with "Keep me signed in": the loading screen, then Home; swiped away and reopened: the loading screen, then Home.
6. (Regression) Back from the phone's Home button: no loading screen, Home as it was.
7. (New) The new logo on About Salu-Salo; Log out goes straight to Sign In.
8. (New, §28) The food doodles behind a chat's messages; bubbles, typing and the keyboard as before.
9. (New) The dashboard: the new logo on the sign-in card and the sidebar, after the cache fix (29.1) was deployed.

Not tested: Android 7 (the legacy icon), themed icons (Android 13+), turning the phone during the first 1.2 s.

### 29.3 Known limits

- Opening the app now shows the loading screen for at least 1.2 s before Sign In or Home.
- The themed (monochrome) icon is a silhouette made from the colours; its fine details (the bread's stripes) are approximate.

## 30. Bug fix pass, round 4 (2026-10-04)

### 30.0 Status

**Written on branch `claude/bugfix-pass-4` (from `master` at `7369654`, the PR #26 merge) on 2026-10-04, after a second full, read-only audit the same day. Built: `assembleDebug` passed, `testDebugUnitTest` 73 of 73 (71 before, plus 2 in `ReserveBlockerTest`), `lintDebug` 0 errors. The migration passed a local PostgreSQL 16 dry run (§30.3) and was applied to the live project on 2026-10-04 ("Success. No rows returned"). Live safety check 9 of 9; the one-phone test (§30.4) passed on 2026-10-04. Merged into `master` through PR #27 on 2026-10-04 (merge commit `a5f9a2c`).**

### 30.1 The audit (2026-10-04)

A read-only audit of `master` at `7369654`: all 23 migrations as they now stand, the `set-firebase-role` Edge Function, all of the Kotlin app and the admin dashboard's JS, `firebase.json` and its Content-Security-Policy. It found nothing critical or high. The numbers follow the 2026-09-29 audit.

| Bug | Severity | What was wrong | Status |
|---|---|---|---|
| #36 | Medium | `remove_my_food_post` deleted every listing nobody had reserved, also one that was reported or that someone had asked about in a chat. A reported donor could erase the evidence: the admin then saw only "Deleted listing" (no photos, details or chat), and the other person's chat and its messages were deleted with it, without telling them. | FIXED (migration `20261015`) |
| #37 | Low | Log out (and a session that ended) redrew Home for a moment without an account: a second set of screens was made and the feed, response rate and ratings were requested without a login (all refused). | FIXED |
| #38 | Low | The per-minute feed reload (round 3) changed the open Review reservation page to the listing's latest price and amount, and Confirm sent those, so the price check (#8) no longer guaranteed the price the user had read. | FIXED |
| #39 | Low | A chat was read oldest first without a limit; Supabase answers at most 1000 rows, so a longer chat lost its newest messages. | FIXED |
| #40 | Low | Closing a chat reloaded the inbox in a way that shows errors: offline, a chat that failed to open showed a second error on Back. | FIXED |
| #41 | Low | Activity's Active filter, Saved Listings and the claim and donation records ignored the end of pickup: a listing nobody could reserve any more stayed "Active" (with no badge) until it expired. | FIXED |
| #42 | Low | These docs said nothing expires reservations, 57 tests and 19 migrations, and §29 and `CLAUDE.md` said "not yet committed" after PR #26. | FIXED |
| Small | Minor | A message sent while the phone was turned stayed in the box; turning the phone during the first 1.2 s restarted the loading screen; unsaved Food preferences ticks were lost on rotation; the empty Cancelled list in Manage reservations named only the recipient's cancellations. | FIXED |

Still open from the first audit, unchanged: #10, #11, #12, #16, #24, #28, #31 and #34 in part ("Current known issues").

### 30.2 What changed

- **Database** (`20261015_bugfix_keep_reported_listings.sql`, #36): `remove_my_food_post` deletes a listing only when nobody reserved it, reported it or started a chat about it. Otherwise it is kept as `REMOVED`, like a reserved one: hidden from Home and from the donor's listings, its photos, chats and reports kept, saved rows removed. Everything else is the 20260930 function unchanged (the same locks, checks, texts and result), so any app build keeps working: such a listing comes back as `REMOVED`, which every build shows as "Listing removed". Checks: `supabase/tests/bugfix_keep_reported_listings_live_safety_check.sql` (9 checks, always rolls back).
- `MainActivity.kt`: Home is drawn only with an account (#37); the Review reservation page keeps the price and amount it opened with, and Confirm sends those (#38); the loading screen counts its 1.2 s from when the app opened, so a rotation doesn't restart it.
- `data/MessageRepository.kt` (#39): a chat loads its newest 1000 messages and shows them oldest first.
- `viewmodel/MessageViewModel.kt` and `ChatScreen.kt`: closing a chat reloads the inbox quietly (#40); what is typed in a chat is held by the ViewModel, so a send that finishes after a rotation clears the box that is shown.
- `model/FoodPost.kt` (#41): `canBeReserved()` and `isActiveListing()`; `ActivityScreen.kt` uses them for the Active and Closed filters and shows a "Pickup ended" badge; `ProfileSections.kt` uses `canBeReserved()` for Saved Listings and the records' "View listing". The empty Cancelled list in Manage reservations now names cancelled, released and expired reservations.
- `ProfileSettingsUi.kt`: unsaved Food preferences ticks survive rotation.
- The Delete listing dialog (`DELETE_LISTING_TEXT`) and the comments on `remove_my_food_post` in the app follow #36. The dialog never says "reported": the reported person is not told.
- Tests: two in `ReserveBlockerTest` (`canBeReserved`, `isActiveListing`).
- Docs: this section, "Current state", "Current known issues", §29's status, `CLAUDE.md` and `supabase/README.md` (#42).

### 30.3 Checks run (local only, 2026-10-04)

- Local PostgreSQL 16.4 with stand-ins for Supabase's roles, `auth.jwt()`, Storage and Cron: all 24 migrations from an empty database, the new one also run twice. The new check, written first, failed before the migration (5 passed, 4 failed: the reported and chatted-about listings were deleted, and the admin's report showed "Deleted listing") and passes **9 of 9** after it. Still passing: `phase3_live_safety_check` 14/14, `admin_dashboard_live_safety_check` 20/20, `bugfix_security_live_safety_check` 21 passed, 2 skipped (as before), `reservation_limits_live_safety_check` 20/20, `bugfix_anon_grants_check` 3/3, `phase3_demo_fixes_check` 13/13.
- JVM: the two new tests failed to compile before the change (`canBeReserved` and `isActiveListing` didn't exist) and pass after it; `testDebugUnitTest` 73 of 73. `assembleDebug` passed; `lintDebug` 0 errors.
- No automated test covers #37, #38, #39, #40 or the three rotation fixes (screens and network code); the phone test does, except #39, which would need a chat of more than 1000 messages.

### 30.4 Phone test (one phone, switching accounts; A = donor, B = recipient)

Before installing: in the SQL Editor run `supabase/migrations/20261015_bugfix_keep_reported_listings.sql` ("Success. No rows returned"), then `supabase/tests/bugfix_keep_reported_listings_live_safety_check.sql` ("SAFETY CHECK FINISHED: 9 passed, 0 failed, 0 skipped."). Then Run in Android Studio.

1. [Setup] A: post three free listings, "Keep 1", "Keep 2" and "Delete me", and a fourth, "Ends soon", with the pickup end about 5 minutes from now (expiry later today).
2. [New #36] B: open "Keep 1" → Report this listing → any reason → "Report sent…". Open "Keep 2" → Message → send "Test". Bookmark "Ends soon".
3. [New #36] A: Activity → "Keep 1" → Delete listing: the dialog reads "A listing nobody reserved or messaged you about is deleted with its photos. Otherwise, or when Salu-Salo needs to keep it, it is removed…" → Delete → "Listing removed. Its past reservations and chats are kept." (before: "Listing deleted."). The same for "Keep 2". Then "Delete me" → "Listing deleted." (regression).
4. [New #36] Dashboard → Reports: the report on "Keep 1" shows the listing (not "Deleted listing"), with its photo; its listing page says "Removed by the donor" and "The donor removed it in the app."
5. [New #36] B: Messages → the chat about "Keep 2" is still there with "Test"; "Keep 1" and "Keep 2" are gone from Home.
6. [New #41] After the end of pickup of "Ends soon": A: Activity → My Listings → Active no longer lists it; Closed lists it with a "Pickup ended" badge (Edit is still offered). B: Profile → Saved Listings: "Ends soon" says "No longer available", with Remove only.
7. [New #40] B: Messages, then airplane mode on, tap a chat → the error dialog → OK → Back: no second error. Airplane mode off.
8. [New] B: open a listing → tap Allergens: the chat opens with "Hi! What allergens does … contain?" in the box. Turn the phone while the message sends (tap send, turn at once): the box is empty afterwards and the message shows once.
9. [New] Close the app (swipe away), open it and turn the phone during the loading screen: it ends after about 1.2 s in all, not 1.2 s after turning. Profile → Settings → Food preferences: tick two categories, don't save, turn the phone: still ticked.
10. [Regression #37, #38] B: reserve 1 of an available listing (free or priced): the review page shows the price; Confirm → receipt. A: mark it picked up → "Marked as picked up…". Profile → Log out → Sign In, with no error.

Optional, with two phones (#38): B opens a priced listing of A, chooses 2 and taps Reserve (the review page shows its price and total); A edits the price; B waits a minute on the review page: the price and total don't change; Confirm → "The price changed to ₱… per unit. Check the details before reserving." (before: the page switched to the new price and the reservation went through).

Left out: #39 (a chat of more than 1000 messages).

### 30.4a Result (2026-10-04)

The developer ran steps 1–10 on one phone, switching between A (PASTOR) and B, with the dashboard on the PC: **all passed** (reported as "all test passed"). In step 4 the open report on "Keep 1" showed the listing with its photo, "From PASTOR" and a Removed badge, with no Remove listing button; for an open report the way to the listing page is the listing's name in the "Reported listing" card (the View listing button appears only once a report is closed). The live safety check (`bugfix_keep_reported_listings_live_safety_check.sql`) passed **9 of 9** (donor PASTOR, recipient SaluSalo). Not reported: the optional two-phone #38 step.

### 30.5 Known limits

- #36: a listing someone reported or started a chat about stays in the database as `REMOVED`, with its photos; its donor can't delete it completely. A donor who expected "Listing deleted" for a listing nobody reserved or messaged about could guess it was reported. Listings deleted before this migration stay "Deleted listing" in their reports.
- #38: if the donor changes the price while the review page is open, Confirm is refused with the new price; the user goes back and reserves again.
- #39: a chat shows only its newest 1000 messages.
- The text typed in a chat is no longer kept if Android closes the app in the background; the open chat closes then anyway.

## 31. On-device recommendations (2026-10-04)

### 31.0 Status

**Written on branch `ccr-01f52d20-ihgnaw` (from `master` at `a5f9a2c`, the PR #27 merge) on 2026-10-04, at the developer's request for a simple recommendation based on reservations and saves "that can be called ML" and doesn't break anything; renamed `claude/ml-recommendations` for the PR (`claude/home-recommendations` is the older §19.4 branch). No SQL, no new library, no new permission. The writing session could not build with AGP (§31.4); built afterwards on the developer's PC: `assembleDebug`, `testDebugUnitTest` 86 of 86 and `lintDebug` 0 errors. The 10-step one-phone test (§31.5) passed on 2026-10-04, and the 4-card follow-up (`ba394b8`) its 2-step check the same day. Merged into `master` through PR #29 on 2026-10-04 at 17:51 (+08:00), merge commit `b15cb2a`.**

### 31.1 What the user sees

- The row at the top of Home (shown, as before, only with no chip and no search) becomes **"Recommended for you"**, "Learned from what you reserve and save", when the model has picks: up to 4 listings (2 before the PR #29 follow-up; the row scrolls sideways), each with a line saying why ("Like the cooked meals you reserved", "From a donor you reserved from", "Similar to food you saved", "Free food, like what you reserved").
- Otherwise the row is exactly as before: "Picked for you" with saved Food preferences or a pickup area, else "Just posted".
- Saved Food preferences and the pickup area still apply: the model picks only among the listings that match them.
- It never shows the account's own listings or listings it already reserved (in any state) or saved.
- The Food preferences page's caption now says "Choose the food for the row at the top of Home." (it named the row's old title).

### 31.2 How the model works (`model/ListingRecommender.kt`)

- It is machine learning in the plain textbook sense: a supervised classifier (logistic regression) trained by gradient descent on examples, on the phone, each time its data changes. It is deliberately small: no neural network, no server, nothing learned from other accounts. In a report it can be described as an on-device, content-based recommender: a logistic regression on listing features, trained on the user's own implicit feedback (reservations and saves).
- **Examples:** every listing the account reserved, whatever happened to the reservation later, is a positive with weight 1; every listing it only saved, a positive with weight 0.5. The other listings in the feed, apart from its own, are the negatives ("not chosen so far", the usual reading of implicit feedback), weighted so both classes count the same. At most the newest 50 chosen listings and the newest 100 others are used.
- **Features:** the category, free or for sale, the donor, and the words of the listing's name (lower case, simple English plurals folded, numbers and filler such as "fresh", "pack" or "mga" left out). Each group adds up to one unit, so a long name doesn't outweigh the category. Only features that occur in the chosen listings get a weight, so no listing is marked down for what makes it different.
- **Training:** full-batch gradient descent on the weighted log loss with L2 regularisation (0.1), step 0.5, 200 steps, from zero weights. Examples are sorted first, so the same history and feed always give the same model, whatever order the lists arrive in.
- **Picking:** a listing is recommended only when one of its features adds at least 0.1 to its score, so every card has a real reason; best score first, ties to the newest. The reason is the strongest of its category, donor or name words when that adds at least 0.1, else free or for sale; "reserved" or "saved" says which kind of choice that feature came from. Scores aren't shown: with balanced classes they rank listings but are not a chance that the user will reserve.
- **Data:** the feed, the account's reservations (`ReservationViewModel.myReservations`) and its saved listings (`SavedListingViewModel.savedIds` and `saved`), all already on the phone. Nothing is sent to Supabase or stored; the model lives in memory and is trained again when any of them changes.

### 31.3 What changed

- `model/ListingRecommender.kt` (new): the model, `listingFeatures()`, `nameWords()` and `foldPlural()`.
- `UiText.kt`: `recommendationLabel()`, the line under each card.
- `MainActivity.kt` (`HomeScreen`): trains the model in a `remember` keyed on everything it reads (the feed, the reservations, the saved IDs and rows, the preferences, the account); the row's "Recommended for you" title, subtitle and reasons; collects `myReservations` and the saved rows; calls `reservationViewModel.start()`.
- `viewmodel/ReservationViewModel.kt`: `start(profileId)` loads the reservation lists once per session when Home starts, so the model can use them before Activity is opened. It is quiet: a failed load leaves the load flags as they were, so Activity and Profile behave as before (they reload when opened and show their own errors). Rotation doesn't load again; signing out resets it.
- `ProfileSettingsUi.kt`: the Food preferences caption.
- Tests: `ListingRecommenderTest` (12: no model without history or without anything else to show; similar food first and unrelated food never; own, reserved and saved listings never shown; a reservation counts more than a save; the reason says reserved or saved; a donor as the reason; a save known only from the feed; the same answer whatever the input order; ties and the limit; plural folding and filler words; one unit per feature group) and one more in `UiTextTest` (the reason texts).

### 31.4 Checks run (2026-10-04)

- **AGP, on the developer's PC** (at `59237ff`, in a separate worktree): `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --offline` BUILD SUCCESSFUL in 6m 7s; `testDebugUnitTest` 86 of 86 (0 failures, 0 skipped); `lintDebug` 0 errors (25 warnings, 7 hints; the 3 in changed files are on old lines of `MainActivity.kt`). It merges into `master` and into Mika's open PR #28 without conflicts.
- **AGP in the writing session: not possible.** `dl.google.com` (the Android SDK and Google's Maven repository) is blocked by the session's network policy, and `maven.google.com` only redirects there.
- **Type check:** kotlinc 2.2.10 with the Compose and serialization plugins, in a scratch Gradle JVM project (not committed), as in §19.2: Compose Multiplatform 1.7.3 desktop jars (older than the project's Compose BOM), Robolectric's `android-all` 17 jar, osmdroid 6.1.20, supabase-kt 3.5.0, Coil 3.3.0, and hand-written stand-ins for AndroidX Activity, Lifecycle and Core, Credential Manager, Play services' `Task` and Firebase Auth. Unchanged `master` (`a5f9a2c`): 0 errors, 0 warnings. This branch: 0 errors, 0 warnings.
- **JVM tests** (JUnit 4.13.2 in the same project): `master` 73 of 73; this branch 86 of 86.
- **Model checks outside the tests:** a Python copy of the algorithm on 500 random histories and feeds gave the same top 2 after 200 steps as after 3000 in 499 of them. Timing on the session's JVM after warm-up: training 1.7 ms (40 listings, 8 reservations), 2.8 ms (120 listings, 60 reservations), 3.5 ms (400 listings, 300 reservations, cut to the caps); scoring the feed under 0.3 ms. Home trains it on the main thread, like its other lists; a phone will be slower than this machine.
- Read again against the code: Activity's alert focus still waits for its own reload (its `refresh()` marks the lists as loading before the focus check runs, as on any later visit), and Profile still reloads its counts when opened.

### 31.5 Phone test (one phone, switching accounts; A = donor, C = a new account)

**Passed on 2026-10-04**, all 10 steps, on the developer's phone with a build of this branch from Android Studio (no SQL). The developer posted their own four listings instead of the draft's, so the steps below use them. Before the test, Claude ran a copy of the model on these four listings (with and without other listings in the feed, and for the likely categories): "Chicken & fries" came first in step 3 in every case.

1. [Setup] A: post three free listings, "Fried chicken", "Ham and cheese overload pizza" and "Chicken & fries", and one for sale, "Bulaklak" (₱25).
2. [Regression] Sign up a new account C (nothing reserved or saved): Home's top row is "Just posted", as before.
3. [New] C: bookmark "Fried chicken" (its card under Available food). The top row becomes "Recommended for you", "Learned from what you reserve and save"; its first card is "Chicken & fries" with "Similar to food you saved" or "Like the cooked meals you saved"; "Fried chicken" itself is not in the row.
4. [New] C: reserve 1 "Ham and cheese overload pizza" (pick **2 hours** as the pickup time) → Confirm reservation → receipt → Home: the row still says "Recommended for you" and the pizza is not in it.
5. [New] C: remove the bookmark from "Fried chicken": the row stays "Recommended for you" (the reservation still counts).
6. [New] Turn the phone on Home and back: the row doesn't change.
7. [New] Swipe the app away and open it again as C (Create Account keeps C signed in), without opening Activity: once Home has loaded, the row is "Recommended for you".
8. [New] C: Profile → gear → Food preferences: tick only the category of "Chicken & fries" → Save preferences → Home: the row shows only that category ("Recommended for you" or "Picked for you"). Untick and save again.
9. [Regression] C: Activity → My Reservations shows the pizza reservation; cancel it → "Reservation cancelled. The quantity is available again." Home: the row still says "Recommended for you" and the pizza is still not in it (a cancelled reservation still counts).
10. [New] A: when A's row says "Recommended for you", none of A's own listings are in it.

During the run, the first pizza reservation (default 30-minute pickup time) expired before step 9 and so was not under My Reservations. That is §24 working as designed: the developer confirmed the pickup time had passed. Step 9 passed with a second reservation, made with 2 hours. Step 4 above now says to pick 2 hours.

Changed from the draft written in the session: its offline step was dropped, because an app started offline stops at Sign In (the profile setup needs the network) and never reaches Home. Its swipe-away step now notes that a new account stays signed in.

Left out: a quiet start-up load that fails (hard to cause on purpose), a history longer than the caps (50 choices), and timing on a slow phone.

**Follow-up for PR #29 (2026-10-04, at the developer's request):** the row shows up to **4** cards instead of 2, both "Recommended for you" and the fallback "Picked for you" / "Just posted" (one constant, `TOP_ROW_CARDS`; commit `ba394b8`, `testDebugUnitTest` 86/86, `lintDebug` 0 errors). Short check, **passed on 2026-10-04** (installed from Android Studio):

11. [New] C (with its saves or reservations from above, and at least 5 other listings in the feed): Home's top row shows up to 4 cards and swipes sideways; none is C's own, reserved or saved.
12. [Regression] A new account or one with no history: "Just posted" shows up to 4 of the newest listings.

### 31.6 Known limits

- It learns only from this account's own reservations and saves, as loaded on this phone: not from what other people choose (no collaborative filtering), not from views or ratings.
- Listings not chosen so far count as negatives, including ones the user never saw; a cancelled or expired reservation still counts as a choice, and its listing is never recommended again.
- The row shows at most 4 recommendations (`TOP_ROW_CARDS` in `MainActivity.kt`, also the fallback rows' size), fewer when fewer listings share something with the account's choices; "Browse all" opens the normal list sorted by Newest (the model doesn't sort it).
- Words are matched as written: "pandesal" and "pan de sal" differ, and plural folding is simple and English-only.
- Only the newest 50 chosen listings count, by the listing's posting time.
- The account's reservation lists now also load when Home starts: one more set of requests per session (its reservations, those on its listings and its reviews), as when Activity is opened. The row can switch from "Just posted" or "Picked for you" to "Recommended for you" a moment after Home appears, when those loads finish.

## 32. My allergies (2026-10-05)

### 32.0 Status

**Written on branch `claude/allergy-filter` (from `master` at `b15cb2a`, the PR #29 merge) on 2026-10-05, from a suggestion by the developer's friend; the developer chose "hide, with a Show link" after seeing mock-ups in the session. No SQL, no new library, no new permission. Built with AGP on the developer's PC: `assembleDebug`, `testDebugUnitTest` 91 of 91 and `lintDebug` 0 errors (§32.4). The 10-step one-phone test (§32.5) passed on 2026-10-06.**

### 32.1 What the user sees

- **Post and Edit:** the Allergens text box is replaced by chips: Milk, Eggs, Fish, Shellfish, Peanuts, Tree nuts, Wheat/gluten, Soy, Sesame, Other and None. One is required ("Pick the allergens, or None if there are none."). None turns the others off. Other opens an "Other allergens" box that must be filled in ("Describe the other allergens, or tap Other to remove it."). The hint names patis (fish) and bagoong (fish or shrimp). On Edit, an older listing's typed allergens open under Other, so nothing is lost. "Check your listing" shows the same allergen problem as Post.
- **Profile → My allergies** (under Food preferences; also in the Settings hub): the nine allergens as checkboxes, with examples under Fish, Shellfish and Tree nuts; a warning that typed and "Other" allergens can't be checked; Save allergies ("Allergies saved. Home hides listings that contain them." or "Allergies cleared. Home shows every listing.").
- **Home and All listings:** a listing whose donor picked one of the user's allergies is left out. A notice under "Available food" says "1 listing hidden because of your allergies (Milk)." with Show. After Show: "Showing 1 listing with your allergies." with Hide, and each such card has a red "Contains Milk" line under the donor's name. Show lasts until Hide or until the app is closed (rotation keeps it). When every listing in view is left out, the empty state is "No food without your allergies right now" with "Show them". The count under "Available food" counts only the listings shown.
- **The row at the top of Home** ("Recommended for you", "Picked for you", "Just posted") never shows such a listing, even after Show. The recommendation model is unchanged (it never read allergens); only its candidates are filtered, as Food preferences already did.
- **Food details** (also opened from Saved Listings and the Profile records): a red "Contains Milk, which is on your allergy list." at the top; Reserve stays (the food may be for someone else). When the user has allergies and the listing's allergens can't be checked (typed before the chips, blank, or under Other), an amber note under Allergens says so and to ask the donor. The user's own listings are never hidden or marked.

### 32.2 How it works

- **Stored text:** the chips write the existing `food_posts.allergens` text: "None", or the picked labels in list order ("Milk, Shellfish"), optionally ending with "Other: <text>". The column, its 300-character check and `update_my_food_post_with_pin` are unchanged, and the admin dashboard and older builds show the same readable text.
- **Reading it back:** `parseAllergens()` accepts only that form (labels compared without case). Anything else, including blank and typed text such as "Contains gatas", can't be checked. Only allergens picked from the list are matched; nothing is ever called allergen-free or safe.
- **My allergies** are saved with Food preferences on the phone, per account (`DiscoveryPreferences`, key `allergies`, by enum name), never on Supabase.

### 32.3 What changed

- `model/Allergens.kt` (new): `Allergen` (labels and examples), `ListingAllergens`, `allergenText()`, `parseAllergens()`, `allergiesIn()`, `allergenNames()`.
- `PostScreen.kt`: `AllergenPicker`, `AllergenChoice` (kept across rotation by `AllergenChoiceSaver`) and `allergenChoiceOf()`, on Post and Edit; the allergen checks and texts.
- `DiscoveryPreferences.kt`: `allergies`, `myAllergiesIn()`, `saveAllergies()`.
- `ProfileSettingsUi.kt`: `AllergySettings` and its row in the Settings hub. `ProfileScreen.kt`: the My allergies row. `ProfileSections.kt`: its title and page.
- `MainActivity.kt` (`HomeScreen`, `FeedEmptyState`, `AvailableFoodCard`, `FoodDetailsScreen`): leaving listings out, Show and Hide, the empty state, the card line, the details warnings, and the top row's filter.
- Tests: `AllergensTest` (5: the stored text, reading it back, blank or typed text not checkable, only picked allergies match, the names as a sentence).

### 32.4 Checks run (2026-10-05)

- On the developer's PC: `gradlew.bat :app:assembleDebug :app:testDebugUnitTest --offline` BUILD SUCCESSFUL; `testDebugUnitTest` 91 of 91 (0 failures); `lintDebug` 0 errors and 26 warnings (25 before; the new one is the same `SharedPreferences.edit` KTX hint as its two neighbours in `DiscoveryPreferences.kt`).

### 32.5 Phone test (one phone, switching accounts; A = donor, B = recipient)

**Passed on 2026-10-06: every step, reported by the developer.** The optional step below was not reported. Install this branch from Android Studio (no SQL).

1. [New] A: Post. Under "Food information" the Allergens box is now chips. Fill in a free listing "Leche flan test" but pick no allergen → Post listing → "Pick the allergens, or None if there are none." Tap Other → an "Other allergens *" box appears; Post listing → "Describe the other allergens, or tap Other to remove it." Tap None → every other chip turns off and the box goes away. Tap Milk and Eggs (None turns off) → Post listing → published.
2. [New] A: post a second free listing "Pancit test" with Wheat/gluten and Other: "shrimp paste".
3. [Regression] A: Home → open "Leche flan test": Allergens "Milk, Eggs"; no red or amber note.
4. [New] B: Profile → "My allergies" (under Food preferences); Shellfish shows "shrimp, crab, squid, clams, alamang". Tick Milk and Shellfish → Save allergies → "Allergies saved. Home hides listings that contain them."
5. [New] B: Home: "Leche flan test" is in neither Available food nor the row at the top. Under "Available food": "1 listing hidden because of your allergies (Milk)." with Show. "Pancit test" is listed as usual.
6. [New] B: Show → "Showing 1 listing with your allergies." with Hide; "Leche flan test" appears with a red "Contains Milk" line. Open it: red "Contains Milk, which is on your allergy list." at the top, and Reserve is still there. Back, turn the phone: still shown. Tap Hide → hidden again.
7. [New] B: open "Pancit test": no red warning; under Allergens ("Wheat/gluten, Other: shrimp paste") an amber note: "Allergens under "Other" aren't checked against your allergies. Read them, and ask the donor if you're unsure." (Shrimp paste under Other is not matched to Shellfish, as designed: §32.6.)
8. [New] B: search "leche" → "No food without your allergies right now", "1 listing with your allergies is hidden.", "Show them" → the listing appears with the red line. Clear the search. Swipe the app away and open it again (sign in as B if asked): it is hidden again.
9. [New] B: Profile → My allergies → untick both → Save allergies → "Allergies cleared. Home shows every listing." Home: no notice, and "Leche flan test" has no red line.
10. [New] A: Activity → My Listings → "Leche flan test" → Edit listing: Milk and Eggs are selected. Tap Eggs off → Save changes → "Listing updated."; its details show "Allergens: Milk". Delete both test listings.

Optional, if a listing posted before this build is still in the feed: B with allergies set sees the amber "These allergens can't be checked against your allergies. Ask the donor if you're unsure." on it; its donor's Edit listing shows the typed text under Other.

### 32.6 Known limits

- Only allergens picked from the list are matched. Older listings with typed allergens, and anything under Other ("shrimp paste" is not matched to Shellfish), are never hidden; they show a note instead. Older listings leave the feed as they expire.
- The app trusts the donor's choice and can't check the food; a donor can still pick None by mistake.
- My allergies belong to the account on this phone (Android's backup can restore them); another phone doesn't know them, and neither does the server.
- Only Home, All listings and the top row leave listings out. Saved Listings, Activity and Messages show them as before; the details page warns wherever it opens, but Activity's own listing pages don't.
- The labels are part of the stored text: renaming one (for example "Wheat/gluten") would make listings posted with the old label uncheckable.
- Older app builds still have the text box; listings posted from them can't be checked.

## 33. Keyboard and map fixes (2026-10-06)

### 33.0 Status

**Written on branch `claude/keyboard-and-map` (from `claude/allergy-filter` at `aa76d51`, the §32 commit in PR #30, which wasn't merged yet) on 2026-10-06, from the developer's friend's suggestions #1 and #3 (#2 was §32). No SQL, no new library, no new permission. Built with AGP on the developer's PC: `assembleDebug`, `testDebugUnitTest` 95 of 95 and `lintDebug` 0 errors (§33.3). The 7-step one-phone test (§33.4) passed on 2026-10-06, and so did steps 8–9 (the ✕ on the full-screen map and the "saved" snackbar, added afterwards); the developer reported them right after the merge. Committed as `da71998`; merged into `master` through PR #31 on 2026-10-06 (merge commit `9568264`), right after PR #30 (`ed07da9`).**

### 33.1 What the user sees

- **The bottom bar hides while the keyboard is open** (Home's search, Post, and any other page in the main window). Before, the app shrank above the keyboard and the bar rode on top of it. It comes back when the keyboard has closed. Full-screen pages (chat, Edit Listing, the Profile pages) never had the bar.
- **"Use my location" fills in the pickup location** on Post and Edit, when the box is still empty: once the pin is placed from a precise fix, Android's address lookup (Geocoder) turns it into "street, barangay, town, province" (for example "Rizal Street, Poblacion, Tagbilaran City, Bohol"), never with a house number or the country, and the line under the map reads "Pin placed, and the pickup location filled in from it. Check it above and add a landmark." It never replaces typed text, and only fills while the pin is still the one it looked up. No address (offline, nothing found, no Geocoder, over 10 seconds): the box stays empty and nothing is said. A pin placed by tapping the map never fills the box.
- **A full-screen map:** a button in the small map's top-right corner ("Show the map full screen") opens the same map full screen, with "Pickup pin", the status line and Done at the top, and a large white ✕ ("Close the map") in the map's top-left corner, added after the test at the developer's request so older users find the way out. Tapping places or moves the pin there as on the small map; Done, the ✕ or Back closes it (the pin stays), and the small map then moves to the pin. It stays open through rotation.
- **"Saved" messages pop up at the bottom (a snackbar),** added at the developer's request: on the Profile pages, "Preferences saved. Check Home for matching listings.", "Allergies saved…" / "Allergies cleared…", "Pickup area saved.", "Preferences saved to your account." (Notifications), "Profile saved to your account.", "Profile photo saved." and "Your password was changed." show as a dark-green bar with a white tick at the bottom of the page for about 4 seconds, instead of a box under the Save button. It sits below the page (never over the Save button) and stays when Back goes to the previous Profile page. Errors and "fix this" texts ("Your passwords do not match.", "Enter your display name.", "could not be saved") stay next to the form as before. Notifications no longer shows "Preferences saved on this device." first: the sound is still saved at once, and the snackbar comes when the account's settings are saved.

### 33.2 What changed

- `MainActivity.kt` (`HomeScreen`): `MainNavigationBar` is left out while the keyboard is open (`WindowInsets.ime`, through `derivedStateOf`, so Home recomposes only when it opens or closes).
- `model/Geo.kt`: `pickupAddressText(street, area, town, province)`: joins the parts, drops blanks, repeats, "Unnamed Road" and a house number at the start of the street ("12 ", "12B ", "12-B "; "10th Street" stays).
- `data/PhoneLocation.kt`: `pickupAddress(context, pin)`: one Geocoder lookup on the IO dispatcher (the blocking call, which works on every Android version), at most 10 seconds; `thoroughfare`, `subLocality`, `locality` and `subAdminArea` (else `adminArea`). Its note and the object's note say the position goes to the Geocoder's provider.
- `PickupMap.kt` (`PickupPinPicker`): new `locationText` and `onLocationTextChange`; the fill after "Use my location"; the expand button and the full-screen `Dialog` (`EdgeToEdgeDialog`, `SaluTopBar`), with a 52 dp ✕ on the map (the `FullScreenPhotos` style) besides Done.
- `PostScreen.kt`: Post and Edit pass their pickup location to the picker.
- `ProfileSections.kt`: a `SnackbarHostState` and `showSaved()` (clears the inline text, replaces a snackbar still showing), `SnackbarHost` as the last row of the page and `SavedSnackbar` (Material 3 `Snackbar`, `SaluColors.DarkGreen`, `SaluRadius.Control`); Notifications and Change Password use it. `ProfileSettingsUi.kt`: `ProfileEditor`, `FoodPreferenceSettings`, `AllergySettings` and `LocationPreferenceSettings` take `onSaved`; the last three lose their own message box.
- **Privacy:** the pin's position is sent to the Geocoder's provider (Google on most phones) when the donor taps "Use my location" and the box is empty. Before, the position left the phone only when the donor saved the pin with a listing. Nothing goes to Supabase, and nothing is logged or stored.
- Tests: 4 more in `GeoTest` (the order of the parts; blank, "Unnamed Road" and repeated parts; no house number; null when nothing is left).

### 33.3 Checks run (2026-10-06)

- On the developer's PC: `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --offline` BUILD SUCCESSFUL; no Kotlin warnings; `testDebugUnitTest` 95 of 95 (0 failures); `lintDebug` 0 errors and 26 warnings (as in §32.4; none on a changed line).
- The new `GeoTest` cases failed against a stub first (3 of 4; the null case passes against any stub), then passed.

### 33.4 Phone test (one phone, account A)

**All 9 steps passed on 2026-10-06, reported by the developer (steps 8–9 right after the merge).** Install this branch from Android Studio (no SQL). Location on, and internet on.

1. [New] Home: tap the search box. The keyboard opens and the bottom bar is gone (before, it sat on top of the keyboard). Close the keyboard: the bar comes back.
2. [New] Post: tap Food name. No bar while typing, and the form scrolls above the keyboard. Close the keyboard: the bar is back.
3. [New] Post, Pickup location empty: tap "Use my location" (allow Precise if asked). The pin is placed, and within a few seconds the Pickup location box fills with something like "<street>, <barangay>, <town>, Bohol", with no house number and no "Philippines". The line under the map reads "Pin placed, and the pickup location filled in from it. Check it above and add a landmark." (If the box stays empty, note it: the lookup may have no address for that spot, which is allowed.)
4. [New] Change the box to "Gate of the plaza", tap "Use my location" again: the pin is placed, the line reads "Pin placed. Tap the map again to move it.", and the box still says "Gate of the plaza".
5. [New] Tap the button in the map's top-right corner: the map fills the screen, with "Pickup pin", the status line and Done at the top. Pinch to zoom, tap another spot: the pin moves. Turn the phone: still full screen, same pin. Tap Done: back on the form, and the small map shows the new pin. Open it again and press Back: it closes the same way.
6. [Regression] Fill in the rest and post the listing → published. Home → open it → the pickup location page shows "Gate of the plaza" and the pin where you put it.
7. [New] Activity → My Listings → the listing → Edit listing: tap "Use my location": the pin moves, but the filled-in location box doesn't change. The full-screen button works here too. Close Edit, then delete the test listing.
8. [New, added after steps 1–7 passed] Post → open the map full screen: a large white ✕ in the map's top-left corner. Tap the map to move the pin, then tap ✕: back on the form, the pin kept, and the small map shows it.
9. [New, added after steps 1–7 passed] Profile → Settings → My allergies → tick Milk → Save allergies: a dark-green bar with a tick fades in at the bottom, "Allergies saved. Home hides listings that contain them.", and goes away after a few seconds; no box under the button. Back, then Food preferences → Save preferences: "Preferences saved. Check Home for matching listings." the same way. Notifications → Save preferences: "Preferences saved to your account.". Change Password with two different new passwords: "Your passwords do not match." stays in a box on the page (no bar). Untick Milk again and save.

### 33.5 Known limits

- Philippine addresses from the Geocoder are often rough (only the barangay or the town, or nothing); the text is a starting point the donor checks.
- The lookup sends the pin's position to the Geocoder's provider (Google on most phones). Phones without one never fill the box.
- Only "Use my location" fills the box, and only an empty one; a pin placed by tapping (or on the full-screen map) doesn't.
- Turning the phone while the address is looked up drops the lookup (the pin stays placed).
- The full-screen map opens at the pin (or where the small map started), not where the small map was dragged to.
- The bar comes back only once the keyboard has fully closed.
## 34. Portrait only (2026-10-06)

### 34.0 Status

**Written on branch `claude/portrait-only` (from `master` at `9568264`, the PR #31 merge) on 2026-10-06, at the developer's request. No SQL, no new library, no new permission. Built with AGP on the developer's PC (§34.2). Not tested on a phone yet (§34.3).**

### 34.1 What changed

- `AndroidManifest.xml`: `MainActivity` has `android:screenOrientation="portrait"`, so the app stays upright whether auto-rotate is on or off; its dialogs and full-screen pages follow it. Lint's `LockedOrientationActivity` and `DiscouragedApi` warnings are turned off for that line, with a comment, because the lock is the developer's choice.
- Android 16 and later ignore the lock on large screens (smallest width 600 dp or more: tablets, unfolded foldables) for apps that target Android 16 (`targetSdk` 36), so the app still turns there. Phones keep it. The app's state survives a turn there, as it does a dark-mode or font-size change (the `rememberSaveable` work of §21 and round 3 stays).

### 34.2 Checks run (2026-10-06)

- On the developer's PC: `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --offline` BUILD SUCCESSFUL; `testDebugUnitTest` 95 of 95; `lintDebug` 0 errors and 26 warnings (as before; the two orientation warnings are turned off on purpose).

### 34.3 Phone test (one phone)

**Not run yet.** Install this branch from Android Studio (no SQL).

1. [New] Turn auto-rotate on. Open the app and turn the phone sideways on Home, on Post and on a listing's details: the app stays upright each time.
2. [New] Turn auto-rotate off and repeat: the app stays upright.
3. [Regression] Open the full-screen pickup map and a listing's full-screen photos, and turn the phone: both stay upright and work as before.

## 35. Push notifications and staying signed in (2026-10-07)

### 35.0 Status

**Written on branch `claude/push-notifications` (from `claude/portrait-only` at `387e0bb`, so it also carries §34) on 2026-10-07, at the developer's request ("the pop-up notification when the app is closed", and to stay signed in until logging out). Design: `docs/superpowers/specs/2026-10-07-push-notifications-design.md`; plan: `docs/superpowers/plans/2026-10-07-push-notifications.md`. Built with AGP and checked locally (§35.3). Migration `20261016_push_notifications.sql` and the `send-push` setup: not done yet (the developer's steps in `supabase/README.md`, "Push notifications"). Not tested on a phone yet (§35.4).**

### 35.1 What the user sees

- Every alert (a message, a reservation update, an admin's announcement) also pops up as a phone notification ("New message from Ana" / "Pandesal: Is it still there?"), whether Salu-Salo is closed, in the background or open. Not for a message of the chat open on screen. "New messages" and "Reservation updates" off stop those pop-ups too (the alert is never created).
- Tapping it opens the same place as tapping the alert on the Alerts page: the chat, the reservation in Activity, or the announcement's text on Alerts. A tap for an account that isn't signed in on the phone any more just opens the app.
- The phone's notification sound; silent when Profile → Notifications → "Notification sound" is off. Silent mode, vibrate and Do Not Disturb are Android's. While the phone shows Salu-Salo's notifications, the app's own sound stays quiet (the pop-up sounds instead); with notifications off it plays as before.
- Android 13+ asks "Allow Salu-Salo to send you notifications?" once, the first time Home opens after this update. Profile → Notifications shows "Phone notifications are off, so alerts show only inside Salu-Salo." with Turn on (the phone's notification settings) while they are off; its caption now says alerts pop up on the phone.
- Sign In and Create Account have no "Keep me signed in" box: an account stays signed in until Log out. After Log out the phone stops getting that account's pop-ups.

### 35.2 What changed

- **Database (`20261016_push_notifications.sql`):** `pg_net`; table `push_tokens` (token, account, last seen; RLS on, no privileges for any client); `register_my_push_token(p_token)` (SECURITY DEFINER, authenticated only; the caller from the token; a token moves to the account that registers it last; at most 5 per account); the trigger `notifications_push` with the internal `push_new_notification()`: for each new alert it reads the Vault secrets `push_function_url` and `push_webhook_secret` and the account's tokens seen in 60 days (newest 5), and posts the alert, the Firebase UID and the tokens to `send-push` through `net.http_post`; any error is only a warning with its code, so an alert never fails. Check: `supabase/tests/push_notifications_live_safety_check.sql` (10 checks).
- **Edge Function `send-push`:** refuses a call without the header `x-push-secret` equal to the secret `PUSH_WEBHOOK_SECRET`; checks the body; gets a Google access token with the service-account key `FIREBASE_PUSH_KEY` (only `cloudmessaging.messages.create`); sends one FCM v1 data message per token (`alert_id`, `type`, `title`, `body` cut to 300 characters, `uid`, the IDs it has; high priority, kept a day). Logs only counts and HTTP statuses. Deployed by hand with JWT verification off. Tests: `supabase/functions/tests/send-push-test.ts` (12).
- **App:** `firebase-messaging` (Firebase BoM); the manifest's `POST_NOTIFICATIONS` and `PushService`. New `PushNotifications.kt`: `PushTap`, `pushTapFrom()`, `shouldShowPush()` (the signed-in account only; not the open chat), `AlertPresence` (Home resumed, open chat), `showAlertNotification()` (channel "Alerts", high importance; a chat's newest message replaces its older one), `phoneNotificationsOn()`, `cancelChatNotification()`, the permission-asked flag. `NotificationRepository.registerPushToken()`; `NotificationViewModel.start()` registers the token; `markRead` also sends alerts not loaded yet. `MainActivity`: a tapped notification (`onCreate` on a fresh start, `onNewIntent`) waits for Home, which routes it through `openAlert()`, now shared with `AlertsScreen` (one `onOpenAlert` instead of four callbacks); the permission request; `deleteToken()` when Firebase signs out. `ProfileSections.kt`: the notice and caption. "Keep me signed in" removed (`MainActivity.onCreate`'s sign-out, the checkbox, `keepSignedIn()`/`setKeepSignedIn()`); `welcomeSeen()` still counts an older saved choice as seen.
- Firebase 25.1 marks `getToken()`, `deleteToken()` and `onNewToken()` deprecated in favour of a registration API that is off unless the manifest opts in; the app keeps the default path (with commented suppressions) and also handles `onRegistered()`.

### 35.3 Checks run (2026-10-07)

- On the developer's PC: `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --offline` BUILD SUCCESSFUL; `testDebugUnitTest` 104 of 104 (9 new in `PushRulesTest`); `lintDebug` 0 errors and 25 warnings (none on a changed line); no Kotlin warnings.
- Deno 2.9.6 (in the session's scratch folder): `deno test` 33 of 33 (`send-push` 12, `set-firebase-role` 21); `deno check` and `deno lint` clean.
- SQL on a local PostgreSQL 16.4 with stand-ins for Supabase's roles, Vault and `pg_net`: all 25 migrations in order; the safety check failed before the migration (no `push_tokens`) and passed 10 of 10 after it, both without Vault secrets (nothing queued) and with them (one request queued); the migration ran a second time without errors; the request carried the UID, only the token seen within 60 days, and JSON null for missing IDs; an account without tokens queued nothing; a failing `net.http_post` left only a warning and the alert was saved.

### 35.4 Phone test (two phones)

**Not run yet.** First the setup in `supabase/README.md` → "Push notifications" (A–F) and the live safety check (expected 10/10). Then install this branch on both phones: phone 1 = account A (the Xiaomi), phone 2 = account B; A has an active listing.

1. [New] Phone 1: open the app → "Allow Salu-Salo to send you notifications?" → Allow. If signed out: the Sign In page has no "Keep me signed in" box. Sign in as A. Phone 2: the same as B → Allow.
2. [New] Phone 1: swipe Salu-Salo away from Recents and open it again: still signed in as A. Swipe it away again and leave it closed.
3. [New] Phone 2: message A about the listing ("Push test 1"). Phone 1 (closed): a pop-up "New message from <B>" with "<listing>: Push test 1", with sound; tapping it opens the app and then that chat. If nothing arrives within 30 seconds: Settings → Apps → Salu-Salo → Autostart on; if it shows only in the shade: Floating notifications on.
4. [New] Phone 1 keeps that chat open; phone 2 sends "Push test 2": no pop-up, the message appears in the chat. Phone 1 closes the chat (Home); phone 2 sends "Push test 3": a pop-up while the app is open, one sound.
5. [New] Phone 1 in the background (Home button); phone 2 reserves A's listing (2-hour pickup): pop-up "New reservation"; tapping it opens Activity → My Listings → that listing's Manage reservations. Phone 2 cancels.
6. [New] Admin dashboard → Notifications → an announcement to Everyone: both phones pop up; tapping it on phone 1 opens Alerts with its text.
7. [New] Phone 1: "Notification sound" off → Save; phone 2 messages A: a silent pop-up; turn it back on. Then the phone's notifications for Salu-Salo off: Profile → Notifications shows "Phone notifications are off, so alerts show only inside Salu-Salo." with Turn on; with Home open, phone 2 messages A: no pop-up, but the app's own sound plays. Turn on → switch on → back: the notice is gone.
8. [New] Phone 1: Log out; phone 2 messages A: no pop-up. Sign in as A again; phone 2 messages A: the pop-up again.
9. [Regression, §34] Auto-rotate on, then off: Home, Post, a listing's details, the full-screen pickup map and photos stay upright.
10. [Regression] With the app open, the chat and Alerts badges still go up live; "New messages" off → no alert and no pop-up; turn it back on.

Left out (not tested): Android 7, Do Not Disturb (Android's), switching accounts without Log out, more than 5 phones per account, a token unused for 60 days.

### 35.5 Known limits

- Xiaomi, Redmi and POCO phones (and some other brands) may block pop-ups for an app swiped away from Recents until Autostart is allowed; no app can get around it, and `phoneNotificationsOn()` can't see it.
- The alert's title and text (up to 300 characters) pass through Google's FCM.
- Before the setup is done, while phone notifications are on, no sound plays for a new alert while the app is open (the app leaves the sound to the pop-up).
- Dead tokens stay in `push_tokens` until the 60-day filter and the 5-per-account cap leave them out (the function has no database access).
- An announcement makes one `pg_net` call per account (fine for the current number of users).
- Someone who learned another phone's FCM token could register it to their own account; the app never shows or sends a token anywhere else.
- A tap after the account signed out, or on a phone now signed in to another account, just opens the app.
- Firebase 25.1's deprecated token calls (§35.2) may need the newer registration API in a later Firebase version.
