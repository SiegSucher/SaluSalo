# Reservation limits and pickup deadlines — design

Date: 2026-10-02. Status: design approved in chat (parts 1–3); this written spec awaits the developer's review.

## 1. Why

PR #16 (Mika, branch `mika/admin-ui`) proposed a per-person limit on listings and a pickup timer that releases no-shows. It can't be merged: it is based on `f27ee47` and conflicts with `master` in 7 files, its central migration (`20261008_reservation_expiry.sql`) is in no branch, its migration names reuse `master`'s `20261005`–`20261010`, and its `create_reservation` drops round 2's rate limit and §21's pickup-end check while its Reserve skips the price check (`create_reservation_at_price`).

The developer chose to rebuild the idea on top of `master` instead. It closes open audit finding #9: reservations never expire by themselves, and one person can reserve a listing's whole quantity.

A read-only check of the live database (2026-10-02) found none of PR #16's SQL applied: no `create_reservation_with_pickup`, `expire_pickup_reservations`, `get_donor_reservations_with_pickup` or `get_my_reservation_allowance`; no `max_per_receiver`, `pickup_minutes` or `pickup_deadline` columns; none of the reservation functions changed; the rate limit, the pickup-end check and the price-checked Reserve in place; `pg_cron` not installed.

## 2. Decisions

| Question | Decision |
|---|---|
| Who chooses how long a reservation is held | The recipient, on Review reservation: 15 min, 30 min (selected first), 1 hour or 2 hours. |
| When the hold starts | At the later of the moment of reserving and the listing's pickup start, so a listing can be reserved ahead of its pickup window. |
| Cap on the deadline | Never after the end of pickup; for older listings without a pickup window, never after the expiry. |
| What happens at the deadline | A server job (Supabase Cron, every minute) cancels the reservation with reason `PICKUP_EXPIRED`, puts its quantity back on the listing and alerts both people. Works with every phone off. |
| "Mark as picked up" after the deadline | Refused by the server; the button is hidden. |
| Where the per-person limit is set | On Post only. It can't be changed later. Older listings have no limit. |
| What counts toward the limit | The person's `RESERVED` and `CLAIMED` quantity on that listing. Cancelled and expired reservations don't count. |

## 3. Out of scope (YAGNI)

- Changing the limit on Edit Listing.
- Extending a deadline (recipient or donor).
- Deadlines for reservations that already exist, or that older app builds make: they never expire, as today, and donors release them by hand.
- Moving deadlines when a donor later edits the pickup window.
- Showing the limit on Home cards (the details page shows it).
- Any timer on the phone that changes data. The phone only displays; the server decides.

## 4. Database

One new migration, `supabase/migrations/20261011_reservation_limits_and_expiry.sql`. Safe to run again, safe on the live data, no explicit `begin`/`commit` (so a trial run can be rolled back, §8). It must run **before** the new app build is installed; older builds keep working after it.

### 4.1 The per-person limit

- `food_posts.max_per_recipient integer` (nullable; NULL = no limit), with `food_posts_max_per_recipient_check`: NULL or 1 to 10,000.
- "At most the listing's quantity" is checked **on insert only**, in the existing `food_posts_check_values()` trigger function (its insert branch), with the text "The maximum per person must be from 1 to the quantity." It can't be a table constraint: the quantity goes down as people reserve, and a constraint would then refuse the reservation's own update.
- Inserts already have table-level privileges for `authenticated`, so no grant is needed. Clients have no UPDATE on `food_posts`, so the value can't change after posting. `update_my_food_post` and `update_my_food_post_with_pin` are unchanged and never touch it.
- `create_reservation(uuid, integer)` is redefined: the `20261009` version unchanged (listing lock, not-found/expiry, `REMOVED`, pickup end, profile, own listing, quantity, then the per-profile rate limit under its advisory lock), plus one check after the quantity check and before the rate limit. When `max_per_recipient` is set, it sums the caller's `RESERVED` and `CLAIMED` quantity on the listing. If that sum plus the new amount is over the limit, it refuses:
  - at or over the limit already: "You've reserved the most this listing allows per person (3)."
  - otherwise: "This listing allows 3 per person. You can reserve 1 more."

  The listing lock, taken first, makes simultaneous reservations by the same person count correctly.

### 4.2 The pickup deadline

