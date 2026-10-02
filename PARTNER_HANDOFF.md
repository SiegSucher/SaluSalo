# Draft PR #16 — reservation limits and pickup deadlines

This PR adds maximum quantity per receiver, 15/30/60/120-minute pickup choices,
server deadlines, expiry/stock restoration, and countdown/expired-status screens.

## Database application order

Apply only migrations not already applied, in this order, after the existing
project migrations through 20261004:

1. 20261005_listing_receiver_limit.sql — listing limit column.
2. 20261006_enforce_receiver_limit.sql — cumulative receiver limit under listing lock.
3. 20261007_reservation_pickup_deadline.sql — duration/deadline and new creation RPC.
4. 20261008_reservation_expiry.sql — expiry, safe release, guarded cancellation/claim.
5. 20261009_donor_pickup_details.sql — provider's deadline/cancellation-reason query.
6. 20261010_reservation_allowance.sql — current receiver's stock/allowance/time snapshot.

Merging the PR does not apply these migrations or enable Cron. Use the same
function-owner role (normally postgres) for the SECURITY DEFINER functions.
Review alongside any database changes not represented in the uploaded migration files.

## Enable and verify expiry

Enable Supabase Cron/pg_cron. As the function owner, run:

```sql
select cron.schedule(
    'salusalo-expire-pickups',
    '* * * * *',
    'select public.expire_pickup_reservations();'
);
```

Confirm cron.job has the active job and cron.job_run_details records successful
runs. Exact inspection queries and expiry scenarios are in the comments at the
bottom of 20261008_reservation_expiry.sql. The worker processes up to 500 rows
per run and skips locked rows; monitor any persistent backlog or failed job.

Late pickup is blocked by server time even before cleanup runs. Visible
cancellation/stock release normally follows on the next successful minute job;
Activity polling can add up to 15 seconds, with additional delays if offline,
locked or backlogged. No client timer changes database stock.

## Compatibility and existing records

- Existing listings without a limit stay unlimited beyond available stock.
- Existing reservations with NULL pickup_deadline remain manual; they are not
  retroactively cancelled and the UI labels them without inventing a countdown.
- New reservations use create_reservation_with_pickup. Migration 20261008 revokes
  client access to the old deadline-free create_reservation RPC. Older app builds
  will fail to create reservations after that migration. Coordinate deployment.
- RESERVED and CLAIMED count toward the receiver limit. CANCELLED does not.
- Expiry restores accounting quantity once without reopening removed/expired
  listings. Removed reservations retain LISTING_REMOVED rather than timeout labels.
- Name/bio and unrelated migrations are not part of this update's new SQL.

## Verification required before making the PR ready

Run the phone/database tests in START_HERE.md and 20261008. In particular,
test concurrent reserve/cancel/claim/remove/expiry, completed pickups never
expiring, no double stock restoration, and expiry with both apps closed.

Local fixture tests and Kotlin syntax checks passed. Full Android build results
and shared-Supabase/Cron results must be supplied by the team; they have not been
claimed as verified in this package. Keep PR #16 Draft until these gates pass.