- `reservations.pickup_deadline timestamptz` (nullable; NULL = never expires).
- A partial index on `pickup_deadline` where `status = 'RESERVED' and pickup_deadline is not null`, for the job.
- `reservations_cancel_reason_check` is replaced to allow `PICKUP_EXPIRED`: `cancel_reason is null or (cancel_reason in ('LISTING_REMOVED', 'DONOR_RELEASED', 'PICKUP_EXPIRED') and status = 'CANCELLED')`.
- New `create_reservation_with_hold(p_food_post_id uuid, p_quantity integer, p_unit_price numeric, p_hold_minutes integer) returns reservations`, SECURITY DEFINER, `search_path = public`, EXECUTE for `authenticated` only:
  1. "Not authenticated" without a SaluSalo token.
  2. `p_hold_minutes` must be 15, 30, 60 or 120: "Choose how soon you'll pick up: 15, 30, 60 or 120 minutes."
  3. Reserves through `create_reservation_at_price` (the price check, then `create_reservation` with every check above).
  4. Reads the listing (already locked by this transaction) and sets the deadline:
     `least(greatest(now(), coalesce(pickup_start, now())) + p_hold_minutes minutes, coalesce(pickup_end, expiration_date), expiration_date)`.
  5. Returns the updated reservation.

  The update fires no alert (`reservations_notify` alerts only on status changes).
- `create_reservation` and `create_reservation_at_price` keep their arguments and results and set no deadline (older builds).
- `claim_reservation` is redefined: the `20261005` version plus, after the status check, "The pickup time for this reservation has passed." when `pickup_deadline <= now()`.
- `cancel_reservation` and `release_reservation` are unchanged. Between the deadline and the job's next run they still work; the quantity comes back once either way, because each changes only `RESERVED` rows.

### 4.3 The expiry job

- New `expire_overdue_reservations() returns integer`, executable by no client (`revoke all` from `public`, `anon` and `authenticated`), run by Cron as the job owner (`postgres`).
- It takes up to 500 overdue `RESERVED` reservations, oldest deadline first, `for update skip locked` (reservations first, then the listing, the project's lock order). For each one:
  - lock its listing `for update skip locked`; if someone else is using it, skip the reservation (it's retried next minute);
  - set `status = 'CANCELLED'`, `cancel_reason = 'PICKUP_EXPIRED'`;
  - add its quantity back to the listing and set the listing `AVAILABLE`, except that a `REMOVED` listing stays `REMOVED` (as `release_reservation` does).

  It returns how many reservations it expired. It never waits on a lock, so it can't deadlock with the app's functions.
- Cron: `create extension if not exists pg_cron with schema pg_catalog`, the two grants to `postgres` from Supabase's install guide, then `cron.schedule('salusalo-expire-reservations', '* * * * *', 'select public.expire_overdue_reservations()')`. Scheduling an existing job name replaces it, so running the file again is safe. If Supabase refuses the extension from the SQL Editor, the developer switches Cron on under Integrations → Cron and runs the file again.

### 4.4 Alerts and the donor's list

- `notify_reservation_change()` gets a branch for `PICKUP_EXPIRED` (only when the status changes). It sends two alerts of type `RESERVATION_CANCELLED`, which every app build already decodes and opens:
  - to the recipient, titled "Reservation expired": "Your reservation of <listing> expired because it wasn't picked up in time."
  - to the donor, titled "Reservation expired": "<recipient>'s reservation of <listing> expired because it wasn't picked up in time. The quantity is available again."

  Alert texts contain no clock times (the server's time zone is UTC). Each alert follows the person's "Reservation updates" switch, as all reservation alerts do. Every other branch stays as in `20261006`.
- `get_donor_reservations()` also returns `pickup_deadline`. Its result type changes, so it is dropped and recreated, as in `20261006`. Older builds ignore the extra column.

### 4.5 Rules added to CLAUDE.md

- Keep the limit check and the rate limit when redefining `create_reservation`; keep the price check in `create_reservation_with_hold`.
- `pickup_deadline` is set only by `create_reservation_with_hold`, never by the app.
- `expire_overdue_reservations()` stays non-executable by every client role; the Cron job calls only it.

## 5. App: posting and reserving

- **Model** (`model/FoodPost.kt`, `model/Reservation.kt`):
  - `FoodPost.maxPerRecipient: Int? = null`.
  - `NewFoodPost.maxPerRecipient: Int?` with no default, so it is always sent (null when blank), per the "no defaults in `NewFoodPost`" rule.
  - `FoodPostDraft.maxPerRecipient`.
  - `Reservation.pickupDeadline: String? = null` and the same on `DonorReservationRow` (and `toReservation()`).
  - `CANCEL_REASON_PICKUP_EXPIRED` and `Reservation.expiredUnpicked`.
- **Pure rules** (unit-tested):
  - `pickupDeadlineEstimate(now, pickupStart, pickupEnd, expiration, holdMinutes)`: the server's formula.
  - `reservableNow(limit, available, held)`: `available` without a limit, otherwise the smaller of `available` and `limit − held` (never below 0).
  - `HOLD_MINUTES = listOf(15, 30, 60, 120)`, default 30.
- **Post** (`PostScreen.kt`):
  - An optional "Maximum per person" field under Quantity, with "Optional. Leave blank for no limit.", validated like the database: "Enter a maximum per person from 1 to the quantity, or leave it blank."
  - "Check your listing" shows "Maximum per person: 3" or "No limit".
  - `EditListingDialog` shows "Maximum per person: 3 (can't be changed)" when set.
- **Details page** (`MainActivity.kt`, `FoodDetailsScreen`):
  - When the listing has a limit, it shows "Limit: 3 per person". On opening, it reads the person's own `RESERVED`/`CLAIMED` quantity on the listing (a plain read of `reservations`, already allowed by RLS), and the quantity stepper stops at `reservableNow(...)`.
  - At 0 it shows "You've reserved the most this listing allows per person (3)." instead of Reserve.
  - If the read fails, the stepper uses the quantity left and the server's message explains a refusal.
- **Review reservation** (`FoodActions.kt`, `ReservationReviewDialog`):
  - A "Pick up within" card with the four choices (kept across rotation).
  - Under the choices, the phone's estimate:
    - "Pick up by 5:30 PM";
    - before pickup starts: "Pickup starts at 5:00 PM, so pick up by 5:30 PM.";
    - when cut short: "Pick up by 7:00 PM, when pickup ends.".
  - Then the line "If it isn't marked as picked up by then, the reservation expires and the food goes back on the listing."
  - Confirm passes the minutes.
- **Receipt** ("Reserved"): "Pick up by …" from the saved `pickup_deadline`, plus the same expiry line.
- **Data** (`ReservationRepository`, `ReservationViewModel`):
  - `createReservation(foodPostId, quantity, unitPrice, holdMinutes)` calls `create_reservation_with_hold`.
  - New `heldQuantity(foodPostId)`.
  - `reserve(...)` gains `holdMinutes`.
  - Errors show through the existing short-error path.
- **Clock text** (`UiText.kt`, unit-tested in `UiTextTest`): "5:30 PM" today, otherwise `momentLabel` style ("Tomorrow, 9:30 AM").

## 6. App: Activity

- **Deadline lines.** My Reservations cards and Manage reservations cards show, for open reservations with a deadline:
  - "Pick up by 5:30 PM · 12 min left";
  - before pickup starts: "Pick up by 5:30 PM · pickup starts 5:00 PM";
  - in the error colour under 10 minutes;
  - "Time's up" once passed.

  Reservations without a deadline look as they do today.
- **Reservation details page.** A "Pick up by" row.
- **Updates.** The time-left text updates once a minute (a minute ticker), not every second.
- **Reload.** While Activity is shown and the app is in front, it reloads the reservation lists once, quietly, about 70 seconds after the earliest deadline it shows has passed (after the job's next run). There is no polling.
- **After the deadline**, the donor's Mark as picked up is hidden on the Manage reservations card and the reservation page, with "The pickup time has passed." The server refuses anyway.
- **History.** An expired reservation shows the `SaluStatus.Expired` badge instead of Cancelled, and the reservation page's note says:
  - for the recipient: "You didn't pick up in time, so the reservation expired and the food went back on the listing."
  - for the donor: "<name> didn't pick up in time, so the reservation expired."

## 7. Compatibility

- **Order:** migration, then the new build. The new build reserves through `create_reservation_with_hold`.
- **Older builds:**
  - they reserve as before, without a deadline;
  - the per-person limit applies to them, and they show the server's message;
  - they see an expired reservation as an ordinary cancelled one and receive both alerts;
  - their `get_donor_reservations()` rows gain a column they ignore.
- **Existing open reservations** keep no deadline and never expire.

## 8. Testing

There is no PostgreSQL, Docker or WSL distribution on the development PC, so the SQL is tested on the live project in ways that leave nothing behind:

1. **Trial run.** In the SQL Editor, paste the migration followed by `supabase/tests/reservation_limits_live_safety_check.sql` and run both as one query. The check ends with an error on purpose, and because the editor runs one multi-statement query as a single transaction, that error also undoes the migration. Afterwards, the read-only check must report the new objects as **missing**, which confirms nothing stayed. If it reports them present instead, the editor committed the statements one by one. That is harmless, because the migration is safe on live data and older builds keep working, and it simply counts as step 2. The check's own test data is still undone by its error.
2. **Apply for real**, then run `supabase/tests/reservation_limits_check.sql` (read-only). It checks:
   - the columns, the constraints and the replaced cancel-reason rule;
   - the functions and their EXECUTE grants (`anon` none; `authenticated` on `create_reservation_with_hold` only among the new ones; nobody on `expire_overdue_reservations`);
   - that `create_reservation` still has the rate limit, the pickup-end check and the limit;
   - the Cron job `salusalo-expire-reservations` (active, every minute) and at least one successful run in `cron.job_run_details`.
3. **Live safety check** (also run alone after applying; it rolls back its own test data). Pretend sign-ins as a donor and another account, as the existing checks do:
   - **Sanity:**
     - a listing with quantity 10 and limit 3 inserts;
     - a reservation of 2 with a 30-minute hold gets the expected deadline (before pickup start, inside the window, and cut to pickup end);
     - an old-style `create_reservation` call still works and sets no deadline.
   - **Refusals:**
     - limit 0, and a limit over the quantity, on insert;
     - a hold of 20;
     - 2 more over the limit (the "1 more" text), and a third reservation at the limit;
     - a direct update of `pickup_deadline` by `authenticated`;
     - `anon` and `authenticated` calling `expire_overdue_reservations()`;
     - a claim after the deadline.
   - **Expiry:**
     - a past deadline plus a job call cancels with `PICKUP_EXPIRED` and returns the quantity;
     - a second call changes nothing (the quantity comes back exactly once);
     - one "Reservation expired" alert each for the recipient and the donor;
     - a `REMOVED` listing stays `REMOVED`;
     - a cancel after the deadline but before the job also returns the quantity once.
4. **App:**
   - new unit tests: the deadline estimate, `reservableNow` and the clock text;
   - all existing tests (57) still pass;
   - `assembleDebug` and `lintDebug` (0 errors).
5. **Two-phone A→B→A test** (written in full in `PROJECT_STATUS.md` §24). To keep it short, A posts a listing with limit 2 whose pickup ends 6 minutes after posting. B:
   - reserves 1, then is refused above the limit;
   - picks 15 minutes and sees "Pick up by …, when pickup ends";
   - watches the countdown in Activity.

   Within about a minute after the end, B's reservation moves to History as Expired, both phones get the alert, and Home shows the quantity back. A late Mark as picked up is refused. The regression covers Reserve on an unlimited listing, cancel, release, claim before the deadline, and Post.

## 9. Docs and process

- **Branch.** `claude/reservation-limits`, from `master` after round 3 (`claude/bugfix-pass-3`) is phone-tested and merged; this work touches the same files.
- **Docs to update:**
  - `PROJECT_STATUS.md` §24, and its current-state and known-issues lists (#9 becomes fixed, with its limits);
  - `CLAUDE.md` (Supabase objects, the rules in §4.5, the roadmap line);
  - `README.md`;
  - `supabase/README.md` (the run order and the Cron step).
- **Mika.** Credit (`Co-authored-by`) on the commit and closing PR #16 with a link to the new PR happen only when the developer says so.

## 10. Known limits

- Expiry happens up to about a minute after the deadline. It takes longer if more than 500 reservations are overdue at once or a listing is busy at that moment (retried each minute).
- If Cron is turned off, nothing expires (late claims are still refused); the read-only check shows the job's state.
- A donor who hands food over but doesn't tap Mark as picked up before the deadline sees it expire and the quantity come back. Edit can't change the quantity, so the donor would have to remove the listing if nothing is left.
- Deadlines are fixed when reserving; a later edit of the pickup window doesn't move them.
- The limit is per account; a second account gets its own allowance (as with #12).
- The details page's "you can still reserve" count is read when the page opens; the server decides at Reserve.
